# Terminal-style controller screen on the Galaxy A33, 2026-10-17

The controller screen moved from programmatic Views to Jetpack Compose in a
terminal style ([ADR 0001](../adr/0001-terminal-controller-ui-in-compose.md)).
This record shows that the harness still drives the real screen on the phone
and that a person can reach every action.

## Device and build

- Phone: Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16
  (SDK 36), arm64-v8a, `ro.kernel.qemu=0`. Read-only preflight ran at 14:18
  local time with `phone-use/scripts/preflight.py`.
- Revision: `c7ac55f` plus the uncommitted Compose change; the commit that
  follows this record holds the exact sources.
- Controller debug APK sha256
  `3ea82d0f56330b39ffdf7d35f57bfd222e7ae0786fa13a5492f7bfe6d28890b8`,
  test APK sha256
  `5b89e8c9ac70ef685d8c9c0604a8eed19810a8ee68157af14581b700ce45421a`, both
  installed over USB through the debug installer (last update 14:18:41). The
  controller's own package, signer and data were kept.
- Host chain: `./gradlew --offline spotlessApply :controller:testDebugUnitTest
  :controller:assembleDebug :controller:assembleDebugAndroidTest
  :controller:lintDebug` passed; 248 unit tests, 0 failures; lint 0 errors.

## Cases

| Case | Inputs | Observation | Result |
| --- | --- | --- | --- |
| Home renders the plan | launch | screenshot: top bar with project name and `ready`, status line, log, "It works, keep it", feedback box, `>` prompt line, "Build it", "Menu" | pass |
| Menu lists actions first | tap Menu | screenshot: actions, chats in this project, projects (31 after the deletion below) with the current rows marked | pass |
| Project tools page | Menu, Project tools | screenshot: source box, outlined "Build from this source", plain rows for the other tools, "Delete this project" in the failed hue | pass |
| Builder key page | Menu, Builder key | screenshot: note, masked key box, outlined "Save key on this phone", "Remove saved key" row | pass |
| Prompt with keyboard | tap the prompt line | screenshot: keyboard opens, prompt line and "Build it" stay above it | pass |
| Suite preview through the screen | `suite.py run a33_records05_20261017_33 records_05 --continue a33_records05_20261017_20` | probe taps "Build it" by accessibility, reads the Send dialog payload, cancels; context `def1a06f…`, 16,734 bytes | pass |
| Suite send through the screen | same run | probe taps "Build it", confirms "Send & generate"; one paid `deepseek/deepseek-v4.1-flash` reply, 10,556 tokens, compile verified at revision 8, APK `dbda08e2…` installed, `READY_FOR_CHECKLIST` | pass |
| Restore through the tools page | `restoreAccepted` with `continueRun a33_records05_20261017_20`, then `install`, `pinBaseline` (run `a33_records05_20261017_34`) | probe opens Menu, Project tools, taps "Build from this source"; revision 9 rebuilt from the accepted revision 7 snapshot `b6a09ad6…` with no model request, APK `3a18754a…` installed | pass |
| Delete a project through the screen | select "A33 live compiler repair fixture network-20261012", Menu, Project tools, "Delete this project", "DELETE PROJECT" | the platform dialog names the project; afterwards 31 project directories remain (32 before) and the screen shows another project | pass |

Raw logs: `toolchains/alpha-goal-20261017/a33_records05_20261017_3{0,1,2,3,4}/`.
Runs `_30` (restore refused: head was the accepted revision), `_31` and `_32`
(preview could not find "Build it") are the failed attempts that led to the
finder fix below. Screenshots stayed on the laptop under `/tmp`.

The records_05 project's head is now revision 9 (the accepted revision 7
content rebuilt); its accepted outcome row `a33_records05_20261017_20` is
unchanged. The regenerated revision 8 was installed by the send case and then
replaced by revision 9; neither was functionally tested or accepted.

## What changed for probes

- Widgets are Kotlin state holders (`TextBox`, `Control`, `Picker`) with the
  Views method names. Probes keep `MainActivity` field names and cast to the
  holders; `Ui.button(activity, label)` finds a control by its label.
