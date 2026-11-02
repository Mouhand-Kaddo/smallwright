#!/usr/bin/env bash
set -euo pipefail

root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$root"
adb="${ANDROID_HOME:?Set ANDROID_HOME to the project Android SDK}/platform-tools/adb"
serial=emulator-5554

./gradlew :controller:assembleDebug :worker:assembleDebug :worker:assembleDebugAndroidTest \
    :security-probe:assembleDebug :security-probe:assembleDebugAndroidTest \
    -PincludeSecurityProbe=true --no-daemon --offline --max-workers=1

if [ "$("$adb" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ] ||
   [ "$("$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')" != 36 ] ||
   [ "$("$adb" -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')" != x86_64 ]; then
    echo 'Refusing to run: emulator-5554 is not the disposable Android 16 x86_64 emulator.' >&2
    exit 1
fi

for apk in \
    controller/build/outputs/apk/debug/controller-debug.apk \
    worker/build/outputs/apk/debug/worker-debug.apk \
    worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk \
    security-probe/build/outputs/apk/debug/security-probe-debug.apk \
    security-probe/build/outputs/apk/androidTest/debug/security-probe-debug-androidTest.apk; do
    if [ ! -f "$apk" ]; then
        echo "Missing built APK: $apk" >&2
        exit 1
    fi
    "$adb" -s "$serial" install -r "$apk" >/dev/null
done

controller_uid="$("$adb" -s "$serial" shell pm list packages -U dev.smallwright.controller \
    | tr -d '\r' | awk '$1 == "package:dev.smallwright.controller" {sub(/^uid:/, "", $2); print $2}')"
worker_uid="$("$adb" -s "$serial" shell pm list packages -U dev.smallwright.worker \
    | tr -d '\r' | awk '$1 == "package:dev.smallwright.worker" {sub(/^uid:/, "", $2); print $2}')"
if [ -z "$controller_uid" ] || [ -z "$worker_uid" ] || [ "$controller_uid" = "$worker_uid" ]; then
    echo 'Controller and worker do not have distinct installed UIDs.' >&2
    exit 1
fi
printf 'Installed controller UID %s; worker UID %s.\n' "$controller_uid" "$worker_uid"

sentinel_name="confinement-probe-$(date +%s)-$$"
sentinel_path="/data/user/0/dev.smallwright.controller/files/$sentinel_name"
"$adb" -s "$serial" shell run-as dev.smallwright.controller mkdir -p \
    /data/user/0/dev.smallwright.controller/files
printf 'dummy-controller-owned-data\n' | "$adb" -s "$serial" shell run-as \
    dev.smallwright.controller tee "$sentinel_path" >/dev/null
"$adb" -s "$serial" shell run-as dev.smallwright.controller test -f "$sentinel_path"

worker_output="$("$adb" -s "$serial" shell am instrument -r -w \
    -e sentinelPath "$sentinel_path" \
    dev.smallwright.worker.test/dev.smallwright.worker.ConfinementInstrumentation | tr -d '\r')"
printf '%s\n' "$worker_output"
if [ "$(grep -c '^INSTRUMENTATION_CODE: -1$' <<< "$worker_output")" != 1 ] ||
   ! grep -Eq 'Worker confinement: worker UID, controller file inaccessible (EACCES|EPERM|ENOENT \(existing file hidden\)), AF_INET socket denied (EACCES|EPERM)' \
        <<< "$worker_output"; then
    echo 'Worker confinement instrumentation failed.' >&2
    exit 1
fi

probe_output="$("$adb" -s "$serial" shell am instrument -r -w \
    dev.smallwright.securityprobe.test/dev.smallwright.securityprobe.HostileCallerInstrumentation | tr -d '\r')"
printf '%s\n' "$probe_output"
if [ "$(grep -c '^INSTRUMENTATION_CODE: -1$' <<< "$probe_output")" != 1 ] ||
   ! grep -q 'Separate-signer probe: signature permission denied;' <<< "$probe_output"; then
    echo 'Separate-signer caller instrumentation failed.' >&2
    exit 1
fi
