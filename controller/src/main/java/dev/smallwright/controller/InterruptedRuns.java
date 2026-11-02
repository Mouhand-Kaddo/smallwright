package dev.smallwright.controller;

import android.util.Log;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.controller.ui.StepLog;
import dev.smallwright.project.ChatStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Tells a chat about its run that process death ended or whose chat could not be saved, when the
 * project opens again: the person's message, the builder steps it had shown and a closing status.
 * Nothing is sent to the model again, and the project stays at the revision the run last saved.
 */
final class InterruptedRuns {
    static final String ENDED =
            "The builder was closed before it finished, so this run ended here. Your project is as"
                    + " the builder last saved it; changes it had not saved yet were dropped. Send"
                    + " a message to continue.";

    static final String REPLY_UNSAVED =
            "The builder finished this run, but its reply could not be saved here. Your project is"
                    + " as the builder left it. Send a message to continue.";

    static final String REBUILDING =
            "The builder was closed while it was building your app. It is building the saved"
                    + " version again now.";

    private static boolean stoppedOnFullStorage(RunJournal.Progress run) {
        List<Step> shown = StepLog.decode(run.steps);
        if (shown.isEmpty()) return false;
        Step last = shown.get(shown.size() - 1);
        return last.getKind() == StepKind.FAILED
                && StepRun.STORAGE_FULL_STEP.equals(last.getText());
    }

    /** What the step lines may take in one chat message, not counting their traces. */
    static final int MAX_ROW_BYTES = 8 * 1024;

    /** What the traces of the steps kept may take together, before escaping. */
    static final int MAX_TRACE_BYTES = 256 * 1024;

    private InterruptedRuns() {}

    /**
     * The steps as the chat keeps them: the run's plan first, whole, then the newest steps whose
     * lines fit in {@link #MAX_ROW_BYTES}, with the newest traces that fit in {@link
     * #MAX_TRACE_BYTES}.
     */
    static String steps(List<Step> steps) {
        List<Step> work = new ArrayList<>();
        Step plan = null;
        for (Step step : steps)
            if (step.getKind() == StepKind.PLAN) plan = step;
            else work.add(step);
        String kept = work(work);
        return plan == null ? kept : StepLog.encode(List.of(plan)) + "\n" + kept;
    }

    private static String work(List<Step> steps) {
        List<Step> rows = new ArrayList<>();
        for (Step step : steps)
            rows.add(new Step(step.getKind(), step.getText(), step.getDetail()));
        int first = rows(rows);
        if (first == steps.size())
            return StepLog.encode(List.of(new Step(StepKind.FAILED, "Did not finish", "")));
        List<Step> kept = new ArrayList<>(steps.subList(first, steps.size()));
        int traces = 0;
        for (int index = kept.size() - 1; index >= 0; index--) {
            traces += kept.get(index).getTrace().getBytes(StandardCharsets.UTF_8).length;
            if (traces > MAX_TRACE_BYTES) kept.set(index, rows.get(first + index));
        }
        return StepLog.encode(kept);
    }

    /** Where the newest steps that fit in {@link #MAX_ROW_BYTES} start. */
    private static int rows(List<Step> steps) {
        int first = steps.size();
        int bytes = -1;
        while (first > 0) {
            int line =
                    StepLog.encode(List.of(steps.get(first - 1)))
                                    .getBytes(StandardCharsets.UTF_8)
                                    .length
                            + 1;
            if (bytes + line > MAX_ROW_BYTES) break;
            bytes += line;
            first--;
        }
        return first;
    }

    /** Closes this project's interrupted runs, then writes each one's messages to its chat. */
    static int reopen(RunJournal journal, ChatStore chats, String projectId, int revision)
            throws IOException {
        int closed = journal.recover(revision);
        writeUnsaved(journal, chats, projectId);
        return closed;
    }

    /**
     * Writes the messages of this project's ended runs that their chats do not hold yet. Returns
     * false when one could not be written; it is tried again next time.
     */
    static boolean writeUnsaved(RunJournal journal, ChatStore chats, String projectId) {
        List<RunJournal.Progress> pending;
        try {
            pending = journal.unsaved();
        } catch (android.database.SQLException unreadable) {
            Log.w("SmallwrightRun", "Could not read which runs' chats are saved", unreadable);
            return false;
        }
        boolean all = true;
        for (RunJournal.Progress run : pending) {
            try {
                save(chats, projectId, run, journal.load(run.runId));
            } catch (IOException unsaved) {
                Log.w(
                        "SmallwrightRun",
                        "Could not tell the chat about a run; trying next time",
                        unsaved);
                all = false;
                continue;
            } catch (IllegalArgumentException unsaved) {
                Log.w(
                        "SmallwrightRun",
                        "Could not tell the chat about an interrupted run",
                        unsaved);
            }
            try {
                journal.saved(run.runId);
            } catch (android.database.SQLException unrecorded) {
                Log.w("SmallwrightRun", "Could not record that a run's chat is saved", unrecorded);
                all = false;
            }
        }
        return all;
    }

    /**
     * Writes what the chat does not hold yet of this run, after any messages the run itself saved
     * before the process ended. Writing again adds nothing.
     */
    static void save(
            ChatStore chats, String projectId, RunJournal.Progress run, RunJournal.Snapshot latest)
            throws IOException {
        String closing =
                switch (latest.state) {
                    case BUILDING -> REBUILDING;
                    case READY_TO_TEST, ANSWERED -> REPLY_UNSAVED;
                    default -> stoppedOnFullStorage(run) ? GenerationFailure.STORAGE_FULL : ENDED;
                };
        String[][] messages = {{"user", run.request}, {"tool", run.steps}, {"status", closing}};
        int written = chats.listMessages(projectId, run.chatId).size() - run.chatSize;
        for (int index = Math.max(written, 0); index < messages.length; index++)
            chats.appendMessage(
                    projectId,
                    run.chatId,
                    messages[index][0],
                    messages[index][1],
                    latest.sourceRevision);
    }
}
