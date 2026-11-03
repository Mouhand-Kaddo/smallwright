# Phone-local AI App Builder Implementation Plan

> **Status:** Approved for local implementation. Follow `AGENTS.md` for execution rules and `docs/STATUS.md` for verified progress and blockers.

**Goal:** Let a user describe an Android app, have a cloud-backed agent edit and compile actual source files entirely on the user's unrooted Android phone, install the result with user approval, and improve it from feedback without a remote development workspace.

**Architecture:** An Android controller owns the conversation, project files, history, credentials and signing. A restricted, phone-local compiler worker consumes one immutable project snapshot and produces an unsigned APK; the controller validates, signs and presents it for installation. Generated apps run as ordinary, separate Android applications—not streamed emulators or previews hosted on a server.

**Tech Stack:** Proposed controller: Kotlin, Jetpack Compose, coroutines, Android SQLite/filesystem, Android Keystore, PackageInstaller and Binder IPC. Proposed first output: real Java/XML Android source using framework APIs, compiled with an Android-compatible compiler/resource/dex pipeline. Exact compiler versions and packaging are decisions gated by physical-device evidence, not assumed compatibility. Kotlin output and richer native libraries follow only after that baseline works.

---

## 1. Scope and non-negotiables

### Confirmed requirements

- The product uses a ChatGPT-like structure: users have projects, and each project contains multiple persistent chats. Chat is the main place to request and discuss app changes.
- The LLM is a cloud model. The phone executes the agent's tools and applies its edits.
- Source, workspace, build processes, build artifacts and generated-app execution reside on the phone.
- No remote shell, remote workspace, remote compilation, server-side repair, cloud emulator or hidden remote fallback.
- User testing is part of the intended loop. Installation requires an explicit user action.
- The output is an independently installable native APK plus editable/exportable source, not merely a website, screenshot or proprietary mini-app description.

### Proposed initial scope—not additional user requirements

- Directly distributed private Android alpha, rather than making Play approval a prerequisite to technical learning.
- ARM64 phones; proposed initial minimum Android 12. Test both a representative midrange phone and a newer Android version before expanding support. Physical hardware availability is not yet known.
- One local project task at a time; one toolchain; no parallel agents on the phone.
- First apps: single-user lists, forms, trackers, checklists, local records and explicit file export.
- Generated apps have no network permission by default. No accounts, payments, hosted backends, remote sync, arbitrary plugins, NDK dependencies, general shell access or automated publishing in the first milestone.
- Controller UI can use Kotlin/Compose regardless of the first generated source language. Java/XML is a risk-reduction starting point for local compiler integration, not a permanent product limitation.
- Cloud calls use a user-entered API credential for the technical alpha. No vendor secret embedded in an APK. Select one provider/model after checking tool-call support, terms and a user-approved spend cap; do not assume a personal subscription grants API access.
- Building the harness itself on the development team's laptop/CI is separate from the product promise. Every end-user generated app must be built on the phone.

### Evidence and workspace status

This document began as a plan before implementation evidence existed. Current results and remaining gates are recorded in `docs/STATUS.md`. File paths below are relative to this repository.

### Project and chat experience

The user opens a project, selects or starts a chat, and describes the app or a change. The conversation shows progress, results and recovery actions. A successful build presents an action to install the verified app through Android's confirmation flow. Toolchain details and build diagnostics remain available when useful, without becoming required steps in the normal conversation.

Proposed initial behavior: one project owns one generated app, its source revisions, build history and signing identity. Chats have separate message histories and work on that shared project. Starting a new chat does not create a new app or signing key. Each edit/build records its originating chat and exact project revision; an older chat cannot silently overwrite newer changes. Keep one active editing/build run at a time, with its status visible when switching chats.

Android compatibility is a project setting with a tested default, applied to requests from every chat. Explain a version requirement only when it affects a requested feature. Persistent projects/chats and recovery across controller restarts need implementation tests; this section records the intended experience, not a claim that the current compiler-test UI already provides it.

## 2. Build the riskiest part first

Sequence:

1. Prove a packaged modern-target Android app can compile, sign and install a native app locally, without AI.
2. Prove the compiler boundary cannot read cloud credentials, signing material or unrelated projects.
3. Add a bounded agent that edits source and uses that working compiler pipeline.
4. Add the user test/feedback/update loop and recovery.
5. Run a private device/user alpha before broadening language and feature support.

