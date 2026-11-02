# Frozen A33 counter_05 — initial request accepted

One exact request from the unchanged fixed suite passed on physical A33
SM-A336E Android16/API36. This is agent-observed native acceptance, not human
alpha signoff or a full two-device pass. Counter_01's earlier rejected rows
and accepted feedback2, and all four failed counter_02 attempts remain intact.

## Meaningful installed baseline

The new once-only test route was independently reviewed, compiled, packaged
and public-signature checked. Initial Java overload and wrong-signer candidate
failures were preserved before the corrected same-signer candidate was used;
neither wrong-signer APK was installed. Missing baselineBuild phase and
unprepared phase guards were observed before the normal-UI phase was added.
Exact host results and APK identities are in
[the bounded case contract](counter05-case-contract-a33-20261012.md).

Fresh device and all four installed harness hashes matched before mutation.
Only controller.test was updated with data retained. Controller45adb0b,
worker0fc753e and worker.testddf7bef stayed unchanged. No generated app source
or APK bytes were copied to the host. The prepared source0 project was pristine
and had no model/journal attempt. Normal visible Build wrote identical bytes
as source1, version1, with the exact selected-chat manual-build origin.
Build-click-to-verified-ready was3548ms including observer verification;
first/warm process classification is unmeasured.

- Project: `app6c3f1743d2ed4a2a90415662e0b233c9`
- Package: `dev.smallwright.generated.app6c3f1743d2ed4a2a90415662e0b233c9`
- Chat: `78945c6717ee4c5ab7cbb0da17dbc01d`
- Manual origin: `manual_eda0cb6599e44b4cbbb306406b16d29c`
- Starting source bytes SHA256: `215c7ca1130693cd0f1bdd0452794115d932850bf2288088f6852f510705a62a`
- v1 APK SHA256: `2b97a28f7ac99dcfd5118ad50f7b069ef5064babb0e3fbbe279a87fa142f8328`,16859 bytes
- Public project signer: `2fce4628bcc8d06a10f7ba2c1423f7f9972ae418a0c6328d3fb7c07551c91ad1`

The exact verified APK streamed entirely on the phone to the authorized debug
installer. With controller and worker stopped, native Increment and Counter
passed0→1→2 and restart2. This preseed happened before the model request and
used native taps, with no developer source or private preference edits.
pinBaseline verified source1, manual origin, receipt, policy, installed bytes,
version1 and public signer. The count evidence remains a separate native record.

## Exact free-only request and actual compilation

The untouched counter_05 prompt request SHA256 was
`a238904a50feb8e6edd3088fd4dde293c5ac28a9500d2f1e7f2c137a0c61ef50`.
Preview8224 bytes was cancelled; context SHA256
`4772c3e3d777d071d2f71cade7e8cb13ae0898356672ca7326aed1fab7a66902`.
Wrong-context Send rejected before mutation. Fresh unauthenticated provider
catalog metadata confirmed selected model `cohere/north-mini-code:free`
prompt/completion prices0, no paid fallback and$0 cap. Standing user approval
was used only with the exact in-app context/source/once-only approval checks.

One real normal Send settled in12778ms, source1→2, READY_TO_TEST/journal5,
reported4434 tokens, zero repairs. Provider-reported cost is unknown. The
conservative last-pre-BUILDING-query-to-visible-ready upper bound was1670ms,
maximum state poll gap125835153ns. This includes possible unsampled model
tail/setup and is not exact compiler latency or warm/cold classification.
The ledger's exact phone_build_elapsed_ms stays null. Exact whole-builder
peak memory/storage/thermal and offline/detached generation were not measured.

- Production run: `run_e44006e44e3a4142b6c869c24261ab1b`
- Final source bytes SHA256: `42d7d9ffc44323ceae1563d93bd00022b09254daaa45383418e7f910c9e2d222`
- v2 APK SHA256: `4a368f933d730b0698dac8b62e55a3d981875a6475532bd33217a1c93552d223`,16859 bytes
- Phone path: `/data/user/0/dev.smallwright.controller/files/projects/app6c3f1743d2ed4a2a90415662e0b233c9/artifacts/build_ff44b99e8daf46e9ae21e8f64db233de/app.apk`

Ready reopen passed before update: exact current receipt/source2/version2,
same project signer, immutable baseline v1 APK/origin, and still-installed v1.
Controller own-package retained storage reported56716800→56761856 bytes;
cache is included in data. Controller-only outcome counters VmHWM191912KiB
and VmRSS181204KiB are asynchronous approximate counters, not total physical
builder peak memory or a full-suite resource pass.

## Exact update and native checklist

Parent independently checked phone artifact bytes and the installed v1 hash,
then streamed the exact v2 APK entirely on the phone to the debug installer.
Same package/signer retained data. With controller and worker absent, the
native Button displayed ADD ONE, count2 was retained before tapping, one tap
gave3, and force-stop/reopen retained3. Both installed APK hash and version2
matched. This passes all three counter_05 checklist items. It is an authorized
debug installation, not Android user-confirmation evidence or human signoff.

Duplicate Send rejected without another request. Prior project/chat restoration
and fresh verification passed. Native count3, source2/v2, signing identity,
chat history and all earlier apps/cases remain retained. No functional-feedback
rescue, source edit, data clear, uninstall, paid request or generated-byte
export occurred. The schema-valid new row is
`benchmarks/results/a33_counter05_20261012_01.jsonl`; it adds one supported
A33 pass. Seventeen other supported requests and the S23 suite remain pending.

Exact guarded scripts, original raw/terminal handles, fresh preflights, source/
installed hashes, host failures/logs, free catalog metadata and standalone UI
hierarchies are under ignored
`toolchains/alpha-goal-20261012/counter05-initial/` and
`toolchains/alpha-goal-20261012/counter05-baseline-build/`.

```sh
python3 toolchains/alpha-goal-20261012/run_counter05.py --phase prepare --label actual-01
python3 toolchains/alpha-goal-20261012/run_counter05.py --phase baselineBuild --label actual-01
python3 toolchains/alpha-goal-20261012/counter05-baseline-build/install_baseline_native.py
python3 toolchains/alpha-goal-20261012/run_counter05.py --phase pinBaseline --label actual-01
python3 toolchains/alpha-goal-20261012/run_counter05.py --phase preview --label actual-01
python3 toolchains/alpha-goal-20261012/run_counter05.py --phase send --label actual-01 --context 4772c3e3d777d071d2f71cade7e8cb13ae0898356672ca7326aed1fab7a66902
python3 toolchains/alpha-goal-20261012/run_counter05.py --phase ready --label actual-01
python3 toolchains/alpha-goal-20261012/counter05-baseline-build/install_update_native.py
python3 scripts/validate_benchmarks.py benchmarks/results/a33_counter05_20261012_01.jsonl
```

These are recorded executed commands, not repeat instructions for the consumed
case. Immutable old guard records intentionally stop after a later harness or
source checkpoint changes; never reset the one-shot case or overwrite its row.
