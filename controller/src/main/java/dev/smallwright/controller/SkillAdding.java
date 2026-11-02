package dev.smallwright.controller;

import dev.smallwright.agent.RunTools;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.skills.GitHubLink;
import dev.smallwright.skills.GitHubSkills;
import dev.smallwright.skills.SkillStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs {@code add_skill}: finds the skills at a GitHub link and adds them, or the one named, turned
 * on for every project or for this one only, and readable for the rest of the run. A skill already
 * on this phone is never replaced. Asks the person first when they chose that in Settings.
 */
final class SkillAdding {
    /** Whether the person agrees, asked before anything is downloaded. */
    interface Permission {
        boolean asks(String question, String detail);
    }

    static final SkillAdding NONE = new SkillAdding(null, null, null);

    private final GitHubSkills github;
    private final Permission permission;
    private final String projectTitle;

    /** {@code permission} is null when skills are added without asking. */
    SkillAdding(GitHubSkills github, Permission permission, String projectTitle) {
        this.github = github;
        this.permission = permission;
        this.projectTitle = projectTitle;
    }

    String add(RunTools.Call call, RunSkills skills, RunSteps steps) {
        String link = shown(call.link);
        String what =
                call.nameArgument == null
                        ? "skills from " + link
                        : "the skill " + call.nameArgument;
        String where = call.projectOnly ? " for this project" : "";
        if (github == null || !skills.canAdd())
            return failed(steps, what, "Skills cannot be added on this phone right now.");
        try {
            GitHubLink.parse(call.link);
        } catch (IOException notALink) {
            return failed(steps, what, notALink.getMessage());
        }
        steps.show(StepKind.RUNNING, "Adding " + what + where, "");
        if (permission != null) {
            if (!permission.asks("Add " + what + where + "?", link)) {
                steps.finishLast(StepKind.DONE, "Did not add " + what, "You chose not to.");
                return "The person chose not to add "
                        + what
                        + ". Nothing was downloaded. Do not add it again unless they ask.";
            }
            steps.finishLast(StepKind.RUNNING, "Adding " + what + where, "");
        }
        try {
            GitHubSkills.Result result =
                    github.find(call.link, (done, total) -> {}, call.nameArgument);
            if (call.nameArgument != null && named(result, call.nameArgument).isEmpty())
                result = github.find(call.link, (done, total) -> {});
            List<GitHubSkills.Found> chosen =
                    call.nameArgument == null ? result.found : named(result, call.nameArgument);
            List<String> names = new ArrayList<>();
            for (GitHubSkills.Found skill : result.found) names.add(skill.name);
            if (chosen.isEmpty())
                return failed(
                        steps,
                        what,
                        names.isEmpty()
                                ? "No skill at that link could be used." + skipped(result)
                                : "There is no skill named "
                                        + call.nameArgument
                                        + " at that link. The skills there are: "
                                        + String.join(", ", names)
                                        + ". Nothing was added.");
            RunSkills.Added added =
                    skills.add(result.source, chosen, call.projectOnly ? projectTitle : null);
            StringBuilder answer = new StringBuilder();
            List<String> addedNames = new ArrayList<>();
            for (SkillStore.Installed skill : added.saved) {
                addedNames.add(skill.name);
                answer.append("Added ")
                        .append(skill.name)
                        .append(": ")
                        .append(skill.description)
                        .append('\n');
                if (!skills.mayRead(skill))
                    answer.append(skill.name)
                            .append(" starts only when the person begins a message with /")
                            .append(skill.name)
                            .append(
                                    ". Do not read it unless they do; tell them how to start it.\n");
            }
            List<String> kept = new ArrayList<>();
            for (SkillStore.Installed skill : added.alreadyHere) {
                kept.add(skill.name);
                answer.append(skill.name)
                        .append(" is already on this phone")
                        .append(skill.projectTitle == null ? "" : ", only in " + skill.projectTitle)
                        .append(", so it was left as it is. To replace it, the person adds it")
                        .append(" again from its link in Settings.\n");
            }
            if (added.saved.isEmpty()) {
                steps.finishLast(
                        StepKind.DONE,
                        kept.size() == 1
                                ? "Skill " + kept.get(0) + " is already added"
                                : "These skills are already added",
                        kept.size() == 1 ? "" : String.join(", ", kept));
                return answer.toString().trim();
            }
            steps.finishLast(
                    StepKind.DONE,
                    (addedNames.size() == 1
                                    ? "Added skill " + addedNames.get(0)
                                    : "Added " + addedNames.size() + " skills")
                            + where,
                    addedNames.size() == 1 ? "" : String.join(", ", addedNames));
            return answer.append(
                            call.projectOnly
                                    ? "They are on for this project only."
                                    : "They are on for every project.")
                    .append(" Read one with read_skill when it fits.")
                    .append(call.nameArgument == null ? skipped(result) : "")
                    .toString();
        } catch (IOException failure) {
            return failed(
                    steps,
                    what,
                    failure.getMessage() == null ? "Could not add it." : failure.getMessage());
        }
    }

    private static List<GitHubSkills.Found> named(GitHubSkills.Result result, String name) {
        List<GitHubSkills.Found> named = new ArrayList<>();
        for (GitHubSkills.Found skill : result.found) if (skill.name.equals(name)) named.add(skill);
        return named;
    }

    private static String skipped(GitHubSkills.Result result) {
        return result.skipped.isEmpty()
                ? ""
                : " Not added, because they could not be used: "
                        + String.join("; ", result.skipped);
    }

    private static String failed(RunSteps steps, String what, String why) {
        if (steps.lastIs(StepKind.RUNNING))
            steps.finishLast(StepKind.FAILED, "Could not add " + what, why);
        else steps.show(StepKind.FAILED, "Could not add " + what, why);
        return why;
    }

    private static String shown(String link) {
        String text = link.trim().replaceFirst("^https?://", "").replaceFirst("/+$", "");
        return text.length() > 80 ? text.substring(0, 80) + "…" : text;
    }
}
