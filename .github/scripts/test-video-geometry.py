#!/usr/bin/env python3
"""Compile and run the pure Kotlin regression tests without Android test tasks."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import subprocess
import sys
import urllib.request

source = Path(sys.argv[1]).resolve()
cache = Path('/tmp/aurevia-geometry-compiler')
cache.mkdir(parents=True, exist_ok=True)
artifacts = {
    'compiler.jar': 'org/jetbrains/kotlin/kotlin-compiler-embeddable/1.9.25/kotlin-compiler-embeddable-1.9.25.jar',
    'stdlib.jar': 'org/jetbrains/kotlin/kotlin-stdlib/1.9.25/kotlin-stdlib-1.9.25.jar',
    'reflect.jar': 'org/jetbrains/kotlin/kotlin-reflect/1.6.10/kotlin-reflect-1.6.10.jar',
    'script.jar': 'org/jetbrains/kotlin/kotlin-script-runtime/1.9.25/kotlin-script-runtime-1.9.25.jar',
    'annotations.jar': 'org/jetbrains/annotations/13.0/annotations-13.0.jar',
    'junit.jar': 'junit/junit/4.13.2/junit-4.13.2.jar',
    'hamcrest.jar': 'org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar',
    'trove.jar': 'org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar',
}


def download(item):
    name, relative = item
    destination = cache / name
    if not destination.exists():
        with urllib.request.urlopen('https://repo.maven.apache.org/maven2/' + relative, timeout=120) as response:
            destination.write_bytes(response.read())


with ThreadPoolExecutor(max_workers=5) as pool:
    list(pool.map(download, artifacts.items()))
classpath = ':'.join(str(cache / name) for name in artifacts)
output = cache / 'classes'
output.mkdir(exist_ok=True)
files = [
    source / 'app/src/main/kotlin/com/metrolist/music/aurevia/YtVideoTransform.kt',
    source / 'app/src/test/kotlin/com/metrolist/music/aurevia/YtVideoTransformTest.kt',
    source / 'app/src/main/kotlin/com/metrolist/music/playback/AureviaPlaybackPolicy.kt',
    source / 'app/src/test/kotlin/com/metrolist/music/playback/AureviaPlaybackPolicyTest.kt',
    source / 'app/src/main/kotlin/com/metrolist/music/aurevia/LivePlaybackPolicy.kt',
    source / 'app/src/test/kotlin/com/metrolist/music/aurevia/LivePlaybackPolicyTest.kt',
    source / 'app/src/main/kotlin/com/metrolist/music/aurevia/VideoStreamPolicy.kt',
    source / 'app/src/test/kotlin/com/metrolist/music/aurevia/VideoStreamPolicyTest.kt',
    source / 'app/src/test/kotlin/com/metrolist/music/aurevia/VideoHttpTransportTest.kt',
    source / 'app/src/main/kotlin/com/metrolist/music/aurevia/IosScrollPhysics.kt',
    source / 'app/src/test/kotlin/com/metrolist/music/aurevia/IosScrollPhysicsTest.kt',
    source / 'app/src/main/kotlin/com/metrolist/music/aurevia/VyoraLayoutPolicy.kt',
    source / 'app/src/test/kotlin/com/metrolist/music/aurevia/VyoraLayoutPolicyTest.kt',
    source / 'app/src/main/kotlin/com/metrolist/music/aurevia/AureviaInterfacePolicy.kt',
    source / 'app/src/test/kotlin/com/metrolist/music/aurevia/AureviaInterfacePolicyTest.kt',
    source / 'app/src/main/kotlin/com/metrolist/music/vyora/glass/floatingtabbar/FoldGeometry.kt',
    source / 'app/src/test/kotlin/com/metrolist/music/aurevia/AureviaFoldGeometryTest.kt',
]
assert all(path.is_file() for path in files)
subprocess.run([
    'java', '-cp', classpath, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
    '-no-stdlib', '-no-reflect', '-jvm-target', '1.8',
    '-classpath', classpath, '-d', str(output), *map(str, files),
], check=True)
result = subprocess.run([
    'java', '-cp', str(output) + ':' + classpath, 'org.junit.runner.JUnitCore',
    'com.metrolist.music.aurevia.YtVideoTransformTest',
    'com.metrolist.music.playback.AureviaPlaybackPolicyTest',
    'com.metrolist.music.aurevia.LivePlaybackPolicyTest',
    'com.metrolist.music.aurevia.VideoStreamPolicyTest',
    'com.metrolist.music.aurevia.VideoHttpTransportTest',
    'com.metrolist.music.aurevia.IosScrollPhysicsTest',
    'com.metrolist.music.aurevia.VyoraLayoutPolicyTest',
    'com.metrolist.music.aurevia.AureviaInterfacePolicyTest',
    'com.metrolist.music.aurevia.AureviaFoldGeometryTest',
], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
print(result.stdout, flush=True)
Path('/tmp/video-geometry-tests.txt').write_text(result.stdout)
sys.exit(result.returncode)
