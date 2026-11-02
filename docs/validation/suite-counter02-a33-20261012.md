# A33 Reset request: two actual compiler failures

The frozen `counter_02` request asks for Reset with confirmation. Its initial
normal $0-only Send saved source4 but failed compilation. One normal
compiler-error repair later saved source5 and also failed compilation.
No generated APK installation or Reset functional acceptance is claimed.
The previous accepted counter_01 version3 remains installed; all failed
snapshots, journals, diagnostics and original observer records remain intact.

## Actual observations

| Attempt | Observed outcome |
| --- | --- |
| Initial request | source3→4; REPAIR_NEEDED, failed-build count1; 5293 reported tokens; Send-to-settled34533ms |
| First repair | source4→5; REPAIR_NEEDED, failed-build count2, journal sequence9; 6483 additional reported tokens/11776 cumulative; Send-to-settled42540ms |

Provider cost, exact compiler-only duration, memory/thermal/storage for these
requests remain unknown. The actual catalogue confirmed prompt/completion
pricing0 for `cohere/north-mini-code:free`; no paid fallback or developer edit
of generated source occurred. Context hashes are in the schema-validated ledger
rows. The original initial observer reported the real outcome, then failed its
artifact-field assertion; its host0/Android code0 is preserved, not promoted
to an overall passing observer.

