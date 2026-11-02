# A33 frozen counter_01: initial attempt rejected

Physical Samsung Galaxy A33 5G (SM-A336E), Android 16/API36, ARM64,
2026-10-12. Fresh connection and unlock checks passed. This is the first actual
A33 supported-request attempt in the frozen twenty-request suite; nineteen
requests remain pending. Historical S23 trials and bounded synthetic checks do
not count as this round. No full alpha pass or human functional acceptance.

## Inputs and execution

Frozen request: “Add a Decrement button beside Increment. It should subtract
one and persist the count after closing the app.” The exact catalogue row is
copied into `controller/src/androidTest/assets/alpha/counter_01.json` and checked
against `benchmarks/prompts.jsonl`. Catalogue/protocol/source/helper pins,
preflight and contract are retained under ignored
`toolchains/alpha-suite-counter01-20261012/`.

Normal UI created an untouched source0 project. Prepare and cancelled exact
context preview passed. Missing approval, wrong hash and same-attempt duplicate
Send guards rejected without another model request. One normal Send at
04:32:39UTC used `cohere/north-mini-code:free`; fresh unauthenticated OpenRouter
catalogue metadata reported prompt/completion prices0 at04:27UTC. App-owned key
was used only by the app; no credential read/export, paid fallback, generated
source patch, remote build or APK download.

Initial snapshot SHA256 `f7b6e6d7e98eb71d482184c49857fe562604036c27fd8949acf17fb1fe07ec6c`;
request SHA256 `07508ab2c815e04ab1ad8088e983289eb03459ff7e57c5287bdf3010e522331b`;
approved context `0f42a83b1c70a8c1319dc62146c6d0cfa5c91186b25cdb0c6f4774e9f777f867`.
Production run `run_f069c5b25ff74b20b610a1ebdaf444a2` reached READY_TO_TEST,
sequence5, source1/version1, zero compiler repairs. Provider tokens4585; cost
unknown. Send-to-settled20286ms. Last pre-BUILDING query start to verified visible
readiness2152ms is a conservative upper bracket including unsampled model tail,
setup and observation overhead; exact compiler latency remains null in ledger.

## Independently observed outcome

| Case | Expected | Actual |
| --- | --- | --- |
| Phone compilation/signature/readiness | Verified native APK | Passed |
| Authorized USB debug installation | Exact package/version/hash | Passed; not production installer consent |
| Buttons | Decrement beside Increment | Failed: Decrement above Increment, vertically stacked |
| Increment then Decrement | 0→1→0 | Passed |
| Decrement from zero | 0→-1 | Failed: stayed0 |
| Force-stop/relaunch after failed subtraction | Observed saved value retained | Passed:0 remained0; does not cure arithmetic failure |

`installed-runtime-01.json` preserves the first failed placement assertion
before any taps. `installed-runtime-02.json` separately preserves the subsequent
negative-subtraction failure on the same unchanged APK. The negative XML and
`failed-checklist-persistence-01.json` independently establish actual0 versus
expected-1 and restart0. Builders were stopped for native execution; native
TextView/Button classes and exact generated foreground package were observed.
No failed result was overwritten or assertion weakened. The benchmark row
`benchmarks/results/a33_counter01_20261012_01.jsonl` is **rejected**.

## Artifact and harness identity

Generated package `dev.smallwright.generated.app652c00374f764652a3e4c0e501e3a6dc`;
version1,16859 bytes, no requested permissions. APK SHA256
`9c4adc9bdeb6e733e592da91ed70b2fd0e9d2af4bc4fbe13326bf1e8d7d01061`;
project signer `b7b36902471bc208a6c2c96b23620fa473d6f56a75c22f9cef92ec85bc2d2d19`.
Final source SHA256
`92fa35ebc3ad2dfab52454dc104adc15ba03ddc97e5bd2b3f5b304bb194dc7b9`.
Phone-only artifact:
`/data/user/0/dev.smallwright.controller/files/projects/app652c00374f764652a3e4c0e501e3a6dc/artifacts/build_134facea315248a3b3af90774dad40ee/app.apk`.

