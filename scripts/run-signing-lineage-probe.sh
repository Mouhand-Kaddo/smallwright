#!/usr/bin/env bash
set -euo pipefail

root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$root"
adb="${ANDROID_HOME:?Set ANDROID_HOME to the project Android SDK}/platform-tools/adb"
serial=emulator-5554

if [ "$("$adb" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ] ||
   [ "$("$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')" != 36 ] ||
   [ "$("$adb" -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')" != x86_64 ]; then
    echo 'Refusing to run: emulator-5554 is not the disposable Android 16 x86_64 emulator.' >&2
    exit 1
fi

"$adb" -s "$serial" install -r \
    controller/build/outputs/apk/debug/controller-debug.apk >/dev/null
"$adb" -s "$serial" install -r \
    controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk >/dev/null
output="$(timeout --signal=TERM --kill-after=5s 90s "$adb" -s "$serial" \
    shell am instrument -r -w -e signingLineageOnly true \
    dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation \
    | tr -d '\r')"
printf '%s\n' "$output"
if [ "$(grep -c '^INSTRUMENTATION_CODE: -1$' <<< "$output")" != 1 ] ||
   ! grep -Fq 'Signing lineage: disposable Android Keystore signer created a restorable old-to-new proof' \
        <<< "$output"; then
    echo 'Disposable signing lineage probe failed.' >&2
    exit 1
fi
