# ARM64 AAPT2 source-build experiment — 2026-09-26 to 2026-09-27

This is an **ignored, local experiment**, not a replacement for the working
worker AAPT2 or a redistribution approval. The candidate later passed the
physical checks recorded below. The original worker APK has been restored.

## Inputs and boundaries

- Wrapper: Termux `android-build-tools` tag `16.0.0.4`, commit
  `c4edf8539a34a8600538e6642c1ecb170452a79e`, at ignored
  `toolchains/aapt2-source-c4edf85/`. All 11 submodules match its Gitlinks;
  see [the provenance audit](../toolchain-redistribution-audit.md). The
  checkout includes 13 patches; these and the tracked compatibility patches
  were applied and replayed as recorded below.
- Android SDK Manager installed NDK `28.2.13676358` (r28c) and CMake
  `3.31.4` (bundled Ninja `1.10.2`) under ignored `toolchains/android-sdk/`.
  Their `package.xml` files both use `android-sdk-license`; NDK `NOTICE` and
  `NOTICE.toolchain`, CMake `doc/cmake-3.31/Copyright.txt`, and Ninja
  `doc/ninja/LICENSE` remain with the installed tools. Exact hashes are in
  [the provenance audit](../toolchain-redistribution-audit.md).
- The Android ABI is `arm64-v8a` and minimum native API is 31. The NDK
  toolchain's Clang reports `19.0.1`. One build job is used at a time.
- All source and build folders below are under ignored `toolchains/`.
  Git network commands disabled the credential helper and global/system Git
  configuration. No existing credential store was accessed.

The following source trees use the official AOSP annotated tag
`android-16.0.0_r4`. The table records the peeled source commit, the local
root license file's SHA-256, and the static archive built for ARM64. Every
listed archive's object headers reported `Machine: AArch64` with `readelf`.
These root files alone are not a complete transitive license inventory.

| Source | Commit | Root license SHA-256 | ARM64 archive SHA-256 |
| --- | --- | --- | --- |
| `expat` | `79dde19a8fe1dfb580d39de37854221a6de5f09b` | `COPYING` `31b15de82aa19a845156169a17a5488bf597e561b2c318d159ed583139b25e87` | `libexpat.a` `809127e17664d89437f3443e851fb71f60662b82359ac6e5069c2fff31284424` |
| `fmtlib` | `d50695f04854e474766da7d713bf8242f4ceb4f8` | `LICENSE` `07580f2a3b35709ce703d523f447b242f6dfec7582a8c0df102c7fa2849375f8` | `libfmt.a` `e225988cd10ff40e8cfd3a8bf3f03cfdc82426b8234086a69781fe9b43d6a882` |
| `googletest` | `cbeab5d01811d23ea488efc07f2c31f7c53024e2` | `LICENSE` `9702de7e4117a8e2b20dafab11ffda58c198aede066406496bef670d40a22138` | `libgtest.a` `93b56437bb409ca945639f797c4073ae098f0b016514e04784d7c407c8fa9146` |
| `abseil-cpp` | `2bf8472a92b84fe4617eeb50053e7a942edcb1b3` | `LICENSE` `c79a7fea0e3cac04cd43f20e7b648e5a0ff8fa5344e644b0ee09ca1162b62747` | Many `libabsl_*.a`; individual link inputs remain to be determined. |
| `protobuf` | `31fc4250f78631cf8f4f1cc48b200acf11e9c884` | `LICENSE` `6e5e117324afd944dcf67f36cf329843bc1a92229a8cd9bb573d7a83130fea7d` | `libprotobuf.a` `88aa9ca5e991306fbd11e199362216865ca5c94de72923cbd195375903ab6406` |
| `zlib` | `a6f47e8bd7c1ba412c4b5a3539aa77fe628d0172` | `LICENSE` `e1cfcc55c325b3f78cf55df9664abaa066e2271dffe8213347d9fccdfbac8f2c` | `libz.a` `db4066c74d522c928aa00e3484d74e59789c257d62d35ff2146ca22e67d1a5b0` |
| `libpng` | `812ea249a49e26a13fa080821dc094f33c0f6608` | `LICENSE` `16d9daaafbf63a31a5bdc91d4600972548fef5aaa1244202393288dbd079c49a` | `libpng16.a` `a9d738c17b6f3b51d4995ecacfe3bc0327bc24fafb934856c7ef8dae5e3c1cb4` |

