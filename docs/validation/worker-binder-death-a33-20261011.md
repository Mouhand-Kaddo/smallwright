# Admitted worker Binder death and recovery — A33, 2026-10-11

The physical A33 passed actual worker termination during admitted snapshot input.
The controller remained alive, received genuine old-Binder death and
DeadObjectException, retained exact source1, removed its temporary outputs and
offered no APK/receipt/Install/export. A fresh-process reopen retained that state
and rebound the worker. Visible fresh Build then produced unchanged source
content at revision2/version1 through real native compilation/signing/receipt;
another fresh-process reopen restored the exact artifact without rebuilding.
Production code was unchanged. This is controlled worker force-stop during input,
not ECJ-phase cancellation or spontaneous Android eviction.

Issue: #10.
Base: local `c45121cc7fd43544868d04c87a1a79bc5951179c`, plus the three test
paths below. Raw records are ignored under `toolchains/alpha-binder-death-20261011/`.

## Contract and actual execution

- Physical Samsung Galaxy A33 5G, SM-A336E, Android16/API36, ARM64,
  hardware s5e8825, qemu0. Fresh read-only preflight was ready at17:29:48 UTC.
  Serial remains only in ignored records. All ADB commands were serial scoped.
- All four installed baseline APK hashes matched the prior checkpoint. Both own
  controller/worker processes were absent before this fixture started, so the
  worker terminated here belonged to this test. Existing projects, installed
  generated apps and user-entered credentials were not inspected or changed.
- One-shot v2 pins reserve a random isolated `binding-ui-probe-*` root. The real
  New project dialog creates the project; the real editor/visible Build commits
  a synthetic valid native source with a seeded comment below64KiB. Display
  assistance limits editor height and enables horizontal scrolling; it does not
  alter the full source or substitute a model result.
- The established test relay wraps the actual bound worker solely to control
  EOF. It verifies the exact leased ZIP's unique Main source SHA, prefills a
  disposable4096-byte pipe to EAGAIN before Binder dispatch, forwards every byte
  on phone, observes actual reader drainage and holds EOF open. The actual
  request must remain unfinished and the actual worker must reject a concurrent
  toolchain probe with exactly `ERROR: Worker busy` before ARMED is emitted.
- While the lease is held, the probe checks committed editor revision/text and
  the admitted ZIP hash. ProjectStore intentionally rejects concurrent reads;
  durable source is checked after the held call and lease settle.
- At ARMED, the host matches the emitted controller PID, observes a worker PID,
  force-stops only `dev.smallwright.worker`, verifies worker PID absence and an
  unchanged controller PID while ADB reports device. The controller's real old
  Binder DeathRecipient must fire, that proxy must be dead, and the held request
  must finish with an actual DeadObjectException in its failure chain. No Binder
  error, compiler output, source response or APK is fabricated.
- Releasing only the relay then lets the controller settle the real exception.
  Assertions require source1 exact hash, no manual-run journal creation, no APK,
  receipt, unsigned/snapshot/receipt partials or Install/export readiness, and a
  usable editor. The controller process does not crash during worker termination.
- Separate instrumentation invocations reopen the exact fixture; controller PID
  was absent before each invocation, recorded in `recovery-and-neighbor-results.json`.
  The failed-state reopen requires a newly bound worker and no automatic rebuild.
  Visible fresh Build commits unchanged content as revision2 and produces native
  version1: failed input never completed signing/publication or consumed an install
  version. Actual manifest/package/version/signature, immutable receipt digest and
  fully visible enabled Install are checked. Ready reopen checks exact digest and
  public signer, with no new source revision or rebuild. Install is never clicked.

## Cases, failures and review limits

| Attempt | Observed result | Classification / raw record |
| --- | --- | --- |
| v1 preparation | IllegalStateException before ARMED at START_AND_OBSERVE_ADMISSION; no worker stop performed | Failed probe setup, `prepare-01.txt`, `worker-death-01.json` |
| v2 admitted request + external worker death | Exact worker absence/controller survival; old Binder death + actual DeadObjectException; source1/no readiness/partials verified, code-1 | PASS, `prepare-02.txt`, `worker-death-02.json` |
| Failed-state fresh-process reopen | Source1 retained, new worker available, no APK/receipt/readiness or automatic rebuild, code-1 | PASS, `restart-02.txt` |
| Visible fresh Build | Source2/version1, real native APK/signature/receipt and visible Install, code-1 | PASS, `recover-02.txt` |
| Ready fresh-process reopen | Same source2/version1 artifact digest/public signer, code-1 | PASS, `ready-02.txt` |
| Duplicate prepare | One-shot guard rejected before another reservation/project, code0 at GUARD | Expected rejection, `guard-02.txt` |
| Prior admitted-worker Stop readiness restart | Exact existing revision2 receipt/readiness restored, code-1 | PASS, `neighbor-stop-ready.txt` |
| Prior receipt-write recovery ready restart | Exact existing same-signer source2/version2 receipt/APK restored, code-1 | PASS, `neighbor-receipt-ready.txt` |
| Completion ordering | Real SQLite/filesystem Stop/failure/readiness checks with verification-only bytes, code-1 | PASS, `neighbor-completion.txt`; no compiler/signer |

Parent source inspection found the v1 probe attempted a concurrent ProjectStore
read while the real build lease was held; `ProjectStore.enter` correctly rejects
that read as busy. V2 changes the observation to exact admitted ZIP/editor state
and checks disk after settlement. No product lease/security policy was weakened.
V1 was not armed or killed and proves no Binder-death outcome. Its root/pins and
any resulting files remain; run-owned processes were stopped only after its
host handle was terminal (`failed-v1-cleanup.json`). The v2 namespace is fresh.

