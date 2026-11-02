# Larger native build resources — A33, 2026-10-11

Six actual native builds of a 53400-byte synthetic Java source passed
on the physical Galaxy A33, with exact source/version/signature/receipt checks
and same-signer restoration after restart. First Build was 6660ms; warm builds
were 2990/2511/2363/2173/2046ms. The existing 10s first/5s warm caps were retained.
This is a bounded profiled workload, not a full alpha/performance acceptance pass.

Issue: #14.
Base: local `4de44ff`. Production code/dependencies unchanged.
Raw records remain ignored under `toolchains/alpha-build-resources-20261011/`.

## Contract and exact runtime

Approved A3/E1 local native Build/worker/signature/receipt seams, extended to six
consecutive real UI builds in a fresh one-shot isolated project. Synthetic valid
Java adds 1000 small integer methods to the default native source, below the
existing 64KiB controller bound. Actual New project/editor/Build/visible verified
Install controls are exercised; Install is never clicked. All source/tool/compiler/
APK work stays on phone through actual authenticated separate-UID Binder.
No model request, credential inspection, generated app installation/export/pull,
data clearing, fake compiler output or production hook occurs.

Fresh preflight identified physical Samsung Galaxy A33 5G SM-A336E,
Android 16/API 36, ARM64, s5e8825, qemu0. All four installed harness hashes matched
the preceding checkpoint; processes absent and keyguard false/Awake were recorded
before the first run. Test APKs were package/public-certificate/byte verified,
installed through authorized USB debug `-r` preserving data, then installed-byte
verified. This harness route is separate from production generated-app consent.

Worker-test instrumentation profiles its actual own process. v2 lists numeric
`/proc` process directories and reads ownership metadata only; status is read
after same-UID ownership, then PPid must equal the actual worker before collecting
child memory. No other UID's private files, command lines, full statuses, source,
stack traces or signing bytes are exported. Only numeric memory/identity/clocks,
counts and bounds are reported. Direct children only; no descendant-tree proof.

Memory observations run about every 20ms, bounded to 120s/4096 batches. The
process-history threshold is 32 at batch start, permitting at most 48 retained
records if a final batch adds 16 new children; each batch permits at most 16
child candidates.
The sampler includes instrumentation/allocation/I/O overhead. Per-batch RSS sums
are sequential reads and can double-count shared pages. Observed process VmHWM
is a kernel lifetime high-water mark up to that reading; a child's final lifetime
peak may occur after its last sample. Controller VmHWM is read after each build.
Root process peaks, child peaks and batch RSS must not be summed into an exact
simultaneous whole-pipeline peak. Short-lived children can be missed altogether.

Host gates occur before Build1 and after Build6, outside the click-to-visible-ready
clocks. Each build clocks the actual Build click to fully visible/enabled verified
Install readiness, including UI polling/reveal, while the worker observer runs.
Independent package/version/policy/signature/receipt/signer/source checks follow.
Each build normally advances source and package version; same content/signer and
unchanged worker PID/start identity are independently verified across the run.

Observer RESULT or INVALID unblocks the final controller gate. The host waits
for the original controller terminal handle before explicit worker release.
An INVALID result or timing-cap miss fails after orderly release; no unconditional
PASS is printed. Other observation timeouts leave original handles for inspection,
with no automatic retry, data reset or target kill. Invalid hold/over-cap paths
have synthetic host-test evidence, not forced physical fault evidence.

## Preserved first attempt and review fixes

v1 six builds passed 6222/2956/2471/2359/2204/2086ms, exact artifact/receipt/signer,
ready reopen, guard and four neighbors. Its 913 worker samples showed max RSS
163860KiB/VmHWM164256KiB but 20136 child-discovery failures: this Android kernel
omits `/proc/PID/task/TID/children`. No native-child coverage is claimed for v1.
`correlation-01.json`, raw phone results, original source pins/APK identities and
`physically-run-profile-v1.py` remain unchanged. Original host handles were
terminal before stopping only the four own harness packages; data retained.

