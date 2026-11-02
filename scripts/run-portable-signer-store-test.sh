#!/usr/bin/env bash
set -euo pipefail

root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$root"
adb="${ANDROID_HOME:?Set ANDROID_HOME with scripts/env.sh}/platform-tools/adb"
serial=emulator-5554
wanted_api="${1:?Usage: run-portable-signer-store-test.sh 31|36}"
case "$wanted_api" in 31|36) ;; *) exit 2 ;; esac

if [ "$("$adb" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ] ||
   [ "$("$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')" != "$wanted_api" ] ||
   [ "$("$adb" -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')" != x86_64 ]; then
    echo 'Refusing to run: emulator-5554 is not the requested x86_64 emulator.' >&2
    exit 1
fi

"$adb" -s "$serial" install -r \
    controller/build/outputs/apk/debug/controller-debug.apk >/dev/null
"$adb" -s "$serial" install -r \
    controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk >/dev/null
output="$(timeout --signal=TERM --kill-after=5s 180s "$adb" -s "$serial" \
    shell am instrument -r -w -e portableSignerStoreOnly true \
    dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation \
    | tr -d '\r')"
printf '%s\n' "$output"
if [ "$(grep -c '^INSTRUMENTATION_CODE: -1$' <<< "$output")" != 1 ] ||
   ! grep -Fq 'Portable signer store: round trip, tamper, missing key, lineage passed' \
        <<< "$output"; then
    echo 'Portable signer store Android runtime probe failed.' >&2
    exit 1
fi
