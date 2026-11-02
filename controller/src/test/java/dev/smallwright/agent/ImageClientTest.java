package dev.smallwright.agent;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.Test;

/** The app icon image request, against a local stand-in for OpenRouter. */
public final class ImageClientTest {
    private static final byte[] KEY = "dummy-key".getBytes(StandardCharsets.US_ASCII);

    private static ImageClient.Image make(ImageServer server) throws IOException {
        return server.client()
                .make(
                        KEY,
                        "A green leaf",
                        ImageClient.Shape.SQUARE,
                        new ModelClient.Cancellation());
    }

    @Test
    public void asksForTheAspectRatioOfEachShape() throws Exception {
        try (ImageServer server = new ImageServer().png(ImageServer.png(256, 256), "0.04")) {
            for (ImageClient.Shape shape : ImageClient.Shape.values())
                server.client().make(KEY, "A door", shape, new ModelClient.Cancellation());
            String[] ratios = new String[server.bodies.size()];
            for (int index = 0; index < ratios.length; index++)
                ratios[index] =
                        JsonParser.parseString(server.bodies.get(index))
                                .getAsJsonObject()
                                .getAsJsonObject("image_config")
                                .get("aspect_ratio")
                                .getAsString();
            assertArrayEquals(new String[] {"1:1", "16:9", "9:16"}, ratios);
        }
    }

    @Test
    public void acceptsAReplyNamingTheModelsDatedVersion() throws Exception {
        byte[] png = ImageServer.png(256, 256);
        try (ImageServer server = new ImageServer()) {
            server.answer(
                    200,
                    ImageServer.reply(
                                    "google/gemini-3.1-flash-lite-image-20260630",
                                    ImageServer.dataUrl(png),
                                    "0.04")
                            .toString());
            assertArrayEquals(png, make(server).bytes);
        }
    }

    @Test
    public void asksTheImageModelForOneSquareImageAndReturnsTheCheckedPng() throws Exception {
        byte[] png = ImageServer.png(256, 256);
        try (ImageServer server = new ImageServer().png(png, "0.0391")) {
            ImageClient.Image image = make(server);
            assertArrayEquals(png, image.bytes);
            assertNull(image.refusal);
            assertEquals(new BigDecimal("0.0391"), image.costUsd);
            assertEquals(1330, image.totalTokens);
            assertEquals(40, image.promptTokens);
            assertEquals(1, server.bodies.size());
            assertEquals("Bearer dummy-key", server.authorizations.get(0));
            JsonObject body = JsonParser.parseString(server.bodies.get(0)).getAsJsonObject();
            assertEquals("google/gemini-3.1-flash-lite-image", body.get("model").getAsString());
            assertEquals(JsonParser.parseString("[\"image\",\"text\"]"), body.get("modalities"));
            assertEquals(
                    JsonParser.parseString("{\"aspect_ratio\":\"1:1\"}"), body.get("image_config"));
            assertFalse(body.get("stream").getAsBoolean());
            assertFalse(body.has("tools"));
            assertEquals(
                    JsonParser.parseString("[{\"role\":\"user\",\"content\":\"A green leaf\"}]"),
                    body.get("messages"));
        }
    }

    @Test
    public void acceptsJpegAndWebpWhoseBytesAreWhatTheReplySays() throws Exception {
        byte[] jpeg = new byte[64];
        jpeg[0] = (byte) 0xff;
        jpeg[1] = (byte) 0xd8;
        jpeg[2] = (byte) 0xff;
        byte[] webp = new byte[64];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBPVP8 ".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 8);
        String[] types = {"image/jpeg", "image/webp", "image/jpg", "IMAGE/JPEG"};
        byte[][] images = {jpeg, webp, jpeg, jpeg};
        for (int index = 0; index < types.length; index++)
            try (ImageServer server =
                    new ImageServer()
                            .image(
                                    "data:" + types[index] + ";base64," + encoded(images[index]),
                                    "0.04")) {
                ImageClient.Image image = make(server);
                assertNull(image.refusal);
                assertArrayEquals(images[index], image.bytes);
            }
    }

