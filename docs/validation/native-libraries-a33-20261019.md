# Libraries with native code on the Galaxy A33, 2026-10-19

Ticket #48 of spec #35. An Android library (AAR) may now carry prebuilt
native code. Its `jni/<abi>/lib<name>.so` files are packaged as
`lib/<abi>/lib<name>.so`, stored uncompressed and 16 KB-aligned, for the
ABIs of the phone running the build. Nothing is compiled from C.

## Design

- **Which ABIs.** The worker keeps the ABIs in `Build.SUPPORTED_ABIS` of the
  phone it runs on (A33: `arm64-v8a, armeabi-v7a, armeabi`) and drops the
  rest, as an AGP build with those `abiFilters` would. Android installs one
  ABI per app (the first in that list the APK has), so on the A33 the
  `armeabi-v7a` copy is unused weight (1.8 MB for Conscrypt); it was kept to
  follow the spec text "the phone's supported ABIs".
- **No usable ABI.** A library whose native code is only for other ABIs is
  refused at build time: "The Android library org.example has native code
  only for x86, x86_64, which this phone cannot run (it runs arm64-v8a, …);
  choose a library without native code or one built for this phone". The
  model reads it as a build error. A library with some usable and some other
  ABIs keeps the usable ones silently.
- **Untrusted bytes** (`AndroidLibraries`, worker). Existing checks still
  apply to every entry (unsafe names, 50,000 entries, 256 MiB unpacked counted
  while reading). Native entries must match
  `jni/[a-z][a-z0-9_-]{0,31}/lib[A-Za-z0-9_][A-Za-z0-9_.+-]{0,99}\.so`. Any
  other entry whose name ends in `.so` (any case), including `res/raw/libx.so`,
  `jni/libx.so`, nested or upper-case ABI folders and a root `lib/`, refuses
  the library. Other files under `jni/` are ignored. Two libraries holding
  the same `lib/<abi>/<name>` refuse the build. The APK stays capped at
  32 MiB.
- **Only under `lib/<abi>/`** (`BuildService.packageApk`). The native map is
  the only source of `.so` entries. A `.so` among AAPT2's output (the app's
  resources or assets) refuses the build, and library JAR entries ending in
  `.so` are no longer packaged as Java resources.
- **Alignment.** The existing aligned-entry code for `resources.arsc` used a
  `0xCAFE` extra field with a one-byte size, which cannot carry 16 KB of
  padding. It now writes zipalign's `0xD935` field (alignment value, then zero
  padding): 4 for `resources.arsc`, 16,384 for `.so`. The controller signs
  with apksig 8.13.0, whose `ApkSigner` re-aligns stored `.so` entries to
  16,384 by default and honours the `0xD935` value, so the signed APK keeps
  the alignment (checked on the signed APKs below).
- **`extractNativeLibs`.** Not forced. Unset (the platform default, true), the
  installer extracts the primary ABI's `.so` into `nativeLibraryDir`. Set to
  false by the model, the libraries load straight from the APK, which needs
  them stored and page-aligned; both were run below.
  `ArtifactValidator` reads the manifest only (`getPackageArchiveInfo`), so
  native entries do not affect it and needed no change.
- **Controller.** `AndroidArchive.manifest` no longer refuses `jni/*.so`. The
  build contract now says libraries with native code work for this phone's
  processor and the app cannot have C or C++ of its own.