Do not spend the first sprint on a polished chat UI. Do not treat a desktop build, a downloaded prebuilt APK, an emulator-only result, or a Termux-only demonstration as passing the product's local-build gate.

## 3. Proposed architecture

```text
PHONE: controller application (trusted, has Internet permission)
  Native UI / conversation / progress / cancellation
  Local project store + immutable snapshots + run journal
  Cloud model client + context filter + spend limits
  Tool dispatcher + source patch policy
  Artifact validator + per-project signer + install coordinator
             |
             | authenticated Binder IPC; bounded input/output
             | one project snapshot, never credentials or signing keys
             v
PHONE: compiler worker (different Android UID, no Internet permission)
  Packaged, pinned compiler and resource/dex tools
  Read-only known dependency inputs
  Disposable scratch workspace for the active request only
  Fixed compile pipeline; no user build scripts or annotation processors
             |
             | unsigned APK + bounded structured diagnostics
             v
PHONE: controller verifies -> signs -> user approves installation
             |
             v
PHONE: standalone generated application (its own Android UID)
  Minimal manifest permissions
  Own private app data
  Optional explicit diagnostic export in development builds

ONLY CLOUD CONNECTION:
  controller -> selected model API -> controller
  Prompts/selected source/errors may leave the phone; tools never execute there.
```

### Why a separate worker application first

A normal `android:process=":worker"` is not a UID/security boundary. A companion worker APK provides normal private scratch storage under a different UID without granting it Internet or user-data permissions. It adds a second install to the alpha: disclose this friction rather than pretending it is a seamless single-app release.

The controller and worker are signed by the same harness publisher certificate solely to authenticate a signature-protected IPC service. Generated apps use separate per-project keys and must NOT share that certificate. Verify the Binder caller as well as the declared permission. Transfer large snapshots/artifacts with bounded file descriptors, not large Binder byte arrays. No unauthenticated localhost command server, implicit intent command channel or broad content provider.

A single APK with an `isolatedProcess` service was tested on the A33 and does not work with the current toolchain: the isolated process has no writable directory and cannot reopen passed descriptors by path, and AAPT2 needs paths ([record](validation/isolated-process-a33-20261103.md)).

The worker holds only the current project's scratch copy and public toolchain inputs, not a mirror of the entire project store. Wipe stale scratch before accepting a request after a crash. Run serialization and cleanup are part of the isolation contract.

### Compiler route and fallback rules

**Preferred experiment:** a small, packaged, fixed pipeline:

1. Validate manifest, source/resource paths, input sizes and allowed features.
2. Process resources using an Android-compatible packaged AAPT2 or supported resource-tool integration.
3. Compile Java/XML template sources with an Android-compatible compiler, evaluating ECJ-based embedding as a candidate.
4. Convert class output to DEX using an Android-compatible packaged D8 integration.
5. Package resources and DEX into an unsigned APK; validate and align before final signing as required by the selected signing tool.
6. Controller signs and verifies the final artifact; install the exact verified bytes.

ECJ, D8, AAPT2 and signing libraries are candidates, not a claim that their stock desktop distributions run under ART. Validate API compatibility, redistribution terms, host/target ABI, dependency licensing, native page-size compatibility and modern target-SDK behavior before selecting versions.

**Bounded fallback experiment:** a packaged Android-compatible JVM/toolchain with a fixed Gradle template. This is acceptable only if its packaging, licensing, device performance and execution boundary pass the same tests. Lock wrapper, plugins and dependencies; prohibit generated Gradle edits and annotation processors. Do not introduce this complexity unless the smaller pipeline fails a documented requirement.

A Pi/Termux experiment can help assess local agent behavior, but is not a substitute for the packaged-app build gate. Do not force end users to configure Termux. If neither packaged route works safely within the experimental timebox, stop and report the precise blocker. Do not quietly move compilation to the cloud or replace the output with a WebView.

## 4. Agent contract and local data

### Initial tools

- `list_files(project_id, path)`
- `read_file(project_id, path, offset, limit)`
- `search_text(project_id, query, path)`
- `apply_patch(project_id, expected_revision, changes)`
- `build(project_id, revision)`
- `read_build_diagnostics(build_id, offset, limit)`
- `request_user_test(build_id, checklist)`

