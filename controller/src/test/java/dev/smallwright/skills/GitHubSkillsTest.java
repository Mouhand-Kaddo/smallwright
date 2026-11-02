package dev.smallwright.skills;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.smallwright.libraries.MavenFixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

/** Skills found through a local stand-in for GitHub's API and raw file host. */
public final class GitHubSkillsTest {
    private static final String COMMIT = "49dd158d1076134a641b33efb035946536778336";

    private final MavenFixture github;
    private final GitHubSkills skills;
    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private final Map<String, Long> declaredSizes = new LinkedHashMap<>();
    private final Map<String, String> wrongShas = new LinkedHashMap<>();
    private boolean truncated;

    public GitHubSkillsTest() throws IOException {
        github = new MavenFixture();
        skills =
                new GitHubSkills(
                        new GitHubSkills.Endpoints(github.base("api"), github.base("raw")));
    }

    @After
    public void close() {
        github.close();
    }

    private static String skill(String name, String description) {
        return "---\nname: " + name + "\ndescription: " + description + "\n---\n\nDo the thing.\n";
    }

    private void file(String path, String text) {
        files.put(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private void publish(String ref) throws Exception {
        github.putRaw(
                "api",
                "repos/owner/repo/commits/" + ref,
                COMMIT.getBytes(StandardCharsets.US_ASCII));
        JsonArray tree = new JsonArray();
        List<String> folders = new ArrayList<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String path = file.getKey();
            for (int slash = path.indexOf('/'); slash > 0; slash = path.indexOf('/', slash + 1))
                if (!folders.contains(path.substring(0, slash))) {
                    folders.add(path.substring(0, slash));
                    JsonObject folder = new JsonObject();
                    folder.addProperty("path", path.substring(0, slash));
                    folder.addProperty("type", "tree");
                    folder.addProperty("sha", "0".repeat(40));
                    tree.add(folder);
                }
            JsonObject blob = new JsonObject();
            blob.addProperty("path", path);
            blob.addProperty("type", "blob");
            blob.addProperty("sha", wrongShas.getOrDefault(path, blobSha(file.getValue())));
            blob.addProperty(
                    "size", declaredSizes.getOrDefault(path, (long) file.getValue().length));
            tree.add(blob);
            github.putRaw("raw", "owner/repo/" + COMMIT + "/" + path, file.getValue());
        }
        JsonObject root = new JsonObject();
        root.addProperty("sha", COMMIT);
        root.add("tree", tree);
        root.addProperty("truncated", truncated);
        github.putRaw(
                "api",
                "repos/owner/repo/git/trees/" + COMMIT + "?recursive=1",
                root.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String blobSha(byte[] content) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(("blob " + content.length + "\0").getBytes(StandardCharsets.US_ASCII));
        StringBuilder hex = new StringBuilder();
        for (byte value : sha1.digest(content)) hex.append(String.format("%02x", value));
        return hex.toString();
    }

    private List<String> rawRequests() {
        List<String> raw = new ArrayList<>();
        for (String request : github.requests())
            if (request.startsWith("/raw/"))
                raw.add(request.substring(("/raw/owner/repo/" + COMMIT + "/").length()));
        return raw;
    }

    private static List<String> names(GitHubSkills.Result result) {
        List<String> names = new ArrayList<>();
        for (GitHubSkills.Found found : result.found) names.add(found.name);
        return names;
    }

    @Test
    public void findsEverySkillWithItsMarkdownFilesAndNothingElse() throws Exception {
        file("README.md", "# Not a skill");
        file("skills/tdd/SKILL.md", skill("tdd", "Test first."));
        file("skills/tdd/tests.md", "Good tests.");
        file("skills/tdd/references/MOCKING.MD", "Mock little.");
        file("skills/tdd/scripts/run.sh", "rm -rf /");
        file("skills/tdd/agents/openai.yaml", "x: 1");
        file("skills/tdd/nested/SKILL.md", skill("nested", "Its own skill."));
        file("skills/tdd/nested/inner.md", "Belongs to nested.");
        file(
                "skills/grill-me/SKILL.md",
                skill("grill-me", "Interview.")
                        .replace("---\n\n", "disable-model-invocation: true\n---\n\n"));
        publish("HEAD");

        GitHubSkills.Result result =
                skills.find("https://github.com/owner/repo", (done, total) -> {});

        assertEquals(List.of("grill-me", "nested", "tdd"), names(result));
        assertTrue(result.skipped.isEmpty());
        GitHubSkills.Found tdd = result.found.get(2);
        assertEquals("Test first.", tdd.description);
        assertTrue(tdd.modelInvocable);
        assertFalse(result.found.get(0).modelInvocable);
        assertEquals(
                List.of("SKILL.md", "references/MOCKING.MD", "tests.md"),
                new ArrayList<>(tdd.files.keySet()));
        assertEquals("Good tests.", new String(tdd.files.get("tests.md"), StandardCharsets.UTF_8));
        assertEquals(
                List.of("SKILL.md", "inner.md"),
                new ArrayList<>(result.found.get(1).files.keySet()));
        assertEquals("https://github.com/owner/repo", result.source);
        for (String requested : rawRequests())
            assertTrue(requested, requested.toLowerCase().endsWith(".md"));
        assertFalse(rawRequests().contains("README.md"));
    }

    @Test
    public void aFolderOrSkillFileLinkKeepsToIt() throws Exception {
        file("skills/a/one/SKILL.md", skill("one", "One."));
        file("skills/b/two/SKILL.md", skill("two", "Two."));
        file("skills/b/three/SKILL.md", skill("three", "Three."));
        file("skills/b/two/guide.md", "Two's guide.");
        file("skills/b/two/inner/SKILL.md", skill("inner", "Inside two."));
        file("skills/b/two/inner/notes.md", "Inner's notes.");
        publish("main");

        assertEquals(
                List.of("inner", "three", "two"),
                names(
                        skills.find(
                                "https://github.com/owner/repo/tree/main/skills/b", (d, t) -> {})));
        GitHubSkills.Result single =
                skills.find(
                        "https://github.com/owner/repo/blob/main/skills/b/two/SKILL.md",
                        (d, t) -> {});
        assertEquals(List.of("two"), names(single));
        assertEquals(
                "a skill folder inside it is its own skill, not part of this one",
                List.of("SKILL.md", "guide.md"),
                new ArrayList<>(single.found.get(0).files.keySet()));
        assertTrue(
                assertThrows(
                                IOException.class,
                                () ->
                                        skills.find(
                                                "https://github.com/owner/repo/blob/main/skills/b/SKILL.md",
                                                (d, t) -> {}))
                        .getMessage()
                        .contains("no SKILL.md"));
    }

    @Test
    public void skipsSkillsOutsideTheLimitsOrThatDoNotParseWithAPlainReason() throws Exception {
        file("bad/SKILL.md", "no frontmatter");
        file("big-file/SKILL.md", skill("big-file", "Big."));
        file("big-file/huge.md", "x");
        declaredSizes.put("big-file/huge.md", 64L * 1024 + 1);
        file("big-skill/SKILL.md", skill("big-skill", "Big."));
        for (int part = 0; part < 9; part++) {
            file("big-skill/part" + part + ".md", "x");
            declaredSizes.put("big-skill/part" + part + ".md", 60L * 1024);
        }
        file("first/SKILL.md", skill("same", "First."));
        file("second/SKILL.md", skill("same", "Second."));
        file("changed/SKILL.md", skill("changed", "Changed."));
        wrongShas.put("changed/SKILL.md", "1".repeat(40));
        file("good/SKILL.md", skill("good", "Good."));
        publish("HEAD");

        GitHubSkills.Result result = skills.find("github.com/owner/repo", (done, total) -> {});

        assertEquals(List.of("good", "same"), names(result));
        assertEquals("First.", result.found.get(1).description);
        String skipped = String.join("\n", result.skipped);
        assertTrue(skipped, skipped.contains("bad: SKILL.md does not start with a --- line"));
        assertTrue(skipped, skipped.contains("big-file: huge.md is larger than 64 KB"));
        assertTrue(skipped, skipped.contains("big-skill: its files add up to more than 512 KB"));
        assertTrue(skipped, skipped.contains("second: another skill here is also named same"));
        assertTrue(skipped, skipped.contains("changed: SKILL.md did not match GitHub's list"));
        assertFalse(rawRequests().contains("big-file/huge.md"));
        assertFalse(rawRequests().contains("big-skill/SKILL.md"));
    }

    @Test
    public void refusesALinkWithMoreThanAHundredSkillsOrNone() throws Exception {
        for (int index = 0; index <= GitHubSkills.MAX_SKILLS; index++)
            file("s" + index + "/SKILL.md", skill("s" + index, "Skill."));
        publish("HEAD");
        String tooMany =
                assertThrows(
                                IOException.class,
                                () -> skills.find("github.com/owner/repo", (d, t) -> {}))
                        .getMessage();
        assertTrue(tooMany, tooMany.contains("101 skills") && tooMany.contains("at most 100"));
        assertTrue(rawRequests().isEmpty());

        files.clear();
        file("README.md", "# Nothing");
        publish("HEAD");
        assertTrue(
                assertThrows(
                                IOException.class,
                                () -> skills.find("github.com/owner/repo", (d, t) -> {}))
                        .getMessage()
                        .contains("No skills"));
    }

    @Test
    public void explainsGitHubRefusalsPlainly() throws Exception {
        String missing =
                assertThrows(
                                IOException.class,
                                () -> skills.find("github.com/owner/none", (d, t) -> {}))
                        .getMessage();
        assertTrue(missing, missing.contains("no public repository owner/none"));
        github.status("api", "repos/owner/limited/commits/HEAD", 403);
        assertTrue(
                assertThrows(
                                IOException.class,
                                () -> skills.find("github.com/owner/limited", (d, t) -> {}))
                        .getMessage()
                        .contains("Try again in an hour"));
        github.redirect("api", "repos/owner/moved/commits/HEAD", "https://example.com/");
        assertTrue(
                assertThrows(
                                IOException.class,
                                () -> skills.find("github.com/owner/moved", (d, t) -> {}))
                        .getMessage()
                        .contains("redirect"));
        github.putRaw(
                "api",
                "repos/owner/odd/commits/HEAD",
                "<html>".getBytes(StandardCharsets.US_ASCII));
        assertTrue(
                assertThrows(
                                IOException.class,
                                () -> skills.find("github.com/owner/odd", (d, t) -> {}))
                        .getMessage()
                        .contains("did not answer as expected"));
        file("a/SKILL.md", skill("a", "A."));
        truncated = true;
        publish("HEAD");
        assertTrue(
                assertThrows(
                                IOException.class,
                                () -> skills.find("github.com/owner/repo", (d, t) -> {}))
                        .getMessage()
                        .contains("too many files"));
    }

    @Test
    public void refusesALinkWhoseSkillsAddUpToMoreThan8MbBeforeDownloading() throws Exception {
        for (int index = 0; index < 20; index++) {
            file("s" + index + "/SKILL.md", skill("s" + index, "Skill."));
            for (int part = 0; part < 7; part++) {
                file("s" + index + "/part" + part + ".md", "x");
                declaredSizes.put("s" + index + "/part" + part + ".md", 60L * 1024);
            }
        }
        publish("HEAD");
        String refused =
                assertThrows(
                                IOException.class,
                                () -> skills.find("github.com/owner/repo", (d, t) -> {}))
                        .getMessage();
        assertTrue(refused, refused.contains("more than 8 MB"));
        assertTrue(rawRequests().isEmpty());
    }

    @Test
    public void refusesAFileListOver2Mb() throws Exception {
        github.putRaw(
                "api", "repos/owner/huge/commits/HEAD", COMMIT.getBytes(StandardCharsets.US_ASCII));
        github.putRaw(
                "api",
                "repos/owner/huge/git/trees/" + COMMIT + "?recursive=1",
                new byte[2 * 1024 * 1024 + 1]);
        String refused =
                assertThrows(
                                IOException.class,
                                () -> skills.find("github.com/owner/huge", (d, t) -> {}))
                        .getMessage();
        assertTrue(refused, refused.contains("larger than the builder accepts"));
    }
}
