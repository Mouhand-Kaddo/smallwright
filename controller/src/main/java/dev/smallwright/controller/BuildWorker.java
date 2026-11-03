package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import dev.smallwright.controller.ui.WorkerState;

/**
 * The separate worker app that builds projects. It is bound only when it is signed with this app's
 * key, so another app installed under the worker's package name never receives project files.
 */
final class BuildWorker {
    static final ComponentName SERVICE =
            new ComponentName("dev.smallwright.worker", "dev.smallwright.worker.BuildService");

    /** The worker APK this app carries, signed with this app's key and built at its version. */
    static final String BUNDLED_APK = "worker.apk";

    private BuildWorker() {}

    static WorkerState state(Context context) {
        PackageManager packages = context.getPackageManager();
        long installedVersion;
        long ownVersion;
        try {
            installedVersion =
                    packages.getPackageInfo(SERVICE.getPackageName(), 0).getLongVersionCode();
            ownVersion = packages.getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException missing) {
            return WorkerState.MISSING;
        }
        boolean sameKey =
                packages.checkSignatures(context.getPackageName(), SERVICE.getPackageName())
                        == PackageManager.SIGNATURE_MATCH;
        return state(sameKey, installedVersion, ownVersion);
    }

    static WorkerState state(boolean sameKey, long installedVersion, long ownVersion) {
        if (!sameKey) return WorkerState.OTHER_KEY;
        return installedVersion < ownVersion ? WorkerState.OUTDATED : WorkerState.READY;
    }

    /** Returns false when the worker is missing, signed by another key, or refused the binding. */
    static boolean bind(Context context, ServiceConnection connection) {
        if (context.getPackageManager()
                        .checkSignatures(context.getPackageName(), SERVICE.getPackageName())
                != PackageManager.SIGNATURE_MATCH) return false;
        return context.bindService(
                new Intent().setComponent(SERVICE), connection, Context.BIND_AUTO_CREATE);
    }
}
