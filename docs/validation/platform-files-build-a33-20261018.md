# Builds use the Android platform files on the Galaxy A33, 2026-10-18

Ticket #37 of spec #35. The worker compiles against Google's API 36
`android.jar` (ECJ boot classpath, AAPT2 `-I`, D8 library input) instead of the
hand-written stubs. The build contract no longer lists an API allowlist.

## Transfer

- Before each build, `LocalBuildCoordinator` takes `PlatformFiles.verifiedJar()`
  (pinned size and SHA-256 rechecked) and hands it to the worker as a read-only
  descriptor through the new `IBuildService.usePlatform`.
- The worker hashes the descriptor under the build admission and a 60 s deadline,
  capped at the pinned size, and refuses anything else. Only when its own copy
  (`no_backup/platform/<sha256>.jar`) is missing or changed does it write a new
  one. So 27 MB is written once, not per build.
- Every build rechecks the worker's copy (size and SHA-256) and refuses with "The
  compiler does not have the Android platform files" when it is absent. There is
  no fallback to the stubs.
- Google's `android.jar` has no `java.lang.invoke.LambdaMetafactory`, so ECJ
  rejected every lambda. `LambdaBootstrap` writes a small declaration of it
  (our own source) next to the app sources. D8 rewrites all uses, and the class
  never reaches the APK (the host test checks the dex).

## Device and build

- Phone: SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16, USB, under
  the phone lock. The S23 Ultra was not connected.
- Signer of the built worker and controller equals the installed one
  (`19a7407f…bd25`).
- Installed with `adb install -r`, before then after (sha256):
  controller `11927bb6…9e3c` to `4743777b931439232eeccbb1b8f8f5dc26dd7dea9cb1003d2da5246899f73bfb`;
  worker `f2ec0225…353f` to `178b13ec544de5da62ad2fd9fce5dbfafd93513475f8ab03246ba87420b65a86`;
  test APK `180a5841…52c2` to `30ee6d8aeacb624591d2f22027b4909cf60ba3e9280628aa5a5de0c891933e7e`.
  Installed hashes equal the built ones.
- Worker appId 10318, controller appId 10319. The worker requests no
  permissions, so it still has no Internet.
- Host chain (`spotlessApply :controller:testDebugUnitTest :worker:testDebugUnitTest
  :protocol:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug
  :controller:assembleDebugAndroidTest :controller:lintDebug`): 393 tests,
  0 failures; lint 0 errors.

## Host tests

- `PlatformCompileTest`: the fixture `MainActivity` (InputType flags,
  `TextView.setMinLines`, `Log.w` with a Throwable, a lambda and a method
  reference) compiles with the worker's ECJ arguments against
  `toolchain/input/android/android-36.jar`. D8 with the jar as its library
  produces `classes.dex` holding both synthetic lambda classes and no
  `LambdaMetafactory`.
- `PlatformInputTest` (real jar): the Google jar is kept byte for byte. Same-size
  changed bytes, a longer file and a shorter file are refused, and so is a wrong
  file while the right one is kept. A missing copy is refused. A changed copy is
  refused, then replaced by the next good input.
- `ContextFilterTest`: the contract says any public Android API 36 platform API
  may be used and no longer holds "Only these APIs compile" or "Not available:".

## Cases on the phone

Probe: `PlatformBuildProbe` (instrumentation argument
`platformBuild=workerMissing|controllerMissing|wrongInput|app`). It uses a
disposable project in an isolated registry under the controller's files
directory, which it then deletes. The user's "Main project" project and its
selection were never touched, and the controller still opens on it afterwards.
No model request was made.

| Case | Result |
| --- | --- |
| `workerMissing`: first build on the new worker, called directly with no platform input | refused: `ERROR: The compiler does not have the Android platform files`; no APK bytes |
| `controllerMissing`: controller whose platform directory is empty | refused before the worker is asked: `The Android platform files are missing or changed`; no APK |
| `wrongInput`, before any copy was kept | same-size changed jar and 1 KB jar: `ERROR: The Android platform files given to the compiler do not match the pinned API 36 file`; no descriptor: `ERROR: The Android platform files were not given to the compiler`; Google jar: `OK` |
| `wrongInput`, again with the verified copy kept | same four answers |
| `app`: the fixture built through `LocalBuildCoordinator` and the real worker | built in 1.84 s (the platform checks included), APK 12,762 B, sha256 `da102587…49bc` |
| Install with the debug installer (`pm install -r` from the controller's private copy after comparing hashes) | installed hash equals the built one |
| Launch | cold start 273 ms; screen shows "Built with the Android platform files"; logcat shows `Log.w` with the IllegalStateException stack; no crash |
| Type "hello phone", tap Count letters (lambda) | "Letters: 11" |
| Tap Clear (method reference) | "Cleared", the note is empty |

Evidence: `toolchains/spec35/37/` (local, not committed). It holds the raw
outputs `00-before.txt` to `04-after.txt` and the screenshots `app-launched.png`,
`app-count-letters.png`, `app-cleared.png` and `controller-after.png`. The
installed throwaway package is
`dev.smallwright.generated.app703b7e82c66d4dc9b0c98d0f3173fefe`.

## Not run, and notes

- Not run on the S23 Ultra, because it was not connected.
- No Send through the chat: no model request was approved for this ticket. The
  manual Build and the run both go through `LocalBuildCoordinator`, which the
  probe drives.
- The fixture's first line sits under the status bar, because the app draws
  edge to edge at target API 36. This is about the app's layout, not the build.
- The stubs, `ExperimentalPlatformInput` and the toolchain probe's stub compile
  are still in the code, but normal builds no longer use them (#38 deletes
  them).
