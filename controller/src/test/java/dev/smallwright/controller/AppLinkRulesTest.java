package dev.smallwright.controller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.CRC32;
import org.junit.Test;

public final class AppLinkRulesTest {
    private static final String APP =
            "dev.smallwright.generated.app0123456789abcdef0123456789abcdef";
    private static final byte[] PROJECT_CERT = {1, 2, 3, 4};
    private static final byte[] OTHER_CERT = {1, 2, 3, 5};

    @Test
    public void acceptsOnlyTheProjectPackageSignedByThatProjectAlone() {
        assertTrue(AppLinkRules.acceptsSender(APP, APP, new byte[][] {PROJECT_CERT}, PROJECT_CERT));
        assertFalse(AppLinkRules.acceptsSender(APP, APP, new byte[][] {OTHER_CERT}, PROJECT_CERT));
        assertFalse(
                AppLinkRules.acceptsSender(
                        APP, APP, new byte[][] {PROJECT_CERT, OTHER_CERT}, PROJECT_CERT));
        assertFalse(AppLinkRules.acceptsSender(APP, APP, new byte[0][], PROJECT_CERT));
        assertFalse(AppLinkRules.acceptsSender(APP, APP, null, PROJECT_CERT));
        assertFalse(AppLinkRules.acceptsSender(APP, APP, new byte[][] {PROJECT_CERT}, null));
        assertFalse(
                AppLinkRules.acceptsSender(
                        "com.example.other", APP, new byte[][] {PROJECT_CERT}, PROJECT_CERT));
        assertFalse(
                AppLinkRules.acceptsSender(null, APP, new byte[][] {PROJECT_CERT}, PROJECT_CERT));
        assertFalse(
                AppLinkRules.acceptsSender(
                        ProjectSourceStore.PACKAGE_NAME,
                        ProjectSourceStore.PACKAGE_NAME,
                        new byte[][] {PROJECT_CERT},
                        PROJECT_CERT));
    }

    @Test
    public void readsTheSizeOfAWellFormedPng() throws Exception {
        assertArrayEquals(new int[] {720, 1568}, AppLinkRules.pngSize(png(720, 1568)));
    }

