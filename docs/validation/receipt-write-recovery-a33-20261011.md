# Native receipt-write failure and recovery — A33, 2026-10-11

The physical A33 passed a bounded filesystem publication failure through the
actual controller Build UI. Real native compilation/signing succeeded, a tiny
nonempty directory obstructed the receipt destination, and receipt IO failed
without exposing Install/export readiness or losing saved source. An externally
verified controller force-stop preserved that failed state. After moving the
fixture obstruction aside, a visible fresh Build produced source2/version2 with
the same project signer; another force-stop restored the exact valid receipt/APK.
Production code was unchanged. This is **not actual low storage or ENOSPC**.

Issue: #9.
Base: local `39acf29f4ccfc49f8384890ef156e2a0598b189b`, plus the two test-source
paths below. Raw evidence is ignored under
`toolchains/alpha-storage-recovery-20261011/`.

## Contract, assistance and actual execution

- Physical Samsung Galaxy A33 5G, SM-A336E, Android16/API36, ARM64,
  hardware s5e8825, qemu0. Read-only preflight was ready at17:13:22 UTC.
  Serial remains only in ignored local records. All ADB commands were serial
  scoped; exact model/connection was checked before external stops.
- A one-shot v2 fixture reserves a random `binding-ui-probe-*` root, uses the
  real New project dialog, edits synthetic default native source through the
  real editor and clicks fully visible Build. The root isolates project registry,
  source, journal, cache, preferences and no-backup files from user projects/keys.
- Only the new project's receipt destination is obstructed: a directory named
  `artifacts/verified-artifact.properties` containing a tiny fixture marker.
  This creates actual filesystem rejection during atomic receipt publication.
  It does not fill storage, change phone settings or inject a canned exception.
- After actual authenticated separate-UID worker compilation and project signing,
  the probe requires a valid native v1 APK/manifest/signature and the actual UI's
  failed-build message. A second call to the same real receipt publication path
  with that verified artifact must throw IOException. The directory/marker must
  remain, while no valid receipt or temporary snapshot/unsigned/receipt partial
  is present. Install/export are disabled; editor/Build remain usable.
- External-stop arm phases reopen and validate the exact fixture, emit their
  controller PID and hold only the instrumentation thread for at most15s. No
  source/build/key mutation runs during that hold. The host matches the emitted
  PID, force-stops only the controller and verifies PID absence while ADB is
  ready. Original instrumentation must report Process crashed/code0; this is
  expected termination only when paired with successful separate restart checks.
- The first post-stop phase keeps the obstruction in place and requires exact
  source1, unchanged failed-v1 digest/public signer, unusable receipt, no false
  readiness and usable editor/Build. Retry moves only the fixture obstruction to
  `receipt-obstruction-evidence` by atomic rename, preserving its marker. Actual
  visible Build recompiles the retained source; its unchanged content becomes
  revision2/version2. The saved version had advanced before the failed receipt
  write, so retry does not reuse v1.
- Ready checks require a valid native v2 APK, unchanged v1 artifact, same public
  project signer, exact immutable v2 artifact/receipt and fully visible Install.
  Another external stop/reopen restores the same digest without a new revision
  or build. Install is never clicked. All generated APK bytes remain on phone.

## Cases and failure history

