# API 36 Android HTTP source and license evidence

2026-10-12. Scope: the 14 `android/net/http/*.java` source-stub entries carrying
Chromium BSD-style headers in the pinned Android SDK Platform 36 revision 2
`android-stubs-src.jar`. This note records component evidence only. It does
not clear the SDK package as a whole or establish a legal opinion. No SDK
binary was copied, modified, or included in an APK.

## Finding

The API signature contribution is `framework-connectivity` in
`platform/packages/modules/Connectivity` at commit
`627eb3c63e7c7cb3cb9f8ea319e4bb1676079c3a`. The Android 16 r2 manifest pins
that repo to the r2 tag, and `git ls-remote` resolves the peeled r2 commit to
that same hash. The signature file is `framework/api/current.txt` (SHA-256
`d5a8d26c8172e43258b709ca7b63ad3ed7bcb2d2c81fb188a1a6011c61d9c1a7`). It
records the public API surface, but it is not the raw implementation source.

The matching raw API source files are in `platform/external/cronet`, under
`android/java/src/android/net/http/`, at the peeled Android 16 r2 commit
`3d00a6f18075427606068c038ea2695053213440`. The r2 `android/Android.bp`
`httpclient_api_sources` filegroup explicitly lists these files (and one
additional `HeaderBlock.java` outside this task's finite set). Its source
entries have Chromium copyright lines and say the BSD-style terms are in
`LICENSE`. The actual file headers, source hashes, and build declaration are
listed below.

The AOSP Cronet README says `android/` exists only in AOSP and is not imported
from Chromium. Therefore, the exact raw source path is the AOSP Cronet
repository; the API signature contribution is separately in Connectivity.
The source files still carry the Chromium copyright/BSD pointer. The canonical
Chromium `LICENSE` at the stable source version recorded by AOSP Cronet's
`stable/METADATA` (`126.0.6423.0`) contains the three-condition BSD-style grant
and matching disclaimer. The copyright and the source/binary redistribution
conditions should travel with any copies of these files or derived classes.

## The 14 entries

SDK stub hashes are computed from entries in local
`toolchains/android-sdk/platforms/android-36/android-stubs-src.jar`, SHA-256
`f06372cb9bdbe4dececd35e5cb7b61fec01bb521dcd8a2424489f659b1e9322a`. Raw
source hashes are over exact Gitiles response bytes after base64 decoding at
the pinned r2 Cronet commit. Generated stubs differ from raw sources because
the SDK archive removes method implementations.

| SDK stub | Pinned raw AOSP source | Stub SHA-256 | Raw source SHA-256 |
| --- | --- | --- | --- |
| `BidirectionalStream.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/BidirectionalStream.java) | `a92427a0f24c4d520a3d7f080f0945809b89fd116ae927d9d492aa9d2a5de3d7` | `b81793c0fcec23d79dd719b99d68e7d334e4936a91c345d25547320963e0acec` |
| `CallbackException.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/CallbackException.java) | `d08805850fc90119a016fd143fae4cd704146d05456700a4379836284e78a3b0` | `834410ec46a215edaa694f1b3c1e0abbbbd1e712f351f842160c3f25caa7f7fd` |
| `ConnectionMigrationOptions.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/ConnectionMigrationOptions.java) | `8a4793a6bdcaa61352ddf45180065acf7a687ecd755cbb91718d02cc156a6914` | `0d59056fe83baa1e3f4ef80a263ee129ac530136d3176583a06902c953c72559` |
| `DnsOptions.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/DnsOptions.java) | `843b40c09da045630d39c1b130dbfe8e4a1c73050ec63ed47cc2a1c8ef684216` | `03b6838f67276303a5a5cb7ee3a76906aaa247bfb28f7973665f0841fea9e412` |
| `HttpEngine.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/HttpEngine.java) | `2265c75057ab85888eec302361c27c390d103e5c3e78e1d2a5944e441f0e309c` | `b225dd8ac65c607c1e94f245c0cd3761b3f2100b9692cc6dedd843c4b5dd1114` |
| `HttpException.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/HttpException.java) | `08d74240a9fb9b507a06cf3d801e6f155b16a51eea9f603e7a758fb138e0431e` | `58e57863fdac84e74bc755ca13ee114001f5f0f3c0d995ededa21becf3b0f7f4` |
| `InlineExecutionProhibitedException.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/InlineExecutionProhibitedException.java) | `fa3daeef082c3af4098dfe0c233aaecb46a2bb1c16542127cf260b7341361cb1` | `37075632caa492cd7608b414702f9c8514eedf075bdcbb1683ed54fe4b833df2` |
| `NetworkException.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/NetworkException.java) | `15fc5ac49c9e6b3ed11318c113597a6be2f7fb7bdecfeb84ab78357a6d2b8898` | `ec175639d6fb904b69c025f245ea358e7a2e4774b684f0e04af647e96f2867e6` |
| `QuicException.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/QuicException.java) | `14ada6cb42e3738344171d4e4f2f318c97b27eb060fd9969cabe9f97d93860b1` | `d8ca6d3617904fa870c972d2fa7503aeaf43733d87d3c71b4bfa4d32078eb65e` |
| `QuicOptions.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/QuicOptions.java) | `1f9d4d9f8f06e6918bc1a24bb1a44f90a9395612fdda937b1b795eb346779009` | `38b2ba029c78f404fbe24b0c48b42970b9903b96eb42c3f8f6cb860da82fa8bf` |
| `UploadDataProvider.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/UploadDataProvider.java) | `7ee9dd8947f6777e0bca78905f46970868c3ebba5ca1566355a9f32036ac02d8` | `ab9afc0065fe7d65916dded6d60ea49d005489bef1c8fcc11e000739191cf599` |
| `UploadDataSink.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/UploadDataSink.java) | `e08ba8e64c5fc3c6dfe20769141eb81a243e6dca2038835171b339a49cba69ce` | `5f7c161e43107985d01b402b3de5c427e788c6a40ddf29d2ff34df417b1c721e` |
| `UrlRequest.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/UrlRequest.java) | `2b7228156a81942182d4c559c65053cf7a010f1bf600a5a911cf71a4ad75a2c9` | `23b0a9ef694e41954e97b2a9d50f5b8f92e32e5f5f3b90e4308d7ec593f93b0b` |
| `UrlResponseInfo.java` | [source](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/java/src/android/net/http/UrlResponseInfo.java) | `ace816ee2a42160b439573927a96159d84389abac59ce18a8bae9c5979dd2fc0` | `ce3d25b9093a8dba2b22f8e9f423d7e8cc294ad0f4a3cf6e49dda591c5b10090` |

The SHA-256 of each stub and raw source is different, as expected for generated
stubs. The class names, package, and public API declarations are represented in
the Connectivity r2 API signature file. These facts support the source/API
mapping; the SDK archive does not carry an individual raw-source provenance
manifest, so they do not prove that the fullsdk r2 build used these exact bytes.

## Pinned public-source assembly edge

The r2 Connectivity build files do provide the concrete API assembly edge.
In [`framework/Android.bp`](https://android.googlesource.com/platform/packages/modules/Connectivity/+/627eb3c63e7c7cb3cb9f8ea319e4bb1676079c3a/framework/Android.bp)
(SHA-256 `be388d2f86427a001796b469898a6baa1ef4f221f1cd668b3dcdabbc4fa98b42`),
`CronetJavaDefaults` sets `srcs: [":httpclient_api_sources"]` and the
`framework-connectivity` `java_sdk_library` includes those defaults. In the
same pinned source graph, [`external/cronet/android/Android.bp`](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/Android.bp)
(SHA-256 `0692ebf707a65a7ee7ad7f627f68ed618481e568cafd5c1a4caf977b869ec7b7`)
defines `httpclient_api_sources` with the 14 listed Java files plus
`HeaderBlock.java`. Its `httpclient_api` Java library consumes that filegroup.
Connectivity also defines `framework-connectivity-pre-jarjar` with
`httpclient_api` and `httpclient_impl` as static libraries. Thus the pinned
build graph ties the Cronet API source filegroup to both the public
`framework-connectivity` source assembly and the pre-jarjar implementation
assembly.

This is an exact source-build graph at the two Android 16 r2 pins. It does not
provide a fullsdk artifact-generation receipt linking the published
`android-stubs-src.jar` blob to those exact inputs; keep the distinction from
the SDK JAR's artifact identity.

## License and build metadata

All 14 raw files begin with Chromium copyright attribution and the same
statement that a BSD-style license is in `LICENSE`. The [Chromium root LICENSE
at version 126.0.6423.0](https://chromium.googlesource.com/chromium/src/+/refs/tags/126.0.6423.0/LICENSE)
is 1,536 bytes, SHA-256
`368cca1106be99d39ecd32a38d8305585d802a475effb66380b91ffc9bcf709b`. It
contains the three BSD-style redistribution conditions: retain notice/terms/
disclaimer in source; reproduce them in binary documentation/materials; and
do not use Google LLC or contributor names for endorsement without permission.
The disclaimer and liability limitation follow those conditions. This is a
component-source basis for BSD-style terms, rather than an inference from the
Connectivity repo's Apache license metadata.

The AOSP Cronet [`stable/METADATA`](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/stable/METADATA)
records the Chromium source URL and version `126.0.6423.0`; that version
matches the linked upstream license revision. The AOSP Cronet root
[`README.md`](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/README.md)
states that `android/` is AOSP-only and not imported from Chromium, so the
individual source headers remain important evidence for the BSD terms attached
to these AOSP-maintained API files.

Build metadata has two levels and a gap:

- `platform/packages/modules/Connectivity/framework/Android.bp` sets the
  package's `default_applicable_licenses` to `Android-Apache-2.0`. This covers
  that repository's build metadata/signature contribution; it does not replace
  the Chromium BSD headers on the implementation sources in another repo.
- `platform/external/cronet/android/Android.bp` lists the API sources in
  `httpclient_api_sources`. The root external/cronet `Android.bp` declares an
  `external_cronet_license` with a broad set of license kinds and chooses
  `stable/LICENSE` as the default license text (or `tot/LICENSE` under its
  alternate build setting). `stable/LICENSE` is a 397,073-byte generated
  collection, SHA-256
  `7730cf068a3e2fa9f5f7c3fa6be2693df756eb3512e6c0cb5538554aae0037de`.
  The license module is declared in the root `Android.bp`, but inspection of
  that file, `android/Android.bp`, and `stable/Android.bp` found no
  `package { default_applicable_licenses: [...] }` block. Neither the
  `httpclient_api_sources` filegroup nor its `httpclient_api` consumer declares
  a `licenses:` property in the pinned `android/Android.bp`. Thus these checked
  blueprints do not show an explicit edge attaching `external_cronet_license`
  to this source filegroup/consumer. That is a metadata linkage gap; it does
  not erase the per-file BSD pointer or establish that no external build
  metadata associates the license module. The checked Cronet repo has no root
  `LICENSE` or standalone `NOTICE`; its source headers plus the matching
  Chromium root `LICENSE` provide the more specific BSD evidence.

The broad `external_cronet_license` kinds include Apache, BSD variants, BSL,
ICU, ISC, MIT, MPL, NCSA, Zlib, OpenSSL, Unicode, and `legacy_unencumbered`.
This is aggregate build metadata for a large mixed component; it cannot assign
all those terms to every API file. Likewise, the Connectivity package's
Apache-2.0 default is not a license override for sources from Cronet.

For component-level packaging research, keep each original copyright line and
the complete applicable BSD license conditions/disclaimer with these 14 files
or their generated classes, and record the r2 source pins above. If shipping
additional code from external/cronet's stable source tree, separately review
the corresponding entries in its aggregate `stable/LICENSE` and all affected
third-party notices. This note does not clear the full SDK package, unrelated
`android/net/http` stubs, framework resources, or the compiler toolchain.

## Source records and limits

- [Android 16 r2 manifest](https://android.googlesource.com/platform/manifest/+/refs/tags/android-16.0.0_r2/default.xml): default source tag and project declarations for `platform/packages/modules/Connectivity` and `platform/external/cronet`.
- [Connectivity `framework/api/current.txt`](https://android.googlesource.com/platform/packages/modules/Connectivity/+/627eb3c63e7c7cb3cb9f8ea319e4bb1676079c3a/framework/api/current.txt): pinned API signature contribution, SHA-256 `d5a8d26c8172e43258b709ca7b63ad3ed7bcb2d2c81fb188a1a6011c61d9c1a7`.
- [Cronet `android/Android.bp`](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/android/Android.bp): filegroup lists the API sources.
- [Cronet root `Android.bp`](https://android.googlesource.com/platform/external/cronet/+/3d00a6f18075427606068c038ea2695053213440/Android.bp): aggregate license module and selected license text paths.
- [SDK r2 source-stub archive](https://android.googlesource.com/platform/prebuilts/fullsdk/platforms/+/1b45636761b0dbab9f80779add8fc85ab94b80fb/android-36/android-stubs-src.jar): exact generated API inventory artifact.

All network reads were public, read-only Gitiles requests. Hashes listed for
raw source, generated stubs, and license texts are over downloaded/extracted
bytes as described above. No APK, installation, device operation, credential,
or application source change was part of this task.

Parent independently refetched all fourteen raw source hashes and the exact
license/build metadata described above, including both ends of the source
assembly edge. The BSD text is retained in
[the component notice directory](../toolchain/api36-component-notices/chromium-BSD-LICENSE).
See [parent verification and limits](validation/api36-component-provenance-20261012.md).
