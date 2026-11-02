# Two-project persistent-chat acceptance probe

This checkpoint adds acceptance coverage for issue #2 / implementation Task D1.
No production defect was identified, and no production fix or fabricated
red-to-green claim is made. The parent subsequently ran the complete bounded
case on the connected physical A33; results are recorded below.

## Existing coverage and remaining evidence gap

- `ChatStoreTest.separateChatsAndMessagesSurviveReopen` covers persisted
  one-project histories. `projectIdsConfineChatHistory` rejects foreign chat
  access; `ProjectRegistryTest.createdProjectsRemainDistinctAcrossReopen`
  covers separate package/project metadata.
- `ContextPreviewGateTest` rejects changed source/request/history approvals;
  `SourceReplacementDispatcherTest.rejectsMismatchedAndStaleRevisionsWithoutMutation`
  rejects stale parsed proposals at the actual immutable source store.
- Existing `ProjectNavigationProbe` is emulator-only and does not exercise
  two projects with two populated chats each. `ActiveChatNavigationProbe`
  exercises one project's histories during queued work.

`PersistentChatsAcceptanceProbe` adds a normal `MainActivity` UI case with
two **new**, uniquely named projects, two chats each, and four real local builds.
It uses normal New project/New chat/project/chat pickers, the source editor,
and Build controls. It uses existing visible-dialog helpers, not a substituted
activity or private storage context. Existing project source, histories,
artifacts, keys and run records are not edited. The prior project/chat selection
is restored; all newly created projects and artifacts are retained.

Histories are explicitly test-seeded through `ChatStore.appendMessage`; source
changes are comment markers entered in the controller editor and compiled by
the real worker. An older chat captures an exact revision1 approval and a
test-authored parsed replacement; a newer chat then builds revision2. Approval
and dispatch must both reject revision1 without changing revision2 source,
receipt or either retained APK. This is a real source-store/approval-boundary
check, not a live model proposal or request test.

For each project, creation of a second chat must retain the revision1 package,
verified receipt, artifact hash/version and public signer fingerprint. Its next
build must use that same signer and package with increasing source/version;
project signers/packages must be distinct. Activity finish/reopen must recover
both projects' rendered separate histories, exact current source and verified
receipt/readiness. The normal local Build path creates retained artifacts but
does not create model run-journal records: no live origin/run-history claim is
made. Activity reopen is not process-death or spontaneous-eviction evidence.

## Guard and exact invocation

The flag is opt-in; the phone must be unlocked and interactive. It requires
both `approveLocalChatAcceptance=true` and a caller-selected 32-lowercase-hex
`observationId`. Fixed controller-private preferences
`persistent-chats-acceptance-v1` reserve the first observation before any UI
creation. Missing approval/invalid ID or any repeated observation is refused.
Do not clear these preferences, delete projects, or automatically reseed after
a failure. Preserve the original output and owned projects for inspection.
There is no model call, generated-app installation, provider initialization or
existing model credential read in this case.

After the parent verifies device/harness identity, it can run:

```bash
source scripts/env.sh
adb -s "$DEVICE_SERIAL" shell am instrument -r -w \
  -e persistentChatsAcceptanceOnly true \
  -e approveLocalChatAcceptance true \
  -e observationId "$OBSERVATION_ID" \
  dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
```

For a nonmutating approval guard check, omit `approveLocalChatAcceptance`;
expect `FAILED stage=GUARD type=AssertionError` and instrumentation code0.
For the actual observation, require instrumentation code-1 and the exact
success stream. A repeated actual invocation must fail the same guard without
creating projects/builds. Success output identifies owned project/package,
current source revision and Java source SHA-256, version, public signer,
both retained phone APK paths and APK SHA-256 values. The fixture saves this
public evidence and a `selectionRestored` boolean for subsequent inspection.
Signing key/credential bytes are never output. Preserve all failures; a host
test or successful APK build alone does not pass this physical case.

## Host verification

Worktree: `toolchains/worktrees/alpha-chat-20261012`, branch
`feature/alpha-chat-acceptance-20261012`, based on integration08025d9 and merged
integration3aefa1d before handoff. All Gradle runs used the parent repository's
absolute `scripts/env.sh` (JDK21.0.12.1+1, shared SDK/cache), offline/no-daemon,
one worker and `-Xmx1536m -XX:MaxMetaspaceSize=512m`.

- Initial `spotlessApply :controller:testDebugUnitTest :controller:assembleDebug
  :controller:assembleDebugAndroidTest` ran all213 controller JVM tests in38
  suites, with0 failures/errors. Instrumentation compilation failed on a test
  driver mistake: passing an EditText to a Button-only reveal helper. The
  failed2m46s output remains at
  `/tmp/phone-local-alpha-chat-host-build-20261012.log`. This is not a production
  behavior failure or acceptance red.
- After fixing the driver, `spotlessApply qualityCheck
  :controller:testDebugUnitTest :controller:assembleDebug
  :controller:assembleDebugAndroidTest` passed in1m48s. JVM tests were cached;
  formatting, lint and both harness APK tasks passed. Output remains at
  `/tmp/phone-local-alpha-chat-host-quality-20261012.log`.
- Final formatting of the displayed-source assertion followed by
  `spotlessApply qualityCheck :controller:assembleDebugAndroidTest` passed
  in1m1s. Output remains at
  `/tmp/phone-local-alpha-chat-host-final-20261012.log`. `git diff --check` passed.

Exact host-built harness outputs and final compiled test-source pins:

