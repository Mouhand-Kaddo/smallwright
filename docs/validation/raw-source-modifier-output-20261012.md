# Raw source modifier output checkpoint — 2026-10-12

The third actual run passed all three selected method comparisons. The two
earlier failures remain preserved below. Parent independently verified the
candidate against the pinned baseline and SDK and retained all three cases in
the root worktree; this is a bounded host experiment, not full API completion.

This test-only checkpoint tracks issue #31. It extends the pinned raw-source
model experiment from #30 with an isolated projection of exactly three method
changes: native on `Log.isLoggable`, a direct synchronized
`ServiceStartNotAllowedException.getCause`, and final on the inherited
`SpannedString.length` implementation. It does not change production code,
Metalava, the API filter, the candidate baseline, or the ABI comparator.

The first #31 scoped run did not reach corrected source emission. Pinned inputs,
the extractor compile, all three raw model checks, baseline RED checks, and all
three one-token mutation negatives passed. It then failed because the runner
expected the synchronized source stub to end in `}\n`, while the exact pinned
stub ends in `}\n\n`. The correction now requires that exact pinned suffix and
inserts before the final class brace, preserving the suffix bytes. This source
correction had not yet been rerun at that first checkpoint. Corrected owner compilation, selected
attribute preservation, caller compilation, and SDK-last comparison remain
unverified at that checkpoint.

The failed run's preserved evidence is under
`toolchains/platform-api36-source-projection-20261012/integration-50a4000c439e498abf6d9a52b67b8e1f/`.
Its terminal failure is in `runner-failure.txt` (SHA-256
`1b4e12b1b2644129c725d8efcf2daa6403ff8ea82309bc91599af82fcdcf7f66`), and
the partial receipt is `receipt.json` (SHA-256
`75e89c9b6d2a5cd017c4c2c199150a497e1f6a1860ef85ab863b52878a31b206`). It
reached no corrected candidate, caller compilation, or SDK access.

After correcting the exact EOF boundary, the second scoped run passed source
model checks, baseline RED checks, and all mutation negatives, then failed at
`compile-corrected-owner-stubs`. The new native declaration still had the
filtered stub's method body, which javac rejects for a native method. Compiling
the complete copied Log stub also reports package-private
`android.annotation.Nullable` and `NonNull` types as inaccessible from
`android.util`. The compiler reported 40 errors and 4 warnings. No corrected
candidate JAR, caller compile, or SDK comparison was reached. Preserve this as
a real failure; there was no GREEN result at that second checkpoint.

The second run is retained under
`toolchains/platform-api36-source-projection-20261012/integration-e1d672ba46fd499dac2262ced3869de4/`.
`runner-failure.txt` has SHA-256
`276e26c7ab135069c7774853c404e8fa03d4f6324feb9c5ca58e5d4adceabf86`, the
full compiler output is in `compile-corrected-owner-stubs.log` (SHA-256
`25981c64274cf7053827c05a9e329ddcc70842044d551c9001756f24edbae1be`), and
`receipt.json` has SHA-256
`9d1056401de4b46ff0b94e447527ab6526efd3a69b31b8953256c467f060a353`.

A source-only follow-up retained the pinned Log parameter annotation and
changed the native stub body to a semicolon. Owner compilation used separate
annotation support classes from the existing experiment's pinned Metalava
files at commit
`9809e0d70a5b807be3d7070d51f9c4714f2fdcb4`:

| Source | Git blob | SHA-256 |
| --- | --- | --- |
| `stub-annotations/src/main/java/android/annotation/NonNull.java` | `c826461be7ee6d1209a7470bada9e91d1f532765` | `abfade84be1f981581becbedee722e036c4469f3593f9924311df7803ab5d3ff` |
| `stub-annotations/src/main/java/android/annotation/Nullable.java` | `93b73e3de5ad45606241749e69c1f08cfb08cf3d` | `7827195fab5740747a47666b44ea4c7bf066f904de88a844652670ea9f2cf460` |

Both pinned source prologues identify Apache-2.0; both declare CLASS retention
and METHOD/PARAMETER/FIELD targets. The pinned Metalava root has no tracked
top-level `NOTICE`; the separately copied frameworks/base `NOTICE` remains
recorded above. The support classes were compiler-only inputs: the candidate
JAR kept the original annotation entries and replaced only the three selected
owner class entries.

The third scoped run completed successfully in 61.968 seconds. The command
output is retained in `runner-success.txt` (SHA-256
`fb6755b9964b3f95ed678172c0c8fb2202019ff6eb7f915691f5e89321b46e99`); the
receipt is `receipt.json` (SHA-256
`c772e01c51fb2cdda9bd32668f9cac8485e28e19ee72d0e9d51aafab44c1291d`). The
corrected candidate JAR is `corrected-candidate.jar`, SHA-256
`0f7fff850165664967e142835853d62286308155eadde27e65e798ea5b7fcd34`. The
only changed entries are `android/util/Log.class`,
`android/app/ServiceStartNotAllowedException.class`, and
`android/text/SpannedString.class`. Candidate methods had flags `0x109`,
`0x21`, and `0x11`, respectively, and each full selected method tuple
(including attributes) equaled the pinned API 36 reference. The pinned SDK
hash was `d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`
and was opened only after the caller compiled against the candidate alone.
All three raw-source mutations failed their same expected-flags assertions,
and existing selected method attributes stayed unchanged through owner
recompilation. Javac emitted only source/target 8 deprecation warnings and the
expected missing boot-classpath warning; there were no compilation errors.

This is a bounded test-only source-to-projection checkpoint, not whole-API
closure or licensing clearance. The strict comparator, production/API
generation path, and physical device were not run for this checkpoint.

On 2026-10-13 the parent independently read the actual receipt and classfiles:
the complete JAR entry inventory matched, exactly three entries changed,
original annotation entries remained byte-identical, and all unselected
methods/fields/class attributes/flags/superclass/interfaces remained unchanged.
The three complete selected method tuples equaled the pinned SDK without
masks; all three genuine mutation assertion failures and the candidate-only
caller class were present. The first parent inspection omitted the parser's
required ZIP-entry-name argument; that read-only observer error is preserved,
and the corrected invocation used the actual entry name. No rerun or input
change was needed. Root ignored evidence is
`toolchains/platform-api36-source-projection-20261012/`, including all three
case directories, `parent-review-01.json` and `parent-observer-failure-01.json`.

The test-only runner is:

```bash
PLB_RAW_SOURCE_MODIFIER_INPUT_ROOT=. \
PLB_RAW_SOURCE_MODIFIER_PROJECTION=1 \
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=scripts \
python3 -m unittest test_raw_source_modifier_projection
```

The runner pins the existing JDK, packaged Metalava, filtered candidate and
source stubs, four raw AOSP source files, Git blobs, license prologues, and
NOTICE. It checks the same one-token model mutation negatives before writing
corrected source. The Log declaration keeps the pinned projection's
`@android.annotation.Nullable` parameter syntax after the raw model validates
owner, method, descriptor, erased types, names, and modifiers. Existing
selected method attributes must remain identical through recompilation. The
new Service method is checked against the full API 36 method tuple at the final
SDK-only comparison step. The caller compiles against the candidate alone
before the API 36 reference is opened.

On completion, record the exact command, exit code, retained logs and receipt
directory, plus whether each stage passed. If any selected attributes or SDK
tuple differs, preserve the failure and report the actual gap; do not weaken
the assertion or claim a source-derived correction. This checkpoint does not
close whole-API provenance, licensing, or device validation.
