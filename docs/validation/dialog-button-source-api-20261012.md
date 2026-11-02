# Bounded source-derived integer dialog buttons — 2026-10-12

The subsequent independent physical gate is complete in
[new A33 native dialog evidence](dialog-native2-20261012.md). This source
record retains the earlier failed phone observations separately.

Issue #28 adds only public `AlertDialog.Builder.setPositiveButton(int,
DialogInterface.OnClickListener)` and `setNegativeButton(int,
DialogInterface.OnClickListener)` compile declarations. It excludes framework
`android.R` constants and does not diagnose or rescue frozen counter_02.
The parent owns APK assembly, installation and an isolated physical runtime
case; no phone/model/GitHub action or generated app export ran here.

## Inputs and actual source models

Both immutable files belong to frameworks/base Android16 r2 commit
`99b01a65cc4c104933788b3143285ab6bae65827`:

| Raw file | Git blob | SHA256 |
| --- | --- | --- |
| `core/java/android/app/AlertDialog.java` | `702b960ed759ba3caf76f672783f9a39606c8317` | `490a54f1718df9f914b7feb22bdb95b27c89bbf425551730d44c598dd5eabe52` |
| `core/java/android/content/DialogInterface.java` | `511f356c928912b7daeda1bad75d4958daad5f40` | `4ea8f08614b245088d74f183f5bc87d9effb971330f951bffe3a04efb63e4ee9` |

The dedicated extractor consumes exactly these two copied raw files and no
Android SDK definitions. It resolves the nested Builder and OnClickListener
as `COMMAND_LINE` models at their exact isolated source paths. Owner names,
modifiers, parameter names/types, return types, erased descriptors and generic
shape come from the stock source models. The actual listener callback model
also emits the isolated listener compile input; normal existing listener
headers remain unchanged. Unselected unresolved-import diagnostics are
retained (8204 bytes), with no suppression or full closure claim.

Existing [Activity layout source-model seam](layout-source-api-20261012.md)
supplies the pinned stock Metalava/JDK identities and declaration renderer.
All90 Metalava and46 JDK files are rehashed before Java launch. Metalava source
revision remains `9809e0d70a5b807be3d7070d51f9c4714f2fdcb4`, JDK21.0.12.1+1.
The ECJ JAR matches the existing tracked SHA256. Java subprocesses have bounded
heaps; ECJ receives an empty boot directory and empty classpath. No SDK archive
is opened by the source generator.

## Real test-first results

The isolated branch started at integration `e9a494a`. Two new normal worker
compiler-seam fixtures independently call each integer overload with an
application `R.string` field and an actual callback implementation. The first
focused run failed both tests (34s/38 executed tasks), but assertion argument
evaluation captured an empty diagnostic message. That record is preserved.
Capturing the compile result before asserting retained both exact ECJ errors
in the second RED (26s/3 executed tasks): each CharSequence overload was not
applicable to `(int, new DialogInterface.OnClickListener(){})`. No header had
changed before either RED.

Actual opt-in source-model integration then passed in47.354 host seconds at
`toolchains/worktrees/alpha-dialog-overload-20261012/toolchains/alpha-dialog-overload-20261012/integration-88b84ca403db4a478862460e1e500423/`:

| Boundary | Observed result |
| --- | --- |
| Existing normal headers; each independent integer call | ECJ255, actual method applicability error |
| Changed raw integer parameter without new source pin | Generator1, checksum mismatch before model/Java launch |
| Identical raw bytes without pinned Git association | Generator1, pinned Git revision rejected before model/Java launch |
| Stock parser; actual raw Builder/listener only | Extractor javac0, parser0; exact source association |
| Model-derived methods plus model-derived listener compile input | Both independent ECJ calls0 |
| Each selected identifier renamed in actual raw AlertDialog | Generator1; exactly one selected method; no normal worker constant emitted |
| Each missing-method compile variant | Corresponding ECJ255; other overload still ECJ0 |
| Selected class ABI after source-only GREEN | Public flags1; descriptor below; no generic Signature |
| Pinned SDK opened last, comparison-only | Both selected method records match access/descriptor/all parsed attributes |

Both selected methods have descriptor
`(ILandroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;`.
The comparison reference is API36 android.jar SHA256
`d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`.
Selected parity is not full API parity, runtime verification or clearance for
other platform/tool components. No framework R type was emitted. Negative
raw copies have changed hashes in retained receipts and cannot emit a normal
worker constant. Model JSON paths make its hash specific to the actual run.

## Bounded promotion, notices and normal checks

Only the generated constant's package declaration is removed before its
otherwise identical class body is appended to `SourceStubs.java`. One
concatenation inserts its two-method fragment inside the existing Builder.
Earlier CharSequence methods, Activity methods, manifest, confinement,
compiler policy and app-only DEX selection remain unchanged. The existing
whole-file source checksum covers the appended constant; no Gradle/verifier
framework or SDK input is introduced.

`toolchain/licenses/dialog-button-source/` contains the exact fragment,
PROVENANCE.json, modification/attribution NOTICE.txt, complete202-line
Apache-2.0 terms and frameworks-base NOTICE. Both actual source prologues
(2007 AlertDialog and2006 DialogInterface) are retained in the fragment and
embedded constant. License terms are byte-identical to the existing reviewed
complete terms; framework NOTICE bytes independently matched the pinned
Git commit's actual `NOTICE`. This bounded grant/source association does not
clear the other toolchain or full-platform distribution questions.

| Bytes | SHA256 |
| --- | --- |
| SourceStubs.java | `ddcd31d65a7f27184e146b38114dfb6015c6bf26f23df68467b1e49f76536784` |
| Source-derived two-method fragment | `91d6084e9db059ab8482a7873fd5b4a123d5d5d7e065d4713ab691d85752daac` |
| Standalone generated constant | `fee8077ae2448c4bb8c217fc46ea2991f86a95fcc9f92f6704f1c4dd42c1f30c` |
| Appended constant after package removal | `4ce11f8c34a45defb4e76c00a8d05d0f6ad9c6acfed93af2681add53acb62369` |
| Actual model JSON | `e8575dd582c2eec15207bdcaf07214baf76f48f63327e43ae75a352e190ddec0` |
| Runner | `c5c0ce84972bd311f71bb2d83b8b509ef74b6b86f1ef7905a0715cdf29c8afc4` |
| Extractor | `15c3727c45742d6c9b148154f48dedfe8ed284ddb288476f028246651df1739f` |
| Complete Apache terms | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| Framework NOTICE | `cf4dd7cea5b6bcd2622ddd3529b3c74d16ba2b4db444575bc6f5c33f8a960530` |

After promotion the full SourceStubsCompileTest passed (30s), including the
same two previously failing fixtures. Full worker verification then passed
in1m5s (79 tasks,39 executed/40 up-to-date):60 fresh JVM tests, no failures,
errors or skips; zero worker lint errors/two warnings; formatting check passed.
Ten Python checksum tests and actual compiler/source checksum verification
passed. No APK was assembled here. All failures, actual command logs,
XML reports and source/parser diagnostics remain in the same ignored
`alpha-dialog-overload-20261012/` evidence directory. The host JVM/parser/
Gradle slot was released before the later authored phone fixture was added.

```sh
source scripts/env.sh
./gradlew :worker:testDebugUnitTest --tests dev.smallwright.worker.SourceStubsCompileTest.positiveDialogApplicationResourceLabelCompilesWithoutPackagedAndroidJar --tests dev.smallwright.worker.SourceStubsCompileTest.negativeDialogApplicationResourceLabelCompilesWithoutPackagedAndroidJar --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
PLB_DIALOG_API_ROOT=. PLB_DIALOG_API_INTEGRATION=1 PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=scripts python3 -m unittest test_dialog_button_source_api
./gradlew :worker:testDebugUnitTest --tests dev.smallwright.worker.SourceStubsCompileTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew spotlessCheck :worker:testDebugUnitTest :worker:lintDebug --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=scripts python3 -m unittest test_verify_compiler_inputs
python3 scripts/verify_compiler_inputs.py --manifest toolchain/manifest.json --inputs toolchain/input
```

## New opt-in physical fixture: pending parent build and execution

The parent requested `DialogResourceProbe` after the host slot was assigned
to another agent. Its new controller Android test code has been statically
reviewed and authored resource XML parsed, but has not yet been formatted,
Android-compiled, assembled or run. Parent's combined build must complete
those gates before this fixture is used; earlier worker test results above
are independent of this later test-only addition.

The route requires `dialogResourcePhase`, `observationId`32hex and
`approveLocalDialogFixture=true`. It supports `prepare`, `inspect`, `compile`
and `ready`. Preparation reserves exactly one fresh project and stages the
three authored Java/layout/string-resource assets on that new phone project.
No frozen suite project is selected for mutation, reset or source export.
`compile` additionally requires `expectedCompile=red|green` and exact installed
`expectedWorkerSha256`. Each compile stage is once-only; GREEN requires
recorded actual JavaCompileFailure RED and a changed worker APK. Normal
visible Build preserves identical authored snapshot bytes while legitimately
advancing source revision, and successful output must have a verified exact
receipt and captured manual-build chat origin. Ready only restores/verifies.
Observed pending work emits status every30s and remains active without Stop/
retry; cleanup waits for idle before restoring the previous project/chat.
No model, generated install,
APK export or native-runtime action occurs in this probe.

The parent's separate native checklist after exact phone-only debug
installation is: initial count0, Add→1, Open resource dialog, observe
`Confirm resource reset` and `Cancel resource reset`; Cancel retains1 and
restart retains1; reopen/Confirm gives0 and restart retains0. These are future
checks, not passing observations. Existing generated app data and all original
Reset failures remain intact. Parent review/combined build, actual old-worker
RED/new-worker GREEN, installation and native dialog acceptance remain pending.

The feature worktree merged integration `c028219` (actual final Reset failure
evidence/status only) before handoff. Its existing repair worktree and all
ignored evidence remain preserved for parent review/backup.

## Preparation diagnostic: source-only handoff, execution pending

The parent retained an actual `prepare` failure at `NEW_PROJECT` after
`TITLE_INPUT` and `AWAIT_CREATED_PROJECT`. This stage covers the create helper,
the pristine snapshot guard and the already-installed package guard; the
available result does not establish which assertion failed. The observation
remains reserved, with no project pin published. Its original failed result
must remain unchanged; preparation must not be retried or reseeded.

The added opt-in `dialogResourcePhase=diagnose` requires the same approved,
reserved observation. It returns before UI startup and before every pin/source
write. It uses the existing bounded ProjectRegistry to find the exact title
`Dialog resource <observation>` and reports missing/ambiguous without creating
anything. A unique match reports only identity/title, current revision,
pristine status, file count and SHA256 hashes keyed by source path. If no
committed HEAD exists, snapshot fields are null; it never initializes a store.
No file contents, selection changes, package installation, model calls or
builds are performed. Installed-package status remains a separate read-only
parent check after the identity is known.

This diagnostic addition merged integration `417bfc3`. Only static diff
inspection and `git diff --check` have run; Java/Gradle/formatter execution is
withheld while the parent holds the host slot. No diagnostic GREEN, regression
pass, APK or fix for the original failure is claimed. Parent review and combined
Android test build must precede the actual read-only phone observation.

## Independent saved-source Binder trace

The parent's original normal Build observer ended at
`ACTUAL_JAVA_COMPILE_RED` with AssertionError. Its saved revision advanced1→2
with identical authored source bytes, but this result does not establish an
ECJ failure; the stage also covers the exact status/readiness/persistence
assertions. The consumed `redAttempted` record, original result and source2
must not be reset or reclassified. Empty artifact-directory evidence remains
a separate observation from that failed expected-RED assertion.

Parent approved one independent `dialogResourcePhase=trace` on exactly the
retained source2 with its original manual-build version1. Parent ran the old
test APK's actual missing-route RED: terminal host0/instrumentation0, GUARD
AssertionError. Raw and handle remain under the root worktree's ignored
`toolchains/alpha-goal-20261012/dialog-diagnostic/trace-missing-route-red.txt`
and `trace-handle-missing-route-red.json`.

The new test-only route returns before UI startup, requires the original
reserved observation/project/chat/revision/hash, consumed-but-unverified old
RED attempt, original exact worker APK hash, exact authored bytes and captured
manual source origin. It reserves a separate once-only
`dialog-resource-trace28-20261012` preference record. All original observer
preferences and source revision/snapshot must remain identical afterward.
The existing `ProjectSourceStore.beginBuild` lease assembles the normal
manifest+three-source-file snapshot. A separate real worker Binder connection
executes handshake2 and exactly one `buildWithDiagnostics`; there is no source
write, UI selection, signing, receipt publication, installation, model or
generated-source/APK export. Temporary snapshot/unsigned output/report bytes
stay in phone cache and are cleaned after actual terminal completion.

Progress records identify snapshot assembly, numeric capability, Binder
entry/return and30s pending observations. Pending work is never stopped or
retried, and unbinding waits for the actual call even if pending-status
delivery fails. Results allowlist worker sentinels/AAPT2 failure categories
and exception class/stage without raw worker error text. Numeric input is
bounded to135 bytes plus an oversize sentinel and validated by the existing
`CompilerDiagnostics.decode` against admitted source bytes; only its source
hash, up to8 numeric id/category/line records and truncation flag are reported.
Unsigned output is reported by byte count only; unexpected OK remains
unverified. A new trace may establish its own compiler outcome, never turn
the old observer into a pass. No worker/controller production code changes.

Host formatting passed20s, then final formatting passed21s after pending-status
delivery failure handling was tightened. The capped offline
`spotlessCheck :controller:lintDebug :controller:assembleDebugAndroidTest`
passed in1m39s:102 tasks,57 executed/45 up-to-date. Changed Android test javac
executed; controller lint had0 errors/77 warnings. No JVM unit tests were run
or claimed for this test-only addition. Logs remain in this feature worktree's
ignored `toolchains/alpha-dialog-overload-20261012/trace/` directory.

| New host test candidate | SHA256 |
| --- | --- |
| DialogResourceProbe.java | `0708970ceb6362013d351adfc052c0d4569dc3450e523626c5c46e9974188fb1` |
| `trace/trace-controller-debug-androidTest.apk` | `b15b0fc5db16b8f20d705625dd43160b1790e673517a395297d7f5d5ec542aed` |
| Public signer certificate | `19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25` |

`apksigner verify --verbose --print-certs` passed v2 verification with one
signer. AAPT2 archive inspection reported package
`dev.smallwright.controller.test`, min31/target36. Only this new controller test
APK was assembled; worker production bytes/SourceStubs checksum remain
unchanged. Parent must independently review the source/public signature
before installation. Host slot was released after checks; actual trace
execution remains the parent's physical-device gate. Existing worktrees,
original outcomes and all ignored evidence are preserved.

```sh
source scripts/env.sh
./gradlew format --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew spotlessCheck :controller:lintDebug :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
toolchains/android-sdk/build-tools/36.0.0/apksigner verify --verbose --print-certs toolchains/alpha-dialog-overload-20261012/trace/trace-controller-debug-androidTest.apk
toolchains/android-sdk/build-tools/36.0.0/aapt2 dump badging toolchains/alpha-dialog-overload-20261012/trace/trace-controller-debug-androidTest.apk
```

## Parent integration and actual A33 result

The parent independently replayed the source-only integration in48.360s,
rehashing all136 tool pins and verifying the emitted fragment against the
appended constant. Both ECJ REDs, both GREENs, raw-source negatives and the
SDK comparison opened last passed. Combined formatting/build/lint passed
in1m35s; a separate explicit `:worker:testDebugUnitTest` passed in28s with
60 fresh tests and no failures/errors/skips. Earlier cached reports were
preserved separately, not counted as fresh tests. Separate Standards/Spec
reviews through503c625 reported no unexpected findings; full-alpha final
review remains pending. Parent logs, reports and source receipts are under
`toolchains/alpha-goal-20261012/dialog-api/`.

The physical preparation observer failed at NEW_PROJECT. Subsequent approved
read-only diagnosis and retained inspection passed and found the exact
three-file fixture at source1, with no artifact. This proves preparation
progressed further than the recorded stage suggests; the original failure
remains. The normal old-worker expected Java RED then failed at
ACTUAL_JAVA_COMPILE_RED, advancing identical source bytes1→2 without an APK.
Neither result is a successful compiler RED. Restoration of the original
preparation selection was not established. The later detached fixture
records this dialog project as its previous selection.

At integration9eb12f7 the parent verified the trace test APK's package,
signature and hash, then updated only `dev.smallwright.controller.test` with
data retained. Controller45adb0b, worker0fc753e and worker.testb66e173 were
unchanged. Fresh explicit-device preflight confirmed A33 SM-A336E. One actual
`--phase trace --label actual-01` returned host0/instrumentation-1: the
observer completed, but its real compiler outcome was **AAPT2_FAILURE**.
Snapshot assembly, numeric capability and Binder entry/return passed;
unsigned output0/report0. Java compilation was not reached or verified.
Source2, original observation pins and authored snapshot hash
`aacfe9cf50f9460bef5b93bf67493d2c4ff5214d5747c7f75f5095a291e3aac1`
remained unchanged. No model, signed artifact, generated installation or
generated-source/APK export occurred. The original UI failures remain failed.

Exact trace raw/terminal handle, installed identities, fresh preflight and
public signature evidence are retained under
`toolchains/alpha-goal-20261012/dialog-trace/`. Issue28 stays open: actual
old-worker Java RED, new-worker GREEN and native dialog acceptance are not
established. The new worker candidate remains uninstalled. The AAPT2 cause
needs independent diagnosis; no fix is claimed.