| Worktree-relative path | SHA-256 |
| --- | --- |
| `controller/build/outputs/apk/debug/controller-debug.apk` | `e8440724704d359aa80528d05d30a3e4b1866872639c1971ac5326e4b808a636` |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `c0a900a65059b3f3479d820516f3ecc37fc6499c1d5f5e929feb95c398e3a1a0` |
| `controller/src/androidTest/java/dev/smallwright/controller/PersistentChatsAcceptanceProbe.java` | `0e04c5360ba9644d1abdba54a88fd9f182d7f82dcdd3d2d3d5d45d2473325d74` |
| `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java` | `8e96141732632d76bcb5d40ff6147fc18debca23a1b609360b125577b3180bb5` |

No ADB action, generated build, installation or device test was performed by
the implementer. The parent’s subsequent physical execution is recorded below.

## Limits and next task

This bounded case does not close issue #2's live conversational
request/build/install/failure-retry gates, process-death recovery, or the fixed
twenty-request physical alpha suite. Verify the recorded source/receipt/APKs
against phone-private paths, then continue those separately documented gates.

A concrete remaining implementation gap is direct local Build provenance:
`MainActivity.startBuild()` commits source and publishes a receipt with a no-op
run-state recorder, without recording the originating chat in `RunJournal`.
The generation path does record its captured chat/run. The plan's requirement
that **each** edit/build records its originating chat and exact project revision
therefore remains open for the direct Build path. A subsequent test-first
vertical slice should cover that durable provenance and reopening behavior;
this coverage-only checkpoint does not change it.


## Parent physical execution — October12 Dubai / October11 UTC

Fresh skill preflight matched SM-A336E, Android16/API36, ARM64 s5e8825, qemu0,
unlocked and interactive. All four pre-existing installed harness hashes matched
the prior detached preparation. The exact compiled test-source pins above matched
merged root sources. The new controller.test APK’s package and public signature
were verified before a data-preserving `install -r`; its installed bytes matched
`c0a900a65059b3f3479d820516f3ecc37fc6499c1d5f5e929feb95c398e3a1a0`.
Production controller/worker and worker.test were unchanged. The host-built test
APK is preserved at `toolchains/alpha-project-chats-20261012/controller-debug-androidTest.apk`.

- Missing approval rejected at GUARD before fixture reservation/UI/build:
  host0, instrumentation code0 (`approval-guard-01.txt`).
- The original approved observation passed host0/code-1 (`acceptance-01.txt`,
  `handle-01.json`): normal New project/New chat UI created two owned projects
  with two chats each; four real phone-local Java/XML builds produced source1/2
  and version1/2. Separate rendered seeded histories, editor source and receipt/
  readiness survived Activity reopen and both project/chat pickers.
- Both older-chat approvals and parsed revision1 source proposals were refused
  after the newer chat committed revision2. Exact source2 and both retained APKs
  were unchanged. Each project retained its package and signer; the two projects’
  packages/signers were distinct.
- Repeating the same approved observation rejected at GUARD, host0/code0
  (`acceptance-duplicate-01.txt`, `handle-duplicate-01.json`), without reseeding.
- Parent independently rehashed all four retained APKs on phone, matched all
  installed harness bytes/source pins, confirmed the fixture’s verified and
  selectionRestored flags, confirmed neither generated package was installed,
  and verified worker scratch absent. Controller/worker were then stopped; all
  projects, chats, source and artifacts remain retained.

Exact phone artifacts:

| Project / package suffix | Source2 Java SHA-256 | Version1 APK SHA-256 | Version2 APK SHA-256 | Public signer SHA-256 |
| --- | --- | --- | --- | --- |
| `app15459cc320e6402b816c40651a73ae1e` | `9479901b2843c7b2b83d142a4f40cd81925b7840b25ac1b2b319364f4bd5a3b2` | `8263a626023142d1e7b987a0dcd9bc082e2d99810d8ba9067be13fc8d06b58bd` | `3d0c617f5177e5b95606acc728593bd8813586425be443365480b0322b5d5a44` | `a506c0ce911010bdfefedb048fd17e027392c5e7fa81af103772940c85d55772` |
| `app93d87e939cbe4bf0b90938dd911bcda9` | `7e83620e020b293be25afc357fdf8daaeb8ec075bb98d8773d9eae691ff88477` | `7062751857271d501132ab769a07796d8ca1d6e03cc16eaed027889698b92ff8` | `a19a7876de93966f3e3d3d7c0087c8056deaebf498dac3b580d9209afe6f3db0` | `37ceb499781c53a57b676495c7ee94368efd1ba2622e7a696e8782bca06e8982` |

Packages are `dev.smallwright.generated.<suffix>`. APKs remain only on phone at
`/data/user/0/dev.smallwright.controller/files/projects/<suffix>/artifacts/app-v1.apk`
and `app-v2.apk`. No generated source/APK was exported. Exact parent identity/
restoration record is `toolchains/alpha-project-chats-20261012/final-identity-and-restoration-01.json`.
Private device ID stays only in ignored preflight records.

Implemented ignored parent drivers and executed commands:

```bash
python3 toolchains/alpha-project-chats-20261012/verify_install_and_guard.py
python3 toolchains/alpha-project-chats-20261012/run_acceptance.py 01
python3 toolchains/alpha-project-chats-20261012/run_acceptance.py duplicate-01
```

No provider request, generated installation, functional alpha denominator entry,
performance measurement or process-death claim occurred. The provenance gap above
is now separately tracked as issue #20.
Full #2 and private-alpha acceptance remain open.


The clean owned chat implementation worktree was removed at checkpoint cleanup;
its feature branch remains. Historical command/worktree paths above remain
execution records. Non-secret host APKs, lint report and actual 213-controller
test XML are preserved under
`toolchains/alpha-project-chats-20261012/host-evidence-final/`, with exact backup
hashes in the manual-origin case's `host-evidence-backup-manifest-01.json`.
The phone's project sources, signers and four generated APKs were not removed.
