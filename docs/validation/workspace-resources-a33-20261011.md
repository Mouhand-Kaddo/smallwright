# Owned workspace and host resource observations — A33, 2026-10-11

Six real phone-built native Java/XML APKs passed exact source1..6/version1..6
package, manifest policy, signature, receipt and same-signer verification.
First Build: 5901ms; warm: 3064/2652/2414/2259/2238ms, within unchanged 10s/5s caps.
Exact source6/version6 readiness survived a fresh controller process.
This bounded local checkpoint does not establish private-alpha acceptance.

Issue: #15.
Base local commit: `90dc9dc`; tested source was uncommitted, pinned below.
Raw metadata/results remain ignored in `toolchains/alpha-workspace-resources-20261011/`.
Production controller/worker and worker-test APKs are unchanged. No dependencies downloaded.

## Contract and execution

Approved implementation-plan A3/E1 continuation using the real-phone-test flow:
fresh one-shot isolated project, synthetic 53400-byte Java source with 1000 tiny
methods, real editor/Build controls and actual authenticated separate-UID compiler.
Each build advances the committed source revision and package version; content and
signer remain fixed. Install readiness is observed; Install is never clicked.
No model requests, credential inspection, generated installation/export/pull or
existing-data deletion. All generated source, compilation and APK bytes remain
on phone. This is not a generated app functional launch or full alpha-suite run.

Fresh identity preflight: physical Galaxy A33 5G SM-A336E, Android16/API36,
ARM64 s5e8825, qemu0. All four prior installed hashes matched, own processes absent,
keyguard false/Awake. Only controller.test was rebuilt and installed through the
authorized data-preserving USB debug `-r` harness route, after exact package/public
certificate/hash verification, followed by installed-byte verification.
This harness update does not exercise production installation consent.

Original final run began `2026-10-11T19:37:13.818853+00:00`. Host exit0, named Workspace
resources PASS and instrumentation code-1. Controller UID10319, worker UID10318.
Their PID/startTicks stayed unchanged across before, repeated and after reads.
Only controller instrumentation ran; no worker-test profiler or production hook.

The debug Activity's files root is fresh `binding-ui-probe-58472302fd104086b576ef866b581540`.
Its cache and no-backup directories are nested there; its prefixed preferences
are delegated to the outer app context, outside that root. Metadata-only lstat
recursion counts regular-file logical sizes and filesystem st_blocks*512 for
files/directories, refusing symlinks/special files, bounded to depth16/4096 entries.
This covers the selected project plus this root's owned default scaffold/cache/
no-backup data. No private signing bytes are read. It excludes shared preferences,
system Keystore, unrelated existing projects/credentials, installed-app data,
ART/system caches and general phone storage. Allocation is filesystem accounting,
not exclusive physical-media use. The host's zero-byte gate acknowledgment file
is included in post-build counts; the after6 acknowledgment is created later.

## Preserved failed attempt and review

Attempt01 used workspace namespace v1. The host wrongly required ps exit0;
Android toybox ps returns exit1 with only its exact numeric header and empty stderr
when no direct children match. One actual Build result (5570ms) and one sampling
batch were captured, followed by host AssertionError. No instrumentation terminal
code was captured. The original host/controller PIDs were later confirmed absent,
worker scratch absent; only the own worker was stopped, preserving the fixture.
This attempt is a failed/incomplete measurement, not six-build evidence or a
successful terminal instrumentation result. Its disappearance mechanism is not proven.
See `host-profile-01.json`, `measure-01.txt`, `host-run-01.txt`,
`failed-selector-inspection-01.json`, `failed-final-01.json` and original source pins.

Correction: accept ps exit0 with validated rows, or exit1 only with exact numeric
header/no rows/empty stderr. Malformed, symbolic, duplicate, foreign parent/UID,
over16-child rows and other errors are rejected before process reads. Five host
unit tests pass; the pre-helper ImportError red result is retained and is not
misrepresented as a physical regression. The original physical failure establishes
the bug. Failure handling now persists failure class and waits only the original
handle for up to45s, recording actual exit/code or pending without kill/retry.
At least one in-window sample and available scratch measurement are required.
Fresh namespace v2 preserves v1 and prevents rerunning either used fixture.

An independent cost-efficient reviewer checked selection/lifecycle/accounting.
Their initial storage concern was retracted after verifying the actual debug
ContextWrapper; parent independently checked it. The reviewer found the missing
minimum-sample assertion, which was added before the final physical run. Parent
checked final sources and raw records. `parent-review.json` retains the review.

## Final storage and timing

