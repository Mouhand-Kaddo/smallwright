package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.junit.Test;

/** The run's wait for a look: the app's answer, Stop, or a bound on Android and the app. */
public final class LookWaitTest {
    private final BlockingQueue<AppLink.Reply> reply = new ArrayBlockingQueue<>(1);
    private int closed;

    @Test
    public void theAppsAnswerEndsTheWait() throws Exception {
        AppLink.Reply picture = AppLink.Reply.picture(new byte[] {1});
        reply.offer(picture);

        assertSame(
                picture, LookActivity.await(reply, () -> true, () -> true, 300, 300, this::close));
        assertEquals(0, closed);
    }

    @Test
    public void aLookThatStartedButNeverAnswersEndsAsNotCopiedAndIsClosed() throws Exception {
        AppLink.Reply ended =
                LookActivity.await(reply, () -> true, () -> true, 300, 300, this::close);

        assertNull(ended.png);
        assertEquals(AppLink.Reply.NOT_COPIED, ended.failure);
        assertEquals(1, closed);
    }

    @Test
    public void aLookAndroidNeverStartsWasNotOpened() throws Exception {
        AppLink.Reply ended =
                LookActivity.await(reply, () -> false, () -> true, 300, 300, this::close);

        assertEquals(AppLink.Reply.NOT_OPENED, ended.failure);
    }

    @Test
    public void stopClosesTheLookAndEndsTheWait() {
        assertThrows(
                IllegalStateException.class,
                () -> LookActivity.await(reply, () -> true, () -> false, 300, 60_000, this::close));
        assertEquals(1, closed);
    }

    private void close() {
        closed++;
    }
}