The static archives and headers were installed in ignored
`toolchains/aapt2-sysroot-arm64/`. The Expat configure used
`EXPAT_SHARED_LIBS=OFF`, with tools/tests/examples/docs off. fmt used
`BUILD_SHARED_LIBS=OFF`, tests/docs off. GoogleTest used static libraries
with tests and GoogleMock off. Abseil used static libraries, C++17 and tests
off. Protobuf 4.25.8 used static target libraries, tests/protoc/zlib off,
and the pinned Abseil package. zlib 1.3.1 and libpng 1.6.47 used static
libraries; libpng linked the local `libz.a`, with shared/tests/tools off.
The zlib CMake configure renamed its checkout's `zconf.h` to
`zconf.h.included`; that local scratch checkout is not pristine.

## Initial configure results — 2026-09-26

The first NDK/CMake configure stopped at missing Android-target Expat. After
building Expat, fmt and GoogleTest, it stopped at protobuf. After building
protobuf/Abseil, a configure using the default host `pkg-config` found the
**host** libpng 1.6.58. That configuration is invalid as Android evidence.
A fresh `toolchains/aapt2-build-arm64-clean/` configure with
`PKG_CONFIG_LIBDIR` limited to the ARM64 sysroot then correctly stopped at
missing target libpng. After building zlib/libpng, the same clean configure
found **target** libpng 1.6.47 and generated Ninja files.

The first configure warned that host `protoc 36.1` did not match target
protobuf `4.25.8`. A matching host `protoc 25.8` was built from the pinned
protobuf and Abseil source (`toolchains/aapt2-deps-build/protobuf-host/protoc`,
SHA-256 `aff1ef9be535ce36daa526462c85b536fe4146ff6d6620efad99298a4ee7f6da`).
It is a build-time generator and is not shipped. All 13 wrapper patches were
applied sequentially with `git apply --check` before each application in the
ignored source checkout. The wrapper's automatic `git am` patching stayed off.

## Linked candidate — 2026-09-27

The single-job `cmake --build toolchains/aapt2-build-arm64-strict --target
aapt2 --parallel 1` completed after these **additional local compatibility
edits** in ignored source. They are now captured as tracked patches under
`toolchain/aapt2-source-compat/`:

- `vendor/libandroidfw.cmake`: add the already pinned
  `native/libs/binder/include` path for `binder/TextOutput.h`.
- `vendor/libincfs.cmake`: add the pinned `incremental_delivery/incfs/include`
  path and publicly define `INCFS_SUPPORT_DISABLED=1`. In that same submodule,
  `incfs/util/map_ptr.cpp` and `incfs/util/include/util/map_ptr.h` now guard
  their Android IncFS branches with `!INCFS_SUPPORT_DISABLED`. This makes the
  mapped-file code use its ordinary-file branch. The intended worker inputs
  are private scratch files, but the behavior still needs on-phone testing.
- `vendor/liblog.cmake`: include pinned `logd_writer.cpp` and
  `pmsg_writer.cpp` to satisfy four link symbols. In its local
  `logging/liblog/include/android/log.h`, remove the API 37 availability
  annotation only from the function that this static liblog itself defines
  and calls; no system API 37 dependency was added.
- `vendor/libutils.cmake`: define `__ANDROID_RECOVERY__` privately for that
  target, suppressing its system VNDK loader branch, which cannot run as an
  ordinary app process. In this pinned source, only `core/libutils/misc.cpp`
  tests that macro.