Read-only review also identified cleanup ordering: if a host misses the worker
kill, releasing EOF before Stop could let a failed setup publish readiness. V2
requests Stop through the actual handler before releasing EOF in finally. This
is cleanup assistance, not a visible Stop test. A still-live compiler can finish
and leave an APK despite cancellation, as in the earlier Stop checkpoint; the
no-APK assertion applies to the **verified death case**, not every failed setup.
Death/failure checks and one-shot pins prevent such a failed setup from passing
or being replayed as successful recovery. That no-death cleanup branch was
reviewed by source inspection, not deliberately exercised as a physical case.

## Exact final fixture and generated artifact

```text
root: cfb0ebdcc98d45a4a05825f0b2f30b6a
project: app93697e7878de42549154e7cd09a4cd00
package: dev.smallwright.generated.app93697e7878de42549154e7cd09a4cd00
source SHA-256, same content at revisions1 and2:
67c598434366fb0df4c84a0b5e72f45a5e0335c5108924ad4f6dc27cd1257959
verified native version1 APK SHA-256:
2f0fb1f31967e67060f75ffb4e0b59d797b64c20bea9c828c0ad2124dc84ebb1
public project signer SHA-256:
e3a76dbf0039d0979e0a02ea72ed8539afe8913e97ae8aa7256d0532d7bb33be
```

Phone-only generated APK, never pulled or installed:

```text
/data/user/0/dev.smallwright.controller/files/binding-ui-probe-cfb0ebdcc98d45a4a05825f0b2f30b6a/projects/app93697e7878de42549154e7cd09a4cd00/artifacts/app-v1.apk
```

Worker termination was observed17:37:32.418020 UTC. This is an event timestamp,
not a build/recovery duration measurement.

## Commands, host results and harness artifacts

Project-local JDK21.0.12.1+1/SDK36, offline, one Gradle worker, no downloads:

```bash
source scripts/env.sh
./gradlew format :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew qualityCheck :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest :controller:assembleDebug :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-binder-death-20261011/run_worker_death.py --serial "$TEST_SERIAL" --label 02
adb -s "$TEST_SERIAL" shell am instrument -w -r -e workerDeathRecoveryOnly restart dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
```

The implemented local host runner observes ARMED and the exact live PIDs before
the scoped worker force-stop, then waits for the same original handle's result.
After successful `prepare`, invoke `restart`, `recover`, then `ready`. Preparation
is one-shot; never clear pins to retry. Use the freshly verified intended serial.
The relay holds at most15s; its older Stop caller retains its original3s default.
Death/settlement/UI wait limits are ceilings, not measured performance.

Probe build01 passed in30s; final build02 passed in29s,72 tasks:6 executed/
66 up-to-date; both freshly compiled/packaged the test APK. V1 test SHA was
`14bbf1a9b12e9a43b08f4dbc689427d767c63cbff30506c3f0295dd4f248869c`.
Final quality passed in31s,162 tasks:8 executed/154 up-to-date. AndroidTest Lint
analysis ran freshly; all JVM tasks, assembly and aggregate Lint reports were
UP-TO-DATE. No fresh JVM count is claimed. Cached reports remain zero Lint
errors,72 controller/two worker/zero protocol warnings. Formatting and pinned
compiler-input checks passed. Only package/certificate/byte-verified controller
test APKs were installed with `install -r`; production harness APKs were unchanged.

| Harness APK path | Bytes | SHA-256 |
| --- | --- | --- |
| `controller/build/outputs/apk/debug/controller-debug.apk` | 9093057 | `abf873d81bd6c9556eba40ded679db91fda7277d0ce719a7bd50425f7dd6c839` |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | 972086 | `043b89da7664aacdc71b223d37a00b2faeafe9a08b7dcd325ddc0a1e63d675a4` |
| `worker/build/outputs/apk/debug/worker-debug.apk` | 21193573 | `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850` |
| `worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk` | 137066 | `a0bdf714f3ecffa311e7365d9342bf7b9c3449429b56f74e9666bd39c647c53b` |

Public harness certificate SHA-256:
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
At17:41:07 UTC, all four installed APK hashes matched their host files and all
three source hashes matched `tested-source-sha256.json`. Both own packages were
stopped and PID absence verified while ADB reported device. App data, failed/
successful fixture roots and new project signer identity remain. Records:
`final-installed-and-restoration.json`, `final-cached-lint-counts.json`.

## Changed paths, limits and next task

- `controller/src/androidTest/java/dev/smallwright/controller/WorkerDeathRecoveryProbe.java`:
  actual worker-death/native-UI failure/reopen/retry/readiness and one-shot guards.
- `controller/src/androidTest/java/dev/smallwright/controller/ActiveWorkerStopProbe.java`:
  shares seven existing helpers/relay at package scope, adds bounded hold parameter
  and failure capture; older caller's default remains3s.
- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`:
  routes these phases and reports safe class/stage failure markers.
- `docs/STATUS.md`, `docs/alpha-acceptance.md`, this validation record:
  current evidence and remaining gates.

No production/worker/manifest/security policy/dependency/SDK changes, model/API
requests, account/key inspection, generated installation/export or phone-wide
settings changes. Normal project signing uses only the fixture identity through
the app's signing APIs; no private key bytes are read or logged. No timing,
storage, memory or thermal performance measurement is claimed. Ready Install
is not standalone functional acceptance. Complete Binder/ECJ-phase cancellation,
spontaneous eviction, actual low storage, live free-only repair, encrypted
cross-device recovery, detached-laptop execution, redistribution closure and
full alpha on both devices remain open. Next: screen-lock interruption/recovery,
with any required unlock performed on the phone by the user.
