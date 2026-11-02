package dev.smallwright.skills;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;

public final class SkillFileTest {
    private static SkillFile parse(String text) throws IOException {
        return SkillFile.parse(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String refusal(String text) {
        return assertThrows(IOException.class, () -> parse(text)).getMessage();
    }

    @Test
    public void readsNameDescriptionAndBody() throws Exception {
        SkillFile skill =
                parse(
                        "---\nname: grilling\ndescription: Grill the user about a plan.\n---\n\n"
                                + "Interview the user.\n");
        assertEquals("grilling", skill.name);
        assertEquals("Grill the user about a plan.", skill.description);
        assertTrue(skill.modelInvocable);
    }

    @Test
    public void honoursDisableModelInvocation() throws Exception {
        SkillFile skill =
                parse(
                        "---\nname: grill-me\ndescription: An interview.\n"
                                + "disable-model-invocation: true\n---\nCall grilling.\n");
        assertFalse(skill.modelInvocable);
    }

    @Test
    public void acceptsQuotedFoldedAndColonValuesAndIgnoresOtherFields() throws Exception {
        assertEquals(
                "Use when: the user asks about PDFs",
                parse("---\nname: pdf\ndescription: Use when: the user asks about PDFs\n---\n")
                        .description);
        assertEquals(
                "Say \"hi\" and #1",
                parse("---\nname: \"hi\"\ndescription: \"Say \\\"hi\\\" and #1\"\n---\n")
                        .description);
        assertEquals(
                "It's done",
                parse("---\nname: 'done'\ndescription: 'It''s done'\n---\n").description);
        assertEquals(
                "First line second line",
                parse("---\nname: fold\ndescription: >\n  First line\n  second line\n---\n")
                        .description);
        assertEquals(
                "Plain continued here",
                parse("---\nname: plain\ndescription: Plain\n  continued here\n---\n").description);
        SkillFile withMetadata =
                parse(
                        (char) 0xFEFF
                                + "---\r\nname: meta # a comment\r\nlicense: MIT\r\nmetadata:\r\n"
                                + "  author: someone\r\n  version: \"1.0\"\r\n"
                                + "description: With metadata\r\n---\r\nBody\r\n");
        assertEquals("meta", withMetadata.name);
        assertEquals("With metadata", withMetadata.description);
    }

    @Test
    public void refusesMalformedFrontmatter() {
        assertTrue(refusal("# Just markdown\n").contains("---"));
        assertTrue(refusal("---\nname: open\ndescription: never closed\n").contains("closing"));
        assertTrue(
                refusal("---\nname: x\n- a list item\ndescription: d\n---\n").contains("line 3"));
        assertTrue(refusal("---\n  name: indented\n---\n").contains("line 2"));
    }

    @Test
    public void refusesMissingFields() {
        assertTrue(refusal("---\ndescription: No name\n---\n").contains("no name"));
        assertTrue(refusal("---\nname: no-description\n---\n").contains("no description"));
        assertTrue(
                refusal("---\nname: blank\ndescription: \"  \"\n---\n").contains("no description"));
    }

    @Test
    public void validatesNamesAsTheFormatRequires() {
        for (String bad :
                new String[] {
                    "PDF-Processing",
                    "-pdf",
                    "pdf-",
                    "pdf--processing",
                    "pdf_processing",
                    "pdf processing",
                    "a".repeat(65),
                    "../etc"
                }) {
            assertFalse(bad, SkillFile.validName(bad));
            assertTrue(
                    bad,
                    refusal("---\nname: " + bad + "\ndescription: d\n---\n")
                            .contains("is not a skill name"));
        }
        assertTrue(SkillFile.validName("a".repeat(64)));
        assertTrue(SkillFile.validName("data-analysis-2"));
    }

    @Test
    public void refusesOverLimitFiles() {
        assertTrue(
                refusal("---\nname: long\ndescription: " + "d".repeat(1025) + "\n---\n")
                        .contains("1024"));
        byte[] large = new byte[SkillFile.MAX_FILE_BYTES + 1];
        Arrays.fill(large, (byte) 'a');
        assertTrue(
                assertThrows(IOException.class, () -> SkillFile.parse(large))
                        .getMessage()
                        .contains("64 KB"));
        byte[] binary = {'-', '-', '-', '\n', (byte) 0xC3, (byte) 0x28, '\n'};
        assertTrue(
                assertThrows(IOException.class, () -> SkillFile.parse(binary))
                        .getMessage()
                        .contains("text"));
    }
}