    @Test
    public void refusesWhatIsNotABoundedSanePng() {
        assertThrows(IOException.class, () -> AppLinkRules.pngSize(null));
        assertThrows(IOException.class, () -> AppLinkRules.pngSize(new byte[0]));
        assertThrows(
                IOException.class,
                () -> AppLinkRules.pngSize(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        byte[] jpegHeaded = png(10, 10);
        jpegHeaded[0] = (byte) 0xFF;
        assertThrows(IOException.class, () -> AppLinkRules.pngSize(jpegHeaded));
        assertThrows(IOException.class, () -> AppLinkRules.pngSize(png(0, 10)));
        assertThrows(IOException.class, () -> AppLinkRules.pngSize(png(10, 4097)));
        assertThrows(IOException.class, () -> AppLinkRules.pngSize(png(-1, 10)));
        byte[] badCrc = png(10, 10);
        badCrc[29] ^= 1;
        assertThrows(IOException.class, () -> AppLinkRules.pngSize(badCrc));
        byte[] truncated = java.util.Arrays.copyOf(png(10, 10), 40);
        assertThrows(IOException.class, () -> AppLinkRules.pngSize(truncated));
        assertThrows(
                IOException.class,
                () -> AppLinkRules.pngSize(new byte[AppLinkRules.MAX_PNG_BYTES + 1]));
    }

    @Test
    public void refusesAPngWithMoreChunksThanAPhoneScreenshotCouldNeed() throws Exception {
        int dataChunks = AppLinkRules.MAX_PNG_CHUNKS - 2;
        assertArrayEquals(new int[] {10, 10}, AppLinkRules.pngSize(png(10, 10, dataChunks)));
        IOException refused =
                assertThrows(
                        IOException.class, () -> AppLinkRules.pngSize(png(10, 10, dataChunks + 1)));
        assertTrue(refused.getMessage(), refused.getMessage().contains("too many parts"));
    }

    @Test
    public void opensTheLauncherOrADeclaredActivityByAnyOfItsNames() throws Exception {
        List<String> declared = List.of(APP + ".MainActivity", APP + ".detail.DetailActivity");
        assertEquals(
                APP + ".MainActivity",
                AppLinkRules.target(null, declared, APP + ".MainActivity", APP));
        assertEquals(
                APP + ".detail.DetailActivity",
                AppLinkRules.target(
                        ".detail.DetailActivity", declared, APP + ".MainActivity", APP));
        assertEquals(
                APP + ".detail.DetailActivity",
                AppLinkRules.target(
                        APP + ".detail.DetailActivity", declared, APP + ".MainActivity", APP));
        assertEquals(
                APP + ".detail.DetailActivity",
                AppLinkRules.target("DetailActivity", declared, APP + ".MainActivity", APP));
    }

    @Test
    public void refusesAnActivityTheAppDoesNotDeclareOrTheBuilderOwns() {
        List<String> declared =
                List.of(APP + ".MainActivity", APP + ".builderlink.BuilderLink$Look");
        AppLinkRules.Refused missing =
                assertThrows(
                        AppLinkRules.Refused.class,
                        () ->
                                AppLinkRules.target(
                                        "Settings", declared, APP + ".MainActivity", APP));
        assertTrue(missing.getMessage(), missing.getMessage().contains(".MainActivity"));
        assertFalse(missing.getMessage(), missing.getMessage().contains("builderlink"));
        assertThrows(
                AppLinkRules.Refused.class,
                () ->
                        AppLinkRules.target(
                                ".builderlink.BuilderLink$Look",
                                declared,
                                APP + ".MainActivity",
                                APP));
        assertThrows(
                AppLinkRules.Refused.class, () -> AppLinkRules.target(null, declared, null, APP));
    }

    @Test
    public void acceptsOnlyTheLookPictureOfThatApp() {
        assertTrue(AppLinkRules.isLookUri("content", APP + ".builderlink", "/look.png", null, APP));
        assertFalse(AppLinkRules.isLookUri("file", APP + ".builderlink", "/look.png", null, APP));
        assertFalse(
                AppLinkRules.isLookUri(
                        "content", "com.example.builderlink", "/look.png", null, APP));
        assertFalse(
                AppLinkRules.isLookUri("content", APP + ".builderlink", "/../look.png", null, APP));
        assertFalse(
                AppLinkRules.isLookUri("content", APP + ".builderlink", "/look.png", "a=1", APP));
        assertFalse(AppLinkRules.isLookUri("content", null, "/look.png", null, APP));
    }

    @Test
    public void readsAtMostTheBoundFromTheApp() throws Exception {
        byte[] small = new byte[100];
        assertEquals(100, AppLinkRules.readBounded(new java.io.ByteArrayInputStream(small)).length);
        assertThrows(
                IOException.class,
                () ->
                        AppLinkRules.readBounded(
                                new java.io.ByteArrayInputStream(
                                        new byte[AppLinkRules.MAX_PNG_BYTES + 1])));
        assertThrows(IOException.class, () -> AppLinkRules.readBounded(null));
    }

    static byte[] png(int width, int height) {
        return png(width, height, 1);
    }

    private static byte[] png(int width, int height, int dataChunks) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            header.write(intBytes(width));
            header.write(intBytes(height));
            header.write(new byte[] {8, 6, 0, 0, 0});
            chunk(out, "IHDR", header.toByteArray());
            chunk(out, "IDAT", new byte[] {0x78, (byte) 0x9C, 0x03, 0x00, 0x00, 0x00, 0x00, 0x01});
            for (int index = 1; index < dataChunks; index++) chunk(out, "IDAT", new byte[0]);
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data)
            throws IOException {
        out.write(intBytes(data.length));
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(name);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        out.write(intBytes((int) crc.getValue()));
    }

    private static byte[] intBytes(int value) {
        return new byte[] {
            (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
        };
    }
}
