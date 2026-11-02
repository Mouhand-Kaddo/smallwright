# Audited notices

This directory holds the license texts for the third-party code in the two
APKs. Build configuration packages it into both APKs under `assets/`.
`Apache-2.0.txt` is the Apache License 2.0 text that the Apache-2.0 rows
below point to. It was fetched from
`https://www.apache.org/licenses/LICENSE-2.0.txt` on 2026-11-02, SHA-256
`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`.
Evidence for the R8 contents and the provenance overview are in
[licenses and provenance](../../docs/licenses-provenance.md).

## Worker

| Input | SHA-256 | Verbatim material | Source and revision | Status |
| --- | --- | --- | --- | --- |
| Eclipse ECJ 4.12 | `69dad18a1fcacd342a7d44c5abf74f50e7529975553a24c64bce0b29b86af497` | `ecj/about.html`; `ecj/EPL-2.0.txt` `0becf16567beb77fa252b7664631dd177c8f9a1889e48995b45379c7130e5303` (from `https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.txt`, 2026-11-02); `ecj/SOURCE.txt` | `https://archive.eclipse.org/eclipse/downloads/drops4/R-4.12-201906051800/ecj-4.12.jar`; source `ecjsrc-4.12.jar` from the same drop, SHA-256 `d42c8ae16f590cbade9700525263b31cb723f278ee22dbf72d1c5e3c4d48db71` | EPL-2.0. Shipped unmodified. Both JARs match Eclipse's published SHA-512 list for the release. `SOURCE.txt` says where the source is. |
| R8 8.13.23 | `e3cdcb003d9beca956209ad6b9e9df31f26b732bfaed9c7c8674e903ca9f3b81` | `r8/LICENSE`, SHA-256 `c931eaa66cdf229ccab4ab97972c44620bcf4b7341dd9fa3eeddef3b9fb26c48` | `https://dl.google.com/android/maven2/com/android/tools/r8/8.13.23/r8-8.13.23.jar`, byte-identical to `https://storage.googleapis.com/r8-releases/raw/8.13.23/r8lib.jar`; revision `5fa61e42c2aca157e391442fd89bbec558c83b97` | BSD-3-Clause, with bundled ASM (BSD-3-Clause), protobuf-java (BSD-3-Clause), fastutil, Guava, Gson, Kotlin stdlib and metadata, AAPT2 protos and parts of Android `sdk-common`/`common` (Apache-2.0). The `LICENSE` also names Trove4J and JAXB; the published class map shows neither is in the jar. Evidence in `docs/licenses-provenance.md`. |
| Source-built ARM64 AAPT2 | `ed917a383f890b2cad3eb3be00657f20d4fc6f73ca5bbbabf062d357633374e7` | See "AAPT2 linked components" below | Termux `android-build-tools` tag `16.0.0.4`, commit `c4edf8539a34a8600538e6642c1ecb170452a79e`, its 11 AOSP submodules at their Gitlinks, AOSP `android-16.0.0_r4` dependencies, NDK `28.2.13676358`; [source-build record](../../docs/validation/aapt2-source-build.md) | The binary shipped in release worker builds (`arm64-v8a` only). Built from the clean-source checkout `toolchains/aapt2-source-clean-20260927` against the dependency archives in `toolchains/aapt2-replay-20260927-oneshot/sysroot`; link map SHA-256 `7ecfee60dfc0d30e330b73bd4594fc52921424cfbbb3e7a962defdb0a0961ccc`. Apache-2.0 plus the linked components' licenses. |
| AOSP libcore `core-lambda-stubs-for-system-modules` sources (filegroup `openjdk_lambda_stub_files`) | `LambdaMetafactory.java` `d94fc80083704fbc9ed1d004a677ec596a91d472559d20ef2556a2a6927d40fe` (blob `e1ce46f3330386eac06d5d45e02bc361e927d17d`); `SerializedLambda.java` `b961edb25ed34896d4dfe6ee9566504936db3802fb875e512dd4086e86219afd` (blob `55ec67091cba75754f2b0d82382e5e060bae337c`); `StringConcatFactory.java` `0df4096a88c55e12cdbb938bab218663e16628c984ac7a42e8fe519b9295f6aa` (blob `950f62900f6da01fcfcf3479f7f188ffa58f78b3`) | `core-lambda-stubs/LICENSE` `46d411827d56eda34df30f901bfa4ea020119b2e38b6b7efb6a32e91461457a2` (blob `368340269de134f58a48df1286449d7d251f1bd7`) and `core-lambda-stubs/NOTICE` `4e1144ff2b1369e7b9e8a1a3fb94ea3deee99d5b327cd94b423d148dfa34d25f` (blob `80c1d466cbafdb3885e52f1b326fd85ce74915f2`): the `license_text` of license module `libcore_ojluni_src_main_license` (`SPDX-license-identifier-GPL-2.0-with-classpath-exception`, `SPDX-license-identifier-Apache-2.0`), from `ojluni/src/main/` | `https://android.googlesource.com/platform/libcore`, commit `de876a01b29230b877c9f408348c38a90ee724a5`, `ojluni/src/lambda/java/java/lang/invoke/` (tree `2515e113261e600a39fb1cece2d2f2530a0de510`); module and filegroup in `JavaLibrary.bp` (blob `a7e23325c53b77137fca3b65667caf9a8cec0bad`) and `openjdk_java_files.bp` (blob `b8057dee4a8df7a07b24b55d266b634f4bf97b26`) | Collected byte-for-byte, each source with its Oracle GPLv2 + Classpath exception header. The three sources ship unchanged in the worker only, as assets under `core-lambda-stubs/java/lang/invoke/`, and the worker refuses them unless each SHA-256 matches. Compile-only ECJ input for lambdas, method references and serializable lambdas; D8 desugars every use and no `java.lang.invoke` class enters a generated app. |

