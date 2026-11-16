#!/usr/bin/env python3
"""Publish only a candidate whose build AND real install/upgrade checks passed.

PTERO_TOKEN is supplied in the process environment, never written to disk.
"""
import argparse
import hashlib
import io
import json
import os
import tarfile
from pathlib import Path
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--evidence', type=Path, required=True)
parser.add_argument('--source', type=Path, required=True)
parser.add_argument('--version', required=True)
parser.add_argument('--code', type=int, required=True)
parser.add_argument('--expected-previous-code', type=int, required=True)
parser.add_argument('--note', type=Path, required=True)
parser.add_argument('--verify-only', action='store_true', help='Read-only automated preflight; NEVER upload or change OTA')
parser.add_argument('--acceptance', type=Path, help='Owner-confirmed physical regression acceptance for these exact APK bytes; required for actual publication')
parser.add_argument('--server-id', default='e41563a0', choices=['e41563a0'], help='Verified Aurevia destination; never publish to the retired source')
args = parser.parse_args()
assert re.fullmatch(r'\d+\.\d+\.\d+', args.version)
assert args.code >= 197, 'This publisher is for the unified release and later; baseline 196 is already published'
proof = json.loads((args.evidence / 'run-result.json').read_text())
assert proof['conclusion'] == 'success', 'Build/install workflow has not passed'
assert all(j['conclusion'] == 'success' for j in proof['jobs']), proof['jobs']
for api in (30, 33, 35):
    report = args.evidence / f'install-report-api-{api}'
    text = (report / 'result.txt').read_text()
    assert 'fresh=0 baseline=0 upgrade=0' in text, (api, text)
    assert 'INSTALLED_RESULT baseline=0 upgrade=0' in text, 'The already-installed200 must update to201 without uninstall'
    assert 'firstInstallTime unchanged' in (report / 'installed-upgrade-data-preservation.txt').read_text()
    assert (report / 'fresh-pid.txt').read_text().strip(), 'App failed fresh launch'
    assert (report / 'upgrade-pid.txt').read_text().strip(), 'App failed upgrade launch'
    firebase = (report / 'firebase-client-check.txt').read_text()
    for phase in ('fresh', 'upgrade', 'installed-upgrade'):
        assert f'{phase}: FirebaseMessaging component ready; no invalid registrar' in firebase, 'Native Firebase component discovery did not pass on the actual Android runtime'

candidate = args.evidence / 'aurevia-apk'
apk = candidate / f'Aurevia-Music-v{args.version}.apk'
if not apk.is_file():
    # A delivery-only filename may identify the UI/build clearly; signed bytes MUST
    # still match the original CI digest and EVERY installed-artifact report.
    named = list(candidate.glob(f'Aurevia-Music-v{args.version}-*-build{args.code}.apk'))
    assert len(named) == 1, 'Missing or ambiguous exact-build APK deliverable'
    apk = named[0]
apk_data = apk.read_bytes()
apk_sha = hashlib.sha256(apk_data).hexdigest()
assert (candidate / 'apk-sha.txt').read_text().split()[0] == apk_sha
for api in (30, 33, 35):
    report = args.evidence / f'install-report-api-{api}'
    assert apk_sha in (report / 'input-sha256.txt').read_text(), 'Install evidence belongs to different APK bytes'
    assert 'e991a59ebc8fea1276d47cde655a8b7c3db38d2ebda420f70cb6b863e5ffc5f3' in (report / 'installed-baseline-sha256.txt').read_text(), 'Upgrade fixture must be the exact private200 installed by the owner'