| Attempt | Observation | Classification / raw evidence |
| --- | --- | --- |
| Initial test build | Android compile SDK lacks `Files.writeString`; javac rejected the probe | Setup compile failure, `probe-build-01.log`; changed to UTF-8 `Files.write` |
| Initial v1 fixture | Actual native v1/receipt IO failure, source retained/no readiness, code-1 | Passed initial case, `prepare-02.txt`, test APK `026e053535abbfd3336401f351255099aa4238b644dc687ce5cb5152f777c46c` |
| Initial external-stop precondition | Controller already absent after completed instrumentation | Host setup failure before mutation; no force-stop/restart claim, `failure-force-stop-attempt-01.json` |
| Direct ADB debug-activity launch | SecurityException: activity is not exported | Rejected setup; no security/manifest change, `arm-setup-failures.json` |
| Final v2 preparation | Valid native v1 then actual receipt IO failure; source1 saved, no readiness, usable UI, code-1 | PASS, `prepare-03.txt` |
| Failure-state external stop | Exact armed PID matched and absent after controller force-stop; Process crashed/code0 | Expected termination, `failure-force-stop-03.txt`/`.json`, paired with next case |
| Reopen failed state | Exact source1/v1 retained, obstruction intact, no rebuild/readiness, code-1 | PASS, `restart-03.txt` |
| Move fixture obstruction and visible Build | Real native source2/version2 with same signer, valid receipt and visible Install, code-1 | PASS, `recover-03.txt` |
| Ready-state external stop | Exact armed PID absent; Process crashed/code0 | Expected termination, `ready-force-stop-03.txt`/`.json`, paired with next case |
| Ready reopen | Exact v2 receipt/APK restored without rebuilding, code-1 | PASS, `ready-03.txt` |
| Duplicate preparation | One-shot guard rejected before another reservation/project, code0 at GUARD | Expected rejection, `guard-03.txt` |
| Prior controller-interruption receipt restart | Exact source1/build/version1 receipt/readiness restored, code-1 | PASS, `neighbor-interruption-ready.txt` |
| Completion ordering | Real SQLite/filesystem failure/Stop/readiness assertions with verification-only bytes, code-1 | PASS, `neighbor-completion.txt`; no compiler/signer |
| Busy-worker recovery | Actual busy admission then real native source1/build/version1 recovery, code-1 | PASS, `neighbor-busy.txt` |

The initial v1 fixture remains intact at root
`130cc3c0616747d5bf2fe1644206abad`, project
`appe85bba6cfe2e4f3ab6b604455062801f`. It was not cleared or reused.
The final v2 namespace ran the full sequence on one final test APK. Read-only
review and parent inspection found no production defect requiring a fix.
The first direct activity launch was an avoidable host setup mistake; the final
instrumentation arm phases preserve the activity's non-exported boundary.

## Exact final identity

```text
root: ace29f4ac69a4e31a8c23714ec6ed945
project: app764af85143874be3819f334d6618fe54
package: dev.smallwright.generated.app764af85143874be3819f334d6618fe54
source content SHA-256, identical at revisions1 and2:
55ec697b1209e264d00eb864106ff45de4cc8c051c9a897beffb03199b81fe8f
failed-publication native v1 APK SHA-256:
19fb36aa39e1948b774ac1c747d2536afd061d7b0e2ba286484ef40cf5ed7611
recovered native v2 APK SHA-256:
93db274bfcb9385f0cc908491c46c9e112bd438a461218f594a77db60c68be8a
public project signer SHA-256, same for v1/v2:
a23965bdf5c77a572896c7f3d7d15f9cac78b031ef5946e7606b9264d06c531c
```

Phone-only generated APK paths, neither exported nor installed:

```text
/data/user/0/dev.smallwright.controller/files/binding-ui-probe-ace29f4ac69a4e31a8c23714ec6ed945/projects/app764af85143874be3819f334d6618fe54/artifacts/app-v1.apk
/data/user/0/dev.smallwright.controller/files/binding-ui-probe-ace29f4ac69a4e31a8c23714ec6ed945/projects/app764af85143874be3819f334d6618fe54/artifacts/app-v2.apk
```

Failure-state stop was observed17:21:45.919554 UTC; ready-state stop was
observed17:22:23.656807 UTC. These are event timestamps, not duration measurements.
The busy neighbor generated its own separate phone-local artifact, digest
`9f450290e64a9e66f1fc7f78ea621db5abe6061d4777b64935943ac27c2ed51c`.

## Commands, builds and exact harnesses

Project-local JDK21.0.12.1+1/SDK36, offline, one Gradle worker, no downloads:

