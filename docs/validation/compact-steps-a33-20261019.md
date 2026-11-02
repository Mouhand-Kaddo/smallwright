# Compact builder steps on the Galaxy A33, 2026-10-19

Issue #54 (parent #53): every builder step was its own row, so a run of 20
steps filled several screens. The "Added library" step for Material listed
dozens of libraries in one block several screens tall.

## What changed

- `ui/StepSummary.kt` (new): `StepSummaries.of(steps, ended)` counts a group of
  steps by what each one did, using the texts StepRun and LibraryTools give them,
  and returns plain words such as "Listed the files, read 3 files, changed 5
  files, deleted 1 file, added 1 library, built once". Details:
  - Each file is counted once.
  - Refused steps are not counted.
  - "Thought for" steps are not counted.
  - When the group has ended, a failure part is added:
    - "the build failed" or "the last build failed", when the last build
      failed;
    - "did not finish", when the steps were saved unfinished (process death);
    - otherwise the failed last step's own words.
  - A group with nothing countable shows its last step.
- `ui/Chat.kt`:
  - The steps between two messages are one bordered row: ▸/▾ and the summary,
    with the failure part in `Ink.failed`. Tapping the row opens or closes it,
    and open, it lists today's step rows.
  - Consecutive saved step messages count as one group. Older chats saved one
    run in several parts.
  - A group of one step stays a plain step row.
  - While a run works, the steps so far are the row, and the newest step
    follows it live, with its pulsing dot and moving dots. ASKING is always that
    newest step, so it and its Add and Don't add buttons are never collapsed.
  - A step detail shows at most 3 lines, with an ellipsis. A clipped step
    becomes a button that shows the whole detail.
  - Accessibility: the row is one clickable node with `Role.Button`, read out as
    its summary. Its `stateDescription` is "expanded" or "collapsed", and the
    arrow has no semantics. A clipped step has the same state.
- `StepLog` and the saved format are unchanged.

## Device and build

- Phone: Samsung Galaxy A33 (SM-A336E), serial `<A33 serial>`, Android 16
  (SDK 36), arm64-v8a, USB.
- Host chain on `spec35/t54-steps` at ee11129, with `spec35/integration` 69d74db
  merged:
  - steps: spotlessApply, controller and worker unit tests, controller and worker
    assembleDebug, controller assembleDebugAndroidTest, lintDebug;
  - results: 460 tests, 0 failures, 0 errors, 0 skipped; lint 0 errors;
  - `StepSummaryTest` has 9 tests.
- Final APKs:
  - controller `3433cf6f01a2c2e5d6d23626692cad3d6ad329263b951b2156078b6d930b6054`
  - worker `b33272acc67db6db205285690ee457ec908791eb0f9306c0696048b4b10858b4`
  - controller androidTest `b4e607f31da191da96823ef9b8e9d9dc0a61b41411d2b05be9ad9f3788fdc506`
- Builds installed during the checks:

  | Controller | Commit | Used for |
  | --- | --- | --- |
  | `1e7637eb…` | d55ce8d + integration c2037b2 | session A |
  | `fa7f6dbb…` | 1ea2533 | session B |
  | `12eacd0f…` | 026e51f, integration 69d74db merged | session C (screenshots 01–06) and session D (07) |
  | `3433cf6f…` | ee11129, final | sessions E and E2 (08) |

  The final build differs from 12eacd0f only in joining consecutive saved step
  messages. A run started in this app saves one step message, so screenshots
  01–06 look the same on the final build.
- Every install was `adb install -r`. The installed `base.apk` hash was checked
  before and after each install, and matched the APK each time. The worker was
  not reinstalled; its installed hash was `087fe746…`.

## Cases on the phone

