# AOSP framework resource table provenance and notices

## Finding

For the **compile-only input** at `toolchains/platform-api36-source-probe/resource-r2-table-fixed.apk`, local inspection found only `AndroidManifest.xml` and `resources.arsc`; the latter is reported by the project as unchanged from the pinned-source-derived table. This is a narrow artifact, not the full `framework-res` export APK. The source checkout is `platform/frameworks/base` commit `99b01a65cc4c104933788b3143285ab6bae65827` (tag `android-16.0.0_r2`). Its `core/res/Android.bp` declares Apache-2.0 for the package, and `core/res/MODULE_LICENSE_APACHE2` records that module classification.

This is useful provenance evidence for using the table as an internal compiler input. It is **not legal release clearance**: the Android.bp comment itself says its broad automatically assigned license may not be entirely correct, files may contain independently licensed contributions, and a purpose-specific audit of every string/value embedded in the table was not performed. Do not describe the APK or the entire resource tree as fully cleared.

## Scope and local inventory

The checkout resolved to the exact commit above. `core/res/res` contains 11,158 files: 6,238 PNG, 4,869 XML, 46 HTML, and 5 other extensions. A header scan of text-like resource files found no non-Apache license grant. Among the unusual named-copyright headers, `res/layout/subscription_item_layout.xml` says MediaTek Inc. and expressly licenses the file under Apache-2.0; Google Inc. headers likewise explicitly say Apache-2.0. No conflicting per-file grant (for example, a different license identifier or proprietary restriction) was found in the scanned text resources.

All 46 HTML files lack an embedded copyright/license header. That absence is not evidence that they are independently public domain or individually cleared; the source directory’s package-wide Soong declaration is relevant evidence, but the metadata caveat below prevents treating it as conclusive. The scan does not establish authorship or licensing for individual PNG or other binary files, nor establish that every value represented in `resources.arsc` is free of third-party rights or attribution requirements.

## What the metadata establishes

At the pinned revision, `core/res/Android.bp` sets `default_applicable_licenses` to `frameworks_base_core_res_license`; that license is marked `SPDX-license-identifier-Apache-2.0` and points to `NOTICE`. The same file defines `android_app { name: "framework-res" ... export_package_resources: true }`, showing this directory is the platform framework resource module and that Soong has an export-package-resources build path. It does not say the candidate table-only APK is itself the canonical export artifact.

Crucially, the license stanza says it was added automatically by a large-scale change applying found licenses to every target, warns this might not be entirely correct, and advises splitting licenses and overriding defaults where needed. Therefore the module metadata is strong provenance/classification evidence, not a substitute for checking embedded input files and derived contents. The scanned MediaTek XML is a concrete different copyright holder but not a license conflict: its file header explicitly grants Apache-2.0. HTML files' missing headers and binary files' uninspected metadata remain limits.

`core/res/NOTICE` begins with an AOSP copyright and contains the complete Apache License 2.0 text; the inspected copy has no separate third-party attribution paragraph beyond the license text. If this resource work is redistributed as a work or derivative, Apache-2.0 §4(a) requires giving recipients a copy of the license, §4(c) addresses retaining source notices, and §4(d) applies readable relevant attribution notices if a NOTICE file is part of the distributed work. Preserve the applicable source copyright/attribution notices and provide the license text in the distribution’s accompanying notices/docs. A `resources.arsc` file by itself cannot carry source comment headers, so package-level accompanying notice material is the practical place to retain required notices. Do not infer that the entire upstream NOTICE must be copied verbatim when its contents are only the license text; include a readable applicable notice/license copy and have release packaging reviewed against the final distributed work.

The candidate has a manifest plus compiled table; it does not carry the upstream NOTICE or LICENSE file in its ZIP. That may be acceptable for a private local compiler input, but a distributable compiler/tool package containing it should ship appropriate license text and applicable attribution. Keep source commit provenance and checksum alongside the pinned input.

## Primary sources

- [Pinned AOSP `core/res/Android.bp`](https://android.googlesource.com/platform/frameworks/base/+/99b01a65cc4c104933788b3143285ab6bae65827/core/res/Android.bp)
- [Pinned AOSP `core/res/NOTICE`](https://android.googlesource.com/platform/frameworks/base/+/99b01a65cc4c104933788b3143285ab6bae65827/core/res/NOTICE)
- [Pinned AOSP `core/res/MODULE_LICENSE_APACHE2`](https://android.googlesource.com/platform/frameworks/base/+/99b01a65cc4c104933788b3143285ab6bae65827/core/res/MODULE_LICENSE_APACHE2)
- [Pinned MediaTek-authored resource header](https://android.googlesource.com/platform/frameworks/base/+/99b01a65cc4c104933788b3143285ab6bae65827/core/res/res/layout/subscription_item_layout.xml)
- [Apache License 2.0, official text](https://www.apache.org/licenses/LICENSE-2.0.txt), especially §§2, 4(a)–(d), 6, and 7.
- [AOSP legal notice](https://source.android.com/docs/setup/about/faqs#licensing), which describes AOSP source as mostly Apache-2.0 while noting Android contains different licenses; this general statement does not clear this artifact.

The pinned source links identify source provenance; the file counts and scans above are local observations against that checkout. No legal opinion was sought, no complete file-by-file copyright audit was made, and no claim is made that all binary/string/value content is free of additional obligations.
