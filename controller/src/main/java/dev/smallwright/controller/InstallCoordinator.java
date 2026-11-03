package dev.smallwright.controller;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.FileUtils;
import dev.smallwright.project.ProjectIdentity;
import java.io.InputStream;
import java.io.OutputStream;

final class InstallCoordinator {
    interface Listener {
        void onInstallStatus(String status);
    }

    private interface ApkWriter {
        void writeTo(OutputStream output) throws Exception;
    }

    private static final String ACTION_STATUS = "dev.smallwright.controller.INSTALL_STATUS";
    private final Context context;
    private final Listener listener;
    private final BroadcastReceiver receiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context ignored, Intent intent) {
                    int status =
                            intent.getIntExtra(
                                    PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                        if (confirmation != null) {
                            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(confirmation);
                            listener.onInstallStatus("Installation needs your confirmation.");
                            return;
                        }
                    }
                    if (status == PackageInstaller.STATUS_SUCCESS) {
                        listener.onInstallStatus("Installation completed.");
                    } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
                        listener.onInstallStatus("Installation cancelled.");
                    } else {
                        listener.onInstallStatus(
                                "Installation failed: "
                                        + intent.getStringExtra(
                                                PackageInstaller.EXTRA_STATUS_MESSAGE));
                    }
                }
            };

    InstallCoordinator(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void install(VerifiedArtifact apk) throws Exception {
        install(apk, ProjectSourceStore.PACKAGE_NAME);
    }

    void install(VerifiedArtifact apk, ProjectIdentity identity) throws Exception {
        if (identity == null) throw new IllegalArgumentException("Missing installation project");
        install(apk, identity.packageName());
    }

    /** Installs or updates the build worker from the copy inside this app. */
    void installWorker() throws Exception {
        try (InputStream apk = context.getAssets().open(BuildWorker.BUNDLED_APK)) {
            commit(BuildWorker.SERVICE.getPackageName(), -1, output -> FileUtils.copy(apk, output));
        }
    }

    private void install(VerifiedArtifact apk, String packageName) throws Exception {
        if (!packageName.matches("dev\\.smallwright\\.generated\\.(?:counter|app[0-9a-f]{32})"))
            throw new IllegalArgumentException("Invalid generated package");
        apk.verifyPackage(context, packageName);
        commit(packageName, apk.length(), apk::copyTo);
    }

    private void commit(String packageName, long length, ApkWriter apk) throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(packageName);
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }
        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (OutputStream output = session.openWrite("app.apk", 0, length)) {
                apk.writeTo(output);
                session.fsync(output);
            }
            Intent status = new Intent(ACTION_STATUS).setPackage(context.getPackageName());
            int flags =
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            | android.app.PendingIntent.FLAG_MUTABLE;
            IntentSender sender =
                    android.app.PendingIntent.getBroadcast(context, sessionId, status, flags)
                            .getIntentSender();
            session.commit(sender);
        } catch (Exception failure) {
            try {
                installer.abandonSession(sessionId);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // API 31-32 lack receiver export flags.
    void register() {
        IntentFilter filter = new IntentFilter(ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33)
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver, filter);
    }

    void unregister() {
        context.unregisterReceiver(receiver);
    }
}
