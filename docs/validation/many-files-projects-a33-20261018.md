# Projects hold many files on the Galaxy A33, 2026-10-18

Ticket #39 of spec #35. A project may hold Java files in sub-packages, every
standard Android resource directory type, assets and `AndroidManifest.xml` as a
project file path. The harness still generates the manifest used for builds.

## Limits

| Limit | Old | New | Where |
| --- | --- | --- | --- |
| Bytes per file | 64 KB | 1 MiB | `PatchApplier`, worker `BuildService` |
| Files per project | 7 | 500 (a project's own manifest counts; the generated one does not) | `PatchApplier`, worker |
| Bytes per project | 252 KB | 16 MiB | `PatchApplier`, worker |
| Snapshot archive | 512 KB | 17 MiB | worker |
| Worker manifest | within total | 64 KB | worker |
| Admitted `MainActivity.java` for diagnostics | 256 KB | 1 MiB | `LocalBuildCoordinator`, `EcjDiagnostics`, `CompilerDiagnostics` |
| APK artifact | 4 MiB | 32 MiB | worker, `ArtifactValidator`, `VerifiedArtifact`, `SignedArtifactReceipt` |

Path depth: at most 6 sub-package directories, at most 6 asset path segments,
at most 8 resource qualifiers. The path cases are one shared file,
`testdata/project-paths.txt`, read by both the controller and the worker tests.

The suggested values held on the phone, so they were kept.

## Device and build

- Phone: SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16, USB, under
  the phone lock. The S23 Ultra was not connected.
- Installed in place with `adb install -r`, before then after (sha256):
  controller `238de093...7ae` to `b9acdf8023e05e13372a8294bc982a58ab2be037eab25e0484e08969a6b88283`;
  worker `0742f471...611` to `f2ec02255775bbc7091f7dc2885d9ddb7ce4e88193bf09ad72b865e19c37353f`;
  test APK `51b4830f85055419b8c9dd9f59eba5066ca5337152d761133dc1c0a2b961c050`.
  Installed hashes equal the built ones. These are the pre-merge build; the merge
  with `spec35/integration` (platform files download) was checked on the host only.
- Host chain after merging `spec35/integration`: `spotlessApply`,
  `:controller:testDebugUnitTest :worker:testDebugUnitTest
  :protocol:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug
  :controller:assembleDebugAndroidTest :controller:lintDebug` passed. 381 tests,
  0 failures; lint 0 errors. Before the merge: 368 tests.

## Host tests

- Every accepted path shape (35) and every refused shape (60, plus empty, NUL
  and null) through `ProjectStore.applyPatch`; the revision stays unchanged.
- The same shared cases through the worker's `isAllowedSnapshotPath`.
- Controller store at exactly 1 MiB per file, 500 files and 16 MiB, and one over
  each. Worker `unpackSnapshot` at the same boundaries, the 17 MiB archive
  budget and the 64 KB manifest.
- Symlinks as a file and as a directory, in assets, sub-package Java and
  drawables, are refused on read, patch and snapshot.
- Snapshot keeps the harness manifest when the project holds its own.
- Sub-package class files are collected for dexing.
- Context building now refuses, naming the file, when the project holds a file
  the current model request cannot send (see below).

## Cases on the phone

Probe: `ManyFilesBuildProbe` (instrumentation argument `manyFilesBuild=app|limits`).
It makes a disposable project in an isolated registry under the controller's
files directory, so the user's "Main project" project and its selection
were never touched. The real worker builds it; no model request is made.
Generated apps were installed with `pm install -r` from the controller's
private copy after comparing hashes (debug installer exception), and launched.

| Case | Result |
| --- | --- |
| `app`: 72 files, 71 KB: 34 Java files in `ui`, `util/deep/er`, `data/model`; 4 layouts incl. `layout-land`; values (strings, plurals, arrays, colors, dimens, styles, attrs, `values-night`, `values-b+sr+Latn`); drawables as XML shape, selector, vector, PNG (two densities), 9-patch, JPG, WebP, GIF; mipmap PNG and adaptive XML; menu, anim, animator, color, interpolator, transition, navigation, xml, font (TTF and family), raw; 3 assets | built in 2.7 s, APK 88,596 B, installed (hash equal), launched; screen shows `notes=30 sum=1305`, `joined=a-b-c`, the drawable background and image, the strings; assets in the APK are byte-identical (probe check) |
| `limits`: exactly 500 files and exactly 16,777,216 bytes; a 1 MiB `MainActivity.java`; 12 incompressible 1 MiB assets plus a filler asset; 415 Java classes of 7,000 B (computed from the file budget; the asset count is from the APK listing) | built in 15.9 s, APK 12,971,403 B, installed (hash equal), launched, same screen text |

Worker memory, polled twice a second from `/proc/<pid>/status`: `app` peak
VmHWM 127,536 kB; `limits` peak VmHWM 197,444 kB, peak VmRSS 169,040 kB, on a
phone with 5.5 GB. No kill, no timeout (request timeout is 120 s).

Evidence: `toolchains/spec35/39/` (local, not committed): instrument output,
memory samples, APK listings from `unzip -l` on the phone, UI text, and
`app-launched.png`, `limits-launched.png`. Installed throwaway packages:
`dev.smallwright.generated.appdabe9b8ae7f7431397d7fe9825923586` and
`dev.smallwright.generated.app25ad34bb011c4a50a6a496a169bf25d9`; the probe's
temporary project directories were removed.

## Not run, and notes

- Reading an asset at runtime. The hand-written stubs have no `AssetManager`, so
  a generated app cannot open one yet. Assets were checked in the APK instead.
- Font, navigation and raw files are linked but not used by the app's code.
- Host AAPT2 test: none. The only AAPT2 evidence is the phone's packaged one.
- The merged build (with platform files) was not installed on the phone.
- Recovery export, source export and the recovery bundle still hold the old
  fixed shape (two or seven files, 64 KB). They refuse a larger project with a
  clear error and were not changed.

## For #41

- `ProjectSourceStore.readCurrentContextFiles` throws `IOException` ("The
  builder cannot send <path> to the model yet") for any stored file outside
  `MainActivity.java`, `res/layout/empty.xml` and `AndroidManifest.xml`. This
  replaces the earlier silent drop, so the current chat flow fails clearly on a
  project with extra files. `ContextFilter.isAllowedPath` is now public.
- `ContextFilter` still caps 8 files, 64 KB per file and 128 KB total; the
  model tool still accepts only its two paths.
- A project's own `AndroidManifest.xml` is stored but skipped in the snapshot;
  the worker still takes the generated one and refuses `uses-permission`.
- `ArtifactValidator` still requires exactly one activity, no services and no
  permissions.
