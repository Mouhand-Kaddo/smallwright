# A33 guarded120-build resource checkpoint

A fresh physical A33 run completed **120 actual native phone builds in
240.021450290s**, passing the predeclared10s first/5s warm,
256MiB combined-builder retained-storage and1GiB reported-kernel-counter targets.
First5519ms, warm1669..3055ms (median1774ms), thermal maximum0. Every retained
APK hash/size matched its real signed receipt/version/source/signer.

This is a materially longer synthetic compiler/resource checkpoint than the
[earlier30 builds](guarded-resources-a33-20261012.md), not the frozen twenty-request
suite, whole alpha, exact physical-memory/transient-storage peak, actual ENOSPC,
spontaneous eviction or sustained-temperature limit. #27 remains open.

## Contract, fixed targets and actual execution

Fresh real-phone-test preflight03:52:15UTC identified physical Galaxy A33
SM-A336E, Android16/API36,ARM64,qemu0, authorized. Awake/unlocked guards preceded
UI phases. Private serial/raw captures stay ignored. Base revision6148517 plus
only the new opt-in test case/observer changes; main harness bytes unchanged.

At03:45:23UTC, before this evaluation, targets were frozen from real
[own-package/cache conformance](package-storage-a33-20261012.md):
256MiB main controller+worker app+data (cache included), existing10s/5s latency,
1GiB approximate reported high-water sum, MODERATE2 stop,120 real builds,
600s sequence bound/900s observer/512process entries/32768samples. No target
relaxation, timeout increase during evaluation, reseed or artificial cooldown.
The larger bounds belong only to this fresh long case; legacy30 limits retained.
The pre-run frozen JSON still says caseNotYetRun:true because it is an archived
pre-evaluation declaration, not the current outcome.

Fixed long-storage-20261012 has separate one-shot preferences/title/root. Actual
normal Build UI stages version/source1..120 from one53,400B Java/XML snapshot
with1000 added methods. Real guarded AAPT2/ECJ/D8, signing, receipt validation
and visible Install readiness ran on phone. Source/hash/signer stayed constant;
versions and source revisions advanced through120. No model, generated install,
source/APK export, private-content/credential read or production policy change.

All original controller/observer handles were followed to terminal/code-1.
Same worker/controller PID and start ticks were verified before/after the
sequence; the first worker was fresh with zero prior reaped-child counters.
Worker observation stopped at after120 gate; controller acknowledged only after
its result arrived; worker release occurred **after** controller terminal.
No observer timeout permitted restart/kill. A failure before after120 may leave
the observer sampling until its own bound before consuming release; that failure
path was not exercised here. No expired-bound/thermal-stop pass is claimed.

Controller package stats were sampled before, at each completed-build checkpoint
outside click-to-ready timing, and after the closed sequence. Worker stats were
before/final only. Their synchronous calls can lag; separate snapshots are not
atomic. API overhead and existing metadata/receipt verification are in recorded
inter-build gaps; no intentional compiler cooldown.

## Measurements and scope

| Metric | Actual observation | Predeclared target |
| --- | --- | --- |
| First click→verified visible readiness |5519ms|10000ms|
|119 warm builds|1669..3055ms, median1774ms|5000ms each|
| Whole sequence|240.021450290s|600s|
| Thermal pressure status|max0; one listener callback0|stop at MODERATE2|
| Combined builder package storage before/final|71.955078125→84.177734375MiB|256MiB retained checkpoints|
| Controller reported VmHWM|189988KiB|component only|
| Worker SELF reported maxRSS|219720KiB|component only|
| Largest waited-child reported maxRSS|219720KiB|component only|
| Sum of reported component maxima|629428KiB=614.67578125MiB|1048576KiB=1GiB|

