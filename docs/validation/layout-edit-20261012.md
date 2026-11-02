# Bounded layout edit, issue #21

Part of #1 and
#2, scoped by
#21. This does
not close full conversational acceptance, the fixed twenty-request alpha suite,
complete API/resource inputs, permissions or dependency management.

## Vertical host evidence

The agreed seams are the existing model protocol, project replacement dispatcher,
generation coordinator and real phone pipeline. Protocol tests use explicitly
synthetic HTTPS connections and a dummy fixture credential; there is no provider
call. Dispatcher tests use actual temporary project/revision files. Coordinator
resource failure is a labelled synthetic failure at its build adapter, not
physical AAPT2 evidence.

The first test offered an exact `res/layout/empty.xml` proposal using the existing
forced `replace_source` operation. It compiled and failed at runtime with
`Invalid model response (tool target)`: 11 tests, one failure, 59s
(`/tmp/layout-edit-protocol-red1.log`). The parser/schema was changed to exactly
the project's MainActivity path or the existing layout. The first GREEN attempt
passed the new behavior but exposed an older schema assertion reading the removed
`path.const`; its NullPointerException is preserved in
`/tmp/layout-edit-protocol-green1.log` (53s). The authorized schema expectation
was updated to assert exactly the two enum paths, not relaxed. The corrected
protocol suite passed 11 tests in 30s
(`/tmp/layout-edit-protocol-green1-corrected.log`).

The next test parsed the now-admitted layout and attempted a real immutable-store
replacement/reopen. It failed at runtime because the dispatcher still rejected
the layout: seven tests, one IOException failure, 37s
(`/tmp/layout-edit-dispatcher-red2.log`). The minimal dispatcher/store change then
passed seven tests in 46s (`/tmp/layout-edit-dispatcher-green2.log`), preserving
the original Java bytes and recovering the exact layout/revision after reopen.

The focused follow-up executed 52 tests with zero failures/errors in 30s:
11 protocol, eight dispatcher, 25 generation, and eight context-preview tests.
Additional checks reject a foreign project's layout, unapproved/stale/replayed
revision, alternate resource/manifest/traversal paths, and preserve both files.
Recorded generation retains the exact saved layout/Java and FAILED journal on a
synthetic resource build failure; Stop before applying preserves both files and
records cancellation. Existing strict argument/duplicate/multiple-call/UTF-8
byte-limit/cancellation/context guards remain in the same suites. No failure was
invented for guards that already passed. Log:
`/tmp/layout-edit-focused-guards.log`.

