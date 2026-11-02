package dev.smallwright.worker;

import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import java.io.IOException;
import java.io.InputStream;

/** Owns one transferred descriptor; the absolute deadline never resets on incoming bytes. */
final class SnapshotInputStream extends InputStream {
    static final class Timeout extends IOException {
        Timeout() {
            super("Snapshot transfer timed out");
        }
    }

    private final ParcelFileDescriptor descriptor;
    private final long deadline;
    private final StructPollfd[] poll;

    SnapshotInputStream(ParcelFileDescriptor descriptor, long timeoutMillis) throws IOException {
        this.descriptor = descriptor;
        deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        try {
            int flags = Os.fcntlInt(descriptor.getFileDescriptor(), OsConstants.F_GETFL, 0);
            Os.fcntlInt(
                    descriptor.getFileDescriptor(),
                    OsConstants.F_SETFL,
                    flags | OsConstants.O_NONBLOCK);
        } catch (ErrnoException failure) {
            throw new IOException("Could not prepare snapshot transfer", failure);
        }
        StructPollfd entry = new StructPollfd();
        entry.fd = descriptor.getFileDescriptor();
        entry.events = (short) (OsConstants.POLLIN | OsConstants.POLLHUP);
        poll = new StructPollfd[] {entry};
    }

    @Override
    public int read() throws IOException {
        byte[] value = new byte[1];
        return read(value, 0, 1) == -1 ? -1 : value[0] & 0xff;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset > buffer.length - length)
            throw new IndexOutOfBoundsException();
        if (length == 0) return 0;
        while (true) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0) throw new Timeout();
            if (Thread.currentThread().isInterrupted())
                throw new IOException("Snapshot transfer interrupted");
            try {
                int count = Os.read(descriptor.getFileDescriptor(), buffer, offset, length);
                return count == 0 ? -1 : count;
            } catch (ErrnoException failure) {
                if (failure.errno == OsConstants.EINTR) continue;
                if (failure.errno != OsConstants.EAGAIN)
                    throw new IOException("Could not read snapshot transfer", failure);
            }
            try {
                Os.poll(poll, (int) Math.min(remaining, Integer.MAX_VALUE));
            } catch (ErrnoException failure) {
                if (failure.errno != OsConstants.EINTR)
                    throw new IOException("Could not wait for snapshot transfer", failure);
            }
        }
    }

    @Override
    public void close() throws IOException {
        descriptor.close();
    }
}