- Compose exposes a button's label on a child text node and the click on a
  plain clickable ancestor, with no `android.widget.Button` class. `Ui.find`
  starts at the label and climbs to the nearest clickable node; the shared
  dialog finders fall back to it. Platform dialogs are unchanged.
- The suite chain and `CompilerMeasurementProbe` tap through accessibility.
  Older probes call `Control.performClick`, which runs the action without the
  screen; they compile and are not rerun here. `ActiveWorkerStopProbe` and
  `CompilerMeasurementProbe` are one-shot fixtures and were not rerun.

## Limits

- No accept, repair, feedback or install tap ran through the new screen in
  this record; they share the `Ui.tap` path proven by the preview and send
  cases, and their dialogs are the unchanged platform dialogs.
- Emulator and S23 Ultra are not covered. The S23 Ultra was not connected.
- The approval dialogs are still platform `AlertDialog`s with a dark theme.

## Fix round after independent review

Claude Sonnet 5.5 reviewed commit `eb03974` (no blocker, nine warnings). The
fixes, all on the A33 with controller APK sha256
`407cdde19eea5949608f720cf597c5eec5f58d88a2054aa157cfd9384b178d0c` and test
APK `ddd5a8b85248e91c3ea9260116007fd1b06ddf9c3d9a58f94117e80f8e60009c`:

- `Control.isShown` counts render sites, so a control drawn both on the home
  screen and in the menu stays shown after the menu closes.
- The state word is set at the end of `refreshControls`, after the repair
  control's enabled state it depends on.
- The home screen is not composed while the menu or a page is open. With the
  menu open, the accessibility tree held no "Build it" node.
