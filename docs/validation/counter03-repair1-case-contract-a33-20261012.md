# A33 frozen `counter_03` repair 1 contract

The subsequent physical continuation is recorded in
[the actual repair1 result](suite-counter03-repair1-a33-20261012.md): it failed
compilation at R3, preserved the installed v1/count2 and left further compiler
repairs available. The host-only statements below describe the original
pre-installation checkpoint, not the current phone state.

This is one fixed test-only continuation of the actual A33 initial `counter_03`
compile failure. It does not alter the frozen prompt, initial result, Reset
attempts, prior accepted cases, production code or `UsageBudget.MAX_REPAIRS=3`.

The required predecessor is the saved initial case
`a33-suite-counter-a33_counter03_20261012_01`, not a new project or fresh
generation. Its actual failure is project
`appc7df5a374abf4be69cb4367c58e88e01`, chat
`7a8d3451430e494996e5ab9187a4c51a`, production run
`run_68b777296e0248c7b7e3d2d6a5acdb31`, source R1→R2,
`REPAIR_NEEDED` sequence 5, `compile_error/java_compile`, one recorded repair
count but zero repair requests sent by the initial probe, 5,432 provider
reported tokens and 25,705ms Send-to-settled. Cost and compiler-only time are
unknown. The retained R2 source snapshot hash is
`df0b926a1fd629ec9a016bf7bb133bd354565ea64dea530d9630dcc9d4cdbb65`.
The installed same-project v1 baseline was independently observed as hash
prefix `5a69…`, signer prefix `0fdb…`, with native saved count 2 surviving
relaunch. A failed-state reopen and duplicate initial-Send guard passed; the
same v1 package bytes and native count2/relaunch2 were rechecked after failure
without an install or tap. No source2 artifact was produced or installed. See
`docs/validation/suite-counter03-a33-20261012.md` for the initial evidence.

Instrumentation argument `a33Counter03Repair1` selects only these phases:
`inspect`, `preview`, `send`, `ready`, `failed`, `restoreSelection`, and
`verifySelection`. The dedicated pins namespace is
`a33-counter03-repair1-20261012`; it must not reuse the Reset namespaces.

`inspect` is read-only. It revalidates the exact saved predecessor outcome,
production run identity/state/sequence/repair count, R2 snapshot hash, original
request and chat-bound verified repair origin. It verifies the immutable
`artifacts/app-v1.apk` path/hash/size, v1 public signer and manifest policy,
manual origin and installed v1 bytes/version/signer. It requires the failed R2
run to have no verified R2 receipt or readiness. Diagnostics may leave the
phone only as numeric problem IDs, categories and line numbers; no source,
diagnostic message text or UI hierarchy is returned.

After HEAD advances, the test-only historical snapshot reader verifies retained
R1 and R2 under the selected project's immutable
`revision-store/revisions/1` and `/2` directories. It inventories every file and
directory, rejects symlinks, foreign paths and non-regular files, applies the
same seven-file, 64 KiB-per-file and 252 KiB-total limits as `PatchApplier`,
then reads each validated file through `ProjectStore.readFile(revision, path)`.
It checks the complete snapshot SHA-256 for R1 and R2 and uses that exact R1
snapshot for the pinned `ManualBuildOrigin` digest. It does not change HEAD or
add a production history API.

Before every Send, the parent separately observes the physical A33 and verifies
the native count remains 2 after relaunch. The probe checks the immutable
installed v1 baseline itself; it cannot read the generated app's private count.
`preview` creates one ordinary app repair preview from the original frozen
request, R2 and the verified failed run, then cancels and saves only its exact
context hash and byte count. Immediately before the sole repair Send, the
parent must verify a fresh provider catalog still reports zero prompt and
completion price for `cohere/north-mini-code:free`. The app still requires the
exact saved preview hash, current repair origin and baseline, the parent-passed
free-only approval, and the ordinary `Send & repair` approval. Missing
approval, stale/corrupt/wrong context, or duplicate Send must fail before any
model mutation. No paid fallback, automatic retry, feedback rescue, developer
source edit, generated-source export or additional repair is allowed in this
slice.

The attempt flag is durably reserved before the one `Send & repair`. It waits on
that same production run without Stop or retry. Source may advance exactly
once, R2→R3. On readiness, derive the actual generated version from the
settled journal and verified receipt; require it to be above the pinned v1 and
signed by the same project key. Do not assume version 3. Do not install the
artifact. On another compile failure, preserve the exact source/journal/run
outcome and leave remaining repair capacity pending a separate reviewed slice;
do not call it cap-exhausted. `RunJournal.repairs` counts compiler-failure
transitions: the actual initial R2 failure reports 1 despite zero repair Sends;
a successful repair can remain at 1, while another compile failure advances it
to 2. The global limit remains three. The `inspect`, `preview`, `send`, `ready`
and `failed` phases retain the immutable installed v1 bytes/version/signer
before any parent-owned debug update. Immutable v1 APK and manual-origin checks
remain mandatory in every phase, independent of what is installed.

If the one repair reaches `FAILED` or `CANCELLED`, the probe retains that actual
journal state, source revision and complete source hash, records a fixed outcome
category, records both the current source revision and journal source revision,
and requires no current-revision artifact or readiness. It does not
turn policy, model, resource, cancellation or local-build failures into a repair
success or discard them because the source advanced. `restoreSelection` first
revalidates the pinned v1 and settled outcome while the repair project remains
the currently selected project; it reserves a durable one-shot flag before
restoring the pinned prior project/chat. For a successful repair it verifies
the installed package against the exact v2 receipt bytes/version/signer; for a
failed or cancelled repair it verifies the installed package remains the exact
v1 baseline. `verifySelection` independently checks that the one-shot restore
completed and both UI selection and active-project preference match the original
pins.

The bounded host verification completed offline with the project JDK 21 and
the existing named local harness signing identity. Root `spotlessApply` and
`spotlessCheck`, `:controller:lintDebug`, and
`:controller:assembleDebugAndroidTest` passed. Lint reported 0 errors and 77
warnings; none referenced the new repair probe. The first Android-test compile
attempt exposed the platform `JSONObject.put` checked `JSONException` contract
in `numericProblems`; the helper now declares that exception, and the full gate
passed on retry. Logs are preserved in the ignored
`toolchains/repair1-hostproof-20261012/hostcheck-initial-failure.log` and
`hostcheck-success.log` files.

The final test APK is preserved at
`toolchains/repair1-hostproof-20261012/controller-debug-androidTest-ebe476bdb30bc88e0aba67a06df6c6379ab9db773461752f2d5a9ed606f399a5.apk`.
Its SHA-256 is
`ebe476bdb30bc88e0aba67a06df6c6379ab9db773461752f2d5a9ed606f399a5`, package
`dev.smallwright.controller.test`, min SDK 31, target/compile SDK 36, and public
signer SHA-256
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`. It has
not been installed at that host checkpoint. That checkpoint did not exercise the actual phone
guards or model route.

The TDD runtime gate after source review must first exercise `inspect` against
the actual saved failure, then prove missing-approval, wrong-hash, corrupted
context and duplicate-attempt guards return without source/journal/artifact
mutation. Only after those guards and a fresh zero-cost catalog/native count-2
check may the parent execute the one approved repair Send. The original source-only
proposal had not run on Android; it made no repair-success,
artifact, installation or checklist-pass claim.
