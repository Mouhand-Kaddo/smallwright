# Thirty sequential native builds and resource observations — A33

Physical execution on 2026-10-11 UTC (2026-10-12 Dubai), base local commit
`9959528`, tracked in issue #18.
This is a bounded resource/reliability checkpoint, not private-alpha acceptance.

## Contract and execution boundary

Fresh read-only preflight at20:47:22.404758 UTC matched the user-authorized Galaxy
A33 5G SM-A336E, Android16/API36, ARM64 s5e8825, qemu0. All ADB commands selected
the private serial explicitly; no personal identifier/capture is committed.
Before physical execution all four installed baseline hashes matched and own
processes were absent; unlocked/Awake state was checked. Only verified test APKs
were updated with data-preserving debug `install -r`, after exact package/public
certificate/hash checks and followed by installed-base byte comparison.
Production controller and worker APKs/code were unchanged, as were dependencies.

A new one-shot isolated controller root/project received synthetic53400-byte
Java source with1000 tiny methods. Thirty sequential actual visible Build clicks
ran the normal authenticated separate-UID worker, native Java/XML compiler,
project signing and immutable receipt verification at source1..30/version1..30.
Every iteration checked package/manifest policy, APK signature, source hash and
unchanged project signer, then observed verified Install readiness. Install was
never clicked. No model/credential access, generated app install/export/pull,
remote build, prebuilt generated APK, data clear or source repair occurred.
Generated source, compiler tools and APK bytes remained on phone.

Existing first10s/warm5s click-to-visible-ready caps remained unchanged. New test
bounds were declared before execution:30 builds,180s sequence duration,
MODERATE(2) thermal-stop threshold,128 tracked worker/direct-child process entries,
8192 samples and240s observer window. These are instrumentation test bounds,
not newly enforced production workspace or whole-pipeline memory budgets. First
means a fresh worker process, not OS-cache-cold. Warm requires the same worker
PID/startTicks, verified externally before/after the sequence.

No artificial cooldown was added. Gaps include necessary UI setup, artifact/
source verification and storage observations. The controller registered a real
Android thermal listener and sampled status before/after builds; it reread status
on the main thread immediately before every next click. A reached thermal bound
finishes any admitted build, then starts no further build. The duration bound is
checked before and after each build. Sequence end is captured before the later
host gate wait. No observation timeout requests Stop, kills a process or starts
a new attempt: original controller/observer handles are awaited in30s chunks,
and the worker observer is released only after controller termination.

The controller test Activity scopes files/cache/no-backup and preference names
to its owned root; normal main-project selection is untouched. Storage traversal
uses only lstat metadata (regular-file sizes, st_blocks*512, directories), bounded
to depth16/4096 entries, rejecting symlinks/special files. No signing or credential
bytes are read. This excludes shared preferences, system Keystore, installed APKs,
ART/system caches, unrelated projects and general phone storage.

## Preserved failure, diagnosis and physical regression

Attempt01,20:53:45.820278 UTC, retained30 real Build-result records: first5920ms,
maximum warm2900ms, source1..30/version1..30 verified, thermal statuses0. However,
the worker observer reported only an AssertionError category and INVALID. The
host withheld after30 acknowledgment, so the controller test gate later failed
at AFTER_GATE. Both original commands completed host0/instrumentation code0;
this attempt remains a failed resource run. No active build was killed or replayed.
Read-only inspection at20:58:27.613138 UTC independently verified its final exact
source30/version30 signed APK/receipt and readiness with
`originalResourceTestPassed:false`. Project/source/history/artifacts were preserved.

Narrow diagnostic markers then recorded only fixed reason identifiers, numeric
field names, owned PID/sample counts; no exception body, process commandline,
source, headers or credentials. A separate physical worker-only child-exit fixture
spawned known `/system/bin/true` children under the worker UID, without a shell,
compiler/model/generated app or file output. It waits each child and releases the
observer only after the producer completion marker. At21:01:00.430950 UTC it
completed886 children within its15s/1000-child bound and reproduced missing VmRSS
after5 samples. A reduced100-child case at21:03:20.854864 UTC reproduced the same
failure after5 samples (reason9, numericField VmRSS, code0). This is an actual
kernel-process sampling regression, not fake compiler or resource evidence.

