package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Builds a disposable note fixture through the real Binder worker and signer. */
final class NoteRuntimeProbe {
    private static final String SOURCE =
            """
            package dev.smallwright.generated.counter;
            import android.app.Activity;
            import android.os.Bundle;
            import android.view.View;
            import android.widget.Button;
            import android.widget.EditText;
            import android.widget.LinearLayout;
            import android.widget.TextView;
            public final class MainActivity extends Activity {
              private EditText note;
              private TextView status;
              @Override protected void onCreate(Bundle state) {
                super.onCreate(state);
                LinearLayout root = new LinearLayout(this);
                root.setOrientation(LinearLayout.VERTICAL);
                note = new EditText(this);
                note.setHint("Enter fixture note");
                note.setText(getPreferences(0).getString("note", ""));
                root.addView(note);
                Button save = new Button(this);
                save.setText("SAVE NOTE");
                status = new TextView(this);
                status.setText("Ready");
                save.setOnClickListener(new View.OnClickListener() {
                  @Override public void onClick(View view) {
                    String value = note.getText().toString();
                    getPreferences(0).edit().putString("note", value).apply();
                    status.setText("Saved: " + value);
                  }
                });
                root.addView(save);
                root.addView(status);
                setContentView(root);
              }
            }
            """;

    static String build(Context target) throws Exception {
        File root =
                Files.createTempDirectory(target.getFilesDir().toPath(), "note-runtime-").toFile();
        File cache = new File(root, "cache");
        if (!cache.mkdir()) throw new AssertionError("Fixture cache unavailable");
        Context isolated =
                new ContextWrapper(target) {
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
                        return root;
                    }
                };
        ProjectSourceStore source = new ProjectSourceStore(isolated);
        source.initialize();
        int revision = source.writeSource(0, SOURCE);
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBuildService> worker = new AtomicReference<>();
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        worker.set(IBuildService.Stub.asInterface(binder));
                        connected.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        worker.set(null);
                    }
                };
        Intent intent =
                new Intent()
                        .setComponent(
                                new ComponentName(
                                        "dev.smallwright.worker",
                                        "dev.smallwright.worker.BuildService"));
        if (!target.bindService(intent, connection, Context.BIND_AUTO_CREATE))
            throw new AssertionError("Worker bind refused");
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || worker.get() == null)
                throw new AssertionError("Worker unavailable");
            VerifiedArtifact artifact =
                    new LocalBuildCoordinator(isolated, source)
                            .buildCommittedRevision(revision, 1, worker.get());
            artifact.verifyUnchanged();
            return "Note fixture signed on emulator: "
                    + new File(root, "projects/counter-alpha/artifacts/counter-v1.apk")
                            .getAbsolutePath()
                    + " SHA-256 "
                    + artifact.sha256()
                    + "\n";
        } finally {
            target.unbindService(connection);
        }
    }
}
