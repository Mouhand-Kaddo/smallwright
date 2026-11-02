# Runs keep going in the background, with run notifications, on the Galaxy A33, 2026-10-18

Ticket #45 of spec #35. A run now works under a controller foreground service
of type `dataSync`, so it goes on while the person is in another app or the
screen is locked. One notification channel, Runs, shows the run's state:
working (the latest builder step and Stop), needs your input (the run ended in
words without a successful build), done (opens the chat with Install) and
failed (opens the chat with the reason). While the person views that run's
chat only the working state shows. Setup asks once for notification
permission; a refusal changes nothing else.

## Design

- `RunService` (foreground service, `dataSync`, not exported) holds the
  `RunCoordinator` of a working run, so a screen that comes back attaches to
  it instead of making a new one, and keeps the compiler bound while the run
  works. Its `Shade` turns run notices into Android notifications: the working
  notice is the service's own notification, end notices are a second one.
  Stop in the notification goes to the service, which stops the run only if
  that notification's run is the one still working.
- `RunNotices` decides what shows: working as steps change; built → done;
  ended in words → needs your input (first line of the reply); failure →
  failed (the chat's reason); stopped by the person → nothing. End notices are
  not posted while that chat is on screen, and opening that chat clears one.
- `RunCoordinator` takes an attachable screen listener (`attach`/`detach`), so
  `Listener.shows` and `close()` are gone; it keeps the live steps for a screen
  that attaches and feeds the notices.
- `MainActivity` no longer stops the run in `onStop`; it reports the chat on
  screen in `onStart`/`onStop` and on chat changes, detaches in `onDestroy`,
  shares one work executor across screens, opens the project of a working run
  without closing its journal as interrupted, and opens the chat a notification
  names (`onCreate`/`onNewIntent`).
- Copy: titles name the state ("Working", "Needs your input", "Your app is
  ready", "Did not finish"); the project is the header subtext.

Official rules followed (developer.android.com, "Foreground service types" and
"Foreground service timeouts", read 2026-10-18): Android 14+ needs the type in
the manifest and `FOREGROUND_SERVICE_DATA_SYNC` beside `FOREGROUND_SERVICE`;
apps targeting 15+ get `onTimeout` after 6 hours of background `dataSync` in
24 hours and must stop the service, which `RunService.onTimeout` does (the run
itself goes on without the service). `POST_NOTIFICATIONS` is requested only on
API 33+.

## Device and build

- Phone: Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16
  (API 36), arm64, connected over USB. Keyguard has no credential (swipe). The
  S23 Ultra was not connected.
- Branch `spec35/t45-background`. Before installing, the phone held another
  ticket's builds: controller `6985e882…1d5f`, worker `d616e7d3…31d1`.
- First install at 21:49:55 (+04): controller
  `a1796a6bd774aa130395ae1abb91587a7b21674cd41e6e7e58a23a240727ef52`
  (commit `290a2cb`), worker
  `9fc07f6316940996dbfe864f025d0ef415a214520e923644f413b12423326091`, both
  with `adb install -r`; signer SHA-256 `19a7407f…bd25`; installed `base.apk`
  hashes equal the built ones; data kept.
- Second install at 22:06:03 (+04) after the copy change: controller
  `8894d6746651ff1acf8e7c8e2aa7ccd66a771c80834ecec0957a7ce3667633e3`
  (installed before: `a1796a6b…ef52`); installed hash equal. Worker unchanged.
- Host chain through `spec35/gradle.sh`: `spotlessApply
  :controller:testDebugUnitTest :worker:testDebugUnitTest
  :controller:assembleDebug :worker:assembleDebug
  :controller:assembleDebugAndroidTest :controller:lintDebug` passed; 312
  tests, 0 failures; lint 0 errors (one new warning: `StaticFieldLeak` for the
  coordinator `RunService` holds during a run, by design).

## Host tests added

`RunNoticesTest` (9): working shows the latest step; built → "Your app is
ready" opening its chat; words without a build → "Needs your input" with the
reply's first line; failure → "Did not finish" with the reason; stopped →
nothing; while the run's chat is open only working shows; another chat open
still gets the end notice; opening the chat clears its end notice; Stop from a
notification reaches only the run that notification shows.

## Cases on the phone

A disposable project "Background run test 45" was made with New project. The
model was the saved DeepSeek V4.1 Flash (`deepseek/deepseek-v4.1-flash`).
Notification state was read with `dumpsys notification --noredact`, filtered
to the controller's package; power and keyguard with `dumpsys power` and
`dumpsys window policy`. Screenshots are in `toolchains/spec35/45/` (local,
not committed).

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| Setup asks once | launch after install (onboarding already done) | Android's "Allow Smallwright to send you notifications?"; Allow; relaunch shows no second ask (01, 02) | pass |
| Run 1: Home, then lock | Send a tip calculator request 21:52:15, Home, open the shade, then `KEYCODE_SLEEP` at 21:52:47 | working notice "Working on Background run test 45 / Thinking", then "Building on this phone" with Stop (05, 06); power `Dozing`, keyguard showing; run ended 21:52:50 while locked; done notice present at 21:53:01 while the screen was off | pass |
| Done opens the chat with Install | wake, `wm dismiss-keyguard`, swipe away Samsung's touch protection, tap "Your app is ready" | chat of the disposable project with the reply, "Your app is ready." and Install on this phone; notice gone (07, 08) | pass |
| Run 2: locked for minutes | Send a history and settings request 21:54:15, Home, lock 21:54:21 | service `isForeground=true`, `types=0x1`; working notice stayed "Thinking" until 21:56:52, then "Building on this phone", all while `Dozing`; run ended 21:56:58, still locked; done notice; tapping it opened the chat with Install (10, 11) | pass |
| Stop from the notification | Send a dark theme request 21:57:48, Home, expand the working notice, tap Stop 21:57:59 | run ended 21:57:59.771; no notification left, service gone; the chat shows "Stopped" and the stopped status; the earlier build stays installable (12, 13, 14) | pass |
| Needs your input | Send "ask me one question … do not build yet", Home | "…needs your input" with the builder's question; tap opens the chat with the question (15, 16) | pass |
| Quiet while viewing | Send a short reply while staying on the chat | working notice during the run, nothing after it ended (17) | pass |
| Permission refused | `pm revoke dev.smallwright.controller android.permission.POST_NOTIFICATIONS` 22:00:08, relaunch (no prompt), Send a build request, Home | service foreground, no notifications posted; run ended 22:03:40 with the launcher in front; chat shows the reply, "Your app is ready." and Install (18, 19); `pm grant` back at 22:03:53, granted=true | pass |
| New copy: needs your input | second APK; Send a question request, Home | title "Needs your input", header "Background run test 45", the question as text; tap opens the chat (21, 22) | pass |
| New copy: working and done | Send a small build request, Home | "Working / Thinking" with Stop and the project in the header (23); then "Your app is ready / Tap to install it." (24); tap opens the chat with Install (25) | pass |
| Clean up | Project tools → Delete this project → Delete project | project removed; the app switched to "Main project"; projects left: "Main project" and "Counter alpha" (26, 27) | pass |

The first copy truncated "…needs your input" behind long project names (15),
which is why the titles changed to name the state first.

### Requests and cost

From the controller's `SmallwrightRun` log line at each run's end:

| Run | Requests | Reported cost (USD) |
| --- | --- | --- |
| 1 tip calculator | 5 | 0.0022033592 |
| 2 history and settings | 8 | 0.0076997424 |
| 3 stopped from the notification | 2 | 0.0008594880 |
| 4 question | 1 | 0.0001668912 |
| 5 while viewing | 1 | 0.0001102640 |
| 6 permission refused | 11 | 0.0100677864 |
| 7 question, new copy | 1 | 0.0001836016 |
| 8 build, new copy | 16 | 0.0043482152 |
| Total | 45 | 0.0256393480 |

The user's project was not opened for building, edited or sent messages. No
generated app was installed.

## Not run, and why

- Failed notice on the phone. Causing a failure would need the network off or
  another phone setting change, which is not allowed; host tests cover it.
- A screen recreated during a run (rotation, "Don't keep activities", the
  system destroying the activity). Both need phone setting changes. Home, the
  lock and notification taps kept the same activity, so re-attachment through
  a new screen is host-built only.
- The Android 15+ six-hour `dataSync` timeout; not reachable in a session.
- Probes. `StopUiProbe`'s background case now asserts that leaving the screen
  keeps a queued build (`backgroundKeepsBuildOnly`); it compiles, no probe ran.
- Process death during a run (#44).