Fix: child UID, parent and stat/startTicks checks remain strict. A direct child's
absent VmRSS/VmHWM is an explicit unavailable sample, counted separately from a
vanished/status-IO sample; own worker missing memory, malformed numbers, ownership,
parent, PID reuse and bound failures stay fatal. Identity is reserved before
memory parsing, so even a first unavailable sample retains startTicks. Zero valid
memory samples have null maxima, never measured zero. Discovery read failures and
unavailable/vanished child memory mark the batch incomplete. RSS sums explicitly
omit unavailable values and cannot represent an exact pipeline peak.

The100-child regression at21:05:05.769274 UTC passed:100 completions, one unavailable
memory sample, one vanished/status-IO sample, two incomplete batches, code-1.
Review then identified first-unavailable identity retention and discovery-batch
accounting gaps; both were tightened. Final100-child regression at
21:11:50.573219 UTC passed with two unavailable first-memory samples and two
retained identities having samples0/null memory maxima. Green requires that the
actual missing-memory condition triggers; a run that merely sees no failure is
not enough. Original red/raw handles are preserved.

Attempt02,21:05:55.709021 UTC, used a new v2 namespace/project, passed all30 builds
in64.341319108s, first5816ms/warm maximum2977ms/thermal0. Its2761 worker samples
included one explicit unavailable memory sample; both original commands exited
host0/code-1. Source30 ready reopen/duplicate guard and six neighbors passed.
A further v3 namespace preserves this successful prior fixture while verifying
the final stricter identity/discovery accounting. No previous attempted flag,
source, revision, journal or artifact was reset or overwritten.

The first run's exact assertion cannot retrospectively be proven from its coarse
category alone. The separate child-exit reproduction establishes a concrete
observer bug and its fix; it is not relabelled as the original run's proven reason.
Independent cost-efficient review was checked against code/runtime by the parent.

## Commands and source paths

Changed code:
`controller/src/androidTest/java/dev/smallwright/controller/BuildResourcesProbe.java`,
`controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`,
`worker/src/androidTest/java/dev/smallwright/worker/BuildResourceObserver.java`,
`worker/src/androidTest/java/dev/smallwright/worker/ConfinementInstrumentation.java`.
The opt-in sustained modes retain existing six-build entrypoints and bounds.
Safe failure routes return explicit code0; successful named results require
code-1 independently of host ADB exit0.

Ignored implemented drivers/raw evidence are in
`toolchains/alpha-sustained-resources-20261011/`: `run_measure.py`,
`run_exit_diagnostic.py`, `run_phase.py`, `run_neighbors.py`, `verify_final.py`.
These are local validation tooling, not distributed app features. Unique record
labels/one-shot guards reject overwriting/restarting an existing attempt.
`tested-source-01/02-before-fix/03/04-sha256.json` retain historical/final pins.

```bash
source scripts/env.sh
./gradlew spotlessApply :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-diagnostics-20261011/verify_install_harness.py --serial "$PHONE_SERIAL" --record toolchains/alpha-sustained-resources-20261011/probe-artifacts-06.json controller.test worker.test
python3 toolchains/alpha-sustained-resources-20261011/run_exit_diagnostic.py --serial "$PHONE_SERIAL" --label 04
python3 toolchains/alpha-sustained-resources-20261011/run_measure.py --serial "$PHONE_SERIAL" --label 03
python3 toolchains/alpha-sustained-resources-20261011/run_neighbors.py --serial "$PHONE_SERIAL" --label 02
./gradlew qualityCheck :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 scripts/validate_benchmarks.py benchmarks/results/*.jsonl
python3 toolchains/alpha-sustained-resources-20261011/verify_final.py --serial "$PHONE_SERIAL"
```

These are historical commands; existing labels/fixtures cannot be replayed.
The local supported JDK21.0.12.1+1/SDK36 and capped offline Gradle were used,
with no downloads/global Java changes. A patch helper failed its exact-text
assertion before writing files; the redundant unchanged build02 is retained,
and actual bound fixes first compiled in build03. All build logs remain local.

## Interpretation and remaining gates

Sampling includes worker instrumentation/profiling overhead, sequential reads,
possible missed short-lived children and shared-page RSS double counting. Kernel
VmHWM is a process lifetime high-water value; a child may be sampled before exec.
It does not identify a compiler-tool phase or simultaneous peak. Controller
post-build readings and worker batches cannot be summed into a measured
whole-pipeline peak. Even a batch with all selected children available cannot
prove that discovery captured every child. Thermal status is Android's reported
pressure category, not measured temperature, indefinitely sustained stability or
detached-laptop evidence.

