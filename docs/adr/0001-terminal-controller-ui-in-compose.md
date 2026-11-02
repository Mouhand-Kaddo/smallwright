# 0001. A terminal-style controller UI in Jetpack Compose

Date: 2026-10-17. Status: accepted.

## Context

The controller screen is a programmatic Views form: a status label, a project
and chat spinner, a chat history box, a request box and eleven stacked
Material buttons in capitals, with two more sections folded underneath. It
was built for the compiler and security work, and it reads that way. The user
asked for a screen for people who are not developers, inspired by the
terminal the way Omarchy is: one typeface, a dark calm surface, text that
reads top to bottom, colour that carries meaning, and menus that are lists.
They rejected two directions by name: a generic chat-app clone, and a dense
screen for technical people.

The implementation plan allowed Kotlin and Compose for the controller from the
start (plan, "Tech Stack"). The phone-side evidence for the private alpha was
gathered by instrumentation probes that reach into the Views by reflection
(`EditText`, `Button`, `Spinner` casts) and click buttons by their text.

## Decision

### Stack

- Kotlin 2.4.10 with the Compose compiler plugin; Compose foundation and ui
  1.11.4 (the 1.12 line needs compileSdk 37; the project pins 36);
  activity-compose 1.12.0. Foundation and ui only. No Material library: Material defaults are the generic look the user
  rejected, and every control here is text in a box.
- Cascadia Mono, the typeface Omarchy ships as CaskaydiaMono, as a variable
  font under SIL OFL 1.1 (`toolchain/licenses/INDEX.md`). It is bundled in the
  controller only; generated apps are unchanged.
- The home screen is not composed while the menu or a page is open, so
  nothing underneath can take a tap or an accessibility action. The
  transcript's scroll position lives above it and survives the round trip;
  prompt focus and the keyboard do not.
- While the prompt holds words, Build it is the outlined action and the
  state action is plain text.
- Build it, Send my feedback and Fix the build error send at once, with the
  model shown beside Build it. There is no review dialog; see the update
  below. Other confirmations (delete, install, recovery) stay platform
  `AlertDialog`s with a dark theme.

### Design tokens

Colour. Tokyo Night lineage, as in Omarchy's theme of that name, with each hue
holding one meaning. Contrast against the background is at least 5:1 for text.

| Name | Hex | Meaning |
| --- | --- | --- |
| background | `#1A1B26` | the screen |
| panel | `#24283B` | menu rows, the prompt box |
| rule | `#636C99` | borders where structure needs them |
| text | `#C0CAF5` | body |
| dim | `#8A93BE` | metadata, hints, disabled |
| prompt | `#7AA2F7` | the prompt glyph, the one primary action |
| done | `#9ECE6A` | built, installed, accepted |
| waiting | `#E0AF68` | needs your OK, paid step, building |
| failed | `#F7768E` | a build or request failed |

Type. One family, Cascadia Mono, at five sizes: 12 small, 14 body, 15
semibold action, 20 semibold title, 28 bold display. Body line height 1.5.
Sentence case everywhere; no capitals-only labels.

Layout. One left-aligned column.

```text
┌──────────────────────────────────────────┐
│ notes app                   ● ready      │  top bar: project, state word
│ Build ready for installation at rev 7.   │  status line (the harness message)
├──────────────────────────────────────────┤
│ you                                      │
│ Make a local notes list with a title     │
│ and a short body for each note.          │
│                                          │
│ builder                                  │
│ Build ready for installation at rev 3.   │
│                                          │
│ ┌──────────────────────────────────────┐ │
│ │ Install on this phone                │ │  one primary action, state-driven
│ └──────────────────────────────────────┘ │
├──────────────────────────────────────────┤
│ > Describe the app you want_             │  prompt line, block cursor
│                              Build it    │
│ menu                                     │  the list menu, as Omarchy's
└──────────────────────────────────────────┘
```

Principles.

1. The prompt line is the memorable element. Everything else is quiet.
2. One primary action, named by what happens next: Build it, Install on this
   phone, It works, keep it, Fix the build error. The harness state machine
   decides which one shows; while one of them shows, Build it is plain text.
3. Everything else lives in the menu: a list with a highlighted row, the way
   Omarchy's Walker menu works. Actions come first (new project, new chat,
   install, fix, project tools, builder key), then the chats of the open
   project, then every project, so the short list is never below the fold.
   Project tools and the builder key are pages reached from it, not sections
   stacked on the home screen. A page has at most one outlined button; its
   other actions are plain rows.
4. Colour is information. A word in `done`, `waiting` or `failed` is the
   state; there are no icons.
5. The harness's own status messages are kept word for word. The probes assert
   on them, and they are honest.

### Review of the plan against generic defaults

- Near-black with one acid accent: no. The background is a navy ink, and four
  hues carry four meanings. Blue is the prompt, not a brand accent.
- Cards and pills: none. The only boxes are the prompt and the primary action,
  and both are rectangles with a one-pixel rule.
- Monospace for small data labels only: the brief asks for the terminal, so the
  whole screen is monospace, and the small size is used for metadata only.
- Capitals-only eyebrows, middle dots, trailing arrows: none.
- Chat-app clone: the user later asked for bubbles and a visible thinking
  state, the way Claude Code and Codex show work. Your words sit in a bubble;
  the builder's side is a terminal log of steps with coloured dots, not a
  second bubble. No avatars and no timestamps.

