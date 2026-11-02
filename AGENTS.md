# Smallwright: approved execution boundary

## Start here
- Read docs/STATUS.md first, then the relevant sections of docs/IMPLEMENTATION_PLAN.md (approved; implement it in this repository and independently verify each checkpoint). Read linked validation records or dated status archives only when the current task needs their evidence.
- Before a paid or free model request, an APK install on the phone, a download/install, or a commit: read `docs/agents/approvals.md` for what the user has already approved.

## Device and authentication
- The permanent test phone is the Samsung Galaxy A33 5G; the S23 Ultra is no longer tested (user decision, 2026-10-19). Use the global `phone-use` skill for physical tests and verify the current device connection first. Emulator verification is useful but is NOT a physical-device pass. No fabricated measurements.
- The app's future authentication may investigate Codex subscription use ONLY through a supported, permitted integration. Do not copy/reuse host OAuth tokens in an APK, spoof an official client, invent an OAuth client ID, or introduce remote execution. First compiler APK requires no AI credentials.

## Scope and security
- Cloud inference ONLY. Source files, tools, builds, APK artifacts and generated app execution live on PHONE. No remote builds, downloaded ready-made generated app, streamed emulators or WebView substitution.
- Initial goal is compiler-test controller + isolated worker APKs. Start with actual Java/XML native app output. Kotlin/Compose may be used for controller; pick a modest pinned supported stack after documentation lookup.
- Controller owns keys and projects; separate-UID worker has no Internet permission and receives only the current snapshot and its checksum-verified compile inputs (the Android platform files and project libraries the controller downloaded; ADRs 0002 and 0004). Caller authentication, manifest policy, per-project signing and immutable artifact verification are required. Production installation uses Android's explicit user-confirmation flow. A normal separate process with same UID is NOT isolation.
- Keep the app's exact context/revision checks: the context prepared at the tap on Send is the one sent, and a stale revision is refused.
- Never put a time limit on how long the model may think or write; the person can tap Stop (user decision, 2026-10-20).
- No arbitrary AI shell, Gradle edits, plugins or annotation processors. Never load generated code into the controller.
- The harness itself can be built on this laptop. Its generated apps MUST compile on phone. Validate on a local emulator if feasible and prepare ARM64 artifacts for the A33.
- Do not copy Sketchware Pro source under an assumed license. Its README restricts redistribution. Individually verify compiler/tool redistribution rights, pin/checksum artifacts, preserve required notices.
- No existing secrets may be read, logged, copied or sent to a model. Runtime-generated throwaway signing keys for this project's local test APKs may be created and used by build tools under this project's ignored local directories; never read/print their bytes, never commit them or ship shared debug signing identities in generated apps. Name-only references are sufficient. All existing credential stores remain forbidden.
- No sudo, system package manager installs, destructive commands, global configuration changes, gateway restarts, or edits to unrelated repositories. No paid/cloud resources.
- No network listeners exposed outside localhost. Avoid unnecessary services. KVM is accessible; memory is limited, so one worker/build/emulator at a time. Cap Gradle memory/workers. Host default Java is 26: install/use a project-local supported JDK rather than altering global Java.
- Context7 is available for bounded SDK/library documentation; other MCP integrations are disabled. Do not touch Redmine/GlitchTip or other tasks.

## Workflow
- Strict vertical test-first development for nontrivial logic, real execution and honest reporting. Do not weaken tests or create fake integration evidence. Compiler and security boundaries need real runtime tests.
- Do not build a provider framework, plugin system, UI design system or general orchestrator.
- Keep docs/STATUS.md as a concise current snapshot of what actually ran, what remains, current paths/commands and known blockers; place detailed checkpoint evidence in linked validation documents or dated archives. Do not commit generated builds, SDKs, caches, signing materials or local identity files.
- For every handoff: summary, exact changed paths, commands/results, APK paths if built, limitations, next task. Never claim physical tests without connected hardware.
- Tools and scripts that do not exist must be implemented or explicitly reported missing. Do not report success from placeholder methods, canned API responses or prebuilt fixture APKs.
- When a step doesn't need me, keep going and put status notes in the same message as your next action. Stop and ask only when you can't continue without me, or before anything destructive: deleting data, force-pushing, or changing anything outside this repository.
- Check subagent work before accepting it.
- Wait for a log line with `scripts/wait-for-line.sh FILE REGEX [SECONDS]`; it returns the moment the line appears.

## Agent skills

### Issue tracker

Track issues and specs in GitHub Issues for this repository. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the five default triage labels. See `docs/agents/triage-labels.md`.

### Domain docs

Use a single root `GLOSSARY.md` and `docs/adr/` layout. See `docs/agents/domain.md`.
