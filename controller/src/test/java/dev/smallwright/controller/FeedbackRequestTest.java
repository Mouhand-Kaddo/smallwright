package dev.smallwright.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class FeedbackRequestTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void userReportIsSeparateBoundedRequestWithExactCurrentSource() throws Exception {
        ProjectSourceStore source = new ProjectSourceStore(temporaryFolder.newFolder());
        source.initialize();
        ContextPreviewGate gate = new ContextPreviewGate(source);
        String request = FeedbackRequest.compose("After I installed it, the button did nothing.");
        ContextPreviewGate.Preview preview = gate.preview(request, 0);
        assertTrue(preview.payload().contains("User-reported app feedback"));
        assertTrue(preview.payload().contains("the button did nothing"));
        assertTrue(preview.payload().contains("MainActivity.java"));
        assertTrue(preview.payload().equals(gate.approve(preview, request, 0)));
        assertThrows(
                IllegalStateException.class,
                () ->
                        gate.approve(
                                preview,
                                FeedbackRequest.compose("After I installed it, the button worked."),
                                0));
        source.writeSource(0, "changed source");
        assertThrows(IllegalStateException.class, () -> gate.approve(preview, request, 0));
    }

    @Test
    public void blankAndOversizeReportsNeverBecomeRequests() {
        assertThrows(IllegalArgumentException.class, () -> FeedbackRequest.compose(" \n "));
        assertThrows(
                IllegalArgumentException.class, () -> FeedbackRequest.compose("é".repeat(4097)));
        String request = FeedbackRequest.compose("Button fails");
        assertFalse(request.contains("diagnostic"));
    }
}
