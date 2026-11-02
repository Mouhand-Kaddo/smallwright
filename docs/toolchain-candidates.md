# Local Compiler Toolchain Candidates

## 2026-09-25 repo-local host tooling restoration

The development harness now uses the following ignored local tooling under `toolchains/`.
These host inputs build the harness; they do not compile generated apps for users. The worker's
packaged ECJ/D8/AAPT2 pipeline remains the generated-app compiler.

| Input | Version and source | Verified SHA-256 | License/role |
| --- | --- | --- | --- |
| Eclipse Temurin JDK | 21.0.12.1+1, official Adoptium API archive | `ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94` | OpenJDK/GPLv2 with Classpath Exception; host Gradle/Java tooling, not packaged in APKs. |
| Android command-line tools | 15859902, official Android developer download | `4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583` | Android SDK license accepted for the specifically installed packages; host SDK manager and build tooling. |
| Gradle distribution | 8.13 binary distribution, wrapper checksum pinned in `gradle/wrapper/gradle-wrapper.properties` | `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78` | Apache-2.0; host build only. |

Installed SDK package versions are API 36 platform r2, build-tools 35.0.0/36.0.0,
platform-tools 37.0.1, emulator 37.1.11, and API 36 default x86_64 system image r2.
`scripts/env.sh` sets the repo-local JDK, SDK and Gradle cache. No global Java or SDK path
was changed. The earlier worker packaged Android 36 `android.jar`; the current
worker uses authored source stubs and excludes that JAR. Its historical
redistribution question is recorded in `toolchain/licenses/INDEX.md`.

## 2026-09-13 bounded investigation

The worker packages ECJ 4.12, R8 8.13.23 D8 and Android-hosted AAPT2. It now
uses authored Java/Android compile signatures instead of bundling the SDK
`android.jar`. The no-JAR route built changed source on the ARM64 S23 Ultra
through the actual separate-UID worker and passed a generated-app runtime and
update check on a fresh API 36 emulator, as recorded in `docs/STATUS.md`.
AAPT2 redistribution remains unverified. A host build is not evidence for the
phone-local compiler requirement.

| Candidate | Evidence | Decision |
| --- | --- | --- |
| Android SDK Build Tools 36.0.0 AAPT2 | The installed `toolchains/android-sdk/build-tools/36.0.0/aapt2` identifies as an x86-64 Linux ELF. It cannot execute on the ARM64 S23 Ultra. | Rejected as a phone package input. It remains host-only verification tooling. |
| Android SDK Build Tools 36.0.0 D8 | The installed distribution includes `lib/d8.jar`, but it has only been run by the host JDK. ART compatibility, dependencies, and redistribution terms were not demonstrated. | Unverified. Do not embed or claim it as an Android compiler. |
| AndroidIDE | Its public repository is GPL-3.0-only. Its published build-tool documentation URL from the implementation plan returned 404 during this task. | Not used. Adopting its code or packaged tools would require a separate licensing and provenance review. |
| ECJ 4.12 plus R8 D8 plus Android-hosted AAPT2 | The pinned worker pipeline compiled and installed a generated app on an API 36 x86_64 emulator, then compiled changed source on the ARM64 S23 Ultra. | Selected for the compiler alpha. Complete redistribution evidence remains open. |

## AAPT2 spike result

`lzhiyong/android-sdk-tools` release `35.0.2` was cloned at wrapper revision
`aa13480acd6c32f7e1c384a2db76b9e2ba8eb989`. Its `LICENSE.txt` is Apache-2.0.
The release archive is not accepted as a complete redistribution input yet: its source-fetch
script selects `platform-tools-35.0.2` across AOSP repositories and the AAPT2 CMake target
links AOSP components plus BoringSSL, protobuf, Expat, PCRE2, libpng, zstd, lz4, Brotli,
Abseil, GoogleTest and tinyxml2. The wrapper release contains no consolidated notices.

