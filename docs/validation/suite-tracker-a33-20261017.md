# A33 tracker requests

Date: 2026-10-17 Dubai. Device: Galaxy A33 5G (SM-A336E), Android 16/API 36.
Harness: controller `f4993fdc…`, worker `0742f471…`, test APK from commit
`6a6f6be`. Free model `cohere/north-mini-code:free`. Raw probe output:
`toolchains/alpha-goal-20261017/<run id>/NN-phase.txt`. Each generated APK was
verified on the phone and streamed from `run-as` into `pm install` (debug
install, not Android installer consent). I ran every checklist through the
generated app's UI with the controller and worker force-stopped. These are agent
observations, not human acceptance.

## tracker_01: pass

Run `a33_tracker01_20261017_01`, project `appda582fee…`. The pristine R0 was
built with the normal Build button (v1 `cd8fc0fb…`) and installed. One send
with the exact previewed context `ed9b9f2f…` (9904 bytes) produced R2 and v2
`ed25762e…`: 4571 reported tokens, zero repairs, 22.1 s from Send to settled.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Add glass raises today's total | `Glasses Today: 0`, then 1 and 2 after two taps (8 and 16 oz) | pass |
| Total is visible | glasses and ounces are shown at the top | pass |
| Today's total survives relaunch | after force-stop and relaunch, `Glasses Today: 2` | pass |

## tracker_02: pass, with two visible defects

Run `a33_tracker02_20261017_01` continued the accepted tracker_01 project at
R2/v2. The exact previewed context `80c41d4a…` (6860 bytes) produced a compile
error. Two compiler-error repairs followed (contexts `fa7ddb65…`, then
`85fdd860…`), and R5 compiled to v5 `e4edeeac…`. 16432 reported tokens in total.
v5 was installed over v2 and kept the saved total of 2 glasses.

Test assistance: Samsung's keyboard closed the goal dialog whenever shell input
injected a key or tap. Text entry and clicks for this checklist therefore went
through accessibility actions from the controller test APK (`foreignUi`:
`ACTION_SET_TEXT`, `ACTION_CLICK`), not the keyboard.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Goal can be set locally | Set Daily Goal dialog, 64 → 32, Set: progress became `16 / 32 ounces` | pass |
| Progress updates after a glass | Add glass: `24 / 32 ounces (75%%)`, glasses 3 | pass |
| Goal survives relaunch | force-stop and relaunch: `Daily Goal: 32 ounces`, `24 / 32` | pass |

Defects seen: the progress text shows `%%` instead of `%`, and the "Daily
Goal" line keeps the old value until the next launch.

## tracker_03: initial update rejected

Run `a33_tracker03_20261017_01` continued the accepted tracker_02 project at
R5/v5. The exact previewed context `48094396…` (11798 bytes) produced R6 and v6
`fd2cd226…` in one send: 6328 reported tokens, zero repairs, 22.6 s from Send to
settled. v6 was installed over v5 and kept 3 glasses. Clicks went through
`foreignUi` accessibility actions.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Undo lowers total once | first Undo on the carried-over total went 3 → 0; after three new glasses, Undo went 3 → 2, and a second Undo went 2 → 3 | fail |
| Total cannot become negative | at 0, Undo kept 0 | pass |
| Updated total survives relaunch | force-stop and relaunch: `Glasses Today: 3` | pass |

The generated Undo restores one saved previous value and then saves the current
value as the new previous value, so a second Undo toggles back. The row stays
**rejected**. An observed-feedback update follows.

### tracker_03 feedback updates: fail

The probe now has `feedbackPreview` and `feedbackSend` phases. They put the
observation into the normal "Test feedback" field and use "Update from feedback
(free)" and "Send & update" with the exact previewed context hash. They are
allowed only when the current revision's verified APK is the installed one and
the run is not accepted, for at most three sends. All three sends used the same
agent-written observation of the Undo defect.

| Ledger row | Context | Result |
| --- | --- | --- |
| `…_feedback1` | `31a6164d…`, 14982 bytes | model timed out at 45327 ms (app deadline 45 s); source stayed R6; v6 stayed installed |
| `…_feedback2` | `f89638b9…`, 15478 bytes | model timed out at 45331 ms; same outcome |
| `…_feedback3` | `3303c082…`, 15890 bytes | controller `0f9ff16a…` with a 120 s deadline; settled in 23.9 s, 7605 reported tokens, zero repairs, R7/v7 `3b8e64be…`; v6 → v7 kept 3 glasses |

v7 checklist, with clicks through `foreignUi` and the controller and worker
force-stopped first: Undo went 3 → 2 → 3 → 2 → 3, Add went 3 → 5, Undo then
5 → 4. "Undo lowers total once" still fails, so **tracker_03 fails** with its
feedback cap used up. R7's `MainActivity.java` is byte-identical to R6's (same
SHA-256, 11589 bytes): the model returned the unchanged file, and the app
accepted and rebuilt it. The third send did not need the longer deadline, so these
two timeouts do not prove that 45 s was too short. Earlier successful sends in
this suite took up to 41 s, which is the reason for the change.

## tracker_04: fail

Run `a33_tracker04_20261017_01` continued the tracker_02 project at R7/v7. R7
is not an accepted revision (tracker_03 was rejected), but the probe pinned it
as the documented baseline. One send with the exact previewed context
`edca9754…` (15662 bytes) ended FAILED with "Invalid model response (tool
arguments json)": the `replace_source` arguments were not valid JSON. A
proposal-policy failure allows no compiler repair. The source stayed at R7, and
there is no new APK.

Likely cause, not proven: the reply was cut off at the 4096-token completion
cap. R7's Java file is 11589 bytes, and tracker_03's reply that rewrote it used
about 3300 completion tokens. A file with a history feature added would need
about 4000 or more. The app keeps no raw reply and did not compare
`completion_tokens` with the cap, so the evidence cannot separate truncation
from malformed JSON. The free model allows 64000 completion tokens (OpenRouter
model list).

Controller `3cebe21a…`, installed after this run, raises the source cap to 8192.
Its parser also reports "Invalid model response (output limit)" when the
arguments are invalid JSON and `completion_tokens` equals the cap. However, the
controller's status allowlist (`GenerationFailure`) did not include that
category until controller `5d64b67c…`, so earlier controllers showed it as
"OpenRouter request failed".

## tracker_05: unknown

Run `a33_tracker05_20261017_01` stopped in `prepare` with "Project is busy"
before any model request. The probe read the project store while the
controller's executor was still restoring the signed APK. The probe now drains
that executor first. The run id was used, so the request ran as
`a33_tracker05_20261017_02`.

That run continued the tracker_02 project at the documented R7 baseline. The
exact previewed context `6fd7bf18…` (15667 bytes) produced R8 and v8
`cbdeaa6b…` in one send: 7803 reported tokens, zero repairs. v7 → v8 kept 4
glasses. Clicks went through `foreignUi`, with the controller and worker
force-stopped first.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Confirmation is shown | Clear Today opened "Clear Today / This will reset today's water count…" with Cancel and Clear Today | pass |
| Cancel keeps today's total | Cancel: `Glasses Today: 4`, 32 oz | pass |
| Confirm resets today without deleting older days | Confirm: 0 glasses, 0 oz, goal 32 kept; same after force-stop and relaunch. R8 stores no dates or older days, so their preservation cannot be observed | unknown |

The older "Reset Today" button still resets without confirmation. The
`foreignUi` click now tries every node with the target text. The dialog title
"Clear Today" had matched first and hid the button.
