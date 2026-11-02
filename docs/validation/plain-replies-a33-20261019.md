# Plain, rendered builder replies on the Galaxy A33, 2026-10-19

Issue #52 (parent #35): builder replies showed raw Markdown (`**bold**`,
backticks, `- ` lists) and programmer terms. The chat now renders the small
Markdown subset, and the build contract asks for a short plain closing reply.

## What changed

- `ui/ReplyText.kt` (new): reads bold, inline code, bullet (`- ` or `* `) and
  numbered (`1.` or `1)`) lists and paragraphs, using only Compose's
  `AnnotatedString`. Marks are dropped; anything else (headings, links, images,
  tables, HTML, unmatched or cross-line marks) stays exactly as written. Inline
  code uses the mono family, `Ink.prompt` text on an `Ink.panel` background; bold
  uses the bold mono weight.
- `ui/Chat.kt` `BuilderReply`: draws the blocks, list markers hanging at the left.
  Saved chat history is plain text with marks, so older replies render the same way.
- `ContextFilter.BUILD_CONTRACT`: the closing reply is "a short plain summary of
  what the app now does for the person, in everyday words, without file, class or
  library names unless the person used them, and with a question at the end only
  when you need a decision", and may use bold, code, bullets and numbered lists
  and nothing else from Markdown.
- Tests: `ReplyTextTest` (5), `ContextFilterTest` contract assertions.

## Device and build

- Phone: Samsung Galaxy A33, serial `<A33 serial>`, Android 16, USB. The S23 Ultra
  was not connected.
- Host chain on the merged tip (integration 2dd1114 merged): spotlessApply,
  controller and worker unit tests (controller 349, worker 81; 0 failures, 0
  skipped), controller and worker assembleDebug, controller assembleDebugAndroidTest,
  lintDebug (0 errors).
- Controller debug APK sha256
  `f0e4211d73243c2d64eeca5a31880ed319ae0e0f8969c1410929053323b55ce1`; installed
  with `adb install -r`, installed base APK hash equal. The APK installed before it
  was a build of the integration branch (sha256
  `0ced0ce3c7c3b700aa71e90332256c63b4c702b650c63b6caef02e0940fa94f3`, old
  rendering and old contract), used to produce a reply the way older versions did.

## Cases on the phone

All in a disposable project, Plain52, deleted at the end. Model
`deepseek/deepseek-v4.1-flash` (paid, approved).

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| Old reply with raw marks | Old build: "Make a simple counter app with a big number and Plus and Minus buttons." | Closing reply shows `- ` lines and `**Plus**` with the marks; `01-before-old-version-raw-marks.png` | reproduced |
| Same saved reply, new build | Install new build in place, reopen the same chat | Bullets with a dot, Plus, Minus and Reset in bold, no marks, question as its own paragraph; `02-same-chat-new-version-rendered.png` | pass |
| New run's closing reply | New build, same chat: "Now add a Reset button that sets the number back to zero and make the background dark." | Reply names no file, class or library; says the app now has a dark background, lists what it does, ends with one question (restyle the buttons?); `03-new-run-closing-reply.png` | pass |
| Cleanup | Delete Plain52 from project tools, select "Main project" again | Menu lists only the three earlier projects; the user's project untouched, only viewed | pass |

Screenshots: `toolchains/spec35/52/` (local, not committed).

## Findings and limits

- The model still wrote "centre" and button names the person used; it named no
  file or class. One sample from one model; the contract is a request, not a check.
- Nested lists are shown flat, and a list item's continuation line becomes its own
  paragraph. Headings (`# x`) show as written.
- Bold in the mono family is a modest weight step; it is readable, not loud.
- Requests: two runs, one step per model request plus a few; the request count
  and cost were not read (the app does not show spending and the key is never
  read). Each run was a small app, so the spend is cents at DeepSeek flash prices.
- The 'ask before adding libraries' question row (#50) was merged and is
  unchanged; not exercised here.
- Not run: S23 Ultra; a Markdown-heavy reply with numbered lists on the phone
  (numbered lists and code spans are covered by host tests only).
