package dev.smallwright.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Asks OpenRouter's image model for one picture, with the person's key, for an app icon or a
 * picture the app shows. The reply's PNG, JPEG or WebP is checked before anyone decodes it; its
 * reported cost is returned either way.
 */
public final class ImageClient {
    public static final String MODEL = "google/gemini-3.1-flash-lite-image";
    private static final String DATED_MODEL = "google/gemini-3.1-flash-lite-image-20260630";
    public static final int MAX_PROMPT_CHARS = 2000;
    public static final int MAX_RESPONSE_BYTES = 6 * 1024 * 1024;
    public static final int MAX_PNG_BYTES = 4 * 1024 * 1024;
    public static final int MIN_SIDE = 64;
    public static final int MAX_SIDE = 4096;

    /** Why a run with a model server makes no icon or picture: the image model is OpenRouter's. */
    public static final String NEEDS_OPENROUTER =
            "Icons and pictures are made only with an OpenRouter model. Pick one to make them";

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    private static final String UNREADABLE = "The image model's image could not be read.";

    private final ModelClient transport;

    /** The picture's shape, sent as OpenRouter's {@code image_config.aspect_ratio}. */
    public enum Shape {
        SQUARE("1:1"),
        WIDE("16:9"),
        TALL("9:16");

        public final String aspectRatio;

        Shape(String aspectRatio) {
            this.aspectRatio = aspectRatio;
        }

        /** The shape the builder named in lower case, or null for any other word. */
        public static Shape named(String name) {
            for (Shape shape : values())
                if (shape.name().toLowerCase(java.util.Locale.ROOT).equals(name)) return shape;
            return null;
        }
    }

    public ImageClient() {
        this(url -> (java.net.HttpURLConnection) url.openConnection());
    }

    public ImageClient(ModelClient.ConnectionFactory connections) {
        transport = new ModelClient(connections, ModelChoice.DEFAULT);
    }

    /**
     * What one image request gave: a checked PNG, JPEG or WebP, or why it cannot be used, and what
     * it cost.
     */
    public static final class Image {
        /** The image, or null when {@link #refusal} says why it cannot be used. */
        public final byte[] bytes;

        public final String refusal;
        public final int totalTokens;
        public final int promptTokens;
        public final BigDecimal costUsd;

        private Image(
                byte[] bytes,
                String refusal,
                int totalTokens,
                int promptTokens,
                BigDecimal costUsd) {
            this.bytes = bytes;
            this.refusal = refusal;
            this.totalTokens = totalTokens;
            this.promptTokens = promptTokens;
            this.costUsd = costUsd;
        }
    }

    /**
     * Asks for one image. The reply is not streamed, so the wait has no time limit; Stop ({@code
     * cancellation}) ends it.
     */
    public Image make(
            byte[] credential, String prompt, Shape shape, ModelClient.Cancellation cancellation)
            throws IOException {
        if (prompt == null || prompt.trim().isEmpty() || prompt.length() > MAX_PROMPT_CHARS)
            throw new IOException("Invalid image prompt");
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        JsonArray messages = new JsonArray();
        messages.add(message);
        JsonArray modalities = new JsonArray();
        modalities.add("image");
        modalities.add("text");
        JsonObject config = new JsonObject();
        config.addProperty("aspect_ratio", shape.aspectRatio);
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("stream", false);
        body.add("modalities", modalities);
        body.add("image_config", config);
        body.add("messages", messages);
        return transport.send(
                credential, body, cancellation, MAX_RESPONSE_BYTES, 0, ImageClient::parse);
    }

    private static Image parse(String json) throws IOException {
        JsonObject root;
        int total;
        int prompt;
        BigDecimal cost;
        try {
            root = ModelClient.strictJson(json, MAX_RESPONSE_BYTES).getAsJsonObject();
            if (root.has("error")) throw new IllegalArgumentException();
            String answered = ModelClient.string(root, "model");
            if (!MODEL.equals(answered) && !DATED_MODEL.equals(answered))
                throw new IllegalArgumentException();
            JsonObject usage = root.getAsJsonObject("usage");
            prompt = ModelClient.integer(usage, "prompt_tokens", UsageBudget.MAX_PROMPT_TOKENS);
            int completion =
                    ModelClient.integer(usage, "completion_tokens", UsageBudget.MAX_PROMPT_TOKENS);
            total = ModelClient.integer(usage, "total_tokens", UsageBudget.MAX_REPLY_TOKENS);
            if (total != prompt + completion) throw new IllegalArgumentException();
            JsonPrimitive reported = usage.getAsJsonPrimitive("cost");
            if (reported == null || !reported.isNumber()) throw new IllegalArgumentException();
            cost = reported.getAsBigDecimal();
            if (cost.signum() < 0) throw new IllegalArgumentException();
        } catch (RuntimeException | IOException malformed) {
            throw new IOException("Invalid model response (envelope)");
        }
        String refusal;
        byte[] image = null;
        try {
            String url = firstImage(root);
            refusal = url == null ? "The image model returned no image." : null;
            if (url != null) image = checkedImage(url);
        } catch (RefusedImage refused) {
            refusal = refused.getMessage();
        }
        return new Image(image, refusal, total, prompt, cost);
    }

