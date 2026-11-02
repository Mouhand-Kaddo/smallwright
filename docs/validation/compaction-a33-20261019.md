# Long runs summarize and continue (#43), host and Galaxy A33, 2026-10-18/19

When the next request of a run would pass three quarters of the picked
model's request limit, the same model summarizes the earlier steps in a
request that offers no tools. The run then continues with the first message
approved at the tap, the summary, the project's files as they are now and the
latest steps. The compaction shows as the builder step "Summarizing earlier
steps to make room", then "Summarized earlier steps to make room" with its
duration.

## Design

- `Compaction` (controller, new) owns the budget and the new conversation.
  `StepRun` asks `due(messages)` before each request and calls `compact` when
  it is. `StepRun.Model` gained `summarize` and `contextTokens`.
- Limit = the catalog context length less room for the reply
  (`min(MAX_COMPLETION_TOKENS, context/4)`), and never above
  `UsageBudget.MAX_PROMPT_TOKENS` or what fits in
  `ModelClient.MAX_STEP_REQUEST_BYTES` (4 MiB). Compaction starts above 3/4 of
  the limit. Tokens are counted cautiously: three bytes per token for the
  messages plus the tool schemas, or the model's reported total for the last
  request plus three bytes per token of what was added since, whichever is
  larger.
- Kept verbatim: the approved first message (the person's request, chat
  history and build contract), the listing of every file with its size and
  "changed in this run", the current text of files changed in this run
  (redacted like `read_file`, within 1/8 of the limit), and the latest one or
  two model replies with their results (within 1/8 of the limit). The rest is
  summarized. The summary request holds the first message and a transcript of
  the earlier steps (calls and results cut to 1,000/2,000 characters, within a
  quarter of the limit). An earlier summary is carried into the next one.
- The summary answer is capped at 16 KB (`ModelClient.MAX_SUMMARY_BYTES`) and
  at 1/8 of the limit. A tool call in a summary reply is refused. Summary
  requests count toward the run's requests, tokens and cost.
- If the compacted conversation still passes 3/4 of the limit, the run ends
  with "The run no longer fits the model's context, even with its earlier
  steps summarized", rather than paying for summaries again and again.
