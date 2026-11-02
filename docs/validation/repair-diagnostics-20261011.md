# Repair diagnostic integration — physical A33, 2026-10-11

This checkpoint adds actual compiler records to explicit repair approval.
It uses the real-phone-test flow. The user selected local checks and deferred
live free-only repair. No model request or credential inspection ran. Synthetic
phone-side source and simulated proposals are labelled below; no benchmark or
complete-alpha pass is claimed.

## Boundary and implementation

- Protocol1 remains compatible. Handshake2 returns the exact numeric-diagnostics
  capability before one build; a new final Binder transaction carries a separate
  output descriptor. Caller authentication, worker admission and snapshot timeout
  remain in force. No second build is issued to fetch diagnostics.
- The descriptor must be empty, writable, regular, singly linked and distinct
  from source/APK inodes. Transferred descriptors close on rejection, failure and
  busy admission. Worker output contains only actual admitted MainActivity ECJ
  records: source SHA-256, ID/category/line and truncation, at most8 records/135 bytes.
- The controller validates exact binary framing, bounds, numeric fields, source
  hash and actual source line count against bytes in its still-held build lease.
  It accepts detailed records only with COMPILE_ERROR; an empty legacy report
  remains generic. It never imports raw compiler prose or paths.
- Actual build failures pin records with the current project/run/build/revision
  and whole-source-map digest before REPAIR_NEEDED. Pinning uses the Stop/completion
  lock and requires a matching live BUILDING attempt. New source pins clear older
  diagnostics; old origins remain generic, with no retrospective fixture report.
- Preview and both proposal approval checks reload provenance. Only the captured
  REPAIR_NEEDED event or exactly one EDITING advance may own its report. Request,
  chat/history, source, build identity and payload changes invalidate approval.

## Device and installation

Target: user-confirmed Galaxy A33 5G, Samsung SM-A336E, Android16/SDK36,
arm64-v8a, hardware s5e8825, qemu0. Physical A33 evidence is separate from the
historical S23 Ultra checks. Fresh preflight and serial-scoped ADB were used.
The serial and raw records stay under ignored local toolchains directories.

Only this project's four harness/test APKs were updated through debug install-r,
after package/hash/signer/worker-permission verification. Installed package bytes
were checked against each host artifact. Harness certificate SHA-256:
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
This is debug installation, not production Android installation-consent evidence.
No generated APK/source was pulled, no generated app was installed in this
checkpoint, and the previously installed counter and its data were untouched.

## RED → GREEN and physical cases

Ignored evidence: `toolchains/alpha-diagnostics-20261011/`.

| Case | Actual result and evidence |
| --- | --- |
| Binary report contract | Missing API RED (`wire-red.log`); literal independently specified binary/SHA fixture, mutations, wrong source/lines and signed ECJ IDs pass. Immutable List.of input exposed NPE (`wire-factory-red.log`), fixed and passed. |
| Real Binder compiler report | Original malformed-source probe failed `Real Binder compile failure lost source-bound diagnostics` (`binder-red.txt`); same real compiler case passed with report (`binder-green.txt`). |
| Persistence/provenance | Missing persistence methods RED (`origin-red.log`); record restart, exact source/build and waiting/in-flight journal checks pass. Wrong-source, wrong-build, terminal/APPLYING state rejected. |
| Changed-report approval | Missing preview API RED (`preview-red.log`); changed numeric tuple after preview rejected (`preview-green.log`). Synthetic bounded report fixture, not live compiler evidence. |
| Different failed run | Genuine RED dispatched repair for another run (`run-binding-red.log`); matching captured run required before provider/journal/source mutation. |
| Real restarted repair UI | `ui-red-final-verifyNumeric.txt` fails at NUMERIC_PAYLOAD; `ui-green-verifyNumeric.txt` passes after wiring. A newly created fixture used an actual Binder compiler failure. Phone accessibility verified exact source hash and every numeric tuple in the real dialog; Cancel preserved journal sequence. No provider/build/install from preview. |
| Invalid descriptors | Pipe/nonempty/source-alias/artifact-alias reach the worker and reject. Hardlink fixture creation denied by Android (`descriptor-red.txt`): that attempt is BLOCKED, not a worker pass. Continuing supported cases found real readonly admission RED (`descriptor-red-final.txt`); writable-mode check fixed it (`descriptor-green-invalid.txt`). Multi-link rejection is implemented/reviewed but physical worker hardlink case remains NOT RUN. |
| Admission/deadline | `descriptor-green-lifecycle.txt`: an observed active held-open input caused concurrent new-method build to return Worker busy; transferred snapshot/artifact pipes closed, report stayed empty. First call returned SNAPSHOT_TIMEOUT with closed pipes/empty report. Same worker then compiled and validated a fresh native Java/XML APK. |
| Explicit legacy control | `local-repair-green.txt`: test-only adapter rejects handshake2 and forwards exactly one real legacy Binder build. Malformed Java returns fixed failure and no detailed records; this is not an old-binary installation claim. |
| Local repair recovery | Same record: actual error report persisted into repair context; GenerationRun checked it before and after an explicitly simulated proposal; actual worker compile/sign/verification and durable journal reached READY_TO_TEST. No AI or generated install. |

An intermediate `run-binding-green.log` did not pass: it compiled while the next
new persistence test still required an unimplemented method. The corrected
`run-origin-green.log` passed both classes. The stale test artifact installed
by that intermediate combined shell did not establish a new UI assertion;
`ui-red-final-verifyNumeric.txt` supplies the unambiguous original UI RED.

The first final quality command (`checkpoint.log`) failed with two NewApi errors:
InputStream.readNBytes requires API33, while minSdk is31. It was replaced with
bounded API31-compatible sentinel reads, without suppressions or raising minSdk.

