package dev.smallwright.agent;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class OpenRouterSignInTest {
    private static final String KEY = "sk-or-v1-0123456789abcdef0123456789abcdef";

    @Test
    public void theChallengeIsTheS256OfTheVerifierFromRfc7636() {
        OpenRouterSignIn signIn =
                new OpenRouterSignIn("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk");

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", signIn.challenge());
    }

    @Test
    public void theSignInPageShowsTheCodeInsteadOfCallingBack() {
        String url =
                new OpenRouterSignIn("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
                        .authorizationUrl();

        assertEquals(
                "https://openrouter.ai/auth?code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
                        + "&code_challenge_method=S256&key_label=Smallwright",
                url);
        assertFalse(url.contains("callback_url"));
    }

    @Test
    public void eachSignInHasItsOwnVerifier() {
        assertNotEquals(
                OpenRouterSignIn.start().authorizationUrl(),
                OpenRouterSignIn.start().authorizationUrl());
    }

    @Test
    public void thePastedCodeIsExchangedForTheKeyWithTheVerifier() throws Exception {
        OpenRouterSignIn signIn =
                new OpenRouterSignIn("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk");
        try (ImageServer server = new ImageServer().answer(200, "{\"key\":\"" + KEY + "\"}")) {
            byte[] key =
                    signIn.exchange(
                            "  code-123_abc \n", server.connections(OpenRouterSignIn.EXCHANGE));

            assertArrayEquals(KEY.getBytes(StandardCharsets.US_ASCII), key);
            JsonObject sent = JsonParser.parseString(server.bodies.get(0)).getAsJsonObject();
            assertEquals("code-123_abc", sent.get("code").getAsString());
            assertEquals(
                    "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                    sent.get("code_verifier").getAsString());
            assertEquals("S256", sent.get("code_challenge_method").getAsString());
            assertNull(server.authorizations.get(0));
        }
    }

    @Test
    public void aRefusedCodeAsksForANewSignIn() throws Exception {
        for (int status : new int[] {400, 403}) {
            try (ImageServer server =
                    new ImageServer()
                            .answer(status, "{\"error\":\"Invalid code or code_verifier\"}")) {
                assertThrows(
                        OpenRouterSignIn.RefusedCode.class,
                        () ->
                                OpenRouterSignIn.start()
                                        .exchange(
                                                "code",
                                                server.connections(OpenRouterSignIn.EXCHANGE)));
            }
        }
    }

    @Test
    public void somethingThatIsNotACodeIsRefusedWithoutAsking() throws Exception {
        try (ImageServer server = new ImageServer().answer(200, "{\"key\":\"" + KEY + "\"}")) {
            for (String pasted :
                    new String[] {null, "", "   ", "two words", "a\"b", "x".repeat(513)})
                assertThrows(
                        OpenRouterSignIn.RefusedCode.class,
                        () ->
                                OpenRouterSignIn.start()
                                        .exchange(
                                                pasted,
                                                server.connections(OpenRouterSignIn.EXCHANGE)));
            assertTrue(server.bodies.isEmpty());
        }
    }

    @Test
    public void aReplyWithoutAnOpenRouterKeyIsAFailureNotAKey() throws Exception {
        for (String reply :
                new String[] {"{\"key\":\"not-a-key\"}", "{}", "not json", "{\"key\":7}"}) {
            try (ImageServer server = new ImageServer().answer(200, reply)) {
                IOException failure =
                        assertThrows(
                                IOException.class,
                                () ->
                                        OpenRouterSignIn.start()
                                                .exchange(
                                                        "code",
                                                        server.connections(
                                                                OpenRouterSignIn.EXCHANGE)));
                assertFalse(failure instanceof OpenRouterSignIn.RefusedCode);
            }
        }
    }

    @Test
    public void aServerFailureIsNotBlamedOnTheCode() throws Exception {
        try (ImageServer server = new ImageServer().answer(503, "{}")) {
            IOException failure =
                    assertThrows(
                            IOException.class,
                            () ->
                                    OpenRouterSignIn.start()
                                            .exchange(
                                                    "code",
                                                    server.connections(OpenRouterSignIn.EXCHANGE)));
            assertFalse(failure instanceof OpenRouterSignIn.RefusedCode);
        }
    }
}
