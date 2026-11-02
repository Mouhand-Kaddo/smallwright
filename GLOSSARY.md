# Smallwright

The shared language for projects, conversations and native apps built on the
user's phone.

## Language

**Project**:
One generated app, its editable source, chats, build history and signing identity.

**Chat**:
A conversation within a project, with its own message history and the project's
shared source and app history.

**Controller**:
The trusted builder app that owns projects, conversations, model credentials
and the approval, signing and installation flow. People using the app call it
the Builder.

**Worker**:
The restricted compiler app that receives only the current project snapshot
and produces an unsigned app artifact.

**Generated app**:
The standalone native Android app belonging to a project, with its own saved
data and signing identity.
_Avoid_: Preview, streamed app

**Harness**:
The controller and worker apps used to build and verify generated apps.

**Project revision**:
A committed version of a project's source, shared by its chats.

**Snapshot**:
The immutable project source for one exact revision used by a build.

**Run**:
Everything the builder does for one Send: its model requests, file reads and
edits, builds and fixes, until the model says it is done or asks a question
round, the person stops it, or a run limit they set is reached. It keeps going
while the person is in another app.

**Signed artifact receipt**:
The verified record tying a signed app artifact to its project, source revision,
build and signer; it does not establish installation or functional acceptance.

**Undo last change**:
Putting the project's files back to how they were before the builder's latest
change, as a new project revision; earlier revisions are kept, and undoing
again goes one change further back. The chat gets a note the builder reads.
The app on the phone changes only when the person installs again.
_Avoid_: revert, rollback

**Feedback update**:
A new project change based on observed app behavior or explicitly selected
diagnostics, sent as an ordinary chat message.

**Compiler-error repair**:
A change prompted by an actual failed build, followed by another build. It is
a step within the run, repeated until the build passes, unless the person
turned on stop when stuck.

**Chat answer**:
A reply in words from the model, with no source change and no build. The run
ends in the `ANSWERED` state.

**Question round**:
Questions the builder asks the person at once, each with a few answers to tap
and the one it recommends already picked. It ends the run. The person's picks,
or their own words, go back as an ordinary chat message.
_Avoid_: form, quiz, survey

**Primary action**:
The one button the controller shows beside Send for the project's current
state: Install on this phone, or It works, keep it.

**Menu**:
The panel that slides in from the left over the chat, opened from the icon at
the top left or a tap on the project name. It shows projects as folders, the
current one open with its chats, and leads to Project tools, Settings and How
it works.

**Compiler**:
The part of the worker that turns a project's source into an app. It stays the
same whatever Android version or libraries the app uses.
_Avoid_: using it for the Android platform files

**Android platform files**:
Google's official developer files for one Android version, which tell the
compiler everything Android offers. The person downloads them from Google
during setup, after accepting Google's terms, and they are used unchanged.
_Avoid_: compiler, stubs, our copy of Android

**Project libraries**:
The libraries a project uses, each at an exact version, from Google's Maven
repository or Maven Central. The builder adds, updates and removes them; the
phone downloads each one once and keeps it for offline builds.
_Avoid_: built-in libraries, library set

**Run limit**:
An optional cap on a run's steps or spending, set by the person in Settings.
There is none unless they set one.

**Stop when stuck**:
An optional setting, off by default, that ends a run when the same build error
comes back after three fixes in a row.

**Run notification**:
The phone notification that shows a run's state while the person is elsewhere:
working, needs your input, done or failed.

**Model choice**:
The model the person picked to write their apps: an OpenRouter model with its
listed price in dollars per 1M tokens in and out, or a model at their model
server, which lists no price. Every request uses the model picked at the tap;
the default is DeepSeek V4.1 Flash on OpenRouter.

**Model server**:
One OpenAI-compatible server the person adds in Settings by its https address
and API key, as the other place besides OpenRouter that models can come from.
Icons, pictures, web search, the thinking level, attached pictures and a
spending limit need an OpenRouter model.
_Avoid_: provider plugin, custom provider

**Thinking level**:
How much the model reasons before it answers: off, low, medium or high, low by
default, kept on the phone. It is shown beside the model choice only for models
that OpenRouter lists with reasoning, taken at the tap with the model, and sent
as OpenRouter's reasoning effort (off sends `none`).

**Interview**:
The builder's rounds of three or four plain numbered questions, each with a
suggested answer, before it builds a new app or makes a big change. It ends
with a short plan the person agrees to. Small changes, fixes and "just build
it" skip it.
_Avoid_: grilling, questionnaire

**Project brief**:
The decisions an interview settled, in short plain words, which the builder
writes and reads at the start of every run in the project. It is kept beside
the project's chats, outside its revisions, so it is never built into the app
and undo leaves it as it is.

**Plan**:
The builder's checklist for a big job in one run, ticked off as it works and
shown in the chat as "3 of 7 done". Each update replaces the whole list.
_Avoid_: to-do list, tasks

**Builder step**:
One line of what the builder did in a run (thinking, a tool call, a build),
shown live in the chat and saved with it as a `tool` message.

**App name**:
The name a generated app shows under its icon on the phone: the one the person
gave it, otherwise a short name the builder picks and tells them.

**App icon**:
The picture a generated app shows in the phone's launcher. The builder has one
made by an image model only when the person asks for it; that request is paid
like the run's other requests.
_Avoid_: logo file, launcher asset

**App picture**:
A picture a generated app shows, such as a background or an illustration. The
builder has one made by the image model, square, wide or tall, only when the
person asked for pictures or agreed to them; each is paid like the run's other
requests.
_Avoid_: image asset, artwork file

**Skill**:
Instructions for the builder, in the Agent Skills format, for every project,
or for one project when the person asks for that. The person adds one from a
GitHub link in Settings, or asks the builder in a chat, by link or by name.
Only its Markdown text is kept; nothing in it is run. The builder reads a skill
when it fits the request, or the person starts one by beginning a message with
`/name`.
_Avoid_: plugin, script

**Attached picture**:
A picture the person adds to one chat message from Android's photo picker, at
most four. The phone shrinks it to a JPEG and sends it with that message only,
after the words. It is part of the context checked at the tap, and is saved
with the message. Send is refused for a model whose listing lacks image input.
_Avoid_: attachment file, upload
