package dev.smallwright.skills;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The skills on this phone, shared by every project, in the controller's private files. Each added
 * skill gets its own new folder, so a replaced or removed skill's text is gone rather than changed
 * under a run that listed it. {@code skills.json} lists them and whether each is on.
 */
public final class SkillStore {
    private static final Object LOCK = new Object();
    private static final String INDEX = "skills.json";
    private static final String DAMAGED = "The list of skills on this phone is damaged.";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String STAGING = ".tmp";
    private static final Pattern ID = Pattern.compile("[0-9a-f]{32}");

    /** One added skill. */
    public static final class Installed {
        public final String name;
        public final String description;
        public final boolean modelInvocable;
        public final boolean on;
        public final String source;

        /** The one project it is for and that project's name, or null for every project. */
        public final String project, projectTitle;

        final String id;

        /** Its files and their sizes in bytes, {@code SKILL.md} first. */
        public final Map<String, Integer> files;

        Installed(
                String name,
                String description,
                boolean modelInvocable,
                boolean on,
                String source,
                String project,
                String projectTitle,
                String id,
                Map<String, Integer> files) {
            this.name = name;
            this.description = description;
            this.modelInvocable = modelInvocable;
            this.on = on;
            this.source = source;
            this.project = project;
            this.projectTitle = projectTitle;
            this.id = id;
            this.files = Collections.unmodifiableMap(files);
        }

        Installed turned(boolean on) {
            return new Installed(
                    name,
                    description,
                    modelInvocable,
                    on,
                    source,
                    project,
                    projectTitle,
                    id,
                    files);
        }

        /** Whether the builder may use it in this project. */
        public boolean isFor(String projectId) {
            return project == null || project.equals(projectId);
        }
    }

    private final File directory;

    public SkillStore(File directory) {
        this.directory = directory;
    }

    /** Every added skill, by name. */
    public List<Installed> list() throws IOException {
        synchronized (LOCK) {
            return new ArrayList<>(load().values());
        }
    }

    /**
     * Adds skills turned on; one with the name of a skill already here replaces it and keeps its
     * project. Files are written and synced before the lock is taken, so the list stays readable
     * meanwhile.
     */
    public void add(String source, List<GitHubSkills.Found> found) throws IOException {
        add(source, found, null, null);
    }

    /** Adds skills for one project only, or for every project when {@code project} is null. */
    public void add(
            String source, List<GitHubSkills.Found> found, String project, String projectTitle)
            throws IOException {
        if (project != null && projectTitle == null)
            throw new IllegalArgumentException("A project's skill needs the project's name");
        Map<String, String> ids = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> sizes = new LinkedHashMap<>();
        try {
            for (GitHubSkills.Found skill : found) {
                String id = newId();
                ids.put(skill.name, id);
                sizes.put(skill.name, stage(skill, new File(directory, id + STAGING)));
            }
            synchronized (LOCK) {
                Map<String, Installed> skills = load();
                for (GitHubSkills.Found skill : found) {
                    String id = ids.get(skill.name);
                    Installed replaced = skills.get(skill.name);
                    boolean keepsProject = project == null && replaced != null;
                    Files.move(
                            new File(directory, id + STAGING).toPath(),
                            new File(directory, id).toPath(),
                            StandardCopyOption.ATOMIC_MOVE);
                    skills.put(
                            skill.name,
                            new Installed(
                                    skill.name,
                                    skill.description,
                                    skill.modelInvocable,
                                    true,
                                    source,
                                    keepsProject ? replaced.project : project,
                                    keepsProject ? replaced.projectTitle : projectTitle,
                                    id,
                                    sizes.get(skill.name)));
                }
                save(skills);
            }
        } finally {
            for (String id : ids.values()) delete(new File(directory, id + STAGING));
        }
    }

