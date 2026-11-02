# Adding skills from a chat, on the Galaxy A33 (2026-10-19, #58)

## Setup

- **Phone.** Galaxy A33 `<A33 serial>` (SM-A336E, Android 16), checked with `adb devices -l`.
- **Controller.** Case A ran on `4166bb3acb429b69ca1dde4276983871b93ceff6bbd6f256454c5f8593542a1a`, built from 2b89d31. Cases B, C and D ran on `378778eea1062af55bc5ee8226a58f8b873d5500cf2ae99d8a4aefedac0e9cf2`, built from 78c0ce1. Case E ran on `c9495e9524ba3e232a76f902b452d77bd93bd9717d48a5509d7937cbd3ff34e6` (10252e2), and case F on `4891543f976d3ac3af81dc7b87ed76214b10857fba4c5440f6a729cb6c779aa7` (24bffe6). Each was installed with `adb install -r`, which keeps the app's data. The worker was not changed and not reinstalled.
- **Model.** DeepSeek V4.1 Flash, the paid model with standing approval for testing the step run.
- **Skills already on the phone.** The 37 mattpocock skills from #57. The test used Anthropic's public repository `github.com/anthropics/skills` (20 skills), which was not on the phone.
- **Project.** Disposable projects Skill58, Skill58b and Skill58c, each deleted at the end of its session. The app then selected "Main project" again.
- **Scripts.** `/tmp/t58/s2.py` (case A), `s3.py` (cases B to D), `s4.py` (E) and `s5.py` (F), each one locked phone session. Logs are beside them, and screenshots in `toolchains/spec35/58/`.

## Cases

| Case | Message and setting | Expected | Actual | Cost |
| --- | --- | --- | --- | --- |
| A | "add the frontend-design skill from Anthropic", with "Ask before adding skills" off | The builder adds it without asking, then reads it | It added the skill with no question, then read it. The steps were "Added skill frontend-design" and "Read skill frontend-design". The reply said the skill is on for every project. | 3 requests, $0.0010 |
| B | "/doc-coauthoring help me write a short description of my app", with the switch on | `/name` for a skill not on the phone is sent. The builder asks before downloading, and Don't add adds nothing. | The question "Add the skill doc-coauthoring?" appeared, with `github.com/anthropics/skills` and Add / Don't add (`06-b-question.png`). After Don't add, the builder helped without the skill. | 2 requests, $0.0012 |
| C | "add the doc-coauthoring skill from Anthropic, only for this project", with the switch off | The skill is added for this project only | Settings shows doc-coauthoring with "Only in Skill58" (`13-settings-only-in-project.png`). | 3 requests, $0.0010 |
| D | "add the frontend-design skill from Anthropic" again | Not replaced | "The frontend-design skill from Anthropic is already on this phone, so nothing changed." | 2 requests, $0.0004 |
| E | "add the frontend-design skill from Anthropic, only for this project", on the review-fix build | Added for this project | Settings shows "Only in Skill58b". The row read only "Thought for 9.0 s", because the step summary did not count added skills. Fixed in 24bffe6. | 2 requests, $0.0004 |
| F | The same message, on build 24bffe6 | The row names the added skill | The row reads "Added skill frontend-design". Opened, it shows the first `add_skill` failing ("No skills were found at that link"), then "Added skill frontend-design for this project" (`20-f-steps-opened.png`). Settings shows "Only in Skill58c". | 3 requests, $0.0004 |

The Settings switch was off at the start, turned on for B and off again after it (`05-switch-on.png`, `09-switch-off.png`). At the end the switch was off, frontend-design and doc-coauthoring had been removed in Settings, and Skill58 had been deleted.

## A finding fixed between the runs

In case A, the tool's answer listed the repository's skipped skills (claude-api was too large). The model read that list as "the repository installed everything except claude-api", though only frontend-design was added. Since 78c0ce1, the skipped list is sent only when no skill name was given.

## Limits

- Each case is a single run with one model. Whether the builder finds the right link for a name depends on the model knowing it. There is no GitHub search, because GitHub's code search needs a signed-in account.
- In case F, the model's first link held no skill; it then found the right one. Finding a skill by name depends on the model.
- I did not capture case C's step line on screen. The Settings label is the evidence for the project scope, and host tests check the step text.
- The question for case B came from the builder's first `add_skill` call. Stop while the question is waiting is covered only by the library question's existing tests.