Review also found that the initial host runner waited only for a successful
worker RESULT while both instrumentations could remain gated on INVALID. It
also recorded, but did not enforce, latency-cap compliance. Meaningful synthetic
host orchestration tests reproduced both bugs on the original runner (two failed,
one valid-flow pass), then all three passed after corrections. The first test
setup itself had incorrect synthetic dumpsys formatting; that error is retained
separately and is not the regression result. These tests launch no ADB or APK and
provide no physical/compiler evidence.

v2 changed only test instrumentation/runner: direct-child discovery uses numeric
process ownership metadata; controller gets a fresh one-shot namespace. Corrected
host protocol and caps were used on the fresh physical run. Independent review
found no further concrete source-level boundary/lifecycle/cap defect; parent
checked the source and raw results before accepting it.

## Final physical observations

Original final host run started 2026-10-11T19:11:42.737813+00:00 and exited 0. Both controller
and observer returned their named PASS and instrumentation code -1; the observer
was released only after the original controller was terminal. Controller UID
10319 differs from worker UID 10318; worker process/start
identity matched across the entire run. Collector interval was
21.671238s, including startup/gates/verification.

| Build/source/version | Click-to-visible-ready ms | APK bytes | Project logical file bytes | Allocated bytes including dirs | Files / dirs | Controller VmHWM KiB | Thermal before / after |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 6660 | 33244 | 94932 | 194560 | 8 / 20 | 182156 | 0 / 0 |
| 2 | 2990 | 33243 | 181697 | 321536 | 11 / 28 | 186536 | 0 / 0 |
| 3 | 2511 | 33244 | 268463 | 448512 | 14 / 36 | 186536 | 0 / 0 |
| 4 | 2363 | 33244 | 355229 | 575488 | 17 / 44 | 186536 | 0 / 0 |
| 5 | 2173 | 33244 | 441995 | 702464 | 20 / 52 | 186536 | 0 / 0 |
| 6 | 2046 | 33244 | 528761 | 829440 | 23 / 60 | 186536 | 0 / 0 |

Pristine owned-project baseline: {'logicalFileBytes': 7816, 'allocatedBytesIncludingDirectories': 59904, 'files': 4, 'directories': 11}. Final project
allocated 829440bytes (810KiB), logical files 528761bytes; each warm iteration added
86765–86766 logical bytes and 126976 allocated bytes. Counts grew by 3 files/8 directories
per warm iteration. `lstat` metadata includes all files/directories within this
new project, including revisions/APKs and its runtime-created signing files;
no signing bytes were read. Allocation is reported filesystem block accounting,
not exclusive physical-media usage. Shared preferences, harness APK/toolchain,
worker scratch/other projects and installed generated-app data are outside this
project storage measurement. It does not establish total-phone or scratch peak.
Snapshots/APKs are retained, not pruned; long-term retention growth remains open.

Observer collected 866 batches, observed nine same-UID direct child
processes and the root worker. Maximum sequential worker+seen-child RSS batch:
217704KiB. Root worker observed VmHWM:219380KiB.
Maximum sample-call duration:27.511808ms; maximum
inter-sample end-to-next-start gap:24.209654ms.
There were 2 discovery-read failures and
0 missing discovered-child status samples. Zero
missing status reads does not prove every child was discovered. Public child
process identities/first/last clocks remain in the ignored numeric raw result.

| Observed child | Samples | Maximum observed RSS KiB | Maximum observed VmHWM KiB |
| --- | --- | --- | --- |
| 1 | 4 | 9576 | 9656 |
| 2 | 1 | 924 | 996 |
| 3 | 2 | 4248 | 4248 |
| 4 | 1 | 1316 | 1316 |
| 5 | 2 | 7460 | 7460 |
| 6 | 2 | 5524 | 6944 |
| 7 | 2 | 9304 | 9628 |
| 8 | 1 | 764 | 1000 |
| 9 | 3 | 9540 | 9628 |

