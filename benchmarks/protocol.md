# Phone-local alpha evidence protocol

Record only observed runs. Do not fill a field from a model's claim, a fixture,
or a laptop build when the field is about on-phone execution. Each result line
must validate against `results.schema.json`; use `null` when a measurement was
not made. Keep prompts in a separately reviewed fixed test set. Results must not
contain credentials, source text, model reply text, crash bodies, or private app
data. Hashes and bounded outcome categories are sufficient for this ledger.

Validate result files locally with
`python3 scripts/validate_benchmarks.py benchmarks/results/<run-id>.jsonl`.
The script implements the exact keywords used by this repository's schema and
prints line numbers and pass/fail categories without echoing result content.

## Procedure

1. Assign a run ID and prompt ID before starting. Record device model, Android
   version, controller/worker APK hashes, compiler input hashes, and whether
   the phone is physically connected to the laptop.
2. Note source revision before approval. For a cloud step, record the exact
   approved context hash, configured model ID, whether the request was
   free-only, request destination class, and provider-reported tokens/cost if
   available. The payload itself stays out of the results ledger.
3. Record each accepted tool effect by name and location (`controller` or
   `worker`), the resulting source revision, worker UID, and compilation
   outcome. A model response without a verified local effect is not success.
4. For a build, record a start/end monotonic timestamp measured on the phone,
   artifact SHA-256, package name, version, signature-verification result and
   permissions. Record a failed build as failed, with no invented APK hash.
5. Treat `PackageInstaller` success as install completion only. Record explicit
   user confirmation, installed package/version, standalone launch, visible
   functional result, and any retained-data check separately. For an authorized
   USB debug installation/update, use `debug_installed`; reserve `confirmed`
   for observed installation through Android's user-confirmation flow. Standing
   authorization for development installs does not establish production consent.
6. For a controlled offline build, record that networking was disabled before
   the build and restored afterward. Never infer airplane-mode success from the
   worker merely lacking Internet permission. For detached tests, record when
   ADB was removed and what the user completed without it.
7. Mark the run complete only after the relevant acceptance checklist is
   observed. Record failures, cancellation and unknowns as such; do not delete
   unsuccessful attempts from the ledger.

The first repeatable benchmark will use the plan's 20 fixed requests on at
least two physical devices. A single S23 Ultra run is useful gate evidence but
cannot satisfy the two-device alpha criterion. Establish latency/resource
thresholds from measured compiler spikes before applying a performance cutoff.

## Outcome vocabulary

`compile_result` is `verified`, `compile_error`, `policy_rejected`,
`cancelled`, `failed`, or `unknown`. `install_result` is `confirmed`,
`debug_installed`, `rejected`, `failed`, `not_attempted`, or `unknown`. `functional_result` is
`accepted`, `rejected`, `untested`, or `unknown`. Keep these independent.

The schema captures one final line per attempt. Detailed per-transition
journal evidence remains on the phone; only bounded, nonsecret summary values
belong in a benchmark result.
