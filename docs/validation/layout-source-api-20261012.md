# Bounded Activity layout compile API — 2026-10-12

Issue #22 adds only the source-derived compile declarations needed for
`Activity.setContentView(int)` and generic `Activity.findViewById(int)`.
Whole Android API assembly, raw module closure, source/resource distribution
clearance and private-alpha acceptance remain open.

The approved seams are actual pinned raw source → stock Metalava source model
→ bounded declarations → actual ECJ with empty boot path, plus selected
class-file descriptor/access/Signature checks. Physical XML inflation, button
lookup, click and retained-state reopen belong to the parent device checkpoint.

## Inputs and source association

Both actual source files belong to frameworks/base at
`99b01a65cc4c104933788b3143285ab6bae65827` (Android 16 r2):

| Raw file | Git blob | SHA-256 |
| --- | --- | --- |
| `core/java/android/app/Activity.java` | `c129fde3f81916e3ed4e2e67b77bfeac2288a1dc` | `1b9327a6b634673de3d436d93593ae64c9d35c154b55234e48292498ee5a08e7` |
| `core/java/android/view/View.java` | `1213d173ab3bd4b4aa0d2f56253edf6af308627c` | `ceb24df845d5304ba82422d828f0bd3af8f9da93c33ce898935850df875c7ac2` |

Each has the actual `Copyright (C) 2006 The Android Open Source Project`
Apache-2.0 prologue. The per-file declarations and their generic owner are
the evidence for this bounded fragment. Aggregate framework notice retrieval
and unresolved component questions remain in the existing provenance audit.
The published SDK source/JAR is comparison-only and does not supply a method
declaration, bound or access modifier to the extractor.

The tool pin manifest retains the independently reviewed installed stock
Metalava runtime (90 files) and local JDK (46 files). Metalava source revision
is `9809e0d70a5b807be3d7070d51f9c4714f2fdcb4`; the pinned local JDK is
21.0.12.1+1. Pins must match before any Java tool launch.

## Actual isolated execution

The isolated implementer worktree starts at integration commit `fda84a6`.
The parent granted one sequential Java/parser/ECJ slot. The initial actual run
is preserved at ignored
`toolchains/alpha-layout-api-20261012/integration-5718c7bedb6a4847ad8b7d0083a4f8b3`.
Existing-header helper javac and dump passed, then ECJ exited 255 with exactly
the two expected compiler errors: `setContentView(View)` is not applicable to
an integer, and `findViewById(int)` is undefined. This is behavioral RED.
The subsequent missing-runner exit 2 is a separate setup failure; it is not
claimed as behavioral evidence.

After implementing the runner, the same test passed in 54.112 host seconds:
ignored `toolchains/alpha-layout-api-20261012/integration-3428b7990f0b4f6c8fcd7e1635974625`.
Its actual stage records and logs preserve the existing-header RED, the
source-only GREEN, and both missing-raw-method variants:

| Boundary | Result |
| --- | --- |
| Existing contract ECJ, empty boot/classpath | 255, same two expected API errors |
| Extractor javac against pinned stock Metalava | 0; 2.646 observed host seconds |
| Stock parser, only raw Activity/View and empty Android classpath | 0; 11.913 observed host seconds |
| ECJ using only existing headers plus generated two-method fragment | 0 |
| Raw Activity identifier renamed for `setContentView` | One method selected; generator rejects with 1; ECJ rejects int call with 255 |
| Raw Activity identifier renamed for `findViewById` | One method selected; generator rejects with 1; ECJ rejects lookup with 255 |
| Source-only selected class ABI | Exact descriptors, access flags and generic Signature pass |
| SDK opened last, comparison-only | Same selected descriptors/access/Signature; no whole-Activity parity claim |

The positive Activity class methods are public instance methods, flags `0x1`:
`setContentView` descriptor `(I)V`, and `findViewById` descriptor
`(I)Landroid/view/View;`, with Signature
`<T:Landroid/view/View;>(I)TT;`. Method names select the bounded surface;
types, names of parameters, bounds, visibility and erased descriptors come
from the actual source model. The generic return requires no cast in the
fixture. The fixture class's exact class references are its own Activity,
platform Activity/Button and project R/R.layout/R.id, with no packaged
platform implementation. The ECJ boot directory remains empty.

Raw Activity and View remain byte-identical in the positive source root and
resolve as `COMMAND_LINE` models at those isolated file paths. Negative
variant files explicitly rename a selected raw identifier throughout Activity;
their changed hashes and test-only status are recorded, and no worker
constant is emitted. The parser's 101,504 diagnostic bytes are preserved
without suppression. Other framework imports remain unresolved; these two
selected declarations do not prove complete framework closure.

