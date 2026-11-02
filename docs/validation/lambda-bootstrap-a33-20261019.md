# Lambdas compile against pinned AOSP source, 2026-10-19

Issue #33.
Since #37 the worker compiled lambdas through `LambdaBootstrap`, a handwritten
`java.lang.invoke.LambdaMetafactory` source string, because Google's
platform-36 `android.jar` leaves that class out. #33 forbids handwritten
signatures. The worker now compiles the three unchanged sources of AOSP
libcore's `core-lambda-stubs-for-system-modules` instead. They ship as worker
assets, and the worker refuses them unless each SHA-256 matches its pin.
`LambdaBootstrap` is deleted.

## Provenance

Repository `https://android.googlesource.com/platform/libcore`, commit
`de876a01b29230b877c9f408348c38a90ee724a5` (2025-03-26). Fetched with
`/+/<commit>/<path>?format=TEXT`; each file's `git hash-object` equals the
upstream blob id, and the committed blobs carry the same ids.

| File (upstream path) | Blob | SHA-256 | Bytes |
| --- | --- | --- | --- |
| `ojluni/src/lambda/java/java/lang/invoke/LambdaMetafactory.java` | `e1ce46f3330386eac06d5d45e02bc361e927d17d` | `d94fc80083704fbc9ed1d004a677ec596a91d472559d20ef2556a2a6927d40fe` | 2,213 |
| `ojluni/src/lambda/java/java/lang/invoke/SerializedLambda.java` | `55ec67091cba75754f2b0d82382e5e060bae337c` | `b961edb25ed34896d4dfe6ee9566504936db3802fb875e512dd4086e86219afd` | 2,677 |
| `ojluni/src/lambda/java/java/lang/invoke/StringConcatFactory.java` | `950f62900f6da01fcfcf3479f7f188ffa58f78b3` | `0df4096a88c55e12cdbb938bab218663e16628c984ac7a42e8fe519b9295f6aa` | 1,780 |
| `ojluni/src/main/LICENSE` | `368340269de134f58a48df1286449d7d251f1bd7` | `46d411827d56eda34df30f901bfa4ea020119b2e38b6b7efb6a32e91461457a2` | 407,542 |
| `ojluni/src/main/NOTICE` | `80c1d466cbafdb3885e52f1b326fd85ce74915f2` | `4e1144ff2b1369e7b9e8a1a3fb94ea3deee99d5b327cd94b423d148dfa34d25f` | 13,928 |

Directory `ojluni/src/lambda/java/java/lang/invoke/` is tree
`2515e113261e600a39fb1cece2d2f2530a0de510`. Each source has Oracle's GPLv2
header with the Classpath exception. The methods return null or do nothing.

**Module edge.** `openjdk_java_files.bp` (blob
`b8057dee4a8df7a07b24b55d266b634f4bf97b26`) splits the directory into two
filegroups. `openjdk_lambda_stub_files` holds LambdaMetafactory,
SerializedLambda and StringConcatFactory. `openjdk_lambda_duplicate_stub_files`
holds CallSite, MethodHandles, LambdaConversionException, MethodHandle,
MethodType, StringConcatException and `java/lang/runtime/SwitchBootstraps`.
`JavaLibrary.bp` (blob `a7e23325c53b77137fca3b65667caf9a8cec0bad`) defines
two modules:
- `core-lambda-stubs` holds both filegroups.
- `core-lambda-stubs-for-system-modules` holds only the first one, "because
  those classes are also part of the core library public SDK API (since API
  level 26)".

The worker uses the second module, all of it. Two checks back this choice:
- **The module is the exact gap.** Google's `android-36.jar` contains all six
  duplicate classes and SwitchBootstraps, and contains none of the three
  module classes. So the module completes the platform file without shadowing
  any class in it.
- **ECJ needs SerializedLambda, not just LambdaMetafactory.** A serializable
  lambda, `(Runnable & java.io.Serializable) () -> {}`, compiled with only
  LambdaMetafactory fails: "The type java.lang.invoke.SerializedLambda cannot
  be resolved". It compiles with the three files. StringConcatFactory is only
  used from `-9` up (the worker compiles `-1.8`). It is kept so the module stays
  whole.

