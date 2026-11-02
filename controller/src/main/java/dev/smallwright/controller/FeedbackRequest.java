package dev.smallwright.controller;

import java.nio.charset.StandardCharsets;

/** User-entered observations only; no implicit install, crash, or device evidence. */
final class FeedbackRequest {
    private static final int MAX_FEEDBACK_BYTES = 4096;
    private static final String PREFIX =
            "User-reported app feedback. "
                    + "Use this observation to revise the current Java source or its existing XML layout, preserving other behavior. "
                    + "Any installation or testing described here is reported by the user.\n\n";

    private FeedbackRequest() {}

    static String compose(String feedback) {
        if (feedback == null || feedback.trim().isEmpty())
            throw new IllegalArgumentException("Enter test feedback first");
        if (feedback.getBytes(StandardCharsets.UTF_8).length > MAX_FEEDBACK_BYTES)
            throw new IllegalArgumentException("Test feedback is too long");
        return PREFIX + feedback;
    }
}
