# Retained kernel resource readings during thirty A33 builds

On 2026-10-11 UTC / 2026-10-12 Dubai, actual kernel counters retained an exited
child's high-water and agreed with worker `/proc` high-water. A new isolated
thirty-build case then passed unchanged timing/thermal bounds, receipts and
independent retained-APK checks. This improves the earlier sampler's missed-child
coverage. It does **not** establish an exact simultaneous pipeline peak, a whole
pipeline memory cap, long-term retention/thermal limits or full alpha acceptance.
The earlier [thirty-build case](sustained-resources-a33-20261011.md) is separate.

## Contract and physical results

Fresh preflight identified Samsung Galaxy A33 5G SM-A336E, Android16/API36,
ARM64, qemu0 at `2026-10-11T23:00:29.756088+00:00`. Private serial stays in ignored
records; each ADB command targets that serial. The installed production
controller/worker were retained; only verified test APKs were updated in place.
Generated source/APK bytes stayed on the phone. No model request, generated
installation, personal-screen capture or production compiler/provider hook ran.

The original test-only JNI interface failed on the phone with the library absent:
`NATIVE_READ / UnsatisfiedLinkError`, host0/Android code0. Implementing the minimal
reader with existing pinned NDK **28.2.13676358** produced ARM64/minimum API31
ELF, all three LOAD alignments16KiB, dynamic dependencies libc/libdl only.
Native library SHA256 `074f99ace730091c31cc49123656446fbf95a7ca946d1c0c3bcea44f007de79e`,
5,832 bytes. Source/header/NDK notice hashes are retained in `green-native-build.json`;
[reviewed semantics](../research-kernel-resource-accounting-20261012.md) explain scope.
Syscall failure throws rather than returning zero. No native reader ships in the
production worker; packaging into worker.test requires `-PkernelResourceTests=true`.
Default test packaging was independently built and contained no native library
or native build-task dependency, preserving the existing default test route.

Standalone GREEN01 really waited for `/system/bin/true`: initial child counters0,
reaped child RSS90,052KiB, child system CPU12,921us, SELF91,840KiB, independently
bracketed `/proc` HWM91,840/91,840KiB. GREEN02 on the integrated observer APK also
passed: child90,292KiB/system9,997us; SELF and bracket92,084KiB. Neither is a
compiler measurement. Both original handles ended host0/code-1 with named PASS.

The existing physical observer output failed the new retained-counter contract
with missing `kernelAccounting` before integration. The opt-in observer then
reported actual before/after counters during normal phone compilation:

| Measurement | Recorded value / scope |
| --- | --- |
| Thirty actual native builds | source1..30/version1..30; fixed synthetic53,400-byte/1,000-method input |
| First visible-ready build | 6403ms; cap10,000ms |
| Largest warm visible-ready build | 3113ms; cap5,000ms |
| Build sequence | 66832ms; bound180s |
| Thermal | before/after/callback maximum0; no artificial cooldown; stop threshold MODERATE2 |
| Controller recorded HWM | 192,200KiB through ready-build readings |
| Worker kernel lifetime HWM | 218,744KiB |
| Largest waited-for child lifetime HWM | 218,744KiB; retained after reaping |
| Sum of those individual maxima | 629,688KiB = 614.930MiB; conditional envelope, not simultaneous peak |
| Largest sampled direct-child HWM | 9,668KiB; sampling can miss short-lived peaks |
| Sampler | 2834 batches / 47 identities; unavailable child-memory2, missing child-status2, discovery failures3 |
| Available batch RSS maximum | 218,504KiB; unavailable values omitted, not zero |
| Reaped-child CPU | user997235us / system984759us; accumulated, not wall time |
| Owned workspace after build30 | logical2,649,145bytes / allocated4,250,624bytes; synthetic fixture only |

All native readings bracketed the observation window and exceeded/equalled their
corresponding available sampled process HWM. Initial waited-child RSS/CPU were0
in a fresh worker. New kernel counters complement sampling; unavailable samples
remain unavailable. The child lifetime includes launcher/fork stages and shared
pages; the child value is not an exclusive AAPT2 allocation measurement. The
summed envelope assumes serial native children and no unaccounted processes.
No exact concurrent tree/unique physical/GPU/whole-device memory claim is made.

