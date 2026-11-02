# Stop during actual ECJ compilation — A33, 2026-10-11

Physical Galaxy A33 Stop during observed real ECJ compilation passed, followed
by saved-source restart, a separate fresh Build and exact ready restart.
The late real signed APK never published a receipt or Install/export readiness.
This establishes cancellation of controller readiness during ECJ execution;
it does not establish immediate compiler interruption. The worker completed.

Issue: #13.
Base local revision: `f1f7ed4`. Production code/dependencies are unchanged.
Raw source pins, artifact records, build logs, preflight, original handle records
and numeric stack observations remain ignored under
`toolchains/alpha-ecj-stop-20261011/`.

## Contract and assistance

The approved local A3/Stop path uses actual native New project, source editor,
Build and visible Stop controls in an isolated one-shot project. Synthetic valid
Java adds 1000 small integer methods to the default native source, staying below
the existing 64KiB controller bound. No compiler output, APK or receipt is faked.
Source/compiler/build/signing/output stay on phone and cross actual authenticated
separate-UID Binder. No model, existing credential inspection, generated
installation/export/pull, data reset or paid request occurs.

Worker-test instrumentation observes its actual worker JVM. It recognizes one
thread with both `org.eclipse.jdt.internal.compiler.Compiler.compile` and
`dev.smallwright.worker.BuildService.compileJavaOrThrow` stack frames. Only
numeric sample call start/end elapsedRealtimeNanos, process/UID/thread identity
and counts are reported; full stacks/source/arguments are never exported.
Each later sample examines that same thread, about every 1ms. Sampling has
runtime overhead and cannot serve as a compiler performance benchmark.

The host signals only the isolated controller fixture cache after genuine ECJ
activation. The controller executes the actual fully visible Stop click on its
main thread, clocks the whole click and checks that its original active StopToken
was requested by the real callback. No compiler pause, fake phase, artificial
input relay, token injection or direct cancellation callback is used.
Independent samples must be wholly before and after the entire Stop click, on
one unchanged worker PID/start identity and compiler thread, with gaps <=50ms.
These observations bracket ECJ execution; they do not continuously trace every
instant between snapshots.

Observation collection is bounded to 25s/4096 samples. Post-READY observation
failure records INVALID and holds the observer alive until explicit host release.
The host releases it only after the original controller handle is terminal,
so finishing observer instrumentation cannot manufacture a stopped compiler.
A host timeout is not target termination: original handles remain for inspection,
with no automatic restart/force-stop. These invalid-observation branches were
reviewed structurally; forced overflow/timeout was not physically tested.

## Physical runs and harness corrections

Fresh serial-scoped preflight at 2026-10-11T18:52:37.346983+00:00
identified Samsung Galaxy A33 5G SM-A336E, Android 16/API 36, ARM64, s5e8825,
qemu0. Phone was unlocked/Awake and all four own package processes absent.
The earlier all-package baseline matched the installed harness bytes. Test APKs
were checked for exact package/public certificate/bytes, installed by authorized
USB debug `-r` preserving data, and installed-byte verified. This is a harness
installation, separate from production Android generated-app consent.

The first v1 real run passed with 537 ECJ samples, Stop endpoints
20909879920482/20909883014482ns and inner-edge gaps 0.535731/0.657577ms.
Its `prepare-01.txt`, `observer-01.txt`, `correlation-01.json` and artifact/source
pins are retained; original host/controller/observer handles were terminal.
This is historical evidence, not the final tested harness. Review identified a
partial-JSON parsing race and two invalid/timeout paths that could finish
observer instrumentation prematurely. The final harness requires a complete
newline-delimited WINDOW and explicit post-controller release even on invalid
observation. A fresh v2 namespace and both freshly verified test APKs were used.

The original final physical runner exited 0; controller and observer each exited 0
with instrumentation code -1 and their named PASS markers. The observer stayed
alive through the real late compile/dex/sign work and was released only after
the original controller handle was terminal. Same worker process/start ticks
were independently checked before release.