    private static String firstImage(JsonObject root) throws RefusedImage {
        try {
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices.size() != 1) throw new IllegalArgumentException();
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            JsonElement images = message.get("images");
            if (images == null || images.isJsonNull() || images.getAsJsonArray().isEmpty())
                return null;
            return ModelClient.string(
                    images.getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("image_url"),
                    "url");
        } catch (RuntimeException malformed) {
            throw new RefusedImage("The image model's reply could not be read.");
        }
    }

    /**
     * The image a data URL holds when it is a PNG, JPEG or WebP whose first bytes match that type.
     * A PNG's size is checked here; Android reads the others' size before it decodes them.
     */
    private static byte[] checkedImage(String url) throws RefusedImage {
        int comma = url.indexOf(',');
        String head = comma < 0 ? "" : url.substring(0, comma + 1);
        String lower = head.toLowerCase(java.util.Locale.ROOT);
        String type =
                lower.startsWith("data:") && lower.endsWith(";base64,")
                        ? lower.substring(5, lower.length() - 8)
                        : "";
        if (type.equals("image/jpg")) type = "image/jpeg";
        if (!type.equals("image/png") && !type.equals("image/jpeg") && !type.equals("image/webp"))
            throw new RefusedImage(
                    "The image model returned "
                            + (type.isEmpty() ? "an image in a form" : "\"" + shown(type) + "\"")
                            + " this app cannot use; it takes PNG, JPEG or WebP.");
        if (url.length() - head.length() > (MAX_PNG_BYTES + 2) / 3 * 4)
            throw new RefusedImage(tooLarge());
        byte[] image;
        try {
            image = Base64.getDecoder().decode(url.substring(head.length()));
        } catch (IllegalArgumentException malformed) {
            throw new RefusedImage(UNREADABLE);
        }
        if (image.length > MAX_PNG_BYTES) throw new RefusedImage(tooLarge());
        if (type.equals("image/jpeg")) {
            if (image.length < 4
                    || (image[0] & 0xff) != 0xff
                    || (image[1] & 0xff) != 0xd8
                    || (image[2] & 0xff) != 0xff) throw new RefusedImage(UNREADABLE);
            return image;
        }
        if (type.equals("image/webp")) {
            if (image.length < 16
                    || !"RIFF".equals(new String(image, 0, 4, StandardCharsets.US_ASCII))
                    || !"WEBP".equals(new String(image, 8, 4, StandardCharsets.US_ASCII)))
                throw new RefusedImage(UNREADABLE);
            return image;
        }
        byte[] png = image;
        if (png.length < 33
                || !Arrays.equals(Arrays.copyOf(png, 8), SIGNATURE)
                || ByteBuffer.wrap(png, 8, 4).getInt() != 13
                || !"IHDR".equals(new String(png, 12, 4, StandardCharsets.US_ASCII)))
            throw new RefusedImage(UNREADABLE);
        long width = ByteBuffer.wrap(png, 16, 4).getInt() & 0xffffffffL;
        long height = ByteBuffer.wrap(png, 20, 4).getInt() & 0xffffffffL;
        if (width > MAX_SIDE || height > MAX_SIDE)
            throw new RefusedImage(
                    "The image is too large ("
                            + width
                            + "×"
                            + height
                            + " pixels, at most "
                            + MAX_SIDE
                            + " a side).");
        if (width < MIN_SIDE || height < MIN_SIDE)
            throw new RefusedImage(
                    "The image is too small ("
                            + width
                            + "×"
                            + height
                            + " pixels, at least "
                            + MIN_SIDE
                            + " a side).");
        return png;
    }

    private static String shown(String type) {
        StringBuilder safe = new StringBuilder();
        for (char c : type.substring(0, Math.min(type.length(), 40)).toCharArray())
            safe.append(
                    c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || "/+.-".indexOf(c) >= 0
                            ? c
                            : '?');
        return safe.toString();
    }

    private static String tooLarge() {
        return "The image is too large (more than " + MAX_PNG_BYTES / (1024 * 1024) + " MB).";
    }

    private static final class RefusedImage extends Exception {
        RefusedImage(String message) {
            super(message);
        }
    }
}
