# Quieter top bar and menu on the Galaxy A33, 2026-10-18

The user asked for the menu at the top left as an icon, no "Sent to <model>"
status, no Stop or state word at the top right, Stop in place of Send during
a run, and a menu without Project tools or the model. The design is in
[ADR 0001](../adr/0001-terminal-controller-ui-in-compose.md), section
"Update, 2026-10-18: a quieter top bar and menu".

## Device and build

- Phone: Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16,
  connected over USB. The S23 Ultra was not connected.
- Controller debug APK sha256
  `64a66552f10ab3260cfffaf9999b116c1374b5b3258e89b3f19e109f580b11e9`, test APK
  `2943af8104692c75c42d6c71c42c6e1f7a10b01fa185ea9a5e7402c6ca83985b`, installed
  in place with `adb install -r` on 2026-10-18 at 16:36. The update kept the
  signer and the project data. The installed base APK hash equals the built one.
- Host chain: `./gradlew --offline spotlessApply :controller:testDebugUnitTest
  :controller:assembleDebug :controller:assembleDebugAndroidTest
  :controller:lintDebug` passed; 274 unit tests, 0 failures; lint 0 errors.

## Cases on the phone

The phone was in landscape. The open project was the user's own, with a
ready, installed build.

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| Home | launch | menu icon at top left, then the project name; nothing at top right; Send under the prompt; no Menu text at the bottom | pass |
| Menu | tap the icon | New project, New chat, Install on this phone, OpenRouter key, How it works, then chats and projects; no Project tools or model row | pass |
| Project tools | long press on the project name | project tools page opens; Back returns home unchanged | pass |

Screenshots: `toolchains/alpha-goal-20261017/ui-topbar-a33/` (local, not
committed).

## Follow-up: shorter hint, no repeated "ready"

Controller APK sha256
`c8567843e97dc703ad0a124d0d40caf3278dc4155d85432bbfdee5861a8f8fad`, installed
in place at 16:45; installed hash equal; same host chain passed (274 tests,
lint 0 errors). On launch the prompt hint reads "Message the builder", the
status line under the project name is empty, and no hint line sits under It
works, keep it. The chat's older "Your app is ready. Tap Install on this phone
to try it." line is a saved message; new builds save "Your app is ready."
Screenshot: `toolchains/alpha-goal-20261017/ui-short-hint-a33/01-home.png`.

## Not run on a phone

- Stop in Send's place. It shows only during a run, and a run on this phone
  is a paid request or a rebuild of the user's project. Host build only.
- Retry saved build as the chat's primary action. It needs a saved build that
  waits to be rebuilt after a restart. Host build only.
- Probes. `Ui.showTools` now long-presses the project name through
  accessibility, and taps match a content description as well as text, so the
  Menu icon is found by its label. They compile; no probe ran.
