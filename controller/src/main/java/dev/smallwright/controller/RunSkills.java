package dev.smallwright.controller;

import dev.smallwright.agent.RunTools;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.skills.GitHubSkills;
import dev.smallwright.skills.SkillFile;
import dev.smallwright.skills.SkillStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The skills of one run, as they were at the tap on Send: those turned on, and the one the message
 * started with {@code /name}, if any; then those the builder adds during the run. {@code
 * read_skill} reads only these.
 */
final class RunSkills {
    static final RunSkills NONE =
            new RunSkills(null, null, new LinkedHashMap<>(), Set.of(), null, null, null);
    static final int MAX_CATALOG_BYTES = 16 * 1024;

    private static final Pattern COMMAND = Pattern.compile("/([^\\s/]+)(?:\\s[\\s\\S]*)?");

    /** A Send refused because of its skills; nothing was sent. */
    static final class Refused extends IllegalArgumentException {
        Refused(String message) {
            super(message);
        }
    }

    private final SkillStore store;
    private final String projectId;
    private final Map<String, SkillStore.Installed> on;
    private final Set<String> startedEarlier;

    /** The skill the person started with {@code /name}, or null. */
    final SkillStore.Installed used;

    /** That skill's {@code SKILL.md} as sent, or null. */
    final String usedText;

    /** The skill the message started with {@code /name} that is not on this phone, or null. */
    final String wanted;

    private RunSkills(
            SkillStore store,
            String projectId,
            Map<String, SkillStore.Installed> on,
            Set<String> startedEarlier,
            SkillStore.Installed used,
            String usedText,
            String wanted) {
        this.store = store;
        this.projectId = projectId;
        this.on = on;
        this.startedEarlier = startedEarlier;
        this.used = used;
        this.usedText = usedText;
        this.wanted = wanted;
    }

    /** The skill name a message starts with, as {@code /name}, or null. */
    static String command(String message) {
        Matcher command = COMMAND.matcher(message == null ? "" : message);
        return command.matches() ? command.group(1) : null;
    }

    /**
     * Reads the skills of project {@code projectId} now, those for every project and those for it
     * alone, for {@code message} after the chat's {@code earlier} messages from the person. A skill
     * only the person may start stays readable once they started it with {@code /name} earlier in
     * the chat. A {@code /name} not on this phone is sent for the builder to find and add. Throws
     * {@link Refused} for a {@code /name} that is off or cannot be read, or a list of skills too
     * long to send. A damaged list sends no skills.
     */
    static RunSkills atSend(
            SkillStore store, String projectId, String message, List<String> earlier)
            throws IOException {
        String name = command(message);
        if (store == null) {
            if (name != null) throw unknownName(name);
            return NONE;
        }
        List<SkillStore.Installed> all;
        try {
            all = store.list();
        } catch (IOException damaged) {
            if (name == null) return NONE;
            throw new Refused(
                    "The list of skills on this phone is damaged. Remove all skills in Settings,"
                            + " then add them again. Nothing was sent.");
        }
        Set<String> started = new HashSet<>();
        for (String text : earlier) {
            String earlierName = command(text);
            if (earlierName != null) started.add(earlierName);
        }
        Map<String, SkillStore.Installed> on = new LinkedHashMap<>();
        SkillStore.Installed named = null;
        for (SkillStore.Installed skill : all) {
            if (!skill.isFor(projectId)) {
                if (skill.name.equals(name))
                    throw new Refused(
                            "The skill "
                                    + name
                                    + " is only in "
                                    + skill.projectTitle
                                    + ". Nothing was sent.");
                continue;
            }
            if (skill.on) on.put(skill.name, skill);
            if (skill.name.equals(name)) named = skill;
        }
        if (name == null)
            return checked(new RunSkills(store, projectId, on, started, null, null, null));
        if (named == null) {
            if (!SkillFile.validName(name)) throw unknownName(name);
            return checked(new RunSkills(store, projectId, on, started, null, null, name));
        }
        if (!named.on)
            throw new Refused(
                    "The skill "
                            + name
                            + " is off. Turn it on in Settings to use it. Nothing was sent.");
        byte[] bytes = store.read(named, SkillFile.NAME);
        String text = bytes == null ? null : SkillFile.text(bytes);
        if (text == null)
            throw new Refused(
                    "The skill "
                            + name
                            + " could not be read. Add it again in Settings. Nothing was sent.");
        return checked(new RunSkills(store, projectId, on, started, named, text, null));
    }

    private static RunSkills checked(RunSkills skills) {
        if (bytes(skills.offered().values()) > MAX_CATALOG_BYTES)
            throw new Refused(
                    "Too many skills are on. Turn some off in Settings. Nothing was sent.");
        return skills;
    }

    private static int bytes(Iterable<String> descriptions) {
        int bytes = 0;
        for (String description : descriptions)
            bytes += description.getBytes(StandardCharsets.UTF_8).length;
        return bytes;
    }

    boolean mayRead(SkillStore.Installed skill) {
        return skill.modelInvocable
                || skill == used
                || skill.name.equals(wanted)
                || startedEarlier.contains(skill.name);
    }

    boolean canAdd() {
        return store != null;
    }

    /** What {@link #add} did: the skills it saved, and those already here that it left alone. */
    static final class Added {
        final List<SkillStore.Installed> saved = new ArrayList<>();
        final List<SkillStore.Installed> alreadyHere = new ArrayList<>();
    }

