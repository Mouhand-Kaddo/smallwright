package dev.smallwright.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.smallwright.agent.ModelChoice;
import dev.smallwright.project.ChatStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The pictures the person attached to one message, already shrunk on this phone to JPEG. They go
 * with that message only, after its text, as chat completions image parts.
 */
final class AttachedImages {
    static final int MAX_COUNT = ChatStore.MAX_MESSAGE_IMAGES;

    /** The longest side, in pixels, a picture is shrunk to before it is attached. */
    static final int MAX_SIDE = 1568;

    /**
     * Tokens one picture is assumed to take until the model reports its count: a 1568 by 1568
     * picture at 750 pixels a token, rounded up.
     */
    static final int ESTIMATED_TOKENS = 4000;

    static final AttachedImages NONE = new AttachedImages(List.of());

    private final List<byte[]> jpegs;

    AttachedImages(List<byte[]> jpegs) {
        if (jpegs == null || jpegs.size() > MAX_COUNT)
            throw new IllegalArgumentException("Too many pictures");
        List<byte[]> kept = new ArrayList<>();
        for (byte[] jpeg : jpegs) {
            if (!ChatStore.isStorableImage(jpeg))
                throw new IllegalArgumentException("Invalid picture");
            kept.add(jpeg.clone());
        }
        this.jpegs = List.copyOf(kept);
    }

    boolean isEmpty() {
        return jpegs.isEmpty();
    }

    int count() {
        return jpegs.size();
    }

    /** Copies of the JPEG bytes, in the order picked. */
    List<byte[]> bytes() {
        List<byte[]> copies = new ArrayList<>();
        for (byte[] jpeg : jpegs) copies.add(jpeg.clone());
        return copies;
    }

    /** The message content: the text alone, or the text followed by one image part a picture. */
    JsonElement userContent(String text) {
        if (jpegs.isEmpty()) return new JsonPrimitive(text);
        JsonArray parts = new JsonArray();
        JsonObject words = new JsonObject();
        words.addProperty("type", "text");
        words.addProperty("text", text);
        parts.add(words);
        for (byte[] jpeg : jpegs) {
            JsonObject url = new JsonObject();
            url.addProperty("url", dataUrl(jpeg));
            JsonObject part = new JsonObject();
            part.addProperty("type", "image_url");
            part.add("image_url", url);
            parts.add(part);
        }
        return parts;
    }

    /** The bytes the data URLs take in a request. */
    int dataUrlBytes() {
        int bytes = 0;
        for (byte[] jpeg : jpegs) bytes += dataUrl(jpeg).length();
        return bytes;
    }

    /** How many image parts {@code messages} hold, and the bytes their data URLs take. */
    static int[] pictures(JsonArray messages) {
        int count = 0;
        int bytes = 0;
        for (JsonElement message : messages) {
            JsonElement content = message.getAsJsonObject().get("content");
            if (content == null || !content.isJsonArray()) continue;
            for (JsonElement part : content.getAsJsonArray()) {
                JsonObject image = part.getAsJsonObject().getAsJsonObject("image_url");
                if (image == null) continue;
                count++;
                bytes += image.get("url").getAsString().length();
            }
        }
        return new int[] {count, bytes};
    }

    /** SHA-256 over every picture's length and bytes, in order. */
    String digest() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] jpeg : jpegs) {
                digest.update(
                        Integer.toString(jpeg.length)
                                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(jpeg);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest())
                hex.append(Character.forDigit((value >> 4) & 15, 16))
                        .append(Character.forDigit(value & 15, 16));
            return jpegs.size() + ":" + hex;
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    /** Why Send is refused with these pictures and this model, or null when it is not. */
    String refusal(ModelChoice model) {
        if (jpegs.isEmpty() || model.readsImages) return null;
        if (!model.provider.isOpenRouter())
            return "Pictures go only to OpenRouter models listed as reading them. Remove the"
                    + " pictures or pick one of those models. Nothing was sent.";
        return model.shortName()
                + " can't look at pictures. Remove the pictures or pick a model that can."
                + " Nothing was sent.";
    }

    /** The size a picture is shrunk to: its longest side at most {@link #MAX_SIDE}. */
    static int[] fitWithin(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Invalid picture size");
        int longest = Math.max(width, height);
        if (longest <= MAX_SIDE) return new int[] {width, height};
        return new int[] {
            Math.max(1, Math.round((float) width * MAX_SIDE / longest)),
            Math.max(1, Math.round((float) height * MAX_SIDE / longest))
        };
    }

    private static String dataUrl(byte[] jpeg) {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
    }
}
