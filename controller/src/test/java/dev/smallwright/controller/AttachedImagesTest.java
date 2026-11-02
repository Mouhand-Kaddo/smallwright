package dev.smallwright.controller;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.smallwright.agent.ModelChoice;
import dev.smallwright.project.ChatStore;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.Test;

public final class AttachedImagesTest {
    static byte[] jpeg(int size, int seed) {
        byte[] bytes = new byte[size];
        for (int index = 0; index < size; index++) bytes[index] = (byte) (index * 31 + seed);
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }

    @Test
    public void withoutImagesTheUserContentIsThePlainText() {
        JsonElement content = AttachedImages.NONE.userContent("Make a timer");
        assertTrue(content.isJsonPrimitive());
        assertEquals("Make a timer", content.getAsString());
    }

    @Test
    public void imagesFollowTheTextAsDataUrlPartsInTheOrderPicked() {
        byte[] first = jpeg(40, 1);
        byte[] second = jpeg(50, 2);
        JsonArray parts =
                new AttachedImages(List.of(first, second))
                        .userContent("What is this?")
                        .getAsJsonArray();
        assertEquals(3, parts.size());
        JsonObject text = parts.get(0).getAsJsonObject();
        assertEquals("text", text.get("type").getAsString());
        assertEquals("What is this?", text.get("text").getAsString());
        for (int index = 0; index < 2; index++) {
            JsonObject part = parts.get(index + 1).getAsJsonObject();
            assertEquals("image_url", part.get("type").getAsString());
            assertEquals(
                    "data:image/jpeg;base64,"
                            + Base64.getEncoder().encodeToString(index == 0 ? first : second),
                    part.getAsJsonObject("image_url").get("url").getAsString());
        }
    }

    @Test
    public void refusesMoreThanFourImagesOversizedOrNonJpegBytesAndEmptyImages() {
        byte[] small = jpeg(10, 0);
        assertEquals(4, AttachedImages.MAX_COUNT);
        new AttachedImages(List.of(small, small, small, small));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AttachedImages(List.of(small, small, small, small, small)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AttachedImages(List.of(jpeg(ChatStore.MAX_IMAGE_BYTES + 1, 0))));
        new AttachedImages(List.of(jpeg(ChatStore.MAX_IMAGE_BYTES, 0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AttachedImages(List.of("PNG not jpeg".getBytes())));
        assertThrows(
                IllegalArgumentException.class, () -> new AttachedImages(List.of(new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> new AttachedImages(null));
    }

    @Test
    public void keepsItsOwnCopyOfThePictureBytes() {
        byte[] picked = jpeg(20, 3);
        AttachedImages images = new AttachedImages(List.of(picked));
        String before = images.digest();
        picked[5] = 99;
        images.bytes().get(0)[6] = 77;
        assertEquals(before, images.digest());
        assertFalse(Arrays.equals(picked, images.bytes().get(0)));
    }

    @Test
    public void theDigestChangesWithAnyByteOrTheOrder() {
        byte[] first = jpeg(30, 4);
        byte[] second = jpeg(30, 5);
        String digest = new AttachedImages(List.of(first, second)).digest();
        assertEquals(digest, new AttachedImages(List.of(first.clone(), second.clone())).digest());
        assertNotEquals(digest, new AttachedImages(List.of(second, first)).digest());
        byte[] changed = first.clone();
        changed[20]++;
        assertNotEquals(digest, new AttachedImages(List.of(changed, second)).digest());
        assertNotEquals(digest, new AttachedImages(List.of(first)).digest());
        assertNotEquals(digest, AttachedImages.NONE.digest());
    }

    @Test
    public void shrinksTheLongestSideTo1568AndKeepsTheShape() {
        assertArrayEquals(new int[] {1568, 1176}, AttachedImages.fitWithin(4000, 3000));
        assertArrayEquals(new int[] {376, 1568}, AttachedImages.fitWithin(1200, 5000));
        assertArrayEquals(new int[] {1000, 800}, AttachedImages.fitWithin(1000, 800));
        assertArrayEquals(new int[] {1568, 1568}, AttachedImages.fitWithin(1568, 1568));
        assertArrayEquals(new int[] {1568, 1}, AttachedImages.fitWithin(8000, 2));
        assertThrows(IllegalArgumentException.class, () -> AttachedImages.fitWithin(0, 10));
        assertThrows(IllegalArgumentException.class, () -> AttachedImages.fitWithin(10, -1));
    }

    @Test
    public void picturesForAServerModelAreRefusedPlainly() {
        ModelChoice served =
                ModelChoice.atServer(
                        dev.smallwright.agent.ModelProvider.openAiCompatible(
                                "https://api.example.com/v1"),
                        "gpt-5.5",
                        131_072);
        AttachedImages one = new AttachedImages(List.of(jpeg(10, 6)));
        assertNull(AttachedImages.NONE.refusal(served));
        String refusal = one.refusal(served);
        assertTrue(refusal, refusal.contains("OpenRouter"));
        assertTrue(refusal, refusal.contains("Nothing was sent"));
    }

    @Test
    public void aModelThatCannotReadImagesIsRefusedOnlyWhenImagesAreAttached() {
        ModelChoice textOnly =
                new ModelChoice(
                        "vendor/text-only",
                        "vendor/text-only",
                        "Vendor: Text Only",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        131_072,
                        true,
                        false,
                        0);
        AttachedImages one = new AttachedImages(List.of(jpeg(10, 6)));
        assertNull(AttachedImages.NONE.refusal(textOnly));
        assertNull(one.refusal(ModelChoice.DEFAULT));
        String refusal = one.refusal(textOnly);
        assertTrue(refusal, refusal.contains("Text Only"));
        assertTrue(refusal, refusal.contains("Nothing was sent"));
    }
}