No arbitrary `bash`, `exec`, package installer, network fetch, permission grant, APK signing or deployment tool is exposed directly to the model. The agent can ask for a build or testing; the controller owns the state transition and enforces limits. Later, add constrained test execution only inside a boundary that has separately passed adversarial tests.

The model edits real source. A declarative feature specification can help it plan, but must not replace source files with a proprietary runtime-only format.

### State machine

```text
DRAFT -> PLANNING -> EDITING -> BUILDING
                           ^      |
                           |      +-> REPAIR_NEEDED (bounded retries)
                           |                 |
                           +-----------------+
BUILDING -> READY_TO_TEST -> AWAITING_INSTALL_CONFIRMATION
         -> INSTALLED_UNVERIFIED -> AWAITING_USER_FEEDBACK
         -> USER_ACCEPTED or EDITING

Any active step -> PAUSED / CANCELLED / FAILED
```

A model's claim that a build or test passed cannot change status. Use worker exit status, artifact validation, PackageInstaller results, and user feedback respectively. After process death, verify actual package state and recover from the journal; do not blindly repeat installation or model calls.

### Proposed alpha limits

These are initial product-policy settings to tune, not measured optimums:

- One active run and build at a time.
- Three automatic repair attempts per requested change, then an explicit handoff.
- Explicit model-call, input/output and wall-clock caps per run.
- Per-project file-count, archive expansion, path-depth and byte limits.
- Stop/cancel always available. A cancelled in-flight provider request may already have incurred cost; show usage honestly.
- Process-stop builds restart from a known snapshot; do not promise resumption from a half-written compiler output.

### Local layout (inside controller private app storage)

```text
projects/<project-id>/
  project.json
  src/main/AndroidManifest.xml
  src/main/java/<package-path>/MainActivity.java
  src/main/res/layout/activity_main.xml
  src/main/res/values/strings.xml
  assets/
  snapshots/<revision>/
  artifacts/<build-id>/app.apk
  runs/<run-id>/events.jsonl
  runs/<run-id>/usage.json
```

Project keys and model credentials are outside source, snapshots, prompts and ordinary exports. Exclude projects, histories and credentials from automatic cloud backup. Provide explicit local export and a separate, user-approved encrypted recovery export for keys. Select export destinations through Android's file picker rather than all-files access.

Snapshots can initially be complete copies with bounded retention. Avoid embedding Git until measured project sizes or user requirements justify it. Use SQLite for run metadata/journal indexes and atomic project revision promotion; do not implement a general version-control system.

## 5. Milestones and implementation tasks

All commands below are FUTURE verification targets, not commands already executed or claims of passing output. Once the repository exists, create the named modules/tests and lock the toolchain so these commands are executable. Each code task follows one vertical RED -> GREEN -> REFACTOR slice at a time. Do not write every test in the plan before implementing any behavior. No commits or pushes unless separately requested.

### Milestone A — prove the local compiler and installation path

#### Task A1: establish the device and toolchain experiment

**Objective:** Fix the supported experiment and avoid an unlicensed or obsolete foundation.

**Files:**
- Create `docs/adr/0001-local-build-boundary.md`.
- Create `docs/toolchain-candidates.md`.
- Create `docs/device-matrix.md`.
- Create `toolchain/manifest.json` and `toolchain/NOTICE.txt` after selecting verified inputs.

**Steps:**
1. Confirm access to an unrooted ARM64 test phone and a secondary representative device; record OS, RAM, storage and page size without collecting personal identifiers.
2. Inspect candidate upstream source and licenses. Record exact revisions/artifact checksums and build provenance when obtained.
3. Choose a current target SDK appropriate to distribution; do not lower the target simply to evade execution restrictions.
4. Define a minimal Java/XML counter fixture and exact success criteria.
5. Set a bounded experimentation window before attempting compiler integration.

**Gate:** Approved experiment, device access, and a license/provenance route. Sketchware Pro is behavioral evidence, NOT an approved code base to fork: its current README explicitly restricts app-store redistribution, and the probed `LICENSE` URL was not found. Get file-level rights/legal clarification before copying code. Independently licensed compiler components require their own audit.

#### Task A2: create controller and worker shells

**Objective:** Establish the real UID and IPC boundary before handling model credentials.

