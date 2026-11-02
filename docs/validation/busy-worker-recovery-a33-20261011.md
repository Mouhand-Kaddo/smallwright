# Saved-build recovery while the worker is busy — A33, 2026-10-11

The physical A33 reproduced a production failure: opening a saved BUILDING run
while the real worker was occupied changed that run to FAILED. The controller
now preserves its exact source/build/version, waits for bounded admission retries,
and offers Retry or Stop if the worker remains occupied. Four final physical
cases passed: automatic recovery, manual retry after exhaustion, Stop during
waiting and Stop after exhaustion. No model request or generated installation ran.

Issue: #8.
Base: local `452deb7f09873870a0fa58079c8cb660d8dba32e`, plus the six source
paths listed below. This follows the separate
[actual controller force-stop case](active-build-interruption-a33-20261011.md).

## Contract and actual execution

- Physical Samsung Galaxy A33 5G, SM-A336E, Android16/API36, ARM64,
  hardware s5e8825, qemu0. Read-only connection preflight was ready at
  16:46:20 UTC; private serial remains in ignored records. This is second-device
  evidence and supplies no new S23 evidence.
- Each invocation creates a random isolated `binding-ui-probe-*` root, real
  project/source store and SQLite journal. One synthetic parsed replacement
  proposal passes the real approval/dispatch/GenerationRun commit path.
  A distinct injected Error after durable BUILDING and before dispatch prepares
  source1/build/version1, sequence4. This preparation is explicit test assistance,
  **not process death**. No provider, account, saved key or credential is read.
- A different genuine authenticated Binder request occupies the separate-UID
  worker. Its deliberately malformed zero-filled ZIP input remains open at EOF;
  it uses no ProjectStore lease, so a controller-wide lease cannot mask busy
  admission. The disposable pipe is prefilled to4096 bytes and EAGAIN before
  dispatch. A successful later one-byte write proves actual reader drainage.
  The request must remain unfinished, and the actual worker must reject its
  toolchain probe with exactly `ERROR: Worker busy`.
- The real isolated MainActivity opens the saved BUILDING run. Assertions require
  visible waiting, enabled Stop, no signed artifact and unchanged journal identity.
  Closing only the fixture's writer releases the blocker, which must return its
  actual `ERROR: Snapshot archive is malformed` result. The recovered valid
  source then travels through the real Binder/native compiler and project signer.
  No result, compiler output or APK is substituted.
- Successful recovery requires unchanged source1/build/version1 and one proposal,
  sequence5 READY_TO_TEST, native manifest/package validation, a valid actual APK
  signature, matching immutable artifact/receipt digest, no partial outputs and
  fully visible enabled Install. Install is not clicked; bytes remain on phone.
- The Retry case keeps the worker occupied beyond the retry budget, verifies
  BUILDING sequence4/pending state, locked source and enabled Stop, expands the
  actual Project tools pane and clicks the visible Retry saved build button after
  release. Both Stop cases require CANCELLED sequence5, pending state cleared,
  source retained/editable, and no APK, receipt, partials or readiness after EOF.

## Failure history and final physical results

Raw records are under ignored `toolchains/alpha-busy-recovery-20261011/`.
Instrumentation code-1 is success; code0 alone is not a pass.

| Attempt | Observed result | Classification |
| --- | --- | --- |
| `red-physical-01.txt` | AWAIT_BUSY_RECOVERY, actual journal FAILED, code0 | Production RED on unchanged recovery code |
| `green-physical-01.txt` | Journal remained BUILDING, but waiting text assertion missed the normal `Smallwright` prefix, code0 | Probe observation error; prefix corrected |
| `green-physical-02.txt` | Exact automatic native recovery, code-1 | Initial GREEN |
| `retry-physical-01.txt` | AWAIT_BOUNDED_WAIT_END, journal BUILDING, code0 | Ambiguous probe failure; finer stage labels added |
| `retry-physical-02.txt` | VERIFY_RETRY_BUTTON, journal BUILDING, code0 | Retry was inside collapsed Project tools; actual expand/click flow added |
| `retry-physical-03.txt` | Exact native recovery after visible Retry, code-1 | Initial Retry GREEN |
| `final-stop.txt` | Stop during waiting; source retained, no late readiness, code-1 | Final PASS |
| `final-stopAfterWait.txt` | Stop after budget exhaustion; no late readiness, code-1 | Final PASS |
| `final-true.txt` | Exact automatic native recovery after worker release, code-1 | Final PASS |
| `final-retry.txt` | Budget exhaustion preserves identity; visible Retry recovers it, code-1 | Final PASS |

