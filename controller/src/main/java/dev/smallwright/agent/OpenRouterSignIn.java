package dev.smallwright.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import dev.smallwright.security.ModelKeyInput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * One OpenRouter sign-in without a callback (OAuth PKCE, headless): the person approves on
 * OpenRouter's page, which shows a one-time code; the pasted code and this sign-in's verifier are
 * exchanged for a key the person owns. Nothing on this phone listens for a reply.
 */
public final class OpenRouterSignIn {
    static final String EXCHANGE = "https://openrouter.ai/api/v1/auth/keys";
    private static final String AUTHORIZE = "https://openrouter.ai/auth";
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9._~-]{1,512}");
    private static final int MAX_REPLY_BYTES = 16 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** OpenRouter refused the code: mistyped, already used, or older than ten minutes. */
    public static final class RefusedCode extends IOException {
        RefusedCode() {
            super("OpenRouter refused the sign-in code");
        }
    }

    private final String verifier;

    OpenRouterSignIn(String verifier) {
        this.verifier = verifier;
    }

    public static OpenRouterSignIn start() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return new OpenRouterSignIn(base64Url(random));
    }

    /** OpenRouter's page to open in the browser; it names the new key Smallwright. */
    public String authorizationUrl() {
        return AUTHORIZE
                + "?code_challenge="
                + challenge()
                + "&code_challenge_method=S256&key_label=Smallwright";
    }

    String challenge() {
        try {
            return base64Url(
                    MessageDigest.getInstance("SHA-256")
                            .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    /**
     * The key for the code the person pasted, as caller-owned ASCII bytes that pass the same check
     * as a pasted key. Call off the UI thread.
     */
    public byte[] exchange(String pasted) throws IOException {
        return exchange(pasted, ModelProvider.OPENROUTER::open);
    }

    byte[] exchange(String pasted, ModelClient.ConnectionFactory connections) throws IOException {
        String code = pasted == null ? "" : pasted.trim();
        if (!CODE.matcher(code).matches()) throw new RefusedCode();
        JsonObject request = new JsonObject();
        request.addProperty("code", code);
        request.addProperty("code_verifier", verifier);
        request.addProperty("code_challenge_method", "S256");
        HttpURLConnection connection = connections.open(new URL(EXCHANGE));
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            for (String[] header : ModelClient.OPENROUTER_ATTRIBUTION)
                connection.setRequestProperty(header[0], header[1]);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status == 400 || status == 403) throw new RefusedCode();
            if (status != 200) throw new IOException("OpenRouter sign-in unavailable");
            return key(read(connection));
        } finally {
            connection.disconnect();
        }
    }

    private static String read(HttpURLConnection connection) throws IOException {
        try (InputStream input = connection.getInputStream();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            for (int count; (count = input.read(chunk)) != -1; ) {
                if (bytes.size() + count > MAX_REPLY_BYTES)
                    throw new IOException("Sign-in reply exceeds limit");
                bytes.write(chunk, 0, count);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] key(String reply) throws IOException {
        try {
            JsonReader reader = new JsonReader(new StringReader(reply));
            reader.setStrictness(Strictness.STRICT);
            JsonElement key = JsonParser.parseReader(reader).getAsJsonObject().get("key");
            if (key == null || !key.isJsonPrimitive() || !key.getAsJsonPrimitive().isString())
                throw new IOException("Sign-in reply has no key");
            return ModelKeyInput.openRouterKey(key.getAsString().toCharArray());
        } catch (RuntimeException malformed) {
            throw new IOException("Invalid sign-in reply");
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
