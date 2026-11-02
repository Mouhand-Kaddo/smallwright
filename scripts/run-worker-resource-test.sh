#!/usr/bin/env bash
set -euo pipefail

root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$root"
adb="${ANDROID_HOME:?Set ANDROID_HOME to the project Android SDK}/platform-tools/adb"
serial=emulator-5554

./gradlew :worker:assembleDebug :worker:assembleDebugAndroidTest \
    --offline --no-daemon --max-workers=1 --console=plain

if [ "$("$adb" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ] ||
   [ "$("$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')" != 36 ] ||
   [ "$("$adb" -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')" != x86_64 ]; then
    echo 'Refusing to run: emulator-5554 is not the disposable Android 16 x86_64 emulator.' >&2
    exit 1
fi

"$adb" -s "$serial" install -r worker/build/outputs/apk/debug/worker-debug.apk >/dev/null
"$adb" -s "$serial" install -r \
    worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk >/dev/null
output="$("$adb" -s "$serial" shell am instrument -r -w -e mode malformedResource \
    dev.smallwright.worker.test/dev.smallwright.worker.ConfinementInstrumentation | tr -d '\r')"
printf '%s\n' "$output"
if [ "$(grep -c '^INSTRUMENTATION_CODE: -1$' <<< "$output")" != 1 ] ||
   ! grep -q 'Worker resource: packaged AAPT2 rejected malformed XML under worker UID' \
        <<< "$output"; then
    echo 'Worker malformed-resource instrumentation failed.' >&2
    exit 1
fi
