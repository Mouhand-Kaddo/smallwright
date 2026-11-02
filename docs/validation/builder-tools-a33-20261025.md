# A33: builder tools, sidebar and interview (2026-10-25)

Date: 2026-10-25 Dubai. Device: Galaxy A33 5G, Android 16/API 36, USB debug.
Branch `spec35/w66-int`. Final controller debug APK `dae36209…` at `5a4c2f9`
(androidTest `abeedbdd…` at `5ecd687`). Installs were `adb install -r`, keeping
app data. Model requests were free-only (Nemotron 3.5 Super VL (free)), except
the DeepSeek V4.1 Flash runs approved for step-run tests and one paid request
named under Limits. Screenshots stay local under `toolchains/spec35/66/`.
These are agent observations, not human acceptance.

## What the branch adds

- No app-side output limit: a request asks for the model's own ceiling, and a
  reply cut off by length is dropped with a note to the model instead of
  ending the run (three cut-offs in a row end it).
- Menu as a sidebar: projects as folders with their chats.
- The builder interviews first with question cards (`ask`), plans in everyday
  words (`update_plan`), keeps a project brief (`write_brief`), finds libraries
  itself, and makes app pictures (`make_image`).
- Tools: `lookup_code`, `fetch_web` (and web search for paid models),
  `read_crash_report`, `look_at_app`, and Undo of the builder's last change.

## Earlier checks on integration builds (before `95b4b67`)

Passed on the A33 on earlier builds of this branch: the output-limit change
with DeepSeek; the sidebar; an interview with question cards, plan and build
(project Water66); "Tell the builder" from a crash and `read_crash_report`;
`look_at_app` with a description that matched the screen; the plan checklist;
the brief page; Undo with its confirmation and chat note; "(free)" labels.

The fix2 manifest rule refused every generated build (the crash dialog's own
task affinity). `95b4b67` allows exactly the builder's affinities; the on-phone
build probe then passed (`builderLink=look,crash,starter`).

## Checks on the final builds

| Case | Expected | Actual |
| --- | --- | --- |
| Startup after `95b4b67` | Projects open | **Failed**: "Could not verify local project and run history"; log showed `Project is busy` from a main-thread revision read. Fixed in `55a5055`. |
| Startup after `55a5055` | All six projects, Notes66 opens | Pass: Notes66 at revision 3; sidebar lists all six projects |
| Notes66 "Build from this source" | Builds | Pass: "Your app is ready.", Install offered |
| Crash dialog, Close (Water66 "Crash test") | No report sent | Pass: saved report unchanged (17:30); builder back in front |
| `look_at_app` run | Look, describe | Pass: first call named an undeclared activity and got the list; second look described the real screen |
| Question card, empty prompt | Send drawn as outline | Pass: outline while the card waits; filled once text is typed |
| Run notification tap | Opens the builder on the run's chat | Pass: Water66 chat |
| Recents | Builder listed | Pass: "Smallwright" once |
| Answer card, run edits and builds | Change, build, Undo | Pass |
| After the run | "Your app is ready." and Install | **Failed** once: line hidden (chat redrawn before the new revision). Fixed in `25a04bd`; next two runs (plain and card-first) showed both. |
| Undo, force-stop, relaunch | "Build the app" still offered | Pass |
| Undo, then "Build the app" | Builds | **Failed**: "Recovery snapshot is missing starter files" (the model had deleted `res/layout/empty.xml`). Fixed in `6b6df01`; then built, Install offered. |
| Build probe with the component rule (`5ecd687`) | Passes the artifact check | Pass |
| Final build: run that changes a file | Undo, ready line, Install | Pass |

Host: 826 tests (controller and worker) and lint (0 errors) at `5a4c2f9`, 828 and 0 at `886e3c3`. Two
independent reviews (Claude Sonnet 5.5 and beam-free), two rounds each, found
no blocker; their should-fix items are fixed.

## Paid runs (approved by the user, 2026-10-25)

Model DeepSeek V4.1 Flash 1017, new test project Plants66. Costs are as
OpenRouter reported them.

| Run | Result | Cost |
| --- | --- | --- |
| "An app to remember when to water my plants" | One round of four question cards, a 6-step plan, a build; 25 requests | $0.032 |
| App icon and empty-list picture | **Failed**: every image refused as "not a PNG"; the model drew vector art instead. Fixed in `a3464af` (PNG, JPEG or WebP accepted; on-phone icon probe passed with a JPEG icon and a WebP picture) | $0.207 |
| Same request after the fix | Pass: Gemini made a watering-can icon and a potted-plant picture; build passed | $0.069 |
| Web search for watering intervals | Pass: one request; the reply cites Virginia Tech's extension guide. No "Searched the web" step showed | $0.022 |
| Same search with a temporary log of the stream (approved 2026-10-25) | The streamed usage reports `server_tool_use_details.web_search_requests` (2), not the documented `server_tool_use`; replies also stream `url_citation` annotations | $0.016 |
| DeepSeek V4.1 Flash search after the fix (APK `d97dee61…`) | Pass: a search step above the cited answer, detail "2 searches"; its title counted requests ("once"), fixed so it counts searches; on APK `99cc8efe…` the same chat reads "Searched the web twice" | not read |

The interview asked one round and then built without asking whether to build.
The user then asked that its depth follow what the model still does not know,
as in the grilling skill. `10bd83e` drops the fixed "three or four questions"
and always ends a new app with a plan and its own build question. Checked with
DeepSeek V4.1 Flash (approved for step runs) in fresh projects, stopping at the
build question:

| Request | Before `10bd83e` | After |
| --- | --- | --- |
| "I want a notes app." | One round of 5, then the build question mixed with a look question | One round of 5, then the plan and the build question alone |
| A detailed counter app (name, buttons, colours, no pictures) | No questions; built at once | No questions; the plan and one build question |

Image review fixes (`e701c87`): an image Android cannot recognise is refused
instead of throwing; `image/jpg` and upper-case types are accepted. On-phone
decode probe (`-e appIconBuild decode`): JPEG and WebP accepted; GIF, unknown
bytes, 32×32, a 5000×5000 header and a truncated JPEG refused.


## Not run on the phone

- The crash dialog's "builder did not accept the report" text: needs a report
  the builder refuses.
- Stop during a look, and the 45 s look bound: need a slow or hung app. Covered
  by host tests (`LookWaitTest`).
- The look picture in the step: kept only while the run lasts; the free-model
  look ended within about two seconds.

## Limits

- One paid request ran without approval earlier in this work (a paid model
  with the same name as a free one). "(free)" labels now tell them apart.
- Main-thread project reads can still meet "Project is busy" while a
  background check reads the project. They fail safe with a message and the
  controls recover on the next refresh.
- Source export still accepts only the two files of the old one-file project,
  so a multi-file app cannot be exported as source.
