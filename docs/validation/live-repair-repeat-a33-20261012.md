# Fresh live free-only repair on A33

On 2026-10-11 UTC / 2026-10-12 Dubai, a fresh isolated A33 case received one
real OpenRouter repair response and compiled, signed, restored and installed
its exact native APK entirely on the phone. Later standalone increment and
restart checks passed on the unchanged APK. The first standalone test **failed**
when its second tap left the displayed count at 1; that failure remains recorded
and its cause is unconfirmed. No full alpha or twenty-request-suite pass is claimed.

The previous [A33 case](live-repair-a33-20261011.md) remains separate. This run
uses `a33-live-repair-fixture-v1-repeat-20261012`, with a new owned project.
The initial unresolved increment symbol and origin are test-assisted; the Java
compiler failure, provider response, source application, compilation/signing,
receipt verification, installation and runtime observations are real.
No developer source edit, second Send, synthetic response or paid fallback
rescued this attempt. Model credentials were used only by ordinary app runtime;
the host and instrumentation did not read credential stores or key bytes.

## Device and policy

Fresh preflight at `2026-10-11T22:52:41.915871+00:00` identified physical Samsung
Galaxy A33 5G SM-A336E, Android 16/API36, ARM64, qemu0. Serial and UI coordinates
remain in ignored records. Awake/keyguard guards preceded each phase.

