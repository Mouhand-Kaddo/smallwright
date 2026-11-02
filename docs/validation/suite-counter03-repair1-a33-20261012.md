# A33 frozen counter_03: first compiler repair

The first actual compiler repair **failed** on 2026-10-12. The ordinary app
`Send & repair` advanced source R2→R3 and returned `compile_error/java_compile`,
`REPAIR_NEEDED`, journal sequence 9 and failure counter 2. It produced no new
verified APK or installation. This is one repair Send after the initial failure,
not an exhausted three-repair budget or a functional acceptance.

The native v1 baseline remained installed with saved count 2; count 2 survived
relaunch with controller and worker stopped before and after the repair. No tap,
new generated install, source rescue, functional-feedback request or paid
fallback was used. The owned failed project stays selected for the next reviewed
compiler repair; the original prior-selection pins remain untouched.

## Contract and identities

The frozen request is unchanged: “Let the user choose a positive step size from
1 to 10 and make Increment add that amount.” The APK asset was independently
checked byte-identical to the fixed test entry. See the
[initial failure](suite-counter03-a33-20261012.md) and
[reviewed repair contract](counter03-repair1-case-contract-a33-20261012.md).

- Physical phone: Samsung `SM-A336E`, Android 16/API 36, ARM64, connected USB.
- Source checkpoint: `9e9ab828079fbe036f90ef22f8b297811781597b`.
- Case: `a33_counter03_repair1_20261012_01`.
- Project: `appc7df5a374abf4be69cb4367c58e88e01`.
- Package: `dev.smallwright.generated.appc7df5a374abf4be69cb4367c58e88e01`.
- Chat: `7a8d3451430e494996e5ab9187a4c51a`.
- Production run: `run_68b777296e0248c7b7e3d2d6a5acdb31`.
- Initial namespace: `a33-suite-counter-a33_counter03_20261012_01`.
- Repair namespace: `a33-counter03-repair1-20261012`.
- R2 SHA-256: `df0b926a1fd629ec9a016bf7bb133bd354565ea64dea530d9630dcc9d4cdbb65`.
- R3 SHA-256: `d1e048ca0e27efb11f51667a0cf95539f65aa1463f9fe192fc6fd4d5cb0d0625`.
- Approved repair context: 11,492 bytes,
  `141c595cf9f3b00815ab9ddf50d772ad01aa841ffd38df385b02411d5aae91e5`.
- Retained v1 APK: `5a69f9fc7d48b8d5347382b7e6c00738b77c1b7952eea82dbaca6930a55e0861`,
  16,857 bytes; public project signer
  `0fdbf5cf1bcb223817cfac8780d6c5d71dd7da5911171ba13e8ee896f7411bc9`.

Generated source and APK bytes stayed on the phone. Numeric compiler IDs,
categories and line numbers alone were returned during inspection; diagnostic
messages and generated source were not exported.

## Harness and preparation

The source review corrected current-HEAD reads used as historical reads, bounded
the complete R1/R2 inventory, preserved real terminal failure outcomes, and
bounded symlink checks to app-private storage. Immutable history was read without
changing HEAD, with seven-file, 64 KiB/file and 252 KiB-total limits. Selection
restoration has separate one-shot and installed-artifact guards.

The first host build failed at the numeric `JSONObject.put` helper's checked
`JSONException`; the failure log remains preserved. The helper's throws
declaration was corrected. Offline formatting, Spotless, controller lint and
Android-test assembly then passed in 1m29s, 102 tasks (23 executed, 79 up-to-date).
Lint had zero errors and 77 existing warnings. Commands used the project JDK 21,
one worker, no daemon, `-Xmx1536m -XX:MaxMetaspaceSize=512m`:

```bash
./gradlew spotlessApply --offline --no-daemon --max-workers=1
./gradlew spotlessCheck :controller:lintDebug :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m'
```

Parent independently verified the candidate's ZIP asset/DEX, public v2 signature,
package, min31/target36 and publisher certificate. An observer's mistaken
assumption that the test APK used application version1 was preserved and
corrected: the instrumentation manifest has no versionCode (effective0), matching
the already-installed test APK's version0. No downgrade occurred. The first
read-only preparation observer also mishandled `pidof` exit1 for an absent
process; the corrected guard verified absence without mutation.

Only `dev.smallwright.controller.test` was updated in place, preserving all data.
All four installed APK hashes were checked before and after:

| Package | SHA-256 |
| --- | --- |
| controller | `45adb0b429c5660c822e1b115fb68161131cbda0bb8f0acaa3e02cded41e658b` |
| controller.test | `ebe476bdb30bc88e0aba67a06df6c6379ab9db773461752f2d5a9ed606f399a5` |
| worker | `c1daca5782eb3fb751b0356c7a985f30024420afaaaf5b383ab0113db004f2bb` |
| worker.test | `ddf7bef8fd017d3d0278abdaf0e15b0fd5562759281b28557fbde0c37173c63f` |

The 983,184-byte test APK and public certificate evidence are in ignored
`toolchains/alpha-goal-20261012/counter03-repair1/`; the publisher certificate
SHA-256 is `19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.

## Actual phone results

The scoped runner is ignored
`toolchains/alpha-goal-20261012/run_counter03_repair1.py`; preparation used
`prepare_counter03_repair1.py`. All observer handles are terminal, host exit0.
Successful phases have instrumentation code-1; deliberate guard rejections have
code0 and the expected assertion stage.

| Phase | Observed result |
| --- | --- |
| Premature ready | GUARD rejection before an outcome existed |
| Inspect | Original R2/sequence5/failure counter1, full history, v1 origin/artifact/policy/signer and installed bytes verified; seven numeric problems, no R2 receipt |
| Preview | Exact displayed 11,492-byte context cancelled; no model request |
| Missing approval / wrong context | Both GUARD rejections |
| Inspect after guards | Same R2, journal and numeric problem set; baseline unchanged |
| Native before Send | Exact installed v1/count2/relaunch2, builders absent; no tap or install |
| Fresh catalog | Unauthenticated OpenRouter catalog reported zero prompt/completion/request price for configured `cohere/north-mini-code:free`; $0 cap/no paid fallback |
| One Send | R2→R3 compile error, `REPAIR_NEEDED`, sequence9/failure counter2; no ready artifact |
| Failed-state reopen | Saved full source hash/journal/failure/no-artifact outcome verified independently |
| Duplicate Send | GUARD rejection before another request |
| Native after failure | Exact v1/count2/relaunch2 again, builders absent; no tap or install |

Reported repair tokens were 6,871, cumulative run tokens 12,303 (initial 5,432).
Actual reported cost is unknown. Send-to-settled was 31,668ms, including inference
and observation; compiler-only time, cold/warm classification, thermal and total
worker/child/physical memory peaks were not measured. Controller own-package
app+data bytes were 56,962,560 before and 56,987,136 after; cache is included in
data. These two aggregate snapshots are not peak or combined-builder storage.

Raw handles/logs, source and artifact pins, catalog, baseline observations and
filtered own-app native hierarchies remain in the ignored case directory.
The benchmark row is
`benchmarks/results/a33_counter03_repair1_20261012_01.jsonl`; the original initial
row and every Reset failure remain unchanged.

## Remaining gates

The Step functional checklist is untested. Prepare a separate reviewed second
repair against this same R3 and production run under the unchanged global cap.
Do not restore the prior selection while this continuation is being prepared.
No new generated APK, production installer consent, human signoff, benchmark
performance pass or full alpha pass is claimed. The S23 suite, sixteen other
unrun A33 requests and broader API/licensing/resource/recovery gates remain open.
