# Guarded thirty-build A33 resource sequence

A fresh physical A33 sequence on2026-10-12 completed30 real phone-local native
builds/signatures/receipts in67.967s. Every build passed unchanged10s first/5s
warm caps. Kernel-reported per-process high-water counters sum612.574MiB, below
a fixed pre-run1GiB target; thermal status/listener remained0. This is bounded
synthetic-workload evidence, not a twenty-request-suite, page-exact memory,
total retained-storage or longer sustained-thermal pass.

## Scope, budgets and precision

The [AAPT2 guard](child-process-policy-a33-20261012.md) now prevents AAPT2 from
creating separate processes, while allowing threads. Production version/compile/
link all use it; ECJ/D8 remain in the worker, and the existing tracked-child
permit/lock allows one AAPT2 process at a time. Controller and worker have distinct
UIDs. This addresses the previous unbounded opaque-tool-descendant assumption.
It does not turn per-process counters into a simultaneous physical-memory scan.

The one-shot `guarded-20261012` resource case has new prefs/title/owned Context
root. It uses1000 synthetic extra Java methods (53,400 source bytes),30 sequential
normal Build clicks, a real paired worker observer, existing latency caps,
thermal stop thresholdMODERATE2 and180s sequence bound. Before launching, the
ignored `measurement-contract.json` fixed the reported-counter sum target at
1,048,576KiB (1GiB), with headroom over the earlier conditional629,688KiB spike.
No cap was raised after measurement. These are working limits; no percentiles,
OS-cache-cold, enforced RSS ceiling or full-suite acceptance is claimed.

