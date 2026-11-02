# Live provider Stop and local recovery on A33

Physical execution occurred on 2026-10-11 UTC (2026-10-12 Dubai), tracked in
issue #17.
This closes a bounded client cancellation and saved-source recovery checkpoint.
It does not close the private alpha or prove that the remote provider aborted inference.
The preceding user-selected live compiler-error repair is recorded separately in
[live repair evidence](live-repair-a33-20261011.md).

## Boundary and assistance

The connected device was Samsung Galaxy A33 5G, SM-A336E, Android 16/API36,
ARM64, s5e8825, `ro.kernel.qemu=0`. Every command used an explicit private device
serial. Preflight at 20:16:09.018095 UTC was ready; before each phase the driver
verified the model, authorized connection, unlocked keyguard, awake state and
absence of a preceding controller instrumentation process. The S23 was not tested.

Production controller/worker/provider code was unchanged. New instrumentation
used actual native controller UI, production HTTPS client and normal separate-UID
worker. It created two distinct owned pristine counter projects, previewed the
actual 8381-byte request payload and cancelled that preview before Send. Each
live Send required explicit free-only approval, matching SHA-256 of the exact
preview, unchanged source0/editor0/chat/no attachments and an unused one-shot
fixture. Missing approval and mismatched context were rejected before Activity
creation. The attempted flag was persisted before each actual Send.

