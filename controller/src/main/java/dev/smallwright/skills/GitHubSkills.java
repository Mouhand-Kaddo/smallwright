package dev.smallwright.skills;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.smallwright.net.HttpsDownload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Finds the skills a GitHub link holds and downloads their Markdown files, without a token: one API
 * request for the commit, one for the file tree at that commit, then each {@code .md} file of each
 * skill from raw.githubusercontent.com at that commit, checked against the tree's blob hash.
 * Scripts and every other file are never downloaded.
 */
public final class GitHubSkills {
    public static final int MAX_SKILLS = 100;
    public static final int MAX_SKILL_BYTES = 512 * 1024;
    private static final int MAX_COMMIT_BYTES = 1024;
    public static final int MAX_LINK_BYTES = 8 * 1024 * 1024;
    private static final int MAX_TREE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DEPTH = 6;
    private static final String GITHUB = "GitHub";

    /** Where GitHub's API and raw files are. */
    public static final class Endpoints {
        final String api, raw;

        public Endpoints(String api, String raw) {
            this.api = api;
            this.raw = raw;
        }
    }

    public static final Endpoints PUBLIC =
            new Endpoints("https://api.github.com/", "https://raw.githubusercontent.com/");

    /** One skill ready to add: its checked {@code SKILL.md} first, then its other files. */
    public static final class Found {
        public final String name;
        public final String description;
        public final boolean modelInvocable;
        public final Map<String, byte[]> files;

        public Found(SkillFile skill, Map<String, byte[]> files) {
            name = skill.name;
            description = skill.description;
            modelInvocable = skill.modelInvocable;
            this.files = Collections.unmodifiableMap(files);
        }
    }

    /** What a link held: the skills found, by name, and a plain line for each one skipped. */
    public static final class Result {
        public final String source;
        public final List<Found> found;
        public final List<String> skipped;

        Result(String source, List<Found> found, List<String> skipped) {
            this.source = source;
            this.found = Collections.unmodifiableList(found);
            this.skipped = Collections.unmodifiableList(skipped);
        }
    }

    public interface Progress {
        void at(int done, int total);
    }

    private static final class Blob {
        final String sha;
        final long size;

        Blob(String sha, long size) {
            this.sha = sha;
            this.size = size;
        }
    }

    private final Endpoints endpoints;

    public GitHubSkills(Endpoints endpoints) {
        this.endpoints = endpoints;
    }

    public Result find(String text, Progress progress) throws IOException {
        return find(text, progress, null);
    }

    /**
     * Like {@link #find(String, Progress)}, but when a skill folder is named {@code name}, finds
     * only the skill in it.
     */
    public Result find(String text, Progress progress, String name) throws IOException {
        GitHubLink link = GitHubLink.parse(text);
        String commit = commit(link);
        Map<String, Blob> blobs = tree(link, commit);
        List<String> folders = new ArrayList<>();
        for (String path : blobs.keySet()) {
            String folder = parent(path);
            if (name(path).equals(SkillFile.NAME) && inside(folder, link)) folders.add(folder);
        }
        if (link.singleSkill && !folders.contains(link.folder))
            throw new IOException("There is no SKILL.md at that link.");
        List<String> wanted = link.singleSkill ? List.of(link.folder) : folders;
        for (String folder : folders)
            if (!link.singleSkill && name != null && name(folder).equals(name))
                wanted = List.of(folder);
        if (folders.isEmpty())
            throw new IOException(
                    "No skills were found at that link. A skill is a folder with a SKILL.md file.");
        if (wanted.size() > MAX_SKILLS)
            throw new IOException(
                    "That link holds "
                            + wanted.size()
                            + " skills; the builder adds at most "
                            + MAX_SKILLS
                            + " from one link. Link to a folder with fewer.");
        Map<String, List<String>> filesOf = new LinkedHashMap<>();
        long declared = 0;
        for (String folder : wanted) {
            List<String> files = files(folder, folders, blobs);
            filesOf.put(folder, files);
            for (String file : files) declared += Math.max(blobs.get(join(folder, file)).size, 0);
        }
        if (declared > MAX_LINK_BYTES) throw tooMuch();
        Map<String, Found> found = new TreeMap<>();
        List<String> skipped = new ArrayList<>();
        long downloaded = 0;
        int done = 0;
        for (String folder : wanted) {
            progress.at(done++, wanted.size());
            String label = folder.isEmpty() ? link.repo : folder;
            try {
                Found skill = download(link, commit, folder, filesOf.get(folder), blobs);
                for (byte[] bytes : skill.files.values()) downloaded += bytes.length;
                if (downloaded > MAX_LINK_BYTES) throw tooMuch();
                if (found.containsKey(skill.name))
                    skipped.add(label + ": another skill here is also named " + skill.name + ".");
                else found.put(skill.name, skill);
            } catch (Skip skip) {
                skipped.add(label + ": " + skip.getMessage());
            }
        }
        progress.at(done, wanted.size());
        return new Result(link.toString(), new ArrayList<>(found.values()), skipped);
    }

