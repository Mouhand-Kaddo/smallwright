# Attaching pictures to a message, on the Galaxy A33 (2026-10-20, #62)

## Setup

- **Phone.** Galaxy A33 `<A33 serial>` (SM-A336E, Android 16), checked with `adb devices -l`.
- **Controller.** `29227fd7ca9670ad1b5ba53db1431310f3b06210c211304df08c8d27816f423a`, built from 58dcdfe. It was installed with `adb install -r`, which keeps the app's data. Session 1 replaced `6a68ff2c…` (#61's build). Session 4 replaced `236bb4b7…`, another agent's build installed between sessions. The worker was not changed or reinstalled.
- **Model.** DeepSeek V4.1 Flash, the paid model with standing approval for testing. Before testing, the phone's saved model was GLM 5.3 FlashX. The tests used it only for the refusal case, where nothing is sent, and picked it again at the end.
- **Projects.** Disposable projects Pics62 and Big62, each deleted through the app's delete path. The app then selected "Main project" again.
- **Test picture.** `/tmp/t62/pic/plb-red-circle.png`: a red circle on white, 1200 × 900 PNG, generated on the laptop. It was pushed to `/sdcard/Pictures/plb-test/` and the folder was removed afterwards.
- **Scripts.** `/tmp/t62/s1.py` and `s2.py` set up the project and looked at the picker. `s3.py` ran cases A to F and `s4.py` ran case G. Each was one locked phone session. Logs are `/tmp/t62/s3.log` and `s4.log`. Screenshots are in `toolchains/spec35/62/`.

## Host chain

`gradle.sh … spotlessApply :controller:testDebugUnitTest :worker:testDebugUnitTest :controller:assembleDebug :worker:assembleDebug :controller:assembleDebugAndroidTest :worker:assembleDebugAndroidTest :controller:lintDebug` passed.

- **Tests.** Controller: 451 tests, 0 failures. Worker: 91 tests, 0 failures.
- **Lint.** 0 errors.
- **New tests.** `AttachedImagesTest` covers the payload parts, the bounds, the digest and the refusal. `ChatStoreTest` covers storage, the limits, leftover files and delete. `ContextPreviewGateTest` covers a changed or missing picture being refused at approve. `StepRunTest` covers pictures in every step, compaction and the size budget. `ModelCatalogTest` covers `input_modalities`.
- **Red check.** With the old token estimate, the four-large-pictures run test failed (1 of 27).

## Cases

| Case | What was done | Expected | Actual | Cost |
| --- | --- | --- | --- | --- |
| A | Tap "+ Pictures" and pick the red circle in Android's photo picker ("Done") | No permission prompt. A thumbnail with a × shows above the prompt. | The thumbnail showed with ×, and no permission was asked (`01-thumbnail-saved-model.png`). | none |
| B | Send "what is in this picture? answer in one sentence" with the saved GLM 5.3 FlashX | Refused in plain words, with nothing sent | The status read "GLM 5.3 FlashX can't look at pictures. Remove the pictures or pick a model that can. Nothing was sent." No run started and no `SmallwrightRun` log line appeared. The words and the picture stayed (`02-refused-saved-model.png`). See the limits. | none |
| C | Tap × | The picture is taken off | Gone (`03-picture-removed.png`) | none |
| D | Pick DeepSeek V4.1 Flash, attach the picture again, Send | The thumbnail shows before Send. The reply describes the picture. | `04-thumbnail-before-send.png`. While running, the bubble showed the thumbnail (`05-sent-message-running.png`). Reply: "The picture shows a single large red filled circle centered on a plain white background." (`06-reply.png`) | 1 request, $0.00064344 (3,993 prompt, 201 completion tokens) |
| E | Switch to "Main project", then back to Pics62 | The thumbnail is read from storage | "Picture 1 you sent" shown (`07-reopened-thumbnail.png`). The stored file was `files/chats/<project>/<chat>/images/00000001-1.jpg`: a 20,049-byte JPEG at 1200 × 900. The source was under 1568 px, so it was saved again as JPEG at the same size. | none |
| F | Delete Pics62 in the app; remove the picture folder | No stored pictures remain | `find files/chats -name '*.jpg'` (run-as) returned nothing, and `/sdcard/Pictures/plb-test` was gone. | none |
| G | A second run in Big62, to see a large picture shrunk | Longest side 1568 | See the note below. The stored JPEG was 706 × 1568 from a 1080 × 2400 source, 93,339 bytes. Reply: a description of the pictured app screen (`10-big-before-send.png`, `11-big-reply.png`). | 1 request, $0.00038742 (4,023 prompt, 2,176 cached, 285 completion tokens) |

Total spend: 2 requests, $0.00103086.

**Note on case G.** I pushed a 4000 × 3000 test PNG, but the media index had not picked it up when the picker opened. The script took the newest photo without checking its name, and sent one of `t55a.png` and `t55b.png` instead. That is a screenshot of this app's own "New project" dialog (PickerMeasure55), left on the phone by #55's tests on 2026-10-19. It holds no personal content. It was sent to DeepSeek V4.1 Flash once. I left that file on the phone; my unindexed PNG was removed with the folder. Later scripts should check the picked photo's date or name before tapping Done.

At the end, the app showed "Main project" with GLM 5.3 FlashX picked again (`12-back-on-user-project.png`).

## Limits

- **Saved model choices.** A choice saved before this build has no record of image input. It reads as unable to see pictures, unless it is the built-in default DeepSeek V4.1 Flash. Case B shows this with GLM 5.3 FlashX, whose OpenRouter listing does include image. Picking the model again in the model list stores the flag. The refusal text does not say that.
- **No text-only model on the phone.** No model listed as text-only was tried on the phone. Host tests cover the catalog flag and the refusal.
- **Interrupted runs.** If the app process dies during a run, the user message saved by the interrupted-run path keeps its words but not its pictures.
- **Thumbnail decoding.** Thumbnails for saved messages are decoded on the screen's thread when a chat opens. A chat holds at most 40 pictures.
- **Untested inputs.** EXIF rotation (ImageDecoder applies it), HEIC and transparent PNGs (flattened onto white) were not tried on the phone.
