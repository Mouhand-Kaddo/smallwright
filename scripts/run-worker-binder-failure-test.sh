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

"$adb" -s "$serial" install -r worker/build/outputs/apk/debug/worker-debug.apk >/dev/null
"$adb" -s "$serial" install -r controller/build/outputs/apk/debug/controller-debug.apk >/dev/null
"$adb" -s "$serial" install -r \
    worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk >/dev/null
"$adb" -s "$serial" install -r \
    controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk >/dev/null

run_probe() {
    local expected="$1"
    shift
    local output
    output="$(timeout --signal=TERM --kill-after=5s 90s "$adb" -s "$serial" \
        shell am instrument -r -w "$@" | tr -d '\r')"
    printf '%s\n' "$output"
    if [ "$(grep -c '^INSTRUMENTATION_CODE: -1$' <<< "$output")" != 1 ] ||
       ! grep -Fq "$expected" <<< "$output"; then
        echo 'Worker Binder failure probe failed.' >&2
        exit 1
    fi
}

run_probe 'Binder failure: oversized snapshot rejected; both transferred pipes closed' \
    -e workerBinderFailureOnly true \
    dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
run_probe 'Worker scratch: failed Binder build left no active scratch' \
    -e mode cleanBuildScratch \
    dev.smallwright.worker.test/dev.smallwright.worker.ConfinementInstrumentation
run_probe 'Binder overlap: concurrent build and toolchain probe refused; busy build pipes closed' \
    -e workerBinderOverlapOnly true \
    dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
run_probe 'Worker scratch: failed Binder build left no active scratch' \
    -e mode cleanBuildScratch \
    dev.smallwright.worker.test/dev.smallwright.worker.ConfinementInstrumentation
run_probe 'Locality probe: controller UID' \
    -e localityProofOnly true \
    dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