**Files:**
- Create `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` and Gradle wrapper files.
- Create `controller/build.gradle.kts`, `controller/src/main/AndroidManifest.xml`.
- Create `worker/build.gradle.kts`, `worker/src/main/AndroidManifest.xml`.
- Create `protocol/src/main/aidl/dev/smallwright/protocol/IBuildService.aidl`.
- Create `worker/src/main/java/dev/smallwright/worker/BuildService.kt`.
- Test `worker/src/androidTest/java/dev/smallwright/worker/CallerAuthorizationTest.kt`.

**Steps:**
1. Write a device test proving an unrelated caller is denied; run and observe the expected failure before the authorization feature exists.
2. Add the minimum signature permission and caller verification; rerun the test.
3. Test an authorized no-op request and Binder death handling.
4. Confirm worker lacks Internet, shared UID, accessibility, all-files and package-install permissions.

**Future verification:** `./gradlew :worker:connectedDebugAndroidTest`.
**Expected:** authorized call succeeds; unauthorized request fails; a distinct UID is observed. Desktop/emulator results are useful development checks, not the physical-device gate.

#### Task A3: build an APK from actual local sources

**Objective:** Exercise the full compiler path on the phone without AI.

**Files:**
- Create `worker/src/main/java/dev/smallwright/worker/CompilePipeline.kt`.
- Create `worker/src/main/java/dev/smallwright/worker/ToolchainVerifier.kt`.
- Create `worker/src/main/java/dev/smallwright/worker/ScratchWorkspace.kt`.
- Create `fixtures/counter/src/main/AndroidManifest.xml` and Java/XML source files.
- Test `worker/src/androidTest/java/dev/smallwright/worker/LocalCompileTest.kt`.

**Steps:**
1. Test that an intentionally invalid source returns a real compiler diagnostic and no successful artifact.
2. Add the minimum packaged resource/compiler/dex/packaging pipeline.
3. Test valid counter source produces an APK; inspect package identity and expected resources/DEX.
4. Change a visible source string ON THE PHONE and rebuild; record source snapshot and artifact checksums.
5. After toolchain provisioning, disconnect networking and repeat the manual edit/build. This proves local compilation, not offline model inference.
6. Run cold and warm builds; record wall time, peak memory, storage, thermal observations and interruptions.

**Future verification:** `./gradlew :worker:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.smallwright.worker.LocalCompileTest`.
**Gate:** a physical phone produces a changed, unsigned APK from changed local source with networking disabled. No uploaded project, fetched prebuilt fixture APK, remote log pretending to be local, or laptop compiler may satisfy this gate.

#### Task A4: verify, sign, install and update

**Objective:** Deliver the first usable native app through explicit Android installation UI.

**Files:**
- Create `controller/src/main/java/dev/smallwright/security/ArtifactValidator.kt`.
- Create `controller/src/main/java/dev/smallwright/security/ProjectSigner.kt`.
- Create `controller/src/main/java/dev/smallwright/install/InstallCoordinator.kt`.
- Test `controller/src/test/java/dev/smallwright/ArtifactPolicyTest.kt`.
- Test `controller/src/androidTest/java/dev/smallwright/InstallFlowTest.kt`.

**Steps:**
1. Test rejection of a wrong package name, forbidden permission, shared UID and altered post-validation artifact.
2. Implement package/manifest checks and sign the approved bytes with a per-project key.
3. Verify the signature before requesting installation; preserve the validated artifact immutably through handoff.
4. Show user confirmation and handle denied source trust, cancelled installation and successful installation separately.
5. On the phone, run the counter, create persisted data, edit/build a new version and install it as an update.
6. Confirm the update shows the new behavior and retains existing data.

**Gate:** standalone app launch + user-approved update + retained local data, demonstrated on physical hardware. No silent install, root or ADB dependency in the user-facing workflow. Developers may use ADB for test inspection; the demo must still pass after detaching the laptop.

### Milestone B — harden project and execution boundaries

#### Task B1: project store and transactional edits

**Objective:** Restrict model edits to a revisioned source workspace.

**Files:**
- Create `controller/src/main/java/dev/smallwright/project/ProjectStore.kt`.
- Create `controller/src/main/java/dev/smallwright/project/PatchApplier.kt`.
- Create `controller/src/main/java/dev/smallwright/project/SnapshotStore.kt`.
- Test `controller/src/test/java/dev/smallwright/ProjectStoreTest.kt`.

