# Source-built AAPT2 linked-input inventory — 2026-09-27

This inventory belongs to the **candidate** ARM64 AAPT2, not the shipped
worker compiler. LLVM lld's `-Map` output is retained in ignored
`toolchains/aapt2-build-arm64-strict/aapt2-link.map` (SHA-256
`d97f516e1f5ee67e45d6c797c50bd62fd7eb9741129f78844efa3e3ae7c61c8d`).
The linked candidate ELF remained byte-identical after enabling the map:
SHA-256 `b98cb0180ef839850ce63fc81d6d662834503d24b0fae83594c5d497e86c2179`.
The compact tracked [`link-members.json`](../../toolchain/aapt2-source-compat/link-members.json)
records every distinct archive/member label parsed from the map. It is an
object/link inventory, **not** a complete source-license determination.

| Link input group | Archives | Distinct archive/member labels | Confirmed object instances |
| --- | ---: | ---: | ---: |
| Pinned wrapper AOSP archives | 8 | 133 | 134 |
| Source-built Abseil archives | 46 | 90 | 90 |
| Source-built Expat, fmt, libpng, protobuf, UTF-8 validity | 5 | 65 | 65 |
| NDK libc++, libc++abi, unwind, compiler builtins | 4 | 110 | 110 |
| **Total** | **63** | **398** | **399** |

`vendor/liblibaapt2.a` has two linked objects with the same `Util.cpp.o`
member label. The map attributes symbols to both pinned
`base/tools/aapt2/io/Util.cpp` and `base/tools/aapt2/util/Util.cpp`; both
source files have Apache-2.0 headers. Counting the label once would omit one
linked source object.

The map also contains 12 direct objects: ten AAPT2 command objects and two
NDK startup objects. The linked ELF's dynamic dependencies are Android
`libz.so`, `liblog.so`, `libm.so`, `libdl.so` and `libc.so`; those are not
bundled archives. No host library path appears in the linked archive list.

## Pinned source and notice locations

| Linked archive(s) | Source/notice evidence | Limit |
| --- | --- | --- |
| Pinned AAPT2 CMake wrapper and patch-generated header | Pinned wrapper commit `c4edf8539a34a8600538e6642c1ecb170452a79e`; full Apache-2.0 `LICENSE.txt` SHA-256 `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`, preserved under `aapt2-source-built/wrapper-LICENSE.txt`. | This establishes the wrapper's declared license and exact text, not complete rights for every nested AOSP source or NDK runtime object. |
| `liblibaapt2.a`, `liblibandroidfw.a`, direct AAPT2 objects | Pinned `vendor/base` Gitlink; `base/NOTICE` SHA `cf4dd7cea5b6bcd2622ddd3529b3c74d16ba2b4db444575bc6f5c33f8a960530`; `base/libs/androidfw/NOTICE` SHA `38751245389e1e23f73e6f5384b5cbe7fa972cc4410c5adc9c04b082a0b9561a` | Generated protobuf source derives from pinned `.proto` files; nested includes still need file-level review. |
| `liblibcutils.a`, `liblibutils.a` | Pinned `vendor/core` Gitlink; `core/libcutils/NOTICE` and `core/libutils/NOTICE` each SHA `38751245389e1e23f73e6f5384b5cbe7fa972cc4410c5adc9c04b082a0b9561a` | Included headers need review. |
| `liblibincfs.a` | Pinned `vendor/incremental_delivery` Gitlink; `incfs/NOTICE` SHA `55ffa4d261bd3cc039fefee3a7463222cd2409c92b608c854fd4b7b6844b8030` | Candidate's ordinary-file guard is a local patch; review its compatibility and notice implications. |
| `liblibbase.a` | Pinned `vendor/libbase` Gitlink; `NOTICE` SHA `e0afbb47be661ce2076d21a1d4dc02caa58b7c215ab7ff9d151b6724be341f77` | Included headers need review. |
| `libliblog.a` | Pinned `vendor/logging` Gitlink; `liblog/NOTICE` SHA `f09747e3bc87e0ec3bdea983b37e18eeda223e01e678f76418a8a731ed8f590f` | Two extra pinned writer objects are included by the candidate patch. |
| `liblibziparchive.a` | Pinned `vendor/libziparchive` Gitlink. Its four selected `.cc` files (`zip_archive`, `zip_cd_entry_map`, `zip_error`, `zip_writer`) carry Apache-2.0 headers. | This checkout has no root `NOTICE` or `LICENSE` file; included headers and any nested code still need review. |
| `libabsl_*.a` | Pinned AOSP Abseil commit and `LICENSE` SHA `c79a7fea0e3cac04cd43f20e7b648e5a0ff8fa5344e644b0ee09ca1162b62747`, already tracked in `aapt2-aosp/`. All 90 linked object source files mapped through the generated Ninja archive/object edges and carry Apache-2.0 headers. | Confirm any third-party notices used by transitive included headers. |
| `libexpat.a`, `libfmt.a`, `libpng16.a`, `libprotobuf.a` | Exact AOSP source commits and root license SHA-256 values are in [the source-build record](aapt2-source-build.md). Exact candidate Expat, fmt and libpng notices are now tracked in `toolchain/licenses/aapt2-source-built/`; the protobuf root license is already in `aapt2-aosp/`. | Root licenses alone do not prove complete transitive coverage. |
| `libutf8_validity.a` | Built from pinned protobuf checkout's `third_party/utf8_range`; its MIT `LICENSE` SHA is `02de69b64fc36d9e938f418e52723e42f0b2b226d58a9cb3c8dcbdf7059f5074`, now tracked as `aapt2-source-built/utf8-range-LICENSE`. | This nested license was absent from the earlier root-only table. |
| NDK static runtime archives and startup objects | User-local NDK `28.2.13676358`; `NOTICE.toolchain` SHA `789714a9076571c580cf531bdcebec776a4d889c12c3c1108adafc6e4338710a`, LLVM prebuilt `NOTICE` SHA `f96f763beb66a7ba7a667647fc64c0226ace875e590c831fdd9579ec1c1d91e1`, sysroot `NOTICE` SHA `2905ac5c59628352af990c748a8f4c3675a04f36eca7914612e3068661d2c6a6`. The latter two exact files are now tracked in `aapt2-source-built/`. | Map each selected runtime archive/object to the exact applicable notice sections before distribution. The aggregate `NOTICE.toolchain` also covers host tools, so its inclusion does not imply every notice applies to the linked AAPT2. |

