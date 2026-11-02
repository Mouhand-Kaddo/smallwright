# The model writes the manifest, on the Galaxy A33, 2026-10-18

Ticket #42 of spec #35 ([ADR 0004](../adr/0004-generated-apps-are-ordinary-android-apps.md)).
The model reads and writes `AndroidManifest.xml` with the file tools. Before
every build the controller normalizes and checks it (`AppManifestPolicy`); a
refusal comes back to the model as the build's tool result and nothing is built
or saved. The worker builds with the project's manifest and no longer refuses
`uses-permission`.

## Policy as implemented

Forced whatever the model writes: `package` (a different one is refused, a
missing one is added), `android:versionCode` (the build's version), `uses-sdk`
with minSdk 31 and targetSdk 36 (rebuilt, so `maxSdkVersion` is dropped),
`android:allowBackup="false"`, and `android:exported="true"` on the launcher
activity. The project discovery action `dev.smallwright.generated.PROJECT_APP` is
moved onto the launcher's intent-filter (and left out for the legacy project).

Refused, with a message the model can act on: a manifest that is empty, over
64 KB, malformed, not rooted at `<manifest>`, has a DOCTYPE or entity, lacks
`xmlns:android` or has not exactly one `<application>`; `android:sharedUserId`;
`debuggable`, `testOnly` or `persistent` anywhere unless literally `false`; a
different package; `<instrumentation>`, `<permission>`, `<permission-tree>`,
`<permission-group>`; no launcher or more than one (an `<activity>` whose
intent-filter holds MAIN and LAUNCHER), a launcher `<activity-alias>`, `<data>`
or attributes on the launcher filter; a component with an intent-filter and no
`android:exported`; a class inside the project package that has no Java file.
Allowed: any `<uses-permission>` (Internet included, no warning), any number of
activities, services, receivers and providers, any activity as launcher.

After building, `LauncherManifestPolicy` and `ArtifactValidator` check the
compiled APK: package, version, no shared user id, no instrumentation or
declared permissions, SDK levels, no debuggable, test-only, persistent or
backup flags, no app component factory or backup agent, app process and task
affinity equal to the package, component processes inside the package, exactly
one launcher activity (exported, enabled, no permission, project task
affinity). Dropped: one activity only, no services, receivers or permissions,
the `MainActivity` name, application class and metadata, per-activity launch
mode, direct boot and parent activity.

Existing projects: the one-file project opens unchanged (no revision is
written on open, so receipts and installed-build state stay valid). Its
working copy shows the generated manifest as `AndroidManifest.xml`, the first
commit of a run that changes anything stores it, and a build of a revision
without the file uses the generated manifest as before. New projects start
with the file. `MainActivity.java` may now be deleted once the manifest's
launcher points elsewhere; `AndroidManifest.xml` itself cannot be deleted.
The worker no longer requires a class called `MainActivity` (any class in the
project package keeps the dex valid), so the "missing launcher class" probe now
checks that a class from another package is left out of the dex.

## Device and build

- Phone: Samsung SM-A336E (Galaxy A33 5G), serial `<A33 serial>`, Android 16,
  USB, under the phone lock. The S23 Ultra was not connected.
- Branch `spec35/t42-manifest`, with `spec35/integration` at `0d53eaf` merged.
- Installed in place with `adb install -r`, hashes before then after (sha256):
  controller `4920e470e9ac9499925315e5fe7789cc92041620cc33401e5c5a332b71030954`
  to `089e3d7fcb85fa2e64687581742a4923ecaee359e1ce25e8c7a4e7659f63357d`;
  worker `f3bb36212ad97505494a3fdaa529df38169f4c95838c3bb5468bab1d923dd318`
  to `d616e7d3af87edb59c4db121316fcec194726133c9038b78191bb725059831d1`;
  controller test APK `c4d227f7ae0ff227d3253fbaf806d1cbd5bae59a07e8cb4f6ceb7bcd2466395b`;
  worker test APK `d35c858ae984968efe0b1c06f9475cd82b00f2691b908295bd855e5c102d1326`.
  Installed hashes equal the built ones. Signer and project data kept.
- Host chain through `spec35/gradle.sh`: `spotlessApply
  :controller:testDebugUnitTest :worker:testDebugUnitTest
  :controller:assembleDebug :worker:assembleDebug
  :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest
  :controller:lintDebug` passed after the merge: 348 tests, 0 failures; lint 0
  errors.

## Host tests

- `AppManifestPolicyTest` (25): each forced value, each refusal above, the
  allowed permissions and components, discovery action placement, escaping on
  write-back, launcher class naming, declared project classes.
- `ProjectSourceManifestTest` (5): new projects hold the manifest file and stay
  pristine, a build uses the project's manifest with the version forced, a
  refused manifest stops the build before a snapshot exists, a project without
  the file builds with the generated one, reading the editor source survives a
  deleted `MainActivity.java`.
- `StepRunTest` (+4, 2 changed): read and write the manifest then build; a
  refused manifest comes back as a build result, nothing is built, and a fixed
  one builds; the migration (generated manifest shown, saved with the first
  change, untouched by a read-only run); deleting `MainActivity.java` is refused
  until the launcher moves; deleting the manifest is refused.
- Worker: `uses-permission` accepted, `sharedUserId` still refused, class files
  collected without `MainActivity`. `ContextFilterTest`: the contract says the
  model owns the manifest and names the rules.

## Cases on the phone

| Case | Action | Observation | Result |
| --- | --- | --- | --- |
| Compiled-manifest policy | default `ArtifactPolicyInstrumentation` (33 fixtures from `scripts/artifact-policy-fixtures.py`) | accepted: service, disabled service, receiver, provider, extra activity, activity alias, Internet permission, two activities with Internet, activity and application process, direct boot, application class; rejected: no or wrong launcher, second launcher, launcher with data, launcher alias, custom permission, instrumentation, backup, debuggable, test-only, component factory, app process, activity permission or not exported or disabled, wrong package, shared UID, wrong version, minSdk, targetSdk | "Artifact policy: 33 checks passed" |
| Worker snapshot | `-e mode productionSnapshot` | two package-isolated builds, app-only dex, wrong-package source left out of the dex, scratch removed | pass |
| Existing-shape project builds | `-e manifestBuild oldProject` (isolated registry, manifest file removed from revision 1) | built in 2.1 s with the generated manifest: 1 activity, no permissions, validated | pass |
| Two activities and Internet, no model | `-e manifestBuild twoScreens` (isolated registry): project manifest with `INTERNET` and `.DetailActivity`, lambdas, `HttpURLConnection` | built in 2.4 s, 2 activities, `INTERNET`; APK sha256 `21d5465300e6d7d1c12f4f003d18e4f3555b04c3253866d037158b12c88abfd7`; installed with `run-as … cat` and `pm install -r` (debug installer exception, hash equal); `INTERNET` granted; launch shows "First screen"; tap "Open second screen"; second screen shows "Fetched: Example Domain (HTTP 200, 577 bytes)" from `https://example.com` | pass |
| Paid run, model writes the manifest | New project "Manifest test 42" through the app, DeepSeek V4.1 Flash (`deepseek/deepseek-v4.1-flash`, approved), one Send at 22:28:15: "Make an app with two screens. The first screen has a button that opens the second screen. The second screen loads a joke from a public HTTP API and shows it." | steps: listed the files, read `AndroidManifest.xml`, `MainActivity.java`, `res/layout/empty.xml`, wrote `AndroidManifest.xml` and `MainActivity.java`, created `JokeActivity.java`, `activity_main.xml`, `activity_joke.xml`, `strings.xml`, deleted `empty.xml`, built in 2.6 s (first build succeeded), summary, Install on this phone. 4 model requests, reported cost 0.0019508496 USD (logcat `SmallwrightRun`) | pass |
| The model's manifest | read from the project's revision 1 | `INTERNET`, launcher `.MainActivity` with the discovery action, `.JokeActivity` not exported, `allowBackup=false`, SDK 31/36 | recorded |
| Install and run the result | artifact `app.apk` of package `dev.smallwright.generated.app5d16023d07bc41c59e800b67969303e6`, sha256 `669faacb52589e65589b88e77defee7a20cd4c6b9b04387f322465d99e6b3800`, same install route | installed (hash equal), `INTERNET` granted; launch shows "Two-Screen Joke App" and Open jokes; the second screen shows a joke fetched over the Internet ("What's the best time to go to the dentist? Tooth hurty.") | pass |
| Clean up | long press the project name, Delete this project, Delete project | project removed; the app switched to "Main project"; its store still at revision 8, last written before this session | pass |

Screenshots in `toolchains/spec35/42/` (local, not committed):
`01-probe-first-screen.png`, `02-probe-second-screen-fetched.png`,
`07-prompt-typed.png`, `08-run-live-1.png` (end of the run),
`09-chat-up-1.png`, `10-chat-up-2.png` (the steps), `11-model-app-first.png`,
`12-model-app-second-joke.png`, `16-after-delete.png`, plus the menu and
dialog steps (`03` to `06`, `13` to `15`). Command output: `phone1.log`
(before the merge), `phone2.log`, `phone3.log`.

## Not run, and notes

- A refused manifest in a paid run: the model wrote a valid manifest at the first
  try, so the refusal path is covered by host tests only (and the compiled
  checks by the 33 fixtures).
- The user's project "Main project" was opened but not built or changed. Its
  migration (manifest file written with its first run) is host-tested only.
- `Export source` still needs the two starter files; it hides the generated
  manifest and refuses a project whose manifest was changed. Recovery staging
  still requires `MainActivity.java` and `res/layout/empty.xml`, so a project
  that deleted either cannot yet be staged for recovery import.
- The probe app `dev.smallwright.generated.appbbe311c138604d23a87ccaeed32cc779` and
  the model's `…app5d16023d07bc41c59e800b67969303e6` stay installed, as the delete
  dialog says. Both probe project directories were removed.
- Cleartext `http://` is blocked by Android for targetSdk 36 unless the model
  sets `android:usesCleartextTraffic`, which the policy allows.
- Launcher activity classes are not checked for existence if they are outside
  the project package (library activities).
- Ops note: an `adb` server started inside a `phone.sh` session inherits the
  lock and holds it after the session ends; start it outside the lock first
  (`adb start-server`), or kill it, as happened here.

## For #47 (library manifests)

Merge library manifests into the project's manifest before `AppManifestPolicy`
runs: `ProjectSourceStore.buildManifest(revision, versionCode)` reads the stored
manifest and normalizes it just before `SnapshotStore.write`, so the merged text
should be produced there, and the same text should go through
`AppManifestPolicy.normalize` in `StepRun.manifestRefusal` if its refusals
should reach the model. The policy refuses `<permission>`, instrumentation,
sharedUserId, debuggable, testOnly and persistent from any source, requires
`android:exported` on every component with an intent-filter (library components
usually have it), allows providers, and rewrites `uses-sdk`. The manifest
limit is 64 KB after merging. `AppManifestPolicy.projectClasses` only checks
classes inside the project package, so library class names are not checked.
`ArtifactValidator` allows component processes that start with the package,
application class and `<meta-data>`.
