# Run requests work for the saved model and for free models (#51), host and Galaxy A33, 2026-10-19

Issue #51 (found while testing #43, see
[compaction-a33-20261019.md](compaction-a33-20261019.md)): the saved DeepSeek
choice was ignored, fixing that made every DeepSeek run a 404 because of
`provider.max_price`, no free model worked because of `require_parameters`
with `parallel_tool_calls`, and a reply with more than 16 tool calls ended the
run.

## What changed

- `UiState.model` uses `referentialEqualityPolicy()`, so a saved choice with
  the same id as the built-in default replaces it, with its own price and
  context length.
- Requests no longer send `provider.max_price`, `provider.require_parameters`
  or `parallel_tool_calls`. DeepSeek V4.1 Flash keeps
  `provider.only` = deepinfra, together, nebius. Every request keeps
  `reasoning` (`effort` low, `exclude` true), `tool_choice` auto, `tools` and
  `max_tokens`. Other models send no `provider` object.
- `RunTools.parse` no longer refuses a reply for its call count. `StepRun`
  runs the first `RunTools.MAX_CALLS` (16) calls and answers each further call
  with the tool result `RunTools.NOT_RUN` ("Not run: a reply runs at most 16
  tool calls. Ask for this call again in your next step."). The reply is
  counted like any other.

## Why this routing (OpenRouter docs fetched with ctx7, 2026-10-19)

- Provider routing, "Requiring Providers to Support All Parameters": with
  `require_parameters: true` a provider that lacks any parameter in the
  request is not used at all. By default providers ignore parameters they do
  not support, and `tools` is already a soft preference: if some of a model's
  providers support tools and others do not, only the supporting ones are
  used; if none do, the model is still used. So `require_parameters` adds
  nothing for tools and can only remove providers. It was added for the old
  forced `replace_source` call; the run now sends `tool_choice` auto.
- Tool calling: `parallel_tool_calls` defaults to true and is only a request
  for fewer calls; the run already handles several calls per reply.
- `max_price` is "the maximum price you want to pay ... per million tokens". The
  catalog lists the cheapest endpoint (DeepSeek V4.1 Flash: $0.046/$0.12, the
  cheapest of 38 endpoints), below the three pinned providers ($0.14/$0.42,
  $0.30/$1.20, $0.30/$1.20), so it cannot be combined with `only`. ADR 0003
  leaves the person's cap to Settings (#49).
- Reasoning: the public model list (`/api/v1/models?supported_parameters=tools`)
  shows all 15 free tool models and every DeepSeek V4.1 Flash endpoint list
  `reasoning`, so it would not have excluded them under `require_parameters`.
  79 of 376 tool models do not list it (for example
  `mistralai/devstral-2512`); without `require_parameters` they ignore it.
  The picker only offers models listing `tools` and `tool_choice`. Two free
  models (`thinkingmachines/inkling*`) lack `tool_choice` and are not offered.
- No `parallel_tool_calls` appears in any free endpoint's supported
  parameters (checked cohere/north-mini-code, google/gemma-4-26b-a4b-it,
  nvidia/nemotron-3-super-120b-a12b, thinkingmachines/inkling-small), which
  was the 404.

## Dead code check

`UsageBudget.MAX_REQUEST_COST_USD` no longer exists. The catalog price is
still used: `ModelChoice.maxReplyCostUsd()` (a free model must report cost 0, a
paid reply may not exceed a full request at the listed price, at least $0.05),
the picker's price label, and `ModelChoiceStore`. Nothing became dead.

## Host

Branch `spec35/t51-run-requests`, code commit `3e1e341`, from
`spec35/integration` at `4b47d10` (had not moved). Through `spec35/gradle.sh`:
`spotlessApply :controller:testDebugUnitTest :worker:testDebugUnitTest
:controller:assembleDebug :worker:assembleDebug
:controller:assembleDebugAndroidTest :controller:lintDebug` passed: 394 tests,
0 failures, 0 skipped; lint 0 errors.

Tests written first and seen failing (8 failures) before the change:

- `UiStateModelTest`: a saved choice of the default's id is the one in use.
- `ModelChoiceTest.requestsThePickedModelWithoutAnyProviderRouting`,
  `aFreeModelRequestHasNoPriceCapOrParameterRequirement`,
  `defaultModelKeepsItsPinnedProviders`, `ModelProtocolTest` and
  `StepProtocolTest.sendsTheConversationWithTheRunToolsAndAutoChoice`: request
  bodies hold no `max_price`, `require_parameters` or `parallel_tool_calls`;
  DeepSeek keeps only `{"only":[...]}`.
- `StepProtocolTest.aReplyWithMoreCallsThanTheRunLimitIsKeptWhole` and
  `StepRunTest.aReplyWithMoreCallsThanTheLimitRunsTheFirstAndAnswersTheRest`:
  18 calls in one reply, the first 16 run, 2 get the not-run result, the
  outcome counts 2 requests and $0.0002.

## Device and build

Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16, USB. The
S23 Ultra was not connected. Phone lock held for the session.

- Before: controller `47da46aea83535287b01d518e2c73acd72ee65aa5b47c88ea8e28ebac7d8d310`.
- Installed with `adb install -r` (data kept): controller
  `6fe39d1f245c6b5879307b7ff52db250a3f83156c016772d6ad5d61c20d76aaf`, equal
  to the installed `base.apk` afterwards. Worker not reinstalled.

## Cases on the phone

Disposable project "Requests_51_test" (`appd3b87123d1f64c3e850bd3c5e2702cb0`),
created and driven through the app's own screens (uiautomator taps, no
instrumentation, no prefs edits). Screenshots are in
`toolchains/spec35/51/` (local, not committed).