Kernel RSS high-water counters are asynchronous/imprecise, maxima may occur at
different times and shared pages may be counted twice. Guarded process scope
makes the component sum useful as a reported-counter envelope, **not** exact
simultaneous/page-exact unique RAM, GPU/whole-device use or enforced RSS cap.
See primary [Linux proc accounting](https://www.kernel.org/doc/html/latest/filesystems/proc.html).

Worker10019 observation batches/200process entries; missing child status9,
unavailable child memory7, incomplete batches37, discovery failures21. Missing
samples are explicit, never fake zeros. Maximum sample duration59,815,154ns and
inter-sample gap35,638,923ns. Retained kernel child high-water included exited
children; independent sample high-water/identity checks passed. No claimed
continuous precise instant peak.

Final controller package app9166336/data46735360/cache2207744/total55901696B;
worker app30243328/data2121728/cache2097152/total32365056B. Combined88,266,752B.
Target uses maximum controller retained checkpoint plus maximum worker
before/final, not a simultaneous transient allocation bound. Includes main
package history/code/cache and retained conformance fixtures; excludes other-UID
installed generated apps/test packages, host SDK/toolchains, other volumes/exports
and OS bookkeeping outside those packages. No quota/pruning or eviction claim.
Owned new root du16049KiB is separate filesystem metadata, not total builder use
or exact attribution of aggregate API growth. Cache/data APIs were independently
validated by2MiB conformance; do not add cache twice.

## Evidence, guards and identities

Ignored root: toolchains/alpha-long-resources-20261012/.

| Case | Result | Evidence |
| --- | --- | --- |
| Missing case RED03:52:16UTC | Actual GUARD rejection before implementation; no source/model/build | missing-case-red-01.txt/handle |
| Unknown selector | Expected GUARD | kernel-guard-01.txt/handle |
| Fresh120 builds03:56:36UTC | PASS all predeclared targets/real receipts/identity | kernel-measure-01.txt, kernel-observer-01.txt, original handle JSON |
| All120 retained artifacts | PASS exact on-phone hashes/sizes, same signer/source, uninstalled package | retained-artifacts-01.json |
| New ready reopen | PASS source120/version120 exact receipt/APK/signer | kernel-ready-01.txt/handle |
| Duplicate with same valid observation ID | Expected GUARD/no build121 | duplicate-valid-id-01.txt/handle |
| Legacy guarded30 ready | PASS saved30 source/receipt/signer; no new builds | prior root kernel-ready-02.txt/handle |
| Six recovery neighbors | PASS verification only/no model/build/install | recovery-and-neighbor-results-01.json |
| Prior XML v4 handoff | PASS source/receipt/installed bytes/stored acceptance; selection/chat restored | retained-handoff-01.txt/handle |
| Final04:05:57UTC | Harness/source/original helper pins match, all original handles absent, scratch absent, own harness processes stopped, data retained | final-verification.json |

Six neighbors: workspace/larger-build ready, ECJ Stop, compiler-measurement
recovery, screen-lock recovery and completion ordering. Prior XML acceptance
was earlier test-assisted; no new human acceptance, installer consent or count
measurement. The fresh live-repair v2 and earlier XML v4 installed hashes remained
9885c2cef4aa8b5b44e1b7b43ad9b57f3ee3c447f96cc8632b7f651e9e9578f7 and
283ee00a4796164671c58baf9b5667b00e76ef7f58dac8a6d71ec00f65df7800.
No test resets/deletions. Existing project selection is independent of the owned
ProjectBindingProbeActivity root; retained handoff restored its actual prior
project/chat.

- Root `9c79c6abe553440ab8b3fdc69eaffdc4`, project `app3accc20925214e1fa92b6d2c43baebf7`.
- Final source SHA256`628f7544971ae963d1937536e51acc0ebb9feb47ace0e8da00094695fdcf1631`.
- Final phone APK `/data/user/0/dev.smallwright.controller/files/binding-ui-probe-9c79c6abe553440ab8b3fdc69eaffdc4/projects/app3accc20925214e1fa92b6d2c43baebf7/artifacts/app-v120.apk`,33241B.
- APK SHA256`09579d56a8ffd5fd546f06c6ee977f8c6569a0589de1b0579a65435e18a7b6e8`.
- Per-project public signer SHA256`1adb962447c5c0c3039194d7ea25f9449680a54d29bf6c1b957d314f6ae71e2d`.
- All120 retained APKs stay on phone; new resource app not installed.

| Harness package / durable host APK | Installed SHA256 |
| --- | --- |
| controller: toolchains/alpha-test-handoff-20261012/fixed-controller-debug.apk |45adb0b429c5660c822e1b115fb68161131cbda0bb8f0acaa3e02cded41e658b|
| worker: toolchains/alpha-child-policy-20261012/production-worker-debug.apk |0fc753e43a7d3c4b1ca259ef9f320528bc16320cd74754f8396b4cb6378f39a7|
| controller.test: toolchains/alpha-long-resources-20261012/long-green-controller-debug-androidTest.apk |348a6950a1e8ab5076e9a8bf7cb7c5fca8aa19df66b507bd5f99e493258712df|
| worker.test: toolchains/alpha-long-resources-20261012/long-green-worker-debug-androidTest.apk |b66e17365a3daa395b78f7871bc490dfc9b8a47528ef06f8297df016a70c1901|

Public harness certificate19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25.
Original source/runner pins in tested-source-pins.json remained unchanged;
post-run helpers were pinned separately without rewriting original input pins.
Focused read-only efficient peer review found no concrete must-fix issue;
parent independently checked source, actual runtime, retained identities and limits.

## Commands, changed paths and next task

Completed one-shot commands below are evidence, **not permission to repeat the
measurement or overwrite its labels/IDs**. PHONE_SERIAL is private.

```sh
source scripts/env.sh
./gradlew spotlessApply spotlessCheck -PkernelResourceTests=true -PchildPolicyTests=true :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest :controller:lintDebug :worker:lintDebug --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-long-resources-20261012/install_tests.py --serial "$PHONE_SERIAL" --label long-green --baseline toolchains/alpha-package-storage-20261012/cache-green-installed.json
python3 toolchains/alpha-long-resources-20261012/run_measure.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-long-resources-20261012/verify_retained.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-long-resources-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase ready
python3 toolchains/alpha-long-resources-20261012/run_duplicate.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-guarded-resources-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase ready --label 02
python3 toolchains/alpha-long-resources-20261012/run_retained.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-long-resources-20261012/run_neighbors.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-long-resources-20261012/verify_final.py --serial "$PHONE_SERIAL"
```

Quality passed1m4s/167tasks17executed150cached; zero lint errors/controller77/
worker2 warnings (analyses cached). Actual androidTest Java/D8/package operations
ran. No new JVM suite/production rebuild; syntax checks for actual host runners
passed. In-place test updates preserved both opt-in JNI probes, data, main APKs
and public certificate. No dependency downloads/global configuration/emulator.

Changed tracked paths: controller/src/androidTest/java/dev/smallwright/controller/BuildResourcesProbe.java,
worker/src/androidTest/java/dev/smallwright/worker/BuildResourceObserver.java,
worker/src/androidTest/java/dev/smallwright/worker/ConfinementInstrumentation.java,
docs/validation/long-resources-a33-20261012.md, docs/STATUS.md, docs/alpha-acceptance.md.
User skill/research files and STATUS tooling tail remain unstaged.

Next: carry frozen caps and honest scopes into the actual supported-request
suite, retain failed/unknown attempts in its denominator, and continue source/API
licensing and remaining low-storage/OS-eviction/cross-device/S23/full-alpha gates.
#27 stays open; this synthetic sequence contributes no full-suite rows.
