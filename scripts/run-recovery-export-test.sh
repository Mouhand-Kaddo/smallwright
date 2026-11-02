#!/usr/bin/env bash
set -euo pipefail

project_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$project_root"
adb_bin="${ANDROID_HOME:?Set ANDROID_HOME with scripts/env.sh}/platform-tools/adb"
serial=emulator-5554
wanted_api="${1:?Usage: run-recovery-export-test.sh 31|36}"
case "$wanted_api" in 31|36) ;; *) exit 2 ;; esac

if [ "$("$adb_bin" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ] ||
   [ "$("$adb_bin" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')" != "$wanted_api" ] ||
   [ "$("$adb_bin" -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')" != x86_64 ] ||
   [[ "$("$adb_bin" -s "$serial" shell getprop ro.boot.qemu.avd_name | tr -d '\r')" != smallwright-recovery-export-* ]]; then
    echo 'Refusing to run outside the requested dedicated recovery-export emulator.' >&2
    exit 1
fi
package=dev.smallwright.generated.counter
if [ -n "$("$adb_bin" -s "$serial" shell pm path "$package")" ] ||
   [[ "$("$adb_bin" -s "$serial" shell cmd package list packages -u --user 0 "$package" | tr -d '\r')" == *"package:$package"* ]]; then
    echo 'Refusing an emulator with installed or retained generated-app package state.' >&2
    exit 1
fi

"$adb_bin" -s "$serial" install -r \
    controller/build/outputs/apk/debug/controller-debug.apk >/dev/null
"$adb_bin" -s "$serial" install -r \
    controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk >/dev/null
output="$(timeout --signal=TERM --kill-after=5s 180s "$adb_bin" -s "$serial" \
    shell am instrument -r -w -e recoveryExportOnly true \
    dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation \
    | tr -d '\r')"
printf '%s\n' "$output"
if [ "$(rg -c '^INSTRUMENTATION_CODE: -1$' <<< "$output")" != 1 ] ||
   [[ "$output" != *'Recovery export: exact three-file snapshot and durable portable signer sealed twice'* ]]; then
    echo 'Recovery export Android runtime probe failed.' >&2
    exit 1
fi
