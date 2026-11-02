# Bounded `MergedConfiguration` source-link closure — 2026-10-12

This checkpoint adds one exact framework source model to the approved stock
Metalava signature-source seam. It does not change the normal worker/toolchain,
header builder, strict ABI comparator, phone, or project state. No Android
source compilation, header/JAR/APK generation, model request, network fetch, or
license conclusion occurred.

## RED and bounded result

The current 91-source result in
`toolchains/platform-api36-source-probe/owner-signature-graph-20261011/experiment-fa5b036e6dc442f1b10cac45aaaec935/`
contains exactly two unresolved `MergedConfiguration` contexts:

- `ActivityThread.prepareRelaunchActivity(...).config`
- `CompatibilityInfo.applyOverrideIfNeeded(...).mergedConfig`

The following assertion was run as the RED control and failed with
`AssertionError: MergedConfiguration remains unresolved`, as expected:

```bash
python3 -c 'import json; p="toolchains/platform-api36-source-probe/owner-signature-graph-20261011/experiment-fa5b036e6dc442f1b10cac45aaaec935/unresolved-signature-types.json"; rows=json.load(open(p)); assert not any(x["type"] == "MergedConfiguration" for x in rows), "MergedConfiguration remains unresolved"'
```

The new actual-parser run used 92 top-level source models with the exact added
source at `toolchains/platform-api36-source-probe/base-r2-sparse/core/java/android/util/MergedConfiguration.java`.
The file SHA-256 is
`ad5b6bdc7412698746ae27b6c4c9534030d4d0cb0bb5ce772e513ab1eebaa3dd`; the
frameworks/base blob is `4328e0826bd0bcf784ad0ec5ec13653bf1e43eea` at pinned
commit `99b01a65cc4c104933788b3143285ab6bae65827`. Before parser launch, the
runner compared the local bytes to that Git blob with `GIT_NO_LAZY_FETCH=1` and
Git protocol access disabled. The byte-identical source copy and its actual PSI
VirtualFile path were also checked in the parser output. The raw source retains
its Apache-2.0 header; this experiment does not complete redistribution or
notice review.

The source belongs to `framework-core-sources` through the exact
`core/java/Android.bp` `"**/*.java"` glob, and the root frameworks/base build file
includes that source group. This establishes the source-group edge, not complete
canonical Soong dependency assembly.

Result: parser exit 0, observed Java stage 18.910734544 seconds on the host
(not phone latency). Across 6,246 signature links, 4,717 resolved from the
unchanged source-only baseline JAR, 1,367 from isolated raw/generated source,
and 162 remained unresolved across 63 symbolic types. The two prior contexts
resolve to the exact `android/util/MergedConfiguration.java` isolated copy. Ten
links of that type resolve from the added model, and all 32 links originating
inside its declarations resolve; no new unresolved owner link came from it.
Diagnostics are 168 `UnresolvedImport` and 17 `InvalidJavadoc`, compared with
170 and 16 in the 91-source result. The added diagnostic is preserved.

The exact 91-source baseline had 6,214 links: 4,693 baseline JAR, 1,357 source,
164 unresolved across 64 types. The unresolved-link count fell by two; this is
not a whole-graph pass. The stock parser records direct signature types only;
it does not establish method-body/type-variable/annotation closure, complete
module classpaths, member compatibility, full headers or release eligibility.

## Reproduction and retained evidence

The single parser command was:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 toolchains/platform-api36-source-probe/owner-signature-graph-20261012/run_graph_merged_configuration.py --run
```

It used the already compiled driver and pinned stock Metalava with local JDK
21, `-Xmx1024m`, a sanitized child environment and a 60-second child timeout.
The runner SHA-256 is
`a45737f12aec981ae1088758bee48f0b7f10cc62f9aed0b9119f01ff7e85bae0`. The
parent-root ignored evidence is retained under
`toolchains/platform-api36-source-probe/owner-signature-graph-20261012/experiment-d52b5c8a74c34e419dbcd7a4a2922408/`:
`receipt.json`, `actual-model.log`, `model-result.json`, `unresolved-signature-types.json`,
92 isolated source copies, source/model inventories and temporary working
directory. The receipt contains prelaunch source/tool/argument hashes and
completion measurements. The prepared-only attempt and first corrected-runner
failure remain preserved in sibling experiment directories; neither ran the
parser.

Independent parent review is recorded at
`toolchains/platform-api36-source-probe/owner-signature-graph-20261012/root-independent-review-01.json`
(SHA-256 `e2719f592d544cbb3f33d1645c70c7dd236a31fca5cbc253ffb0eb2ba0f7d609`).
Its script is
`toolchains/platform-api36-source-probe/owner-signature-graph-20261012/root_review_01.py`
(SHA-256 `34ef6a2fb463079e163eaa566b33086a5e4796bdf9af57852d7333a395cf16cf`).
Review independently confirmed 92 source models with the prior 91 unchanged,
exact source/tool/artifact hashes, both prior contexts resolved to the exact
source, ten source-backed matching type links, all 32 owner-signature links
resolved, and 162 remaining links/63 types. It also checked the strict comparator
hash was unchanged and that no candidate header/APK was made. The review receipt
binds parser receipt SHA-256
`82d2bfe0d876f1498f62ad202133c5a1ae6c304e9b32e72785878f21821abed1`.

The unchanged strict API36 comparator was not run because this slice emitted no
candidate headers/JAR. The existing strict failure remains 1,912 differing
classes, zero missing/extra, as recorded in
[the integrated source graph history](platform-api36-source-graph.md). No ABI,
canonical closure, physical-device or distribution pass is claimed. Remaining
owner/module closure and component notice work stay open under issue #1.
