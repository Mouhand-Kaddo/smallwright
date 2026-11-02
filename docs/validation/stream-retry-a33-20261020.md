# Streamed replies, no model time limit, retries, on the Galaxy A33 (2026-10-20)

The person saw runs end with "Model request timed out". A model request had a 120 s total deadline and a 30 s read timeout, and since #60 replies have no length cap and thinking is on, so long replies hit them. The person decided: no time limit on how long the model thinks or writes (Stop ends a run), stream the reply so the thinking shows live, and try a failed request again when that can help.

## What changed

- Requests stream (`stream: true`). `ReplyStream` reads the server-sent events and rebuilds the reply the old parser checks. A stream that ends before `[DONE]` counts as a dropped connection.
- The total deadline is gone. A streamed request fails only after 90 s with no bytes at all (OpenRouter sends heartbeats while the model works). Image requests are not streamed and have no read timeout.
- Thinking shows live under "Thinking", its newest two lines, at most every 150 ms, on screen only. The finished step is "Thought for N s" with the trace, as before. Reasoning no longer counts toward the 1 MiB reply cap; a bounded start and end of it are kept.
- A failed request is tried up to 3 more times, after 2, 5 and 15 s or the wait OpenRouter asks for (at most 60 s), for a dropped or silent connection, HTTP 408, 429 and 5xx, a provider error during the stream, and garbled tool arguments. Not for a wrong key, no credit, other refusals, an oversize reply, or Stop. The chat shows "Connection dropped. Trying again (n of 3)…" or "OpenRouter is busy. Trying again (n of 3)…" on screen only. Usage of a failed try counts toward the run's limits, and no try is sent once a limit is reached.

## Setup

- **Phone.** Galaxy A33 `<A33 serial>`, Android 16.
- **Controller.** Installed with `adb install -r` (app data kept):
  - Case A on `adf1530a862c0fb37e154ce37cee5a78f501f619fdf5372dc0c3e98ba0bb405d` (288e21b).
  - Case B on `02f7371e35b66770790c92af8692724e7ad961db807705ac5dbf61caf8c141d2` (1c3bb6a).
  - Case C on `6f383ac0cbf363a3f62e480c94c68d51c658a633ba31b6f8b002a5d68acd1936` (7eaa7c2, final).
- **Host checks at 7eaa7c2.** 521 controller and 91 worker tests pass; lint reports 0 errors.
- **Model.** DeepSeek V4.1 Flash (standing approval for step-run tests), thinking low. GLM 5.3 FlashX was set again on "Main project" at the end of each session.
- **Scripts.** `/tmp/t65/live.py`, `retry.py`, `final.py`, each one locked phone session. Screenshots are in `toolchains/spec35/65/`.
- **Test assistance.** To force a dropped connection, Wi-Fi was turned off with `cmd wifi set-wifi-enabled disabled` about 3 s after Send and on again about 5 s later. Wi-Fi was on at the end of each session.

## Cases

| Case | What was done | Expected | Actual | Cost |
| --- | --- | --- | --- | --- |
| A | New project, "plan a tip calculator app … describe the plan in a few lines. Do not change any files." | Thinking shows and moves while the model works. | One second apart the line under "Thinking" changed from "describe the plan in a few lines. Do not change" to "results, and settings/history maybe. Let me describe in a few lines." (`live-thinking.png`). The reply followed. | 2 runs, $0.0009 |
| B | New project, a long-thinking request; Wi-Fi off 3.2 s after Send, on at 8.3 s. | The run says why it waits, tries again, and finishes. | Thinking streamed ("2. Card gallery with photos…"), then "Connection dropped. Trying again (1 of 3)…" and "(2 of 3)…" while Wi-Fi came back; the next try streamed new thinking and the reply arrived (`retry-steps.png`, `r2/b/`). | 2 runs, $0.0012 |
| C | Case B again on the final build. | As B; the note clears when the next try replies. | "(1 of 3)", "(2 of 3)", "(3 of 3)" while Wi-Fi reconnected, then the last try streamed thinking without the note and the reply arrived (`r3/b/`). | 2 runs, $0.0012 |

The log lines report 1 model request per run; the failed tries sent no usage, so none was counted.

## Cleanup

The test projects Live65, Retry65 and Retry65b were deleted. At the end "Main project" was selected with GLM 5.3 FlashX and an empty draft. Nothing was sent in it.

## Limits

- Retries were seen only for a dropped connection. Busy (429/5xx), mid-stream provider errors, garbled tool arguments, Retry-After, the run-limit check before a retry and Stop during a wait are covered by host tests only.
- The 90 s silence guard was not hit on the phone.
- Each case is a single run with one model. A thinking trace longer than 1 MiB has not been tried on the phone.
