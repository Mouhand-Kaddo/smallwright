# Screen-lock cancellation and recovery — A33, 2026-10-11

The physical A33 passed actual screen lock during admitted worker input. Real
keyguard/noninteractive state and the controller's lifecycle Stop token were
observed before input was released. A real native build/sign completed late
while locked, but the controller retained source1 without a receipt or
Install/export readiness. After the user unlocked the phone, fresh-process
reopen, visible fresh Build and another ready restart passed. Production code
was unchanged. No model request or generated installation ran.

Issue: #11.
Base: local `2239e2a`, plus the two test paths below. Raw evidence is ignored
under `toolchains/alpha-screen-lock-20261011/`.

## Contract and physical observations

Fresh preflight identified Samsung Galaxy A33 5G SM-A336E, Android16/API36,
ARM64, s5e8825, qemu0. All commands used its selected private serial. Baseline
verification matched all four prior installed APK hashes and found both own
processes absent; prior projects/data/signing identities were retained.

A one-shot random isolated root and actual New project dialog created the
fixture. Real editor/visible Build committed valid synthetic native source
with a seeded comment below64KiB. Editor height/scroll assistance affected
display only. The established relay verified the exact leased source hash,
prefilled a private4096-byte pipe to EAGAIN, forwarded actual bytes on phone,
observed remote reader drainage and held EOF. An unfinished single request and
exact actual `ERROR: Worker busy` response were required before ARMED.

The host matched the emitted live controller PID and observed a worker PID.
It verified awake/unlocked state before sending serial-scoped KEYCODE_SLEEP
once. Filtered system-policy/power observations recorded keyguard showing and
noninteractive power state while the same controller PID remained alive. The
original instrumentation independently required KeyguardManager locked,
PowerManager noninteractive and the **same real active Stop token requested**
before releasing EOF. No direct lifecycle callback, Stop click, fabricated
worker result, lock dismissal or PIN input was used for the passing case.

