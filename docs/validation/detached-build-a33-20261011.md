# Detached local build preparation — physical A33

On2026-10-11 UTC (2026-10-12 Dubai), preparation for a disconnected-laptop
local native build/install/persistence check passed on the user-authorized
Samsung Galaxy A33 5G SM-A336E, Android16/API36, ARM64 s5e8825, qemu0.
Base local checkpoint `002a22d`; tracked in
issue #19.

**User-reported detached execution and post-reconnect artifact/runtime checks
passed.** The original USB observer expired while connected, so there is no
agent-observed USB-absence event for this attempt; issue #19’s explicit transport
observation requirement remains unmet. Detached duration is unknown. This is
not a model-generation benchmark, network-disabled test, user-cohort or alpha pass.
The preparation and original pending observations below are historical.

## Contract and existing authorization

The user approved continued alpha work on the connected A33, local commits,
data-preserving harness updates and free-only requests. No model call is needed
for this case. The harness may be built on the laptop; generated source, tools,
compilation, signing, APK and execution remain on phone. Ordinary Android
installation confirmation is required for this case; debug installation would
not establish that gate. Only the user can physically remove the cable and
report what they completed while it was disconnected.

The initial source is the normal native Java/XML counter template with one
synthetic button-label change to `Detached add one`. The project is independent
of the S23 frozen Step state and prior A33 live requests. No developer repair of
an evaluation attempt, secret read, paid fallback, package reset/data clear,
source/APK export or remote build occurred. Previous selection is retained in
fixture pins; the owned project stays selected while awaiting the detached run.

Case: saved source1/no APK/no installed package → user physically unplugs USB →
normal app Show project tools / Build → verified Install readiness → user taps
Install and approves Android → standalone native count0→1→2 → reopen retains2 →
USB reconnect → fresh device preflight and exact saved source2/version1 signed
receipt/APK/policy/project signer plus installed hash/version verification.
Installation confirmation and behavior while disconnected are user-reported;
post-reconnect artifact/state observations are separately agent-verified. ADB
transport absence by itself does not establish physical cable removal or user
behavior. Any duration not captured on phone stays unknown. One Build only;
repeat clicks are not silently accepted as the same attempt.

## Actual preparation evidence

Ignored local root: `toolchains/alpha-detached-a33-20261011/`.
Fresh skill preflight at21:20:41.945864 UTC matched the physical A33; model,
unlocked/Awake state and explicit serial were reconfirmed before each operation.
Private device ID stays only in local records. All four installed hashes matched
at the normal-controller launch at2026-10-11T21:25:27.838535+00:00. Worker active-build
scratch was absent, inactive own worker was stopped before normal startup,
and the controller window received focus. No unrelated app/settings were changed.

| Case | Actual result | Raw / original handle evidence |
| --- | --- | --- |
| Verify before any fixture | Expected GUARD rejection, host0/code0; no Activity/build/model | `verify-01.txt`, `verify-handle-01.json` |
| One-shot prepare at2026-10-11T21:24:23.708115+00:00 | Actual New project UI, saved source1, no compiler/model | `prepare-01.txt`, `prepare-handle-01.json` |
| Armed reopen at2026-10-11T21:24:36.538159+00:00 | Exact project/source1/snapshot/editor restored; no receipt/app-v1.apk | `armed-01.txt`, `armed-handle-01.json` |
| Verify before user Build | Expected VERIFY_READY rejection, host0/code0; source1 remains | `verify-02.txt`, `verify-handle-02.json` |
| Normal app launch | All four installed hashes match; generated package absent; controller focused | `launch-prepared-01.json` |
| USB observer | Original exec session15932 retained; up to600s; read-only selected transport sampling | `usb-watch-exec-session-01.json`, `usb-detachment-01.json`, `usb-watch-host-01.txt` |
| Detached build/install/count/reopen | Pending user actions and after-reconnect verification | No pass evidence yet |

All completed phase host commands exited0 and had explicit expected Android
instrumentation completion codes; their original handles are terminal. Negative
cases test refusal, not failed detached builds and not functional evidence.
Preparation and armed phases require code-1 independently of host exit0.
The USB observer's original live handle must be inspected, not restarted solely
because a wait expires. A600-second observation timeout is not a detached pass.

## Exact initial identities and code

- Project: `app845a2934746e45e39a813c0691a77e38`.
- Package: `dev.smallwright.generated.app845a2934746e45e39a813c0691a77e38`.
- Displayed title: `A33 detached local build`.
- Saved source1 Java SHA-256: `676b06f23903baf678d32ecef06530d301dfaffef7aeae913cc9e46cb4dd3967`.
- No generated APK exists at preparation; no APK path/hash is fabricated.