The retained owned-root size for one bounded synthetic project is not total app
storage, user-project growth or a long-term retention budget. No actual ENOSPC,
phone-wide low storage or spontaneous OS eviction was induced. No alpha ledger
entry, functional generated-app launch or two-device suite pass is claimed.
Whole-pipeline memory/retention/longer thermal budgets remain open; do not assign
limits that the observations cannot support. Next: establish those missing
resource limits and actual failure recovery, then run the fixed20-request suite
on both physical devices; preserve the S23 frozen Step queue, detached/cross-device
recovery, redistribution and user-cohort gates.


## Final v3 physical result

Attempt03 began at2026-10-11T21:12:28.330388+00:00; both original host commands exited0,
with explicit controller/observer code-1, no pending handle, and observer release
only after controller termination. All30 signed artifacts passed their exact
source/version/package/policy/receipt/same-signer checks. The unchanged first10s/
warm5s latency caps passed. Sequence duration was66.038082375s, excluding the
later host gate; largest ready-to-next-click gap was58.178538ms. The final saved
source30/version30 reopened with the same immutable artifact and enabled Install.
Duplicate measure failed at GUARD before mutation. Six neighboring ready/
completion cases passed, all original handles terminal. See `measure-handle-03.json`,
`measure-03.txt`, `observer-03.txt`, `recovery-and-neighbor-results-02.json`
and each named neighbor raw record in the ignored evidence root.

Click-to-visible-ready durations in version order (ms):

```text
6278, 3013, 2506, 2379, 2189, 2166, 2033, 2050, 2033, 2026, 1994, 1974, 1914, 1915, 1881, 1973, 1887, 1898, 1869, 1845, 1862, 1850, 1868, 1776, 1813, 1804, 1824, 1819, 1844, 2316
```

The registered thermal listener emitted one observed callback at status0;
all before/after readings and maximum reported pressure were0. These are
real pressure-category observations for this bounded66-second sequence.

| Observation | Final v3 value | Scope |
| --- | --- | --- |
| Worker samples | 2830 | Includes observer instrumentation overhead |
| Tracked process identities | 53 | Worker +52 discovered direct children |
| Available batch RSS maximum | 217668KiB | Available worker/child readings only |
| Complete observed batch RSS maximum | 217668KiB | Selected reads available; discovery can miss children |
| Complete / incomplete observed batches | 2824 / 6 | Missing/discovery reads explicitly incomplete |
| Unavailable child memory / vanished status samples | 3 / 1 | Unknown values omitted, never zero |
| Child discovery failures | 2 | Numeric ownership/status scan |
| Longest sampling duration / inter-sample gap | 25.561424 /26.675808ms | Observed sampler overhead/gap |
| Worker lifetime RSS / HWM maximum | 217668 /218568KiB | Own worker, not pipeline aggregate |
| Controller post-build RSS / HWM maximum | 182580 /187596KiB | Snapshot/lifetime values, not simultaneous worker peak |
| Final project logical / allocated bytes | 2611139 /3876864 | 95files,252directories |
| Final owned root logical / allocated bytes | 2639490 /3997696 | 104files,272directories; before after30 ack |

No final build-run identity had zero valid memory samples; the separate final
100-child regression did retain two such null-memory identities. These records
are distinct. Final v3 observed actual missing child memory3 times without
invalidating the strict identity checks or representing unavailable memory as0.

Final exact identity:

- Owned root: `55d75c933c7140abb6b59ec2d2d00867`.
- Project: `app605b464843214eed981b2029e26cd8da`.
- Package: `dev.smallwright.generated.app605b464843214eed981b2029e26cd8da`.
- Source30 SHA-256: `6c39f6493cbcb0fe60b1de49aff8b17275bdf4ec7b077cc2802de7e1587ef8e7`.
- Version30 APK SHA-256: `53464fb94abc8040ff52996fa98ebfe6f71cfd0503a34dbee485ada287619763`, 33243bytes.
- Project public signing certificate SHA-256: `3b3823de2747e71adba743b63bc6a5e6fe2126010b64075f58526c41b78f0e49`.
- Phone APK: `/data/user/0/dev.smallwright.controller/files/binding-ui-probe-55d75c933c7140abb6b59ec2d2d00867/projects/app605b464843214eed981b2029e26cd8da/artifacts/app-v30.apk`.

