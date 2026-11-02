package dev.smallwright.security;

import java.util.Arrays;
import java.util.regex.Pattern;

/** Copies a user-entered model key without creating an immutable plaintext String. */
public final class ModelKeyInput {
    private static final char[] OPENROUTER_PREFIX = {'s', 'k', '-', 'o', 'r', '-'};
    private static final String OPENROUTER_PUNCTUATION = "-_";

    /** The characters of an OAuth bearer token (RFC 6750 b64token), which any server key uses. */
    private static final String BEARER_PUNCTUATION = "-._~+/=";

    private static final int MAX_KEY_CHARS = 1024;
    private static final Pattern SENDABLE =
            Pattern.compile("[A-Za-z0-9._~+/=-]{1," + MAX_KEY_CHARS + "}");

    private ModelKeyInput() {}

    /**
     * An OpenRouter key: {@code sk-or-}, letters, digits, hyphens and underscores, 20 to 256
     * characters. Returns caller-owned ASCII bytes and clears the input on every path.
     */
    public static byte[] openRouterKey(char[] input) {
        return take(input, 20, 256, OPENROUTER_PREFIX, OPENROUTER_PUNCTUATION);
    }

    /**
     * A model server's key: a bearer token of 8 to {@value #MAX_KEY_CHARS} characters. Returns
     * caller-owned ASCII bytes and clears the input on every path.
     */
    public static byte[] serverKey(char[] input) {
        return take(input, 8, MAX_KEY_CHARS, new char[0], BEARER_PUNCTUATION);
    }

    /** True when {@code key} can go in an Authorization header as a bearer token. */
    public static boolean sendable(String key) {
        return key != null && SENDABLE.matcher(key).matches();
    }

    private static byte[] take(char[] input, int min, int max, char[] prefix, String punctuation) {
        if (input == null) throw new IllegalArgumentException("Invalid key format");
        try {
            if (input.length < min || input.length > max)
                throw new IllegalArgumentException("Invalid key format");
            for (int i = 0; i < prefix.length; i++)
                if (input[i] != prefix[i]) throw new IllegalArgumentException("Invalid key format");
            for (char character : input)
                if (!((character >= 'A' && character <= 'Z')
                        || (character >= 'a' && character <= 'z')
                        || (character >= '0' && character <= '9')
                        || punctuation.indexOf(character) >= 0))
                    throw new IllegalArgumentException("Invalid key format");
            byte[] copied = new byte[input.length];
            for (int i = 0; i < input.length; i++) copied[i] = (byte) input[i];
            return copied;
        } finally {
            Arrays.fill(input, '\0');
        }
    }
}