The fragment carries the actual Activity copyright/Apache prologue and an
explicit modification notice: selection, annotation omission, qualified model
types and replacement compile-only bodies. The generated declarations are
not executable platform implementation. Resource annotations are omitted by
the stock model's default type rendering; no annotation definitions are
invented or packaged.

| Positive output/tooling bytes | SHA-256 |
| --- | --- |
| Executed runner | `cb800651acc9c7d3988a1cea1582a23c685efc26171a51542b4c1176d8ae1290` |
| Executed Java extractor | `5a3919d798a209c364208465d7e19fc5f51cec075f51afc9ecca41a50b0688a4` |
| Actual model JSON | `85871d26b2ba7a88a166d4859051642a42c3a645fc969e791a30fcdbcf4435b9` |
| Two-method fragment | `45bdc99341cf536e8904d0df35f62c1241f1459083c745868f36fffc4b0d9c94` |
| Generated worker constant | `98d42658a8481c3f391b9633168286a5012b99259955be45fa487252ee22879c` |

Parent independently replayed the strengthened selected-ABI test in 53.371
host seconds at `integration-b09d5588d8b140b39baded5dda490a28`. The final SDK
comparison checks complete selected method records, including all parsed
attributes. Parent rehashed all 136 installed tools, both original and
isolated raw files, source associations, exact output bytes and the concrete
notice package, then approved bounded normal promotion. The ignored
`parent-source-proof-review-01.json` retains that review. Source-only fragment
and standalone constant hashes are exactly reproducible across both runs;
absolute source paths make model JSON receipts run-specific.

## Normal compiler contract

Only the generated constant's one package declaration is removed before its
class body is appended unchanged to the existing `SourceStubs.java`. The
existing whole-file compiler-input checksum and formatting exclusion cover
the derived constant. A single concatenation inserts the two generated methods
into the existing Activity header, preserving all its earlier limited methods.
No verifier framework, Gradle change, SDK asset, permission or dependency is
added. The manifest now records the derived fragment and source association.

`SourceStubs.java` SHA-256:
`d4c9ebdd193e3733d06a50f02cd69c4f8d65b7e7721eaa949e0d7e0fe5fb26db`.
The exact appended constant (standalone output with package declaration
removed) has SHA-256
`389e458353d68977762fb8288a70be36e085be58a91b6a2f305abb8a2e837295`.
The five bounded notice/source files live at
`toolchain/licenses/activity-layout-source/`; the worker's existing asset
contract includes that directory. PROVENANCE records both standalone and
appended constant hashes and the embedding change.

Before final handoff, review caught that the first copied `LICENSE.txt` was
an existing 15-line Apache pointer/prologue rather than the complete terms.
That initial APK and notice package remain preserved in the earlier host
receipt. The asset is corrected to the complete 202-line standard Apache-2.0
text, byte-identical to the already tracked Gson and source-built wrapper
license files, SHA-256
`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`.
Actual grant/copyright associations continue to come from the pinned raw
Activity/View prologues; copying standard license terms does not substitute
for them. PROVENANCE records the license text origin and corrected hash.
The final APK asset refresh is recorded below; source/method bytes are
unchanged.

The new normal-contract JUnit test first encountered a test setup failure:
Android's compile classpath lacks `Files.writeString`. That failed compile is
retained at `normal-red-evidence/layout-api-normal-red-20261012.log`; using the
existing supported `Files.write` pattern corrected setup before the real RED.
The subsequent focused JUnit test exited 1 with the same two actual ECJ
diagnostics as the isolated baseline. The XML failure record and command log
remain in `normal-red-evidence/`. After promotion, the same focused JUnit
test passed (`BUILD SUCCESSFUL in 26s`); XML/log are in
`normal-green-evidence/`. Source checksum verification passed.

A preparatory Python test demonstrated that an unpinned separate generated
class would evade the old verifier. That test-only change was shelved when
the parent selected the smaller existing-file embedding. Only that authored
uncommitted test hunk was reverted; the verifier itself remains unchanged.
The actual observed command/result is retained in
`shelved-separate-file-verifier-red.json`; it is not a compiler boundary pass.

Normal checks completed sequentially with the parent-managed build slot:

```bash
source scripts/env.sh
./gradlew :worker:testDebugUnitTest --tests dev.smallwright.worker.SourceStubsCompileTest.resourceLayoutAndGenericButtonLookupCompileWithoutPackagedAndroidJar --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew spotlessJavaApply spotlessCheck :worker:testDebugUnitTest :worker:lintDebug :worker:assembleDebug :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=scripts python3 -m unittest test_verify_compiler_inputs
python3 scripts/verify_compiler_inputs.py --manifest toolchain/manifest.json --inputs toolchain/input
```

