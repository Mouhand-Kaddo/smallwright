# API 36 framework/base headerless source slice

2026-10-12. This is a bounded provenance and license-applicability investigation for five API sources (seven classes) categorized as `Android headerless` in the published source inventory. It does not determine a legal conclusion or clear any SDK/API distribution.

## Finding

Each raw source in the pinned local Android 16 r2 `platform/frameworks/base` snapshot is byte-identical to its Git blob at commit `99b01a65cc4c104933788b3143285ab6bae65827`. Each selected published SDK source stub also matches its previously inventoried SHA-256 when read directly from the local `android-stubs-src.jar` archive in memory. The existing inventory records seven class-file names for these five stubs; this slice did not independently parse the seven bindings. Raw implementation bytes and generated/published stub bytes are different inputs and have different hashes.

No per-file license grant was established for these exact pinned raw files. The raw files start at their package declarations without a visible license prologue. The framework/base license metadata associates a broad set of license kinds with the source package, while its own comments warn that the broad assignment may be incorrect and recommend target-specific mapping. AOSP policy says Apache 2.0 is preferred and that exceptions are handled case by case; that policy does not itself grant a license to a particular file. Historical source pages include both Apache-headered predecessors and headerless forms, but the examined pages do not establish complete lineage from those versions to these exact pinned blobs. Keep this five-file slice unresolved pending stronger source-lineage and grant evidence.

## Exact local raw-source evidence

The immutable receipt is at `toolchains/api36-headerless-framework-20261012/receipt.json` (SHA-256 `4a4ace1460822da49872c5368b557db701d90fe113a51bdb3cd9c333e4ad02c9`). It records each Git blob ID, raw byte count, raw/local SHA-256 equality, corresponding published-stub SHA-256, class-file mapping, and the no-lazy-fetch Git environment. The checkout's HEAD equals the pinned commit. Its two parent commit objects are unavailable locally, so this checkout cannot establish file history or authorship beyond the pinned snapshot.

| Raw path in `core/java/` | Git blob | Raw SHA-256 | Published stub SHA-256 | Inventory-recorded class files |
| --- | --- | --- | --- | --- |
| `android/app/FragmentTransaction.java` | `34c492853c9dd940e8fc879f04cf3fb48e3dc735` | `f07d544047ce89e9b3cd60530f19f4b3a2aace9672337928e253ef723079fb80` | `b745764600a78302a0e8949da2a9806c1a549e9d8f08ffd30fe0bff41ce285f4` | `android/app/FragmentTransaction.class` |
| `android/app/assist/AssistContent.java` | `adf8c94ff8d59f30cefe2d1eb3aa8a9d9de604df` | `1f652f5cfb2d62f4087eab3581832c41d86c72c2d161cb845807f398f9835e0a` | `13e0102d0755553a9125c363797d7f1b764e2cec51a2785c1b0d2ce2144cf3c9` | `android/app/assist/AssistContent.class` |
| `android/app/assist/AssistStructure.java` | `1af2437a5d6a2cc465af786d518bf0b839260d11` | `da44be7911c247941b2259adb31faf94935f92bb0d6706579d8ad359edf17fab` | `02ec983a909a4a21478de962a3bd99d0e5a1ede6f52b7f45df569fd506572105` | `AssistStructure`, `$ViewNode`, `$WindowNode` |
| `android/app/backup/FullBackupDataOutput.java` | `a47478cc66a71cfaeb6a947c040583a1cd4f01c6` | `b434f2f42128cdc6e197d911d7e016e581cc5282f936ce512eaf478a6a927b2d` | `ff729ab28ee166e34706d10821e43afafd9393219a2314b89247fa3aaf89d739` | `android/app/backup/FullBackupDataOutput.class` |
| `android/os/WorkSource.java` | `517418a717fb041ee6448118b657ffa4f0f4130c` | `2b8aef63c6e536164ab244a04da60c8e8bea0c0a17c6a09eb9a34c40684e2eb2` | `0c4e438e94b966feab288f49acef46ce605ea8277f4ce513619e0c0084258607` | `android/os/WorkSource.class` |

Replay the five local blob byte comparisons from the repository root without network or lazy fetch:

```bash
repo=toolchains/platform-api36-source-probe/base-r2-sparse
commit=99b01a65cc4c104933788b3143285ab6bae65827
export GIT_NO_LAZY_FETCH=1 GIT_TERMINAL_PROMPT=0 GIT_PROTOCOL_FROM_USER=0 GIT_ALLOW_PROTOCOL=none
for path in \
  android/app/FragmentTransaction.java \
  android/app/assist/AssistContent.java \
  android/app/assist/AssistStructure.java \
  android/app/backup/FullBackupDataOutput.java \
  android/os/WorkSource.java
do
  git -C "$repo" cat-file blob "$commit:core/java/$path" | cmp - "$repo/core/java/$path" || exit 1
done
```

