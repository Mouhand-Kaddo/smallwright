# Thinking level and prices per 1M tokens, on the Galaxy A33 (2026-10-20, #61)

## Setup

- **Phone.** Galaxy A33 `<A33 serial>`, checked with `adb devices`.
- **Controller.** `6a68ff2cf03be3e08caa1ed96e98f970f635fa078dc24d6e041036a0c459228e`, built from 53fea5c. It replaced `4891543f…779aa7` (#58), installed with `adb install -r`, which keeps the app's data. The installed `base.apk` hash matched after the install. The worker was not changed and not reinstalled.
- **Host chain.** `spotlessApply :controller:testDebugUnitTest :worker:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest :controller:lintDebug`: controller 441 tests and worker 91 tests, 0 failures, 0 lint errors.
- **OpenRouter docs.** Fetched with Context7 (`/websites/openrouter_ai`, reasoning tokens guide): `"effort": "none"` disables reasoning entirely. Off sends that; Low, Medium and High send `low`, `medium` and `high`.
- **Model.** The saved model choice on the phone was GLM 5.3 FlashX (`z-ai/glm-5.3-flashx`), not DeepSeek. The session picked DeepSeek V4.1 Flash for its two requests, the only paid model approved for tests, and then picked GLM 5.3 FlashX again from the list. The saved choice afterwards has the same id, canonical id, name, prices and context as before, plus the new `reasoning` flag.
- **Project.** Disposable project Think61, deleted through the app at the end. The app then selected "Main project" again. That project was not changed.
- **Script.** `/tmp/t61/s1.py`, one locked phone session, log at `/tmp/t61/s1.log`. Screenshots are in `toolchains/spec35/61/`. The phone first showed Samsung's "Accidental touch protection" screen, which the script dismissed with the swipe the screen asks for.

## Cases

| Case | What | Expected | Actual |
| --- | --- | --- | --- |
| 1 | The saved GLM choice from an older build | No `reasoning` flag is stored, so no thinking control | Only "GLM 5.3 FlashX ▾" under the prompt (`00-saved-model-before.png`) |
| 2 | Model page | The price per 1M tokens, free stays free, no estimate note | "Z.ai · $0.37 in, $1.25 out per 1M tokens", "DeepSeek · $0.035 in, $0.011 out per 1M tokens", free models "free" (`03-model-picker-per-1m.png`, `03b-model-picker-scrolled.png`) |
| 3 | DeepSeek picked | "thinking: low ▾" beside the model | Shown (`01-control-under-prompt.png`). The model name shortens to "DeepSeek V4.1 Fla…" to make room. |
| 4 | Tap the control | off, low, medium, high, with low marked | Row of four, low highlighted (`02-level-choice-open.png`). Back closes it. |
| 5 | Pick high, force-stop and reopen the app | The level is kept | "thinking: high ▾" after the restart (`04-high-kept-after-restart.png`) |
| 6 | Send "what is 17 times 23 answer in one short sentence and change nothing" at high | One request runs | "Thought for 2.8 s", reply "17 times 23 is 391 …" (`05-high-reply.png`). Log: `Run ended after 1 model requests; reported cost 0.00048888 USD; 3348 prompt tokens (0 cached), 48 completion tokens` |
| 7 | Same message at off | One request runs, less thinking | "Thought for 1.3 s", same reply (`06-off-set.png`, `07-off-reply.png`). Log: `Run ended after 1 model requests; reported cost 0.00048118 USD; 3380 prompt tokens (0 cached), 19 completion tokens` |
| 8 | Level back to low, project deleted, GLM picked again | The person's project, their model, low | "Main project", "GLM 5.3 FlashX ▾ thinking: low ▾" (`08-back-on-user-project-low.png`). Think61 is no longer in the project list. |

Total reported cost: $0.00097 for 2 requests.

## Limits

- The app does not log the effort it sends. The body for each level is checked by host tests (`ModelChoiceTest.sendsTheThinkingLevelAsTheReasoningEffort`, `everyRequestOfARunKeepsTheLevelTakenAtTheTap`). On the phone the evidence is indirect: high used 48 completion tokens and 2.8 s, off 19 tokens and 1.3 s, for the same question.
- The picker shows OpenRouter's listed price. DeepSeek runs are pinned to providers that charge more than the listed price (#43 finding 2). The high run cost about $0.15 per 1M prompt tokens, while the list says $0.035.
- `ModelChoiceStore` has no host test, because the controller has no Android preference test setup. Reading a stored level is tested through `ReasoningLevel.stored`, and the phone kept "high" across a restart.
- A model saved by an older build shows no thinking control until it is picked again from the list, because its `reasoning` flag was not stored. The default DeepSeek choice counts as supporting reasoning without the list.
- Medium was not sent on the phone. Its request body is checked by the host test.
