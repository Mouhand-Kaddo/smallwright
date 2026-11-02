# The builder picks a skill without `/name`, on the Galaxy A33 (2026-10-19)

## Setup

- **Phone.** Galaxy A33 `<A33 serial>`.
- **Controller.** `b2ae2316c83d02cdd427e11a10e8f83269d245a6562374ddbdb94a3f236b4d89`, from 4fd7962 (it matches the spec #53 final check).
- **Model.** DeepSeek V4.1 Flash. It is the only paid model with standing approval for testing the step run. The user asked for the cheapest paid model; a cheaper unapproved one was not used.
- **Skills.** The 37 mattpocock skills from #57 are on, including `grill-me` and `grilling`. `grill-me` sets `disable-model-invocation: true`, so the model cannot start it by itself. The one it can start is `grilling`, whose description reads "Use when the user wants to stress-test their thinking, or uses any 'grill' trigger phrases."
- **Scripts.** `/tmp/final53/skillauto.py` and `skillauto2.py`. Each ran as one locked session in a new disposable project, which was deleted at the end, and selected "Main project" again.

## Cases

| Case | Message | Expected | Actual | Cost |
| --- | --- | --- | --- | --- |
| A | "help me plan a habit tracker app, ask me questions first" | Possibly a skill | No skill read. One request with no tool calls; the reply was 5 plain questions. The message matches neither part of grilling's description, so this is normal description matching, not a fault. | 1 request, $0.00050848 |
| B | "grill me on my plan for a habit tracker app" | `grilling` read by the model | Steps "Read skill grilling", then "Read MainActivity.java" and "Read AndroidManifest.xml". The row reads "Read skill grilling, read 2 files". The reply follows the skill: numbered questions with a recommended answer each. No "Used skill" step, which is correct, because no `/name` was used. | 3 requests, $0.0018284952 |

Screenshots:
- Case A: `toolchains/spec35/57-auto/02-run-top.png`.
- Case B: `toolchains/spec35/57-auto/b/03-run-steps-opened.png` and `04-run-reply.png`.

## Limits

These are two single runs with one model. Whether a skill is picked depends on how well the message matches the skill's own description, which the skill's author writes.
