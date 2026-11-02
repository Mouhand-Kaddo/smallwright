# One Send, answers in words and automatic fixes on the Galaxy A33, 2026-10-18

The user asked for the flow of Claude Code and Codex: one button that sends
everything, a model that can discuss an idea before building it, feedback as
a normal chat message, and build errors fixed with no button. The design is in
[ADR 0001](../adr/0001-terminal-controller-ui-in-compose.md), section "Update,
2026-10-17: one Send, answers in words, automatic fixes". AGENTS.md now says
the tap on Send approves that message and its automatic fixes.

## Device and build

- Phone: Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16,
  arm64-v8a, `ro.kernel.qemu=0`; read-only preflight `ready` on 2026-10-17 at
  23:56. The S23 Ultra was not connected.
- Controller debug APK sha256
  `ee69a5bc323862a19cae360b55667173bef601c53e6bc148363af4c7af2f3107`, test APK
  `8b65621c3c6d7a15faee55ce7fe582bef87ca989287d2e23a2eb6a9db0e8bfe6`, installed
  in place over USB with the debug installer on 2026-10-18 at 00:13, after the
  tracker_05 check. The signer (`19a7407f…`) matched the installed app, so
  project data was kept. The installed base APK hash equals the built one.
- Host chain: `./gradlew --offline spotlessApply :controller:testDebugUnitTest
  :controller:assembleDebug :controller:assembleDebugAndroidTest
  :controller:lintDebug` passed; 274 unit tests, 0 failures; lint 0 errors.

## New tests

- `SourceReplacementProtocolTest`: a chat reply in words returns the answer
  and sends `tool_choice` "auto" with the revision-pinned `replace_source`
  tool; a tool call returns the unexecuted proposal; an empty or null
  `tool_calls` list with text is an answer; long answers are shortened on a
  character boundary; empty, cut-off, wrong-revision, wrong-tool, over-cost,
  wrong-role and malformed replies are refused without echoing the context.
- `GenerationRunTest`: an answer ends the run `ANSWERED` with the reported
  tokens; the source, its revision and the build are untouched.
- `RunJournalTest`: `ANSWERED` is reached only from `EDITING`, at the same
  revision, and ends the run.
- `ContextPreviewGateTest`: chat history carries the builder's answers in
  order, up to 4 KB each, drops the oldest first, and leaves status lines out.
- `ContextFilterTest`: the build contract starts with the chat rule.

## Cases on the phone

| Case | Inputs | Observation | Result |
| --- | --- | --- | --- |
| Home after the update | launch on the user's project, which has a build fix left pending from before | one outlined Send beside the model name; prompt hint "Ask, describe an app, or say what to change"; no Fix the build error or feedback field; status "The last build failed before it was fixed. Send a message and the builder will try again." | pass |
| Menu | tap Menu | New project, New chat, Install on this phone, Project tools, Model, OpenRouter key, How it works; no fix row | pass |

Screenshots: `toolchains/alpha-goal-20261017/ui-single-send-a33/` (local,
not committed). Older chat lines still read "Tap Fix the build error (paid)",
because they are messages saved by earlier versions.

## Not run on a phone

- A live send. The open project on this phone is the user's own, with a
  build fix left pending. A message there ends that fix and sends a paid
  request with the user's key that changes their app; switching projects also
  ends the fix. So answers in words, building on request and the automatic
  fix loop have host tests only. The next suite run, or a message from the
  user, will exercise them.
- The changed `A33SuiteProbe`: no manual repair phases, feedback typed into
  the prompt, and a paid bound of four requests per send. It compiles.
- The ten older one-shot probes that tapped the removed buttons now look for
  the old labels by text, so they fail at once if run. They could not rerun
  before this change either.

## Independent review

Two reviewers read the change: Claude Sonnet 5.5 and GLM-5.3 Flash Free
(OpenCode). Both found that an automatic fix used the model picked at fix
time instead of the model of the Send tap; Sonnet rated it a blocker. Fixed:
the fix uses the model of the send that failed.

Other findings, all fixed:

- A message sent while a fix was pending only reached the model as history.
  Now that message ends the pending fix and is sent as a normal turn; its own
  build gets its own fixes. The fix is ended only after the new message's
  context is accepted.
- Switching chats during a failing build skipped the fix while the status
  said it was running. The fix now waits and starts when the chat is opened
  again, and the status says so. If it cannot start, the status says that.
- An answer with an empty `tool_calls` list was refused.
- History left out builder answers over 2 KB although answers can be 4 KB.
- A Stop after an answer hid the previous installable build.
- A crash report was sent again with every later message; it is now cleared
  once a run answers or builds.
- Send now refuses while a saved build waits for recovery.

A second Sonnet round on the fixes found no blocker. Dismissed: a reply with
finish reason `stop` and a real tool call is still refused, as before this
change. `RunJournal` formatting went into its own commit.
