# AAPT2 process policy: physical A33 validation

On 2026-10-12, physical Galaxy A33 SM-A336E (Android16/API36, ARM64, qemu0)
passed the production AAPT2 process guard, real compiler positive/negative cases,
and saved deadline/restart recovery. This completes #26's process-policy scope,
not the private alpha, exact-memory/full-suite or distribution gates.

## Change and boundary

`WorkerProcesses.startAapt2` prepends the packaged sibling native guard to every
production AAPT2 version/compile/link command. The existing fixed shell PID/permit
registration remains; exec preserves that PID. The guard accepts only its own
canonical sibling `libaapt2_exec.so`, sets no-new-privileges, then installs a
seccomp filter before exec. Failure exits without compiler execution/fallback.
Invalid args/target return124, unavailable policy125, failed exec126.

The filter rejects foreign ABI, ptrace and separate-process clone; legacy clone
requires CLONE_THREAD|CLONE_VM|CLONE_SIGHAND and zero high flag bits. clone3 returns
ENOSYS; native x86_64 additionally rejects fork/vfork and x32. Threads remain
allowed and inherit the filter. ECJ/D8 stay in the unfiltered worker JVM. The
filter is a process-count restriction, not a complete sandbox/RSS ceiling or a
replacement for separate UID, caller authentication, no Internet, snapshot,
manifest, signing and immutable-artifact checks.