## AAPT2 linked components

These are the 63 archives and two NDK start/end objects in the link map of
the shipped binary, grouped by source. AOSP notices in `aapt2-aosp/` that are
listed here are byte-identical to the same file in the pinned checkout.

| Component | Linked as | Notice |
| --- | --- | --- |
| Termux `android-build-tools` wrapper (CMake files, patches) | build recipe for all AOSP archives | `aapt2-source-built/wrapper-LICENSE.txt` (Apache-2.0) |
| `frameworks/base` `45034f0663f960d9ee5fb0a101a4732b71f6e2f4`: `tools/aapt2`, generated AAPT2 protos | `liblibaapt2.a` and 10 direct `aapt2.dir` objects | `aapt2-aosp/frameworks-base-NOTICE` `cf4dd7cea5b6bcd2622ddd3529b3c74d16ba2b4db444575bc6f5c33f8a960530` |
| `frameworks/base` `libs/androidfw` | `liblibandroidfw.a` | its `NOTICE` is byte-identical to `aapt2-aosp/core-libutils-NOTICE` `38751245389e1e23f73e6f5384b5cbe7fa972cc4410c5adc9c04b082a0b9561a` |
| `system/libbase` `bdf0f4db82fae78492c92a55d473aacc4e963e79` | `liblibbase.a` | `aapt2-aosp/libbase-NOTICE` `e0afbb47be661ce2076d21a1d4dc02caa58b7c215ab7ff9d151b6724be341f77` |
| `system/core` `e9d1fa3705d7fbd0ac1c942bc4c276161bfbfed1`: `libcutils`, `libutils` | `liblibcutils.a`, `liblibutils.a` | `aapt2-aosp/core-libcutils-NOTICE`, `aapt2-aosp/core-libutils-NOTICE` (both `38751245…`) |
| `system/incremental_delivery` `6d8b9b339e7cc9abfeba475fc4c89984c2824cd9`: `incfs` | `liblibincfs.a` | `aapt2-source-built/incremental-delivery-incfs-NOTICE` `55ffa4d261bd3cc039fefee3a7463222cd2409c92b608c854fd4b7b6844b8030` |
| `system/logging` `58dac317ea08b8f275ebd01ce864acab2de30b93`: `liblog` | `libliblog.a` | `aapt2-source-built/logging-liblog-NOTICE` `f09747e3bc87e0ec3bdea983b37e18eeda223e01e678f76418a8a731ed8f590f` |
| `system/libziparchive` `94db64a81930e4cc8bacca4010bf935f664c97e1` | `liblibziparchive.a` | No NOTICE in the checkout; its `Android.bp` declares `Android-Apache-2.0`. `Apache-2.0.txt` |
| `frameworks/native` `fcbde2bcff56ca1d7bec9cabf8bdca5e288bf103` | headers only (`binder/TextOutput.h`) | `aapt2-source-built/frameworks-native-NOTICE` `5f28c11f8b1b3f97b9d42c74b85371ebf4127f241b799279bcecd69f7ee542dc` |
| Abseil `2bf8472a92b84fe4617eeb50053e7a942edcb1b3` | 46 `libabsl_*.a` | `aapt2-aosp/abseil-LICENSE` `c79a7fea0e3cac04cd43f20e7b648e5a0ff8fa5344e644b0ee09ca1162b62747` |
| protobuf `31fc4250f78631cf8f4f1cc48b200acf11e9c884` (4.25.8) | `libprotobuf.a` | `aapt2-aosp/protobuf-LICENSE` `6e5e117324afd944dcf67f36cf329843bc1a92229a8cd9bb573d7a83130fea7d` |
| utf8_range (in protobuf) | `libutf8_validity.a` | `aapt2-source-built/utf8-range-LICENSE` |
| Expat `79dde19a8fe1dfb580d39de37854221a6de5f09b` | `libexpat.a` | `aapt2-source-built/expat-COPYING` |
| fmt `d50695f04854e474766da7d713bf8242f4ceb4f8` | `libfmt.a` | `aapt2-source-built/fmt-LICENSE` |
| libpng `812ea249a49e26a13fa080821dc094f33c0f6608` | `libpng16.a` | `aapt2-source-built/libpng-LICENSE` |
| NDK r28c LLVM runtime: `libc++_static.a`, `libc++abi.a`, `libunwind.a`, `libclang_rt.builtins-aarch64-android.a` | static archives | `aapt2-source-built/ndk-llvm-prebuilt-NOTICE`, `llvm-libunwind-LICENSE.TXT`, `llvm-{compiler-rt,libcxx,libcxxabi}-CREDITS.TXT`; source map in [NDK runtime source map](../aapt2-source-compat/ndk-runtime-source-map.json) |
| NDK r28c sysroot (bionic) | `crtbegin_dynamic.o`, `crtend_android.o` | `aapt2-source-built/ndk-sysroot-NOTICE` |

