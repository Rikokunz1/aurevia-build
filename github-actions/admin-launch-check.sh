#!/usr/bin/env bash
# Proves the Aurevia Admin APK installs and actually opens on an emulator.
# Kept as a file because ReactiveCircus/android-emulator-runner executes every
# line of `script:` in its own shell, so variables do not survive between lines.
set -euxo pipefail

APK=$(find artifacts -name 'Aurevia-Admin-v*.apk' | head -1)
test -n "$APK"
ls -l "$APK"
mkdir -p reports

adb install --no-streaming "$APK" 2>&1 | tee reports/install.txt
adb shell pm list packages | grep -F app.aurevia.admin | tee reports/package.txt

adb shell am start -W -n app.aurevia.admin/app.aurevia.admin.MainActivity 2>&1 | tee reports/launch.txt
# The launch must be reported as ok, not just attempted.
grep -Eq 'Status: ok' reports/launch.txt

sleep 15
adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | tee reports/focus.txt
grep -F 'app.aurevia.admin' reports/focus.txt

# Recording the actual pixels is proof the shell painted something.
adb exec-out screencap -p > reports/screen.png
test -s reports/screen.png

# Stronger proof: the bundled HTML executed. The panel logs PANEL_READY once its
# script has actually run, and WebChromeClient forwards console output to logcat.
adb logcat -d | grep -F 'PANEL_READY' | tail -5 | tee reports/panel-ready.txt
grep -Eq 'PANEL_READY [0-9]+\.[0-9]+\.[0-9]+ nav=[1-9]' reports/panel-ready.txt

adb shell pidof app.aurevia.admin | tee reports/pid.txt
test -s reports/pid.txt

if adb logcat -d -v brief | grep -E 'FATAL EXCEPTION|ActivityNotFoundException|Process: app.aurevia.admin' > reports/crash.txt; then
  echo 'CRASH_DETECTED'
  cat reports/crash.txt
  exit 1
fi
echo 'LAUNCH_PROOF_OK'