All model requests went to `deepseek/deepseek-v4.1-flash`, in Steps54, a
disposable project. It was created through New project and deleted at the end
through Project tools → Delete this project. Each phone session was one
`phone.sh` command. It started by checking the installed hash and reinstalling
when another agent's build was installed, and ended by selecting "Main project".

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| Working run, collapsed with a live step | Sent "Make the plus button bigger." (third message) and took frames every 1 s | The bordered row "▸ Read 3 files, changed 1 file", then "● Building on this phone..." with the pulsing dot and moving dots. Stop shows. In all three runs, once two steps had finished, every frame showed the row (growing as steps finished) with the newest step under it; the first frames show only "Thinking". `01`, `live/`, `live2/`, `live3/` | pass |
| Finished runs collapsed (reopened chat) | Switched projects and back; scrolled to the top | Each run is one row: "Listed the files, read 3 files, changed 5 files, deleted 1 file, added 1 library, built once" and "Listed the files, read 2 files, changed 2 files, built once", each followed by its reply. The arrow sits on the first line. `02`, `06` | pass |
| The same run opened | Tapped the first row | ▾, then each step row with its dot: Listed the project's files, Read AndroidManifest.xml, …, Added library com.google.android.material:material 1.14.0, Created … `03` | pass |
| Long library step clipped to 3 lines | Same view | "└ Also androidx.activity:activity 1.8.0, androidx.annotation:annotation 1.8.1, androidx.annotation:annotation-experiment…" ends with an ellipsis after 3 lines. `04` | pass |
| Tapping the clipped step | Tapped the library step | The full list of libraries shows. Before, the node was [136,1401][1035,1727]; after, [136,1401][1035,1989] and continuing below the fold. `05` | pass |
| Red failure in a saved chat | Viewed "Main project" without tapping anything (12eacd0f) | Rows "Built once, **the build failed**", with the failure words in red. `07` | pass |
| Older chat saved in parts | Viewed "Main project" without tapping anything (final 3433cf6f) | After "ok fix the app" there is one row, "Built 3 times", where 12eacd0f showed three rows. That run's last build worked, so nothing is red. `08` | pass |
| Accessibility tree | uiautomator dumps | The row is one clickable node across the whole bordered row ([45,820][1035,1079]). Of the opened steps, only the clipped one is clickable. | pass (node only; see limits) |
| Cleanup | Deleted Steps54; selected "Main project" | Steps54 is no longer in the project list. The selected model is DeepSeek V4.1 Flash. The user's project was only viewed. | pass |

Screenshots are local and not committed: `toolchains/spec35/54/01-…` to `08-…`,
with live frames in `live/`, `live2/` and `live3/`, and dumps in `raw/`. The
`old/` folder holds the session B frames from fa7f6dbb.

Cost: three runs of 5, 5 and 4 requests, reported as 0.0017679 + 0.0004734 +
0.0008569 = 0.0030982 USD.

## Findings and limits

- **Phone sharing.** Several implementers share the A33, so per-command lock
  holds interleaved. Another agent's typing dismissed my first New project
  dialog. I typed nothing into anyone's field. After that, each of my sessions
  ran as one locked script, as the coordinator asked. The adb server had stopped
  listing the phone after a USB reconnect. I restarted the project adb server
  once, killing it inside the lock and starting it outside.
- **Not run:**
  - A live red failure: no run failed its last build. The red wording was seen
    in the user's saved chat (07), and host tests cover the counting.
  - The "did not finish" wording on the phone: host tests only.
  - The ASKING step: "Ask before adding libraries" was off. ASKING stays outside
    the group by construction: it is always the newest live step.
  - TalkBack: uiautomator does not show `stateDescription`, so "expanded" and
    "collapsed" rest on the code.
- **Open state.** A row's open or closed state is kept per chat position while
  the chat is shown. It is not saved, and a run's live row closes when the run
  ends and its saved row takes its place.
- **Wording.** It follows today's step texts. A new tool or a reworded step needs
  a line in `StepSummaries`. Until then, steps it does not know are not counted
  in the summary, but they still show when the row is opened.
- Not run: the S23 Ultra (no longer tested), other font scales.
