# Testing

For harness formatting and Android Lint, use the pinned tools and
`format` / `qualityCheck` tasks in [CODE_QUALITY.md](CODE_QUALITY.md).
These checks complement the execution tests below.

## Current test artifacts and environment

Use the ignored project-local JDK and SDK through `scripts/env.sh`. Restore the pinned inputs
described in `docs/toolchain-candidates.md` and `toolchain/manifest.json` if `toolchains/` is absent.
Create the ignored harness signing identity once. The Android 16 x86_64 emulator is development
evidence only; it is not a pass for the ARM64 Galaxy S23 Ultra.

```bash
source scripts/env.sh
./scripts/create-signing.sh
./gradlew :controller:testDebugUnitTest :worker:testDebugUnitTest :controller:lintDebug :worker:lintDebug :controller:assembleDebug :worker:assembleDebug --no-daemon --offline
```

Built APKs are `controller/build/outputs/apk/debug/controller-debug.apk` and
`worker/build/outputs/apk/debug/worker-debug.apk`. Install the worker first. On a physical phone,
use Android's Settings flow to allow the controller as an unknown-app source when prompted; do
not grant it through an ADB app-op for the user-facing test.

## Physical technical-debt regressions

The disposable runner supports the authorized connected Android 16 ARM64 phone.
It installs all four local harness/test APKs in place, preserving app data,
and records raw instrumentation output under ignored `toolchains/debt-validation/`.
It does not read saved model credentials, use existing project signers, or
install/update a generated app. The compiler probe creates its own project and
signer; UI and receipt probes use isolated project/journal/preference roots.

```bash
source scripts/env.sh
./gradlew :controller:assembleDebug :worker:assembleDebug :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m'
python3 scripts/run-tech-debt-phone-tests.py --serial <S23 serial>
```

The six sequential probes cover a held-open snapshot deadline and subsequent
real build; responsive delayed project binding and selection persistence;
Stop/receipt/readiness ordering with real SQLite/filesystem persistence;
real on-phone compilation/signing and simulated-proposal repair; intentional
worker/child death; and fresh-process compiler recovery. The kill probe's
`Process crashed` result is expected only when the subsequent recovery probe
validates its deadline receipt, child termination and actual compilation.

Completion fault injection uses verification-only bytes and an injected
interruption, not a controller process crash. The worker termination check
uses a 500ms test deadline; production whole-request admission uses 120s.
This suite passed on the physical S23 Ultra on 2026-10-02. See
[STATUS.md](STATUS.md) for exact evidence and remaining release limitations.
Other runners below keep their existing emulator-only guards.

## Emulator development run

```bash
source scripts/env.sh
ANDROID_AVD_HOME="$PWD/toolchains/avd" "$ANDROID_HOME/emulator/emulator" -avd smallwright-api36 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect
adb -s emulator-5554 install -r worker/build/outputs/apk/debug/worker-debug.apk
adb -s emulator-5554 install -r controller/build/outputs/apk/debug/controller-debug.apk
adb -s emulator-5554 shell am start -n dev.smallwright.controller/.MainActivity
```

The AVD must exist under ignored `toolchains/avd/smallwright-api36`; `docs/STATUS.md` records the
system image used in the verified run. In the controller, edit the Java source, tap Build, inspect
a real compiler diagnostic for a syntax error, fix it, build again, tap Install verified APK and
approve Android's installer UI. Launch the generated counter and increment it. Edit `Counter: `
to `Updated: ` in the controller source, rebuild and install the update. The changed label and
retained count are required. Do not uninstall the generated counter between installations.

The bounded manifest-policy device test uses real host-built APK manifest fixtures. It tests
the controller validator, not the generated-app compiler:

```bash
source scripts/env.sh
python3 scripts/artifact-policy-fixtures.py
./gradlew :controller:assembleDebugAndroidTest --no-daemon --offline
adb -s emulator-5554 install -r controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk
./scripts/run-artifact-policy-test.py
# The wrapper defaults to emulator-5554; use --serial emulator-N when needed.
```

The worker confinement test is also **emulator only**. It creates a new dummy file in the
controller's private storage, builds a separately signed test app, and leaves those test
artifacts on the disposable emulator. It refuses a non-emulator target. Run it after creating
the ignored, throwaway probe signing identity:

