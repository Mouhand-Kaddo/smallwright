# API 36 component inventory, notices and raw XML Pull regeneration

2026-10-12 Dubai / 2026-10-11 UTC. This follows the
[complete published-source reference](published-api-source-reference-20261012.md).
It advances full licensed API input work in issue #1; normal worker inputs,
phone state and the broader alpha acceptance remain unchanged.

## Verified results

All 4,161 published Java entries have individual source/prologue hashes and
class bindings. The 4,110 class-emitting files have one unambiguous matching
public-signature contribution across 22 repositories; the other 51 are
package documentation. Their 6,222 classes plus the five actual Metalava
annotation helpers account for all 6,227 reference classes exactly once.
Signature contributions are provider evidence, not the complete SDK build's
raw-source lineage. The HTTP investigation demonstrates why that distinction
matters: Connectivity contributes signatures while Cronet supplies raw code.

| Visible source category | Java files | Bound classes |
| --- | ---: | ---: |
| Apache header | 2,679 | 4,534 |
| GPL with Classpath Exception header | 1,223 | 1,339 |
| ICU/Unicode pointer | 110 | 214 |
| W3C header | 36 | 36 |
| Chromium BSD pointer | 14 | 26 |
| SAX public-domain declaration | 35 | 35 |
| XML Pull pointer/headerless | 5 | 5 |
| Package documentation without prologue | 51 | 0 |
| Other Android source without header | 5 | 7 |
| Copyright only | 1 | 1 |
| Generated marker | 2 | 25 |

This reproduces the earlier finite header inventory without treating category
membership as legal clearance. Five final annotation classes are additional
to the table; their five AOSP sources have Apache headers.

Fourteen verbatim notice/license files (158,600 bytes) are now retained under
`toolchain/api36-component-notices/`, with exact URLs/commits/hashes in
[the tracked component manifest](../../toolchain/platform-api36-component-inventory.json).
Twelve are aggregate component texts retrieved from pinned AOSP commits; eleven
also match prior local copies. The extra libcore `ojluni/src/main/NOTICE`
closes a concrete missing-text input in the existing license definition.
The other two texts are official XML Pull and Chromium BSD licenses.
This is a partial component evidence bundle, not a complete release package.
After verification the fourteen new texts were moved from their initial capture
location under `toolchain/licenses/` to `toolchain/api36-component-notices/`.
Both APKs automatically package the former directory, so using the latter keeps
these unselected API inputs outside current APK assets. Original capture
receipts remain immutable in ignored storage; the tracked retrieval manifest
records both original and current locations, with the same file hashes.

## Two source/notice investigations

A cost-efficient background researcher used primary sources and wrote the
[XML Pull](../research-api36-xmlpull-source-license.md) and
[HTTP](../research-api36-http-source-license.md) notes. Parent independently
refetched and checked every one of the nineteen raw source hashes, both
specific license texts, the XML Pull filegroup/r2 tag, Cronet metadata and the
Connectivity/Cronet build edges. The original researcher initially described
missing direct license attachment on the HTTP filegroup; parent required a
check for package-default inheritance before accepting that limit. The amended
note distinguishes an actual inspected metadata gap from an assumption based
only on an absent `licenses:` property.

XML Pull: five raw API files at libcore r2 commit
`fff4fcc0cf7f080cf2511cbb57561482b13b218f`, explicitly listed in
`non_openjdk_javadoc_xml_files`. Official distribution license SHA-256
`4eeafa29d1d9a0cdbdad0f5178cd553527329b57b5808ffe912cb9c698343612`,
370 bytes. Parent confirms the distribution statement covers API source,
compiled code and documentation and excludes tests. This does not assign the
libcore root's GPL aggregate to XML Pull or clear the entire SDK archive.

