# First and warm native builds — A33, 2026-10-11

The screen-lock recovery after user unlock already passed in local `adf148b`;
see [its physical evidence](screen-lock-recovery-a33-20261011.md). After the user dismissed the blocking system window, two real phone builds
passed: **2174ms first and 867ms warm**, followed by exact ready restart,
one-shot guard and recovery/completion neighbors. These are two observations,
not performance percentiles or a full alpha pass.

Issue: #12.
Base: `adf148b`. Production code, worker and dependencies are unchanged.
Raw attempts, source hashes, setup/build logs, private preflight and cleanup
records remain ignored under `toolchains/alpha-compiler-measurement-20261011/`.

## Contract

Use a new isolated one-shot project and actual native New project, editor and
Build controls. Change one default label, then measure first and second actual
phone builds through authenticated separate-UID Binder, native compilation,
validation, per-project signing and immutable receipt. Independently check
source revision/content hash, APK package/version/policy/signature/receipt,
same signer, and fresh-process restoration. No model, generated installation,
source/APK pull, data reset or existing credential inspection is allowed here.

The first build starts in a new worker process and creates a fixture signer;
it is not an OS-cache-cold measurement. The second must retain worker PID/start
identity and signer. Clock endpoints use elapsedRealtimeNanos immediately before
the actual Build click and after observed visible enabled Install readiness.
UI polling/reveal overhead is included. Independent artifact checks follow.
Host pre/post numeric process observations and acknowledgments remain outside
timed intervals. PSS samples are not build peaks; cumulative VmHWM excludes
native children and cannot establish per-build or whole-pipeline peak memory.
Thermal status is not temperature or sustained-load evidence. Logical source/
artifact sizes and filesystem available bytes are not total workspace size.

## Actual setup attempts

Fresh preflight again identified physical Samsung Galaxy A33 5G SM-A336E,
Android16/API36, ARM64, s5e8825, qemu0. Commands were serial-scoped. Initial
installed-byte baseline matched all four harness packages and their own
processes were absent. Each retry used a fresh namespace; terminal original
handles were verified before stopping only this run's controller/worker.
Existing data and failed fixture records were preserved. Test APK installation
used verified package/public certificate/hash and data-preserving debug `-r`.

| Attempt | Installed controller-test SHA-256 | Bytes | Actual result |
| --- | --- | --- | --- |
| 1 | `45603d611fcd7509abd96c1c25718ac48745b4938c7195de9146d647087d39ae` | 998394 | Setup failed before first Build; code0 |
| 2 | `4b12e379a2758722544c81e3113d1aeacca631010068e3576f7e1766c2ca9c4d` | 999446 | Setup failed before first Build; code0 |
| 3 | `167ae4d9a1d6428bc0c5e5958868bfe687c9944ce44fa76303286c14eead3e97` | 999462 | Setup failed before first Build; code0 |
| 4 | `b90ce032ec59a1d2b13296eae44183dfcfb2b409a58ae36b7566218ae0c94e7a` | 1000470 | Setup failed before first Build; code0 |
| 5 | `a8a8e4fb4536237ce3a966dd56c6063e3281b55609a8546f3ca789109b81232f` | 1002222 | Setup failed before first Build; code0 |
| 6 | `82c8ae251d62ea5fd1c1a148413e9ea72fce34bbf02eba0c5782dc435577ca1e` | 1003818 | Setup failed before first Build; code0 |
| 7 | `3f7c815b735f204a6d004979660a7aa22d6c1cc0cd10e9353b754f05e1083332` | 1004182 | Setup failed before first Build; code0 |
| 8 | `bd64c0214b9ad1a46cad46a17c518b29e516aeba1c6d6848196854be1dcce99c` | 1002618 | Setup failed before first Build; code0 |

All eight original runs finished with a failed setup result, instrumentation
code0, and no timing gates/build-result markers. Their host sample arrays are
empty. None is a compiler failure or an alpha benchmark pass.

Attempts1–3 failed finding the project input. Attempt4 added narrow metadata:
activity focus false, accessibility root nonnull but not controller-owned.
Attempt5 searched controller roots across interactive windows and still failed.
Attempt6 confirmed an active/focused foreign accessibility window, without
logging its package/text. Attempt7 classified it as Android System UI; no
controller input appeared. Attempt8 used native in-process windows and still
found no focused owned project dialog. Its own failure marker independently
reported KeyguardManager locked=false and PowerManager interactive=true.
After terminal exit, narrow host policy/power readings also showed keyguard
false and Awake. The system panel/dialog contents were not captured.