| Source / version | Click-to-visible-ready ms | APK bytes | Owned-root logical bytes | Allocated bytes including dirs | Files / dirs |
| --- | --- | --- | --- | --- | --- |
| 1 | 5901 | 33242 | 123285 | 315392 | 17 / 40 |
| 2 | 3064 | 33242 | 210049 | 442368 | 20 / 48 |
| 3 | 2652 | 33242 | 296813 | 569344 | 23 / 56 |
| 4 | 2414 | 33242 | 383577 | 696320 | 26 / 64 |
| 5 | 2259 | 33242 | 470341 | 823296 | 29 / 72 |
| 6 | 2238 | 33242 | 557105 | 950272 | 32 / 80 |

Baseline root: 36171 logical bytes, 180736 allocated bytes, 12files/31dirs.
Final root: 557105 logical bytes, 950272 allocated bytes (928KiB), 32files/80dirs.
Final selected project: 528754 logical bytes, 829440 allocated bytes (810KiB),
23files/60dirs. Each warm iteration adds 86764 logical/126976 allocated bytes
and 3files/8dirs. Revisions and signed APKs remain retained; this is not a
long-term retention bound or a resource budget.

Known installed-file logical sizes, recorded separately: controller base APK
9093057bytes, worker base APK21193573bytes, extracted fixed AAPT2 library6477984bytes.
These are actual public installed-file metadata, not total harness/toolchain
allocated footprint. Other extracted libraries/system caches are not measured;
these numbers must not be called all-app or all-toolchain storage.

Host sampled worker-owned `cache/active-build` via scoped du -sk:
26available observations, maximum771KiB allocated; four unavailable samples were
followed by confirmed path absence. Absence/unavailable is not substituted with
zero. Scratch changes during traversal, so this is an observed maximum, not a
complete or precise scratch peak. Final scratch absence was verified independently.

## Memory coverage and limits

The host sampled exact root PIDs/UIDs and kernel startTicks; selected numeric
status fields and stat after removing the command field were read under own
run-as identities. Direct children are selected with ps -P WORKER_PID using
numeric PID/PPID/UID only; identity/parent checks precede using child memory.
No global trace, commandline, full status, source, signing bytes or unrelated
process private contents were exported. Metadata observation commands still have
ADB/tool overhead; no zero-overhead or production-memory claim is made.

30 sequential batches; all root observations available. Maximum sequential
controller+worker RSS batch318244KiB, observed worker VmHWM151920KiB,
controller VmHWM187200KiB. Maximum batch duration733.110786ms;
maximum end-to-next-start gap101.975554ms. Two direct-child observations were
selected but neither had an available complete numeric status/stat pair.
Their read-failure causes are unconfirmed; no child memory value is invented.
Other short-lived children may be entirely missed. This therefore supplies
no useful native-child memory coverage or exact simultaneous whole-pipeline peak.
Sequential RSS can double-count shared pages; root lifetime high-water marks
cannot be summed into a simultaneous peak. Do not compare these observations
with the previous in-worker profiler as controlled overhead measurements.

All six before/after thermal status readings were0. No temperature, continuous
thermal observation or sustained-load thermal pass. Whole-pipeline memory,
retention and sustained thermal budgets remain open; latency caps were not relaxed.

## Exact generated and harness artifacts

Root: `58472302fd104086b576ef866b581540`. Project: `app920d66c4a464460c9ee2d5e9acf335c9`.
Package: `dev.smallwright.generated.app920d66c4a464460c9ee2d5e9acf335c9`.
Source SHA-256: `a5fa243d88f5a553ee51755248b6572e274142cb22369bd0e1510132cb2d7b91`.
Public project signer SHA-256: `78f35e7ea7d3275f728ace69129823d3bbe0e10b3185c4c5815d7798dd8f39fb`.

On-phone artifact directory:
`/data/user/0/dev.smallwright.controller/files/binding-ui-probe-58472302fd104086b576ef866b581540/projects/app920d66c4a464460c9ee2d5e9acf335c9/artifacts/`.

| Phone-only APK | SHA-256 |
| --- | --- |
| `app-v1.apk` | `2dc822c22fbd7d26d1092a038d04d55befce3a1e8aba10416fbbb99d1c8e9ad8` |
| `app-v2.apk` | `280ef1d1950bb375d8ead99bed0eeaffde4d5f04533ded799dcc830562092da4` |
| `app-v3.apk` | `8552cff2e26dae7ad00e6d692452bff29629a5e03f374f7c6fe06acdf7da8e73` |
| `app-v4.apk` | `427aadeb3de8a271f415e3399e6d543b7736e6d47d4308cf3cc680b5b2b87479` |
| `app-v5.apk` | `361cd099051618163e65761631c0692aa2431e021eecee46b38ca43be0147ddd` |
| `app-v6.apk` | `5830c347a96659b0262fb98fbe72644b49302827d8cf67bc3a2c7c11ce63ee81` |