| Final observation | Actual value |
| --- | --- |
| Physical start UTC | 2026-10-11T18:52:46.281696+00:00 |
| Source bytes | 53401 |
| ECJ samples | 603 |
| Entire Stop click start/end ns | 21223675335680 / 21223678173296 |
| Stop click duration ms | 2.837616 |
| Conservative before/after gap ms | 1.575038 / 0.6975 |
| Maximum stack-capture duration ms | 0.623693 |
| Maximum inter-sample gap ms | 4.123346 |

A later review correctly required conservative **outer** sample-call edges:
Stop start minus prior sample start, and next sample end minus Stop end. Original
physical `correlation-02.json` preserves its original inner-edge gaps
0.992384/0.440384ms unchanged. `conservative-bracket-revalidation-02.json`
independently rechecks the unchanged raw physical timestamps and passes the
stricter bounds above; this is offline analysis of the same run, not a third
physical run. The committed runner uses conservative edges. Its executable AST
matches the final helper except ignored output-directory resolution. A bounded
parser check rejected every partial prefix of a nested JSON result and accepted
only the complete line. No new compiler run was claimed for this parser check.

## Source, artifacts and recovery

Final root: `b0096eeac61e46759def84f2b69b9c4b`; project: `appaecfc01a006c4102b06483b26b9478cc`.
Generated package: `dev.smallwright.generated.appaecfc01a006c4102b06483b26b9478cc`.
Source SHA-256: `31df96c1b9d36ac9da943b8cd53f6e195c6f724d70a1d5f06be79a27ffe878ce`.
Public project signer: `45ab5066dce6ec4fc111f87d10eec66c136c1365a32fd76d71e47dd7771907e0`.

On-phone APK path:

`/data/user/0/dev.smallwright.controller/files/binding-ui-probe-b0096eeac61e46759def84f2b69b9c4b/projects/appaecfc01a006c4102b06483b26b9478cc/artifacts/app-v1.apk`

The late actual v1 SHA-256 was `dfb4fd4bdd1407427c9ffd5ef4812b6c92119a00a2cc5535fa5a0df29450dcdf`.
It passed manifest/package/version/policy, real ApkVerifier signature and existing
per-project signer verification. Exact saved source1 survived, with no immutable
receipt, signedArtifact, enabled Install/export or automatic run history.
The late artifact existed, but readiness remained suppressed.

Each recovery probe started in a fresh controller process (externally absent
before launch) and finished its original handle. `run_recovery_and_neighbors.py`
recorded all eight outcomes:

| Case | Result |
| --- | --- |
| Reopen stopped project | PASS exact source1, no auto-build/receipt/Install/export |
| Separate actual visible fresh Build | PASS same content source2/version1, same signer, exact APK/receipt |
| Reopen ready project | PASS exact source2/version1 APK/receipt/signer, no rebuild |
| Duplicate prepare | Expected rejection at GUARD, instrumentation code 0 |
| Prior measurement ready | PASS exact source2/version2 restoration |
| Prior admitted-worker Stop ready | PASS exact source2 artifact/receipt restoration |
| Prior screen-lock ready | PASS exact source2/version1 artifact/receipt/signer restoration |
| Generation completion ordering | PASS SQLite/filesystem verification-only bytes |

All seven passing cases returned host 0/instrumentation -1; the guard returned
host 0/instrumentation 0 with the exact expected failure marker. The completion
neighbor does not invoke a compiler/signer or supply generated-app evidence.
Fresh Build replaces the late version1 artifact at the same path. Its verified
SHA-256, restored on the next process, is
`835d61d93968bd1f0b03194206e6c13c917a1e522ad1f902d92745b19ebbc4e1`.
Generated app execution/function/install was not attempted in these checks.

Final test APKs:

| Host-built harness APK | SHA-256 | Bytes |
| --- | --- | --- |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `2a4b2fe5bde811a9e631aa65d2a71c9725966db30eec523a9ba7e750aad4d928` | 1013242 |
| `worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk` | `cd979ee0dc1b13f496982c3902b50985e45f67c08de62f5aee80c743915f7072` | 141118 |

