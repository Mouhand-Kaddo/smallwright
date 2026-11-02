# Fresh A33 repair with safe network observation

On2026-10-12 a fresh one-shot free-only repair **passed**: the real phone
compiler rejected the seeded counter error, the live model repaired source2,
and the isolated worker built/signed version2. Exact phone-only USB debug
installation passed; standalone native Counter0→1→2 and force-stop/relaunch2
passed with controller/worker stopped. This is separate from the two preserved
[timeouts](live-repair-user-a33-20261012.md); **their cause is not established
and no timeout fix is claimed**. No fixed-suite row/full alpha pass.

## Diagnosis and contract

Before this attempt, ranked possibilities were connect/upload stall, waiting
for response headers, and slow response-body delivery. A test-only sampler
observed this new request every5s, bounded32 samples/24 allowlisted method names
per active model thread. It records own-process thread state and class/method
names only: no headers, payloads, locals, keys, filenames or line numbers.
Sampling filters on the production ModelClient.completeWithKey call; unrelated
thread frames are not retained. An empty sample means not observed at that
instant, not proof of network inactivity. This adds no production observability
framework or request/client change.

Samples at26ms,5111ms,10215ms,15320ms were retained. The first had no observed
model call; the latter three showed SocketInputStream→ConscryptEngineSocket→
Http1xStream.ChunkedSource→InflaterSource/GzipSource→ModelClient response reads.
Together with completeWithKey's real200-only input path, this establishes
response-body waiting for **this successful request**. It does not measure DNS,
TLS, upload durations, byte arrival, provider queue/inference separately, or
locate earlier timeout phases. Provider intermittency is a possibility, not a
confirmed cause. Deterministic fast provider reproduction is unavailable;
production timeout/retry/spend policy was left unchanged, no speculative fix.

Real-phone-test preflight03:34:23UTC identified physical Galaxy A33 SM-A336E,
Android16/API36,ARM64,qemu0, authorized. Awake/unlocked guards preceded UI phases.
Private serial and UI captures remain ignored. Base revisiona4b50f1 plus the
new test-only network case and pre-existing uncommitted storage test helpers.
Only controller.test was updated in place after all four installed hashes,
package and public certificate were checked. No data clear/uninstall.