- `vendor/base/libs/androidfw/include/android_content_res.h`: the pinned
  wrapper's existing `patches/base/0003-base-Add-android_content_res.h.patch`
  adds a `false` implementation of the generated Aconfig flag accessor
  included by pinned `LoadedArsc.cpp`. It was initially omitted from the
  manifest because the patched file is untracked in the submodule; the exact
  provenance correction and unverified flag semantics are detailed below.

The final CMake cache points to the matching local `protoc`, the exact ARM64
`libpng16.a`, and the ARM64 sysroot as `CMAKE_FIND_ROOT_PATH`. The final link
used the ARM64 static dependency archives and Android NDK system libraries;
there was no host-libpng input. The unstripped executable is
`toolchains/aapt2-build-arm64-strict/vendor/aapt2`, SHA-256
`b98cb0180ef839850ce63fc81d6d662834503d24b0fae83594c5d497e86c2179`.
The stripped experiment copy at `vendor/aapt2-stripped` is SHA-256
`b53f1864211640e07b9d24eece65501adf98f287e24cbb621b848d7020b59890`
(34 MiB; unstripped 114 MiB).

`llvm-readelf` reports ELF64 AArch64 PIE, interpreter `/system/bin/linker64`,
Android API 31, all four `LOAD` segments aligned to `0x4000`, and only
`libz.so`, `liblog.so`, `libm.so`, `libdl.so`, `libc.so` as dynamic dependencies.
An [lld link-map inventory](aapt2-linked-inputs.md) records the selected
archive members and additional NDK runtime contributions; it is not a
redistribution clearance.
## Physical candidate checks and restoration — 2026-09-27

With approval for a disposable phone directory, the stripped candidate ran on
the connected Samsung SM-S918B Android 16/ARM64 from
`/data/local/tmp/plb-aapt2-b53f1864/`. `aapt2 version` returned
`Android Asset Packaging Tool (aapt) 2.20-android-16.0.0_r4`. It compiled one
throwaway `res/layout/main.xml` to a 344-byte `.flat` file, linked that file
and a throwaway manifest against the pinned API 36 `android.jar` on the phone,
and parsed the resulting **uninstalled** resource-only APK with `dump badging`.
That APK was SHA-256
`b13ba01f59e8031132ac678c5c9c8d0f66ee5193205622b52fb64b5277bde520`;
badging showed the disposable package, compile/target SDK 36 and minimum 31.
The exact test directory contents were listed before deleting only that
directory; `test ! -e` confirmed removal. This shell-context test alone did
not exercise the worker UID.

For that boundary, the existing ignored `worker/src/main/jniLibs/arm64-v8a/
libaapt2_exec.so` and worker debug APK were backed up with exact SHA-256
`db737b3bbee99dd8a9e56108cc05d032597874d3f8ac557a69f3c5de5efaf57f`
and `f877d6cb78e56a2ae39f0cea8da1d6e6a7a0cdef49c096091c486a85e853b115`.
The candidate binary was temporarily packaged as that native library. Its
candidate worker APK SHA-256 is
`6e4ba33de12c896522bcd729a600da98728d33647f7856439abbf0ceffd2e325`;
the embedded ARM64 entry hash exactly matched stripped candidate
`b53f1864211640e07b9d24eece65501adf98f287e24cbb621b848d7020b59890`.
In-place installation kept the same worker app. Direct
`localityProofOnly` instrumentation returned code `-1`: controller UID 10465,
worker UID 10464, worker Internet denied, changed Java/XML compiled and signed
on phone, generated no-permission APK SHA-256
`17388daad0336e2853258df86aa19affa819ee49548480409b5c91a37c1dda9d`.
That test APK was **not installed**. `updateContinuityOnly` also returned `-1`
for two real worker builds from revisions 1→2 with the same package/cert;
their generated APKs were not installed.