assert 'OK (118 tests)' in (candidate / 'video-geometry-tests.txt').read_text(), 'All geometry, local playback, Live/picker, byte-range HTTP, quality-recovery and iOS scroll regressions must pass'
backend_tests = (candidate / 'backend-tests.txt').read_text()
# Node selects TAP or its Unicode spec reporter depending on runner/TTY. Both have exact final totals.
backend_totals = {name: int(value) for name, value in re.findall(r'^(?:#|ℹ)\s+(tests|pass|fail)\s+(\d+)\s*$', backend_tests, re.M)}
assert backend_totals == {'tests': 24, 'pass': 24, 'fail': 0}, 'Backend Live/push regression gate failed: ' + str(backend_totals)
assert 'PACKAGED_FIREBASE_INIT_OK' in (candidate / 'firebase-config-check.txt').read_text(), 'Firebase config must be packaged and retained in the signed APK'
expected_cert = '44682485497b9db1b5742834a7a0fb041e04f2494c8ee63be4212adbb308c5c7'
assert expected_cert in (candidate / 'apk-signature.txt').read_text()
assert f"package: name='app.aurevia.music' versionCode='{args.code}' versionName='{args.version}'" in (candidate / 'apk-badging.txt').read_text()
source_data = args.source.read_bytes()
source_sha = hashlib.sha256(source_data).hexdigest()
assert (candidate / 'source-sha.txt').read_text().split()[0] == source_sha, 'Source bytes differ from the source actually built by CI'
with tarfile.open(fileobj=io.BytesIO(source_data), mode='r:gz') as bundle:
    client_config = json.load(bundle.extractfile('app/google-services.json'))
assert not {'private_key', 'private_key_id', 'client_email'}.intersection(client_config), 'Private server credential is forbidden in the source/APK'
client_project = client_config['project_info']
assert client_project['project_id'] == 'rixx-app-b5aab', 'Owner selected the new Firebase project; never publish an old-project candidate'
client = next(c for c in client_config['client'] if c['client_info']['android_client_info']['package_name'] == 'app.aurevia.music')
expected_firebase = {'appId': client['client_info']['mobilesdk_app_id'],
                     'apiKey': next(k['current_key'] for k in client['api_key'] if k.get('current_key')),
                     'senderId': str(client_project['project_number']), 'projectId': client_project['project_id']}
source_name = f'Aurevia-Source-v{args.version}.tar.gz'

# Once the destination has accepted traffic, never publish or roll DBs back on the old source.
public_headers = {'User-Agent': 'okhttp/4.12.0', 'Cache-Control': 'no-cache'}
with urllib.request.urlopen(urllib.request.Request('https://aurevia.rixxstore.my.id/health?publish_gate=unified', headers=public_headers), timeout=30) as response:
    health = json.load(response)
assert health.get('ok') and health.get('instance') == args.server_id, 'Public domain is not verified against the destination backend'
with urllib.request.urlopen(urllib.request.Request('https://aurevia.rixxstore.my.id/v1/push/config', headers=public_headers), timeout=30) as response:
    push_status = json.load(response)
assert push_status.get('enabled') is True, 'Unified release includes social push: server authorization is still missing; do NOT claim the complete feature set'
assert push_status.get('config') == expected_firebase, 'Packaged Android and backend Firebase options do not match'

# Automated evidence is NOT phone acceptance. A read-only check can run without any
# panel credential, while actual public promotion additionally requires the owner's
# acceptance of the exact signed APK after all requested behaviors were retested.
preflight = {'candidateCode': args.code, 'apkSha256': apk_sha, 'sourceSha256': source_sha,
             'automatedAndroidApiLevels': [30, 33, 35], 'kotlinJvmTests': 118, 'backendTests': 10,
             'publicDestination': health['instance'], 'firebaseProject': expected_firebase['projectId'],
             'automatedPreflightPassed': True, 'physicalOwnerAcceptanceImplied': False,
             'productionMutated': False}
if args.verify_only:
    (args.evidence / 'verified-publish-preflight.json').write_text(json.dumps(preflight, indent=2))
    print('READ-ONLY PREFLIGHT PASSED; no upload/restart/OTA mutation; physical acceptance still required.', flush=True)
    raise SystemExit(0)
assert args.acceptance and args.acceptance.is_file(), 'Do not publicly publish from compile/install tests alone: owner physical acceptance is required'
accepted = json.loads(args.acceptance.read_text())
assert accepted.get('ownerConfirmed') is True and accepted.get('apkSha256') == apk_sha
for behavior in ('phoneUpdateInPlace', 'nativeYouTubePlayback', 'repeatedResolutionChanges', 'iosLikeScroll', 'vyoraWholeUi',
                 'liveVideoPickerReturnsAndDisplays', 'liveKickAndMuteWork', 'liveHostTransferSyncs', 'twoDeviceWatchTogetherWithVoice', 'socialBackgroundPush'):
    assert accepted.get(behavior) is True, 'Owner acceptance missing for ' + behavior

