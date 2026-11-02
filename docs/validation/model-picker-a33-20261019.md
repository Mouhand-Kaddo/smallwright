# Model picker for everyone on the Galaxy A33, 2026-10-19

Ticket #55 (spec #53). The model page now shows short names with the maker as a
small dim word, no id, a plain price per app, and the sections "in use",
"suggested", "free" and "all models" (cheapest per app first). A search shows one
flat list. The label under the prompt and the setup button use the same short
name.

## Device and build

- Phone: Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, USB. The S23
  Ultra is no longer tested.
- Branch `spec35/t55-models`, merged with `spec35/integration` at `c2037b2`
  (#56 type scale, Stop button, italic). The merge had no conflict.
- Host chain through `spec35/gradle.sh`: `spotlessApply :controller:testDebugUnitTest
  :worker:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug
  :controller:assembleDebugAndroidTest :controller:lintDebug` passed. 450 tests,
  0 failures, 0 errors; lint 0 errors.
- Measured run build (token totals logged, estimate constants still 0): controller
  `1e3c09f1c021ac55ca03010f5d4100d509871e53c15cb7c5a67aee70ccbfd5ff`, worker
  `b33272acc67db6db205285690ee457ec908791eb0f9306c0696048b4b10858b4`.
- Final build used for the screenshots: controller
  `2fbebefbedd81fcfe547b65d66bf0ec8d474b027512f0d44b5f2079288955f12`, worker
  `b33272acc67db6db205285690ee457ec908791eb0f9306c0696048b4b10858b4`, test APK
  `b4e607f31da191da96823ef9b8e9d9dc0a61b41411d2b05be9ad9f3788fdc506`.
- Every install was `adb install -r`, and each phone session was one locked
  command that installed, checked the hashes, then tapped. Before each
  session another round 2 agent's controller was installed (`ee925a4c…` at
  13:51, `d976f599…` at 13:54 and 14:11). After each install the installed
  `base.apk` hash equalled the built APK.

## Measured run for the price estimate

Disposable project "PickerMeasure55", empty at revision 0. Model DeepSeek V4.1
Flash (`deepseek/deepseek-v4.1-flash`), the saved choice. The prompt was the
reading list prompt of the final spec 35 check (read from its screenshot
`toolchains/spec35/final/06f-chat-steps-top.png`, since that record does not
quote it). It was checked character for character in the field before Send:

> Build a reading list app. Use Material cards in a scrolling list. The first
> screen lists books with title and author; tapping a book opens a second screen
> that fetches its description from https://openlibrary.org/search.json?q=<title>
> over the internet and shows the first result's author and first publish year.
> Save the list on the phone.

Send was tapped at about 13:56. The controller's `SmallwrightRun` line at
13:58:50.252:

"Run ended after 15 model requests; reported cost 0.0092879416 USD; 153186
prompt tokens (130048 cached), 13101 completion tokens"

The run built the app and ended with "Your app is ready." and Install on this
phone (`01-home-label`). The generated app was not installed.

| Measure | Value |
| --- | --- |
| Model requests | 15 |
| Prompt tokens | 153,186 (130,048 of them read from the provider's cache) |
| Reply tokens | 13,101 |
| Cost OpenRouter reported | $0.0092879416 |

The constants `ModelChoice.TYPICAL_PROMPT_TOKENS` = 150,000 and
`TYPICAL_COMPLETION_TOKENS` = 13,000 are these counts rounded to two figures.
The estimate is listed prompt price × 150,000 + listed reply price × 13,000, and
it is worded "about …" ("well under a cent", "about half a cent", whole cents
below 10¢, two figures above). At the catalog's DeepSeek price ($0.0448 and
$0.08 per million) that is $0.0078, shown as "about 1¢ per app". The reported
cost, $0.0093, is also about 1¢.

For comparison, the final spec 35 check ran the same prompt in 8 requests for
$0.0046692072. No token counts were logged then. One run is all the estimate
rests on.

## Cases on the phone

Screenshots are in `toolchains/spec35/55/` (local, not committed). Before-shots
are in `before/`.

| Case | Expected | Actual | Result |
| --- | --- | --- | --- |
| Home label | short name under the prompt | "DeepSeek V4.1 Flash ▾" on PickerMeasure55 (`01-home-label`) and on the user's project (`05-user-project-selected`) | pass |
| Page top | one plain estimate line; "in use" with the highlight bar; maker dim; no id | "Prices are a rough guess of what building one small app costs.", then "in use": DeepSeek V4.1 Flash / "DeepSeek · about 1¢ per app" with the blue bar and panel, then "free" (`02-model-page-top`) | pass |
| Suggested | the suggested model, unless it appears earlier | No "suggested" heading: DeepSeek V4.1 Flash is the only suggested model and it is already "in use". The host test `ModelSectionsTest` covers the section when another model is in use | pass (empty section left out) |
| Scrolled | "all models" cheapest first | End of "free" (…, Free Models Router), then "all models": gpt-oss-20b, Ling 3.0 Flash, Mistral Nemo "about half a cent per app", then Mercury 2.5, Llama 3.1 8B Instruct, … "about 1¢ per app" (`03-model-page-scrolled`, `03c-model-page-all-models-more`) | pass |
| Search | one flat list, starting at the top | "gemini" typed after scrolling: the estimate line, then Gemini 4 Argon "about 43¢", Nano Banana 2.1 "about 32¢", Gemini 3.8 Flash "about 18¢", … with no headings (`04-search`) | pass |
| Model kept | `deepseek/deepseek-v4.1-flash` | Nothing was picked; the saved choice still reads id `deepseek/deepseek-v4.1-flash`, name "DeepSeek: DeepSeek V4.1 Flash" | pass |
| User's project | selected again, untouched | "Main project" selected at the end, nothing typed or sent in it (`05-user-project-selected`) | pass |

The first screenshot session found a bug: a search kept the list's earlier
scroll position, so the top matches were hidden. The list now scrolls to the
top when the search text changes, and `04-search` is from the fixed build.

## Not run, and limits

- No probe ran; the changes are UI wording and order, checked by the host tests and the screenshots.
- The estimate charges every prompt token at the listed prompt price. Most of
  this run's prompt was read from the provider's cache, which providers often
  bill lower. The listed DeepSeek price is also below the pinned provider's
  price, which is why the reported $0.0093 is above the $0.0079 the listed price gives.
- The disposable project "PickerMeasure55" was left on the phone with its run,
  as the coordinator set it up. Its built app was not installed.
- During the measured run my script's own log wait failed (the device `date`
  call split on a space). The script kept holding the phone lock while I read
  the `SmallwrightRun` line with a read-only `adb logcat -d` from outside it.
  Then I stopped the script.
