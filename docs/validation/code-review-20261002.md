# Review and corrections, 2026-10-02

User-selected baseline: `2195026fa7dad7c38209fdf98ff403b3d4be48f1`.
Reviewed head: `d6e4828`; captured command: `git diff 2195026...HEAD`.
Commits: `e4f9b66`, `33e79b2`, `9dbc287`, `d6e4828` (19 changed files).
Two independent agents reviewed Standards and Spec in parallel. Root checked
their findings and corrections. No phone, model request or generated phone
source/APK transfer was used.

## Standards

No documented-standard breach. One judgement finding, **Duplicated Code**:
Reset and Step probes repeated journal queries, run-ID validation,
READY/revision/version checks and SHA-256 conversion. A focused test-only
`PhoneAlphaRunHistory` now owns the common queries/guards; callers explicitly
retain their three/four/five prior-run counts. Both probes reuse the existing
`PhoneAlphaCounterProbe.sha256` implementation.

Root and the original Standards reviewer verify matching query limits,
placeholder counts, duplicate-ID rejection, close-before-production-load,
unique revisions and Step's run-ID equality. Reset gains the same equality
check. Frozen project/chat/source/artifact/signer and repair-origin guards
remain in their respective probes. No probe framework or production interface
is introduced. Finding resolved; no further actionable Standards finding.

## Spec

One P2 finding: both Reset ledgers recorded `install_result=confirmed` for
authorized USB debug updates. AGENTS requires: "record debug installation
separately from production installation consent." Benchmark protocol step5
requires recording "explicit user confirmation." Notes distinguished the
debug route, but the structured outcome did not.

The schema now accepts `debug_installed`; protocol reserves `confirmed` for
observed Android installation confirmation. Both Reset entries and two earlier
Counter feedback entries with the same explicit debug-update evidence are
corrected. Only `install_result` changes; failed/accepted functional outcomes,
hashes, timing, usage and notes remain unchanged. The actual manually installed
initial Counter remains `confirmed`. Related STATUS descriptions are corrected.
The original Spec reviewer verifies backward compatibility and completeness.
Finding resolved; no further actionable Spec finding.

## Verification and remaining gates

Real validator CLI regression first fails for the new debug outcome under the
old schema, then passes after the schema change. Three CLI tests pass: debug
outcome accepted, Android confirmation still accepted, unknown outcome rejected.
All seven actual benchmark lines validate. Test-only synthetic records live
only in temporary directories, never in the measured ledger.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p test_validate_benchmarks.py
python3 scripts/validate_benchmarks.py benchmarks/results/*.jsonl
source scripts/env.sh
./gradlew :controller:testDebugUnitTest :worker:testDebugUnitTest :controller:lintDebug :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

Gradle passes28s, rebuilding the actual controller instrumentation APK.
Unchanged controller/worker JVM suites are up to date: 210/56 passing results
remain applicable to unchanged production code. Controller lint passes with
zero errors/73 warnings; Android-test lint analysis runs for the changed probes.
No new physical execution is claimed.
Ignored logs: `toolchains/product-phone-20261002/review-debug-install-red.log`,
`review-debug-install-green.log`, `code-review-fixes-build.log`.

Controller test APK:
`controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk`,
SHA256 `5748f3df034c86ab6d6120bf52facab8c013068165ee33a43460c806cfe59070`.
It has not been installed. Next physical connection must verify the harness
and install this updated test APK before running the prepared Step repair
guards/preview. The shared journal helper is compiled/reviewed, but its
refactored physical paths remain unexecuted while the user needs the phone.
Earlier physical observations are retained as historical results. ART numeric
capture, detailed repair transport/context, full source/ABI/license and broader
alpha gates remain pending as already documented.

Standards: one heuristic finding, fixed; worst was repeated journal guards.
Spec: one P2 finding, fixed; worst was install-consent misclassification.
