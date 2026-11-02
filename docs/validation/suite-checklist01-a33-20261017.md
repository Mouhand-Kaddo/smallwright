# A33 checklist_01: terminal compile failure after three repairs

Date: 2026-10-17 Dubai (2026-10-16 UTC). Device: Galaxy A33 5G (SM-A336E),
Android 16/API 36, preflight `ready` on the USB serial recorded locally.
Run `a33_checklist01_20261017_01`, request `checklist_01` from
`benchmarks/prompts.jsonl`. Result: fail. No APK was produced, so no install or
functional checklist ran.

## Harness

| Installed | SHA-256 |
| --- | --- |
| controller, initial send and repair 1 | `57d39a61385208325a10233c4eabc35e0dc9d6ea53ebf3ac705de694fb514037` |
| controller, repairs 2 and 3 (named ECJ diagnostics with line text) | `c371ff51efa2968782594c70e3775852cc0cb5aa4ef6afd84d3ee1964717e519` |
| worker | `c6e49bebf35e08fc352da3f0a3537d97573d02d90e4f019ad59ec0a2ecd4eb1e` |

The controller changed between repair 1 and repair 2. Each change was a
same-signer `adb install -r` that kept app data. The project, journal and
repair count carried over unchanged.

## Run

The parameterized `A33SuiteProbe` drove the real UI. Raw output is in
`toolchains/alpha-goal-20261017/a33_checklist01_20261017_01/NN-phase.txt`.

1. `prepare` created project `app4ca2cf50c46843439c807e7a5fa3870c` at pristine
   R0. `baselineBuild` built R1 with the normal Build button
   (`a6490ec2…77a0`). `pinBaseline` verified the receipt.
2. `preview` gave context `d13c49b4…3c9f` (9924 bytes). `send` with that exact
   hash, free model `cohere/north-mini-code:free`, saved R2. ECJ rejected it and
   the journal moved to REPAIR_NEEDED (31.6 s Send to settled).
3. Repair 1 (context `ad873cce…`, 14000 bytes) addressed four numeric-only
   `67108979` records and saved R3. R3 failed with six errors.
4. Repair 2 (context `7620c0ac…`, 14072 bytes) was the first to carry problem
   names and the failing line text. It saved R4, which failed with one error,
   `83886247` at line 218.
5. Repair 3 (context `a018eae0…`, 13586 bytes) saved R5. The journal settled
   FAILED with three repairs used, `UsageBudget.MAX_REPAIRS`. The chat shows
   "Java compilation still failed after 3 repairs. Source remains saved. No APK
   is ready." for revision 5. I read it in the UI after the run.

Total provider-reported tokens: 26130. Provider cost was not reported. Every
request was a free model.

## Classification

I read only the failing source lines with `run-as` and printed them on the
host. Each model request also sent the full saved MainActivity source to
OpenRouter, as every request does. Each error was a model mistake against API that exists in the
stubs:

- `setText(...)` used as a value;
- `getSharedPreferences(0)` with the name argument missing;
- a field deleted while code still used it;
- a `JSONException` catch around code that cannot throw it.

None was a stub gap. The named diagnostics helped: the error count went from
6 to 1 across repairs 2 and 3, but the last repair did not reach a clean
compile.

## Limits

Agent observed only. The initial send and repair 1 used the older diagnostic
text, so this run does not measure the new repair context from the start. The
S23 Ultra was not connected. Ledger row:
`benchmarks/results/a33_checklist01_20261017_01.jsonl`.
