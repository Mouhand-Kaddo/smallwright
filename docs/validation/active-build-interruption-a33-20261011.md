# Active-build controller termination and recovery on A33 — 2026-10-11

The physical A33 passed the bounded D3 case: commit a source replacement,
terminate the controller during an admitted real worker request, immediately
reopen the actual controller UI, rebuild the exact saved revision/build/version,
discard partial artifacts, and restore verified Install readiness. Another
controller restart restored the same signed artifact without a rebuild.
This is explicit debug force-stop during controlled worker input, not an
ECJ-phase interruption or spontaneous Android process eviction.

Issue: #7.
Base: local commit `183d90557d0c6ba3667726dfc1e5e5d21a940178`, plus the two
test-source changes below. Production controller/worker code is unchanged.
A temporary restricted session interrupted the handoff; restored access allowed
the remaining neighbor, installed-byte comparison and own-process cleanup below.

## Contract, device and isolation

- Physical Samsung Galaxy A33 5G, `SM-A336E`, Android16/API36, ARM64,
  hardware `s5e8825`, qemu property0. This is the user's connected second device;
  it supplies no new S23 evidence. Serial is retained only in ignored records.
- Read-only preflight was ready at16:08:24 UTC before this run and16:27:14 UTC
  before the neighboring check. Preflight is connection evidence, not a pass.
- A fresh random `binding-ui-probe-*` root isolates source, project registry,
  journal, cache, no-backup files and project preferences. Existing projects,
  installed counter data and credentials are untouched. Failed roots remain.
- One synthetic `replace_source` proposal uses the real parser, approval gate,
  dispatcher, source store, `GenerationRun`, SQLite journal and build coordinator.
  A valid seeded comment keeps the input below64KiB while making the ZIP larger
  than the disposable4096-byte test pipe. No model/API/account request occurs.
- The relay forwards the exact leased ZIP entirely on phone. It pre-fills its
  private pipe to EAGAIN before dispatch and requires actual reader progress,
  unfinished Binder work and the real worker's exact busy response. It holds EOF
  for a bounded interval while the host kills the controller. No compiler output
  or Binder result is substituted. The source hash is checked before forwarding.
- At ARMED, the real run is BUILDING at revision1/version1, sequence4, with
  actual snapshot/unsigned partial files and no completed APK/receipt.
  The host verifies the exact controller PID and a worker PID, force-stops only
  `dev.smallwright.controller`, and verifies controller absence while ADB is ready.
  Recovery opens immediately, with no worker-idle polling or inserted sleep.

## Cases and observed results

| Case | Actual observation | Outcome / raw record |
| --- | --- | --- |
| Original v1 preparation | AssertionError at AWAIT_ADMISSION; no ARMED signal; no externally verified active-request kill | Failed setup, `prepare-01.txt` |
| Corrected v2 preparation + external death | ARMED with pinned project/build/source; exact controller PID absent after force-stop; original instrumentation reports `Process crashed`, code0 | Expected termination only when paired with recovery; `prepare-02.txt`, `termination-and-recovery-02.json` |
| Actual UI recovery | Exact source1/build/version1; sequence5 READY_TO_TEST; valid native APK/signature/receipt; partials absent; Install fully visible and enabled; one synthetic proposal | Passed, code-1, `recover-02.txt` |
| Another explicit controller restart | Exact saved APK digest/receipt/readiness, no rebuild or installation | Passed, code-1, `ready-02.txt` |
| Duplicate prepare | Guard refuses the reserved fixture before creating another project | Expected rejection, code0, `guard-02.txt` |
| Prior Stop fixture's ready restart | Exact revision2 receipt/APK restored, no automatic rebuild/install | Passed, code-1, `neighbor-stop-ready-02.txt` |
| Completion-ordering neighbor | Real SQLite/filesystem receipt, Stop and readiness-write/interruption assertions; verification-only bytes, no compiler/signer | Passed after access restoration, code-1, `neighbor-completion-restored.txt` |

The first neighbor invocation omitted `-r`: its assertion prose passed, but
the host checker rejected the missing raw completion code. That output is
preserved as `neighbor-stop-ready.txt`; the corrected raw invocation supplies
the code-1 result above. No phone behavior was changed to resolve this host
result-formatting error. The subsequent host runner handle disappeared on the
session interruption; no completion result or live process was available to
justify claiming the second neighbor passed at that time. A fresh connection
preflight at16:36:16 UTC then allowed its successful separate invocation.

The initial host compile also failed because the proposal helper omitted a
declared checked exception (`probe-build-01.log`). After that correction,
builds02/03 passed. The original physical v1 fixture had committed revision1,
but never armed. Its inner admission failure was not captured, so no specific
compiler or production recovery cause is claimed. The corrected v2 fixture
adds observed pipe drainage and bounded admission checks; it preserves v1 data.
Build04 and the actual v2 physical rerun passed. These are test-fixture changes,
not a demonstrated production recovery fix.

### Exact recovered identity

```text
root: 18a4e360aca74f1c97e928e79b4f4a30
project: app4335e502c0c245c3af82faeeaca0cc94
package: dev.smallwright.generated.app4335e502c0c245c3af82faeeaca0cc94
run: interruption_687a9a8e23da4afda251ac3aeb9cf1b8
build: build_2dfc2ec19c634904957545ef12f00bec
source revision/version: 1 / 1
source SHA-256: 066e01d2cc7bf07407963c842a51e2f31e8baa02e3e6530ae53816fde30b64cd
signed APK SHA-256: 2d3a12a0fcf977340ac6fe514d38b9d5020ab63a9414ce20c66ac2057b6beb53
```

