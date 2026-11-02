# Bounded layout/API checkpoint review

Independent Standards and Spec reviews cover only #21/#22, not the whole alpha.
The frozen comparison is
`git diff c1b4ccb13f9a4117b832c753e4120ba5bc384637...222026a710d62dd493d4f479e002c8356b10cf17`.
The eight commits are `a3a903e`, `1fdc1df`, `9349ac2`, `47904b1`, `52ff1ea`,
`38095c7`, `c2cbadf` and `222026a`. Implementers used separate worktrees;
a merger fast-forwarded the verified changes onto
`integration/private-alpha-20261012`. The parent inspected the fixes, actual
test XML, public signatures, packaged notices and frozen source pins.

## Standards

Initial review: zero hard violations, one possible Duplicated Code smell
(a judgment call): the bounded layout path was repeated across schemas,
parser and dispatcher. One implementer introduced
`ToolSchema.REPLACE_LAYOUT_PATH` while retaining the independent checks.
Final independent review: **zero hard violations, zero remaining smells**;
the original finding is resolved. The documented locality, isolation,
provenance and honest-evidence boundaries remain intact. Formatting is
tool-enforced and excluded from findings.

## Spec

Initial review: one P2 fixture defect. Explicit lookup casts could conceal
loss of generic `Activity.findViewById`. Both casts are removed; generated
source directly assigns lookup results to TextView and Button. Final
independent review: **zero remaining actionable findings**, one resolved.
No scope creep or production approval/revision/security regression was found.

Acceptance is still partial: #21/#22 require actual phone compilation,
resource inflation, exact generated APK installation and native
interaction/reopen persistence. No execution of this candidate occurred.
Both issues remain open. Whole licensed API/source delivery, detached transport
evidence and the fixed twenty-request suite on both phones remain open too.

## Verification and preserved artifacts

The corrected offline command in the layout implementer's worktree passed
in 2m45s: 228 controller tests executed with zero failures/errors/skips;
four unchanged protocol-module tests were cached. Formatting/check,
controller/protocol lint and controller/test assembly passed. Its genuine
earlier API-compatibility lint failure is preserved; compatible String.replace
fixed it without a lint suppression or minimum-SDK change. The separately
verified #22 worker ran 58 JVM tests and its quality/assembly checks; the
parent also independently replayed the actual raw-source extraction/ECJ/ABI
experiment. See [layout evidence](layout-edit-20261012.md) and
[source-derived API evidence](layout-source-api-20261012.md) for commands,
original failures, timings and source/tool hashes.

Durable ignored controller evidence is
`toolchains/alpha-layout-edit-20261012/review-fixed-host-evidence/`.
Original and final separate review reports, actual unit XML/lint reports,
failed/corrected logs and the compiled-fixture protocol driver are retained.
`parent-controller-host-review-02.json` independently pins the frozen sources,
228 XML test cases, exact packages, verified public signatures and all five
source/notice assets. `parent-merged-tested-source-pins-02.json` records the
source hashes. The public harness certificate is
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.

| Uninstalled harness candidate | SHA-256 |
| --- | --- |
| `toolchains/alpha-layout-edit-20261012/controller-review-fixed-debug.apk` | `57e69d1ea13f3da9555aec15927bf781db1fd3145a07a7c6e067ecb01cfb1c11` |
| `toolchains/alpha-layout-edit-20261012/controller-review-fixed-debug-androidTest.apk` | `0c117c2c77f98614a1ada81d9185b6e9722593b93117321496fdf726c34ba0c2` |
| `toolchains/alpha-layout-api-20261012/worker-debug-license-complete.apk` | `436d6a83aceff3635b471484f1c32c50d10b993d445434d24adba02d012cbb4c` |

The parent installer aborted before mutation when #23's separately authorized
run changed the installed controller.test/worker.test identities. Its original
guard failure is `install-baseline-rejection-03.json`; no candidate was
installed and no failed observer was reinterpreted as a pass. The pending
availability question concerns exclusive use of the A33, not renewed approval
for already authorized tests. Fresh connection/ownership/exact artifacts must
be established before any phone mutation. Preserve the existing kernel test
APK, all projects, generated artifacts, signers and selections.

After this frozen review, concurrent kernel checkpoint `4c49068` added another
test selector to ArtifactPolicyInstrumentation and changed separate resource
test/build files. The parent source-pin guard detected that difference. The
APKs above remain exact **222026a** candidates; these tests/reviews do not
claim to validate that later combined checkout. A combined candidate requires
fresh host checks and an independently pinned pair before phone acceptance.

## Exact implementation paths

The reviewed range changes these paths:

- `controller/src/main/java/dev/smallwright/agent/ToolSchema.java`
- `controller/src/main/java/dev/smallwright/controller/ProjectSourceStore.java`
- `controller/src/main/java/dev/smallwright/controller/SourceReplacementDispatcher.java`
- `controller/src/test/java/dev/smallwright/agent/SourceReplacementProtocolTest.java`
- `controller/src/test/java/dev/smallwright/controller/GenerationRunTest.java`
- `controller/src/test/java/dev/smallwright/controller/SourceReplacementDispatcherTest.java`
- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`
- `controller/src/androidTest/java/dev/smallwright/controller/LayoutEditAcceptanceProbe.java`
- `worker/src/main/java/dev/smallwright/worker/SourceStubs.java`
- `worker/src/test/java/dev/smallwright/worker/SourceStubsCompileTest.java`
- `scripts/activity-layout-api/ActivityLayoutExtractor.java`
- `scripts/activity-layout-api/tool-pins.json`
- `scripts/build_activity_layout_source_api.py`
- `scripts/test_activity_layout_source_api.py`
- `toolchain/manifest.json`
- `toolchain/licenses/activity-layout-source/Activity.methods.java.inc`
- `toolchain/licenses/activity-layout-source/LICENSE.txt`
- `toolchain/licenses/activity-layout-source/NOTICE.txt`
- `toolchain/licenses/activity-layout-source/PROVENANCE.json`
- `toolchain/licenses/activity-layout-source/frameworks-base-NOTICE`
- `docs/TESTING.md`
- `docs/validation/layout-edit-20261012.md`
- `docs/validation/layout-source-api-20261012.md`

This handoff additionally updates this review record and `docs/STATUS.md`.
No model request or phone mutation was made for #21/#22. Separately recorded
live-repair and kernel work retain their own evidence and scope.

The two owned implementation worktrees were removed without force after their
committed heads were verified integrated and their nonsecret APKs/reports/logs
were backed up. Feature branches remain. Only their verified setup symlink names
were unlinked; shared toolchain/signing targets and unrelated kernel/live-repair
worktrees were untouched. The ignored cleanup receipt is
`toolchains/alpha-layout-edit-20261012/own-worktree-cleanup-01.json`.
