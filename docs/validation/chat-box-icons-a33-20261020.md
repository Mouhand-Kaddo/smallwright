# A calmer chat box, on the Galaxy A33 (2026-10-20, #64)

The person found the chat box busy. It had six parts, and four of them were words. Two layouts were built and photographed on the A33 (`toolchains/spec35/calm/option-a.png`, `option-b.png`). The person chose option A:
- The message has its own line.
- Below it, one row holds four parts: a picture icon, three rising bars for the thinking level, the model name, and Send as a square key with an up arrow.
- The thinking bars are lit in blue up to the level, and the rest are dim.
- Send is solid blue when sending is the next step, and only outlined when another action, such as "It works, keep it", comes first.
- During a run, Send becomes a solid red Stop key.
- Each icon is read out by its label: Add pictures, Thinking: low, Send, Stop.

## Setup

- **Phone.** Galaxy A33 `<A33 serial>`.
- **Controller.**
  - `aad494b2d9b9119ffb821ce136d501ff70b31c110b066252683c367743c87f82` was used for the run and for the thinking choice.
  - `c1167a36702145a1eb0b7919ce5755840993b86804a3d0c78e7c7aa60c595672` has the blue bars and is the final build.
  - Both were installed with `adb install -r`, which keeps the app's data. The worker was not changed.
- **Host checks.** 488 controller tests and 91 worker tests pass, the test APKs build, and lint reports 0 errors. After the bar colour change, the controller tests and lint passed again.
- **Model.** DeepSeek V4.1 Flash, for one short run in the throwaway project Calm64. It has standing approval for step-run tests.
- **Scripts.** `/tmp/t64/check.py`, `restore.py` and `final.py`. Screenshots are in `toolchains/spec35/64/`.

## Cases

| Case | Expected | Actual |
| --- | --- | --- |
| Empty, on "Main project" | One row of keys under the prompt, with the model name readable. Send is outlined because "It works, keep it" comes first. | Matches (`01-user-project-empty.png`, `08-final-empty.png`). The screen reader names, from left to right, are Add pictures, Thinking: low, GLM 5.3 FlashX and Send. |
| Typed message, in Calm64 | Send turns solid blue. | Matches (`03-typed.png`). |
| Send, then the run | Stop shows as a red key while the run goes, then Send comes back. | The first frame shows the red Stop key without the picture icon (`live/001.png`), and later frames show Send again. The run made 1 request, cost $0.0002 and took 17 s. The script did not read the Stop key from the screen tree, because the tree was read only after the run ended. The Stop key's label comes from the same code as Send's, which was read. |
| Tap the thinking bars | The four levels open below the row, and Back closes them. | Matches: off, low, medium and high, with low marked (`06-thinking-open.png`, `09-final-thinking-open.png`). |

## Stop key, second version

The person found the first Stop key unattractive. It was a solid pink-red block with a dark square in it, and the picture icon disappeared during a run, so the row shifted left. In the second version, Stop is an outlined red key with a red square, the same shape as Send. The picture icon stays in place, dimmed, because it is already disabled during a run.

- **Build.** `88537d1c7c5c7d270a86457ca140d13d96ee870b47fc53380335fd4b2d191864`. The controller tests and lint passed (0 errors).
- **Run.** One DeepSeek V4.1 Flash run in the throwaway project Stop64: 1 request, $0.0002, 17 s.
- **Evidence.** Screenshots taken every 0.4 s after Send show the outlined Stop key and the dim picture icon, and the row does not move (`toolchains/spec35/64/stop2/live/fast-*.png`, `stop2/stop-key.png`).
- **Cleanup.** Stop64 was deleted, and GLM 5.3 FlashX was set again on "Main project".

## Cleanup

- Calm64 was deleted, and the project list no longer shows it.
- The model went back to GLM 5.3 FlashX. The first try failed because the model page kept its last search, so the "in use" heading never showed; `restore.py` cleared the search and picked GLM.
- "Main project" is selected and its message box is empty.

## Limits

- Option A drops the "▾" after the model name. The name is still a button, and the screen reader calls it "Change model".
- The phone tests in `androidTest` that tap Send or Stop by label were built but not run on the phone.
