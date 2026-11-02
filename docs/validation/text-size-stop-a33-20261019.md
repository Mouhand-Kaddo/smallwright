# Smaller text and a plain Stop button on the Galaxy A33, 2026-10-19

Issue #56 (parent #53): the body text was too big for how much the builder
writes, and Stop was a red outlined rectangle the user found ugly.

## What changed

- `ui/Theme.kt` `Type`: body 13sp / 20sp (was 14/21), small 11sp / 16sp (12/18),
  action 14sp / 20sp (15/20), title 18sp / 24sp (20/26), display 24sp / 29sp
  (28/34). Still Cascadia Mono everywhere.
- `ui/ControllerScreen.kt`: `TerminalButton` gained `square`, which draws an 8dp
  filled square in the button's hue before the label. The square has no
  semantics, so the button is still read out as "Stop". Stop in `PromptLine` is
  now `outlined = false, square = true` in `Ink.failed`. Height (min 48dp, same
  padding) and the right-aligned position are the same as Send's.
- Comparison variant, local branch `spec35/t56-type-sans` (one commit on top, not
  for merging): `Type.reading` = `FontFamily.SansSerif` 15sp / 22sp, used for your
  bubbles and builder replies (list markers too). Steps stay mono 13sp. No font file.

## Device and build

- Phone: Samsung Galaxy A33 (SM-A336E), serial `<A33 serial>`, Android 16, USB,
  1080x2400, density 450, system font scale 1.15.
- Host chain on `spec35/t56-type` (base 19d2fd0) and again on the variant:
  spotlessApply, controller and worker unit tests (443 tests, 0 failures, 0
  errors, 0 skipped), controller and worker assembleDebug, controller
  assembleDebugAndroidTest, lintDebug (0 errors).
- Installed before: controller `e80d107c614230dbb2081555937802768dc9c6cf1b3fe74207c2fb044bfbe09a`.
- Main build (commit b3c77a6), first build used for the mono screenshots:
  `6015e0da1f90dc4b72286cad2df8ab36b3c6967f6c0cc99db497b91ecb0c8859`.
- Variant (commit af1c661): `dfa41d3bf2674980137723f9f5a405bf3bed900712a7ea08d81f836ef24d5d94`.
- Main build rebuilt from the same commit b3c77a6 and left installed:
  `b5e2e457fca1dbef1770966e65771a788fdd70c0ed8dc13dbb6a7aadf5d42930`. The debug
  build is not byte-reproducible, so its hash differs from the first build.
- Every install was `adb install -r`. After each install, the installed `base.apk`
  hash matched the APK. The worker was not reinstalled.

## Cases on the phone

All in a disposable project, Type56, created through New project and deleted at
the end through Project tools. The model was the free `cohere/north-mini-code:free`.
No paid request was sent.

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| Long reply, mono | Free request asking for a detailed numbered explanation | About 37 characters per full-width reply line (the ticket measured about 33 before). Bold, numbered and bullet lists render; nothing clips. `mono/01`, `mono/03` | pass |
| Steps and bubbles, mono | Scroll to the steps | Dots line up with 13sp lines; detail lines at 11sp; bubble fits. `mono/02` | pass |
| Prompt and cursor | Idle prompt | Chevron, hint and block cursor (9x18dp) sit on one line; nothing overlaps. `mono/01` | pass |
| Menu | Open the menu | Rows and section labels fit; no clipping. `mono/04` | pass |
| Settings | Open Settings, scroll | Fields, buttons, switch rows and notes fit. `mono/06`, `mono/07` | pass |
| Onboarding first page | Menu, How it works | 24sp display, typed lines and Get started fit. `mono/08` | pass |
| Stop during a working run | Send a free request, capture while Thinking | "■ Stop" in the failed hue, no outline. The word has the same bounds as Send ([877,2171][985,2225] px) and the row height does not change. `mono/05` | pass |
| Variant: same screens | Install variant with `install -r`, same chat | Replies and bubbles in the phone's sans-serif 15sp, about 41 characters per full-width line; steps stay mono. Menu, settings and onboarding unchanged. `sans/01`–`sans/08` | pass (for comparison) |
| Cleanup | Delete Type56, select "Main project", set the model back to DeepSeek V4.1 Flash, reinstall the main build | Project list shows only "Main project" and "Counter alpha". The user's project was only viewed. | pass |

Screenshots: `toolchains/spec35/56/mono/` and `toolchains/spec35/56/sans/`
(local, not committed). The variant's run-with-Stop frame (`sans/05`) comes from
a second short free request in the same chat, so its content differs from `mono/05`.

## Findings and limits

- No existing disposable chat held a long reply (the A33 had only the user's
  project and Counter alpha). So two free requests produced one: a build run that
  ended with "Invalid model response (tool arguments fields)", then a words-only
  answer. The variant used one more short free request.
- The free model's reply used `*italic*`, which `ReplyText` (#52) does not parse,
  so the asterisks show. This is out of scope here.
- Readout as "Stop" rests on the code (the square has no semantics) and on
  uiautomator showing one text node "Stop". TalkBack itself was not run.
- The 48dp minimum height is unchanged in code. The clickable node's bounds were
  not measured during a run.
- Stop is wider than Send (square plus gap), so its left edge sits further left.
  The right edge, the word's position and the height do not move. The model name
  keeps its full width at this font scale.
- Not run: the S23 Ultra (no longer tested); other font scales; TalkBack.
