# Settings page and run limits on the Galaxy A33, 2026-10-19

Ticket #49. A Settings page from the menu holds the OpenRouter key and three
run limits, all off by default: most steps, most to spend, and Stop when stuck.
It also says whether run notifications can show, with a button to Android's
notification settings when they cannot. A run that reaches a limit ends with a
builder message naming it, and the run notification ends as "needs your input".
Decisions: [ADR 0003](../adr/0003-a-run-works-in-steps-until-the-model-is-done-with-no-default-limits.md).

## Design

- One step is one model request with the actions it asks for. A compaction
  summary is not a step, but its cost counts toward the spending limit, and the
  limit is checked again after a summary, before the next step.
- A limit is checked before each model request. The reply that reaches the
  limit still runs its actions, so no work is lost; the run then ends, saves
  its changes as one revision and says so. The next Send starts a normal run.
- Spending: the run stops once the cost OpenRouter reported (validated usage)
  reaches the limit. One request may overshoot it a little.
- Stop when stuck: the same build error counts as the same when the same files
  have the same compiler problems, whatever the line numbers. The fourth failed
  build in a row with that error ends the run, after three fixes. A good build
  or a different error starts the count again.
- Limits are read when the run starts and kept in the app's private
  preferences (`run-limits`). Leaving Settings keeps limits that are valid;
  Save limits shows what was saved or what is wrong.
- Code: `RunLimits` (values, parsing, the message, `Watch`), `RunSettings`
  (storage), about 20 lines in `StepRun`, one line in `RunCoordinator`
  (`notices.answered` for a limit-ended run even when a build succeeded).

## Device and build

- Phone: Samsung SM_A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16,
  USB. The S23 Ultra was not connected. Held the phone lock for the session.
