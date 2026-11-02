package dev.smallwright.worker;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Numeric own-process profiling; no commands, source, signing bytes or compiler hooks. */
final class BuildResourceObserver {
    static String run(Instrumentation inst, Context context, String id) throws Exception {
        return run(inst, context, id, false);
    }

    static String run(Instrumentation inst, Context context, String id, boolean sustained)
            throws Exception {
        return run(inst, context, id, sustained, false);
    }

    static String run(
            Instrumentation inst,
            Context context,
            String id,
            boolean sustained,
            boolean kernelAccounting)
            throws Exception {
        return run(inst, context, id, sustained, kernelAccounting, false);
    }

    static String run(
            Instrumentation inst,
            Context context,
            String id,
            boolean sustained,
            boolean kernelAccounting,
            boolean longRun)
            throws Exception {
        if (longRun && (!sustained || !kernelAccounting))
            throw new AssertionError("Invalid long observation mode");
        int maximumEntries = longRun ? 512 : sustained ? 128 : 32;
        int maximumSamples = longRun ? 32768 : sustained ? 8192 : 4096;
        int maximumSeconds = longRun ? 900 : sustained ? 240 : 120;
        if (id == null || !id.matches("[0-9a-f]{32}"))
            throw new AssertionError("Observation ID required");
        int pid = android.os.Process.myPid(), uid = android.os.Process.myUid();
        if (uid != context.getApplicationInfo().uid) throw new AssertionError("Wrong worker UID");
        File root = new File(context.getFilesDir(), "build-resource-observer-" + id);
        if (!root.mkdir()) throw new AssertionError("Observer already reserved");
        JSONObject packageStorageBefore =
                longRun ? dev.smallwright.testing.OwnPackageStorage.read(context) : null;
        long[] kernelBefore = kernelAccounting ? KernelResourceUsageProbe.readUsage() : null;
        if (kernelAccounting
                && (kernelBefore[1] != 0 || kernelBefore[2] != 0 || kernelBefore[3] != 0))
            throw new AssertionError("Fresh worker must have no prior child usage");
        marker(inst, "RESOURCE_OBSERVER_READY id=" + id + " workerPid=" + pid);
        Throwable failure = null;
        Map<Integer, JSONObject> processes = new LinkedHashMap<>();
        int completedSamples = 0;
        try {
            long started = SystemClock.elapsedRealtimeNanos(), priorEnd = started;
            long maxDuration = 0, maxGap = 0, maxBatchRss = 0, maxCompleteObservedBatchRss = 0;
            int samples = 0, missingChildren = 0, childDiscoveryFailures = 0;
            int unavailableChildMemorySamples = 0,
                    incompleteBatches = 0,
                    completeObservedBatches = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(maximumSeconds);
            while (!new File(root, "stop-sampling.ack").isFile()) {
                if (samples >= (maximumSamples) || System.nanoTime() >= deadline)
                    throw new AssertionError("Resource observation bound");
                long start = SystemClock.elapsedRealtimeNanos();
                maxGap = Math.max(maxGap, start - priorEnd);
                Set<Integer> children = new HashSet<>();
                boolean discoveryComplete = true;
                // This Android kernel omits task/*/children. Inspect numeric process-directory
                // ownership metadata first; read status only for our UID, then require this parent.
                File[] candidates = new File("/proc").listFiles();
                if (candidates == null || candidates.length > 4096)
                    throw new AssertionError("Process discovery bound");
                for (File candidate : candidates) {
                    if (!candidate.getName().matches("[0-9]+")) continue;
                    try {
                        if (Os.lstat(candidate.getAbsolutePath()).st_uid != uid) continue;
                        String status =
                                new String(
                                        Files.readAllBytes(new File(candidate, "status").toPath()),
                                        StandardCharsets.US_ASCII);
                        if (value(status, "Uid:") != uid)
                            throw new AssertionError("Process ownership changed");
                        if (value(status, "PPid:") == pid)
                            children.add(Integer.parseInt(candidate.getName()));
                    } catch (java.io.IOException | ErrnoException unavailable) {
                        childDiscoveryFailures++;
                        discoveryComplete = false;
                    }
                }
                if (children.size() > 16 || processes.size() > (maximumEntries))
                    throw new AssertionError("Observed process bound");
                long rss = observe(pid, uid, processes, start, maximumEntries, pid);
                boolean allDiscoveredChildrenAvailable = discoveryComplete;
                for (int child : children) {
                    try {
                        rss += observe(child, uid, processes, start, maximumEntries, pid);
                    } catch (ChildMemoryUnavailable exited) {
                        unavailableChildMemorySamples++;
                        allDiscoveredChildrenAvailable = false;
                    } catch (java.io.IOException vanished) {
                        missingChildren++;
                        allDiscoveredChildrenAvailable = false;
                    }
                }
                if (allDiscoveredChildrenAvailable) {
                    completeObservedBatches++;
                    maxCompleteObservedBatchRss = Math.max(maxCompleteObservedBatchRss, rss);
                } else incompleteBatches++;
                maxBatchRss = Math.max(maxBatchRss, rss);
                priorEnd = SystemClock.elapsedRealtimeNanos();
                maxDuration = Math.max(maxDuration, priorEnd - start);
                samples++;
                completedSamples = samples;
                Thread.sleep(20);
            }
            if (samples < 10) throw new AssertionError("Insufficient resource observations");
            JSONArray entries = new JSONArray();
            for (JSONObject entry : processes.values()) entries.put(entry);
            JSONObject packageStorageAfter =
                    longRun ? dev.smallwright.testing.OwnPackageStorage.read(context) : null;
            JSONObject kernelAfter =
                    kernelAccounting
                            ? KernelResourceUsageProbe.usageJson(
                                    KernelResourceUsageProbe.readUsage())
                            : null;
            marker(
                    inst,
                    "RESOURCE_OBSERVER_RESULT "
                            + new JSONObject()
                                    .put("id", id)
                                    .put("workerPid", pid)
                                    .put("workerUid", uid)
                                    .put("sustainedMode", sustained)
                                    .put("kernelAccounting", kernelAccounting)
                                    .put("longRunMode", longRun)
                                    .put(
                                            "workerPackageStorageBefore",
                                            longRun ? packageStorageBefore : JSONObject.NULL)
                                    .put(
                                            "workerPackageStorageAtWindowEnd",
                                            longRun ? packageStorageAfter : JSONObject.NULL)
                                    .put(
                                            "kernelUsageBefore",
                                            kernelAccounting
                                                    ? KernelResourceUsageProbe.usageJson(
                                                            kernelBefore)
                                                    : JSONObject.NULL)
                                    .put(
                                            "kernelUsageAfter",
                                            kernelAccounting ? kernelAfter : JSONObject.NULL)
                                    .put(
                                            "kernelChildScope",
                                            "largest waited-for child high-water; not simultaneous tree peak")
                                    .put("maximumProcessEntries", maximumEntries)
                                    .put("maximumSamples", maximumSamples)
                                    .put("maximumObservationSeconds", maximumSeconds)
                                    .put("startedNanos", started)
                                    .put("endedNanos", priorEnd)
                                    .put("samples", samples)
                                    .put("maximumBatchRssKiB", maxBatchRss)
                                    .put(
                                            "batchRssScope",
                                            "sum of available own/direct-child observations; missing values omitted, not zero")
                                    .put(
                                            "maximumCompleteObservedBatchRssKiB",
                                            completeObservedBatches == 0
                                                    ? JSONObject.NULL
                                                    : maxCompleteObservedBatchRss)
                                    .put("completeObservedBatches", completeObservedBatches)
                                    .put("incompleteChildMemoryBatches", incompleteBatches)
                                    .put(
                                            "unavailableChildMemorySamples",
                                            unavailableChildMemorySamples)
                                    .put("maximumSampleDurationNanos", maxDuration)
                                    .put("maximumInterSampleGapNanos", maxGap)
                                    .put("missingChildStatusSamples", missingChildren)
                                    .put("childDiscoveryFailures", childDiscoveryFailures)
                                    .put(
                                            "discoveryMode",
                                            "numeric proc directory ownership metadata then own-UID direct-child status")
                                    .put("processes", entries));
        } catch (Exception | AssertionError invalid) {
            failure = invalid;
            marker(
                    inst,
                    "RESOURCE_OBSERVER_INVALID id="
                            + id
                            + " category="
                            + invalid.getClass().getSimpleName()
                            + " reason="
                            + safeReason(invalid)
                            + " numericField="
                            + numericField
                            + " sampledPid="
                            + sampledPid
                            + " completedSamples="
                            + completedSamples
                            + " trackedProcesses="
                            + processes.size());
        }
        // Finishing instrumentation can end the real worker. No observation timeout may do that.
        while (!new File(root, "release.ack").isFile()) Thread.sleep(50);
        if (failure != null) throw new AssertionError("Resource observation invalid");
        return "Resource observer PASS id="
                + id
                + "; sampled numeric memory, not exact pipeline peak\n";
    }

