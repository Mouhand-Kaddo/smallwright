# A33 Reset second repair: actual compiler failure retained

2026-10-12 physical Samsung Galaxy A33/SM-A336E Android16/API36 ARM64,
qemu0. The second normal free-only compiler-error repair saved source6 but
again failed compilation. It did not produce a verified artifact or a Reset
functional pass. The previous accepted generated app version3 remains installed.
The [initial request and first repair](suite-counter02-a33-20261012.md) remain
separate failures. No developer edited generated source or used a paid fallback.

## Cases actually observed

Fresh connection/package/source guards preceded the update of only
controller.test, retaining its data and existing public harness signer.
Production controller/worker and the existing worker.test remained unchanged.
The reviewed probe's actual missing `inspect2` phase failed at GUARD on the old
test APK (host0/code0). After the isolated implementation and successful capped
host formatting/lint/assembly, actual `inspect2` passed (host0/code-1).
It independently required exact source5, failed run/origin/diagnostics,
sequence9/tokens11776, no current-revision receipt, null shown artifact and
disabled Install. These are real phone assertions, not seeded API responses.

The exact12250-byte source5 repair preview was displayed and cancelled.
Missing approval and wrong-context sends were rejected at GUARD (host0/code0),
before model call/mutation. A fresh catalogue read still reported prompt0 and
completion0 for `cohere/north-mini-code:free`; reported billing cost remains
unknown because no cost was returned.

Before the second Send or any update, a separate runtime contract declared
the actual measured baseline-4. Exact installed version3/public signer/APK
matched, native Increment/Decrement were adjacent, and count-4 survived actual
force-stop/relaunch with controller/worker absent. No count tap/reset, source
edit or installation occurred. The prior expected-2 versus actual-4 precondition
failure remains unchanged; its cause is unknown. This new baseline is not Reset
acceptance or reinterpretation of that failed check.

One approved `send2` then finished host0/code-1 with this actual outcome:

| Field | Actual observation |
| --- | --- |
| Project | `app652c00374f764652a3e4c0e501e3a6dc` |
| State/revision | REPAIR_NEEDED, source5→6 |
| Failed-build count/journal | 3 / sequence13 |
| Full source6 snapshot SHA256 | `865d1cc5b13c743b8654e981344ff966f441ecd0e5779f2760c10a31d7c36557` |
| Exact approved context SHA256 | `b9271695ece2428f72ba3d9c027ab3eaf27421ace9dc01875ca085b5796baf6b` |
| Additional/cumulative reported tokens | 6319 / 18095 |
| Send-to-settled time | 28825ms, including inference/UI/build; not compiler-only latency |
| Provider cost/compile-only time/resource measurements | Unknown |
| New verified APK/install/Reset runtime | None/not attempted/not run |

Fresh `ready2` passed the exact retained source6 failed-state snapshot/journal
without another model call/build. The next unsupported `inspect3` phase was
separately rejected at GUARD on this APK; its real RED is preserved. Production
`UsageBudget.MAX_REPAIRS=3`/`MAX_ATTEMPTS=4` and `repairRecorded` still permit
one final repair at failed count3. No budget, SDK input, assertion or threshold
was increased. The next bounded test route is being implemented separately.

## Artifacts, commands and handoff

The public harness certificate remains
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
Exact reviewed/installed controller.test APK is durably retained at
`toolchains/alpha-goal-20261012/counter02-repair/repair2-host/repair2-controller-debug-androidTest.apk`,
SHA256 `59099dc733c1cb6832a1d77f8f0eba9a29d5cdf34a15acc7533c42793f740eff`.
Fixture SHA256 `46f331027144821119cbf488bd1fc0eb202748298bd9d958649402b03cc97157`;
source checkpoint `bd690390b07ac31db463199753e1fed5fe698b10`.
Parent inspected the full diff, independently checked source/APK pins and public
signature, and preserved host reports/logs including the missing-input setup
failure. The worker.test SHA256 remains
`b66e17365a3daa395b78f7871bc490dfc9b8a47528ef06f8297df016a70c1901`.

Ignored physical evidence:
`toolchains/alpha-goal-20261012/counter02-repair2/` contains the implemented
explicit-serial runner, fresh identity/free catalogue, exact installed/source
pins, inspect2 GREEN, preview2, both guards, predeclared baseline/its two native
captures, send2/ready2 and missing inspect3 RED with their original terminal
handles. Coordinates/serial stay local. Generated source and APK bytes remain
on the phone; no generated APK was downloaded or installed here.

```sh
python3 toolchains/alpha-goal-20261012/counter02-repair2/run_repair.py --phase inspect2 --label actual-green-01
python3 toolchains/alpha-goal-20261012/counter02-repair2/run_repair.py --phase preview2 --label actual-01
python3 toolchains/alpha-goal-20261012/counter02-repair2/verify_current_baseline.py --serial "$PHONE_SERIAL" --label 01
python3 toolchains/alpha-goal-20261012/counter02-repair2/run_repair.py --phase send2 --label actual-01 --context-sha256 b9271695ece2428f72ba3d9c027ab3eaf27421ace9dc01875ca085b5796baf6b
python3 toolchains/alpha-goal-20261012/counter02-repair2/run_repair.py --phase ready2 --label retained-failure-01
python3 scripts/validate_benchmarks.py benchmarks/results/a33_counter02_20261012_01_repair2.jsonl
```

These commands identify original reserved observations and refuse overwrite;
do not repeat/reseed the sends. The new ledger row initially exceeded the
500-character notes limit; its invalid candidate is preserved locally and
only the summary was shortened. All three actual failure rows now validate.
Tracked changes in this physical handoff are this record and that new ledger
row. Next: exact final repair route, fresh context/once-only guards, then actual
install/native Reset acceptance only if a verified artifact exists. The alpha
goal and #1/#2/#19/#27 remain open.
