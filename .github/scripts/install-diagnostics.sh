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

printf '\n=== Candidate fresh install ===\n'
adb install --no-streaming "$APK" 2>&1 | tee reports/fresh-install.txt
FRESH_RESULT=${PIPESTATUS[0]}
if [ "$FRESH_RESULT" -eq 0 ]; then
  adb shell dumpsys package "$PKG" > reports/fresh-package.txt
  adb shell am start -W -n "$PKG/com.metrolist.music.MainActivity" 2>&1 | tee reports/fresh-launch.txt
  sleep 12
  adb shell pidof "$PKG" | tee reports/fresh-pid.txt || true
  adb shell dumpsys activity activities > reports/fresh-activities.txt
  adb exec-out screencap -p > reports/fresh-screen.png
else
  FAILED=1
fi
adb logcat -d -v threadtime > reports/fresh-logcat.txt
adb uninstall "$PKG" || true

printf '\n=== Baseline fresh install ===\n'
adb logcat -c
adb install --no-streaming "$BASELINE" 2>&1 | tee reports/baseline-install.txt
BASE_RESULT=${PIPESTATUS[0]}
if [ "$BASE_RESULT" -eq 0 ]; then
  adb shell dumpsys package "$PKG" > reports/baseline-package.txt
  adb shell am start -W -n "$PKG/com.metrolist.music.MainActivity" 2>&1 | tee reports/baseline-launch.txt
  sleep 5
  adb shell am force-stop "$PKG"
  printf '\n=== Upgrade baseline -> candidate (no uninstall) ===\n'
  adb install --no-streaming -r "$APK" 2>&1 | tee reports/upgrade-install.txt
  UPGRADE_RESULT=${PIPESTATUS[0]}
  if [ "$UPGRADE_RESULT" -ne 0 ]; then
    FAILED=1
  else
    adb shell dumpsys package "$PKG" > reports/upgrade-package.txt
    adb shell am start -W -n "$PKG/com.metrolist.music.MainActivity" 2>&1 | tee reports/upgrade-launch.txt
    sleep 10
    adb shell pidof "$PKG" | tee reports/upgrade-pid.txt || true
    adb exec-out screencap -p > reports/upgrade-screen.png
  fi
else
  FAILED=1
  UPGRADE_RESULT=99
fi
adb logcat -d -v threadtime > reports/upgrade-logcat.txt
printf '\nRESULT fresh=%s baseline=%s upgrade=%s\n' "$FRESH_RESULT" "$BASE_RESULT" "$UPGRADE_RESULT" | tee reports/result.txt
exit "$FAILED"
