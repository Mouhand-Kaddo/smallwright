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

- 2026-11-03, A33: OpenRouter sign-in by a pasted one-time code
  ([ADR 0010](adr/0010-openrouter-sign-in-by-pasted-code.md)). The user signed
  in and approved, the pasted code became a saved key, and one DeepSeek V4.1
  Flash question with that key read 3 files and answered without changes.
  [Record](validation/openrouter-sign-in-a33-20261103.md).
- 2026-11-03, A33: the builder carries the worker and installs or updates it
  from its main screen through Android's prompt; builds work after both, and
  the builder now reconnects after a worker update.
  [Record](validation/bundled-worker-a33-20261103.md). One APK with an isolated
  process instead of a worker app does not work: AAPT2 has no writable folder
  there. [Record](validation/isolated-process-a33-20261103.md).
- 2026-11-02, recovery on the A33: an idle app evicted, a background run killed
  with `kill -9` after a build, and a run killed during a build all reopened
  correctly; a full disk ends a run with a plain storage message, a restart
  with a full disk opens fine, and work continues after space is freed. Two
  wording gaps found there are fixed (`8f4ee43`); the refusal of a send while an
  earlier chat is unsaved is host-reviewed only, because Android frees space
  within seconds. [Record](validation/recovery-a33-20261102.md).
- Host, after the Smallwright rename: 858 tests (767 controller, 91 worker)
  pass in each of the debug and release variants (779 controller after
  2026-11-03, debug run), plus 43 platform probe and 5
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

1. Released publicly on 2026-11-02 as
   [Mouhand-Kaddo/smallwright](https://github.com/Mouhand-Kaddo/smallwright),
   v0.1.0; 0.1.1 (one APK) and 0.1.2 (OpenRouter sign-in) followed on
   2026-11-03. See
   [release](release-readiness.md); known gaps are listed there.

## Commands

Use the project-local JDK/SDK; run one build or emulator at a time:

```bash
source scripts/env.sh
./gradlew :controller:testDebugUnitTest :worker:testDebugUnitTest :controller:lintDebug :worker:lintDebug :controller:assembleDebug :worker:assembleDebug :controller:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
python3 scripts/validate_benchmarks.py benchmarks/results/*.jsonl
PHONE_SERIAL=<A33 serial> APPROVED_MODEL=deepseek/deepseek-v4.1-flash python3 scripts/suite/suite.py run <runId> <promptId> [--continue <runId>]
```

Installed on the A33 (2026-11-03): release 0.1.2 builder `305ac165…` and its
worker, version code 5. See [testing](TESTING.md) for other commands.
