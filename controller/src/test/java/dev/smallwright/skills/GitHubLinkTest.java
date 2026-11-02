package dev.smallwright.skills;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.Test;

public final class GitHubLinkTest {
    private static final String API = "https://api.github.com/";
    private static final String RAW = "https://raw.githubusercontent.com/";
    private static final String COMMIT = "49dd158d1076134a641b33efb035946536778336";

    @Test
    public void aRepositoryUsesItsDefaultBranch() throws Exception {
        for (String link :
                new String[] {
                    "https://github.com/mattpocock/skills",
                    "  https://github.com/mattpocock/skills/  ",
                    "https://www.github.com/mattpocock/skills.git",
                    "github.com/mattpocock/skills?tab=readme-ov-file#readme",
                }) {
            GitHubLink parsed = GitHubLink.parse(link);
            assertEquals("mattpocock", parsed.owner);
            assertEquals("skills", parsed.repo);
            assertNull(parsed.ref);
            assertEquals("", parsed.folder);
            assertFalse(parsed.singleSkill);
            assertEquals(API + "repos/mattpocock/skills/commits/HEAD", parsed.commitUrl(API));
            assertEquals("https://github.com/mattpocock/skills", parsed.toString());
        }
    }

    @Test
    public void aFolderLinkNamesItsBranchAndFolder() throws Exception {
        GitHubLink parsed =
                GitHubLink.parse(
                        "https://github.com/mattpocock/skills/tree/main/skills/productivity");
        assertEquals("main", parsed.ref);
        assertEquals("skills/productivity", parsed.folder);
        assertFalse(parsed.singleSkill);
        assertEquals(API + "repos/mattpocock/skills/commits/main", parsed.commitUrl(API));
        assertEquals(
                API + "repos/mattpocock/skills/git/trees/" + COMMIT + "?recursive=1",
                parsed.treeUrl(API, COMMIT));
        assertEquals(
                RAW + "mattpocock/skills/" + COMMIT + "/skills/productivity/grill-me/SKILL.md",
                parsed.rawUrl(RAW, COMMIT, "skills/productivity/grill-me/SKILL.md"));
        assertEquals(
                "https://github.com/mattpocock/skills/tree/main/skills/productivity",
                parsed.toString());
        assertEquals("", GitHubLink.parse("https://github.com/o/r/tree/v1.2").folder);
    }

    @Test
    public void aSkillFileLinkMeansThatOneSkill() throws Exception {
        GitHubLink parsed =
                GitHubLink.parse(
                        "https://github.com/mattpocock/skills/blob/main/skills/productivity/grill-me/SKILL.md");
        assertTrue(parsed.singleSkill);
        assertEquals("skills/productivity/grill-me", parsed.folder);
        assertEquals("main", parsed.ref);
        assertTrue(GitHubLink.parse("https://github.com/o/r/blob/main/SKILL.md").singleSkill);
        assertEquals("", GitHubLink.parse("https://github.com/o/r/blob/main/SKILL.md").folder);
    }

    @Test
    public void refusesLinksThatAreNotGitHubSkillLinks() {
        for (String link :
                new String[] {
                    "",
                    "https://gitlab.com/o/r",
                    "https://github.com.evil.example/o/r",
                    "https://raw.githubusercontent.com/o/r/main/SKILL.md",
                    "http://github.com/o/r",
                    "ftp://github.com/o/r",
                    "https://github.com/o",
                    "https://github.com/o/r/issues/1",
                    "https://github.com/o/r/blob/main/README.md",
                    "https://github.com/o/r/tree/main/../secrets",
                    "https://github.com/o/r/tree/main/a%2F..%2Fb",
                    "https://github.com/o/r/tree/..",
                    "https://user:pass@github.com/o/r",
                    "https://github.com:8443/o/r",
                    "https://github.com/o/r/tree/main/a b",
                }) {
            IOException refused =
                    assertThrows(link, IOException.class, () -> GitHubLink.parse(link));
            assertFalse(refused.getMessage().isEmpty());
        }
    }
}
