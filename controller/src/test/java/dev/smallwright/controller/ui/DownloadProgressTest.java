package dev.smallwright.controller.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DownloadProgressTest {
    @Test
    public void showsABarAPercentAndSizesInMegabytes() {
        assertEquals("[░░░░░░░░░░░░░░░░░░░░]   0%", DownloadProgress.bar(0, 65_878_410L));
        assertEquals("[██████████░░░░░░░░░░]  50%", DownloadProgress.bar(32_939_205L, 65_878_410L));
        assertEquals("[████████████████████] 100%", DownloadProgress.bar(65_878_410L, 65_878_410L));
        assertEquals("32.9 of 65.9 MB", DownloadProgress.sizes(32_939_205L, 65_878_410L));
    }

    @Test
    public void anUnknownTotalNeverDividesByZero() {
        assertEquals("[░░░░░░░░░░░░░░░░░░░░]   0%", DownloadProgress.bar(10, 0));
    }
}
