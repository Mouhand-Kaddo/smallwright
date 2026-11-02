# Chat steps, model picker and onboarding on the Galaxy A33, 2026-10-17

The user asked for four changes after using the terminal screen: onboarding
for new users, a chat that shows the builder working, an OpenRouter model
picker in place of the cost notes, and no "verified signed APK" text. They
also asked that Build it, Send my feedback and Fix the build error send at
once, with no review dialog, and changed the rule in AGENTS.md to match. The
design is in [ADR 0001](../adr/0001-terminal-controller-ui-in-compose.md),
section "Update, 2026-10-17".

## Device and build

- Phone: Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16,
  arm64-v8a, `ro.kernel.qemu=0`; read-only preflight `ready` at 20:07 local
  time. The S23 Ultra was not connected.
- Controller debug APK sha256
  `d704164fe00d1dcab7fd11c0a6d3d3162c47467ec391ffe468a0484ecaf404d8`, test APK
  `e526661511fdadf66daf1a18f829b6533b8f4688bd336d6cf52ae0f3de2b902a`, installed
  in place over USB with the debug installer. The installed controller's
  signer (`19a7407f…`) matched the new APK's, so project data was kept.
- Host chain: `./gradlew --offline spotlessApply :controller:testDebugUnitTest
  :controller:assembleDebug :controller:assembleDebugAndroidTest
  :controller:lintDebug` passed; 266 unit tests, 0 failures; lint 0 errors.
  New tests: `ModelChoiceTest`, `ModelCatalogTest`, `StepLogTest`, and a
  `tool`-role case in `ChatStoreTest`.

## Cases

| Case | Inputs | Observation | Result |
| --- | --- | --- | --- |
| Home after the update | launch on a project with a failed build | your messages in bubbles on the right, builder messages with dots on the left; "Fix the build error" outlined; the prompt empty; the model name "DeepSeek V4.1 Flash ▾" where the cost note was; no cost text | pass |
| Menu | tap Menu | actions, then "Model: DeepSeek V4.1 Flash", "OpenRouter key", "How it works", then chats and projects | pass |
| Model list loads on the phone | Menu, Model | the controller fetched OpenRouter's public list (no key sent); the current model is marked; each row shows name, id and listed price | pass |
| Search | type `haiku` | three Claude Haiku rows; the others hidden | pass |
| Onboarding, welcome | Menu, How it works | "step 1 of 3", the two-line headline, three `>` lines typed out, the locality sentence, "Get started" | pass |
| Onboarding, key | Get started | "A key is saved on this phone." and "Continue", because this phone has a key | pass |
| Onboarding, model | Continue | the picker with "Use DeepSeek V4.1 Flash"; tapping it returns home | pass |
| Back from How it works | Menu, How it works, Back | returns to the chat, not out of the app | pass |

Screenshots: `toolchains/alpha-goal-20261017/ui-chat-models-a33/` (local,
not committed).

## Not run on a phone

- A live send. The open project on this phone is the user's own, with a
  pending build fix. A send in it, or switching projects, ends that pending
  fix (the app asks "End pending repair?"). I tapped Stay and sent nothing,
  so the "Thinking", "Called replace_source" and build steps have host tests
  only (`StepLogTest`, compile and lint). The next paid send on the S23 will
  show them.
- First launch without a key (field, Save key, Skip). This phone has a key,
  and removing it would delete the user's saved credential.
- The changed `A33SuiteProbe` (no dialog; it now checks the picked model, its
  price bound, and that the app's approved-context hash equals the probe's
  preview hash). It compiles; its first run will be the next suite request.

## Independent review

Two reviewers read the uncommitted change: Claude Sonnet 5.5 and GLM-5.3
Flash Free (OpenCode). Neither found a blocker. The case table above was
rerun on the final APKs for home, menu and the model list.

Claude Sonnet 5.5 reviewed the uncommitted change: no blocker, eight
warnings, all fixed here.

- A send without a saved key now opens the key page and sends nothing.
- The app records the SHA-256 of the context it approved at the tap; the suite
  probe asserts it equals the hash it previewed.
- The probe refuses a picked model whose full-request cost at the listed price
  exceeds its $0.04 per-send bound.
- The end of a run saves the user, step and result messages and replaces the
  live view in one screen update, so nothing shows twice.
- Requests to a model without pinned providers set
  `provider.require_parameters`, so OpenRouter routes only to providers that
  honour the forced tool call.
- A free model's reply must report a cost of 0.
- Back from "How it works" returns home once onboarding has been done.
- Seventeen older one-shot probes still wait for the removed Send dialogs or
  old labels. They compile and are not rerunnable as they stand; STATUS lists
  them.

Nits fixed: repeated model ids are dropped, prices with more than 12 decimal
places are refused, and skipping the key step clears the field and its
message.

GLM-5.3 Flash Free agreed on the model, reply and threading checks and raised
four warnings, fixed here:

- The key-presence check also counts AtomicFile's `.bak` backup, so a key
  that Android can still restore is not reported missing. (GLM named the
  `.new` file; Android's AtomicFile does not restore that one.)
- A saved model choice that cannot be read now says so in the status line
  instead of switching to the default silently.
- The suite probe clears the app's approved-context hash before the tap, so a
  stale value cannot pass the comparison.
- Model ids starting with `~` are left out of the list as well as entries
  marked with `alias_target`.
