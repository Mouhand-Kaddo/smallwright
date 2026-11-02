# Live free-only compiler-error repair on physical A33

## Outcome and scope

On 2026-10-11 UTC (2026-10-12 Dubai), the user-selected live repair passed on
Samsung Galaxy A33 5G SM-A336E, Android16/API36, ARM64, physical properties
qemu0. Fresh preflight at20:06:32.810731UTC reconfirmed this device before
installation/runtime. Private serial and coordinates remain in ignored records.
Earlier S23 Ultra evidence and its frozen Step repair are separate.

A test-assisted initial counter source really failed Java compilation on the
phone. One actual Send & repair attempt used the production app/client and a
real OpenRouter response. The resulting source2/version2 really compiled and
signed in the separate-UID worker, restored from its exact receipt, installed
through the approved phone-only USB debug route, and passed native standalone
Counter0→1→2 plus force-stop/relaunch retention2. No developer source edit,
synthetic model response, second Send, paid fallback or remote build rescued it.
The initial instrumentation attempt failed a wrong test expectation; later
observation of that same saved run passed. Preserve these distinct outcomes.

This is a bounded compiler-error repair check, not a frozen benchmark request,
live generation of the initial error, automatic multi-repair-loop evaluation,
full alpha pass, S23 frozen Step repair or production installer-consent pass.
The twenty-request suite is still unrun on both devices. No benchmark row added.

## Contract and implementation

Base checkpoint `b637856`; production sources/dependencies unchanged. New
`controller/src/androidTest/java/dev/smallwright/controller/A33LiveRepairProbe.java`
and opt-in `a33LiveRepair` route in `ArtifactPolicyInstrumentation.java`.
`docs/STATUS.md`, `docs/alpha-acceptance.md` and this record describe the result.
User-owned skill/documentation changes remain outside this checkpoint.

Use normal MainActivity with a new owned project, so only ordinary production
runtime uses the credential previously entered in the app. Neither host nor
instrumentation reads, copies, prints or decrypts a key. No existing credential
store or host OAuth token was inspected. A project-test ContextWrapper would
have had no credential and would not exercise the requested production route.

The initial fixture replaces one default `count++;` with
`count += missingRepairIncrement;`. Initial source/origin metadata is synthetic;
LocalBuildCoordinator/worker diagnostics are real. The request asks to repair
this native Java counter while preserving its package, Activity, Increment,
count persistence, framework-only APIs and existing permissions. No S23 fixture
was seeded/substituted. The fixture is one-shot and remains saved under
`a33-live-repair-fixture-v1`.

Missing free approval, wrong exact context SHA and duplicate authorized Send
all fail at GUARD before opening the Activity or issuing a request. Preview
checks actual displayed payload against ContextPreviewGate bytes and cancels
without changing failed source/run. Send rechecks source1/run sequence5,
request/chat, numeric diagnostic provenance, attachments and exact context SHA;
then persistently reserves the one-shot attempt before clicking Send & repair.
The app's own context/revision checks stay enabled. Each host runner retains its
original Popen/PID; observation timeout means wait on that same handle, not retry,
kill or fabricate completion. Instrumentation exit and named result are checked.

