package dev.smallwright.controller;

import dev.smallwright.security.ContextFilter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * A project's brief: the person's decisions about the app, which the builder writes with
 * write_brief after the interview and reads at the start of every run. It is saved beside the
 * project's other data, outside its source revisions, so it is never built into the app and undo
 * does not change it.
 */
final class ProjectBrief {
    static final String FILE = "builder-brief.txt";

    private final File projectDirectory;

    ProjectBrief(File projectDirectory) {
        this.projectDirectory = projectDirectory;
    }

    /** The saved brief, or empty when there is none or the saved one no longer passes. */
    String read() throws IOException {
        Path saved = new File(projectDirectory, FILE).toPath();
        byte[] bytes;
        try {
            if (Files.isSymbolicLink(saved) || Files.size(saved) > ContextFilter.MAX_BRIEF_BYTES)
                return "";
            bytes = Files.readAllBytes(saved);
        } catch (NoSuchFileException none) {
            return "";
        }
        String text = StepRun.text(bytes);
        return text == null || refusal(text) != null ? "" : text;
    }

    /** Replaces the brief with {@code text}, trimmed; refused unless {@link #refusal} is null. */
    void write(String text) throws IOException {
        String why = refusal(text);
        if (why != null) throw new IllegalArgumentException(why);
        Path destination = new File(projectDirectory, FILE).toPath();
        Path partial = new File(projectDirectory, FILE + ".partial").toPath();
        if (Files.isSymbolicLink(destination) || Files.isSymbolicLink(partial))
            throw new IOException("Project brief path is a link");
        try {
            Files.write(partial, cleaned(text).getBytes(StandardCharsets.UTF_8));
            Files.move(
                    partial,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    /** Why {@code text} cannot be the brief, in words for the model, or null when it can. */
    static String refusal(String text) {
        String brief = text == null ? "" : cleaned(text);
        if (brief.isEmpty()) return "Not saved: the brief is empty.";
        int bytes = brief.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > ContextFilter.MAX_BRIEF_BYTES)
            return "Not saved: the brief is "
                    + bytes
                    + " bytes, and it may hold at most "
                    + ContextFilter.MAX_BRIEF_BYTES
                    + ". Write it shorter.";
        for (int index = 0; index < brief.length(); index++) {
            char character = brief.charAt(index);
            if (Character.isISOControl(character) && character != '\n' && character != '\t')
                return "Not saved: the brief may hold only text, line breaks and tabs.";
        }
        return null;
    }

    private static String cleaned(String text) {
        return text.replace("\r\n", "\n").strip();
    }
}
