# A33 counter_04: failed pre-request baseline

The fresh counter_04 baseline failed its normal local Build on 2026-10-13.
Source revision 1 was saved, but the project has no generated APK or verified
receipt. No lifetime-tap model request, installation or functional checklist has
run. The original attempt remains failed; its attempt flag and all data remain
preserved. This is pre-request setup evidence, not another benchmark result.

## Actual phone evidence

Physical device: Samsung SM-A336E, Android 16/API36 ARM64. The fresh preflight
and exact four installed harness hashes matched. Awake/unlocked and no active
worker build were checked before the original phases. Preparation and deliberate
unprepared-Build/duplicate-preparation guards passed. Source checkpoint was
`6445563` on `integration/private-alpha-20261012`.

The probe authored a test fixture through the ordinary editor, then clicked
`Build and sign app locally` once. It did not seed ProjectSourceStore or call a
model. The fixture adds native Increment and Reset buttons around a persisted
main counter; the lifetime-tap request and frozen acceptance asset are unchanged.

The original observer returned host exit0 but instrumentation code0 with
`A33 counter04 FAILED stage=VERIFY_AUTHORED_BASELINE_R1_READY category=IOException`.
This is a failed instrumentation result. Its handle is terminal. Fresh read-only
metadata independently verified HEAD1, `baselineBuildAttempted=true`, no
`baselineBuildComplete` or baseline build ID, an empty artifacts directory and
no `verified-artifact.properties`. The original source verification had already
persisted the R1 snapshot digest before receipt verification failed. The coarse
marker does not establish whether the underlying cause was ECJ, AAPT2, worker
I/O or controller I/O; no cause or fix is claimed yet.

- Project: `app17753756e1e44617af041946f2050ad3`.
- Authored main-source SHA-256: `60f5f72a8ee6b2e4b9c8e6ab6f9f1fd1a3c821b9580a35e905110cfd9c561594`.
- R1 full-snapshot SHA-256: `c045a0786305f77fa0db94b3804932505187ac353f7d757c820fdfca466bdf59`.
- Frozen request SHA-256: `c2dfa3094bc90bf3142eeb540888fc2aff30c76f36c428eb0610b1acc3028a6f`.
- Original failure output SHA-256: `d6a8345fb364683db801e9c515e16e8ca26adc59eaf91ad81bac0d60ae19d411`.

Ignored evidence is in `toolchains/alpha-goal-20261013/counter04-initial/`:
`baselineBuild-actual-01.txt`, its terminal handle,
`diagnostic-preflight-01.json` and `diagnostic-retained-metadata-01.json`.
Generated source and APK bytes have not been exported. All four installed
harness APKs remain as recorded in STATUS; production code is unchanged.

## Unchanged-snapshot diagnostic

The reviewed test-only probe was merged at `6746c1a` after the offline host gate
passed: Spotless apply29s, format/lint/test-APK assembly1m57s, zero lint errors/
77 warnings, and the frozen asset test1pass. Parent artifact review checked public
v2 signature, same certificate, min31/target36/effective test version0 and exact
ZIP inventory. Only `classes2.dex` changed; both frozen assets stayed byte-equal.
The first parent badging observer used the wrong SDK field label and stopped;
the corrected exact `minSdkVersion` observer passed on the unchanged artifact.

Only `controller.test` was updated in place, with all four hashes verified
before/after and no data clear. Actual current test APK:
`toolchains/alpha-goal-20261013/counter04-initial/counter04-diagnostic-controller-debug-androidTest.apk`,
1,080,622 bytes, SHA-256
`afdfec484702187e9e3c996e1b40db02a4da0bff979c566b2307a6ddfdf7fa63`,
public publisher certificate
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`.
Production controller/worker and worker.test are unchanged.

The one actual `diagnoseBaselineBuild` replay passed its observer assertions but
returned a genuine worker `COMPILE_ERROR`, zero output bytes. The bounded numeric
report validated against the exact authored SHA and contained one problem:
ID16777540/category10/line1, truncated=false. The pinned ECJ public constants map
that ID to `IsClassPathCorrect`; the missing declaration is not identified by
this numeric report. There was no model call, signing, receipt publication,
installation or source change. Original baseline flags and R1 snapshot remained
identical, no journal attempt was added and temporary phone files were cleaned.
The diagnostic result is not a successful baseline or functional pass.

The original diagnostic handle settled at host exit0/instrumentation code-1;
its duplicate was rejected at `DIAGNOSTIC_BASELINE_GUARD`, code0. Both handles
are terminal. Raw outputs/handles and source/installed guardset02 are retained
under the same ignored evidence directory. The original baseline failure and
source/installed guardset01 remain intact. Host logs/artifact metadata are in
`toolchains/alpha-goal-20261013/counter04-diagnostic-hostproof/`.

## Causal host reproduction and next fix

The locally authored fixture was compiled against the same worker source stubs,
pinned ECJ and compile options. Two setup failures did not execute the test:
missing ignored signing link, then missing regular compiler JAR copies. Both
logs are preserved. Approved name-only signing linkage and byte-identical pinned
compiler copies corrected only the worktree environment.

The actual lambda fixture expected-success test then failed at the same
16777540/10/1 problem as the phone; its local-fixture compiler output identified
`java.lang.invoke.MethodHandles` as missing. A separate control changed only
the two callback syntaxes to anonymous OnClickListener implementations; count,
persistence, labels, package, APIs and compiler inputs stayed the same. The
control passed one test. Neither the failing assertion nor the phone source was
changed. The unmerged test proposal is `beffbeb`; it is not a GREEN worker fix.

The first RED's Gradle XML was inspected but not copied before the control
replaced it. That preservation claim was corrected. A separately recorded RED04
reran the unchanged failing test and preserved its actual XML before any other
Gradle task. The control XML was also copied before the rerun. Parent independently
checked both XML reports and the exact numeric problem/message:

- `counter04-source-stubs-red-04.xml`: one test, one failure, SHA-256 `10d6e4ff74f5771d74bdaca95fe8fb295b85046d210f4b31fb40fcbed392a531`.
- `counter04-source-stubs-anonymous-control-01.xml`: one test, zero failures/errors, SHA-256 `aad8ca6d28759d9e7a77be30d99ce7b3d90e9df7da7bd2b9bafdb6d21d90d747`.

All raw setup/RED/control logs, XML reports and `host-causal-parent-review-01.json`
are retained in the ignored evidence directory. Host fixture diagnosis does not
replace actual phone verification of a correction.

Issue #33 tracks
the source-derived compiler bootstrap fix. Primary-source review located AOSP's
complete `core-lambda-stubs-src` source module at libcore commit
`1c599b67bcd3de5c50c79d0622e40b6de99b4cb4`. Parent independently fetched and checked
its source-group/module metadata and MethodHandles Git blobs. The module is
compiler-only; its source headers and license metadata require retained upstream
notices/source and GPLv2/Classpath exception handling. This is a proposed input
route, not an implemented fix or general licensing clearance. Source inventory
and implementation are underway; no bootstrap artifact was promoted to the phone.

Do not export phone source, clear the old attempt flag, remove lambdas from the
frozen failed baseline or relabel it as successful. The correction requires actual
host GREEN and phone checks on unchanged source, then an independently identified
fresh normal baseline. The lifetime-tap model request remains unsent.

Counter_04 native baseline and the frozen request remain pending. The A33 still
has two supported functional passes, terminal Reset and Step failures and sixteen
unrun requests; full two-device and human acceptance remain open.
