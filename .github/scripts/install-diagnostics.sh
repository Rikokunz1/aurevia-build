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
exit "$FAILED"