The installed NDK's `source.properties` identifies `28.2.13676358`/r28c,
matching the [official NDK r28c release tag](https://android.googlesource.com/platform/ndk/+/refs/tags/ndk-r28c/).
Its LLVM prebuilt `clang_source_info.md` (SHA-256
`8ffa6ebc4941a0f0de492584444c08b65ce96ba32d824d674fd35aa0398f1454`)
names upstream LLVM base `3b5e7c83a6e226d5bd7ed2e9b67449b64812074c`
and an Android patch stack; the packaged `clang --version` separately reports
Android LLVM revision `97a699bf4812a18fb657c2779f5296a4ab2694d2`.
Neither string is an archive-specific build record for the 110 selected NDK
runtime objects. The aggregate `NOTICE.toolchain` contains separate libc++,
libc++abi and compiler-rt license sections, but those sections have not yet
been mapped to every linked member; exact runtime provenance and notice
coverage remain open.

The [Android NDK C++ runtime guide](https://developer.android.com/ndk/guides/cpp-support)
explicitly describes packaging the NDK's libc++ runtime in apps and identifies
the LLVM project's Apache-2.0-with-LLVM-exceptions terms. This supports the
ordinary use of NDK runtime code in native app binaries; it does not identify
the exact source or required notices for each member linked into this AAPT2.

For the pinned AOSP wrapper source objects, 128 mapped non-generated archive
member labels and the ten direct AAPT2 command sources carry Apache-2.0
headers. The shared `Util.cpp.o` label was checked against both source files.
The five generated AAPT2 protobuf objects (`ApkInfo`, `Configuration`, `ResourceMetadata`,
`Resources`, `ResourcesInternal`) derive from five pinned `.proto` files that
also carry Apache-2.0 headers. These header checks do not cover transitive
included headers, generator runtime code, or the non-AOSP archives.

The 65 linked labels from the five non-Abseil third-party archives map to
three Expat C files, one fmt C++ file, 17 libpng C files, 43 protobuf C++
files, and one UTF-8 validity C++ file. All 17 libpng source headers refer to
the libpng license in pinned `png.h`; the fmt source refers to the MIT text in
`format.h`; all three Expat sources carry their copyright header and pinned
`COPYING` supplies the MIT-family terms. Of the 43 protobuf objects, 40 source
files cite the root BSD-style license, one embeds the BSD terms, and two are
generated from pinned BSD-licensed `.proto` files. The UTF-8 validity source
identifies its nested MIT license. This file-level pass still does not audit
every transitive included header or NDK runtime object. The source-built zlib
archive is not among the 63 linked archives, but its pinned license is
collected because libpng was built against its headers/archive.

The uninterrupted replay's eight **ARM64 build** Ninja dependency databases
were also queried with `ninja -t deps`; the separate host `protoc` database
is outside this inventory. Across **all ARM64 compiled objects**, including objects not
selected by the linker, they list 2,736 distinct input paths: 1,197 under the
NDK, 703 under the pinned third-party dependency checkouts, 348 under the
replay's generated/build trees, and the remainder under the pinned wrapper
source tree or linker inputs. This is a conservative header-search inventory,
not a mapping of headers used by only the 399 linked object instances.
Within the pinned third-party and wrapper trees, a first-pass scan found 23
included header-like files without a `copyright`, `license`, `licensed`, or
`SPDX` marker in their first 5 KiB. This heuristic is not a license verdict:
12 are protobuf-generated `.pb.h` files whose paired checked-in `.proto`
files carry BSD-style terms; one is protobuf's checked-in
`cpp_edition_defaults.h` without a per-file marker. Eight come from the
pinned wrapper source trees, including the patch-generated Aconfig header.
The other two are Abseil's `tzfile.h` and
`get_current_time_posix.inc`; the latter has no per-file marker and the
former explicitly says it is in the public domain and is included by linked
`libabsl_time_zone.a` objects. The pinned Abseil root license is tracked,
but exact transitive notice coverage remains open.
One local header,
`vendor/base/libs/androidfw/include/android_content_res.h`, was initially
untracked and missed by the 26-file patch verifier despite being included by
linked `LoadedArsc.cpp`. The pinned wrapper already adds it with
`patches/base/0003-base-Add-android_content_res.h.patch`; it is now the 27th
hash-pinned source input. A clean local checkout passed application and
verification of all 13 wrapper and three compatibility patches, then a fresh
ARM64 binary compile/link. Its 398 normalized archive/member labels match
the earlier replay. Complete header-license coverage remains open.
The remaining unmarked files and NDK headers still need exact source/notice
review before distribution.

This inventory narrows the candidate's source/notice work. It does not clear
redistribution of the candidate or of the separate Android API 36 `android.jar`.
The current release worker still uses the earlier opaque AAPT2 binary.

## Installed runtime build lineage — 2026-09-28

The installed NDK contains a build manifest and BUILD_INFO, not just the
LLVM base/patch summary. Independently verified
`manifest_13624864.xml` SHA-256
`f6d04b56172c8d31f11cf7286208caa1dda259d544d72706ca56fc173ac1d47c`.
Its exact projects are LLVM `97a699bf4812a18fb657c2779f5296a4ab2694d2`,
Android LLVM build recipes `e727bfb014bd436f581a66a450c939a6983a1fc3`
and Bionic `b86008a9cd14a7748867d2232e6de439e4809c10`. BUILD_INFO identifies
build 13624864 on branch `aosp-llvm-r530567`. This establishes installed
source/build lineage; it is not a reconstructed or bit-for-bit archive build.

The tracked [runtime receipt](../../toolchain/aapt2-source-compat/ndk-runtime-inputs.json)
records exact hashes for the four linked runtime archives and two API31
startup objects, plus all selected member labels and metadata/notice hashes.
Counts independently replayed from the strict candidate map are libc++ 26,
libc++abi 16, unwind four and compiler builtins 64: 110 members total.
This receipt belongs to that exact map/ELF. Later candidate output must be
compared before using it. It does not close member source-path, per-file
license or transitive notice coverage; those checks continue against the
pinned source revisions. No compiler APK was changed by this audit.

## Selected runtime source and notice closure — 2026-09-28

The new [source/notice map](../../toolchain/aapt2-source-compat/ndk-runtime-source-map.json)
was independently checked against exact local archives, map/ELF bytes,
upstream source files, component licenses and pinned Bionic recipes.
All **110 selected runtime members** map to **63** actual files at LLVM
`97a699bf4812a18fb657c2779f5296a4ab2694d2`: libc++ 26/26, libc++abi 16/16,
unwind 4/4 and builtins 64/17. Each selected source explicitly identifies
`Apache-2.0 WITH LLVM-exception`. Generated outline atomics are based on
actual `compiler-rt/lib/builtins/aarch64/lse.S`; source recipe mappings resolve
AArch64 fp_mode and generic/PowerPC basename collisions. This identifies
sources; it is not bit-identical rebuilding of the installed NDK archives.

The strict, uninterrupted replay and clean-source maps each have **398**
normalized archive/member pairs, all identical (SHA-256
`ca96874114bc62d76112b7042cbae4e6450d199d5bd9163e8d988ac9ca8afec4`).
Their map and ELF hashes differ and are preserved separately. Do not
attribute a runtime receipt to another binary just by its display name.

Actual compiler-rt, libc++ and libc++abi LICENSE files exactly match text
inside the installed NDK notice bundles and collected prebuilt notice.
The pinned **libunwind/LICENSE.TXT** is absent byte-for-byte from those
bundles. Its exact text (including legacy UIUC/MIT terms) is now preserved
in `toolchain/licenses/aapt2-source-built/llvm-libunwind-LICENSE.TXT`, SHA-256
`b5efebcaca80879234098e52d1725e6d9eb8fb96a19fce625d39184b705f7b6d`.
Three exact CREDITS files referenced by the other component licenses are
also retained there; carrying the originals avoids losing the referenced
credit lists. No new APK packaging verification is claimed.

Pinned Bionic `libc/Android.bp` traces crtbegin_dynamic to `crtbegin.c`
and `crtbrand.S`, and crtend_android to `crtend.S`. All three source BSD-style
notice blocks occur in NDK sysroot NOTICE. Source/recipe/object/notice hashes
and URLs are recorded in the source map. Main rechecked all 110 mappings,
63 source hashes, startup objects and all three normalized member-set files
with Python assertions; returned **0**. Ignored audit source checkout:
`toolchains/aapt2-runtime-notice-audit/llvm-project/`.

The specific missing component text is now collected. Transitive runtime
headers/generated inputs and the earlier unmarked wrapper/dependency header
inventory still need review. Root SPDX/header observations and collected
notices do not alone grant full redistribution clearance. Normal APKs and
worker assets remain unchanged.

## Conservative NDK header source inventory — 2026-09-28

Main independently replayed the eight ARM64 replay Ninja dependency queries
and rehashed every NDK input in the resulting **1,197**-path set. This exactly
matches the receipt: **1,182 headers** and **15 linker/runtime inputs**.
The union includes objects not selected by the final link; it is a conservative
input inventory, not selected-object closure. A
[compact tracked summary](../../toolchain/aapt2-source-compat/ndk-header-input-summary.json)
pins the full ignored per-path receipt hash and the exceptional source cases.

| Header group | Count | Verified source correspondence |
| --- | ---: | --- |
| libc++ | 939 | 932 byte-identical to pinned LLVM source; four generated/NDK-only paths and three differing files. All have the LLVM exception SPDX marker. |
| Clang resource | 23 | 19 byte-identical; three generated paths and one differing limits.h. 22 have LLVM exception SPDX and limits.h contains copyright/license text. |
| Bionic/POSIX | 108 | 103 have copyright/license text in the inspected prefix, one BSD-2-Clause marker and four no conventional prefix marker. Exact source/notice mapping continues. |
| Linux UAPI/architecture | 104 | Generated Bionic-header route is stated in each file; upstream kernel origin, regeneration and applicable license sections still need verification. |
| Android NDK API | 8 | Seven have copyright/license text; ndk-version.h is generated/unmarked. |

Main checked **951** byte-identical LLVM source pairs, all other available
same-path source hashes and all 1,197 installed file hashes. The 11 generated/
different LLVM source cases and five unmarked non-kernel wrapper paths are
explicitly retained in the summary. Source/notice work is now focused on these
exceptions and generated kernel headers. A prefix marker is an observation,
not a license conclusion; absence is not evidence of public-domain status.
No GPL/BSD/kernel exception classification is inferred without the actual
source/generator/license mapping. Full ignored receipts:
`toolchains/aapt2-runtime-notice-audit/ndk-header-dependency-audit.{json,md}`.
No new APK or physical test was part of this inventory pass.