Pinned AAPT2 dependencies consulted by that CMake target include framework/base
`e692a557537a6da5ef4fcb5986973c2854de8db1`, system/libbase
`8d00a4f2e93ce79877575157db1a56c8f8485c90`, system/core
`de0979b80d7ccda90c265be919691e038b0c7690`, BoringSSL
`76ee6046c38800b731a4e1162b6446a25915b79a`, protobuf
`e8339fb1ea41e258031ed7fb0006a3e53ccf79bf`, Expat
`b257389b1a8d2af937cfa09fb4bb0934d82f0b4e`, PCRE2
`15f2c7f85edb65b79a1388b37378634dd629af0d`, libpng
`fd431af9a5bb2a1b646aab75e529d4fd39171c7b`, zstd
`7c43aee915a242c7699bc29b8218cbede5405e89`, lz4
`4c0b9e31c84884d7a5fab4d95e116bc021fde23b`, Brotli
`8ce782376bb7ada23b8dfe9d69de1b7954c3114d`, and Abseil
`7be0c5f441cf614122a974a970086642735b7bff`.

The downloaded ARM64 archive SHA-256 is
`db1cea2c4454d5f9c5a802646b2d1cf560b4ee7badbe23e51ab8e1881bb50fc2`; its
`aapt2` SHA-256 is `db737b3bbee99dd8a9e56108cc05d032597874d3f8ac557a69f3c5de5efaf57f`.
It is a static Android API 30 AArch64 ELF with no dynamic interpreter or dependencies. The
matching x86_64 archive SHA-256 is
`30110518265f0f6e8fb18bcce9ef29a7f1caf507e8d6a37ae92d26b11e44bf07`.

On the API 36 x86_64 AOSP emulator, the signed controller called an isolated worker Binder
method. The worker executed the matching static AAPT2 from its extracted native-library
directory and returned `Android Asset Packaging Tool (aapt) 2.19-13847098`. The subsequent
ECJ 4.12 probe below also executes AAPT2 resource compilation and linking. It does not
establish signing, install/update behavior, or physical ARM64 execution.

The Android SDK packages used during the September 13 run contained only host
`platform-tools`, platform 36, and build tools 35/36. They contained no Android-hosted ARM64 AAPT2 package. No
downloaded generated APK, desktop compiler output, or WebView replacement is
accepted as a substitute.

## ECJ/D8 ART probe result

Maven ECJ `3.42.0` failed on ART because `javax.lang.model.SourceVersion` is absent. It is
rejected. The replacement is Eclipse 4.12 ECJ from its upstream release URL, SHA-256
`69dad18a1fcacd342a7d44c5abf74f50e7529975553a24c64bce0b29b86af497`, with its EPL-2.0
`about.html` and parser/message resources inspected. R8 remains
`com.android.tools:r8:8.13.23`, SHA-256
`e3cdcb003d9beca956209ad6b9e9df31f26b732bfaed9c7c8674e903ca9f3b81`. At this
earlier probe checkpoint, the worker also packaged Android SDK Platform 36 `android.jar`, SHA-256
`d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`, as an explicit
compile-time bootclasspath and classpath, never a runtime implementation.

On the API 36 x86_64 emulator, the signature-authenticated controller invoked the worker's
fixed valid-source, malformed-source and D8 probe. ECJ 4.12 produced `Valid.class`, rejected
the malformed source with diagnostics, and D8 produced `classes.dex`. The worker then compiled
and linked fixed Java/XML counter resources with Android-hosted AAPT2, merged the DEX into an
unsigned APK, and verified it contains `AndroidManifest.xml`, `resources.arsc`, and
`classes.dex`. That temporary probe artifact was erased with its worker scratch directory.
A later controller-to-worker snapshot build, signing, install, and update test is recorded in
`docs/STATUS.md`. No desktop compiler result was substituted.

R8 is an upstream Google Maven artifact, rather than an Android SDK redistribution. Its POM
lists BSD-3-Clause, Apache-2.0, MIT, BSD, CDDL/GPL with Classpath Exception, LGPL-2.1 and
Eclipse Distribution License inputs. The complete R8 LICENSE was later packaged;
the worker no longer bundles the Android SDK compile JAR, but AAPT2 redistribution
evidence remains incomplete, so distribution is still blocked.
