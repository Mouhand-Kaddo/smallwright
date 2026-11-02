# Physical conversation acceptance — 2026-10-02

Device: connected Samsung Galaxy S23 Ultra, SM_S918B, Android 16/API 36,
ARM64, serial `<S23 serial>`. This is a bounded live-change acceptance, not
the twenty-request private-alpha suite.

The user approved submitting a **new test project's** counter source and
the following change to the configured free-only model, with a $0 spend cap:

> Change the Increment button label to Add one. Preserve the counter, saved
> count, and crash report features. Change only that label in the existing
> Java source; keep its package and APIs.

The app used its phone-stored credential internally. No credential bytes,
provider response body, generated source or generated APK were copied to the
host. The controller presented the context preview before each live attempt.
No paid fallback or automatic retry was introduced.

## Observed live sequence

1. The UI probe created a freshly named project and two chats through the
   controller, selected Chat 2, and persisted only their identifiers.
2. The first 2,048-token source-replacement attempt failed at tool arguments;
   the second failed at the more precise `tool arguments json` category.
   Neither committed source nor offered an APK. The fixed categories expose
   no provider argument values.
3. After increasing only the source-replacement completion cap to 4,096,
   the third live attempt committed **exactly one** source revision and
   compiled/signature-verified the native APK using the real phone worker.
   The sequence is consistent with output truncation, but does not prove
   that the cap alone caused the earlier failures; replies are nondeterministic.
4. Force-stopping/reopening the controller preserved the exact selected
   project/chat, two chats, revision, APK hash and version, and enabled Install.
5. The controller's normal Install action displayed Android's actual
   confirmation sheet. The user tapped Install and replied “Installed”.
   An earlier instrumentation-only Install attempt finished before its
   asynchronous callback could show that sheet; it is not installer evidence.