```bash
source scripts/env.sh
./gradlew format :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew qualityCheck :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest :controller:assembleDebug :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
adb -s "$TEST_SERIAL" shell am instrument -w -r -e receiptWriteRecoveryOnly prepare dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
```

Final phase order: `prepare` → `armFailureStop` plus externally verified stop →
`restart` → `recover` → `armReadyStop` plus externally verified stop → `ready`.
Use the freshly verified intended `TEST_SERIAL`. Preparation is one-shot and
must never clear pins to rerun. The implemented local external-stop runner is
`toolchains/alpha-storage-recovery-20261011/run_external_stop.py`; it observes
ARMED, requires a live exact PID, records the scoped force-stop and waits for
the original handle's termination. Its15s observation/arm limits are ceilings,
not measured recovery durations. A missing marker is not permission to restart
an unverified live request.

Build02 passed in29s; final probe build03 passed in27s,72 tasks:6 executed/
66 up-to-date. Both freshly compiled/packaged the test APK. Only the verified
controller test APK was installed with data-preserving `install -r`; package,
public certificate and installed bytes were verified. Controller/worker harness
production APKs remained unchanged from the prior checkpoint.

| Harness APK path | Bytes | SHA-256 |
| --- | --- | --- |
| `controller/build/outputs/apk/debug/controller-debug.apk` | 9093057 | `abf873d81bd6c9556eba40ded679db91fda7277d0ce719a7bd50425f7dd6c839` |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | 957778 | `893ec8f855404ef7aac9683ab261ee5b49098e22cdff570d7901e8cb26ad072e` |
| `worker/build/outputs/apk/debug/worker-debug.apk` | 21193573 | `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850` |
| `worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk` | 137066 | `a0bdf714f3ecffa311e7365d9342bf7b9c3449429b56f74e9666bd39c647c53b` |

Public harness certificate SHA-256:
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.

Final host command passed in33s,162 tasks:8 executed/154 up-to-date
(`final-quality-build.log`). Controller androidTest Lint analysis ran freshly;
all three JVM tasks, APK assembly and aggregate Lint reports were UP-TO-DATE.
No new JVM execution/count is claimed. Cached reports remain zero Lint errors,
72 controller/two worker/zero protocol warnings. Formatting and compiler-input
checksum checks passed. No dependencies or compiler inputs were downloaded.

At17:25:16 UTC, all four installed harness APK hashes matched their host files,
and both tested-source hashes matched `tested-source-sha256.json`. Both own
controller/worker packages were then force-stopped and PID absence verified
while ADB reported device. App data, both fixture versions, failed/successful
artifacts, preserved obstruction and project signer identities remain. No
generated APK was pulled or installed. Records:
`final-installed-and-restoration.json`, `final-cached-lint-counts.json`,
`ready-and-neighbor-results.json`. `git diff --check` and local documentation
link checks passed.

## Changed paths, limits and next task

- `controller/src/androidTest/java/dev/smallwright/controller/ReceiptWriteRecoveryProbe.java`:
  actual native UI failure/restart/retry/ready and bounded external-stop fixture.
- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`:
  routes these phases and reports safe stage/class failure markers.
- `docs/STATUS.md`, `docs/alpha-acceptance.md`, this validation record:
  current evidence and remaining gates.

No production, worker, manifest, compiler policy, dependency or SDK changes.
No model/provider/account/key inspection, generated installation, APK pull,
phone-wide setting change or storage filling. Normal project signing uses only
the fresh fixture's identity through the app's signing APIs, with public digest
comparison; no key bytes are read or logged. Failed artifact/obstruction/roots
are preserved. No build/recovery latency, memory, thermal or storage measurement
is claimed. Visible Install does not establish standalone functional acceptance.

Actual low storage/ENOSPC, ECJ-phase/provider cancellation, spontaneous eviction,
screen-lock behavior, live free-only repair, full alpha on both devices,
cross-device encrypted recovery, detached-laptop execution and redistribution
closure remain open. Next local task: admitted-request Binder disconnect and
retained-source recovery, within the same phone-only/security boundaries.
