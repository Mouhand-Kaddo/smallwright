# Runs recover step by step, on the Galaxy A33, 2026-10-19

Ticket #44 of spec #35. The run journal now keeps each working run's steps.
When process death ends a run, opening its project again does three things:
it ends the run at the project's last committed revision, writes the
person's message and the steps shown so far to the chat, and adds an honest
closing status. Nothing is sent to the model again. Edits that were not
committed are gone, and none is left half applied.

## Design

- `RunJournal` gains a `run_progress` table (project, run, chat, the
  person's message, the chat's message count at Send, the steps so far).
  `create(revision, Progress)` writes it in the same transaction as the
  run's first event. `showed` updates the steps after each builder step;
  `RunCoordinator` writes it from the `RunSteps` publish callback.
  `saved` drops the row once `finishRun` has written the run's messages
  (also when the run could not start). `unsaved` lists a project's rows.
- `InterruptedRuns` (new) runs when a project opens (`MainActivity.prepareProject`,
  only when no run of that project is working in this process). It closes
  interrupted runs with the existing `recover` at the project's revision,
  then for each leftover row writes what the chat is still missing, counted
  from the chat size at Send: the message, the steps, then the status
  "The builder was closed before it finished, so this run ended here. Your
  project is as the builder last saved it; changes it had not saved yet
  were dropped. Send a message to continue." A committed build left
  BUILDING is still rebuilt on the phone, as before, and its status says so
  ("…It is building the saved version again now."). Writing again adds
  nothing. Steps are cut from the oldest so they fit one chat message (8 KB).
- Revisions: `ProjectStore.applyPatch` skips a revision directory left by
  death between publishing a revision and moving HEAD. The journal now
  accepts any later revision for a commit (it used to require exactly +1).
  It also accepts an interrupted APPLYING ending at any later revision.
  Before this change, the next run after such a death failed at its first
  build, and its journal stayed active.
- Removed journal states and paths that no longer apply: `PAUSED`, which
  was never written. `REPAIR_NEEDED` stays only so older journals still
  replay; opening its project closes it. Also removed:
  `abandonRepairNeeded` and its two calls in `MainActivity`,
  `pendingRepair`, verified-repair recovery, Stop's repair branch, and
  `create` superseding a repair.

## Device and build

- Phone: Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16,
  USB. The S23 Ultra was not connected.
- Run 1 build (`b4519f1`, before the merges): controller
  `247f073d4f5cae5b83ba847cd49c79d9fdbc08e5fae1d0147319fe7ee27a70b7`
  (installed before: `2746a9fa…83d8`), worker
  `263f6d162ea87d5681730215ae54bbe1c2f19c8fb98e8006564b467fbb1bf582`
  (before: `f0ba0286…7a30`), test APK `bc9fbbc1…732c`, `adb install -r` at
  08:53.
- Run 2 build (`10c5601`, merged with integration `792b158`): controller
  `205ee11dcc08226d20595580a6829b1052b46c47f36bc26ce331a750a1d9d3d0`
  (installed before: `6024442916a4…35ad`, this branch at `0f32033`), worker
  `50b5e62481c22231cff9bfd50795e85ae486567a20a3b6bf8b47885483e2c8a1`, test
  APK `ed50b32ba96e4b812af41a4b5119a3fce31f036097b83ace29db6c997bb307da`.
  Each installed `base.apk` hash was checked equal after install; data kept.
