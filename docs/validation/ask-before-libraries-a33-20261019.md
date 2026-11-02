# Ask before adding libraries on the Galaxy A33, 2026-10-19

Ticket #50. A Settings switch, "Ask before adding libraries" (off by default),
turns `add_library` into a question in the chat. The run pauses at the call,
like a permission prompt, and nothing is downloaded until the person says yes.
Decisions: [ADR 0003](../adr/0003-a-run-works-in-steps-until-the-model-is-done-with-no-default-limits.md),
[ADR 0004](../adr/0004-generated-apps-are-ordinary-android-apps.md).

## Design

- The gate sits in `LibraryTools.add`, after the library's name and exact
  version are known and the "already in the project" check, and before the
  first library file is resolved or downloaded. The version of a bare
  `group:artifact` comes from the repository's `maven-metadata.xml`, a small
  index file, not a library file. Dependencies are not resolved to name them:
  the question says "It may bring in other libraries." and the step after yes
  lists what came along.
- `LibraryQuestion` (new) is the only addition to the run: it shows an `ASKING`
  builder step, then holds the run's thread until `answer(boolean)`. No time
  limit. It checks every 200 ms whether the run is still active, so Stop ends
  the wait and the run ends as Stopped.
- Yes: the step becomes "Adding library ..." and the existing add runs. No: the
  step becomes "Did not add library ... / You chose not to." and the model gets
  a tool result saying the person declined, nothing was downloaded and not to
  add it again. The run continues.
- UI: the chat shows the question with two buttons, "Add" and "Don't add", only
  while the last live step is the question. A screen that comes back (opened
  chat, rotated, relaunched into the held run) shows it from the live steps. A
  question saved with an unfinished run is stored as a failed "Stopped" step
  with no buttons.
- Notification: `RunNotice.Kind.ASKING` reads "Needs your input" with the
  question, keeps the foreground service and the Stop action (so the process
  stays alive while the run waits) and alerts once. It goes back to "Working"
  with the next step. While the chat is on screen the notice stays "Working"
  with the question text, and becomes "Needs your input" when the person leaves.
- Setting: `RunSettings.asksBeforeLibraries` in the `run-limits` preferences,
  read once per run in `RunCoordinator.runSteps`. No `RunLimits` field.
- Code: `LibraryQuestion`, about 12 lines in `LibraryTools`, a few lines each in
  `RunCoordinator`, `RunNotices`, `RunNotice`, `RunService`, `RunSteps`,
  `MainActivity`, `UiState`, `Labels`, `Chat.kt`, `ControllerScreen.kt`.

## Device and build

- Phone: Samsung SM_A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16, USB.
  The S23 Ultra was not connected. Held the phone lock for the session.
- Tree: branch `spec35/t50-ask-libraries` merged with `spec35/integration` at
  82cb17f (#47 and #51 included).
- Installed 2026-10-19 09:4x with `adb install -r` (debug installer, data kept).
  Installed `base.apk` before:
  `6fe39d1f245c6b5879307b7ff52db250a3f83156c016772d6ad5d61c20d76aaf` (another
  implementer's build). Built and installed after:
  `b6e8a684f618251bccf7e44efd77cfdb550c5b95ffa098aaf7b216610ac11362`. The two
  hashes match.
- Host chain (`gradle.sh -q spotlessApply :controller:testDebugUnitTest
  :worker:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug
  :controller:assembleDebugAndroidTest :controller:lintDebug`) on the merged
  tree: passed; controller 344 tests, worker 81, 425 in all, 0 failures,
  0 skipped; lint 0 errors.

## Host tests

| Case | Result |
| --- | --- |
| With asking on, `add_library` shows "Add the library Gson (com.google.code.gson:gson 2.14.0)?" with "It may bring in other libraries.", the run stays blocked for over 600 ms, and the fixture server saw no `.jar` or `.pom` request and `libraries.txt` does not exist; after yes the library is downloaded, listed and the step reads "Added library ..." | pass |
| No: nothing is downloaded, `libraries.txt` is not written, the model's tool result says the person chose not to add it, the step reads "Did not add library ...", the run goes on and ends with the model's answer | pass |
| Stop while waiting ends the run with "Run stopped", journal CANCELLED, nothing downloaded | pass |
| A library already in the project at that version asks nothing | pass |
| Notices: a question shows "Needs your input" while the run stays stoppable; the next step returns to "Working"; on the chat on screen it shows as "Working" until the person leaves | pass |
| An unanswered question is saved as a failed "Stopped" step; `ASKING` survives the step-log round trip as failed | pass |

(`LibraryRunTest` 4 new, `RunNoticesTest` 2 new, `RunStepsTest` 1 new,
`StepLogTest` extended.)

## Cases on the phone

Disposable project "Ask library test 50", model `deepseek/deepseek-v4.1-flash`
(approved), key in the app (never read). Screenshots are under
`toolchains/spec35/50/` (local, not
committed); each was opened and checked.

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| Setting, off by default | menu, Settings, scroll | `[ ]` Ask before adding libraries under Stop when stuck, with "The builder asks in the chat before it downloads a library."; `01-settings-switch-off.png` | pass |
| Switch on | tap the row | `[x]`; `02-settings-switch-on.png` | pass |
| The question | "Make a very small app with one button that shows a name and age as pretty JSON. Use the Gson library. Build it.", Send | after listing and reading the project: "Add the library Gson (com.google.code.gson:gson 2.14.0)?", "It may bring in other libraries.", buttons Add and Don't add, Stop in place of Send; `03-question-in-chat.png` | pass |
| Notification while waiting | Home, open the shade | "Needs your input", Ask libr..., "Add the library Gson (com.google.code.gson:gson 2.14...."; `04-needs-your-input-notification.png`. Reopened the app: question and buttons still there | pass |
| Add | tap Add | "Added library com.google.code.gson:gson 2.14.0 / Also com.google.errorprone:error_prone_annotations 2.48.0", then "Thinking..."; `05-after-add-run-continues.png`. The notice went back to working | pass |
| Run finishes | wait | built; "Your app is ready." with Install on this phone; `06-run-finished.png` | pass |

Model requests: 5 paid requests, reported cost 0.0010762808 USD (the app's
`SmallwrightRun` log line). No other model requests.

Cleanup: the disposable project was deleted through the project tools (long
press on the title, Delete this project); "Main project" was selected
again and not changed; the Ask before adding libraries switch was turned off
again (`shared_prefs/run-limits.xml` shows `ask-before-libraries` false, and
the other limits unchanged). The leftover project "Run interruption test 44"
was not touched.

## Notes

- Gson 2.14.0 was already in the phone's library cache from an earlier test
  (files dated 2026-10-18 22:14), so the phone cannot show that nothing was
  downloaded before Add; the host test with a fixture server shows it.
- The dialog for a new project closes when the screen hierarchy is read in
  between, so the new-project and delete-project dialogs were driven with
  fixed taps after a screenshot.

## Not run

- "Don't add" and Stop while waiting on the phone: the ticket asked for one
  run; both are covered by host tests.
- The question while the screen was off or the app was swiped away: only Home
  was tried.
- A bare `group:artifact` against a library not yet cached on the phone.
- The S23 Ultra: not connected.
