package dev.smallwright.controller;

import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.libraries.Library;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Asks the person a yes-or-no question in the chat and holds the run until they answer: before a
 * library or a skill is added. There is no time limit; Stop ends the wait.
 */
final class RunQuestion implements LibraryTools.Permission, SkillAdding.Permission {
    private static final long CHECK_STOP_MILLIS = 200;

    private final RunSteps steps;
    private final BooleanSupplier active;
    private final BlockingQueue<Boolean> answers = new ArrayBlockingQueue<>(1);
    private volatile boolean waiting;

    RunQuestion(RunSteps steps, BooleanSupplier active) {
        this.steps = steps;
        this.active = active;
    }

    @Override
    public boolean allows(Library library) {
        boolean yes =
                asks(
                        "Add the library "
                                + library.artifact.substring(0, 1).toUpperCase(Locale.ROOT)
                                + library.artifact.substring(1)
                                + " ("
                                + library.key()
                                + " "
                                + library.version
                                + ")?",
                        "It may bring in other libraries.");
        if (yes) steps.finishLast(StepKind.RUNNING, "Adding library " + library.key(), "");
        return yes;
    }

    @Override
    public boolean asks(String question, String detail) {
        answers.clear();
        waiting = true;
        steps.finishLast(StepKind.ASKING, question, detail);
        try {
            Boolean answer = null;
            while (answer == null) {
                if (!active.getAsBoolean()) throw new IllegalStateException("Run stopped");
                answer = answers.poll(CHECK_STOP_MILLIS, TimeUnit.MILLISECONDS);
            }
            return answer;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Run stopped", interrupted);
        } finally {
            waiting = false;
        }
    }

    /** The person's answer; one that comes when no question is waiting is ignored. */
    void answer(boolean yes) {
        if (waiting) answers.offer(yes);
    }
}