    private static Map<String, Integer> stage(GitHubSkills.Found skill, File staged)
            throws IOException {
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> file : skill.files.entrySet()) {
            if (!GitHubLink.safePath(file.getKey()))
                throw new IOException("A skill file has a name the builder does not accept.");
            File target = new File(staged, file.getKey());
            File parent = target.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs())
                throw new IOException("Could not save the skill " + skill.name + ".");
            writeSynced(target, file.getValue());
            sizes.put(file.getKey(), file.getValue().length);
        }
        return sizes;
    }

    private static void writeSynced(File file, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            output.getChannel().force(true);
        }
    }

    /** Turns a skill on when it is off, and off when it is on. */
    public void toggle(String name) throws IOException {
        synchronized (LOCK) {
            Map<String, Installed> skills = load();
            Installed skill = skills.get(name);
            if (skill == null) throw new IOException("There is no skill named " + name + ".");
            skills.put(name, skill.turned(!skill.on));
            save(skills);
        }
    }

    /** Deletes every skill and the list, even when the list is damaged. */
    public void removeAll() {
        synchronized (LOCK) {
            delete(directory);
        }
    }

    public void setOn(String name, boolean on) throws IOException {
        synchronized (LOCK) {
            Map<String, Installed> skills = load();
            Installed skill = skills.get(name);
            if (skill == null) throw new IOException("There is no skill named " + name + ".");
            skills.put(name, skill.turned(on));
            save(skills);
        }
    }

    public void remove(String name) throws IOException {
        synchronized (LOCK) {
            Map<String, Installed> skills = load();
            if (skills.remove(name) == null)
                throw new IOException("There is no skill named " + name + ".");
            save(skills);
        }
    }

    /**
     * One file of a skill as it was listed, or null when the skill holds no such file or was
     * removed or replaced since.
     */
    public byte[] read(Installed skill, String path) throws IOException {
        if (path == null || !skill.files.containsKey(path)) return null;
        synchronized (LOCK) {
            File file = new File(new File(directory, skill.id), path);
            if (!file.isFile()) return null;
            byte[] bytes = Files.readAllBytes(file.toPath());
            return bytes.length == skill.files.get(path) ? bytes : null;
        }
    }

    private Map<String, Installed> load() throws IOException {
        Map<String, Installed> skills = new TreeMap<>();
        File index = new File(directory, INDEX);
        if (!index.isFile()) return skills;
        try {
            JsonObject root =
                    JsonParser.parseString(
                                    new String(
                                            Files.readAllBytes(index.toPath()),
                                            StandardCharsets.UTF_8))
                            .getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("skills")) {
                JsonObject entry = element.getAsJsonObject();
                Map<String, Integer> files = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> file :
                        entry.getAsJsonObject("files").entrySet()) {
                    if (!GitHubLink.safePath(file.getKey())) throw new IOException(DAMAGED);
                    files.put(file.getKey(), file.getValue().getAsInt());
                }
                String name = entry.get("name").getAsString();
                String project = entry.has("project") ? entry.get("project").getAsString() : null;
                String projectTitle =
                        project == null ? null : entry.get("projectTitle").getAsString();
                String id = entry.get("id").getAsString();
                if (!SkillFile.validName(name)
                        || !ID.matcher(id).matches()
                        || !files.containsKey(SkillFile.NAME)) throw new IOException(DAMAGED);
                skills.put(
                        name,
                        new Installed(
                                name,
                                entry.get("description").getAsString(),
                                entry.get("modelInvocable").getAsBoolean(),
                                entry.get("on").getAsBoolean(),
                                entry.get("source").getAsString(),
                                project,
                                projectTitle,
                                id,
                                files));
            }
        } catch (RuntimeException damaged) {
            throw new IOException(DAMAGED);
        }
        return skills;
    }

    /** Writes the list in one step, then deletes every folder it no longer names. */
    private void save(Map<String, Installed> skills) throws IOException {
        JsonArray list = new JsonArray();
        Set<String> kept = new HashSet<>();
        for (Installed skill : skills.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", skill.name);
            entry.addProperty("description", skill.description);
            entry.addProperty("modelInvocable", skill.modelInvocable);
            entry.addProperty("on", skill.on);
            entry.addProperty("source", skill.source);
            entry.addProperty("id", skill.id);
            if (skill.project != null) {
                entry.addProperty("project", skill.project);
                entry.addProperty("projectTitle", skill.projectTitle);
            }
            JsonObject files = new JsonObject();
            for (Map.Entry<String, Integer> file : skill.files.entrySet())
                files.addProperty(file.getKey(), file.getValue());
            entry.add("files", files);
            list.add(entry);
            kept.add(skill.id);
        }
        JsonObject root = new JsonObject();
        root.add("skills", list);
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Could not save the skills.");
        File temporary = new File(directory, INDEX + ".tmp");
        writeSynced(temporary, root.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(
                temporary.toPath(),
                new File(directory, INDEX).toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children)
            if (child.isDirectory()
                    && ID.matcher(child.getName()).matches()
                    && !kept.contains(child.getName())) delete(child);
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }

    private static String newId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(32);
        for (byte value : bytes)
            hex.append(Character.forDigit((value >> 4) & 15, 16))
                    .append(Character.forDigit(value & 15, 16));
        return hex.toString();
    }
}
