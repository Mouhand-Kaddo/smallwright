# API 36 compile definitions: redistribution research

Historical. Neither APK bundles Android platform files or stubs built from
them any more. Setup downloads Google's platform files on the phone
([ADR 0002](adr/0002-android-platform-files-downloaded-from-google.md)).
This record is kept as research evidence only.

2026-09-27. Scope: the exact API 36 revision 2 platform JARs in the ignored local SDK, and a usable path for phone-local Java compilation. This is an evidence and implementation audit, not a legal clearance decision. No JAR was changed or built for this research.

A later [2026-10-12 host reference build](validation/published-api-source-reference-20261012.md)
compiled the complete SDK-distributed source inventory with actual AOSP
annotations and matched all 6,227 class declarations. This supplies engineering
evidence absent when this research was written; it does not clear the component
or SDK-package redistribution questions below. No candidate entered an APK.

The [2026-10-12 component follow-up](validation/api36-component-provenance-20261012.md)
now records every source/class binding, retains fourteen exact notice texts,
locates XML Pull and HTTP component terms, and regenerates the five raw XML Pull
definitions while preserving the full declaration match. Signature-provider
membership remains separate from raw-source ownership and release clearance.

## Package-specific terms and their limits

The [API 36 r2 package metadata](https://android.googlesource.com/platform/prebuilts/fullsdk/platforms/+/1b45636761b0dbab9f80779add8fc85ab94b80fb/android-36/package.xml) embeds `android-sdk-license`. Its section 1.1 explicitly includes **packaged APIs** in the SDK. Sections 3.1–3.5 say:

| Section | Relevant rule in the installed package | Consequence / limit |
| --- | --- | --- |
| 3.1 | Limited, non-sublicensable SDK use solely to develop applications for compatible Android implementations. | Building Android apps is the granted purpose; this is not by itself a right to pass SDK files to users. |
| 3.2 | Do not use **this SDK** to develop another SDK or apps for other platforms. | The prohibition concerns use of the Google SDK package. It does not by itself govern independently acquired AOSP source used under applicable open-source grants. Direct SDK-package-derived or user-downloaded SDK routes warrant contract review. |
| 3.3 | Google or third parties own the SDK's applicable intellectual property; rights not granted are reserved. | Public download or public Git access does not create a general license. |
| 3.4 | Except as applicable third-party licenses require, no copying beyond backup, modification, redistribution, reverse engineering or derivatives of any SDK part. | Copying the installed whole `android.jar` into a worker APK is not cleared by the general SDK license. |
| 3.5 | Open-source-licensed SDK components are governed solely by their open-source licenses. | This permits a component-specific route once the relevant components and duties are established; it does **not** label the whole JAR as open source. |

The [current website terms](https://developer.android.com/studio/terms) have the same material 3.1–3.5 language as inspected on 2026-09-27, but are dated **April 28, 2026** and differ in some other wording, including acceptance by clicking **and/or using** the SDK and Google LLC's description. The installed r2 `package.xml` is the exact package-specific record and is the basis for this audit; the live website is corroboration, not a substitute for that embedded text. Neither text supplies a sideload or private-alpha redistribution exception. These are interpretations of the cited contract text, not a legal ruling.

## Exact artifacts and source evidence

Read-only local checks produced:

| File | SHA-256 | Git blob SHA-1 | Public source identity |
| --- | --- | --- | --- |
| `toolchains/android-sdk/platforms/android-36/android.jar` | `d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a` | `e560471790469782b41614eac5f290f5388d0d79` | [r2 `android.jar` blob](https://android.googlesource.com/platform/prebuilts/fullsdk/platforms/+/1b45636761b0dbab9f80779add8fc85ab94b80fb/android-36/android.jar) |
| `toolchains/android-sdk/platforms/android-36/android-stubs-src.jar` | `f06372cb9bdbe4dececd35e5cb7b61fec01bb521dcd8a2424489f659b1e9322a` | `98b4873edde5fc499b2336e6ffd1d1971983a221` | [r2 source-stub blob](https://android.googlesource.com/platform/prebuilts/fullsdk/platforms/+/1b45636761b0dbab9f80779add8fc85ab94b80fb/android-36/android-stubs-src.jar) |

The blob identities match the [pinned fullsdk r2 upgrade](https://android.googlesource.com/platform/prebuilts/fullsdk/platforms/+/1b45636761b0dbab9f80779add8fc85ab94b80fb%5E%21/). This proves public artifact identity, **not** source-to-class correspondence or redistribution permission. ZIP inspection found 6,227 `.class` entries and one `NOTICES/libcore-NOTICES.txt` in `android.jar`; that notice concerns libcore components and is not a whole-JAR grant. `android-stubs-src.jar` has 4,475 entries, 4,161 of them `.java`, and no separately named `LICENSE`, `NOTICE`, or `COPYING` entry.

The 27,768,026-byte `android.jar` is not merely a Java API library. Read-only ZIP accounting found 6,227 classes totaling 4,573,388 compressed payload bytes (12,727,253 uncompressed), alongside 8,769 nonclass entries totaling 20,653,120 compressed payload bytes (36,532,193 uncompressed). The latter include `resources.arsc` (18,220,604 uncompressed bytes), Android resources including wallpapers, `AndroidManifest.xml`, and the libcore notice. ZIP container overhead explains why payload sums need not equal file length. A class-header audit can support a **classes-only** replacement, but cannot clear those resource assets. Resource linking needs a separate input; the current `/system/framework/framework-res.apk` approach has only the cited device/fixture evidence.

An exhaustive **archive entry scan** of the 4,161 generated `.java` files (license text was not independently resolved for every group) gives this finite triage list:

| Source-stub entries | Count | Evidence / next mapping |
| --- | ---: | --- |
| Apache-2.0 header | 2,679 | Example: `android/app/Activity.java`; map applicable upstream NOTICE. |
| GPLv2 with Classpath Exception header | 1,223 | Example: `java/lang/String.java`; map exact corresponding source and source-delivery duties. |
| Unicode/ICU copyright and terms pointer | 110 | `android/icu/`; retrieve version-specific Unicode license/notices. |
| W3C Software License header | 36 | `org/w3c/dom/`; preserve W3C text/attribution. |
| Chromium BSD-style pointer | 14 | `android/net/http/`; find the corresponding LICENSE for exact source revision. |
| SAX public-domain declaration | 35 | `org/xml/sax/`; confirm upstream origin and retain declarations. |
| XML Pull Parser sources | 5 | `org/xmlpull/`; four point to an absent `LICENSE.txt`, and `XmlSerializer.java` has no header. Obtain exact upstream terms. |
| `package-info.java` with no copyright/license prologue | 51 | Documentation stubs; trace originating source/package notices. |
| Other Android stubs without a license header | 5 | `FragmentTransaction`, `AssistContent`, `AssistStructure`, `FullBackupDataOutput`, `WorkSource`; trace their source commits. |
| Copyright only | 1 | `android/speech/tts/UtteranceProgressListener.java`; trace upstream license. |
| Generated marker only | 2 | `android/Manifest.java`, `android/R.java`; trace resource/build generators and inputs. |

The category totals equal 4,161. Four XML Pull entries reference an absent `LICENSE.txt`; this is a concrete notice gap, not a finding that they are unlicensed. All 35 SAX entries contain a public-domain declaration. The 51 `package-info.java` files may contribute only documentation and no class bytes. The scan groups by visible prologue and path; it does not itself prove license applicability. Concrete inspected headers include `android/app/Activity.java` (Apache-2.0) and `java/lang/String.java` (GPLv2 plus Oracle Classpath Exception). The [AOSP stub build declaration](https://android.googlesource.com/platform/frameworks/base/+/a890d7b328dc436999d7de4f8a1dc3337b5c7944/StubLibraries.bp) describes raw sources → metalava stub sources → Java stub JAR and names multiple public module inputs.

By class-name path, **6,222 of 6,227 class entries** in `android.jar` have a same-named top-level `.java` file in `android-stubs-src.jar` (nested `$` classes map to their enclosing source file). The five unmatched classes are `android.annotation.{FlaggedApi,NonNull,Nullable}` and `androidx.annotation.{RecentlyNonNull,RecentlyNullable}`. This is strong coverage for using the generated source archive as an engineering inventory, but does not prove exact bytecode provenance. There are 51 source files with no same-named class, all `package-info.java`. Regenerating from these **SDK-distributed** source stubs plus separately sourced annotation stubs is a concrete technical experiment, but still needs a verified open-source grant for each included component and resolution of package-derived contract questions. No such JAR had been built or tested when this research was written; the linked later reference experiment supplies a strict declaration match, while rights remain unresolved. Reconstructing from independently acquired, pinned AOSP sources under their applicable licenses is the clearer rights route.

For mapped Apache-2.0 components, [the license's redistribution sections 4(a)–(d)](https://www.apache.org/licenses/LICENSE-2.0) require a license copy, marking changed files, retaining applicable copyright/patent/trademark/attribution notices, and carrying applicable NOTICE contents. For mapped GPLv2+Classpath components, the [AOSP libcore license text](https://android.googlesource.com/platform/libcore/+/05d1de77274ccd96bb5a98505da0317b5ad617c1/LICENSE) includes GPLv2 copying/source duties and an exception for linking independent modules; the exception is **not** a waiver of the GPL duties for the covered stub code itself. The [libcore build metadata](https://android.googlesource.com/platform/libcore/+/2c2d9172457f2c89c7b353b68150e5d7cc30b8fe/ojluni/src/main/Android.bp) explicitly lists both Apache-2.0 and GPL-2.0-with-classpath-exception and a source packaging mechanism. Exact obligations depend on what is distributed and its source lineage. A component-level manifest is a practical way to prove the section 3.5 case; the agreement does not state that every class must have a separate license record.

The [AOSP `prebuilts/sdk` license metadata](https://android.googlesource.com/platform/prebuilts/sdk/+/master/Android.bp) warns of **special licensing conditions** and says its broad `license_kinds` were automatically applied to all targets and may overinclude terms. It is a different prebuilt repository, and its warning is evidence against inferring a universal Apache grant, **not** proof of the exact fullsdk r2 JAR's terms. [AOSP license guidance](https://source.android.com/docs/setup/contribute/licenses) also says Apache-2.0 is preferred, with exceptions.

## Practical route and user experience

1. **Existing harness only:** the worker currently uses narrowly authored compile-only stubs and links resources against the phone's `/system/framework/framework-res.apk`; [the existing audit](toolchain-redistribution-audit.md) records one Samsung fixture and default-source worker pass. This preserves a test harness but does not provide general Android API coverage or the intended product experience.
2. **Product target:** build a **full public API parity, classes-only compile library** for the chosen Android SDK level from independently acquired, pinned AOSP source and build rules under each component's applicable open-source grants. Produce a reproducible source/build manifest, class/component lineage, complete license and notice bundle, and corresponding source where required. Use the exact r2 source archive as an inventory/reference, not as the legal source of automatically redistributable bytes. Independently obtained OSS source is governed by its applicable licenses; the Google SDK package's section 3.2 is not a blanket bar to this route. Keep resource linking as a distinct input with its own provenance: extend and validate the device `framework-res.apk` route or audit a redistributable resource package separately. Match all public signatures and compile behavior against the chosen SDK reference, then run real on-phone ECJ/AAPT2/APK tests. The [AOSP build file](https://android.googlesource.com/platform/frameworks/base/+/a890d7b328dc436999d7de4f8a1dc3337b5c7944/StubLibraries.bp) is a technical starting point, not a blanket grant for every input. This is substantial engineering work due to modules, annotations, and generated resources.
3. **Alternative:** a user-installed, on-device SDK obtained through Google's official distribution could avoid bundling platform files, but imposes a large setup/download burden. Review whether its intended use within a compiler product complies with the SDK contract. The worker's no-Internet boundary would require controller-managed acquisition and a deliberate local handoff. This route has not been implemented or validated.

## Remaining release questions

- For an approach that **copies, transforms, or distributes the SDK-package JAR/source archive**, establish the specific section 3.5 open-source component grants and review section 3.2's application. Independently acquired AOSP source with verified applicable OSS grants is a different route; no blanket Google permission requirement is inferred from section 3.2 for that route. Separately review a direct user-installed SDK route under its SDK agreement.
- For a broad replacement, start with the finite table above: resolve the 110 Unicode/ICU, 36 W3C, 14 Chromium, 35 SAX, five XML Pull, 51 documentation, five Android headerless, one copyright-only, two generated entries, and the five class-only annotation stubs. Pin source revisions and build rules, then preserve all applicable licenses/notices and GPL corresponding source. The 2,679 Apache and 1,223 GPL header groups still require their applicable notice/source packages; a header count is not a completed compliance bundle.
- Confirm the desired public API coverage and actual on-phone compile/link behavior. The existing narrow stubs prove only the tested templates; the `/system/framework/framework-res.apk` result is device/fixture bounded.
- Keep the independent AAPT2 provenance/license gate open; resolving API definitions does not clear the whole compiler worker for distribution.

Local inspection commands: `sha256sum` and `git hash-object` on the two local JARs; `unzip -Z1` for entry names; Python `zipfile` read-only header scan. No APK was built, installed, or released for this note.

### Reproduce the archive inventory

Run from the repository root. This scanner reads only the two local archives and prints the table counts and class-name reconciliation. Path-based residual groups are **triage categories**, not legal conclusions; inspect every group's source/license before shipping.

```python
from collections import Counter
from pathlib import Path
from zipfile import ZipFile

sdk = Path("toolchains/android-sdk/platforms/android-36")
with ZipFile(sdk / "android-stubs-src.jar") as sources, ZipFile(sdk / "android.jar") as binary:
    java = {n for n in sources.namelist() if n.endswith(".java")}
    classes = {n for n in binary.namelist() if n.endswith(".class")}
    count = Counter()
    for name in sorted(java):
        preamble = sources.read(name).decode("utf-8", "replace").split("package ", 1)[0]
        if "Licensed under the Apache License, Version 2.0" in preamble or "Apache License, Version 2.0" in preamble:
            group = "Apache"
        elif "GNU General Public License" in preamble and "Classpath" in preamble:
            group = "GPL+Classpath"
        elif name.startswith("android/icu/"):
            group = "Unicode/ICU"
        elif name.startswith("org/w3c/dom/"):
            group = "W3C"
        elif name.startswith("android/net/http/") and "BSD-style" in preamble:
            group = "Chromium BSD pointer"
        elif name.startswith("org/xml/sax/"):
            group = "SAX public domain"
        elif name.startswith("org/xmlpull/"):
            group = "XML Pull"
        elif name.endswith("package-info.java"):
            group = "package-info"
        elif "AUTO-GENERATED FILE" in preamble:
            group = "generated marker"
        elif name == "android/speech/tts/UtteranceProgressListener.java":
            group = "copyright only"
        else:
            group = "Android headerless"
        count[group] += 1
    source_for = lambda cls: cls.removesuffix(".class").split("$")[0] + ".java"
    missing = sorted(cls for cls in classes if source_for(cls) not in java)
    print("Java sources:", len(java), "class files:", len(classes))
    print("Groups:", dict(sorted(count.items())))
    print("Classes without same-named source:", missing)
    assert len(java) == 4161 and len(classes) == 6227 and len(missing) == 5
```
