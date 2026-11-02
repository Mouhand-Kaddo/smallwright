package dev.smallwright.worker;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Process;
import java.io.File;

/** Actual native process/thread/exec checks, never compiler or generated-code substitution. */
final class ChildProcessPolicyProbe {
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String run(Instrumentation in, String id) throws Exception {
        stage = "GUARD";
        Context target = in.getTargetContext();
        if (id == null
                || !id.matches("[0-9a-f]{32}")
                || Process.myUid() != target.getApplicationInfo().uid)
            throw new AssertionError("Invalid probe identity");
        if (!new File(target.getFilesDir(), "child-policy-probe-" + id).mkdir())
            throw new AssertionError("Already reserved");
        stage = "NATIVE_EXEC";
        File executable =
                new File(
                        in.getContext().getApplicationInfo().nativeLibraryDir,
                        "libchild_policy_probe_exec.so");
        String baseline =
                BuildService.runBounded(
                        new ProcessBuilder(executable.getAbsolutePath(), "baseline")
                                .redirectErrorStream(true)
                                .start(),
                        10_000,
                        1024);
        if (!baseline.matches(
                "CHILD_POLICY_BASELINE forkAllowed=1 threadAllowed=1 clone3Available=[01] clone3Errno=[0-9]+ pid=[0-9]+"))
            throw new AssertionError("Independent unfiltered process baseline failed");
        android.os.Bundle observed = new android.os.Bundle();
        observed.putString("stream", baseline + "\n");
        in.sendStatus(0, observed);
        java.lang.Process child =
                new ProcessBuilder(executable.getAbsolutePath()).redirectErrorStream(true).start();
        String result = BuildService.runBounded(child, 10_000, 1024);
        stage = "ENFORCEMENT";
        if (!result.matches(
                "CHILD_POLICY_RESULT forkDenied=1 threadAllowed=1 threadForkDenied=1 cloneDenied=1 clone3ObservedEnosys=1 ptraceDenied=1 execSamePid=1 pid=[0-9]+"))
            throw new AssertionError("Separate process denied and threads allowed required");
        observed.putString("stream", result + "\n");
        in.sendStatus(0, observed);
        int pid = Integer.parseInt(result.substring(result.lastIndexOf('=') + 1));
        if (pid == Process.myPid()) throw new AssertionError("Policy must execute only in child");
        stage = "PRODUCTION_LAUNCHER";
        File library = new File(target.getApplicationInfo().nativeLibraryDir);
        File guard = new File(library, "libaapt2_guard_exec.so");
        requireRejected(new ProcessBuilder(guard.getAbsolutePath()).start());
        requireRejected(new ProcessBuilder(guard.getAbsolutePath(), "/system/bin/true").start());
        java.lang.Process refusedPolicy =
                new ProcessBuilder(
                                executable.getAbsolutePath(),
                                "refuse-policy",
                                guard.getAbsolutePath(),
                                new File(library, "libaapt2_exec.so").getAbsolutePath())
                        .redirectErrorStream(true)
                        .start();
        try {
            if (!refusedPolicy.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                    || refusedPolicy.exitValue() != 125
                    || !new String(
                                    refusedPolicy.getInputStream().readNBytes(4097),
                                    java.nio.charset.StandardCharsets.US_ASCII)
                            .equals("AAPT2 process policy unavailable\n"))
                throw new AssertionError("Unavailable policy must not execute AAPT2");
        } finally {
            requireStopped(refusedPolicy);
        }
        java.lang.Process version =
                WorkerProcesses.startAapt2(
                        new ProcessBuilder(
                                new File(library, "libaapt2_exec.so").getAbsolutePath(),
                                "version"));
        try {
            String text = BuildService.runBounded(version, 10_000, 4096);
            if (!text.startsWith("Android Asset Packaging Tool"))
                throw new AssertionError("Production guarded AAPT2 version failed");
        } finally {
            WorkerProcesses.finished(version);
        }
        observed.putString(
                "stream",
                "PRODUCTION_LAUNCHER_RESULT invalidArgsRejected=1 wrongTargetRejected=1 unavailablePolicyRejected=1 guardedVersionExecuted=1\n");
        in.sendStatus(0, observed);
        return "Child process policy PASS workerPid=" + Process.myPid() + " childPid=" + pid + "\n";
    }

    private static void requireRejected(java.lang.Process child) throws Exception {
        try {
            if (!child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                    || child.exitValue() != 124)
                throw new AssertionError("Invalid target must not execute");
        } finally {
            requireStopped(child);
        }
    }

    private static void requireStopped(java.lang.Process child) throws Exception {
        if (child.isAlive()) child.destroyForcibly();
        if (!child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
            throw new AssertionError("Owned native test child did not stop");
    }
}
