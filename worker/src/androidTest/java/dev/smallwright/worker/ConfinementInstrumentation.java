package dev.smallwright.worker;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Runs in the worker app's UID against a newly created, dummy controller-private file. */
public final class ConfinementInstrumentation extends Instrumentation {
    private String sentinelPath;
    private String mode;
    private String deadlineProbeId;
    private String observationId;
    private String storagePhase;
    private String valuesPhase;
    private String expectedWorkerSha256;
    private boolean approvedValuesFixture;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        sentinelPath = arguments.getString("sentinelPath");
        mode = arguments.getString("mode", "confinement");
        deadlineProbeId = arguments.getString("deadlineProbeId");
        observationId = arguments.getString("observationId");
        storagePhase = arguments.getString("storagePhase", "read");
        valuesPhase = arguments.getString("valuesPhase");
        expectedWorkerSha256 = arguments.getString("expectedWorkerSha256");
        approvedValuesFixture =
                Boolean.parseBoolean(arguments.getString("approveLocalValuesFixture", "false"));
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            if ("valuesResources".equals(mode)) {
                try {
                    result.putString(
                            "stream",
                            ValuesResourceProbe.run(
                                    this,
                                    valuesPhase,
                                    observationId,
                                    approvedValuesFixture,
                                    expectedWorkerSha256));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Values resources FAILED stage="
                                    + ValuesResourceProbe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if ("ownPackageStorage".equals(mode)) {
                try {
                    result.putString(
                            "stream",
                            dev.smallwright.testing.OwnPackageStorage.run(
                                    this, observationId, storagePhase));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Own package storage FAILED stage="
                                    + dev.smallwright.testing.OwnPackageStorage.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if ("isolatedProcess".equals(mode)) {
                try {
                    result.putString("stream", IsolatedProcessProbe.run(this));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString("stream", "Isolated process probe FAILED " + failure + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if ("childProcessPolicy".equals(mode)) {
                try {
                    result.putString("stream", ChildProcessPolicyProbe.run(this, observationId));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Child process policy FAILED stage="
                                    + ChildProcessPolicyProbe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if ("kernelResourceUsage".equals(mode)) {
                try {
                    result.putString(
                            "stream",
                            KernelResourceUsageProbe.run(this, getTargetContext(), observationId));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Kernel resource usage FAILED stage="
                                    + KernelResourceUsageProbe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if ("resourceExitDiagnostic".equals(mode)) {
                if (observationId == null || !observationId.matches("[0-9a-f]{32}"))
                    throw new AssertionError("Diagnostic ID required");
                java.util.concurrent.atomic.AtomicReference<Throwable> childFailure =
                        new java.util.concurrent.atomic.AtomicReference<>();
                Thread exits =
                        new Thread(
                                () -> {
                                    int completed = 0;
                                    long start = android.os.SystemClock.elapsedRealtimeNanos();
                                    try {
                                        while (completed < 100
                                                && android.os.SystemClock.elapsedRealtimeNanos()
                                                                - start
                                                        < java.util.concurrent.TimeUnit.SECONDS
                                                                .toNanos(15)) {
                                            java.lang.Process child =
                                                    new ProcessBuilder("/system/bin/true").start();
                                            if (child.waitFor() != 0)
                                                throw new AssertionError("Diagnostic child failed");
                                            completed++;
                                        }
                                    } catch (Throwable failed) {
                                        childFailure.set(failed);
                                    }
                                    Bundle marker = new Bundle();
                                    marker.putString(
                                            "stream",
                                            "RESOURCE_EXIT_DIAGNOSTIC_DONE id="
                                                    + observationId
                                                    + " completed="
                                                    + completed
                                                    + "\n");
                                    sendStatus(0, marker);
                                },
                                "owned-child-exit-diagnostic");
                exits.start();
                try {
                    result.putString(
                            "stream",
                            BuildResourceObserver.run(
                                    this, getTargetContext(), observationId, true));
                    if (childFailure.get() != null)
                        throw new AssertionError("Diagnostic child fixture failed");
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failed) {
                    result.putString(
                            "stream",
                            "Resource exit diagnostic FAILED: "
                                    + failed.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                } finally {
                    exits.join();
                }
                return;
            }
            if ("kernelResourceObserver".equals(mode) || "longResourceObserver".equals(mode)) {
                try {
                    result.putString(
                            "stream",
                            BuildResourceObserver.run(
                                    this,
                                    getTargetContext(),
                                    observationId,
                                    true,
                                    true,
                                    "longResourceObserver".equals(mode)));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Kernel resource observer FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if ("sustainedResourceObserver".equals(mode)) {
                try {
                    result.putString(
                            "stream",
                            BuildResourceObserver.run(
                                    this, getTargetContext(), observationId, true));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Sustained resource observer FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if ("buildResourceObserver".equals(mode)) {
                result.putString(
                        "stream",
                        BuildResourceObserver.run(this, getTargetContext(), observationId));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if ("ecjStopObserver".equals(mode)) {
                result.putString(
                        "stream", EcjStopObserver.run(this, getTargetContext(), observationId));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if ("requestDeadlineKill".equals(mode)) {
                RequestDeadlineProbe.kill(getTargetContext(), deadlineProbeId);
                throw new AssertionError("Worker deadline did not terminate instrumentation");
            }
            if ("requestDeadlineRecovery".equals(mode)) {
                result.putString(
                        "stream",
                        RequestDeadlineProbe.recover(
                                getContext(), getTargetContext(), deadlineProbeId));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if ("cleanBuildScratch".equals(mode)) {
                File scratch = new File(getTargetContext().getCacheDir(), "active-build");
                if (scratch.exists())
                    throw new AssertionError("Failed Binder build left active scratch");
                result.putString(
                        "stream", "Worker scratch: failed Binder build left no active scratch\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if ("malformedResource".equals(mode)) {
                requireMalformedResourceRejected();
                result.putString(
                        "stream",
                        "Worker resource: packaged AAPT2 rejected malformed XML "
                                + "under worker UID\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if ("productionSnapshot".equals(mode)) {
                result.putString(
                        "stream", ProductionSnapshotProbe.run(getContext(), getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if ("scratchPreparationFailure".equals(mode)) {
                result.putString(
                        "stream",
                        ScratchPreparationFailureProbe.run(getContext(), getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if ("compileDiagnostics".equals(mode)) {
                result.putString(
                        "stream",
                        CompileDiagnosticsProbe.run(getTargetContext())
                                + ProductionSnapshotProbe.run(getContext(), getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if ("generatedResources".equals(mode)) {
                result.putString("stream", GeneratedResourceProbe.run(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (!"confinement".equals(mode)) {
                throw new AssertionError("Unknown test mode");
            }
            if (sentinelPath == null
                    || !sentinelPath.startsWith(
                            "/data/user/0/dev.smallwright.controller/files/confinement-probe-")) {
                throw new AssertionError(
                        "A new controller-private dummy sentinel path is required");
            }
            int workerUid = getTargetContext().getApplicationInfo().uid;
            if (Process.myUid() != workerUid) {
                throw new AssertionError("Probe is not running under the worker UID");
            }
            String fileDenial = requireDeniedOpen(sentinelPath);
            String socketDenial = requireDeniedSocket();
            result.putString(
                    "stream",
                    "Worker confinement: worker UID, controller file inaccessible "
                            + fileDenial
                            + ", AF_INET socket denied "
                            + socketDenial
                            + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            if ("scratchPreparationFailure".equals(mode) || "compileDiagnostics".equals(mode)) {
                String type = failure.getClass().getName();
                String prefix =
                        "compileDiagnostics".equals(mode)
                                ? "Worker compile diagnostics FAILED: "
                                : "Worker scratch preparation FAILED: ";
                result.putString(
                        "stream", prefix + type.substring(0, Math.min(type.length(), 128)) + "\n");
            } else {
                result.putString("stream", "Worker confinement FAILED: " + failure + "\n");
            }
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void requireMalformedResourceRejected() throws Exception {
        if (Process.myUid() != getTargetContext().getApplicationInfo().uid) {
            throw new AssertionError("Resource probe is not running under the worker UID");
        }
        File scratch =
                new File(
                        getTargetContext().getCacheDir(),
                        "malformed-resource-probe-" + System.nanoTime());
        try {
            File values = new File(scratch, "res/values");
            if (!values.mkdirs())
                throw new AssertionError("Could not create test resource directory");
            Files.write(
                    new File(values, "strings.xml").toPath(),
                    "<resources><string name=\"label\">unclosed".getBytes(StandardCharsets.UTF_8));
            File compiled = new File(scratch, "compiled.zip");
            String aapt2 =
                    getTargetContext().getApplicationInfo().nativeLibraryDir + "/libaapt2_exec.so";
            java.lang.Process child =
                    new ProcessBuilder(
                                    aapt2,
                                    "compile",
                                    "--dir",
                                    new File(scratch, "res").getAbsolutePath(),
                                    "-o",
                                    compiled.getAbsolutePath())
                            .redirectErrorStream(true)
                            .start();
            try {
                String success = BuildService.runBounded(child, 30_000, 16_384);
                throw new AssertionError("Malformed XML compiled: " + success);
            } catch (IllegalStateException rejected) {
                if (!rejected.getMessage().startsWith("Packaged AAPT2 failed:")
                        || rejected.getMessage().getBytes(StandardCharsets.UTF_8).length > 16_384) {
                    throw new AssertionError(
                            "Malformed XML failed without bounded AAPT2 diagnostics", rejected);
                }
            }
        } finally {
            removeOwnTestTree(scratch);
        }
        if (scratch.exists())
            throw new AssertionError("Malformed resource scratch was not removed");
    }

    private static void removeOwnTestTree(File path) throws IOException {
        if (!path.exists()) return;
        File[] children = path.listFiles();
        if (children != null) {
            for (File child : children) removeOwnTestTree(child);
        }
        if (!path.delete()) throw new IOException("Could not remove resource test scratch");
    }

    private static String requireDeniedOpen(String path) throws Exception {
        try {
            FileDescriptor descriptor = Os.open(path, OsConstants.O_RDONLY, 0);
            Os.close(descriptor);
            throw new AssertionError("Worker opened controller-private sentinel");
        } catch (ErrnoException denied) {
            // The runner first attests this exact file exists under the controller UID.
            // Android app-data isolation may hide it from other UIDs as ENOENT.
            if (denied.errno == OsConstants.ENOENT) return "ENOENT (existing file hidden)";
            if (denied.errno != OsConstants.EACCES && denied.errno != OsConstants.EPERM) {
                throw new AssertionError("Sentinel open failed for another reason: " + denied);
            }
            return denied.errno == OsConstants.EACCES ? "EACCES" : "EPERM";
        }
    }

    private static String requireDeniedSocket() throws Exception {
        try {
            FileDescriptor descriptor = Os.socket(OsConstants.AF_INET, OsConstants.SOCK_STREAM, 0);
            Os.close(descriptor);
            throw new AssertionError(
                    "Worker created an IPv4 TCP socket without INTERNET permission");
        } catch (ErrnoException denied) {
            if (denied.errno != OsConstants.EACCES && denied.errno != OsConstants.EPERM) {
                throw new AssertionError("Socket creation failed for another reason: " + denied);
            }
            return denied.errno == OsConstants.EACCES ? "EACCES" : "EPERM";
        }
    }
}
