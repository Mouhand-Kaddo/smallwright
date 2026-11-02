# API 36 XML Pull source and license evidence

2026-10-12. Scope: the five `org/xmlpull/*.java` entries in the pinned Android
SDK Platform 36 revision 2 `android-stubs-src.jar`. This is component research,
not legal advice or clearance for the SDK package as a whole. No SDK binary was
copied, modified, or placed in an APK.

## Finding

The five API definitions map to the XML Pull API sources in AOSP `platform/libcore`
at Android tag `android-16.0.0_r2`, under `xml/src/main/java/...`. The files name
the package and types found in the SDK stubs, and the r2 libcore source inventory
explicitly lists these five files together in `non_openjdk_javadoc_xml_files`.
Four source files expressly point to the XML Pull distribution's `LICENSE.txt`;
the fifth, `XmlSerializer.java`, has no license prologue, but is part of the same
XML Pull API distribution and is covered by its distribution-level license text.

The upstream XML Pull project states that **all XMLPULL API source code,
compiled code, and documentation in its distribution, except tests, are in the
public domain**. It disclaims warranty and names initial authors Stefan
Haustein and Aleksander Slominski. The license document itself is only 370
bytes. This is the applicable component evidence located for these API files;
it does not turn every file in libcore, the SDK platform, or `android.jar` into
public-domain material.

## Five-file mapping and integrity

The SDK-stub SHA-256 values below were computed over the exact entries in local
`toolchains/android-sdk/platforms/android-36/android-stubs-src.jar` (archive
SHA-256 `f06372cb9bdbe4dececd35e5cb7b61fec01bb521dcd8a2424489f659b1e9322a`).
The AOSP hashes were computed over the public raw source fetched from the
Android 16 r2 libcore tag; they are not SDK-stub hashes, because Metalava
stubs remove implementations and replace method bodies.

