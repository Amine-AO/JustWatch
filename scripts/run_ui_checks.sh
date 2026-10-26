#!/usr/bin/env bash
# Keep the emulator awake while the test APK is compiled and installed.
set -u
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 1800000
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard

gradle :linkfixture:assembleDebug --stacktrace || exit $?
adb install -r linkfixture/build/outputs/apk/debug/linkfixture-debug.apk || exit $?
adb shell pm set-app-links-allowed --user 0 --package com.justwatch.links.fixture false
# Quickstep can ANR during cold boot and leave a system dialog covering every app.
# Stop only the emulator launcher, never the app under test.
adb shell am force-stop com.android.launcher3
gradle :app:connectedDebugAndroidTest --stacktrace
test_status=$?

# Preserve visual evidence even if a check fails.
mkdir -p ui-screenshots
adb pull /data/local/tmp/justwatch-ui/. ui-screenshots/ || true
adb exec-out screencap -p > ui-screenshots/emulator-final.png
adb shell dumpsys window > ui-screenshots/window-state.txt
exit "$test_status"
