# Status

Updated 2026-11-02 Dubai. The full history up to 2026-10-25 is in
[STATUS-2026-10-25.md](STATUS-2026-10-25.md) and, before that,
[STATUS-2026-10-02.md](STATUS-2026-10-02.md). Execution boundaries are in
[AGENTS.md](../AGENTS.md); standing approvals in
[agents/approvals.md](agents/approvals.md).

## Where it stands

- Renamed Smallwright on 2026-11-02 (briefly Palmwright, dropped because a
  Florida software company uses that name): app ids
  `dev.smallwright.controller` and `dev.smallwright.worker`, generated apps
  `dev.smallwright.generated.*`. Renamed apps install beside older ones and
  start empty. Records and benchmark results from before the rename use the
  new names; those runs used the app's earlier package names.
- Besides OpenRouter, the builder can use any OpenAI-compatible server
  ([ADR 0009](adr/0009-openai-compatible-model-servers.md)).
- The phone builds ordinary multi-file Java/XML Android apps on the Galaxy A33,
  in a separate worker app without Internet access, against Google's Android
  platform files downloaded at setup. The builder (Kotlin/Compose) interviews
  first for new apps, plans, works in steps until the model is done, runs in
  the background with notifications, can make icons and pictures, look things
  up, read crash reports, look at the running app, and undo its last change.
- The user did hands-on testing of the current builder on the A33 and reported
  it good (2026-11-02, [alpha acceptance](alpha-acceptance.md)).
- The A33 is the only test phone. The S23 Ultra is no longer tested.
- No GitHub issues are open (all six closed 2026-11-02 with reasons).

## Latest verification

- 2026-11-02, fixed 20-request suite on the step-by-step builder, A33, paid
  DeepSeek V4.1 Flash (images by Gemini 3.1 Flash Lite Image, approved for this
  round): **19 of 20 pass**; the user closed tracker_05's last check ("older
  days stay") as done without an agent observation. One request needed one feedback message
  (tracker_02, the goal was not editable); none needed a build repair. About
  $0.27 reported for 28 runs. [Record](validation/suite-step-run-a33-20261102.md).
- 2026-11-02, recovery on the A33: an idle app evicted, a background run killed
  with `kill -9` after a build, and a run killed during a build all reopened
  correctly; a full disk ends a run with a plain storage message, a restart
  with a full disk opens fine, and work continues after space is freed. Two
  wording gaps found there are fixed (`8f4ee43`); the refusal of a send while an
  earlier chat is unsaved is host-reviewed only, because Android frees space
  within seconds. [Record](validation/recovery-a33-20261102.md).
- Host, after the Smallwright rename: 858 tests (767 controller, 91 worker)
  pass in each of the debug and release variants, plus 43 platform probe and 5
  protocol tests; Spotless and lint pass.
- 2026-11-02, A33: release APKs signed with the release key install, set up,
  build the starter app and install it through Android's installer.
  [Record](validation/smallwright-a33-release-20261102.md).
- 2026-11-02, A33: the renamed apps install and set up, the worker signer check
  binds, the starter app builds with the source-built AAPT2 and installs through
  Android's installer after the user's confirmation, and Claude Haiku 5.5
  through Anthropic's OpenAI-compatible endpoint added a working Reset button
  in one run (4 requests, about $0.004).
  [Record](validation/smallwright-a33-20261102.md).

## Remaining

1. Public release on GitHub: see [release readiness](release-readiness.md).

## Commands

Use the project-local JDK/SDK; run one build or emulator at a time:

```bash
source scripts/env.sh
./gradlew :controller:testDebugUnitTest :worker:testDebugUnitTest :controller:lintDebug :worker:lintDebug :controller:assembleDebug :worker:assembleDebug :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 scripts/validate_benchmarks.py benchmarks/results/*.jsonl
PHONE_SERIAL=<A33 serial> APPROVED_MODEL=deepseek/deepseek-v4.1-flash python3 scripts/suite/suite.py run <runId> <promptId> [--continue <runId>]
```

Installed on the A33 (2026-11-02): Smallwright controller `c3a428c1…`, worker
`b543b150…`. See [testing](TESTING.md) for other commands.
