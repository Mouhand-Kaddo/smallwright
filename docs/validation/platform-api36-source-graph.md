# Wider public API source extraction

This follows [the initial experiment](platform-api36-experiment.md).
All header candidates below are **host-only, partial experiments**; later
sections separately record emulator tests of the earlier source baseline.
The latest verified experiments cover
[module classpaths and raw modifiers](#module-classpaths-and-raw-modifiers--2026-10-02)
and [libcore warning nullability](#libcore-warning-nullability-policy--2026-10-02).
The [integrated builder](#integrated-libcore-header-builder--2026-10-02) now
applies the verified policy to the full source graph.
Normal controller/worker header choices remain unchanged. The phone was
disconnected for the September experiments and connected for recorded October
product checks. It reconnected after a temporary interruption; no new candidate
from this document has a physical-device runtime pass.

## MergedConfiguration source-link closure — 2026-10-12

Added the exact pinned r2 `android.util.MergedConfiguration` raw source to a
fresh 92-source actual Metalava signature-link scan. Both previously unresolved
contexts in the current 91-source graph resolve to the isolated byte-identical
source copy. All 32 signature links originating inside the added model resolve;
the final graph has 162 unresolved links across 63 symbolic types. This is a
bounded source-model resolution result only. It does not close the framework
module graph, emit headers, pass strict ABI comparison, or establish distribution
clearance. The new model adds one `InvalidJavadoc` diagnostic; diagnostics were
not suppressed. Independent parent review rehashed inputs and outputs and
confirmed the exact two-context resolution and unchanged strict comparator.
See [exact source, provenance and parser evidence](framework-merged-configuration-20261012.md).

## Complete published-source reference — 2026-10-12

A different, host-only reference experiment compiles all 4,161 Java files from
the SDK-distributed r2 source archive with actual AOSP annotations. All 6,227
class names and strict public/protected declarations match, with zero differences;
independent review also matches every parsed class metadata record. This does
not establish raw-source regeneration or SDK-derived redistribution clearance.
The independent baseline/raw-owner failures below remain unchanged. No worker
promotion, APK or phone work. See [complete reference inputs, failures and
results](published-api-source-reference-20261012.md).

## Raw-owner signature graph — 2026-10-11

The actual90-source owner/OpenGL scan exposed179 unresolved signature links
across63 symbolic strings. Adding byte-identical pinned r2 CompatibilityInfo
resolved all18 prior use sites to its real source model; the91-source scan
still has164 unresolved links/64 symbolic strings, including additional owner
dependencies. Parser diagnostics remain; source-model presence does not close
the raw graph, canonical module assembly, strict ABI or redistribution gates.
No new headers/JAR/APK or phone/model work. Exact inputs, parser timings,
provenance, preserved reviewer failures and next dependencies are in
[the source-signature graph record](framework-owner-signature-graph-20261011.md).

## Hidden AIDL Java owners at the actual source-model seam — 2026-10-02

The next bounded experiment uses stock installed Metalava's
`SourceParser.parseSources`, `Codebase.resolveClass` and generated
`ClassTypeItem.asClass` links. It produces model evidence only: no Android Java
compilation, new header JAR, OpenGL assembly or worker promotion. The earlier
strict ABI comparator and canonical closure gate remain unchanged.

Baseline: the actual22 nonempty AIDL-generated Java files above, isolated and
byte-identical, with source-only6227-class headers JAR SHA256
`e36f41ffc131afab50024cdc1d269ba1bd96c06387d3662580c7d7af868fe27a`.
All14 selected hidden/SystemApi/annotation owners initially resolve to null.
This is a measured missing-owner result; the diagnostic JVM exits0, not a
claim that Java compilation fails or succeeds.

Adding exactly16 real owner/helper files resolves all14 owners and3089
generated signature class links. The14 comprise SigningDetails,
ParceledListSlice, PermissionEnforcer, ActivityThread, DomainSet,
VerifierDeviceIdentity, KeySet, SplitPermissionInfoParcelable, RemoteCallback,
SuspendDialogInfo, PermissionManager.PermissionState, UnsupportedAppUsage,
EnforcePermission and RequiresPermission. Additional AppGlobals and
BaseParceledListSlice are real raw sources, not invented stand-ins. Thirteen
framework source files are byte-identical to pinned r2 commit
`99b01a65cc4c104933788b3143285ab6bae65827`; two modules-utils annotation files
match r4 commit `e945bbce0ccb83e1111b6031a3bba89e6b5cac6f`. Compat annotation
matches the earlier official r2 fetch/receipt at commit
`db0de6104d8527114a7ae441b0a9c35f885d2876`. This r2/r4 mixture is explicitly
experimental and does not establish a canonical r2 product/source selection.

Actual underlying PSI VirtualFile provenance establishes403 links from the
exact isolated source copies and2686 from the exact baseline JAR. All48
distinct classpath names, including seven java.lang/java.util names overlapping
the host JDK, point to real entries in that JAR; host-JDK definition links0.
FQN membership alone was insufficient evidence, so the final driver records
actual file paths/URLs/protocols. No SDK definitions/framework.aidl/-p input,
neighboring raw files or compiler/diagnostic suppression is used.

The real package-private BaseParceledListSlice parent resolves as SOURCE_PATH,
including its superclass link; pinned PSI assembler deliberately defers
inaccessible command-line classes. Its exact copied-file path/hash is checked.
PermissionState is the actual nested public/static/final Parcelable class,
with containing-owner source and declared method shapes verified. AppGlobals
also resolves from its exact source copy. The initial parent's COMMAND_LINE
assumption failed and remains retained. Earlier driver compilation failed
because an old checkout showed five parseSources parameters while the installed
r4 implementation has six; exact matching Metalava commit
`9809e0d70a5b807be3d7070d51f9c4714f2fdcb4` and installed getters are checked
before correcting the driver. No failed output is overwritten.

All parser diagnostics remain:165 UnresolvedImport and13 InvalidJavadoc.
Signature-link scope covers generated classes/nested classes, superclass,
interfaces, fields, callable returns/parameters/throws, type arguments,
arrays and wildcards. It does not verify Java bodies, type-variable bounds,
complete hidden-owner graphs, member compatibility, canonical system/module-lib
classpaths, full source assembly or redistribution clearance.

Frozen evidence:
`toolchains/platform-api36-source-probe/source-model-owner-20261002/provenance-c014624f8a1949399c60ac1c0dca6282/`.
Runner SHA256 `6adf18afd396cf958a86e3542d270278893167866d5f30b6b031b9a9fdc0e07d`;
driver SHA256 `d6426283c0ee6eaa71dc97d53fc71dc1956ab795c79b01b8a9edbb1604f38f13`.
The earlier failures are under sibling
`experiment-490765f5825f406f98ab76e490eff976/compatibility-failure-01` and
`postrun-failure-01`. Raw plan SHA256
`66b5fb62b1252f9694529ba0f378ade17c5bd854aa39d64fc302f68733411d5f`.

Root independently copies the five frozen replay inputs to fresh sibling
`root-review-1f920dae99104cbe9782639917d0815e/`, then actually compiles the
driver1.248s, measures baseline null owners6.007s and owner-resolution7.226s,
all exit0 with guards passed. Root additionally verifies15 raw inputs against
pinned local git blob objects with lazyfetch/network protocol disabled,
the earlier compat fetch receipt, all model/source/argument/tool pins,
all48 actual baseline entries and exact403/2686/0 provenance counts.
`root-independent-review.json` records this review. Replay after these ignored
inputs/tools exist uses fresh phase suffixes:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 toolchains/platform-api36-source-probe/source-model-owner-20261002/root-review-1f920dae99104cbe9782639917d0815e/run_probe.py red red-review-02
PYTHONDONTWRITEBYTECODE=1 python3 toolchains/platform-api36-source-probe/source-model-owner-20261002/root-review-1f920dae99104cbe9782639917d0815e/run_probe.py green green-review-02
```

Next: actual module/classpath closure for the remaining raw owner graph and
unchanged strict ABI validation. Owner-model resolution alone does not make
the current compiler source-complete or ready for distribution.

## Hidden interface AIDL dependency generation — 2026-10-02

The whole OpenGL owner remains unresolved at AppGlobals/IPackageManager; this
experiment verifies only raw schema dependency generation. Existing pinned
frameworks/base r2 definitions resolve IPackageManager through57 files and
IPermissionManager through7, union60. Actual host AIDL on the two root
interfaces first returns1 for missing PersistableBundle and
AttributionSourceState. Those failures are retained.

The two missing schemas and permission ownership BP are acquired directly from
frameworks/native r2 commit `2827a4a16b0340ecd07c2d5a6c89991799b362bb`:

| Path | SHA256 |
| --- | --- |
| `aidl/binder/android/os/PersistableBundle.aidl` | `6f6c70a303e5b3f898450d732c0b84957f84b851e1efde69fba7dc1f85594841` |
| `libs/permission/aidl/android/content/AttributionSourceState.aidl` | `34b8d4deacde589e86addc79862a2f50ea03a258012ef5f9a17eace333a7c88b` |
| `libs/permission/Android.bp` | `f69aa5043b349d27c0c74f0e8f0aadff2e5d60395fab166a9bbafff30765c107` |

Both schemas retain Apache headers. Exact ownership: native Android.bp's
framework_native_aidl_binder owns PersistableBundle (not libbinder_aidl);
framework-permission-aidl owns AttributionSourceState. The latter BP inherits
frameworks_native_license; native NOTICE and Gitiles responses are retained.
No r4 schema substitution. Existing pinned Soong droiddoc/gen/API-level source
establishes the tested source-stub generation seam: Java, include roots,
depfiles and min_sdk_version10000, without framework implementation's extra
transaction_names/enforce_permissions switches. This does not establish a
canonical complete r2 Soong/product build.

Host Android SDK36.0.0 AIDL SHA256
`18b31f1ab7ebbcde3e67a4eb47ce6bd58f35ae179f9227a23d2785e37e5ec00b`
is a generator only: no SDK framework.aidl, -p definitions or SDK Java classes.
All62 raw files are copied byte-identically into isolated include roots.
Actual generation returns0 for all62 invocations:22 nonempty Java files plus
45 zero-byte placeholders exactly match the independently parsed raw
declarations, including opaque nested parcelables. An earlier prediction
missed the real DataLoaderType enum; failed inventory/configuration and
selection-reader attempts remain recorded. Empty placeholders are not Java
implementations or proof of hidden-type closure. All62 depfiles reference only
the pinned isolated schemas and their union covers all62 inputs.

Root reviews the script and independently repeats both missing-import RED
cases and all62 GREEN invocations (0.262s summed generator time), then rehashes
source/tool/runtime/fetch/output/depfile inventories. Evidence root:
`toolchains/platform-api36-source-probe/aidl-opengl-20261002/experiment-326a1fc6f8034484b50f0d34cf88c5b5/`.
Root receipts are `red-root-review/receipt.json` and
`green-root-review/receipt.json`. Frozen probe.py SHA256
`e44cf526b6b4b3c200fbcbcccde9093aace082a29f15c7a837f09f05faea01c2`.
Plan/inventory SHA256
`58d12c65d74e575c65f8a701ac375b7b2723de1f155ee6f2c14a560681ecd8c3`.
Replay requires these existing ignored pinned inputs/tools and a fresh suffix:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 toolchains/platform-api36-source-probe/aidl-opengl-20261002/experiment-326a1fc6f8034484b50f0d34cf88c5b5/probe.py red-review-02
PYTHONDONTWRITEBYTECODE=1 python3 toolchains/platform-api36-source-probe/aidl-opengl-20261002/experiment-326a1fc6f8034484b50f0d34cf88c5b5/probe.py green-review-02
```

No Java/Metalava/OpenGL assembly or physical runtime test runs for this slice.
AppGlobals/ActivityThread, hidden Java helpers (PermissionEnforcer,
ParceledListSlice, UserInfo and others) and canonical owner/system/module-lib
classpaths remain unresolved. Generator redistribution clearance and complete
component notices remain open. Worker inputs and strict ABI gates stay unchanged;
the earlier OpenGL candidate's1890 differing classes are not improved by this
generation-only result.

## Module classpaths and raw modifiers — 2026-10-02

Continued issue #1 without changing normal worker inputs or the strict gate.
Two opt-in tests execute actual stock Metalava and javac on pinned source;
their candidates are host-only mechanism experiments.

`scripts/test_network_module_source_headers.py` records javac RED with all
six previously identified missing calls, extracts the actual hidden SystemApi
NetworkSpecifier as support-only classpath, then separately extracts public
Connectivity-T, Connectivity and selected Wi-Fi owners. The source-only base
archive is the filtered Android16 candidate described below. Compiling all
six calls becomes GREEN. Only seven owner/Builder class files change; the
public NetworkSpecifier bytes stay identical and its hidden methods remain
absent. Actual module assembly uses scope-specific resolution; flattened
public signature text had lost the hidden-parent override predicate.

Latest run passed one test in 19.964s. Its 19 source/history/flag/annotation
pins and 135 installed tool files (90 Metalava, 45 JDK) were independently
rehashed by the root agent. Receipt written before tools and sanitized child
environment retained under:

```text
toolchains/platform-api36-source-probe/network-module-20261002/
integration-f36abd5f9ce3481c809e5002e527be5c/
```

`public-six-probe.jar` SHA256:
`f00ef96d0b25990779b7a67ae276f427c2a0d6de974ba73ff99bdc33eec44b1f`.
The independent unchanged strict comparator returned **1**, 6,227 classes
each, zero missing/extra and **1,945 differing classes**, down from 1,949.
No differences were suppressed or rebaselined. R4 annotation helpers and the
bounded classpath are not canonical r2 Soong assembly. The two selected Wi-Fi
raw files have no FlaggedApi declarations; this does not establish full Wi-Fi
release flag selection or redistribution rights.

The next raw-source test isolated a second loss in signature text.
Pinned raw `android.util.Log.isLoggable` declares `native`; signature text
omits it. `scripts/test_raw_platform_source_modifiers.py` first records the
exact flag assertion RED (`0x9`, expected `0x109`), then extracts actual raw
Log source and compiles its emitted native declaration. GREEN is `0x109`.
Only Log.class changes and the public method/field inventory is preserved.
The first focused run passed one test in 11.730s; the root independently
replayed all ten source pins, 135 tool hashes and the complete parsed public
Log ABI against the SDK comparison reference.

```text
toolchains/platform-api36-source-probe/raw-modifiers-20261002/
integration-b7bdfbe178d3405f96cf67ec90b8638c/
```

`public-log-probe.jar` SHA256:
`4102e5dfc07ad5d9d5712e76db18684c347dfd5787b716551ab028f574851425`.
Unchanged strict comparison returned **1**, 6,227 classes each, zero missing/
extra and **1,944 differing classes**. SDK definitions were comparison-only.
Metalava reports an informational unresolved hidden
`android.compat.annotation.UnsupportedAppUsage` import; no helper was invented
and no diagnostic suppressed. This remains a bounded extraction.

Further inspection found that supplying a raw file in its original sparse
checkout can implicitly resolve neighboring raw sources. The initial receipts
therefore do not prove the complete extraction source closure. The final test
copies each pinned owner byte-identically into a fresh root containing exactly
one Java file and passes explicit `--source-path`. Two isolated cases passed
in 12.284s:

- Log: `integration-47387e52cd604034b4af342c6e7b9151/`,
  `public-owner-probe.jar` byte-identical to the earlier Log candidate.
  Exact original public inventory retained; strict comparison still 1,944.
- ProgressBar: `integration-7482ce09aa7e4a55b2f007c950b5954b/`,
  `public-owner-probe.jar` SHA256
  `1a3fc474a80f3585bed0864eb39f127b22c31f6633f7bb60421409773b040019`.
  Exact getMax modifier RED `0x1` to GREEN `0x21`, one replaced class.
  Strict comparison returns **1**, **1,943 differing classes**, 6,227 each,
  zero missing/extra. Complete parsed public ProgressBar ABI matches the
  independent SDK reference, including restored synchronized/ExportedProperty
  metadata and 14 actual raw overrides previously inherited in the baseline.

Root independently checked the isolated source copies, 10/13 input pins and
135 tool files per case, and exact full owner ABIs. Isolated extraction retains
four Log and seven ProgressBar unresolved-import informational diagnostics;
no neighboring sparse checkout paths appear in the extraction logs. Missing
private/body annotation resolution remains documented rather than suppressed.
ProgressBar's first real helper compilation failed because actual Px requires
Dimension; adding the existing pinned upstream Dimension resolved that stage.
A subsequent exact-original-inventory assertion failed on the 14 legitimate
raw overrides. The final assertion requires exactly those independently
verified descriptors plus the baseline, no removals, unchanged fields/class
flags/hierarchy and the exact target modifier. Both failures remain in ignored
receipts/logs. No compiler inputs or strict ABI assertions were weakened.

The third isolated case exercises flagged native declarations directly through
release policy/history, without the extra JAR reparse. Pinned raw SystemClock
and five actual generated android.os flag sources are copied into a fresh
bounded source root. Actual BP2A XML selects
`android.os.adpf_gpu_report_actual_work_duration` enabled/immutable; existing
source-only Android35 released history supplies reversion context. Exact
uptimeNanos native modifier is RED `0x9`, GREEN `0x109` after actual Metalava
policy/history extraction and javac. The public nine-method/field inventory
stays unchanged; all flag helpers remain hidden, only SystemClock.class changes.

Focused run passed one test in 6.480s. The root independently rechecked all
20 input pins, 135 tools, six raw copies and full public SystemClock ABI against
the comparison-only SDK reference. Unchanged strict comparator returns **1**,
**1,942 differing classes**, 6,227 each, zero missing/extra. Evidence:

```text
toolchains/platform-api36-source-probe/raw-modifiers-20261002/
integration-aa30c0992e2a4be49fe85d890420b4fe/public-owner-probe.jar
SHA256 17f3b655406c4138627386af95ffeaa7c591f2a31d5c97d57dd17e804c5ccf37
```

Thirteen informational unresolved private/body imports remain; no neighboring
raw paths, invented helpers or suppressed diagnostics. An initial XML preflight
failed because the test lookup did not handle the namespace; its receipt is
retained, and only lookup handling was corrected. The root subsequently ran
the complete final three-case opt-in suite: **3 passed in 18.920s**. This proves
this enabled flagged-native seam, not complete module release history: reverted
declarations still sourced from signature history may lose modifiers. Libcore's
raw source closure/package nullability policy is still unassembled; its sparse
checkout currently has signature/BP/legal inputs, no Java sources.

Read-only classification of the six-call candidate found 76 classes differing
solely in native/synchronized flags, 92 solely in those plus final, and 1,853
still differing after diagnostic masking of those three flags. That diagnostic
never changes the strict comparator. There are 1,808 classes with direct-member
materialization differences, 150 with method flags, 112 with method annotation
attributes, 20 with field annotation attributes and 25 with empty-versus-absent
InnerClasses attributes (categories overlap); no field presence/flag/constant
differences. All 5,631 SDK-direct-only descriptors occur in candidate ancestors;
inherited presence is not strict parity or universal call-site success.

Pinned upstream source explains the seams: Metalava ModifierListWriter emits
native/synchronized only to stubs, PsiMethodItem normalizes method final in a
final owner, and ApiAnalyzer materializes methods from hidden parents.
Raw SpannedString inherits final length from its package-private parent; its
pre-filter stub retains final, while the release-filter JAR reparse drops it.
Raw extraction alone cannot be claimed to fix all final normalization cases.
The actual non-updatable extraction resolves module-lib tethering,
module-lib connectivity-t and system Android scopes; source-built equivalents
remain required before claiming complete upstream assembly.

```bash
PYTHONDONTWRITEBYTECODE=1 PLB_NETWORK_MODULE_INTEGRATION=1 PYTHONPATH=scripts python3 -m unittest test_network_module_source_headers
PYTHONDONTWRITEBYTECODE=1 PLB_RAW_SOURCE_MODIFIERS_INTEGRATION=1 PYTHONPATH=scripts python3 -m unittest test_raw_platform_source_modifiers.RawPlatformSourceModifiersIntegrationTest.test_raw_log_source_preserves_native_public_method
python3 scripts/compare_platform_abi.py --reference toolchains/android-sdk/platforms/android-36/android.jar --candidate toolchains/platform-api36-source-probe/raw-modifiers-20261002/integration-b7bdfbe178d3405f96cf67ec90b8638c/public-log-probe.jar
```

Run the entire `test_raw_platform_source_modifiers` module for all three isolated
owner cases. Its currently pinned ProgressBar resolution base is byte-identical
to the isolated Log output. The tests require those existing ignored inputs.

Both opt-in tests require already pinned ignored source/tools; they do not
download or package artifacts. Receipts/logs include failed intermediate runs.
The release gate, complete component/notice mapping, broader API/runtime
verification and independent source-only module assembly remain open.

## Matching sources and generated support

Ignored local root: `toolchains/platform-api36-source-probe/`.

| Raw source repository | Android 16 r2 commit | Added inputs |
| --- | --- | --- |
| frameworks/base | `99b01a65cc4c104933788b3143285ab6bae65827` | 2,032 selected public Java files, real package metadata, 167 flag declarations, SecurityLogTags, proto schemas |
| frameworks/av | `4b2111885ff934799e70eddae2527a9848c3be29` | Four camera/audio/codec flag files |
| frameworks/native | `2827a4a16b0340ecd07c2d5a6c89991799b362bb` | Input flags and private input/display/dumpstate AIDL |
| frameworks/opt/telephony | `3449e9e2cd2738d8cdeb88514498f728622e3a6b` | Twelve flag files |
| hardware/interfaces | `5688f7eb1e117ed26e642a695de300b7683acb87` | Radio/CAS/keymint/thermal AIDL, health HIDL |
| frameworks/proto_logging | `29d96a39adaffda3ad1b08ae17b6befbe2a625fa` | OS, telephony, view and service enum schemas |

The selected r2 base source files all exist, including 28 telecom files
whose directory moved between r2 and r4. Framework annotation support still
comes from the previously pinned r4 module-utils checkout; this is not a
canonical complete r2 Soong build.

Actual source-built `aconfig` generated 655 Java support files from 184
raw declarations in 131 packages. The cache dump has 2,236 rows; the tracked
readonly-policy helper emits 1,462 enabled readonly policies. Generation
commands, inputs, release values and results are retained in
`framework-flags-r2-complete/generation-report.json`. The AP3A/AP4A/BP1A/BP2A
value chain is explicit. The actual SDK build's `ExportRuntimeApis` product
selection remains unestablished; this projection must not silently be
reported as its canonical release configuration.

Five radio enum Java files were generated by the host SDK36.0.0 AIDL tool
from actual r2 schemas; see `radio-r2-generation-report.json`. The generator
is a host test tool, not packaged in a worker. The existing upstream
build/make r4 event-log script generated the actual 46 SecurityLogTags
constants. No constants or public API declarations were handwritten.

[Metalava's manual annotation directory](https://android.googlesource.com/platform/tools/metalava/+/a2548e478c121d5801137d636f6e83af37eafc99/manual/)
supplies the historical LocationManager permission qualifiers. Passing that
unchanged directory resolved the remaining permission diagnostic without
adding a baseline entry or suppressing the diagnostic.

## Successful partial projection and failed gates

The r2 projection used 2,894 inputs, emitted 2,032 stub sources and returned
**0**. Javac returned **0** with 84 warnings. Compared against the reference
families selected from the original raw-source list, it has 3,439 classes:
**3,385 declaration ABIs match; 54 differ; zero classes are missing or extra**.
The full SDK reference has 6,227 classes. Across those differences, an
independent parser replay counted 240 changed fields (missing constant
values), 15 missing fields, 19 missing methods, seven changed methods and
no extra fields/methods.

A separate strict released-API compatibility run returned **255**, reported
245 error lines (237 changed values, four removed packages, two removed
classes and two removed deprecated classes) and emitted **zero** stubs.
The partial graph lacks R/Manifest and other public packages. This remains a
failed compatibility gate; it was not baselined or declared successful.

The r2 tool rejected `--check-compatibility disabled` with exit **1**. Built
unmodified r4 Metalava at
[`9809e0d70a5b807be3d7070d51f9c4714f2fdcb4`](https://android.googlesource.com/platform/tools/metalava/+/9809e0d70a5b807be3d7070d51f9c4714f2fdcb4)
using project JDK21 and one Gradle worker. Its wrapper pins Gradle9.0.0
SHA-256 `8fad3d78296ca518113f3d29016617c7f9367dc005f932bd9d93bf45ba46072b`.
The `:metalava:installDist` build returned **0**; its host dependency receipt
records 89 installed JAR filenames/checksums and embedded notice entry names
in ignored `metalava-r4-host-dependencies.json`. Source modules have upstream
Apache2 headers; these downloaded tools/dependencies are not shipped and
this receipt does not grant distribution clearance.

R4 explicitly supports supplying released APIs for flagged-API reversion
with compatibility checks disabled. Such a history-only run is an
extraction experiment, **not a compatibility pass**. It emitted/compiled
3,439 classes and reduced declaration differences to 38, but the extractor
returned **255** on AudioRecordingConfiguration.getClientPackageName's
permission documentation. R2 raw source uses `@pending`; the r4 source
replaces it with `@hide pending for SystemApi`, and the newer parser no
longer handles that r2 tag in the same way. Its output has one extra public
method and one missing method. Keep this failed mixed-version run separate.
Do not fix it by suppressing the diagnostic or fabricating an annotation.

API35 is an explicit host comparison/history choice. Soong's prebuilt API
selection is per module/scope and chooses the latest numeric version;
`next_api_dir` does not cap that selection. Therefore these experiments do
not establish API35 as the canonical history of the pinned r4 source tree.
SDK resolution/history files remain host-only inputs with unresolved rights.

## Additional actual generators

The r2 prebuilt host HIDL generator checkout is
`8c01d80d728e5bf542e7d754064994d3f1107a7d`. Its generator SHA-256 is
`c01053a23105eb2475ad95ce7c9d82a297229f36b9b9727abd37adc752c10f28`, and
its required libc++.so SHA-256 is
`7a040ad91472ba96a6a21d28d0338fd4e1b86d7476aaefaa2a80aa061cbca8ec`.
The embedded source manifest pins system/tools/hidl to
`b033122b662aa85be8e01d18db4ec05cfa617b5b`; its Android.bp license rule
references Apache2 and NOTICE. The checkout's root metadata describes other
GPL tools too; that is not a license assignment to hidl-gen itself. Host
runtime dependency notice mapping is not complete, and neither binary is
packaged in an APK.

HIDL health constants generation and javac returned **0**. Adding that
support to the r2 projection returned **0**, javac returned **0** and the
ABI preflight still failed, with 53 different classes and zero missing/extra.
Thermal/vibrator generation initially failed on missing imported HIDL base
sources; those failures are recorded separately.

Built the actual r2 `tools/streaming_proto` Java generator against existing
host protobuf36.1/GCC16.2.1. Its first link failed on Abseil dependencies;
using protobuf's pkg-config link flags returned **0**, without source edits.
The host protobuf license is BSD3 at `/usr/share/licenses/protobuf/LICENSE`.
The source generator's framework license rule references Apache2; host
libraries remain test tools outside APKs. Actual sensor privacy, print and
OS/telephony/view/service schemas generated 28 support Java files, and
javac returned **0**. Private AIDL generated 16 support files in the integrated checkpoint. Its first
javac run failed on private Parcelable stability definitions; compiling
against the actual r2 Parcelable source and upstream annotations returned
**0**. No handcrafted internal interface was introduced.

Integrating these support class directories into a fresh r2 extraction
returned **0**; javac returned **0**. The result has all 3,439 expected
classes, zero missing/extra and **32** differing declaration ABIs. Remaining
changed constant fields fell from 240 to **13**; 15 fields and 19 methods
remain missing. The exact r2 generation target was independently located
in `ProtoLibraries.bp`: `framework-javastream-protos` includes
`core/proto/**/*.proto`, stats enum/message inputs and other internal
schemas. This verifies the generation seam for sensor privacy and print;
it is still a partial reimplementation of that source graph.

Thermal/vibrator HIDL then generated successfully after supplying actual
libhidl r2 source at `93f79786f64c02b71ba899a46c34829266f9c370` and
`transport/current.txt` frozen-interface metadata. Their support javac
returned **0**. An additional actual radio EmergencyNumber AIDL was
generated/compiled successfully. Integration of these newest inputs is
in progress.

The tracked flag helper now supports explicit `--export-runtime-apis`.
Verified against Soong's `droidstubs.go` and
`scripts/aconfig_api_config.py`: include enabled readonly flags plus
**all** mutable flags, preserving enabled/disabled status. The new
regression test failed first, then all three helper tests passed. Actual
r2 caches produced 2,107 policy rows: 1,462 readonly and 645 mutable. This
runtime comparison returned extractor/javac **0**, but fails the unchanged
ABI preflight with **3,448 classes, nine extra classes and 62 differing
classes**. It has 67 extra fields and 49 extra methods, so it is not selected.
It does not establish the canonical release's product variable value.
A follow-up source audit found missing actual flag declaration packages,
including `com.android.media.codec.flags`, whose enabled readonly BP2A
values were already present. Expanding those inputs is preferable to
assuming a different SDK flag policy.

## Reproduction and evidence

The ignored working recipes/receipts include:

- `prepare-r2-complete-flags.py` and `framework-flags-r2-complete/`.
- `base-r2-generated-{sources.list,roots.txt}`.
- `base-r2-generated-projection-{metalava.log,javac.log,classes.jar,abi-report.json,abi-details.json}`.
- `base-r2-generated-metalava.log` (strict compatibility failure).
- `base-r2-generated-extraction-metalava.log` (unsupported old CLI option).
- `base-r2-metalava-r4-history-{metalava.log,javac.log,abi-report.json,abi-details.json}`.
- `base-r2-health-projection-{metalava.log,javac.log,classes.jar,abi-report.json}`.
- `generate-private-r2-aidl.py`, `private-r2-aidl-generation-report.json`.
- `sensorprivacy-r2-generation.log`, `print-r2-generation.log`,
  `enum-protos-r2-generation.log`, `proto-r2-javac.log`.
- `private-r2-with-parcelable-javac.log`.
- `base-r2-private-projection-{metalava.log,javac.log,classes.jar,abi-report.json}`.
- `thermal-r2-frozen-generation.log`, `vibrator-r2-frozen-generation.log`,
  `health-thermal-vibrator-r2-javac.log`.
- `framework-flags-r2-complete/runtime-flags-config.xml`.

The main extraction command is the earlier wider-source command with r2
source lists/roots, `framework-flags-r2-complete/exportable-flags-config.xml`,
`--merge-qualifier-annotations .../metalava/manual`, and a fresh output
path. Private support class directories precede the SDK in the host
classpath; they are not included in the resulting public class archive.
Javac uses `-source 8 -target 8` and the existing upstream annotation support.
The three retained class archives have these SHA-256 values:

| Probe | SHA-256 |
| --- | --- |
| Generated r2 projection, 54 differences | `26446a0073255b66c3c8031760ba052539d91e02811af120ec8d036d3f2c9354` |
| Private support integration, 32 differences | `87587c2f7280cc21ffc8969a6abe05cc6146e6d2f04dac4d9f73e37d981e3628` |
| Rejected runtime policy, 62 differences | `5f516d85d4354a4899a31d1d8a43f717219dd561a872f52a93a288eddf11e805` |

The unchanged preflight is:

```bash
python3 scripts/compare_platform_abi.py \
  --reference toolchains/platform-api36-source-probe/base-selected-reference.jar \
  --candidate toolchains/platform-api36-source-probe/base-r2-generated-projection-classes.jar
# Expected at this checkpoint: exit 1 (54 differing declaration ABIs).
```

Next: integrate the additional generated support, resolve release/history
inputs, and cover the remaining public modules. Also inspect AOSP's
maintained source API signature texts as a possible generator input, with
real source supplements where a signature omits callable declarations.
The full class/declaration/resource gates and independent provenance are
required before replacing the worker platform input. Physical S23 testing
waits for hardware to reconnect.

## Extended declaration graph checkpoint

Added actual r2 inputs from services/Telecomm
`e3af993ac0f53e1a0e2ee8b7be3d67cb9970a616`, modules/Uwb
`adcc2fe7aadac53e67402901e2246af21699749d`, modules/AdServices
`a6ee8245f54f1719a899809cc8727f7fcce9ca35`, system/security
`ce2aa0ef49158d0d45fb133fd42ab1deff210f8c`, the other AV declarations
and SurfaceFlinger declarations in the already pinned native repository.
Their actual Android.bp modules and release values establish the missing
flag names; this is not a manual list of API definitions.

`framework-flags-r2-source-extended/` has **222** declarations in **145**
packages, **725** generated Java support files and **2,464** cache rows.
`prepare-r2-source-extended-flags.py` records the actual working recipe.
The wider source list has **2,965** inputs, including actual AAPT2-generated
`com.android.internal.R` source for Window's content ID. The standalone
attempt to compile the full generated R files failed for annotation/flag
classpath dependencies; only the subsequent source extraction is a pass.
No public resource or Java constant was handwritten.

`base-r2-extended-projection-metalava.log` and `-javac.log` both returned
**0**. The class archive has **3,439** expected classes, no missing/extra;
**3,429 declaration ABIs match and ten differ**. Its SHA-256 is
`9cb049a5b1058898556dd8da29289497ad281b7bb478bac7b79aa9a8047f40f6`.
The unchanged preflight returns **1**. All prior missing ConstantValue
attributes now match. Remaining differences:

- PackageManager's three permission methods, SurfaceView's two ordering
  methods and WebView.startSafeBrowsing are missing; flag reversion needs
  released API history.
- MediaProjection, MediaProjectionManager, TvAdView, TextUtils, Display
  and the two checked-change callbacks have nullability metadata differences.

Evidence is `base-r2-extended-{sources.list,roots.txt}` and
`base-r2-extended-projection-{metalava.log,javac.log,classes.jar,abi-report.json,abi-details.json}`.
This remains a partial, SDK-resolved host experiment, not a release input.

Also cloned source API/legal inputs from libcore r2
`fff4fcc0cf7f080cf2511cbb57561482b13b218f` and ICU r2
`f17caeafcf20bd38074a9963c31df3629b70b5f5`. Six source-maintained
signature contributions merged successfully into a 6,326,764-byte inventory.
The exact inputs are the actual source files, not SDK prebuilt signatures:
base core/api/current.txt, libcore api/current.txt, ICU
android_icu4j/api/public/current.txt, AdServices adservices/framework/api/current.txt,
SDK sandbox sdksandbox/framework/api/current.txt, and Uwb
ranging/framework/api/current.txt. Coverage/compilation and remaining module
inputs still need checks. Root/component license rules/notices are retained;
no blanket rights clearance is inferred from their names.

## History reversion and source-only resolution checkpoint

The r4 source-built extractor with the extended flag graph and API35 history
returned **0**. One actual r4 upstream file replaces the entire r2
`media/java/android/media/AudioRecordingConfiguration.java`; the r4 file uses
`@hide pending for SystemApi` instead of the r2 `@pending` block. No source
API declaration was handwritten and no extractor diagnostic was suppressed.
This is a mixed-tag host experiment, not the canonical r2 build.

The first javac invocation returned **1**: the SDK's RecentlyNullable and
RecentlyNonNull helpers are package-private. A fresh invocation with actual
upstream `public-annotation-classes` before `framework-annotation-classes`
and the SDK reference returned **0**. All 3,439 expected classes are present,
zero missing/extra; 3,432 declaration ABIs match and seven differ. Independent
parser replay found zero missing/extra methods and fields and identical class
attributes and constants. The differences are exactly the seven method
nullability signatures identified above. The unchanged preflight returns **1**.
Candidate SHA-256:
`b0e57cc7c81b2339c533ad73ad3e38d1510b8f2d3ef1199ebe9459756ddd46c9`.
Evidence: `base-r2-r4-doc-overlay-{sources.list,metalava.log,javac.log,
javac-public-annotations.log,classes.jar,abi-report.json,abi-details.json}`.

The six actual source signature contributions were extracted with the r2
source-built Metalava using `--api-class-resolution api --include-annotations`
and no SDK classpath. This returned **0**. Fresh javac with `-source 8
-target 8 -proc:none -bootclasspath '' -classpath ''`, including actual
upstream annotation support sources, returned **1**, with 47 errors for
missing module types. No platform prebuilt was used to satisfy those types.
The errors point to Bluetooth, AppSearch, Wi-Fi, Connectivity, IPsec and
Media source API inputs. This tests resolution independence only; it does
not establish complete coverage, ABI parity, or redistribution rights.
Evidence: `aosp-source-signature-inventory.json` (six source commits and
signature hashes), `aosp-source-six-signatures.txt`, `aosp-source-six-stubs-metalava.log`
and `aosp-source-six-javac.log`. All remain ignored, host-only outputs.

## First SDK-free source header archive

Extended the six-signature inventory with verified r2 contributions from
AppSearch, Bluetooth, Wi-Fi, Connectivity, IPsec and Media. Exact module
names were independently found in their declaring Android.bp files, and
HEAD matched each peeled r2 tag. Twelve signatures merged/extracted with
exit **0**; fresh SDK-free javac returned **1** for six additional unresolved
type errors. Added base `libs/hwui/api/current.txt` (framework-graphics),
base `location/api/current.txt` (framework-location) and Connectivity
`framework-t/api/current.txt` (framework-connectivity-t). These are distinct
public scope contributions. Fifteen inputs contain 5,862 type definitions;
a declaration-block overlap scan found zero duplicate/conflicting types.

Source-built Metalava merge and public stub extraction returned **0**.
Fresh javac with `-source 8 -target 8 -proc:none -bootclasspath '' -classpath ''`
compiled 3,927 sources with exit **0**, including the actual upstream public
annotation counterparts. No SDK/platform classes or history contributed to
this archive. The actual upstream `make-annotations-package-private` command
selected/generated five non-source-retention helpers; separate JDK javac
returned **0**, and those private helper class files replace the compilation
support versions in the archive, following the upstream separate helper seam.
The SDK reference JAR is only a subsequent comparison input.

Candidate `aosp-source-fifteen-classes.jar` SHA-256:
`d800ee4fafd188497838ca440be3c999d4c2eeae37dac424349757b1960e1c23`.
Unchanged strict preflight returns **1**: 5,867 candidate classes versus
6,227 reference, 398 missing, 38 extra and 2,172 differing common classes.
This first archive is incomplete and does not pass API parity or license
clearance. A separate inherited-member audit of common visible classes found
six unavailable method signatures across EthernetNetworkSpecifier,
L2capNetworkSpecifier, WifiNetworkSpecifier and WifiAwareNetworkSpecifier;
all common class hierarchies, generic signatures and constants match. This
is a diagnostic audit, not a replacement gate or a claim about missing classes.
The earlier SDK-signature-only probe's missing winning-seller method is present
in the actual AOSP AdServices contribution. Full module and flag coverage,
raw upstream hidden-parent callable overrides and annotations remain needed.

Evidence under the ignored probe root:
`aosp-source-{twelve,fifteen}-signature-inventory.json`,
`aosp-source-fifteen-overlap-check.json`,
`aosp-source-fifteen-{signatures-merge.log,stubs-metalava.log,javac.log,
private-annotation-javac.log,classes.jar,abi-report.json,semantic-audit.json}`,
and `r2-private-stub-annotations.log`.

The remaining VCN module was independently located in base:
`packages/Vcn/framework-b/Android.bp:129` declares framework-connectivity-b;
`packages/Vcn/framework-b/api/current.txt` supplies its public scope.
It is not included in the fifteen-input archive above.

## Complete pinned source signature baseline

[Module inventory](../../toolchain/platform-api36-source-inputs.json) records
38 module alternatives from 26 repositories. The base bootclasspath has
33 unconditional modules and five conditional alternatives. The actual
chosen graph uses the platform CrashRecovery and OnDeviceIntelligence
variants, and Ranging, plus non-updatable base: **37 contributions**.
The two alternative pairs have identical, empty public signature files at
this source tag; they are not merged twice. Every selected definition is
unique: **6,276 types**, zero duplicates/conflicts. Root license references
are recorded observations, including libcore and ICU's mixed aggregation;
they are not whole-component permission conclusions.

The new [builder](../../scripts/build_platform_source_headers.py) validates
hashes before creating output, refuses existing output directories, runs
stock upstream tools with bounded logs/heap/time, builds against source-only
Java boot classes and atomically publishes a deterministic class-only ZIP.
The actual full build used the same hashes/paths now retained in the
[tracked manifest](../../toolchain/platform-api36-source-header-manifest.json).
The source repositories and source-built Metalava remain ignored local
inputs; this helper does not fetch or install them. With those inputs present,
reproduce in a **new** output directory:

```sh
python3 scripts/build_platform_source_headers.py \
  --manifest toolchain/platform-api36-source-header-manifest.json \
  --metalava toolchains/platform-api36-source-probe/out/metalava/metalava/build/install/metalava/bin/metalava \
  --jdk toolchains/jdk-21.0.12.1+1 \
  --annotation-sources toolchains/platform-api36-source-probe/metalava/stub-annotations/src/main/java \
  --output 'toolchains/platform-api36-source-probe/new source headers'
python3 scripts/test_build_platform_source_headers.py
```

All five actual build stages returned **0**, with 4,149 public Java stubs
plus five actual upstream annotation counterparts. Five package-private
annotation helpers were separately compiled against the generated source
classes, replacing their public compilation-support counterparts. No SDK
class/history or JDK bootstrap supplied platform definitions. The actual
`full source headers/headers.jar` has **6,281 classes**, **4,873,235 bytes**,
SHA-256 `0f2d820498f770e9904a02d45a60d1b2b0a52257ed1fbe82e4e289aa9406c534`.
Unchanged strict check returns **1**: zero missing classes, 54 extra and
2,375 differing declarations versus the 6,227-class SDK reference.
Seven builder regression tests pass, including real noisy child drainage,
closed-output timeout and reader failure termination. The cleanup failures
were reproduced before their fixes; no mocked success is used as pipeline
integration evidence. The actual receipt records all commands/hashes/stages
under `full source headers/receipt.json` and `logs/`.

## Full API flag source closure and filtering experiments

The original 2,464-row cache covered 348 of the full API's **466** flag
names. Added 21 actual declarations from pinned module sources, resolving
all **118** missing names. The last update-engine declaration is pinned at
`2da98fe459c01d8902ea68b3fb2090578f49c8f6`. Actual upstream aconfig generation
returned **0**: **243 declarations**, **163 packages**, **815 generated Java**
files, **2,811 cache rows**. The API names resolve to 385 readonly/enabled,
29 readonly/disabled and 52 read-write/disabled flags. Readonly-enabled
export policies remain an explicit experiment; canonical product release
selection is not claimed. No API declaration or flag value was invented.

Three different stock extraction routes were tested:

1. Reading signature text with flag config returned **0** but retained all
   4,149 Java stubs. The text model is pre-filtered, so this route did not
   remove the 54 future classes. It is not selected as a filtered candidate.
2. Reparsing those generated Java stubs returned **255**, reporting 6,876
   DeprecationMismatch errors because declaration-only stubs lack the
   original deprecated Javadocs. Those diagnostics were not suppressed.
3. Stock Metalava's JAR model loader, which performs API analysis without
   requesting source Javadocs, returned **0**, emitted 4,110 stubs and filtered
   the future classes. Fresh javac (4,115 inputs, empty boot/classpath plus
   actual public annotation sources) and separate private annotation javac
   returned **0**. Exploratory private-helper javac used the ordinary JDK
   bootstrap; the new baseline builder instead proves source-only bootstrap.

The JAR-model invocation uses source-built r4 Metalava, 2 GiB heap:

```sh
JAVA_HOME="$PWD/toolchains/jdk-21.0.12.1+1" JAVA_OPTS='-Xms128m -Xmx2048m' \
  toolchains/platform-api36-source-probe/out-r4/metalava/metalava/build/install/metalava/bin/metalava \
  --quiet main --include-annotations --exclude-documentation-from-stubs \
  --config-file toolchains/platform-api36-source-probe/framework-flags-r2-public-modules/exportable-flags-config.xml \
  --migrate-nullness 'toolchains/platform-api36-source-probe/full source headers/combined-api.txt' \
  --check-compatibility disabled \
  --check-compatibility:api:released toolchains/platform-api36-source-probe/android-35-public-api.txt \
  --stubs toolchains/platform-api36-source-probe/full-source-jar-filtered-stubs \
  'toolchains/platform-api36-source-probe/full source headers/headers.jar'
```

This still consumes **SDK-derived API35 history as a test input**; replacing
it with verified AOSP source history is pending. Disabled compatibility
checking is not a compatibility pass. The archive contains exactly **6,227**
reference classes, zero missing/extra, SHA-256
`88644cba114c3574384b4d36833106a435f96cb4c63e6bc7e0b06293853a649a`.
Strict ABI remains **1** with **1,949 differing** common declarations.
A diagnostic inherited-member replay found zero hierarchy, generic or
constant differences and six unavailable method signatures, with many
modifier and annotation differences. Its branch-specific superclass walk
normalizes `.class` map keys and gives class ancestors priority over interface
ancestors; it does not replace strict ABI or runtime verification.

The six missing concrete methods belong to EthernetNetworkSpecifier,
L2capNetworkSpecifier, WifiNetworkSpecifier and WifiAwareNetworkSpecifier.
Main independently read their actual upstream `@hide` blocks and the
`@SystemApi` base declarations. A small actual raw-source extraction returned
**0** and fresh javac returned **0** after putting actual public annotation
support before the package-private helper classes on the bootclasspath.
The first compile failed for private annotation access. Eight resulting
classes replace the corresponding baseline classes in an ignored experiment,
but **all six methods remain absent**, and strict ABI still fails. No manual
method overlay or acceptance rebaseline is used. The actual SDK module graph
has separate public/system/module-lib stubs and dependency classpaths; that
assembly needs reproduction to explain these hidden artifact declarations.

An attempted raw-base nullness migration against the full source36 signatures
returned **255** with source API lint/deprecation errors and is not a pass.
No diagnostics were suppressed. Earlier successful 3,439-class results retain
their documented SDK-resolved/mixed-tag limitations.

Additional ignored evidence: `aosp-source-full-{signature-manifest.json,
overlap-check.json,missing-api-flags.json,new-flag-declarations.json}`,
`full-api-flag-source-inputs/`, `full-public-flag-generation.log`,
`framework-flags-r2-public-modules/`, `full-source-jar-filtered-{metalava.log,
build.log,javac.log,private-annotation-javac.log,classes.jar,abi-report.json,
semantic-audit.json}`, `network-raw-source-overlays/inventory.json`,
`network-raw-overlay-{metalava.log,javac.log,public-annotation-javac.log}`,
`full-source-network-overlay-{classes.jar,abi-report.json}` and
`base-r2-source36-nullness-{command.json,metalava.log}`.

All archives above are ignored host experiments; no worker asset or APK was
changed, and no physical/cloud run occurred. Next: source-derived API35
history, upstream module assembly, complete source notices and isolated
compiler runtime verification. Exact class counts do not prove full SDK
parity or redistribution clearance.

## Source-derived Android 15 history trial

The Android 15 r1 base bootclasspath has 31 unconditional public module
contributions plus non-updatable base, and a conditional CrashRecovery
module selected by `release_crashrecovery_module`. This trial uses the 32
unconditional/base inputs; that conditional release choice remains under
review. Main independently verified all **32**
source files/hashes against their peeled source tag commits. NFC moved from
base `nfc/api/current.txt` at r15 to its separate module at r2 Android16;
Virtualization moved from `java/framework/api/current.txt` to
`libs/framework-virtualization/api/current.txt`. The new
[history manifest](../../toolchain/platform-api35-source-history-inputs.json)
records these exact sources; it does not claim that their unfiltered current
snapshots are canonical released API history. Stock source-built Metalava
merge returned **0**. NFC's apps repository supplies implementation references,
not the authoritative public signature contribution.

Replacing only the JAR-model filter's released-history input with the merged
AOSP current snapshots returned **0**. Fresh public javac (4,116 inputs, empty
boot/classpath plus actual public annotation sources) returned **0**, and
private annotation javac against these source-built public classes returned
**0**. No SDK class or history contributed to this trial. Candidate SHA-256:
`f6370920955b7c093fe188ea9320878926a3442c22191f467d50d2f00942238f`.
It has **6,228** classes, no missing classes and one extra:
`android.nfc.WlcListenerDeviceInfo`. The strict check is **1**, with **1,970**
differing common declarations. It also restores future flagged members in
several classes; this is why raw current.txt is not accepted as released
history. The actual upstream release flag policy/frozen-snapshot graph needs
reproduction. No manual API deletion or gate rebaseline is used.

Evidence under the ignored root: `api35-source-history/{closure-receipt.json,
full-source-signature-manifest.json,combined-api35.txt,merge.log}`,
`full-source-aosp35-history-{command.json,metalava.log,build.log,javac.log,
private-annotation-javac.log,classes.jar,abi-report.json,semantic-audit.json}`
and `source35-history-candidate-declaration-delta.json`. The raw assembly
investigation is retained in `network-module-stub-investigation.md`.
The source-current history trial is host-only; no phone, model or production
worker result is claimed.

## Isolated compiler source-header runtime probe

The explicit `experimentalSourceHeadersJar` option accepts only the pinned
source baseline hash `0f2d820498f770e9904a02d45a60d1b2b0a52257ed1fbe82e4e289aa9406c534`.
It is mutually exclusive with `experimentalPlatformJar`, stages only into
platformProbe and uses a distinct asset name. The runtime loader validates
its checksum, size bound and Activity/Object entries without requiring APK
resource entries in this class-only archive. The original SDK and resource
validation remain separate. No normal/default build selects these headers.

Four meaningful new unit cases were observed failing at test compilation
before implementation (ten missing symbols). The green capped Gradle worker
unit run passed; main inspected all **eight** ExperimentalPlatformInput
cases, reviewed the changes and verified actual staged asset hash. The
mutually exclusive Gradle properties were also exercised and rejected.

The first assembly plus lint run returned **1**: lint's generated asset
model lacked a dependency on the staging Sync task. Main changed the assets
source directory to the task provider. The same input configuration then
returned **0**, 114 tasks. A later input-removal trial described below found
that AGP still needed explicit dependencies for its asset/lint consumers:

```sh
source scripts/env.sh
./gradlew :worker:assemblePlatformProbe :worker:assemblePlatformProbeAndroidTest \
  :worker:lintPlatformProbe -PplatformProbeTests=true \
  '-PexperimentalSourceHeadersJar=toolchains/platform-api36-source-probe/full source headers/headers.jar' \
  -PexperimentalPlatformResources=toolchains/platform-api36-source-probe/resource-r2-table-script.apk \
  --offline --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx1536m -XX:MaxMetaspaceSize=512m'
```

Main independently checked both asset hashes in the APK, absence of the SDK
reference asset and all **42** tracked notices byte-for-byte. Probe APK SHA:
`4a63c6c2f64612481da63cfff68b644f3842e47f4f3f58c19e1a48b29ebf0edc`.
API36 initially used test APK SHA
`31295cd620a63bf44e0c09f8c5552e6df7110c70fe0baf01e25a608ec9f1bb0e`.
Before API31, added an explicit `Process.myUid()` worker-context assertion
and rebuilt the AndroidTest APK with the same source inputs/heap/worker caps;
returned **0**, 67 tasks. Final test APK SHA:
`2ec01569f35853bfe11aedfb6fe8cf3ed2de96d1220db4fe69cfe1d53f1e6ecc`.
Paths are `worker/build/outputs/apk/platformProbe/worker-platformProbe.apk`
and `worker/build/outputs/apk/androidTest/platformProbe/worker-platformProbe-androidTest.apk`.

Started read-only `note-runtime-api36` and
`phone-local-platform-input-api31` AVDs one at a time, with 1,536 MiB memory,
two cores, no window/audio/snapshot and swiftshader_indirect. Confirmed
actual device API with `getprop ro.build.version.sdk`, installed both
probe APKs with `adb -s emulator-5554 install -r`, and ran:

```sh
toolchains/android-sdk/platform-tools/adb -s emulator-5554 shell am instrument \
  -w -e mode experimentalPlatform \
  dev.smallwright.worker.platformprobe.test/dev.smallwright.worker.ConfinementInstrumentation
# Additional comparison only on API31:
toolchains/android-sdk/platform-tools/adb -s emulator-5554 shell am instrument \
  -w -e mode api36ResourceOnApi31 \
  dev.smallwright.worker.platformprobe.test/dev.smallwright.worker.ConfinementInstrumentation
```

Both devices reported: **Source-built API 36 headers: PdfDocument and native
note editor with generated R compiled to disposable unsigned APKs under
worker UID**. This is actual ECJ/D8/AAPT2 execution in the worker context,
with no handwritten platform source stubs selected. The note DEX must contain
only generated app class definitions, and the probe checks resource/DEX
entries before cleaning its scratch. API31's explicit UID assertion passed;
package list evidence gives worker probe 10106, normal worker 10105 and
controller 10107. Host manifest inspection confirms no Internet permission.
API31 also reported its device framework rejected the API36 attribute and
the source-derived resource table linked it successfully. Both AVDs were
stopped using `adb emu kill`; physical phone was disconnected throughout.

Ignored evidence: `source-headers-{worker-build.log,worker-build-retry.log,
uid-test-build.log,probe-apk-receipt.json,emulator-api31.log,emulator-api36.log,
runtime-api31.txt,runtime-api36.txt,resource-runtime-api31.txt,
runtime-api36-package.txt}`. Normal debug APK hashes independently matched
their existing receipts. The APK now verifies collection/packaging of the
four new LLVM notice/credit files, which were not in earlier APKs.

These are source **baseline** headers including 54 future classes, not a
flag-filtered SDK parity pass. The current emulator worker still uses its
existing packaged resource/dex/compiler executables; this does not switch
normal AAPT2 or establish whole-toolchain rights. No generated note app was
signed, installed or executed in this header probe, and no physical/ARM64
performance or cloud call is claimed. Next: source release/history filtering,
transitive notice review and an authenticated Binder/sign/install trial.


## Source-header controller sign/install/edit/restart journeys

`sourceHeadersNoteOnly=true` selects an emulator-only variant of the existing
ProjectInstallProbe. It creates a fresh project/package, compiles a real native
note editor that also opens/closes PdfDocument, builds through the authenticated
platformProbe Binder worker, signs per project and publishes the validated
receipt. It then uses the controller Install button and Android's confirmation
UI, launches the installed generated Activity, sets native text, force-stops
only that new fixture package, relaunches it and checks retained text. This
uses no AI fixture response and no host-built generated APK.

The bounded offline harness assemble plus worker lint command passed (176 tasks):

```sh
source scripts/env.sh
./gradlew :controller:assembleDebug :controller:assembleDebugAndroidTest \
  :worker:assemblePlatformProbe :worker:assemblePlatformProbeAndroidTest \
  :worker:lintPlatformProbe -PplatformProbeTests=true \
  '-PexperimentalSourceHeadersJar=toolchains/platform-api36-source-probe/full source headers/headers.jar' \
  -PexperimentalPlatformResources=toolchains/platform-api36-source-probe/resource-r2-table-script.apk \
  --offline --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx1536m -XX:MaxMetaspaceSize=512m'
```

Using the same read-only AVDs and memory limits listed above, confirmed API36
and API31, installed the probe worker/controller/controller-test APKs with
`adb -s emulator-5554 install -r`, then ran on each:

```sh
toolchains/android-sdk/platform-tools/adb -s emulator-5554 shell am instrument \
  -w -e sourceHeadersNoteOnly true \
  dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
```

Both returned **Source-header API 36 note: controller-to-worker build/sign,
explicit install, native text edit and restart persistence passed**. UID
inventories show controller/probe worker 10150/10164 on API36 and
10107/10106 on API31. The emulator-only tests operate on freshly created
project packages, preserving pre-existing project/app data.

Both controller and probe worker contained all **42 current notice files**
byte-for-byte; both source assets matched their pinned hashes and the SDK
reference asset was absent. Tested APKs are preserved under ignored
`source-headers-install-with-resource-apks/` (relative to the probe root):

| APK | SHA-256 |
| --- | --- |
| worker-platformProbe.apk | `1a04e9faebad120f686da557ba95a1adf2d2819fbb42452d528035d8195bd9ba` |
| worker-platformProbe-androidTest.apk | `c8ef5b07b22b1720a1ca5a3d8f02bb29dce2866dc4df20aef9e5d2f0c9a0de52` |
| controller-debug.apk | `b45b0b1b6e08dab81ae2b91f1946367bc715aba7964f3e9fb4f146671946fbc7` |
| controller-debug-androidTest.apk | `ec1f38afd94b741e12f8fbafbc90bcf1131bad1079092e8c32cb3e216edcdd5e` |

### Source headers with device resource fallback

The new resource selector prevents a class-only header JAR from becoming
AAPT2's `-I` table. Three file-selection regression tests cover source-only
fallback, explicit resource precedence and SDK/device defaults. Tests first
failed with the missing method, then temporarily restoring the old selection
produced two assertion failures. Fixed green worker execution passed 43 tests;
main independently checked **11 ExperimentalPlatformInput cases, zero failures**.
No red/green log artifact is assumed if not preserved by the agent; the test
execution record and final XML are the evidence for this narrow change.

The source-only build (omit `experimentalPlatformResources`, keep all other
source-header/cap flags) initially returned 0, but APK inspection found the
previous table still present. AGP had not scheduled the Sync producer. Explicit
asset/lint staging dependencies corrected that. The first retry returned 1
because lowercase lint task names were missed; case-insensitive lint matching
corrected the remaining dependency. Final assemble/lint returned **0**, 89
tasks. Its actual APK contained the pinned source headers, **no SDK JAR and no
resource asset**, plus all 42 current notices. Preserved APK:
`source-headers-device-resource-worker.apk`, SHA-256
`3ef5e9a39136c108413dfc285a7ae0cf01b1718a98e6b26e81265ac749023b9b`.

The same controller install journey passed on a fresh read-only API31 session
with that source-only worker APK. Therefore the real worker linked the native
note against device resources, signed/installed it and retained text after
restart. This fallback does not provide API36-only resource attributes on
older devices; the separately supplied table is needed for that experiment.

Ignored evidence in `toolchains/platform-api36-source-probe/`:
`source-headers-install-{build.log,apk-receipt.json,runtime-api31.txt,
runtime-api36.txt,uids-api31.txt,uids-api36.txt,emulator-api31.log,
emulator-api36.log}`, `source-headers-fallback-build{.log,-retry.log,-final.log}`,
`source-headers-device-resource-{receipt.json,emulator-api31.log,runtime-api31.txt}`.
All emulators were stopped. Production header/tool choices are unchanged.
These probes use the unfiltered source baseline, existing compiler/resource
executables and no cloud calls. They establish emulator install/runtime
behavior for these fixtures, not full API/release/redistribution clearance,
physical S23 testing or whole ARM64 source-toolchain compatibility.

## Unflagged source-maintained Android 14 history inputs

Tracked inventory: `toolchain/platform-api34-source-history-inputs.json`.
The exact android-14.0.0_r1 source graph contributes non-updatable base plus
26 bootclasspath modules. Main independently checked all annotated tag objects,
peeled commits, copied signature/declaring-BP hashes and actual source blobs,
and the graph's ordered module list. These 27 signatures contain **5,716
fully qualified types, zero duplicates and zero FlaggedApi annotations**.
Eight module contributions are valid signature-header-only files.

No prebuilt SDK API text/classes contribute. Current source files are not
claimed to equal the frozen SDK34 release. This is the next candidate historical
input for the actual r15 release-filtering experiment. Ignored collection and
main verification receipts are `api34-source-history/{manifest.json,
collect-api34-history.py,main-verification.json}`. A misleading bare-name/ctor
count in the agent's first parser was rejected and corrected before acceptance.

## Source release filtering — 2026-10-02

The header builder now accepts an optional `--release-filter` manifest. It
verifies the flag XML, history manifest and every historical signature hash
before creating output or launching tools. It builds the current signatures
into a real JAR, merges the pinned previous source contributions, applies
stock Metalava's JAR-model flag reversion, and compiles the filtered stubs
with an empty boot/classpath. Signature-text codebases are prefiltered by
Metalava, so applying flags directly to merged text would not remove future
APIs. Source-built annotation helpers remain the only added definitions.

The existing `build()` interface is the tested seam. A changed policy first
failed because the filter option did not exist; its green case rejects the
changed bytes before any tool/output. A real source integration case first
published `android.nfc.WlcListenerDeviceInfo` under an empty export policy and
failed. After implementation it passed using actual Metalava/JDK and all 32
pinned Android15 source contributions. One intermediate attempt failed at
filter exit255 because the test XML omitted Metalava's namespace; correcting
that input preserved the future-class assertion. The empty configuration is
only a regression control, not a chosen release policy.

### Actual AP3A source inputs

[The policy inventory](../../toolchain/platform-api35-release-policy-inputs.json)
records all **271** public signature flag decisions with exact declaration and
value hashes, source paths and commits. Collection downloaded 146 declarations
in 65 packages; the public decisions depend on 90 distinct declaration files
and 219 value files. Main independently checked every downloaded declaration,
1,257 AP3A value-set files, 13 root files and ten copied policy-source hashes,
then independently parsed all public flag defaults/overrides and compared them
with real generated caches. The selected public states are:

| State and permission | Flags |
| --- | --- |
| Enabled, read-only | 219 |
| Disabled, read-only | 11 |
| Disabled, read-write | 41 |

AP3A automatically inherits root in the pinned release evaluator; its
`prior_stages: "trunk"` describes flag advancement, not value inheritance.
The root value set affects only `com.android.aconfig.test`, with no public
package intersection, so root+AP3A and AP3A-only public XML bytes match.
This was checked rather than assumed. The exact source controls are
[release evaluator](https://android.googlesource.com/platform/build/soong/+/3fe78dc9b32f1ac3f7eb279ae9376d7037b18452/cmd/release_config/release_config_lib/release_config.go),
[AP3A config](https://android.googlesource.com/platform/build/release/+/e1dfb3dc783166aadcf150d67d3d53d68001883a/release_configs/ap3a.textproto),
[Soong filtering](https://android.googlesource.com/platform/build/soong/+/3fe78dc9b32f1ac3f7eb279ae9376d7037b18452/java/droidstubs.go)
and [aconfig defaults](https://android.googlesource.com/platform/build/+/3d9c9aacdb6ca0b745b3048dad88d0db4680d50e/tools/aconfig/aconfig/src/commands.rs).

The actual pinned r15 `keep-flagged-apis.sh` also processed the real cache
selection successfully. The cache generator is the existing source-built
Android16 executable, SHA256
`1f393c5a5d33a65df437824a88adb5d3b6e9e3e430654195ef83b497bc483a85`;
its selected states were independently checked against r15 source defaults
and values. It is not represented as an r15-built tool. The r4 XML model
finalizes enabled immutable annotations differently from r15 revert exceptions,
and module-specific `aconfig_declarations` dependency selection is still open.
These inputs do not establish canonical frozen-SDK equivalence.

### Build results and strict rejection

| Source candidate | Classes | SHA256 |
| --- | --- | --- |
| Unfiltered Android15 signatures | 6,027 | `1f15810d8572558a48c14df089886353c40ce1388ec06a52b9c1576f74e18a29` |
| AP3A-filtered Android15 with unflagged Android14 source history | 5,977 | `df5e7640e98d7bbab687ffe276f052b585760de599a5cb9a37fa34eb6f6a77c1` |
| Android16 with existing BP2A source flag policy and filtered Android15 source history | 6,227 | `a705b40a43b7067735b33ba97d83dc676f54810bb43bb692534c60df0ad3e2a8` |

Each builder invocation returned0. Both filtered builds completed all nine
real tool stages. The API35 result omits the future NFC charging class while
retaining the enabled observe-mode API. The API36 candidate uses no SDK
classes/history for definitions. Its unchanged strict comparison against
API36 r2 returned **1**: 6,227 classes on both sides, zero missing/extra,
and **1,949 different classes**. It is rejected for release.

Diagnostics do not change that gate. They found no differing field constants,
no missing/extra fields and no class hierarchy differences. Method modifiers,
annotation/nullness metadata and inherited method materialization differ.
After resolving candidate inheritance, the six previously identified callable
methods remain absent: `canBeSatisfiedBy` on EthernetNetworkSpecifier,
L2capNetworkSpecifier, WifiNetworkSpecifier and WifiAwareNetworkSpecifier,
and `redact` on L2capNetworkSpecifier/WifiNetworkSpecifier. Exact modifiers,
nullness history and upstream module stub assembly remain required.

Reproduction after restoring the pinned ignored inputs:

```bash
source scripts/env.sh
PLB_HEADER_FILTER_INTEGRATION=1 PYTHONPATH=scripts python3 -m unittest test_build_platform_source_headers
python3 scripts/build_platform_source_headers.py --manifest toolchain/platform-api35-source-history-inputs.json --release-filter toolchain/platform-api35-release-filter.json --metalava toolchains/platform-api36-source-probe/out-r4/metalava/metalava/build/install/metalava/bin/metalava --jdk toolchains/jdk-21.0.12.1+1 --annotation-sources toolchains/platform-api36-source-probe/metalava/stub-annotations/src/main/java --output toolchains/platform-api36-source-probe/api35-release-20261002/ap3a-filtered
python3 scripts/build_platform_source_headers.py --manifest toolchain/platform-api36-source-header-manifest.json --release-filter toolchain/platform-api36-release-filter.json --metalava toolchains/platform-api36-source-probe/out-r4/metalava/metalava/build/install/metalava/bin/metalava --jdk toolchains/jdk-21.0.12.1+1 --annotation-sources toolchains/platform-api36-source-probe/metalava/stub-annotations/src/main/java --output toolchains/platform-api36-source-probe/api35-release-20261002/api36-filtered-with-source35-history
python3 scripts/compare_platform_abi.py --reference toolchains/android-sdk/platforms/android-36/android.jar --candidate toolchains/platform-api36-source-probe/api35-release-20261002/api36-filtered-with-source35-history/headers.jar
```

The builder refuses existing output; select a fresh directory for repetition.
Regenerated history paths/hashes must be explicitly updated rather than silently
replacing pinned inputs. The corrected root+AP3A XML has the same bytes used
by the successful first build. `platform-api35-filtered-source-history.json`
pins its generated signature for the second stage. Strict API36 exit1 is
expected until its actual differences are repaired.

Nine header-builder tests, including the real integration, passed. The regular
Python discovery run passed 34 cases and skipped the opt-in integration; that
case was separately executed successfully. Raw receipts, logs, selected inputs
and diagnostics are under ignored
`toolchains/platform-api36-source-probe/api35-release-20261002/`.
No APK was built/installed and no cloud call ran in this checkpoint. Source
license/notice mapping remains open for unmarked metadata files; header
observations in the inventory do not grant redistribution clearance.

## Libcore warning-nullability policy — 2026-10-02

The actual libcore commit `fff4fcc0cf7f080cf2511cbb57561482b13b218f`
JavaLibrary.bp records `--force-convert-to-warning-nullability-annotations
+*:-android.*:+android.icu.*:-dalvik.*`. Pinned source API text identifies
79 module packages. The checkout contains API/BP/legal metadata, not raw Java;
these are signature/JAR policy experiments. Eight inputs plus90 installed
Metalava and45 JDK files are hashed before tool launch; child environments
contain only bounded tool paths/options and locale. Matching SDK SHA256
`d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a`
is comparison-only and supplies no definitions to either compilation.

Both experiments first fail exact fixtures for ByteArrayOutputStream.buf,
toByteArray and Objects.isNull parameter annotations. Actual Metalava applies
RecentlyNonNull/RecentlyNullable; Os.getsockoptTimeval remains NonNull because
android.system is excluded. Root reran both final cases together:2passed23.307s.

The whole current-text contribution compiles1394 stubs/1515 classes, but does
not consume release flags. It adds9 HPKE classes, Os.madvise(JJI)V,
Duration.isPositive()Z and24 MADV constants; no declarations are removed.
Its strict result is6227 reference/6236 candidate, zero missing,9extra,
1917different. This must not be counted as aggregate release progress. Fresh
root receipt is `libcore-nullability-20261002/integration-36ed56d3d8f04b80a94bc8251e716b7a/receipt.json`,
candidate `whole-libcore-policy-probe.jar` SHA256
`54a7c3e1f7528f39c8e6b54566ff6f539601e0a24f1556ce14096236381d4e1b`.
Added ZIP entries retain current timestamps, so whole-text archive bytes are
not claimed deterministic. Earlier successful/failed receipts remain retained;
the first preflight used an unannotated Android control and failed before tools.

The released-JAR case selects exactly1506 classes from the source-only,
filtered SystemClock baseline SHA256
`17f3b655406c4138627386af95ffeaa7c591f2a31d5c97d57dd17e804c5ccf37`
using the pinned module packages, then compiles1387 stubs under the actual
warning policy. Root independently rehashed all8 source inputs/135 tool files,
replayed the package-selection receipt, and checked full class/member inventory,
flags, hierarchy/class metadata and exact annotation transformations. Outside
module class bytes remain identical; only78 field and1671 method attribute
entries differ (1108 declaration and1074 parameter attributes). Four bounded
fixtures separately match SDK metadata. All6227 class keys remain;37 newly
match full SDK ABI, and no previously matching class regresses.

The unchanged strict gate still rejects it with1905 differing classes and
zero missing/extra. Fresh root receipt is
`libcore-nullability-20261002/integration-455ad551fb5741ccbafa7a8adad244d5/receipt.json`,
candidate `released-libcore-policy-probe.jar` SHA256
`405f5d72e99240fdf0178e4f9f64d5ef80d03db50996ff30811343c3deabf083`.
Root's complete mapping/inventory review is preserved beside the byte-identical
earlier candidate in `integration-e71b3007fc7945b6b59d94485276c804/root-independent-review.json`;
fresh source/tool/stage/strict replay is `root-reproduction-review.json`.
All paths above are under ignored `toolchains/platform-api36-source-probe/`.

```bash
PYTHONDONTWRITEBYTECODE=1 PLB_LIBCORE_NULLABILITY_INTEGRATION=1 PYTHONPATH=scripts python3 -m unittest test_libcore_nullability_policy
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/compare_platform_abi.py --reference toolchains/android-sdk/platforms/android-36/android.jar --candidate toolchains/platform-api36-source-probe/libcore-nullability-20261002/integration-455ad551fb5741ccbafa7a8adad244d5/released-libcore-policy-probe.jar
```

Regular discovery passed34 cases and skipped7 opt-in cases (41total,6.343s).
No compilation success inferred from those skips. Test helper and two docs are
the only tracked changes; no worker/tool pins, APKs, strict comparison rules or
license decisions changed. Next work belongs at existing per-module header
assembly, preserving flag/history selection. These experiments do not establish
complete raw-source equivalence, public release rights or physical compatibility.

## Integrated libcore header builder — 2026-10-02

The existing builder consumes optional root manifest field
`libcore_warning_policy: {path, sha256}`. It validates the actual BP bytes and
supported upstream pattern before tools/output, requires one
art.module.public.api contribution, derives packages from pinned signatures and
rejects ownership shared with another contribution. Of79 packages,73 are
eligible; exact positive selectors preserve the upstream android/dalvik
exclusions (including package roots) without applying a broad wildcard to
other modules. Both main-stubs and final filter-release-api receive the same
selector. Policy/source/ownership details are retained in the build receipt.
The tracked API36 manifest now explicitly selects the pinned libcore BP.

Two validation tests first failed at the forbidden tool-launch guard under
the old builder. Five now pass (tamper, shared owner, wrong module, unsupported
pattern, exact-root scope). The real no-policy build supplies the annotation
RED control; the policy build supplies GREEN. Root reran all final cases:
6passed103.255s. Each actual build completed nine tool stages. Both have1506
module/1511 whole classes, identical member keys/flags and class metadata.
Every final member attribute matches the exact upstream warning conversion;
this includes surviving flagged/history-reverted declarations, resolving the
specific concern that history substitution might bypass conversion. Future
HPKE/MADV/Duration APIs remain excluded. Merged/filtered API text and release
selection remain byte-identical.

Fresh root module-only evidence:
`libcore-builder-20261002/integration-bee2415ad57140d18845419a5aa4e151/`.
No-policy headers.jar SHA256
`a5dcc98a5aecae65d2aefece60b7f522861902ceac4d73be766f4805f67910fd`;
policy headers.jar SHA256
`9f17a0571e53d9e5f462bcf5c133c1382b4173a0595ebfa2670f3f585ba4743b`.
Root independently rehashed5 source/BP/flag/history inputs,55 source annotation
helpers and135 installed tool files, checked all metadata transformations and
compared every module ABI to the proven no-policy/source baseline and earlier
released-JAR policy experiment. Zero profile differences. SDK definitions were
not used. Root review is `root-independent-review.json`; earlier validation RED
and agent-run receipts remain in the same ignored root.

Root also built all37 contributions with the policy and actual BP2A flags plus
source-filtered AP3A history. Actual build completed nine stages in92.891s.
The tracked manifest independently resolves to the exact contribution hashes
and policy used by this build; the executed call was build(BuildArguments(...)).
Input/tool/helper/policy hashes were saved before tools in
`libcore-builder-20261002/aggregate-root-prelaunch.json`.

Full candidate:
`libcore-builder-20261002/aggregate-2265914c3d69485487387a9e8eecd83f/headers.jar`,
SHA256 `e36f41ffc131afab50024cdc1d269ba1bd96c06387d3662580c7d7af868fe27a`.
All6227 classes/member keys, flags and class metadata remain;99 owners change
only through the scoped annotation mapping. Outside-libcore bytes are exactly
unchanged; merged/filtered API text remains identical. Unchanged strict SDK
comparison returns1:1912 differing classes, zero missing/extra. Thirty-seven
newly match SDK ABI and none regress, compared with the corresponding unmodified
aggregate's1949 differences. This aggregate has no network/raw-modifier
overlays; the earlier layered candidate's1905 result is separate.

Reproduction after restoring the pinned ignored inputs:

```bash
PYTHONDONTWRITEBYTECODE=1 PLB_LIBCORE_BUILDER_INTEGRATION=1 PYTHONPATH=scripts python3 -m unittest test_libcore_header_builder
python3 scripts/build_platform_source_headers.py --manifest toolchain/platform-api36-source-header-manifest.json --release-filter toolchain/platform-api36-release-filter.json --metalava toolchains/platform-api36-source-probe/out-r4/metalava/metalava/build/install/metalava/bin/metalava --jdk toolchains/jdk-21.0.12.1+1 --annotation-sources toolchains/platform-api36-source-probe/metalava/stub-annotations/src/main/java --output toolchains/platform-api36-source-probe/libcore-builder-20261002/FRESH-OUTPUT-DIRECTORY
python3 scripts/compare_platform_abi.py --reference toolchains/android-sdk/platforms/android-36/android.jar --candidate toolchains/platform-api36-source-probe/libcore-builder-20261002/aggregate-2265914c3d69485487387a9e8eecd83f/headers.jar
```

Regular discovery47cases:39passed/8opt-in skipped,4.609s. Actual integration
results above are separate. No worker input, APK, strict comparator or rights
decision changed. Raw-source/native/final/history semantics and component
license/notice duties remain unresolved; no canonical or release pass.

## Whole OpenGL source group experiment — 2026-10-02

`scripts/test_opengl_module_source_headers.py` is an opt-in host experiment,
not a worker-input selector. It pins the actual `framework-opengl-sources`
filegroup in `frameworks/base/opengl/java/Android.bp`, whose `**/*.java` glob
contains52 existing raw files. All52 are copied byte-identically into a fresh
isolated source root; the emitted public scope includes android.opengl and
javax.microedition.khronos. Hidden JNI implementations are retained as inputs.
The earlier selected inventory included only41 files from this group. No
neighboring source directory is passed to Metalava or javac.

Resolution uses the source-only libcore-policy aggregate above, SHA256
`e36f41ffc131afab50024cdc1d269ba1bd96c06387d3662580c7d7af868fe27a`.
This substitutes public source headers for canonical Soong dependencies and
does not establish complete module closure. BP2A release config and source-only
AP3A filtered history keep their existing pins. The framework itself independently
requires the warning-nullability option at pinned `api/Android.bp:406`, under
android-non-updatable-stubs-defaults; the test validates that exact option rather
than borrowing libcore contribution policy. No OpenGL/javax annotation changes
were observed.

Real helper javac uses pinned android.annotation.NonNull,
androidx.annotation.NonNull and actual UnsupportedAppUsage sources. The latter
was absent locally and fetched only from official AOSP tools/platform-compat,
Android16r2 commit `db0de6104d8527114a7ae441b0a9c35f885d2876`:
`java/android/compat/annotation/UnsupportedAppUsage.java`, SHA256
`80e83654d128662e4f93f2ad003d033fd4ff9bd13ef3bb805a28f1f898f8ecda`.
Its Apache-2.0 source header is retained. The actual adjacent Android.bp has
SHA256 `db0664e3ac5ce4a33ab69b73956c360b1edf345806e13326f699b0266a7027dd`.
Commit/ref, URLs, bytes and failed initial policy-path lookup remain under
ignored `opengl-module-20261002/helper-fetch-58fde64fe0c745faa850d52e56ee0fc9/`.
No SDK source/class definition supplies this helper or any output declaration.

Baseline RED is exact GLES20.glActiveTexture(int) flags0x9 versus raw native
0x109, plus ten absent direct overrides independently read from EGL handle
classes and GLSurfaceView. Initial real run
`integration-50aa1e20cd7940769b7bfac8c11070be` failed Metalava with255: four
DeprecationMismatch errors, two InvalidJavadoc warnings and unresolved
AppGlobals/IPackageManager imports in hidden GLImpl. Those four errors already
appear in the unchanged upstream `core/api/lint-baseline.txt`, SHA256
`2b41bf5cd9a6eb8c9904721995e070b69f3922131019697941417eae642ccad6`.
`core/api/Android.bp` maps that file to non-updatable-lint-baseline.txt;
`api/StubLibraries.bp` supplies it to the public source-stub extraction.
The follow-up uses the complete pinned file through `--baseline:api-lint`,
without adding baseline entries or hide flags. All other diagnostics remain.

Follow-up `integration-76d2aa032cfa45e7a0dfeb9e8c99f5e2` completed source tools
but caught a test-reader error resolving nested DebugProc/DebugProcKHR types.
That attempt is retained. The reader now uses actual nested type declarations.
A read-only full metadata review of its49 emitted classes found zero errors
after that correction; no assertion or output policy was relaxed.

Fresh final source run:
`opengl-module-20261002/integration-d93dd9dd0607445d8278b4885cbb03d6/`.
Helper javac passed0.478s, actual raw Metalava extraction passed5.404s and
public-stub javac passed0.933s. All49 emitted classes preserve exact class
inventory, flags, hierarchy, fields and attributes. Every existing method
preserves all attributes and changes only independently raw-derived native
flags:920 changes. Exactly ten independently specified raw overrides are added,
including their access flags and finalize throws attribute. No methods are
removed. The diagnostic overlay preserves all6227 aggregate class entries and
all bytes outside the49 emitted classes.

Only after those source-only assertions does the SDK open as an independent
comparison reference. All49 emitted class ABIs match it. Unchanged strict
comparison remains a failure:1890 differing classes, zero missing/extra,
22 newly matching classes and zero regressions relative to1912. Host-only JAR:
`integration-d93dd9dd0607445d8278b4885cbb03d6/raw-module-probe.jar`, SHA256
`f2a7a007e9a80521bff1bb43542f84ce2099c20be0f766d2b77afe0c62ef9f3a`.
SDK bytes never feed the output.

**The opt-in test still fails module closure.** It ran10.742s and ends at the
strict assertion for the two retained hidden imports: android.app.AppGlobals
and generated android.content.pm.IPackageManager. The receipt records public
emitted source ABI GREEN separately from whole-module closure RED. This is
not canonical module or release GREEN. Fifty-two raw files, three helper
copies,66 source/input hashes and90 Metalava/45 JDK installed-file hashes are
preserved before tools; the child environment is explicitly sanitized.
Public source profiles, commands, full diagnostics, release-selected API and
strict comparison remain beside the receipt.

Final script changes after that run only clarify receipt keys
`public_emitted_abi`/`module_closure` and validate the already pinned framework
option; no further compiler ran. Final script SHA256
`deab3615b3eda2e28128314f523bfcc45b02a025ac518cd5653289f6363d9b52`.
The exact executed script SHA and final review SHA are recorded separately in
`opengl-module-20261002/review-08317f5e42e34ed3ab899311cebe22cc.json`.
Root independently replayed the final exact script in10.874s:
`integration-cd7ad9ba3bfa4530bfbd95095bd63780/`. All three source stages return0;
the test correctly fails the same final hidden-import closure assertion. Root
rehashed66 inputs,55 isolated copies and135 installed tools, and independently
checked every field/class/method attribute,920 raw native changes and ten raw
overrides. Outside-owner bytes are identical; candidate SHA256 is the exact
same `f2a7a007...` as the earlier run. The unmodified strict comparison remains
1890 differences. `root-independent-review.json` records that review; no release
or canonical-closure pass is claimed.

```bash
PYTHONDONTWRITEBYTECODE=1 PLB_OPENGL_MODULE_INTEGRATION=1 PYTHONPATH=scripts python3 -m unittest test_opengl_module_source_headers
python3 scripts/compare_platform_abi.py --reference toolchains/android-sdk/platforms/android-36/android.jar --candidate toolchains/platform-api36-source-probe/opengl-module-20261002/integration-d93dd9dd0607445d8278b4885cbb03d6/raw-module-probe.jar
```

The first command is expected to fail until actual hidden-source closure is
available; a skipped opt-in invocation is not evidence. Next work is bounded
actual framework/AIDL resolution for these imports, retaining complete source
selection and the unchanged strict comparator. No APK, worker manifest/input,
production header builder, STATUS entry or redistribution clearance changed.
