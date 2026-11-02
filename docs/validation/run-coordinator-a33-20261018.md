# The run moves out of the screen (#40) on the Galaxy A33, 2026-10-18

Ticket #40 of spec #35 is a prefactor with no user-visible change. The Send
run, its automatic compile fixes, manual and recovered builds, version codes
and Stop move from `MainActivity` into `RunCoordinator`. The screen observes
it through `RunCoordinator.Listener`.

## Device and build

- Phone: Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16,
  connected over USB. The S23 Ultra was not connected.
- Built from the merge of `spec35/t40-run-module` with `spec35/integration`
  at `ce254d4`.
- Controller debug APK sha256
  `36cf192aceb0f6041a1f0c3c418bc54b6ba3ceeae17714afa6eea721e594e134`. It
  replaced the installed
  `b9acdf8023e05e13372a8294bc982a58ab2be037eab25e0484e08969a6b88283`.
- Controller test APK sha256
  `5fd96f453e259553c155ab6b086b6016ea68e6a8049e838d9c8c75dfa2f1e97f`. It
  replaced the installed
  `51b4830f85055419b8c9dd9f59eba5066ca5337152d761133dc1c0a2b961c050`.
- Both were installed in place with `adb install -r` at 18:00. Afterwards
  both installed `base.apk` hashes equalled the built ones. The update kept
  the signer and the project data.
- The worker was not touched. The installed worker is
  `f2ec02255775bbc7091f7dc2885d9ddb7ce4e88193bf09ad72b865e19c37353f`, and
  this branch builds
  `5da3009812bf4d6808ddbacd1662d5dab58e4e905f22d2f556750017a207d7b6`. This
  ticket does not change worker sources.
- Host chain: the spec35 `gradle.sh` command with `spotlessApply`, both unit
  test tasks, both `assembleDebug` tasks, `:controller:assembleDebugAndroidTest`
  and `:controller:lintDebug`. It passed on the merged tree:
  - Controller: 291 tests, 0 failures. This includes the new `RunStepsTest`
    and `GenerationFailureTest.aRunThatEndsWithoutAnAppSaysWhy`.
  - Worker: 66 tests, 0 failures.
  - Lint: 0 errors.

## Cases on the phone

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| Home after update | launch `MainActivity` | "Main project" is open with its chat, its builder steps and "It works, keep it"; nothing else on screen changed | pass |
| Stop before a queued build | `-e stopBeforeBuildOnly true` (`StopUiProbe`, isolated probe project) | "Stop: queued build left source untouched and offered no APK", code -1 | pass |
| Leaving the app stops a queued build | `-e backgroundStopsBuildOnly true` (`StopUiProbe`, isolated probe project) | "Background: queued build stopped without changing source", code -1 | pass |
| Home after probes | launch `MainActivity` | same screen as before; saved `active-project` is still the user's project (`app511e…2267`) | pass |
| Manual build on a new disposable project | Menu, New project, "t40 disposable", Create | refused: "Could not create project: Project limit reached" (32 projects on the phone); nothing created, the user's project stayed selected | not run |

Screenshots and probe output are in
`toolchains/spec35/40/` (local, not committed):

- `01-home-after-install.png`
- `02-home-after-probes.png`
- `03-menu.png`
- `04-new-project-dialog.png`
- `05-disposable-created.png`, which shows the refusal
- `probe-*.txt`
- `built-sha256.txt` and `installed-sha256.txt`

## Not run on a phone

- **A real compile from project tools.** The project registry is at its
  limit. The other projects hold earlier probes' retained evidence, so none
  was built in or deleted. `StopUiProbe` exercises the moved build path up to
  the worker call, and Stop, but it does not compile.
- **A Send run, an automatic fix, and Stop during a model request.** These
  need model requests, which this ticket excludes. They are covered on the
  host by the unchanged `GenerationRunTest` and the new tests only. The
  probes that drive them (`ProviderCancellationProbe`, `PhoneAlphaCounterProbe`
  and the repair probes) compile against the moved fields but were not run.
- **A recovered build after a restart.** No saved build was waiting on the
  phone.