Phone-only generated APK, never exported or installed in this run:

```text
/data/user/0/dev.smallwright.controller/files/binding-ui-probe-18a4e360aca74f1c97e928e79b4f4a30/projects/app4335e502c0c245c3af82faeeaca0cc94/artifacts/build_2dfc2ec19c634904957545ef12f00bec/app.apk
```

ARMED was recorded16:19:42.641399 UTC; recovery invocation started
16:19:42.829828 UTC. No recovery-finished timestamp or elapsed build/recovery
measurement was recorded. Those timestamps do not establish recovery duration.

## Host validation and harness artifacts

Project-local JDK21.0.12.1+1/SDK36, one Gradle worker, no new downloads:

```bash
source scripts/env.sh
./gradlew qualityCheck :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

Final command: BUILD SUCCESSFUL in34s,146 tasks:8 executed/138 up-to-date.
Controller androidTest Lint analysis ran freshly; all three JVM test tasks and
the final APK assembly task were UP-TO-DATE. No fresh JVM test count is claimed.
Lint reports show zero errors,72 controller/two worker/zero protocol warnings;
report tasks were cached. Formatting and pinned compiler-input checks passed.
Earlier build04 freshly compiled/packaged the final test APK in27s.
`git diff --check` and unchanged tested-source/APK digest checks passed.

| Host-built harness path | SHA-256 |
| --- | --- |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `5c82fa1ba7198de0ce5d5ff1835e37995b64cc12eb10ac0ff9132041d9250dd1` |
| `controller/build/outputs/apk/debug/controller-debug.apk` | `f2285fb020236bbf0c93452f88f379e306992287e325b2dcceebd8362d6c278c` |
| `worker/build/outputs/apk/debug/worker-debug.apk` | `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850` |
| `worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk` | `a0bdf714f3ecffa311e7365d9342bf7b9c3449429b56f74e9666bd39c647c53b` |

Only controller-test was replaced, using verified `adb install -r`, preserving
app data. It is913958 bytes, package `dev.smallwright.controller.test`, with public
certificate SHA-256
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
Preinstallation package/certificate checks and installed-byte comparison are in
`probe-artifacts-02.json`. After the temporary ADB restriction ended, all four installed APK hashes were
compared again with the exact host artifacts. All matched; see
`final-installed-and-restoration.json`. No APK was pulled for this comparison.

Raw evidence and implemented local host runner:
`toolchains/alpha-interruption-20261011/`, including `run_interruption.py`,
`tested-source-sha256.json`, `fixture-v2-metadata.xml`, `final-quality-build.log`,
the preflight/artifact records and named case files. These are ignored local
records, not distributed scripts or publicly available captures.
All ADB calls were scoped to the selected serial. Instrumentation entrypoint:

```bash
adb -s "$PHONE_SERIAL" shell am instrument -w -r -e activeInterruption ready dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
```

Implemented phases are `prepare`, `recover`, `ready`. Prepare is deliberately
one-shot for the v2 pins: do not clear pins or reuse failed roots to rerun it.
The host runner pairs ARMED/PID death with successful recovery; code0 or a
`Process crashed` string alone is never a pass.

## Changed paths, restoration, limits and next task

```text
controller/src/androidTest/java/dev/smallwright/controller/ActiveBuildInterruptionProbe.java
controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java
docs/STATUS.md
docs/alpha-acceptance.md
docs/validation/active-build-interruption-a33-20261011.md
docs/validation/active-worker-stop-a33-20261011.md
```

Recovery/ready phases finish only their own isolated activity. Test roots,
throwaway project signing identities and existing app data remain. No keys,
credential views, full logs, APK bytes or personal UI captures were collected.
No generated installation, production installer consent, provider request or
phone-wide configuration change occurred. User-entered OpenRouter credentials
and the account's free-only setting have not been inspected or verified.

After a session interruption, ADB access was temporarily denied by the restricted
environment (`could not install *smartsocket* listener: Operation not permitted`;
`adb-access-check.txt`). A normal GitHub comment attempt also failed
(`issue-comment-result.txt`). These were host restrictions, not observed phone
unauthorized state. Once access was restored, fresh preflight matched the A33,
the completion neighbor passed, and both own controller/worker packages were
explicitly stopped. Both PID absences were verified while ADB reported device;
app data, test roots and project signing identities were retained. The final
installed-byte/restoration record establishes these observations. Local commits
are permitted again; this record accompanies the recovery checkpoint.

Next: continue deterministic busy-on-reconnect and bounded storage failure tests. Read-only review identified a possible old-worker busy
race on immediate recovery; this physical attempt reopened without an idle wait
and passed, but no deterministic busy-at-reconnect test or production fix is
claimed. ECJ-phase cancellation, spontaneous OS eviction, screen-lock/background
behavior, actual low storage, live free-only repair, cross-device recovery,
detached-laptop execution and the full alpha suite remain open. See the earlier
[Stop checkpoint](active-worker-stop-a33-20261011.md) and
[alpha acceptance](../alpha-acceptance.md).

Subsequent checkpoint: [deterministic busy-worker recovery](busy-worker-recovery-a33-20261011.md)
reproduced the admission failure, fixed saved-build handling and passed automatic
recovery, bounded manual Retry and both Stop cases. Its pending-run setup was
synthetic; it does not broaden this record's actual process-death coverage.
