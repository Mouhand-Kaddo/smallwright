package dev.smallwright.skills;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A skill's {@code SKILL.md}, read as the Agent Skills format describes it: YAML frontmatter
 * between {@code ---} lines with a required {@code name} and {@code description}, then Markdown.
 * Only top-level scalar fields are read; values with an unquoted colon are accepted, as other skill
 * clients accept them.
 */
public final class SkillFile {
    public static final String NAME = "SKILL.md";
    public static final int MAX_FILE_BYTES = 64 * 1024;
    public static final int MAX_NAME_CHARS = 64;
    public static final int MAX_DESCRIPTION_CHARS = 1024;

    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern FIELD = Pattern.compile("([A-Za-z0-9_.-]+)[ \\t]*:(.*)");
    private static final Pattern BLOCK = Pattern.compile("([|>])[-+0-9]*(?:\\s+#.*)?");

    public final String name;
    public final String description;

    /** False when the skill says {@code disable-model-invocation: true}: only /name starts it. */
    public final boolean modelInvocable;

    private SkillFile(String name, String description, boolean modelInvocable) {
        this.name = name;
        this.description = description;
        this.modelInvocable = modelInvocable;
    }

    public static boolean validName(String name) {
        return name != null
                && name.length() <= MAX_NAME_CHARS
                && VALID_NAME.matcher(name).matches();
    }

    /** The bytes as UTF-8 text, or null when they are not. */
    public static String text(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException binary) {
            return null;
        }
    }

    /** Parses and checks a {@code SKILL.md}; the message of a refusal is plain. */
    public static SkillFile parse(byte[] bytes) throws IOException {
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("SKILL.md is larger than 64 KB.");
        String text = text(bytes);
        if (text == null) throw new IOException("SKILL.md is not plain text.");
        if (!text.isEmpty() && text.charAt(0) == 0xFEFF) text = text.substring(1);
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        if (!lines[0].trim().equals("---"))
            throw new IOException(
                    "SKILL.md does not start with a --- line followed by its name and"
                            + " description.");
        int end = 1;
        while (end < lines.length && !lines[end].trim().equals("---")) end++;
        if (end == lines.length)
            throw new IOException(
                    "SKILL.md has no closing --- line after its name and description.");
        Map<String, String> fields = fields(lines, end);
        String name = fields.get("name");
        if (name == null || name.isEmpty()) throw new IOException("SKILL.md has no name.");
        if (!validName(name))
            throw new IOException(
                    "\""
                            + (name.length() > 80 ? name.substring(0, 80) + "…" : name)
                            + "\" is not a skill name: a name has only lowercase letters, digits"
                            + " and single hyphens inside, at most 64 characters.");
        String description = fields.get("description");
        if (description == null || description.isEmpty())
            throw new IOException("SKILL.md has no description.");
        if (description.length() > MAX_DESCRIPTION_CHARS)
            throw new IOException(
                    "The description of " + name + " is longer than 1024 characters.");
        String invocation = fields.get("disable-model-invocation");
        return new SkillFile(
                name,
                description,
                !"true".equals(invocation == null ? "" : invocation.toLowerCase(Locale.ROOT)));
    }

    private static Map<String, String> fields(String[] lines, int end) throws IOException {
        Map<String, String> fields = new HashMap<>();
        String key = null;
        String first = null;
        List<String> more = new ArrayList<>();
        for (int index = 1; index < end; index++) {
            String line = lines[index];
            if (line.trim().isEmpty()) {
                if (key != null) more.add("");
                continue;
            }
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (key == null) throw unreadable(index);
                more.add(line);
                continue;
            }
            if (line.startsWith("#")) continue;
            Matcher field = FIELD.matcher(line);
            if (!field.matches()) throw unreadable(index);
            if (key != null) fields.put(key, scalar(first, more));
            key = field.group(1);
            first = field.group(2).trim();
            more = new ArrayList<>();
        }
        if (key != null) fields.put(key, scalar(first, more));
        return fields;
    }

    private static IOException unreadable(int index) {
        return new IOException(
                "The frontmatter of SKILL.md could not be read at line " + (index + 1) + ".");
    }

    private static String scalar(String first, List<String> more) {
        while (!more.isEmpty() && more.get(more.size() - 1).isEmpty()) more.remove(more.size() - 1);
        Matcher block = BLOCK.matcher(first);
        if (block.matches()) {
            StringBuilder value = new StringBuilder();
            boolean literal = block.group(1).equals("|");
            for (String line : more) {
                String part = line.trim();
                if (value.length() > 0)
                    value.append(literal || part.isEmpty() || endsWithNewline(value) ? "\n" : " ");
                value.append(part);
            }
            return value.toString().trim();
        }
        StringBuilder joined = new StringBuilder(first);
        for (String line : more) {
            if (line.isEmpty()) continue;
            if (joined.length() > 0) joined.append(' ');
            joined.append(line.trim());
        }
        String value = joined.toString();
        if (value.startsWith("\"")) return doubleQuoted(value);
        if (value.startsWith("'")) return singleQuoted(value);
        int comment = value.indexOf(" #");
        return (comment < 0 ? value : value.substring(0, comment)).trim();
    }

    private static boolean endsWithNewline(StringBuilder value) {
        return value.charAt(value.length() - 1) == '\n';
    }

    private static String doubleQuoted(String value) {
        StringBuilder text = new StringBuilder();
        for (int index = 1; index < value.length(); index++) {
            char next = value.charAt(index);
            if (next == '"') break;
            if (next == '\\' && index + 1 < value.length()) {
                char escaped = value.charAt(++index);
                text.append(escaped == 'n' ? '\n' : escaped == 't' ? '\t' : escaped);
            } else text.append(next);
        }
        return text.toString().trim();
    }

    private static String singleQuoted(String value) {
        StringBuilder text = new StringBuilder();
        for (int index = 1; index < value.length(); index++) {
            char next = value.charAt(index);
            if (next == '\'') {
                if (index + 1 < value.length() && value.charAt(index + 1) == '\'') {
                    text.append('\'');
                    index++;
                    continue;
                }
                break;
            }
            text.append(next);
        }
        return text.toString().trim();
    }
}