These results do not prove an exact whole-pipeline peak, OS-cache-cold execution,
low-overhead production memory, memory ceilings or broad resource acceptance.
Thermal status 0 at each boundary is not temperature, continuous thermal coverage
or a long-duration sustained-load pass. No phone-wide settings/network/charging
state was changed. Latency caps were not raised; warm observations were within 5s.
Whole-pipeline memory, total storage/retention and sustained thermal budgets
remain unset pending adequate measurements; this checkpoint does not close them.

## Exact source and APKs

Root:`3544edd230d14ba5ba2c0b9df627cf57`; project:`appd9a21ea90d9e4436985d894e0fefa494`.
Package:`dev.smallwright.generated.appd9a21ea90d9e4436985d894e0fefa494`.
Source SHA-256:`4b845a13cf46c90a85def0aed65e225566cef8dd0eb6f8348b081846c0c3d7aa`.
Public signer:`c8f8ee0080c71e7330484a4892c23931657d7bd2df6591b4243bd82e5f6b8ae3`.

On-phone artifact directory:

`/data/user/0/dev.smallwright.controller/files/binding-ui-probe-3544edd230d14ba5ba2c0b9df627cf57/projects/appd9a21ea90d9e4436985d894e0fefa494/artifacts/`

| On-phone APK | SHA-256 |
| --- | --- |
| `app-v1.apk` | `875cb48bb6328df0fdde4942edeba8ad32271647889c38ba5add4296bb759353` |
| `app-v2.apk` | `f6b5c9178363795b893a3e4531780b9b4b368e1e213fa06be219ad2130918aea` |
| `app-v3.apk` | `adf382efd6eb855f01dc146032370456889a609cc9b3adb6e17ae89807d00f83` |
| `app-v4.apk` | `e2e4f95d59daadc857a01cce6d239945db1dc1530f22fb7e9d1d0e1280cf602e` |
| `app-v5.apk` | `7a77f95b32be7acf1f7bccbd81486324f2f83fa9c13946bbc55ba07677f4c1bb` |
| `app-v6.apk` | `80773c9faad0de02fa2236700aab7f94acc55bb17804f4ae45ca103bd3fbf462` |

Native manifest/package/version/policy, real ApkVerifier signature, existing
project signer, exact source revision/hash and immutable receipt were verified
on phone for each build. No generated bytes were copied to the host. Exact
source6/version6 APK/receipt/signer restored in a fresh controller process without
rebuilding. Expected duplicate measurement guard returned code 0; ECJ Stop ready,
prior measurement ready, screen-lock ready and completion-ordering neighbors
passed (host 0/instrumentation -1). Completion uses verification-only bytes and
is not another compiler or generated-function test. Generated functional launch,
installation/update/persistence were not attempted here.

Final installed test harness artifacts:

| Host APK path | SHA-256 | Bytes |
| --- | --- | --- |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `fb9b585e3e6fb096d561e180ed37cde469be8db64320eb9d2f0fd89a84df3622` | 1025578 |
| `worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk` | `5243fb4e0d993bf25c3d98064005322ba020a30f324de6eee888140ec53e1434` | 147666 |

Production harness APKs remain unchanged: `controller/build/outputs/apk/debug/controller-debug.apk`
SHA-256 `abf873d81bd6c9556eba40ded679db91fda7277d0ce719a7bd50425f7dd6c839`;
`worker/build/outputs/apk/debug/worker-debug.apk`
SHA-256 `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850`.
All four public harness certificate SHA-256 values:
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
Existing credential stores were never inspected; normal app fixture signing APIs
reported only public fingerprints. Sources/artifacts/signing identities survive.

## Commands, quality and handoff

```bash
source scripts/env.sh
./gradlew spotlessApply :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest qualityCheck :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 scripts/test_profile_phone_builds.py
python3 scripts/profile_phone_builds.py --serial "$PHONE_SERIAL" --label 02
```

Label 02/preparation is one-shot and already consumed; never replay it. Recovery
route is `-e buildResourcesOnly ready`, runner
`dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation`.
Original serial-scoped `run_ready_and_neighbors_v2.py` results are retained in
`recovery-and-neighbor-results-02.json` and each named `*-02.txt`. Five named
passes and one expected guard rejection were terminal. A fresh attempt requires
an explicit fresh namespace/rebuilt artifact, not clearing data or retrying a
previous handle. Initial host compile failure (`Files.readString` unavailable
in this Android SDK surface) was preserved, corrected to supported readAllBytes/
ASCII, and rebuilt successfully before either physical run.

