# Complete API 36 published-source reference build

2026-10-12 Dubai / 2026-10-11 UTC. Host-only compiler-input experiment, following
[the source/license inventory](../research-sdk-api-license.md) and
[the independent source graph](platform-api36-source-graph.md).

## Result and boundary

All 4,161 Java entries in the exact SDK-distributed API 36 revision 2 source
archive compiled locally with actual AOSP annotation support. The resulting
classes-only JAR contains exactly 6,227 classes: zero missing, zero extra,
zero different classes in the unchanged strict public/protected declaration
comparator. Independent review also compared the parsed declaration metadata
of every class, including the five package-private annotation helpers: zero
metadata differences. No API was removed or comparator rule relaxed.

This closes a technical uncertainty in the published-source route: complete
reference declarations can be reconstructed without handwritten signatures.
It supplies an exact reference for the complete, licensed product input still
required by issue #1. It does **not** establish independent regeneration from
raw AOSP sources or redistribution rights for SDK-package-derived inputs.
The earlier independently assembled source baseline still has 1,912 differing
classes; its raw-owner dependency graph remains incomplete. Those failures are
preserved, and are not relabeled by this different experiment.

Normal worker inputs remain limited. No candidate entered an APK; no phone,
project, installation, model request, credential or production source changed.
A33 detached execution evidence is recorded separately; this experiment uses
no phone observation to assert its reference-build result.

## Exact inputs and compile path

| Input | Identity / role |
| --- | --- |
| SDK reference `android.jar` | SHA-256 `d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`; comparison only, never a compiler classpath or output source |
| SDK `android-stubs-src.jar` | SHA-256 `f06372cb9bdbe4dececd35e5cb7b61fec01bb521dcd8a2424489f659b1e9322a`; all 4,161 Java entries extracted byte-identically |
| Actual annotation sources | All 55 Java files under Metalava `stub-annotations/src/main/java`; independently matched to local git blobs at commit `a2548e478c121d5801137d636f6e83af37eafc99` with lazy fetch/network disabled |
| Annotation-support bootclasspath | Existing independently source-built 6,227-class baseline, SHA-256 `e36f41ffc131afab50024cdc1d269ba1bd96c06387d3662580c7d7af868fe27a`; only for compiling the 55 annotation inputs |
| Five final annotation helpers | Earlier stock Metalava package-private output, verified against its receipt and exact public source; each differs solely by removal of `public` from its annotation declaration |
| Compiler | Project-local JDK 21.0.12.1+1; 45 executable/runtime/config pins checked before and after execution; maximum Java heap 1,536 MiB |
| Comparator | Unchanged `scripts/compare_platform_abi.py`, SHA-256 `051d78abd5f699155b90a1ade35b11509c40c72bb1c76eb3a518b9fe4731491d` |

The 55 annotation inputs produce 59 support classes. Main compilation uses
only these support classes as its classpath and an **empty bootclasspath**;
it compiles all published Java files and emits 6,222 classes. The host JDK
platform, SDK binary classes, independent baseline and raw hidden framework
owners are absent from this main compilation's classpath. An empty sourcepath
prevents incidental neighboring source discovery; `-proc:none` disables
annotation processors. Java source/target are 8, matching the existing probe.

A second compilation produces exactly the five package-private helpers using
the newly compiled public classes as its bootclasspath. Assembly combines
those five with the 6,222 main outputs. The other 54 support class names are
not shipped in the candidate: their compiler-support role and upstream
SOURCE-retention policy are distinct from the five final helper definitions.
The compiled source archive itself has no same-named Java source for those
five helpers. No SDK `.class` bytes are copied into the candidate. No resource
assets or notice entries are copied into this classes-only experiment.

## Execution and retained failures

Successful run: `2026-10-11T21:47:53.066706+00:00` through
`21:48:36.536916+00:00`; independent review passed at
`21:49:59.149863+00:00`. Stage timings measure actual javac processes,
not phone builds, inference latency or whole product peak resource use.

