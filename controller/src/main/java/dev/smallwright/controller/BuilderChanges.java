package dev.smallwright.controller;

import dev.smallwright.project.ChatStore;
import java.io.File;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The project's history of runs that changed its files, and of undoing them. Undo commits a new
 * revision with the files from before the latest change; every revision is kept. Revisions are
 * matched by their files, so rebuilding the same files does not hide a change from Undo.
 */
final class BuilderChanges {
    /** One line of the history. */
    sealed interface Entry permits Change, Undo {}

    /**
     * A run that changed the files from revision {@code before} to {@code after}; {@code messageId}
     * is null when the run's summary could not be saved in the chat.
     */
    record Change(
            int before,
            int after,
            String digest,
            String chatId,
            String messageId,
            long atMillis,
            String request)
            implements Entry {}

    /** An undo of the change that ended at {@code undid}, committed as {@code revision}. */
    record Undo(int revision, String digest, int undid) implements Entry {}

    /** The change Undo would undo now; {@code newest} when no later run or undo followed it. */
    record Undoable(Change change, boolean newest) {}

    /** {@code noted} is false when the chat was too full for the note. */
    record Undone(int revision, Change change, boolean noted, boolean artifactKept) {}

    /** Why nothing was undone, in plain words. */
    static final class Refused extends Exception {
        Refused(String message) {
            super(message);
        }
    }

    private static final String FILE = "builder-changes.txt";
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_FILE_BYTES = 64 * 1024;
    private static final int MAX_REQUEST_CHARS = 200;
    private static final int QUOTED_CHARS = 80;
    private static final Object LOCK = new Object();

    private final ProjectSourceStore source;
    private final ChatStore chats;
    private final Path file;

    BuilderChanges(ProjectSourceStore source, ChatStore chats) {
        this.source = source;
        this.chats = chats;
        this.file = new File(source.directory(), FILE).toPath();
    }

    /** Why Undo cannot run now, or null when it can. */
    static String refusal(boolean running, boolean unsavedEdits, Undoable undoable) {
        if (running) return "Wait for the builder to finish, or stop it, then undo.";
        if (unsavedEdits) return "Save your source edits with Build first, then undo.";
        if (undoable == null) return "There is no change from the builder to undo.";
        return null;
    }

    /** Remembers a run that ended at {@code after}, having started at {@code before}. */
    void recordRun(
            int before, int after, String chatId, String messageId, String request, long atMillis)
            throws IOException {
        if (before == after) return;
        Change change =
                new Change(
                        before,
                        after,
                        digest(source.readRevision(after)),
                        chatId,
                        messageId,
                        atMillis,
                        oneLine(request, MAX_REQUEST_CHARS));
        synchronized (LOCK) {
            List<Entry> entries = read();
            entries.add(change);
            write(entries);
        }
    }

    /** The change Undo would undo at revision {@code head}, or null when there is none. */
    Undoable undoable(int head) throws IOException {
        String current = digest(source.readRecoverySources(head));
        synchronized (LOCK) {
            List<Entry> entries = read();
            int index = entries.size() - 1;
            while (index >= 0) {
                Entry entry = entries.get(index);
                if (entry instanceof Undo undo && undo.digest.equals(current)) {
                    index = indexOfRun(entries, undo.undid, index) - 1;
                } else if (entry instanceof Change change && change.digest.equals(current)) {
                    return new Undoable(change, index == entries.size() - 1);
                } else {
                    index--;
                }
            }
            return null;
        }
    }

    /**
     * Undoes the latest change at revision {@code expectedRevision}, which must be {@code shown}:
     * commits the files from before it, notes that in the change's chat, and keeps the last signed
     * app when it was built from those same files. A stale revision is refused and nothing changes.
     */
    Undone undo(int expectedRevision, Change shown) throws IOException, Refused {
        Undoable undoable = undoable(expectedRevision);
        String refused = refusal(false, false, undoable);
        if (refused != null) throw new Refused(refused);
        Change change = undoable.change;
        if (!change.equals(shown))
            throw new Refused(
                    "The builder's last change is not the one you were shown. Nothing was undone.");
        int revision = source.restore(expectedRevision, change.before);
        String restored = digest(source.readRecoverySources(revision));
        synchronized (LOCK) {
            List<Entry> entries = read();
            entries.add(new Undo(revision, restored, change.after));
            write(entries);
        }
        boolean noted;
        try {
            chats.appendMessage(
                    source.projectId(), change.chatId, ChatStore.UNDO, note(change), revision);
            noted = true;
        } catch (IOException | IllegalArgumentException chatFull) {
            noted = false;
        }
        return new Undone(revision, change, noted, keepSignedApp(revision, restored));
    }

    /** Whether revision {@code head} is one an undo committed. */
    boolean undidAt(int head) throws IOException {
        synchronized (LOCK) {
            for (Entry entry : read())
                if (entry instanceof Undo undo && undo.revision == head) return true;
        }
        return false;
    }