    private static IOException tooMuch() {
        return new IOException(
                "The skills at that link add up to more than 8 MB. Link to a folder with fewer"
                        + " skills.");
    }

    private static final class Skip extends IOException {
        Skip(String message) {
            super(message);
        }
    }

    private String commit(GitHubLink link) throws IOException {
        byte[] answer;
        try {
            answer =
                    HttpsDownload.bytes(
                            GITHUB,
                            link.commitUrl(endpoints.api),
                            "the repository",
                            MAX_COMMIT_BYTES,
                            "Accept",
                            "application/vnd.github.sha");
        } catch (HttpsDownload.NotFound | HttpsDownload.Refused refused) {
            if (refused instanceof HttpsDownload.Refused
                    && ((HttpsDownload.Refused) refused).code != 422)
                throw limited((HttpsDownload.Refused) refused);
            throw new IOException(
                    "GitHub has no public repository "
                            + link.owner
                            + "/"
                            + link.repo
                            + (link.ref == null ? "" : " with a branch or tag " + link.ref)
                            + ".");
        }
        String commit = new String(answer, StandardCharsets.US_ASCII).trim();
        if (!commit.matches("[0-9a-f]{40}"))
            throw new IOException("GitHub did not answer as expected. Try again later.");
        return commit;
    }

    private static IOException limited(HttpsDownload.Refused refused) {
        if (refused.code == 403 || refused.code == 429)
            return new IOException(
                    "GitHub is limiting how often this phone can ask it. Try again in an hour.");
        return refused;
    }