**Steps:**
1. Write one failing test for an ordinary atomic source patch; implement and pass it.
2. Add separate tests for `../`, absolute paths, symlinks, archive traversal, stale revisions and over-limit writes.
3. Permit source/resource edits only; lock signing, package identity, dependency inputs and build configuration outside model-editable paths.
4. Ensure failed or interrupted patches leave the previous complete revision intact.
5. Enforce one active project mutation/build request.

**Future verification:** `./gradlew :controller:testDebugUnitTest --tests 'dev.smallwright.ProjectStoreTest'`.

#### Task B2: worker confinement and resource exhaustion

**Objective:** Bound the damage of malformed compiler input and compromised worker code.

**Files:**
- Create `worker/src/main/java/dev/smallwright/worker/BuildLimits.kt`.
- Create `worker/src/main/java/dev/smallwright/worker/BoundedDiagnostics.kt`.
- Create `security-probe/src/main/AndroidManifest.xml` and a test-only adversarial caller.
- Test `worker/src/androidTest/java/dev/smallwright/worker/ConfinementTest.kt`.

**Steps:**
1. From a test-only worker probe, attempt controller-file access and direct network connection; require OS-level denial, not only tool-policy rejection.
2. Test malformed resource/archive input, oversized diagnostics, cancel, deadline expiry and worker crash.
3. Ensure only one snapshot is ever present; erase scratch on success/failure/startup and reject concurrent requests.
4. Confirm no generated classes, Gradle scripts, annotation processors or tests execute within the trusted controller.
5. Probe signature-protected services/providers from the separately signed generated-app test fixture.

**Gate:** credentials/signing keys/unrelated projects remain unavailable; denied calls are recorded; worker failure does not corrupt source. No hostile app is installed on the user's personal phone for this test—use a dedicated test device and explicit consent.

#### Task B3: credential handling and privacy controls

**Objective:** Keep cloud access restricted to intentional model context.

**Files:**
- Create `controller/src/main/java/dev/smallwright/security/CredentialStore.kt`.
- Create `controller/src/main/java/dev/smallwright/agent/ContextFilter.kt`.
- Create `controller/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`.
- Test `controller/src/test/java/dev/smallwright/ContextFilterTest.kt`.
- Test `controller/src/androidTest/java/dev/smallwright/BackupPolicyTest.kt`.

**Steps:**
1. Test that dummy credentials, signing material and unrelated project content never enter serialized model requests, logs or normal exports.
2. Store secrets encrypted with a Keystore-backed wrapping key; never place real secrets in tests or model prompts.
3. Disable automatic cloud backup of workspaces, histories and secrets using rules appropriate to supported OS versions.
4. Add a visible explanation of exactly what is sent to the model and a context preview for explicit uploads.
5. Test data retention/export behavior and app reinstall/phone-loss recovery limitations.

### Milestone C — add the cloud-backed, phone-executed agent

#### Task C1: one model client with strict tool schemas

**Objective:** Make a real cloud model request without giving the model execution authority.

**Files:**
- Create `controller/src/main/java/dev/smallwright/agent/ModelClient.kt`.
- Create `controller/src/main/java/dev/smallwright/agent/ToolSchema.kt`.
- Create `controller/src/main/java/dev/smallwright/agent/UsageBudget.kt`.
- Test `controller/src/test/java/dev/smallwright/ModelProtocolTest.kt`.

**Steps:**
1. Write failing protocol tests for a valid response, invalid tool JSON, unknown tool name and interrupted streaming.
2. Implement only the chosen provider contract; do not build a multi-provider framework now.
3. Enforce per-run caps, redact authorization headers and handle authentication/rate-limit errors.
4. With separately approved credentials and spend, run a live request on the phone; keep deterministic simulated-provider tests clearly labeled as such.

**Gate:** real model response is received; all tool effects are executed locally by the dispatcher. No simulated response may be reported as live-provider evidence.

#### Task C2: bounded generation and repair loop

**Objective:** Turn a prompt into changed local source and a verified APK.

**Files:**
- Create `controller/src/main/java/dev/smallwright/agent/AgentLoop.kt`.
- Create `controller/src/main/java/dev/smallwright/agent/ToolDispatcher.kt`.
- Create `controller/src/main/java/dev/smallwright/agent/RunJournal.kt`.
- Test `controller/src/test/java/dev/smallwright/AgentLoopTest.kt`.

