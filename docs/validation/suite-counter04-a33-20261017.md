# Fresh A33 counter_04 run after the lambda fix

Date: 2026-10-17 Dubai (2026-10-16 UTC). Device: Galaxy A33 5G (SM-A336E),
Android 16/API 36, preflight `ready` on the USB serial recorded locally.
This is a fresh run, `a33_counter04_20261017_01`. The original
`a33_counter04_20261012_01` baseline failure and its flags stay untouched; see
[baseline failure](counter04-baseline-a33-20261013.md).

## What changed before the run

Commit `2d9ab02` adds compile-only `java.lang.invoke` stubs (ticket #33),
broadens the authored stubs to 132 classes, checks every stub declaration
against the API 36 reference with `scripts/check_stub_abi.py`, and sends the
model a fixed `build_contract`. Host results: 64 worker and 235 controller JVM
tests, worker/controller lint and assembly passed.

| Installed (same-signer `adb install -r`, data retained) | SHA-256 |
| --- | --- |
| controller `toolchains/alpha-goal-20261017/stubs-2d9ab02/controller-debug.apk` | `57d39a61385208325a10233c4eabc35e0dc9d6ea53ebf3ac705de694fb514037` |
| worker `toolchains/alpha-goal-20261017/stubs-2d9ab02/worker-debug.apk` | `c6e49bebf35e08fc352da3f0a3537d97573d02d90e4f019ad59ec0a2ecd4eb1e` |
| controller.test `toolchains/alpha-goal-20261017/counter04-fresh/controller-debug-androidTest.apk` | rebuilt with run id 20261017 and failure frames |

Installed hashes were read back from the phone after install. The harness
signer `19a7407f…bd25` matched the previously installed pair.

## Run

Raw instrumentation output: `toolchains/alpha-goal-20261017/counter04-fresh/0*.txt`.

1. `prepare` created project `app138231c5f5cc42caafd5bf206a684779` at pristine R0.
2. `baselineBuild` entered the frozen lambda fixture and clicked the normal
   Build once. R1 compiled, signed and verified in 3395ms
   (`90004391…486c`, 12761 bytes). This is the exact fixture that failed with
   problem 16777540 on 2026-10-13, so ticket #33 passes on the phone.
3. The verified v1 APK was streamed on the phone from `run-as` into
   `pm install` (debug install, not Android installer consent). Native taps,
   controller and worker stopped: `Counter: 0` → 1 → 2, force-stop and reopen 2.
4. `pinBaseline` first failed with `IllegalStateException` at stage
   `PIN_BASELINE_BUILD`. The frame was not captured. A rerun on unchanged state
   passed and verified receipt, installed bytes, version 1 and signer. The
   preceding force-stop of the controller is a possible cause; unconfirmed.
5. `preview` produced context `6475e15f…c149`, 3513 bytes, free model
   `cohere/north-mini-code:free`. `send` with that exact hash made one request:
   READY_TO_TEST, journal 5, source 1→2, 1776 reported tokens, cost unknown,
   zero repairs, Send-to-settled 10984ms, conservative build upper bound 1577ms.
   v2 `9619333e…5dfb`, same project signer `aaa37420…f572`.
6. v2 was streamed on the phone into `pm install`; installed hash matched.

## Checklist (agent observed, controller and worker stopped)

| Item | Observation | Result |
| --- | --- | --- |
| Tap count is distinct from the main count | v2 shows `Counter: 2` and `Lifetime Taps: 0`; two Increments give 4 and 2 | pass |
| Reset does not clear tap count | Reset gives `Counter: 0`, `Lifetime Taps: 2`; Increment gives 1 and 3 | pass |
| Both values remain after relaunch | force-stop and relaunch shows 1 and 3 | pass |

`restoreSelection` and `verifySelection` restored the prior project and chat.
Ledger row: `benchmarks/results/a33_counter04_20261017_01.jsonl`.

## Limits

Agent observed only; no human acceptance and no Android installer consent.
The lifetime value started at 0 after the update because the baseline never
stored one. Exact compiler time, total memory, storage and thermal were not
measured. The S23 Ultra was not connected.
