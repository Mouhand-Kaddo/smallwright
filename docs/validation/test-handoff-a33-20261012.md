# Exact APK test handoff on the A33

## Outcome and boundary

Checkpoint #25 implements approved D1/D3 installation reconciliation and explicit
self-reported test acceptance. Parent #2 and the full alpha remain open.
Physical A33 inspection, dialog Cancel, test-assisted acceptance and fresh-process
recovery passed. Existing native count4 survived reopen/restart. These checks
verify the mechanism; no human acceptance or full-alpha pass is claimed.

The controller now distinguishes a verified build awaiting installation, the exact
installed build awaiting a test, and a build the user explicitly accepted. A
project-private acceptance record names one APK SHA-256. It is a self-report,
not a compiler, installer-consent or functional-test result. The build/run journal
and signed artifact receipt retain their original meanings.

Installed recognition reads Android PackageManager metadata and hashes the actual
single installed base APK. Expected package, positive/equal version, no splits,
exact byte hash and stable package path/version are required. The selected
previously validated artifact is rechecked before and after recognition. Exact
bytes bind its verified signer as well; a package/version match alone is
insufficient. Missing/different installation never enables acceptance; unreadable
identity or corrupt status fails closed. This does not establish the installation
route, consent, launch or behavior. Uses the existing generated-project package
visibility query, with no new permissions. API references:
[PackageInfo](https://developer.android.com/reference/android/content/pm/PackageInfo)
and [ApplicationInfo](https://developer.android.com/reference/android/content/pm/ApplicationInfo).

Reconciliation runs on artifact changes, Activity resume and installer status
callbacks. It never starts/repeats an installation. The normal confirmation button
rechecks installation before saving, blocks project/edit/build controls during
that write, and pins project/artifact identity. Stale async display results are
ignored. Cancel writes nothing. Newer builds and other projects do not inherit
acceptance. Existing project/chat selection and source/APK/count are preserved.
No model request, credential read, source patch or compiler/policy expansion ran.

## Test contract and assistance

Seams are the approved normal UI, project status persistence and installed-package
boundary. Owned fixture is the prior live XML project
`app7ee6a7cc1bfa4762b40a3e0e3c2ac410`, source4/version4,
APK SHA `283ee00a4796164671c58baf9b5667b00e76ef7f58dac8a6d71ec00f65df7800`.
It was already installed through the approved phone-only USB debug route.
This checkpoint does not install/update that generated app. Generated source/APK
bytes remain on the phone. Its saved source snapshot is
`8ddb99da23cda1bb7d9cea4a7d06e3f57f5b3b56a0090321bd519e441cc44ce9`.

Instrumentation opens/selects this owned project through the normal project
picker and uses the production acceptance dialog. Acceptance clicks are
**synthetic test input**, proving the mechanism and persistence, not an actual
human functional signoff or benchmark pass. The owned test project's resulting
acceptance record is retained. A separate fresh-process phase verifies recovery.
The probe requires its specific approval argument before acceptance and rejects
a duplicate; this test guard grants no production consent. It restores the prior
project/chat and finishes its Activity, with no data reset or uninstall.

Fresh read-only device preflight at `2026-10-12T00:45:47.144371Z` identified the
connected Samsung Galaxy A33 5G SM-A336E, Android16/API36, ARM64/s5e8825, qemu0.
Green update preflight is in `preflight-green.json`. Awake/keyguard guards precede
UI work. Personal serial/coordinates remain ignored. This is physical A33
coverage, not the queued S23 frozen repair or a second-device full-suite pass.

## Host RED and GREEN

Ignored raw root: `toolchains/alpha-test-handoff-20261012/`.

- `jvm-red.log`: initial missing persistence interface fails compilation; no
  executed JVM-case pass claimed from it. `jvm-green-01.log`: the first real
  exact-acceptance/reopen case passes.
- `build-green-quality.log`: test code used SDK-unavailable Files readString/
  writeString; compile failed before tests. Corrected to existing bounded byte
  APIs. This is a test implementation failure, not a phone/compiler defect.
- `build-green-quality-02.log`: **234 fresh controller JVM cases**, zero
  failures/errors/skips; formatting, controller lint and controller/test assembly
  passed in3m7s. Lint77warnings, zero errors/fatal; no baseline/threshold change.
  This run predates the final trusted-base fix.
- `jvm-ancestor-red.log`: added platform-alias/private/project regression fails
  with IOException under the initial full-ancestor path check.
- `build-green-quality-03.log`: final targeted **18 fresh cases**—7AppTestStatus,
  5VerifiedArtifact,6SignedArtifactReceipt—pass with zero failures/errors/skips.
  Formatting, lint and controller/test assembly pass in1m23s. Full235-case final
  suite is not claimed as a fresh run.
- `build-final-test.log`: corrected test-only approval parsing, formatting and
  Android test assembly passed. `build-restoration-test.log` adds explicit
  restored project/chat equality checks; formatting/assembly passed.

The initial physical failure exposed a real over-broad store check: it walked
past the framework-provided private files directory and rejected a platform
ancestor link. The corrected store takes an explicit trusted private base,
requires the record under that existing NOFOLLOW directory, rejects links from
record/partial through project and base, and stops there. A33 reports
`platformAncestorLinkOutsidePrivate=true` and now passes. Private descendants
remain protected; this changes no filesystem permissions or system configuration.

Independent efficient-model focused static review and a second review of the
trusted-base fix found no actionable material issue. Parent checked the code,
failed physical evidence, regression and actual rerun before accepting that
review. Static review itself supplies no runtime or full-alpha evidence.

## Physical records

All original host Popen handles are retained until terminal; named results and
Android instrumentation codes are required, not ADB exit alone. Failure records
are never overwritten or relabelled. The original RED test APK is retained.

| Record | Actual outcome |
| --- | --- |
| `red-01.txt` | Expected missing handoff failure, OBSERVE/AssertionError, code0 |
| `red-preparation-recovered.json` | Installer observer rejected extra ADB streamed-install prefix; installed test hash independently matched, no reinstall |
| `inspect-green-01.txt` | First production candidate fails OBSERVE; exact installed-v4 recognition and older-v3 rejection already passed |
| `inspect-diagnostic-01.txt` | Direct status read fails IOException |
| `inspect-diagnostic-02.txt` | Fixed diagnostic category STORE_LINK/IOException, no acceptance |
| `inspect-fixed-01.txt` | Physical installed-awaiting-test PASS, code-1; outside-private ancestor link observed |
| `missing-approval-01.txt` | Expected test approval guard, code0, before UI/write |
| `accept-fixed-01.txt` | Incorrect Boolean read of ADB text argument rejects at GUARD before UI/write; corrected only test route parsing |

| `missing-approval-final.txt` | Corrected route rejects missing approval at GUARD before UI/write, code0 |
| `accept-final-01.txt` | Normal dialog Cancel leaves installed-awaiting-test; positive action persists exact v4 acceptance, code-1 |
| `verify-fresh-process-01.txt` | Fresh controller process restores acceptance/source4/exact APK, code-1 |
| `duplicate-approval-final.txt` | Expected duplicate test-acceptance guard before UI/write, code0 |
| `test-handoff-neighbor-results-01.json` | Nine neighboring readiness/recovery/guard checks passed, no new model/build/install |
| `runtime-first-observer-failure.json` | Immediate post-launch focus assertion failed before a count observation; no taps/install/source change; cause unconfirmed |
| `verify-restoration-01.txt` | Latest test APK verifies recovered acceptance and actual prior project/chat equality after restoration, code-1 |
| `final-integrity.json` | All four installed harnesses/generated APK/source pins match; own accepted/selectionRestored flags true, original handles absent, scratch absent, own processes stopped |
| `final-runtime-verification.json` | Observed the original already-launched app at count4, then explicit restart4; exact installed bytes/native IDs/classes/controller-worker absence; original host handles absent |

The corrected runtime observer waits for actual owned-app focus before capturing
that app. It did not relabel the first failure, reinstall, reset data or change
code. No screenshot/hierarchy from a personal screen was collected. The first
successful count observation used the original live Android launch, not a retry.

Nine neighbors: kernel-source30 ready, prior sustained-source30 ready, expected
kernel duplicate guard, workspace/larger-source6 ready, ECJ Stop/source2 ready,
compiler-measurement/source2 ready, screen-lock/source2 ready and completion
verification. The last uses verification-only bytes; it is not compiler/model
execution evidence. Earlier fixture identities/outcomes remain unchanged.

## Exact changed paths and commands

Production:
- `controller/src/main/java/dev/smallwright/controller/AppTestStatus.java`
- `controller/src/main/java/dev/smallwright/controller/InstalledArtifact.java`
- `controller/src/main/java/dev/smallwright/controller/MainActivity.java`

Tests:
- `controller/src/test/java/dev/smallwright/controller/AppTestStatusTest.java`
- `controller/src/androidTest/java/dev/smallwright/controller/TestHandoffProbe.java`
- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`

Evidence: this file and `docs/STATUS.md`. User-owned skill/tooling notes stay out
of the checkpoint.

```sh
source scripts/env.sh
./gradlew spotlessApply spotlessCheck :controller:testDebugUnitTest :controller:assembleDebug :controller:assembleDebugAndroidTest :controller:lintDebug --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
# Final trusted-base change: the same command with these JVM filters:
# --tests dev.smallwright.controller.AppTestStatusTest
# --tests dev.smallwright.controller.SignedArtifactReceiptTest
# --tests dev.smallwright.controller.VerifiedArtifactTest
python3 toolchains/alpha-test-handoff-20261012/prepare.py --serial "$PHONE_SERIAL" --stage fixed
python3 toolchains/alpha-test-handoff-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase inspect --label inspect-fixed-01
python3 toolchains/alpha-test-handoff-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase accept --label accept-final-01 --approve
python3 toolchains/alpha-test-handoff-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase verify --label verify-fresh-process-01
python3 toolchains/alpha-test-handoff-20261012/run_neighbors.py --serial "$PHONE_SERIAL" --label 01
python3 toolchains/alpha-test-handoff-20261012/verify_retained_runtime.py --serial "$PHONE_SERIAL" --already-launched
```

The ignored runners exist and guard unique record paths, physical identity,
installed baseline, package and signer, in-place harness updates and original
process completion. Use new labels for a new case; do not blindly repeat an
acceptance phase or reinstall after an observer timeout.

## Final artifacts and restoration

At `2026-10-12T01:14:40.907505Z`, all four installed harness hashes and the
unchanged generated v4 APK matched. Only this run's nonsecret test preferences
were read to confirm `accepted=true`, `selectionRestored=true` and exact APK hash;
no general preferences or credential store was inspected. Latest probe independently
compared actual restored project/chat fields with the captured prior selection.
Own controller/worker/generated processes are stopped, worker scratch absent,
all original host handles terminal/absent. Count4 was last independently observed
at01:11:41UTC; no subsequent generated-app execution or data change was performed.
The test-assisted acceptance record remains only in the owned test project.

| Durable host harness APK | SHA-256 | Bytes |
| --- | --- | --- |
| `toolchains/alpha-test-handoff-20261012/fixed-controller-debug.apk` | `45adb0b429c5660c822e1b115fb68161131cbda0bb8f0acaa3e02cded41e658b` | 9150568 |
| `toolchains/alpha-test-handoff-20261012/restoration-controller-debug-androidTest.apk` | `eed9484ae72a5fe15b8696780230479af489222fd3e3981000576d839b70f630` | 1171782 |
| `toolchains/alpha-layout-api-20261012/worker-debug-license-complete.apk` | `436d6a83aceff3635b471484f1c32c50d10b993d445434d24adba02d012cbb4c` | 21149950 |
| `toolchains/alpha-kernel-resources-20261012/root-worker-debug-androidTest.apk` | `fa1ac79e0676bcaca6df8f639f0f80ff7278afa4d3b88f7101406a5afb8462a5` | 159630 |

Harness public signer SHA-256:
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
Both harness updates are verified in-place debug installs; existing keys/projects,
source/history/data and unrelated test APK contents are preserved. Worker pair
unchanged. No generated install or production installer-consent check occurs.
Source hashes are in `final-integrity.json`; final installed production is the
trusted-base candidate. Earlier failing candidate/test APKs remain retained.

Next: continue #2's conversational acceptance and the full fixed-suite prerequisites
(resource budgets and licensed API/toolchain breadth), then genuine two-device
and user alpha checks. Production Android installation consent, actual human
acceptance and benchmark denominator remain separate; no full alpha pass.