| Host-built harness APK path | SHA-256 | Bytes |
| --- | --- | --- |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `1e8327c3fae036da3ff5ecd303962802e134dc4f76386a3f79b0bf8d40c85825` | 1027342 |
| `worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk` | `5243fb4e0d993bf25c3d98064005322ba020a30f324de6eee888140ec53e1434` | 147666 |
| `controller/build/outputs/apk/debug/controller-debug.apk` | `abf873d81bd6c9556eba40ded679db91fda7277d0ce719a7bd50425f7dd6c839` | 9093057 |
| `worker/build/outputs/apk/debug/worker-debug.apk` | `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850` | 21193573 |

Public harness certificate SHA-256:
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.

| Tested source path | SHA-256 |
| --- | --- |
| `controller/src/androidTest/java/dev/smallwright/controller/BuildResourcesProbe.java` | `77ca0679c548903fc852e9ccb472c7a3fdf13067462c4aefe782baa0730f79bc` |
| `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java` | `d89cf2d40655f3e382e20463b539cd639c96c09225c7b4c9eb72cc9e6d167c7c` |
| `scripts/profile_phone_workspace.py` | `0380f2428fe0a876c256e536d043e0b50ea6b86da27f49791b9a78692d7929f0` |
| `scripts/test_profile_phone_workspace.py` | `c9a4a688d26963b953c008f508583e784ffd4a31eba5d93a206c79d724fd0a51` |

## Restoration, commands and handoff

Fresh ready-only instrumentation restored exact source6/version6 APK/receipt/
signer without rebuilding. Expected duplicate measure returned GUARD/code0.
Prior build-resources ready, ECJ Stop ready, compiler-measurement ready,
screen-lock ready and generation-completion neighbors passed host0/code-1.
Each started with controller PID absent. Completion uses verification-only
bytes; it is not another generated compiler/functional pass.

Actual commands, using serial scoped to the private local preflight record:

```bash
source scripts/env.sh
./gradlew spotlessApply :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-diagnostics-20261011/verify_install_harness.py --serial "$PHONE_SERIAL" --record toolchains/alpha-workspace-resources-20261011/probe-artifacts-02.json controller.test
python3 scripts/profile_phone_workspace.py --serial "$PHONE_SERIAL" --label 02
python3 toolchains/alpha-workspace-resources-20261011/run_ready_and_neighbors.py --serial "$PHONE_SERIAL"
./gradlew qualityCheck :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 scripts/test_profile_phone_workspace.py
python3 scripts/test_profile_phone_builds.py
python3 scripts/validate_benchmarks.py benchmarks/results/*.jsonl
python3 toolchains/alpha-workspace-resources-20261011/verify_final_workspace.py
```

These are historical commands; reusing existing record labels or one-shot
measurement namespaces is rejected. Inspect original handles before any new run.
Final offline quality passed41s,207tasks:6executed/201up-to-date. All three JVM
test tasks were up-to-date; controller Android-test lint analysis ran, lint report
tasks were up-to-date. Recorded lint0errors, warnings72/2/0(controller/worker/protocol).
Five new and three neighboring host tests passed; seven existing benchmark rows
validated unchanged. No new alpha ledger row or full-suite result was added.

At `2026-10-11T19:39:34.029914+00:00` all original host PIDs were absent, all four
installed byte hashes and final tested source pins matched, worker scratch absent.
Only the four own harness packages were force-stopped; all app/project data retained.
Keyguard false/Awake at that observation; no assertion about later phone state.

Changed paths: controller/src/androidTest/java/dev/smallwright/controller/
ArtifactPolicyInstrumentation.java and BuildResourcesProbe.java;
scripts/profile_phone_workspace.py; scripts/test_profile_phone_workspace.py;
docs/STATUS.md; docs/alpha-acceptance.md; this validation document.
Existing skill files/tooling handoff changes are unrelated and preserved.

Git/network access was temporarily restricted while this evidence was recorded;
no extra phone pass was inferred from that interruption. Next: continue bounded resource/reliability
coverage and live free-only repair when local-only deferral is lifted; retain
S23's frozen Step repair queue. Actual ENOSPC, provider cancellation, spontaneous
eviction, encrypted recovery, detached-laptop operation, sustained thermal,
redistribution clearance and the full20-request suite on both phones remain open.
