# App name and app icon, on the Galaxy A33 (2026-10-20, #63)

Spec #59, items 4 and 5. The build contract makes the builder set the app's
`android:label`: the person's name, otherwise a short name it picks and tells
them. A new run tool, `make_app_icon(description)`, asks OpenRouter's
`google/gemini-3.1-flash-lite-image` for one square PNG, checks it, and writes
an adaptive launcher icon that the manifest points at (ADR 0006).

## Setup

- **Phone.** Galaxy A33 `<A33 serial>` (SM-A336E, Android 16), checked with
  `adb devices -l` at the start of each session, under the phone lock.
- **Controller.** `ee03fa4134655862cb3b8aca412fbcc0bc5c1908bef5f5ddf5a6e83545f356d5`,
  installed with `adb install -r` in both sessions (between them another
  agent had installed `236bb4b7…`). Test APK
  `940764d4ca2026bf75ad81dffca50f0e4cdaa720ae3e42178010a2b882b0c95b`.
- **Worker.** Not changed by this ticket and not reinstalled. Installed:
  `9a4e28fb0f45f6c46ef35b6fee11c489c448e389db567ebf78bc95c7e38166cd` (this
  branch's build is `52f84456…`, same source).
- **Host chain.** `spotlessApply :controller:testDebugUnitTest
  :worker:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug
  :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest
  :controller:lintDebug`: controller 448 tests, worker 91, 0 failures; lint 0
  errors.
- **Probe.** `AppIconBuildProbe` (`-e appIconBuild app|named|unnamed`). Each
  case uses a disposable project in an isolated registry under the
  controller's files, deleted when the probe ends. The built APK is copied
  out, checked against its receipt on the phone, installed by streaming it
  from `run-as` into `pm install -r -S` (the USB debug installer exception,
  not production install consent), then uninstalled and the copy deleted.
- **Script.** `/tmp/t63/s1.py`, one locked session per run (`app`, then
  `named,unnamed`). Logs and screenshots: `toolchains/spec35/63/`.

## Host tests

- `ImageClientTest`, against `ImageServer`, a stand-in on 127.0.0.1: the
  request body (model, `modalities` image and text, `image_config` 1:1, no
  tools, the prompt) and the key header; the PNG returned with its cost and
  tokens; a JPEG, non-PNG bytes, bad base64, a URL that is not a data URL, no
  image, 32×32, 5000×5000 and over 4 MB (8 MB when this ran) are refused with the cost still
  reported; HTTP 402, an error body, negative or missing cost, another model,
  trailing JSON and a reply over 6 MB (16 MB when this ran) throw; an empty or oversized prompt is
  never sent.
- `AppIconRunTest`, a scripted run with the stand-in image server: the icon
  files and the manifest's `android:icon`, 3 requests and $0.0392 counted
  (two steps plus the image), the "Made an app icon" step; a refused image and
  an image Android cannot decode are plain tool results that still count;
  a failed request is a tool result and the run goes on; the spending limit
  stops the icon before any image request; with no icons, no request.
- `AppManifestPolicyTest`: a label and icon the model sets survive the policy;
  `withIcon` sets the application's icon and round icon, and a launcher's own
  icon, leaves other activities alone, and refuses a broken manifest.
- `ContextFilterTest`, `StepProtocolTest`, `StepSummaryTest`: the contract's
  name and icon rules, the tool's strict parsing and schema, and the step
  row "Made an app icon".

## Cases on the phone

| Case | What ran | Expected | Actual | Requests, cost |
| --- | --- | --- | --- | --- |
| `app` | Scripted replies over a fake connection: `make_app_icon`, whose image reply is the fixed file `controller/src/androidTest/assets/icon-fixtures/leaf.png` (1024×1024, sha256 `31c35470…a251`), then an edit of the label to "Leaf Notes", then `build`. Real `ImageClient` checks, `LauncherIcon` (Android's decoder) and the real worker. No request left the phone. | The app is named Leaf Notes and shows the leaf | Built in 4.5 s. APK 21,123 B, sha256 `7e5a94d3069acbf8fd5ef1914fe76f98a2edc5ae8413cbe2755b5a4a85c38a33`, holding `res/drawable-nodpi-v4/app_icon_image.png` (2,261 B) and `res/mipmap-anydpi-v26/app_icon.xml`. The package manager reads label "Leaf Notes" and an `AdaptiveIconDrawable` from the APK. Installed hash equal. The launcher and App info show "Leaf Notes" with the leaf in the launcher's shape (`launcher-app.png`, `app-info-app.png`). A tap on it opens the app (`opened-app.png`). | 5 counted (scripted), $0.0394 counted (scripted) |
| `named` | DeepSeek V4.1 Flash with the saved key, "Make a simple app that shows a random motivational quote each time I tap a button. Call it Daily Spark." The probe's model has no image request, and app icons were off. | Label "Daily Spark" | Label "Daily Spark" in the launcher and App info (`launcher-named.png`, `app-info-named.png`). The app opens (`opened-named.png`). APK sha256 `67b52ab5a9edec7bb313f85d93cbd4d1b5d83d40b788f28a6439b50a92c3221f`, installed hash equal. | 7, $0.0039 |
| `unnamed` | The same, "Make a simple app where I tap a button to roll a six-sided die and see the number." | A short name the builder picks and says | Label "Dice Roll". The reply says "Your app is ready. I named it **Dice Roll**." (`launcher-unnamed.png`, `app-info-unnamed.png`, `opened-unnamed.png`). APK sha256 `38fc40ed99fdb5a7bbf07b7c31c5db8cf3398bfbb8ec9b4e6008847ab739598f`, installed hash equal. | 6, $0.0011 |

After each session the three generated apps were uninstalled, the copied APKs
were deleted (no `app-icon-*` files left), and the controller showed "Main
project" (`controller-after.png`). That project was not opened for editing,
built or sent a message.

## Not run, and limits

- **No real image-model request.** The user has not approved one. The
  request format follows OpenRouter's current documentation (fetched with
  Context7) and is tested only against the stand-in server; the phone case
  used a fixed PNG. The first real call may show a difference in the reply's
  `model` field (since 8b1b2ec, `google/gemini-3.1-flash-lite-image` or its dated id `google/gemini-3.1-flash-lite-image-20260630`) or in
  timing (120 s read timeout, 180 s overall).
- No run through the chat screen with `make_app_icon`: the app's runs use the
  real image client, so that would be a real image request.
- The label cases are one run each with one model.
- The icon's look on other launchers (circle masks, themed icons) was not
  checked; One UI's squircle is in the screenshots.
