# Plain Java libraries on the Galaxy A33, 2026-10-18

Ticket #46 of spec #35. A run can now search Google's Maven repository and
Maven Central, add a library at its newest stable version (or an exact one),
change its version and remove it. `libraries.txt` lists the project's
libraries as exact `group:artifact:version` lines; only `add_library` and
`remove_library` change it, and the file tools refuse it. The controller
resolves and checks everything before the list changes; each build hands the
worker the resolved JARs, which go on ECJ's class path and are dexed into the
app by D8.

## Design

- **Resolver** (`controller/.../libraries/`). `MavenRepositories` knows two
  repositories only: `https://dl.google.com/dl/android/maven2/` and
  `https://repo1.maven.org/maven2/`. A POM is looked for in Google's
  repository first, then Maven Central; the library's JAR comes from the same
  repository as its POM. `<repositories>` in POMs are ignored and redirects are
  never followed, so nothing is fetched from anywhere else. Every file (POMs,
  JARs, `maven-metadata.xml`) is checked against the strongest checksum the
  repository publishes: `.sha512`, else `.sha256`, else `.sha1`; both
  repositories publish all three today. A file without one, or with a
  mismatch, is refused and not kept. Checked files are kept in
  `no_backup/libraries/<google|central>/<group path>/<artifact>/<version>/`
  with a `.sha256` record, rechecked on every use, and re-downloaded if
  changed. Downloads go to `partial/` first.
- `LibraryResolver` reads parents (up to 10 levels), properties
  (`project.*`, `pom.*`, parent and custom ones), `dependencyManagement` and
  imported BOMs; follows `compile` and `runtime` scopes (no `test`,
  `provided`, `system` or optional ones); takes the highest requested version
  of each library and repeats until the choice settles, so only the winner's
  own dependencies stay; applies exclusions (with `*`) below the dependency
  that declares them; takes `[x]` and `[x,...` ranges at `x` and refuses
  other ranges and classifiers. POMs are parsed with the platform DOM parser
  and refused if they declare a DOCTYPE.
- **AARs are refused** with "… is an Android library (AAR). The builder
  cannot use Android libraries yet; choose a plain Java library instead."
  This applies to a direct library and to any dependency. AARs that are only
  a `classes.jar` are not special-cased: #47 handles AARs properly.
- **Tools** (`LibraryTools`, registered in `StepRun` and `RunTools`).
  `search_libraries` uses Maven Central's search
  (`search.maven.org/solrsearch/select?q=…&rows=10&wt=json`) and Google's
  `master-index.xml` and `group-index.xml`; only names that are valid
  coordinates are shown. Search answers carry no checksum; they only guide the
  model. `add_library` picks the newest stable version from
  `maven-metadata.xml` when none is given, resolves the whole new list, and
  only then writes `libraries.txt` in the run's working copy. Its step reads
  "Added library g:a v" (or "Changed library g:a to v") with
  "Also …" for libraries newly brought in; failures are FAILED steps with the
  reason as the tool result.
