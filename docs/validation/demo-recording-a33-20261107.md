# Demo recording on the A33 (2026-11-07)

A new water tracker built from a chat on the phone, recorded for the README (`docs/media/demo.gif`), the site video and the store screenshots (`fastlane/.../phoneScreenshots/2-plan.png`, `3-build.png`, `4-install.png`).

## Setup

- Phone: Samsung Galaxy A33 5G (SM-A336E), Android 16, arm64-v8a.
- Installed release 0.1.2 (version code 5) of the builder and worker, already set up.
- Model: DeepSeek V4.1 Flash through OpenRouter, thinking low. Cost was not recorded.
- Recording: `adb shell screenrecord` in 180-second segments, 6 minutes 15 seconds in total, joined and cut to 55 seconds with ffmpeg. Raw footage stays local.
- Input: agent taps over ADB, through the app's own screens and Android's installer.

## Run

| Step | Result |
|---|---|
| New project "Water tracker", tap the suggestion "A water tracker that counts glasses per day", Send | The builder asked four questions with recommended answers |
| Answers | Kept the recommendations for the daily goal (8), the 7-day history and tapping glasses; for the look, "Something else": "Soft blue look and the name, no icon", to avoid the paid icon request |
| Plan | "Shall I build the water tracker this way?" Yes, build it |
| Build | "Building on this phone", then "Your app is ready." about a minute after the plan was approved |
| Install on this phone | Android asked "Do you want to install this app?"; Install; Play Protect "App scan recommended", Scan app (uploads the app to Google, as in earlier runs), "This app looks safe", Install |
| Open | Water Tracker opened at 0 / 8; tapping glasses 1–5 showed 5 / 8, "3 glasses to go", and Today 5 / 8 in the last 7 days |
