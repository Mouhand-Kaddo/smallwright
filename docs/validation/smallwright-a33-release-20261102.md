# A33: release-signed Smallwright (2026-11-02)

Run under the app's earlier name, Palmwright (`dev.palmwright.*`). The app was
renamed Smallwright the same day; names below follow the new name.

Date: 2026-11-02 Dubai. Device: Galaxy A33 5G (SM-A336E), Android 16/API 36,
USB debug. Commit `5879c28`. Release key created with
`scripts/create-signing.sh release`; certificate `CN=Smallwright, O=Smallwright`,
SHA-256 `0b6999be…f09193bd`, RSA 3072, valid to 2054.

Built with `./gradlew :worker:assembleRelease :controller:assembleRelease`:

| APK | Size | SHA-256 | Native code |
| --- | --- | --- | --- |
| `worker/build/outputs/apk/release/worker-release.apk` | 18.9 MB | `e479d7e19615b594…` | arm64-v8a |
| `controller/build/outputs/apk/release/controller-release.apk` | 27.5 MB | `8628036989f8ac07…` | all four ABIs (one 10 KB AndroidX library) |

`apksigner verify` passed for both, with the same signer. The test-key
`dev.smallwright.*` apps were uninstalled with the user's approval, deleting
their data, and the release APKs were installed with `adb install`, worker
first. The UI was driven with `input` taps and `uiautomator dump`.

| Case | Expected | Actual |
| --- | --- | --- |
| Install | both install, not debuggable, the builder holds the worker permission | pass: `pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`, `BIND_BUILD_WORKER: granted=true` |
| Onboarding | works without a key | pass: key skipped, DeepSeek V4.1 Flash picked, SDK terms accepted, "The Android platform files are on this phone." |
| Build in the release worker | starter source builds | pass on a new project "Release check": "Your app is ready." |
| Install through Android | the person confirms Android's dialog | pass: install permission allowed in Settings, "Install" tapped, Play Protect "Scan app" (user's choice; uploads the starter app to Google), "This app looks safe", Install, "Installation completed." The app opened showing "Counter: 0" |
| Old generated app | a new install has new project keys, so it must not overwrite an app signed by the old ones | refused as designed: building the existing Counter alpha project stopped at signing with `SecurityException: Installed app signer is incompatible with this project signer` (the counter app from the test-key install is still on the phone). The screen only said "Local build failed. No new APK is ready." |

## Smallwright release key

After the rename, the Palmwright release key was moved to
`signing/unused-palmwright/` and a new one created: `CN=Smallwright,
O=Smallwright`, SHA-256 `8f8190a9…363ee4`. Worker `8fc3de5bf398314b…` and
controller `06315dddb525a3a2…` were installed beside the Palmwright apps.

| Case | Expected | Actual |
| --- | --- | --- |
| Install | not debuggable, worker permission held | pass: `dev.smallwright.permission.BIND_BUILD_WORKER: granted=true` |
| Onboarding | works without a key | pass: platform files downloaded |
| Build and install | starter Counter alpha builds and installs through Android | pass: "Your app is ready.", install permission allowed, Play Protect scan "This app looks safe", "Installation completed."; `dev.smallwright.generated.counter` opened, Increment showed "Counter: 1" |

## Not covered

- Model requests with the release build.
- The screen message for the signer refusal does not say why or what to do.

Restored: `stay_on_while_plugged_in` back to 0. Left on the phone: the
Smallwright release apps with Counter alpha and its counter app, the Palmwright release
apps with the Release check and Counter alpha projects, the starter app
`dev.smallwright.generated.app79e8…`, the older counter app, and Smallwright's
install permission.
