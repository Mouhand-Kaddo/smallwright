# Isolated-process worker probe on the A33 (2026-11-03)

Question: can the build worker run as an `android:isolatedProcess` service inside one APK instead of as a second app?

Answer: not with the current toolchain. The isolated process is a real security boundary, but it has no writable directory and cannot reopen handed-over files by path. AAPT2 only works with file paths, so it cannot build resources there.

## Setup

- Device: Samsung Galaxy A33 5G, Android 16, build `BP2A.250605.031.A3`, user/release-keys.
- Revision: `68df880` plus the probe changes in this commit.
- Build: `./gradlew :worker:assembleDebug :worker:assembleDebugAndroidTest -PisolationProbe=true`. The property adds the debug-only `IsolatedProbeService` (`worker/src/isolationProbe/`) and installs the worker as `dev.smallwright.worker.isolationprobe`, so the installed release apps were untouched.
- Worker probe APK SHA-256 (first run): `e48e75ac5d16a7e17a4da1cd8b8fb1edda520456fbacda0859cd855eb5add7b7`.
- Installed with `adb install -r`; both probe packages were uninstalled afterwards.
- Run: `adb shell am instrument -w -r -e mode isolatedProcess dev.smallwright.worker.isolationprobe.test/dev.smallwright.worker.ConfinementInstrumentation`.

The instrumentation runs in the app's normal UID, writes a private file, opens an empty file for writing and passes that descriptor to the isolated service.

## Results

| Check | Result |
|---|---|
| UID | Isolated UID 99066 (app UID 10372), `Process.isIsolated()` true |
| Network socket | Refused: `socket failed: EACCES` |
| Read the app's private file by path | Refused: file not visible |
| Write `cache`, `no_backup`, `files`, `code_cache`, `/tmp` | All refused: `mkdirs` failed |
| Write through a passed descriptor | Works; the app read back the text |
| Reopen a passed file by `/proc/self/fd/N` | Refused: `EACCES` |
| `memfd_create`, then reopen by `/proc/self/fd/N` | Created, reopen refused: `EACCES` |
| Run packaged AAPT2 `version` directly | Works, exit 0, `2.20-android-16.0.0_r4` |
| Run AAPT2 through the guarded launcher | Works, exit 0 |

## What this means

- The security side passes: separate UID, no network, no access to the app's own files.
- ECJ and D8 are Java and could in principle run on in-memory input and output, writing results back through descriptors.
- AAPT2 reads resource directories, the manifest and the platform JAR by path and writes its outputs by path. With no writable directory and no way to turn a descriptor into a path, it cannot run there unchanged.
- Running AAPT2 in a normal process of the builder would put model-written resource files through native code in the UID that holds the API key. That breaks the isolation rule, so it was not tried.

The two-app layout stays. To make installing easier without changing the security model, the builder could carry the worker APK and offer it through Android's install prompt.
