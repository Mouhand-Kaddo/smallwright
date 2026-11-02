package dev.smallwright.controller;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/** Debug-only real controller screen with disposable project and journal directories. */
public final class ProjectBindingProbeActivity extends MainActivity {
    static volatile CountDownLatch recoveryBlocked;
    static volatile CountDownLatch heartbeat;
    static volatile boolean heartbeatDuringRecovery;
    private Context isolated;

    @Override
    protected void onCreate(Bundle state) {
        String requested = getIntent().getStringExtra("bindingProbeId");
        String id = requested == null ? UUID.randomUUID().toString().replace("-", "") : requested;
        if (!id.matches("[0-9a-f]{32}"))
            throw new IllegalArgumentException("Invalid binding probe identity");
        File root = new File(super.getFilesDir(), "binding-ui-probe-" + id);
        File cache = new File(root, "cache");
        File noBackup = new File(root, "no-backup");
        if (!cache.mkdirs() && !cache.isDirectory()
                || !noBackup.mkdirs() && !noBackup.isDirectory())
            throw new IllegalStateException("Probe directories unavailable");
        isolated =
                new ContextWrapper(super.getApplicationContext()) {
                    @Override
                    public File getFilesDir() {
                        return root;
                    }

                    @Override
                    public File getCacheDir() {
                        return cache;
                    }

                    @Override
                    public File getNoBackupFilesDir() {
                        return noBackup;
                    }

                    @Override
                    public SharedPreferences getSharedPreferences(String name, int mode) {
                        return super.getSharedPreferences(
                                "binding-ui-probe-" + id + "-" + name, mode);
                    }
                };
        new Handler(Looper.getMainLooper())
                .post(
                        () -> {
                            CountDownLatch blocked = recoveryBlocked;
                            heartbeatDuringRecovery = blocked != null && blocked.getCount() != 0;
                            CountDownLatch pulse = heartbeat;
                            if (pulse != null) pulse.countDown();
                        });
        super.onCreate(state);
    }

    @Override
    public Context getApplicationContext() {
        return isolated == null ? super.getApplicationContext() : isolated;
    }

    @Override
    public File getFilesDir() {
        return isolated == null ? super.getFilesDir() : isolated.getFilesDir();
    }

    @Override
    public File getCacheDir() {
        return isolated == null ? super.getCacheDir() : isolated.getCacheDir();
    }

    @Override
    public File getNoBackupFilesDir() {
        return isolated == null ? super.getNoBackupFilesDir() : isolated.getNoBackupFilesDir();
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return isolated == null
                ? super.getSharedPreferences(name, mode)
                : isolated.getSharedPreferences(name, mode);
    }
}