Not linked: GoogleTest and the static `libz.a` are build inputs only. zlib
comes from the phone's own `libz.so`, one of the five Android system libraries
the binary loads (`libz`, `liblog`, `libm`, `libdl`, `libc`).
`aapt2-source-built/zlib-LICENSE` stays because libpng was compiled against
zlib's headers.

## Controller

The resolved runtime graph below is from the AGP lint model
`controller/build/intermediates/lint_report_lint_model/debug/generateDebugLintReportModel/debug-artifact-dependencies.xml`
(2026-11-02). The release variant declares the same dependencies. Licenses are
from each module's POM.

| Input | Version | License | Text |
| --- | --- | --- | --- |
| AndroidX: `activity` (activity, activity-ktx, activity-compose) 1.12.0; `annotation` annotation-jvm 1.9.1, annotation-experimental 1.4.1; `arch.core` core-common, core-runtime 2.2.0; `autofill` 1.0.0; `collection` collection-jvm, collection-ktx 1.5.0; `concurrent-futures` 1.1.0; `core` core, core-ktx 1.16.0, core-viewtree 1.0.0; `customview-poolingcontainer` 1.0.0; `documentfile` 1.0.0; `dynamicanimation` 1.0.0; `emoji2` 1.4.0; `graphics-path` 1.0.1; `interpolator` 1.0.0; `legacy-support-core-utils` 1.0.0; `lifecycle` (common-jvm, livedata, livedata-core, livedata-core-ktx, process, runtime-android, runtime-compose-android, runtime-ktx-android, viewmodel, viewmodel-android, viewmodel-ktx, viewmodel-savedstate-android) 2.9.4; `loader` 1.0.0; `localbroadcastmanager` 1.0.0; `navigationevent` (android, compose-android) 1.0.0; `print` 1.0.0; `profileinstaller` 1.4.0; `savedstate` (android, compose-android, ktx) 1.3.2; `startup-runtime` 1.1.1; `tracing` 1.2.0; `transition` 1.6.0; `versionedparcelable` 1.1.1; `window`, `window-core-android` 1.5.0 | as listed | Apache-2.0 | `Apache-2.0.txt` |
| Jetpack Compose: `animation`, `animation-core`, `foundation`, `foundation-layout`, `runtime`, `runtime-annotation`, `runtime-retain`, `runtime-saveable`, `ui`, `ui-geometry`, `ui-graphics`, `ui-text`, `ui-unit`, `ui-util` (all `-android`) | 1.11.4 | Apache-2.0 | `Apache-2.0.txt` |
| Kotlin standard library `org.jetbrains.kotlin:kotlin-stdlib` | 2.4.10 | Apache-2.0 | `Apache-2.0.txt` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm`, `kotlinx-coroutines-android` | 1.9.0 | Apache-2.0 | `Apache-2.0.txt` |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.7.3 | Apache-2.0 | `Apache-2.0.txt` |
| `org.jetbrains:annotations` | 23.0.0 | Apache-2.0 | `Apache-2.0.txt` |
| `org.jspecify:jspecify` | 1.0.0 | Apache-2.0 | `Apache-2.0.txt` |
| `com.google.guava:listenablefuture` (license declared by parent `guava-parent` 26.0-android) | 1.0 | Apache-2.0 | `Apache-2.0.txt` |
| Gson `com.google.code.gson:gson`, JAR `57928d6e5a6edeb2abd3770a8f95ba44dce45f3b23b7a9dc2b309c581552a78b` | 2.11.0 | Apache-2.0 | `gson/LICENSE` (identical to `Apache-2.0.txt`); `gson/PROVENANCE.md` |
| apksig `com.android.tools.build:apksig`, JAR `c070ed1394629d74641aa0906f60b2ffa1ee77e6366a1f93437f59717b1aeb89` (SHA-1 `f005788487574c7d6ba23dea63bf8cb3a4f164a6`, matches Google Maven's `.sha1`) | 8.13.0 | Apache-2.0 | `apksig/LICENSE` `39961513f07c54e796b0fec574e8d64c75e1f0f83736061f114b6f6be1d91e6b`, the `LICENSE` entry inside the JAR (AOSP copyright line plus the full Apache-2.0 text); `apksig/pom.xml` `b39b6c4169fc42bb7d3a74e045de8203509fa377693b33b7e754e1f07c759869` |
| Bouncy Castle `bcpkix`, `bcprov`, `bcutil` (`-jdk18on`), JARs `8d8b41a4b149bdae8d331f059a578870954d1e3ff29f8269a9eb3ee19a7e4ef7`, `2af190b300cbb0b35e248ccf5f4a06b6072030aeb3da7a98ec73abe5b4cb371f`, `1c268e15f785aafb1e4670d8d6e5f856f69475e96c162dadcd1ff9b350e9139f` | 1.86 | Bouncy Castle Licence (MIT) | `bouncycastle/LICENSE.md` `0e01f1549c9022f406392ac2947d32223b7c2e977d21ea2f8c182fdeb4dae5fd`, from the [r1rv86 tag](https://raw.githubusercontent.com/bcgit/bc-java/r1rv86/LICENSE.md); byte-identical to `META-INF/LICENSE.md` in all three JARs |
| Cascadia Mono variable font (Google Fonts copy, `CascadiaMono[wght].ttf`), SHA-256 `78864a1b4d03767a44731454a6f24c6b7d6e93fb5f62932860eb02e409cd50cb` | fetched 2026-10-17 from `https://raw.githubusercontent.com/google/fonts/main/ofl/cascadiamono/CascadiaMono%5Bwght%5D.ttf` | SIL OFL 1.1 | `CascadiaMono-OFL.txt`. Bundled as `res/font/cascadia_mono.ttf` for the controller UI only; never part of a generated app. |