```bash
source scripts/env.sh
./scripts/create-security-probe-signing.sh
./scripts/run-confinement-probes.sh
```

The credential-store probe is **emulator only** and uses a dummy value. It exercises the
Android Keystore round trip in the controller UID, then removes its test data. The runner
checks the raw instrumentation result and refuses a non-emulator target:

```bash
source scripts/env.sh
./gradlew :controller:assembleDebug :controller:assembleDebugAndroidTest --no-daemon --offline --max-workers=1
./scripts/run-credential-store-test.sh
```

The Binder failure probe is **emulator only**. After assembling both debug and
androidTest APKs, it installs them on the disposable API 36 x86_64 emulator,
forces an oversized snapshot through the real worker service, checks both
transferred pipes close, rejects overlapping Binder work, checks worker scratch,
and runs a later real compile/sign build. It refuses a physical device:

```bash
source scripts/env.sh
./gradlew :controller:assembleDebug :worker:assembleDebug :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1
./scripts/run-worker-binder-failure-test.sh
```

The signing-lineage probe is **emulator only**. It creates and removes fresh
test-only Android Keystore aliases, checks that apksig can create a restorable
old-to-new lineage, and never touches the project signer or installs an app:

```bash
source scripts/env.sh
./gradlew :controller:assembleDebug :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1
./scripts/run-signing-lineage-probe.sh
```

The APK rotation probe is **emulator only** and requires a fresh dedicated
API 31, 32 or 36 x86_64 AVD whose name begins `smallwright-rotation-`. It refuses to
replace an existing generated package or its retained package settings. After building both debug APKs and the
controller androidTest APK, start the dedicated emulator and run:

```bash
source scripts/env.sh
python3 scripts/run-signing-rotation-emulator.py
# Use --api 31 or --api 32 on the matching fresh AVD.
# Use --direct on a separate clean AVD to test v1→v3.
# Use --portable on a separate clean AVD to rotate to a disposable software
# signer, seal/open its test key, and sign v3 through the restored key path.
# Use --product on a separate clean AVD to exercise ProjectSigner and its
# encrypted disposable signer store, plus installed-app rejection preflights.
```

The runner compiles three generated APKs inside the emulator, installs them
with test-only ADB commands, taps the counter and checks data retention and
Android's signer history against the installed v1 certificate. It leaves the APKs and installed generated app on
that emulator. This does not test the user-facing PackageInstaller flow.

To exercise the positive installed-app migration preflight, use a different,
fresh API 31 or 36 x86_64 AVD named `smallwright-rotation-installed-*` and run
`python3 scripts/run-installed-signer-migration-emulator.py --api 31` (or `36`).
This runner builds and installs v1 first, then activates the portable signer
while v1 is installed, builds and installs v2, and checks retained counter data
and Android's signing history. It refuses an already installed or retained
generated package. It uses disposable test identities and ADB installs only.

The inactive recovery-envelope component can be checked with dummy bytes on
an API 31, 32 or 36 x86_64 emulator. After assembling the controller debug and
androidTest APKs, run `./scripts/run-recovery-envelope-test.sh 31` (or the
matching API level). It installs only the controller and test APKs and checks
Unicode-passphrase round trip, wrong-passphrase rejection and tamper rejection.
This does not export or import any actual project identity.

The inactive portable-signing test uses only a fresh in-memory software RSA
key. On an API 31, 32 or 36 x86_64 emulator with both controller APKs
assembled, run `./scripts/run-portable-signer-test.sh 31` (or the matching API
level). It checks certificate creation, key restore and signature matching,
without signing or installing a generated app.

The inactive recovery bundle and durable signer store have disposable tests on
API 31 and 36 x86_64 emulators. After assembling both controller APKs, run
`./scripts/run-recovery-bundle-test.sh 31` and
`./scripts/run-portable-signer-store-test.sh 31` (or use `36` on a matching
emulator). The bundle runner checks sealed dummy sources and a fresh test key;
the store runner checks Android Keystore wrapping, tamper/missing-key failure
and a valid public lineage. Neither action exports/imports an actual project
or changes the production project signer. Both use test-only identities.

