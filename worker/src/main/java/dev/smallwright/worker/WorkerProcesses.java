package dev.smallwright.worker;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Tracks the fixed AAPT2 child so hard worker termination cannot leave it compiling. */
final class WorkerProcesses {
    private static final Object LOCK = new Object();
    private static volatile Process activeChild;
    private static volatile int activePid;
    private static volatile boolean terminating;

    private WorkerProcesses() {}

    static Process startAapt2(ProcessBuilder command) throws IOException {
        List<String> original = command.command();
        if (original.isEmpty()) throw new IOException("Missing AAPT2 command");
        java.io.File executable = new java.io.File(original.get(0));
        if (!executable.isAbsolute() || !"libaapt2_exec.so".equals(executable.getName()))
            throw new IOException("Invalid AAPT2 executable");
        List<String> protectedCommand = new ArrayList<>();
        protectedCommand.add(
                new java.io.File(executable.getParentFile(), "libaapt2_guard_exec.so")
                        .getAbsolutePath());
        protectedCommand.addAll(original);
        return start(new ProcessBuilder(protectedCommand).redirectErrorStream(true));
    }

    static Process start(ProcessBuilder command) throws IOException {
        synchronized (LOCK) {
            if (terminating) throw new IOException("Worker is terminating");
            if (activeChild != null) throw new IOException("Worker child is already active");
            // The fixed launcher cannot execute AAPT2 before registration. If this native
            // spawn stalls and the worker dies, its permit pipe reaches EOF and it exits.
            // Positional argv is preserved; source text never becomes a shell command.
            List<String> argv =
                    new ArrayList<>(
                            Arrays.asList(
                                    "/system/bin/sh",
                                    "-c",
                                    "echo $$; read -r permit || exit 124; [ \"$permit\" = compile ] || exit 124; exec \"$@\"",
                                    "smallwright-aapt2"));
            argv.addAll(command.command());
            Process child = new ProcessBuilder(argv).redirectErrorStream(true).start();
            activeChild = child;
            try {
                activePid = readPid(child.getInputStream());
                if (terminating) throw new IOException("Worker is terminating");
                child.getOutputStream().write("compile\n".getBytes(StandardCharsets.US_ASCII));
                child.getOutputStream().close();
                return child;
            } catch (IOException | RuntimeException failure) {
                // EOF refuses the permit even if termination raced before registration.
                try {
                    child.getOutputStream().close();
                } catch (IOException ignored) {
                }
                child.destroyForcibly();
                finished(child);
                throw failure;
            }
        }
    }

    static void finished(Process child) {
        if (child.isAlive()) {
            child.destroyForcibly();
            try {
                child.waitFor(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            if (child.isAlive()) terminateWorker();
        }
        synchronized (LOCK) {
            if (activeChild == child) {
                activePid = 0;
                activeChild = null;
            }
        }
    }

    static void terminateWorker() {
        // Do not wait for the launch monitor, compiler, Java Process monitor, or output I/O.
        terminating = true;
        int pid = activePid;
        try {
            if (pid > 0) Os.kill(pid, OsConstants.SIGKILL);
        } catch (ErrnoException ignored) {
            // The child may already have exited. Worker termination remains mandatory.
        } finally {
            android.os.Process.killProcess(android.os.Process.myPid());
            // Self SIGKILL normally never returns. Retain a fail-closed fallback.
            System.exit(124);
        }
    }

    private static int readPid(InputStream output) throws IOException {
        StringBuilder pid = new StringBuilder();
        int next;
        while ((next = output.read()) != '\n') {
            if (next < '0' || next > '9' || pid.length() >= 10)
                throw new IOException("Compiler launcher did not provide its PID");
            pid.append((char) next);
        }
        try {
            int value = Integer.parseInt(pid.toString());
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException failure) {
            throw new IOException("Compiler launcher provided an invalid PID", failure);
        }
    }
}