**License.** The license module is `libcore_ojluni_src_main_license` in
`ojluni/src/main/Android.bp` (blob `49dab3a5a8589c9bdad6742271008eca51f47785`),
with kinds `SPDX-license-identifier-GPL-2.0-with-classpath-exception` and
`SPDX-license-identifier-Apache-2.0`. Its `license_text` is `LICENSE` and
`NOTICE`. Both are kept verbatim in `toolchain/licenses/core-lambda-stubs/`,
which both harness APKs package. The three sources themselves ship in the
worker (`worker/src/main/assets/core-lambda-stubs/java/lang/invoke/`), so the
covered source travels with the program. The provenance row is in
`toolchain/licenses/INDEX.md`, and a paragraph is in `toolchain/NOTICE.txt`
and `INDEX.txt`. Spotless excludes `worker/src/main/assets/**`, so the
sources are never reformatted. Not legal advice. The whole-worker
redistribution gates in `toolchain/NOTICE.txt` remain open.

## How the worker uses them

`LambdaStubs.write(assets, scratch)` copies each pinned source from the APK
assets into the build's scratch directory, compares its SHA-256 with the pin,
and deletes it and fails the build if they differ. `BuildService` adds the
three files to ECJ's sources, where `LambdaBootstrap` used to be. Their
classes land in `classes/java/lang/invoke/`. Only the generated app's package
and its library R classes are passed to D8. D8 desugars every lambda and
method reference, and drops `$deserializeLambda$`. Snapshot paths can't
collide with `core-lambda-stubs/`, because the snapshot policy allows only
`java/dev/smallwright/generated/…`, `res/`, `assets/` and the manifest.

## RED and GREEN (host)

The test is `LambdaCompileTest`. Its counter_04 fixture is the exact app
source from `beffbeb` (`SourceStubsCompileTest`, 41 string lines, diffed
identical), and its control is the same anonymous-listener transformation. It
uses the same compiler and options as the worker: ECJ 4.12,
`-1.8 -proc:none`, with the real `android-36.jar` as boot class path, and
`BuildService.compileJavaOrThrow` with numeric diagnostics.

- **RED.** I ran the fixture test first with no lambda input at all:
  `2 tests completed, 1 failed`. The failure was
  `ECJ id/category/line: 16777540/10/1`, the same numeric problem as the
  phone failure on 2026-10-13. ECJ's prose for that source (SHA-256
  `a1c689d5…25c8`) is "The type java.lang.invoke.LambdaMetafactory cannot be
  resolved. It is indirectly referenced from required .class files". The
  anonymous control passed.
- **GREEN.** With the pinned sources, the test class passes `5/5` and
  `PlatformCompileTest` passes `1/1`.

| Test | Asserts |
| --- | --- |
| `counterLambdaFixtureCompilesAndDexesWithThePinnedLambdaStubs` | compiles; dex has `ExternalSyntheticLambda0` and `1`; no `java/lang/invoke` in the dex |
| `counterLambdaFixtureFailsWithoutTheLambdaStubs` | the platform file alone still fails with `16777540/10/1` (the RED, kept as a guard) |
| `counterAnonymousListenerControlCompilesAndDexesWithoutTheLambdaStubs` | the control compiles with no lambda input; no synthetic lambda or `java/lang/invoke` |
| `serializableLambdaCompilesAndDexesWithoutInvokeReferences` | needs SerializedLambda; no `java/lang/invoke` in the dex |
| `changedLambdaStubSourceIsRefused` | changed bytes fail with "does not match its pin", and the changed copy is deleted from scratch |

`PlatformCompileTest` (platform APIs, a lambda and a method reference) keeps
its assertions and now loads the pinned assets. Its "must not reach the app"
check is unchanged.

Raw outputs (local, not committed) are in
`toolchains/spec35/33/host/`: `red-gradle.txt`, `red.xml`,
`red-ecj-prose.txt`, `green-gradle.txt`, `green.xml`, `chain.txt` and
`release.txt`.

## Host chain

Command:

    gradle.sh <worktree> -q spotlessApply :controller:testDebugUnitTest
      :worker:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug
      :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest
      :controller:lintDebug :worker:lintDebug

The chain ran twice:
- At `770647c`.
- At `839a6ce`, after the coordinator's review fixes. The phone used this
  build.

Both runs had the same results:
- Exit 0.
- Controller: 417 tests, worker: 91 tests, 0 failures, 0 errors.
- Lint: 0 errors (controller 19 warnings, worker 2).

`:worker:assembleRelease` also passed, as a separate run at `770647c`.