Final offline quality passed in 36s: 177 tasks, 11 executed/166 up-to-date.
JVM test tasks were cached, not freshly executed. Fresh Android-test lint
analysis, Spotless, compiler-input verification and both test APK assemblies
passed; lint report tasks cached with 0 errors and 72/2/0 controller/worker/protocol
warnings. All three host protocol tests passed freshly. All seven existing
frozen alpha ledger rows validated unchanged; these synthetic local profiles
add no alpha denominator entries.

Final tested Java/host-source pins and all four installed APK byte hashes matched
the snapshots after quality. At 2026-10-11T19:17:27.818985+00:00, original physical/
observer/controller/recovery host PIDs were absent. Worker `cache/active-build`
was independently absent before cleanup. Only this run's four harness packages
were force-stopped; data/projects/signers/failed records/receipts retained. Phone
policy/power showed keyguard false and Awake. One initial scratch check failed
because an ADB `sh -c` argument was incorrectly quoted; direct `run-as ... test`
verified the actual absence before cleanup. A first existing-label invocation
rejected because the own worker was still alive; after cleanup, the same label 02
was correctly rejected at the no-overwrite guard before any instrumentation.
Both guard failures are retained separately; neither is a compiler/runtime pass.

Final tested source pins (exact corresponding APKs listed above):

| Path | SHA-256 |
| --- | --- |
| `controller/src/androidTest/java/dev/smallwright/controller/BuildResourcesProbe.java` | `5b457d46a035b4543b0a420d1505ef0b204541b988966c19d1ad14343940d57b` |
| `controller/src/androidTest/java/dev/smallwright/controller/CompilerMeasurementProbe.java` | `4b60e3618ee3d62679f3cea44d015189029460d2522a6ccca32da296774c69a9` |
| `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java` | `2dc00b1a45097341bf106506b09db44546fbaf26593ae32ca28a8dcb666e7215` |
| `worker/src/androidTest/java/dev/smallwright/worker/BuildResourceObserver.java` | `225c6b13f8bb1e0b3ee604d521bc0348eda63427b4cd580ea06cabee02e247bc` |
| `worker/src/androidTest/java/dev/smallwright/worker/ConfinementInstrumentation.java` | `4e02820efa0e715451379da53810d0ecd58a7e232af95dadf4992b120c3cc897` |
| `scripts/profile_phone_builds.py` | `9f4e55a04ff65dda3d859ae319bdcc153c0552cebbcfe8d5887e1758809f1165` |
| `scripts/test_profile_phone_builds.py` | `46e73481408392409c5f21565bbec402a851aa5a7cf96cf6211de0b23ac9a3b0` |


Exact changed paths: `controller/src/androidTest/java/dev/smallwright/controller/BuildResourcesProbe.java`,
`controller/src/androidTest/java/dev/smallwright/controller/CompilerMeasurementProbe.java`,
`controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`,
`worker/src/androidTest/java/dev/smallwright/worker/BuildResourceObserver.java`,
`worker/src/androidTest/java/dev/smallwright/worker/ConfinementInstrumentation.java`,
`scripts/profile_phone_builds.py`, `scripts/test_profile_phone_builds.py`,
`docs/STATUS.md`, `docs/alpha-acceptance.md`, and this validation document.
No frozen alpha ledger rows were added. Existing unrelated skill/tooling-tail
changes remain outside this checkpoint. Full alpha, live free-only repair,
actual ENOSPC, spontaneous OS eviction/provider cancellation/detached execution,
cross-device encrypted recovery and broader resource gates remain open. Live
repair stays deferred under the user's local-check instruction. Next local work:
a lower-overhead native-child/full-workspace measurement or toolchain closure;
do not treat this sampled spike as complete memory/thermal acceptance.