The original ignored binary was restored. A rebuild embedded its exact hash
but produced a different outer APK hash
(`47e712ba3bbc9d6c19362d872c8e3b7738e3263be59571bc9f828b8bdde060de`); no cause is
claimed. The exact saved original worker APK was then restored to
`worker/build/outputs/apk/debug/worker-debug.apk` and installed in place.
Both the phone's installed worker `base.apk` and that output path again hashed
`f877d6cb78e56a2ae39f0cea8da1d6e6a7a0cdef49c096091c486a85e853b115`.
The controller and generated app were not replaced in this experiment.

## Compatibility patch review

Read-only review of the original three tracked compatibility patches found one
conditional risk: disabling IncFS verification makes the fallback
`IncFsFileMap::Verify` return success without checking a partially loaded
IncFS file. The current worker calls AAPT2 only after unpacking each allowed
snapshot entry into its private `getCacheDir()/active-build` directory and
copying the platform JAR asset into that same scratch directory
(`BuildService.buildSnapshot`, `unpackSnapshot`, `copyAsset`, and
`packageSnapshot`). The fixed toolchain probe also writes its inputs in
private scratch. Thus the exercised input path uses fully materialized ordinary
files. The source review and physical worker pass do not test an IncFS mount;
any future direct-file input route must retain that scratch-copy invariant or
restore IncFS verification before adopting this patch.

## Patch replay and remaining build inputs

`scripts/prepare_aapt2_source_candidate.py` takes an existing clean checkout
at the pinned wrapper commit. It verifies all 11 checked-out submodule HEADs
against their Gitlinks, applies the wrapper's 13 patches and three tracked
compatibility patches, then checks the exact SHA-256 values and changed-path
set of 27 patched source files in
`toolchain/aapt2-source-compat/patched-files.json`. It performs no source fetch
or build. `--verify` checks an already prepared checkout without editing it.
The pinned wrapper checkout's full Apache-2.0 `LICENSE.txt` has SHA-256
`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`
and is preserved verbatim as
`toolchain/licenses/aapt2-source-built/wrapper-LICENSE.txt`. It differs from
the earlier 35.0.2 release wrapper's short notice; the full pinned-source
license was not in the previous seven-file candidate notice bundle.
The pinned wrapper patch `patches/base/0003-base-Add-android_content_res.h.patch`
adds `base/libs/androidfw/include/android_content_res.h` for pinned
`LoadedArsc.cpp`. Its five-line content, SHA-256
`9e4d4aca97ff878e2a983e66bd381507d35a315e5ff9077800b9d1fc4dfc38ad`,
was already present as an **untracked** file during earlier candidate builds.
It hardcodes `resource_readwrite_flags()` to `false`; the pinned AOSP flag is
fixed read-only, but this build has not reproduced Android's generated
Aconfig value pipeline. The exercised XML fixtures do not cover
`RES_TABLE_FLAG_LIST` behavior, so feature parity there remains unverified.
The old verifier ignored untracked files, so its earlier 26-file result did not
cover every actual source input. The verifier now inventories all untracked
files as well as tracked edits, rejects extra paths and symlinked patch outputs,
and hash-checks the header. Two focused tests failed before the fix and pass
after it. A separate clean checkout of the pinned local wrapper and all 11
submodules accepted all 13 wrapper and three compatibility patches, then
  passed `--verify`. A fresh binary build from that clean source checkout
  subsequently passed. The commands
used across these checkpoints were:

```sh
python3 scripts/prepare_aapt2_source_candidate.py toolchains/aapt2-source-c4edf85
python3 scripts/prepare_aapt2_source_candidate.py toolchains/aapt2-source-c4edf85 --verify
python3 -m unittest discover -s scripts -p 'test_prepare_aapt2_source_candidate.py' -v
```

For the earlier relink replay, the three compatibility patches
and 13 wrapper patches
were reversed **only inside the ignored source checkout**, then the tracked
script reapplied and verified them. A single-job ARM64 relink returned the
same unstripped SHA-256
`b98cb0180ef839850ce63fc81d6d662834503d24b0fae83594c5d497e86c2179`
and the same stripped SHA-256
`b53f1864211640e07b9d24eece65501adf98f287e24cbb621b848d7020b59890`;
`cmp` of the two stripped files succeeded. A temporary extra edit to a
patched CMake file caused `--verify` to reject the source hash; that edit was
restored, and verification passed again. This proves repeatability in the
current local environment with the shim already present, not a clean source
or clean machine rebuild. At that checkpoint,
the ARM64 dependency archives had been assembled manually.

