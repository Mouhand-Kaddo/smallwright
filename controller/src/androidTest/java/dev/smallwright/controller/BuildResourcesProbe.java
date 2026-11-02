package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ProjectIdentity;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONArray;
import org.json.JSONObject;

/** Six real UI builds, metadata-only owned-project storage and numeric process observations. */
final class BuildResourcesProbe {
    private static final String PREFS = "build-resources-fixture-v2";
    private static final String TITLE = "Native build resources fixture";
    private static final int BUILDS = 6;
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String run(Instrumentation inst, String phase, String observationId) throws Exception {
        return run(inst, phase, observationId, false);
    }

    static String run(Instrumentation inst, String phase, String observationId, boolean workspace)
            throws Exception {
        return run(inst, phase, observationId, workspace, false);
    }

    static String run(
            Instrumentation inst,
            String phase,
            String observationId,
            boolean workspace,
            boolean sustained)
            throws Exception {
        return run(inst, phase, observationId, workspace, sustained, false);
    }

    static String run(
            Instrumentation inst,
            String phase,
            String observationId,
            boolean workspace,
            boolean sustained,
            boolean kernelAccounting)
            throws Exception {
        return run(inst, phase, observationId, workspace, sustained, kernelAccounting, null);
    }

    static String run(
            Instrumentation inst,
            String phase,
            String observationId,
            boolean workspace,
            boolean sustained,
            boolean kernelAccounting,
            String fixtureCase)
            throws Exception {
        stage = "GUARD";
        if (fixtureCase != null
                && (!kernelAccounting
                        || !java.util.List.of("guarded-20261012", "long-storage-20261012")
                                .contains(fixtureCase)))
            throw new AssertionError("Unknown resource case");
        boolean guarded = fixtureCase != null;
        boolean longRun = "long-storage-20261012".equals(fixtureCase);
        int maximumSequenceSeconds = longRun ? 600 : 180;
        String title =
                longRun
                        ? "Native long storage kernel resources fixture"
                        : guarded
                                ? "Native guarded kernel resources fixture"
                                : kernelAccounting
                                        ? "Native kernel resources fixture"
                                        : sustained
                                                ? "Native sustained resources fixture"
                                                : workspace
                                                        ? "Native workspace resources fixture"
                                                        : TITLE;
        String preferences =
                longRun
                        ? "long-storage-kernel-resources-fixture-v1"
                        : guarded
                                ? "guarded-kernel-resources-fixture-v1"
                                : kernelAccounting
                                        ? "kernel-resources-fixture-v1"
                                        : sustained
                                                ? ("inspectFailed".equals(phase)
                                                        ? "sustained-resources-fixture-v1"
                                                        : "sustained-resources-fixture-v3")
                                                : workspace
                                                        ? "workspace-resources-fixture-v2"
                                                        : PREFS;
        int count = longRun ? 120 : sustained ? 30 : BUILDS;
        String label =
                longRun
                        ? "Long storage kernel resources"
                        : guarded
                                ? "Guarded kernel resources"
                                : kernelAccounting
                                        ? "Kernel resources"
                                        : sustained
                                                ? "Sustained resources"
                                                : workspace
                                                        ? "Workspace resources"
                                                        : "Build resources";
        stage = "GUARD";
        if (!java.util.List.of("measure", "ready", "inspectFailed").contains(phase))
            throw new AssertionError("Unknown resource phase");
        Context context = inst.getTargetContext();
        if (context.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !context.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Unlock required");
        SharedPreferences pins = context.getSharedPreferences(preferences, Context.MODE_PRIVATE);
        if ("measure".equals(phase)) {
            if (pins.contains("root")
                    || observationId == null
                    || !observationId.matches("[0-9a-f]{32}"))
                throw new AssertionError("One-shot observation guard");
            save(pins.edit().putString("root", UUID.randomUUID().toString().replace("-", "")));
        } else if (!pins.getBoolean("measured", false)
                && !(sustained && "inspectFailed".equals(phase) && pins.contains("project")))
            throw new AssertionError("No completed resource fixture");
        Activity ui =
                inst.startActivitySync(
                        new Intent(context, ProjectBindingProbeActivity.class)
                                .putExtra("bindingProbeId", pins.getString("root", ""))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        PowerManager thermal = ui.getSystemService(PowerManager.class);
        CopyOnWriteArrayList<long[]> thermalEvents = new CopyOnWriteArrayList<>();
        AtomicInteger maximumThermal = new AtomicInteger(thermal.getCurrentThermalStatus());
        PowerManager.OnThermalStatusChangedListener listener =
                status -> {
                    maximumThermal.accumulateAndGet(status, Math::max);
                    if (thermalEvents.size() < 4096)
                        thermalEvents.add(new long[] {SystemClock.elapsedRealtimeNanos(), status});
                };
        if (sustained) thermal.addThermalStatusListener(ui.getMainExecutor(), listener);
        try {
            stage = "OPEN";
            ControllerUi.awaitProjectBinding(inst, ui);
            ActiveWorkerStopProbe.awaitWorker(inst, ui);
            if ("measure".equals(phase)) {
                stage = "CREATE_PROJECT";
                CompilerMeasurementProbe.createProject(inst, ui, title);
            }
            ProjectIdentity project =
                    PhoneAlphaCounterProbe.mainValue(
                            inst, () -> (ProjectIdentity) field(ui, "activeProject"));
            if (project == null
                    || !title.equals(project.title())
                    || !project.id().matches("app[0-9a-f]{32}")
                    || !"measure".equals(phase)
                            && !project.id().equals(pins.getString("project", "")))
                throw new AssertionError("Wrong fixture project");
            ProjectSourceStore source = new ProjectSourceStore(project);
            if ("ready".equals(phase) || "inspectFailed".equals(phase)) {
                ((java.util.concurrent.ExecutorService) field(ui, "executor"))
                        .submit(() -> {})
                        .get(10, TimeUnit.SECONDS);
                inst.waitForIdleSync();
                SignedArtifactReceipt.Record receipt =
                        CompilerMeasurementProbe.requireReady(
                                inst,
                                ui,
                                project,
                                source,
                                count,
                                pins.getString("sourceSha256", ""));
                if ("inspectFailed".equals(phase))
                    return "Sustained resources failed-test inspection "
                            + new JSONObject()
                                    .put("root", pins.getString("root", ""))
                                    .put("project", project.id())
                                    .put("package", project.packageName())
                                    .put("sourceRevision", count)
                                    .put("version", receipt.versionCode)
                                    .put("apkSha256", receipt.sha256)
                                    .put("signerSha256", receipt.signerFingerprint)
                                    .put("apkBytes", receipt.apk.length())
                                    .put("apkPath", receipt.apk.getAbsolutePath())
                                    .put("sourceSha256", pins.getString("sourceSha256", ""))
                                    .put("originalResourceTestPassed", false)
                            + "\n";
                if (!receipt.sha256.equals(pins.getString("readySha256", ""))
                        || !receipt.signerFingerprint.equals(pins.getString("signerSha256", "")))
                    throw new AssertionError("Ready artifact changed");
                return label
                        + " ready PASS exact source"
                        + count
                        + "/version"
                        + count
                        + " APK/receipt/signer; no rebuild/model/install\n";
            }
            stage = "SOURCE";
            if (source.revision() != 0 || !source.isPristineDefaultSnapshot())
                throw new AssertionError("Not pristine");
            String before = source.readSource();
            int closing = before.lastIndexOf('}');
            if (closing < 0
                    || before.indexOf("\"Increment\"") < 0
                    || before.indexOf("\"Increment\"") != before.lastIndexOf("\"Increment\""))
                throw new AssertionError("Default source differs");
            StringBuilder methods = new StringBuilder("\n");
            for (int n = 0; n < 1000; n++)
                methods.append("private static int f")
                        .append(n)
                        .append("(int x){return x+")
                        .append(n)
                        .append(";}\n");
            String expected =
                    (before.substring(0, closing) + methods + before.substring(closing))
                            .replace("\"Increment\"", "\"Resource add one\"");
            int sourceBytes = expected.getBytes(StandardCharsets.UTF_8).length;
            if (sourceBytes >= 64 * 1024) throw new AssertionError("Source bound");
            String sha = ActiveBuildInterruptionProbe.hash(expected);
            save(pins.edit().putString("project", project.id()).putString("sourceSha256", sha));
            ActiveWorkerStopProbe.showTools(inst, ui);
            PhoneAlphaCounterProbe.onMain(
                    inst,
                    () -> {
                        TextBox editor = (TextBox) field(ui, "source");
                        editor.setText(expected);
                    });
            JSONObject beforePackageStorage =
                    longRun ? dev.smallwright.testing.OwnPackageStorage.read(context) : null;
            JSONObject beforeStorage = storage(project.directory());
            JSONObject beforeWorkspaceStorage = workspace ? storage(ui.getFilesDir()) : null;
            JSONArray builds = new JSONArray();
            String signer = null;
            stage = "BEFORE_GATE";
            gate(inst, ui, pins, observationId, "before1");
            long sequenceStarted = SystemClock.elapsedRealtimeNanos();
            long previousReady = sequenceStarted;
            for (int version = 1; version <= count; version++) {
                PowerManager power = ui.getSystemService(PowerManager.class);
                int thermalBefore = power.getCurrentThermalStatus();
                AtomicLong start = new AtomicLong(), end = new AtomicLong();
                Control build = Ui.button(ui, Labels.BUILD_SOURCE);
                PhoneAlphaCounterProbe.onMain(inst, () -> PhoneAlphaCounterProbe.reveal(build));
                inst.waitForIdleSync();
                stage = "BUILD_" + version;
                PhoneAlphaCounterProbe.onMain(
                        inst,
                        () -> {
                            if (sustained) {
                                int current = thermal.getCurrentThermalStatus();
                                maximumThermal.accumulateAndGet(current, Math::max);
                                if (maximumThermal.get() >= PowerManager.THERMAL_STATUS_MODERATE)
                                    throw new AssertionError(
                                            "Thermal sequence bound; no next build");
                                if (SystemClock.elapsedRealtimeNanos() - sequenceStarted
                                        >= TimeUnit.SECONDS.toNanos(maximumSequenceSeconds))
                                    throw new AssertionError(
                                            "Sequence duration bound; no next build");
                            }
                            start.set(SystemClock.elapsedRealtimeNanos());
                            PhoneAlphaCounterProbe.visibleClick(build);
                            if (!(boolean) field(ui, "buildInProgress"))
                                throw new AssertionError("Actual Build did not start");
                        });
                ActiveWorkerStopProbe.awaitIdle(inst, ui);
                Control install = Ui.button(ui, Labels.INSTALL);
                PhoneAlphaCounterProbe.onMain(inst, () -> PhoneAlphaCounterProbe.reveal(install));
                inst.waitForIdleSync();
                PhoneAlphaCounterProbe.onMain(
                        inst,
                        () -> {
                            CompilerMeasurementProbe.requireVisible(install);
                            if (field(ui, "signedArtifact") == null)
                                throw new AssertionError("No readiness");
                            end.set(SystemClock.elapsedRealtimeNanos());
                        });
                stage = "VERIFY_" + version;
                SignedArtifactReceipt.Record receipt =
                        CompilerMeasurementProbe.requireReady(
                                inst, ui, project, source, version, sha);
                if (signer != null && !signer.equals(receipt.signerFingerprint))
                    throw new AssertionError("Project signer changed");
                signer = receipt.signerFingerprint;
                JSONObject result =
                        new JSONObject()
                                .put("version", version)
                                .put("sourceRevision", version)
                                .put("startNanos", start.get())
                                .put("endNanos", end.get())
                                .put(
                                        "observedClickToVisibleReadyMs",
                                        TimeUnit.NANOSECONDS.toMillis(end.get() - start.get()))
                                .put("sourceSha256", sha)
                                .put("apkSha256", receipt.sha256)
                                .put("signerSha256", signer)
                                .put("apkBytes", receipt.apk.length())
                                .put("projectStorage", storage(project.directory()))
                                .put("controllerMemory", controllerMemory())
                                .put("thermalStatusBefore", thermalBefore)
                                .put("thermalStatusAfter", power.getCurrentThermalStatus())
                                .put(
                                        "filesystemAvailableBytes",
                                        new StatFs(ui.getFilesDir().getAbsolutePath())
                                                .getAvailableBytes());
                if (workspace) result.put("ownedWorkspaceStorage", storage(ui.getFilesDir()));
                if (longRun)
                    result.put(
                            "controllerPackageStorage",
                            dev.smallwright.testing.OwnPackageStorage.read(context));
                if (sustained) {
                    maximumThermal.accumulateAndGet(power.getCurrentThermalStatus(), Math::max);
                    result.put("readyToNextClickGapNanos", start.get() - previousReady)
                            .put("maximumThermalStatusSoFar", maximumThermal.get());
                }
                builds.put(result);
                marker(inst, "BUILD_RESOURCE_RESULT " + result);
                previousReady = end.get();
                if (sustained && maximumThermal.get() >= PowerManager.THERMAL_STATUS_MODERATE)
                    throw new AssertionError("Thermal sequence bound after completed build");
                if (sustained
                        && SystemClock.elapsedRealtimeNanos() - sequenceStarted
                                >= TimeUnit.SECONDS.toNanos(maximumSequenceSeconds))
                    throw new AssertionError("Sequence duration bound after completed build");
            }
            long sequenceEnded = SystemClock.elapsedRealtimeNanos();
            stage = "AFTER_GATE";
            gate(inst, ui, pins, observationId, "after" + count);
            JSONObject output =
                    new JSONObject()
                            .put("root", pins.getString("root", ""))
                            .put("project", project.id())
                            .put("package", project.packageName())
                            .put("controllerPid", android.os.Process.myPid())
                            .put("controllerUid", android.os.Process.myUid())
                            .put(
                                    "workerUid",
                                    context.getPackageManager()
                                            .getApplicationInfo("dev.smallwright.worker", 0)
                                            .uid)
                            .put("sourceBytes", sourceBytes)
                            .put("kernelAccountingCase", kernelAccounting)
                            .put("baselineProjectStorage", beforeStorage)
                            .put("builds", builds);
            if (workspace) {
                android.content.pm.ApplicationInfo workerInfo =
                        context.getPackageManager().getApplicationInfo("dev.smallwright.worker", 0);
                output.put("workspaceMode", true)
                        .put("baselineOwnedWorkspaceStorage", beforeWorkspaceStorage)
                        .put(
                                "installedControllerApkBytes",
                                new File(context.getApplicationInfo().sourceDir).length())
                        .put("installedWorkerApkBytes", new File(workerInfo.sourceDir).length())
                        .put(
                                "installedWorkerAapt2Bytes",
                                new File(workerInfo.nativeLibraryDir, "libaapt2_exec.so").length());
                if (output.getLong("installedControllerApkBytes") <= 0
                        || output.getLong("installedWorkerApkBytes") <= 0
                        || output.getLong("installedWorkerAapt2Bytes") <= 0)
                    throw new AssertionError("Installed size metadata unavailable");
            }
            if (sustained) {
                JSONArray events = new JSONArray();
                for (long[] event : thermalEvents)
                    events.put(
                            new JSONObject()
                                    .put("observedNanos", event[0])
                                    .put("status", event[1]));
                output.put("sustainedMode", true)
                        .put("sequenceStartedNanos", sequenceStarted)
                        .put("sequenceEndedNanos", sequenceEnded)
                        .put("maximumThermalStatus", maximumThermal.get())
                        .put("thermalStatusEvents", events)
                        .put("thermalStatusStopThreshold", PowerManager.THERMAL_STATUS_MODERATE)
                        .put(
                                "maximumSequenceNanos",
                                TimeUnit.SECONDS.toNanos(maximumSequenceSeconds));
            }
            if (output.getInt("workerUid") == output.getInt("controllerUid"))
                throw new AssertionError("Shared UID");
            if (longRun)
                output.put("controllerPackageStorageBefore", beforePackageStorage)
                        .put(
                                "controllerPackageStorageAtWindowEnd",
                                dev.smallwright.testing.OwnPackageStorage.read(context))
                        .put(
                                "packageStorageScope",
                                "own main controller package/user/data volume aggregate; retained checkpoints, not transient peak");
            if (guarded)
                output.put("processPolicyCase", fixtureCase)
                        .put("controllerMemoryAtWindowEnd", controllerMemory())
                        .put("controllerMemoryReadNanos", SystemClock.elapsedRealtimeNanos());
            save(
                    pins.edit()
                            .putBoolean("measured", true)
                            .putString(
                                    "readySha256",
                                    builds.getJSONObject(count - 1).getString("apkSha256"))
                            .putString("signerSha256", signer));
            return label + " PASS " + output + "\n";
        } finally {
            if (sustained) {
                while (!PhoneAlphaCounterProbe.awaitIdle(inst, ui, 30))
                    marker(
                            inst,
                            "Sustained resources original build pending; wait same handle, no Stop/retry");
                thermal.removeThermalStatusListener(listener);
                PhoneAlphaCounterProbe.onMain(inst, ui::finish);
            } else
                PhoneAlphaCounterProbe.onMain(
                        inst,
                        () -> {
                            Control stop = (Control) field(ui, "stop");
                            if (stop.isEnabled()) Ui.click(stop);
                            ui.finish();
                        });
        }
    }

    private static JSONObject controllerMemory() throws Exception {
        String status =
                new String(
                        Files.readAllBytes(new File("/proc/self/status").toPath()),
                        StandardCharsets.US_ASCII);
        JSONObject result = new JSONObject();
        for (String field : java.util.List.of("VmHWM", "VmRSS")) {
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("(?m)^" + field + ":\\s+(\\d+) kB$")
                            .matcher(status);
            if (!m.find()) throw new AssertionError("Own memory field absent");
            result.put(field + "KiB", Long.parseLong(m.group(1)));
        }
        return result;
    }

    private static JSONObject storage(File root) throws Exception {
        long[] count = new long[4];
        metadata(root, count, 0);
        return new JSONObject()
                .put("logicalFileBytes", count[0])
                .put("allocatedBytesIncludingDirectories", count[1])
                .put("files", count[2])
                .put("directories", count[3]);
    }

    private static void metadata(File path, long[] count, int depth) throws Exception {
        if (depth > 16 || count[2] + count[3] >= 4096)
            throw new AssertionError("Storage observation bound");
        StructStat stat = Os.lstat(path.getAbsolutePath());
        count[1] = Math.addExact(count[1], Math.multiplyExact(stat.st_blocks, 512));
        if (OsConstants.S_ISDIR(stat.st_mode)) {
            count[3]++;
            File[] children = path.listFiles();
            if (children == null) throw new AssertionError("Owned directory unavailable");
            for (File child : children) metadata(child, count, depth + 1);
        } else if (OsConstants.S_ISREG(stat.st_mode)) {
            count[0] = Math.addExact(count[0], stat.st_size);
            count[2]++;
        } else throw new AssertionError("Unexpected owned storage type");
    }

    private static void gate(
            Instrumentation inst, Activity ui, SharedPreferences pins, String id, String phase)
            throws Exception {
        File ack = new File(ui.getCacheDir(), "build-resource-" + phase + ".ack");
        if (ack.exists()) throw new AssertionError("Gate previously used");
        marker(
                inst,
                "BUILD_RESOURCE_GATE root="
                        + pins.getString("root", "")
                        + " id="
                        + id
                        + " phase="
                        + phase
                        + " controllerPid="
                        + android.os.Process.myPid());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!ack.isFile()) {
            if (System.nanoTime() >= deadline)
                throw new AssertionError("Host observation gate timeout");
            Thread.sleep(20);
        }
    }

    private static Object field(Activity ui, String name) throws Exception {
        return ActiveBuildInterruptionProbe.field(ui, name);
    }

    private static void save(SharedPreferences.Editor edit) {
        if (!edit.commit()) throw new AssertionError("Pins not saved");
    }

    private static void marker(Instrumentation inst, String text) {
        Bundle bundle = new Bundle();
        bundle.putString("stream", text + "\n");
        inst.sendStatus(0, bundle);
    }
}