In the 2026-11-02 controller debug APK, only four license files from these
libraries survive on their own (`META-INF/androidx/{annotation,collection,collection-ktx,lifecycle-common}/LICENSE.txt`)
plus one `META-INF/LICENSE.md`. The others are dropped by packaging, which is
why the texts are kept here.

## Historical, not shipped in release builds

| Input | SHA-256 | Verbatim material | Source and revision | Status |
| --- | --- | --- | --- | --- |
| lzhiyong AAPT2 wrapper 35.0.2 | `db737b3bbee99dd8a9e56108cc05d032597874d3f8ac557a69f3c5de5efaf57f` | `aapt2-wrapper/LICENSE.txt`, `aapt2-aosp/*` | wrapper revision `aa13480acd6c32f7e1c384a2db76b9e2ba8eb989`; known AOSP revisions are recorded in `docs/toolchain-candidates.md` | No longer shipped in release builds; replaced by the source-built binary above. Its release-to-source correspondence was never proven. The [35.0.2 tag tree](https://github.com/lzhiyong/android-sdk-tools/tree/35.0.2) has no checked-in AOSP `src/` tree. `aapt2-aosp/` stays because several of its files also cover the source-built binary (see above); the BoringSSL, Brotli, GoogleTest, LZ4, PCRE, tinyxml2 and zstd files there apply only to this old binary. Exact URLs and hashes for seven of them are in `aapt2-aosp/retrieved-2026-09-25.json`. |
| Android API 36 `android.jar` | `d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a` | `sdk36/package.xml` (SDK terms); the JAR itself contains `NOTICES/libcore-NOTICES.txt`, SHA-256 `8304b9f57ad50d93101628cdaccc82ac1333276f942f431cf15f953c61617e39` | Installed SDK platform 36 r2; exact [public AOSP fullsdk blob](https://android.googlesource.com/platform/prebuilts/fullsdk/platforms/+/1b45636761b0dbab9f80779add8fc85ab94b80fb/android-36/android.jar) | An earlier worker bundled this JAR. Neither APK does now: setup downloads Google's platform-36 files ([ADR 0002](../../docs/adr/0002-android-platform-files-downloaded-from-google.md)) and the worker compiles against that verified copy. Do not add it back. |
