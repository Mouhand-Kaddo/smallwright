package dev.smallwright.controller;

import dev.smallwright.agent.ModelClient;
import dev.smallwright.agent.RunPlan;
import dev.smallwright.controller.ui.LiveRun;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** What the builder is doing in one run. Changed on the run's thread; observers get copies. */
final class RunSteps {
    private static final long SHOW_THINKING_NANOS = 150_000_000L;
    private static final int MAX_LIVE_THINKING_CHARS = ModelClient.MAX_REASONING_BYTES / 4;

    private final String chatId;
    private final String request;
    private final Consumer<LiveRun> publish;
    private final Consumer<LiveRun> preview;
    private final List<Step> steps = new ArrayList<>();
    private long thinkingShown = System.nanoTime() - SHOW_THINKING_NANOS;

    RunSteps(String chatId, String request, String thinking, Consumer<LiveRun> publish) {
        this(chatId, request, thinking, publish, publish);
    }

    /** As above; the model's live thinking goes only to {@code preview}, which keeps nothing. */
    RunSteps(
            String chatId,
            String request,
            String thinking,
            Consumer<LiveRun> publish,
            Consumer<LiveRun> preview) {
        this.chatId = chatId;
        this.request = request;
        this.publish = publish;
        this.preview = preview;
        steps.add(new Step(StepKind.THINKING, thinking, ""));
    }

    void add(StepKind kind, String text, String detail) {
        add(kind, text, detail, "");
    }

    private void add(StepKind kind, String text, String detail, String trace) {
        steps.add(new Step(kind, text, detail == null ? "" : detail, trace));
        publish.accept(new LiveRun(chatId, request, List.copyOf(steps)));
    }

    /**
     * Shows the model's thinking so far on the thinking step, at most every 150 ms and only its
     * newest part when long. Not saved: the finished step carries the thinking that is kept. The
     * first piece of a reply clears why the run was waiting to try again, at once.
     */
    void thought(CharSequence soFar) {
        if (!lastIs(StepKind.THINKING)) return;
        Step thinking = steps.get(steps.size() - 1);
        boolean waited = !thinking.getDetail().isEmpty();
        long now = System.nanoTime();
        if (!waited && now - thinkingShown < SHOW_THINKING_NANOS) return;
        String shown = newest(soFar);
        if (!waited && shown.equals(thinking.getTrace())) return;
        thinkingShown = now;
        showThinking("", shown);
    }

    private void showThinking(String detail, String trace) {
        steps.set(
                steps.size() - 1,
                new Step(StepKind.THINKING, steps.get(steps.size() - 1).getText(), detail, trace));
        preview.accept(new LiveRun(chatId, request, List.copyOf(steps)));
    }

    private static String newest(CharSequence text) {
        if (text.length() <= MAX_LIVE_THINKING_CHARS) return text.toString();
        int start = text.length() - MAX_LIVE_THINKING_CHARS;
        if (Character.isLowSurrogate(text.charAt(start))) start++;
        return "…" + text.subSequence(start, text.length());
    }

    /**
     * The thinking step says why the run waits to send its request again, on screen only; thinking
     * starts over.
     */
    void retrying(String detail) {
        if (lastIs(StepKind.THINKING)) showThinking(detail, "");
    }

    void finishLast(StepKind kind, String text, String detail) {
        finishLast(kind, text, detail, "");
    }

    /** Replaces the last step with one that carries the model's thinking as its trace. */
    void finishLast(StepKind kind, String text, String detail, String trace) {
        if (!steps.isEmpty()) steps.remove(steps.size() - 1);
        add(kind, text, detail, trace);
    }

    /**
     * Finishes the look step with the picture sent to the model; an earlier look's picture is
     * dropped, as the model keeps only the newest.
     */
    void looked(String text, byte[] jpeg) {
        steps.replaceAll(
                step ->
                        step.getPicture() == null
                                ? step
                                : new Step(
                                        step.getKind(),
                                        step.getText(),
                                        step.getDetail(),
                                        step.getTrace()));
        if (!steps.isEmpty()) steps.remove(steps.size() - 1);
        steps.add(new Step(StepKind.DONE, text, "", "", jpeg));
        publish.accept(new LiveRun(chatId, request, List.copyOf(steps)));
    }

    /** Shows a step in place of the thinking line, or after the steps so far. */
    void show(StepKind kind, String text, String detail) {
        if (lastIs(StepKind.THINKING)) finishLast(kind, text, detail);
        else add(kind, text, detail);
    }

    /** Shows the run's plan in place of the one shown before, as one plan step. */
    void plan(RunPlan plan) {
        steps.removeIf(step -> step.getKind() == StepKind.PLAN);
        if (lastIs(StepKind.THINKING)) steps.remove(steps.size() - 1);
        add(StepKind.PLAN, plan.progress(), "", plan.saved());
    }

    boolean lastIs(StepKind kind) {
        return !steps.isEmpty() && steps.get(steps.size() - 1).getKind() == kind;
    }

    void stopUnfinished(String text) {
        if (lastIs(StepKind.THINKING) || lastIs(StepKind.RUNNING) || lastIs(StepKind.ASKING))
            finishLast(StepKind.FAILED, text, "");
    }

    /** The steps as the chat saves them: the newest that fit in one chat message. */
    String saved() {
        return InterruptedRuns.steps(steps);
    }
}
