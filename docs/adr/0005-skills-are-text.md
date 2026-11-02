# 0005. Skills are instruction text only

Date: 2026-10-19. Status: accepted.

The user wants to add skills from GitHub, such as
https://github.com/mattpocock/skills, and have the builder use them during a
run, the way Claude Code does. A skill in the Agent Skills format is a folder
with a `SKILL.md` (YAML frontmatter with `name` and `description`, then
Markdown) and sometimes more files: Markdown references, but also scripts and
other assets. Skills are written for agents on computers with a shell, git and
GitHub. The builder has none of these: its model may only list, read, change
and build the project's files on the phone.

So a skill is instruction text and nothing more. From a GitHub link (a
repository, a folder or one `SKILL.md`), the controller asks GitHub's public
API, without a token, for the commit and its file tree, then downloads only
`SKILL.md` and the other `.md` files of each skill from
raw.githubusercontent.com at that commit. It checks each file against the
tree's blob hash. Scripts and every other file are never downloaded, and
nothing from a skill is ever run. Limits: 64 KB per file, 512 KB per skill,
100 skills and 8 MB per link; names follow the format (lowercase letters, digits and
single hyphens, at most 64 characters). As the format's guide for clients
advises, a name need not match its folder's name. Skills live in the controller's
private files, shared by all projects, each one on or off.

At the tap on Send the skills that are on are listed, by name and
description, in the run's first request, with a note that skills were written
for computers with a shell, git and GitHub and that the builder must skip
steps that need tools it doesn't have and say so. A message that starts with
`/name` adds that skill's `SKILL.md` to the first request; an unknown or
switched-off `/name` is refused before anything is sent. During the run the
model reads a skill with `read_skill(name, file?)`, which serves only the
skills captured at the tap and only the files each one holds. Skills marked
`disable-model-invocation: true` are started only by the person with `/name`,
as in Claude Code, and stay readable for the rest of that chat. The
descriptions sent with each request are capped at 16 KB; past that, Send asks
the person to turn some skills off.

## Adding skills from a chat (2026-10-19)

The person may also ask the builder for a skill in a chat, by link or by name.
The model calls `add_skill(link, name?, project_only?)` with a GitHub link:
the one the person gave, or one it knows for that name. The controller checks
the link, then finds the skills with the same downloads and limits as Settings
(text only); with a name and a folder of that name, only that folder is
downloaded first, and the whole link when that folder holds another skill. The skills are saved turned on, for every project, or for this
project only when the person asks for that. Settings marks a project's own
skill "Only in <project>", and other projects never see it.

The chat never replaces a skill already on the phone, whatever its project: it
says so, and the person replaces it by adding it again from its link in
Settings, which keeps the skill's project. A `/name` for another project's
skill is refused at Send. It adds nothing when the
skills would make the descriptions sent with each message pass the 16 KB cap in
any project they are for.
A skill added this way can be read for the rest of the run, except a skill only
the person may start, unless it is the one their `/name` asked for. A message
that starts with `/name` for a valid skill name that is not on the phone is no
longer refused: it is sent with that name, and the builder finds and adds it,
then follows it. A `/name` for a skill that is off is still refused, and so is
one that is not a valid skill name.

By default the builder adds the skill without asking, as the user chose. In
Settings, "Ask before adding skills" (off by default) makes the builder ask in
the chat first, with Add and Don't add, before anything is downloaded.

## Considered options

- Download whole skill folders and run their scripts. Rejected: the builder
  has no shell by design, and the controller never runs downloaded code.
- Paste skills by hand. Rejected: the user asked for links like Claude Code's
  plugin and skill installs.
- Ask in the chat before every skill the builder adds. Rejected as the default
  by the user, who wants asking for a skill to be enough; it stays a setting.
- Search GitHub for a skill by name. Not done: GitHub's code search needs a
  signed-in account. The builder uses a link it knows, or asks for one.
- Put every skill's full text in every request. Rejected: dozens of skills
  would cost every request thousands of tokens; the format's progressive
  disclosure (catalog first, text on demand) avoids it.

## Consequences

- Skills that are mostly scripts, git or GitHub work do little here; the
  builder says what it skipped.
- Skill text is a tool result like any other, so a long run's summary may
  fold it away; the model can read it again.
- GitHub limits unauthenticated API use to about 60 requests an hour per
  address; adding skills uses two.
- With asking off, text the builder reads during a run, such as another
  skill, could lead it to add a skill the person did not ask for. Each one
  shows as an "Added skill" step and in Settings, where it can be removed.
- Deleting a project leaves the skills added for it alone; Settings still
  lists them with the project's name, and the person may remove them.