### Test seam

`MainActivity` keeps its logic and field names. Widget fields become small
Kotlin state holders with the same method names the Java code already calls:
`TextBox` (text, hint, enabled) for `EditText` and `TextView`, `Control`
(label, enabled, shown, click) for `Button`, `Picker` (items, selection) for
`Spinner`. The
Compose screen renders that state. Probes keep their reflection by name and
change their casts; button taps go through accessibility, which is also what a
person's finger does.

## Consequences

- The controller gains Kotlin, Compose and AndroidX. The worker and generated
  apps do not.
- Status strings and dialog text are unchanged, so the recorded evidence still
  describes the running app.
- Probes that click Views by text need the accessibility helper. The alpha
  suite chain is updated and rerun on the A33; older probes are updated to
  compile and are rerun as needed.
- A later step can restyle the approval dialogs in Compose once their content
  is covered by a probe that reads the Compose tree.

## Update, 2026-10-17: chat, model picker, onboarding

The user asked for four changes after using the screen: onboarding for new
users, a chat that shows the builder working, a model picker in place of the
cost note, and no "verified signed APK" text. They also asked that sending
skip the review dialog; AGENTS.md was changed to match.

- Chat. Your message shows at once in a bubble on the right. The builder's
  steps follow on the left, each a dot in its hue: "Thinking" with moving
  dots while the model works, then "Thought for 9.4 s"; "Called
  replace_source" with the file under it; "Building on this phone", then
  "Built on this phone in 2.1 s" or "The build failed" with the error count.
  The steps are saved in the chat as one `tool` message per run, so history
  shows them too. Replies are not streamed; the dots stand for the wait.
- Sending. A tap on Build it, Send my feedback or Fix the build error is the
  approval. The app still prepares the exact context at the tap, sends that
  context with the model picked at the tap, and refuses a stale revision.
  "Show what the builder will read" in project tools still shows the full
  context.
- Model picker. The model name sits beside Build it and in the menu; it opens
  a page with OpenRouter's public model list (no key needed), a search field,
  and each model's price. The list keeps models that accept forced tool calls
  and leaves out batch-only entries, aliases and variable-price routers. A
  request caps the provider price at the listed price (the highest
  time-of-day price), accepts a reply only from the picked model or its
  listed versioned name, and accepts a reported cost up to a full request
  budget at that price, at least $0.05. DeepSeek V4.1 Flash stays the default
  and keeps its pinned providers.
- Onboarding. Three steps on first launch without a saved key: what the app
  does (the lines type out once, unless the phone has animations off),
  connect OpenRouter, pick a model. "How it works" in the menu opens it again.
- Words. Success says "Your app is ready. Tap Install on this phone to try
  it." The cost notes and "(paid)" labels are gone; the price is shown where
  the model is picked.

## Update, 2026-10-17: one Send, answers in words, automatic fixes

The user found three send buttons confusing and asked for the flow of Claude
Code and Codex: one button that sends everything, whether it is an idea,
a build request or feedback, and build errors fixed with no button.

- One Send. The prompt has one button, Send. The model gets the
  `replace_source` tool with `tool_choice` "auto" and the chat rule at the top
  of the build contract: answer in a few plain sentences while the person
  asks or shapes an idea; call `replace_source` when they ask to build,
  change or fix the app, or report a problem. A reply in words is saved as a
  builder message and the run ends `ANSWERED`, a new terminal journal state
  reached only from `EDITING`; nothing is applied or built, and the last
  installable app stays installable.
- History. The model reads the latest person and builder messages of the
  chat that fit in 8 KB, oldest first; status and step lines stay out.
- Automatic fixes. After a compile failure the app starts the fix in the same
  screen update that ends the attempt, so the run never looks idle between
  attempts. Fixes keep the forced `replace_source` call and the compiler's
  error records, at most three per send. A fix left pending by a restart runs
  first on the next Send in its chat, after that message is saved where the
  model reads it. The app never sends at launch by itself.
- Removed. "Fix the build error", "Send my feedback" and the feedback field.
  A crash report picked in project tools goes with the next message.
- Trade-off. Each discussion turn is a model request at the picked model's
  price, and a chat holds at most 100 messages (a person and builder pair per
  turn).

## Update, 2026-10-18: a quieter top bar and menu

The user found the top right busy, the Menu text at the bottom left odd, and
project tools and the model row in the menu confusing.

- Top bar. A three-bar menu icon at the top left, read out as Menu, then the
  project name. A tap on the name also opens the menu. The state word
  (working, ready, failed) is gone; the chat and the status line show it.
- Stop. During a run, Stop takes the place of Send under the prompt.
- Status. "Sent to <model>" is gone; the model is shown under the prompt.
- Menu. Project tools and the model row are gone. Project tools still open
  with a long press on the project name, for the source editor, exports,
  recovery files, crash reports and project deletion.
- Retry saved build. When a saved build waits to be rebuilt, the chat shows
  Retry saved build as its primary action, since project tools are hidden.
- Trade-off. Deleting a project, exporting and importing a crash report now
  need the long press, which a person will not find alone.
- Less repeated text. The prompt hint is "Message the builder". A finished
  build says "Your app is ready." once, in the chat, above the Install button;
  the status line stays empty. The line under the primary action ("Installed.
  Try it, ...") is gone; a failed install check goes to the status line.
