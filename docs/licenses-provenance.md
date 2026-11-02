# Licenses And Provenance

The project's own code is Apache-2.0. The repository and the two APKs also
contain third-party material under other licenses. The root `NOTICE` lists
it. `toolchain/licenses/INDEX.md` gives each bundled tool and library with its
version, SHA-256, license and where the text is. Both APKs package
`toolchain/licenses/` as assets.

## What the repository holds

- `controller`, `worker`, `protocol`, `fixtures`, `scripts`, `test-support`:
  source written for this project. No Sketchware code.
- `worker/src/main/assets/core-lambda-stubs`: three unchanged AOSP libcore
  sources, GPLv2 with the Classpath exception. Text in
  `toolchain/licenses/core-lambda-stubs/`.
- `controller/src/main/res/font/cascadia_mono.ttf`: Cascadia Mono, SIL OFL 1.1.
  Text in `toolchain/licenses/CascadiaMono-OFL.txt`.
- `controller/src/main/assets/android-sdk-terms.txt`: Google's Android SDK
  License Agreement, shown at setup before the platform download. It is
  Google's text and is not licensed by this project.
- `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`: Gradle wrapper,
  Apache-2.0.
- `toolchain/aapt2-source-compat/*.patch`: changes to Apache-2.0 AOSP and
  Termux `android-build-tools` sources, Apache-2.0.
- `toolchain/licenses/` and `toolchain/api36-component-notices/`: verbatim
  third-party license texts.

ECJ, R8, the AAPT2 binary, the Android SDK, NDK, JDK and Gradle caches are not
tracked. They are downloaded or built under ignored directories, and the build
checks them against `toolchain/manifest.json`.

## What the APKs hold

The worker contains ECJ 4.12 (EPL-2.0), R8/D8 8.13.23 (BSD-3-Clause plus the
libraries R8 bundles) and the source-built ARM64 AAPT2 (Apache-2.0 plus its
linked components). The controller contains AndroidX, Compose, Kotlin,
kotlinx, Gson, apksig and Bouncy Castle. Neither APK contains Google's
`android.jar`: the controller downloads Google's platform files during setup
(docs/adr/0002-android-platform-files-downloaded-from-google.md) and hands them
unchanged to the worker. Release builds are `arm64-v8a` only.

## ECJ source

ECJ is shipped unmodified. Its corresponding source is `ecjsrc-4.12.jar` from
the same Eclipse release. `toolchain/licenses/ecj/SOURCE.txt` gives the URL and
hashes, and `toolchain/licenses/ecj/EPL-2.0.txt` is the full license.

## What R8 8.13.23 bundles

R8's `LICENSE` lists every library R8's build can resolve, including Trove4J
(LGPL-2.1) and JAXB (CDDL/GPL with the Classpath exception, or EDL/BSD). Checked
on 2026-11-02: **neither Trove4J nor JAXB code is in the R8 jar we ship.**

Evidence:

- The pinned jar, `toolchain/input/r8-8.13.23.jar` (SHA-256
  `e3cdcb003d9beca956209ad6b9e9df31f26b732bfaed9c7c8674e903ca9f3b81`), is
  byte-identical to the R8 team's own archive
  `https://storage.googleapis.com/r8-releases/raw/8.13.23/r8lib.jar`. Its
  `r8-version.properties` names revision
  `5fa61e42c2aca157e391442fd89bbec558c83b97`.
- The same archive publishes the jar's obfuscation map,
  `https://storage.googleapis.com/r8-releases/raw/8.13.23/r8lib.jar.map`
  (SHA-256 `f84cc57ce601a6936e38fa7a10f02ffb0b09983021a8c743a9b37695708c1e20`).
  Every one of the jar's 7,162 classes has an entry in it, so the map gives
  each class's name before obfuscation.
- At that revision, `d8_r8/main/build.gradle.kts` builds `deps.jar` from the
  compile classpaths of the main and resource-shrinker modules, then relocates
  only these packages under `com.android.tools.r8`: `com.android`,
  `com.google.common`, `com.google.gson`, `com.google.thirdparty`,
  `it.unimi.dsi.fastutil`, `kotlin`, `kotlinx`, `org.jetbrains`,
  `org.intellij`, `org.checkerframework`, `com.google.j2objc`,
  `com.google.protobuf`, `android.aapt` and `org.objectweb.asm`. Trove4J
  (`gnu.trove`) and JAXB (`javax.xml.bind`, `com.sun.xml.bind`,
  `org.glassfish.jaxb`) are not in that list, so had they been in `deps.jar`
  they would keep their own package names.
- Every original class name in the map starts with `com.android.tools.r8.`.
  None contains `trove`, `jaxb` or `xml.bind`. The third-party code present is
  fastutil (458 classes), Guava (369), Kotlin stdlib and kotlin-metadata
  (about 500), protobuf-java (269), the AAPT2 resource protos (184), Gson (173),
  ASM (100) and a few classes from Android's `sdk-common`/`common`
  (`com.android.ide.common`, `com.android.resources`, `com.android.utils`).
- R8's SBOM for this release,
  `https://storage.googleapis.com/r8-releases/raw/8.13.23/r8.spdx.json`
  (SHA-256 `6e4006940fa2abc7ccc231699d4cabbd453bbb9baa16aae27b7b0aadcefb3c0a`),
  lists trove4j 1.0.20200330 and jaxb-runtime 2.3.2 as dependencies of
  `sdk-common`. The build's `consolidatedLicense` task requires every
  `runtimeClasspath` artifact to be named in `LIBRARY-LICENSE`, which is why
  they appear in the `LICENSE`. They reach the resolved graph but not the jar.

Sources: [main build](https://r8.googlesource.com/r8/+/5fa61e42c2aca157e391442fd89bbec558c83b97/d8_r8/main/build.gradle.kts),
[resource-shrinker build](https://r8.googlesource.com/r8/+/5fa61e42c2aca157e391442fd89bbec558c83b97/d8_r8/resourceshrinker/build.gradle.kts),
[archive script](https://r8.googlesource.com/r8/+/5fa61e42c2aca157e391442fd89bbec558c83b97/tools/archive.py)
(uploads `r8lib.jar`, its map and `r8.spdx.json`).

All the bundled libraries are BSD-3-Clause (R8, ASM, protobuf) or Apache-2.0
(the rest). Their texts are in `toolchain/licenses/r8/LICENSE`, the exact file
inside the jar.

## AAPT2

The release worker ships the source-built ARM64 AAPT2, SHA-256
`ed917a383f890b2cad3eb3be00657f20d4fc6f73ca5bbbabf062d357633374e7`. Its link
map names 63 archives. Each linked component and its notice is listed in
`toolchain/licenses/INDEX.md`. How it was built is in
[the source-build record](validation/aapt2-source-build.md). The earlier
lzhiyong 35.0.2 binary is no longer shipped in release builds.
