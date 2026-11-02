# Delete project: host tests and A33 use

Date: 2026-10-17 Dubai. Device: Galaxy A33 5G (SM-A336E), Android 16/API 36.
The user approved deleting the nine projects that the free-model bakeoff
created and adding a delete action to the controller.

## Behaviour

Project tools has a "Delete this project" button. It is enabled only when the
controller is idle, the editor matches the saved source, the project is not the
original "Counter alpha" project and another project exists. A confirmation
dialog names the project and states that the delete cannot be undone and that an
installed copy of its app stays installed but can no longer be updated.

On confirm, the controller ends a pending repair, then on its serialized
background executor:

1. removes the project's `project.bin`, so the project leaves the catalog;
2. removes the project's chats;
3. removes its saved repair origin;
4. removes its portable signer file and both Keystore signing keys;
5. removes its directory tree (source, revisions, artifacts, receipts);
6. opens the first other project.

The directory stays until the last step, so it marks an unfinished delete. Every
step can run again safely. At startup the controller finishes steps 2 to 5 for
each generated-project directory that has no `project.bin`, before it opens a
project. A saved selection that names such a directory counts as an unknown
project, so startup opens another project.

Two reviews of the first version (Opus and GPT) found that a delete killed after
step 1 left a directory without `project.bin`. Startup then failed on every
launch, and the chats, repair origin and keys stayed with no way to remove them.
The order and the startup cleanup above fix this.

Run journal rows for the project stay in `run-journal.sqlite`. They hold states
and counts, no user text, and no remaining project can reach them.

## Host tests

`ProjectRegistryTest` (delete frees a slot at the cap; the deleted id is unknown
afterwards; the legacy project cannot be deleted), `ChatStoreTest` and
`RepairOriginStoreTest` (only the deleted project's data goes), and
`interruptedDeleteIsUnknownAndStaysListedForCleanup` (an unpublished project is
unknown to `get`, absent from `list`, reported by `unpublished`, and `purge` can
repeat). 241 controller JVM tests, lint and spotless pass.

## A33 interrupted-delete probe

Controller `30db5181…`. `-e interruptedDelete 1` created a throwaway project
with a chat and an `artifacts` directory and saved it as the selected project.
It then removed only its `project.bin`, the state of a delete killed after step
1, and opened the controller. Result
(`toolchains/alpha-goal-20261017/interrupted-delete/01-probe.txt`): startup
opened `counter-alpha` without blocking, and the project directory and chats
were gone. The project directory count was 24 before and after. The probe made
no signing keys, so it does not check key removal.

## A33 use

Controller `f4993fdc…` was installed with same-signer `adb install -r`. Each
deletion went through the real button and dialog. Before each confirm, the
driver read the selected project id from the controller's preferences and
stopped unless it was one of the nine approved ids.

| Before | After |
| --- | --- |
| 32 projects | 23 projects; the remaining set equals the original set minus the nine approved ids |
| 9 bakeoff apps installed | all 9 still installed |

For the first deletion I also checked that its chat and repair-origin
directories were gone. Three first attempts tapped the button while the
selected project was still restoring its signed APK. The dialog did not open,
and the delete succeeded on a retry. Keystore removal ran without error, but I
did not inspect the Keystore afterwards.

## Round-2 review fixes

Round-2 reviews (Opus and GPT) confirmed that both round-1 findings are closed.
Opus found that a cleanup step that keeps failing, such as a Keystore error,
would throw from startup and block every launch. Controller `d6c3787c…`
finishes each unfinished delete separately and leaves a failing one for the next
launch. The probe now restores the selection it changes. Rerun on the A33
(`interrupted-delete/02-probe.txt`): startup recovered and removed the
directory and chats, the selection was the same before and after
(`app5c15274b…`), and the project directory count stayed at 28.

GPT reported that `feedbackPreview` bypasses the approval gate, because the
probe tests preview phases with `endsWith("review")`. That report is wrong:
"feedbackPreview" ends with "review". Each `feedbackPreview` run on the phone
returned a saved context hash and made no request, and each `feedbackSend`
passed the exact-hash gate. A power-loss gap remains (chat, repair-origin and
signer-file removals are not fsynced before the directory removal), and this
change does not address it.