Only test code changes: new
`controller/src/androidTest/java/dev/smallwright/controller/DetachedBuildProbe.java`
and opt-in `detachedBuild` route in
`controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`.
The probe implements guarded prepare, armed, saved artifact verify and safe
selection-restoration phases. It never clicks Build or Install, calls a model,
reads a key or fabricates detached behavior. Verification checks actual source2,
version1, manifest/package policy, real ApkVerifier signature, own project public
signer, immutable receipt and restored enabled Install readiness. That phase is
implemented but remains unrun on a completed detached artifact.
On cleanup it waits original work in30s chunks, without Stop/retry, then finishes
its Activity. Selection restoration requires verified state and current owned
selection; do not restore while the user is still performing the test.

Final installed host-built harness identities at preparation:

| APK path | SHA-256 | Bytes |
| --- | --- | --- |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `d4a3b57028eda6f9857293e033648a251f4ebb564f87ffe47c3125fa80f7905c` | 1085830 |
| `worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk` | `4019923938cb23ab850a6e27c63e843d5b56f74808fa5e7ee87482edea9f477b` | 154302 |
| `controller/build/outputs/apk/debug/controller-debug.apk` | `abf873d81bd6c9556eba40ded679db91fda7277d0ce719a7bd50425f7dd6c839` | 9093057 |
| `worker/build/outputs/apk/debug/worker-debug.apk` | `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850` | 21193573 |

Controller.test public certificate SHA-256:
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
Only controller.test was updated via verified `install -r`; installed bytes
matched. Production controller/worker and worker.test are unchanged.

Final source/helper pins (`tested-source-02-sha256.json`):