Fresh physical preflight at08:21UTC identified SM-A336E Android16/API36 ARM64,
qemu0 and awake/unlocked. The original Send handle was terminal and absent;
idle harness PID identities matched before only the owned idle harness was
stopped. Exact production/worker.test APKs were retained. Only verified
controller.test was updated in place, public signer
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`,
SHA256 `9ab84f08fe13590819a8394236940e675da705a52496c0d7f75c6972b5bd1972`.
Host assembly/lint/format evidence is the existing real successful
`repair-quality-03.log` (1m1s, 138 tasks/13 executed); no new host unit execution
is inferred from cached tasks or these Android fixture changes.

The new helper inspected the actual source4 failure/origin/diagnostics:
current-revision receipt absent, shown artifact null and Install disabled.
It previewed then cancelled the exact11987-byte repair context. Missing
approval and wrong-context attempts were rejected at GUARD before mutation
or model call. The single approved repair used that exact context. All those
original handles were terminal host0; success phases required Android code-1,
expected guards code0/named GUARD. Fresh `ready` retained the actual source5
failed outcome without another request/build. The missing `inspect2` phase
was a real GUARD RED for preparing the next bounded repair helper.

## Preserved saved-count precondition failure

The earlier recorded installed-v3 count was-2. A fresh native baseline check
at08:30UTC verified exact v3 APK, then displayed-4 and failed its expected-2
assertion. No tap or count reset occurred in this check. The cause of the
different saved value is unknown. The parent started the repair before
inspecting that check's failed result; this sequencing mistake is recorded.
That approved source repair does not establish data-retention acceptance.
Keep the failed baseline unchanged. Any eventual update/runtime attempt must
declare and independently observe its current baseline before installation.

## Exact changed paths and evidence

The continuing test fixture changes are
`controller/src/androidTest/java/dev/smallwright/controller/A33CounterFeedbackProbe.java`,
`A33ResetRepairProbe.java`, `ArtifactPolicyInstrumentation.java` in that same
directory, and `controller/src/androidTest/assets/alpha/counter_02.json`.
They add bounded normal-UI test routes, not generated-app source, provider
policy or production compiler changes. The two actual ledger files are
`benchmarks/results/a33_counter02_20261012_01.jsonl` and
`a33_counter02_20261012_01_repair1.jsonl` in that directory. This validation
record and the concise status snapshot describe the remaining work.

Ignored original evidence is `toolchains/alpha-suite-counter02-20261012/`:
initial RED/GREEN host logs, guards/preview/Send handles, reported initial
failure and retained-v3 baselines. Goal continuation evidence is
`toolchains/alpha-goal-20261012/counter02-repair/`: frozen source/public APK
pins, fresh catalogue, verified installation, inspect/preview/guards/Send/ready,
missing-phase RED and `preserved-precondition-failure.json`. Serial and raw
test UI remain local. APKs/generated source remain on phone.

```sh
python3 toolchains/alpha-goal-20261012/counter02-repair/run_repair.py --phase inspect --label actual-01
python3 toolchains/alpha-goal-20261012/counter02-repair/run_repair.py --phase preview --label actual-01
python3 toolchains/alpha-goal-20261012/counter02-repair/run_repair.py --phase guard --label missing-approval
python3 toolchains/alpha-goal-20261012/counter02-repair/run_repair.py --phase guard --label wrong-context --context-sha256 0000000000000000000000000000000000000000000000000000000000000000
python3 toolchains/alpha-goal-20261012/counter02-repair/run_repair.py --phase send --label actual-01 --context-sha256 c7d0d71feebf269bd28ab55f41d2644e28ec03a9718f4463d7034b40aa08c98e
python3 toolchains/alpha-goal-20261012/counter02-repair/run_repair.py --phase ready --label retained-failure-01
python3 scripts/validate_benchmarks.py benchmarks/results/a33_counter02_20261012_01.jsonl benchmarks/results/a33_counter02_20261012_01_repair1.jsonl
```

Commands describe reserved original observations, not repeatable fresh sends.
The helper targets the private serial from its verified local preflight and
refuses stale package/source pins or existing record paths. Both ledger rows
validated. This checkpoint is not a functional, human, installation-consent
or alpha pass. Next: implement and independently verify the next exact failed
revision/origin repair route, retain the existing cap, and observe actual
native Reset/Cancel/Confirm/persistence only if a verified artifact exists.


## Second exact repair fixture: host checkpoint

The single existing `A33ResetRepairProbe.java` now accepts `inspect2`,
`preview2`, `send2`, and `ready2`, while preserving the original phase result
names. The operations share the original probe; second-repair pins use
`a33-counter02-repair2-20261012`, separate from the untouched repair1 pins.
The second route requires repair1's actual saved REPAIR_NEEDED/source5/
failed-build-count2 outcome, original project/package/run identity,
journal sequence9 and cumulative reported tokens11776. It verifies the
current source5 snapshot, exact saved origin and actual compiler diagnostics
before preview/send. The original route retains initial source4/count1 and
its original token base5293; the second route subtracts11776 rather than
repair1's additional6483 from later cumulative reported usage.

Exact fresh context approval, one-shot attempted pins, cancelled preview,
current-revision receipt rejection, installed-v3 APK pin, stored originating
request/chat and verified-success signer checks remain in place. A verified
second success must be source6/version6; other settled states remain actual
reported outcomes. No generated source, provider/model policy, repair cap,
caller/manifest/header/Gradle inputs or production code changed. This fixture
neither resets the native saved count nor repairs generated code itself.

The parent retained the real physical RED before this change:
`toolchains/alpha-goal-20261012/counter02-repair/inspect2-missing-phase-red.txt`
reported `FAILED stage=GUARD category=AssertionError`, Android code0 and
terminal host0. No unit failure or additional physical run is claimed by this
host checkpoint. Physical inspect2 GREEN, any newly approved normal repair,
installation and Reset runtime checklist remain pending. The preserved-2 vs
observed-4 saved-count failure above remains unresolved.


Host verification in the isolated `feature/alpha-reset-repair-goal-20261012`
worktree used owned JDK21.0.12.1+1/SDK/cache/signing directory paths, with no
signing content inspected. Formatting passed in27s (6 tasks executed).
The first quality/assembly attempt failed in1m19s at
`worker:verifyCompilerInputs` because the worktree lacked ignored compiler
JAR inputs; `repair2-quality-01.log` preserves that real setup failure.
Linking the owned compiler-input directory allowed the unchanged tracked
checksum verifier to pass. The repeated command passed in1m28s
(135 tasks,60 executed/75 up-to-date): zero lint errors, controller77,
worker2 and protocol0 warnings. Android test Java compilation and APK
assembly ran. No JVM test invocation or physical GREEN is claimed.

```sh
source scripts/env.sh
./gradlew format --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew qualityCheck :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