HTTP: fourteen raw files under Cronet r2 commit
`3d00a6f18075427606068c038ea2695053213440`. Connectivity commit
`627eb3c63e7c7cb3cb9f8ea319e4bb1676079c3a` uses
`framework-connectivity -> CronetJavaDefaults -> httpclient_api_sources`.
The source filegroup additionally contains HeaderBlock outside the selected
SDK entries. All fourteen selected headers retain the Chromium BSD pointer.
Chromium 126.0.6423.0 LICENSE SHA-256
`368cca1106be99d39ecd32a38d8305585d802a475effb66380b91ffc9bcf709b`,
1,536 bytes. The much larger Cronet aggregate LICENSE was checked as evidence,
not copied into this selected-header notice directory. Its unrelated runtime
implementation/dependencies are not packaged. Raw HTTP regeneration remains
untested; exact SDK artifact build lineage remains separate.

## Actual raw XML Pull regeneration

The five byte-identical raw r2 files were isolated, then stock installed
Metalava generated exactly five Java stubs. Javac compiled exactly those five
classes. Every parsed class declaration matched the corresponding SDK class.
All five outputs were inserted into the existing complete published-source
reference; every other class's bytes stayed unchanged. The unchanged strict
comparator still passes all 6,227 class names/public-protected declarations,
with zero missing, extra or differing classes. No API was dropped.

| Stage | Exit | Seconds |
| --- | ---: | ---: |
| Stock Metalava raw-source stub extraction | 0 | 6.208 |
| JDK 21 actual stub compilation | 0 | 1.068 |

Extraction and compilation use only the existing independently source-built
6,227-class baseline and actual AOSP annotation support. SDK binary classes
never feed either compiler classpath. The complete published-source reference
is used only for assembly/comparison; it remains SDK-derived for the other
components. These results establish a reproducible raw-source declaration route
for the five XML Pull files, not a canonical complete AOSP build, raw-body
compilation/runtime or a full independent API replacement.

Tool/runtime pins: 136; annotation class pins: 59, checked before/after.
Heap maximum 1 GiB; Metalava initial heap explicitly 64 MiB. Captured Metalava
log is empty; javac retains its source/target-8 deprecation warnings. Timings
are host process timings, not inference/build latency on a phone.

Candidate `complete-reference-with-raw-xmlpull.jar`: 4,918,215 bytes, SHA-256
`015162e61dc185ac7c77bd12208921de208efa5f7645e35dda6a1c0128f2e70b`.
Four replaced class byte sequences differ from the preceding reference;
XmlSerializer's independently compiled output is byte-identical. All five
origins were verified, including that identical output.

## Preserved failures and authoritative evidence

Ignored root: `toolchains/platform-api36-source-probe/component-inventory-20261012/`.

- Full inventory `inventory-bfcf28a49e4c42edbeca4ae10cce22b7.json`, SHA-256
  `4ad6f2671054e0ab24dc8d97784082074c7ce05f58294040dc292fc0f59c56e6`:
  per-entry source/header hashes, class names, signature-provider membership,
  category/provider matrix and five final helper sources.
- XML Pull retrieval `xmlpull-5527a7700a5546b9bea0bdc313edbb41/`:
  seven exact fetches; original parent semantic verifier failed because it
  expected lowercase `public domain` and `except tests`, while the actual text
  uses `Public Domain` and `*except* for tests`. Exact hashes had already
  matched. `parent-verifier-failure-01.json` preserves the failure; corrected
  `parent-review-02.json` verifies the same unchanged source/license bytes.
- Notices `notices-f117e5056d784e72b7369058e39b7708/receipt.json`:
  twelve exact AOSP retrievals and eleven prior-copy comparisons.
- HTTP `http-review-ac671011109a46969b5d7cc70e1c6c5d/`:
  twenty raw-source/metadata/license retrievals plus two build-edge retrievals;
  exact source/header/filegroup checks and inspected default-license properties.
  A first ad hoc reader looked for CronetJavaDefaults in the wrong repo file
  and failed; the corrected parent check verifies Connectivity's actual edge.
- Raw regeneration first output `xmlpull-regeneration-e84d1e358fb44e67820b05ecfac1e9db/`:
  JVM startup failed, exit1/0.022s, because wrapper's default initial heap
  exceeded the explicit1GiB maximum. No extraction or compilation passed there.
  Original runner/receipt/log retained. Set both initial and maximum heap,
  add exact annotation class pins, then use a fresh output directory.
