package dev.smallwright.security;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class ModelKeyInputTest {
    @Test
    public void consumesDummyKeyWithoutKeepingInputCharacters() {
        char[] typed = "sk-or-v1-dummy-test-only".toCharArray();

        byte[] copied = ModelKeyInput.openRouterKey(typed);

        assertArrayEquals("sk-or-v1-dummy-test-only".getBytes(StandardCharsets.US_ASCII), copied);
        for (char character : typed) assertEquals(0, character);
    }

    @Test
    public void rejectsOtherProviderAndClearsInput() {
        char[] typed = "sk-other-dummy-test-only".toCharArray();

        assertThrows(IllegalArgumentException.class, () -> ModelKeyInput.openRouterKey(typed));

        for (char character : typed) assertEquals(0, character);
    }

    @Test
    public void rejectsWhitespaceAndOversizedKeyWithoutEchoingInput() {
        char[] withSpace = "sk-or-v1-dummy key test".toCharArray();
        IllegalArgumentException badSpace =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ModelKeyInput.openRouterKey(withSpace));
        for (char character : withSpace) assertEquals(0, character);
        if (badSpace.getMessage().contains("dummy"))
            throw new AssertionError("Input leaked in error");

        char[] oversized = new char[257];
        java.util.Arrays.fill(oversized, 'x');
        oversized[0] = 's';
        oversized[1] = 'k';
        oversized[2] = '-';
        oversized[3] = 'o';
        oversized[4] = 'r';
        oversized[5] = '-';
        assertThrows(IllegalArgumentException.class, () -> ModelKeyInput.openRouterKey(oversized));
        for (char character : oversized) assertEquals(0, character);
    }

    @Test
    public void acceptsTypicalServerKeysAndClearsInput() {
        for (String key :
                new String[] {
                    "sk-proj-dummy_test-only",
                    "gsk_dummyTestOnly1234",
                    "AIzaDummy-Test_Only",
                    "dummy.test.only+key/part==",
                    "0123456789abcdef"
                }) {
            char[] typed = key.toCharArray();
            assertArrayEquals(
                    key.getBytes(StandardCharsets.US_ASCII), ModelKeyInput.serverKey(typed));
            for (char character : typed) assertEquals(0, character);
        }
    }

    @Test
    public void refusesServerKeysThatCannotBeSentAsABearerTokenAndClearsInput() {
        char[] oversized = new char[1025];
        java.util.Arrays.fill(oversized, 'k');
        for (char[] typed :
                new char[][] {
                    "short".toCharArray(),
                    "sk-dummy key".toCharArray(),
                    "sk-dummy\r\nX:y".toCharArray(),
                    "sk-dümmy-test-only".toCharArray(),
                    "sk-dummy\"test".toCharArray(),
                    oversized
                }) {
            IllegalArgumentException refused =
                    assertThrows(
                            IllegalArgumentException.class, () -> ModelKeyInput.serverKey(typed));
            if (refused.getMessage().contains("dummy"))
                throw new AssertionError("Input leaked in error");
            for (char character : typed) assertEquals(0, character);
        }
        assertThrows(IllegalArgumentException.class, () -> ModelKeyInput.serverKey(null));
    }

    @Test
    public void sendableKeysAreBearerTokensOnly() {
        assertEquals(true, ModelKeyInput.sendable("sk-or-v1-dummy"));
        assertEquals(true, ModelKeyInput.sendable("dummy.test+key/part=="));
        assertEquals(false, ModelKeyInput.sendable("dummy key"));
        assertEquals(false, ModelKeyInput.sendable("dummy\nkey"));
        assertEquals(false, ModelKeyInput.sendable(""));
        assertEquals(false, ModelKeyInput.sendable("k".repeat(1025)));
    }
}
