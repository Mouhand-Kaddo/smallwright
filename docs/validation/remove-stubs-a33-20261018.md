# Hand-written Android stubs removed, builds checked on the Galaxy A33, 2026-10-18

Ticket #38 of spec #35. The real Android platform file is now the only
platform anything builds or tests against.

## Deleted

- Worker: `SourceStubs`, `ExperimentalPlatformInput`, their tests
  (`SourceStubsAbiTest`, `SourceStubsCompileTest`, `ExperimentalPlatformInputTest`)
  and the probes that only exercised them (`DefaultSourceStubProbe`,
  `SourceStubCounterProbe`, `ExperimentalPlatformProbe`, `Api36ResourceProbe`,
  instrumentation modes `sourceStubCounter`, `sourceStubDefault`,
  `experimentalPlatform`, `api36ResourceOnApi31`).
- Gradle: the `experimentalPlatformJar`, `experimentalSourceHeadersJar` and
  `experimentalPlatformResources` properties, `stageExperimentalPlatform`, the
  `platformProbe` build type and `platformProbeTests`, the Spotless
  `targetExclude` and the `verifyCompilerInputs` input for `SourceStubs`.
- Scripts and pins: `check_stub_abi.py` and its test, the `android_compile_stubs`
  pin in `toolchain/manifest.json` and its check in `verify_compiler_inputs.py`,
  the builders and tests of the two source fragments appended to `SourceStubs`
  (`build_activity_layout_source_api.py`, `build_dialog_button_source_api.py`,
  their tests, `scripts/dialog-button-api`, `ActivityLayoutExtractor.java`) and
  their notices (`toolchain/licenses/activity-layout-source`, `dialog-button-source`).
- Controller: the `queries` entry for the platformProbe worker, and the
  `sourceHeadersNoteOnly` probe mode.

## Switched to the real platform file

- `BuildService.runToolchainProbe` and `packageCounter` compile against the
  worker's verified platform copy (ECJ boot classpath, `aapt2 link -I`) instead
  of stubs and `framework-res.apk`. `PlatformBuildProbe` case `app` now also
  calls `toolchainProbe` and checks its answer.
- `CompileDiagnosticsTest` and `CompileDiagnosticsProbe` compile against the host
  jar and the worker's platform copy. The test that asserted stub classes were
  emitted now asserts that `java/lang/Object.class` is not.
- `ProjectInstallProbe` note journey (renamed `noteOnly`) uses the normal worker.
- Doc lines in `TESTING.md`, `CODE_QUALITY.md`, `licenses-provenance.md`,
  `toolchain/NOTICE.txt` and `licenses/INDEX.txt` no longer describe stubs.

## Host chain

`spotlessApply :controller:testDebugUnitTest :worker:testDebugUnitTest
:protocol:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug
:controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest
:controller:lintDebug`: 368 tests, 0 failures; lint 0 errors.
`scripts/test_verify_compiler_inputs.py`: 7 tests pass.

## Device and installs

Phone: SM-A336E (Galaxy A33), serial `<A33 serial>`, under the phone lock. The
S23 Ultra was not connected. Built signer equals installed signer (`19a7407f...`).
Installed with `adb install -r`, before then after (sha256, installed equals built):

| Package | Before | After |
| --- | --- | --- |
| controller | `4743777b...3bfb` | `bc690abb3626025f6db225bea33051d994bbe0cb224fa8fb00de9651fb949f3f` |
| worker | `178b13ec...5a86` | `c5766c5bc38239013ab23cab255bdbabc0f545570a7a32e71f3730bc0e6bffad` |
| controller test | `30ee6d8a...3e7e` | `22e30f058793f454007f071176065727bdf9069a652cceb7b639131de00b0232` |
| worker test | `78987070...5554` | `de85881f2dfc87d5edc3cc905a12843a51152cf13b51c4b6c4ab4fe23eaacda5` |

## Cases on the phone

`PlatformBuildProbe` on a disposable project in an isolated registry (deleted by
the probe). No model request was made.

| Case | Result |
| --- | --- |
| `controllerMissing` | refused before the worker: `The Android platform files are missing or changed` |
| `wrongInput` | same-size changed and 1 KB jar refused, null refused, Google jar `OK` |
| `app` | built through `LocalBuildCoordinator` and the real worker, 2,337 ms, APK 12,761 B, sha256 `e5cb87d1...a71b`; `toolchainProbe` returned `ECJ valid class and invalid-source diagnostic; D8 classes.dex; unsigned counter APK` |
| Install and launch of that APK | installed hash equals built; cold start 281 ms; `Log.w` stack from the fixture; no crash |
| worker `compileDiagnostics` | passed (ECJ diagnostics against the platform copy, production snapshot) |
| Controller afterwards | opens on the user's project "Main project", untouched |

Evidence: `toolchains/spec35/38/` (local, not committed), with the screenshots
`app-launched.png` and `controller-after.png`.

## Not run

- `workerMissing`: needs a worker with no kept platform copy; this worker has had
  one since #37 (the toolchain probe passing shows the copy is verified).
- S23 Ultra: not connected.
- API 31 emulator `api36ResourceOnApi31`: deleted with the experimental inputs.
