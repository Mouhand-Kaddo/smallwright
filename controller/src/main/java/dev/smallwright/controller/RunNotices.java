package dev.smallwright.controller;

/** Decides which run notification shows as a run works and ends. Called on the main thread. */
final class RunNotices {
    /** Where notices appear on the phone. */
    interface Shade {
        /** Shows this notice in place of any other. */
        void show(RunNotice notice);

        /** Removes every run notice. */
        void clear();
    }

    private final Shade shade;
    private RunNotice current;
    private String viewedProject;
    private String viewedChat;
    private String question;

    RunNotices(Shade shade) {
        this.shade = shade;
    }

    void started(String runId, String projectId, String projectTitle, String chatId) {
        question = null;
        current = RunNotice.working(runId, projectId, projectTitle, chatId, "Thinking");
        shade.show(current);
    }

    void step(String text) {
        if (current == null || !current.going()) return;
        question = null;
        current = current.withStep(text);
        shade.show(current);
    }

    /** The run waits for an answer; the notice says so unless that chat is on screen. */
    void asking(String text) {
        if (current == null || !current.going()) return;
        question = text;
        showQuestion();
    }

    private void showQuestion() {
        current = viewed(current) ? current.withStep(question) : current.asking(question);
        shade.show(current);
    }

    void built() {
        end(current == null ? null : current.done());
    }

    void answered(String reply) {
        end(current == null ? null : current.needsInput(reply));
    }

    void failed(String reason) {
        end(current == null ? null : current.failed(reason));
    }

    void stopped() {
        end(null);
    }

    /** Whether Stop on a notice for this run reaches the run working now. */
    boolean stops(String runId) {
        return current != null && current.going() && current.runId.equals(runId);
    }

    /** The chat on screen now; nulls when the screen is not visible. */
    void viewing(String projectId, String chatId) {
        viewedProject = projectId;
        viewedChat = chatId;
        if (current == null) return;
        if (question != null) showQuestion();
        else if (!current.going() && viewed(current)) end(null);
    }

    private boolean viewed(RunNotice notice) {
        return notice.projectId.equals(viewedProject) && notice.chatId.equals(viewedChat);
    }

    private void end(RunNotice notice) {
        question = null;
        current = notice == null || viewed(notice) ? null : notice;
        if (current == null) shade.clear();
        else shade.show(current);
    }
}