```bash
source scripts/env.sh
./gradlew :controller:testDebugUnitTest \
  --tests dev.smallwright.agent.SourceReplacementProtocolTest \
  --tests dev.smallwright.controller.SourceReplacementDispatcherTest \
  --tests dev.smallwright.controller.GenerationRunTest \
  --tests dev.smallwright.controller.ContextPreviewGateTest \
  --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

## Implementation boundary

The edit phase still advertises one forced tool named `replace_source`, with the
same strict project/revision/content argument keys and 64 KiB UTF-8 file cap.
Its path enum contains exactly the authenticated project's MainActivity and
`res/layout/empty.xml`; existing Java proposals remain accepted. The dispatcher
checks project/package identity and approved revision, then uses the existing
immutable `ProjectStore.applyPatch` for the one selected file. It preserves the
whole-project limits and every other source byte. No manifest, permission,
dependency, arbitrary path, additional tool, provider phase/framework or worker
path policy was introduced. Generation/journal/approval/Stop/publication code is
unchanged; the new file selection uses those existing boundaries.

## Physical probe preparation and actual dependency

`LayoutEditAcceptanceProbe` uses normal MainActivity New project/chat context,
then calls the production recorded-generation seam with explicit synthetic
proposals. It uses exact context previews, actual SQLite run events and existing
chat/full-source origin pinning. Its build phase creates one owned observation
and never reseeds or replays it. It attempts malformed layout through the actual
worker and requires AAPT2 failure without a receipt or reopened Install/APK
export readiness. A fresh approved layout proposal recovers the saved source;
a later source proposal references generated R and directly inflates the XML
layout. Versions are allocated through production `RunRecovery` using stored,
journal and installed versions, then reported from the actual receipts.
Success/reopen checks exact source/layout/package/policy/signature/receipt/APK and
restores the prior selection. `verify` reads the retained successful case without
a new build. Synthetic proposals are not normal Send or live inference.

There was a real dependency before its rendered-resource case could run: the
normal worker's Activity compiler header lacked `setContentView(int)` and
`findViewById`; its historical R probe only stores R integers and does not inflate
XML. Separate #22 supplied bounded source-derived methods and finalized notices
in integration `52ff1ea`. See [its independent derivation/tests and exact worker
candidate](layout-source-api-20261012.md). This layout slice does not hand-author
header signatures, substitute programmatic views, or claim rendered XML from a
compile-only result. It introduces no worker changes of its own.
Physical compilation, exact phone-only debug installation and standalone native
resource control/persistence remain pending parent coordination after that gate.
No physical pass is inferred from these host tests or probe preparation.

## Initial full-run result and preserved assembly failure

The full offline run on `c1b4ccb` executed 228 controller tests and four protocol
tests with zero failures/errors. Formatting and `qualityCheck` (controller,
worker and protocol lint) passed. Worker unit tests/assembly were not requested;
this is not combined #22 worker validation. Android test assembly then failed
because the newly drafted probe called nonexistent `RunJournal.latest()`.
The exact existing seam is `load(runId)`; the probe was corrected to load its
reserved `failedRunId`, tightening evidence ownership. Production/unit code did
not change after their successful execution. The failed overall build remains
recorded: 3m53s, 145 tasks (112 executed, 33 up to date),
`/tmp/layout-edit-final-quality.log`.

The initial controller APK was produced but is not a phone-verified or final
paired harness. It is preserved in the ignored layout worktree at
`toolchains/alpha-layout-initial-candidate/controller-debug.apk`, SHA-256
`470e7b3637dcd4ce7f91136401ebba1aca7a0ce159fbb559684c5ace93d08c43`, with
`pins.json`. The test APK did not compile; no installation or physical success is
claimed from this run.

```bash
source scripts/env.sh
./gradlew spotlessApply :controller:testDebugUnitTest :protocol:testDebugUnitTest \
  qualityCheck :controller:assembleDebug :controller:assembleDebugAndroidTest \
  --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

The concurrent #22 notice correction changes packaged controller assets. Its
finalized integration `52ff1ea` was merged before refreshing the harness pair
and rechecking the corrected probe's lint. No new unit/production changes
required repeating the successful unit suites. The initial tested result and
candidate remain preserved; that candidate will not be installed as the final
resource harness. The corrected probe's formatting/check, controller lint and
paired controller/test assembly then passed in **1m59s**: 121 tasks, 27 executed
and 94 up to date (`/tmp/layout-edit-final-assets-refresh.log`). No unit suite was
rerun in this asset/test-assembly refresh; the earlier 228+4 executed results are
the unit evidence. The independent #22 worker candidate has its own tests and
artifact record; this run does not claim combined worker validation.

```bash
source scripts/env.sh
./gradlew spotlessApply spotlessCheck :controller:lintDebug \
  :controller:assembleDebug :controller:assembleDebugAndroidTest \
  --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

Pre-remediation host harness identities (controller version 1; test package has no declared version):

| Package / APK | SHA-256 |
| --- | --- |
| `dev.smallwright.controller`, `controller/build/outputs/apk/debug/controller-debug.apk` | `c9535c8bcee0a3e8ba41dae197860d02e3f01d02cbcade5dfd2e39ee485bf644` |
| `dev.smallwright.controller.test`, `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `5f5a57cc449486369fabf85fb50d810af6bb9ee16273cb00a245e1f719d5d108` |