Controller SHA256 `45adb0b429c5660c822e1b115fb68161131cbda0bb8f0acaa3e02cded41e658b`;
worker `0fc753e43a7d3c4b1ca259ef9f320528bc16320cd74754f8396b4cb6378f39a7`;
initial suite controller.test `143437aedfdee50446b2e94eeb01128782149417a859383dd92bb4b94ebac6cb`;
worker.test `b66e17365a3daa395b78f7871bc490dfc9b8a47528ef06f8297df016a70c1901`.
Controller.test host artifact:
`toolchains/alpha-suite-counter01-20261012/green-controller-debug-androidTest.apk`.
Test-only helper/routes/assets changed; production APKs remained unchanged.

## Resources, validation and restoration

Before this round, provisional targets were frozen:10s first/5s warm compilation,
1GiB approximate reported kernel-counter envelope and256MiB combined main-builder
own-package app+data retained snapshots. This request's upper build bracket2152ms
passed the first-build target. Reported component high-water sum656800KiB
(641.40625MiB) passed1GiB; controller finish tail afterward is excluded. Own-package
retained sums88598528B before/88647680B after passed256MiB. Non-atomic snapshots
exclude generated/test APK UIDs; cache is already included in data. No transient,
page-exact, simultaneous physical peak, enforced memory cap or thermal-status
measurement was established for this request.

Host RED build passed52s; physical missing-helper RED failed at GUARD as expected.
Two later host test-source build failures (missing Files import and unavailable
Files.readString API) are preserved. Final `format qualityCheck
:controller:assembleDebugAndroidTest` passed55s, zero lint errors/controller77
warnings. These host setup failures are not model attempts. Benchmark validation
passed8 actual ledger rows. Original generation/observer handles both reached
terminal success before sampling release. Saved readiness/reopen, duplicate
rejection and prior selection restoration followed; fresh-process selection
verification passed04:50UTC. Generated v1 remained installed at initial cleanup; the separate feedback attempt below updated it in place.

Commands use project-local SDK/JDK, capped offline Gradle and explicit selected
ADB serial. Private serial/coordinates/raw hierarchy stay in ignored evidence.
Primary helpers: `run_phase.py`, `run_send_observed.py`, `install_and_runtime.py`,
`continue_runtime.py`, `check_failed_persistence.py`, `postrun_phase.py` in the
ignored root. No uninstall, clear-data, phone-wide settings or source edits.

## Separate free-only feedback attempt1: layout still rejected

On the same physical phone, one normal “Update from feedback (free)” Send at
04:57:32UTC produced source2/version2/READY_TO_TEST. The factual feedback described
the observed stacked buttons and0→0 subtraction failure; it contained no source
patch, attached crash/screenshot or developer rescue. The original frozen request
and original rejected row are unchanged. Fresh unauthenticated official catalogue
metadata at04:54:45UTC still reported prompt/completion prices0 for the fixed model.
No paid model fallback or credentials inspection. Billing cost remains unknown.

The new test-only `A33CounterFeedbackProbe` first failed physically at its missing
entrypoint as expected. `format qualityCheck :controller:assembleDebugAndroidTest`
passed twice (each reported1m); no new production changes or JVM-test result claimed.
Exact source/editor revision, original journal/snapshot/installed APK and signer
checks passed; preview cancelled without a call. Missing approval/wrong context/
one-shot duplicate guards passed. Independent efficient-model review found no
must-fix issue; parent checked its guard and output limitations. Normal project/
chat picker selection and original-work terminal waits were used. No response or
source bodies were exported.

Before Send, the original native app passed0→1→2→3/restart3. The version2 update
used the authorized phone-only USB debug installer, retained the same signer and
saved3, and verified exact installed bytes/version. Its first runtime check failed
before taps because Decrement remained above Increment (vertically stacked).
This failed installation/runtime observer remains `installed-runtime-01.json`.
The arithmetic-only observer then hit two test-capture name collisions: first
before any tap, later after partial3→4→3→2 observations and the next tap. Both
failed records remain unchanged; they are observer failures, not model attempts.
A distinct fresh observer from actual1 passed1→2→1→0→-1/restart-1 on the unchanged
version2 APK, with builder processes stopped. It explicitly retains the overall
functional result **rejected** because the layout still fails. No additional Send,
source edit or installation was used to obtain those observations.

