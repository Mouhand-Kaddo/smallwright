# Manual Build origin, issue #20

Part of #2,
bounded by #20.
This checkpoint does not establish private alpha completion.

## Host vertical evidence

The approved seams are the existing `ProjectSourceStore` package API,
`GenerationRun.Control` completion boundary, and normal MainActivity Build UI.
Tests use actual temporary files and ChatStore/ProjectRegistry metadata.
JVM receipt tests use explicit verification-only byte fixtures; they do not
claim Android compilation, signing, installation, or generated app execution.

Before source-origin persistence, the new source seam delegated to unchanged
`writeSource` behavior and its read accessor returned no origin. The first test
compiled and failed at the runtime assertion:
`Committed manual source has no durable captured-chat origin` (one test, one
failure). After source persistence, one test passed. Before candidate artifact
binding, the completion seam did not persist a candidate. The second test
compiled and failed at `Published manual artifact has no immutable origin
binding` (two tests, one failure); candidate persistence then passed both tests.
Temporary interface scaffolding was removed in these GREEN implementations.
There was no missing-API compilation failure reported as a runtime RED.

The subsequent boundary suite executed **10 tests, zero failures**, covering
restart durability, foreign chat/project rejection, stale editor rejection,
whole-snapshot changes, corrupt records, symlinked metadata storage, candidate
metadata failure before publication, actual receipt-write IO failure, Stop,
immutable artifact binding, foreign artifact files, and unknown legacy origins.
These additional guards passed existing implemented checks; no failure was
invented for them.

After the normal Build wiring, the full offline run passed in **3m17s**:
223 controller tests (including these 10), 57 worker tests and four protocol
tests, all with zero failures/errors. Formatting, `qualityCheck` (normal module
lint), controller assembly and controller test assembly passed in the same run;
165 tasks, 84 executed and 81 up to date. The unit test tasks executed; those
counts are not a cached-only claim. Log:
`/tmp/manual-origin-final-quality.log`.

```bash
source scripts/env.sh
./gradlew spotlessApply :controller:testDebugUnitTest :worker:testDebugUnitTest \
  :protocol:testDebugUnitTest qualityCheck :controller:assembleDebug \
  :controller:assembleDebugAndroidTest \
  --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

All executions used:

```bash
source scripts/env.sh
./gradlew :controller:testDebugUnitTest \
  --tests dev.smallwright.controller.ManualBuildOriginTest \
  --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

Logs: `/tmp/manual-origin-red1.log` (37s, failed assertion),
`/tmp/manual-origin-green1.log` (31s, passed),
`/tmp/manual-origin-red2.log` (30s, failed assertion),
`/tmp/manual-origin-green2.log` (33s, passed),
`/tmp/manual-origin-boundaries.log` (28s, passed).

## Persistence and publication rules

Manual source metadata resides in
`projects/<project>/manual-build-origins/<revision>.bin`; source revisions,
signers, receipts, and direct `app-vN.apk` / `counter-vN.apk` paths are preserved.
Each bounded record stores project/chat/build identity, actual source revision,
allocated APK version, canonical framed SHA-256 of every admitted source file,
and an optional candidate APK SHA-256. Record checksum, identity, bounds,
symlink checks, atomic replacement, file sync and directory sync guard storage.
There are at most 1,024 records per project, at most 512 bytes each; reaching the
limit fails before a new source stage/HEAD promotion and preserves prior data.

The existing exact snapshot staging API persists source and origin before
promoting HEAD. Reads validate the project's actual existing chat and source
bytes; they never guess or substitute a chat. A candidate APK binding is written
only inside the existing completion ownership guard. The authoritative signed
receipt is published last. Candidate metadata alone does not establish success;
verified origin recovery additionally requires the exact current receipt,
version and unchanged APK bytes. Metadata or receipt IO failure cannot publish a
new verified origin. Stop before completion leaves saved source provenance and
no new artifact claim.

Existing artifacts without manual metadata remain origin-unknown. The source
accessor currently validates a retained record only against the current source
revision; historical noncurrent origins are retained but fail the current-source
check. The signed receipt remains the current artifact pointer. This is not a
new history browser, recovery migration, or repair framework.

## Physical test harness preparation

`ManualBuildOriginProbe` uses normal MainActivity New project/New chat/Build
controls and the actual worker/compiler/signature/receipt. An explicitly assisted
executor latch permits chat browsing before queued build work begins. One
reserved observation owns the newly created project; its RED artifact is retained
without an invented origin. GREEN uses that same project's next source/version,
and `verify` reads the retained successful case without another build. Previous
project/chat selection is restored. There is no model request or generated app
installation.