- Status messages name the buttons as labelled ("Tap Install on this phone to
  test it"); no probe asserted on the old wording.
- The transcript scrolls after the next frame and re-scrolls when the turn
  list changes. Headers say "version", not "rev".
- The builder key field: the content view is `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS`,
  `TextBox.clear` also clears the undo history, and leaving the page empties
  the field.
- The probe failure log skips editable and password nodes and caps each text
  at 80 characters. `Ui.find` prefers an exact text match and climbs to a
  clickable ancestor only for button lookups. The legacy finders fall back
  once, at the root. `Control.performClick` left production code; probes
  call `Ui.click`, and the fake tools control is gone.
- "Build it" is plain text while another primary action shows. Menu actions
  close the menu. Rows and buttons are at least 48 dp tall; the rule colour
  is `#565F89`.

Reruns through the fixed screen, same device and runner:

| Case | Run | Result |
| --- | --- | --- |
| Suite preview (tap "Build it", read the dialog, cancel) | `a33_records05_20261017_35` | pass; context `938866e5…`, 16,753 bytes, revision 9 |
| Restore through the tools page, install, pinBaseline | `a33_records05_20261017_36` | pass; revision 10 from the accepted revision 7 snapshot `b6a09ad6…`, no model request |
| Screens after the fixes (home, menu, tools) | screenshots | pass; one outlined primary, menu hides the home screen |

Run `_35`'s `restoreAccepted` was refused by the probe's one-shot guard
("One-shot run reserved") because the run already held `prepare` pins; that is
the guard working, not a screen fault. Host chain: 248 unit tests, 0
failures, lint 0 errors.

Not rerun: the paid send, accept, repair, feedback and install taps. They use
the same `Ui.tap` path as the preview case.

## Second fix round (GLM review)

GLM 5.3 Flash Free (OpenCode) reviewed `eb03974` independently. Six of its
findings overlapped the first round; the rest were fixed here. Controller APK
sha256 `110c9ef214be11bf492a6bfd15fa477a898f0753fe3ccc7ff3b0b8fd715d0b34`, test
APK `151d544a7d7331a80b6e4cc49ddb089fdac7c3ee57c16ce9a2819786d94bb93e`,
both installed over USB through the debug installer.

- Fourteen probe sites, among them the suite probe's failure classification,
  still cast the `status`, `source`, `modelRequest` and `chatPicker` holders
  to `TextView`, `EditText` or `Spinner`. They compiled and would have thrown
  where they collect evidence. They now cast to `TextBox` and `Picker`; the
  one `Spinner.setSelection` became the notifying `Picker.select`.
- `StatusVisibilityProbe` walked the View tree for `Button` Views, which no
  longer exist. Its three entry points now read the holders and the
  accessibility tree.
- `Ui.find` tries every node with the label and takes the first with a
  clickable ancestor, so a bare title with the same text cannot shadow a row.
- `LocalType` was unused and is gone. The source editor grows to 24 lines.
  The paid note also appears under "Send my feedback".

Reruns on the A33:

| Case | Command | Result |
| --- | --- | --- |
| Ready controls after startup | `am instrument … -e readyControlsOnly true` | pass |
| Status line and Build it on screen | `… -e statusVisibleOnly true` | pass, after the probe learned to wait for the window and to re-read a status that changes while the worker connects |
| Feedback available after restart | `… -e feedbackAvailableOnly true` | pass |
| Suite preview through the screen | `a33_records05_20261017_37` prepare, preview | pass; context `938866e5…` at revision 10 |

Host chain: 248 unit tests, 0 failures, lint 0 errors.

## Third fix round (Sonnet round 2)

Claude Sonnet 5.5 reviewed the first fix commit `806d288`: no blocker, four
warnings, five nits. Fixed here, the last fix round under the review
protocol. Controller APK sha256
`6644ff703af589df5f1f7cc907256ad68467d3924a5a6d35055e968797728498`, test APK
`1bd59e3909661d3a3f677d4f588608502ee9be05ee3f58ea2a7423ec3eac8628`, both
installed over USB through the debug installer.

- The builder key field is also cleared when its page leaves composition,
  not only on Back or Close.
- While the prompt holds words, "Build it" is the outlined action and the
  state action (Install, It works, Fix) is plain text, so the next step is
  always the one outlined.
- The transcript's scroll state lives above the home screen and survives a
  menu round trip. Prompt focus and the keyboard do not; the ADR says so.
- Six probes that still walked the View tree or cast controls to `Button`
  (`WorkerDeathRecoveryProbe`, `StopUiProbe`, `ChatUiProbe`,
  `LiveReadFileProbe`, `ConversationLayoutProbe`, `ReceiptRestartProbe`)
  now read the state holders and the accessibility tree.
- The rule colour is `#636C99` (about 3.4:1 on the background).
  `Ui.openMenu` closes an open page first.

Reruns on the A33:

| Case | Command | Result |
| --- | --- | --- |
| Suite preview through the screen | `a33_records05_20261017_38` prepare, preview | pass; context `938866e5…` at revision 10 |
| Conversation layout (tools collapsed, prompt and Build it visible, tools open and close) | `am instrument … -e conversationLayoutOnly true` | pass |
| Typed prompt | typed "Add a search box", screenshot | "Build it" outlined, "It works, keep it" plain; the text was then deleted |

Host chain: 248 unit tests, 0 failures, lint 0 errors.

Known and not fixed: `Ui.click` on a disabled control returns false silently,
as `performClick` did; there is no runtime test for the key field's autofill
flag, undo history or clearing; the remaining one-shot probes compiled but
were not rerun.

GLM 5.3 Flash Free reviewed the second fix commit `01d78e4` last: no blocker.
It noted two things left open after the three fix rounds the review protocol
allows. First, at least 11 more sites in probes outside the suite chain (a cast
search; GLM listed more under other helpers) still cast a state holder to a
View and would throw when run: `BusyRecoveryProbe` (3), `ReceiptWriteRecoveryProbe` (4), `WorkerDeathRecoveryProbe` (4). Those probes are
compile-only until that swap lands. Second, `StatusVisibilityProbe.run` only
looks the Stop control up by label; the old assertion that Stop was on screen
is gone, so the case covers the status line and Build it only. Both are
listed as the next task in STATUS.