    private static String encoded(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    public void anUnusableImageIsRefusedButItsCostIsStillReported() throws Exception {
        String[][] cases = {
            {
                "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(new byte[] {1, 2}),
                "could not be read"
            },
            {
                ImageServer.dataUrl("not a png at all".getBytes(StandardCharsets.UTF_8)),
                "could not be read"
            },
            {"data:image/webp;base64," + encoded(ImageServer.png(256, 256)), "could not be read"},
            {"data:image/png;base64,!!!not-base64!!!", "could not be read"},
            {"data:image/gif;base64," + encoded(new byte[64]), "image/gif"},
            {"data:image/x\u0000evil;base64,AAAA", "\"image/x?evil\""},
            {"data:image/jpeg;base64," + encoded(ImageServer.png(256, 256)), "could not be read"},
            {"data:image/webp;base64," + encoded(new byte[8]), "could not be read"},
            {ImageServer.dataUrl(ImageServer.png(32, 32)), "too small"},
            {ImageServer.dataUrl(ImageServer.header(5000, 5000)), "too large"},
            {ImageServer.dataUrl(ImageServer.header(4096, 0)), "too small"},
            {"https://images.example/icon.png", "cannot use"},
            {null, "no image"},
            {ImageServer.dataUrl(new byte[ImageClient.MAX_PNG_BYTES + 1]), "too large"}
        };
        for (String[] unusable : cases)
            try (ImageServer server = new ImageServer().image(unusable[0], "0.02")) {
                ImageClient.Image image = make(server);
                assertNull(unusable[1], image.bytes);
                assertTrue(image.refusal, image.refusal.contains(unusable[1]));
                assertEquals(new BigDecimal("0.02"), image.costUsd);
            }
    }

    @Test
    public void refusesFailedRequestsAndRepliesWithoutAUsableCost() throws Exception {
        String png = ImageServer.dataUrl(ImageServer.png(128, 128));
        try (ImageServer server = new ImageServer()) {
            server.answer(402, "{\"error\":{\"message\":\"dummy-key has no credit\"}}");
            IOException credit = assertThrows(IOException.class, () -> make(server));
            assertEquals(ModelClient.CREDIT_UNAVAILABLE, credit.getMessage());
            server.answer(200, "{\"error\":{\"message\":\"boom\"}}");
            assertThrows(IOException.class, () -> make(server));
            server.image(png, "-0.01");
            assertThrows(IOException.class, () -> make(server));
            JsonObject noCost = ImageServer.reply(ImageClient.MODEL, png, "0.01");
            noCost.getAsJsonObject("usage").remove("cost");
            server.answer(200, noCost.toString());
            assertThrows(IOException.class, () -> make(server));
            server.answer(200, ImageServer.reply("openai/gpt-image-1", png, "0.01").toString());
            assertThrows(IOException.class, () -> make(server));
            server.answer(200, "{\"model\":\"x\"} trailing");
            assertThrows(IOException.class, () -> make(server));
            server.answer(200, "x".repeat(ImageClient.MAX_RESPONSE_BYTES + 1));
            IOException tooLarge = assertThrows(IOException.class, () -> make(server));
            assertEquals("Model response exceeds limit", tooLarge.getMessage());
        }
    }

    @Test
    public void refusesAnEmptyOrOversizedPromptBeforeSending() throws Exception {
        try (ImageServer server = new ImageServer().png(ImageServer.png(64, 64), "0.01")) {
            ModelClient.Cancellation cancellation = new ModelClient.Cancellation();
            assertThrows(
                    IOException.class,
                    () -> server.client().make(KEY, " ", ImageClient.Shape.SQUARE, cancellation));
            assertThrows(
                    IOException.class,
                    () ->
                            server.client()
                                    .make(
                                            KEY,
                                            "x".repeat(ImageClient.MAX_PROMPT_CHARS + 1),
                                            ImageClient.Shape.SQUARE,
                                            cancellation));
            assertEquals(0, server.bodies.size());
        }
    }

    @Test
    public void anImageRequestWaitsForTheModelWithoutATimeLimitUntilStop() throws Exception {
        java.util.concurrent.CountDownLatch reading = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch disconnected =
                new java.util.concurrent.CountDownLatch(1);
        ModelProtocolTest.FakeConnection working =
                new ModelProtocolTest.FakeConnection(200, "") {
                    @Override
                    public void disconnect() {
                        disconnected.countDown();
                    }

                    @Override
                    public java.io.InputStream getInputStream() {
                        return new java.io.InputStream() {
                            @Override
                            public int read() throws IOException {
                                reading.countDown();
                                try {
                                    disconnected.await();
                                } catch (InterruptedException interrupted) {
                                    throw new java.io.InterruptedIOException();
                                }
                                throw new java.net.SocketException("closed");
                            }
                        };
                    }
                };
        ModelClient.Cancellation cancellation = new ModelClient.Cancellation();
        Thread stop =
                new Thread(
                        () -> {
                            try {
                                reading.await();
                                cancellation.cancel();
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                        });
        stop.start();
        IOException stopped =
                assertThrows(
                        IOException.class,
                        () ->
                                new ImageClient(url -> working)
                                        .make(
                                                KEY,
                                                "A leaf",
                                                ImageClient.Shape.SQUARE,
                                                cancellation));
        stop.join();
        assertEquals("Model request cancelled", stopped.getMessage());
        assertEquals(0, working.getReadTimeout());
        assertEquals(10_000, working.getConnectTimeout());
    }
}
