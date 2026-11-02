# Raw source modifier model checkpoint — 2026-10-12

This is a test-only checkpoint for the bounded source/API modifier frontier
tracked by issue #30. The extractor asks the pinned packaged Metalava
`SourceParser` for three raw method modifier cases. It does not modify
production worker code, the pinned Metalava checkout, API-selection behavior,
or the strict ABI comparator.

The first scoped host run used the initial `4f9362b` assertion that both
`SpannedString` and package-private `SpannableStringInternal` would have
`COMMAND_LINE` origin. It failed at the inherited-final origin assertion. The
run did establish raw-model GREEN for `Log.isLoggable(String,int)` with flags
`0x109` and `ServiceStartNotAllowedException.getCause()` with flags `0x21`.
All three candidate baseline REDs were captured (`0x9` vs `0x109`, missing
direct `getCause()`, and `0x1` vs `0x11`). The failure and full evidence are
preserved at:

- `/tmp/raw-source-modifier-model-20261012-run.log`
- `toolchains/platform-api36-source-probe/raw-source-model-20261012/integration-7ff35dc176cf4590bf37bc19317fe95e/`
- Read-only pinned-parser diagnosis:
  `/tmp/phone-local-source-modifier-sourcepath-diagnosis-20261012.md`

That run did not reach mutations, candidate caller compilation, or the SDK-last
step; no SDK file was opened. The original failed receipt and logs remain
unchanged.

After approval of the hidden-owner provenance correction, the scoped runner
was executed once more as an independent attempt. The invocation was:

```bash
PLB_RAW_SOURCE_MODIFIER_INPUT_ROOT=. \
PLB_RAW_SOURCE_MODIFIER_MODEL_INTEGRATION=1 \
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=scripts \
python3 -m unittest test_raw_source_modifier_model
```

It completed successfully: `Ran 1 test in 47.712s; OK`. Captured output and
evidence are:

- `/tmp/raw-source-modifier-model-20261012-run2.log` (SHA-256
  `63c102031ded3266ae39e29ce5d47fecb54eb8a0685cc4ef34a5e91a2a59db11`)
- `toolchains/platform-api36-source-probe/raw-source-model-20261012/integration-ceecc9294f874e11a56b9ce435cdbe46/`
- `receipt.json` SHA-256
  `317295812556e96302df3f0621f1fb5946721961d08fa4e411d4b168922df45d`

The receipt records pinned JDK/Metalava identities, all four raw source Git
blobs and file hashes, AOSP license prologues, a byte-identical base-r2
`NOTICE` copy, every command and stage exit, per-stage logs, and source/model
hashes. Raw source modeling used `sourceModelClasspath=[]` and
`sdkGenerationInputs=[]`. The caller compiled before the reference SDK was
opened. The API 36 reference hash matched the pinned
`d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`.

| Case | Raw source-model result | Filtered candidate | API 36 reference |
| --- | --- | --- | --- |
| `Log.isLoggable(String,int)` | `0x109` | `0x9` | `0x109` |
| `ServiceStartNotAllowedException.getCause()` | direct synchronized method, `0x21` | direct member absent | `0x21` |
| inherited `SpannedString.length()` | exact package-private source base, final, `0x11` | `0x1` | `0x11` |

All three isolated one-token mutations lost the selected accessor and failed
the same expected-flags assertion: native `0x9` vs `0x109`, synchronized
`0x1` vs `0x21`, and final `0x1` vs `0x11`. The caller compiled against only
the filtered candidate JAR without the SDK. The strict API comparator and
production/API-generation code were not run or changed.

The corrected runner pins the Android 16 r2 source commit and four exact raw
source files, records the base-r2 `NOTICE`, checks the packaged Metalava/JDK
file list, and uses an empty source-model classpath with no SDK generation
inputs. The `SpannedString` public child is required to be `COMMAND_LINE`. Its
package-private `SpannableStringInternal` base must be `SOURCE_PATH` with the
exact explicitly listed, byte-pinned isolated source path. This is stock
Metalava behavior: `PsiCodebaseAssembler.createPossibleApiClass` defers
inaccessible classes, then `findOrCreateClass` gives a referenced non-class
source file `SOURCE_PATH` origin. The inherited `length()` lookup must remain
owned by that exact hidden base and retain `final` (`0x11`). The full source
reasoning and exact pinned locations are in the linked diagnosis above.

Each raw mutation removes only its selected token in a separate isolated copy
and must fail the same expected-flags assertion. The caller compilation uses
only the pre-existing filtered candidate JAR. The pinned API 36 SDK reference
was opened only after source-model checks, mutations, and caller compilation.
The successful follow-up used a new evidence directory, leaving the first
failed attempt intact.

The filtered candidate’s direct synchronized member and inherited-final flag
remain diagnostic inputs to this checkpoint. A source-model GREEN does not
prove that filtering/reparse retains those flags, and the selected-entry
comparison does not claim complete API closure. Previous raw-modifier
experiments and their evidence remain untouched.