Existing fixed model `cohere/north-mini-code:free` is listed free in the
[official OpenRouter pricing page](https://openrouter.ai/cohere/north-mini-code:free/pricing),
checked before Send. ModelClient has no paid fallback or transport retry.
This establishes the selected free-only request policy, not a measured account
bill. Provider-reported total usage was **4673 tokens**; reported cost is
**unknown/null**, because the app did not retain it. No billing/key inspection.

## Physical sequence and authoritative evidence

Ignored evidence root: `toolchains/alpha-live-repair-20261011/`. All UTC timestamps
below come from original records; no inference/build latency is claimed.

| Case | Expected / actual | Evidence in that root |
| --- | --- | --- |
| Seed,19:55:10.647522 | source1; actual JavaCompileFailure; one numeric problem; REPAIR_NEEDED sequence5, repairs1; no model | `seed-handle-01.json`, `seed-01.txt` |
| Exact preview/cancel,19:55:38.901757 | 8812-byte displayed payload; same failed source/run; no request | `preview-handle-01.json`, `preview-01.txt` |
| Missing approval / wrong SHA | expected GUARD rejection, host0/instrumentation0 | `guard-handle-01.json`, `guard-handle-02.json`, corresponding raw text |
| One live Send,19:56:48.584168 | real response/apply/phone build; original observer FAIL at VERIFY_REPAIRED_ARTIFACT | `send-handle-01.json`, `send-01.txt`, `send-driver-handle.json` |
| First saved observer | FAIL IllegalStateException at INSPECT_ORIGINAL_SAVED_RUN; no model/source change | `verifySaved-handle-01.json`, `verifySaved-01.txt` |
| Corrected saved verification,20:02:51.753304 | same original run source2/version2 READY_TO_TEST sequence9, repairs1; exact signed APK, PASS host0/instrumentation-1 | `verifySaved-handle-02.json`, `verifySaved-02.txt` |
| Fresh ready reopen,20:06:33.238032 | exact same source snapshot/sequence/artifact/signer and visible enabled Install; no rebuild/request | `ready-handle-01.json`, `ready-01.txt` |
| Exact phone install + failed first verifier | new package absent; install returned Success; verifier then rejected Android path prefix | `installed-runtime-01.json`, original terminal traceback |
| Installed identity / runtime preparation | exact installed v2/hash; verifier command had no focus summary; no taps | `installed-runtime-02.json`, original terminal traceback |
| First native UI observation | Counter0; Android renders native button as INCREMENT; exact-case selector rejected it; no taps | `installed-runtime-03.json`, `runtime-initial-03.xml`, original terminal traceback |
| Corrected native runtime,20:09:16..29 | installed bytes unchanged; counts0/1/2/2; controller/worker absent, PASS; no reinstall/request | `installed-runtime-04.json`, `runtime-*-04.xml` |
| Six neighbors,20:09:44..56 | named PASS, host0/instrumentation-1; no model/build/install | `recovery-and-neighbor-results-01.json`, `neighbor-*-01.txt` |
| Duplicate approved exact Send,20:10:16.053105 | expected GUARD rejection before UI/request, PASS | `guard-duplicate-handle-01.json`, `guard-duplicate-01.txt` |
| Selection restoration,20:10:24.223015 | only prior active project preference restored; owned project/source/outcome retained | `restoreSelection-handle-01.json`, `restoreSelection-01.txt` |

The seed failed BUILDING reservation deliberately consumed version1.
RunJournal.maximumVersionCode and the existing monotonic allocator correctly
assign version2 to the repaired build. The initial test mistakenly required1;
only the test expectation changed. The first saved verifier then wrongly tried
`readRecoverySources(1)` after source2 existed: this API reads the current
revision, not historical versions. It was corrected to verify current source2;
original source1/diagnostic pins remain established by seed/preview/Send records.

Two host-only verifier fixes followed installation: allow `~~` in Android's
installed directory and request `dumpsys window` (its `windows` subcommand omits
the focus summary here). UI inspection established the native Button's actual
all-caps INCREMENT text before accepting either framework-rendered case. The
final checks still require exactly one native TextView/count and one enabled
native Increment Button, exact count after each tap and restart, app focus,
exact installed hash/version and absence of controller/worker. Failed records
were never overwritten; no functional output was changed to fit the test.
All cloud-generated source/APK bytes stayed on the phone throughout.

Neighbors: owned-workspace source6/version6 readiness, larger-build source6/
version6 readiness, actual ECJ Stop ready source2/version1, native-template
measurement ready source2/version2, screen-lock recovery ready source2/version1,
and generation-completion ordering/failure checks. The last uses SQLite/
verification-only bytes, not a model/compiler/signature execution claim.

## Exact identities

- Project: `app24c7a77909c24cb29df6a7c970ae9043`.
- Package: `dev.smallwright.generated.app24c7a77909c24cb29df6a7c970ae9043`.
- Run: `run_726066339dc14ebfb8ce4ddcd6dfac82`.
- Failed source1 snapshot SHA: `3182b4b5407a1ed5bd7a53395517198c545a53a5fddebdbca48ffd33b08607c3`.
- Diagnostic Java SHA: `224cb74a6df640c50c3a3125553a7772ddd57ae3d9c1338ad5ad4ca0edc8c5d1`.
- Approved context SHA: `5a95f08aeec8f15bd3775749600431f53a25462c15faa07257b26ddeb892cdc5`.
- Final source2 snapshot SHA: `034ec7d8835feed69466597a00616c65a62af62f5a3f84ccaa2525b0d21a593f`.
- Phone APK: `/data/user/0/dev.smallwright.controller/files/projects/app24c7a77909c24cb29df6a7c970ae9043/artifacts/build_807a1fe11f424e2298b6b5aab60549f7/app.apk`.
- Actual version2 APK SHA: `5acea315860a443e7d49d01d4d8c497a30a077f2abfdfa4fc2cdfa07eca1872c`, 16860bytes.
- Project signing public certificate SHA: `f4970319808b31a288e1b833a126fca49f543b05cace54b9588a63a395f4a8bd`.

On-phone receipt/immutable artifact, ArtifactValidator package/manifest policy,
ApkVerifier real signature and ProjectSigner existing identity all checked.
Before install, run-as sha256sum and stat matched the exact verified artifact.
Package was absent; the pipeline executed entirely in adb shell on the phone:

```sh
run-as dev.smallwright.controller cat VERIFIED_PHONE_APK | pm install -r --user current --pkg VERIFIED_PACKAGE -S VERIFIED_BYTES -- -
```

Installed single base.apk matched the same bytes/hash and version2, again after
runtime and at final cleanup. This is the user's persistent USB debug installer
exception, not evidence of Android production installation confirmation.
No generated APK/source was downloaded, no uninstall/data clear occurred.

Final host-built harness artifacts, all installed bytes matched:

| Host APK path | SHA-256 | Bytes |
| --- | --- | --- |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `8ec5244003b3d67f2aacbd550ac713140bd289a44aa1670799cdf48c31b1ba2f` | 1047166 |
| `worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk` | `5243fb4e0d993bf25c3d98064005322ba020a30f324de6eee888140ec53e1434` | 147666 |
| `controller/build/outputs/apk/debug/controller-debug.apk` | `abf873d81bd6c9556eba40ded679db91fda7277d0ce719a7bd50425f7dd6c839` | 9093057 |
| `worker/build/outputs/apk/debug/worker-debug.apk` | `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850` | 21193573 |

Controller.test certificate SHA:
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
Original Send used controller.test SHA
`5066dfc9a63c5bdda0aa8f879c407a007211f80e3fad27f8bd585156bcc8fa1f`,
1044398bytes. Saved verifier01 used
`e11f3009022f629e992eaccf5bdc758143920f998fc0ab568b17384530db5460`,
1047150bytes. Only test APK changed; production controller/worker remained exact.
The record does not present the final harness as the original Send harness.

Final source pins (`tested-source-03-sha256.json`) matched after quality and cleanup:

- `controller/src/androidTest/java/dev/smallwright/controller/A33LiveRepairProbe.java`: `05f9701e13dca8c6989ce8d4501ff4f24d8cc55f0c204fdb74ed2727df24925c`.
- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`: `659d69b4236f68e3dad2c40dff626c959d56b114e6c890d66697bbf94dc6d341`.
- `toolchains/alpha-live-repair-20261011/run_phase.py`: `0b55552c96dcf2f823647606e71c9254330239d57ae7c3b4d981748d5abc2a5e`.

## Commands, checks, restoration and limits

Implemented ignored helpers are `run_phase.py`, `install_and_runtime.py`,
`run_neighbors.py`, `verify_final.py`. They require explicit serial, retain unique
records and do not erase data/retry Send. Example commands below use PHONE_SERIAL
from the verified private preflight; do not repeat seed or Send on this fixture.

```sh
python3 .agents/skills/real-phone-test/scripts/preflight.py --adb toolchains/android-sdk/platform-tools/adb --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-20261011/run_phase.py --serial "$PHONE_SERIAL" --phase seed
python3 toolchains/alpha-live-repair-20261011/run_phase.py --serial "$PHONE_SERIAL" --phase preview
python3 toolchains/alpha-live-repair-20261011/run_phase.py --serial "$PHONE_SERIAL" --phase send --context-sha256 5a95f08aeec8f15bd3775749600431f53a25462c15faa07257b26ddeb892cdc5
python3 toolchains/alpha-live-repair-20261011/run_phase.py --serial "$PHONE_SERIAL" --phase verifySaved --label 02
python3 toolchains/alpha-live-repair-20261011/run_phase.py --serial "$PHONE_SERIAL" --phase ready
python3 toolchains/alpha-live-repair-20261011/install_and_runtime.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-20261011/install_and_runtime.py --serial "$PHONE_SERIAL" --verify-installed-only --label 04
python3 toolchains/alpha-live-repair-20261011/run_neighbors.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-20261011/run_phase.py --serial "$PHONE_SERIAL" --phase restoreSelection
source scripts/env.sh
./gradlew qualityCheck :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 scripts/validate_benchmarks.py benchmarks/results/*.jsonl
python3 toolchains/alpha-live-repair-20261011/verify_final.py --serial "$PHONE_SERIAL"
```

Initial probe build failed a missing ChatStore parameter; the six host build attempts
are retained in `probe-build-01.log`..`06.log`. Final rebuild06 passed41s.
Offline quality passed53s,207tasks,8executed/199up-to-date; JVM tests reused cached
results. Changed androidTest lint analysis and Java formatting checks ran;
reports retained zero errors and controller72/worker2/protocol0 warnings.
No warning baseline/threshold change. Seven existing benchmark rows validated,
unchanged. No new model request or compiler workload during neighboring checks.
Independent review found the original timeout edge before Send; parent verified
its fix and final current-source/version-reservation verification. Parent checked
actual physical results independently; review is not physical evidence.

At `2026-10-11T20:11:46.077057+00:00`, all original instrumentation host PIDs were absent,
all four installed host-built APKs matched workspace bytes, tested source pins
matched, worker active-build scratch absent. Only own four harness packages and
this new generated package were stopped; existing app data/key/project retained.
Previous project selection restored. Phone keyguardfalse/Awake then; no future
unlocked-state promise. Raw temporary UI XML is retained under named test-only
paths and in ignored captures; no phone-wide capture, personal screen or key UI.
No live latency retained after the original observer failure; later reinspection
elapsed time is not an inference/build measurement.

Next alpha work: provider cancellation, spontaneous eviction/actual low storage,
whole-pipeline resource/thermal/retention budgets, detached-laptop execution,
full fixed suite on both devices, source/tool redistribution and user cohort.
The S23 frozen repair stays queued for that phone. This record closes issue#16's
bounded A33 live repair/runtime checkpoint, not the broader alpha goal.