| SDK stub entry | AOSP r2 source path | SDK stub SHA-256 | AOSP raw source SHA-256 |
| --- | --- | --- | --- |
| `org/xmlpull/v1/XmlPullParser.java` | [`platform/libcore/xml/src/main/java/org/xmlpull/v1/XmlPullParser.java`](https://android.googlesource.com/platform/libcore/+/fff4fcc0cf7f080cf2511cbb57561482b13b218f/xml/src/main/java/org/xmlpull/v1/XmlPullParser.java) | `a4dad5b1056049a1556017764d850504a74d442b54d9e2de82ddcf6306d49e17` | `ffd59aad6f26bc53e016d1d437e83059ebb09ff9aca7ff05d0decd43a718d458` |
| `org/xmlpull/v1/XmlPullParserException.java` | [`platform/libcore/xml/src/main/java/org/xmlpull/v1/XmlPullParserException.java`](https://android.googlesource.com/platform/libcore/+/fff4fcc0cf7f080cf2511cbb57561482b13b218f/xml/src/main/java/org/xmlpull/v1/XmlPullParserException.java) | `c6ae82789151c04ab114a6f49721b223e36065682d522013c002ad191e48d946` | `a275c02f1aa601dab909b37d9e17eb9413f284d3a9736294b18e6cb1c0966a24` |
| `org/xmlpull/v1/XmlPullParserFactory.java` | [`platform/libcore/xml/src/main/java/org/xmlpull/v1/XmlPullParserFactory.java`](https://android.googlesource.com/platform/libcore/+/fff4fcc0cf7f080cf2511cbb57561482b13b218f/xml/src/main/java/org/xmlpull/v1/XmlPullParserFactory.java) | `d4fb43b37e898bed56819877e004b1193769596b7248a86ab8c05ffa99a9b923` | `a5749154add84344cacfc64900816938d5ee1752fc6c5982f68fd3cb88316254` |
| `org/xmlpull/v1/XmlSerializer.java` | [`platform/libcore/xml/src/main/java/org/xmlpull/v1/XmlSerializer.java`](https://android.googlesource.com/platform/libcore/+/fff4fcc0cf7f080cf2511cbb57561482b13b218f/xml/src/main/java/org/xmlpull/v1/XmlSerializer.java) | `22b07819dbed0adca56368307aba5a9cb890a9f02fff983985edbc16f2b8e27d` | `8264a479c66efc52a5acdab9c294850a3b0e986bb2263d3e3b18e48a4eb8fc5a` |
| `org/xmlpull/v1/sax2/Driver.java` | [`platform/libcore/xml/src/main/java/org/xmlpull/v1/sax2/Driver.java`](https://android.googlesource.com/platform/libcore/+/fff4fcc0cf7f080cf2511cbb57561482b13b218f/xml/src/main/java/org/xmlpull/v1/sax2/Driver.java) | `980d86746d26b304a3ed377b7aadb5ae65208a9b3c4d5e8a521780ee283888cc` | `244f78e9c8f2be525c84425f60a7c9f156bb8c63c3846c8e50d85ba89e716fcc` |

The raw file headers for `XmlPullParser`, `XmlPullParserException`,
`XmlPullParserFactory`, and `sax2/Driver` each say “for license please see
accompanying LICENSE.txt file” and link to `xmlpull.org`. The SDK stub copies
preserve that pointer. The SDK stub for `XmlSerializer` begins directly with its
package declaration; no file-specific license text appears there. The upstream
distribution-level license is therefore particularly relevant to this fifth
file.

## Exact revision and source/build lineage

The [Android 16.0.0 r2 manifest](https://android.googlesource.com/platform/manifest/+/refs/tags/android-16.0.0_r2/default.xml)
sets the default revision to `refs/tags/android-16.0.0_r2` and declares
`platform/libcore` at path `libcore`. The peeled r2 libcore commit is
[`fff4fcc0cf7f080cf2511cbb57561482b13b218f`](https://android.googlesource.com/platform/libcore/+/fff4fcc0cf7f080cf2511cbb57561482b13b218f).
Its [`non_openjdk_java_files.bp`](https://android.googlesource.com/platform/libcore/+/fff4fcc0cf7f080cf2511cbb57561482b13b218f/non_openjdk_java_files.bp)
defines `non_openjdk_javadoc_xml_files` with all five exact paths and
`path: "xml/src/main"`. This ties the five API types to the pinned libcore
source inventory rather than to the similarly named kXML implementation.

The local SDK source archive is the exact public API 36 r2 prebuilt blob at
[fullsdk commit `1b45636761b0dbab9f80779add8fc85ab94b80fb`](https://android.googlesource.com/platform/prebuilts/fullsdk/platforms/+/1b45636761b0dbab9f80779add8fc85ab94b80fb/android-36/android-stubs-src.jar),
as recorded in [the existing inventory](research-sdk-api-license.md). The
matching public-source reference experiment compiled all 4,161 Java entries
from this archive and matched all 6,227 classes and parsed declarations; see
[its validation record](validation/published-api-source-reference-20261012.md).
Those are strong engineering identity and API evidence, not byte-for-byte
source lineage for each generated stub. The source archive has no per-entry
origin manifest. The precise r2 libcore file list plus the identical API
package/types establishes the mapping with high confidence, while direct proof
of the upstream source-to-Metalava-output transformation for these five
individual files remains unavailable in the archive.

## Independently tested raw-source regeneration

A later parent-run experiment isolated these five exact raw r2 files, generated
exactly five Java stubs with stock Metalava and compiled them with the local
JDK and source-built API resolution inputs. All five parsed declarations match
the SDK. Inserting all five outputs into the complete reference preserves its
6,227-class strict declaration match. This demonstrates the source-to-stub route
for this component; it does not establish the historical fullsdk build recipe.
The verified notice is now retained at
[`toolchain/api36-component-notices/xmlpull-LICENSE.txt`](../toolchain/api36-component-notices/xmlpull-LICENSE.txt).
See [exact commands, failures and parent review](validation/api36-component-provenance-20261012.md).

## Applicable license text and limits

The [official XML Pull project license text](https://www.xmlpull.org/v1/download/unpacked/LICENSE.txt)
reads, in substance, that all XMLPULL API source, compiled code, and docs in the
distribution other than tests are public domain, with no warranty. Its fetched
byte length is 370 and SHA-256 is
`4eeafa29d1d9a0cdbdad0f5178cd553527329b57b5808ffe912cb9c698343612`.
The project's [official overview](https://www.xmlpull.org/) separately says
the XmlPull API is public domain and distinguishes the conformance tests as
LGPL-licensed. That exclusion does not apply to these five API sources based on
their paths and roles, but a distribution that includes tests must review the
separate test license.

The AOSP libcore root [`LICENSE`](https://android.googlesource.com/platform/libcore/+/fff4fcc0cf7f080cf2511cbb57561482b13b218f/LICENSE)
is an aggregated license file containing GPLv2 text and a Classpath Exception.
The exception text limits itself to files whose headers expressly designate
the Oracle Classpath exception. These XML Pull files instead refer to the
separate XML Pull license (or lack a prologue), so the root GPL/ClassPath text
is not evidence that these five files are GPL+Classpath. Similarly, libcore's
[`Android.bp`](https://android.googlesource.com/platform/libcore/+/fff4fcc0cf7f080cf2511cbb57561482b13b218f/Android.bp)
warns its broad license kinds were automatically applied to all targets and
may be incorrect; the package default aggregates Apache, BSD, GPL+Classpath,
MIT, OpenSSL, Unicode, W3C, and `legacy_unencumbered`. Do not treat that
repository-wide metadata or its `LICENSE` as a per-file override of the
XMLPull license evidence.

For a component-specific API-input decision, retain the official 370-byte
license text and named authors alongside these five API sources or generated
classes, with a provenance record to the r2 libcore paths and SDK inventory.
This is a component-level evidence-based conclusion, not a legal opinion.
It does not resolve the Google SDK package agreement, whether SDK-derived
artifacts may be redistributed, licensing of other `android-stubs-src.jar`
entries, framework resources, or the whole compiler/toolchain distribution.

## Evidence captured

- Public-source retrieval used read-only HTTPS requests to Android Gitiles and
  the official XML Pull site. SHA-256 values above are for the exact raw
  response bytes after Gitiles base64 decoding, and for the local SDK archive
  entries; the XML Pull license digest is over its downloaded text bytes.
- The manifest was checked at the r2 tag and confirms the default tag revision
  and `platform/libcore` project declaration. `git ls-remote` resolved the
  annotated r2 libcore tag to peeled commit `fff4fcc0cf7f080cf2511cbb57561482b13b218f`.
- No AOSP `xml/Android.bp` or local per-file `LICENSE.txt` exists at the pinned
  source paths. The license pointer resolves to the original XML Pull project
  license URL above.
- Nothing in this note asserts permission for the SDK package as a whole,
  source-to-stub byte identity, or legal clearance.