Production controller and worker remain the prior exact installed artifacts:
`controller/build/outputs/apk/debug/controller-debug.apk`
SHA-256 `abf873d81bd6c9556eba40ded679db91fda7277d0ce719a7bd50425f7dd6c839`,
9093057bytes; `worker/build/outputs/apk/debug/worker-debug.apk`
SHA-256 `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850`,
21193573bytes. All four public harness certificate digests are
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
Private signing bytes were never inspected; fixture signer creation/verification
uses normal app APIs and only the public fingerprint is reported.

Tested Java source pins:

| Path | SHA-256 |
| --- | --- |
| `controller/src/androidTest/java/dev/smallwright/controller/EcjPhaseStopProbe.java` | `95b9e97db3f49be033f52bc10ca1cec2a33240ee0a745a38ab9c5027365fb03e` |
| `controller/src/androidTest/java/dev/smallwright/controller/CompilerMeasurementProbe.java` | `7ddaf35c46491e8bcf7ec6298fbb8d35a2abca40b1b01fc20c357cd61d00d7fa` |
| `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java` | `ee376b2ed44834eb80227160861b1eb962d4613c355a21b21e1605ac4a8c8765` |
| `worker/src/androidTest/java/dev/smallwright/worker/EcjStopObserver.java` | `88a7385223c5d99e1e04f7725864aa83eebf3ed179ccad0548670a62c1027f76` |
| `worker/src/androidTest/java/dev/smallwright/worker/ConfinementInstrumentation.java` | `fa608fcd582179d4f6670b471e1994d92a6e5da1808bf43c5df487a71118386d` |

## Commands, quality and remaining gates

```bash
source scripts/env.sh
./gradlew spotlessApply :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest qualityCheck :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 scripts/check_phone_ecj_stop.py --serial "$PHONE_SERIAL" --label 02
```

Label 02 already exists and preparation is one-shot; never replay it. The actual
physical host run used ignored `run_correlated_stop.py`; the committed equivalent
includes the subsequent conservative-edge analysis correction described above.
Recovery uses instrumentation route `-e ecjStopOnly restart`, then `recover`,
then `ready`, with runner
`dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation`.
Verify original terminal handles between phases. A fresh attempt needs a new
explicit guarded fixture namespace and recorded APK, not a data reset.

Final offline quality passed in 35s: 177 tasks, 11 executed/166 up-to-date.
JVM tests were UP-TO-DATE, not freshly executed. Spotless, compiler-input
checks, Android Lint and both test APK assemblies passed. Android-test lint
analysis was fresh; lint report tasks were cached, with 0 errors and 72/2/0
controller/worker/protocol warnings. Both final Java source pins and all four
installed APK byte hashes matched the tested snapshots after the final build.
All seven existing frozen ledger rows passed the unchanged validator.

Original physical/controller/observer/recovery host PIDs were absent before
scoped cleanup. Only this run's four harness packages were force-stopped;
existing projects, failed records, receipts and app data were retained. At
2026-10-11T18:56:08.633237+00:00, policy/power showed keyguard false and Awake.
The committed runner rejected existing label 02 at the no-overwrite guard before
starting instrumentation; records/data remained intact. A separate host AST
comparison verified the final helper equivalence and final conservative bound.
This check is separate from the original physical run's timing analysis.

Exact changed paths: the five Java files in the source-pin table above;
`scripts/check_phone_ecj_stop.py`; `docs/STATUS.md`; `docs/alpha-acceptance.md`;
`docs/validation/ecj-phase-stop-a33-20261011.md`. Existing unrelated skill files
and the tooling tail of STATUS remain outside this checkpoint.

No alpha ledger rows were added: this synthetic local test lies outside the
frozen twenty-request denominator. No performance, memory, sustained thermal,
ENOSPC, spontaneous OS eviction, immediate CPU abort, DeviceIdle/Doze, detached
execution or provider cancellation pass is claimed. Provisional first/warm caps
and broader resource gates remain unchanged. Full alpha and live free-only
repair remain open; live repair stays deferred under the user's local-check
instruction. Next task is a bounded larger-workload resource/retention check,
with whole-pipeline peak/storage/thermal limits still to establish.
