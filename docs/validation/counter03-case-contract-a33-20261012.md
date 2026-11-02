# A33 fixed `counter_03` case contract

This adds one test-only route for the unchanged supported prompt in
`benchmarks/prompts.jsonl`. The Android asset is
`controller/src/androidTest/assets/alpha/counter_03.json`; the standard-library
contract test verifies it is exactly the frozen prompt object. No prompt row or
older result/project is edited.

The instrumentation argument is `a33Counter03`; its independent state namespace
is `a33-suite-counter-a33_counter03_20261012_01`. Phases are `prepare`,
`baselineBuild`, `pinBaseline`, `preview`, `send`, `ready`, `failed`,
`restoreSelection`, and `verifySelection`.

1. `prepare` reserves a fresh project and chat at pristine R0, pins the complete
   source snapshot and request hashes, prior project/chat selection, and requires
   no prior journal attempt or chat messages.
2. `baselineBuild` is a one-shot visible normal Build from the exact pinned R0.
   It requires the selected chat and unchanged editor bytes, then waits for the
   original Build to settle without Stop or retry. It verifies unchanged source
   at R1, selected-chat manual origin, version-1 receipt, APK bytes/hash, policy
   and public signer. Its phone result contains metadata, artifact phone path,
   hashes, sizes and timing only; generated source and UI hierarchy are never
   returned. The parent separately installs that exact APK on-device and seeds
   count 2 with native taps while controller and worker are stopped.
3. `pinBaseline` requires the parent-observed native preseed/restart evidence
   separately. It checks R1 and the exact manual origin, immutable v1 APK,
   package policy and signer, installed version-1 bytes and signer. Later
   `ready`/`failed` checks read that pinned immutable v1 artifact independently
   of the mutable current-receipt pointer. A generated v2 must retain the same
   project signer. This phase does not change the app data or install an APK.
4. `preview` sends the exact fixed prompt through the ordinary in-app context
   preview and cancels. It pins only context SHA-256/length and request metadata.
   Before the one Send, missing free-only approval, a wrong context hash, or a
   duplicate attempt rejects before model mutation. The parent must also record
   a fresh provider-catalog result showing zero prompt and completion prices for
   `cohere/north-mini-code:free`. The app approval, exact preview hash, project,
   chat, source R1 and baseline artifact checks remain required. Standing `$0`
   authorization permits that one free request; there is no paid fallback.
5. `send` reserves the one-shot attempt before clicking the exact app Send
   control. It makes no repair or feedback request. `ready` and `failed` preserve
   the actual journal, source revision/hash, outcome category, token/cost fields,
   controller storage snapshots and available controller memory observations.
   If the run ends in `REPAIR_NEEDED`, the result records that repair decision as
   pending a later reviewed vertical slice; it does not call the attempt
   cap-exhausted. `UsageBudget.MAX_REPAIRS=3` remains unchanged. Every future
   repair would need its own exact preview, fresh zero-price catalog, approval
   and one-shot guard.
6. After verified readiness only, the parent may hash-check and debug-update
   the same package without uninstall or clearing data. Native observations
   must verify step values 1–10, a selected step 3 changing count 2→5, and
   persistence of both values after relaunch; step 1 adds 1, step 10 adds 10,
   and the final values survive another restart. No source-level inspection is
   used for those checklist observations. `restoreSelection` and
   `verifySelection` restore and confirm the original project/chat.

The route reports the normal Build click-to-visible-ready duration, controller
PID/UID, worker UID, controller own-package storage before/after, controller
memory if readable and thermal status at Build boundaries. Existing helpers do
not expose a trustworthy worker process start identity or worker/child RSS and
storage counters to this route, so those values and cold/warm classification are
explicitly unknown. Keep the registered targets visible: 10s first build, 5s
warm build, 1GiB sum of reported controller/worker/largest-waited-child RSS
high-water counters, and 256MiB combined controller+worker own-package
`appBytes + dataBytes`. Missing counters stay unknown and do not block the
functional case. Send-to-settled and any BUILDING-to-ready bracket are not exact
compiler latency.

The old S23 `counter_03` source6 compile error remains a separate historical
failure with unknown cause. The A33 #29 resource correction establishes only
the resource path it tested and does not certify this request. Preserve all old
counter_01, counter_02 and counter_05 rows, projects, artifacts and installed
packages. The probe and frozen asset passed the combined bounded host gate recorded in
[dialog native2 host evidence](dialog-native2-20261012.md): one fresh
standard-library frozen contract test, formatter/spotless, controller lint and
executed Android test Java compilation/assembly. The combined controller.test
APK includes both independent routes. No A33 model request, phone test,
installation or physical acceptance is claimed by this contract.