Failed fixture roots remain intact. The RED root was
`461b047791ee44bf9f35d5bf831458eb`, run
`busy_788e5265369046819215995bdad539fd`, build
`build_02bcc956610e406d9592c2d0d76f2f49`. The RED test APK SHA-256 was
`0e2e147a7a16d99d1e7d21ccc0fffe5e700cc8718f63386b7800fce41bbae8fb`;
its exact source digests are in `red-source-sha256.json`.

Read-only review also found a Stop ordering race between the busy-result UI
runnable and its finalizer. Parent inspection confirmed it and the fix clears
matching cancelled pending state regardless of the busy flag. Both actual Stop
cases pass; the rare intermediate runnable interleaving was not deliberately
scheduled, so no deterministic physical reproduction of that race is claimed.

Final case identities, all source1/version1:

| Phase | Root | Project | Build |
| --- | --- | --- | --- |
| Stop during wait | `fd110e666011468ebd4726d7e5ebaff9` | `appdd8f59abed1b4cd3bd73a9d9ea2ed3a9` | `build_c29093fcc6b842ab99cad16908a72c69` |
| Stop after wait | `12c8e55e3f1b4af7bc70366d39603b28` | `app3e9f73e4e7ac4694a740e481a0196f90` | `build_c240a7e023194ea9a5ff624c6b8b7538` |
| Automatic recovery | `fcda10a96eeb4ecb8a3a1fa93cb04e38` | `appd6068069833145dcb53fb935744f33bf` | `build_35ccf646bdd249e4a1f2368bb06827fd` |
| Manual Retry | `d2abe0dc18854477991ca1a814fa5939` | `app3649ad8c2b2142e89f5f5d1ec754da50` | `build_493bc4d9fb9c40078abe45472505a19b` |

The full run IDs are recorded in those four raw files. Actual generated APKs:

```text
/data/user/0/dev.smallwright.controller/files/binding-ui-probe-fcda10a96eeb4ecb8a3a1fa93cb04e38/projects/appd6068069833145dcb53fb935744f33bf/artifacts/build_35ccf646bdd249e4a1f2368bb06827fd/app.apk
SHA-256 ac8fe0fdc2ef64c2ce863214aa678983298434d2b9d04bead2ee86ab05957ff2
/data/user/0/dev.smallwright.controller/files/binding-ui-probe-d2abe0dc18854477991ca1a814fa5939/projects/app3649ad8c2b2142e89f5f5d1ec754da50/artifacts/build_493bc4d9fb9c40078abe45472505a19b/app.apk
SHA-256 708898431e562e5c0d16eedcb0854b35d1f536c1cb81a18d9fbeeb9d452eb70c
```

The existing exact controller-interruption receipt restart, admitted-worker Stop
receipt restart, queued Stop, background/lifecycle Stop and completion-ordering
neighbors also returned code-1 on the final test APK. The completion neighbor
uses real SQLite/filesystem verification-only bytes, without a compiler/signer.
Raw files: `neighbor-interruption-ready.txt`, `neighbor-stop-ready.txt`,
`neighbor-queued-stop.txt`, `neighbor-lifecycle-stop.txt`, `neighbor-completion.txt`.
The first host batch rejected lifecycle success prose because it searched for
capitalized `Stop` instead of the actual `Background: ... stopped` result. Its
raw code-1 was verified, and the not-yet-run completion case was then invoked
separately; no physical failure or repeated lifecycle invocation is implied.

## Implementation and changed paths

- `controller/src/main/java/dev/smallwright/controller/WorkerBusyFailure.java`:
  exact authenticated admission refusal has a distinct IOException type.
- `controller/src/main/java/dev/smallwright/controller/LocalBuildCoordinator.java`:
  recovery retries only that exact busy result, keeping pinned source/build/version.
  Each actual build attempt releases its lease and cleans partials before waiting.
  The budget is20 attempts with19 possible250ms gaps. The4.75s maximum nominal
  delay is **not a total wall-clock bound**; Binder/IO take additional time.
  Active/interrupt checks retain Stop. Ordinary new builds keep their old path.