| APK (`839a6ce`) | SHA-256 | Bytes |
| --- | --- | --- |
| controller debug | `04cf3c3913a831a93531583336de50b269b4e03f0672b01da2726a90ed4fe069` | 32,920,233 |
| worker debug | `9a4e28fb0f45f6c46ef35b6fee11c489c448e389db567ebf78bc95c7e38166cd` | 21,189,872 |
| controller androidTest | `b4e607f31da191da96823ef9b8e9d9dc0a61b41411d2b05be9ad9f3788fdc506` | 909,294 |
| worker androidTest | `408ccd52666fd9abf7b10c7bb97b6f191404ef20bc242e7673869e3d7245195f` | 96,052 |
| worker release, unsigned (`770647c`) | `4b2e325e7b460c340cc353892e0062af81a2fa5a9d4507f834ee5e3a4a72b6fd` | 19,092,440 |

The `770647c` debug APKs were controller `7ed6bfa7…4708` and worker
`541d2bc1…cf7`.

## APK contents

I unzipped each APK and compared the files byte for byte with `cmp`.

| APK | Three sources | LICENSE, NOTICE | Old `LambdaBootstrap` in dex |
| --- | --- | --- | --- |
| worker debug | identical | identical | absent |
| worker release | identical | identical | absent |
| controller debug | absent (not needed) | identical | absent |
| both androidTest APKs | absent | absent | absent |

The test APKs package no main assets. They run against the installed worker
and controller, which carry the files.

For generated apps, the host tests check the dex D8 produces from the
generated package: the counter_04 fixture, a serializable lambda and the
platform fixture all have no `java/lang/invoke` reference. `generatedClassFiles`
admits only the generated package, so neither the stub classes nor any
android or toolchain class is dexed. The phone-built APK below was checked
on the phone.

## Phone (Galaxy A33, `<A33 serial>`)

The A33 (SM-A336E, Android 16) was connected over USB. Each session was one
locked `phone.sh` script that started the same way:
- Compare the built worker and controller APKs with the installed `base.apk`,
  and reinstall with `adb install -r` if they differ.
- Run a 4-second `getevent` on the touchscreen, and stop if it sees any
  touch.

No model request was made.

**First attempt (18:00 to 18:06, `770647c` build): blocked.** Something was
touching the screen. The New project dialog closed by itself four times
(sessions 2 to 6). Logcat showed the dialog closing on a touch with no
matching "Inject motion" line. `getevent -lt` showed real contact on
`/dev/input/event4` at about x 510–540, y 1140–1146:
- It was already down before my tap, and pressed again every second or so.
- It was still there at 18:06:16.
- When the keyboard pushed the dialog up, the next press landed outside the
  dialog and closed it.

