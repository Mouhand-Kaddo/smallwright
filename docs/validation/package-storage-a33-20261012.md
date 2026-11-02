# Own-package storage and cache accounting on A33

Physical A33 storage accounting **passed** through Android's real own-package
StorageStatsManager API. Separate controller/worker snapshots after conformance
sum to75,368,448B=71.876953125MiB. Each new inert2MiB cache file increased cache
and data by exactly2MiB; app+data total increased2MiB rather than4MiB.
No credential stores or private file contents were read/enumerated. No new model
request, generated build or installation occurred during storage checks.

This closes a bounded measurement checkpoint, **not** #27, total-device retention,
low-storage/eviction, longer thermal, exact-memory or full-suite acceptance.
The fresh live repair separately passed in
[network repair evidence](live-repair-network-a33-20261012.md).

## API, permissions, scope and targets

[Android16 StorageStatsManager source](https://android.googlesource.com/platform/frameworks/base/+/android-16.0.0_r1/core/java/android/app/usage/StorageStatsManager.java)
permits queryStatsForPackage for the caller's own package without usage access.
Other packages require usage access. Each instrumentation runs in its target's
UID, checks target/PackageManager UID==Process.myUid, and queries only its own
package with Process.myUserHandle and StorageManager.getUuidForPath(dataDir).
No new manifest permission or user grant. Worker still has no Internet permission.

[StorageStats](https://developer.android.com/reference/android/app/usage/StorageStats)
appBytes includes installed APK/code/native extraction; dataBytes includes cache,
code cache and applicable own external storage on the same volume. cacheBytes
is already included in dataBytes. Sum app+data; do not add cache twice.
Query timestamps bracket synchronous API calls. Aggregate stats can lag writes;
separate UID snapshots are not atomic. No proof of page/byte-exact instant
whole-device allocation or permission to inspect other packages is implied.

Includes all retained history of each main builder package, not just the new
resource fixture. Excludes separate-UID installed generated apps/test packages,
host SDK/toolchains, other volumes/exports and OS bookkeeping outside those
packages. The small reservation directories and retained4MiB cache fixtures are
part of package data. No automatic pruning/deletion or production storage policy.

At03:45:23UTC, before any longer/full-suite evaluation, froze a **256MiB
(268,435,456B) provisional combined main-builder app+data target**. Measured
spike about72MiB leaves about184MiB headroom for the planned120-build case and
later bounded suite. This is a defensible working acceptance target for the
stated scope, not a measured all-app/device footprint or enforced storage quota.
Do not relax after failure. frozen-resource-targets.json retains the rationale,
exclusions and precise pre-run targets.

The planned fresh long-storage-20261012 case will retain unchanged10s first/5s
warm and1GiB reported high-water counter target, MODERATE2 stop,120 real native
builds,600s sequence window,900s worker observer,512 process entries/32768 samples.
The new case/routes/observer/helper implementation is **not yet present or run**;
these are frozen requirements, not completed tools or a long-run pass. Approximate
RSS remains distinct from exact simultaneous/page-exact physical memory.

## Physical RED→GREEN and independent allocation

Preflight03:00:57UTC identified Samsung Galaxy A33 SM-A336E, Android16/API36,
ARM64,qemu0, authorized; fresh03:34 preflight preceded later phone mutations.
No screen capture or UI was needed for storage instrumentation. Private serial
and raw evidence stay under ignored toolchains/alpha-package-storage-20261012/.

| Case | Actual result | Evidence |
| --- | --- | --- |
| Initial own-stats RED03:09:58UTC | Expected missing implementation READ_OWN_STATS/UnsupportedOperationException | red-worker-01.txt/handle |
| Initial own-stats GREEN03:15UTC | PASS independent installed APK size, own UID and app+data/cache invariant for both packages | green-worker/controller-01.txt/handles |
| Invalid worker ID | Expected GUARD rejection | invalid-worker-01.txt/handle |
| Cache conformance RED03:41:25UTC | Expected CACHE_DELTA/UnsupportedOperationException | cache-red-worker-01.txt/handle |
| Cache conformance GREEN03:44:00/07UTC | PASS both UID cache/data increments and unchanged code size | cache-green-worker/controller-01.txt/handles |
| Duplicate worker observation ID | Expected GUARD before allocating another file | cache-duplicate-worker-01.txt/handle |
| Invalid controller ID | Expected GUARD | cache-invalid-controller-01.txt/handle |
| Final03:46:50UTC | All installed/source/helper pins match; exact new cache fixtures retained, scratch absent, all original handles terminal/PIDs absent, own harness processes absent; generated live v2 and XML v4 installed bytes unchanged | cache-final-verification.json |

Cache conformance writes one newly created2MiB random inert file per UID, fsyncs,
and independently lstat-checks regular-file type, logical size and st_blocks×512.
Metadata is rechecked during polling; no file content is read/exported. Baseline
JSON is copied before nesting to avoid cycles. Polling allows15s between completed
synchronous queries, with250ms intervals if lagged; blocked API calls are not
forcibly cancelled. Actual conformance matched on the **first** after-query for
both packages, with exactly2,097,152B allocated/logical size and2,097,152B cache,
data and total increases. No lagged-query success or eviction is claimed.

Observed final main-package snapshots (bytes):

| Package | app | data (includes cache) | cache subset | app+data total |
| --- | --- | --- | --- | --- |
| controller | 9166336 | 33837056 | 2207744 | 43003392 |
| worker | 30243328 | 2121728 | 2097152 | 32365056 |

Initial03:15 snapshots totaled70,432,768B=67.169921875MiB, before later live-test
history and4MiB cache files. The conformance contract requires aggregate growth
at least equal to the new allocation. Exact observed increments support this
case; concurrent package writes could otherwise contribute. This does not prove
precise attribution, long-term retention or spontaneous cache eviction.

## Code, quality, artifacts and handoff

Shared helper is included **only** in androidTest source sets and formatting
scope; no production module/framework or permission change. Controller/worker
routes use the same helper and report safe stage/class failures. Temporary RED
stubs were replaced by the real API/file operations; no placeholder remains.
Two focused read-only efficient subagent reviews found no concrete issues;
parent verified source and actual physical observations. Reviews were static,
not substitutes for runtime/API evidence.

Quality/assembly commands use existing project-local JDK/SDK, capped one-worker
Gradle, offline, no global configuration or downloads. Exact API source retrieval
is retained with timestamp in primary-source.json. Source/helper hashes in
cache-green-source-pins.json were checked at final verification.

```sh
source scripts/env.sh
./gradlew spotlessApply spotlessCheck -PkernelResourceTests=true -PchildPolicyTests=true :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest :controller:lintDebug :worker:lintDebug --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-package-storage-20261012/install_tests.py --serial "$PHONE_SERIAL" --label cache-green --baseline toolchains/alpha-package-storage-20261012/cache-red-installed.json
python3 toolchains/alpha-package-storage-20261012/run_storage.py --serial "$PHONE_SERIAL" --package worker --label cache-green-worker-01 --expect pass --storage-phase cacheDelta
python3 toolchains/alpha-package-storage-20261012/run_storage.py --serial "$PHONE_SERIAL" --package controller --label cache-green-controller-01 --expect pass --storage-phase cacheDelta
python3 toolchains/alpha-package-storage-20261012/verify_cache_final.py --serial "$PHONE_SERIAL"
```

These document completed one-shot observations; do not repeat labels/IDs or
overwrite failures. Initial GREEN quality passed1m37s/167tasks30executed137cached.
Cache RED build passed38s/128tasks11executed117cached; final GREEN quality passed
1m3s/167tasks17executed150cached. Zero lint errors/controller77/worker2 warnings;
final lint analysis cached. No new JVM suite claimed for instrumentation-only
changes. Both opt-in native probe flags retained; default test APK not installed.

Only test APKs were updated in place after exact four-package baseline and
public certificate/package checks. Main harness unchanged; all data preserved.

| Package / retained host APK | Installed SHA256 |
| --- | --- |
| controller: toolchains/alpha-test-handoff-20261012/fixed-controller-debug.apk | 45adb0b429c5660c822e1b115fb68161131cbda0bb8f0acaa3e02cded41e658b |
| worker: toolchains/alpha-child-policy-20261012/production-worker-debug.apk | 0fc753e43a7d3c4b1ca259ef9f320528bc16320cd74754f8396b4cb6378f39a7 |
| controller.test: toolchains/alpha-package-storage-20261012/cache-green-controller-debug-androidTest.apk | 70514591a8ada47cb72e96fd8c7de23a6f3276298d7322c09ea16f782e9f6e02 |
| worker.test: toolchains/alpha-package-storage-20261012/cache-green-worker-debug-androidTest.apk | c6a88321835499404978de7da65b015d5c541f5a97d85c432971e642b03638ce |

Public harness certificate19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25.
No new generated APK path for this storage checkpoint. Prior fresh live v2
9885c2cef4aa8b5b44e1b7b43ad9b57f3ee3c447f96cc8632b7f651e9e9578f7
and XML v4 283ee00a4796164671c58baf9b5667b00e76ef7f58dac8a6d71ec00f65df7800
installed hashes stayed unchanged; no new count/source/UI measurement at final.

Exact changed tracked paths: build.gradle.kts, controller/build.gradle.kts,
worker/build.gradle.kts, test-support/java/dev/smallwright/testing/OwnPackageStorage.java,
controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java,
worker/src/androidTest/java/dev/smallwright/worker/ConfinementInstrumentation.java,
docs/validation/package-storage-a33-20261012.md, docs/alpha-acceptance.md,
docs/STATUS.md. Controller Gradle formatting normalized existing text by Spotless.
User skill/research files and STATUS tooling tail remain separate/unstaged.

Next: implement and run the frozen longer case with real worker/compiler/receipt
and all retained-artifact checks, no model or generated installation required.
Keep #27 open; carry targets/exclusions into full-suite evaluation and preserve
actual low-storage/OS eviction/cross-device/whole-alpha/licensing gates.
