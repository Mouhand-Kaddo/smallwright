package dev.smallwright.testing;

import android.app.Instrumentation;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import java.io.File;
import java.util.UUID;
import org.json.JSONObject;

/** Test-only own-package aggregate metadata. Never enumerates or reads private files. */
public final class OwnPackageStorage {
    private static String stage = "GUARD";

    private OwnPackageStorage() {}

    public static String failureStage() {
        return stage;
    }

    public static String run(Instrumentation in, String id) throws Exception {
        return run(in, id, "read");
    }

    public static String run(Instrumentation in, String id, String phase) throws Exception {
        stage = "GUARD";
        Context target = in.getTargetContext();
        if (!java.util.List.of("read", "cacheDelta").contains(phase)
                || id == null
                || !id.matches("[0-9a-f]{32}")
                || Process.myUid() != target.getApplicationInfo().uid)
            throw new AssertionError("Invalid own-package observation");
        if (!new File(target.getFilesDir(), "package-storage-probe-" + id).mkdir())
            throw new AssertionError("Observation already reserved");
        stage = "READ_OWN_STATS";
        JSONObject value = read(target);
        stage = "VERIFY_OWN_STATS";
        long apkBytes = new File(target.getApplicationInfo().sourceDir).length();
        if (apkBytes <= 0
                || value.getLong("appBytes") < apkBytes
                || value.getLong("cacheBytes") > value.getLong("dataBytes")
                || !target.getPackageName().equals(value.getString("package")))
            throw new AssertionError("Own package statistics inconsistent with installed APK");
        value.put("baseApkFileBytes", apkBytes).put("observationId", id);
        if ("cacheDelta".equals(phase)) {
            stage = "CACHE_DELTA";
            value.put("cacheDelta", observeCacheDelta(in, target, id, value));
        }
        return "Own package storage PASS " + value + "\n";
    }

    private static JSONObject observeCacheDelta(
            Instrumentation in, Context target, String id, JSONObject before) throws Exception {
        JSONObject baseline = new JSONObject(before.toString());
        File fixture = new File(target.getCacheDir(), "package-storage-cache-" + id);
        stage = "CACHE_DELTA_WRITE";
        if (!fixture.createNewFile()) throw new AssertionError("Cache fixture already exists");
        final int logicalBytes = 2 * 1024 * 1024;
        byte[] block = new byte[4096];
        java.security.SecureRandom random = new java.security.SecureRandom();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(fixture)) {
            for (int written = 0; written < logicalBytes; written += block.length) {
                random.nextBytes(block);
                out.write(block);
            }
            out.getFD().sync();
        }
        java.util.Arrays.fill(block, (byte) 0);
        android.system.StructStat metadata = android.system.Os.lstat(fixture.getAbsolutePath());
        long allocated = Math.multiplyExact(metadata.st_blocks, 512L);
        if (!android.system.OsConstants.S_ISREG(metadata.st_mode)
                || metadata.st_size != logicalBytes
                || allocated <= 0) {
            throw new AssertionError("Cache fixture allocation not observed");
        }
        stage = "CACHE_DELTA_STATS";
        org.json.JSONArray observations = new org.json.JSONArray();
        long deadline = SystemClock.elapsedRealtime() + 15000;
        JSONObject after;
        while (true) {
            after = read(target);
            observations.put(after);
            android.system.StructStat retained = android.system.Os.lstat(fixture.getAbsolutePath());
            if (retained.st_size != logicalBytes || retained.st_blocks != metadata.st_blocks) {
                throw new AssertionError("Cache fixture changed during observation");
            }
            if (after.getLong("cacheBytes") - baseline.getLong("cacheBytes") >= allocated
                    && after.getLong("dataBytes") - baseline.getLong("dataBytes") >= allocated)
                break;
            if (SystemClock.elapsedRealtime() >= deadline) {
                android.os.Bundle evidence = new android.os.Bundle();
                evidence.putString(
                        "stream",
                        "CACHE_DELTA_UNMATCHED "
                                + new JSONObject()
                                        .put("before", baseline)
                                        .put("observations", observations)
                                        .put("fixtureAllocatedBytes", allocated)
                                + "\n");
                // Preserve the observed lag; do not change the contract or retry the fixture.
                // The caller records a safe named failure after this numeric-only observation.
                in.sendStatus(0, evidence);
                throw new AssertionError("Package cache/data delta not reflected within15s");
            }
            Thread.sleep(250);
        }
        return new JSONObject()
                .put("before", baseline)
                .put("after", after)
                .put("observations", observations)
                .put("fixtureLogicalBytes", logicalBytes)
                .put("fixtureAllocatedBytes", allocated)
                .put("fixtureName", fixture.getName())
                .put(
                        "dataIncreaseBytes",
                        after.getLong("dataBytes") - baseline.getLong("dataBytes"))
                .put(
                        "cacheIncreaseBytes",
                        after.getLong("cacheBytes") - baseline.getLong("cacheBytes"))
                .put("fixtureRetained", true)
                .put("fixtureContentRead", false)
                .put(
                        "expectedAccounting",
                        "data and cache each include new allocated cache bytes; total remains app plus data");
    }

    public static JSONObject read(Context context) throws Exception {
        String name = context.getPackageName();
        int uid = context.getPackageManager().getApplicationInfo(name, 0).uid;
        if (uid != Process.myUid()) throw new AssertionError("Only own package permitted");
        StorageStatsManager statistics = context.getSystemService(StorageStatsManager.class);
        StorageManager storage = context.getSystemService(StorageManager.class);
        if (statistics == null || storage == null)
            throw new AssertionError("Own package storage API unavailable");
        UUID volume = storage.getUuidForPath(context.getDataDir());
        long started = SystemClock.elapsedRealtimeNanos();
        StorageStats value = statistics.queryStatsForPackage(volume, name, Process.myUserHandle());
        long app = value.getAppBytes(), data = value.getDataBytes(), cache = value.getCacheBytes();
        if (app < 0 || data < 0 || cache < 0 || cache > data)
            throw new AssertionError("Invalid storage counters");
        return new JSONObject()
                .put("package", name)
                .put("uid", uid)
                .put("defaultVolume", StorageManager.UUID_DEFAULT.equals(volume))
                .put("queryStartedNanos", started)
                .put("queryEndedNanos", SystemClock.elapsedRealtimeNanos())
                .put("appBytes", app)
                .put("dataBytes", data)
                .put("cacheBytes", cache)
                .put("reportedTotalBytes", Math.addExact(app, data))
                .put("cacheIncludedInData", true)
                .put(
                        "scope",
                        "own package/user/volume aggregate app plus data; cache not added twice")
                .put("otherPackagesQueried", false)
                .put("privateFileEnumerationOrContentRead", false);
    }
}
