# Application values linking: bounded production checkpoint

Issue29 is a bounded dependency discovered while verifying issue28. The actual
independent dialog Binder trace returned AAPT2_FAILURE/output0/report0; compile,
link and launcher remain undistinguished. The `-R` overlay hypothesis is
supported by local TableMerger source and
[official AAPT2 documentation](https://developer.android.com/tools/aapt2).
It is not a confirmed cause or fix. The detailed read-only note is
`/tmp/phone-local-dialog-aapt2-diagnosis-20261012.md`.

This feature worktree started at exact integration
`2e3fae4b8270b8aaa95cd7b3747bbe882a735d85`. Only new test code, an opt-in worker
instrumentation route and authored harness resources are changed. Production
BuildService, API headers, compiler inputs, manifest/confinement policies and
Gradle remain unchanged. All original dialog/Reset outcomes, source revisions,
preferences and existing worktrees are preserved. The production fix is gated
on the parent's actual phone RED and independent differential.

`mode=valuesResources` requires `valuesPhase=differential|regression`, a new
32hex `observationId`, `approveLocalValuesFixture=true` and the exact installed
normal `expectedWorkerSha256`. It verifies the worker UID/package/hash and
rejects experimental platform assets. Each phase reserves its own once-only
worker-private marker; a consumed case must never be reset. Its workspace is
new worker cache, not a saved project. No UI selection, signing, receipt,
installation, model, generated source/APK export or frozen-case mutation occurs.

The two resource assets are exact copies of the existing authored harness
fixture, not exported phone source:

| Authored resource | SHA256 |
| --- | --- |
| `values-resource/empty.xml` | `23ae78adc6d88761f1c4cb40f648916c6bb68a783c5550851c845de1908fa9c6` |
| `values-resource/dialog_labels.xml` | `784ffe824d172ffaa412a9d31e0234a0a3db13b1fc756a641b4525e22c76b290` |

Differential compiles once through the installed native binary and existing
`WorkerProcesses.startAapt2` guard. It compares the current `-R` link command
with positional base input using the exact same compiled archive/manifest/
framework input. It requires compile success, current overlay failure with
the fixed new-resource-overlay rejection indicator, and positional success.
It checks both integer fields in application R.string, linked manifest/
resource table and unchanged compiled bytes. It reports phase/exit categories,
the allowlisted boolean, SHA256 hashes, symbol count and output byte counts
only; raw compiler/source/error prose is never reported. Generated R and
resource APK bytes remain in phone cache until cleanup after actual native
completion.

Regression independently creates a four-entry snapshot: normal authored
manifest, minimal Activity referencing both application R.string fields,
layout and values. It calls the existing real `BuildService.buildSnapshot`
with disposable cache and expects OK. The pre-assertion safe observation
reports the actual sentinel category/output count/scratch cleanup, so an
actual current-worker failure remains useful RED evidence. Successful output
must contain resource table/DEX, application R.string and app-only class
definitions. This is a phone-produced unsigned artifact, with no signing,
publication or installation. Existing layout/id positive, missing-ID and
malformed-resource cases remain untouched.

At the initial source checkpoint, only static review, `git diff --check` and authored
asset byte/hash checks have run. No formatter, Java, Gradle, host compiler or
phone operation ran while the parent owns the detached-build slot. No new
APK, regression RED, differential result or GREEN is claimed. Parent source
review precedes granted capped host checks and the exact test-APK handoff;
parent owns current-worker installation and actual A33 execution. A production
change must wait for both real normal-pipeline RED and confirming differential.

## Verified host test candidate: phone regression still pending

The feature merged current integration `93e729d` before the granted host build.
The existing `guarded()` cleanup policy is preserved: `WorkerProcesses.finished`
destroys a still-running child, waits500ms and terminates the worker fail-closed
if that child remains alive. Exit metadata is read afterward. The existing
AAPT2 deadline/guard is unchanged; no new drain loop or fallback is introduced.

Owned setup links point to the root worktree's existing supported JDK21,
Android SDK/Gradle cache, public compiler/native inputs and ignored harness
signing directory. No signing material was read or printed. These links,
builds and evidence stay untracked. Only the worker Android test APK was
assembled; production linking is unchanged, and parent alone owns phone
installation/current-worker RED/differential execution.

Formatting passed in28s (six tasks executed). The granted capped offline
`spotlessCheck :worker:lintDebug :worker:assembleDebugAndroidTest` passed
in1m17s:105 tasks,101 executed/four up-to-date. Changed worker Android test
javac executed; lint had0 errors/two warnings. No JVM unit tests were run or
claimed for this test-only slice. Logs and the durable candidate remain in
the feature worktree's ignored `toolchains/alpha-values-link-20261012/`:
`format-01.log`, `worker-check-01.log`, `public-signature-01.txt` and
`badging-01.txt`. Existing production worker code, API/header pins, native
guard and Gradle are unchanged. No production APK or generated app was built
by this handoff.

| Verified candidate / source | SHA256 |
| --- | --- |
| `values-worker-debug-androidTest.apk` | `07763d4572ab7450660cbbf5ccf3ed60da4d10abcea8497cfb5b32b56b95e3bc` |
| ValuesResourceProbe.java | `f21c5ac50629cc2682bc16668a9bc209910eea5a93b747bbe79520731a48caef` |
| ConfinementInstrumentation.java | `2a384318442534b4f7d46e95db5cb7b0a916228266809f835ed0e0191e71f4e9` |
| Public signer certificate | `19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25` |

Public APK verification passed v2 with one signer; archive badging reports
`dev.smallwright.worker.test`, min31/target36. Current integration `0168f15`
(STATUS-only after `93e729d`) was merged before handoff; it changes no built
input, so no rebuild was needed. Host slot was released after public signature
verification. Parent independent review/test-only installation on original
worker0fc, fresh real normal-pipeline RED and the differential remain pending.
The physical gate cannot be replaced by this successful host test assembly.

```sh
source scripts/env.sh
./gradlew format --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew spotlessCheck :worker:lintDebug :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
toolchains/android-sdk/build-tools/36.0.0/apksigner verify --verbose --print-certs toolchains/alpha-values-link-20261012/values-worker-debug-androidTest.apk
toolchains/android-sdk/build-tools/36.0.0/aapt2 dump badging toolchains/alpha-values-link-20261012/values-worker-debug-androidTest.apk
```

## Corrected test observer: actual partial result preserved

Parent's actual old-worker normal regression established RED for independent
case `feb37916b8a14ed186a013784b09c663`: expected OK, actual AAPT2_FAILURE,
unsigned output0, pipeline scratch cleaned. Its host handle exited0 and the
instrumentation failed with code0 at NORMAL_PIPELINE_BUILD, as expected for
the failing positive regression. Raw/terminal evidence remains under the
root worktree's ignored `toolchains/alpha-goal-20261012/values-red/`:
`regression-actual-old-worker-red-01.txt` and
`regression-handle-actual-old-worker-red-01.json`.

The same case's independent differential did NOT pass. Actual stages recorded
compile OK/exit0, overlay EXIT_NONZERO/exit1 with new-resource-overlay rejection,
and positional base link OK/exit0 using the same compiled input. It then failed
at DIFFERENTIAL_BASE_LINK with PatternSyntaxException before completing the
R.string symbol proof. Its original failed result/once-only marker remains
preserved in `differential-actual-01.txt` and
`differential-handle-actual-01.json`. These are useful partial native results,
not a completed differential or permission to change production.

Only the test observer regex now explicitly escapes its closing literal brace
for Android ICU. The source matching requirements, expected fields, authored
bytes and worker production link command are unchanged. Parent
will predeclare a NEW independent case to finish symbol validation; the old
case is never rerun, cleared or relabeled. No model, generated source/error
prose/APK export or phone operation is performed by this correction.

The corrected host candidate passed formatting in21s. The same capped offline
`spotlessCheck :worker:lintDebug :worker:assembleDebugAndroidTest` passed
in46s:105 tasks/eight executed/97 up-to-date. Changed Android test javac and
Android test lint analysis executed; main lint/report was cached at0 errors/
two warnings. No JVM unit test or Android ICU runtime pass is claimed.
Logs are `format-regex-01.log`, `worker-check-regex-01.log`,
`public-signature-regex-01.txt` and `badging-regex-01.txt` in the same ignored
feature evidence directory. The first test APK and all failed phone evidence
remain preserved.

| Corrected host candidate | SHA256 |
| --- | --- |
| `values-regex-worker-debug-androidTest.apk` | `ddf7bef8fd017d3d0278abdaf0e15b0fd5562759281b28557fbde0c37173c63f` |
| ValuesResourceProbe.java | `bc2d97c55923cdebde07355eba5ac2a8bf54ebc2f5e54674eb954aa6a0f13f2c` |
| Public signer certificate | `19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25` |

V2 signature verification passed with one signer; package remains
`dev.smallwright.worker.test`, min31/target36. The worktree is based on current
integration `d0ac79d`; no production code is changed. Host slot was released
after public metadata checks. Parent independent review and new-case physical
validation remain required before the bounded production fix.

## Bounded production change: real prerequisites established, host gate pending

Parent predeclared a NEW independent case
`4b423fdab81d41fcb5cde2e97a8803f8` for the corrected observer. It completed
with host0/instrumentation-1: actual compile exit0, overlay link exit1 with
new-resource-overlay rejection, positional base link exit0, both application
integer string symbols/count2 and resource output2493 bytes. The compiled
archive was identical between link inputs (SHA256
`10a59a5fa0dd2be5ceb79ff86749246af2b015c3623b929088af9d8be382607c`).
Its full symbol proof passed separately from the original failed differential.
The parent's [actual phone prerequisite record](values-resource-phone-a33-20261012.md)
was merged from current integration `b9c9653` before handoff.
Raw/terminal/contract remain under root ignored
`toolchains/alpha-goal-20261012/values-differential2/`:
`differential-actual-01.txt`, `differential-handle-actual-01.json` and
`contract-before-run.json`. Original case `feb37916...` remains consumed;
its normal-pipeline RED and failed partial differential are unchanged.

With both real prerequisites now established, parent authorized removal of
only `-R` from normal `BuildService.packageSnapshot` resource linking. Its
single compiled application archive is now a positional base input. The
legacy counter/toolchain fixture's separate `-R` is retained. No manifest,
source paths/counts, headers, API declarations, permissions, process policy,
deadlines, SDK inputs or Gradle changes accompany this edit. The two current
source-derived integer dialog methods remain unchanged.

At this edit checkpoint only static diff checks ran. Java/Gradle/compilation
waits for a new host-slot grant after the parent's counter05 compilation.
No production APK or normal-pipeline GREEN is claimed yet. The intended host
gate is capped full worker tests/lint/production assembly with actual fresh
test results, followed by parent independent verification, a NEW phone normal
values regression GREEN and relevant layout/id/malformed/missing-ID neighbors.
All generated output stays on phone; no model or installation action is
performed here.

Issue28's actual old-API ECJ RED/native dialog gate remains separate. A possible
future expressly unmerged baseline combining this resource-link fix with
pre-503c SourceStubs and its matching manifest pin is not prepared here;
parent must authorize and verify that separate baseline before use. This
resource change does not relabel the earlier AAPT2 failures as Java RED.

## Verified production host candidate: physical GREEN pending

The granted worker-only gate passed using existing local JDK21/offline/
no-daemon/max-workers1/Xmx1536m/metaspace512m. Compiler-input verification
passed, and10 checksum tests passed in0.109s. The requested
`spotlessCheck :worker:testDebugUnitTest :worker:lintDebug :worker:assembleDebug`
passed in2m44s:100 tasks,33 executed/67 up-to-date. The full worker test task
executed with60 fresh cases, zero failures/errors/skips; it was not cached.
Worker main/test/unit lint analysis ran, with existing report0 errors/two
warnings. No controller or unrelated fixture was built; no new artificial
JVM test was added for the link command.

Durable ignored evidence is under the feature worktree's
`toolchains/alpha-values-link-20261012/production/`: `worker-check-01.log`,
`compiler-inputs-01.log`, `checksum-tests-01.log`, copied
`unit-test-results-01/`, `lint-results-01.txt`, public signature/badging/
manifest records and the production APK below.

| Candidate / source | SHA256 |
| --- | --- |
| `values-worker-debug.apk` | `c1daca5782eb3fb751b0356c7a985f30024420afaaaf5b383ab0113db004f2bb` |
| BuildService.java | `9bce0ad090bd3c9c31bd79cebd4d67f6217f101525bd244fd25419e2414756fe` |
| SourceStubs.java, unchanged | `ddcd31d65a7f27184e146b38114dfb6015c6bf26f23df68467b1e49f76536784` |
| Public signer certificate | `19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25` |

`apksigner` verified v2 with one signer. Archive badging identifies
`dev.smallwright.worker`, version1/0.1.0, min31/target36. The archive audit
byte-matched all52 tracked license/notice/provenance assets to source,
including complete dialog-source terms/notices. All four packaged AAPT2/
guard native entries (ARM64/x86_64) byte-match the original0fc worker APK.
Manifest inspection confirms no uses-permissions/Internet permission,
signature-level2 binding permission on BuildService, and allowBackup=false.
This preserves existing policy/notices; it does not close unresolved complete
toolchain redistribution/source-correspondence questions.

The first archive-audit assertion expected a different hex display syntax
for the protection level and failed. Its exact script/failure is preserved
as `archive-and-tests-01.failed.py/.log`. The corrected audit parses the
actual numeric value and requires2, checks the service permission and
backupfalse; `archive-and-tests-02.py/.log/.json` passed. No production bytes
were changed to satisfy this observer correction.

Current integration `949d981` (counter05 evidence/row only) was merged before
handoff; it changes no built worker input and needed no rebuild. Host slot
was released after public APK/audit checks. Parent independent source/public
signature review, same-signer worker update preserving data, a NEW actual
normal R.string regression GREEN and relevant resource-positive/malformed/
missing-ID neighbors remain pending. No phone, model, GitHub or generated
app export/install action ran in this implementation worktree.

```sh
source scripts/env.sh
python3 scripts/verify_compiler_inputs.py --manifest toolchain/manifest.json --inputs toolchain/input
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=scripts python3 -m unittest test_verify_compiler_inputs
./gradlew spotlessCheck :worker:testDebugUnitTest :worker:lintDebug :worker:assembleDebug --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
toolchains/android-sdk/build-tools/36.0.0/apksigner verify --verbose --print-certs toolchains/alpha-values-link-20261012/production/values-worker-debug.apk
toolchains/android-sdk/build-tools/36.0.0/aapt2 dump badging toolchains/alpha-values-link-20261012/production/values-worker-debug.apk
toolchains/android-sdk/build-tools/36.0.0/aapt2 dump xmltree toolchains/alpha-values-link-20261012/production/values-worker-debug.apk --file AndroidManifest.xml
python3 toolchains/alpha-values-link-20261012/production/archive-and-tests-02.py
```