The [kernel seccomp documentation](https://www.kernel.org/doc/html/latest/userspace-api/seccomp_filter.html)
supports unprivileged installation after no-new-privileges, inheritance and
layering, and requires ABI checks. These API facts do not substitute for runtime
evidence. Android/ABI compatibility beyond this recorded A33 remains untested.

## Actual evidence

Ignored raw root: `toolchains/alpha-child-policy-20261012/`. Private serial and
fixture process IDs remain local. Fresh skill preflight at02:11:55UTC reconfirmed
A33; earlier same-device preflights and all original failures are retained.

| Case | Result / raw evidence |
| --- | --- |
| First test without custom filter | Expected physical RED at ENFORCEMENT; `red-01.txt` |
| First filter + exec | Physical GREEN, threads allowed, fork/process-clone denied; `green-01.txt` |
| Production sibling guard | Version runs; invalid args/wrong target reject; `production-green-01.txt` |
| Review correction | Unfiltered baseline now observes clone3 availability/errno instead of attributing every ENOSYS to this filter |
| New unavailable-policy expectation before native helper | Physical RED at PRODUCTION_LAUNCHER; `failclosed-red-01.txt` |
| Real policy-install denial | Physical GREEN; test child installs outer seccomp denying later filter install, execs actual guard, gets exact exit125/fixed stderr and no AAPT2 version output; `failclosed-green-01.txt` |
| Differential process controls | Unfiltered fork and clone3 available; under filter fork/thread-fork/VM-process-clone/ptrace deny, clone3 ENOSYS, pthread succeeds, exec same PID; same GREEN file |
| Generated resource build | Actual production layout/id compilation + app-only DEX; missing id and malformed XML produce no APK; `generatedResources-01.txt` |
| Production snapshots | Two package-isolated unsigned native Java/XML APKs, missing-main reject, scratch cleanup; `productionSnapshot-01.txt` |
| Preparation failure | Real mkdir obstruction, descriptors closed, zero output, unchanged snapshot, then actual compiler recovery; `scratchPreparationFailure-01.txt` |
| Deadline kill observer | **Host assertion failed**: expected no Android code; actual code0/Process crashed. Raw failure remains `requestDeadlineKill-01.txt`, `production-compiler-checks.json` |
| Original deadline receipt/recovery | No repeated kill: receipt502ms, admission busy until death, original child gone, new worker and real snapshot compile; `original-deadline-receipt.json`, `requestDeadlineRecovery-01.txt`, `deadline-recovery-handle-01.json` |

Successful observers require host0/code-1 and named results. Expected RED requires
host0/code0 and the named assertion. Intentional death has Android code0 with
Process crashed and needs the actual saved deadline/recovery assertions; its
initial host observer mistake is not rewritten as a pass. This deadline uses a
fixed test sleep/stalled compiler, not a new actual ECJ-phase cancellation claim.
No model call, credential inspection, generated installation, data clear or
phone-wide policy change occurred. The refusal filter exists only in its dedicated
test child; the production worker remained able to compile afterward.

## Build, packaging and review

Pinned NDK r28c28.2.13676358, Clang19.0.1, minimumAPI31; authored C builds both
ARM64 and x86_64 PIE executables with 16KiB LOAD alignment, warnings-as-errors,
and dynamic OS libc only. Source/header/consumed C dependencies, compiler wrappers,
clang/ld.lld, NDK revision/notices and outputs are fingerprinted; Gradle also
tracks sysroot include/lib and Clang runtime directories. Generated records:
`worker/build/generated/aapt2-process-guard/launcher-build.json` and test probe
`worker/build/generated/child-policy-probe/probe-build.json`.

Packaged native hashes:
ARM64 `d1971ae9eff8fe946fb3908b875f895748b2c35a158e0ed1f470233f1d0d6152`,
x86_64 `792b4467fc9ec12bc904bf911428a71cf535c243939cabbdbf0f3c1ff0802a6f`.
`production-native-packaging.json`, `native-linkage-verified.json` and both ELF
reports establish actual package contents, alignment and linkage. x86_64 is
build/packaging evidence only; no emulator/runtime pass. Default test APK omits
both opt-in native probes; production omits test natives/depfiles. The frozen
default test APK was not installed. Existing NDK notice assets match this local
NDK byte-for-byte; see [audit](../toolchain-redistribution-audit.md).

Initial integration ran58 fresh worker JVM tests, zero failures/errors, in
`build-integration-quality.log`. Later test-only builds reuse those reports.
Dual ABI/full quality passed; final baseline quality46s, default packaging/resource
case quality62s, and final test cleanup quality41s passed format/lint/assembly.
Changed androidTest lint analysis ran; current controller77/worker2 warnings,
zero errors; no baseline/threshold weakening. Logs are retained under the raw root.

Parent checked source and raw results. Cost-efficient independent focused review
caught the initial missing x86_64 build, incomplete input tracking and clone3
attribution; all addressed. Final review found no concrete bypass; its timeout
cleanup caveat was fixed with explicit child wait. This was focused/static
review, not a whole-branch Standards/Spec review or runtime evidence.

## Commands and handoff

Documentary commands use PHONE_SERIAL from verified private preflight. Do not
replay reserved RED/kill cases or replace existing raw labels.

```sh
source scripts/env.sh
./gradlew spotlessApply spotlessCheck :worker:testDebugUnitTest :worker:assembleDebug :worker:assembleDebugAndroidTest :worker:lintDebug -PkernelResourceTests=true -PchildPolicyTests=true --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 toolchains/alpha-child-policy-20261012/run_probe.py --serial "$PHONE_SERIAL" --label failclosed-green-01 --expect pass
```

Exact final installed APK paths/hashes and source pins are shared with the
[subsequent guarded resource run](guarded-resources-a33-20261012.md); that record
also verifies restoration/cleanup. Sources changed: `worker/build.gradle.kts`,
`WorkerProcesses.java`, `BuildService.java`, `worker/src/main/c/child_process_policy.h`,
`worker/src/main/c/aapt2_guard_exec.c`, `scripts/build_child_process_tools.py`,
`worker/src/androidTest/AndroidManifest.xml`,
`worker/src/androidTest/c/child_policy_probe.c`,
`worker/src/androidTest/java/dev/smallwright/worker/ChildProcessPolicyProbe.java`,
`worker/src/androidTest/java/dev/smallwright/worker/ConfinementInstrumentation.java`.
The Java main paths are under `worker/src/main/java/dev/smallwright/worker/`.
Related controller measurement/docs changes are listed in the resource record.

Next: total retained-storage and longer thermal evaluation, then full fixed suite
and remaining physical/distribution gates. No full toolchain clearance or alpha
pass; opaque AAPT2/API source/license questions remain.