Before MainActivity wiring, formatting and controller/test assembly passed in
1m11s (`/tmp/manual-origin-physical-red-build.log`). Initial harness identities:

| Artifact | SHA-256 |
| --- | --- |
| `controller/build/outputs/apk/debug/controller-debug.apk` | `c5cbfd8713726545d32d11cdbd042f914e493085cf3ccd50c57544f61780ecaf` |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `548150e8916c0d40be0a11989422b8b783091910f44de7a2f9acf26d7fc1d8db` |
| Unchanged `MainActivity.java` | `9413672af1c24e00577d679910b5b6b4653616c01d616af34c8ae0794d4a960d` |

## Physical RED on the authorized A33

The parent verified the physical connection and exact harness pair, updated only
the controller/test harness, and ran one fresh normal-UI RED observation
`82ed8664c20f45b1b118fa66548eb9f4`. The actual source 1/version 1 phone build
compiled, signed and published its receipt. The probe then failed its intended
origin assertion at `EXPECT_DURABLE_CAPTURED_ORIGIN_red` (ADB host exit 0,
Android instrumentation code 0). This is genuine missing-provenance evidence,
not a failed compiler or test-tool configuration.

| Pin | Observed identity |
| --- | --- |
| Project | `appdfcf95d94bbe45449db689a9c0147d34` |
| Captured chat | `e3fd4b25aa634d6b87a57307c77178e5` |
| Browsed chat | `c1ef040786e64b50b4b1287e01e91083` |
| Main source SHA-256 | `08ec3e81cb09aaa89561daaae2cbe0b010fb716a56b2adf4d92eaca9c6be7b00` |
| Project signer fingerprint | `7e11524e6d015405a3debc65eb8aebe336374beffeb65b3eb4f8b74c0a7253b0` |
| Generated APK SHA-256 | `9e51b031661b791e6016c3a87a5a7f2967348454d44ce55f028cc1a4ed7f80c9` |

The retained generated APK is phone-local:
`/data/user/0/dev.smallwright.controller/files/projects/appdfcf95d94bbe45449db689a9c0147d34/artifacts/app-v1.apk`.
The parent independently matched the source/APK hashes, found origin 1 absent,
confirmed `redRecorded` and restored selection, verified both harness pins,
found worker scratch absent, and stopped its own controller/worker processes.
No generated package installation occurred. The first independent observer
incorrectly expected `pm path` host exit 0 for an absent generated package;
the corrected observer accepted its actual host exit 1. That observer failure
is preserved; there was no new build or reseed to correct it.

Raw retained evidence is under the ignored root
`toolchains/alpha-manual-origin-20261012/`: `red-01.txt`,
`red-installed-01.json`, and `red-independent-review-02.json`. The failed first
observer traceback remains in the root tool transcript; its explanation is also
recorded in `red-independent-review-02.json`. MainActivity wiring followed this
physical RED; the original source, APK and unknown origin were preserved for the
same-project GREEN.

The GREEN harness pair prepared after host verification:

| Artifact | SHA-256 |
| --- | --- |
| `controller/build/outputs/apk/debug/controller-debug.apk` | `4e4dc51ddc271d4ab4f7ce00491a8a1e366ab47e6da489c51af947a43cbb9dfe` |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `548150e8916c0d40be0a11989422b8b783091910f44de7a2f9acf26d7fc1d8db` |
| Updated `MainActivity.java` | `ce5f7931cdbccde16638c5ae10aec0faddead4e6407b71da7d7d09d72db26c3a` |

The physical probe and instrumentation route did not change between RED and
GREEN preparation. Other implementation pins are
`ProjectSourceStore.java`: `89de4e8339d28bc57954d8d8d355b4e621f1dbd08806dca7cf20432b265fcdd5`;
`ManualBuildOriginStore.java`: `8e32a0014ff369d696fd46672379b5537d1c3f246450b84ad1b3256ddd8712d8`;
`ManualBuildOriginProbe.java`: `a065b71a3648f04adcf115b0086d955ea95e2ab0a6dd3640dec2f4652a96833e`;
`ArtifactPolicyInstrumentation.java`: `fcb81043fe13601d89f4dbb247b2e00e1ba8c0d99aab2472dbcf676fbabd1d4b`.

## Physical GREEN on the same A33 project

The parent verified and installed the exact GREEN harness pair above. The same
reserved observation then passed `green` (ADB host exit 0, Android code -1),
using the same project and captured/browsed chats from RED. Its one actual phone
Build committed source 2/version 2, preserved the project signer and legacy
source 1 APK, and recovered the same captured origin after an activity/store
reopen. Source 1 remained origin-unknown; there was no retrofit or reseed.

