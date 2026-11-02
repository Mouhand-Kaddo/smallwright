package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;

/**
 * The separate worker app that builds projects. It is bound only when it is signed with this app's
 * key, so another app installed under the worker's package name never receives project files.
 */
final class BuildWorker {
    static final ComponentName SERVICE =
            new ComponentName("dev.smallwright.worker", "dev.smallwright.worker.BuildService");

    private BuildWorker() {}

    /** Returns false when the worker is missing, signed by another key, or refused the binding. */
    static boolean bind(Context context, ServiceConnection connection) {
        if (context.getPackageManager()
                        .checkSignatures(context.getPackageName(), SERVICE.getPackageName())
                != PackageManager.SIGNATURE_MATCH) return false;
        return context.bindService(
                new Intent().setComponent(SERVICE), connection, Context.BIND_AUTO_CREATE);
    }
}
