# Frozen A33 counter_03 initial compiler failure — 2026-10-12

The exact supported step-size request from `benchmarks/prompts.jsonl` ran once
on a fresh documented native counter baseline. It returned a real phone Java
compile error, source2/REPAIR_NEEDED, with no new APK or functional acceptance.
No compiler repair Send has run yet; the unchanged approved repair cap remains
available. The older S23 counter03 failure and all earlier A33 rows are separate
and unchanged. This is not a cap-exhausted result or a diagnosed compiler cause.

Parent reviewed the fixed probe/asset and its
[host contract](counter03-case-contract-a33-20261012.md). Its source-string mirror
test was removed; the remaining stdlib test checks the frozen asset exactly.
Optional baseline metric locals were corrected to Object for explicit JSON-null
values. Combined formatter/lint/Android test compilation and assembly passed in
the [combined harness gate](dialog-native2-20261012.md). No production change.

Fresh physical preflight confirmed A33 SM-A336E Android16/API36 awake/unlocked;
all four installed harness hashes matched. Existing combined controller.test
19de1160 remained installed; production controller45adb0b, workerc1daca57 and
worker.testddf7bef8 were unchanged. No new harness installation was needed.

## Normal baseline and native precondition

An unprepared baselineBuild rejected at GUARD before case mutation. Prepare
created a pristine source0 project/chat with no journal attempt or developer
edit. One normal visible Build saved the same bytes at source1/version1,
verified receipt/APK/policy/signature/manual originating chat and Install-ready.
Build-click-to-ready was3937ms including observer verification. Worker start
identity/cold-warm classification is unknown; the duration fits both registered
10s first/5s warm targets without identifying either class.

- Run: `a33_counter03_20261012_01`
- Project: `appc7df5a374abf4be69cb4367c58e88e01`
- Package: `dev.smallwright.generated.appc7df5a374abf4be69cb4367c58e88e01`
- Chat: `7a8d3451430e494996e5ab9187a4c51a`
- Manual origin: `manual_69a4e2e3f00146588fb1a8224d319245`
- Baseline source0/1 full snapshot SHA256: `b3238df1757035b21fddb960c6b8cef040ee71153544a484ccd9ccc49b9c5004`
- v1 APK SHA256: `5a69f9fc7d48b8d5347382b7e6c00738b77c1b7952eea82dbaca6930a55e0861`,16857 bytes
- Public project signer: `0fdbf5cf1bcb223817cfac8780d6c5d71dd7da5911171ba13e8ee896f7411bc9`
- Phone artifact: `/data/user/0/dev.smallwright.controller/files/projects/appc7df5a374abf4be69cb4367c58e88e01/artifacts/app-v1.apk`

Exact bytes streamed entirely on phone through the approved debug installer;
installed hash/version1 matched. With builders stopped, native0→1→2/restart2
passed before inference, with no preference/source edits. Duplicate baseline
Build rejected at NORMAL_BASELINE_R0_GUARD. pinBaseline passed immutable v1
bytes/manual origin/signer/installed identity independently of future mutable
receipt state.

Controller own-package app+data storage reported56880640→56933888 bytes during
baseline Build; cache is included. Controller ready counters VmHWM167824KiB /
VmRSS166832KiB are asynchronous own-process observations. Thermal boundary
statuses were0/0. Worker/child RSS, combined storage, exact physical peak and
longer thermal remain unknown; no1GiB/256MiB full-pipeline pass is inferred.

## One exact free-only Send: compile_error

Cancelled normal preview8224 bytes pinned context
`3907aa027ad95d0d5517bd8923da201821f889e2cab8a42091fec4861fa619ff`.
Unchanged frozen request SHA256:
`7fd522e65c1573256fd4a9060162fbac93a6b3ccd0547e648fc7fb91eae3f2d6`.
Real missing-approval and wrong-context guards rejected at GUARD without a model
request. Fresh unauthenticated public OpenRouter catalog metadata verified the
configured `cohere/north-mini-code:free` prompt/completion/request prices0,
with$0 cap/no paid fallback and exact app context/revision approval retained.

One Send settled in25705ms with5432 reported tokens/cost unknown:
production run `run_68b777296e0248c7b7e3d2d6a5acdb31`, source1→2,
REPAIR_NEEDED/journal5, compile_error/java_compile. Journal repair counter
is1; actual compiler repair requests sent by this probe are0. Final snapshot
SHA256 `df0b926a1fd629ec9a016bf7bb133bd354565ea64dea530d9630dcc9d4cdbb65`.
No new verified APK/install/native step checklist was available. The compiler
cause is not diagnosed from this category. Exact compiler duration is unknown;
state polling observed EDITING/APPLYING/BUILDING/REPAIR_NEEDED, maximum gap
118522923ns. Send-to-settled includes inference/setup and is not build time.

Controller storage reported56925696→56954368 bytes; outcome VmHWM195424KiB /
VmRSS188492KiB. These exclude worker/child/other UIDs and are not whole-builder
peaks. Cost, worker process identity and whole-pipeline resource limits remain
unknown. No paid fallback, functional-feedback rescue or developer edit occurred.

Separate failed-state reopen verified exact source2/no new readiness and retained
immutable v1. Duplicate initial Send rejected at GUARD without another request.
Parent independently observed the same installed v1 at native count2/restart2
after this failure, with builders stopped and no new installation/tap. All
original handles are terminal. The owned failed project remains selected for
the next approved compiler-repair slice; prior selection pins/data are retained.

The schema-valid initial row is
`benchmarks/results/a33_counter03_20261012_01.jsonl`. It records compile_error,
no generated artifact/version, no new install and functional untested. There
are still two agent-observed supported A33 passes, a capped Reset failure,
this Step repair pending, and sixteen other supported requests unrun. S23,
human acceptance and full-suite/performance/distribution gates remain open.

Durable scripts, contracts, source/installed pins, public zero-price metadata,
raw/terminal observers, baseline/debug installer and native UI evidence are in
`toolchains/alpha-goal-20261012/counter03-initial/`.

```sh
python3 toolchains/alpha-goal-20261012/prepare_counter03.py
python3 toolchains/alpha-goal-20261012/run_counter03.py --phase prepare --label actual-01
python3 toolchains/alpha-goal-20261012/run_counter03.py --phase baselineBuild --label actual-01
python3 toolchains/alpha-goal-20261012/counter03-initial/install_baseline_native.py
python3 toolchains/alpha-goal-20261012/run_counter03.py --phase pinBaseline --label actual-01
python3 toolchains/alpha-goal-20261012/run_counter03.py --phase preview --label actual-01
python3 toolchains/alpha-goal-20261012/run_counter03.py --phase send --label actual-01 --context 3907aa027ad95d0d5517bd8923da201821f889e2cab8a42091fec4861fa619ff
python3 toolchains/alpha-goal-20261012/run_counter03.py --phase failed --label actual-01
python3 toolchains/alpha-goal-20261012/counter03-initial/verify_failed_baseline_native.py
python3 scripts/validate_benchmarks.py benchmarks/results/a33_counter03_20261012_01.jsonl
```