| Pin | Observed identity |
| --- | --- |
| Main source SHA-256 | `98c2c3a02ab50179b505bcac94a7dc0821361515b99db2b4c8d35108bf5676c4` |
| Complete canonical source snapshot SHA-256 | `15e659ccee135ab2a70d9e63c3767a12691a923654af04452747577d721f3dc0` |
| Build ID | `manual_6e341811873c426eb910c4717472d4fc` |
| Generated APK SHA-256 | `42510d96619c875c107f75cd8f20790f07d0aa1217489f8959482851f0f9709f` |
| Project signer fingerprint | `7e11524e6d015405a3debc65eb8aebe336374beffeb65b3eb4f8b74c0a7253b0` |

The new generated APK remains phone-local beside retained version 1:
`/data/user/0/dev.smallwright.controller/files/projects/appdfcf95d94bbe45449db689a9c0147d34/artifacts/app-v2.apk`.
Raw evidence: `green-01.txt` and `green-installed-01.json` under the same ignored
root case directory. The phone already held the same tested test APK bytes;
the harness update preserved data, and no generated APK was installed.

Before a separate `verify` phase, the parent explicitly force-stopped its
controller/worker and confirmed both process IDs absent in
`fresh-process-before-verify-01.json`. The separate `verify` phase then passed
(ADB host exit 0, Android code -1), recovering the exact source 2/version 2,
captured chat, build ID, source snapshot hash, APK hash and project signer in the
fresh controller process. It performed no new build. Raw `verify-01.txt` and
`verify-handle-01.json` retain this evidence. A repeated `green` on the same
reserved observation was rejected at `GUARD` (host exit 0, Android code 0),
without a second build; see `green-duplicate-01.txt` and
`green-handle-duplicate-01.json`.

Four neighboring Android checks passed (code -1 each): queued Stop, lifecycle
Stop, active chat navigation, and completion. The host observer then failed
because its broad `FAILED` text check also matched the completion probe's
expected success statement, `real receipt write failure stays FAILED`. This
was an observer error after four actual passes, not a production failure. The
original host script and four raw results remain in `run_neighbors.py`,
`neighbor-results-01.json`, and the root tool transcript. The parent independently
verified the exact result markers without rerunning those four checks. Only the
two remaining saved-artifact neighbors were then executed: prior ECJ Stop ready
recovery and prior screen-lock ready recovery. Both passed (host exit 0, Android
code -1), recorded in `neighbor-remaining-results-01.json` and their raw
`neighbor-prior-ecj-stop-ready-01.txt` /
`neighbor-prior-screen-lock-ready-01.txt` streams. Thus six Android neighbor
checks passed; the first host observer failure remains an explicit separate
failure, not a silently rerun result.

The parent's final independent review passed in
`final-independent-review-01.json`, matching both retained source and APK
hashes, the decoded project/chat/build/source/artifact origin metadata, all tested
source pins and installed harness hashes. Origin 1 remained absent. The final
origin record SHA-256 was
`f5cfe2674cc5588104c17df73ddf0b9b2c1378634270343f51d4477a2845a0b6`.
All probe selections were restored, the prior four project/chat acceptance APK
hashes matched, worker scratch was absent, and the parent's own processes were
stopped. The generated package remained uninstalled; no model request, generated
APK/source export, data clear or generated installation occurred in this case.
This is bounded A33 provenance evidence, not an S23 pass, functional generated
app acceptance, or the two-device/fixed-20-request private alpha suite.

## Post-review source digest sharing

The frozen two-axis review at `201bed7` reported no documented standards
violations and no actionable spec findings. It identified one maintenance
concern: the manual and compiler-repair provenance stores repeated the same
canonical source digest algorithm. Review remediation centralizes only that
domain operation in package-private `SourceSnapshotDigest.compute(Map)`.
`ProjectSourceStore` and `RepairOriginStore` call it directly; the redundant
manual/repair source digest loops are removed. Sorted paths, UTF-8 path bytes,
big-endian path/content lengths, exact source bytes and SHA-256 framing are
preserved. Both record serialization/checksum formats and the retained physical
origin/source/APK bytes remain unchanged.

This is a behavior-preserving review refactor. Existing store/repair/guard tests
provide meaningful coverage; no invented RED or implementation-mirroring tests
were added. The initial checkpoint's real RED/GREEN and 284 executed host tests
remain their dated evidence. A separate final offline run passed in **3m3s**
(`/tmp/manual-origin-shared-digest-quality.log`): 223 controller tests executed
again with zero failures/errors, including ten manual-origin and nine
repair-origin tests plus existing generation/approval/receipt/recovery guards.
Unchanged worker (57 tests) and protocol (four tests) tasks were **UP-TO-DATE**;
they are cached results, not new executions. Formatting, `qualityCheck`,
controller assembly and controller test assembly passed in that same run;
165 tasks, 21 executed and 144 up to date.