Trade-off: Java's `ZipOutputStream` writes the same extra field into the
central directory, so each `.so` costs up to 16 KB more there (20 KB for
Conscrypt's two libraries).

## Device and build

- Phone: Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16,
  `ro.product.cpu.abilist` `arm64-v8a,armeabi-v7a,armeabi`, USB. The S23
  Ultra was not connected.
- Phone lock held 10:11:33 to 10:24:13. The installed controller
  (`f0e4211d…`), worker (`50b5e624…`) and test APK (`a18ad0f0…`) were another
  session's; mine were installed at 10:11:52 with `adb install -r`, and each
  installed `base.apk` sha256 equalled the built one:
  - controller `1518ae978918974e0b4f9152f6eb1374b380031e6b2f4c91a4a6e40d1a3df1ea`
    (branch after merging #50),
  - worker `b33272acc67db6db205285690ee457ec908791eb0f9306c0696048b4b10858b4`,
  - controller test APK
    `7fd4383c2fe72b339da649304e63169236f159f6aef6cd3369d03e48ee895899`.
- Host chain on the final merge (`spec35/integration` at `792b158`, with #50
  and #52), through `spec35/gradle.sh`: `spotlessApply
  :controller:testDebugUnitTest :worker:testDebugUnitTest
  :controller:assembleDebug :worker:assembleDebug
  :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest
  :controller:lintDebug` exited 0; controller 349 tests and worker 86, 0
  failures; lint 0 errors. The final worker APK is byte-identical to the one
  on the phone; the final controller (`5b467b53…`, adds #52 only) was not
  installed.

## Host tests added or changed

- Worker `LibraryPackagingTest`: **three `.so` files of odd sizes are packaged
  `STORED` under `lib/<abi>/`, byte-for-byte, with data offsets on 16,384
  bytes, read from the local headers as zipalign does**; `resources.arsc`
  stays stored on 4 bytes; `.so` entries in a library JAR are not packaged; a
  `.so` among the resources or assets refuses the build. Mutation check:
  packaging the `.so` with 4-byte alignment fails the test.
- Worker `AndroidLibrariesTest`: native code kept only for the phone's ABIs,
  two libraries merged in path order, a `README.txt` under `jni/` ignored;
  native code only for x86/x86_64 refused naming both; the same
  `lib/arm64-v8a/libx.so` from two libraries refused; eleven misplaced or
  badly named `.so` entries refused.
- Worker `CompileArgumentsTest`: the `0xD935` field ends the local header on
  4 and 16,384 bytes, with its size and alignment value.
- Controller `LibraryResolverTest`: an AAR with `jni/arm64-v8a/*.so` resolves.
  `LibraryRunTest`: the AAR refusal case now uses an AAR without a manifest.
  `ContextFilterTest`: the new contract sentence.
- New probe `NativeLibraryBuildProbe` (`-e nativeLibraryBuild 1` on
  `ArtifactPolicyInstrumentation`): isolated project with
  `org.conscrypt:conscrypt-android:2.7.0`, `extractNativeLibs="false"`, one
  real worker build, no model request.

## Library choice

`org.conscrypt:conscrypt-android:2.7.0` (Maven Central, Apache 2.0, no
dependencies). Its AAR (4,927,898 B, sha256 `86072ce7…4e7f` equal to the
published `.sha256`) holds `jni/{arm64-v8a,armeabi-v7a,x86,x86_64}/libconscrypt_jni.so`;
the arm64 ELF has 16 KB (`0x4000`) LOAD alignment. Its native BoringSSL is
reached through `Conscrypt.newProvider()` with no setup.

## Cases on the phone

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| 1. Probe build | `am instrument -w -e nativeLibraryBuild 1 …ArtifactPolicyInstrumentation` at 10:12:07 | resolve 2,450 ms (Conscrypt downloaded), build 11,833 ms; `native=[lib/arm64-v8a/libconscrypt_jni.so:2754312:stored, lib/armeabi-v7a/libconscrypt_jni.so:1776996:stored]` (x86 and x86_64 dropped); APK 4,821,646 B, sha256 `39375b4a…71de` | pass |
| 2. Probe APK alignment | signed APK pulled with `run-as … cat`; SDK 36 `zipalign -c -P 16 -v 4`; `unzip -v` | arm64 `.so` data at 245,760 (15 × 16,384), armeabi-v7a at 3,014,656 (184 × 16,384), both `Stored`, `resources.arsc` at 1,148 stored; "Verification successful" | pass |
| 3. Probe app runs, loading from the APK | `run-as … cat app-v1.apk \| pm install -r --pkg … -S 4821646` (USB debug installer exception, not production consent); launch | installed hash equal; `primaryCpuAbi=arm64-v8a`; `lib/arm64` empty (nothing extracted); screen and logcat `NativeProbeApp`: "Conscrypt 2.7.0 native code loaded: true", SHA-256 of `phone-local` by BoringSSL `eef0d07b…7ca4` (equal to `sha256sum` on the laptop), "TLS context: Conscrypt TLSv1.3", "Code mapped from base.apk: 1" (an `r-xp` mapping of `base.apk`) | pass |
| 4. Model run | new disposable project "Native48" (`appe6faf402035b499cac0340c7e7b72e07`); DeepSeek V4.1 Flash; Send at 10:19:55: "Build an app that uses the library org.conscrypt:conscrypt-android, which has native code. On start, create a provider with Conscrypt.newProvider, compute the SHA-256 of the text phone-local with it, and show the hex, Conscrypt.isAvailable and the Conscrypt version on screen. Add the library first, then build it." | Listed files, Read ×3, **Added library org.conscrypt:conscrypt-android 2.7.0**, Wrote MainActivity.java, **Built on this phone in 3.1 s** (library dex kept from case 1), summary, "Your app is ready." with Install; ended 10:20:21 | pass |
| 5. Model-built app | receipt: version 1, revision 1, 4,821,591 B, sha256 `55e70167…93ec`; pulled; `zipalign -c -P 16`; debug install as in case 3; launch | same two `.so` entries stored at 245,760 and 3,014,656, verification successful; installed hash equal; manifest leaves `extractNativeLibs` unset, so the installer extracted `lib/arm64/libconscrypt_jni.so` (2,754,312 B); screen: "Conscrypt.isAvailable: true", "Provider name: Conscrypt", "Provider version: 1.0", SHA-256 `eef0d07b…7ca4`; no `AndroidRuntime` error | pass |
| 6. Clean up | long press on the title → Delete this project → Delete project for "Native48"; menu → "Main project"; `run-as … rm -r files/native-build-*`; `adb uninstall` the two generated apps I installed | Native48 gone (`files/projects`: #44's project, the user's, `counter-alpha`); after the delete the app showed #44's "Run interruption test 44", which I only viewed before selecting the user's project; the user's chat unchanged; probe root and both apps removed | pass |

Requests and cost (logcat `SmallwrightRun`): one run, 5 requests,
0.0010918488 USD. The probe made no model requests.

Incident: creating the project, the New project dialog closed on its own
about two seconds after its field took focus (with or without reading the
hierarchy), and one keystroke, "e", landed in the user's message box. It was
deleted at once and nothing was sent; the box showed its placeholder again.
The dialog was then driven with focus, typing and Create inside one
`adb shell` call. The dialog closing itself was not investigated (not this
ticket).

Screenshots in `toolchains/spec35/48/` (local, not committed):
`01-probe-app-native-output.png` (case 3), `10-disposable-project.png`,
`11-run-working.png`, `14-run-steps-top.png`, `12-run-ended.png` (case 4),
`13-model-app-running.png` (case 5), `20-back-to-users-project.png` (case 6).
Raw: `probe1.txt`, `probe-app-v1.apk`, `probe-zipalign.txt`,
`probe-unzip-v.txt`, `probe-app-logcat.txt`, `model-app-v1.apk`,
`model-zipalign.txt`, `model-unzip-v.txt`, `model-MainActivity.java`.

## Not run, and notes

- Not on a phone: the refusals (other ABIs only, duplicate libraries,
  misplaced `.so`, `.so` in resources or assets); host tests only. The S23
  Ultra and any 16 KB page device: not available. The A33 has 4 KB pages, so
  16 KB alignment is checked with zipalign, not by a 16 KB kernel.
- The unsigned worker APK was not pulled from the phone (worker scratch is
  private); its alignment is covered by the host test, and the signed APKs
  were checked.
- A library shipping only `armeabi-v7a` next to another with `arm64-v8a`
  would install as 64-bit and fail to load the 32-bit one at run time, as in
  an AGP build; not detected.
- After the session the worker's checked library copies are 21 MB and its
  dex cache 15 MB (earlier tickets included).