The encrypted recovery export preparation has a disposable Android runtime
probe on a fresh API 31 or 36 x86_64 AVD named
`smallwright-recovery-export-*`. After building the controller debug and
androidTest APKs, run `./scripts/run-recovery-export-test.sh 31` (or `36` on a
matching AVD). The script refuses an installed or retained generated package.
It verifies an invalid phrase cannot activate a signer, then seals a three-file
snapshot twice with the same portable identity and checks the exact bytes after
opening. It does not write an external recovery document.

For the user-facing export, install matching controller and worker debug APKs
on a disposable emulator, open the controller, tap **Create encrypted recovery
file**, enter the same test phrase twice, and choose a location in Android's
document picker. Success requires the controller to display **Encrypted
recovery file saved and verified** after reading back the selected document.
Keep the file on the emulator; do not move a generated app or signer to the
laptop. This action may migrate the current project signer even if the picker
is later canceled.

For the import transaction, use a dedicated fresh API 31 or 36 x86_64 AVD
named `smallwright-recovery-adoption-*` with no generated-app package state.
After assembling the controller debug and androidTest APKs, run
`./scripts/run-recovery-adoption-test.sh 31` or `... 36`. The guarded script
installs the local controller harness, interrupts an import after encrypted
signer storage, force-stops the controller, and verifies exact source bytes,
signer, version floor and rejection of another bundle on restart. It also
exercises a disposable orphaned Android Keystore wrapping key. It retains
the emulator's test state for inspection.

For the visible import path, install the matching controller, worker and
androidTest APKs on a dedicated clean emulator. The test instrumentation
argument `-e recoveryImportDocument true` writes a throwaway encrypted `.plbr`
to that emulator's Downloads; its test phrase is defined in
`RecoveryImportDocumentProbe`. Open the controller, tap **Restore encrypted
recovery file**, choose that file in Android's picker, enter the test phrase,
review package/revision/version/file count and signer prefix, then tap
**Restore**. Build the restored source through the worker and use the
controller's **Install verified APK** button plus Android's visible
confirmation. A further build after controller restart should use a higher
version and the same project signer. Never use an existing personal recovery
file for this disposable test.

Import requires an untouched default controller project with no prior signer,
generation history or consumed version floor. An installed generated app is
accepted only when its single current signing certificate matches the
recovered signer or an earlier certificate in the recovered lineage with
installed-data capability. The next build version rises above both saved and
installed versions. Import does not overwrite the installed app or its data;
Android still requires explicit installation confirmation. No cross-device
transfer or physical-device pass has been verified yet. A recovery file carries
source and the update signer, not generated-app data, model credentials or run
history.

For the installed-app emulator check, use a **fresh** API 31 or 36 x86_64 AVD
named `smallwright-rotation-installed-recovery-api31` or
`smallwright-rotation-installed-recovery-api36`; verify the generated package
has neither an installed APK nor retained package state. Install the locally
built worker, controller and controller androidTest APKs. Run the disposable
instrumentation phases in this order using component
`dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation`:

1. `-e rotationInstalledProductV1Only true` builds and signs `app-v1.apk`
   inside the emulator. The result gives its emulator-local `phone paths`
   directory. Copy that file within the emulator to `/data/local/tmp/` and
   test-install it with `adb shell pm install`; no APK or source is copied to
   the laptop. Launch the generated counter and tap **Increment** once so it
   visibly reads `Counter: 1`.
2. `-e rotationInstalledProductV2ForRecoveryOnly true` rotates the disposable
   project signer while v1 stays installed. Do not install the fixture v2 APK.
   Then `-e recoveryInstalledFixture true` writes an encrypted `.plbr` to
   Android Downloads. The throwaway phrase is
   `disposable installed recovery phrase`. The ordinary
   `rotationInstalledProductV2Only` probe still cleans up its fixture; use the
   recovery-specific key here.
3. Open the actual controller, choose **Restore encrypted recovery file**, pick
   that `.plbr` through Android's picker, enter the throwaway phrase, review
   the signer and source summary, then tap **Restore**. Build in the controller
   and use **Install verified APK**. Allow this controller as an unknown-app
   source through Android Settings if prompted, then tap **Update** in
   PackageInstaller. `-e rotationInstalledVersion 2` checks the installed
   signing history; launching the app should show `Rotated: 1`.