## Final verification

Final command `checkpoint-api31.log` passed in2m25s, 207 tasks:37 executed,
170 up-to-date. All three JVM test tasks executed:213 controller,57 worker and
4 protocol tests; zero failures/errors/skips. Spotless and fresh controller
lint passed; lint has zero errors,72 controller/two worker/zero protocol warnings.
Compiler input/header verification passed. All four APK assembly tasks completed,
with a mixture of fresh and up-to-date work. Seven existing benchmark ledgers
validate; no new benchmark attempt was recorded.

Fresh final preflight at15:31:44 UTC was ready. The installed artifacts in
`final-artifacts.json` passed exact package/certificate/byte-hash checks. Source
and configuration hashes are in `final-source-sha256.json`.

| Host-built APK | SHA-256 | Bytes |
| --- | --- | --- |
| `worker/build/outputs/apk/debug/worker-debug.apk` | `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850` | 21193573 |
| `worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk` | `a0bdf714f3ecffa311e7365d9342bf7b9c3449429b56f74e9666bd39c647c53b` | 137066 |
| `controller/build/outputs/apk/debug/controller-debug.apk` | `f2285fb020236bbf0c93452f88f379e306992287e325b2dcceebd8362d6c278c` | 9090297 |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `bc15aa479fff133cb49e075937e758660a48997354d4904278963ca56dcb9b82` | 869370 |

Final physical reruns all returned instrumentation code-1 with named assertions:
`final-compileDiagnostics.txt` (actual ECJ plus two production snapshot builds),
`final-descriptor-invalid.txt`, `final-descriptor-lifecycle.txt`,
`final-local-repair.txt`, and `final-restart-numeric.txt`.
The last force-stops the controller before opening the real repair dialog.
Its additional tamper check corrupts only its own synthetic fixture origin's
checksum, calls the real gate approval method directly (never Send/provider),
requires rejection, restores the exact original bytes, and requires identical
approved payload plus unchanged journal sequence. It then checks edited drafts
and chat navigation. No credential read or cloud request is part of that test.

Restoration: `final-inspectNumeric.txt` records the unchanged pending fixture;
`final-closeNumeric.txt` terminalizes only that fixture and restores the saved
previous-project preference. Fixture project/chat/source data remain on phone.
Both harness packages were force-stopped and their PIDs were absent. Networking,
credential stores and the existing generated counter/data were not changed.

Commands (serial remains a local environment value):

```bash
source scripts/env.sh
./gradlew format --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew qualityCheck :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest :controller:assembleDebug :controller:assembleDebugAndroidTest :worker:assembleDebug :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
adb -s "$PHONE_SERIAL" shell am instrument -w -r -e repairRestart verifyNumeric dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
adb -s "$PHONE_SERIAL" shell am instrument -w -r -e diagnosticBinder invalid dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
adb -s "$PHONE_SERIAL" shell am instrument -w -r -e diagnosticBinder lifecycle dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
adb -s "$PHONE_SERIAL" shell am instrument -w -r -e localBuildOnly true dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
```

The ignored run helper `verify_install_harness.py` was implemented and used;
it verifies the four allowed packages and expected certificate, rejects worker
permissions, installs with retained data, and checks installed byte hashes.
It is not a placeholder or a claim that a new tracked helper exists.

## Changed paths

```text
protocol/build.gradle.kts
protocol/src/main/aidl/dev/smallwright/protocol/IBuildService.aidl
protocol/src/main/java/dev/smallwright/protocol/CompilerDiagnostics.java
protocol/src/test/java/dev/smallwright/protocol/CompilerDiagnosticsTest.java
worker/src/main/java/dev/smallwright/worker/BuildService.java
controller/src/main/java/dev/smallwright/controller/LocalBuildCoordinator.java
controller/src/main/java/dev/smallwright/controller/JavaCompileFailure.java
controller/src/main/java/dev/smallwright/controller/RepairOriginStore.java
controller/src/main/java/dev/smallwright/controller/ContextPreviewGate.java
controller/src/main/java/dev/smallwright/controller/GenerationRun.java
controller/src/main/java/dev/smallwright/controller/MainActivity.java
controller/src/test/java/dev/smallwright/controller/RepairOriginStoreTest.java
controller/src/test/java/dev/smallwright/controller/ContextPreviewGateTest.java
controller/src/test/java/dev/smallwright/controller/GenerationRunTest.java
controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java
controller/src/androidTest/java/dev/smallwright/controller/WorkerBinderFailureProbe.java
controller/src/androidTest/java/dev/smallwright/controller/LocalBuildCoordinatorProbe.java
controller/src/androidTest/java/dev/smallwright/controller/RepairRestartProbe.java
controller/src/androidTest/java/dev/smallwright/controller/UpdateContinuityProbe.java
docs/STATUS.md
docs/alpha-acceptance.md
docs/validation/compiler-diagnostics.md
docs/validation/repair-diagnostics-20261011.md
```

Pinned formatting reformats touched Java files; much of the diff is mechanical.
Pinned compiler/header inputs and their checksums were not edited. Read-only
independent review found the descriptor issue before its real readonly regression;
parent verified each finding and reviewed the final boundary code.

## Limits and next task

Live free-only repair, the frozen S23 Step project, full twenty-request alpha
suite, detached-laptop execution and other remaining acceptance gates stay
pending. No key configuration or $0-only provider-account setting was inspected
or asserted. Physical hardlink descriptor rejection remains untested. Next local
work is active-operation cancellation and interruption/storage recovery; later
live repair requires only phone-owned app credentials and exact app approval.
