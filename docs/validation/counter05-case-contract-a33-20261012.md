# A33 fixed `counter_05` case contract

This adds exactly one fixed supported-request case from
`benchmarks/prompts.jsonl`; it does not edit the prompt set or any frozen result
row. The fixed asset is `controller/src/androidTest/assets/alpha/counter_05.json`.
A host standard-library test asserts that the Android asset remains byte-semantically
equal to the frozen prompt object.

The route is `ArtifactPolicyInstrumentation` argument `a33Counter05` and
`A33Counter05Probe` phases:

1. `prepare` creates a fresh project at pristine source revision S0 and pins its
   project, package, chat, exact snapshot/request hashes and prior selection.
2. `baselineBuild` is a one-shot probe phase after `prepare`. It requires the
   exact pristine R0 snapshot, pinned project/chat and no model attempt, then
   clicks the visible normal Build action. `MainActivity.startBuild` writes the
   unchanged editor bytes as source revision R1, creates a normal manual-build
   origin bound to the selected chat, and builds version 1. It waits without
   Stop or retry and returns only build/artifact metadata, APK phone path/hash/
   size/public signer and click-to-ready timing. The parent phone-only debug
   installer and native test taps then seed count 2 and verify restart 2.
3. `pinBaseline` independently verifies source R1 has the exact prepared authored
   bytes, the normal manual origin is tied to the pinned project/chat at R1/v1,
   the receipt and APK policy, installed package bytes, version and signer. The
   generated app's private counter value is inaccessible to the controller, so
   this phase does not claim the native count check. Record that parent-owned UI
   observation separately before invoking this phase.
4. `preview` loads the untouched `counter_05` request, checks unchanged source R1,
   opens the app's normal preview/approval dialog and cancels it. `send` requires
   the exact preview context SHA-256 and source revision guard, the configured
   free-only model, and the one-shot flag; the runner must first record a fresh
   provider catalog result confirming the selected model is still zero-priced,
   and pass `freeOnlyApproved=true` only after that preflight. The actual
   approval dialog still carries the payload and is rechecked before the one
   Send. Standing `$0` user authorization avoids a repeated human prompt. It does
   not bypass the exact in-app approval, context, revision, or fresh free-catalog
   preflight gates.
5. `ready` or `failed` reopens the pinned result and verifies its journal/source
   outcome. After generation, it verifies the pinned immutable v1 APK by its
   exact project artifact path, size, hash, policy and signer without treating
   the mutable current-receipt pointer as an R1 receipt. On success it separately
   verifies the current R2/v2 receipt and same signer. It does not install an
   artifact. The parent then hash-verifies and debug-updates the same package;
   the separate native UI check must observe `Add one`, retained count 2, one tap
   to 3, and reopen at 3.
6. `restoreSelection` and `verifySelection` return and check the previous
   project/chat selection.

This vertical slice sends only the initial request. It launches no repair or
functional-feedback request. The production global `UsageBudget.MAX_REPAIRS=3`
remains unchanged. If the initial attempt reaches `REPAIR_NEEDED`/compile error,
`failed` stores and reports that exact state with `repairDecision=pending_next_vertical_slice`;
it is not treated as cap-exhausted or final functional rejection. Any later
repair needs a distinct bounded slice and its own exact preview/context, fresh
zero-cost catalog check and one-shot app approval. Model timeout, cancellation,
policy failure, journal identity, usage, and unknown timings remain in the saved
result; do not overwrite them or retry the request as a fresh run.

## Host-only verification (2026-10-12)

The owned worktree is
`toolchains/worktrees/a33-counter05-20261012`
at merged integration `b9c9653` plus the counter05 commits. Using the project
JDK 21.0.12.1+1, SDK 36, offline Gradle, one worker and a 1,536 MiB heap cap:

- `./gradlew format` passed twice (`format-01.log`, 29s; `format-02.log`, 21s).
- The first `spotlessCheck :controller:lintDebug :controller:assembleDebugAndroidTest`
  run passed Spotless and controller lint, then produced an actual Java compile
  failure: Android `JSONObject.getString` accepts one argument, while this new
  guard passed two. The source was corrected to `optString("package", "")`.