- `controller/src/main/java/dev/smallwright/controller/MainActivity.java`:
  recovery busy exhaustion retains BUILDING/pending state and Stop, locks source
  editing and offers Retry. Retry resumes the saved identity. Stop clears cancelled
  pending state; existing completion/journal gates still control publication.
- `controller/src/androidTest/java/dev/smallwright/controller/BusyRecoveryProbe.java`:
  real busy-worker/native-UI four-case fixture and explicit assistance boundaries.
- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`:
  routes the four `busyRecoveryOnly` phases and reports safe failure stage/state.
- `controller/src/androidTest/java/dev/smallwright/controller/ActiveBuildInterruptionProbe.java`:
  shares four existing fixture helpers at package scope; no behavior change.
- `docs/STATUS.md`, `docs/alpha-acceptance.md`, this record and the historical
  termination record's forward link document this checkpoint.

No worker production/manifest/security policy, dependency or SDK changes.

## Commands and harness artifacts

Project-local JDK21.0.12.1+1/SDK36, offline, one Gradle worker:

```bash
source scripts/env.sh
./gradlew qualityCheck :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest :controller:assembleDebug :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
adb -s "$TEST_SERIAL" shell am instrument -w -r -e busyRecoveryOnly true dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
```

Repeat the last command with `retry`, `stop` or `stopAfterWait`. `TEST_SERIAL`
must be the freshly verified intended phone. Initial test builds and intermediate
fix builds are preserved as `probe-build-01.log` through `probe-build-06.log` and
`fix-build-01.log`/`fix-build-02.log`. Final verification is recorded below.

| Host-built harness APK | Bytes | SHA-256 |
| --- | --- | --- |
| `controller/build/outputs/apk/debug/controller-debug.apk` | 9093057 | `abf873d81bd6c9556eba40ded679db91fda7277d0ce719a7bd50425f7dd6c839` |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | 941206 | `503296b7299061a2ffa4800f4e4231b2f9c48edbf649f4723d7a53fd4342ec3d` |
| `worker/build/outputs/apk/debug/worker-debug.apk` | 21193573 | `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850` |
| `worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk` | 137066 | `a0bdf714f3ecffa311e7365d9342bf7b9c3449429b56f74e9666bd39c647c53b` |

Public harness certificate SHA-256:
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
Verified package/certificate/byte identity preceded data-preserving `install -r`
of the two controller harnesses. The worker APKs were not updated. No signing
material was inspected; generated project identities remain phone-local.

Final host command: BUILD SUCCESSFUL in1m55s,162 tasks:15 executed/147 up-to-date
(`final-quality-build.log`). All213 controller JVM tests ran freshly after the
production fix, with zero failures/errors/skips; report timestamps range from
17:07:58.342 to17:09:09.216 UTC. Worker57/protocol4 test tasks were UP-TO-DATE;
their earlier successful reports were not new execution. Controller main,
androidTest and unit-test Lint analysis/report ran freshly: zero errors,
72 warnings. Worker/protocol Lint was cached: zero errors, two/zero warnings.
Spotless and compiler-input checksum verification passed. Final assembly was
UP-TO-DATE, with APK hashes unchanged from the physically tested artifacts.

At17:09:36 UTC, all four installed harness APK hashes were compared against
their host files, and all six source hashes matched
`final-tested-source-sha256.json`. Both own controller/worker packages were then
force-stopped, with PID absence verified while ADB reported device. App data,
failed/successful fixture roots, project signing identities and the prior installed
counter were retained. No generated APK bytes were pulled or installed.
Records: `final-installed-and-restoration.json`, `final-host-counts.json`,
`final-case-results.json`, `neighbor-results.json`. `git diff --check` passed.

## Limits and next task

No recovery/build duration, cold/warm timing, memory, storage or thermal measurement
is claimed. Probe deadlines are test ceilings. This busy admission case uses
controlled input and synthetic pending setup, not an ECJ-phase interruption,
provider cancellation or spontaneous OS eviction. Ready Install is not a generated
installation or functional app pass. The stored OpenRouter key/free-only account
configuration was not inspected and live repair remains deferred.

Next local task: bounded storage failure/recovery. Full twenty-request alpha on
both phones, source/toolchain redistribution closure, live free-only repair,
cross-device encrypted recovery and detached-laptop execution remain open.
