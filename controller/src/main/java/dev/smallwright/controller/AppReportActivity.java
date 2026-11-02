package dev.smallwright.controller;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import dev.smallwright.project.ProjectIdentity;

/**
 * Where a generated app's "Tell the builder" lands (ADR 0008). Exported with no intent filter. It
 * reads nothing from the intent until Android has named the caller and the caller is a project's
 * app signed by that project alone. It saves the report and opens the builder on that project,
 * through {@link OpenProjectActivity}; it never starts a run.
 */
public final class AppReportActivity extends Activity {
    static final String EXTRA_PROJECT = "dev.smallwright.controller.extra.CRASH_PROJECT";
    static final String DRAFT = "The app crashed. Read the crash report and fix the cause.";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        try {
            ProjectIdentity project = AppLinkAndroid.sender(this, getCallingPackage());
            if (project != null) {
                byte[] report = getIntent().getByteArrayExtra(AppLinkSources.EXTRA_REPORT);
                new AppCrashReports(project.directory())
                        .receive(
                                report,
                                AppLinkAndroid.installedVersion(this, project.packageName()));
                setResult(RESULT_OK);
                startActivity(
                        new Intent(this, OpenProjectActivity.class)
                                .putExtra(EXTRA_PROJECT, project.id())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (Exception | StackOverflowError | OutOfMemoryError refused) {
            Log.w("SmallwrightAppLink", "Refused a crash report: " + refused.getClass().getName());
        }
        finish();
    }
}
