# Toolchain redistribution evidence — 2026-09-25

The later [licensing and UX audit](toolchain-license-and-ux-audit.md) compares
the release paths and sets full public API coverage as the product target.
Its [API inventory](research-sdk-api-license.md) reconciles 6,222 of 6,227
class names with source and identifies the five annotation exceptions.
Its [compiler follow-up](research-compiler-license-path.md) maps the 23
previously flagged AAPT2 headers to their component evidence. These findings
do not clear the current opaque AAPT2 or the entire SDK JAR for distribution.

Distribution remains blocked. This audit retrieves missing source notices and narrows the
remaining questions; it does not establish a complete license inventory or correspondence
between the downloaded AAPT2 binary and its purported source inputs. No compiler binary,
device, signing material, or APK was changed during this audit.

## Exact notices recovered

Seven verbatim files were retrieved through official AOSP Gitiles `?format=TEXT`, decoded
from base64, and SHA-256 checked after copying into `toolchain/licenses/aapt2-aosp/`.
The exact URLs, byte lengths, and hashes are recorded in
[`retrieved-2026-09-25.json`](../toolchain/licenses/aapt2-aosp/retrieved-2026-09-25.json).

| Local file | Source evidence |
| --- | --- |
| `libbase-NOTICE` | [Pinned libbase NOTICE](https://android.googlesource.com/platform/system/libbase/+/8d00a4f2e93ce79877575157db1a56c8f8485c90/NOTICE): Apache-2.0 text plus a Chromium BSD notice. |
| `expat-COPYING` | [Pinned Expat COPYING](https://android.googlesource.com/platform/external/expat/+/b257389b1a8d2af937cfa09fb4bb0934d82f0b4e/COPYING): MIT terms and copyright notice. |
| `libpng-LICENSE` | [Pinned libpng LICENSE](https://android.googlesource.com/platform/external/libpng/+/fd431af9a5bb2a1b646aab75e529d4fd39171c7b/LICENSE): PNG Reference Library licenses and historical notices. |
| `core-libcutils-NOTICE` | [Pinned libcutils NOTICE](https://android.googlesource.com/platform/system/core/+/de0979b80d7ccda90c265be919691e038b0c7690/libcutils/NOTICE): Apache-2.0. |
| `core-libutils-NOTICE` | [Pinned libutils NOTICE](https://android.googlesource.com/platform/system/core/+/de0979b80d7ccda90c265be919691e038b0c7690/libutils/NOTICE): Apache-2.0. |
| `googletest-LICENSE` | [GoogleTest LICENSE](https://android.googlesource.com/platform/external/googletest/+/b9640af0b0a5b0e94bbef3f584763be6926b1ce3/LICENSE): BSD-3-Clause. |
| `tinyxml2-LICENSE.txt` | [tinyxml2 LICENSE](https://android.googlesource.com/platform/external/tinyxml2/+/c1a174ab5c7f50e6877d3f1c8355759890e61300/LICENSE.txt): zlib-style terms. |

The exact `system/core` root listing has no root NOTICE. The two component notices above
replace the unsuccessful root-file lookup; they do not cover every file in that repository.
The previously reported HTTP retrieval blocker is resolved for these selected files.

The official `platform-tools-35.0.2` tags resolve to GoogleTest
`b9640af0b0a5b0e94bbef3f584763be6926b1ce3` and tinyxml2
`c1a174ab5c7f50e6877d3f1c8355759890e61300`. These are verified tag resolutions, not evidence
that either revision contributed code to the packaged binary.
[GoogleTest tag](https://android.googlesource.com/platform/external/googletest/+/refs/tags/platform-tools-35.0.2),
[tinyxml2 tag](https://android.googlesource.com/platform/external/tinyxml2/+/refs/tags/platform-tools-35.0.2).

The pinned Expat `Android.bp` has a broad repository license declaration and directs readers
to consult the owners/licensing contact about special conditions. Its aggregate list includes
MIT, CC0, GFDL and legacy categories; that list alone does not establish which terms apply to
the source subset actually linked. `COPYING` alone therefore does not close the subset audit.
[Exact Expat build metadata](https://android.googlesource.com/platform/external/expat/+/b257389b1a8d2af937cfa09fb4bb0934d82f0b4e/Android.bp).

## AAPT2 source and binary correspondence remains open

At the recorded wrapper revision `aa13480acd6c32f7e1c384a2db76b9e2ba8eb989`, the root
CMake file sets `TOOLS_VERSION` to **35.0.3**, while the packaged download is release
**35.0.2**. The source-fetch script defaults to `master` and accepts a caller-selected tag;
the README gives `platform-tools-35.0.2` as an example. These facts do not establish which
source selection, patches, NDK, or build options produced the downloaded release.
[The upstream v35.0.2 release page](https://github.com/lzhiyong/android-sdk-tools/releases/tag/35.0.2)
associates that release with wrapper commit `aa13480`. This ties the release
metadata to the wrapper revision, but does not identify the source or link
inputs that produced the archive bytes. An
[open upstream question about the v35.0.2 NDK version](https://github.com/lzhiyong/android-sdk-tools/issues/56)
has no maintainer answer in the inspected issue; no NDK version is inferred.
[Pinned CMake file](https://github.com/lzhiyong/android-sdk-tools/blob/aa13480acd6c32f7e1c384a2db76b9e2ba8eb989/CMakeLists.txt),
[source-fetch script](https://github.com/lzhiyong/android-sdk-tools/blob/aa13480acd6c32f7e1c384a2db76b9e2ba8eb989/get_source.py),
[README](https://github.com/lzhiyong/android-sdk-tools/blob/aa13480acd6c32f7e1c384a2db76b9e2ba8eb989/README.md).

The [35.0.2 tag tree](https://github.com/lzhiyong/android-sdk-tools/tree/35.0.2)
contains the wrapper and repository map, but no checked-in `src/` tree. Its README
explicitly says submodules and source-code archives were removed, and describes the
`platform-tools-35.0.2` fetch as an example rather than a record of the release build.
The pinned CMake source-package configuration also excludes `get_source.py` and
`patches/`. Consequently neither the tag nor that source-package recipe is an exact
manifest of the AOSP commits and modifications used for the released AAPT2 binary.
The [tagged wrapper commit](https://github.com/lzhiyong/android-sdk-tools/commit/aa13480acd6c32f7e1c384a2db76b9e2ba8eb989)
still declares `TOOLS_VERSION 35.0.3`; the 35.0.2 label alone does not resolve that
discrepancy. These are limits of the inspected upstream records, not proof that the
binary was built from different sources.

The inspected `aapt2` link declaration includes `libincfs`, `libselinux`, `libsepol`,
`libpackagelistparser`, `libziparchive`, `libbuildversion`, `liblog`, and `c++_static`, in
addition to several already documented libraries. `libandroidfw` publicly links `fmt::fmt`.
These inputs need exact source revisions, subset/license mapping and applicable runtime
notices. GoogleTest and tinyxml2 appear in the root CMake configuration but not in this
target's direct link list; neither their presence nor their absence in the final binary is
established without resolving the complete graph and actual link inputs.
[AAPT2 target](https://github.com/lzhiyong/android-sdk-tools/blob/aa13480acd6c32f7e1c384a2db76b9e2ba8eb989/build-tools/aapt2.cmake),
[libandroidfw target](https://github.com/lzhiyong/android-sdk-tools/blob/aa13480acd6c32f7e1c384a2db76b9e2ba8eb989/lib/libandroidfw.cmake).

The pinned wrapper's repository map identifies these additional source roots. Their actual
release commits and relevant license files were not collected in this bounded audit:

| Target/input | Source root needing subset review |
| --- | --- |
| `libincfs` | `platform/system/incremental_delivery` |
| `libselinux`, `libsepol` | `platform/external/selinux` |
| `liblog` | `platform/system/logging` |
| `libziparchive` | `platform/system/libziparchive` |
| `libbuildversion` | `platform/build/soong`, plus wrapper patch inputs |
| `fmt::fmt` | `platform/external/fmtlib` |
| `libpackagelistparser` | `platform/system/core/libpackagelistparser`; existing libcutils/libutils notices do not establish coverage |
| `c++_static` and compiler/runtime contributions | Actual NDK revision and linked runtime objects are unidentified |

Nested headers, generated protobuf sources and dependencies of these targets still require
review against the actual link graph.
[Pinned repository map](https://github.com/lzhiyong/android-sdk-tools/blob/aa13480acd6c32f7e1c384a2db76b9e2ba8eb989/repos.json).

### Investigated source-build alternative

The [Termux `aapt` recipe](https://github.com/termux/termux-packages/blob/master/packages/aapt/build.sh)
points to [`termux/android-build-tools`](https://github.com/termux/android-build-tools/blob/master/README.md)
at tag `16.0.0.4`. A credential-free shallow checkout verified that tag resolves to
wrapper commit [`c4edf8539a34a8600538e6642c1ecb170452a79e`](https://github.com/termux/android-build-tools/commit/c4edf8539a34a8600538e6642c1ecb170452a79e).
The wrapper does pin 11 AOSP submodules and 13 local patch files. Its CMake file
sets `ANDROID_VERSION` to `android-16.0.0_r4`, enables vendor patching by default,
and requests C++23; `vendor/aapt2.cmake` defines the AAPT2 target. The tag's
direct Gitlinks are:

| Submodule | Pinned commit |
| --- | --- |
| `vendor/aidl` | `3747384b876442e7ea0c355fe5adc75b29362833` |
| `vendor/base` | `45034f0663f960d9ee5fb0a101a4732b71f6e2f4` |
| `vendor/build` | `b815dded1eafbf06191a6ae306956bb6ed6fb415` |
| `vendor/core` | `e9d1fa3705d7fbd0ac1c942bc4c276161bfbfed1` |
| `vendor/incremental_delivery` | `6d8b9b339e7cc9abfeba475fc4c89984c2824cd9` |
| `vendor/libbase` | `bdf0f4db82fae78492c92a55d473aacc4e963e79` |
| `vendor/libziparchive` | `94db64a81930e4cc8bacca4010bf935f664c97e1` |
| `vendor/logging` | `58dac317ea08b8f275ebd01ce864acab2de30b93` |
| `vendor/native` | `fcbde2bcff56ca1d7bec9cabf8bdca5e288bf103` |
| `vendor/sysprop` | `bd6e32a74ee891ec03c5b27c7f3c63501f4a623f` |
| `vendor/zopfli` | `610a2d4f13c64608d21b083177838368c9fcb1cc` |

The recipe lists `fmt`, `libc++`, Expat, libpng, zopfli and zlib as package
dependencies, and GoogleTest and protobuf as build dependencies. It uses
`TERMUX_PKG_SHA256=SKIP_CHECKSUM`; the wrapper tag pins its own source tree,
submodules and patches, but the recipe's moving `master` reference, external
dependency versions and exact Android NDK/toolchain inputs remain unpinned for
this project. The [AOSP AAPT2 build
file](https://android.googlesource.com/platform/frameworks/base/+/master/tools/aapt2/Android.bp)
defines a host binary, so the AOSP build declaration alone does not yield a
packaged Android ARM64 executable.

This is a plausible replacement investigation, not a replacement selected for the
product. Before substituting it, pin the recipe, verify the checked-out submodule
contents against the gitlinks, pin NDK and dependency artifacts with hashes,
enumerate the actual link graph and notices;
build ARM64 with the project's local toolchain; then run the same on-phone resource
compile/link and signed-install tests. A new source-built AAPT2 would not resolve the
separate API 36 `android.jar` redistribution question. No product source, compiler binary,
or product APK was changed when this route was identified. The later local
configure experiment below fetched source into ignored scratch storage only.

### First local ARM64 configure experiment

On 2026-09-26, the wrapper and all 11 submodules were fetched into ignored
`toolchains/aapt2-source-c4edf85/` with no Git credential helper or global
configuration. `git submodule status` matched every Gitlink in the table above.
The source checkout occupied 3.1 GiB (reported by `du -sh`; this is allocated
disk usage, not a download measurement). No vendor patch was applied.

Android SDK Manager installed NDK **28.2.13676358** (r28c; 2.2 GiB allocated)
and CMake **3.31.4** (84 MiB allocated, bundled Ninja 1.10.2) under ignored
`toolchains/android-sdk/`. Both package XML files identify
`android-sdk-license`; the NDK's `NOTICE` and `NOTICE.toolchain`, and CMake's
`doc/cmake-3.31/Copyright.txt` and `doc/ninja/LICENSE`, remain with those local
packages. SHA-256: NDK `package.xml`
`de7942c667fea2fa52d584d3460a2639b49e8a3c485a85a284f5b4182ea9ac84`,
NDK `NOTICE` `faf0dc52cf6ce9ab09a04b0c074a378dd2565ee1ed9bc1179d83793bcdba012e`,
NDK `NOTICE.toolchain` `789714a9076571c580cf531bdcebec776a4d889c12c3c1108adafc6e4338710a`,
CMake `package.xml` `2be12463c7e5f027b7818987712c277fce2def614648d3d954b107b6ea290d99`,
CMake copyright `0676e30827684487aa0ea8f72f01e9460a6320fa34a2888df0ad1dd3e8a06eb8`,
Ninja LICENSE `eb7e9ab9690124c5c9f42bdc81383d886a3dede26345b6ed15bbad7caf81f7ea`.
These are local build tools, not notices for a shippable AAPT2 binary.

An [NDK-documented direct CMake](https://developer.android.com/ndk/guides/cmake)
configure used `arm64-v8a`, minimum API 31, the NDK toolchain file, Ninja, and
`CMAKE_FIND_ROOT_PATH_MODE_PACKAGE=ONLY` to avoid accepting host libraries as
Android inputs. The first configure identified NDK Clang 19.0.1 and host Bison
3.8.2, then stopped with `Could NOT find EXPAT (missing: EXPAT_LIBRARY
EXPAT_INCLUDE_DIR)`. The command disabled vendor patching for this dependency
discovery pass. No ARM64 AAPT2 was built, no worker APK was changed, and no
runtime or redistribution result follows from this configure. A next build
experiment needs pinned Android-target Expat, fmt, libpng/zlib, protobuf and
other actual link inputs; the [Termux recipe](https://github.com/termux/termux-packages/blob/master/packages/aapt/build.sh)
provides dependency names but not a reproducible package input set.
The subsequent pinned Android 16 library builds and the host `pkg-config`
contamination caught on a configure retry are recorded in
[`validation/aapt2-source-build.md`](validation/aapt2-source-build.md). The
later clean configure generated Ninja files with ARM64 Expat, fmt, GoogleTest,
protobuf/Abseil and libpng/zlib inputs, but compilation of AAPT2 and the
matching host `protoc` were still pending at that checkpoint. Neither this
experiment nor the collected root licenses establish complete link notices.

### Subsequent linked candidate and phone evidence

On 2026-09-27 the matching host `protoc 25.8` and an ARM64 AAPT2 executable
linked from the pinned sources. The ignored checkout applied its 13 pinned
wrapper patches, plus small local compatibility edits to CMake target include
paths, static liblog writers, app-inappropriate VNDK/Incremental FS paths and
one self-defined liblog API annotation. The exact edits, build artifact
hashes and constraints are recorded in
[`validation/aapt2-source-build.md`](validation/aapt2-source-build.md). The
stripped candidate has 16 KiB ELF load alignment and five Android system
dynamic dependencies (`libz`, `liblog`, `libm`, `libdl`, `libc`). A disposable
shell-context resource compile/link and a real controller-to-worker signed
build plus two-version continuity passed on the physical S23 Ultra. The
temporary candidate worker was replaced with the exact original debug APK;
its installed and local output hashes match the release snapshot again.

This provides a more traceable alternative to the opaque 35.0.2 release
binary, but it is **not yet a distributable substitution**. The compatibility
edits are tracked as three compatibility patches plus 13 pinned wrapper patches,
with a guarded apply script and 27-file
post-patch SHA-256 manifest. A later audit found that the earlier three-patch,
26-file check had missed an untracked five-line Aconfig header included by
`LoadedArsc.cpp`. The pinned wrapper already patches in that header; it is
now hash-pinned, and the
verifier rejects all extra untracked source paths. Reversing and replaying the
original patches in the ignored checkout produced byte-identical unstripped
and stripped ARM64 binaries; a deliberate tracked source edit was rejected.
That earlier check was incomplete. A separate clean checkout of the pinned
local wrapper and submodules passed all 13 wrapper and three compatibility
patches plus verification and a fresh ARM64 compile/link. A later guarded script completed
a one-shot build from pinned local source and SDK inputs in a new output
directory, and the compatibility patches received a read-only review. The
linked-object notice mapping remains incomplete. An
[`lld` archive-member inventory](validation/aapt2-linked-inputs.md) now narrows
the candidate to 63 archives, 398 distinct archive/member labels (399
confirmed object instances) and 12 direct objects;
source/license mapping for those objects and required notice packaging remain
unfinished. The earlier API 36 `android.jar` issue below is independent; the
current worker excludes that JAR. It still packages the opaque 35.0.2 AAPT2
binary and remains blocked for distribution.

The pinned source-build wrapper's full Apache-2.0 `LICENSE.txt` (SHA-256
`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`)
was later copied verbatim into `toolchain/licenses/aapt2-source-built/`.
The older 35.0.2 release wrapper notice is a different, short file and does
not replace the exact pinned-source license. This closes that one notice gap;
the linked AOSP/third-party/NDK notice and runtime mapping remains open.

The one-shot build produced stripped ARM64 AAPT2 SHA-256
`8f87b17cc1f57d489802e8c0aadc2b68e14ee0a0d4514081e2134b84ce2ecd00`;
its link map had the same 398 archive/member labels (399 object instances)
as the earlier candidate. On the S23 Ultra, that binary compiled and linked
a disposable resource APK and, temporarily packaged in the worker, passed
real separate-UID build and two-version signer probes. Exact hashes, commands,
the IncFS caveat and restoration evidence are in
[`validation/aapt2-source-build.md`](validation/aapt2-source-build.md).

Hashes of the retrieved metadata: root CMake
`14c0ea6f480f5e156260f6360726ef910f68b79e5cc6fb723e8933ff41ce9774`;
AAPT2 CMake `91f3cda3bdbac37be0eb514c25a7c132b5bacafd5bfa8f501e85d0267b39c511`;
libandroidfw CMake `8324396581f62035b4b68e7571362bdd45d0461264ef1c1c59592b141827ed80`.
Metadata was inspected in temporary storage; no compiler source was added to the product.

## Android API 36 compile stubs

The installed API 36 platform's `data/NOTICE.txt` was also checked on
2026-09-26 (SHA-256
`6f3c947e9827793a4984bd5d0e4a306b76c95a65186fe779feec748fc739c56f`).
Its header identifies notices for the **tools** directory, including older
`/bin/mksdcard` entries, and it contains no `android.jar` entry. It cannot
serve as a component notice inventory for the exact API 36 r2 JAR. The
[SDK terms](https://developer.android.com/studio/terms) distinguish the general
redistribution restriction in section 3.4 from open-source component terms in
section 3.5; artifact-specific mapping remains required.

An on-phone experiment on the Samsung SM-S918B showed that AAPT2 can link
the fixed XML fixture using the device's readable
`/system/framework/framework-res.apk` instead of a copied SDK `android.jar`.
The resulting uninstalled resource APK had the same SHA-256 as the SDK-JAR
fixture result. This establishes resource-link compatibility for that device
and fixture only. The phone's `/system/framework/framework.jar` contains DEX
and no `.class` files, so ECJ still needs a permitted Java compile-stub input;
the earlier worker therefore continued to package the SDK JAR. A later
production worker revision uses narrowly authored source stubs and no longer
packages that JAR. On the S23 Ultra it passed an exact default-source build
through the real worker path; broader API coverage and generated-app runtime
remain unverified. See `STATUS.md` for the physical result.

The earlier worker packaged the installed API 36 platform r2 `android.jar`,
which matches the formerly packaged input SHA-256
`d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`.
Its SDK `build.prop` identifies build `13352765` / `BP22.250325.007`.
ZIP inspection of this exact `android.jar` found 6,227 `.class` entries and
one legal-material entry, `NOTICES/libcore-NOTICES.txt` (476,817 bytes,
SHA-256 `8304b9f57ad50d93101628cdaccc82ac1333276f942f431cf15f953c61617e39`).
Its headings identify `core-oj` and `core-libart` runtime components, not a
per-class license mapping for the whole SDK stub JAR. The notice was already
inside the JAR bundled in the earlier worker; its existence narrows the
missing-notice question but does not establish whole-JAR redistribution rights.
The same SDK installation includes `android-stubs-src.jar`, SHA-256
`f06372cb9bdbe4dececd35e5cb7b61fec01bb521dcd8a2424489f659b1e9322a`.
Inspection found 4,475 archive entries and no separately named LICENSE/NOTICE/COPYING file.
The `android/app/Activity.java` header declares Apache-2.0; `java/lang/String.java` carries
GPLv2 with the Classpath Exception. These are concrete mixed-license examples, not a complete
inventory or proof of exact source-to-binary correspondence.

Public AOSP artifacts were checked by comparing Git blob IDs, without downloading
replacement JARs:

| Artifact | Git blob SHA-1 |
| --- | --- |
| Local SDK r2 `android.jar` (`git hash-object`) | `e560471790469782b41614eac5f290f5388d0d79` |
| Local SDK r2 `android-stubs-src.jar` (`git hash-object`) | `98b4873edde5fc499b2336e6ffd1d1971983a221` |
| `prebuilts/fullsdk/platforms`, commit `f40140c79226b1a491c5c76c12c532a3e688418e`, `android-36/android.jar` | `cb4ba919a52660b43fc9dcf355ab4b866f013a5c` |
| `prebuilts/sdk`, `android16-release` resolved to `3b7daadc4e54d51439e5ae9b4dbd96cda8b6104e`, `36/public/android.jar` | `2374e9ca9a010b9dd87815a649351854e949059e` |
| `prebuilts/fullsdk/platforms`, API 36 r2 upgrade commit `1b45636761b0dbab9f80779add8fc85ab94b80fb`, `android-36/android.jar` | `e560471790469782b41614eac5f290f5388d0d79` |
| Same API 36 r2 commit, `android-36/android-stubs-src.jar` | `98b4873edde5fc499b2336e6ffd1d1971983a221` |

The two earlier candidates differ from the packaged input; the older fullsdk
commit identifies build `13214815`. The later fullsdk API 36 r2 upgrade has
**exact blob matches for both local JARs** and identifies build `13352765` /
`BP22.250325.007`, matching the local SDK metadata. This resolves public
artifact identity and provenance, not component-level source mapping or
redistribution rights. The other repository's `Android.bp` declares multiple
license families and special-condition review, while explaining that its broad
aggregate assignment can overinclude terms. That metadata cannot replace an
artifact-specific inventory.
[fullsdk commit](https://android.googlesource.com/platform/prebuilts/fullsdk/platforms/+/f40140c79226b1a491c5c76c12c532a3e688418e),
[API 36 r2 fullsdk upgrade](https://android.googlesource.com/platform/prebuilts/fullsdk/platforms/+/1b45636761b0dbab9f80779add8fc85ab94b80fb%5E%21/),
[public SDK directory](https://android.googlesource.com/platform/prebuilts/sdk/+/3b7daadc4e54d51439e5ae9b4dbd96cda8b6104e/36/public/),
[SDK license metadata](https://android.googlesource.com/platform/prebuilts/sdk/+/3b7daadc4e54d51439e5ae9b4dbd96cda8b6104e/Android.bp).

The collected SDK terms' section 3.5 addresses open-source components, but does not itself
identify all components of this JAR or satisfy their conditions. Resolve the exact r2 build's
source/license inventory, notices and any corresponding-source obligations before bundling
it for distribution. A source-built replacement would need its own verified input list and
build provenance; this audit neither builds nor approves one.
The [exact API 36 r2 package metadata](https://android.googlesource.com/platform/prebuilts/fullsdk/platforms/+/1b45636761b0dbab9f80779add8fc85ab94b80fb/android-36/package.xml)
attaches `android-sdk-license` to the entire platform package. Sections 3.4
and 3.5 of that embedded agreement prohibit copying or redistributing SDK
parts except where applicable third-party open-source terms govern the
components. No private-alpha or sideloading exception appears there. On the
available evidence, **do not distribute the whole SDK `android.jar` in the
worker APK**. The [AOSP license guidance](https://source.android.com/docs/setup/contribute/licenses)
also says Apache-2.0 is preferred but exceptions exist; a broad AOSP license
label cannot clear every class in this JAR.
[Official SDK terms](https://developer.android.com/studio/terms).

## Remaining gate and verification

1. Tie the packaged AAPT2 hash to its actual source manifest, patches, NDK/runtime and link
   inputs; resolve the wrapper/release version discrepancy. A reproducible build is one route.
2. Complete file/subset notices for that actual graph, including the newly identified inputs.
   Preserve GoogleTest/tinyxml2 status as unproven until final link evidence resolves it.
3. Keep the SDK r2 JAR out of distributable APKs. If a future worker bundles
   it again, establish exact component licenses and source correspondence and
   meet their conditions. The current authored-stub route instead needs API
   coverage and runtime validation beyond the default source.

Executed checks: official Gitiles retrieval/base64 decode; nonempty plain-text file checks;
SHA-256 comparison of all seven copied notices; `sha256sum` for installed SDK inputs;
`git hash-object` comparisons; Python ZIP inspection of the installed stub source archive;
JSON parse and `git diff --check`. A later offline controller/worker build and
lint gate passed on 2026-09-26. ZIP inspection of the resulting debug APKs
found all 28 collected legal-material files byte-for-byte equal to the local
`toolchain/licenses/` copies in **both** APKs; each includes 20
`assets/aapt2-aosp/` entries. This verifies packaging of the collected
materials only for that earlier APK. It does not close the missing AAPT2
graph or authorize bundling the historical SDK JAR.

## Authored AAPT2 process guard — 2026-10-12

The private harness now builds its small native guard from authored
`worker/src/main/c/aapt2_guard_exec.c` and `child_process_policy.h`, with
`scripts/build_child_process_tools.py`. This is a launcher/process-count policy,
not a new generated app or a replacement opaque AAPT2. No Sketchware source or
ready-made generated APK was copied. Pinned existing NDK r28c28.2.13676358 / Clang19.0.1,
ARM64 and x86_64, minimumAPI31, 16KiB alignment. Actual ELF inspection shows
PIE/dynamic OS libc, no bundled C++ shared runtime; this does not prove that no
NDK startup/compiler-support objects contributed to the executable.

The generated launcher/probe manifests record source/consumed C headers, compiler
wrappers/clang/ld.lld, NDK revision/notices, flags and output hashes. Gradle tracks
sysroot include/lib and Clang runtime inputs as well. The [physical policy record](validation/child-process-policy-a33-20261012.md)
contains exact packaged hashes, runtime checks and remaining ABI/precision limits.
Generated build manifests stay ignored; this is not an exhaustive source/license
map for every link contribution.

Existing shipped notice assets were checked against the actual local NDK:

| Existing asset / current NDK file | Exact SHA256 |
| --- | --- |
| `toolchain/licenses/aapt2-source-built/ndk-sysroot-NOTICE` / LLVM `sysroot/NOTICE` | `2905ac5c59628352af990c748a8f4c3675a04f36eca7914612e3068661d2c6a6` |
| `toolchain/licenses/aapt2-source-built/ndk-llvm-prebuilt-NOTICE` / LLVM `NOTICE` | `f96f763beb66a7ba7a667647fc64c0226ace875e590c831fdd9579ec1c1d91e1` |

These files remain in the controller/worker license assets. The NDK's root NOTICE
and NOTICE.toolchain hashes remain the earlier recorded r28c values. Linux UAPI
usage must be distinguished from kernel implementation copying; the primary
[kernel licensing rules](https://www.kernel.org/doc/html/latest/process/license-rules.html)
explain the syscall exception. That general rule is not a blanket source-lineage
grant for every generated NDK header. Full toolchain/API grants, source delivery,
and correspondence to the existing opaque AAPT2 remain open; public distribution
is still blocked. No complete redistribution clearance is asserted here.