- First install (branch at fb2ae4f, before merging #43), 2026-10-19 08:40:42:
  controller debug APK sha256
  `9b1e8fd087cd7a327c7966527dee406daf33e0a89a1cf5b2ca25494b41458492`. The
  installed base APK before it was another build,
  `5177211bdd484441835109e9532794677cb1c693736abad478e7c236695ae911`;
  after, the installed hash equalled the built one. Installed with
  `adb install -r` (debug installer, data kept).
- Final install (merged with integration 4584d01), 08:46:59: controller APK
  `2746a9fa97ab07b257d6c70f6fc8c4aad655294fa5376a82bbb5cdd1d2f683d8`,
  androidTest APK
  `a776cfd27e1e882f47f4a841b3da7d6e1cbdb5fb01ffca4062acb4ede2873306` (not
  installed). Installed hash equalled the built one.
- Host chain on the final tree (`gradle.sh -q spotlessApply
  :controller:testDebugUnitTest :worker:testDebugUnitTest
  :controller:assembleDebug :worker:assembleDebug
  :controller:assembleDebugAndroidTest :controller:lintDebug`): passed;
  390 tests, 0 failures, 0 skipped; lint 0 errors. New tests: `RunLimitsTest`
  (4) and `StepRunLimitsTest` (9).

## Host tests

| Case | Result |
| --- | --- |
| All limits off by default: `RunLimits.OFF` has no step, spending or stuck limit; a five-step run finishes with the model's own answer | pass |
| Step limit 2 ends the run after the second request, with "I stopped after 2 steps, the step limit you set in Settings." plus the saved-changes note; the second request's edit is committed; the next run with no limit answers normally | pass |
| Step limit 1 names "1 step"; an answer on the last step is not a limit | pass |
| Spending limit $0.0002 ends the run after the second request, naming $0.0002 and the limit | pass |
| A summary that reaches the spending limit ends the run before the next step | pass |
| A summary is not a step: with a limit of 2 steps and one summary, the run makes two steps and one summary | pass |
| Stop when stuck: four failed builds with the same error (different lines) end the run on the fourth; three do not | pass |
| A good build between failures restarts the count (3 failures, 1 good, 3 failures: no stop) | pass |
| Stop when stuck off: five identical failures never stop the run | pass |
| Parsing: empty means no limit; steps 1 to 10000; dollars above 0 such as `0.50` or `$2`; refusals; money shown as cents or four decimals | pass |

## Cases on the phone

Disposable project `limit49test`, model `deepseek/deepseek-v4.1-flash`, key in
the app (never read). Screenshots are under
`toolchains/spec35/49/` (local, not
committed). Each was opened and checked.

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| Settings, limits off | menu, Settings | page titled settings; OpenRouter key section with the credit-limit note; both limit fields show "No limit"; Stop when stuck is `[ ]`; screenshots 01 and 02 | pass |
| Limits set | steps 2, spending 0.05, Save limits, tap Stop when stuck | "Saved. Steps: 2. Spending: $0.05."; `[x] Stop when stuck`; number keyboard; screenshot 03 | pass |
| Limits kept after the process was killed | permission revoke killed the app; reopened Settings | fields still 2 and 0.05, `[x]` | pass |
| Notifications off | `pm revoke POST_NOTIFICATIONS` at 08:42:07, reopen Settings | "Run notifications are off. You will not see when a run is done or needs you while you are in another app." and the button Open notification settings; screenshot 04 | pass |
| Button | tap Open notification settings | Android's page for Smallwright: Allow notifications off, "All notifications from this app are blocked."; screenshot 05 | pass |
| Back on | turned Allow notifications on in that page (08:42:51), pressed Back | Settings now reads "Run notifications are on." with no button | pass |
| Run ends on the step limit | new project, "Make a notes app with a list screen and a detail screen, then build it.", Send, chat on screen | listed, read three files, created three files, then "I stopped after 2 steps, the step limit you set in Settings. The changes so far are saved."; 2 requests, cost 0.0030768528 USD; screenshot 06 | pass |
| Limit-ended run in the background | "Add a dark mode switch and build it.", Send, Home | run notification "Needs your input" for limit49test: "I stopped after 2 steps, the step limit you set in Settings. The chan..."; tapping it opened the chat; 2 requests, 0.0002534504 USD; screenshot 07 | pass |
| Next Send continues | limits cleared and saved, "Reply with one word: ok" | "ok" after "Thought for 1.6 s"; 1 request, 0.0000666064 USD; screenshots 08 and 09 | pass |
| Restored | `shared_prefs/run-limits.xml` read with `run-as` | steps 0, cost empty, stop-when-stuck false | pass |

Model requests: 5 paid requests, total cost 0.0033969096 USD (reported by
OpenRouter through the app's log line). No other model requests.

Cleanup: the disposable project was deleted through Project tools, Delete this
project; the user's project "Main project" was selected again afterwards
and not changed. All three settings are off again. Notification permission was
revoked with `adb shell pm revoke` at 08:42:07 and given back through
Android's own notification page at 08:42:51; it is granted now. Its flags
changed from `USER_SENSITIVE_*` to `USER_SET|USER_SENSITIVE_*` (Android records
that the person used the switch).

## Notes

- Screenshots 01 to 05 came from the build before the merge with #43 (the
  Settings page is the same in the final build); 06 to 09 came from the final
  build.
- Another implementer's build was installed on the phone between my first
  install and my first screenshot attempt, so the first screenshot showed
  another app. Screens above were all taken while this session held the phone
  lock.

## Not run

- Stop when stuck on the phone: it needs the model to fail the same build
  three times in a row, which cannot be arranged without a scripted model.
  Covered by host tests.
- The spending limit on the phone: a $0.05 limit is far above what two steps
  cost. Covered by host tests.
- Probes: no new probe; the screen was driven through the real UI over adb.
- The S23 Ultra: not connected.

## Handoff for #50

"Ask before adding libraries" goes on the Settings page after Stop when stuck.
Follow the pattern: a `SwitchRow(label, on, onToggle)` in `SettingsPage`
(`ControllerScreen.kt`), a `UiState` flag and callback, `MainActivity` reads and
writes it. Stop when stuck is stored in `RunSettings` (preferences file
`run-limits`); add the new key there, and a field to `RunLimits` if
`StepRun` or `LibraryTools` needs it at run time (`RunSettings.load(context)` is
read once in `RunCoordinator.runSteps`).
