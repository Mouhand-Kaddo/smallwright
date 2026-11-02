# API 36 platform input experiment

The current released candidate worker uses `SourceStubs` and the device's
`/system/framework/framework-res.apk`. It has limited API coverage. This
experiment adds an **opt-in debug build only** path to test complete platform
inputs without presenting the official SDK package as cleared for distribution.

## Exact local reference and mechanical check

The ignored local SDK Platform 36 revision 2 `android.jar` has SHA-256
`d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`.
It is the [published r2 artifact](https://android.googlesource.com/platform/prebuilts/fullsdk/platforms/+/1b45636761b0dbab9f80779add8fc85ab94b80fb/android-36/android.jar).
It contains 6,227 class entries and a framework resource table. Host AAPT2
`dump resources` recognizes it as package `android` (ID `01`) with public
resource entries. `scripts/audit_platform_inputs.py` compares class names and
the framework table against this reference and reports byte hashes; it cannot
establish API-signature parity or redistribution rights. Run:

```bash
python3 -m unittest discover -s scripts -p test_audit_platform_inputs.py
python3 scripts/audit_platform_inputs.py \
  --reference toolchains/android-sdk/platforms/android-36/android.jar \
  --api toolchains/android-sdk/platforms/android-36/android.jar \
  --resources toolchains/android-sdk/platforms/android-36/android.jar
```

With the opt-in `-PexperimentalPlatformJar=` property, only the worker's
separate `platformProbe` build type stages that exact JAR as an asset. That
type has application ID `dev.smallwright.worker.platformprobe` and cannot
replace the normal installed worker. The worker verifies its SHA-256 and
required ZIP entries at runtime, then uses it as ECJ's boot/application
classpath and AAPT2's `-I` resource input for snapshot builds. Generated DEX
still includes only project class files. The property has no effect on the
release or default debug variants.

## Physical S23 compiler result — 2026-09-27

The connected Samsung SM-S918B (Android 16) ran a worker instrumentation mode
that creates a disposable snapshot in worker cache. Its source declares a
public `android.graphics.pdf.PdfDocument` field, which the handwritten stubs
do not provide. Its layout refers to
`?android:attr/textColorPrimary`. The probe exercises the real
`BuildService.buildSnapshot` path, checks for `classes.dex` and
`resources.arsc`, and removes the unsigned APK and scratch. It does not
install the generated app.

The separate probe APK without the platform asset returned
`Narrow input: PdfDocument source rejected before APK creation` with
`INSTRUMENTATION_CODE: -1`. After an opt-in rebuild, the probe APK's embedded
JAR had SHA-256 `d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`.
The same test returned `Full API 36 input: PdfDocument source and framework
resource reference built to disposable unsigned APK under worker UID` with
`INSTRUMENTATION_CODE: -1`. The resulting probe APK SHA-256 was
`12e19809c08a9cb741c4eb096c1553612aedf0ceabe6ed6baf3afd3e5bba3d4f`.
The normal worker's installed package path was identical before and after;
both separate probe packages were uninstalled. This establishes one real
physical compile/link test with a complete reference input. It is not public
API parity, generated-app runtime, independent AOSP source provenance, or
distribution clearance.

Commands used (with `source scripts/env.sh` and device serial
`<S23 serial>`):

```bash
./gradlew :worker:testDebugUnitTest --tests 'dev.smallwright.worker.ExperimentalPlatformInputTest' --offline --no-daemon --max-workers=1
./gradlew :worker:assemblePlatformProbe :worker:assemblePlatformProbeAndroidTest -PplatformProbeTests=true --offline --no-daemon --max-workers=1
adb install worker/build/outputs/apk/platformProbe/worker-platformProbe.apk
adb install worker/build/outputs/apk/androidTest/platformProbe/worker-platformProbe-androidTest.apk
adb shell am instrument -r -w -e mode experimentalPlatform dev.smallwright.worker.platformprobe.test/dev.smallwright.worker.ConfinementInstrumentation
./gradlew :worker:assemblePlatformProbe :worker:assemblePlatformProbeAndroidTest -PplatformProbeTests=true -PexperimentalPlatformJar=toolchains/android-sdk/platforms/android-36/android.jar --offline --no-daemon --max-workers=1
adb install -r worker/build/outputs/apk/platformProbe/worker-platformProbe.apk
adb shell am instrument -r -w -e mode experimentalPlatform dev.smallwright.worker.platformprobe.test/dev.smallwright.worker.ConfinementInstrumentation
adb uninstall dev.smallwright.worker.platformprobe.test
adb uninstall dev.smallwright.worker.platformprobe
```

## API 31 resource result — 2026-09-27

A fresh ignored `phone-local-platform-input-api31` x86_64 AVD booted with
`ro.build.version.sdk=31`. The separate probe worker and its instrumentation
APK installed there. AAPT2 compiled XML containing the API 36 public
`?android:attr/adServiceTypes` reference. Linking it against the emulator's
`/system/framework/framework-res.apk` failed as expected; linking the same
compiled XML against the pinned API 36 JAR succeeded and produced a resource
APK with `resources.arsc`. The instrumentation returned
`INSTRUMENTATION_CODE: -1`. The probe packages were uninstalled and the
emulator stopped. Worker probe APK SHA-256 was
`12e19809c08a9cb741c4eb096c1553612aedf0ceabe6ed6baf3afd3e5bba3d4f`;
test APK SHA-256 was
`eb9c73d2bf8d0d8804477611aa91fc3ec5ffd2dc175b15a9d130c9dac4f61a75`.
This demonstrates the newer canonical resource table can support compilation
on an older device. It does not establish runtime support for newer APIs or
clear the official SDK artifact for distribution.

## Native note editor compile probe — 2026-09-28

After the worker gained AAPT2-generated project `R.java`, the opt-in probe was
extended with a native Java/XML note editor. Its source uses `EditText`,
`TextWatcher`, `Editable`, `SharedPreferences`, `setContentView(R.layout...)`,
and `findViewById(R.id...)`; its layout declares `@+id/note`. This is a fixed
test source, not an AI-generated app or a claim that the release worker can
compile arbitrary apps.

On the disposable API 36 x86_64 emulator, the normal worker test returned
`Narrow input: PdfDocument and note editor APIs rejected before APK creation`
with raw instrumentation code `-1`; the probe checked that neither failed
snapshot produced an APK. The isolated `platformProbe` worker built with the
exact pinned SDK JAR returned `Full API 36 input: PdfDocument and native note
editor with generated R compiled to disposable unsigned APKs under worker UID`
with raw code `-1`. It checked the note layout and DEX, project `R$id`, and
absence of framework classes in generated class definitions. The probe removed
its unsigned APKs after the run. No note app was signed, installed, launched,
or exercised, and the official SDK input remains test-only.

Commands, with `source scripts/env.sh` and `emulator-5556`:

```bash
./gradlew :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1
adb install -r worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk
adb shell am instrument -w -r -e mode experimentalPlatform \
  dev.smallwright.worker.test/dev.smallwright.worker.ConfinementInstrumentation
./gradlew :worker:assemblePlatformProbe :worker:assemblePlatformProbeAndroidTest \
  -PplatformProbeTests=true \
  -PexperimentalPlatformJar=toolchains/android-sdk/platforms/android-36/android.jar \
  --offline --no-daemon --max-workers=1
adb install -r worker/build/outputs/apk/platformProbe/worker-platformProbe.apk
adb install -r worker/build/outputs/apk/androidTest/platformProbe/worker-platformProbe-androidTest.apk
adb shell am instrument -w -r -e mode experimentalPlatform \
  dev.smallwright.worker.platformprobe.test/dev.smallwright.worker.ConfinementInstrumentation
```

## Product artifact route

The source-built route is to pin an Android 16 AOSP source manifest and build
the public `android_stubs_current` module plus
`framework-res{.export-package.apk}` from that same tree. The
[framework stub build rule](https://android.googlesource.com/platform/frameworks/base/+/d6018d5984e6c0071f600c18ae21414d87f589e0/StubLibraries.bp)
shows the public stub module and its merged dependencies. The
[framework resource build rule](https://android.googlesource.com/platform/frameworks/base/+/cde769284360e06acdfb5763c56eb7d696247032/core/res/Android.bp)
states that the exported package contains product-agnostic IDs and type
definitions. These are concrete build targets, not a claim that a build has
been performed or that these cited revisions match SDK r2.

Before bundling that output, record the exact source manifest and component
lineage, verify applicable grants and notices for every included component,
provide corresponding source where required, and compare public/protected
signatures, constants, inheritance, generics, annotations and resource IDs
against the selected SDK. Then run ECJ/AAPT2/D8 and install/runtime tests on
the S23. The SDK package's own terms do not give a blanket redistribution
grant for its `android.jar`; see [the license audit](../research-sdk-api-license.md).

## Declaration ABI preflight — 2026-09-28

`scripts/compare_platform_abi.py` now compares class names and visible Java
declarations in two JARs without requiring byte-identical compiler output. It
normalizes superclass/interfaces, public/protected fields and methods,
descriptors, generic signatures, constant values, throws declarations,
annotation values/defaults (including parameter and type annotations), inner
class access and record/sealed metadata. Method bodies, private members and
compiler version are excluded. This is a mechanical preflight, not a complete
semantic or legal equivalence proof. Resource table parity remains a separate
check in `scripts/audit_platform_inputs.py`; runtime behavior and licensing
still require their own gates.

Three host tests use independently compiled Java fixtures. They verify that
private implementation changes pass, while individual changes to a public
constant, method name, generic signature, superclass and annotation fail;
missing classes and duplicate ZIP entries are also rejected. The pinned API
36 r2 JAR compared against itself parsed all 6,227 classes and reported
`public_api_match: true`, with empty missing/extra/different lists. There is
**no source-built candidate yet**, so no parity claim follows from that
self-comparison. Run:

```bash
source scripts/env.sh
python3 -m unittest discover -s scripts -p test_compare_platform_abi.py -v
python3 scripts/compare_platform_abi.py \
  --reference toolchains/android-sdk/platforms/android-36/android.jar \
  --candidate toolchains/android-sdk/platforms/android-36/android.jar
```

## Installed note editor across API 36 and API 31 emulators — 2026-09-28

A separate controller instrumentation route, `experimentalNoteOnly`, now
exercises a fixed native note editor through the actual controller-to-worker
Binder boundary. On disposable API 36 and API 31 x86_64 emulators, it made a
new project, committed source using `EditText`, multi-line `InputType` and
`TextWatcher`, sent the immutable snapshot to the **opt-in** platform-probe
worker, validated and signed the result in the controller with that project's
identity, restored the signed receipt as needed, accepted Android's visible
install confirmation, launched the installed app, edited the note and verified
the text after force-stopping and reopening that generated app. Both runs
returned raw `INSTRUMENTATION_CODE: -1`. The normal-worker counter installation
journey also returned `-1` on API 31 after this test addition.

The platform-probe worker carries the exact pinned API 36 r2 SDK JAR as a
**local test-only asset**. `controller/src/debug/AndroidManifest.xml` adds
narrow visibility of that test worker on Android 12; the release manifest is
unchanged. On API 31, the test first failed to bind without this query, then
failed because its own `InputStream.readAllBytes()` helper was unavailable;
both were corrected before the passing run. The project test route is guarded
to emulator hardware. No model request or physical S23 run happened here.
This demonstrates one basic app's backward runtime behavior, not complete API
parity for each Android version, a distributable SDK input, or support for all
third-party libraries.

The API 31 AVD is `toolchains/avd/phone-local-platform-input-api31.avd` and
uses the installed Android 31 default x86_64 system image. Only one emulator
was running at a time. After building and installing the opt-in platform-probe
worker and current debug controller/test APKs on each emulator, the checks used:

```bash
adb -s emulator-5556 shell am instrument -w -r -e experimentalNoteOnly true \
  dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
adb -s emulator-5556 shell am instrument -w -r -e projectInstallOnly true \
  dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
```

Host APK paths for this run are
`controller/build/outputs/apk/debug/controller-debug.apk`,
`controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk`,
`worker/build/outputs/apk/debug/worker-debug.apk`, and
`worker/build/outputs/apk/platformProbe/worker-platformProbe.apk`.

## Pinned AOSP prebuilt comparison — 2026-09-28

The Android 16 platform tag alone does not identify the exact published SDK
revision. In a local, ignored technical probe, `prebuilts/sdk` tag
`android-16.0.0_r2` at commit
`3b7daadc4e54d51439e5ae9b4dbd96cda8b6104e` supplied a public
`36/public/android.jar` with SHA-256
`738e68485655e955b2703adfdd838f27b62a2f5fc4f95b2ad1be82ca3d7a9f46`.
It had all 6,227 class names but differed from the pinned API 36 revision 2
SDK reference in 21 visible class declarations and its `resources.arsc`.
The class differences included `android.widget` listener types, `TextUtils`,
and several ad services and ranging types. The same repository's
`android-16.0.0_r4` commit
`8c36435476cbaea0a4170a327e55dd4775cd60b9` supplied a JAR with
SHA-256 `d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`:
byte-for-byte the pinned SDK reference. The ABI preflight reported no class
declaration differences for r4. The earlier tag failed a meaningful parity
gate even though its class count was identical.

These are **prebuilt SDK artifacts**, not independently rebuilt AOSP outputs.
The [r4 repository's `Android.bp`](https://android.googlesource.com/platform/prebuilts/sdk/+/8c36435476cbaea0a4170a327e55dd4775cd60b9/Android.bp)
warns that this package has special licensing conditions and applies an
aggregate license list; its [root `NOTICE`](https://android.googlesource.com/platform/prebuilts/sdk/+/8c36435476cbaea0a4170a327e55dd4775cd60b9/NOTICE) starts
with the Android SDK agreement. Exact public Git identity therefore does not
clear redistribution or use as this app builder's bundled SDK. The r4 JAR is
only a technical reference here. The independent source path still needs the
many public module inputs named by the pinned
[`frameworks/base/api/Android.bp`](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/api/Android.bp), matching
framework resources, component license/notice evidence, and a candidate that
passes both ABI and resource comparisons.

Reproduce the archive checks after obtaining the pinned artifacts locally:

```bash
python3 scripts/compare_platform_abi.py \
  --reference toolchains/android-sdk/platforms/android-36/android.jar \
  --candidate toolchains/platform-api36-source-probe/aosp-android-36-r4.jar
python3 scripts/audit_platform_inputs.py \
  --reference toolchains/android-sdk/platforms/android-36/android.jar \
  --api toolchains/platform-api36-source-probe/aosp-android-36.jar \
  --resources toolchains/platform-api36-source-probe/aosp-android-36.jar
```

## Signature-to-stubs feasibility probe — 2026-09-28

I built [Metalava](https://android.googlesource.com/platform/tools/metalava/+/a2548e478c121d5801137d636f6e83af37eafc99)
from its `android-16.0.0_r2` source tag using the project JDK 21, Gradle
8.12 and one Gradle worker. Its `installDist` task exited 0. Metalava read
the r2 `prebuilts/sdk/36/public/api/android.txt` signature (SHA-256
`343e79ade62e51e8f8044b1443a7ea0a047e545ea3250a8c9db550b92b7f7ab2`)
and generated 4,110 Java stub files. JDK 21 `javac` compiled all generated
files with `-source 8 -target 8 -bootclasspath ''`; it exited 0 with only
three obsolete-option warnings. The resulting ignored local class JAR has
SHA-256 `b296b5a1c4e837c8a5110e564689c130484b1d884a6589422b63a864596014ea`.

That JAR **fails** the exact API 36 r2 declaration preflight: it has 6,222
classes versus 6,227 in the reference. The absent classes are
`android.annotation.{FlaggedApi,NonNull,Nullable}` and
`androidx.annotation.{RecentlyNonNull,RecentlyNullable}`. The ABI script also
reported 3,550 different common classes. Some differences are annotations or
compiler metadata; others are actual declarations. For example, Metalava's
signature-derived `EditText` source omitted eight declared overrides present
in the reference JAR, including `setText(CharSequence, TextView.BufferType)`.
The API signature is a useful input and inventory, but by itself it does not
recreate the reference class ABI. This candidate also has no framework
resource table and cannot be used as the worker's complete platform input.

The signature text came from a **prebuilt SDK repository** whose root `NOTICE`
contains the SDK agreement. Recompiling that text does not resolve its
distribution/use question or establish independent AOSP source provenance.
Do not package this candidate. The next engineering path remains a pinned
source-derived build of the combined public modules and framework resources,
followed by class, declaration and resource checks.

The local commands were:

```bash
# Run in toolchains/platform-api36-source-probe/metalava. Its wrapper script
# overrides JAVA_HOME, so invoke GradleWrapperMain with the project JDK.
JAVA_HOME="$PWD/../../jdk-21.0.12.1+1" \
GRADLE_USER_HOME="$PWD/../../gradle-home" OUT_DIR="$PWD/../out" \
../../jdk-21.0.12.1+1/bin/java -Xmx256m \
  -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
  :metalava:installDist --no-daemon --max-workers=1 \
  -Dorg.gradle.parallel=false -Dorg.gradle.configuration-cache=false \
  -Dorg.gradle.jvmargs=-Xmx1536m --quiet

# Run in the repository root with toolchains/platform-api36-source-probe as
# the ignored output directory.
JAVA_HOME="$PWD/toolchains/jdk-21.0.12.1+1" JAVA_OPTS=-Xmx2048m \
  toolchains/platform-api36-source-probe/out/metalava/metalava/build/install/metalava/bin/metalava \
  --quiet main --api-class-resolution api \
  --stubs toolchains/platform-api36-source-probe/stubs \
  toolchains/platform-api36-source-probe/android-36-public-api.txt
find toolchains/platform-api36-source-probe/stubs -name '*.java' | sort \
  > toolchains/platform-api36-source-probe/sources.list
toolchains/jdk-21.0.12.1+1/bin/javac -J-Xmx2048m -source 8 -target 8 \
  -bootclasspath '' -d toolchains/platform-api36-source-probe/classes \
  @toolchains/platform-api36-source-probe/sources.list
toolchains/jdk-21.0.12.1+1/bin/jar --create \
  --file toolchains/platform-api36-source-probe/signature-built-classes.jar \
  -C toolchains/platform-api36-source-probe/classes .
python3 scripts/compare_platform_abi.py \
  --reference toolchains/android-sdk/platforms/android-36/android.jar \
  --candidate toolchains/platform-api36-source-probe/signature-built-classes.jar
```

## Raw AOSP source stub probe — 2026-09-28

The local `frameworks/base` checkout is the exact
[`android-16.0.0_r4` source commit](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4).
I passed its raw `core/java/android/widget/EditText.java` through pinned
Metalava with the reference JAR as a **host-only resolution classpath**. The
generated source retained all 20 methods in the reference class, including
the eight overrides absent from signature-derived output. JDK 21 compiled it,
and `ClassFile.abi()` matched the reference `EditText.class` exactly. The raw
source file carries an Apache-2.0 header; this one-file result does not prove
the rights or completeness of the combined platform input.

The same local procedure on raw `SharedPreferences.java`, `Bundle.java` and
`TextView.java` preserved all reference method keys. Without Metalava's
`--include-annotations`, the first two classes differed in nullability
attributes. After enabling that option and placing the five **public** AOSP
Metalava stub-annotation sources on `javac`'s classpath, the generated
`SharedPreferences` (including its two nested types) and `Bundle` matched
their reference class ABIs exactly. AOSP's
[`private-stub-annotations-jar` build rule](https://android.googlesource.com/platform/tools/metalava/+/a2548e478c121d5801137d636f6e83af37eafc99/Android.bp)
then makes these annotation classes package-private in the final SDK JAR;
the public forms are needed while compiling annotated stubs.

`TextView` initially failed the annotated compile: its source uses two
`com.android.text.flags.Flags` constants generated from `.aconfig` files.
Without that generated input, Metalava emitted unresolved `FLAG_*` references
inside `@FlaggedApi` annotations. This is a concrete reason to follow the
AOSP build graph and flag generation instead of compiling arbitrary raw files
in isolation. The follow-up below resolved this one-class probe; the remaining
combined public module and resource build has not run. The
[`android-16.0.0_r4` manifest](https://android.googlesource.com/platform/manifest/+/15128c9e27cfa599c48d294babd39286ee8f1426/default.xml)
pins the repository set; `frameworks/base/api/Android.bp` names 33
unconditional public bootclasspath inputs plus release-flag selections.
Official [AOSP workstation guidance](https://source.android.com/docs/setup/start/requirements)
calls for at least 64 GB RAM and 400 GB free space for a full checkout and
build, above this laptop's measured 15 GiB RAM and 365 GiB free at the start
of this probe. A targeted source build may still be feasible; a full tree
build was not attempted here.

### Generated text flags and `TextView` parity

The same pinned Android 16 r4 checkout includes AOSP's Rust `aconfig`
generator at `build/make/tools/aconfig` (commit
`b815dded1eafbf06191a6ae306956bb6ed6fb415`). A standalone Cargo build
initially stopped because its build script assumes a sibling
`prebuilts/sdk/*/finalized-flags.txt` tree. I copied the 1.5 MiB pinned
`aconfig` source and the r4 finalized-flag records into that expected layout
under ignored `toolchains/`. After an initial build with a manually invoked
build script, a **clean** `cargo build --offline --locked --release -p aconfig
-j1` completed from that layout in 4m 15s. The clean host generator has
SHA-256 `1f393c5a5d33a65df437824a88adb5d3b6e9e3e430654195ef83b497bc483a85`.
The exact Cargo lockfile and all 99 registry dependency versions and declared
license expressions are recorded in
[`aconfig-host/Cargo.lock`](aconfig-host/Cargo.lock) and
[`aconfig-host/dependencies.json`](aconfig-host/dependencies.json). This is
host-tool provenance, not license clearance for a release worker.

The generator read both AOSP `core/java/android/text/flags/*.aconfig` files,
then produced `com.android.text.flags.Flags` (SHA-256
`b3d4e43ad6c718d9358af011c03fb34e0fde26ddfc2e7c1dc7bd2177a5766ba0`).
The clean and earlier host binaries produced identical cache bytes and
`Flags.java` bytes despite differing binary hashes.
Metalava resolved the flag constants from that generated source. Its output
then compiled with public AOSP stub annotations on the host classpath. The
reference `TextView` and this output had the same class/member names and
descriptors; eight methods still carried `@FlaggedApi` in the raw-source
output where the finalized SDK reference omits it. An isolated rerun using
Metalava's `--exclude-annotation android.annotation.FlaggedApi` produced
`TextView.class` and three nested class files whose parsed declaration ABIs
all matched the reference. This exclusion was verified **for this class only**;
it is not a rule for all platform flags. A complete source build must apply
the release's flag-finalization behavior and compare every class before use.
The SDK JAR remained a host-only resolution classpath throughout these probes,
and no generated flags or candidate class JAR entered the worker APK.

The decisive commands, after arranging the pinned AOSP files in the ignored
standalone layout and building its `aconfig` binary, were:

```bash
toolchains/platform-api36-source-probe/cargo-target-clean/release/aconfig \
  create-cache --package com.android.text.flags --container system \
  --declarations toolchains/aapt2-source-clean-20260927/vendor/base/core/java/android/text/flags/flags.aconfig \
  --declarations toolchains/aapt2-source-clean-20260927/vendor/base/core/java/android/text/flags/24Q3.aconfig \
  --cache toolchains/platform-api36-source-probe/text-flags-clean.cache
toolchains/platform-api36-source-probe/cargo-target-clean/release/aconfig \
  create-java-lib --cache toolchains/platform-api36-source-probe/text-flags-clean.cache \
  --out toolchains/platform-api36-source-probe/text-flags-clean-java --mode production
JAVA_HOME="$PWD/toolchains/jdk-21.0.12.1+1" JAVA_OPTS=-Xmx2048m \
  toolchains/platform-api36-source-probe/out/metalava/metalava/build/install/metalava/bin/metalava \
  --quiet main --include-annotations \
  --exclude-annotation android.annotation.FlaggedApi \
  --source-files toolchains/aapt2-source-clean-20260927/vendor/base/core/java/android/widget/TextView.java,toolchains/platform-api36-source-probe/text-flags-clean-java/com/android/text/flags/Flags.java \
  --classpath toolchains/android-sdk/platforms/android-36/android.jar:toolchains/platform-api36-source-probe/public-annotation-classes \
  --stubs toolchains/platform-api36-source-probe/raw-textview-finalized
```

## Standalone AOSP framework resource probe — 2026-09-28

The pinned [`core/res/Android.bp`](https://android.googlesource.com/platform/frameworks/base/+/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/core/res/Android.bp)
builds `framework-res` and exports a product-independent
`.export-package.apk` for other builds. This tree contains the 11,187 source
resource files but not that Soong output. A standalone **host-only** probe used
SDK Build Tools 36.0.0 AAPT2 (`2.20-13193326`) to compile the entire source
`core/res/res` directory. A first pass failed on two read-only resource flags:
`android.appwidget.flags.use_smaller_app_widget_system_radius` and
`android.app.notifications_redesign_fonts`. Supplying both as `:ro=false`
compiled all 11,187 files. **False was an exploratory value, not a verified
Android 16 release configuration.**

The source manifest and resources contain 72 distinct `android:featureFlag`
names (70 manifest-only, two used in resources). Linking needed values for
all 72, AAPT2's legacy `-x` framework package ID option, and explicit minimum
and target SDK 36. `--package-id 0x01` is rejected by AAPT2; `-x` is its
documented framework option. The host AAPT2 also rejected the source
manifest's `<valid-purpose>` element, which the pinned AOSP AAPT2 source
recognizes. `--warn-manifest-validation` let the investigation proceed while
reporting that warning. Link also warned that three strings without defaults
were removed. The resulting **noncanonical** resource APK is 35 MiB and has
SHA-256 `966b7bbbfa96eb78f982f31af866a7e80f291cda771992977b496f84a3994fb1`.
It was not copied into any worker.

`scripts/audit_platform_inputs.py` found a different `resources.arsc` SHA-256
(`141e1cb784784527f424b662ec3371b5c0f0d4c684c92bc09c4b6471c9fd95bb`
versus SDK `760f96db566cca5237fb126a2559cebe7455b07e47c15eeed050c4a73fea2d54`).
Parsing `aapt2 dump resources` for all `PUBLIC` entries, including entries
with `STAGED_ID`, found 3,027 unique SDK names and 3,040 source names: zero
SDK names absent, 13 source-only names, and 48 shared names with different
first-listed IDs. Several differences are staging group IDs; the exact
export's release filtering and staged alias behavior still need comparison.
The reference has one duplicate public name across staged aliases; this
source output has 27. The earlier parser that required `PUBLIC` at the end
of a line undercounted these entries, so only the corrected counts are used.

A tiny host XML consumer with `android:adServiceTypes` linked against
both the source resource APK and the pinned SDK. Both generated the same
`0x010106a4` style attribute reference. This proves that one public
reference resolves; it does not make the source table equivalent to SDK 36.
The ignored probe outputs are under
`toolchains/platform-api36-source-probe/resource-standalone-*` and
`toolchains/platform-api36-source-probe/resource-consumer/`.

The pinned [Android 16 r4 `build/release` tag](https://android.googlesource.com/platform/build/release/+/0998133091794b56253fd6bd5c33ed0e0e5bd9de)
maps `aosp_current` to `bp4a`, which inherits `bp3a`, `bp2a`, `bp1a`,
`ap4a` and `ap3a`. Its `bp3a` value for
`android.app.notifications_redesign_fonts` and `bp2a` value for
`android.appwidget.flags.use_smaller_app_widget_system_radius` both say
`ENABLED` and `READ_ONLY`. Recompiling all 11,187 resources with those two
flags `:ro=true` and linking with the last value in that inheritance chain
for each of the 72 names succeeded. The release checkout has explicit values
for 52 names, all enabled; the remaining 20 were **assumed false** for this
probe and still need default/other-repository verification. The new ignored
`resource-bp4a-export.apk` hashes to
`b9b99c486207763c34afa3d78b4c5f021abe7f498d60591a2f8b79e36348e6db`;
its resource-table SHA-256 is
`d3074f87108c0f5958efdfc2ce1cfe941ac08d020eb28a807caa897eb35e8949`.
The public-name and first-listed-ID counts stayed 3,040, 13 source-only,
and 48 differing. In one example, the SDK reference exposes
`dimen/config_motionExpressiveDefaultEffectDamping` only at staged ID
`0x01af0009`, while the r4 source output exposes final ID `0x01050014`
with that staged alias. This points to SDK-export finalization differences,
not simply the two resource flag values. The table still fails exact parity.

Reproduction from the repository root, after acquiring the pinned source
tree, uses `aapt2 compile --dir
toolchains/aapt2-source-clean-20260927/vendor/base/core/res/res
--feature-flags
android.appwidget.flags.use_smaller_app_widget_system_radius:ro=false,android.app.notifications_redesign_fonts:ro=false
-o toolchains/platform-api36-source-probe/resource-standalone-flat`.
Package the resulting `.flat` files as a ZIP, then run `aapt2 link -x
--private-symbols com.android.internal --no-auto-version
--auto-add-overlay --enable-sparse-encoding --warn-manifest-validation
--min-sdk-version 36 --target-sdk-version 36 -R <flat-zip>
--manifest <core/res/AndroidManifest.xml> --feature-flags <all-72-as-:ro=false>
-o <probe.apk>`. The 72 names are the distinct values of
`android:featureFlag\s*=\s*"!?([^"]+)"` in that manifest and resource XML.
The canonical Soong export, verified values for all flags, generated
remote-color resource zip, matching AAPT2 build and applicable asset notices
are still required
before a release input can be selected. No phone test ran in this probe.

### Earlier source tag matches public resource IDs

The [`frameworks/base` Android 16 r2 tag](https://android.googlesource.com/platform/frameworks/base/+/99b01a65cc4c104933788b3143285ab6bae65827)
has 11,158 `core/res/res` source files. Its `public-staging.xml` still lists
`alternateLauncherIcons` and the motion token resources as staged; it does
not declare the 13 source-only names found in r4. The matching
[`build/release` r2 tag](https://android.googlesource.com/platform/build/release/+/5da94f17b7a143d754a3b250c08bff210d3b046b)
maps `aosp_current` to `bp2a`. The r2 resource tree has 41 flag names (40 in
the manifest, one in resource XML). Its single resource flag,
`android.appwidget.flags.use_smaller_app_widget_system_radius`, is explicitly
enabled/read-only in `bp2a`. Across the inherited r2 release configuration,
31 of the 41 names have explicit enabled values. The other ten were assumed
false in this probe; that default still needs verification.

The pinned r4 CMake adaptation successfully built a **host** AAPT2 from the
r4 source tree: `ninja -C
toolchains/platform-api36-source-probe/aapt2-host-r4-build -j2 aapt2`
exited 0. `aapt2 version` reports `2.20-android-16.0.0_r4`; executable SHA-256
is `591f4fb3956a04c5a9caf4709b6050c332fb2947fd0018e2e1ec49ab2c0d4d66`.
This is a host probe built with system libraries, not a releasable binary
provenance claim or an exact r2 AAPT2 reproduction.

That host AAPT2 compiled all 11,158 r2 resources with `--pseudo-localize` and
the one resource flag `:ro=true`. It linked with the r2 manifest, 41 supplied
flag values, Soong-like `--private-symbols`, `--no-auto-version`,
`--auto-add-overlay`, `--enable-sparse-encoding`,
`--enable-compact-entries`, `--no-static-lib-packages`, and SDK 36 min/target
values. A separate run included the generated remote-color resource input
from `core/res/Android.bp`. The latter 35 MiB ignored output is
`toolchains/platform-api36-source-probe/resource-r2-with-remote-export.apk`
(SHA-256 `2db21741ad5f53b26414d6163984878154ef8a88994c24be6f621247f706aecb`).
Link exited 0 with one warning that a no-default `user_switched` string was
removed. No `valid-purpose` warning or legacy `-x` option was needed with
the r4 AAPT2 source.

The corrected `PUBLIC` inventory now **matches the pinned SDK exactly in
names and first-listed IDs**: 3,027 unique names in each, one duplicate
staged alias in each, zero missing, zero extra, and zero ID differences.
The repeatable check is:

```bash
python3 scripts/compare_public_resources.py \
  --aapt2 toolchains/platform-api36-source-probe/aapt2-host-r4-build/vendor/aapt2 \
  --reference toolchains/android-sdk/platforms/android-36/android.jar \
  --candidate toolchains/platform-api36-source-probe/resource-r2-with-remote-export.apk
```

It returned exit 0. The same comparator returned 13 extra names and 48
different first-listed IDs for the r4 source output. Its three focused
parser/comparison tests passed; the parser includes `PUBLIC STAGED_ID` and
counts duplicated staged aliases rather than silently dropping them.
This is a mechanical public-symbol result. The full `resources.arsc` still
differs: source SHA-256
`13abbf4c533297fc0d4fd9641e8db080e60958182b43c18c325386f335b648c1`
versus SDK
`760f96db566cca5237fb126a2559cebe7455b07e47c15eeed050c4a73fea2d54`.
Of the 3,027 first-listed public resource bodies rendered by `aapt2 dump
resources`, 2,993 matched textually; 34 differed. The differences include
extra pseudo-locale `en-rXC` output from the r4 host tool and two raw HTML
resources with an extra `rm` locale. This comparison does not yet cover
private resource behavior, all ZIP entries, exact asset content, or the
canonical r2 Soong export. A tiny host consumer linked
`android:adServiceTypes` against this source output and resolved attribute
ID `0x010106a4`, the same as the SDK consumer. This host comparison did not
use a worker or connected device; the opt-in emulator follow-up is below.

### Split resource input on Android 12 and 16 emulators

The worker's local `platformProbe` build now accepts a separately pinned
`experimentalPlatformResources` APK for AAPT2, while Java compilation still
uses the exact test-only SDK reference JAR. Both inputs are checksum-checked
at build time and when copied into worker scratch. The resource-only archive
needs its compiled manifest/table, not Java class entries. The resource copy
was capped at 48 MiB at commit `963ad53` and invalid copies are removed. This is an opt-in test
route; neither input is staged into the normal worker. A Gradle `Sync` task
removes stale optional assets when switching to a JAR-only probe build.

On API 31 x86_64, `api36ResourceOnApi31` confirmed that the emulator's device
framework rejected the API 36 `adServiceTypes` reference and that the
source-derived candidate table linked it under worker UID. Raw
`INSTRUMENTATION_CODE` was `-1`. `experimentalPlatform` then exercised real
snapshot builds of `PdfDocument` and the native note editor, including
generated `R` and app-only DEX, using the separate source resource table.
It passed on API 31 and API 36 with raw code `-1`. The first API 31 attempt
failed in test-only `InputStream.readAllBytes()`, which is absent on that
runtime; the test now uses a bounded 1 MiB stream read.

The controller's existing `experimentalNoteOnly` journey also passed with
this worker on **both** emulators: controller-to-worker build, per-project
signing, explicit Android install confirmation, native text editing and
restart persistence. The first API 36 install attempt was blocked by an
emulator System UI ANR dialog covering the installer and returned code `0`.
After dismissing that dialog, the unchanged journey returned `-1`. API 31
returned `-1` on its first split-input note journey. One emulator ran at a
time and both were stopped. No model call, physical S23 run, release input
clearance, or reproduction of the user's original model-generated source is
claimed.

The host worker unit suite passed 36 tests with zero failures/errors. Worker
lint passed. APK asset inspection found no experimental inputs in
`worker-debug.apk`; a JAR-only probe rebuild had only the JAR; rebuilding with
both properties restored both assets. Current local APK hashes are:

| APK | SHA-256 |
| --- | --- |
| `worker/build/outputs/apk/debug/worker-debug.apk` | `910caf04e967fd351aef9a7fb484bc338e973d7bc8c29671613b76cff98ce378` |
| `worker/build/outputs/apk/platformProbe/worker-platformProbe.apk` | `9d272aed11a90e564fa1f631a5edc40029ecc8c6138861b373dd4ccae875c1ed` |
| `worker/build/outputs/apk/androidTest/platformProbe/worker-platformProbe-androidTest.apk` | `1851dcc263ece199a972b7d734fcc9882dc90adb8a5787beb07c4c926f923334` |

The unchanged controller/test APKs are the hashes listed in the earlier
native-note installation section. Reproduction commands are:

```bash
source scripts/env.sh
./gradlew :worker:testDebugUnitTest :worker:assemblePlatformProbe \
  :worker:assemblePlatformProbeAndroidTest -PplatformProbeTests=true \
  -PexperimentalPlatformJar=toolchains/android-sdk/platforms/android-36/android.jar \
  -PexperimentalPlatformResources=toolchains/platform-api36-source-probe/resource-r2-table-fixed.apk \
  --offline --no-daemon --max-workers=1 --quiet
# Install the worker/controller and corresponding test APKs on one emulator.
adb -s emulator-5556 shell am instrument -w -r -e mode api36ResourceOnApi31 \
  dev.smallwright.worker.platformprobe.test/dev.smallwright.worker.ConfinementInstrumentation
adb -s emulator-5556 shell am instrument -w -r -e mode experimentalPlatform \
  dev.smallwright.worker.platformprobe.test/dev.smallwright.worker.ConfinementInstrumentation
adb -s emulator-5556 shell am instrument -w -r -e experimentalNoteOnly true \
  dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
```

Use `api36ResourceOnApi31` only on API 31. The normal debug worker remains a
narrow authored-stub harness. A complete permitted Java API input, canonical
resource/asset verification, component notices and physical validation are
still required before this becomes the product compiler.


### Deterministic table-only packaging — 2026-09-28

The current resource pin uses only the manifest and table. AAPT2 linking does
not need to bundle the framework image/audio/HTML payload files into the
compiler input. `scripts/package_framework_table.py` preserves the two input
entries byte-for-byte, sets fixed stored-ZIP metadata and invokes the provided
host `zipalign`. It rejects ambiguous/incomplete inputs and bounds the sum of
retained entry bytes at 24 MiB; it refuses an existing output path.

```bash
python3 -m unittest discover -s scripts -p test_package_framework_table.py
python3 scripts/package_framework_table.py \
  --input toolchains/platform-api36-source-probe/resource-r2-with-remote-export.apk \
  --output toolchains/platform-api36-source-probe/resource-r2-table-fixed.apk \
  --zipalign toolchains/android-sdk/build-tools/36.0.0/zipalign
```

The two test-first cases passed. The local manual output existed before the
CLI was implemented, so the actual script replay used the new output name
`resource-r2-table-script.apk`; the script refused to overwrite the original.
The script output and a second fixed-metadata manual replay matched the
original aligned output exactly:

- ZIP: 19,456,443 bytes; SHA-256
  `82eb76d8be6bd66a353581b2409bffeaca10d922c10d151f314dfb6de1a25bf6`.
- `AndroidManifest.xml`: 382,684 bytes; SHA-256
  `0248f6205ca1cf1411c33959863d7f4d659670d3a9dbbf91131a2de3f7aa87d2`.
- `resources.arsc`: 19,073,516 bytes; SHA-256
  `13abbf4c533297fc0d4fd9641e8db080e60958182b43c18c325386f335b648c1`.

This is 46.5% smaller than the full 36,390,725-byte source resource APK.
The public resource comparison remains 3,027 names/first-listed IDs and zero
differences. A host consumer linked to identical bytes using either input
(SHA-256 `d2b5078e61e1ca13e73e9521832f595ca5d3c5d96b126f6cabfb5abe105f5480`).
The table still differs from the official SDK table, as described above.
The bounded notice/provenance review is in
[`../research-framework-resource-table-notices.md`](../research-framework-resource-table-notices.md).
It records actual metadata/header evidence and remaining audit limits; it
makes no blanket license clearance claim.

The current opt-in build command above now selects this table-only input;
the historical full-resource candidate requires commit `963ad53`. Current
worker unit tests and probe/test assembly passed. On the API 31 x86_64
emulator, all three instrumentation commands above returned raw code `-1`:
newer resource linking, real worker `PdfDocument`/note snapshot compilation,
and controller build/sign/install/native editing/restart persistence.
The unchanged controller/test APKs from the preceding note journey were used.
The emulator was stopped after the run. The table-only candidate was not run
on API 36 or a physical phone in this checkpoint; the full-resource API 36
pass remains separate evidence.

Normal debug assembly and worker lint passed:

```bash
source scripts/env.sh
./gradlew :worker:assembleDebug :worker:lintDebug \
  --offline --no-daemon --max-workers=1 --quiet
```

Normal debug ZIP inspection again found no experimental assets. Current APK
paths are the same as the preceding table; their SHA-256 values are now
`2e699837c99020ec92386e0c45b0c4eff59f2d4e76827892fecc8e0478343953`
(normal worker),
`79f3cf6fd7fa51f91df2d72d5303aed0b94f7e793ada55fbc05b6ee63d063198`
(table-only probe worker), and
`1851dcc263ece199a972b7d734fcc9882dc90adb8a5787beb07c4c926f923334`
(probe instrumentation APK). This remains opt-in development evidence;
Java compilation still uses the exact official SDK reference for testing.


## Public source graph and graphics batch — 2026-09-28

Reviewed the parallel source audit against the actual pinned build files.
`api/StubLibraries.bp` defines `android_stubs_current` as the union of
`all-modules-public-stubs`, `android-non-updatable.stubs` and
`private-stub-annotations-jar`. The source route is raw sources → Metalava
Java stubs → javac. `api/Android.bp` defines
`frameworks-base-api-current.srcjar` as a merge of
`all-modules-public-stubs-source-exportable` and
`api-stubs-docs-non-updatable{.exportable}`. These are real upstream targets,
but the local partial checkout cannot run them as a complete Soong tree.
The 33 unconditional public bootclasspath modules and six release-dependent
selections need their corresponding manifest repositories and release flags.
System-server API signatures are a separate surface; they should not be
mistaken for public SDK classes.

The non-updatable raw source aggregate also needs AIDL, framework R/Manifest,
proto/statslog/system-feature generation, generated flags, annotation inputs
and module resolution libraries. A suffix inventory found 2,037 matching
public top-level source files among the reference's 4,115 top-level classes;
excluding five Ravenwood files leaves 2,032. This is a source-location probe,
not a module-selection proof. The remaining types require other repositories,
generated sources or a different mapping; absence in this checkout does not
mean absence from AOSP.

### Concrete graphics extraction

Selected 118 reference top-level classes with actual raw Java sources in
`frameworks/base/graphics/java`, plus their two hidden canvas superclasses.
Used pinned Metalava and the SDK solely as a host resolution/reference input.
The flag generator read 170 non-test `.aconfig` files from the existing base
checkout, grouped into 126 packages. Two more declaration files came from
`frameworks/av` r4 commit `22fd1c5d895e45a49df5582a4b345d8e3b75e46b`:
`camera/camera_platform.aconfig` and `media/aconfig/codec_fwk.aconfig`.
The ignored sparse AV checkout preserves its root/camera NOTICE and module
license files. This adds source provenance, not a component-wide clearance
claim. Its manifest project uses the Android 16 r4 release tag.

All 128 groups generated successfully, producing 640 Java support files.
A first BP4A readonly-enabled projection used inherited AP3A/AP4A/BP1A/BP2A/
BP3A/BP4A values and yielded 1,468 enabled/read-only flags. Its 118 stubs
compiled into 182 classes, with no missing or extra class names for this
subset. Declaration comparison matched 175; seven differed. `Paint` had two
extra font-variation APIs, `GradientDrawable` had an extra ARC constant, and
five drawable classes differed in migration nullability metadata.

The reference corresponds to the earlier API release surface. Regenerating
using the pinned r2 release config's AP3A/AP4A/BP1A/BP2A value chain yielded
879 enabled/read-only flags. Passing upstream nullability migration against
API 35 history resolved the remaining metadata differences. The history file
is a **test-only** input from pinned `prebuilts/sdk` r4
`35/public/api/android.txt`, SHA-256
`84641e27313f90a889864ef42c7494ef04223c33639d0f117e01bfba67771b8d`.
It retains that repository's unresolved SDK agreement status. This remains a
host source-projection experiment, not a self-contained permitted release
JAR or an independent Java bootclasspath.

Metalava initially returned 255 for seven documentation/deprecation mismatch
errors already listed in the pinned upstream `core/api/lint-baseline.txt`.
A rerun using that existing baseline returned 0 and produced the same 118
Java files byte-for-byte. No baseline was edited and no new suppression was
added. Hidden-superclass warnings for Canvas/RecordingCanvas remained visible.
JDK 21 javac returned 0; declaration ABI comparison of the resulting subset
matched **all 182 classes**, with zero missing, extra or different classes.
The comparison includes superclass/interface declarations, generic signatures,
visible members/constants and the parsed annotation metadata. This does not
cover the other 6,045 reference classes or all source/resource licenses.

The final extraction command was:

```bash
JAVA_HOME="$PWD/toolchains/jdk-21.0.12.1+1" JAVA_OPTS=-Xmx2048m \
  toolchains/platform-api36-source-probe/out/metalava/metalava/build/install/metalava/bin/metalava \
  --quiet main --include-annotations --hide-annotation android.annotation.Hide \
  --config-file toolchains/platform-api36-source-probe/framework-flags-bp2a-batch/exportable-flags-config.xml \
  --migrate-nullness toolchains/platform-api36-source-probe/android-35-public-api.txt \
  --baseline toolchains/aapt2-source-clean-20260927/vendor/base/core/api/lint-baseline.txt \
  --classpath toolchains/platform-api36-source-probe/public-annotation-classes:toolchains/android-sdk/platforms/android-36/android.jar \
  --stubs toolchains/platform-api36-source-probe/graphics-bp2a-baselined-stubs \
  @toolchains/platform-api36-source-probe/graphics-bp2a-sources.list
```

The local source list records the 118 raw files, two superclasses and 640
flag support files. Flag groups, declaration inputs and the 3,765 ordered
release-value inputs are in the ignored
`framework-flags-bp2a-batch/generation-report.json`. All source preparation
and compilation stayed on this laptop; no phone result or generated-app
build is inferred from this host toolchain probe.

### Reproducible API flag policy helper

Added `scripts/aconfig_api_config.py` test-first. It consumes aconfig's
`{package}|{name}|{permission}|{state}` dump format, rejects malformed or
duplicate flags and emits deterministic Metalava XML for enabled/read-only
APIs. This implements Soong's `Exportable` filter when `ExportRuntimeApis`
is false: the helper deliberately filters a raw cache dump before converting
it. Upstream Soong filters/deduplicates protobuf rows first and then runs its
converter, which can also preserve mutable flags. This helper rejects
duplicate raw rows and cannot reproduce `ExportRuntimeApis=true`.
Callers must establish their
release and complete cache inputs. An empty `<api-flags/>` is meaningful:
upstream Metalava then reverts flagged APIs absent from the config.

Both regression cases passed after the initial missing-module failure.
A real dump of all 128 BP2A caches supplied 2,206 rows; the script matched
all 879 policies in the manually produced config. Its sorted output SHA-256
is `932c53ea485bb3d0977e5f348cd141c8683bd27771c7b2e966b2a21040d6a90d`.
The earlier manual XML has a different ordering/hash; the policy sets match.

```bash
python3 -m unittest discover -s scripts -p test_aconfig_api_config.py
# Dump the already prepared caches with all --cache arguments; check that
# upstream aconfig succeeded before feeding its output to this helper.
python3 scripts/aconfig_api_config.py \
  --output toolchains/platform-api36-source-probe/framework-flags-bp2a-batch/script-exportable-flags-config.xml \
  < toolchains/platform-api36-source-probe/framework-flags-bp2a-batch/flag-rows.txt
```

The output must be absent before this command. Neither these generated
sources nor the SDK history/reference inputs were put into a worker APK.
Next: resolve the broader source graph and compare the complete public
surface before choosing a product input.

### Wider base extraction: compile success, parity still fails

The 2,032 selected public base files span 17 Java source roots. Initial
Metalava extraction returned 255 with 334 error lines; generated stubs failed
javac with 30 errors. Adding the actual 108 framework annotation sources from
`platform/frameworks/libs/modules-utils` r4 commit
`e945bbce0ccb83e1111b6031a3bba89e6b5cac6f` reduced extraction errors to 15.
Compiling those annotations plus the actual camera `SyntheticKey` annotation
as host support reduced javac failures to four. This support is separate
from the public annotation helpers and is never packaged in the worker.

The next input set added actual internal menu/helper sources and two AV
flag groups: `media/audio/aconfig/audio_framework.aconfig` and
`media/audio/aconfig/midi_flags.aconfig`. There are now 650 generated flag
support Java files. A combined copy of the unchanged upstream core/location
baseline records leaves one unbaselined error:
`LocationManager.removeProximityAlert` mentions a permission in documentation
without a source `@RequiresPermission`. No new baseline record or blanket
suppression was introduced. This diagnostic requires tracing the module's
annotation inputs; source projection cannot declare an extraction pass.

An initial `--stub-packages android:dalvik:...` invocation returned 0 but
emitted **zero files**: these patterns match exact package names. The output
count check caught it and javac failed with no inputs; this is not a successful
extraction. Correct patterns such as `android.*` include subpackages.
`--hide-package` is unsupported by this pinned Metalava and its attempted
invocation returned 1. Neither failed approach is used as positive evidence.

The independently reviewed Soong filter is a prefix filter of Java input
paths, retaining non-Java generated inputs; it is not equivalent to
Metalava's output package filter. The wider source list also omitted package
documentation. Added 86 real `package-info.java`/`package.html` files under
the selected roots, preferring package-info where both exist. These include
upstream `@hide` metadata for internal packages. Used Soong's normal
`--exclude-documentation-from-stubs` setting, which avoids implementation-only
imports retained for documentation. The final list has 2,883 inputs.

```bash
JAVA_HOME="$PWD/toolchains/jdk-21.0.12.1+1" JAVA_OPTS=-Xmx3072m \
  toolchains/platform-api36-source-probe/out/metalava/metalava/build/install/metalava/bin/metalava \
  --quiet main --include-annotations --hide-annotation android.annotation.Hide \
  --exclude-documentation-from-stubs \
  --stub-packages 'android.*:dalvik.*:java.*:javax.*:junit.*:org.apache.http.*:org.json.*:org.w3c.dom.*:org.xml.sax.*:org.xmlpull.*' \
  --config-file toolchains/platform-api36-source-probe/framework-flags-bp2a-extended/exportable-flags-config.xml \
  --migrate-nullness toolchains/platform-api36-source-probe/android-35-public-api.txt \
  --baseline toolchains/platform-api36-source-probe/core-location-upstream-baseline.txt \
  --source-path "$(cat toolchains/platform-api36-source-probe/base-resolved-roots.txt)" \
  --classpath toolchains/platform-api36-source-probe/public-annotation-classes:toolchains/android-sdk/platforms/android-36/android.jar \
  --stubs toolchains/platform-api36-source-probe/base-package-docs-stubs \
  @toolchains/platform-api36-source-probe/base-package-docs-sources.list
```

Metalava returned 255 for that one permission diagnostic, with 2,032 emitted
Java files. JDK 21 javac returned **0**, producing 3,437 classes:

```bash
toolchains/jdk-21.0.12.1+1/bin/javac -J-Xmx2048m -source 8 -target 8 \
  -classpath toolchains/platform-api36-source-probe/public-annotation-classes:toolchains/platform-api36-source-probe/framework-annotation-classes:toolchains/android-sdk/platforms/android-36/android.jar \
  -d toolchains/platform-api36-source-probe/base-package-docs-classes \
  @toolchains/platform-api36-source-probe/base-package-docs-javac-sources.list
```

The corresponding reference top-level families contain 3,439 classes:
**3,348 match, 89 differ and two nested classes are missing**.
`ActionMenuView`, `ListPopupWindow`, `ContactsContract`, `LocationManager` and
`CameraCharacteristics` now match reference declaration ABIs. Across the
remaining differences, there are 442 changed fields (including unresolved
constant values), 66 missing fields, two extra fields, 45 missing methods,
six extra methods, 19 changed methods and one differing class attribute.
The missing nested classes are `MediaSessionManager.OnSession2TokensChangedListener`
and `InputDevice.ViewBehavior`; their actual raw declarations carry flags.
One extra constant, `VERSION_CODES_FULL.BAKLAVA_1`, comes from the later r4
raw source. Matching raw release sources and private/generated constant
resolution inputs are the next checks. This experiment remains host-only,
uses test-only SDK resolution/history and is not a complete, independently
licensed bootclasspath. No generated API output entered an APK.

Ignored local evidence: `base-package-docs-{sources.list,metalava.log,javac.log,
classes.jar,reference.jar,abi-report.json,abi-details.json}` under
`toolchains/platform-api36-source-probe/`. The full SDK's 6,227 classes remain
the eventual coverage target; the reference subset was selected by emitted
top-level families and must not be reported as full SDK coverage.