| Stage | Exit | Seconds | Evidence |
| --- | ---: | ---: | --- |
| RED: all published Java, missing annotation support | 1 | 15.291 | 25,115 reported errors; first 20 retained by javac's reporting limit |
| Actual annotation-support compilation | 0 | 1.310 | 55 source inputs, 59 classes |
| Complete published Java compilation | 0 | 19.498 | 4,161 source inputs, 6,222 classes |
| Five private helpers | 0 | 0.655 | Exact five class outputs |

The first runner preparation selected the wrong parent directory and failed
before creating output or starting javac; original source/error is retained
in `runner-preparation-failure-01.json`. Corrected runner's first experiment
`experiment-b07859929c584cf5a04c838ba36d8a67` passed the expected missing-helper
RED (13.142s), then failed main compilation (15.237s, 1,420 reported errors)
with only the five non-SOURCE annotations. Actual `SdkConstant` support was
missing. That failure, original runner and logs remain retained. The final
experiment adds the actual full annotation-source set; it changes no published
API source or test expectation. Unified exec sessions 69473 and 57341 are
terminal, exit 1 and 0 respectively; they were not restarted on an observation
timeout. Independent review session 25499 is terminal, exit 0.

## Artifact, evidence and replay

Ignored evidence root:
`toolchains/platform-api36-source-probe/published-source-reference-20261012/`.
Final output directory: `experiment-aebe744aca694561a4571ca058965852/`.

- Candidate `reference-headers.jar`: 4,918,257 bytes; SHA-256
  `57ff80c48d628b1748ed72d9d304f1a2aadbbaffae39f1012cc8706bfa51a797`.
- `receipt.json`: source/archive/helper/tool pins, exact arguments, original
  javac PIDs, exit codes, timestamps, timings and every class output's origin.
  SHA-256 `97c152ea46a5a65ec17208032b1123caecc628d34cf88c7b04d69c19589bf196`.
- `strict-comparison.json`: all 6,227 reference/candidate class names; match true.
- `comparison-cli-01.json`: standalone unchanged comparator command exited 0;
  no missing, extra or differing classes.
- `root-independent-review.json`: rehashed every input/copy/output, independently
  checked all 55 source blobs, helper transformation and absent original compiler
  PIDs, compared all parsed class declarations and checked classes-only assembly.
- `run_reference.py`: SHA-256
  `a2dde370a7a87d955f02e85ce6d2808fce2ca995a140fef0b98a7b7728a8a8ae`.
- `review_reference.py`: SHA-256
  `65006c3d021f39a133febfb7cc3f5b95bd67d2bd9b793b9a7466e2081d41a689`.

The candidate is not byte-identical to the SDK: 962 class files match byte for
byte. Declaration comparison ignores implementation bytecode and some compiler
metadata; it does not prove behavior, complete SDK parity, resource linking,
license applicability or a physical-device pass. No generated app APK exists
from this checkpoint. No production build or Gradle quality run was needed for
these documentation-only tracked changes and host compiler experiments.
Documentation checks passed: `git diff --check` and 46 local file links across
the four changed documents.

After these ignored pinned inputs exist, replay creates a fresh output UUID:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 toolchains/platform-api36-source-probe/published-source-reference-20261012/run_reference.py
python3 scripts/compare_platform_abi.py --reference toolchains/android-sdk/platforms/android-36/android.jar --candidate toolchains/platform-api36-source-probe/published-source-reference-20261012/experiment-aebe744aca694561a4571ca058965852/reference-headers.jar
```

Next compiler work: resolve the finite source/component grants, applicable
notices and corresponding source delivery for a complete candidate, using
[the existing inventory](../research-sdk-api-license.md). An SDK-derived route
still needs its package/component questions resolved; independent regeneration
still needs actual source/build closure. Keep this successful reference separate
from either route's release claim. Matching framework resources, on-phone
ECJ/AAPT2/build tests, source-built AAPT2 packaging and other issue #1 acceptance
items remain open. The full alpha remains unproven.
