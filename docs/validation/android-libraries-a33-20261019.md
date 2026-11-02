# Android libraries (AndroidX and Material) on the Galaxy A33, 2026-10-19

Ticket #47 of spec #35. A project can now use Android libraries (AARs) such as
AndroidX RecyclerView, AppCompat, Material Components and WorkManager.
Kotlin-built libraries bring `kotlin-stdlib` as an ordinary dependency. Each
AAR's `classes.jar` (and `libs/*.jar`) goes on the class path. Its resources
are linked by AAPT2 under the app's own. Its manifest is merged into the app's
before the manifest policy runs. Apps that need more than one dex file ship
every `classesN.dex`. Native code (`jni/*.so`) is refused until #48.

## Design

- **Resolver** (`LibraryResolver`, new `AndroidArchive`). `aar` packaging and
  `<type>aar</type>` dependencies are accepted, and the `.aar` comes from the
  same repository as its POM, checked as before. On each resolve the
  controller reads only the AAR's `AndroidManifest.xml`. That read allows at
  most 20,000 entries, 64 KB for the manifest, strict UTF-8 with no NUL, and
  one manifest entry. An AAR holding `jni/**.so` is refused: "… contains
  native code (jni/), which the builder cannot package yet; choose a library
  without native code." `Resolved.manifest` carries the text; it is null for a
  JAR.
- **Kotlin alignment.** When `kotlin-stdlib` is selected, the selected
  `kotlin-stdlib-jdk7`, `-jdk8` and `-common` move up to its version. Since
  Kotlin 1.8 these parts are empty and their classes live in `kotlin-stdlib`.
  Without this, Material 1.14.0's graph (stdlib 1.8.22 with jdk7/jdk8 1.6.21
  from coroutines 1.6.4) made D8 fail with "Type
  kotlin.internal.jdk7.JDK7PlatformImplementations… is defined multiple
  times". This was seen on the host with the real files. Gradle avoids the
  same problem through Kotlin's module metadata, which this resolver does not
  read.
- **Manifest merge** (new `ManifestMerge`, called from
  `AppManifestPolicy.normalize(manifest, package, version, libraries)`). The
  app's own manifest must pass the policy alone first. Then, for each library
  in resolver order:
  - Its permissions, features, queries and `<application>` children join the
    app's.
  - Relative component names (`.Foo`) resolve against the library's package.
  - `${applicationId}` and `${packageName}` are filled with the app package.
    Any other placeholder refuses the library.
  - Entries are keyed by tag and `android:name`, or by canonical content when
    they have no name. The same entry from several libraries becomes one; for
    example, the `InitializationProvider` from startup, emoji2, lifecycle and
    profileinstaller merges its `meta-data` children.
  - The app's attribute values win.
  - An app entry marked `tools:node="remove"` keeps the library's entry out,
    which is WorkManager's documented on-demand pattern.
  - A library `uses-sdk` above 31 refuses the library.
  - Library `<application>` attributes are not merged, so androidx.core's
    `appComponentFactory` is dropped (the validator refuses it).
  - Every `tools:` attribute is stripped.

  The policy then runs again on the merged document. Its refusals are
  prefixed "With the libraries' manifest entries merged in: …", and the 64 KB
  limit applies to the merged text. `ProjectSourceStore.beginBuild(…,
  libraries)` and `StepRun.manifestRefusal` both use this path; the run
  resolves the working copy's `libraries.txt` for it.
- **Policy change.** `<permission>` is now allowed when its name is under the
  app's own package (`<package>.NAME`); every other permission name is still
  refused. `ArtifactValidator` applies the same rule to the built APK.
  androidx.core, which every AndroidX UI library depends on, declares
  `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` with
  `protectionLevel="signature"`. Permission-tree, permission-group and
  instrumentation are still refused.