    private static long observe(
            int pid,
            int uid,
            Map<Integer, JSONObject> entries,
            long time,
            int maximumEntries,
            int parent)
            throws Exception {
        sampledPid = pid;
        String status =
                new String(
                        Files.readAllBytes(new File("/proc/" + pid + "/status").toPath()),
                        StandardCharsets.US_ASCII);
        long actualUid = value(status, "Uid:");
        if (actualUid != uid) throw new AssertionError("Observed child ownership changed");
        if (pid != parent && value(status, "PPid:") != parent)
            throw new AssertionError("Observed child parent changed");
        String stat =
                new String(
                        Files.readAllBytes(new File("/proc/" + pid + "/stat").toPath()),
                        StandardCharsets.US_ASCII);
        long ticks = Long.parseLong(stat.substring(stat.lastIndexOf(')') + 2).split("\\s+")[19]);
        JSONObject entry = entries.get(pid);
        if (entry != null && entry.getLong("startTicks") != ticks)
            throw new AssertionError("Observed PID reused");
        if (entry == null) {
            if (entries.size() >= maximumEntries)
                throw new AssertionError("Observed process entry bound");
            entry =
                    new JSONObject()
                            .put("pid", pid)
                            .put("uid", uid)
                            .put("startTicks", ticks)
                            .put("firstSeenNanos", time)
                            .put("samples", 0)
                            .put("maximumRssKiB", JSONObject.NULL)
                            .put("maximumVmHwmKiB", JSONObject.NULL);
            entries.put(pid, entry);
        }
        if (entry.getLong("startTicks") != ticks) throw new AssertionError("Observed PID reused");
        long rss, hwm;
        try {
            rss = value(status, "VmRSS:");
            hwm = value(status, "VmHWM:");
        } catch (AssertionError absent) {
            if (pid != parent && "Numeric process field unavailable".equals(absent.getMessage()))
                throw new ChildMemoryUnavailable();
            throw absent;
        }
        entry.put("samples", entry.getInt("samples") + 1)
                .put("lastSeenNanos", time)
                .put(
                        "maximumRssKiB",
                        entry.isNull("maximumRssKiB")
                                ? rss
                                : Math.max(entry.getLong("maximumRssKiB"), rss))
                .put(
                        "maximumVmHwmKiB",
                        entry.isNull("maximumVmHwmKiB")
                                ? hwm
                                : Math.max(entry.getLong("maximumVmHwmKiB"), hwm));
        return rss;
    }

    private static final class ChildMemoryUnavailable extends java.io.IOException {}

    private static String numericField = "none";
    private static int sampledPid = -1;

    private static String safeReason(Throwable failure) {
        String message = failure.getMessage();
        java.util.List<String> reasons =
                java.util.List.of(
                        "Resource observation bound",
                        "Process discovery bound",
                        "Process ownership changed",
                        "Observed process bound",
                        "Insufficient resource observations",
                        "Observed child ownership changed",
                        "Observed child parent changed",
                        "Observed process entry bound",
                        "Observed PID reused",
                        "Numeric process field unavailable");
        int index = reasons.indexOf(message);
        return index < 0 ? "unclassified" : "reason" + index;
    }

    private static long value(String status, String prefix) {
        numericField = prefix.replace(":", "");
        for (String line : status.split("\n"))
            if (line.startsWith(prefix))
                return Long.parseLong(line.substring(prefix.length()).trim().split("\\s+")[0]);
        throw new AssertionError("Numeric process field unavailable");
    }

    private static void marker(Instrumentation inst, String text) {
        Bundle result = new Bundle();
        result.putString("stream", text + "\n");
        inst.sendStatus(0, result);
    }
}
