# Issue tracker: GitHub

Issues and specs for this repo live as GitHub issues in `Mouhand-Kaddo/smallwright`. Use the `gh` CLI for issue operations.

## Conventions

- Create: `gh issue create --title "..." --body-file <file>`.
- Read: `gh issue view <number> --comments` and inspect its labels.
- List: `gh issue list --state open --json number,title,labels`, adding label or state filters as needed.
- Comment: `gh issue comment <number> --body-file <file>`.
- Label: `gh issue edit <number> --add-label "..."` or `--remove-label "..."`.
- Close: `gh issue close <number> --comment "..."`.

Run these commands from this checkout, where `gh` can infer the repository from `origin`. Use the label strings in `docs/agents/triage-labels.md` for triage.

## Pull requests as a triage surface

**PRs as a request surface: no.** Set this to `yes` only if external PRs should enter the triage queue.

## When a skill says "publish to the issue tracker"

Create a GitHub issue.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> --comments`.

## Wayfinding operations

For `/wayfinder`, a map is one issue labelled `wayfinder:map`, with child issues as tickets. Link children as GitHub sub-issues when available; otherwise keep a task list in the map and put `Part of #<map>` in each child. Label each child `wayfinder:<type>` (`research`, `prototype`, `grilling`, or `task`). Record blockers with native issue dependencies when available; otherwise use `Blocked by: #<n>` in the child body. Choose the first open, unblocked, unassigned child in map order, assign it to yourself before work, then resolve it with an answer comment and closure. Append a brief decision and link to the map.