Own worker cgroup membership was inspected read-only. Its process group had no
`memory.current`, `memory.peak`, `memory.max` or `memory.events` files, through
both run-as and ADB shell. Shell could read its `cgroup.procs` containing the
worker; run-as was denied that file. These metrics remain unavailable. No cgroup
configuration, limit, counter reset, root access or phone setting was changed.

## Evidence, artifacts and retained failures

Ignored evidence root: `toolchains/alpha-kernel-resources-20261012/`.

- `red-01.txt`/handle: real missing-library RED; reservation preserved.
- `green-01.txt`, `green-02.txt`/handles: actual reaped-child/SELF tests.
- `observation-contract-red.json`, `cgroup-observer-04.txt`: existing physical
  output lacked retained counters; host contract failed before integration.
- `kernel-measure-01.txt`, `kernel-observer-01.txt`, `kernel-measure-handle-01.json`:
  actual thirty builds/counters; both original handles host0/code-1, named PASS,
  controller terminal before observer release. No restart from observation timeouts.
- `retained-artifacts-01.json`: independent on-phone SHA256/size checks of all30
  versions; package absent, no generated installation/download.
- `kernel-recovery-and-neighbor-results-01.json`: nine named passes, including
  fresh kernel source30/version30 readiness, expected duplicate-measure GUARD,
  previous sustained case and six existing ready/completion neighbors.
- `native-guards-01.json`: malformed ID and original RED reservation reuse both
  rejected at GUARD before native query/child launch.
- `default-packaging-and-quality-01.json`: opt-in boundaries and lint counts.

The first cgroup inspection used a nonexistent `resourceObserver` selector and
failed Unknown test mode before READY. Corrected independent cases02..04 used
the existing `buildResourceObserver`; original failure retained. The first ELF
host check assumed GNU-style output while llvm-readelf defaulted to structured
output. Explicit GNU output fixed the verifier; original output retained. The
package/signer/hash installer had verified identity before updating that test APK,
and corrected ELF checks completed before the GREEN probe.

Native guards first refused a still-live worker left by our ready neighbors,
before launching instrumentation. Its PID/start identity matched the owned ready
window; all original neighbor handles were terminal/absent, controller absent,
services and active scratch absent. Only that idle worker was stopped, data
preserved, before fresh guard tests. No active build was killed for a timeout.

| Exact test APK retained in ignored evidence | SHA256 | Bytes |
| --- | --- | --- |
| `toolchains/alpha-kernel-resources-20261012/observer-worker-debug-androidTest.apk` | `6609bf97be45c247d9f8c359468a10349db0da7aab8992ea934773828c894f09` | 195330 |
| `toolchains/alpha-kernel-resources-20261012/observer-controller-debug-androidTest.apk` | `229fc4d3a7b23fc40c9acb06c3dc5605c5730252c67e0d380bdacecce010a222` | 684454 |

Production controller stayed SHA256
`f3c138eec7d55e8745cb441b7c43c77c5a74a5a193b2210f69c2d2fa73e0af83`;
worker stayed `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850`.
Public test signing certificate SHA256
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
The default, uninstalled worker.test APK is separately retained as
`default-worker-debug-androidTest.apk`; never confuse it with the native-enabled
measured APK. Actual tested source hashes are in `observer-preparation.json`.

Generated final version30 APK remains on phone:
`/data/user/0/dev.smallwright.controller/files/binding-ui-probe-ee54825034b44de7acfba35c1052f098/projects/app9a93e4953a994dbbadb1c9f0ff4caccb/artifacts/app-v30.apk`.
SHA256 `dbc7bc17e84d6e51070b39caace554ff6757ca1e3c2847231e620aed636e8ee6`,
33,242bytes; project public signer SHA256
`3c88cd992f868b8485afb85ea8518c413a7714cf19f7ca58444f44b1691da39c`. All30 retained artifact hashes/sizes and per-build
source/package/version/real signature/receipt/project signer checks matched.
Independent owned-root allocation after reopen was4151KiB.
These storage/thermal observations do not establish long-term budgets.

## Changed paths, commands and remaining work

Only test fixture/build support and documentation changed; the Gradle file's
existing sections were reformatted by the required formatter, without changing
their compiler/signing/experimental-selection behavior.

