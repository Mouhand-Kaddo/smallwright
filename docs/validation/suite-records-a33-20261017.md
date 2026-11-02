# A33 records requests

Date: 2026-10-17 Dubai. Device: Galaxy A33 5G (SM-A336E), Android 16/API 36.
Controller `3cebe21a…` (120 s model deadline, 8192-token source cap). Free model
`cohere/north-mini-code:free`. Raw probe output:
`toolchains/alpha-goal-20261017/<run id>/NN-phase.txt`. Generated APKs were
verified on the phone and streamed from `run-as` into `pm install` (debug
install, not Android installer consent). Checklists ran in the generated app with
the controller and worker force-stopped first. These are agent observations,
not human acceptance.

## records_01: fail

Run `a33_records01_20261017_01`, project `app6dd4c8bb…`. The pristine R0 was
built with the normal Build button (v1 `2b2d4574…`) and installed. One send with
the exact previewed context `1f8fdf52…` (9902 bytes) produced R2 and v2
`e9642d77…`: 3290 reported tokens, zero repairs.

The reply replaced only `res/layout/empty.xml`. The template's MainActivity
builds its screen in code and never shows that layout, so v2 still showed
`Counter: 0`, Increment and Preview crash report. No note could be saved,
opened or kept: all three checklist items fail. Row: **rejected**.

Observed-feedback update `…_feedback1` described only the visible screen and the
requested notes behaviour. The exact context `3d6363ff…` (12043 bytes) produced
R3 with a Java compile error (4823 reported tokens). The first compiler repair
(context `9ecc48ee…`, 10475 bytes) ended FAILED with "Invalid model response
(envelope)": the reply did not call `replace_source`, though the request forces
that tool. That is terminal. R3 does not compile and v2 stays installed, so the
probe allows no further feedback. **records_01 fails.**

Probe changes during this run: a repair now previews with the controller's own
`repairRequest`. The probe checks that it equals the last request the run sent.
The first repair preview had used the frozen suite request and correctly failed
on "Approval UI payload changed". This run's feedback was sent before the probe
saved `lastRequestSha256`. That value was therefore computed on the laptop from
the app's feedback prefix and the sent observation, then written into this run's
probe preferences. The repair preview then matched the controller's dialog.

## Baseline fixture for records_02 to 05

records_01 failed, so these runs start from a documented developer-written
baseline: `controller/src/androidTest/assets/suite-fixtures/records-MainActivity.java`.
It is the pristine template with the counter UI replaced by a level-1 notes
list: Title and Body fields, "Save note", one button per saved title that opens
a dialog with the title and body, and notes saved as JSON in the activity
preferences. `SourceStubsCompileTest` compiles it against the worker stubs. The
probe builds it in a fresh project with the normal Build button
(`baselineBuild -e baselineFixture records`) and checks that R1 equals it.

## records_02: pass

Run `a33_records02_20261017_01`, project `app5c15274b…`, controller `68a78e67…`.
The fixture built as R1/v1 `fdf6c310…` (pinned R1 `680a2a7b…`) and was
installed. Agent check of v1 with the controller and worker stopped: saved
"Groceries / Milk and bread" and "Trip / Pack charger"; Trip opened with its
body; both stayed after force-stop and relaunch.

One send with the exact context `5ec25203…` (14889 bytes) produced R2 and v2
`7b6ca2b6…`: 6966 reported tokens, zero repairs. v1 → v2 kept both notes. Input
went through `foreignUi` with the controller and worker stopped.

| Checklist item | Observation | Result |
| --- | --- | --- |
| An existing note opens for editing | Groceries' Edit: "Edit Note" dialog with `Groceries` and `Milk and bread` filled in | pass |
| Changes appear in the list | Saved as "Groceries list / Milk, bread and eggs": the list shows GROCERIES LIST, and opening it shows the new body; Trip unchanged | pass |
| Changes remain after relaunch | force-stop and relaunch: same title and body | pass |

## records_03: fail

Run `a33_records03_20261017_01` continued the accepted records_02 project at
R2/v2. One send with the exact context `6d16b2fd…` (17421 bytes) ended FAILED
with "Invalid model response (tool arguments json)". The arguments of
`replace_source` were not valid strict JSON. The reply was below the 8192-token
cap, since controller `68a78e67…` would have reported "output limit" at the cap.
A proposal-policy failure allows no repair. The source stayed at R2, and there
is no APK.

Controller `9e166790…`, installed after this run, adds the parse failure kind
to this category from a fixed set: control character, escape, unterminated,
duplicate key or syntax. The status allowlist let the kind through only from
controller `5d64b67c…`. Until then, the status showed "OpenRouter request
failed". Gson's strict mode rejects literal tabs and `\'` inside
strings. Both are common when a model writes Java into a JSON string, so the
kind shows whether relaxing those two rules would help.

## records_04: pass

Run `a33_records04_20261017_01` continued the accepted records_02 project at
R2/v2, controller `9e166790…`. One send with the exact context `30b40426…`
(17493 bytes) produced R3 and v3 `4f6ae347…`: 7638 reported tokens, zero
repairs. v2 → v3 kept both notes. Input went through `foreignUi` with the
controller and worker stopped.

| Checklist item | Observation | Result |
| --- | --- | --- |
| Delete targets only the selected note | each note row has its own Delete; Groceries list's Delete opened "Delete Note / Are you sure you want to delete this note?" | pass |
| Cancel keeps it | Cancel: GROCERIES LIST and TRIP both listed | pass |
| Confirm removes it after relaunch | Delete: only TRIP left; after force-stop and relaunch only TRIP, which still opens with "Pack charger" | pass |

The dialog does not name the note it will delete.

## records_05: fail

Run `a33_records05_20261017_01` continued the accepted records_04 project at
R3/v3, controller `9e166790…`. One send with the exact context `dc02b1f1…`
(18811 bytes) took 44.4 s to reply and gave R4 with a Java compile error (8419
reported tokens). The repair (context `d96c8650…`, 21638 bytes) ended FAILED
after 80.8 s with "OpenRouter request failed". This is terminal: R4 does not
compile, v3 stays installed, and no picker export could be tested. **records_05
fails.**

The status does not show the cause. On this controller, `GenerationFailure`
showed every parser category added in this session ("output limit", the
finish-reason names and the JSON kinds) as "OpenRouter request failed". It also
gave no detail for transport failures. So this failure is either one of those
hidden parser categories or a transport error. Controller `5d64b67c…`, installed
after this run, lets those categories through. It also names transport failures
from a fixed set: read timeout, connection closed, TLS, connection or other.
