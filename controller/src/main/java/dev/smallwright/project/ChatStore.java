package dev.smallwright.project;

import dev.smallwright.agent.QuestionRound;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Controller-private, bounded conversation history. Source remains in the shared project store. */
public final class ChatStore {
    private static final int MAGIC_CHAT = 0x504c4348;
    private static final int MAGIC_MESSAGE = 0x504c4d47;
    private static final int MAX_CHATS = 100;
    private static final int MAX_MESSAGES = 100;
    private static final int MAX_TITLE_BYTES = 256;

    /** As much as one model reply may hold, so the builder's answer is always saved whole. */
    public static final int MAX_TEXT_BYTES = 16_000_000;

    public static final int MAX_MESSAGE_IMAGES = 4;
    public static final int MAX_IMAGE_BYTES = 512 * 1024;
    public static final int MAX_CHAT_IMAGES = 40;

    /** The role of a message that holds a round of questions from the ask tool. */
    public static final String QUESTION = "question";

    /** The role of the note saved when the person undoes the builder's last change. */
    public static final String UNDO = "undo";

    private static final Object LOCK = new Object();
    private final Path root;
    private final Path trustedBase;

    public ChatStore(File root) {
        this(root, null);
    }

    /** The Android controller can supply its OS-owned private files directory as a boundary. */
    public ChatStore(File root, File trustedBase) {
        this.root = root.toPath().toAbsolutePath().normalize();
        this.trustedBase =
                trustedBase == null ? null : trustedBase.toPath().toAbsolutePath().normalize();
        if (this.trustedBase != null
                && (!this.root.startsWith(this.trustedBase)
                        || !Files.isDirectory(this.trustedBase, LinkOption.NOFOLLOW_LINKS))) {
            throw new SecurityException("Chat root is outside private storage");
        }
    }

    public Chat createChat(String projectId, String title) throws IOException {
        validProjectId(projectId);
        byte[] titleBytes = utf8(title, MAX_TITLE_BYTES, false);
        synchronized (LOCK) {
            Path project = project(projectId);
            ensureDirectory(project);
            if (listChats(projectId).size() >= MAX_CHATS) {
                throw new IOException("Chat limit reached");
            }
            String id = newId();
            Path chat = project.resolve(id);
            ensureDirectory(chat);
            Chat created = new Chat(id, title, System.currentTimeMillis());
            writeAtomic(
                    chat.resolve("chat.bin"),
                    record(MAGIC_CHAT, id, titleBytes, created.createdAtMillis));
            return created;
        }
    }

    public void deleteProject(String projectId) throws IOException {
        validProjectId(projectId);
        synchronized (LOCK) {
            if (!existingDirectory(root)) return;
            Path project = project(projectId);
            if (existingDirectory(project)) ProjectStore.deleteTree(project);
        }
    }