**Steps:**
1. Test one vertical sequence: read template -> valid patch -> real build dispatch -> ready for testing.
2. Reject hallucinated success: model text alone cannot create build/test/install success events.
3. Test malformed edits, stale revisions, exhausted repair limits and cancellation individually.
4. Persist revision/build/run IDs and usage after each accepted transition.
5. Treat source, imported text and compiler diagnostics as untrusted data, never authority to expand tools or permissions.
6. Run a real prompt on a physical phone, receive a genuine compile error when present, and verify local repair and final installation.

**Future verification:** `./gradlew :controller:testDebugUnitTest --tests 'dev.smallwright.AgentLoopTest'` plus the separately recorded real-device/live-provider scenario.

#### Task C3: validate local-only execution end to end

**Objective:** Make the product's central claim auditable.

**Files:**
- Create `docs/validation/locality-proof.md`.
- Create `benchmarks/protocol.md`.
- Create `benchmarks/results.schema.json`.

**Steps:**
1. Show the prompt, source revisions, local compiler process/UID, artifact checksums and install result in one evidence record.
2. Inspect network destinations and request classes with test-device tooling. The worker must make no network connection; the controller must contact only the configured model endpoint during the run.
3. Verify no model-side hosted code execution, remote file storage, uploaded workspace or remote build tools are requested.
4. Repeat a build from cached source/toolchain with airplane mode enabled.
5. Detach the laptop and demonstrate the user-facing build/install flow again.

**Gate:** documented evidence differentiates permitted model context transmission from prohibited remote tool execution. The generated app still runs with the controller stopped; local-only apps run without Internet.

### Milestone D — make the loop usable and recoverable

#### Task D1: phone-first UI and honest progress

**Objective:** Let a user complete the workflow without terminal commands.

**Files:**
- Create `controller/src/main/java/dev/smallwright/ui/ProjectScreen.kt`.
- Create `controller/src/main/java/dev/smallwright/ui/ConversationScreen.kt`.
- Create `controller/src/main/java/dev/smallwright/ui/TestHandoffScreen.kt`.
- Test `controller/src/androidTest/java/dev/smallwright/UserJourneyTest.kt`.

**Steps:**
1. Test project creation, multiple persistent chats per project, chat switching and prompt submission, then implement project-list, project and conversation screens. Verify separate chat histories, shared project revisions, active-run visibility and recovery after reopening the controller.
2. Display specific states: editing, compiling, needs help, ready to install, installed but untested, and user accepted.
3. Show changed features, requested permissions and a short test checklist before installation.
4. Provide cancel, retry, source inspection and source/APK export.
5. Clearly show initial worker installation/toolchain size and compatibility failures; no hidden second-app dependency.

#### Task D2: feedback and diagnostics without phone-wide surveillance

**Objective:** Help the agent repair behavior, not only compiler errors.

**Files:**
- Create `controller/src/main/java/dev/smallwright/feedback/FeedbackImporter.kt`.
- Create `templates/native-java/diagnostics/README.md` and optional diagnostic source.
- Test `controller/src/test/java/dev/smallwright/FeedbackImportTest.kt`.

**Steps:**
1. Start with user text feedback and explicit screenshot selection through the system picker.
2. Add development-build crash capture to the generated app's own private storage; import via user-initiated share/export.
3. Redact and preview diagnostics before cloud transmission; never assume access to another app's private files or global logcat.
4. Offer a diagnostic-free export; no hidden runtime dependency on the controller.
5. Keep autonomous cross-app tapping/Accessibility out of the MVP.

#### Task D3: interrupted runs and battery-aware work

**Objective:** Preserve user work when Android pauses or kills the process.

**Files:**
- Create `controller/src/main/java/dev/smallwright/agent/RunRecovery.kt`.
- Test `controller/src/androidTest/java/dev/smallwright/RecoveryTest.kt`.

**Steps:**
1. Test kill/restart after a patch but before build completion; resume from a valid source snapshot, discard partial artifacts.
2. Test backgrounding, screen lock, network loss, low storage and Binder disconnect.
3. First release runs builds while visibly active and pauses/restarts honestly; do not promise background completion yet.
4. If background builds are required, choose a valid service type and policy declaration based on the actual use case; checkpoint and allow cancellation. Do not label compilation as a network transfer to obtain exemptions.
5. Reconcile actual installed package version after an interrupted install rather than blindly retrying it.