The controller records lifetime VmHWM after the sequence and worker window close;
worker JNI records RUSAGE_SELF and CHILDREN high-water after all child waits.
CHILDREN is the largest waited-child value, not a sum. With this fixed process
scope, summing the three component maxima is a conservative envelope of the
**reported** RSS counters. Component maxima can occur at different times and
shared pages can be double-counted. The primary
[kernel proc documentation](https://www.kernel.org/doc/html/latest/filesystems/proc.html)
also says RSS accounting is asynchronous/imprecise. Therefore this does **not**
prove an upper bound on page-exact physical RAM or close exact-memory/full-suite
gates. Precise smaps snapshots would still not establish a continuous peak.
The original runner's stronger “upper bound” wording remains in its raw record;
`measurement-interpretation.json` captures this precision correction without
altering the run or its numerical results. Old unguarded envelopes remain separate.

## Actual observations

Ignored root: `toolchains/alpha-guarded-resources-20261012/`. Fresh skill preflight
confirmed A33 SM-A336E, Android16/API36 ARM64, qemu0; private serial stays local.
No cloud request/credential access, generated APK installation, data clear,
reseed, developer repair or source/APK download.

| Measurement / case | Actual result |
| --- | --- |
| First Build click to visible verified Install | 6632ms; new worker/project signer, not OS-cache-cold |
| Warm builds2..30 | min1759ms, max3059ms, median1989ms |
| Sequence | 67.966687644s; all30 real source1..30/version1..30 artifacts verified |
| Controller late VmHWM | 190268KiB |
| Worker SELF maxrss | 218504KiB |
| Largest waited child maxrss | 218504KiB |
| Reported counter sum / pre-run target | 627276KiB =612.57421875MiB /1024MiB; PASS |
| Thermal pressure | max0; one listener callback0; MODERATE2 stop not reached |
| Sampling | 2903batches,53process entries; child status vanished4, memory unavailable1, incomplete batches10; no fabricated zeros |
| Final owned root storage | allocated4151KiB; project-only measurement, not a total retention cap |
| Retained artifacts | All30 phone hashes/sizes matched; same source hash and project signer |
| Fresh ready reopen | Exact source30/version30 APK/receipt/signer, no rebuild |
| Unknown selector and duplicate measure | Expected GUARD rejections, no new build |
| Original unguarded kernel fixture | Ready source30/version30 retained, no rebuild |
| Prior XML v4 | Exact installed source/receipt/artifact and saved acceptance retained; previous project/chat restored |

Controller and observer original handles returned host0/code-1; the observer was
released only after controller terminal. Positive ready/retained checks require
named assertions. Expected guards returned host0/code0/named GUARD failure.
Scope IDs/process start ticks and monotonic brackets correlate both sides of the
same real sequence. Raw `kernel-measure-01.txt`, `kernel-observer-01.txt`,
`kernel-measure-handle-01.json`, `retained-artifacts-01.json`, ready/guard/legacy
files, `retained-handoff-01.txt` and `final-verification.json` preserve evidence.
Native runtime interaction/count was not remeasured in this resource run.

## Exact artifact and tested code

Base `916a2f5` plus the process-policy and controller test changes listed below.
One new controller.test was installed in place after baseline/certificate/package
verification; production APKs and native-enabled worker.test stayed unchanged
through measurement. Public harness certificate:
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.

| Package / retained host APK | Final installed SHA256 |
| --- | --- |
| controller: `toolchains/alpha-test-handoff-20261012/fixed-controller-debug.apk` | `45adb0b429c5660c822e1b115fb68161131cbda0bb8f0acaa3e02cded41e658b` |
| worker: `toolchains/alpha-child-policy-20261012/production-worker-debug.apk` | `0fc753e43a7d3c4b1ca259ef9f320528bc16320cd74754f8396b4cb6378f39a7` |
| controller.test: `toolchains/alpha-guarded-resources-20261012/controller-debug-androidTest.apk` | `30461548d26205e6ca289184f9ccc397eb190fb997f9cf2b19bb223de2561acf` |
| worker.test: `toolchains/alpha-child-policy-20261012/failclosed-green-worker-debug-androidTest.apk` | `f0c9a726f5b32eb15789f8d6b9fdb9ee1526b00037831850880d7cf931e6ce37` |

- Owned root: `a640142d88bb4480befdb769f04a08df`.
- Project: `app8bbbf761160e4a1297fd4b5fb3a1a843`.
- Final phone APK: `/data/user/0/dev.smallwright.controller/files/binding-ui-probe-a640142d88bb4480befdb769f04a08df/projects/app8bbbf761160e4a1297fd4b5fb3a1a843/artifacts/app-v30.apk`.
- Final SHA256: `962afd1bf8aa97d301f892233f0409e27215afdbb7edb6c7b73dbe6ad49187c3`.
- Project public signer: `45055ba057d33ea4070f45c08873bfad5069e27e363bc48f1eef4d7cd4039873`.
- Prior XML installed v4 SHA256 unchanged: `283ee00a4796164671c58baf9b5667b00e76ef7f58dac8a6d71ec00f65df7800`.

Source/helper/contract hashes were pinned before measurement in
`tested-source-pins.json`; final verification checks them unchanged. Artifacts
remain entirely phone-local; only hashes/sizes/safe numeric metadata were read.

## Commands and handoff

Use verified PHONE_SERIAL. These document the reserved sequence; never repeat
measure on this case or overwrite its existing labels.

```sh
source scripts/env.sh
./gradlew spotlessApply spotlessCheck :controller:assembleDebugAndroidTest :controller:lintDebug :worker:assembleDebug :worker:assembleDebugAndroidTest :worker:lintDebug --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-guarded-resources-20261012/install_test.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-guarded-resources-20261012/run_measure.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-guarded-resources-20261012/verify_retained.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-guarded-resources-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase ready
python3 toolchains/alpha-guarded-resources-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase guard --label 02
python3 toolchains/alpha-guarded-resources-20261012/run_legacy.py --serial "$PHONE_SERIAL" --phase ready
python3 toolchains/alpha-guarded-resources-20261012/run_retained.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-guarded-resources-20261012/verify_final.py --serial "$PHONE_SERIAL"
```

Default production/test packaging quality passed62s; its default worker.test was
frozen/inspected and **not installed**, so physical JNI probes remain available.
The earlier/later opt-in builds and58 worker cases are in the policy record;
zero lint errors, controller77/worker2 warnings. No new controller JVM or full
benchmark suite was run for the test-only resource selector. Parent and focused
independent review checked routing, timing and reported-memory scope.

Additional changed paths: `controller/src/androidTest/java/dev/smallwright/controller/BuildResourcesProbe.java`,
`controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`,
this record, `docs/validation/child-process-policy-a33-20261012.md`,
`docs/STATUS.md`, `docs/alpha-acceptance.md`, `docs/toolchain-redistribution-audit.md`.
Policy source/build/test paths are listed in the linked policy record. User-owned
skill/research changes remain separate.

Final verification at02:43:24UTC matched all pins. Cleanup stops only owned harness processes after all original handles are
terminal, checks empty active-build scratch and all installed/source pins, retains
new and previous projects/data/credentials, and confirms the new generated package
was not installed. Next: own-package total retained-storage observations/limits,
longer thermal sequence, then fixed-suite and remaining physical/source-license
gates. #18 remains closed for its historical bounded thirty-build observation.
Remaining resource acceptance is tracked in #27; the overall alpha remains open.