The guarded replay script
[`scripts/build_aapt2_source_candidate.sh`](../../scripts/build_aapt2_source_candidate.sh)
now encodes the pinned local dependency build and final AAPT2 link. It rejects
an existing output directory, verifies the local source commits and prepared
wrapper, and builds one target at a time in a new ignored directory. It uses
only already present local source checkouts and SDK packages; it does not fetch
or install those inputs. Its `--resume` option accepts only an output directory
marked by an earlier invocation.

## Fresh dependency and AAPT2 replay — 2026-09-27

The first fresh-output invocation stopped at protobuf configuration because
the local Abseil package directory was not explicit. After that path was
added, it completed the dependency and host `protoc` builds but reached the
wrapper's default vendor auto-patching, which tried to apply the already
verified patches again. `git am --quit` ended that failed patch session
without reverting the checkout; the then-current 26-file tracked-source
verification passed but missed the untracked shim. The
script now sets `ANDROID_BUILD_TOOLS_PATCH_VENDOR=OFF` and includes the
`absl_DIR` path. Resuming the first output completed an ARM64 link; this was
a repaired run, not evidence of one-shot success.

An independent, **uninterrupted** invocation then succeeded from a second new
ignored directory:

```sh
bash scripts/build_aapt2_source_candidate.sh --output toolchains/aapt2-replay-20260927-oneshot
```

After the wrapper-generated header was added to the patch manifest and verifier, the same
one-shot output's `--resume` passed with no Ninja work. The unstripped AAPT2,
stripped AAPT2 and link-map hashes remained unchanged. This confirms the
already-built output used the now hash-pinned header bytes. The separate
clean checkout also passed a fresh binary compile/link; see below.

It verified the seven pinned dependency commits, the prepared wrapper and
all patches, built the target archives and matching host `protoc 25.8` with
one job at a time, and linked AAPT2. Its host `protoc` SHA-256 matched the
earlier build exactly:
`aff1ef9be535ce36daa526462c85b536fe4146ff6d6620efad99298a4ee7f6da`.
An immediate `--resume` rerun reported no Ninja work and preserved all three
final artifact hashes.
The one-shot unstripped ELF is
`toolchains/aapt2-replay-20260927-oneshot/build/aapt2/vendor/aapt2`, SHA-256
`a1a8a676ddb43e74c5d323559baa932d0ff3ed431902b5b6b0bee58cc7633cc5`;
the stripped copy is `toolchains/aapt2-replay-20260927-oneshot/aapt2-stripped`,
SHA-256 `8f87b17cc1f57d489802e8c0aadc2b68e14ee0a0d4514081e2134b84ce2ecd00`.
The generated link map SHA-256 is
`f8b8413fbd982cbdae8e8973f5964f7324982e66c445d488225e66116392b003`.
It has the same 63 archives and 398 distinct archive/member labels as the
earlier candidate; the duplicated `Util.cpp.o` label still represents 399
archive object instances. `llvm-readelf` reports AArch64 Android API 31,
four `LOAD` segments aligned to `0x4000`, and only `libz.so`, `liblog.so`,
`libm.so`, `libdl.so`, `libc.so` as dynamic dependencies. The two fresh-output
stripped ELF hashes differ from each other and from the earlier in-place
replay; no cross-directory byte-reproducibility claim is made.