SDK `apksigner verify --print-certs` passed for both APKs; the public certificate
SHA-256 is `19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
SDK `aapt2 dump badging` confirmed the exact package identities. The controller's
packaged `assets/activity-layout-source/LICENSE.txt` matches the finalized full
license bytes exactly, SHA-256
`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`.

Tested source pins:

| File | SHA-256 |
| --- | --- |
| `ToolSchema.java` | `557f61b1bc151e656a43d28800a67da0f35afba9abc8c756d8c67e7c552d532f` |
| `ProjectSourceStore.java` | `5d83b6327b8e69faaf1bdbf3b2ee93f5106c5aec84e16203f3b5ede8d2c8aee9` |
| `SourceReplacementDispatcher.java` | `c36bb44f9d5b777f31f873d2c99cad76532003980192638a181aa6a740783f7d` |
| Unchanged `GenerationRun.java` | `a836aa887e4e8a82a70817dbaaf5bc86776fdbb0e0523256d7296115c22a82ff` |
| Unchanged `MainActivity.java` | `ce5f7931cdbccde16638c5ae10aec0faddead4e6407b71da7d7d09d72db26c3a` |
| `LayoutEditAcceptanceProbe.java` | `ca6ae2020cc5227ad11eae8266527ef091f2908c929f6b144b6405fa769709b2` |
| `ArtifactPolicyInstrumentation.java` | `77b7e9743e0e31e8233927c2ee06251c8f755c582f2811a6f47d88331c36eacc` |

Physical build/recovery/reopen and standalone resource control/persistence remain
pending fresh device/exact harness verification by the parent. No phone build,
model request, generated installation or physical pass occurred during this host
implementation handoff.

Review remediation checkpoint (2026-10-12)

The independent Standards review found no hard violation and identified possible
drift between repeated layout allowlists. The Spec review found that the phone
fixture's two explicit lookup casts would also compile against a nongeneric
`findViewById`; that fixture could not prove the promoted generic contract. The
reports are `/tmp/phone-local-layout-standards-review-20261012.md` and
`/tmp/phone-local-layout-spec-review-20261012.md`, frozen at `c2cbadf`.

Both casts were removed: the generated source now directly assigns the lookup
results to `TextView` and `Button`. Its comment states why the generic contract
is required. One public constant, `ToolSchema.REPLACE_LAYOUT_PATH`, now supplies
the existing bounded replacement path to both schema forms, parser validation
and dispatcher authorization/routing. The independent validation boundaries,
exact two-path enum, storage's private layout constant and all approved-context
guards remain intact. This is review-stage refactoring and fixture correction;
no mirrored constant test or artificial RED was introduced.

The first remediation run genuinely failed controller lint because the initial
legacy schema interpolation used `String.formatted`, available from API 34 while
the controller's minimum is API 31. The 228 controller tests executed and passed
before lint rejected it; the four unchanged protocol tests were up to date. The
failed log is `/tmp/layout-edit-review-remediation.log`: 2m47s, 84 tasks, 15
executed and 69 up to date. Interpolation was corrected to compatible
`String.replace`; no API minimum, lint rule or assertion was weakened.

The following corrected run passed in **2m45s**, 132 tasks: 23 executed and 109
up to date (`/tmp/layout-edit-review-remediation-corrected.log`). All **228
controller tests executed again**, zero failures/errors/skips; this includes
the meaningful replacement protocol (11), dispatcher (8), generation (25) and
context preview (8) tests. The four protocol-module tests remained **up to date**
and retain their earlier executed pass. Formatting/check, controller and
protocol lint, and the corrected paired controller/test assembly passed. No
worker test or build is attributed to this correction.

```bash
source scripts/env.sh
./gradlew spotlessApply spotlessCheck :controller:testDebugUnitTest \
  :protocol:testDebugUnitTest :controller:lintDebug :protocol:lintDebug \
  :controller:assembleDebug :controller:assembleDebugAndroidTest \
  --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

