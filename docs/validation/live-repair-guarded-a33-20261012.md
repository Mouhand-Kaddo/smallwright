# Fresh A33 free-only repair: timeout retained

On 2026-10-12, the user-requested fresh live compiler-error repair **failed**:
the normal app's OpenRouter request timed out. App Send-to-settled observation
was 45,404 ms, including waiting and settlement, not separately measured inference
latency. No model repair was applied; source1 stayed unchanged, journal FAILED
sequence7, no APK became ready, and no generated app was installed. Earlier
[successful repair observations](live-repair-a33-20261011.md) and the
[mixed repeat](live-repair-repeat-a33-20261012.md) remain separate. This adds no
benchmark row and establishes no full alpha pass.

## Contract and policy

Fresh real-phone-test preflight at 02:03:18 UTC, repeated in the run's ignored
preflight record, identified physical Galaxy A33 SM-A336E, Android16/API36,
ARM64, qemu0. The phone was awake/unlocked for the UI phases. Private serial and
captures stay in ignored local evidence.

The new fixed selector `guarded-20261012` uses its own one-shot preferences and
new project. Initial counter source with one unresolved increment symbol and
repair origin are test-assisted; the actual compiler failure and HTTPS request
use the production controller/separate-UID worker on the phone. Generated source
stayed on the phone. No credential store, key or host OAuth token was inspected
by host/instrumentation; only ordinary app runtime uses its app-entered key.