#### Task D4: safe updates and explicit recovery export

**Objective:** Preserve user data and signing identity across iterations.

**Files:**
- Create `controller/src/main/java/dev/smallwright/project/UpdatePolicy.kt`.
- Create `controller/src/main/java/dev/smallwright/project/RecoveryExport.kt`.
- Test `controller/src/androidTest/java/dev/smallwright/DataPreservingUpdateTest.kt`.

**Steps:**
1. Test stable package name, increasing version code and same project signer across updates.
2. Test a real local-data migration using seeded records; no destructive migration fallback.
3. Explain that source rollback is not automatic installed-app/data rollback. Restore source into a new higher-version build; unsafe reverse schema changes require a supported migration or explicit backup recovery.
4. Test explicit encrypted key/project recovery export and import on another test device.
5. Distinguish source/APK export from a recoverable signing-key backup; do not claim Keystore wrapping keys are automatically portable.

### Milestone E — private alpha, then expansion

#### Task E1: benchmark reliability and usefulness

**Objective:** Decide whether the product is worth expanding using repeatable results.

**Files:**
- Create `benchmarks/prompts.jsonl`.
- Create `benchmarks/results/<run-id>.jsonl` during real tests only.
- Create `docs/alpha-acceptance.md`.

**Proposed test set:** 20 fixed requests spanning counters, checklists, local trackers and record lists, plus follow-up edits and a data-preserving update. Run on at least two physical devices. Include a clear refusal for unsupported backend/network features.

**Record per attempt:** device class, OS, toolchain version/checksum, prompt ID, revisions, model/version, provider-reported usage/cost when available, cold/warm timings, compile result, install result, human functional result, repair attempts, crash/recovery and retained-data checks. Mark unknown values unknown. Do not equate model-reported completion with success.

**Proposed go/no-go criteria—not achieved results:**
- Locality, credential-isolation, installation-consent and data-preservation gates must all pass.
- At least 16 of the 20 supported requests complete their functional checklist within the repair cap on each selected device, without developer edits rescuing them.
- Establish latency/resource limits after the first measured compiler spike, before evaluating the larger suite; do not invent benchmark numbers now.
- A small invited group completes repeat creation/update sessions without shell help. Investigate repeat use and willingness to pay rather than treating downloads as demand.
- User-facing failures have recovery actions; no crash leaves all projects unusable.

#### Task E2: choose the next expansion, not every expansion

**Objective:** Invest only in the bottleneck supported by alpha evidence.

**Files:**
- Create `docs/adr/0002-post-alpha-scope.md`.
- Create `docs/distribution-review.md`.

**Candidates, in order of demonstrated need:**
1. Better native components and Kotlin output, after local toolchain validation.
2. Single-APK worker integration if an isolated service can satisfy compatibility and security gates.
3. Curated dependencies with a versioned verified catalog.
4. Managed model billing with explicit retention/privacy guarantees and no cloud workspace; determine Play billing obligations before shipping monetization.
5. A Play-distributed edition, subject to executable-code and installation-permission review.
6. Additional ABIs, languages or optional networking only after re-evaluating isolation and the product contract.

No cloud-build fallback is an acceptable optimization under the current requirement. Any architecture change that moves files/tools/builds remotely requires a new explicit user decision.

## 6. Concrete testing contract

Future standard commands after modules and wrapper are created:

```bash
./gradlew :controller:testDebugUnitTest :worker:testDebugUnitTest
./gradlew :controller:lintDebug :worker:lintDebug
./gradlew :controller:assembleDebug :worker:assembleDebug
./gradlew :controller:connectedDebugAndroidTest :worker:connectedDebugAndroidTest
```

These build/test the HARNESS during development. The generated-app build evidence must come from the phone's worker and be recorded separately; a successful developer-machine Gradle command is not proof of phone-local compilation.

Example behavior assertion for the future unit test contract (illustrative Kotlin/JUnit, not an existing API):

```kotlin
@Test
fun rejectsPatchOutsideProject() {
    val store = TestProjectStore.create()
    val original = store.revision
    assertThrows(SecurityException::class.java) {
        store.applyPatch(original, "../credentials.json", "replacement")
    }
    assertEquals(original, store.revision)
}
```

Add one behavior at a time, see it fail for the missing behavior, implement minimally and rerun the relevant suite. OS-confinement tests require real app identities and permissions, not mocked filesystem checks. Real-model tests require separately approved credentials/budget and cannot be replaced by fake tool responses.

