package dev.smallwright.worker;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Observes real ECJ stacks; never pauses, modifies or invokes a compiler. */
final class EcjStopObserver {
    static String run(Instrumentation inst, Context context, String id) throws Exception {
        if (id == null || !id.matches("[0-9a-f]{32}"))
            throw new AssertionError("Observation ID required");
        if (android.os.Process.myUid() != context.getApplicationInfo().uid)
            throw new AssertionError("Wrong worker UID");
        File root = new File(context.getFilesDir(), "ecj-stop-observer-" + id);
        if (!root.mkdir()) throw new AssertionError("Observation already reserved");
        marker(inst, "ECJ_OBSERVER_READY id=" + id + " workerPid=" + android.os.Process.myPid());
        Throwable failure = null;
        try {
            Thread compiler = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
            JSONArray samples = new JSONArray();
            while (System.nanoTime() < deadline) {
                long start = SystemClock.elapsedRealtimeNanos();
                Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
                long end = SystemClock.elapsedRealtimeNanos();
                for (Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
                    if (!isRealCompile(entry.getValue())) continue;
                    if (compiler != null) throw new AssertionError("Multiple actual compilers");
                    compiler = entry.getKey();
                    samples.put(sample(start, end));
                }
                if (compiler != null) break;
                Thread.sleep(2);
            }
            if (compiler == null) throw new AssertionError("No actual ECJ compilation observed");
            marker(inst, "ECJ_OBSERVER_ACTIVE id=" + id);
            boolean ended = false;
            while (System.nanoTime() < deadline) {
                long start = SystemClock.elapsedRealtimeNanos();
                StackTraceElement[] trace = compiler.getStackTrace();
                long end = SystemClock.elapsedRealtimeNanos();
                if (!isRealCompile(trace)) {
                    ended = true;
                    break;
                }
                if (samples.length() >= 4096) throw new AssertionError("Observation sample bound");
                samples.put(sample(start, end));
                Thread.sleep(1);
            }
            if (!ended || samples.length() < 2)
                throw new AssertionError("No bounded actual ECJ interval");
            JSONObject result =
                    new JSONObject()
                            .put("id", id)
                            .put("workerPid", android.os.Process.myPid())
                            .put("workerUid", android.os.Process.myUid())
                            .put("compilerThreadId", compiler.getId())
                            .put("samples", samples);
            marker(inst, "ECJ_OBSERVER_WINDOW " + result);
        } catch (Exception | AssertionError invalid) {
            failure = invalid;
            marker(
                    inst,
                    "ECJ_OBSERVER_INVALID id="
                            + id
                            + " category="
                            + invalid.getClass().getSimpleName());
        }
        // Finishing instrumentation can end the worker: keep it alive through real D8/output.
        File release = new File(root, "release.ack");
        // Observation bounds must never terminate the target. Only the host, after the
        // ORIGINAL controller handle is terminal, can release this owned test helper.
        // A host observation timeout leaves this handle live for explicit inspection.
        while (!release.isFile()) Thread.sleep(50);
        if (failure != null) throw new AssertionError("Actual ECJ observation was invalid");
        return "ECJ observer PASS id=" + id + "; actual stack timestamps only; no compiler delay\n";
    }

    private static boolean isRealCompile(StackTraceElement[] frames) {
        boolean ecj = false, worker = false;
        for (StackTraceElement frame : frames) {
            if (frame.getClassName().equals("org.eclipse.jdt.internal.compiler.Compiler")
                    && frame.getMethodName().equals("compile")) ecj = true;
            if (frame.getClassName().equals("dev.smallwright.worker.BuildService")
                    && frame.getMethodName().equals("compileJavaOrThrow")) worker = true;
        }
        return ecj && worker;
    }

    private static JSONObject sample(long start, long end) throws Exception {
        return new JSONObject().put("startNanos", start).put("endNanos", end);
    }

    private static void marker(Instrumentation inst, String value) {
        Bundle result = new Bundle();
        result.putString("stream", value + "\n");
        inst.sendStatus(0, result);
    }
}