At 02:05:06 UTC, the unauthenticated official
[OpenRouter model catalog](https://openrouter.ai/api/v1/models) listed
`cohere/north-mini-code:free` prompt/completion prices as zero. Its
[pricing page](https://openrouter.ai/cohere/north-mini-code:free/pricing) also
listed Free. The existing client fixes that model, with no paid fallback or
automatic transport retry. One real request was sent after the exact context
approval; there was no second request, developer repair or synthetic response.
Provider usage/cost and account billing remain **unknown**; no billing inspection
occurred. A timeout does not establish whether the provider processed the request.

## Physical evidence

Ignored raw root: `toolchains/alpha-live-repair-guarded-20261012/`.

| Case | Actual result | Evidence |
| --- | --- | --- |
| Unknown selector | Expected GUARD rejection before request | `guard-01.txt` and handle |
| Seed, 02:05:15 UTC | Actual JavaCompileFailure, one numeric problem, source1, REPAIR_NEEDED sequence5; no model | `seed-01.txt` and handle |
| Preview/cancel | Exact 8,812-byte context; failed source/run unchanged | `preview-01.txt` and handle |
| Missing approval / wrong context hash | Expected GUARD rejections | `guard-02.txt`, `guard-03.txt` and handles |
| Single live Send, 02:05:43 UTC | **FAIL**: model request timed out; source1 unchanged, FAILED sequence7, repairs1, no artifact | `send-01.txt` and handle |
| Fresh-process reopen | PASS: identical saved failed outcome/source and no signed artifact | `ready-01.txt` and handle |
| Duplicate exact-approved Send | Expected GUARD rejection; no second request | `guard-04.txt` and handle |
| Installation / native runtime | **NOT RUN** because no repaired artifact exists | `install-runtime-not-run.json` |
| Previous selection restoration | PASS; owned failed project/source/outcome retained | `restoreSelection-01.txt` and handle |
| Six recovery neighbors | Named PASS; no new model/build/install | `recovery-and-neighbor-results-01.json`, `neighbor-*.txt` |
| Prior XML installed/test handoff | PASS: exact source4/version4 receipt/installed APK and persistent acceptance; original selection/chat restored | `retained-handoff-01.txt` and handle |
| Final verification | Exact installed harness pins, prior XML APK unchanged, scratch absent, original observer PIDs absent, own harness processes stopped | `final-verification.json` |

Host0/Android code-1 establish successful observation phases, **not** a successful
repair. Expected guards require host0/code0 and the named GUARD assertion. The
live outcome explicitly says `repaired:false`. Its original Popen handle was
waited to terminal completion; a 30-second observer yield did not trigger retry.
No timeout increase, fixture reseed, data clear, uninstall or paid fallback.

The six neighbors verify workspace/larger-build ready records, ECJ Stop recovery,
compiler-measurement recovery, screen-lock recovery and generation-completion
ordering. The last uses verification-only bytes, not a new model/compiler pass.
The XML check proves retained source/artifact/acceptance, not a fresh counter-count
measurement or human functional signoff.

## Exact identities and code

- Base revision: `fd9f2df`, plus the test-only selector below and pre-existing
  uncommitted child-policy worker candidate. This record does not close #26 or
  claim a reviewed/committed production-policy checkpoint.
- Project: `appe6f2c8b96b6d4d72b1f1f7c87c383f93`.
- Run: `run_2e8b6d2f4d894687b8aa8d0b4042d42c`.
- Failed and final source1 snapshot SHA256: `5c7a0f4881bc5ee623433ca20e032ad9e5d8e56768b36e022815c1ae0e7657e8`.
- Approved context SHA256: `d386b4753ff53244727a3d17683076f87496d429a890a6323e0de425145244ac`.
- No new generated APK path exists for this failed attempt.

Only controller.test was updated in place for this run after checking all four
installed baselines and its package/public certificate. Existing production
controller/worker and worker.test were preserved. Public harness certificate:
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.

| Package / retained host APK | Installed SHA256 |
| --- | --- |
| controller: `toolchains/alpha-test-handoff-20261012/fixed-controller-debug.apk` | `45adb0b429c5660c822e1b115fb68161131cbda0bb8f0acaa3e02cded41e658b` |
| controller.test: `toolchains/alpha-live-repair-guarded-20261012/controller-debug-androidTest.apk` | `234c7e3aa057c1c336af94715416403914d5dce276470ddb662a134856f7740e` |
| worker: `toolchains/alpha-child-policy-20261012/production-worker-debug.apk` | `0fc753e43a7d3c4b1ca259ef9f320528bc16320cd74754f8396b4cb6378f39a7` |
| worker.test: `toolchains/alpha-child-policy-20261012/production-worker-debug-androidTest.apk` | `3fc2e107fed7966f44414764089bd07579a0a4d2a3e15b1cab52ab8fe6d02f29` |

Exact test/client/worker/launcher and original helper source hashes are in
`tested-source-pins.json`; final verification confirmed those bytes unchanged.
The prior installed native XML v4 SHA256 remained
`283ee00a4796164671c58baf9b5667b00e76ef7f58dac8a6d71ec00f65df7800`.
No generated APK/source bytes were downloaded.

## Commands, checks and next task

These document the completed reserved attempt; do not repeat seed/Send.
Use PHONE_SERIAL from the private preflight and CONTEXT_SHA from the exact preview.

```sh
python3 .agents/skills/real-phone-test/scripts/preflight.py --adb toolchains/android-sdk/platform-tools/adb --serial "$PHONE_SERIAL"
source scripts/env.sh
./gradlew spotlessApply spotlessCheck :controller:assembleDebugAndroidTest :controller:lintDebug --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-live-repair-guarded-20261012/install_test.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-guarded-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase seed
python3 toolchains/alpha-live-repair-guarded-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase preview
python3 toolchains/alpha-live-repair-guarded-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase send --context-sha256 "$CONTEXT_SHA"
python3 toolchains/alpha-live-repair-guarded-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase ready
python3 toolchains/alpha-live-repair-guarded-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase restoreSelection
python3 toolchains/alpha-live-repair-guarded-20261012/run_neighbors.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-guarded-20261012/run_retained.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-guarded-20261012/verify_final.py --serial "$PHONE_SERIAL"
```

Build/format/lint passed in 56s, 105tasks (10executed/95up-to-date). Changed
androidTest lint analysis ran; reports retained zero errors/77warnings. No JVM
suite or twenty-request benchmark run is claimed for this trivial test selector.
Build log: `build-quality-01.log`. Narrow test UI evidence remains local; no
personal/credential screen capture. Only own harness processes were stopped;
failed fixture, credentials, prior projects and generated data remain intact.

Exact changed tracked paths for this checkpoint:
`controller/src/androidTest/java/dev/smallwright/controller/A33LiveRepairProbe.java`,
`docs/validation/live-repair-guarded-a33-20261012.md`, `docs/STATUS.md`.
Pre-existing worker-policy/user skill/research edits remain separate.

Next: any new free-only live attempt needs a separate one-shot case and fresh
model/device checks; preserve this timeout. Continue the independent compiler
policy/resource, full fixed-suite, two-device and distribution gates in
[status](../STATUS.md). The private alpha is still incomplete.