4. Force-stop and reopen the controller, build again, and explicitly approve
   the next **Update**. `-e rotationInstalledVersion 3` checks signer history;
   the app should still show `Rotated: 1`. The first three steps passed on
   both API levels; this post-restart second update has been run on API 36.
   Keep each dedicated emulator's state for inspection.

## Two-project persistent-chat acceptance

The opt-in `layoutEditPhase` probe is the bounded #21 resource-edit case. It
requires `approveLocalLayoutEdit=true` and a fresh exact 32-hex `observationId`.
`build` reserves one owned project once; normal controller project/chat context
feeds explicitly synthetic proposals through production exact-preview,
generation/journal/dispatcher boundaries and the actual phone compiler. It
preserves a real malformed-layout failure, then recovers layout and applies an
R-inflating source proposal. `verify` reads that successful retained case without
another build. Duplicate builds are rejected, data is retained and previous
selection restored. It makes no model request and performs no generated
installation or standalone runtime check. Parent verification/installation
follows exact artifact and package checks and the approved phone-only debug
exception. **The XML inflation case requires source-derived Activity methods
from #22; do not mistake the historical R-constant probe for that runtime pass.**
See [scope, RED/GREEN and pending physical gates](validation/layout-edit-20261012.md).

The opt-in `manualBuildOriginPhase` probe covers issue #20 on the normal
MainActivity New project/New chat/Build controls. An explicit executor latch
permits chat switching before queued work begins; compilation, signing and
receipt publication remain real phone operations. It requires
`approveLocalManualOrigin=true` and an exact 32-hex `observationId`.
`red` is the one-shot pre-fix reproduction: it retains a source/build with
unknown origin and fails the origin assertion. Do not rerun or reseed it.
After the production fix, `green` uses that same retained observation/project
and commits its next source/version; `verify` checks the successful saved case
without compiling. Each phase restores the previous project/chat selection and
preserves all test data. No model request or installation occurs. The parent
must verify the connected device and exact harness before execution; host tests
cannot substitute for the physical run. See
[the RED/GREEN record](validation/manual-build-origin-20261012.md).

The opt-in `persistentChatsAcceptanceOnly` probe uses the normal controller UI
to create two owned projects with two chats each and perform four phone-local
builds. It checks rendered history persistence, stale revision refusal and real
package/signer/retained artifact continuity; it restores the previous selection
and retains all data. See the [exact command, one-shot guard and evidence
limits](validation/project-chat-acceptance-20261012.md). No model call or
generated installation is involved. Host success does not establish a physical
pass.

## Fixed alpha suite probe

`A33SuiteProbe` runs one `benchmarks/prompts.jsonl` request per run id through
the normal controller UI. Run it with
`adb -s "$PHONE_SERIAL" shell am instrument -w -r -e a33Suite <phase> -e promptId <id> -e runId a33_<x>_2026MMDD_NN dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation`.
The phases are, in order:

- `prepare`, which creates a pristine project, or continues an accepted run's project with `-e continueRun <runId>`;
- `baselineBuild`, then `pinBaseline` after the verified v1 is installed;
- `preview`, then `send` with `-e freeOnlyApproved true -e approvedContextSha256 <previewed hash>`;
- `repairPreview` and `repairSend`, while the journal is REPAIR_NEEDED;
- `accept`, only after the functional checklist is written up, with `-e checklistRecord docs/validation/<record>.md`;
- `restoreSelection` and `verifySelection`, or `endRepair` to end a pending repair through its real dialog.

The probe checks only the record path's format. The operator must write the
record first and make it name the run's APK hash. The controller allows at most
`ProjectRegistry.MAX_PROJECTS` (32) projects and has no delete action. Each fresh
run uses one project.

## Warnings and recovery

The generated app is an alpha test artifact. The controller asks Android's PackageInstaller for
explicit confirmation. Do not bypass this with ADB when testing the user-facing flow. The ECJ/D8
and apksig inputs need a complete redistribution notice review before shipping. The
emulator smoke script checks persistent prior evidence and is not a repeatable full UI build cycle.
The initial S23 Ultra ARM64 build and generated-app runtime evidence is recorded in
`docs/STATUS.md`. The user reports the physical compiler/install alpha passed during their
own testing. Separate agent records for offline build and a detached-laptop update were not
captured; repeat those checks before treating them as independently measured evidence.