## 7. Distribution, policy and licensing gates

- Separate OS feasibility from Play eligibility. Off-Play distribution does not remove OS sandbox/executable restrictions.
- Package trusted executable toolchain components through the selected signed distribution channel; do not bootstrap arbitrary executable downloads in a prospective Play edition.
- Review treatment of compiler inputs, dependencies and generated APKs for the exact implementation. Do not claim blanket Play permission or blanket prohibition.
- Review `REQUEST_INSTALL_PACKAGES` eligibility before a Play submission. An IDE is not expressly listed in the cited permitted-use summary. A genuine build/export-only edition is a potential alternative, not preapproved compliance.
- Track Android developer verification and generated package registration separately from the publisher identity of the harness. Do not assume one verified harness covers every generated package.
- Produce an SBOM, notices, pinned artifacts and source/provenance records for shipped compilers. A public GitHub repository does not automatically grant redistribution rights.
- Do not copy Sketchware Pro implementation code under an assumed permissive license; resolve its explicit redistribution caveat first.
- Re-check current policies at distribution time. Research findings are not legal advice or store approval.

## 8. Staffing, timeboxes and decision points

Planning allowances, not delivery commitments:

- **Compiler/security spike:** timebox approximately two weeks of focused senior Android/toolchain work. Deliver A1–A4 plus a minimal confinement test; record what actually passed.
- **Closed-loop technical alpha:** estimate only after compiler and isolation gates pass. Expect multiple further weeks for agent integration, updates, recovery and device testing—not merely a chat UI sprint.
- **Consumer beta/store launch:** do not assign a credible date before private-alpha measurements and distribution review.

Work split if two contributors are available:
- Android/toolchain engineer owns compiler packaging, IPC, signing, installation and device testing.
- Agent/product engineer owns bounded tools, context/privacy, run state, repair loop and UI.
- Security review must examine their boundary jointly before real credentials and generated apps are used outside dedicated test hardware.

Do not parallelize code work before the contracts and physical-device compiler gate are established. For later implementation through OpenCode, select/reuse the verified worktree and enforce one bounded task, test evidence and review at a time; never commit automatically.

## 9. Open decisions before execution

Not blockers to writing this plan, but resolve before their dependent step:

1. Which physical Android test phones are available? Obtain model/OS/memory details from the actual devices, not assumptions.
2. Is a two-APK directly distributed alpha acceptable for proving the security boundary? If not, single-APK isolated-worker compatibility becomes an earlier gate.
3. Accept Java/XML as the initial generated-source baseline, or require Kotlin immediately and accept a larger toolchain spike?
4. Which model API and explicit spend cap should the live tests use?
5. What is the repository name and intended source/license/distribution model?

Recommended defaults are stated above so planning can proceed without a clarification chain. No credentials, device actions or spending are authorized by this document.

## 10. Primary references and unresolved evidence

These are implementation references, not a claim that an assembled stack has been verified:

- Android executable restrictions: https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission
- Service process/isolated-process semantics: https://developer.android.com/guide/topics/manifest/service-element
- Android sandbox: https://source.android.com/docs/security/app-sandbox
- PackageInstaller user action: https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)
- App signing and update identity: https://developer.android.com/studio/publish/app-signing
- Backup rules: https://developer.android.com/identity/data/autobackup
- Play executable-code policy: https://support.google.com/googleplay/android-developer/answer/16559646?hl=en
- Play installation-permission policy: https://support.google.com/googleplay/android-developer/answer/12085295?hl=en
- Android developer-verification FAQ: https://developer.android.com/developer-verification/guides/faq
- AndroidIDE build-tool precedent: https://docs.androidide.com/developer/build-tools.html
- Pi phone-local agent precedent, not a complete compiler product: https://github.com/earendil-works/pi/blob/main/packages/coding-agent/docs/termux.md
- Sketchware Pro source map and redistribution caveat, directly retrieved during planning: https://raw.githubusercontent.com/Sketchware-Pro/Sketchware-Pro/main/README.md

**Execution handoff:** Start with Milestone A only after the user authorizes implementation. Its deliverable is physical-device evidence of an entirely local native build/install/update, a verified security boundary, and a continue/stop decision—not a polished prototype backed by remote compilation.
