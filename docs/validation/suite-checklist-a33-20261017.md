# A33 checklist requests 02 to 05

Date: 2026-10-17 Dubai. Device: Galaxy A33 5G (SM-A336E), Android 16/API 36.
Controller `3cebe21a…`. Free model `cohere/north-mini-code:free`. Raw probe
output: `toolchains/alpha-goal-20261017/<run id>/NN-phase.txt`. Agent
observations, not human acceptance.

## Baseline fixture

checklist_01 failed, so no accepted checklist revision exists. These runs start
from a documented developer-written baseline instead:
`controller/src/androidTest/assets/suite-fixtures/checklist-MainActivity.java`.
It is the pristine template with its counter UI replaced by a level-1 checklist:
a "New item" field, "Add item", a list of `- <item>` rows, and items saved as a
JSON array in the activity preferences. `SourceStubsCompileTest` compiles it
against the worker stubs.

The probe's `baselineBuild -e baselineFixture checklist` puts the fixture into
a fresh project's editor and presses the normal "Build and sign app locally"
button. It then checks that R1 equals the fixture and pins its SHA-256. The
fixture has no done state, so checklist_03 to 05 must also add what checklist_02
would have added.

## checklist_02: fail

Run `a33_checklist02_20261017_01`, project `app9c085aae…`. The fixture
(`efe22468…`) built as R1/v1 `bd5a093e…` in 3.9 s and was installed. Agent check
of v1 with the controller and worker stopped: added Milk and Bread through
`foreignUi`, and both stayed after force-stop and relaunch.

One send with the exact context `f85ab9be…` (13519 bytes) gave R2 with a Java
compile error. All three repairs (`d232b8b2…`, `2b31aeec…`, `2b31aeec…`) failed
at the same line. The model used `Paint.STRIKETHROUGH_TEXT_FLAG`, which Android
does not have. The real constant is `STRIKE_THRU_TEXT_FLAG`, and the stubs
include it. The run ended FAILED at R5 after 27476 reported tokens, with no APK.
The repair records named the problem (undefined field) and quoted the line.

## checklist_03: pass

Run `a33_checklist03_20261017_01`, project `app51a4c562…`. The fixture built as
R1/v1 `8edb8c89…` (pinned R1 `7dad177a…`; the hash differs per project because
the fixture contains the package name) and was installed. One send with the
exact context `6053008a…` (13547 bytes) gave a compile error. Two repairs
followed (`aa7632b1…`, `b9e9fd02…`), and R4 compiled to v4 `36dd211d…`. 23089
reported tokens in total, within the three-repair cap.

The model added a CheckBox per row and a "Clear completed" button. Clicks and
text went through `foreignUi` accessibility actions with the controller and
worker stopped. The CheckBoxes have no label, so the driver clicked them by
position (`#checkN`).

| Checklist item | Observation | Result |
| --- | --- | --- |
| Only completed items are targeted | Alpha, Beta, Gamma added; Alpha and Gamma checked. Dialog: "Delete 2 completed items?" | pass |
| Cancel deletes nothing | Cancel: all three items, Alpha and Gamma still checked | pass |
| Confirm removes completed items only | Clear: "Completed items cleared", only Beta (unchecked) left; same after force-stop and relaunch | pass |

## checklist_04: pass

Run `a33_checklist04_20261017_01` continued the accepted checklist_03 project at
R4/v4. One send with the exact context `878e8711…` (17978 bytes) produced R5 and
v5 `1ae2897e…`: 7625 reported tokens, zero repairs. v4 → v5 kept the saved
item Beta. Input went through `foreignUi`, with the controller and worker
stopped.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Incomplete count is visible | `Incomplete: 1 item` with Beta unchecked | pass |
| Marking done lowers it | Delta added, then Beta checked: 2 → 1 | pass |
| Adding or undoing an item raises it | adding Delta: 1 → 2; unchecking Beta: 1 → 2 | pass |

The count is shown under the title, input and buttons, above the list, not as
the first line of the screen.

## checklist_05: fail

Run `a33_checklist05_20261017_01` continued the accepted checklist_04 project at
R5/v5. One send with the exact context `4bf774c5…` (18557 bytes) produced R6
and v6 `9b696073…` (7925 reported tokens, zero repairs). R6's
`MainActivity.java` is byte-identical to R5's. Only the layout file, which the
app does not show, changed. v6 offered no way to rename an item: all three
checklist items fail. Row: **rejected**.

Observed-feedback update `…_feedback1` described the missing rename action. The
exact context `9bee3002…` (19428 bytes) gave R7 with a compile error (8478
tokens). Repair 1 (`da276a02…`) gave R8, still a compile error. Repair 2
(`b3617b4d…`) ended FAILED with "Invalid model response (envelope)". That is
terminal: R8 does not compile and v6 stays installed, so no further feedback is
allowed. **checklist_05 fails.**

"Envelope" covered several cases: a finish reason other than `tool_calls`, a
missing tool call, or a malformed reply. From controller `68a78e67…`, installed
after this run, the parser names the case: "output limit" for `length`, "tool
call with finish <reason>" or "no tool call, finish <reason>", with the reason
limited to a fixed set. The status allowlist let these names through only from
controller `5d64b67c…`. Until then they showed as "OpenRouter request failed".
