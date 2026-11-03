# Builder installs its own worker on the A33 (2026-11-03)

The builder APK now carries the worker APK of the same build type (`assets/worker.apk`). When the worker is missing or older than the builder, the main screen shows **Install the build worker** or **Update the build worker**. Tapping it installs the carried copy through Android's install prompt. Security is unchanged: the worker is still a separate app with its own UID and no Internet permission, bound only when it is signed with the builder's key.

## Build

- Revision: `ee69bb7` plus the changes in this commit.
- Version: one shared `smallwright.versionCode`/`smallwright.versionName` in `gradle.properties` for both apps. The builder treats an installed worker with a lower version code as outdated.
- `./gradlew :controller:assembleRelease` builds the release worker first and copies it into the builder.
- Release builder `4b2afe4ab0f54e3fb55dce0a463829cac43cd38bd941e2fa3555ef5a0503be74`, 40,103,418 bytes. Its `assets/worker.apk` is byte-identical to `worker-release.apk` (`1993a29b…`). Both are signed with the release key (`8f8190a9…`).
- Host: 771 controller and 91 worker tests pass, including `BuildWorkerTest`; Spotless and lint pass.

## Phone cases (Samsung Galaxy A33 5G, Android 16)

The app's install-unknown-apps permission was already granted from the 0.1.0 test.

| Case | Steps | Result |
|---|---|---|
| Missing worker | Uninstall `dev.smallwright.worker`, update the builder in place with `adb install -r`, open it | **Install the build worker** shown |
| Install | Tap it, then Install; Play Protect: Scan app, "This app looks safe", Install | Worker installed with `installerPackageName=dev.smallwright.controller`, builder connected ("Ready to build"), line gone |
| Build | Project tools, Build from this source | "Your app is ready." |
| Outdated worker | Test builder at version code 2 over worker 1 | **Update the build worker** shown; Android asks "Do you want to update this app?"; worker now at version code 2 |
| Rebuild after update (first try) | Build from this source | **Failed:** Build stayed disabled. Android ends a binding for good when the bound app is updated (`onBindingDied`), and the builder never rebound. |
| Fix | Handle `onBindingDied`: unbind, then connect again | |
| Outdated, after the fix | Test builder at version code 3 over worker 2, update as above | Builder reconnected by itself ("Ready to build"); Build from this source gave "Your app is ready." |

| Final code, fresh install | After switching the asset copy to `FileUtils.copy` (lint: `InputStream.transferTo` needs API 33), uninstall the worker, install version-code-3 builder `f83e0fd2…`, tap Install | Worker installed by the builder, connected, "Your app is ready." Play Protect did not ask again |

The test builders used `-Psmallwright.versionCode=2` and `=3` with version name `0.1.0-update-test` (version-code-3 builder `c47ed79b…`). The A33 now has builder and worker at version code 3. A later release needs version code 4 or higher to install over them, or uninstall them first.

## Not covered

- A phone that has never allowed Smallwright to install apps: the existing "Allow from this source" route, now shared with the worker install, was not run again.
- A worker signed with a different key: covered by `BuildWorkerTest` only.
