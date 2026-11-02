# Active worker Stop and recovery on A33

2026-10-11; base `1ab24c1a1df01a05083ce4b1886c52f6389987d3` plus the
test changes listed below. Tracks issue #6.
No production compiler, cancellation, credential or installation policy changed.

## Device and boundary

Fresh real-phone-test preflight identified physical Samsung Galaxy A33 5G
SM-A336E, Android16/API36, arm64-v8a, hardware s5e8825, qemu0. The phone
temporarily disconnected while the user entered a key in the app. After
reconnection, preflight was repeated at15:52:23 UTC before installation.
Private serial and raw evidence remain in ignored `toolchains/alpha-stop-20261011/`.
Use `$PHONE_SERIAL` from current preflight on every ADB command.

The user selected local checks. No model requests, account-budget inspection,
credential reads, key exports or generated APK installations occurred. Key
entry is user-reported; neither configured-key validity nor account $0-only
settings are asserted. Harness test APK updates used verified debug install
`adb install -r`; retained app data. Production installation consent was not tested.

## Test contract and assistance

All cases use synthetic fixture state on actual hardware. The active-build case
uses the real controller UI, authenticated Binder worker, native compiler,
per-project signer and artifact verifier. A debug-only isolated activity owns
separate project/journal/preferences directories. A fresh project was created
through the real New project dialog, and its new signer alias was checked by
name only before building. Existing signer or credential bytes were never read.

A test-only Binder relay reads the exact leased ZIP inside the phone, verifies
its sole MainActivity entry against the edited source SHA-256, and forwards those
same bytes. The valid random-comment fixture stays below the actual64KiB project
file limit. The editor display is limited to14 lines with horizontal scrolling
for this fixture only. The relay shrinks its own disposable Linux pipe to4096
bytes (`F_SETPIPE_SZ=1024+7`, confirmed in local Linux headers and exercised on
the phone), fills it before starting the reader, then observes actual draining.
All archive bytes are written, and a real `toolchainProbe` returns exactly
`ERROR: Worker busy` while the real build remains active. This establishes
admission and input progress, not complete input consumption before Stop.

The visible Stop button is clicked, then the relay releases EOF. One actual
worker compile and controller signing operation completes late. The probe
checks the signed APK's real manifest/package/version and signature, preserved
source revision/hash, missing receipt and disabled Install. It reads ProjectStore
only after the lease settles; the process-wide build lock forbids those reads
while the build is active. No compiler output or generated APK bytes leave the phone.

This is cancellation during admitted worker input with a controlled EOF hold.
It proves late results cannot publish readiness. It does not demonstrate
immediate worker termination, cancellation inside ECJ, or provider cancellation.

## Failures retained

| Attempt | Actual result | Correction or limit |
| --- | --- | --- |
| v1, `prepare-01.txt` | Android input-dispatch timeout, code0; saved revision0 | Bounded editor display and safe stage markers added. No exact ANR root cause is claimed. |
| v2, `prepare-02.txt` | AssertionError at OBSERVE_ACTIVE_WORKER, code0; revision0 | Reader-before-writer observation had a timing flaw; pipe was prefilled before reader admission. |
| v3, `prepare-03.txt` | Same stage failure, code0; revision0 | Parent found both fixtures exceeded PatchApplier's64KiB limit before worker dispatch. Reduced comment from180000 to24000 characters and shrank only the test pipe. |
| Chat neighbor, `chat-neighbor-red.txt` | Actual preview assertion failed, code0 | Old synthetic REPAIR_NEEDED fixture lacked required repair origin. Seeded matching request/chat/initial revision and exact failed-source pin; no production guard weakened. |

`failed-fixture-revisions.json` confirms all three failed projects still have
HEAD0 and no stopped-success pin. Their data and failure records were retained.
v4 then passed Stop, restart, retry and ready restart on its own isolated root.
The final v5 APK reran those four phases after tightening observation wording
and correcting and isolating neighboring fixtures; v4 was not overwritten.

## Final physical results

Final test APK SHA-256:
`5cf931135ee1277851dd2a64f851c3e46f69f7477eb342b42218162fee5b4d28`
(891018 bytes), package `dev.smallwright.controller.test`.
Public signer certificate SHA-256:
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
Package/signature were verified before update; installed bytes matched afterward.

| Case / raw record | Observed result |
| --- | --- |
| `prepare-final.txt` | Real admitted worker draining; visible Stop; one late real compile/sign; revision1 retained, no receipt or Install readiness. Code-1. |
| `restart-final.txt` | Explicit controller force-stop before reopen; exact revision1 and late APK retained; no receipt, automatic rebuild or Install readiness resurrected. Code-1. |
| `recover-final.txt` | Separate visible Build committed revision2 with unchanged source, compiled/signed/verified version1, persisted receipt and enabled Install. No installation. Code-1. |
| `ready-final.txt` | Another explicit force-stop/reopen restored exact revision2 receipt/APK and enabled Install without rebuilding. Code-1. |
| `reserve-guard-final.txt` | Duplicate prepare refused at GUARD before opening activity. Expected code0; no new fixture or run. |
| `queued-stop-final.txt` | Parked executor, queued Build, Stop; no source commit or APK readiness. Code-1. Test uses isolated debug activity. |
| `background-queued-stop-final.txt` | `moveTaskToBack` caused lifecycle Stop of parked queued Build; source remained untouched. Code-1. No background compilation, screen-off, Doze or indefinite background claim. |
| `chat-neighbor-final.txt` | Separate histories browsable during queued Build, concurrent work blocked, Stop preserves source; corrected synthetic repair origin opens preview, cancelled without sending. Code-1. |
| `completion-ordering-final.txt` | Actual SQLite/filesystem Stop/publication ordering and failure/recovery assertions passed. Verification-only placeholder bytes and injected interruption; no compiler/signer/process crash in this case. Code-1. |