- **Worker unpacking** (new `AndroidLibraries`). A selected library with a
  root `AndroidManifest.xml` is treated as an AAR; anything else passes
  through as a JAR. Only these entries are unpacked into scratch:
  `classes.jar`, `libs/<name>.jar`, `res/<type>[-qualifiers]/<file>` and
  `R.txt`. The checks:
  - Every entry name is checked: no empty, `.` or `..` segments, no leading
    `/`, `\` or NUL.
  - Other `res/` or `libs/` shapes are refused.
  - `jni/*.so` is refused.
  - At most 50,000 entries per build.
  - Unpacked bytes are counted while reading, whatever the archive claims:
    256 MiB per build and 8 MiB per `R.txt`. A host test unpacks a deflated
    "bomb" (classes.jar of zeros) and is refused.
  - The AAR package must be a valid Java package outside `dev.smallwright.` and
    different from the app's.
- **Resources.** Each AAR's `res/` is compiled with `aapt2 compile --dir` and
  linked as `-R` overlays in resolver order, the app's compiled resources last
  with `--auto-add-overlay`. The last overlay wins, so the app's own values
  win. This was checked on the host with SDK aapt2 36 and on the phone below.
  A library's locale-specific value still wins over the app's default value,
  as in an AGP build.
- **R classes.** `--extra-packages` would write the app's full `R` once per
  library package: 113 MB of Java for Material + RecyclerView on the host,
  which is not compilable on a phone. Instead, the link writes
  `--output-text-symbols R.txt`, and the worker writes one `R.java` per AAR
  package holding only the symbols named in that AAR's `R.txt`, with the
  app's final values. Values must be numbers or `{ number, … }`, or the build
  is refused. Those R classes are compiled by ECJ with the app and dexed. For
  this tree the library R sources come to 715 KB, against 2.8 MB for the app
  R; ECJ compiles all of them in about 3.3 s on the host.
- **AAPT2's `Manifest.java`.** A manifest that declares permissions makes
  AAPT2 write `Manifest.java` next to `R.java`. It is now accepted and
  compiled; anything else in the generated directory is still refused. This
  was found on the phone, see case 1.
- **Dex** (new `LibraryDex`). Each library JAR is dexed once by D8 in
  intermediate mode and kept in worker `no_backup/library-dex/<sha256 of the
  jar>/`. An entry is written to `.partial` and renamed, and the cache is
  trimmed least-recently-used above 512 MiB. Each build runs D8 once on the
  app's classes, the library R classes and the kept library dex files. The
  output must be `classes.dex`, `classes2.dex`, … contiguous (at most 100) and
  all of it is packaged.
- **Java resources.** Non-class entries of library JARs are packaged
  (`META-INF/services/*`, `*.kotlin_builtins`, …). The first JAR to hold a
  path wins. Left out: `META-INF/MANIFEST.MF`, `INDEX.LIST`, signature files,
  `META-INF/maven/`, `META-INF/versions/`, Kotlin multiplatform metadata
  (`*Main/default/…`, `*.knm`, `kotlin-project-structure-metadata.json`), and
  paths the APK owns (`AndroidManifest.xml`, `resources.arsc`, `classes*.dex`,
  `res/`, `lib/`, `assets/`). At most 10,000 entries are packaged, all under
  the 32 MiB APK limit.
- **Request deadline.** The worker's per-request deadline went from 120 s to
  600 s. The first build that dexes Material, AppCompat, RecyclerView and
  WorkManager (67 JARs) took 129.8 s on the A33 and was killed at 120 s.
  Builds with the kept dex take about 20 s. Stop still ends a build at once
  (the controller ends the worker); the deadline only bounds a hung worker.
- **Build contract.** It now says AARs (AndroidX, Material, WorkManager) can be
  added, their resources are reached through the app's R and `@style/…`, their
  manifest entries are merged, and libraries with native code cannot be added
  yet.

## Device and build

- Phone: Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16,
  arm64-v8a, locale en-GB, over USB. The S23 Ultra was not connected.
- Phone lock held 08:53:45 to 09:17:50 on 2026-10-19. Before it, #44's session
  had installed its own controller `247f073d…` and worker `263f6d16…`. I
  reinstalled mine at 08:54:38 and checked the installed `base.apk` hashes
  before every case.
- Installed with `adb install -r`; each installed `base.apk` sha256 was equal to
  the built one; the signer and data were kept:
  - controller `8472f8293f81cc754beb7e4c018e07c0c250747a6f815e61442463befd368043`
    (branch after merging #43; same #47 controller code as the final commit),
  - worker `04e5cf60d00b41504d3e7b20f0306f7505b44bb23b3e61db48c3ca351c83e586`,
    then `5451eb420e1caa782276b46c95c93ebf1434c4f36dfe6967ce5421eb86225ca8`
    (Manifest.java fix), then
    `5eeb6c82c57401f1ff0d2d1d1c61b4632dc506b3df7cdd80301bff587016d2b0`
    (600 s deadline; this is the final worker),
  - test APK `f21752c0…`, then
    `2b7c87ff1181a8fe5919c123f9e98c92a17406bdc0a825092ea4e7e8c25b8a15`.
- Host chain on the final merge (`spec35/integration` at `4b47d10`, with #43
  and #49), through `spec35/gradle.sh`: `spotlessApply
  :controller:testDebugUnitTest :worker:testDebugUnitTest
  :controller:assembleDebug :worker:assembleDebug
  :controller:assembleDebugAndroidTest :controller:lintDebug` exited 0, with
  controller 333 tests and worker 81, 0 failures, and lint 0 errors.
  Post-merge controller `088ae17fbd62782b2b9ddd9dbf70b15c0122bfcb7fa725d928c69b730c4a30a4`
  was not installed (it adds #49 only); the worker and test APK are unchanged
  from the phone run.

## Host tests added or changed

- `LibraryResolverTest`: AARs resolve to `.aar` files with their manifest; a
  `jni/*.so` AAR and an AAR without a manifest are refused; Kotlin stdlib
  parts follow `kotlin-stdlib`, and stay where they are without it. The AAR
  refusal test is gone. `MavenFixture` publishes real AARs.
- `LibraryManifestMergeTest` (7): WorkManager- and core-shaped manifests merge
  with full names, the app package and no `tools:` or `${…}`; one
  `InitializationProvider` from three libraries; the app's `tools:node="remove"`
  and its own values win; a library launcher, a foreign `<permission>` and
  instrumentation are refused after merging; an unknown placeholder, minSdk 33,
  malformed XML and a DOCTYPE are refused, naming the library; 900 merged
  services exceed 64 KB; no libraries gives the same result as before.
- `ProjectSourceManifestTest`: the snapshot manifest holds a library's service,
  also for an old project without a manifest file; a foreign permission from a
  library refuses the snapshot.
- `LibraryRunTest`: a library with a foreign `<permission>` makes `build`
  return "The build did not start … libraries' manifest entries merged in …
  <permission>"; the native-code refusal is a tool result.
- `ContextFilterTest`: the new contract text.
- Worker `AndroidLibrariesTest` (8): unpacking order (plain JAR, `classes.jar`,
  `libs/*.jar`), `res/` and `R.txt`; an anchor AAR with only a manifest; ten
  unsafe or misplaced names refused; native code and five bad packages
  refused; a zip bomb refused while it is read; an oversized manifest refused;
  library R classes hold only their own symbols with the app's values and
  compile with ECJ into `R`, `R$attr` and `R$styleable`; an app symbol value
  that is not a number is refused.
- Worker `LibraryPackagingTest` (5): **a library of 2 × 40,000 fields is dexed
  through the kept cache and D8 into `classes.dex` and `classes2.dex`, and the
  packaged APK holds every dex file byte-for-byte**, the service file and a
  properties resource, but no class file, `MANIFEST.MF`, signature,
  `res/raw/x` or second `AndroidManifest.xml`; `resources.arsc` stays stored.
  It also covers: a kept library dex is reused unchanged; dex output with a
  gap or a stranger is refused; the Java resource filter; `R.java` plus an
  optional `Manifest.java` and nothing else.
- `WorkerBoundaryTest`: the oversize packaging test now calls `packageApk`
  directly instead of the removed private `addDex` (same assertions).
- One-off on the host, not committed: resolving Material 1.14.0 +
  RecyclerView 1.4.0 (60 libraries, 41 AARs, about 13 MB, about 3 min,
  mostly sequential POM and checksum requests), plus WorkManager 2.12.0 (70 libraries, 50
  AARs, 15.1 MB). A harness ran the real worker classes with SDK aapt2 36.0.0
  on that set: unpack 0.2 s, AAPT2 0.9 s, library R 0.1 s, ECJ 0.8 s, cold
  library dex 28.6 s, D8 merge 3.8 s; `classes.dex` 11.2 MB +
  `classes2.dex` 2.0 MB, APK 6.7 MB.

## Cases on the phone

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| 1. Library build probe, first try | `am instrument -w -e libraryBuild twice …ArtifactPolicyInstrumentation` at 08:54:57 (isolated registry; libraries.txt = RecyclerView 1.4.0, WorkManager 2.12.0, Material 1.14.0; Material3 theme; MaterialCardView layout; a `Worker`; no model) | resolved and downloaded 70 libraries into the controller cache, then the build failed: "AAPT2 generated unexpected resource source" (the merged core permission made AAPT2 write `Manifest.java`); 4 min 52 s in all | fail, fixed (worker `5451eb42…`, host test added) |
| 2. Probe again | same, 09:02:58 | the worker died at 09:05:00.8, exactly 120 s after it started, with 65 library dex entries kept: the request deadline | fail, fixed (600 s, worker `5eeb6c82…`) |
| 3. Probe with kept dex | same, worker `5451eb42…`, 09:05:55 | resolve 383 ms (cache), build 1 40.1 s (last two library JARs dexed), build 2 17.1 s; `classes.dex` 11,156,072 B + `classes2.dex` 2,021,432 B; APK 6,734,917 B | pass |
| 4. Cold build | worker `5eeb6c82…`; the worker's `library-dex` cache removed first; same probe at 09:08:12 | **build 1 (cold) 129,849 ms, build 2 (warm) 20,044 ms**; same two dex files; merged components `SystemJobService`, `SystemForegroundService`, `MultiInstanceInvalidationService`, `ProfileInstallReceiver`, `ForceStopRunnable$BroadcastReceiver`, `RescheduleReceiver`, `DiagnosticsReceiver`, `InitializationProvider`; permissions `<pkg>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`; APK sha256 `0841c047…fb39` | pass |
| 5. Probe app installed and run | `run-as … cat app-v2.apk \| pm install -r --pkg … -S 6734917` (USB debug installer exception, not production consent); launch | installed hash equal; RecyclerView of Material 3 cards; first card "Material cards (Probe override)": the app's own `character_counter_pattern` won over Material's, read through `com.google.android.material.R`; logcat `LibraryProbeApp: Refresh ran` (WorkManager); `dumpsys package` lists the merged receivers, provider and the signature permission | pass |
| 5a. Earlier probe app (case 3) | same install route | ran, but the first card showed Material's "New notification": the probe overrode `mtrl_badge_numberless_content_description`, which Material also defines in `values-en-rGB`, and the phone is en-GB. Android picks the locale value over the app's default, as in an AGP build. The probe now overrides a string Material does not translate (case 5) | probe fixed |
| 6. Model run | new disposable project "Library47" (`app64a833fcbc71488ea0964c509950a5c8`); DeepSeek V4.1 Flash; Send at 09:13:01: "Build an app that shows a scrolling list of 30 tasks using AndroidX RecyclerView, each task shown as a Material Components card, MaterialCardView, in a Material 3 theme. First add the libraries com.google.android.material:material and androidx.recyclerview:recyclerview. Also add androidx.work:work-runtime and schedule a one-time background job with WorkManager that logs that it ran. Then build it." | steps: Listed files, Read ×3, Added library Material 1.14.0 with its dependencies, RecyclerView 1.4.0, WorkManager 2.12.0 with its dependencies, **AppCompat 1.8.0 (also appcompat-resources 1.8.0), added by the model on its own**, Created `themes.xml` (Theme.App on Theme.Material3.DayNight.NoActionBar), `activity_main.xml`, `item_task.xml`, `strings.xml`, `Task.java`, `TaskAdapter.java`, `LogWorker.java`, Wrote `MainActivity.java`, `AndroidManifest.xml`, Deleted `empty.xml`, **Built on this phone in 27 s**, summary, "Your app is ready." with Install; ended 09:14:42 | pass |
| 7. Model-built app | receipt: version 1, revision 1, 6,739,104 B, sha256 `65e1fc1623e4f03a3f0b37dcf7e06838b25da4330deaa60ce136d176c32ea37b`; debug install as in case 5; launch | installed hash equal; APK holds `classes.dex` 11,159,336 B and `classes2.dex` 2,021,432 B; the app shows Task 1…30 as Material cards with title and description (dark DayNight); logcat: `WM-SystemJobScheduler: Scheduling work … LogWorker`, `LogWorker: Background job ran`, `Worker result SUCCESS` | pass |
| 8. Merged entries in the installed app | `dumpsys package dev.smallwright.generated.app64a833…`, and the stored revision-1 `AndroidManifest.xml` | the model's manifest declares only `.MainActivity` (no services, receivers, provider or permissions); the installed app has WorkManager's `DiagnosticsReceiver` and `RescheduleReceiver`, startup's `InitializationProvider`, `RECEIVE_BOOT_COMPLETED` and `WAKE_LOCK` granted, and `<pkg>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION: prot=signature`, all from the merge | pass |
| 9. Clean up | Project tools → Delete this project → Delete project for "Library47"; menu → "Main project"; `run-as … rm -r files/library-build-*` (the 4 isolated probe roots); `adb uninstall` the three generated test apps I installed | Library47 gone (`files/projects` holds #44's project, the user's and `counter-alpha`); the user's project selected, its chat unchanged ("Built on this phone in 0.8 s", ready); probe roots and test apps removed. #44's "Run interruption test 44" was not touched | pass |

Requests and cost (logcat `SmallwrightRun`): one run, 7 requests,
0.0043542128 USD. The probe cases made no model requests.

Downloads and storage on the phone:

- The probe's 70 libraries total 15,092,561 B (50 AARs). They were downloaded
  in case 1, whose 4 min 52 s includes that download and the failed build;
  the download was not timed apart.
- The model run added AppCompat 1.8.0 and appcompat-resources 1.8.0.
- After the session: controller `no_backup/libraries` 23 MB (this includes
  libraries from earlier tickets), worker `no_backup/libraries` 16 MB (checked copies) and
  `no_backup/library-dex` 14 MB.

Screenshots in `toolchains/spec35/47/` (local, not committed):
`01-probe-app-material-cards.png` (case 5a), `02-probe-app-override-wins.png`
(case 5), `11-run-working.png`, `15-run-steps-3.png`, `14-run-steps-2.png`,
`13-run-steps-1.png`, `12-run-ready.png` (case 6),
`20-generated-app-running.png` (case 7), `30-back-to-users-project.png`.
Raw probe output: `probe1.txt` to `probe4-cold.txt`; `dumpsys-merged.txt`,
`dumpsys-model-app.txt`. `10-run-started.png` shows the home screen: my BACK
key, meant to hide the keyboard, left the app; the run went on in the
background and the chat was reopened from the launcher.

## Not run, and notes

- Not on a phone: refusals of native code, zip bombs, unsafe names, foreign
  permissions, placeholders, minSdk and oversized merges (host tests only),
  and the S23 Ultra. The post-#49-merge controller was not installed.
- First build after adding Material/AppCompat/WorkManager: 130 s on the A33,
  with no progress shown beyond "Building on this phone". Later builds take
  about 20 s; new library versions are dexed once.
- `add_library` for a large tree is slow on first download: the resolver
  fetches each POM and checksum one after another (3 min on the laptop for
  Material). That is unchanged from #46.
- Library assets (`assets/` in AARs) are not packaged; none of the libraries
  here have any. Library `<application>` attributes (`appComponentFactory`)
  are dropped.
- Only Kotlin's stdlib parts are aligned. Other Gradle-metadata constraints
  (for example one version for every lifecycle module) are not; that was
  harmless here, but a future duplicate-class D8 error would show as a build
  failure.
- The app's own `${applicationId}` placeholders are not filled; only library
  manifests' are.
- The controller library cache and the worker's checked copies and dex cache
  grow with each library version. Only the dex cache is trimmed (512 MiB).
