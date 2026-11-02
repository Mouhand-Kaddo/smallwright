package dev.smallwright.controller;

import dev.smallwright.agent.ModelClient;
import dev.smallwright.agent.ModelProvider;
import java.util.function.BooleanSupplier;

/**
 * Sends a run's model request again when it failed in a way another try can get past: up to three
 * more tries, after 2, 5 and 15 seconds, or after the wait the provider asked for, at most a
 * minute. The same messages go again; nothing from a failed try was used. No try is sent again once
 * the run is past the limits the person set. The thinking step says why while it waits, and Stop
 * ends the wait at once.
 */
final class RequestRetry {
    private static final long[] WAITS_MILLIS = {2_000, 5_000, 15_000};
    private static final long MAX_WAIT_MILLIS = 60_000;
    private static final long CHECK_STOP_MILLIS = 200;

    static RequestRetry standard(ModelProvider provider) {
        return new RequestRetry(Thread::sleep, provider);
    }

    interface Pause {
        void sleep(long millis) throws InterruptedException;
    }

    interface Request {
        ModelClient.StepReply send() throws Exception;
    }

    private final Pause pause;
    private final ModelProvider provider;

    RequestRetry(Pause pause, ModelProvider provider) {
        this.pause = pause;
        this.provider = java.util.Objects.requireNonNull(provider);
    }

    ModelClient.StepReply send(
            Request request, RunSteps steps, BooleanSupplier active, BooleanSupplier withinLimits)
            throws Exception {
        for (int retry = 1; ; retry++) {
            try {
                return request.send();
            } catch (ModelClient.RequestFailed failed) {
                if (failed.retry == ModelClient.RequestFailed.Retry.NO
                        || retry > WAITS_MILLIS.length
                        || !active.getAsBoolean()
                        || !withinLimits.getAsBoolean()) throw failed;
                steps.retrying(
                        why(failed.retry)
                                + " Trying again ("
                                + retry
                                + " of "
                                + WAITS_MILLIS.length
                                + ")…");
                waitOrStop(
                        failed.retryAfterSeconds >= 0
                                ? Math.min(failed.retryAfterSeconds * 1000L, MAX_WAIT_MILLIS)
                                : WAITS_MILLIS[retry - 1],
                        active);
            }
        }
    }

    private String why(ModelClient.RequestFailed.Retry retry) {
        return switch (retry) {
            case CONNECTION -> "Connection dropped.";
            case BUSY -> provider.label + " is busy.";
            case REPLY, NO -> "The model's answer came out garbled.";
        };
    }

    private void waitOrStop(long millis, BooleanSupplier active) {
        try {
            for (long left = millis; ; left -= CHECK_STOP_MILLIS) {
                if (!active.getAsBoolean() || Thread.currentThread().isInterrupted())
                    throw new IllegalStateException("Run is no longer active");
                if (left <= 0) return;
                pause.sleep(Math.min(CHECK_STOP_MILLIS, left));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Run is no longer active", interrupted);
        }
    }
}