The APIs have distinct semantics: [isKeyguardLocked](https://developer.android.com/reference/android/app/KeyguardManager#isKeyguardLocked())
reports keyguard showing, including trivially dismissible locks;
[isInteractive](https://developer.android.com/reference/android/os/PowerManager#isInteractive())
reports whether the device is ready for interaction. This does not establish
credential authentication or Android DeviceIdle/Doze behavior. The host's
`mWakefulness=Dozing` value is a power-state observation, not a DeviceIdle pass.

Keyguard first appeared in host samples at17:50:56.147298 UTC and remained
showing/noninteractive at the final outcome17:50:58.157145 UTC. The probe
required a real OK result, valid native version1 APK/signature, exact source1,
no receipt/Install/export, no snapshot/unsigned/receipt partials, usable editor
and an explicit cancelled-build status. The late APK is intentionally retained;
absence of readiness does not mean compilation was aborted. These timestamps
are events, not performance measurements. Locked screens were never captured.

The user replied “Ready — phone unlocked.” Host policy/power checks verified
awake/unlocked state before recovery. Every subsequent invocation found the
controller PID absent beforehand, establishing genuine fresh-process reopen.
Failed-state reopen retained exact source1 and late APK digest without an
automatic build. Visible fresh Build produced unchanged content at source2/
version1, with actual native compilation/signature and immutable receipt.
Another reopen restored the same verified APK and public signer; enabled
Install was fully visible and never clicked.

## Cases and raw results

| Case | Actual result | Raw record |
| --- | --- | --- |
| Admitted input → real lock → lifecycle cancellation | PASS, code-1; actual locked-state late native result without readiness | `prepare-01.txt`, `screen-lock-01.json` |
| User unlock → fresh-process reopen | PASS, code-1; exact source1/late APK, no automatic build or readiness | `restart-01.txt` |
| Visible fresh Build | PASS, code-1; source2/version1 native signature/receipt/visible Install | `recover-01.txt` |
| Ready fresh-process reopen | PASS, code-1; exact ready APK/public signer | `ready-01.txt` |
| Duplicate prepare | Expected guard rejection, code0; no new fixture | `guard-01.txt` |
| Prior Binder-death readiness restart | PASS, code-1; exact prior artifact/signer restored | `neighbor-binder-ready-01.txt` |
| Prior active Stop readiness restart | PASS, code-1 | `neighbor-stop-ready-01.txt` |
| Completion ordering | PASS, code-1; real SQLite/filesystem verification-only bytes, no compiler/signer | `neighbor-completion-01.txt` |

No failed physical attempt or product defect was reproduced. Read-only review
found no concrete probe defect; parent inspected real StopToken/onStop and
result publication ordering. Review is source evidence, not a physical pass.
Finally cleanup requests Stop before EOF if preparation fails; that branch
was inspected but not deliberately exercised. One-shot pins remain intact.

## Exact fixture and artifacts

```text
root: aae6bb53d8a44d9194b0363ff66543d6
project: appa1d9b0e343b04b378cf659bf37acb701
package: dev.smallwright.generated.appa1d9b0e343b04b378cf659bf37acb701
source SHA-256, unchanged content at revisions1 and2:
a3c9cfcc1a1074ea409931698900c5fe82637f67809ef6a5355ed7043f929d5a
ready native version1 APK SHA-256:
888998ba0b00258391d2bf843a0451ecd3d8ebc669e0db8dab863e3ec876d7d4
public project signer SHA-256:
0c9b4183cda40c9d4af41bd5f0129101df49b2ca655864340ee607374b13348a
```

Phone-only generated APK, never pulled or installed:

```text
/data/user/0/dev.smallwright.controller/files/binding-ui-probe-aae6bb53d8a44d9194b0363ff66543d6/projects/appa1d9b0e343b04b378cf659bf37acb701/artifacts/app-v1.apk
```

| Host-built harness APK | Bytes | SHA-256 |
| --- | --- | --- |
| `controller/build/outputs/apk/debug/controller-debug.apk` | 9093057 | `abf873d81bd6c9556eba40ded679db91fda7277d0ce719a7bd50425f7dd6c839` |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | 985678 | `3649e72a1a7228767440e34d7cb2523623627f768e5f6e2c6115c11cf74a34e0` |
| `worker/build/outputs/apk/debug/worker-debug.apk` | 21193573 | `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850` |
| `worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk` | 137066 | `a0bdf714f3ecffa311e7365d9342bf7b9c3449429b56f74e9666bd39c647c53b` |

Only the test APK changed and was installed with data-preserving `install -r`
after package/public certificate/bytes verification. Public harness certificate:
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.

## Commands, checks and restoration

```bash
source scripts/env.sh
./gradlew format :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew qualityCheck :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest :controller:assembleDebug :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-screen-lock-20261011/run_screen_lock.py --serial "$TEST_SERIAL"
# Only after the user unlocks and policy/power checks confirm it:
python3 toolchains/alpha-screen-lock-20261011/run_unlocked_recovery.py --serial "$TEST_SERIAL"
```

Implemented host runners preserve handles/raw records, reject preexisting
attempt files and validate instrumentation codes and named outcomes. An
observation timeout is not permission to restart. Preparation is one-shot.
Relay holds at most15s; actual lock/cancellation wait is8s. These are bounds,
not measured performance.

Offline probe build passed in31s,72 tasks:6 executed/66 up-to-date. Final quality
passed in35s,162 tasks:8 executed/154 up-to-date. AndroidTest Lint analysis ran
freshly; JVM tasks, APK assembly and aggregate Lint reports were cached. No
fresh JVM test-count claim. Cached reports: zero errors,72 controller/two worker/
zero protocol warnings. Formatting and compiler-input checks passed. Local
JDK21.0.12.1+1/SDK36, one Gradle worker, no downloads.

At17:52:55 UTC all four installed hashes matched host files and both tested
source hashes matched. Both own packages were stopped with verified PID absence
and ADB device state; app data, fixture root and project signer identity were
preserved. The phone was verified awake/unlocked. Records:
`final-installed-and-restoration.json`, `final-cached-lint-counts.json`,
`tested-source-sha256.json`, `recovery-and-neighbor-results.json`.

## Changed paths, limits and next task

- `controller/src/androidTest/java/dev/smallwright/controller/ScreenLockRecoveryProbe.java`:
  physical lock observation, actual lifecycle cancellation and native recovery.
- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`:
  phase routing with safe class/stage failure markers.
- `docs/STATUS.md`, `docs/alpha-acceptance.md`, this record: current evidence.

No production, manifest, dependency, SDK or global setting changes; no account/
key inspection, model/API request, generated installation/export or APK pull.
Fixture signing used normal app APIs without inspecting private key bytes.
No phone build timing, peak memory, storage or thermal measurements are claimed.
This bounded screen-lock case leaves ECJ-phase/provider cancellation, spontaneous
eviction, actual ENOSPC, live free-only repair, cross-device/detached recovery,
redistribution closure and the twenty-request alpha suite open. Next local
task: collect measured A33 compiler timing/resource observations before setting
the alpha performance limits. Live repair remains deferred for local checks.
