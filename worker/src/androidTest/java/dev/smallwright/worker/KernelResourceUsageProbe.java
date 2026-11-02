package dev.smallwright.worker;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/** Real kernel readings in this worker UID; no compiler hooks or credential access. */
final class KernelResourceUsageProbe {
    private static String stage = "GUARD";
    private static boolean loaded;

    static String failureStage() {
        return stage;
    }

    static long[] readUsage() {
        if (!loaded) {
            System.loadLibrary("kernel_usage_probe");
            loaded = true;
        }
        return readNativeUsage();
    }

    private static native long[] readNativeUsage();

    static String run(Instrumentation inst, Context context, String id) throws Exception {
        stage = "GUARD";
        if (id == null || !id.matches("[0-9a-f]{32}"))
            throw new AssertionError("Observation ID required");
        if (Process.myUid() != context.getApplicationInfo().uid)
            throw new AssertionError("Wrong worker UID");
        File reservation = new File(context.getFilesDir(), "kernel-usage-probe-" + id);
        if (!reservation.mkdir()) throw new AssertionError("Observation already reserved");
        stage = "NATIVE_READ";
        long[] before = readUsage();
        requireUsage(before);
        if (before[1] != 0 || before[2] != 0 || before[3] != 0)
            throw new AssertionError("Fresh process must have no prior child usage");
        stage = "CHILD_EXIT";
        long started = SystemClock.elapsedRealtimeNanos();
        java.lang.Process child = new ProcessBuilder("/system/bin/true").start();
        if (!child.waitFor(10, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            child.waitFor();
            throw new AssertionError("Owned diagnostic child deadline");
        }
        if (child.exitValue() != 0) throw new AssertionError("Owned diagnostic child failed");
        stage = "REAPED_CHILD_USAGE";
        long[] after = readUsage();
        requireUsage(after);
        if (after[1] <= 0 || after[2] + after[3] <= 0)
            throw new AssertionError("Exited child must remain in kernel accounting");
        stage = "SELF_HIGH_WATER";
        long procBefore = ownHighWaterKiB();
        long[] bracket = readUsage();
        long procAfter = ownHighWaterKiB();
        requireUsage(bracket);
        if (bracket[0] < procBefore || bracket[0] > procAfter)
            throw new AssertionError("Kernel self high-water outside own proc bracket");
        stage = "STABLE_REAPED_CHILD_USAGE";
        if (bracket[1] != after[1] || bracket[2] != after[2] || bracket[3] != after[3])
            throw new AssertionError("Reaped child usage changed without a new child");
        return "Kernel resource usage PASS "
                + new JSONObject()
                        .put("id", id)
                        .put("workerPid", Process.myPid())
                        .put("workerUid", Process.myUid())
                        .put("startedNanos", started)
                        .put("endedNanos", SystemClock.elapsedRealtimeNanos())
                        .put("beforeChildMaxRssKiB", before[1])
                        .put("afterChildMaxRssKiB", after[1])
                        .put("childUserMicros", after[2])
                        .put("childSystemMicros", after[3])
                        .put("kernelSelfMaxRssKiB", bracket[0])
                        .put("procSelfHighWaterBeforeKiB", procBefore)
                        .put("procSelfHighWaterAfterKiB", procAfter)
                        .put("childCompleted", true)
                        .put("compilerExecuted", false)
                        .put("exactSimultaneousPipelinePeak", false)
                        .toString()
                + "\n";
    }

    static JSONObject usageJson(long[] usage) throws Exception {
        requireUsage(usage);
        return new JSONObject()
                .put("selfMaxRssKiB", usage[0])
                .put("largestReapedChildMaxRssKiB", usage[1])
                .put("reapedChildUserMicros", usage[2])
                .put("reapedChildSystemMicros", usage[3]);
    }

    private static void requireUsage(long[] usage) {
        if (usage == null || usage.length != 4 || usage[0] <= 0)
            throw new AssertionError("Invalid real kernel usage");
        for (long value : usage) {
            if (value < 0) throw new AssertionError("Negative kernel usage");
        }
    }

    private static long ownHighWaterKiB() throws Exception {
        String status =
                new String(
                        Files.readAllBytes(new File("/proc/self/status").toPath()),
                        StandardCharsets.US_ASCII);
        Matcher match = Pattern.compile("(?m)^VmHWM:\\s+([0-9]+) kB$").matcher(status);
        if (!match.find()) throw new AssertionError("Own high-water absent");
        return Long.parseLong(match.group(1));
    }
}