An additional host inspection extracted the generated source concatenation
recipe from the newly compiled `LayoutEditAcceptanceProbe.class`, substituted
the synthetic valid project `app00000000000000000000000000000000`, and passed
those exact bytes through public `ToolSchema.parseSourceReplacement`. Both
direct assignments survived the protocol output exactly; neither lookup cast
exists. The temporary driver `/tmp/LayoutCompiledFixtureProtocolCheck.java`
and `/tmp/layout-edit-compiled-fixture-protocol.log` record this **synthetic
protocol check**, not a generated compilation or phone rendering pass. The
extracted source SHA-256 is
`eaa756b07b03a58c79db4e5ccba43a71f6376edd9e55a09a6777d96518534fcc`.

The corrected pair supersedes the earlier host candidate for eventual physical
execution; the parent preserved the original `c953...`/`5f5a...` pair and logs.
Both new APK signatures verified with the same public certificate SHA-256
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
`aapt2 dump badging` again confirms controller version 1 and the test package's
undeclared version. The packaged full Apache license remains exactly
`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`.

| Corrected artifact / changed source | SHA-256 |
| --- | --- |
| `controller/build/outputs/apk/debug/controller-debug.apk` | `57e69d1ea13f3da9555aec15927bf781db1fd3145a07a7c6e067ecb01cfb1c11` |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `0c117c2c77f98614a1ada81d9185b6e9722593b93117321496fdf726c34ba0c2` |
| `ToolSchema.java` | `8681dc84ff528a45f65290eb8966986f53b47ec5ee4dbaa4f03fd22f0b791486` |
| `SourceReplacementDispatcher.java` | `1db204dd7b5ce45d5be99429dbbcfc569f35696ebf96526b0b2e402c00b5b8ba` |
| `LayoutEditAcceptanceProbe.java` | `0042bcbad107be8054cf75a7ceea21774ce5de5b88a16b8e66a2517bdbdcbd1f` |

Integration `c2cbadf` was merged before remediation and checked again before
commit. Other compiled source pins above remain unchanged. Physical acceptance
is still pending: the parent's previous installer preflight rejected changed
A33 harness identities before any mutation. No phone execution of this probe,
model request or generated app installation occurred in this correction.

The parent independently verified the corrected APK signatures, package names,
all five notice/source assets, 228 passing unit XML cases and source bytes at
frozen commit `222026a`. Durable copies are
`toolchains/alpha-layout-edit-20261012/controller-review-fixed-debug.apk` and
`controller-review-fixed-debug-androidTest.apk`; the table above pins their exact
hashes. Original/final review reports, logs and test/lint reports remain in
`review-fixed-host-evidence/` under that ignored case root. The initial failed
candidate remains at `toolchains/alpha-layout-initial-candidate/` in the main
checkout. Commands above are reproducible from the main checkout using its
project-local environment; original evidence is tied to the recorded commits,
not to whatever later checkout is built.

Both independent final reviews found zero remaining actionable findings.
See [frozen review, exact changed paths and pending phone handoff](code-review-layout-20261012.md).
Concurrent kernel commit `4c49068` adds a separate test selector after the frozen
review; these exact candidates do not claim to validate that later combined
checkout. No generated phone APK exists for this case. The pending phone task
needs exclusive A33 availability, fresh exact harness checks and preservation
of #23's existing test APKs/artifacts before actual XML acceptance.


## Subsequent physical acceptance

The pending bounded A33 gate subsequently passed on2026-10-12UTC after a fresh
verified production update preserving both combined-root test APKs. Actual
malformed XML failure/recovery, source3/version3 compilation with generated R
and direct generic lookup, exact phone-only debug installation, native XML
resource IDs/rendering, count0→1→2 and restart2 passed. Previous project/chat
selection/data and all failures remain preserved. No model call or full alpha
pass. See [new physical record](layout-phone-a33-20261012.md) for exact artifacts,
commands, assistance, source/runtime evidence and limits. Earlier pending/failed
statements above remain historical and are not relabelled as passes.
