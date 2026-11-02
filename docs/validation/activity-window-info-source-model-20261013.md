# ActivityWindowInfo source-model checkpoint — 2026-10-13

Issue #32 extends the existing experimental raw-source signature graph with
one exact hidden resolver owner. It does not emit Android API source, a JAR or
any worker input. The complete public API and redistribution gates remain open.

## Implemented runner and acceptance

`scripts/test_activity_window_info_owner_resolution.py` is an opt-in host test
using the already pinned stock `OwnerResolutionProbe` driver, Metalava, JDK 21,
source-only baseline JAR and 92-source inventory. It creates a fresh 92-source
RED parser run and a separate 93-source candidate run, adding only
`android/window/ActivityWindowInfo.java`. It verifies that the nine predecessor
contexts are unresolved in the fresh 92-source run and resolve to the exact
isolated source owner in the candidate. It records full link-origin counts,
remaining unresolved types/links, exact diagnostic additions/removals, tool and
input identities, commands, timings, logs and model hashes. It also rechecks
the pinned predecessor receipt, model, unresolved inventory, argument lists,
all original/isolated source copies and parser log before and after the runs.

Before parser launch the test verifies the raw source against frameworks/base
commit `99b01a65cc4c104933788b3143285ab6bae65827`, Git blob
`71c500cd1bfac7c8b42c691beb60652a036edfeb`, SHA-256
`7589b07cf9e5229d5803b3dcc410c3fa17db2b10b4e4fc2c06af0021c429886f`, its
Apache-2.0 prologue and `@hide` declaration. It validates the pinned
`framework-core-sources` Java glob and the root framework source-group edge,
and retains the existing base NOTICE byte-for-byte. The parser classpath remains
the exact source-built baseline; SDK generation inputs remain empty. The model
owner's raw declaration and actual parser origin/path are retained without
representing this hidden source as a public API output.

The owner-resolution comparison reads the archived model's pinned
`owner_resolutions` metadata; that field is not present in the archived
receipt. Its owner-name input and the previous source-name list come directly
from the experiment directory's `owner-names.txt` and `scan-names.txt` files.
Link correlation uses one explicit spelling rule: the exact symbolic type
`ActivityWindowInfo`, and that exact token inside a method parameter type list,
map to `android.window.ActivityWindowInfo`. It preserves the raw context and
type on both sides, records each qualification rename, and compares all other
links through the same mapping. Getter and field identifiers remain unchanged;
newly observed links therefore do not count old links whose type spelling was
qualified by source resolution.

Reproduce the bounded source-only case once the shared host JVM slot is free:

```bash
PLB_ACTIVITY_WINDOW_INFO_INPUT_ROOT=. \
PLB_ACTIVITY_WINDOW_INFO_INTEGRATION=1 \
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=scripts \
python3 -m unittest test_activity_window_info_owner_resolution
```

The receipt and outputs are written below
`toolchains/platform-api36-source-probe/activity-window-info-20261013/` in the
input-root checkout. A failure keeps its new experiment directory and parser
logs; it does not overwrite predecessor evidence. The first granted run
stopped before parser launch because a relative receipt path resolved against
the wrong checkout; its terminal output and correction are recorded below.
The source-only tests also check the pinned unresolved inventory and
model/receipt schemas and paths, the exact source identity and Soong edges, and
the narrow qualification mapping against the actual nine predecessor
contexts.

The first granted opt-in attempt stopped during preflight, before the experiment
directory was created or either parser stage launched. The archived receipt has
one relative `original_sources` path (the pinned `ActivityThread.java`) and the
same relative `isolated_copies[].original`; the runner originally resolved it
against the feature worktree. The terminal output is preserved under the
ignored root at
`toolchains/alpha-goal-20261013/activity-window-info-preflight-failure-20261013.txt`
(SHA-256 `0be6ae47bed026a569a82d10a9732ae0dddd772310f2cb5102bda06c42e4b0e3`).
The path resolver now uses the pinned input checkout for relative archived
paths, leaves absolute paths unchanged, and records raw and resolved paths
alongside the existing hashes. It retains all archived source, isolated-copy,
driver, driver-class and baseline-JAR hash checks. The later granted run passed
both parser stages, as recorded below.

## Actual bounded parser result

The opt-in command completed successfully on 2026-10-13: unittest reported
`Ran 6 tests in 39.229s`, `OK`; both parser subprocesses exited 0. The 92-source
fresh RED took 19.989 host seconds and retained 6,246 signature links: 4,717
from the pinned source-built baseline JAR, 1,367 from source models and 162
unresolved links across 63 types. All nine `ActivityWindowInfo` predecessor
contexts remained unresolved.

The 93-source candidate took 16.127 host seconds and added only the pinned
`ActivityWindowInfo.java`. All nine predecessor contexts resolve to
`android.window.ActivityWindowInfo` from the exact isolated candidate copy,
with parser origin `COMMAND_LINE`, protocol `file` and matching PSI/file paths.
All 92 prior source inputs stayed byte-identical across runs. The candidate
has 6,268 signature links (4,732 baseline JAR, 1,383 source, 153 unresolved),
with 153 unresolved links across 62 types. The full-graph comparison records
22 newly observed links, 56 raw-spelling qualification changes, zero newly
unresolved links, zero removed previous links, and all nine prior unresolved
to resolved transitions. `UnresolvedImport` diagnostics changed from 168 to
167; `InvalidJavadoc` remained 17. The receipt confirms predecessor evidence
hashes and copies were rechecked at the end.

The full receipt is
`toolchains/platform-api36-source-probe/activity-window-info-20261013/experiment-036e28000df344d2a2579c353f953093/receipt.json`
(SHA-256 `038417a2f8a3a0b60ab452a96b59e8299d2e6a2efc26be4a597273f64128218e`).
Its stage logs and model hashes are:

- `fresh-92-red-parser.log` — `cad378d33dd92e93b34533fd8e6ef2af013b9fc568cdcd14be7296bc81e97827`; model result `95df46e277180f37e5c12c7175d0d0824baf70767aab6094bd337014fc131f56`.
- `candidate-93-parser.log` — `25959797269ad8d08e08e586a0f257e4a4802ad709fcd9e5d1a38f291fb422f1`; model result `5d0144bae4e1a95fdf1b2f5544e9e0a925b57a6bb69ba54a8ce9a6062efb7fb6`.

This is a bounded source-model experiment only: no SDK generation inputs,
public output, API comparison or phone run. The source remains documented
`@hide`; the candidate is experimental. The 153 unresolved signature links,
62 types, complete public API comparison, source/license grants and
redistribution clearance remain open. The initial preflight failure above is
preserved unchanged.

The existing owner graph itself remains experimental: its 92-source run had
6,246 signature links, 4,717 from the source-built baseline JAR, 1,367 from
raw/source models and 162 unresolved links across 63 symbolic types. This
checkpoint only tests the ActivityWindowInfo dependency; any newly exposed
dependencies remain in the complete candidate inventory and require additional
actual source resolution. Keep the unchanged full API comparator unmasked when
a complete candidate is eventually assembled.