- The second `spotlessCheck :controller:lintDebug :controller:assembleDebugAndroidTest`
  passed in 1m22s, including Android-test Java compilation and APK assembly.
  The lint report is at
  `controller/build/reports/lint-results-debug.html`; Gradle reported the lint
  task successful. The SDK emitted its existing XML-v4/tooling-version warning.
- A fresh ignored worktree signing identity produced an initial test APK with
  signer SHA-256 `d4f08f51b19fa85ce0e38f9bde28395305c69e6a2715a886ed3390fcec0f3a0f`.
  That incompatible candidate was not installed and is preserved at
  `toolchains/a33-counter05-20261012/controller-first-new-identity-debug-androidTest.apk`
  (SHA-256 `0dca127eb0c563a7519dc074c55f7a240cfefa555b0b2b8d0afcc61f3365e80c`).
  Its locally generated signing files remain under the worktree's ignored
  `signing/first-new-identity/` directory.
- The original-route final test APK was rebuilt after the worktree signing
  paths were linked by name to the already-approved root project signing
  identity; the link targets were not read or printed. Reassembly passed in
  23s. The candidate
  is package `dev.smallwright.controller.test`, 888,954 bytes, with public signer
  SHA-256 `19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`
  and APK SHA-256
  `413fc5953232e03606fbaa1c8e97b6c1f933235c5e53e067687ae74882ebe3af`.
  It is durably copied at
  `toolchains/a33-counter05-20261012/controller-final-debug-androidTest.apk`;
  the assembly output is
  `toolchains/worktrees/a33-counter05-20261012/controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk`.
- Logs are in
  `toolchains/a33-counter05-20261012/`:
  `format-01.log`, `format-02.log`, `controller-check-01.log` (compile RED),
  `controller-check-02.log` (final checks and assembly), and
  `controller-reassemble-original-signer.log` (final signer rebuild).

The standard-library frozen-asset test was previously run; no JVM tests were
added or mirrored for this route. No phone, model, instrumentation runtime,
installation, generated-app compiler attempt, or physical acceptance was run
in this host slot. Parent-owned device validation remains separate. Record
`debug_installed` for any later authorized development install; that does not
mean Android `confirmed` or production installation consent. Instrumented
observation is not human alpha signoff. Do not change or delete older A33/S23
prompt rows, results, projects or installed packages.

## Baseline-build phase follow-up (2026-10-12)

The parent ran the original APK against the fresh case's `baselineBuild` phase
before this phase existed. The actual guard RED returned host exit 0,
instrumentation code 0, `stage=GUARD`, `AssertionError`, with no project, build
or model request expected. Its raw output and handle are preserved at
`toolchains/alpha-goal-20261012/counter05-initial/baselineBuild-missing-phase-red-01.txt` and
`toolchains/alpha-goal-20261012/counter05-initial/baselineBuild-missing-phase-handle-01.json`.
No source or UI hierarchy was exported.

The new fixed phase compiled in the controller instrumentation APK. Capped
offline `format` passed (24s), and `spotlessCheck`, `:controller:lintDebug` and
`:controller:assembleDebugAndroidTest` passed in 1m with Android-test Java
compilation. The final candidate is package `dev.smallwright.controller.test`,
1,775,394 bytes, public signer SHA-256
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`, APK
SHA-256 `c995f9ab2ed07197fe028e5841d0eaba32a12d31b671182f15d76934602542df`.
The durable copy is
`toolchains/a33-counter05-20261012/controller-counter05-baselineBuild-phase-debug-androidTest.apk`;
the worktree output is
`toolchains/worktrees/a33-counter05-20261012/controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk`.
Both copies matched. The exact host log is
`toolchains/a33-counter05-20261012/baseline-phase-check-01.log`;
format logs are `baseline-phase-format-01.log`, `baseline-phase-format-02.log`
and `baseline-phase-format-final.log` in that same directory.

The phase has not yet run on a device. No normal baseline build, generated APK,
installation, model request, compiler timing or physical acceptance is claimed
for this follow-up.
