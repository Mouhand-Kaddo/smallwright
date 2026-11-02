# 0008. Generated apps talk to the builder through activity results

Date: 2026-10-25. Status: accepted (the phone round trip is untested until it
runs on the A33).

The user approved two features (ticket t72). After a generated app crashes, the
person hands the report to the builder with one tap, and the builder reads it
with a `read_crash_report` tool. With a `look_at_app` tool, the builder sees a
picture of the running app. Both need data to cross from a generated app, which
the model writes, into the controller, which holds the keys. MediaProjection is
out because Android asks for consent every time.

## Decision

Every exchange is a `startActivityForResult` between two explicit components,
so Android itself names the caller (`Activity.getCallingPackage()`, set only
for a start that expects a result). No side has a listener, a service or an
intent filter for this.

- **Crash report (the app pushes).** The builder adds a crash handler to every
  app. It saves the report in the existing message-free format (at most
  16 KiB) and opens a small "stopped" dialog in a separate process. If Android
  blocks that dialog, it opens on the app's next launch instead. "Tell the
  builder" sends the report bytes to the controller's `AppReportActivity`. That
  activity is exported but has no intent filter. It reads nothing from the
  intent until the caller passes two checks. The calling package must be a
  project's package. The installed app must have exactly one signer, equal to
  that project's current signing certificate. The report is then parsed by
  `CrashReportImport.read` and saved as the project's latest report. The builder
  opens on that project with the message "The app crashed. Read the crash report
  and fix the cause." already typed. It opens through the non-exported
  `OpenProjectActivity`, which run notifications use too, so no other app can
  pick the project the exported launcher shows. No run starts: the person taps
  Send, so the exact-context rule and paid requests are unchanged. When the
  controller refuses a report, the app's dialog says so.
- **Picture (the controller pulls).** `look_at_app` first needs four things.
  The model's listing must have image input. Nothing may be edited since the
  last build. The installed APK's SHA-256 must equal the signed artifact of the
  current revision. The builder must be on screen, because Android blocks
  activity starts from the background. The run then starts the controller's
  non-exported `LookActivity`. It starts the app's `BuilderLink$Look` for
  result. That activity answers only a caller that is the controller's package
  holding the controller's certificate, whose SHA-256 is written into the app's
  source at build time. It opens the requested activity, which must be one the
  app declares, or the launcher. After 800 ms it copies that window with
  `PixelCopy` (no permission, its own window only), longest side at most 1568,
  to a PNG. It finishes the activity it opened and returns a read grant for
  `content://<package>.builderlink/look.png`. The person is back in the builder
  when `LookActivity` finishes. The controller then checks several things. The
  reply must name exactly that authority and path. The read may be at most
  6 MiB. The PNG signature and header must be valid, each side 1 to 4096 pixels.
  The same installed APK must still match. Only then does it decode the picture
  through the same guarded `ImageDecoder` path attached pictures use. The JPEG
  it makes goes to the model in one user message after the tool results. Only
  the newest app picture stays in a run's conversation.
- **Controller-owned code.** It lives in one file, `BuilderLink.java`, in the
  reserved sub-package `<package>.builderlink`. Project paths refuse that
  sub-package, so the model cannot write, read or delete it. The controller adds
  the file to the build snapshot, never to a revision.
  `AppManifestPolicy` refuses model or library entries named in it. It then adds
  the provider that installs the handler at process start (not exported), the
  two activities and a `<queries>` entry for the controller. The model cannot
  remove or replace them. The legacy counter project gets none of this, like
  the discovery action.

## Threat model

- Another app sends a crash report: refused, because its package is not a
  project's, or its signer differs (a squatter under a project's package name),
  or it did not start for result (no calling package).
- Another app asks a generated app for its picture: refused by the package and
  certificate check, so it never sees the person's screen. The provider is not
  exported, and only a verified look grants a read.
- Another app answers a look: it cannot. The controller started an explicit
  component in a package whose installed APK it just matched byte for byte. The
  authority belongs to that APK.
- The project's own code shares a process with the builder's code and can
  forge a report or a picture, or replace the handler. That is accepted: the
  boundary is other apps, and all app bytes are untrusted anyway. They pass the
  strict report format and `ContextFilter.redact`, and the PNG checks and
  re-encoding. Neither path can start a paid request.
- Bundles from unknown callers are never unparcelled before the caller checks
  pass, and malformed ones are caught.

## Considered options

- Bytes in the result intent: simpler, but a 1568-pixel PNG can pass the 1 MB
  binder limit. File descriptors are refused in intents.
- A signature permission between the apps: impossible, the controller and each
  app have different keys.
- An exported provider `call()` for the report: it identifies the caller well,
  but it brings nobody into the builder, and an app could push without a tap.
- Holding the run until the builder is on screen: it surprises the person with
  their app when they come back. The tool says why and the builder tells them.
- Waiting for the next launch only: an app that crashes while starting never
  shows a prompt.

## Consequences

- The person sees their app for about a second on each look. If Android has
  not opened the look within 5 seconds (the person left the builder), the tool
  says so. A look that started but has no picture after 45 seconds (the app
  hangs or never shows) ends as not copied. Stop or that bound closes what the
  look opened and brings the builder back. These bound Android and the app, not
  the model.
- A crash in the app's own `Application` class also stops the dialog's
  process, so no dialog shows for it. The report is still saved.
- `PixelCopy` copies the activity's own window, so a separate dialog or popup
  window is not in the picture, and a secure window is refused.
- The builder's file counts toward the worker's 500-file and 16 MB limits, so
  the run's edits leave room for it. A project already past that room may still
  shrink.
- If the controller is signed with a new key, apps built before it refuse the
  look until they are built and installed again.
- The starter app's own crash handler, its "Preview crash report" button and the
  hidden "Import a crash report" page remain. A report the person imports there
  still goes with their next message.
