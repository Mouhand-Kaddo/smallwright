package dev.smallwright.controller;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.util.UUID;

/**
 * The builder's own way to open a project or chat: the crash hand-off and run notifications start
 * it. Not exported, so no other app can. It passes the request on to the launcher activity with a
 * pass that exists only in this process; the launcher ignores those extras without it.
 */
public final class OpenProjectActivity extends Activity {
    private static final String EXTRA_PASS = "dev.smallwright.controller.extra.OPEN_PASS";
    private static final String PASS = UUID.randomUUID().toString();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state == null)
            startActivity(
                    new Intent(this, MainActivity.class)
                            .putExtras(getIntent())
                            .putExtra(EXTRA_PASS, PASS)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    /** True only for an intent this activity passed on in this process. */
    static boolean fromBuilder(Intent intent) {
        try {
            return PASS.equals(intent.getStringExtra(EXTRA_PASS));
        } catch (RuntimeException unreadable) {
            return false;
        }
    }
}