On the physical S23 Ultra, this exact one-shot stripped binary returned
`2.20-android-16.0.0_r4` and matched its host SHA-256. A disposable XML
resource compiled, then linked against the API 36 JAR on the phone into an
**uninstalled** resource APK; `dump badging` reported package
`dev.smallwright.replayprobe`, min SDK 31 and target/compile SDK 36. Its SHA-256
was `a0300c3e154cae50ab1e24247cfb3a8a0d208e8d9a365c1b298aad898759a48d`.
The first fixture link failed because `compile -o main.xml.flat` actually
produced a ZIP; rerunning with `compile --dir res -o compiled-res.zip` and
`link -R compiled-res.zip` passed. All three exact disposable
`/data/local/tmp/plb-aapt2-replay-*` directories created by these checks were
removed, and absence was checked after each.

The one-shot compiler was temporarily embedded in a worker debug APK saved at
`toolchains/aapt2-replay-20260927-oneshot/candidate-worker-debug.apk`, SHA-256
`aefba6f2afe4caa7b3ac7c1b3b7f18a11207edc942489d25fbda6296a13e37cb`.
On the physical phone, `localityProofOnly` and `updateContinuityOnly` each
returned `INSTRUMENTATION_CODE: -1`. The first reported controller UID 10465,
worker UID 10464, worker Internet denied, changed source compiled and signed
on phone, and a no-permission generated APK SHA-256
`a421a3b399db67df2c8c1e3a2fc92a2f614f8f8a33f977ea39a8b6ad20f21936`;
the second reported two real builds, versions 1 to 2, with a stable signing
certificate. The generated APKs were **not installed**. The exact original
worker native input and debug APK were restored; both local output and the
phone's installed worker `base.apk` again hash to
`f877d6cb78e56a2ae39f0cea8da1d6e6a7a0cdef49c096091c486a85e853b115`.
The controller and installed generated app were untouched.

In a separate disposable phone directory, the same stripped AAPT2 linked the
same fixed XML fixture with `-I /system/framework/framework-res.apk` instead
of the copied SDK JAR. `dump badging` succeeded and the resource APK SHA-256
was again `a0300c3e154cae50ab1e24247cfb3a8a0d208e8d9a365c1b298aad898759a48d`.
The exact directory was removed and absence checked. This does not supply
Java `.class` stubs to ECJ or establish compatibility beyond this phone and
fixture.

A later, **temporary** worker variant combined the same source-built compiler
with a test-only source-stub route and omitted the SDK JAR asset. The original
`worker/build.gradle.kts`, ARM64 `libaapt2_exec.so`, and debug APK were first
saved under the ignored one-shot replay directory. The ARM64 library input was
then replaced with `aapt2-stripped`, the single Android stub assets source
directory was temporarily removed from the worker Gradle configuration, and
`source scripts/env.sh && ./gradlew :worker:assembleDebug --offline --no-daemon
--max-workers=1` succeeded. ZIP inspection confirmed no
`assets/android-36.jar` entry and exact embedded compiler SHA-256
`8f87b17cc1f57d489802e8c0aadc2b68e14ee0a0d4514081e2134b84ce2ecd00`.
The worker APK SHA-256 was
`69f82b0ec142adb809c30976b3ef2d5895f3f96b4438455abe0b9acf094e432d`;
a byte-identical rebuild is retained at
`toolchains/aapt2-replay-20260927-oneshot/no-sdk-worker-debug.apk`.

On the S23 Ultra, `adb install -r` of that worker succeeded. The existing
worker androidTest mode `sourceStubDefault` ran the exact controller default
Java source (fixture SHA-256
`d68ef3278979e1c169a36bc62868adc04cf08757be8fa2a0f888399791444760`)
through ECJ with source-authored worker-scratch stubs, D8, the candidate AAPT2
against the device framework resources, and the worker's real unsigned-APK
packager. Instrumentation returned `INSTRUMENTATION_CODE: -1`. Its disposable
unsigned APK had manifest, resources and DEX, 7,762 bytes, SHA-256
`e504c30af3f1cc59ce8b38d402d2bf8b90ef1a67addc88d54f34aee11959579d`.
No generated app was signed or installed; the probe removed its scratch.
The temporary worker's installed `base.apk` hash matched its local hash.
Afterward, the exact original Gradle file, binary input and APK were restored,
and `adb install -r` restored the original worker. Its installed `base.apk`
and local output again hash
`f877d6cb78e56a2ae39f0cea8da1d6e6a7a0cdef49c096091c486a85e853b115`.
This proves composition for one fixture, not a distributable or runtime-verified
replacement worker. At that checkpoint the worker still bundled the SDK JAR;
the later production revision below removed it.