| # | Case | Expected | Actual | Result |
| --- | --- | --- | --- | --- |
| 1 | Pick `cohere/north-mini-code:free` in the model picker (`02-picker-search.png`; the unfiltered list showed the saved DeepSeek row at $0.046/$0.12), Send "Make the button text Tap me. Then build." | free run completes | Listed files, read, edited MainActivity.java, "Built on this phone in 1.3 s", answer and "Your app is ready." (`03-free-running.png`, `04-free-end.png`). `SmallwrightRun`: 5 model requests, reported cost 0 USD | pass |
| 2 | Pick the saved `deepseek/deepseek-v4.1-flash` ($0.046 in, $0.12 out) in the picker, Send "Make the button text Press me. Then build." | run completes | Read three files, edited, built in 0.9 s, "Your app is ready." (`05-deepseek-picked.png`, `07-deepseek-end.png`). `SmallwrightRun`: 4 model requests, cost 0.0006194944 USD | pass |
| 3 | Source shows the edit | button text "Press me" | tools page source has `increment.setText("Press me")` | pass |
| 4 | Delete the disposable project, select the user's project | removed, user's project untouched | Delete this project, Delete project (`08-delete-dialog.png`, `09-after-delete.png`); `files/projects` holds `app0a6c0be0…` (another implementer's), `app511e349b…` (the user's) and `counter-alpha`; "Main project" selected again (`10-user-project-selected.png`) | pass |

The model shown on the screen at the end is DeepSeek V4.1 Flash, the saved
choice. The saved price ($0.046/$0.12) is what the picker lists for the saved
row; the run's success with it is the evidence that the saved choice no longer
404s (case 3 of the #43 record).

## Requests and cost

Free: 5 requests, $0. Paid DeepSeek V4.1 Flash: 4 requests, $0.00062. The user's
project and "Run interruption test 44" were not used.

## Not run

- The over-limit reply on the phone: host tests only; neither model asked for
  more than 16 calls in these short runs.
- A free model other than North Mini Code, and other paid models.
- Long runs against the saved DeepSeek choice (see the risk below).
- S23 Ultra.

## Risk for the coordinator

`ModelChoice.maxReplyCostUsd()` for the saved DeepSeek choice is a full
request at the saved price ($0.046/$0.12 per million tokens), floored at
$0.05, but the pinned providers charge up to $0.30/$1.20. A reply that
reports more than $0.05 (about 160,000 prompt tokens at $0.30) is refused
as "Invalid model response (usage)" after it was paid, and the run fails.
Short runs are unaffected. A fix (for example compare against the dearest
pinned provider, or drop the paid cap now that ADR 0003 leaves limits to
Settings) changes an existing safety test, so it is not in this change.
