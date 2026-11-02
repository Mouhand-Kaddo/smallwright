# Standing approvals

Decisions the user has made once, so agents don't ask again. Anything not listed here still needs the user.

## Model requests
- Free-only model requests for this project's tests: approved, $0 spend cap, no paid fallback.
- Paid requests with `deepseek/deepseek-v4.1-flash`: approved for testing the step-by-step run (2026-10-18).
- Paid image requests with `google/gemini-3.1-flash-lite-image`: approved for the 2026-11-02 fixed 20-request suite round only.
- Ask before any other paid request.
- In-app approval (the tap on Send approves the whole run, no default limits): ADR 0003.

## Installing test APKs on the phone over USB (2026-10-02)
USB development tests may install/update this project's verified generated test APKs through Android's debug installer, without asking each time:
- Verify the exact artifact and package first.
- Keep APK bytes entirely on the phone.
- Preserve existing app data.
- Record debug installation separately from production installation consent.

## Local installs
User-local Android SDK/JDK/dependency installation is permitted under `~/.local/share/smallwright/` and standard dependency caches. Record downloaded versions and licenses.

## Commits
Local checkpoint commits of coherent, verified work are permitted.