Ignored host logs and the copied harness test APK remain in
`toolchains/worktrees/alpha-reset-repair-goal-20261012/toolchains/alpha-reset-repair-goal-20261012/`:
`repair2-format-01.log`, `repair2-quality-01.log`, `repair2-quality-02.log`,
`repair2-apk-public-signature.txt`, `repair2-apk-badging.txt`, and
`repair2-controller-debug-androidTest.apk`.
`apksigner verify --verbose --print-certs` passed (v2, one signer); `aapt2 dump badging` confirmed `dev.smallwright.controller.test`, min31/target36.
The helper source SHA256 is
`46f331027144821119cbf488bd1fc0eb202748298bd9d958649402b03cc97157`;
the test APK SHA256 is
`59099dc733c1cb6832a1d77f8f0eba9a29d5cdf34a15acc7533c42793f740eff`;
the public signer certificate SHA256 is
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
The branch started at exact integration `dcc898c` and merged its latest
`36de5cf` tip (documentation changes only) before handoff. The host build
slot is released. Preserve/backup the ignored logs and APK before any worktree
cleanup. Next: parent independently checks these pins and performs actual
inspect2 GREEN and fresh exact-context approval on the unchanged phone case.


## Final bounded repair fixture: host checkpoint

The same probe now explicitly supports `inspect3`, `preview3`, `send3` and
`ready3`. Its bounded attempt selector admits only the original twelve phase
names across repairs1/2/3; repair3 pins use
`a33-counter02-repair3-20261012` without changing prior saved pins. Repair3
requires repair2's actual saved source6/REPAIR_NEEDED/count3 outcome,
sequence13, cumulative reported tokens18095, runId suffix`_repair2`, and
snapshot `865d1cc5b13c743b8654e981344ff966f441ecd0e5779f2760c10a31d7c36557`.
The original project/package/run/request/chat, current full source, verified
origin/diagnostics, receipt, installed-v3 baseline and exact fresh-context
approval checks still apply. Earlier routes retain their original inputs,
including repair2's sequence9/token base11776. Repair3 subtracts18095 from
later cumulative usage and requires source7/version7 only on verified success;
other settled states remain actual reported outcomes. The fixture does not
change the production repair/attempt/token limits or generated source.

The parent retained the real physical phase3 RED before this extension in
`toolchains/alpha-goal-20261012/counter02-repair2/inspect3-missing-phase-red.txt`:
`FAILED stage=GUARD category=AssertionError`, Android code0/terminal host0.
This host slice claims no new phone/model execution or physical GREEN. The
parent owns the separate actual repair2 row and subsequent phone acceptance.


Formatting passed in21s (6 tasks,2 executed/4 up-to-date). The capped offline
controller verification below passed in1m1s (102 tasks,9 executed/93
up-to-date), including the changed Android test compilation and test-source
lint analysis. Controller lint reported zero errors/77 warnings. No JVM tests
were invoked; unchanged worker quality was not repeated. Earlier failed host
setup logs remain preserved. The worktree merged current integration
`aef7003` (actual repair2 evidence/ledger only) before this checkpoint.

```sh
source scripts/env.sh
./gradlew format --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew spotlessCheck :controller:lintDebug :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

New logs and APK remain beside the repair2 artifacts in the same ignored
worktree evidence directory: `repair3-format-01.log`,
`repair3-quality-01.log`, `repair3-apk-public-signature.txt`,
`repair3-apk-badging.txt`, and `repair3-controller-debug-androidTest.apk`.
Public APK signature verification passed with one signer; badging confirms
`dev.smallwright.controller.test`, min31/target36. Helper source SHA256:
`23d4c3debb13b8e8dd727b039885017b81dc072918bd284f8dd3467b001746ab`.
Test APK SHA256:
`a77dc16de6ccf881dd5cff7b52a522ffd6ab0a18fc6459cd5d3e4d07ae9c9796`.
Public signer certificate SHA256 remains
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
The host slot is released; artifacts/worktree remain for parent backup and
review. Next: independently verify these pins and actual inspect3 GREEN,
then preview/cancel/guards and one freshly approved final normal repair.
