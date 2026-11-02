# User-requested A33 live free-only repair: timeout

The fresh user-requested test on 2026-10-12 **failed** at the app's model-request
timeout. One normal Send was attempted; source1 remained unchanged, journal
FAILED sequence7, and no repaired APK was published or installed. Send-to-settled
observation was 45,394ms, including settlement rather than isolated
inference latency. Provider usage, cost and whether the provider processed the
request remain unknown. No automatic retry, second request, paid fallback,
developer source repair or synthetic model response occurred.

This is separate from the [previous timeout](live-repair-guarded-a33-20261012.md)
and earlier successful cases. It adds no twenty-request benchmark row; alpha
acceptance remains incomplete.

## Contract and physical evidence

The real-phone-test preflight at03:25:18UTC identified physical Galaxy A33
SM-A336E, Android16/API36, ARM64, qemu0, authorized; repeated fresh preflight is
retained. The phone was awake/unlocked for the test phases. Device serial and
raw UI evidence stay local. Base revision098f66d plus the fresh test selector;
uncommitted storage test helpers/configuration and user documentation remain
separate. No production source changed in this checkpoint.

The fixed `user-20261012` selector uses new one-shot preferences and a fresh
project. Initial broken counter source and repair origin are test-assisted;
actual numeric Java compiler diagnostics, HTTPS model request, UI approval and
saved run use the production controller and isolated worker on the phone.
Neither instrumentation nor host inspected credentials. Only app runtime uses
the app-entered key. All generated source/artifact bytes remained on phone.