    private Map<String, Blob> tree(GitHubLink link, String commit) throws IOException {
        byte[] answer;
        try {
            answer =
                    HttpsDownload.bytes(
                            GITHUB,
                            link.treeUrl(endpoints.api, commit),
                            "the file list",
                            MAX_TREE_BYTES);
        } catch (HttpsDownload.NotFound missing) {
            throw new IOException("GitHub did not answer as expected. Try again later.");
        } catch (HttpsDownload.Refused refused) {
            throw limited(refused);
        }
        Map<String, Blob> blobs = new TreeMap<>();
        try {
            JsonObject root =
                    JsonParser.parseString(new String(answer, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            if (root.has("truncated") && root.get("truncated").getAsBoolean())
                throw new IOException(
                        "That repository holds too many files for GitHub to list at once, so"
                                + " the builder cannot add skills from it.");
            for (JsonElement element : root.getAsJsonArray("tree")) {
                JsonObject entry = element.getAsJsonObject();
                if (!"blob".equals(entry.get("type").getAsString())) continue;
                String sha = entry.get("sha").getAsString();
                if (!sha.matches("[0-9a-f]{40}")) continue;
                blobs.put(
                        entry.get("path").getAsString(),
                        new Blob(sha, entry.has("size") ? entry.get("size").getAsLong() : -1));
            }
        } catch (RuntimeException malformed) {
            throw new IOException("GitHub did not answer as expected. Try again later.");
        }
        return blobs;
    }

    /** The skill's Markdown files, relative to its folder, outside any skill folder inside it. */
    private static List<String> files(
            String folder, List<String> folders, Map<String, Blob> blobs) {
        List<String> files = new ArrayList<>();
        for (String path : blobs.keySet()) {
            if (!under(path, folder) || !path.toLowerCase(Locale.ROOT).endsWith(".md")) continue;
            boolean nested = false;
            for (String other : folders)
                if (!other.equals(folder) && under(other, folder) && under(path, other))
                    nested = true;
            if (!nested) files.add(folder.isEmpty() ? path : path.substring(folder.length() + 1));
        }
        return files;
    }

    private Found download(
            GitHubLink link,
            String commit,
            String folder,
            List<String> files,
            Map<String, Blob> blobs)
            throws IOException {
        if (!folder.isEmpty() && !GitHubLink.safePath(folder))
            throw new Skip("its folder name has characters the builder does not accept.");
        List<String> kept = new ArrayList<>();
        long total = 0;
        for (String file : files) {
            if (!GitHubLink.safePath(file) || file.split("/").length > MAX_DEPTH) continue;
            long size = blobs.get(join(folder, file)).size;
            if (size > SkillFile.MAX_FILE_BYTES) throw new Skip(file + " is larger than 64 KB.");
            total += Math.max(size, 0);
            kept.add(file);
        }
        if (total > MAX_SKILL_BYTES) throw new Skip("its files add up to more than 512 KB.");
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        byte[] skillFile = raw(link, commit, folder, SkillFile.NAME, blobs);
        SkillFile skill;
        try {
            skill = SkillFile.parse(skillFile);
        } catch (IOException refused) {
            throw new Skip(refused.getMessage());
        }
        bytes.put(SkillFile.NAME, skillFile);
        total = skillFile.length;
        for (String file : kept) {
            if (file.equals(SkillFile.NAME)) continue;
            byte[] content = raw(link, commit, folder, file, blobs);
            total += content.length;
            if (total > MAX_SKILL_BYTES) throw new Skip("its files add up to more than 512 KB.");
            bytes.put(file, content);
        }
        return new Found(skill, bytes);
    }

    private byte[] raw(
            GitHubLink link, String commit, String folder, String file, Map<String, Blob> blobs)
            throws IOException {
        String path = join(folder, file);
        byte[] content;
        try {
            content =
                    HttpsDownload.bytes(
                            GITHUB,
                            link.rawUrl(endpoints.raw, commit, path),
                            file,
                            SkillFile.MAX_FILE_BYTES);
        } catch (HttpsDownload.NotFound missing) {
            throw new Skip(file + " could not be downloaded.");
        } catch (HttpsDownload.Refused refused) {
            throw limited(refused);
        }
        if (!blobSha(content).equals(blobs.get(path).sha))
            throw new Skip(file + " did not match GitHub's list of files, so it was not used.");
        return content;
    }

    static String blobSha(byte[] content) {
        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
        sha1.update(("blob " + content.length + "\0").getBytes(StandardCharsets.US_ASCII));
        StringBuilder hex = new StringBuilder(40);
        for (byte value : sha1.digest(content))
            hex.append(Character.forDigit((value >> 4) & 15, 16))
                    .append(Character.forDigit(value & 15, 16));
        return hex.toString();
    }

    private static boolean inside(String folder, GitHubLink link) {
        return link.folder.isEmpty() || folder.equals(link.folder) || under(folder, link.folder);
    }

    private static boolean under(String path, String folder) {
        return folder.isEmpty() || path.startsWith(folder + "/");
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static String name(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String join(String folder, String file) {
        return folder.isEmpty() ? file : folder + "/" + file;
    }
}