base = 'https://host.sairistore.web.id/api/client/servers/' + args.server_id
headers = {
    'Authorization': 'Bearer ' + os.environ['PTERO_TOKEN'],
    'Accept': 'application/json',
    'User-Agent': 'okhttp/4.12.0',
}
manifest_path = '/musicsrv/data/aurevia-fork-release.json'
manifest_query = urllib.parse.quote(manifest_path, safe='')


def request(path, body=None, method='GET', raw=False):
    if body is not None and not isinstance(body, bytes):
        body = json.dumps(body).encode()
    req = urllib.request.Request(base + path, data=body, method=method,
                                 headers={**headers, 'Content-Type': 'text/plain' if raw else 'application/json'})
    with urllib.request.urlopen(req, timeout=120) as response:
        result = response.read()
        return json.loads(result) if result else None


manifest = request('/files/contents?file=' + manifest_query)
assert manifest['applicationId'] == 'app.aurevia.music'
if manifest['versionCode'] == args.code:
    assert manifest.get('sha256') == apk_sha, 'Same version code already points to different bytes'
    print('This exact candidate is already published.', flush=True)
    raise SystemExit(0)
assert manifest['versionCode'] == args.expected_previous_code, 'OTA changed while testing; review before publishing'
(args.evidence / 'ota-manifest-before.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2))


def upload(filename, contents):
    for attempt in range(4):
        try:
            signed = request('/files/upload?directory=%2F')['attributes']['url']
            boundary = '----Aurevia' + uuid.uuid4().hex
            body = (f'--{boundary}\r\nContent-Disposition: form-data; name="files"; filename="{filename}"\r\n'
                    'Content-Type: application/octet-stream\r\n\r\n').encode() + contents + f'\r\n--{boundary}--\r\n'.encode()
            req = urllib.request.Request(signed, data=body, method='POST', headers={
                'Content-Type': 'multipart/form-data; boundary=' + boundary,
                'User-Agent': 'Mozilla/5.0',
            })
            with urllib.request.urlopen(req, timeout=300) as response:
                print('Uploaded', filename, response.status, flush=True)
            return
        except Exception as error:
            print('Upload retry', filename, attempt + 1, type(error).__name__, getattr(error, 'code', ''), flush=True)
            if attempt == 3:
                raise RuntimeError('Upload failed; OTA manifest was NOT changed') from None
            time.sleep(10)


upload(apk.name, apk_data)
upload(source_name, source_data)
request('/files/rename', {'root': '/', 'files': [
    {'from': apk.name, 'to': 'musicsrv/public/' + apk.name},
    {'from': source_name, 'to': 'musicsrv/public/' + source_name},
]}, method='PUT')
print('Files placed in public directory; checking remote bytes before OTA update.', flush=True)
for name, expected in ((apk.name, apk_sha), (source_name, source_sha)):
    path = urllib.parse.quote('/musicsrv/public/' + name, safe='')
    signed = request('/files/download?file=' + path)['attributes']['url']
    with urllib.request.urlopen(urllib.request.Request(signed, headers={'User-Agent': 'Mozilla/5.0'}), timeout=240) as response:
        remote_sha = hashlib.sha256(response.read()).hexdigest()
    assert remote_sha == expected, 'Remote upload integrity failure: OTA NOT updated'
    print('Remote SHA-256 OK', name, flush=True)

manifest.update({
    'versionCode': args.code,
    'versionName': args.version,
    'note': args.note.read_text(),
    'releasedAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
    'size': len(apk_data),
    'sha256': apk_sha,
    'sourceSha256': source_sha,
    'certificateSha256': expected_cert,
})
payload = json.dumps(manifest, ensure_ascii=False, indent=2).encode()
request('/files/write?file=' + manifest_query, payload, method='POST', raw=True)
(args.evidence / 'ota-manifest-after.json').write_bytes(payload)
print('OTA UPDATED', args.code, args.version, len(apk_data), apk_sha, flush=True)
