# A33 fixed suite on the step-by-step builder (2026-11-02)

Date: 2026-11-02 Dubai. Device: Galaxy A33 5G (SM-A336E), Android 16/API 36,
arm64, USB debug. Main at `cb11d7d`. Controller debug APK `4ed33106…`, worker
`54a715d5…`, controller test APK `ff1dd9c6…`, installed with `adb install -r`
(app data kept). Model `deepseek/deepseek-v4.1-flash` (DeepSeek V4.1 Flash,
approved for step-run tests), thinking level medium as set in the app.

Every request went through the normal controller UI with `A33SuiteProbe`,
driven by `scripts/suite/suite.py`, with the exact previewed context hash.
Each group's first request starts from a fresh project's starter app; later
requests continue the previous accepted project. A run that ends with a
round of questions is answered with each question's recommended choice
through Send answers (`suite.py answers`). Generated APKs were verified on the
phone and streamed from `run-as` into `pm install` (debug install, not
Android's install confirmation). Checklists ran with the controller and worker
force-stopped, through accessibility actions from the controller test APK
(`suite.py ui`). These are agent observations. Raw output and screenshots:
`toolchains/alpha-goal-20261017/<run id>/`.

Setup notes:

- The app's picked model was Claude Haiku 5.5, which is not approved for test
  requests. Run `a33_counter01_20261102_30` built its starter app, then the
  probe refused the preview at the model check; nothing was sent. The choice
  was backed up on the phone (`files/model-choice.user-backup-20261102.xml`)
  and DeepSeek V4.1 Flash was selected for the round.
- `svc power stayon usb` kept the screen on while connected.

## counter_01: pass

Run `a33_counter01_20261102_31`, fresh project `app4ec17418…`. Starter R0 built
as R1 `c1c3c185…` and was installed. One send with context `a116ffb3…` (13348
bytes) produced R2 `d8d00b9b…`: 26332 tokens, zero repairs.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Decrement is visible | DECREMENT and INCREMENT side by side on one row | pass |
| One tap lowers the displayed count by one | 0 → 3 by Increment; one Decrement 3 → 2 | pass |
| Count remains after relaunch | force-stop and relaunch: `Counter: 2` | pass |

## counter_02: pass

Run `a33_counter02_20261102_31` continued the accepted counter_01 project at
R2. One send with context `17b76dfd…` (13900 bytes) produced R3 `2f760ee0…`:
36297 tokens, zero repairs. The run itself reached READY_TO_TEST, but the
probe's read of the project raced the app's last writes after the run
("Project is busy"), so the outcome was read with `suite.py recover` from the
same run `run_35f6f125…`. The probe now waits up to 30 s for the project
(test APK `aaca46ed…` from counter_03 on).

| Checklist item | Observation | Result |
| --- | --- | --- |
| Reset asks for confirmation | RESET opened "Set the counter to zero?" with Cancel and Reset | pass |
| Cancel keeps the old value | Cancel: `Counter: 2` | pass |
| Confirm sets zero and persists it | dialog Reset: `Counter: 0`; force-stop and relaunch: `Counter: 0` | pass |

## counter_03: pass

Run `a33_counter03_20261102_31` continued the accepted counter_02 project at
R3. One send with context `005303ac…` (14266 bytes) produced R4 `f062efc3…`:
38999 tokens, zero repairs. The step size is a slider; it was set with
`input tap` on its track (assistance: shell taps, not accessibility).

| Checklist item | Observation | Result |
| --- | --- | --- |
| Step size is editable within 1 to 10 | slider ends read `Step size: 10` and `Step size: 1`; middle tap `Step size: 7` | pass |
| Increment uses the selected step | step 7: 0 → 7; step 10: 7 → 17 | pass |
| Step remains after relaunch | force-stop and relaunch: `Counter: 17`, `Step size: 10` | pass |

## counter_04: pass

Run `a33_counter04_20261102_31` continued the accepted counter_03 project at
R4. One send with context `7361e4cf…` (14903 bytes) produced R5 `6b1dd3e7…`:
28989 tokens, zero repairs. R5 kept `Counter: 17` and `Step size: 10`.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Tap count is distinct from the main count | `Lifetime taps: 0` beside `Counter: 17`; two Increments and one Decrement: `Counter: 36`, `Lifetime taps: 2` | pass |
| Reset does not clear tap count | Reset, confirm: `Counter: 0`, `Lifetime taps: 2` | pass |
| Both values remain after relaunch | force-stop and relaunch: `Counter: 0`, `Lifetime taps: 2` | pass |

## counter_05: pass

Run `a33_counter05_20261102_31` continued the accepted counter_04 project at
R5. One send with context `4c44701f…` (15618 bytes) produced R6 `66fe7b39…`:
27595 tokens, zero repairs. The same probe read race recurred ("Project is
busy" after the run settled), so the outcome was read with `suite.py
recover` from run `run_4c272066…`. Cause: the app holds one project lock for
all projects (`ProjectStore.ACTIVE`) during short reads, and a read that finds
it held is refused. The probe now retries each of its project reads for up to
30 s (test APK `4eb6f77d…` from tracker_01 on).

| Checklist item | Observation | Result |
| --- | --- | --- |
| Add one label is visible | the Increment button reads ADD ONE | pass |
| Tap increments exactly once | step 10: one tap 0 → 10; `Lifetime taps` 2 → 3 | pass |
| Saved count remains after update | R6 opened with R5's `Counter: 0`, `Lifetime taps: 2`, `Step size: 10`; force-stop and relaunch after the tap: `Counter: 10`, `Lifetime taps: 3` | pass |

## tracker_01: pass

Run `a33_tracker01_20261102_31`, fresh project `appe7391c2…`. Starter R0 built
as R1 `043e9edc…` and was installed. The request (context `98c4c795…`, 13310
bytes) ended with one round of five questions; the recommended answers (250
ml glass; a 2000 ml goal; only today; name Water Tracker; make an icon and a
picture) went with context `756cad9c…`, and that run ended with the plan and
"Shall I build it now?". "Yes, build it" (context `6ee742f6…`) produced R2
`7ee11ff0…` with zero repairs. Reported tokens 23686, 17371 and 128924; the
build run made the icon and a water background with the image model (approved
by the user for this round). Reported cost of the build run $0.082.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Add glass raises today's total | two taps of "Add glass (250 ml)": `0 ml` → `500 ml`, `2 of 8 glasses (500 of 2000 ml)` | pass |
| Total is visible | large `500 ml` with a progress bar | pass |
| Today's total survives relaunch | force-stop and relaunch: `500 ml` | pass |

## tracker_02: pass after one feedback

Run `a33_tracker02_20261102_31` continued the accepted tracker_01 project at
R2. The send (context `1358331b…`, 18178 bytes, 40952 tokens) changed no file:
the builder judged the goal already present from the interview and rebuilt R2
as version 3 (`26da5d81…`). That app showed `Goal: 2000 ml (8 glasses)` with no
way to change it, so the first checklist item failed.

The first feedback attempt was refused by the probe before any request: its
guard wanted a newer source revision than the baseline, and this run had only
rebuilt. The guard now compares app versions (test APK `529b182d…`). Feedback 1
(context `e6209d5b…`, 93809 tokens) said: "I can't set the goal. The screen
shows Goal: 2000 ml (8 glasses), but tapping it does nothing and there is no
other way to change the goal." It produced R3 `0e18e3bb…`, zero repairs.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Goal can be set locally | "Goal: 2000 ml (8 glasses) — tap to change" opens "Daily goal" with a number field and 1500 to 3000 ml choices; 1750 and Save: `Goal: 1750 ml (7 glasses)` | pass |
| Progress updates after a glass | `2 of 7 glasses (500 of 1750 ml)` → Add glass → `3 of 7 glasses (750 of 1750 ml)` | pass |
| Goal survives relaunch | force-stop and relaunch: `Goal: 1750 ml (7 glasses)`, `750 ml` | pass |

## tracker_03: pass

Run `a33_tracker03_20261102_31` continued the accepted tracker_02 project at
R3. One send with context `ba69ca52…` (20289 bytes) produced R4 `afd09980…`:
61722 tokens, zero repairs. Undo became "Undo last glass" with a short note.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Undo lowers total once | one tap: `750 ml` → `500 ml` | pass |
| Total cannot become negative | two more taps reach `0 ml`; Undo is then disabled (greyed, not clickable) and the total stays `0 ml` | pass |
| Updated total survives relaunch | 250 → two glasses → Undo: `500 ml`; force-stop and relaunch: `500 ml` | pass |

## tracker_04: pass

Run `a33_tracker04_20261102_31` continued the accepted tracker_03 project at
R4. The request (context `de855494…`, 21232 bytes, 22874 tokens) ended with a
round of three questions. The recommended answers (history below today's card;
a list of day, date and total; days without water shown as 0 ml) went with
context `b068df7d…` and produced R6 `12535db2…`: 191668 tokens, zero repairs,
with no separate build question.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Seven-day history is visible | "Last 7 days": Today, Sun 1 Nov, Sat 31 Oct, Fri 30 Oct, Thu 29 Oct, Wed 28 Oct, Tue 27 Oct (last two after a scroll) | pass |
| Today's total appears in history | Today `500 ml`, matching the card; Add glass: Today `750 ml` | pass |
| History survives relaunch | force-stop and relaunch: Today `750 ml` | pass |

Older days read 0 ml because earlier revisions kept only today's total.

## tracker_05: closed by the user

Run `a33_tracker05_20261102_31` continued the accepted tracker_04 project at
R6. One send with context `9281eb9a…` produced R7 `c10f9b5c…`: 86724
tokens, zero repairs.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Confirmation is shown | "Clear today" opens "Clear today's water?" ("…Your goal and past days stay as they are…") with Cancel and Clear | pass |
| Cancel keeps today's total | Cancel: `750 ml` | pass |
| Confirm resets today without deleting older days | Clear: `0 ml`, Today `0 ml`, kept after relaunch. No older day had water yet (earlier revisions kept only today), so the "older days stay" part waits for a day change: 500 ml was logged on 2 Nov at 12:53 Dubai to check after midnight | not agent-observed |

On 2026-11-02 the user closed the older-day check as done and not needed. No
ledger row was written for tracker_05.

## checklist_01: pass

Run `a33_checklist01_20261102_31`, fresh project `appe4851c1…`. Starter R0
built as R1 and was installed. The request (context `7f2025bf…`, 13334 bytes,
23746 tokens) ended with two questions; the recommended answers (tap to tick
off, long-press to delete; name Checklist; context `ed4cf17d…`, 24341 tokens)
ended with the plan and the build question; "Yes, build it" (context
`d09beb1b…`) produced R2 `254bcdbf…`: 55891 tokens, zero repairs. No pictures
were asked about or made.

| Checklist item | Observation | Result |
| --- | --- | --- |
| An item can be added | "Add an item" field and Add: Buy milk, Call mum, Water plants | pass |
| Added text appears in the list | all three listed, unticked | pass |
| List remains after relaunch | force-stop and relaunch: the same three items | pass |

## checklist_02: pass

Run `a33_checklist02_20261102_31` continued the accepted checklist_01 project
at R2. One send with context `ee68d82d…` (17016 bytes) produced R3
`e52a8d6f…`: 70711 tokens, zero repairs. Each row gained a checkbox.

| Checklist item | Observation | Result |
| --- | --- | --- |
| An item can be marked done | tap Buy milk: its checkbox `checked=true` | pass |
| The done state is visually clear | ticked green box and struck-through grey text (screenshot `01-marked.png`) | pass |
| Undo restores the item and state persists | tap Call mum (done), tap Buy milk again (undone); force-stop and relaunch: Buy milk unticked, Call mum ticked, Water plants unticked | pass |

## checklist_03: pass

Run `a33_checklist03_20261102_31` continued the accepted checklist_02 project
at R3. One send with context `e86ee92e…` (17765 bytes) produced R4
`53d92c61…`: 53175 tokens, zero repairs. "Clear completed" shows only while an
item is ticked.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Only completed items are targeted | with Call mum and Water plants ticked: "Delete all 2 completed items from the list?" | pass |
| Cancel deletes nothing | Cancel: Buy milk, Call mum, Water plants all listed, ticks unchanged | pass |
| Confirm removes completed items only | Delete: only Buy milk (unticked) remains; same after force-stop and relaunch | pass |

## checklist_04: pass

Run `a33_checklist04_20261102_31` continued the accepted checklist_03 project
at R4. One send with context `19563f12…` (18576 bytes) produced R5
`6313772a…`: 31749 tokens, zero repairs.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Incomplete count is visible | `1 item left` under the title | pass |
| Marking done lowers it | `3 items left` → tick Call mum `2 items left` → tick Buy milk `1 item left` | pass |
| Adding or undoing an item raises it | adding Call mum and Water plants: `1 item left` → `3 items left`; unticking Call mum: `1` → `2 items left`; after relaunch `2 items left` with Buy milk ticked | pass |

## checklist_05: pass

Run `a33_checklist05_20261102_31` continued the accepted checklist_04 project
at R5. One send with context `d2e24a6b…` (19170 bytes) produced R6
`480c58c2…`: 45314 tokens, zero repairs. A long press now offers Rename,
Remove and Cancel (it removed at once before).

| Checklist item | Observation | Result |
| --- | --- | --- |
| One selected item can be renamed | long press Call mum → Rename → "Rename item" field → `Call grandma`, Save | pass |
| Other items and states stay unchanged | Buy milk still ticked, Water plants unticked, Call grandma unticked as before | pass |
| Rename remains after relaunch | force-stop and relaunch: Buy milk (ticked), Call grandma, Water plants | pass |

## records_01: pass

Run `a33_records01_20261102_31`, fresh project `app6fb5b872…`. Starter R0
built as R1 `cf8fc545…` and was installed. The request (context `a013bc8e…`,
13308 bytes, 23661 tokens) ended with four questions; the recommended answers
(name Notes; a clean light look with soft blue accents; save each note's date
and time; a search box; context `880ec5f9…`, 17001 tokens) ended with the plan
and the build question; "Yes, build it" (context `3ec48a62…`) produced R2
`62a6da1f…`: 266804 tokens, zero repairs. No pictures were asked about or made.

| Checklist item | Observation | Result |
| --- | --- | --- |
| A titled note can be saved | New note → Title `Shopping`, body `Milk, eggs and bread` → Save (tick, `content-desc=Save`; tapped with `input tap`): listed with `2 Nov 2026, 13:03`; a second note `Trip ideas` saved the same way | pass |
| Saved note can be opened | tap Shopping: editor with `Shopping` and `Milk, eggs and bread` | pass |
| Notes remain after relaunch | force-stop and relaunch: Trip ideas and Shopping with bodies and times | pass |

## records_02: pass

Run `a33_records02_20261102_31` continued the accepted records_01 project at
R2. One send with context `f4daab9e…` (18824 bytes) produced R4 `d8418be1…`:
217870 tokens, zero repairs.

| Checklist item | Observation | Result |
| --- | --- | --- |
| An existing note opens for editing | tap Shopping: editor with its title and body | pass |
| Changes appear in the list | `Weekly shopping` / `Milk, eggs, bread and rice` replace the old entry, still two notes | pass |
| Changes remain after relaunch | force-stop and relaunch: the edited note listed and opens with the new text | pass |

## records_03: pass

Run `a33_records03_20261102_31` continued the accepted records_02 project at
R4. One send with context `e771905f…` (20090 bytes) produced R5 `7a678809…`:
79043 tokens, zero repairs. The search box became "Search titles". The APK
requests no Internet permission (only its own dynamic-receiver permission).

| Checklist item | Observation | Result |
| --- | --- | --- |
| Matching titles appear | `trip`: Trip ideas; `SHOP`: Weekly shopping (case ignored) | pass |
| Nonmatching titles are hidden | `trip` hides Weekly shopping; `zzz`: "No titles match “zzz”." | pass |
| Clearing search restores the list | empty box: both notes listed | pass |

## records_04: pass

Run `a33_records04_20261102_31` continued the accepted records_03 project at
R5. One send with context `9fe975a9…` (20830 bytes) produced R6 `1e48d050…`:
211219 tokens, zero repairs. A long press on a note asks before deleting.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Delete targets only the selected note | long press Trip ideas: "Delete this note?" naming “Trip ideas”; Delete leaves Weekly shopping | pass |
| Cancel keeps it | Cancel: both notes listed | pass |
| Confirm removes it after relaunch | force-stop and relaunch: only Weekly shopping | pass |

## records_05: pass

Run `a33_records05_20261102_31` continued the accepted records_04 project at
R6. One send with context `dc9344fb…` (21886 bytes) produced R7 `2eb3fe20…`:
204799 tokens, zero repairs. The export sits in the ⋮ menu (opened with
`input tap`). The APK requests no storage or Internet permission. The "New
note" button overlaps the navigation bar (cosmetic, not in the checklist).

| Checklist item | Observation | Result |
| --- | --- | --- |
| Android save-file picker opens | ⋮ → "Export as text file" opens `com.google.android.documentsui` PickActivity in Downloads with `notes-2026-11-02.txt` | pass |
| Exported file has the saved notes | Save wrote `/sdcard/Download/notes-2026-11-02.txt`, 234 bytes: header, then "Weekly shopping", its time and "Milk, eggs, bread and rice" (copy kept in the run folder; the file was then removed from Downloads) | pass |
| Cancel writes no file | Back from the picker: Downloads listing identical before and after | pass |
