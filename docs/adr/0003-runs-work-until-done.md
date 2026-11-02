# 0003. A run works in steps until the model is done, with no default limits

Date: 2026-10-18. Status: accepted.

A Send used to be one model request that rewrote one whole file, plus at most
three automatic fixes, at about $0.05 a request. The user wants the flow of
Claude Code. So a run is now a loop: the model lists, reads, searches,
creates, edits and deletes project files, starts builds on the phone and reads
their errors, until it answers with no tool call. Every tool works only on the
project's files on the phone, at a checked revision. Like Claude Code, whose
`maxTurns` and `maxBudgetUsd` both default to no limit, there is no step or
spending limit by default. Stop is always available, and a long run summarizes
its older steps and continues instead of failing. Settings offers optional run
limits (steps, spending), a "stop when stuck" switch (the same build error
after three fixes in a row) and "ask before adding libraries". All of these are off unless the person turns them
on. The tap on Send approves the whole run.

## Consequences

- A run can cost far more than one request, paid by the person's own
  OpenRouter key. The person's protection is Stop, the optional limits, and a
  credit limit they can set on their key at OpenRouter.
- Runs continue in the background as an Android foreground service, with a
  run notification: working (with the latest step and Stop), needs your input,
  done or failed. Only the working state shows while the chat is open.
- `AGENTS.md`'s approval sentence ("at most UsageBudget.MAX_REPAIRS more
  requests") and `UsageBudget`'s per-run caps change with this.