- The context length comes from the OpenRouter catalog the model picker
  already loads (`context_length`, or the top provider's when smaller). It is
  saved with the picked model. A saved choice without it reads as the default
  model's 1,048,576 for DeepSeek V4.1 Flash, or 131,072 for other models.

## Host

Branch `spec35/t43-compaction` at `28dcd48` (spec35/integration `9ea35a5`
merged; it had not moved). Through `spec35/gradle.sh`: `spotlessApply
:controller:testDebugUnitTest :worker:testDebugUnitTest :controller:assembleDebug
:worker:assembleDebug :controller:assembleDebugAndroidTest :controller:lintDebug`
passed: 377 tests, 0 failures, 0 skipped; lint 0 errors (19 warnings, none in
changed files).

New tests:

- `StepRunTest.aRunThatOutgrowsTheModelsContextSummarizesItsEarlierStepsAndContinues`:
  a scripted run at a 60,000-token context writes and edits
  `assets/notes.txt`, reads it 14 times, edits `MainActivity.java` and
  answers. It compacts twice and finishes. Every step request stays under the
  limit (45,000 est. tokens; observed peak 32,984, about 11,100 right after
  each compaction). Each continued request repeats the first request's first
  message unchanged, including "Make it better". Its summary message lists
  `assets/notes.txt` as changed with its latest size, and shows its latest text
  (the edited line, not the old one). The second summary request carries the
  first summary. Both steps are shown, and the commit holds both edits.
- `theTokensTheModelReportsCountTowardTheLimitToo`: a reported prompt count
  near the limit triggers compaction even though the bytes are small.
- `aSummaryThatFailsEndsTheRunAndSavesNothing`: the summary request fails, the
  step shows "Could not summarize earlier steps", the journal ends FAILED and
  nothing is committed.
- `StepProtocolTest.aSummaryRequestOffersNoToolsAndKeepsALongerAnswer`,
  `ModelCatalogTest.readsTheContextLengthAndTheTopProvidersSmallerOne`.

## Device and build

- Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16, USB.
  The S23 Ultra was not connected.
- Before (18 Oct 23:07): controller `9b1e8fd087cd7a327c7966527dee406daf33e0a89a1cf5b2ca25494b41458492`,
  worker `f0ba028674ca357fbb53f269741a72b76e62d3601a2a0b7c26b56f7c13f37a30`,
  controller test `c4d227f7ae0ff227d3253fbaf806d1cbd5bae59a07e8cb4f6ceb7bcd2466395b`.
- Installed with `adb install -r` (debug installer, data kept). Final:
  controller `5177211bdd484441835109e9532794677cb1c693736abad478e7c236695ae911`
  and controller test `a776cfd27e1e882f47f4a841b3da7d6e1cbdb5fb01ffca4062acb4ede2873306`,
  both equal to the build at `28dcd48`. The worker was not reinstalled (no
  worker change). Intermediate controller builds `7d4625…`, `b4e2da…` (a
  reverted UiState experiment) and `526de4…` were installed along the way.
- Phone lock held for each session; the 18 Oct session's holder expired
  with the session, the 19 Oct holder was released at 08:40.

## Cases on the phone

Disposable project "Compaction 43 test" (`appfdda14ca4a284b27a051330928c584d0`),
created through the menu. Model DeepSeek V4.1 Flash only. Screenshots are in
`toolchains/spec35/43/` (local, not committed).

| # | Case | Expected | Actual | Result |
| --- | --- | --- | --- | --- |
| 1 | Saved context 24,000 via the model-choice prefs, Send "Build a unit converter app…" (18 Oct 23:09) | compaction once the run grows | no compaction: 7 requests, app built. The screen showed the default model name, so the saved choice was not in use (see finding 1) | not shown |
| 2 | Same, context 20,000, Send "Read every file… History… Settings…" (23:13) | compaction | no compaction, 15 requests, app built; same cause | not shown |
| 3 | With a UiState equality fix, saved choice used (23:22) | run with the saved choice | HTTP 404 before any work: the saved catalog price $0.04/$0.144 is below every pinned provider (finding 2); fix reverted | blocked |
| 4 | Saved free model `cohere/north-mini-code:free` (23:24) | free run | HTTP 404: no free endpoint lists `parallel_tool_calls` (finding 3) | blocked |
| 5 | `CompactionRunProbe` at 24,000, "Read every file of the app again first…" (19 Oct 08:31) | compaction, run continues | three compactions (11 s, 8.4 s, 12 s). After each one the model re-read the files, because the kept request told it to. Then "Invalid model response (tool count)" (finding 4) | compaction pass; run failed |
| 6 | Probe at 32,000 after the wording fix, Favorites request (08:34) | compaction | first reply asked for more than 16 tool calls; run ended at once (finding 4) | blocked |
| 7 | Probe at 32,000, Favorites request with "Read only the files you need, at most four at a time" (08:35–08:36) | compaction shown as a builder step; run continues and builds | 12 reads and 3 files created, then **Summarizing earlier steps to make room** with moving dots and Stop (`20-summarizing-live.png`), then **Summarized earlier steps to make room └ 5.8 s** and Thinking (`21-summarized-continuing.png`). It read 4 files it needed to edit, made 7 edits, **Built on this phone in 1.8 s**, and wrote a summary followed by "Your app is ready." (`22-run-end.png`). Revision 3, artifact `4b5454b4…` (33,787 B). | pass |
| 8 | Delete the disposable project | removed; user's project selected and unchanged | Delete this project → Delete project (`31-delete-dialog.png`); app switched to "Main project" (`32-after-delete.png`); `files/projects` holds `app511e349…` and `counter-alpha`; the user's 24 project files hash the same before and after (list hash `71633da6…`) | pass |

The probe (`compactionRun=<project name>`, `contextTokens`, `messageB64`)
refuses unless the named disposable project is selected. It replaces only the
screen's in-memory model with DeepSeek V4.1 Flash at the built-in price and
pinned providers but a lowered context, as if the catalog listed that length.
It types the message, taps Send through accessibility and waits for the run
to end. Live frames came from `adb exec-out screencap` every 2 s, because
`uiautomator dump` does not work while the instrumentation holds UiAutomation.

Test assistance, restored: cases 1–4 edited the controller's
`shared_prefs/model-choice.xml` through `run-as`, with the app stopped. The
original was backed up first and restored, and its hash `e5ee99cf…` was the
same at the end. The backup was then deleted.

## Requests and cost

`SmallwrightRun` lines: case 1, 7 requests, $0.0072502080. Case 2, 15
requests, $0.0090745312. Case 5, 6 requests, $0.0042439880. Case 7, 11
requests, $0.0075934208. Cases 3 and 4 were rejected by OpenRouter (HTTP 404)
before any model ran. Cases 5 and 6 each also had one reply refused for its
tool count, which the app does not count; its cost is unknown, and small.
Total counted: 39 requests, $0.0282 USD. All requests used DeepSeek V4.1
Flash, except one free-model request in case 4 that was rejected.

## Findings for other tickets

1. **The saved model choice is ignored when it is DeepSeek V4.1 Flash.**
   `ModelChoice.equals` compares ids, and `UiState.model` is a Compose
   `mutableStateOf` with structural equality. Setting the saved choice
   (catalog price $0.04/$0.144, now the context length) over `DEFAULT` with
   the same id is a no-op, so runs always use `DEFAULT`'s price caps and
   context. The #42/#45/#46 record's screenshot shows the same default label.
   `referentialEqualityPolicy()` fixes it (host test written and run), but it
   exposes finding 2, so it is not included here.
2. **The catalog price is below every pinned DeepSeek provider.** With the
   saved price as `max_price` and `provider.only` = deepinfra, together,
   nebius ($0.14/$0.42, $0.30/$1.20, $0.30/$1.20 per OpenRouter's public
   endpoint list), OpenRouter answers 404. The pricing policy needs a decision
   before finding 1 is fixed.
3. **No free model works today.** Unpinned requests send
   `require_parameters: true` with `parallel_tool_calls`, and none of the 11
   free tool models' endpoints I checked lists `parallel_tool_calls`. Every free request
   I tried (case 4) is a 404. This likely affects `A33SuiteProbe` too.
4. **More than 16 tool calls in one reply ends the run** (`RunTools.MAX_CALLS`,
   "Invalid model response (tool count)"), with the reply paid and not
   counted. On a 17-file project, DeepSeek asked to read every file at once.
   A tool result telling the model to use at most 16 calls, rather than ending
   the run, would fit ADR 0003.
5. A compaction loop is possible when the kept request itself asks for work
   that refills the context, as in case 5. The continuation note now says that
   what the summary reports as done is done. The "no longer fits" check stops
   the worst case, and #49's run limits are the person's other guard.

## Not run, and why

- A natural compaction at DeepSeek's real 1,048,576-token context: it needs
  about 750,000 tokens of conversation and would cost dollars. The phone case
  lowers the context in the probe instead.
- The "no longer fits" ending, and a failed summary request, on the phone:
  host tested only.
- Compaction with a model picked from the catalog through the UI. Blocked by
  findings 1–3.
- Installing or opening the generated app from case 7. Not part of this ticket.