Nothing was created or typed then (screenshots `s2-01`, `s3-03`, `s4-03` and
`s7-idle` show the user's message field empty). The user fixed the screen.

**Second attempt (20:33 to 20:46, `839a6ce` build).**
- At 20:33:52 I installed worker `9a4e28fb…66cd` (was `541d2bc1…cf7`) and
  controller `04cf3c39…e069` (was `7ed6bfa7…4708`) with `adb install -r`.
- Installed hashes equal the built ones in every session.
- `getevent` saw 0 touches at the start of every session.

The disposable project was "Lambda33", `app19abcaf741d149d3a9116d46b5326c4f`.
Its package is `dev.smallwright.generated.app19abcaf741d149d3a9116d46b5326c4f`.

The source was the beffbeb counter_04 lambda source (SHA-256
`a1c689d5…25c8`), with only its package line set to the project's package, as
the 2026-10-13 baseline did. The result has SHA-256
`95a1782121dd28c1401c10ddb372b934b03a52c181a3f31d756c98a2a4db58d3`, 1,356
bytes. I typed it into Project tools → source:
- Tap the field, Ctrl+A, Delete.
- `input text` line by line, with Enter between lines.

The field text, read back through uiautomator, equals it exactly.

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| 1 Fresh build | "Build from this source" at 20:36:15 | "Your app is ready." with "Install on this phone"; HEAD 1. R1 `MainActivity.java` is `95a17821…58d3`, `AndroidManifest.xml` `4a464ffd…f6ec`, `res/layout/empty.xml` `f6933324…bd00`. Receipt at 20:36:19: revision 1, version 1, `app-v1.apk` 12,763 B, `115be914d60c21efa11c78c55d7e6fb3f846a478f9863401802874a084c2b73d`, file hash equal | pass |
| 2 Unchanged-snapshot build | no edit (editor still equals the fixture); "Build from this source" again at 20:42:28 | HEAD 2 at 20:42:32. R2's three files have exactly R1's hashes. Receipt: revision 2, version 2, `app-v2.apk` 12,763 B, `4cec3314583446bcbfc82b8ae6be777c856fb987107095ac906b4a97286955e3`, same signer | pass, at a new revision (see below) |
| 3 Debug install | `run-as dev.smallwright.controller cat …/app-v2.apk \| pm install -r --pkg … -S 12763` (USB debug installer exception, not Android's installer consent) | Success; installed `base.apk` `4cec3314…55e3` equals the receipt; versionCode 2, minSdk 31, targetSdk 36 | pass |
| 3 Signer | receipt and `dumpsys package` | Receipt signer `8af1ff65e769b66e2fef3c599700f2bd1686cc0a20411d1890b82ec772d69031`, not the harness signer `19a7407f…bd25`. Installed signature hash `5a18774f`, controller `ad0260d8` | pass |
| 4 Native checks | controller and worker force-stopped (no pid) | launch `Counter: 0`; Increment 1, 2; Reset 0; force-stop and reopen 0; Increment twice 2; force-stop and reopen 2 | pass |
| 5 APK entries | `unzip -l` in the controller's `run-as` | 7 entries: `META-INF/MANIFEST.MF`, `APP19ABC.SF`, `APP19ABC.RSA`, `AndroidManifest.xml`, `resources.arsc`, `classes.dex` (3,696 B), `res/layout/empty.xml`. No stub source, no LICENSE or NOTICE | pass |
| 5 Dex classes | `/apex/com.android.art/bin/dexdump` on the phone | 5 class defs, all in the project package: `MainActivity`, `MainActivity$$ExternalSyntheticLambda0` and `1`, `R`, `R$layout`. 0 outside it, 0 `java/lang/invoke`, 0 `LambdaMetafactory`, 0 `invoke-custom` | pass |
| 5 Byte search | `unzip -p … \| grep -a -c` on the phone | 0 matches each for `java/lang/invoke`, `LambdaMetafactory`, `SerializedLambda`, `StringConcatFactory`, `core-lambda-stubs`, `Classpath`, `GNU General Public`, `Oracle`, `dev/smallwright/worker`, `org/eclipse/jdt`, `com/android/tools/r8` | pass |
| 6 Cleanup | Project tools → Delete this project → DELETE PROJECT | Lambda33 gone from the project list and from `files/projects/`. The app is on "Main project" (selected again from the menu), its chat unchanged. The generated test app was uninstalled with `adb uninstall` | pass |

**Unchanged snapshot.** The ordinary Build always stores the editor text as a
new revision, so the second build is revision 2, not a replay of revision 1.
Its snapshot is byte-identical to R1, and it went through the same path:
`LocalBuildCoordinator`, then the worker's `BuildService`. The worker runs as
`u0_a318`, the controller as `u0_a319`. The two APKs differ only because the
version code is 2.

The only replay probe, `A33Counter04Probe` `diagnoseBaselineBuild`, works
only on the old failed `a33_counter04_20261012_01` project and its pins. It
is one-shot, and it predates `usePlatform`, so it was not used.

Bytes and the counter text:
- Only hashes, sizes, entry names and dexdump class listings left the phone.
  No APK or source bytes did.
- The counter text overlaps the status bar, because the fixture adds no
  window insets. That is the fixture's own layout.

Screenshots (local, not committed) are in `toolchains/spec35/33/`:
- `a-01-created.png`, `a-02-tools.png`
- `b-01-source-typed.png`, `b-02-after-build.png`, `c-01-chat-after-build.png`
- `d-01-tools-unchanged.png`, `d-02-chat-after-rebuild.png`
- `e-01-launch.png` to `e-07-reopen.png`
- `f-01-delete-dialog.png`, `f-02-back-on-user-project.png`

Other local files:
- `b-field-text.txt` (the field text read back);
- `e-zip-entries.txt`, `e-dexdump.txt`;
- session scripts and logs in
  `~/.local/share/smallwright/spec35/t33/`.

## Not run

- **The frozen counter_04 lifetime-tap model request.** #33 makes no model
  requests. The native baseline above belongs to a fresh disposable project,
  now deleted. The frozen request's own route (`A33Counter04Probe`, run
  `a33_counter04_20261012_01`) still has no verified baseline. Its flags and
  source were not touched.
- **A replay of revision 1 itself.** No route in the app does it; see above.
- **Production installation.** Android's installer confirmation was not
  used; only the debug installer was.
- **Benchmark, human or full-API claims.** None are made.