The focused contract test passed after the observed RED. The full Gradle
command exited 0 in 2m51s, 126 tasks (90 executed, 36 up to date), including
58 worker JVM tests with no failures/errors/skips, worker lint, formatting
check and both harness assemblies. Formatting adjusted earlier test source
layout without changing existing test behavior. The 10 Python checksum tests
passed, and actual manifest/input verification passed after its status update.
Logs, XML test/lint results and exact packaged asset hashes remain under
ignored `worker-host-evidence/`, including `host-receipt.json`.

All five packaged `assets/activity-layout-source/` entries matched the then
tracked files byte-for-byte in the first host build. That first APK is a failed
packaging candidate because its license file was incomplete, as recorded above.
The corrected refresh likewise matches all five current files byte-for-byte.
APK inspection found no Android SDK or experimental
platform JAR asset. The unchanged worker manifest, isolation, compiler
argument policy and app-only DEX selection remain in place; this host check
does not replace on-phone app-only DEX inspection.

| Retained host-built harness APK and disposition | SHA-256 |
| --- | --- |
| **Final complete-license candidate:** `toolchains/alpha-layout-api-20261012/worker-debug-license-complete.apk` | `436d6a83aceff3635b471484f1c32c50d10b993d445434d24adba02d012cbb4c` |
| Failed initial notice candidate; preserved: `toolchains/alpha-layout-api-20261012/worker-debug.apk` | `4fe322c0ba46166e80ac0bf8a610bc91c81315f70bce318e1f1de12f2f7d361b` |
| Built only; do not replace existing physical worker.test: `toolchains/alpha-layout-api-20261012/worker-debug-androidTest.apk` | `d5f0a06e5747c447c08e8f7a87d79a475594c898ece46bdb8cc26633b3838061` |

After the parent read all 202 corrected license lines and accepted the source
association, the same capped offline command ran `:worker:assembleDebug` only.
It exited 0 (`BUILD SUCCESSFUL in 22s`; 57 tasks, four executed and 53 up to
date). The prior 58 tests are reused because no code/compiler source changed.
`worker-license-refresh-evidence/host-receipt.json` and its command log record
the distinct refreshed APK and all exact assets. The corrected PROVENANCE
hash is `9e0f32a240b8f506fdcd479056cbc1d1ffff87a6763ea05f9df1fa226fc7ece4`.
ZIP entry payloads differ from the preserved initial APK in exactly
`assets/activity-layout-source/LICENSE.txt` and `PROVENANCE.json`; all DEX,
manifest, resources and other payload bytes are identical. The initial
APK/receipt/proposal remain preserved and are not accepted distribution inputs.
The controller's existing license-asset contract also needs the parent-owned
asset refresh after merging this corrected checkpoint.

The worker test APK was built only. Parent's current physical A33 worker.test
belongs to separately authorized kernel-resource work and must be preserved.
No install, uninstall or data clear occurred here. The isolated worktree
merged current integration `c1b4ccb` before handoff; root STATUS is parent-owned.

No phone action or generated-app APK build ran in this implementer. XML
inflation/rendered button/click/retained-state acceptance remains with the
parent. The opt-in test's default invocation skips and is not runtime evidence.

```bash
PLB_LAYOUT_API_ROOT=. \
PLB_LAYOUT_API_INTEGRATION=1 PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=scripts \
python3 -m unittest test_activity_layout_source_api
```

Run this from the main checkout. The output records actual stages,
diagnostics and source-only results beneath ignored
`toolchains/alpha-layout-api-20261012/integration-*`. Preservation of failures
does not establish compiler or physical runtime success.

The parent independently replayed the actual source-only parser/ECJ/negative
method-removal/selected-ABI checks from the main checkout in 53.371s; evidence
is `integration-b09d5588d8b140b39baded5dda490a28` beneath the ignored case root.
Actual worker test/lint reports are also retained in `archived-worker-reports/`.
The corrected layout controller pair packages the same five notices and now
requires direct generic phone lookup assignments. Separate final Standards/Spec
reviews have zero remaining actionable findings; see
[frozen review and pending phone acceptance](code-review-layout-20261012.md).
#22 stays open until actual phone compilation, exact generated APK installation,
XML rendering/click and retained-state reopen pass. No physical result is inferred
from the host derivation or compiled-fixture protocol check.


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