    public List<Chat> listChats(String projectId) throws IOException {
        validProjectId(projectId);
        synchronized (LOCK) {
            Path project = project(projectId);
            if (!existingDirectory(project)) return List.of();
            List<Chat> chats = new ArrayList<>();
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(project)) {
                for (Path chat : entries) {
                    rejectSymlink(chat);
                    String id = chat.getFileName().toString();
                    if (!id.matches("[0-9a-f]{32}") || !existingDirectory(chat)) {
                        throw new SecurityException("Invalid chat directory");
                    }
                    Path metadata = chat.resolve("chat.bin");
                    if (!Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) continue;
                    chats.add(readChat(metadata, id));
                    if (chats.size() > MAX_CHATS) throw new IOException("Chat limit exceeded");
                }
            }
            chats.sort(
                    Comparator.comparingLong((Chat chat) -> chat.createdAtMillis)
                            .reversed()
                            .thenComparing(chat -> chat.id));
            return chats;
        }
    }

    public Message appendMessage(
            String projectId, String chatId, String role, String text, int sourceRevision)
            throws IOException {
        return appendMessage(projectId, chatId, role, text, sourceRevision, List.of());
    }

    /**
     * Saves a message with the JPEG pictures sent with it. The pictures are written first, so a
     * saved message never lacks its pictures.
     */
    public Message appendMessage(
            String projectId,
            String chatId,
            String role,
            String text,
            int sourceRevision,
            List<byte[]> images)
            throws IOException {
        validProjectId(projectId);
        validChatId(chatId);
        validRole(role);
        byte[] textBytes = utf8(text, MAX_TEXT_BYTES, false);
        if (QUESTION.equals(role)) QuestionRound.fromSaved(text);
        if (sourceRevision < 0) throw new IllegalArgumentException("Negative source revision");
        if (images == null || images.size() > MAX_MESSAGE_IMAGES)
            throw new IllegalArgumentException("Too many pictures");
        for (byte[] image : images)
            if (!isStorableImage(image)) throw new IllegalArgumentException("Invalid picture");
        synchronized (LOCK) {
            Path chat = existingChat(projectId, chatId);
            Path messages = chat.resolve("messages");
            int prior = messageCount(messages);
            if (prior >= MAX_MESSAGES) throw new IOException("Message limit reached");
            if (imageCount(projectId, chatId) + images.size() > MAX_CHAT_IMAGES)
                throw new IOException("Picture limit reached");
            ensureDirectory(messages);
            String id = String.format(java.util.Locale.ROOT, "%08d", prior + 1);
            Path pictures = chat.resolve("images");
            if (!images.isEmpty() || existingDirectory(pictures)) {
                ensureDirectory(pictures);
                for (int index = 1; index <= MAX_MESSAGE_IMAGES; index++)
                    Files.deleteIfExists(pictures.resolve(imageName(id, index)));
                for (int index = 0; index < images.size(); index++)
                    writeAtomic(pictures.resolve(imageName(id, index + 1)), images.get(index));
            }
            Message message = new Message(id, role, text, sourceRevision);
            writeAtomic(
                    messages.resolve(id + ".bin"),
                    record(MAGIC_MESSAGE, role, textBytes, sourceRevision));
            return message;
        }
    }

    public List<Message> listMessages(String projectId, String chatId) throws IOException {
        validProjectId(projectId);
        validChatId(chatId);
        synchronized (LOCK) {
            Path messages = existingChat(projectId, chatId).resolve("messages");
            if (!existingDirectory(messages)) return List.of();
            List<Message> result = new ArrayList<>();
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(messages)) {
                for (Path entry : entries) {
                    rejectSymlink(entry);
                    String file = entry.getFileName().toString();
                    if (file.matches("write--?[0-9]+\\.tmp")) continue;
                    if (!file.matches("[0-9]{8}\\.bin")) {
                        throw new SecurityException("Invalid message file");
                    }
                    result.add(readMessage(entry, file.substring(0, 8)));
                    if (result.size() > MAX_MESSAGES)
                        throw new IOException("Message limit exceeded");
                }
            }
            result.sort(Comparator.comparing(message -> message.id));
            for (int i = 0; i < result.size(); i++) {
                if (!result.get(i).id.equals(String.format(java.util.Locale.ROOT, "%08d", i + 1))) {
                    throw new SecurityException("Message sequence is incomplete");
                }
            }
            return result;
        }
    }

    /**
     * How many messages the folder holds, checked like {@link #listMessages} without reading them.
     */
    private int messageCount(Path messages) throws IOException {
        if (!existingDirectory(messages)) return 0;
        java.util.Set<String> ids = new java.util.HashSet<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(messages)) {
            for (Path entry : entries) {
                rejectSymlink(entry);
                String file = entry.getFileName().toString();
                if (file.matches("write--?[0-9]+\\.tmp")) continue;
                if (!file.matches("[0-9]{8}\\.bin"))
                    throw new SecurityException("Invalid message file");
                ids.add(file.substring(0, 8));
                if (ids.size() > MAX_MESSAGES) throw new IOException("Message limit exceeded");
            }
        }
        for (int i = 1; i <= ids.size(); i++)
            if (!ids.contains(String.format(java.util.Locale.ROOT, "%08d", i)))
                throw new SecurityException("Message sequence is incomplete");
        return ids.size();
    }

    /** The pictures saved with one message, in the order sent; empty when it has none. */
    public List<byte[]> images(String projectId, String chatId, String messageId)
            throws IOException {
        validProjectId(projectId);
        validChatId(chatId);
        if (messageId == null || !messageId.matches("[0-9]{8}"))
            throw new IllegalArgumentException("Invalid message ID");
        synchronized (LOCK) {
            Path pictures = existingChat(projectId, chatId).resolve("images");
            List<byte[]> found = new ArrayList<>();
            if (!existingDirectory(pictures)) return found;
            for (int index = 1; index <= MAX_MESSAGE_IMAGES; index++) {
                Path picture = pictures.resolve(imageName(messageId, index));
                rejectSymlink(picture);
                if (!Files.exists(picture, LinkOption.NOFOLLOW_LINKS)) break;
                if (!Files.isRegularFile(picture, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(picture) > MAX_IMAGE_BYTES)
                    throw new SecurityException("Invalid picture file");
                byte[] bytes = Files.readAllBytes(picture);
                if (!isStorableImage(bytes)) throw new SecurityException("Invalid picture file");
                found.add(bytes);
            }
            return found;
        }
    }

    /** How many pictures the chat's saved messages hold. */
    public int imageCount(String projectId, String chatId) throws IOException {
        synchronized (LOCK) {
            int messages = messageCount(existingChat(projectId, chatId).resolve("messages"));
            Path pictures = existingChat(projectId, chatId).resolve("images");
            if (!existingDirectory(pictures)) return 0;
            int count = 0;
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(pictures)) {
                for (Path entry : entries) {
                    String file = entry.getFileName().toString();
                    if (file.matches("[0-9]{8}-[1-4]\\.jpg")
                            && Integer.parseInt(file.substring(0, 8)) <= messages) count++;
                }
            }
            return count;
        }
    }

    /** A JPEG of at most {@link #MAX_IMAGE_BYTES}, judged by its first bytes. */
    public static boolean isStorableImage(byte[] bytes) {
        return bytes != null
                && bytes.length >= 3
                && bytes.length <= MAX_IMAGE_BYTES
                && bytes[0] == (byte) 0xFF
                && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF;
    }

    private static String imageName(String messageId, int index) {
        return messageId + "-" + index + ".jpg";
    }

    public static final class Chat {
        public final String id;
        public final String title;
        public final long createdAtMillis;

        private Chat(String id, String title, long createdAtMillis) {
            this.id = id;
            this.title = title;
            this.createdAtMillis = createdAtMillis;
        }
    }

    public static final class Message {
        public final String id;
        public final String role;
        public final String text;
        public final int sourceRevision;

        private Message(String id, String role, String text, int sourceRevision) {
            this.id = id;
            this.role = role;
            this.text = text;
            this.sourceRevision = sourceRevision;
        }
    }

    private Path project(String projectId) throws IOException {
        existingDirectory(root);
        return root.resolve(projectId);
    }

    private Path existingChat(String projectId, String chatId) throws IOException {
        Path chat = project(projectId).resolve(chatId);
        if (!existingDirectory(chat)
                || !Files.isRegularFile(chat.resolve("chat.bin"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Unknown chat");
        }
        readChat(chat.resolve("chat.bin"), chatId);
        return chat;
    }

    private static void validProjectId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid project ID");
        }
    }

    private static void validChatId(String id) {
        if (id == null || !id.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Invalid chat ID");
        }
    }

    private static void validRole(String role) {
        if (!java.util.Set.of("user", "assistant", "status", "tool", QUESTION, UNDO)
                .contains(role)) {
            throw new IllegalArgumentException("Invalid message role");
        }
    }

    private static byte[] utf8(String value, int maximum, boolean allowEmpty) {
        if (value == null || (!allowEmpty && value.isEmpty())) {
            throw new IllegalArgumentException("Empty text");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximum) throw new IllegalArgumentException("Text too large");
        return bytes;
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static byte[] record(int magic, String first, byte[] second, long number)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(magic);
            writeBytes(out, first.getBytes(StandardCharsets.UTF_8));
            writeBytes(out, second);
            out.writeLong(number);
        }
        return bytes.toByteArray();
    }

    private static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private Chat readChat(Path path, String expectedId) throws IOException {
        try (DataInputStream in = openRecord(path, MAX_TITLE_BYTES + 64)) {
            if (in.readInt() != MAGIC_CHAT) throw new SecurityException("Invalid chat record");
            String id = new String(readBytes(in, 32), StandardCharsets.UTF_8);
            String title = new String(readBytes(in, MAX_TITLE_BYTES), StandardCharsets.UTF_8);
            long created = in.readLong();
            if (in.read() != -1 || !id.equals(expectedId) || title.isEmpty() || created < 0) {
                throw new SecurityException("Invalid chat record");
            }
            return new Chat(id, title, created);
        }
    }

    private Message readMessage(Path path, String id) throws IOException {
        try (DataInputStream in = openRecord(path, MAX_TEXT_BYTES + 64)) {
            if (in.readInt() != MAGIC_MESSAGE)
                throw new SecurityException("Invalid message record");
            String role = new String(readBytes(in, 9), StandardCharsets.UTF_8);
            String text = new String(readBytes(in, MAX_TEXT_BYTES), StandardCharsets.UTF_8);
            long revision = in.readLong();
            if (in.read() != -1 || text.isEmpty() || revision < 0 || revision > Integer.MAX_VALUE) {
                throw new SecurityException("Invalid message record");
            }
            validRole(role);
            if (QUESTION.equals(role)) {
                try {
                    QuestionRound.fromSaved(text);
                } catch (IllegalArgumentException invalid) {
                    throw new SecurityException("Invalid message record", invalid);
                }
            }
            return new Message(id, role, text, (int) revision);
        }
    }

    private DataInputStream openRecord(Path path, int maximum) throws IOException {
        rejectSymlink(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maximum) {
            throw new SecurityException("Invalid chat record file");
        }
        return new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(path)));
    }

    private static byte[] readBytes(DataInputStream in, int maximum) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > maximum) throw new SecurityException("Invalid record length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    private void writeAtomic(Path target, byte[] bytes) throws IOException {
        rejectSymlink(target);
        Path temporary = Files.createTempFile(target.getParent(), "write-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private boolean existingDirectory(Path path) throws IOException {
        rejectSymlink(path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return false;
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new SecurityException("Invalid chat directory");
        }
        return true;
    }

    private void ensureDirectory(Path path) throws IOException {
        if (existingDirectory(path)) return;
        Path parent = path.getParent();
        if (parent != null) ensureDirectory(parent);
        Files.createDirectory(path);
    }

    private void rejectSymlink(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (trustedBase != null && !absolute.startsWith(trustedBase)) {
            throw new SecurityException("Chat path is outside private storage");
        }
        for (Path part = absolute; part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part)) {
                throw new SecurityException("Chat path is a symlink");
            }
            if (part.equals(trustedBase)) return;
        }
    }
}
