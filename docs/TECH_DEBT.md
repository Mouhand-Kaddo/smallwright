# Codebase design and technical debt

Audit and remediation date: 2026-10-02. The six findings in
issue 3 of the earlier private tracker
are repaired and verified. Scope: controller, isolated compiler worker,
Binder protocol, build configuration, host scripts and their tests.
This assessment does not certify that the entire repository has no
undiscovered debt. Platform coverage, redistribution/source correspondence
and release gates remain in
issue 1 of the earlier private tracker.

## Module design

Preserve the existing `ProjectStore`, `GenerationRun`, `VerifiedArtifact`,
`SignedArtifactReceipt`, `RecoveryAdoption` and worker admission seams.
The different-UID controller/worker boundary is required. The repairs make
generation own its build identity and completion ordering, and worker
admission own request lifetime. They introduce no provider framework,
generated command interface or general orchestrator.

## Resolved findings

Historical scores use impact/risk/effort, each 1–5, with priority
`(impact + risk) × (6 − effort)`. These are prioritization estimates.

| Priority; I/R/E | Finding | Repair and evidence |
| --- | --- | --- |
| 28; 3/4/2 | ECJ/R8 checksums were recorded but unenforced | `verifyCompilerInputs` is a prerequisite of worker compile, dex, package and lint tasks. Both actual inputs match their existing pins. A genuine mismatch fixture made the real Gradle prerequisite fail before worker Java compilation; verification runs even when compilation is up to date. Python regressions also reject either changed JAR, missing inputs and symlinks. |
| 24; 3/3/2 | Recorded build identity depended on callback ordering | `RecordedBuild` receives the exact persisted build ID with the committed revision. Host and phone probes assert journal identity and artifact-directory identity, including a real signed build and repair. |
| 18; 3/3/3 | Stop could cancel between receipt publication and readiness | `GenerationRun.Control` uses a short completion claim; publication runs outside its monitor. Stop before the claim cancels, while Stop during completion returns immediately without cancelling it. A published result with a failed readiness write preserves recoverable `BUILDING`. Host regressions and physical SQLite/filesystem tests cover each boundary. |
| 18; 3/3/3 | Project recovery blocked the activity thread | Project selection, source preparation and recovery run on the existing serial executor; guarded UI application keeps controls disabled until ready. The original APK failed the blocked-recovery heartbeat probe on the phone; the repaired APK passed, with two disposable projects retaining source/package identity and selection after reopening. |
| 18; 3/3/3 | Worker requests had no overall deadline | Snapshot transfer has a 10-second absolute nonblocking descriptor deadline. Worker admission enforces a 120-second absolute whole-request deadline and kills a stalled worker plus its registered AAPT2 child. Physical probes passed held-open pipe closure and a subsequent real build, plus intentional worker/child termination and fresh-process compilation. Timeout classification never authorizes AI repair. |
| 16; 2/2/2 | Repair and token limits had multiple owners | Named limits live in the existing `UsageBudget` policy module. Provider parsing, generation, journal and UI consume them while enforcing their own boundaries. Known-number regressions cover 67,584 per-attempt and 270,336 per-run reported tokens. |

Two additional defects repaired during the audit remain covered: AAPT2 output
reader failure is checked after joining the reader, and generation/repair
publish their signed receipt before readiness. Completed host child-process
checks exercise output capture and overflow rejection.

## Verification and limits

Fresh host suites passed 199 controller tests, 48 worker tests and 33 Python
tests. Protocol has no unit test sources. Final tests, lint and all four
debug/instrumentation APK assemblies passed. Lint has zero errors and 71
controller/two worker warnings; none were suppressed. Most controller
warnings concern text resources. The Bouncy Castle trust-manager warning and
other dependency findings require focused review; this is not a dependency
security or license clearance.

The connected Samsung Galaxy S23 Ultra (SM_S918B, Android 16/API 36, ARM64)
passed all six targeted runtime checks. Controller UID 10465 differs from
worker UID 10464. The runner installs only locally built harness/test APKs,
uses disposable project roots, and retains existing projects and generated
app data. The real compiler probe uses a newly generated test signer and
removes only that new alias.

The completion fault probe uses explicitly verification-only bytes to test
SQLite and receipt ordering; it does not claim those bytes are compiled APKs.
Its injected interruption reopens persisted state without killing the
controller. The worker-death probe really kills the process, using a
500-millisecond test budget and an interruption-resistant blocked job; it
does not measure a real ECJ/D8 hang at the production 120-second limit.
A separate phone probe runs real Binder compilation, validation, signing
and simulated-proposal repair. No cloud model call or generated-app
installation/update was exercised in this remediation pass.

Exact changed paths, commands, APK hashes and local evidence directories are
in [STATUS.md](STATUS.md). Reproduce the physical checks with the documented
[testing runner](TESTING.md#physical-technical-debt-regressions).
