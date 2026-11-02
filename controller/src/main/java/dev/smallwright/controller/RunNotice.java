package dev.smallwright.controller;

/**
 * What the run notification says about one run, and which chat a tap opens. The title names the
 * state; the project goes in the notification's header.
 */
final class RunNotice {
    enum Kind {
        WORKING,
        ASKING,
        NEEDS_INPUT,
        DONE,
        FAILED
    }

    final Kind kind;
    final String runId;
    final String projectId;
    final String projectTitle;
    final String chatId;
    final String title;
    final String text;

    private RunNotice(
            Kind kind,
            String runId,
            String projectId,
            String projectTitle,
            String chatId,
            String title,
            String text) {
        this.kind = kind;
        this.runId = runId;
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.chatId = chatId;
        this.title = title;
        this.text = text;
    }

    static RunNotice working(
            String runId, String projectId, String projectTitle, String chatId, String step) {
        return new RunNotice(Kind.WORKING, runId, projectId, projectTitle, chatId, "Working", step);
    }

    RunNotice asking(String question) {
        return new RunNotice(
                Kind.ASKING, runId, projectId, projectTitle, chatId, "Needs your input", question);
    }

    /** The run is still going: working, or waiting for an answer. */
    boolean going() {
        return kind == Kind.WORKING || kind == Kind.ASKING;
    }

    RunNotice done() {
        return ended(Kind.DONE, "Your app is ready", "Tap to install it.");
    }

    RunNotice needsInput(String reply) {
        return ended(Kind.NEEDS_INPUT, "Needs your input", firstLine(reply));
    }

    RunNotice failed(String reason) {
        return ended(Kind.FAILED, "Did not finish", reason);
    }

    private static String firstLine(String words) {
        if (words != null)
            for (String line : words.split("\n")) if (!line.isBlank()) return line.trim();
        return "The builder replied.";
    }

    private RunNotice ended(Kind kind, String title, String text) {
        return new RunNotice(kind, runId, projectId, projectTitle, chatId, title, text);
    }

    RunNotice withStep(String step) {
        return working(runId, projectId, projectTitle, chatId, step);
    }
}
