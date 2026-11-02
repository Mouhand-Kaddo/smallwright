package dev.smallwright.controller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * One look_at_app (ADR 0008): asks the app's own BuilderLink$Look for a picture and hands what it
 * sent back to the waiting run. Not exported; finishing it brings the person back to the builder.
 */
public final class LookActivity extends Activity {
    private static final String EXTRA_PACKAGE = "dev.smallwright.controller.extra.LOOK_PACKAGE";
    private static final String EXTRA_TARGET = "dev.smallwright.controller.extra.LOOK_TARGET";
    private static final String EXTRA_NONCE = "dev.smallwright.controller.extra.LOOK_NONCE";
    private static final int REQUEST = 1;
    private static final Map<String, BlockingQueue<AppLink.Reply>> WAITING =
            new ConcurrentHashMap<>();
    private static final Map<String, LookActivity> OPEN = new ConcurrentHashMap<>();
    private static final java.util.Set<String> STARTED = ConcurrentHashMap.newKeySet();
    private static final long START_MILLIS = 5000;
    private static final long LOOK_MILLIS = 45000;
    private String nonce;
    private String packageName;

    /**
     * Opens the app's activity and waits for its picture, until Stop. A look that started but has
     * no answer after {@link #LOOK_MILLIS}, because the app hangs or never shows, ends as not
     * copied; that bounds Android and the app, never the model.
     */
    static AppLink.Reply look(
            Context context, String packageName, String activity, BooleanSupplier active)
            throws Exception {
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        String nonce = AppLinkSources.hex(random);
        BlockingQueue<AppLink.Reply> reply = new ArrayBlockingQueue<>(1);
        WAITING.put(nonce, reply);
        try {
            context.startActivity(
                    new Intent(context, LookActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(EXTRA_PACKAGE, packageName)
                            .putExtra(EXTRA_TARGET, activity)
                            .putExtra(EXTRA_NONCE, nonce));
            return await(
                    reply,
                    () -> STARTED.contains(nonce),
                    active,
                    START_MILLIS,
                    LOOK_MILLIS,
                    () -> new Handler(Looper.getMainLooper()).post(() -> close(nonce)));
        } finally {
            WAITING.remove(nonce);
            STARTED.remove(nonce);
        }
    }

    /**
     * Waits for the app's answer. Not started after {@code startMillis}: not opened. Started but
     * unanswered after {@code lookMillis}: closed and not copied. Stop closes it and throws.
     */
    static AppLink.Reply await(
            BlockingQueue<AppLink.Reply> reply,
            BooleanSupplier started,
            BooleanSupplier active,
            long startMillis,
            long lookMillis,
            Runnable close)
            throws InterruptedException {
        long asked = System.nanoTime();
        Long startedAt = null;
        while (true) {
            AppLink.Reply answered = reply.poll(200, TimeUnit.MILLISECONDS);
            if (answered != null) return answered;
            long now = System.nanoTime();
            if (!started.getAsBoolean()) {
                if (now - asked > TimeUnit.MILLISECONDS.toNanos(startMillis))
                    return AppLink.Reply.failed(AppLink.Reply.NOT_OPENED);
            } else if (startedAt == null) {
                startedAt = now;
            } else if (now - startedAt > TimeUnit.MILLISECONDS.toNanos(lookMillis)) {
                close.run();
                return AppLink.Reply.failed(AppLink.Reply.NOT_COPIED);
            }
            if (!active.getAsBoolean()) {
                close.run();
                throw new IllegalStateException("Run is no longer active");
            }
        }
    }

    /** Ends the look and brings the person back to the builder, over whatever the look opened. */
    private static void close(String nonce) {
        LookActivity open = OPEN.get(nonce);
        if (open == null) return;
        open.finishActivity(REQUEST);
        open.startActivity(
                new Intent(open, MainActivity.class)
                        .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        open.finish();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        nonce = getIntent().getStringExtra(EXTRA_NONCE);
        packageName = getIntent().getStringExtra(EXTRA_PACKAGE);
        String target = getIntent().getStringExtra(EXTRA_TARGET);
        if (state != null
                || nonce == null
                || packageName == null
                || target == null
                || !WAITING.containsKey(nonce)) {
            deliver(AppLink.Reply.failed(AppLink.Reply.NOT_ANSWERED));
            return;
        }
        STARTED.add(nonce);
        OPEN.put(nonce, this);
        try {
            startActivityForResult(
                    new Intent()
                            .setClassName(
                                    packageName,
                                    AppLinkSources.component(packageName, AppLinkSources.LOOK))
                            .putExtra(AppLinkSources.EXTRA_ACTIVITY, target),
                    REQUEST);
        } catch (RuntimeException unavailable) {
            deliver(AppLink.Reply.failed(AppLink.Reply.NOT_ANSWERED));
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST) return;
        Uri picture = data == null ? null : data.getData();
        if (result != RESULT_OK || picture == null) {
            deliver(AppLink.Reply.failed(failure(result)));
            return;
        }
        if (!AppLinkRules.isLookUri(
                picture.getScheme(),
                picture.getAuthority(),
                picture.getPath(),
                picture.getQuery(),
                packageName)) {
            deliver(AppLink.Reply.failed(AppLink.Reply.NOT_ANSWERED));
            return;
        }
        Thread reader =
                new Thread(
                        () -> {
                            AppLink.Reply read;
                            try (InputStream input =
                                    getContentResolver().openInputStream(picture)) {
                                read = AppLink.Reply.picture(AppLinkRules.readBounded(input));
                            } catch (Exception unreadable) {
                                read = AppLink.Reply.failed(AppLink.Reply.NOT_COPIED);
                            }
                            AppLink.Reply answer = read;
                            runOnUiThread(() -> deliver(answer));
                        },
                        "look-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private static int failure(int result) {
        if (result == RESULT_FIRST_USER) return AppLink.Reply.NO_ACTIVITY;
        if (result == RESULT_FIRST_USER + 1) return AppLink.Reply.CLOSED;
        if (result == RESULT_FIRST_USER + 2) return AppLink.Reply.NOT_COPIED;
        return AppLink.Reply.NOT_ANSWERED;
    }

    private void deliver(AppLink.Reply reply) {
        if (nonce != null) {
            BlockingQueue<AppLink.Reply> waiting = WAITING.get(nonce);
            if (waiting != null) waiting.offer(reply);
            OPEN.remove(nonce, this);
        }
        if (!isFinishing()) finish();
    }

    @Override
    protected void onDestroy() {
        if (nonce != null && OPEN.remove(nonce, this)) {
            BlockingQueue<AppLink.Reply> waiting = WAITING.get(nonce);
            if (waiting != null) waiting.offer(AppLink.Reply.failed(AppLink.Reply.CLOSED));
        }
        super.onDestroy();
    }
}