At03:25:51UTC the unauthenticated official
[OpenRouter model catalog](https://openrouter.ai/api/v1/models) listed the fixed
`cohere/north-mini-code:free` model's input and output prices as zero. The client
has no model fallback or automatic transport retry; timeout and budget policy
were unchanged. Catalog verification is not an account-billing observation.

Ignored raw root: `toolchains/alpha-live-repair-user-20261012/`.

| Case | Actual result | Evidence |
| --- | --- | --- |
| Unknown case | Expected GUARD rejection | guard-01.txt/handle |
| Seed03:27:06UTC | Actual JavaCompileFailure, one numeric problem, source1, REPAIR_NEEDED sequence5; no model | seed-01.txt/handle |
| Preview/cancel | Exact8812-byte context; failed source/run unchanged | preview-01.txt/handle |
| Missing approval/wrong hash | Expected GUARD rejections | guard-02/03.txt/handles |
| Single live Send03:27:38UTC | **FAIL** timeout; unchanged source1, FAILED sequence7, no artifact | send-01.txt/handle |
| Fresh-process reopen | PASS identical saved failed outcome/no readiness | ready-01.txt/handle |
| Duplicate exact-approved Send | Expected GUARD rejection; no second request | guard-04.txt/handle |
| Installation/runtime | **NOT RUN** because no repaired artifact exists | install-runtime-not-run.json |
| Previous selection restoration | PASS; owned failed project retained | restoreSelection-01.txt/handle |
| Six recovery neighbors | All named PASS; no new model/build/install | recovery-and-neighbor-results-01.json |
| Prior XML test handoff | PASS source4/version4 receipt, installed APK and stored acceptance; selection/chat restored | retained-handoff-01.txt/handle |
| Final03:29:21UTC | Harness/source/helper pins matched, prior installed APK unchanged, scratch absent, original handles absent, own harness processes stopped | final-verification.json |

The six neighbors were workspace/larger-build ready records, ECJ Stop,
compiler-measurement recovery, screen-lock recovery and completion-ordering
verification. These are retained/recovery checks, not fresh model or compiler
passes. XML verification did not newly accept a build or measure counter behavior;
the saved acceptance originated in the earlier test-assisted checkpoint.

Host0/Android code-1 means successful observation, **not** successful repair.
Expected guard failures require host0/code0 and the GUARD assertion. Original
Popen handles were waited to terminal completion; their30s observer yield caused
no retry or kill. No data clear/uninstall, timeout increase or old-fixture reseed.

## Exact identity and artifacts

- Project: `app71e2cdfb3ca641288839e61815573a54`.
- Run: `run_3b8894dcb315479f8d79b191567eafa2`.
- Unchanged source1 snapshot SHA256: `974a381b22eba400f73dbf4b25f2351c2476d1bf1e945148c4aad9a016e87519`.
- Approved context SHA256: `21f3928111c4829a0f27a8e564c0f439cecdb9663c54474cec5f59e09786782e`.
- No new generated APK path exists.
- Public harness certificate SHA256: `19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.

Only controller.test was updated in place, after all four installed baseline
hashes and test package/certificate were checked. Existing production harness
and worker.test remained installed with data retained.

| Package / retained host APK | Installed SHA256 |
| --- | --- |
| controller: toolchains/alpha-test-handoff-20261012/fixed-controller-debug.apk | 45adb0b429c5660c822e1b115fb68161131cbda0bb8f0acaa3e02cded41e658b |
| controller.test: toolchains/alpha-live-repair-user-20261012/controller-debug-androidTest.apk | 32976c22421b7c64b0b399b9189dd3cac8cfbbc1bf2dbaefeb295689427b2b03 |
| worker: toolchains/alpha-child-policy-20261012/production-worker-debug.apk | 0fc753e43a7d3c4b1ca259ef9f320528bc16320cd74754f8396b4cb6378f39a7 |
| worker.test: toolchains/alpha-package-storage-20261012/green-worker-debug-androidTest.apk | 5032a3976316edb0f995c7c18d33509819fe6ec4818332ca44aff51039780648 |

Exact test/client/worker and original helper hashes are in tested-source-pins.json.
The prior installed XML v4 APK remained
`283ee00a4796164671c58baf9b5667b00e76ef7f58dac8a6d71ec00f65df7800`.
No generated APK/source was downloaded.

## Commands and handoff

Completed commands below document the reserved attempt; **do not repeat seed or
Send**. PHONE_SERIAL is private; CONTEXT_SHA is the exact preview hash above.

```sh
python3 .agents/skills/real-phone-test/scripts/preflight.py --adb toolchains/android-sdk/platform-tools/adb --serial "$PHONE_SERIAL"
source scripts/env.sh
./gradlew spotlessApply spotlessCheck :controller:assembleDebugAndroidTest :controller:lintDebug --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-live-repair-user-20261012/install_test.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-user-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase seed
python3 toolchains/alpha-live-repair-user-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase preview
python3 toolchains/alpha-live-repair-user-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase send --context-sha256 "$CONTEXT_SHA"
python3 toolchains/alpha-live-repair-user-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase ready
python3 toolchains/alpha-live-repair-user-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase restoreSelection
python3 toolchains/alpha-live-repair-user-20261012/run_neighbors.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-user-20261012/run_retained.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-user-20261012/verify_final.py --serial "$PHONE_SERIAL"
```

Unknown-case, missing-approval, wrong-hash and duplicate guard invocations are
retained in their handle records. Formatting/lint/test APK assembly passed58s,
105tasks9executed96up-to-date; zero lint errors/77warnings. Relevant androidTest
compile/assembly ran; lint analysis was cached. No new JVM suite is claimed for
the trivial selector. Quality log: build-quality-01.log.

Exact changed tracked paths for this checkpoint:
`controller/src/androidTest/java/dev/smallwright/controller/A33LiveRepairProbe.java`,
`docs/validation/live-repair-user-a33-20261012.md`, `docs/STATUS.md`.
Pre-existing storage/user skill/research work remains unstaged.

Next: investigate repeated model-request timeouts without reading credentials
or raising spend; any subsequent live request needs its own exact approval and
one-shot evidence. Continue independent resource/full-suite/two-device and
licensed-toolchain gates; no full alpha pass.
