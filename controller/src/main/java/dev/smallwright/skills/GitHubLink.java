package dev.smallwright.skills;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A GitHub link to skills: a repository, a {@code tree/<ref>/<folder>} folder or one {@code
 * blob/<ref>/<folder>/SKILL.md}, turned into GitHub API and raw file addresses. A ref is one path
 * segment, so a branch whose name has a slash is read as that branch's first part.
 */
public final class GitHubLink {
    private static final Pattern OWNER = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})");
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final String NOT_A_LINK =
            "Paste a GitHub link to a repository, a folder or a SKILL.md file, such as"
                    + " https://github.com/mattpocock/skills.";

    public final String owner;
    public final String repo;

    /** The branch, tag or commit, or null for the repository's default branch. */
    public final String ref;

    /** The folder the link points at, without slashes at either end; empty for the whole repo. */
    public final String folder;

    /** The link points at one SKILL.md, so it means the skill in {@link #folder} only. */
    public final boolean singleSkill;

    private GitHubLink(String owner, String repo, String ref, String folder, boolean singleSkill) {
        this.owner = owner;
        this.repo = repo;
        this.ref = ref;
        this.folder = folder;
        this.singleSkill = singleSkill;
    }

    public static GitHubLink parse(String link) throws IOException {
        String text = link == null ? "" : link.trim();
        if (text.isEmpty()) throw new IOException(NOT_A_LINK);
        if (!text.contains("://")) text = "https://" + text;
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException malformed) {
            throw new IOException(NOT_A_LINK);
        }
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme())
                || host == null
                || !(host.equals("github.com") || host.equals("www.github.com"))
                || uri.getRawUserInfo() != null
                || uri.getPort() != -1
                || uri.getRawPath() == null
                || uri.getRawPath().contains("%")) throw new IOException(NOT_A_LINK);
        List<String> segments = new ArrayList<>();
        for (String segment : uri.getRawPath().split("/"))
            if (!segment.isEmpty()) segments.add(segment);
        if (segments.size() < 2) throw new IOException(NOT_A_LINK);
        String owner = segments.get(0);
        String repo = segments.get(1);
        if (repo.endsWith(".git")) repo = repo.substring(0, repo.length() - 4);
        if (!OWNER.matcher(owner).matches() || !safe(repo)) throw new IOException(NOT_A_LINK);
        if (segments.size() == 2) return new GitHubLink(owner, repo, null, "", false);
        String kind = segments.get(2);
        if (segments.size() < 4 || !(kind.equals("tree") || kind.equals("blob")))
            throw new IOException(NOT_A_LINK);
        List<String> rest = segments.subList(3, segments.size());
        for (String segment : rest)
            if (!safe(segment))
                throw new IOException(
                        "That link has a folder name the builder does not accept. Folder names"
                                + " may hold letters, digits, dots, hyphens and underscores.");
        String ref = rest.get(0);
        List<String> path = rest.subList(1, rest.size());
        if (kind.equals("blob")) {
            if (path.isEmpty() || !path.get(path.size() - 1).equals(SkillFile.NAME))
                throw new IOException(NOT_A_LINK);
            return new GitHubLink(
                    owner, repo, ref, String.join("/", path.subList(0, path.size() - 1)), true);
        }
        return new GitHubLink(owner, repo, ref, String.join("/", path), false);
    }

    /** Whether a path segment may be used in a link and on the phone. */
    static boolean safe(String segment) {
        return SEGMENT.matcher(segment).matches() && !segment.equals(".") && !segment.equals("..");
    }

    /** Whether every segment of a repository path is {@link #safe}. */
    static boolean safePath(String path) {
        return !path.isEmpty() && Arrays.stream(path.split("/", -1)).allMatch(GitHubLink::safe);
    }

    /** Asks for the commit the ref names, answered as bare text with the {@code sha} media type. */
    public String commitUrl(String apiBase) {
        return apiBase + "repos/" + owner + "/" + repo + "/commits/" + (ref == null ? "HEAD" : ref);
    }

    public String treeUrl(String apiBase, String commit) {
        return apiBase + "repos/" + owner + "/" + repo + "/git/trees/" + commit + "?recursive=1";
    }

    public String rawUrl(String rawBase, String commit, String path) {
        return rawBase + owner + "/" + repo + "/" + commit + "/" + path;
    }

    @Override
    public String toString() {
        String base = "https://github.com/" + owner + "/" + repo;
        if (ref == null) return base;
        if (singleSkill)
            return base
                    + "/blob/"
                    + ref
                    + "/"
                    + (folder.isEmpty() ? "" : folder + "/")
                    + SkillFile.NAME;
        return base + "/tree/" + ref + (folder.isEmpty() ? "" : "/" + folder);
    }
}