- `controller/src/androidTest/java/dev/smallwright/controller/DetachedBuildProbe.java`: `db060723bd2b0210f1e8eac6df89141c9d785acb802acab369840a526e45dacd`.
- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`: `2440ccae4264278a863a21a129347193b99e775a466c90823b06130edee757e8`.
- `toolchains/alpha-detached-a33-20261011/run_phase.py`: `48ba6681d642ead5556212a57df63fceac6ab2b58ba666edc32f59275667cc7e`.
- `toolchains/alpha-detached-a33-20261011/watch_usb.py`: `a72c2375a0356a22297318dd5b2b53ead1faded9bfd22906a3cbd4bafe7e8973`.
- `toolchains/alpha-detached-a33-20261011/launch_prepared.py`: `d070d1c2cde738d1c3e394ade1fa7706a3281a1768948509c39d478f7701bac2`.

Implemented local drivers: `run_phase.py`, `launch_prepared.py`, `watch_usb.py`.
They have unique existing-record guards, explicit serial selection and bounded
numeric/state evidence; no source/credential capture, ADB install of a generated
app or data erasure. They remain ignored local validation tools.

## Commands and host checks

```bash
source scripts/env.sh
./gradlew spotlessApply :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-diagnostics-20261011/verify_install_harness.py --serial "$PHONE_SERIAL" --record toolchains/alpha-detached-a33-20261011/probe-artifacts-01.json controller.test
python3 toolchains/alpha-detached-a33-20261011/run_phase.py --serial "$PHONE_SERIAL" --phase verify --label 01 --expected guard
python3 toolchains/alpha-detached-a33-20261011/run_phase.py --serial "$PHONE_SERIAL" --phase prepare --label 01
python3 toolchains/alpha-detached-a33-20261011/run_phase.py --serial "$PHONE_SERIAL" --phase armed --label 01
python3 toolchains/alpha-detached-a33-20261011/run_phase.py --serial "$PHONE_SERIAL" --phase verify --label 02 --expected unbuilt
./gradlew qualityCheck :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-detached-a33-20261011/launch_prepared.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-detached-a33-20261011/watch_usb.py --serial "$PHONE_SERIAL"
```

Historical unique labels cannot be replayed. Supported local JDK21.0.12.1+1 /
SDK36, capped offline Gradle, no downloads/global Java changes. Build passed45s
(72tasks:6executed/66up-to-date). Final quality passed44s (117tasks:8executed/
109up-to-date); formatting/lint ran, JVM tasks UP-TO-DATE (cached213controller/
57worker/4protocol tests), not fresh test execution. Lint errors0; existing
warnings72controller/2worker/0protocol. Generated source/build/APK bytes stayed
on phone. Full alpha remains incomplete.

Next: inspect original USB observer/user reply; fresh preflight after reconnect,
then verify this exact artifact and installed native state. Do not run verify as
an alternative to the unplugged user actions or reseed the owned project.


## Original USB observer terminal outcome — 2026-10-11

The original exec session15932 completed exit0 after its declared600-second
window. Every recorded selected-transport sample remained connected; no absent/
reconnect event was observed. `usb-detachment-01.json` terminalReason is
`observation deadline; not a detached test pass`. This terminal process was
polled to completion, not restarted on a wait timeout. The human test remains
pending and owned project selection is preserved. There is no new build,
installation, confirmation or count result to verify. Do not mark detached
execution passed or reseed the project based on this observer outcome.


## User completion and post-reconnect verification — 2026-10-11 UTC / October 12 Dubai

The user replied “Done count is 2” to the exact unplug → local Build → normal
Install / Android approval → standalone two increments → reopen → reconnect
instructions. These disconnected actions and Android consent are **user-reported**.
No debug installation, second Build, model call, source repair, APK/source export,
uninstall or data clear was performed during post-reconnect checks.

Fresh skill preflight at21:44:58 UTC matched physical SM-A336E, Android16/API36,
ARM64 s5e8825, qemu0. All four installed harness hashes matched their recorded
preparation artifacts before the verification probe. The generated app was
focused and showed Counter:2. The first UI observer incorrectly expected a
mixed-case button label; Android displayed DETACHED ADD ONE. Its failed host
assertion and `reconnect-generated-ui-01.xml` remain preserved. A separate
corrected observer (`reconnect-baseline-02.json`, `reconnect-generated-ui-02.xml`)
verified the actual uppercase button, count2, installed version1 and APK hash.
No app change or new evaluation attempt accompanied this observer correction.

With worker scratch absent and the receipt present, the completed controller was
force-stopped before the existing post-reconnect probe. `verify --label03` passed
with host0 and INSTRUMENTATION_CODE:-1. It verified unchanged prepared Java
content, source2/version1, manifest policy, actual ApkVerifier signature, per-project
public signer, immutable artifact/receipt and fresh enabled Install readiness.
The phone-built artifact is16860bytes:

- APK: `/data/user/0/dev.smallwright.controller/files/projects/app845a2934746e45e39a813c0691a77e38/artifacts/app-v1.apk`.
- SHA-256: `48a5fc12d2a2ac3f363c66f53a3bffdd862006712a19c1df5ba576af7971a06c`.
- Public project signer SHA-256: `31111d44bc2885661b6e18bd498ede7db3aceaad1daa082a01a85b236233adcf`.
- Installed package: `dev.smallwright.generated.app845a2934746e45e39a813c0691a77e38`, version1, identical APK hash.

At21:47:11 UTC, controller/worker and generated app were force-stopped, then only
the generated native MainActivity was opened. Counter:2 and DETACHED ADD ONE
remained visible; controller/worker were absent before and after observation.
`standalone-final-01.json` records exact installed/saved APK comparison and all
`tested-source-02-sha256.json` pins matched. This is an **agent-verified connected
fresh-process persistence check**, separate from the user’s detached reopen.

`restoreSelection --label01` then passed host0/code-1 and restored the prior
project selection without deleting the owned project, source, artifacts or app
data. Generated count remains2. Original failed pre-build verifier records, the
596-sample connected-only USB observer and the label-case observer failure remain
preserved. The original background notification’s VERIFY_READY rejection was an
expected pre-build negative case, not a failed user build.

Commands after fresh preflight (private serial stays local):

```bash
python3 toolchains/alpha-detached-a33-20261011/run_phase.py --serial "$PHONE_SERIAL" --phase verify --label 03
python3 toolchains/alpha-detached-a33-20261011/run_phase.py --serial "$PHONE_SERIAL" --phase restoreSelection --label 01
```

Exact raw/handle records: `verify-host-03.txt`, `verify-03.txt`,
`verify-handle-03.json`, `restore-selection-host-01.txt`,
`restoreSelection-01.txt`, `restoreSelection-handle-01.json`; preflight/UI/runtime
records remain under the same ignored local root. No harness rebuild was needed.
Host/device clocks recorded October11 UTC, corresponding to October12 Dubai.

**Remaining limit:** no observer was live during the reported unplug. We do not
retroactively infer USB absence from a reconnect or change the original observer’s
failed-to-observe outcome. The user-reported detached result and exact
post-reconnect checks are complete; #19 remains open for its stricter transport
observation criterion. No full alpha, phone-resource cap or S23 pass is claimed.