- **Transfer.** Before each build, after `usePlatform`, the controller resolves
  the revision's list (from the cache, downloading what is missing), and calls
  the new `IBuildService.useLibraries(ParcelFileDescriptor[], String[] sha256)`.
  The list is read before the build lease. The worker (`LibraryInputs`) refuses
  more than 300 files, a file over 16 MiB or 64 MiB in total, malformed or
  repeated hashes, and bytes that do not match their hash. It keeps
  `no_backup/libraries/<sha256>.jar` and records the selection; the next build
  takes the selection once and rechecks each file. ECJ gets
  `-classpath platform:lib…`; D8 gets the JARs as program inputs. A build that
  would need `classes2.dex` fails with a clear message (multi-dex is #47).
  Java resources inside library JARs are not packaged.
- The build contract now names the three tools and says plain Java libraries
  (JARs) from those two repositories may be used, AARs cannot yet, and
  annotation processors or compiler plugins do not work.

## Device and build

- Phone: Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16,
  arm64-v8a, connected over USB. The S23 Ultra was not connected.
- Phone lock held 22:10:14 to 22:21:56.
- Worker debug APK sha256
  `f3bb36212ad97505494a3fdaa529df38169f4c95838c3bb5468bab1d923dd318`
  (installed before: `9fc07f6316940996dbfe864f025d0ef415a214520e923644f413b12423326091`),
  `adb install -r` at 22:10:42; installed `base.apk` hash equal afterwards.
- Controller debug APKs, each installed with `adb install -r` after comparing
  hashes; the installed `base.apk` equalled the built one each time, and the
  signer and project data were kept:
  1. `32b211e37e6d33e815f9adb4af379bf498acc8e9687023c7ec805fec94d036bb`
     (commit `f2ed343`; before: `8894d674…633e3`) at 22:10:48.
  2. `a6cc5d7075199c91a74b7165a8e02cd2938fa6a5851c9993eb8c29035f328f7f`
     (regex fix) at 22:13:39.
  3. `4920e470e9ac9499925315e5fe7789cc92041620cc33401e5c5a332b71030954`
     (regex and build-lease fixes, commit `3b63382` without its `partial/`
     change) at 22:16:21. The passing run used this build.
- Host chain after merging `spec35/integration` (`0d53eaf`) as `f9f9088`,
  through `spec35/gradle.sh`: `spotlessApply :controller:testDebugUnitTest
  :worker:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug
  :controller:assembleDebugAndroidTest :controller:lintDebug` exit 0;
  controller 267 tests, worker 66, 0 failures; lint 0 errors. Post-merge
  controller APK `3d366b220d90ecac117d0765b48f92d70ca97aaf771bd7025d5c2c373a419cfb`
  (not installed), worker unchanged.

## Host tests added

- `LibraryResolverTest` (11), against `MavenFixture`, a socket server bound to
  127.0.0.1 serving `google`, `central` and `other` repositories with
  checksum files: transitive compile and runtime dependencies without test,
  provided or optional ones; parent POMs with properties and managed versions;
  highest version wins and the loser's dependencies drop out; exclusions with
  wildcards; Google first then Central, each JAR kept with its checksum;
  tampered JAR refused and not cached; strongest checksum preferred, SHA-1-only
  accepted, none refused; a dependency only in a POM-declared other repository
  refused and a redirect refused, with no request ever reaching `other`;
  offline reuse from the cache and a changed cached file refused; AAR refused;
  newest stable version from `maven-metadata.xml`.
- `LibraryRunTest` (4), `StepRun` with a scripted model, the real store and
  the real tools over the fixture: add at newest stable with the "Added
  library" step and its "Also" line, `libraries.txt` committed; change, remove
  and re-add; refusals as tool results (write to `libraries.txt`, AAR,
  checksum, a URL as a name, missing library, removing an absent one);
  search over both repositories.
- Worker `LibraryInputsTest` (4) and `LibraryCompileTest` (2): checked
  copies taken once; hash mismatch, malformed or repeated hashes and oversize
  refused; a changed kept copy refused at build time; app code using a
  library JAR compiles and the library's classes are dexed into
  `classes.dex`; without the JAR the same code is a compile error.
- `StepRunTest`, `StepProtocolTest` and `ContextFilterTest` updated for eleven
  tools and the new contract text.
- One-off, not committed: resolving real Gson 2.14.0, OkHttp 4.12.0, Jackson
  databind 2.18.2 (BOM import), androidx `annotation-jvm` 1.9.1 and jsoup
  1.18.3 against the real repositories on the host, real search for "json"
  and "recyclerview", and D8 on the real Gson JAR (multi-release, with
  `META-INF/versions/9/module-info.class`): one `classes.dex`.

## Cases on the phone

Model: DeepSeek V4.1 Flash (`deepseek/deepseek-v4.1-flash`), already the
saved choice. Each run sent: "Build a notes app that stores the notes as JSON
using the Gson library. Add the Gson library first. Below the list, show the
saved JSON text so I can see Gson working."

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| Run 1, disposable project "Library test 46" (build 1) | Send at 22:12:21 | after 2 requests the controller crashed: `PatternSyntaxException` in `LibraryResolver.<clinit>` (Android's ICU regex rejects the unescaped `}` the JVM accepts); reopening showed "An interrupted run was closed at its last saved version" | fail, fixed |
| Run 2, same project (build 2) | Send at 22:14:04 | steps: Listed files, Read MainActivity.java, Read empty.xml, **Added library com.google.code.gson:gson 2.14.0 └ Also com.google.errorprone:error_prone_annotations 2.48.0**, Created Note.java, NoteList.java, NoteStore.java, Wrote MainActivity.java, then "Did not finish" during the build: reading the list inside the build lease met the store's "Project is busy" guard | add step pass; build fail, fixed |
| Run 3, disposable project "Library run 46" (build 3) | Send at 22:17:01 | Listed, Read ×2, Listed, **Added library com.google.code.gson:gson 2.14.0 └ Also …error_prone_annotations 2.48.0**, Created Note.java, NotesStore.java, Wrote MainActivity.java, **Built on this phone in 7.9 s**, the model's summary, "Your app is ready." and Install on this phone; ended 22:17:55 | pass |
| Cache reuse | run 3, then `stat` of `no_backup/libraries` after the session | every kept JAR, POM and `.sha256` record is still dated 22:14:12 to 22:14:15 (run 2); run 3 rewrote none of them. `maven-metadata.xml` is not kept and is fetched for each newest-version lookup | pass |
| Artifact | read receipt and APK in controller storage | package `dev.smallwright.generated.app4e0afce78489412cb454f2fa9fa1647d`, version 1, revision 1, 119,258 B, sha256 `f352cfaa6a0a3ef55fff77edb50c466ef4583cd508ab18eb8c3d69d77a5bff6d` equals the receipt; `classes.dex` 261,264 B contains `Lcom/google/gson/Gson;`; revision 1 holds `libraries.txt` = `com.google.code.gson:gson:2.14.0` | pass |
| Debug install | `run-as … cat app.apk \| pm install -r --pkg … -S 119258` on the phone (USB debug installer exception, not production consent) | Success at 22:18:48; installed hash equals the receipt | pass |
| Gson at runtime | launch, add "Buy milk" and "Call Amal at 5" | list shows both; "Saved JSON" shows Gson's pretty-printed array with `created`, `id`, `text`; sources use `Gson`, `GsonBuilder`, `TypeToken`, `fromJson`, `toJson`, no `org.json` | pass |
| Reload | force-stop and relaunch | both notes and the JSON are back (read with `gson.fromJson`); no crash | pass |
| Clean up | Project tools → Delete this project → Delete project, for both disposable projects | both removed (`files/projects` holds only the user's project and `counter-alpha`); "Main project" selected again, its chat unchanged | pass |

Requests and cost (logcat `SmallwrightRun`): run 1 2 requests,
0.0007557312 USD; run 2 6 requests, 0.0019758592 USD; run 3 7 requests,
0.0020515432 USD; total 15 requests, 0.0047831336 USD.

Downloaded and kept files (controller `no_backup/libraries/central/…`):
`gson-2.14.0.jar` 313,604 B, `gson-2.14.0.pom` 22,768 B,
`gson-parent-2.14.0.pom` 27,361 B, `error_prone_annotations-2.48.0.jar`
20,255 B, `error_prone_annotations-2.48.0.pom` 4,252 B,
`error_prone_parent-2.48.0.pom` 15,882 B; 404,122 B in all. Each
`add_library` without a version also fetched Gson's `maven-metadata.xml`
(1,652 B on the host when this record was written; not kept) and every file's
128-byte `.sha512`. Run 2 left empty `google/…` directories for Google's 404
answers; the `partial/` change in `3b63382` stops that (host test), not yet
seen on the phone.

Screenshots in `toolchains/spec35/46/` (local, not committed):
`01-added-library-step.png` (run 2), `03-run-built-with-library.png`,
`04-result-ready.png`, `05-generated-empty.png`,
`06-generated-gson-json.png`, `07-generated-after-restart.png`,
`08-back-to-users-project.png`.

## Not run on a phone, and notes

- The post-merge controller (`3d366b22…`) and the `partial/` change were not
  installed; the host chain covers them.
- Checksum refusal, other repositories, redirects, exclusions, conflicts,
  AAR refusal, search and remove: host tests only; a phone case would need a
  hostile repository or more paid runs.
- Offline build with the network off: not run, to avoid changing phone
  settings; run 3 shows the kept files being reused.
- Both phone failures came from Android-only behaviour host tests cannot see
  (ICU regex syntax, the store's busy guard inside the build lease). No
  instrumentation probe drives the resolver or a library build yet.
- Exceptions other than IOException in a build step end the run with "The
  builder stopped during a build" and no log line naming the cause; that
  made run 2 slower to diagnose.
- The generated notes app stays installed after its project's deletion, as
  the delete dialog says.
- Source and recovery exports still refuse projects with more than two files.