```bash
source scripts/env.sh
./gradlew spotlessApply :controller:testDebugUnitTest :worker:testDebugUnitTest \
  :protocol:testDebugUnitTest qualityCheck :controller:assembleDebug \
  :controller:assembleDebugAndroidTest \
  --offline --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

Post-refactor harness/source pins:

| File | SHA-256 |
| --- | --- |
| `controller/build/outputs/apk/debug/controller-debug.apk` | `f3c138eec7d55e8745cb441b7c43c77c5a74a5a193b2210f69c2d2fa73e0af83` |
| `controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk` | `548150e8916c0d40be0a11989422b8b783091910f44de7a2f9acf26d7fc1d8db` |
| `ProjectSourceStore.java` | `b1005896aabcd48b400709838ba88eaf718ca73ecf656c1902c61c4d8cc953c4` |
| `ManualBuildOriginStore.java` | `a922d1e6e3195dfccdbad9028708b728a6527736c79dbd380e30af291b98c5a8` |
| `RepairOriginStore.java` | `865dcbb334f17ba92902334cda3a68f82b8e21a71bdbef75f5b9fa070467729c` |
| `SourceSnapshotDigest.java` | `35179af76132483906e98a7d7da669bced1c5e1200a395544d8215df86d42f07` |

MainActivity and the physical probe/route source pins did not change. No phone
action, generated-app build/installation or model request occurred in the host
remediation.

The parent verified and updated the exact post-refactor controller with data
preserved. The already installed test APK matched the unchanged test hash and
needed no update; see `refactor-installed-01.json`. The retained phone `verify`
phase passed (host exit 0, Android code -1) with the same source 2/version 2,
captured chat, build ID, source snapshot digest `15e659...`, generated APK
`42510d...` and signer as the original GREEN. Old origin 1 remained absent. This
establishes reading compatibility for actual pre-refactor persisted metadata;
it is a retained-record check, not another fresh generated-app Build or a new
functional/generated-installation pass. Full identities remain in
`verify-refactor-01.txt` and the tables above.

Post-refactor active chat navigation and generation completion neighbors both
passed (host exit 0, Android code -1), retained in
`refactor-neighbor-results-01.json` and their raw streams. The active chat check
uses a real seeded repair origin and cancels its repair preview; the completion
check exercises receipt IO failure and Stop. These are explicit fixture/guard
checks, not another model repair or generated compiler build. The original
physical RED/fresh Build GREEN evidence remains separately dated above.
The parent's final post-refactor independent review passed in
`refactor-independent-review-01.json`: the same origin record SHA-256
`f5cfe2674cc5588104c17df73ddf0b9b2c1378634270343f51d4477a2845a0b6`, both
retained source/APK hashes, seven source pins, four installed harness pins and
the prior four project/chat APK hashes matched. Legacy origin 1 remained absent;
verification and selection restoration were recorded, worker scratch was absent,
and the parent's own processes were stopped. No generated source/APK bytes were
exported and no new generated build, model request or generated installation
occurred. This completes the bounded review remediation; all broader alpha and
distribution gates remain open.


## Retained evidence after worktree cleanup

The `implement-spec` worktree cleanup removed the clean owned implementation
worktree after verifying non-secret host APK/report/log backups. The feature
branch remains; the seven reviewed source files are on the integration branch.
Historical worktree paths above identify where the commands actually ran.
Current available harness APKs are
`toolchains/alpha-manual-origin-20261012/refactor-controller-debug.apk` and
`refactor-controller-debug-androidTest.apk`; their full hashes are unchanged
from the post-refactor table. Earlier RED/GREEN APK copies remain in that case
directory too. Generated APKs remain only on phone.

Final XML, lint reports, host APKs and original `/tmp/manual-origin-*.log` copies
are under `toolchains/alpha-manual-origin-20261012/host-evidence-final/`.
`host-evidence-backup-manifest-01.json` records original paths, backup paths,
SHA-256 and byte counts, and includes the separate project/chat host backups.
Parent re-parsed the preserved XML: controller223/worker57/protocol4, zero
failures/errors. Execution versus cached status still comes from the dated logs,
not XML counts alone. The final review is linked from
[the checkpoint review](code-review-alpha-20261012.md).

`worktree-cleanup-01.json` records removal of all three owned implementation
worktrees without force. Only the owned symlink names were unlinked; their
signing/toolchain/JNI targets were untouched and no signing bytes were read.
User dirty/untracked files and all phone data remain intact.