- `scripts/build_kernel_usage_probe.py`.
- `worker/build.gradle.kts`.
- `worker/src/androidTest/c/kernel_usage_probe.c`.
- `worker/src/androidTest/java/dev/smallwright/worker/KernelResourceUsageProbe.java`.
- `worker/src/androidTest/java/dev/smallwright/worker/BuildResourceObserver.java`.
- `worker/src/androidTest/java/dev/smallwright/worker/ConfinementInstrumentation.java`.
- `controller/src/androidTest/java/dev/smallwright/controller/BuildResourcesProbe.java`.
- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`.
- `docs/research-kernel-resource-accounting-20261012.md`.
- `docs/validation/kernel-resources-a33-20261012.md`.
- `docs/STATUS.md`.

Build commands ran in
`toolchains/worktrees/alpha-kernel-resources-20261012`, using the local JDK/SDK.
Existing NDK notices remain with the SDK and are recorded in the toolchain audit.

```sh
source scripts/env.sh
./gradlew spotlessApply :worker:assembleDebugAndroidTest :controller:assembleDebugAndroidTest -PkernelResourceTests=true --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew spotlessCheck :controller:lintDebug :worker:lintDebug -PkernelResourceTests=true --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-kernel-resources-20261012/run_measure.py --serial "$PHONE_SERIAL" --label 01
python3 toolchains/alpha-kernel-resources-20261012/check_kernel_observation.py toolchains/alpha-kernel-resources-20261012/kernel-observer-01.txt
python3 toolchains/alpha-kernel-resources-20261012/run_neighbors.py --serial "$PHONE_SERIAL" --label 01
python3 toolchains/alpha-kernel-resources-20261012/verify_retained.py --serial "$PHONE_SERIAL"
```

Commands describe reserved original cases; do not repeat/reseed them. Original
RED build passed47s, native GREEN build41s, integrated observer build82s.
Formatting/lint passed116s, zero errors / controller72 and worker2 warnings,
no baseline/threshold change. Default packaging passed23s without native producer.
Logs are `toolchains/alpha-kernel-resource-build-*.log`,
`alpha-kernel-resource-quality-01.log` and `alpha-kernel-resource-default-packaging-01.log`.
No new JVM, emulator or full benchmark-suite run is claimed.

Original data, fixtures and failures remain intact. Main project selection was
isolated through ProjectBindingProbeActivity's owned context, not reset. Current
source30/artifacts remain ready. Broader whole-pipeline memory/retention/longer
thermal limits, full fixed suite on both phones, recovery/distribution gates and
S23 frozen Step remain in [status](../STATUS.md). This checkpoint closes only the
retained-kernel-counter task #23, not the alpha goal.


## Combined integration handoff

After local integration into the branch containing #21/#22, root formatting,
controller/worker lint and both test assemblies passed in101s (162 tasks,
39 executed/123 up-to-date). Log:
`toolchains/alpha-kernel-resource-root-integration-01.log`. A subsequent
`1c7039d` documentation-only checkpoint did not change compiled source.
Production APKs were not updated in this case. Combined test APKs preserve
the later layout tests and earlier live-repair case selector alongside #23.

| Final verified/installed test APK | SHA256 | Bytes |
| --- | --- | --- |
| `toolchains/alpha-kernel-resources-20261012/root-controller-debug-androidTest.apk` | `f962eb03a8e7f18bcd3437d7cc0033d442beb59f0d8d78a55c09c88cb37754ac` | 1143558 |
| `toolchains/alpha-kernel-resources-20261012/root-worker-debug-androidTest.apk` | `fa1ac79e0676bcaca6df8f639f0f80ff7278afa4d3b88f7101406a5afb8462a5` | 159630 |

Fresh GREEN03 on this pair passed actual child usage, SELF91,988KiB against
both `/proc` bracket readings91,988KiB. Fresh kernel ready reopen retained the
same original source30/version30/artifact; duplicate measure rejected at GUARD.
These are recovery/counter observations, not a new compiler performance run.
Evidence: `green-03.txt`/handle, `kernel-ready-02.txt`/handle,
`kernel-guard-02.txt`/handle and `root-final-verification.json`.

Final check at `2026-10-11T23:55:52.175076+00:00` matched all four installed hashes,
confirmed original handles terminal/absent and active scratch absent. The idle
worker left by this run's ready reopen was stopped after controller completion;
controller/worker were absent. App data and owned projects/artifacts remain.
The A33 was released from this case; a future caller must still recheck connection,
current APK hashes and active work before a new test. #21/#22's earlier installer
refusal is historical; use this fresh baseline for the pending XML acceptance.
