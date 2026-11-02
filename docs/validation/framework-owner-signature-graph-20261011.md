# Actual framework-owner signature links — 2026-10-11

While the user-performed detached A33 case remained pending, host-only compiler
work continued at the approved stock Metalava source-parser seam. Base local
checkpoint `99329cb`; contributes to issue #1.
No phone UI/project/installed APK/model/key was touched. No generated app source
or APK was pulled. No new Android compiler headers, candidate JAR or APK was
produced; no physical or release pass follows from these models.

The earlier [owner-resolution experiment](platform-api36-source-graph.md#hidden-aidl-java-owners-at-the-actual-source-model-seam--2026-10-02)
proved14 hidden owners and3089 AIDL-generated signature class links resolved.
It did not scan the added owners' own declaration dependencies. This case
adds their complete direct signature scan and the actual52-file OpenGL group,
using the same pinned stock parser/driver and source-only baseline.

## Measured source-model scope

RED inputs:22 actual AIDL-generated Java files,16 existing raw owner/helper
files and52 raw OpenGL files, isolated byte-identically:90 source files/top-level
models plus their nested classes. The parser scanned superclass/interfaces,
fields, constructor/method returns/parameters/throws, type arguments, arrays and
wildcards. This excludes method bodies, type-variable bounds, annotation types,
transitive member compatibility and full canonical Soong dependency assembly.

These inputs remain explicitly experimental r2 framework/compat plus r4
modules-utils sources. The source-only6227-class baseline substitutes for
canonical Soong classpaths. No SDK source/class definitions or neighboring
raw directories feed the model; actual PSI VirtualFile paths identify every
resolved link. Host JDK definitions0. Tool runtime libraries run Metalava;
they are not supplied as Android model-definition classpath inputs.

| Measured result | Original90-source scan | With exact CompatibilityInfo source |
| --- | ---: | ---: |
| Actual parser exit / host seconds | 0 /15.532794286 | 0 /18.495114670 |
| Top-level source models | 90 | 91 |
| Signature class links | 6145 | 6214 |
| Links from exact source-only baseline JAR | 4649 | 4693 |
| Links from exact isolated raw/generated sources | 1317 | 1357 |
| Unresolved signature links | 179 | 164 |
| Distinct unresolved symbolic type strings | 63 | 64 |
| UnresolvedImport / InvalidJavadoc diagnostics | 166 /15 | 170 /16 |

Parser durations are actual host Java stage timings, not phone/compiler latency
or total helper time. The first helper re-opened the same baseline ZIP per link
during provenance verification; its original handle was awaited to completion.
The second helper inventories that unchanged ZIP once, without relaxing any
entry/path checks. Both original host handles completed exit0. No parser stage
was restarted/terminated due to observation timeout.

Missing symbolic strings may be unqualified because an import is unresolved;
do not treat this list as a canonical FQN inventory. Contexts and actual raw
imports must identify owners. Old missing `CompatibilityInfo` accounted for18
sites in ActivityThread and nested source models. Its raw file already existed
and matched the exact local pinned Git blob, with lazyfetch/network protocols
disabled. Byte-identical inclusion of that one file resolved all18 old contexts
to the actual isolated source path/COMMAND_LINE model;35 total CompatibilityInfo
links now resolve, including new source-local references. No invented stand-in,
classpath SDK definition, error hiding or diagnostic suppression was added.

There are still164 missing links. The new owner adds three unresolved signature
sites: `InsetsState`, `InsetsSourceControl[]` and `MergedConfiguration` (the last
already missing elsewhere). Distinct symbolic types grow63→64; diagnostic counts
grow too. This is a partial graph improvement, not whole-module closure GREEN.
Most-referenced remaining strings include LoadedApk15, ActivityWindowInfo9,
ContentProviderHolder9, PendingTransactionActions9 and ContextImpl8.

## Exact source, tools and evidence

- New raw owner: `base-r2-sparse/core/java/android/content/res/CompatibilityInfo.java`.
- Frameworks/base commit: `99b01a65cc4c104933788b3143285ab6bae65827`.
- Raw SHA-256: `50c4c6799b08ccf6f0de97ca3a1423f4897380fcf82b365199da0ebd8a8d3d54`.
- Actual r2 core/java/Android.bp selects `**/*.java` under framework-core-sources;
  root Android.bp includes that group in framework-non-updatable-sources.
  This proves the source-group edge, not assembly of every member/dependency.
- Stock Metalava commit: `9809e0d70a5b807be3d7070d51f9c4714f2fdcb4`.
- Baseline JAR SHA-256: `e36f41ffc131afab50024cdc1d269ba1bd96c06387d3662580c7d7af868fe27a`.
- Frozen Java driver SHA-256: `d6426283c0ee6eaa71dc97d53fc71dc1956ab795c79b01b8a9edbb1604f38f13`.
- Verified compiled driver SHA-256: `f2036b489aac7e56e91306b710261ad1f3353451019182106fff5ce32c0fefdc`.

All90 Metalava/45 JDK installed-file hashes and matching public parser API
sources were checked before tools. The actual child command uses supported
local JDK21, explicit source root/classpath, sanitized environment and1GiB heap.
No SDK dependency installation, network request or system configuration change.

Ignored evidence root: `toolchains/platform-api36-source-probe/owner-signature-graph-20261011/`.

- `experiment-6fbbca5cc5aa49bf9065185f63cd97b5/`:90-source RED graph.
- `experiment-fa5b036e6dc442f1b10cac45aaaec935/`:91-source CompatibilityInfo result.
- Each contains prelaunch source/tool/argument/runner identity in `receipt.json`,
  all isolated copies, actual parser log/model JSON and unresolved context list.
- Original host outputs: `host-01.log`, `host-02.log`.
- Parent review: `root-independent-review.json`, `root-review-host-03.log`.

Independent parent review at2026-10-11T21:37:57.213458+00:00 rehashed original/copy
inputs and model outputs, matched all18 old contexts with actual source-backed
PSI locations and checked remaining164/64 counts. The first reviewer used a
naive substring replacement that also changed method/field names; the second
used a relative output path against an absolute PSI location. Both reviewer
failures are preserved in `independent-review-failure-01/02.json`; only reviewer
normalization changed. Parser inputs/output/history were unchanged. Final review
normalizes parameter-type tokens only and compares absolute source identities.

Implemented ignored runners and exact final pins:

- `run_graph.py`: `a7e36d5adf4c9065042465a93b821e9dc04323364ec30b464bc225d33b20b719`.
- `run_graph_with_compatibility.py`: `8fc8bc79f5d64e4b51e8a851ce6350422497eac9570a2b784b5d0138f86a3793`.
- `root_review.py`: `0ebe8d7bf04229ab4cae34bf8b6a820cd507b25be7985592ca035fa703acea3d`.


Historical executed commands (existing records are not overwrite/retry targets):

```bash
PYTHONDONTWRITEBYTECODE=1 python3 toolchains/platform-api36-source-probe/owner-signature-graph-20261011/run_graph.py
PYTHONDONTWRITEBYTECODE=1 python3 toolchains/platform-api36-source-probe/owner-signature-graph-20261011/run_graph_with_compatibility.py
PYTHONDONTWRITEBYTECODE=1 python3 toolchains/platform-api36-source-probe/owner-signature-graph-20261011/root_review.py
```

The tracked changes are this record, `docs/validation/platform-api36-source-graph.md`,
`docs/STATUS.md` and the terminal observer note in
`docs/validation/detached-build-a33-20261011.md`. The local implementation files
above exist but remain ignored experimental tooling. No worker/header builder/
strict ABI comparator rule, dependency, production source or APK changed.
No new ABI comparison is claimed, because this case emits models, not headers.
The prior strict ABI failure remains. Redistribution and complete API coverage
remain open. Next compiler step: supply actual r2 owners for the remaining raw
signature/import dependencies and canonical module classpaths, then emit real
headers and run the unchanged strict comparator. Detached A33 completion still
requires user actions and post-reconnect verification; full alpha remains open.