The fixed network-20261012 case owns fresh project/preferences and exact
source/run/chat/diagnostic provenance. The initial source error/origin are
test-assisted; compiler failure, HTTPS response, source application, worker,
signature/receipt and native runtime are real. Only normal app runtime used
its app-entered credential; host/instrumentation did not inspect credentials.
At03:34:24UTC the unauthenticated official
[OpenRouter catalog](https://openrouter.ai/api/v1/models) listed fixed
cohere/north-mini-code:free input/output prices zero. One exact-approved Send,
no retry/model fallback/paid fallback. Reported tokens4619; provider/account
cost remains unknown. All generated source/APK bytes stayed on phone.

## Actual evidence

Ignored root: toolchains/alpha-live-repair-network-20261012/.

| Case | Result | Raw evidence |
| --- | --- | --- |
| Unknown/missing approval/wrong hash | Expected GUARD rejections | guard-01/02/03.txt and handles |
| Seed03:35:19UTC | Actual one-problem JavaCompileFailure/source1/REPAIR_NEEDED sequence5 | seed-01.txt/handle |
| Exact preview/cancel | PASS8812-byte context and unchanged failed source | preview-01.txt/handle |
| One live Send03:35:53UTC | PASS source2/version2/READY_TO_TEST sequence9, repair1; Send-to-settled17763ms | send-01.txt/handle |
| Fresh-process ready/reopen after runtime | PASS exact source/snapshot/receipt/signer | ready-01/02.txt/handles |
| Duplicate exact-approved Send | Expected GUARD rejection/no second request | guard-04.txt/handle |
| Exact debug install/native runtime | PASS0→1→2/restart2, builders absent | installed-runtime-01.json; runtime XML |
| Restore selection | PASS owned project retained | restoreSelection-01.txt/handle |
| Six recovery neighbors | PASS verification only/no model/build/install | recovery-and-neighbor-results-01.json |
| Prior XML v4 handoff | PASS source/receipt/installed bytes/stored acceptance; selection/chat restored | retained-handoff-01.txt/handle |
| Final03:37:41UTC | All hashes/source/helper pins match; original handles absent; scratch absent; own harness processes stopped | final-verification.json |

The six neighbors were workspace/larger-build ready records, ECJ Stop,
compiler-measurement recovery, screen-lock recovery and completion-ordering.
Prior XML acceptance was previously test-assisted, not newly accepted here;
no fresh XML count/human acceptance or production installer consent claim.
The new generated app remains installed with count2. All original Popen handles
were followed to terminal; no observer timeout caused restart/kill.

## Identities, commands and handoff

- Projectapp1731b817b5bb4cc0adb488fd99d3171f; runrun_20bd44dcf4b34738ac69c5ccd6199783.
- Source2 snapshot SHA256571d546574cbb2dcbd5c76c7076d57bdf97e32bb7c3d3dc7c27f3b0471951ab9.
- Approved context SHA25640e5abe17918c0466e38ff224627a7af670dca021e09987dde51d08c076649d9.
- Generated phone APK: `/data/user/0/dev.smallwright.controller/files/projects/app1731b817b5bb4cc0adb488fd99d3171f/artifacts/build_f9c884ef20944e53a5a7086eccab2fb2/app.apk`.
- APK16860B/SHA256`9885c2cef4aa8b5b44e1b7b43ad9b57f3ee3c447f96cc8632b7f651e9e9578f7`.
- Per-project public signer SHA256`4584954da0b0df6f771e63b394191b24f9b7c93a128d1b6ddd62088fba8b4a42`.

| Harness package / durable host APK | Installed SHA256 |
| --- | --- |
| controller: toolchains/alpha-test-handoff-20261012/fixed-controller-debug.apk | 45adb0b429c5660c822e1b115fb68161131cbda0bb8f0acaa3e02cded41e658b |
| controller.test: toolchains/alpha-live-repair-network-20261012/controller-debug-androidTest.apk | f15eaeb9d5513ac9ed8e378ed59169713dc910dec390409ca05559a4aa3a10c2 |
| worker: toolchains/alpha-child-policy-20261012/production-worker-debug.apk | 0fc753e43a7d3c4b1ca259ef9f320528bc16320cd74754f8396b4cb6378f39a7 |
| worker.test: toolchains/alpha-package-storage-20261012/green-worker-debug-androidTest.apk | 5032a3976316edb0f995c7c18d33509819fe6ec4818332ca44aff51039780648 |

Public harness certificate19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25.
Source/helper pins in tested-source-pins.json. Prior XML v4 hash remained
283ee00a4796164671c58baf9b5667b00e76ef7f58dac8a6d71ec00f65df7800.

Completed commands document this reserved case; **do not repeat seed/Send**.
PHONE_SERIAL is private, CONTEXT_SHA is the exact preview hash above.

```sh
python3 .agents/skills/real-phone-test/scripts/preflight.py --adb toolchains/android-sdk/platform-tools/adb --serial "$PHONE_SERIAL"
source scripts/env.sh
./gradlew spotlessApply spotlessCheck :controller:assembleDebugAndroidTest :controller:lintDebug --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-live-repair-network-20261012/install_test.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-network-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase seed
python3 toolchains/alpha-live-repair-network-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase preview
python3 toolchains/alpha-live-repair-network-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase send --context-sha256 "$CONTEXT_SHA"
python3 toolchains/alpha-live-repair-network-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase ready
python3 toolchains/alpha-live-repair-network-20261012/install_and_runtime.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-network-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase restoreSelection
python3 toolchains/alpha-live-repair-network-20261012/run_neighbors.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-network-20261012/run_retained.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-network-20261012/verify_final.py --serial "$PHONE_SERIAL"
```

Other guard/second-ready arguments retained in handle files. Quality passed58s,
105tasks9executed96up-to-date; zero lint errors/77warnings, analysis cached.
Relevant test compile/assembly ran; no new JVM suite or production rebuild.
No tests asserting the sampler's implementation were added; physical boundary
observations and real receipt/runtime assertions are the validation.

Changed tracked paths: A33LiveRepairProbe.java under controller/src/androidTest,
docs/validation/live-repair-network-a33-20261012.md, docs/STATUS.md.
Next: finish own-package storage accounting and a longer guarded build sequence;
keep earlier timeouts/full-suite/two-device/licensing gates open.