Each attempt finished its test activity. Both owned processes were then stopped;
`failed-v1-cleanup.json` through `failed-v8-cleanup.json` record preservation.
An ADB attempt to launch the nonexported debug activity was denied; its manifest
boundary was retained. The normal exported controller was subsequently opened
for the user's focus-unblocking action. No fixture was reset or replayed.

## Current implementation and validation

- `controller/src/androidTest/java/dev/smallwright/controller/CompilerMeasurementProbe.java`:
  guarded `measure`/`ready` phases, two real Build timings, pre/post observation
  gates, independent artifact assertions and exact restart pins.
- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`:
  bounded opt-in route with safe throwable-class/failure-stage reporting.

The selector now uses public
[WindowInspector.getGlobalWindowViews](https://developer.android.com/reference/kotlin/android/view/inspector/WindowInspector),
which lists windows in the current process. On the main thread it requires one
focused root containing a visible/enabled exact Project-name-hint EditText whose
context is the exact current fixture activity. Title entry and actual Create
click recheck focus and visibility in that same root. No system-window action
or project injection is used. Source review found no concrete boundary defect;
the final v9 selector passed physically after the user dismissed the blocking
system UI. v9 also makes the hint comparison null-safe and uses a fresh namespace.
The underlying System UI contents/cause were not inspected. The successful run
required real focus; it did not bypass or dismiss any system control.

## Successful physical run and resources

After user Ready, fresh preflight matched the same A33; the run-opened normal
controller and worker were stopped with data retained. The exact final test APK
was package/certificate/byte verified and installed with `-r`.
`run_measurement.py --label 09` passed on its original handle with host exit0,
instrumentation code-1 and two independently verified build records.
The committed equivalent is `scripts/measure_phone_compiler.py`; its executable
AST matches the physically run helper except output-directory resolution, and
it preserves one-shot/no-overwrite/terminal-handle behavior.

| Observation | First | Warm |
| --- | --- | --- |
| Build click to visible ready (ms) | 2174 | 867 |
| Source revision / version | 1 | 2 |
| Start elapsedRealtimeNanos | 19816472482302 | 19820521638184 |
| End elapsedRealtimeNanos | 19818646864301 | 19821388919337 |
| APK bytes | 16858 | 16858 |
| Logical source bytes | 7741 | 7741 |
| Logical artifact-directory bytes | 17208 | 34066 |
| Filesystem available bytes | 107597438976 | 107597340672 |
| End thermal status | 0 | 0 |

First build includes new project-signing identity creation and the first build
in a newly started worker; handshake/UI preparation are outside the clock.
Second build reuses the same source content, worker process and signer. Both
source revisions and package versions advance normally. Controller UID10319
and worker UID10318 differ. Exact controller/worker PID and proc start ticks
remained unchanged at all four gates (private numeric identities in raw records).
No observer commands ran during either timed interval.

| Phase | Controller VmHWM KiB | Worker VmHWM KiB | Controller PSS KiB | Worker PSS KiB | Batch ms | Gap since prior batch ms |
| --- | --- | --- | --- | --- | --- | --- |
| before1 | 173220 | 79680 | 101331 | 35228 | 429.605 | n/a |
| after1 | 177428 | 109292 | 97544 | 58280 | 524.008 | 2557.147 |
| before2 | 177428 | 109292 | 97924 | 58280 | 515.689 | 399.331 |
| after2 | 179604 | 121552 | 102754 | 70934 | 549.328 | 1330.974 |

All proc status/stat and targeted `dumpsys meminfo --local -s PID` reads were
available, with per-process UTC start/end and sample durations retained. These
sequential samples include host observer overhead and asynchronous accounting;
process high-water marks must not be summed into a whole-pipeline peak. See
[proc accounting semantics](https://docs.kernel.org/filesystems/proc.html) and
[Android PSS measurements](https://developer.android.com/tools/dumpsys#meminfo).
The thermal API reported status0 at each end, not a measured temperature or a
sustained thermal pass. No cold-cache, phone-wide network-disable or detached
claim is made; worker Internet permission remains absent.

## Exact source and artifacts

Fixture root: `e0c42c02c0a34fb4881b782023197e65`; project: `app9c346a4dd6e64924a24bf60cad841ae4`.
Generated package: `dev.smallwright.generated.app9c346a4dd6e64924a24bf60cad841ae4`.
Source content SHA-256: `2cb2b687d7782f73eb9f7173e5383adcc8758094e554642d2c088e80ffe522fc`.
Public signer fingerprint: `d19391821d035086a64cbbb8353cd5813c2a25b28dfceaa049bdf71454af85d8`.

The generated bytes remained only on phone, under:

`/data/user/0/dev.smallwright.controller/files/binding-ui-probe-e0c42c02c0a34fb4881b782023197e65/projects/app9c346a4dd6e64924a24bf60cad841ae4/artifacts/`

| Phone artifact | SHA-256 |
| --- | --- |
| `app-v1.apk` | `4e10f4826d317ca78094b9b9967923c9ec9d871c9f2609396467f98b073cfec3` |
| `app-v2.apk` | `455aa2d40a8569bbbb5e5b33990773c2b5d15897a0e5e084c792dd31c6c6dd71` |

Actual source hashes/revisions, manifest/package/version/policy, ApkVerifier
signature, existing project signer and immutable receipt were checked on phone
independently after each clock endpoint. No generated APK was installed/exported.

Final host-built and installed harness identities:

| APK path | SHA-256 | Bytes |
| --- | --- | --- |
| `controller/build/outputs/apk/debug/controller-debug.apk` | `abf873d81bd6c9556eba40ded679db91fda7277d0ce719a7bd50425f7dd6c839` | 9093057 |
| `worker/build/outputs/apk/debug/worker-debug.apk` | `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850` | 21193573 |
| `worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk` | `a0bdf714f3ecffa311e7365d9342bf7b9c3449429b56f74e9666bd39c647c53b` | 137066 |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `22e453029a69bb54a12ee94230fb99f0f45b0cf80fd8c06fef8c1701d85d080b` | 1002626 |

All harness public certificate SHA-256 values are
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
Existing private credentials/signing stores were not inspected. Runtime-created
fixture signing used normal app APIs, with only the public fingerprint reported.

## Restart, quality and limits

`run_ready_and_neighbors.py` passed all five named outcomes on their original
handles: fresh-process exact source2/version2 ready restoration (no rebuild),
expected duplicate-measure rejection at GUARD/code0, screen-lock ready,
worker-Binder-death ready, and generation completion ordering (code-1 each).
The completion neighbor uses SQLite/filesystem verification-only bytes and does
not add another real compiler measurement. Raw results and host completion codes
are in `recovery-and-neighbor-results.json` plus the named `.txt` files.

Two initial v6 host commands failed (nonexistent module formatting task, then
missing checked-exception declaration in the diagnostic); corrected commands
were retained and rebuilt successfully. The final command was:

```bash
source scripts/env.sh
./gradlew spotlessApply :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest qualityCheck :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 scripts/measure_phone_compiler.py --serial "$PHONE_SERIAL" --label 09
python3 toolchains/alpha-compiler-measurement-20261011/run_ready_and_neighbors.py --serial "$PHONE_SERIAL"
```

Quality/APK assembly passed in45s:149 tasks,13 executed/136 up-to-date. JVM test
tasks were UP-TO-DATE, not fresh tests. Spotless, compiler-input verification
and Android Lint passed; reports show0 errors and72/2/0 warnings for controller/
worker/protocol. No dependencies or production code changed. Generated-app
function/install checks were untested/not attempted. An attempted custom local
ledger was rejected because the validator reserves prompt IDs for the frozen
suite; it is preserved only in the ignored evidence directory. The validator
was retained unchanged, and these observations do not enter the alpha denominator.

Provisional limits are recorded in [alpha acceptance](../alpha-acceptance.md):
10s first Build and5s warm Build for the supported native template, using this
click-to-visible-ready interval and excluding inference. These deliberately
provide headroom over the two observed runs; they are working evaluation caps,
not estimated percentiles, device-wide guarantees or production enforcement.
Do not relax them in response to alpha failures. Memory/storage/thermal caps
remain unset because this spike does not measure whole-pipeline peaks, total
workspace/retention growth or sustained load. Resource gate closure remains
pending, as do cold-cache behavior and the full twenty-request suite.

Final scoped verification matched all four installed APK byte hashes to the
listed host artifacts and the final v9 Java source hashes to the tested snapshot.
Original measurement/neighbor handles were terminal. Controller/worker were
then stopped; existing data, failures, projects and receipts were retained.
Phone policy/power showed keyguard false and Awake. The committed host runner
was also invoked against existing label09: it rejected before instrumentation
with the exact no-overwrite assertion, leaving records and app state unchanged.
All seven frozen-suite ledger rows passed the unchanged validator; these local
synthetic measurements add no suite entries. Independent source/evidence review
found no concrete boundary or claim defect; parent checked its findings.
Next task: measure bounded larger native source/resource workload and its peak/
retained storage or ECJ-phase cancellation locally. Live free-only repair stays
deferred under the user's local-check instruction. S23 frozen Step stays on that
phone; no A33 substitution. No full alpha pass is claimed.
