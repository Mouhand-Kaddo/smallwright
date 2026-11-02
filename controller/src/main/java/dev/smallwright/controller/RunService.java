package dev.smallwright.controller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.IBinder;
import android.util.Log;
import dev.smallwright.controller.ui.Labels;
import java.util.function.Supplier;

/**
 * Keeps the controller running while a run works, even with the screen gone, and shows the run
 * notification. It holds the coordinator of a working run so the screen can attach to it again, and
 * keeps the compiler bound for that run. Everything here runs on the main thread.
 */
public final class RunService extends Service {
    static final String EXTRA_PROJECT = "dev.smallwright.controller.extra.PROJECT";
    static final String EXTRA_CHAT = "dev.smallwright.controller.extra.CHAT";
    private static final String STOP = "dev.smallwright.controller.action.STOP_RUN";
    private static final String EXTRA_RUN = "dev.smallwright.controller.extra.RUN";
    static final String CHANNEL = "runs";
    private static final int WORKING = 1;
    private static final int ENDED = 2;

    private static RunCoordinator held;
    private static RunService started;
    private static boolean starting;
    private static boolean working;
    private static Notification latest;

    private boolean workerBound;
    private final ServiceConnection worker =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {}

                @Override
                public void onServiceDisconnected(ComponentName name) {}
            };

    /** The coordinator whose work is still going, or a fresh one when nothing is. */
    static RunCoordinator coordinator(Supplier<RunCoordinator> fresh) {
        if (held == null || !held.isBusy()) held = fresh.get();
        return held;
    }

    /** The screen that made this coordinator is gone; an idle one is not kept for the next. */
    static void release(RunCoordinator coordinator) {
        if (held == coordinator && !coordinator.isBusy()) held = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            if (held != null) held.stopFromNotice(intent.getStringExtra(EXTRA_RUN));
            if (started != this) stopSelf(startId);
            return START_NOT_STICKY;
        }
        starting = false;
        try {
            startForeground(WORKING, latest, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } catch (RuntimeException refused) {
            Log.w("SmallwrightRun", "Runs continue without the foreground service", refused);
            stopSelf();
            return START_NOT_STICKY;
        }
        started = this;
        if (!workerBound) workerBound = BuildWorker.bind(this, worker);
        if (!working) finish();
        return START_NOT_STICKY;
    }

    /** Android's daily limit for background data work was reached; the run itself goes on. */
    @Override
    public void onTimeout(int startId, int foregroundServiceType) {
        finish();
    }

    private void finish() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        if (started == this) started = null;
    }

    @Override
    public void onDestroy() {
        if (started == this) started = null;
        if (workerBound) unbindService(worker);
        workerBound = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Shows run notices as Android notifications; the working one keeps this service running. */
    static final class Shade implements RunNotices.Shade {
        private final Context context;

        Shade(Context context) {
            this.context = context;
        }

        @Override
        public void show(RunNotice notice) {
            NotificationManager manager = manager();
            Notification shown = notification(notice);
            if (!notice.going()) {
                endWork();
                manager.notify(ENDED, shown);
                return;
            }
            manager.cancel(ENDED);
            latest = shown;
            working = true;
            if (started != null) manager.notify(WORKING, shown);
            else if (!starting) {
                try {
                    context.startForegroundService(new Intent(context, RunService.class));
                    starting = true;
                } catch (RuntimeException refused) {
                    Log.w(
                            "SmallwrightRun",
                            "Runs continue without the foreground service",
                            refused);
                }
            }
        }

        @Override
        public void clear() {
            endWork();
            manager().cancel(ENDED);
        }

        private void endWork() {
            working = false;
            if (started != null) started.finish();
        }

        private NotificationManager manager() {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(
                    new NotificationChannel(
                            CHANNEL, "Runs", NotificationManager.IMPORTANCE_DEFAULT));
            return manager;
        }

        private Notification notification(RunNotice notice) {
            Intent open =
                    new Intent(context, OpenProjectActivity.class)
                            .putExtra(EXTRA_PROJECT, notice.projectId)
                            .putExtra(EXTRA_CHAT, notice.chatId)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Icon icon = Icon.createWithResource(context, R.drawable.run_notice);
            Notification.Builder builder =
                    new Notification.Builder(context, CHANNEL)
                            .setSmallIcon(icon)
                            .setColor(0xFF7AA2F7)
                            .setSubText(notice.projectTitle)
                            .setContentTitle(notice.title)
                            .setContentText(notice.text)
                            .setStyle(new Notification.BigTextStyle().bigText(notice.text))
                            .setContentIntent(
                                    PendingIntent.getActivity(
                                            context,
                                            0,
                                            open,
                                            PendingIntent.FLAG_IMMUTABLE
                                                    | PendingIntent.FLAG_UPDATE_CURRENT))
                            .setAutoCancel(!notice.going());
            if (notice.going()) {
                Intent stop =
                        new Intent(context, RunService.class)
                                .setAction(STOP)
                                .putExtra(EXTRA_RUN, notice.runId);
                builder.setOngoing(true)
                        .setOnlyAlertOnce(notice.kind == RunNotice.Kind.WORKING)
                        .setGroup(CHANNEL)
                        .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                        .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                        .addAction(
                                new Notification.Action.Builder(
                                                icon,
                                                Labels.STOP,
                                                PendingIntent.getService(
                                                        context,
                                                        0,
                                                        stop,
                                                        PendingIntent.FLAG_IMMUTABLE
                                                                | PendingIntent
                                                                        .FLAG_UPDATE_CURRENT))
                                        .build());
            }
            return builder.build();
        }
    }
}