The unchanged ModelClient fixes `cohere/north-mini-code:free`, with no paid model
fallback or automatic transport retry. Its [official pricing page](https://openrouter.ai/cohere/north-mini-code:free/pricing)
listed Free immediately before this run. The user authorized a $0 spend cap.
Provider-reported total usage was **4,874 tokens**; reported cost is **unknown**.
No account/billing or credential inspection was performed.

Exact source/revision/chat/run/diagnostic provenance and displayed payload checks
remained enabled. One-shot attempt reservation happened before Send. Original
Popen handles were retained until terminal completion; no observer timeout
caused a second request. App elapsed Send-to-settled observation was **13,915 ms**,
including cloud waiting and app work; this is not separate inference or compiler
latency and is not compared with the local-build-only latency caps.

## Actual cases

Ignored evidence root: `toolchains/alpha-live-repair-repeat-20261012/`.

| Case | Actual result | Raw/record evidence |
| --- | --- | --- |
| Unknown case selector | Expected GUARD rejection before UI/request; host0/code0 | `guard-01.txt`, `guard-handle-01.json` |
| Fresh seed, 22:52:56 UTC | Actual JavaCompileFailure, one numeric diagnostic, source1, REPAIR_NEEDED sequence5; no model | `seed-01.txt`, `seed-handle-01.json` |
| Preview/cancel | Exact 8,812-byte payload; failed source/run unchanged | `preview-01.txt`, `preview-handle-01.json` |
| Missing approval / wrong context hash | Expected GUARD rejections; host0/code0 | `guard-02.txt`, `guard-03.txt`, corresponding handles |
| Single live Send, 22:53:19 UTC | PASS: source2/version2, READY_TO_TEST sequence9, repairs1; actual signed artifact | `send-01.txt`, `send-handle-01.json` |
| Fresh process ready reopen | PASS: same source snapshot, sequence, receipt, APK and signer; no rebuild/request | `ready-01.txt`, `ready-handle-01.json` |
| Exact debug installation | New package absent; Success; installed bytes/version match | `installed-runtime-01.json` |
| First standalone sequence | **FAIL**: observed 0 → 1 → 1 after two taps; expected final2 | `runtime-*-01.xml`, `installed-runtime-01.json` |
| Fresh inspection without tap | Still1 at 22:54:29 UTC; original failure was not merely replaced by a later screenshot | `diagnostic-current-01.json`, `.xml` |
| New explicit touchscreen sequence | PASS: unchanged APK, retained baseline1 → 2 → 3; force-stop/relaunch retains3 | `runtime-diagnostic-02.json`, `diagnostic-*-02.xml` |
| New sequence with original input tap command | PASS: unchanged APK, baseline3 → 4 → 5; force-stop/relaunch retains5 | `runtime-original-tap-03.json`, `diagnostic-*-03.xml` |
| Duplicate exact-approved Send | Expected GUARD rejection before UI/request | `guard-04.txt`, `guard-handle-04.json` |
| Previous fixture/default selector guard | Expected duplicate exact-approved Send rejection; no old fixture change/request | `original-fixture-guard-01.txt`, `.json` |
| Selection restoration | PASS: only previous project selection restored; project/source/outcome retained | `restoreSelection-01.txt`, corresponding handle |

Successful instrumentation phases require host exit0, Android code-1 and their
named result. Expected guards require host0, code0 and named GUARD rejection.
The runtime helpers independently require app focus, actual native TextView and
enabled Increment Button, exact observed counts, exact installed hash and absence
of controller/worker. Later sequences started from retained data; they did not
clear data, reinstall, change generated code, weaken the failed assertion or
convert the first runtime record into a pass. The initial tap discrepancy remains
unexplained; subsequent successes establish only their recorded checks.

## Exact artifact and tested code

- Base revision: `fda84a6d4ddee87fa52dc608015fb55a3fe41a8d`, plus the two test-only changes below.
- Project: `app1acf3ed7b529465c99ac1f4a2da48b0c`.
- Package: `dev.smallwright.generated.app1acf3ed7b529465c99ac1f4a2da48b0c`.
- Run: `run_7f2e02ce907a4facb4109271c3c65c4e`.
- Approved context SHA256: `515926f19a098626042b89e630de81cb9886ca6f8af577409573eaf02b42d92d`.
- Final source2 snapshot SHA256: `2b33a362118be825e8431093d1e58ded09452fd79aa94f8842e3cbce801df62e`.
- Phone APK: `/data/user/0/dev.smallwright.controller/files/projects/app1acf3ed7b529465c99ac1f4a2da48b0c/artifacts/build_c42f65744d3147539eebcd8afc175c62/app.apk`.
- APK SHA256: `b27e88be801645579f256e67e387f9ee6c05a219204c7a348564fbe38344abb1`, 16,857 bytes, version2.
- Project public signing certificate SHA256: `130e148bbace21fc7134b3b67fd0745044d5f738df378f0d5fb86e9bf3ae1fc9`.

On-phone immutable receipt, package/manifest policy, ApkVerifier signature and
existing project signer checked before installation. The verified APK was piped
from controller run-as directly to `pm install -r --user current --pkg ... -S ... -- -`
inside the phone shell. No generated APK/source bytes were downloaded. This is
the approved USB debug route, not production installation-consent evidence.

Only controller.test was updated, data-preserving, after comparing all four
installed harness hashes with the recorded baseline and verifying its public
certificate/package. Its exact host APK is:

`toolchains/worktrees/a33-live-repair-repeat-20261012/controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk`

SHA256 `baa01ea02e19478c34e8753367cb7d1c6f6851df58328a79d5ba3ecc0824a28d`,
684,482 bytes; public certificate SHA256
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
Production controller remained installed SHA256 `f3c138eec7d55e8745cb441b7c43c77c5a74a5a193b2210f69c2d2fa73e0af83`;
worker `5f4b4774df98dbed513d864ce7b3efa99787e794fe4f997e1c6f5af829d01850`;
worker.test `4019923938cb23ab850a6e27c63e843d5b56f74808fa5e7ee87482edea9f477b`.
Final installed bytes matched all four and the generated APK.

Changed tracked paths:

- `controller/src/androidTest/java/dev/smallwright/controller/A33LiveRepairProbe.java`: allow one fixed additional isolated case, retain default namespace and one-shot guards.
- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`: forward the opt-in case selector to this probe only.
- `docs/validation/live-repair-repeat-a33-20261012.md`: this evidence.
- `docs/STATUS.md`: concise new observation and limit.

Exact test-source hashes and original helper hashes are in `tested-source-pins.json`;
final verification rechecked both tested source files unchanged. Helper changes
only added duplicate-approval guard support and separate later runtime observations;
original records and diagnostic helper source are preserved.

## Commands, validation and restoration

Use the verified private serial as PHONE_SERIAL. Do not reseed/resend this
reserved case; these commands document the original attempt.

```sh
python3 .agents/skills/real-phone-test/scripts/preflight.py --adb toolchains/android-sdk/platform-tools/adb --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-repeat-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase seed
python3 toolchains/alpha-live-repair-repeat-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase preview
python3 toolchains/alpha-live-repair-repeat-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase send --context-sha256 515926f19a098626042b89e630de81cb9886ca6f8af577409573eaf02b42d92d
python3 toolchains/alpha-live-repair-repeat-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase ready
python3 toolchains/alpha-live-repair-repeat-20261012/install_and_runtime.py --serial "$PHONE_SERIAL"
python3 toolchains/alpha-live-repair-repeat-20261012/run_phase.py --serial "$PHONE_SERIAL" --phase restoreSelection
source scripts/env.sh
./gradlew spotlessApply :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew spotlessCheck :controller:lintDebug --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

The build ran in the named worktree. Build01 failed because the new parameter
was initially forwarded to the wrong instrumentation route; fixed before any
installation. Build02 passed in 67s (72 tasks). Formatting/lint passed in 78s
(73 tasks), zero lint errors / 72 warnings; no baseline or threshold change.
Logs: `toolchains/alpha-live-repair-repeat-build-20261012-01.log`, `-02.log` and
`toolchains/alpha-live-repair-repeat-quality-20261012.log`. No new JVM/benchmark
suite run or row is claimed for these test-only changes.

Final verification at `2026-10-11T22:57:15.219454+00:00` matched installed APKs
and tested source pins; original instrumentation handles were absent, worker
active-build scratch empty and only this run's controller/worker/generated
processes stopped. The app credential, owned project/source/outcome, generated
count5 and prior project data remain intact. Scoped test UI XML is retained in
ignored records and named temporary phone paths; no phone-wide/private capture.

Next: investigate reproducibility of the initial tap discrepancy if it recurs;
continue the separate resource, fixed-suite, two-device and distribution gates
listed in [status](../STATUS.md). The S23 frozen repair remains queued for that phone.
