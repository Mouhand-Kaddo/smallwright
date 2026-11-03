package dev.smallwright.worker;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** Debug-only experiment: reports what an isolated worker process may do on this phone. */
public final class IsolatedProbeService extends Service {
    static final int REPORT = IBinder.FIRST_CALL_TRANSACTION;

    private final Binder binder =
            new Binder() {
                @Override
                protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                        throws android.os.RemoteException {
                    if (code != REPORT) return super.onTransact(code, data, reply, flags);
                    ParcelFileDescriptor handoff = data.readFileDescriptor();
                    String hostPrivatePath = data.readString();
                    reply.writeString(report(handoff, hostPrivatePath));
                    return true;
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private String report(ParcelFileDescriptor handoff, String hostPrivatePath) {
        StringBuilder out = new StringBuilder();
        out.append("isolated uid=")
                .append(Process.myUid())
                .append(" isIsolated=")
                .append(Process.isIsolated())
                .append(" appUid=")
                .append(getApplicationInfo().uid)
                .append('\n');
        attempt(out, "network", this::connect);
        attempt(out, "write cacheDir", () -> writeInto(getCacheDir()));
        attempt(out, "write noBackupFilesDir", () -> writeInto(getNoBackupFilesDir()));
        attempt(out, "write filesDir", () -> writeInto(getFilesDir()));
        attempt(out, "write codeCacheDir", () -> writeInto(getCodeCacheDir()));
        attempt(
                out,
                "write java.io.tmpdir",
                () -> writeInto(new File(System.getProperty("java.io.tmpdir"))));
        attempt(
                out,
                "read host-private file",
                () -> new String(Files.readAllBytes(new File(hostPrivatePath).toPath())));
        attempt(
                out,
                "reopen handoff by /proc/self/fd path",
                () -> {
                    try (FileOutputStream output =
                            new FileOutputStream("/proc/self/fd/" + handoff.getFd())) {
                        return "ok";
                    }
                });
        attempt(out, "write handoff descriptor", () -> writeDescriptor(handoff));
        attempt(out, "memfd and /proc/self/fd reopen", this::memfdRoundTrip);
        String aapt2 = getApplicationInfo().nativeLibraryDir + "/libaapt2_exec.so";
        attempt(out, "exec aapt2 directly", () -> run(new ProcessBuilder(aapt2, "version")));
        attempt(
                out,
                "exec aapt2 through guarded launcher",
                () -> {
                    java.lang.Process child =
                            WorkerProcesses.startAapt2(new ProcessBuilder(aapt2, "version"));
                    try {
                        return collect(child);
                    } finally {
                        WorkerProcesses.finished(child);
                    }
                });
        return out.toString();
    }

    private String connect() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("1.1.1.1", 443), 3000);
            return "CONNECTED";
        }
    }

    private static String writeInto(File directory) throws Exception {
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IllegalStateException("mkdirs failed for " + directory);
        File file = new File(directory, "isolation-probe.txt");
        Files.write(file.toPath(), "probe".getBytes(StandardCharsets.UTF_8));
        String back = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        if (!file.delete()) throw new IllegalStateException("delete failed");
        return "ok " + directory + " read back " + back;
    }

    private static String writeDescriptor(ParcelFileDescriptor handoff) throws Exception {
        try (FileOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(handoff)) {
            output.write("written by isolated process".getBytes(StandardCharsets.UTF_8));
        }
        return "ok";
    }

    private String memfdRoundTrip() throws Exception {
        FileDescriptor memfd = Os.memfd_create("isolation-probe", 0);
        try (ParcelFileDescriptor duplicate = ParcelFileDescriptor.dup(memfd)) {
            byte[] bytes = "memfd".getBytes(StandardCharsets.UTF_8);
            Os.write(memfd, bytes, 0, bytes.length);
            Os.lseek(memfd, 0, OsConstants.SEEK_SET);
            try (InputStream input = new FileInputStream("/proc/self/fd/" + duplicate.getFd())) {
                return "ok read back " + new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            Os.close(memfd);
        }
    }

    private static String run(ProcessBuilder command) throws Exception {
        return collect(command.redirectErrorStream(true).start());
    }

    private static String collect(java.lang.Process child) throws Exception {
        byte[] output = child.getInputStream().readAllBytes();
        if (!child.waitFor(20, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            throw new IllegalStateException("timed out");
        }
        return "exit "
                + child.exitValue()
                + " "
                + new String(output, StandardCharsets.UTF_8).trim();
    }

    private static void attempt(StringBuilder out, String label, Callable<String> check) {
        out.append(label).append(": ");
        try {
            out.append(check.call());
        } catch (Throwable failure) {
            out.append("FAILED ").append(failure);
        }
        out.append('\n');
    }
}
