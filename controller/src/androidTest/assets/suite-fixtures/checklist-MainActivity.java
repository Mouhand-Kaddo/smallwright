package $PACKAGE;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONException;

public final class MainActivity extends Activity {
    private static final String REPORT = "crash-report.txt";
    private static final int MAX_REPORT_BYTES = 16384;
    private static final int EXPORT_REPORT = 1;
    private static final AtomicBoolean HANDLER_INSTALLED = new AtomicBoolean(false);
    private final ArrayList<String> items = new ArrayList<>();
    private LinearLayout list;
    private byte[] pendingReport;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        installCrashHandler();
        loadItems();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setOnApplyWindowInsetsListener(
                new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                        Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                        view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                        return insets;
                    }
                });
        TextView heading = new TextView(this);
        heading.setText("Checklist");
        final EditText input = new EditText(this);
        input.setHint("New item");
        Button add = new Button(this);
        add.setText("Add item");
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        add.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String text = input.getText().toString().trim();
                        if (text.isEmpty()) return;
                        items.add(text);
                        saveItems();
                        input.setText("");
                        showItems();
                    }
                });
        Button report = new Button(this);
        report.setText("Preview crash report");
        report.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        previewReport();
                    }
                });
        layout.addView(heading);
        layout.addView(input);
        layout.addView(add);
        layout.addView(list);
        layout.addView(report);
        showItems();
        setContentView(layout);
    }

    private void loadItems() {
        items.clear();
        try {
            JSONArray saved = new JSONArray(getPreferences(0).getString("items", "[]"));
            for (int i = 0; i < saved.length(); i++) items.add(saved.getString(i));
        } catch (JSONException invalid) {
            items.clear();
        }
    }

    private void saveItems() {
        JSONArray saved = new JSONArray();
        for (String item : items) saved.put(item);
        getPreferences(0).edit().putString("items", saved.toString()).apply();
    }

    private void showItems() {
        list.removeAllViews();
        for (String item : items) {
            TextView row = new TextView(this);
            row.setText("- " + item);
            list.addView(row);
        }
    }

    private void installCrashHandler() {
        if (!HANDLER_INSTALLED.compareAndSet(false, true)) return;
        final android.content.Context app = getApplicationContext();
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        final AtomicBoolean handling = new AtomicBoolean(false);
        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, Throwable failure) {
                        if (handling.compareAndSet(false, true)) {
                            try {
                                byte[] report =
                                        formatReport(failure).getBytes(StandardCharsets.UTF_8);
                                if (report.length > MAX_REPORT_BYTES) {
                                    int end = MAX_REPORT_BYTES;
                                    while ((report[end] & 0xc0) == 0x80) end--;
                                    byte[] limited = new byte[end];
                                    System.arraycopy(report, 0, limited, 0, limited.length);
                                    report = limited;
                                }
                                try (FileOutputStream out =
                                        app.openFileOutput(REPORT, MODE_PRIVATE)) {
                                    out.write(report);
                                }
                            } catch (Throwable ignored) {
                                /* Crash reporting must not replace the original crash. */
                            }
                        }
                        if (previous != null) previous.uncaughtException(thread, failure);
                        else {
                            android.os.Process.killProcess(android.os.Process.myPid());
                            System.exit(10);
                        }
                    }
                });
    }

    private static String formatReport(Throwable failure) {
        StringBuilder text = new StringBuilder(4096);
        text.append("Generated app crash\nException messages and values omitted.\n");
        Throwable current = failure;
        for (int cause = 0; current != null && cause < 4 && text.length() < 12000; cause++) {
            text.append("Cause ")
                    .append(cause)
                    .append(": ")
                    .append(shortText(current.getClass().getName()))
                    .append('\n');
            StackTraceElement[] frames = current.getStackTrace();
            for (int i = 0; i < frames.length && i < 40 && text.length() < 12000; i++) {
                StackTraceElement frame = frames[i];
                text.append("  at ")
                        .append(shortText(frame.getClassName()))
                        .append('.')
                        .append(shortText(frame.getMethodName()))
                        .append(':')
                        .append(frame.getLineNumber())
                        .append('\n');
            }
            current = current.getCause();
        }
        return text.toString();
    }

    private static String shortText(String value) {
        if (value == null) return "";
        return value.length() <= 256 ? value : value.substring(0, 256);
    }

    private byte[] readReport() throws IOException {
        try (FileInputStream in = openFileInput(REPORT);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > MAX_REPORT_BYTES)
                    throw new IOException("Crash report is too large");
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private void previewReport() {
        final byte[] report;
        try {
            report = readReport();
        } catch (java.io.FileNotFoundException missing) {
            message("No crash report saved");
            return;
        } catch (IOException error) {
            message("Could not read crash report");
            return;
        }
        if (report.length == 0) {
            message("No crash report saved");
            return;
        }
        ScrollView scroll = new ScrollView(this);
        TextView preview = new TextView(this);
        preview.setPadding(24, 24, 24, 24);
        preview.setText(new String(report, StandardCharsets.UTF_8));
        scroll.addView(preview);
        new AlertDialog.Builder(this)
                .setTitle("Crash report preview")
                .setView(scroll)
                .setNegativeButton("Close", null)
                .setPositiveButton(
                        "Export file",
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                chooseExport(report);
                            }
                        })
                .show();
    }

    private void chooseExport(byte[] report) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "smallwright-crash-report.txt");
        pendingReport = report;
        try {
            startActivityForResult(intent, EXPORT_REPORT);
        } catch (RuntimeException unavailable) {
            pendingReport = null;
            message("Document picker unavailable");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_REPORT) return;
        byte[] report = pendingReport;
        pendingReport = null;
        if (resultCode != RESULT_OK || report == null || data == null || data.getData() == null)
            return;
        if (!"content".equals(data.getData().getScheme())) {
            message("Invalid document destination");
            return;
        }
        try {
            if (report.length == 0) {
                message("No crash report saved");
                return;
            }
            try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "w")) {
                if (out == null) throw new IOException("Destination unavailable");
                out.write(report);
            }
            message("Crash report exported");
        } catch (IOException | SecurityException error) {
            message("Could not export crash report");
        }
    }

    private void message(String text) {
        new AlertDialog.Builder(this).setMessage(text).setPositiveButton("OK", null).show();
    }
}