6. The installed APK hash matched the verified phone artifact. With controller
   and worker force-stopped, the generated app launched independently, rendered
   `ADD ONE` (Android's uppercase button rendering), changed `Counter: 0` to
   `Counter: 1` on one tap, and retained `Counter: 1` after its own force-stop
   and relaunch. No app data was cleared.

## Exact test identity

- Project: `app68472d8552a64a38bbd3cd8ec43accd1`, revision **1**.
- Package: `dev.smallwright.generated.app68472d8552a64a38bbd3cd8ec43accd1`.
- Installed version: **1**, minSdk 31, targetSdk 36, Android app ID **10070**.
- Phone-only artifact:
  `/data/user/0/dev.smallwright.controller/files/projects/app68472d8552a64a38bbd3cd8ec43accd1/artifacts/build_9da786a7950a48e68c9dfd83051dfa25/app.apk`.
- Verified and installed APK SHA256:
  `6b1b90fab716555e4c7cc901d7581228c6e2f3a190151937b97229b7f94a306e`.
- Controller/worker Android app IDs: **10465/10464**. Scoped package metadata
  grants controller Internet and contains no worker Internet permission.

## UI and protocol regressions

- Conversation layout RED on the original source-first screen, then GREEN:
  request and Generate fully visible on this phone, source/manual tools collapsed
  by default and expandable. AI key controls have a separate collapsed section.
  Root system-bar, cutout and keyboard insets keep content within available space.
- Latest-result regression RED: saved successful build message was offscreen
  after reopening. GREEN after scrolling the conversation to its latest message.
- Active-build navigation RED: existing chats disabled during a queued build.
  GREEN on the physical phone with isolated storage: two separate histories
  browsable, source/new work/project switching locked, Stop available, cancelled
  queued build preserving exact source/revision and offering no APK. This is
  a queued manual-build case, not a live provider cancellation test.
- Review found that chat browsing also cleared the captured repair request.
  Added physical RED/GREEN with a real, isolated journal seeded REPAIR_NEEDED:
  return to its originating chat, restore only its empty request field, open
  the actual Send & repair preview and cancel without sending. The strict
  source/original-request guard remains. Seeded failure is a fixture, not
  proof that a provider proposal really failed compilation. Evidence:
  `repair-navigation-red.txt`, `repair-navigation-green.txt` and the final
  `repair-navigation-green-build.log` (249 JVM tests/lint/build,1m26s).
- Source-output boundary RED: a valid 4,096-token replacement was rejected.
  GREEN accepts exactly 4,096 and rejects 4,097. Read/text responses retain
  their 2,048 cap. Prompt/attempt/repair limits and strict proposal validation
  remain enforced; run accounting permits at most 278,528 reported tokens.
- Argument-category regression RED then GREEN: malformed JSON and wrong fields
  receive distinct fixed categories without exposing simulated private values.

## Reproduction and retained evidence

Build with the pinned local tools, one Gradle worker and capped memory:

```bash
source scripts/env.sh
./gradlew :controller:testDebugUnitTest :worker:testDebugUnitTest :protocol:testDebugUnitTest :controller:lintDebug :worker:lintDebug :controller:assembleDebug :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
adb -s <S23 serial> install -r controller/build/outputs/apk/debug/controller-debug.apk
adb -s <S23 serial> install -r controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk
adb -s <S23 serial> shell am instrument -w -r -e conversationLayoutOnly true dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
adb -s <S23 serial> shell am instrument -w -r -e phoneConversation latestMessage dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
adb -s <S23 serial> shell am force-stop dev.smallwright.controller
adb -s <S23 serial> shell am instrument -w -r -e phoneConversation restore dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
```

`phoneConversation=prepare` creates another new project and two chats.
`phoneConversation=send` additionally requires `approveFreeOnly=true` and
specific user approval for its preview/upload. Do not invoke send merely to
repeat a regression. `restore` and `latestMessage` require the originally
prepared selection; restore compares the exact successful live build receipt.
Android installation confirmation is a separate explicit user action.

Instrumentation **code -1** is success; **code 0** is failure, even if ADB exits
zero. Logs and screenshots are retained in ignored
`toolchains/product-phone-20261002/`: `conversation-live*.txt`,
`conversation-restart.txt`, `restart-current.txt`, `layout-red.txt`,
`layout-current.txt`, `history-red.txt`, `history-green.txt`,
`installer-visible.png`, `standalone-counter.json`, protocol RED/GREEN logs,
and `final-gates.log`. The latter passed 201 controller and 48 worker JVM
tests, with no failures/errors/skips, and both Android lint tasks; protocol
currently has no JVM test cases.

The final chat-navigation build in `active-chat-green-build.log` repeated
the same 249-test/lint gate successfully in 1m24s. Its physical RED/GREEN
logs are `active-chat-red.txt` and `active-chat-green.txt`. Run the disposable
navigation check with `-e activeChatNavigationOnly true` using the same
instrumentation component. It never sends a model request or compiles its
stopped queued snapshot.

## Prepared installed-update check

After the standalone count reached 1, `phoneConversation=prepareUpdate`
used the visible source/build controls to change exactly the Java string
literal `Add one` to `Add one again`, leaving other source bytes unchanged.
The real phone worker compiled revision **2**, version **2**, with the same
installed project signing certificate. The verified phone-only artifact is:

```text
/data/user/0/dev.smallwright.controller/files/projects/app68472d8552a64a38bbd3cd8ec43accd1/artifacts/app-v2.apk
SHA256 472ebd2a10bc34599938f6759d7b9cbafc514672d6df6e9c36120608cae1565f
```

`phoneConversation=restoreUpdate` verified exact update revision/hash/version,
selected chat and Install availability after a controller force-stop.
Android's actual “Update this app?” sheet was opened from the normal
controller and captured in `update-installer-visible.png`. The user tapped
Update and replied “Updated”. Installed APK hash and version matched the
verified update; Android app ID remained **10070**. With controller and worker
stopped, the app rendered `ADD ONE AGAIN`, retained count **1** immediately
after the update, incremented to **2** on one tap and retained **2** after its
own force-stop/relaunch. `installed-update.json` records the scoped observations.
The data-preserving installed-update case passed on this test app.
No additional cloud request was used.
`update-ready.txt`, `update-restart.txt` and the update-probe build logs retain
the actual results. These update phases are restricted to the originally
prepared test selection and pinned live artifact; original live receipt pins
remain separate from update pins.

The current prepared project is revision/version 2. Use `restoreUpdate`
for its restart check; `restore` deliberately requires the original live
revision/version 1 and rejects the changed project.

## Local crash preview fault check

On installed version2, Preview crash report initially showed no saved report.
`adb shell am crash` intentionally injected a VM fault only into this disposable
generated package. On relaunch, count2 survived; Preview crash report showed
the saved 670-byte report, CrashedByAdbException class and the notice that
exception messages/values are omitted. The preview was closed without export
or model upload. `crash-preview.json` preserves these scoped observations.
This is a real runtime fault-injection check, not an organic source bug or the
complete export/import/model-repair feedback loop.

This does not establish the complete crash-report feedback loop,
offline/detached-laptop operation, a second physical
device, performance/thermal acceptance or unrestricted Android API coverage.
Source toolchain candidates still fail the unchanged strict release gate.