    /**
     * Saves these skills turned on, for every project or for this one only, and lets {@code
     * read_skill} read them. A skill with the name of one already on this phone is left alone.
     * Throws, saving nothing, when the skills would make the list sent with each message too long
     * in any project they are for.
     */
    Added add(String source, List<GitHubSkills.Found> found, String projectTitle)
            throws IOException {
        Added added = new Added();
        Map<String, SkillStore.Installed> here = new LinkedHashMap<>();
        for (SkillStore.Installed skill : store.list()) here.put(skill.name, skill);
        List<GitHubSkills.Found> fresh = new ArrayList<>();
        for (GitHubSkills.Found skill : found)
            if (here.containsKey(skill.name)) added.alreadyHere.add(here.get(skill.name));
            else fresh.add(skill);
        if (fresh.isEmpty()) return added;
        Set<String> projects = new HashSet<>();
        projects.add(projectId);
        if (projectTitle == null)
            for (SkillStore.Installed skill : here.values())
                if (skill.project != null) projects.add(skill.project);
        for (String project : projects) {
            List<String> descriptions = new ArrayList<>();
            for (SkillStore.Installed skill : here.values())
                if (skill.on && skill.modelInvocable && skill.isFor(project))
                    descriptions.add(skill.description);
            for (GitHubSkills.Found skill : fresh)
                if (skill.modelInvocable) descriptions.add(skill.description);
            if (bytes(descriptions) > MAX_CATALOG_BYTES)
                throw new IOException(
                        "Adding "
                                + (fresh.size() == 1 ? "it" : "these " + fresh.size() + " skills")
                                + " would make the list of skills sent with each message too long."
                                + " Nothing was added. Add fewer, by name, or ask the person to"
                                + " turn some off in Settings.");
        }
        store.add(source, fresh, projectTitle == null ? null : projectId, projectTitle);
        Set<String> names = new HashSet<>();
        for (GitHubSkills.Found skill : fresh) names.add(skill.name);
        for (SkillStore.Installed skill : store.list())
            if (names.contains(skill.name)) {
                on.put(skill.name, skill);
                added.saved.add(skill);
            }
        return added;
    }

    private static Refused unknownName(String name) {
        return new Refused(
                "There is no skill /"
                        + (name.length() > 64 ? name.substring(0, 64) + "…" : name)
                        + ". Settings lists the skills you added. Nothing was sent.");
    }

    /**
     * The skills the model may read, by name, with their descriptions: those it may start itself,
     * and those the person started earlier in this chat.
     */
    Map<String, String> offered() {
        Map<String, String> offered = new LinkedHashMap<>();
        for (SkillStore.Installed skill : on.values())
            if (skill.modelInvocable || startedEarlier.contains(skill.name))
                offered.put(skill.name, skill.description);
        return offered;
    }

    /** Runs {@code read_skill}: a skill's {@code SKILL.md}, or another of its files. */
    String read(RunTools.Call call, RunSteps steps) throws IOException {
        String name = call.nameArgument;
        String file = call.file == null ? SkillFile.NAME : call.file;
        String what = "Could not read skill " + shorter(name);
        SkillStore.Installed skill = on.get(name);
        if (skill == null)
            return refused(
                    steps,
                    what,
                    "There is no skill named "
                            + shorter(name)
                            + " that is on. The skills you may read are listed under skills.");
        if (!mayRead(skill))
            return refused(
                    steps,
                    what,
                    name
                            + " is started only by the person, by beginning a message with /"
                            + name
                            + ". Do not use it.");
        byte[] bytes = store.read(skill, file);
        if (bytes == null)
            return refused(
                    steps,
                    what,
                    skill.files.containsKey(file)
                            ? name
                                    + "'s "
                                    + shorter(file)
                                    + " could not be read: it was removed, replaced or damaged"
                                    + " after this run started."
                            : name
                                    + " has no file "
                                    + shorter(file)
                                    + ". Its files are: "
                                    + String.join(", ", skill.files.keySet())
                                    + ".");
        String text = SkillFile.text(bytes);
        if (text == null) return refused(steps, what, file + " is not a text file.");
        steps.show(
                StepKind.DONE,
                "Read skill " + name + (file.equals(SkillFile.NAME) ? "" : ", " + file),
                "");
        StringBuilder result =
                new StringBuilder("<skill_content name=\"")
                        .append(name)
                        .append("\" file=\"")
                        .append(file)
                        .append("\">\n")
                        .append(text)
                        .append("\n</skill_content>");
        List<String> others = new ArrayList<>();
        for (Map.Entry<String, Integer> other : skill.files.entrySet())
            if (!other.getKey().equals(file))
                others.add(other.getKey() + " (" + other.getValue() + " bytes)");
        if (file.equals(SkillFile.NAME) && !others.isEmpty())
            result.append("\nThe skill's other files, read with read_skill and file only when it")
                    .append(" points to them: ")
                    .append(String.join(", ", others))
                    .append('.');
        return result.toString();
    }

    private static String refused(RunSteps steps, String what, String why) {
        steps.show(StepKind.FAILED, what, why);
        return why;
    }

    private static String shorter(String text) {
        return text.length() > 80 ? text.substring(0, 80) + "…" : text;
    }
}
