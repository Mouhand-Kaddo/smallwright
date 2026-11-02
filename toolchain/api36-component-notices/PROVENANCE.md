# API 36 component notice evidence

These 14 verbatim license/notice texts are research inputs for a complete
platform compile library. This directory sits outside the current APK asset
roots; its contents are not selected for packaging. They are **not** a complete worker distribution
notice package or an instruction to select a new compiler input.

The [component inventory](../platform-api36-component-inventory.json) gives
every file's SHA-256, exact source URL, scope and source revision.
`retrieved-20261012.json` records the twelve component-text fetches, including
matches against eleven previously fetched local texts. The additional XML Pull
and Chromium texts have independent raw-source/header/build evidence in the
[XML Pull note](../../docs/research-api36-xmlpull-source-license.md) and
[HTTP note](../../docs/research-api36-http-source-license.md).

The twelve aggregate texts cover selected pinned public-API contribution roots:
libcore (including its separately named `ojluni/src/main/NOTICE`), ICU,
Conscrypt, frameworks/base, AdServices, AppSearch, Bluetooth,
OnDevicePersonalization and Uwb. An aggregate may include terms for source
outside the selected API subset; it does not assign every listed license to
all declarations. Keeping it verbatim preserves evidence while applicability
is resolved.

`xmlpull-LICENSE.txt` is the official XML Pull API distribution text. Its
public-domain statement explicitly excludes tests. No XML Pull tests were
selected. `chromium-BSD-LICENSE` is the Chromium 126.0.6423.0 root license,
matching the source version named in pinned Cronet metadata. The fourteen HTTP
API sources retain their individual Chromium copyright/BSD headers; their
Connectivity signature-provider metadata is a separate part of the build.
No Cronet implementation library is selected or packaged here.

Preserve the complete conditions, disclaimers and individual source copyright
lines in a future distribution, together with all applicable GPL corresponding
source and any required modified-file notices. These copies alone do not
supply that source package. Generated/headerless/package-documentation entries,
exact source lineage, SDK-derived component questions and remaining compiler
licenses/resources still require resolution. No APK or physical-device test
was produced by notice collection.

Exact execution, preserved failures, independent review and the five-definition
raw XML Pull regeneration are in the [validation record](../../docs/validation/api36-component-provenance-20261012.md).