The stub bytes were read from the existing local SDK source JAR in memory, not exported to a directory. For each table row, the entry name is the published-source path shown in the first column without the `core/java/` prefix. Its SHA-256 equals the `sourceSha256` recorded in `toolchains/platform-api36-source-probe/component-inventory-20261012/inventory-bfcf28a49e4c42edbeca4ae10cce22b7.json` (inventory SHA-256 `4ad6f2671054e0ab24dc8d97784082074c7ce05f58294040dc292fc0f59c56e6`). The inventory records class names, but explicitly does not establish a license grant. This slice verified the five stub hashes and the recorded total of seven class-file names; it did not independently re-parse each source-to-class declaration binding.

## License and source-lineage evidence

AOSP's [contributor license and header guidance](https://source.android.com/docs/setup/contribute/licenses) says Apache 2.0 is preferred, but recognizes case-by-case exceptions. It instructs contributors to retain existing copyright notices and use the Apache header for new files absent a project-specific license. This guidance is context, not a file-specific grant for legacy files.

The pinned local [`core/java/Android.bp`](https://android.googlesource.com/platform/frameworks/base/+/99b01a65cc4c104933788b3143285ab6bae65827/core/java/Android.bp) places Java files under the recursive `framework-core-sources` group and declares a package default from `frameworks_base_license`. The pinned [root `Android.bp`](https://android.googlesource.com/platform/frameworks/base/+/99b01a65cc4c104933788b3143285ab6bae65827/Android.bp) lists Apache-2.0, BSD, CC-BY, MIT, Unicode-DFS, and `legacy_unencumbered` kinds for that broad license module. Its comments say a large-scale change assigned all found kinds to every target, may be incorrect, and that target-specific licenses should be mapped. The core package describes inheriting that combined license set. Exact local blob/copy identities for both pinned build files are recorded in ignored `toolchains/api36-headerless-framework-20261012/metadata-receipt-02.json` (SHA-256 `a83607593a19164d9d9cc1d0ffea0060c3283fbe1aaabd0ec63fd1284160119e`). Thus these build files show package/source inclusion and broad metadata, but do not identify which grant applies to each of these five files.

The public historical pages offer clues, not closure:

- The [older `FragmentTransaction` source](https://android.googlesource.com/platform/frameworks/base/+/247fe74c934cb3fba85aae7e051a8044f460fb11/core/java/android/app/FragmentTransaction.java) is headerless, as is the current [master source](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/FragmentTransaction.java). The public page is not the pinned 25Q2 blob and does not provide a license grant.
- Older [`AssistContent`](https://android.googlesource.com/platform/frameworks/base/+/2035ac8/core/java/android/app/AssistContent.java) and [`AssistStructure`](https://android.googlesource.com/platform/frameworks/base/+/2035ac8/core/java/android/app/AssistStructure.java) files carry Apache-2.0 headers. A [historical package move diff](https://android.googlesource.com/platform/frameworks/base/+/d8b9b642feab52523ec6db7b170b08f1bc74b004%5E2..d8b9b642feab52523ec6db7b170b08f1bc74b004/) shows new `android.app.assist` files, but the inspected evidence did not prove the exact lineage or scope of the pinned implementations. Do not automatically transfer the old classes' header to the new files.
- A [historical `WorkSource` introduction](https://android.googlesource.com/platform/frameworks/base/+/7e9f4eb2608148436cef36c9969bf8a599b39e72%5E%21/) and a later [headerless source snapshot](https://android.googlesource.com/platform/frameworks/base/+/4f868ed/core/java/android/os/WorkSource.java) establish historical presence and header form, not a per-file license grant.
- The official Android 35 SDK source page for [`FullBackupDataOutput`](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-exifinterface-release/android-35/android/app/backup/FullBackupDataOutput.java) is headerless. A nearby [`BackupDataOutput`](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/app/backup/BackupDataOutput.java) has an Apache header, but sibling licensing cannot resolve this file.

The exact r2 Git blob checks above establish the local source bytes at the pinned commit, not the files' original owners, complete upstream ancestry, or rights associated with them. Further evidence would need to establish the lineage and applicable grant for each exact source; otherwise retain the unresolved status.

## Comparator and scope boundary

No compiler, stub generator, strict ABI comparison, or full API assembly was rerun for this license investigation. The previously recorded published-source experiment remains an SDK-derived declaration reference: its preserved `strict-comparison.json` reports 6,227 reference and candidate classes, `public_api_match: true`, and empty missing/extra/different lists. Its unchanged comparator hash is `051d78abd5f699155b90a1ade35b11509c40c72bb1c76eb3a518b9fe4731491d`. That historical pass is not evidence of independent source lineage or license clearance for this slice.

This note does not alter the component inventory, APK assets, source stubs, API declarations, comparator, or product build inputs. It does not clear the remaining API 36 source categories, notices/corresponding-source obligations, compiler toolchain, or issue #1 acceptance.