Final harness installed identities, verified before the final runtime
(`probe-artifacts-06.json`); production identities remained unchanged:

| Host APK path | SHA-256 | Bytes |
| --- | --- | --- |
| `controller/build/outputs/apk/debug/controller-debug.apk` | `abf873d81bd6c9556eba40ded679db91fda7277d0ce719a7bd50425f7dd6c839` | 9093057 |
| `worker/build/outputs/apk/debug/worker-debug.apk` | `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850` | 21193573 |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `8529d2487348d92fa7c052127e99d7383697d62070ca2bb446b2f7d784938588` | 1079282 |
| `worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk` | `4019923938cb23ab850a6e27c63e843d5b56f74808fa5e7ee87482edea9f477b` | 154302 |

Both test APK public certificate digests matched
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
All previous harness records01..05 are retained; this table identifies the final
v3 candidate, not the original failed run or earlier red/green candidates.

Final Java source SHA-256 pins:

- `controller/src/androidTest/java/dev/smallwright/controller/BuildResourcesProbe.java`: `0d4d88f507775ae2c5e6484d795045ec037a54f9557544ca83271addc533c5c4`.
- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`: `2cc543ea738a92b68e5b8d8be4d081ebfad30c9831f5c371a943e6347638bb4e`.
- `worker/src/androidTest/java/dev/smallwright/worker/BuildResourceObserver.java`: `d8719cc68fc616cb64661097a1622d50f1332e2e6db5e6a7601028f9a032ebb7`.
- `worker/src/androidTest/java/dev/smallwright/worker/ConfinementInstrumentation.java`: `627f26837e839c9a5f1d1df3479fa305e4df734485831034e5a0a2533f22718b`.


## Final quality, retained artifacts and cleanup

Final offline `qualityCheck` plus all four harness assembly tasks passed in48s:
207 actionable tasks,10 executed/197 up-to-date. Formatting and Android lint
ran; controller/worker/protocol JVM tasks were UP-TO-DATE (cached213/57/4 tests,
no failures), not newly executed tests. Lint XML reports zero errors and existing
72controller/2worker/0protocol warnings. `quality-02.log` and the original terminal
host handle preserve this final candidate result; the earlier49s quality01
record belongs to the earlier candidate. Benchmark validation reports7 valid
historical results; no new denominator row was inserted.

At2026-10-11T21:18:46.022570+00:00 final verification passed: all four host and installed
APK hashes/lengths still matched; final Java/helper source pins matched; original
measurement/regression/inspection/neighbor host handles were absent; own harness
processes were stopped and absent, worker active-build scratch absent. Unlocked/
Awake state was confirmed. No data clear/uninstall, project selection change,
model request, generated app installation or APK byte export occurred.

All90 retained APKs across the failed v1 and passing v2/v3 projects matched their
original observed version/hash/length metadata in-place on the phone. Each
owned root's final scoped `du -sk` observation was3904KiB; this includes later
acknowledgment/ready metadata and differs from the before-after30-gate storage
row by design. The three version30 paths are:

- `/data/user/0/dev.smallwright.controller/files/binding-ui-probe-ec5768839a294f10946b0f7fd871ad38/projects/appcbc95470623d4b7b8eeefb35c43ae79a/artifacts/app-v30.apk`.
- `/data/user/0/dev.smallwright.controller/files/binding-ui-probe-a29d4052f51e46a0a64bf7b639435cb9/projects/appfbb05578a92442d0ac420be86cbb0f4e/artifacts/app-v30.apk`.
- `/data/user/0/dev.smallwright.controller/files/binding-ui-probe-55d75c933c7140abb6b59ec2d2d00867/projects/app605b464843214eed981b2029e26cd8da/artifacts/app-v30.apk`.

See `final-installed-source-and-cleanup.json` / `final-verify-host-01.txt`.
The precise tracked change set is the four Java paths above,
`docs/STATUS.md`, `docs/alpha-acceptance.md`, and this validation document.
User-owned skill/tooling documentation remains outside this checkpoint.
Next task: whole-pipeline/retention/longer thermal resource limits and real
failure recovery; full private alpha remains incomplete.
