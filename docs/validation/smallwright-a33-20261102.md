# A33: Smallwright rename, source-built AAPT2 and a Claude model server (2026-11-02)

Run under the app's earlier name, Palmwright (`dev.palmwright.*`). The app was
renamed Smallwright the same day; names below follow the new name.

Date: 2026-11-02 Dubai. Device: Galaxy A33 5G (SM-A336E), Android 16/API 36,
USB debug. Commit `76770c6` plus this record. Debug APKs signed with the local
alpha key (same signer for both): worker `b543b150…`, controller `c3a428c1…`,
installed with `adb install` beside the old `dev.smallwright.*` apps, which were
left untouched. The UI was driven with `input` taps and `uiautomator dump`.

| Case | Expected | Actual |
| --- | --- | --- |
| Install | both renamed apps install; the builder holds the worker permission | pass: `dev.smallwright.worker`, `dev.smallwright.controller`; `dev.smallwright.permission.BIND_BUILD_WORKER: granted=true` |
| First start | onboarding works without a key | pass: key step skipped, DeepSeek V4.1 Flash picked, Google's SDK terms accepted and the 66 MB platform files downloaded ("The Android platform files are on this phone.") |
| Worker signer check | the builder binds a worker with its own signer | pass: status `worker-ready:dev.smallwright.controller` |
| Source-built AAPT2 | the starter app builds in the worker with AAPT2 `ed917a…` | pass: "Build from this source" ended "Your app is ready." |
| Install through Android | Android's installer installs the built app after the person confirms | pass: the user allowed Smallwright to install apps and confirmed Android's install dialog; one `dev.smallwright.generated.*` app installed |
| Model server, Claude | Anthropic's OpenAI-compatible endpoint runs a build request | pass, below |

## Model server run

The user entered their Claude API key through T3's secret prompt. It reached the
phone through stdin into a temporary file readable only by the shell user, was
typed into the masked key field from there, and the file was deleted at once;
the key was not printed or stored on the host outside T3. Settings then read "A
key is saved for https://api.anthropic.com/v1." The server did not list its
models (Anthropic's `/v1/models` does not take a bearer key), so
`claude-haiku-5-5` was typed by hand; the thinking control was hidden.

Request on the starter counter, sent 16:02:43: "Add a Reset button under the
count that sets it back to zero." The run ended at 16:02:53 with "Read 2 files,
changed 1 file, built once", the reply, Undo and "Your app is ready."
Revision 2's `MainActivity.java` adds a Reset button that sets the count to 0,
saves it and updates the label. Log: 4 model requests, 37,110 prompt tokens (0
cached), 889 completion tokens, about $0.004 at Haiku 5.5's listed price. The
same log line says "reported cost 0 USD" because the server reports none; the
screen showed no dollar amount.

## Not covered

- The reset app itself was built but not installed or tapped.
- Refusals for icons, attached pictures and a spending limit with a server
  model are host-tested only.
- Release-signed APKs; the release key does not exist yet.

Restored: `stay_on_while_plugged_in` back to 0. Left on the phone: Smallwright,
its Counter alpha project with revisions 1 and 2, the saved server key, and one
generated counter app.