- Successful raw regeneration `xmlpull-regeneration-9ad395106cd043b9847a59518b87f134/receipt.json`:
  source/classpath/tool/log pins, actual PIDs/commands/exit codes/timings,
  generated source/class origins, complete candidate and strict comparison.
- `root-independent-review.json`: passed at `2026-10-11T22:10:28.252001+00:00`.
  Parent rechecks every source/class binding, nineteen raw files, fourteen texts,
  tool/support/output pins, five exact class origins and unchanged other6,222
  class bytes. Original compiler PIDs absent. No worker promotion/rights clearance.

Original exec sessions47780 and74358 are terminal exit1; notice94514,
HTTP37855/build-edge70433, corrected regeneration60177 and independent
review65708 are terminal exit0. Original records were not overwritten or
restarted solely because an observation expired. No app APK, physical test,
model request, credential access or benchmark row occurred.

After pinned ignored inputs exist, replay creates fresh regeneration UUIDs:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 toolchains/platform-api36-source-probe/component-inventory-20261012/regenerate_xmlpull.py
python3 scripts/compare_platform_abi.py --reference toolchains/android-sdk/platforms/android-36/android.jar --candidate toolchains/platform-api36-source-probe/component-inventory-20261012/xmlpull-regeneration-9ad395106cd043b9847a59518b87f134/complete-reference-with-raw-xmlpull.jar
```

Final-location verification also passed at `2026-10-11T22:18:29.948975+00:00`
(`final-notice-location-review.json`): all fourteen file hashes and 158,600
bytes match at their final paths, outside both current APK asset roots.
All 52 local file links passed. Full staged `git diff --check` reports upstream
trailing whitespace/blank EOF lines in the verbatim AdServices, AppSearch, Uwb
and XML Pull texts; those bytes are deliberately preserved. The scoped staged
documentation/metadata whitespace check passed on all eight non-notice paths.
No Gradle build/quality run was required: tracked changes are research metadata,
notices outside APK assets and documentation; production code/dependencies
are unchanged. Actual Metalava/javac/runtime verification is recorded above.

## Exact tracked paths

- `docs/STATUS.md`
- `docs/research-sdk-api-license.md`
- `docs/research-api36-xmlpull-source-license.md`
- `docs/research-api36-http-source-license.md`
- `docs/validation/api36-component-provenance-20261012.md`
- `toolchain/platform-api36-component-inventory.json`
- `toolchain/api36-component-notices/PROVENANCE.md`
- `toolchain/api36-component-notices/chromium-BSD-LICENSE`
- `toolchain/api36-component-notices/external-conscrypt-LICENSE`
- `toolchain/api36-component-notices/external-conscrypt-NOTICE`
- `toolchain/api36-component-notices/external-icu-LICENSE`
- `toolchain/api36-component-notices/frameworks-base-NOTICE`
- `toolchain/api36-component-notices/libcore-LICENSE`
- `toolchain/api36-component-notices/libcore-NOTICE`
- `toolchain/api36-component-notices/libcore-ojluni-src-main-NOTICE`
- `toolchain/api36-component-notices/packages-modules-AdServices-NOTICE`
- `toolchain/api36-component-notices/packages-modules-AppSearch-NOTICE`
- `toolchain/api36-component-notices/packages-modules-Bluetooth-NOTICE`
- `toolchain/api36-component-notices/packages-modules-OnDevicePersonalization-LICENSE`
- `toolchain/api36-component-notices/packages-modules-Uwb-NOTICE`
- `toolchain/api36-component-notices/retrieved-20261012.json`
- `toolchain/api36-component-notices/xmlpull-LICENSE.txt`

Next: finish actual per-component notices/rights and corresponding-source
packaging for all categories, especially headerless/generated/documentation
sources and GPL obligations. Keep the exact full comparison and validate
resources and complete inputs on the phone before normal-worker promotion.
The broader independent raw graph, other compiler license/notice duties and
full physical alpha acceptance remain open.