This candidate is **not adopted for distribution**. The local compatibility
edits have a tracked patch application script, reviewed IncFS caveat, and
fresh-output replay evidence. Actual linked-source notices are incomplete,
the separate Android API 36
`android.jar` redistribution basis was unresolved for the then-current worker,
and a fixed physical alpha suite had not run. The current worker retains the
earlier AAPT2 binary but no longer bundles that SDK JAR.

## Clean-source ARM64 compile and phone probe — 2026-09-27

A separate checkout of the pinned local wrapper and its 11 submodules was
prepared with the guarded script, then configured with the same NDK r28c,
local dependency sysroot and host `protoc` as the one-shot replay. The final
wrapper configuration retained `ANDROID_BUILD_TOOLS_PATCH_VENDOR=OFF` and
used one Ninja job. The fresh compile/link completed in ignored
`toolchains/aapt2-clean-source-link-20260927/`. Its unstripped executable
SHA-256 is
`969634ab95296a3ea823cdec6b4bf4e46a6f0fda6ec14c54f31dfde52f9b408e`;
the stripped executable is
`ed917a383f890b2cad3eb3be00657f20d4fc6f73ca5bbbabf062d357633374e7`,
and the link map is
`7ecfee60dfc0d30e330b73bd4594fc52921424cfbbb3e7a962defdb0a0961ccc`.
The two link maps each contained the same 398 distinct archive/member labels
after stripping only absolute directory prefixes. `llvm-readelf` reported
AArch64, four `LOAD` segments aligned to `0x4000`, interpreter
`/system/bin/linker64`, and the same five Android system dependencies. The
different build directory produced different ELF bytes; this is a source
rebuild pass, not a bit-for-bit reproducibility claim.

The exact stripped binary was copied into a newly created disposable
`/data/local/tmp/plb-aapt2-clean-source-20260927` directory on the connected
S23 Ultra. It returned version `2.20-android-16.0.0_r4` and completed
`compile --dir` plus `link -I /system/framework/framework-res.apk` for the
fixed replay fixture. `dump badging` showed min SDK 31 and target/compile SDK
36. The uninstalled resource APK SHA-256 was
`a0300c3e154cae50ab1e24247cfb3a8a0d208e8d9a365c1b298aad898759a48d`,
the same as the earlier replay. The phone directory was removed and its
absence checked. No installed app or project data changed in this probe.

After the production worker was changed to use authored source stubs instead
of bundling `android-36.jar`, its ARM64 compiler input was temporarily replaced
with this exact clean-source stripped binary. The resulting disposable worker
APK SHA-256 was
`409b1abe7e6d13a216e730a77d28553fd04f98a40a520f2587051805ffd14115`;
ZIP inspection confirmed the exact ARM64 compiler hash and no SDK JAR asset.
On the S23 Ultra, `productionSnapshot`, real controller-to-worker
`localityProofOnly` and two-version `updateContinuityOnly` all returned
`INSTRUMENTATION_CODE: -1`. The controller's private signed build from the
candidate had SHA-256
`f2380b305415f17ae64578a910e21a1d3269b8ed173c3854d584223fb2bcd2c6`.
No generated app was installed. The original compiler input was restored, the
no-JAR worker rebuilt, and that stable worker was reinstalled with matching
local/device APK SHA-256
`04416cb3f7b464bb8b1f322deef14d00ec97c61e68b92d487fc6ae000a8c2cce`.
The candidate APK is retained under ignored
`toolchains/aapt2-clean-source-link-20260927/worker-composition/`. This
proves the local pipeline composition on one phone; candidate redistribution
and generated-app runtime remain unverified.
