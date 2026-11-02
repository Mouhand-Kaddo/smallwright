# Android platform files download on the Galaxy A33, 2026-10-18

Ticket #36 of spec #35. [ADR 0002](../adr/0002-android-platform-files-downloaded-from-google.md)
decides the route; this records what ran.

## Pinned values

- Package `platform-36_r02`, `https://dl.google.com/android/repository/platform-36_r02.zip`.
- Size 65,878,410 bytes. SHA-1 `2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576` (equals Google's published value).
- SHA-256, computed here on 2026-10-18 from one download into an empty `/tmp` directory:
  `37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e`.
- `android-36/android.jar`: 27,768,026 bytes, SHA-256
  `d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`.
- Terms: the text of https://developer.android.com/studio/terms, fetched 2026-10-18, page dated
  April 28, 2026, shipped as `controller/src/main/assets/android-sdk-terms.txt`. The page text was
  converted from HTML to plain text; sections 1 to 14 are all present.

## Where the file lives

`<controller no_backup dir>/platform/android-36/android.jar`, next to
`platform/acceptance.properties` (revision, acceptedOn, termsDate). #37 reads it through
`PlatformFiles.verifiedJar()`, which rechecks the pinned size and SHA-256 and throws if the file is
missing or changed. `isPresent()` is the cheap size check.

## Device and build

- Phone: Samsung Galaxy A33 `<A33 serial>`, Android 16, USB, phone lock held. The S23 was not connected.
- Installed with `adb install -r`. Before: installed `base.apk` sha256 `af4b3a2a...81d3a`
  (an earlier build of this ticket), then `e023a9d2...2177`, and at the start
  `c8567843e97dc703ad0a124d0d40caf3278dc4155d85432bbfdee5861a8f8fad`. Final build, hash equal on
  the phone and on the laptop: `238de0935e82d82189b36a09c4984507cad3e4c6cf3c764ad0da378ed84ac7ae`.
- Wi-Fi (5 GHz, unmetered). No model requests were made.

## Cases on the phone

Screenshots are in `toolchains/spec35/36/` (local, not committed); each was opened and checked.

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| Existing user after update | launch the updated app | step 4 of 4 opens by itself: what and why (66 MB, Google), Google's license text, Accept and download, Not now (`01-step-offer.png`) | pass |
| Not now | tap Not now | home screen of the user's project (`02`); relaunch does not show the step again (`03`) | pass |
| Menu | open the menu | "Get Android platform files" row listed (`04`); opens the step (`05`) | pass |
| Download | tap Accept and download | bar, percent and MB move, for example 66% / 43.9 of 65.9 MB (`06-progress-*.png`); button reads Downloading... | pass |
| Done | download ends | "The Android platform files are on this phone." and Done (`07`) | pass |
| Kept | read the file in the app's private storage (`run-as`) | `android.jar` is 27,768,026 bytes, SHA-256 equals the pinned value; `acceptance.properties` holds `revision=platform-36_r02`, `acceptedOn=2026-10-18`, the terms date | pass |
| Restart and menu | force-stop, relaunch, open the menu | no step; the Get row is gone (`08`) | pass |
| User's project | compare file hashes before and after | files of "Main project" (project and chat): 44 files, tree hash `dec5f7fa...a73be` before and after; all 729 project and chat files: `4579b974...0ee41` before and after; project and chat shown unchanged (`09`) | pass |

## Host tests

`PlatformFilesTest` (11 cases) runs a socket server bound to 127.0.0.1: success with extraction and
progress; truncated (error, partial file kept); oversized, announced and unannounced (refused, discarded);
wrong checksum (refused, discarded); jar differing from the pinned one (refused, not kept); interrupted
then resumed with a Range request; server ignoring Range (clean restart); leftover larger than the
package (discarded); acceptance recorded once; changed or truncated kept jar not present and not handed
on; shipped terms carry the recorded date. `DownloadProgressTest` (2 cases) covers the bar.
Chain: `spotlessApply :controller:testDebugUnitTest :worker:testDebugUnitTest :controller:assembleDebug
:worker:assembleDebug :controller:assembleDebugAndroidTest :controller:lintDebug` passed; test XML
totals: 353 tests, 0 failures, 0 errors, 0 skipped (controller and worker); lint 0 errors. The rebuilt controller APK has the installed hash.

## Not run, and why

- Send with the file absent (the chat explanation and the "Get Android platform files" line under the
  title). It needs a model request that asks for a build; the ticket said no model requests. The gate is
  in `MainActivity` (a source replacement proposed while `PlatformFiles.isPresent()` is false ends the
  run as a chat answer) and has no host test. Compiles and lints only.
- Failure and retry on the phone. Turning Wi-Fi off changes phone settings, which is forbidden.
  Covered on the host only.
- Fresh first setup (steps 1 to 3 then the new step): clearing app data is forbidden. The existing-user
  path ran for real; the first-run path is the same screen after Pick a model.
- Landscape and the S23 Ultra: not run.
- Wiring into the worker: #37.