    /** The chat note: what the person and the model read after an undo. */
    static String note(Change change) {
        return "Undid the last change. The app's files are back to how they were before \""
                + quoted(change)
                + "\".";
    }

    /** The message that caused a change, shortened to fit in a sentence. */
    static String quoted(Change change) {
        String request = change.request;
        return request.length() <= QUOTED_CHARS
                ? request
                : request.substring(0, QUOTED_CHARS - 1).trim() + "…";
    }

    private boolean keepSignedApp(int revision, String restored) {
        File project = source.directory();
        try {
            SignedArtifactReceipt.Record last =
                    SignedArtifactReceipt.read(project, source.projectId(), source.packageName());
            if (!digest(source.readRevision(last.revision)).equals(restored)) return false;
            SignedArtifactReceipt.write(
                    project,
                    source.projectId(),
                    source.packageName(),
                    revision,
                    last.versionCode,
                    last.artifact,
                    last.signerFingerprint);
            return true;
        } catch (IOException | RuntimeException noUsableBuild) {
            return false;
        }
    }

    private static int indexOfRun(List<Entry> entries, int after, int below) {
        for (int index = below - 1; index >= 0; index--)
            if (entries.get(index) instanceof Change change && change.after == after) return index;
        return -1;
    }

    private static String digest(java.util.Map<String, byte[]> files) throws IOException {
        StringBuilder hex = new StringBuilder(64);
        for (byte value : SourceSnapshotDigest.compute(files))
            hex.append(Character.forDigit((value >> 4) & 15, 16))
                    .append(Character.forDigit(value & 15, 16));
        return hex.toString();
    }

    private static String oneLine(String text, int maximum) {
        String line = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (line.isEmpty()) line = "your message";
        return line.length() <= maximum ? line : line.substring(0, maximum);
    }

    /** The saved history; one that is not valid is kept aside and a new one starts. */
    private List<Entry> read() throws IOException {
        try {
            return parse();
        } catch (InvalidHistory | CharacterCodingException invalid) {
            Files.move(
                    file,
                    file.resolveSibling(FILE + ".invalid"),
                    StandardCopyOption.REPLACE_EXISTING);
            return new ArrayList<>();
        }
    }

    private static final class InvalidHistory extends IOException {
        InvalidHistory(Throwable cause) {
            super("Invalid change history", cause);
        }
    }

    private List<Entry> parse() throws IOException {
        List<Entry> entries = new ArrayList<>();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return entries;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > MAX_FILE_BYTES) throw new InvalidHistory(null);
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] fields = line.split("\t", -1);
            try {
                if (fields.length == 8 && fields[0].equals("run")) {
                    entries.add(
                            new Change(
                                    revision(fields[1]),
                                    revision(fields[2]),
                                    hash(fields[3]),
                                    matching(fields[4], "[0-9a-f]{32}"),
                                    fields[5].isEmpty() ? null : matching(fields[5], "[0-9]{8}"),
                                    Long.parseLong(matching(fields[6], "[0-9]{1,19}")),
                                    fields[7]));
                } else if (fields.length == 4 && fields[0].equals("undo")) {
                    entries.add(
                            new Undo(revision(fields[1]), hash(fields[2]), revision(fields[3])));
                } else {
                    throw new InvalidHistory(null);
                }
            } catch (IllegalArgumentException invalid) {
                throw new InvalidHistory(invalid);
            }
        }
        return entries;
    }

    private void write(List<Entry> entries) throws IOException {
        StringBuilder text = new StringBuilder();
        for (Entry entry :
                entries.subList(Math.max(0, entries.size() - MAX_ENTRIES), entries.size())) {
            if (entry instanceof Change change)
                text.append(
                        String.join(
                                "\t",
                                "run",
                                Integer.toString(change.before),
                                Integer.toString(change.after),
                                change.digest,
                                change.chatId,
                                change.messageId == null ? "" : change.messageId,
                                Long.toString(change.atMillis),
                                change.request));
            else if (entry instanceof Undo undo)
                text.append(
                        String.join(
                                "\t",
                                "undo",
                                Integer.toString(undo.revision),
                                undo.digest,
                                Integer.toString(undo.undid)));
            text.append('\n');
        }
        if (Files.isSymbolicLink(file)) throw new IOException("Change history is a link");
        Path temporary = Files.createTempFile(file.getParent(), "changes-", ".tmp");
        try {
            Files.write(temporary, text.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static int revision(String text) {
        return Integer.parseInt(matching(text, "0|[1-9][0-9]{0,9}"));
    }

    private static String hash(String text) {
        return matching(text, "[0-9a-f]{64}");
    }

    private static String matching(String text, String pattern) {
        if (!text.matches(pattern)) throw new IllegalArgumentException("Invalid field");
        return text;
    }
}