The request asked for a native Reset button preserving Increment and saved count,
using only existing framework APIs and no permissions/dependencies. The fixed
model was `cohere/north-mini-code:free`; its [official pricing page](https://openrouter.ai/cohere/north-mini-code:free/pricing)
was checked during this session. No paid fallback, transport retry or host model
request was introduced. Only the normal app read its saved user credential at
Send. The test did not inspect credential stores, authorization headers or response
bodies; reflection allowlisted connection/cancelled/timedOut metadata and Stop/run
identity. Generated source, APK bytes, signing and execution remained on phone.

For cancellation timing, instrumentation inspected actual thread stacks and the
current production HTTPS connection, then clicked the visible `Stop current run`
button on the main thread. No transport replacement, artificial network hold,
canned provider response or delayed model callback was used. This is test-assisted
observation, not an unaided user session. Original host handles were awaited to
completion in 30-second chunks; elapsed observation time never triggered a new Send.

## Preserved failed attempt and corrected fresh test

Two distinct live Send attempts occurred. The first must remain a failed
cancellation test. At 20:25:31.848523 UTC, `send-01.txt` reported
`AssertionError stage=VISIBLE_STOP_AT_NETWORK_WAIT`, instrumentation code0,
host exit0. Its observer incorrectly required journal PLANNING while awaiting the
model: production `GenerationRun.executeRecorded` advances to EDITING before
`model.request`, as existing JVM tests confirm. Stop had not been clicked. The
original request was allowed to settle before instrumentation finished; its
original host process was independently confirmed absent.

Read-only inspection at 20:29:33.340359 UTC found the original run
`run_e653e557988d4db8b75f1bbd5a0ace32` READY_TO_TEST, sequence5, source1/version1,
4533 provider-reported tokens, cost unknown. Its actual signed APK, package policy,
project signer and receipt were verified on phone. This artifact was not installed
or functionally tested. Its source/project/journal/artifact were preserved; only
previous project selection was restored. The observation explicitly recorded
`originalCancellationTestPassed:false`.

After correcting the test's journal expectation to EDITING, a separate v2 preference
namespace and new owned project were reserved. The first project's attempted flag,
source and history were not cleared or reused. This was an explicit fresh test after
a diagnosed test defect, not an automatic request retry. The v2 preview at
20:32:04.359931 UTC was cancelled without a request. Exact context SHA-256:
`cceb5c662fcfa40e1d2a8c6eebb8c865907ff27ccefbb9ffb57026939ce38a92`.

The second Send, observed at 20:32:51.073477 UTC, passed with host exit0 and
instrumentation code-1. Run `run_9e939e5d0804491e85464a499160a081` used
`com.android.okhttp.internal.huc.HttpsURLConnectionImpl` at the actual fixed
OpenRouter endpoint. Both the initial and final same-thread observations showed
socket/Conscrypt reads under response-header reading, `getResponseCode`,
`ModelClient.completeWithKey` and the production generation path; TLS handshake
frames were excluded. The current token/connection remained identical, uncancelled
and not timed out before the final observation. Journal/source were EDITING/source0.

The predeclared maximum final-observation-to-Stop gap was 5,000,000ns; actual gap
was 815231ns (0.815231ms). The measured Stop-handler interval was 6046077ns
(6.046077ms). These include test overhead and are not model, build or network
latency measurements, or proof of remote inference abort. The freshness bound was
enforced after the actual click, so an over-bound observation would stay stopped
and fail without a retry.

After actual Stop, the production cancellation flags were set. The original
executor settled, a queue barrier completed, and the UI was idle. The connection
and active model call were cleared. Journal was CANCELLED, sequence3, source0,
repairs0, no version. Exact source snapshot remained
`e4f35276f2e3e1c861ed807547f6bc65fba119bcafb003d9822a5123f71d7e87`.
No APK, receipt, Install/export readiness, repair, pending recovery or automatic
build was published. Build was usable. Provider-reported usage and cost were both
unknown; fixed free-only policy is not an observed account-billing measurement.

## Reopen and actual local recovery

Fresh-process `stopped-02` passed at 20:33:40.294043 UTC with the same cancelled
journal/source0 and no automatic request/build/readiness. Original `recover-02`
then failed at 20:34:05.320964 UTC with
`GuardFailure stage=VISIBLE_FRESH_BUILD`: the test driver attempted a visible
Build click before scrolling the button fully into view. Its one-shot recovery
attempt flag was retained; the failure was not relabelled a pass.

The driver was corrected to reveal the actual Build button and wait for UI idle.
An explicit `recoverAfterVisibilityFailure` phase required that the old recovery
attempt existed but no Build click had been marked, then independently revalidated
exact source0, cancelled sequence3, idle state, no artifacts and journal max version0.
No flag/source/history was reset. `stopped-03` passed at 20:37:29.693842 UTC,
confirming that the failed click had not compiled or changed source.

At 20:37:55.124323 UTC the guarded recovery actually clicked Build, observed
buildInProgress, ran the phone's native compiler/signing/receipt pipeline and
passed at source1/version1 with unchanged source content. It independently checked
package/policy, APK signature, project signer, immutable receipt and visible Install.
The original cancelled journal stayed sequence3. Another fresh-process `ready-03`
passed at 20:38:24.109334 UTC for the same exact artifact. No additional model call
or generated installation occurred during recovery.

| Artifact | Original failed cancellation test | Successful cancellation's local recovery |
| --- | --- | --- |
| Project | `app7f7a197e9428424ca0dfe133367d6f84` | `app40c0a0325dea4b53bf12773b160eeeea` |
| Package | `dev.smallwright.generated.app7f7a197e9428424ca0dfe133367d6f84` | `dev.smallwright.generated.app40c0a0325dea4b53bf12773b160eeeea` |
| APK bytes | 16859 | 16860 |
| APK SHA-256 | `21d9fa8602b8b050793f946f70608f53b79181710353791c69be830d3b892870` | `b9039165e38664ab572ca411652043b902c5faffcd065e05b19fa639f78e035a` |
| Public signer SHA-256 | `3860ce0dfd85bd1d284b9b8999652ccccae715c1ce8f36da0bc21c37eefe7b73` | `7d76e760d7c3c179504c43300c7dbc17dff001b912dcb4da8e0a36105ba38a72` |

Phone-only original APK:
`/data/user/0/dev.smallwright.controller/files/projects/app7f7a197e9428424ca0dfe133367d6f84/artifacts/build_f8ac7c40d27c4aaa94c8993c577f6050/app.apk`.
Phone-only local recovery APK:
`/data/user/0/dev.smallwright.controller/files/projects/app40c0a0325dea4b53bf12773b160eeeea/artifacts/app-v1.apk`.

## Code, harnesses and replay commands

Changed code: `controller/src/androidTest/java/dev/smallwright/controller/ProviderCancellationProbe.java`
and its opt-in route in `ArtifactPolicyInstrumentation.java`. The route reports
safe exception class/stage and explicit failed code0; it never emits exception
messages containing arbitrary source/transport data. Final tested Java hashes:
`90c09f7a015c939fbdcdf908a07af6f8f42c0e2a81280995e20a1ac4ed5bd1f0`
(probe), `c46ae427b3c68d28d6c063ecd5afd039ab0bd6a200e0851e698be53e3795d0c2`
(route). Documentation changes are this record, `docs/STATUS.md` and
`docs/alpha-acceptance.md`.

All host-built harness APKs share verified public certificate
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
Installed replacements used `adb install -r` with package/public certificate/hash
checks before installation and installed-base hash afterwards, preserving data.
No signing-key bytes were read or shipped.

| controller test harness use | Bytes | SHA-256 |
| --- | --- | --- |
| Failed v1 Send | 1067878 | `4e211dd934f48605b6891d73b02a7fc3f1255138cef916bd25b362fc87fe18d7` |
| Original settled outcome inspection | 1069742 | `50705bf2a952dbc2ddb7773f2fd7aeac577b9a177e88b0d363dd4a96e6be080e` |
| Actual v2 Stop | 1069906 | `b8e324080f3aaa514603b436b9ccd04536efa7b53ebf7ec33fccbc20007df401` |
| Final recovery/readiness/neighbor checks | 1071014 | `9f9f9e33500698979ff5a6d91f442119f25c835adbb3412ab6e7ab426299adbf` |

Final host-built test APK path:
`controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk`.
The other three harness APK paths/hashes are unchanged and listed in STATUS.

Ignored raw logs, immutable historical source pins, original host handles and
implemented drivers are under `toolchains/alpha-provider-cancel-20261011/`.
`tested-source-01..04-sha256.json` distinguish failed Send, original inspection,
successful Send and final recovery code. The drivers `run_phase.py`,
`guard_duplicate.py`, `run_neighbors.py` and `verify_final.py` exist locally;
they are not distributed tooling. Existing phase handles are never overwritten.
Recorded serial-free command shapes (use the actual private serial locally):

```bash
python3 toolchains/alpha-provider-cancel-20261011/run_phase.py --serial "$DEVICE_SERIAL" --phase prepare --label 02
python3 toolchains/alpha-provider-cancel-20261011/run_phase.py --serial "$DEVICE_SERIAL" --phase preview --label 02
python3 toolchains/alpha-provider-cancel-20261011/run_phase.py --serial "$DEVICE_SERIAL" --phase send --label 02 --context-sha256 cceb5c662fcfa40e1d2a8c6eebb8c865907ff27ccefbb9ffb57026939ce38a92
python3 toolchains/alpha-provider-cancel-20261011/run_phase.py --serial "$DEVICE_SERIAL" --phase stopped --label 03
python3 toolchains/alpha-provider-cancel-20261011/run_phase.py --serial "$DEVICE_SERIAL" --phase recoverAfterVisibilityFailure --label 03
python3 toolchains/alpha-provider-cancel-20261011/run_phase.py --serial "$DEVICE_SERIAL" --phase ready --label 03
python3 toolchains/alpha-provider-cancel-20261011/guard_duplicate.py --serial "$DEVICE_SERIAL"
python3 toolchains/alpha-provider-cancel-20261011/run_neighbors.py --serial "$DEVICE_SERIAL"
python3 toolchains/alpha-provider-cancel-20261011/run_phase.py --serial "$DEVICE_SERIAL" --phase restoreSelection --label 03
```

These describe historical executions, not permission to reset/replay attempted
fixtures. Preview and Send guards 01..04 have expected instrumentation code0;
normal successful phases have code-1. Every original host command completed exit0,
including reported instrumentation failures. Final duplicate Send rejection used
explicit free-only approval and the correct stored context hash, failed at GUARD,
and issued no further model request.

Host builds `probe-build-01/02/04/05/06/07.log` passed in 48/40/42/41/49/44s.
`probe-build-03.log` remains FAILED (36s): a test helper call/signature mismatch
was corrected before any live phase using that candidate. The sixth build actually
executed 23 existing GenerationRunTest cases, zero failures/errors/skips; JVM time
0.855s. Their simulated model callbacks establish state expectations, not physical
provider success.

Final capped offline command:

```bash
source scripts/env.sh
./gradlew qualityCheck :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 scripts/validate_benchmarks.py benchmarks/results/*.jsonl
python3 toolchains/alpha-provider-cancel-20261011/verify_final.py --serial "$DEVICE_SERIAL"
```

`quality-01.log`: BUILD SUCCESSFUL in 2m55s, 207 tasks,9 executed/198 up-to-date.
The full controller JVM task actually executed: 213 tests, zero failures/errors/
skips. Worker57 and protocol4 test results were cached, not rerun. Spotless passed;
Android lint reports contained zero errors, with controller72/worker2/protocol0
warnings. Four assembly tasks passed with APK bytes unchanged. Project-local
JDK21.0.12.1+1, pinned SDK/API36, no global Java change or dependency update.

Six sequential physical neighbors passed, each host exit0/instrumentation code-1:
workspace source6/version6 ready, build-resources source6/version6 ready, ECJ Stop
source2/version1 ready, compiler measurement source2/version2 ready, screen-lock
source2/version1 ready, and generation completion. Completion's controlled SQLite
verification fixture is not another real compiler/provider pass. No neighbor
called a model, compiled, installed or reset the cancellation projects.
Benchmark validation returned `7 valid results`; benchmark rows were unchanged.
No cancellation attempt was inserted into the twenty-request suite denominator.

Previous selection was restored at 20:42:12.551222 UTC with named PASS.
Final verification at 20:45:24.898272 UTC checked every original instrumentation
host PID absent, final Java/driver pins unchanged, all four installed harness bytes
matching host hashes and worker `cache/active-build` absent. Both generated APK
hashes/lengths still matched on phone and neither new package was installed.
Only the four owned harness packages were force-stopped, with their PIDs absent
thereafter. No uninstall, data clear, source overwrite or credential access occurred.
The ignored authoritative result is `final-installed-source-and-cleanup.json`.

## Remaining work

This proves local Stop propagation, eventual original-client settlement,
saved-source preservation and readiness suppression for one observed live request,
plus actual local compilation and exact recovery readiness. It proves neither
server-side inference abort nor zero observed billed usage. It does not provide
an inference/build latency measurement, low-storage/ENOSPC or spontaneous eviction
pass, sustained thermal/whole-pipeline resource budgets, detached-laptop execution,
cross-device encrypted recovery, S23 frozen Step repair, redistribution clearance
or the full20-request suite on both phones. Continue resource/scale and actual
failure-recovery work before closing alpha acceptance.