- Host chain through `spec35/gradle.sh` at `10c5601`: `spotlessApply
  :controller:testDebugUnitTest :worker:testDebugUnitTest
  :controller:assembleDebug :worker:assembleDebug
  :controller:assembleDebugAndroidTest :controller:lintDebug` passed; 437
  tests (controller 356, worker 81), 0 failures; lint 0 errors. After the
  probe fix and the merge of integration `1473d12` (#48), the same chain at
  `49b42f1` passed: 442 tests (controller 356, worker 86), 0 failures; lint
  0 errors.

## Host tests added

- `RunInterruptionTest` (7): a scripted run (real project store, real chat
  store, journal kept as `Snapshot`s the way the coordinator keeps them) is
  killed at each step boundary. Boundaries are before and after every
  journal write, before and after every model request and summary, inside
  every build, after every published step, and between each chat message
  the run saves at its end. The run is then reopened as the controller does
  it. At every boundary:
  - the journal and the store agree on one revision, and the store's files
    equal a committed revision of the uninterrupted run;
  - the run is closed, or left BUILDING to be rebuilt;
  - the chat holds the message, the steps shown and the right status
    exactly once, and reopening again adds nothing.

  The scripts:
  - fixes a failed build, then ends with unsaved edits;
  - ends with a successful build;
  - summarizes earlier steps (#43 compaction);
  - is killed while asking about a library (#50);
  - is killed at the third request: two requests made, none after reopening;
  - a revision directory left by death during publish: the next run commits
    and builds past it.
  - Steps that do not fit one chat message keep the newest.
- `RunJournalTest`: repair runs from before step runs close on reopen; a
  commit may skip revisions but never go back; an interrupted APPLYING may
  end at a later revision. `PAUSED` cases removed.
- `RunJournalProbe` (device SQLite, not run in this session): progress
  persists per project, `InterruptedRuns.reopen` closes the run and writes
  the chat once; repair-only probe cases removed.

## Cases on the phone

`RunInterruptionProbe` drives the real screen. Phase `start` creates a
disposable project "Run interruption test 44". It sends one message with the
saved model (DeepSeek V4.1 Flash; the probe refuses anything but that or a
free model). It waits until the run has committed a build and is working
again with edits after it, then kills its own process (`Process.killProcess`,
SIGKILL, like a low-memory kill). Phase `reopen` starts the controller fresh
and checks the journal, store, chat and screen. Message: "Make this a tip
calculator in two stages…build…move the calculation into TipMath.java…build
again".

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| Run 1: kill mid-run | `start`, 08:53 | killed at revision 1, journal EDITING, 11 steps, last "Created TipMath.java", "Edited MainActivity.java", then Thinking (`01-before-kill.png`) | pass |
| Run 1: reopened by an older build | another implementer's controller (`8472f829…`, without this change) opened the project before my reopen | journal closed FAILED at revision 1 (the existing recovery); progress row left, chat not written | as expected for that build |
| Run 1: reopened by this build | this branch's controller (`6024442…`) opened the project at 10:24 | chat shows the message, the 11 steps with Thinking marked failed, then the closing status; Install offers the revision 1 build; progress row consumed (`r1-00-before-delete.png`) | pass |
| Run 2: kill mid-run | `start`, 10:37, final build | killed at revision 1, EDITING, 9 steps, last "Created TipMath.java" uncommitted; journal BUILDING→EDITING at revision 1; progress row holds the 9 steps (`r2-01-before-kill.png`) | pass |
| Run 2: reopen | `reopen`, 10:37:43, fresh process | journal FAILED at revision 1 (seq 6, reported tokens unchanged, one run in the project, nothing active anywhere); HEAD 1; `TipMath.java` in no revision and not referenced by revision 1; chat has 3 new messages: the request, the 9 steps exactly as recorded, the closing status at revision 1; progress row gone; no work running. Screen: the steps and the status, Install for revision 1 (`r2-02-reopened-app.png`) | pass (app); probe's own check failed, see below |
| Clean up | Project tools → Delete this project → Delete project, by hand, both projects | only "Main project" and "Counter alpha" left; "Main project" selected; probe pins and screenshots removed from the phone (`r1-04-after-delete.png`, `r2-03-after-cleanup.png`) | pass |

The checks in the run 2 reopen row were read directly over `run-as`: the
journal (copied off the phone and read with `sqlite3`), HEAD, the revision
directories and the chat message files. The probe's own assertions did not
finish. It opened `ChatStore` without the trusted base, and `/data/user/0`
is a symlink, so it threw "Chat path is a symlink" after the app's recovery
had already run. That is fixed in the probe now but not rerun, to avoid
another paid run.

Screenshots in `toolchains/spec35/44/` (local, not committed):
`01-before-kill.png`, `r1-00-before-delete.png`, `r1-04-after-delete.png`,
`r2-00-before-delete.png` (an earlier view of run 1's reopened chat),
`r2-01-before-kill.png`, `r2-02-reopened-app.png`, `r2-03-after-cleanup.png`.

### Requests and cost

Killed runs never reach the `SmallwrightRun` end-of-run log line, so request
counts and reported cost were not read. The journal's reported tokens:
run 1 18,176, run 2 17,509 (35,685 in total). At DeepSeek V4.1 Flash list
prices ($0.30 per million input, $1.20 per million output), that is between
$0.011 and $0.043. Most tokens are prompt, so the real cost is near the low
end. Two paid runs; no other model requests.

### Phone-lock overlap, recorded honestly

Run 1's start phase overlapped another implementer's (#47) phone session by
about 20 seconds (08:53:45–08:54:05). I cancelled my queued `phone.sh`
session to merge first. The cancel landed just after the session had taken
the lock and killed only `flock`, so the script went on without it. The
coordinator told #47. #47's controller later opened my disposable project
first, which is the older-build reopen row above. My later sessions held
the lock throughout.

## Not run, and why

- `RunInterruptionProbe` reopen assertions end to end: failed on its own
  `ChatStore` check (fixed, not rerun). The same facts were checked by hand.
- `RunInterruptionProbe` cleanup: "Delete this project" sits below the fold
  of Project tools and the probe does not scroll there; deletion was done
  by hand. The probe's cleanup phase still needs a scroll step.
- `RunJournalProbe` on the phone: compiles, not run.
- A kill during a build (rebuild on reopen): host tests only. The rebuild
  path itself predates this ticket.
- A "did not finish" notification after process death: the working
  notification goes away with the killed service, and nothing restarts the
  run service (`START_NOT_STICKY`), so no end notice is posted. The chat
  tells it on the next open.