The host wrapper initially rejected the completion-ordering success prose
because it contained the expected state name `FAILED`. Its raw runner returned
code-1 with the named success assertions. An independent check of completion
codes, named results and failure/crash markers validated all eight positive
records; no physical rerun or fabricated elapsed duration was added.
`final-case-results.json` retains this host-validator limitation.

Final project: `app758d47e0456145b7b38d3ca59b9aee3d`.
Source SHA-256: `2305959fe18c1c2fd4c538b77808704a4949b1ccd6e147f521812cc523d2d5fa`.
Late signed version1 SHA-256: `d6b6990d80f030293511dc63f9aac0195b029b0e182cefaa1fe577a0dee3e815`.
Fresh retry SHA-256: `c430697f3ac0612eeb068e44b3226ee3c0bf1d6b56d3ecab8eb5e9392dca43c2`.
The retry uses the same version1 path because a stopped result does not consume
the install version. On-phone APK path after retry:
`/data/user/0/dev.smallwright.controller/files/binding-ui-probe-a3f8db100f9e4e99862947d3f40f8297/projects/app758d47e0456145b7b38d3ca59b9aee3d/artifacts/app-v1.apk`.
No generated APK was copied to the laptop or installed.

## Commands and host checks

```bash
source scripts/env.sh
./gradlew format qualityCheck :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
adb -s "$PHONE_SERIAL" shell am instrument -w -r -e activeWorkerStop prepare dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
adb -s "$PHONE_SERIAL" shell am force-stop dev.smallwright.controller
adb -s "$PHONE_SERIAL" shell am instrument -w -r -e activeWorkerStop restart dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
adb -s "$PHONE_SERIAL" shell am instrument -w -r -e activeWorkerStop recover dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
adb -s "$PHONE_SERIAL" shell am force-stop dev.smallwright.controller
adb -s "$PHONE_SERIAL" shell am instrument -w -r -e activeWorkerStop ready dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
```

The fixed v5 fixture permits one prepare; repeat prepare intentionally refuses.
Do not clear its pins, reuse failed roots or silently rewrite evidence to retry.
Other implemented flags: `stopBeforeBuildOnly`, `backgroundStopsBuildOnly`,
`activeChatNavigationOnly`, `generationCompletionOnly`, each with value `true`.
Final host command passed in40s,149 tasks:12 executed/137 up-to-date.
Controller androidTest Lint analysis and test APK compile/package ran freshly.
All three JVM test tasks were UP-TO-DATE; no fresh JVM test count is claimed.
Lint reports still show zero errors,72 controller/two worker/zero protocol
warnings; report tasks were cached. Spotless and pinned compiler inputs passed.
`final-source-sha256.json` identifies exact tested test sources.

Harness APK paths remain `controller/build/outputs/apk/debug/controller-debug.apk`,
`worker/build/outputs/apk/debug/worker-debug.apk`, and each module's
`build/outputs/apk/androidTest/debug/*-debug-androidTest.apk`.
Only controller-test changed. Main harness and worker-test hashes remain the
[diagnostics checkpoint](repair-diagnostics-20261011.md) values; installed bytes
of all four were checked again in `final-installed-and-restoration.json`.

## Changed paths, restoration and next task

```text
controller/src/androidTest/java/dev/smallwright/controller/ActiveWorkerStopProbe.java
controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java
controller/src/androidTest/java/dev/smallwright/controller/ActiveChatNavigationProbe.java
controller/src/androidTest/java/dev/smallwright/controller/StopUiProbe.java
docs/STATUS.md
docs/alpha-acceptance.md
docs/validation/active-worker-stop-a33-20261011.md
```

Pinned formatter accounts for mechanical changes in the two older test files.
Review findings were checked against actual production code; parent caught the
64KiB fixture violation missed by initial review and narrowed the consumption
claim to observed input progress. Main worker/controller code was unchanged.
Controller and worker were explicitly stopped after the tests and absence of
both PIDs verified. Test roots, throwaway project signers and existing app data
were retained. No credential/UI captures, unrelated helpers or phone-wide
configuration changes were made.

Next: local interruption/storage checks and later live free-only repair with
phone-owned credentials and exact context/revision approval. ECJ-phase
cancellation, provider cancellation, active-build process death, low storage,
cross-device recovery, detached-laptop execution and full alpha remain pending.

Subsequent bounded active-build controller force-stop/recovery passed in the
[next checkpoint](active-build-interruption-a33-20261011.md). That record
supersedes the pending explicit force-stop case above; ECJ-phase cancellation,
spontaneous OS eviction and broader interruption gates remain unverified.