Feedback run `run_f91e361a5c304a9cacbd23fd4dfd0533`; approved context
`b557749768667edbfbfc4424a3c69a8b819d8c4535bf0c24465d54e3fc6703b5` (9393 bytes).
Source2 snapshot `ddcf8f31708f4770aeaf148ca139ac15b1cb93dc6f78da61457ca85321a98187`.
Version2 APK16859 bytes/SHA256
`c32327675c53d8e1291c07256f727931e64bfb5c26e46755ab6003a431da042b`;
same signer/package. Phone-only path:
`/data/user/0/dev.smallwright.controller/files/projects/app652c00374f764652a3e4c0e501e3a6dc/artifacts/build_2f7707a3a82940bfba2edef9c9d657eb/app.apk`.
Provider tokens4844, cost unknown, zero compiler repairs; Send-to-settled15216ms.
Exact compiler latency, feedback-attempt memory/storage/thermal measurement remain
unknown. Do not carry the initial request's resource measurements into this attempt.

Current controller.test host APK
`toolchains/alpha-suite-counter01-feedback-20261012/green-controller-debug-androidTest.apk`:
SHA256 `eac1e796cfb04646a92ad24c1e400b2bd5a33fa14310a5d989a5a7977b16f51d`.
Other three installed harness hashes remain as above. Raw contract, source/helper/
installed pins, free-policy metadata, original handle, ready/guard results,
nondefault baseline and all runtime captures are retained under ignored
`toolchains/alpha-suite-counter01-feedback-20261012/`. Key commands:
`run_phase.py --phase preview/guard/send/ready`, `prepare_retained_state.py`,
`install_update_and_runtime.py`, `continue_arithmetic_from_one.py --label 03`,
`verify_prior_selection.py --phase verifySelection`; all with explicit selected
serial. UI automation and debug installer are assistance, not human acceptance
or production consent. Every model/generation handle is terminal.

Fresh prior-selection verification passed05:00:34UTC. The optional old XML
handoff observer subsequently **failed** at SELECT/IllegalStateException before
source/receipt verification; `retained-handoff-01.txt` and its original terminal
handle are preserved. Cause unknown; historical XML passes are not relabelled as
a fresh pass. No rebuild, model call, acceptance tap or install occurred in that
check. A final restoration and installed-artifact check is recorded separately.

The two schema-valid ledger rows both remain **rejected**; one supported request
has been attempted, nineteen A33 requests are pending. Next: diagnose the retained
project observer and obtain a separately recorded layout feedback repair within
remaining approved limits. Full two-device suite, human cohort, licensing and
remaining recovery gates stay open.

Final verification at2026-10-12T05:03:48.234887+00:00: current four harness hashes/source/helper pins matched; original initial ledger/raw failures unchanged; original instrumentation handles terminal and PIDs absent. Version2 phone artifact and installed hash/version matched; historical live-repair v2 and XML v4 installed bytes/version remain unchanged (no fresh XML source/acceptance pass). Prior selection restored after the failed retained observer; worker scratch absent; own harness stopped. Version2 data retained, last actual count-1 at05:00:32UTC. Benchmark validation passed9 rows.

## Changed paths and checkpoint commands

- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`
- `controller/src/androidTest/java/dev/smallwright/controller/A33SuiteCounterProbe.java`
- `controller/src/androidTest/java/dev/smallwright/controller/A33CounterFeedbackProbe.java`
- `controller/src/androidTest/assets/alpha/counter_01.json`
- `benchmarks/results/a33_counter01_20261012_01.jsonl`
- `benchmarks/results/a33_counter01_20261012_01_feedback1.jsonl`
- `docs/validation/suite-counter01-a33-20261012.md`
- `docs/alpha-acceptance.md`
- `docs/STATUS.md` (checkpoint snapshot only; unrelated user tooling tail kept unstaged)

Host invocation: `source scripts/env.sh`, then `./gradlew format qualityCheck
:controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1
'-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain`.
Physical helpers ran through explicit-serial ADB and original terminal handles,
using verified in-place harness test-APK updates. `python3
scripts/validate_benchmarks.py benchmarks/results/*.jsonl` passed9 rows;
`git diff --check` passed. Detailed original command logs are in the ignored roots.
The generated APK remains on the phone; the host-built APK paths above identify
only the allowed harness test packages. This checkpoint has no production change.
