#!/usr/bin/env bash
set -euo pipefail

# Uses the persistent task-local emulator. It never starts, stops, uninstalls, or clears apps.
source "$(dirname -- "$0")/env.sh"
adb="$ANDROID_HOME/platform-tools/adb"
"$adb" -e get-state | grep -qx device
"$adb" -e shell pm list packages dev.smallwright.controller | grep -qx package:dev.smallwright.controller
"$adb" -e shell pm list packages dev.smallwright.worker | grep -qx package:dev.smallwright.worker
"$adb" -e shell am start -n dev.smallwright.controller/.MainActivity
sleep 2
"$adb" -e shell pidof dev.smallwright.worker | grep -q '[0-9]'
"$adb" -e shell dumpsys package dev.smallwright.worker | grep -q 'versionCode=1 minSdk=31 targetSdk=36'
"$adb" -e shell dumpsys package dev.smallwright.generated.counter | grep -q 'installerPackageName=dev.smallwright.controller'
"$adb" -e shell dumpsys package dev.smallwright.generated.counter | grep -q 'versionCode='
"$adb" -e shell am start -n dev.smallwright.generated.counter/.MainActivity
sleep 1
"$adb" -e shell uiautomator dump /sdcard/smallwright-smoke.xml >/dev/null
"$adb" -e shell cat /sdcard/smallwright-smoke.xml | grep -q 'Updated: 1'
echo 'Existing emulator source-to-APK install/update evidence is intact.'
