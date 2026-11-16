#!/usr/bin/env bash
set -u
set -o pipefail
mkdir -p reports
APK=$(find inputs/candidate -name '*.apk' | sort | head -1)
BASELINE=$(find inputs/baseline -name '*.apk' | sort | head -1)
PKG=app.aurevia.music
exec > >(tee reports/console.txt) 2>&1
printf 'Candidate: %s\nBaseline: %s\n' "$APK" "$BASELINE"
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.fingerprint
adb shell getprop ro.product.cpu.abilist
adb logcat -c
FAILED=0

check_firebase_client() {
  local PHASE="$1"
  local PID
  PID=$(awk '{print $1}' "reports/${PHASE}-pid.txt" | head -1)
  if ! [[ "$PID" =~ ^[0-9]+$ ]]; then FAILED=1; return; fi
  if ! adb logcat -d -v threadtime --pid "$PID" > "reports/${PHASE}-app-logcat.txt"; then FAILED=1; return; fi
  if grep -q 'Invalid component registrar' "reports/${PHASE}-app-logcat.txt"; then
    echo "Firebase component discovery failed in $PHASE"
    FAILED=1
    return
  fi
  if ! grep -q 'AureviaPush.*Firebase Messaging client initialized' "reports/${PHASE}-app-logcat.txt"; then
    echo "Firebase Messaging component readiness not observed in $PHASE"
    FAILED=1
    return
  fi
  printf '%s: FirebaseMessaging component ready; no invalid registrar\n' "$PHASE" >> reports/firebase-client-check.txt
}

printf '\n=== Candidate fresh install ===\n'
timeout 120s adb install --no-streaming "$APK" 2>&1 | tee reports/fresh-install.txt
FRESH_RESULT=${PIPESTATUS[0]}
if [ "$FRESH_RESULT" -eq 0 ]; then
  adb shell dumpsys package "$PKG" > reports/fresh-package.txt
  timeout 90s adb shell am start -W -n "$PKG/com.metrolist.music.MainActivity" 2>&1 | tee reports/fresh-launch.txt
  sleep 12
  adb shell pidof "$PKG" | tee reports/fresh-pid.txt || FAILED=1
  check_firebase_client fresh
  adb shell dumpsys activity activities > reports/fresh-activities.txt
  adb exec-out screencap -p > reports/fresh-screen.png
else
  FAILED=1
fi
adb logcat -d -v threadtime > reports/fresh-logcat.txt
adb uninstall "$PKG" || true

printf '\n=== Baseline fresh install ===\n'
adb logcat -c
timeout 120s adb install --no-streaming "$BASELINE" 2>&1 | tee reports/baseline-install.txt
BASE_RESULT=${PIPESTATUS[0]}
if [ "$BASE_RESULT" -eq 0 ]; then
  adb shell dumpsys package "$PKG" > reports/baseline-package.txt
  timeout 90s adb shell am start -W -n "$PKG/com.metrolist.music.MainActivity" 2>&1 | tee reports/baseline-launch.txt
  sleep 5
  adb shell am force-stop "$PKG"
  printf '\n=== Upgrade baseline -> candidate (no uninstall) ===\n'
  timeout 120s adb install --no-streaming -r "$APK" 2>&1 | tee reports/upgrade-install.txt
  UPGRADE_RESULT=${PIPESTATUS[0]}
  if [ "$UPGRADE_RESULT" -ne 0 ]; then
    FAILED=1
  else
    adb shell dumpsys package "$PKG" > reports/upgrade-package.txt
    timeout 90s adb shell am start -W -n "$PKG/com.metrolist.music.MainActivity" 2>&1 | tee reports/upgrade-launch.txt
    sleep 10
    adb shell pidof "$PKG" | tee reports/upgrade-pid.txt || FAILED=1
    check_firebase_client upgrade
    adb exec-out screencap -p > reports/upgrade-screen.png
  fi
else
  FAILED=1
  UPGRADE_RESULT=99
fi
adb logcat -d -v threadtime > reports/upgrade-logcat.txt
printf '\nRESULT fresh=%s baseline=%s upgrade=%s\n' "$FRESH_RESULT" "$BASE_RESULT" "$UPGRADE_RESULT" | tee reports/result.txt

# Separate, disposable emulator scenario: update the SAME private197 installed by
# the owner to the corrected198. Uninstall is ONLY fixture setup on this CI VM;
# the actual baseline->candidate update below never uninstalls or clears data.
if [ -d inputs/installed-baseline ]; then
  INSTALLED=$(find inputs/installed-baseline -name '*.apk' | head -1)
  adb uninstall "$PKG" || true
  adb logcat -c
  timeout 120s adb install --no-streaming "$INSTALLED" 2>&1 | tee reports/installed-baseline-install.txt
  INSTALLED_BASE_RESULT=${PIPESTATUS[0]}
  INSTALLED_UPGRADE_RESULT=99
  if [ "$INSTALLED_BASE_RESULT" -eq 0 ]; then
    adb shell am start -W -n "$PKG/com.metrolist.music.MainActivity" > reports/installed-baseline-launch.txt 2>&1
    sleep 5
    adb shell dumpsys package "$PKG" > reports/installed-baseline-package.txt
    adb shell am force-stop "$PKG"
    timeout 120s adb install --no-streaming -r "$APK" 2>&1 | tee reports/installed-upgrade-install.txt
    INSTALLED_UPGRADE_RESULT=${PIPESTATUS[0]}
    if [ "$INSTALLED_UPGRADE_RESULT" -eq 0 ]; then
      adb shell dumpsys package "$PKG" > reports/installed-upgrade-package.txt
      adb shell am start -W -n "$PKG/com.metrolist.music.MainActivity" > reports/installed-upgrade-launch.txt 2>&1
      sleep 10
      adb shell pidof "$PKG" | tee reports/installed-upgrade-pid.txt || FAILED=1
      check_firebase_client installed-upgrade
      adb exec-out screencap -p > reports/installed-upgrade-screen.png
      python3 - <<'CHECK'
from pathlib import Path
import re
before=Path('reports/installed-baseline-package.txt').read_text()
after=Path('reports/installed-upgrade-package.txt').read_text()
assert re.search(r'versionCode=197\b',before), 'Unexpected phone baseline'
assert re.search(r'versionCode=198\b',after), 'Candidate version must increment for update-in-place'
def stamp(text):
    rows=re.findall(r'firstInstallTime=([^\r\n]+)',text)
    assert rows
    return rows
assert stamp(before)==stamp(after), 'Update changed the package first-install time'
Path('reports/installed-upgrade-data-preservation.txt').write_text('197->198 update-in-place: firstInstallTime unchanged; package retained; no uninstall/clear between baseline and candidate.\n')
CHECK
      [ "$?" -eq 0 ] || FAILED=1
    else FAILED=1; fi
  else FAILED=1; fi
  adb logcat -d -v threadtime > reports/installed-upgrade-logcat.txt
  printf 'INSTALLED_RESULT baseline=%s upgrade=%s\n' "$INSTALLED_BASE_RESULT" "$INSTALLED_UPGRADE_RESULT" | tee -a reports/result.txt
fi
exit "$FAILED"
