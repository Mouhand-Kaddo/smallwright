package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.SystemClock;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Disposable project in an isolated registry whose own manifest declares a second activity and the
 * Internet permission, built by the real worker; no model request is made.
 */
final class ManifestBuildProbe {
    private ManifestBuildProbe() {}

    static String run(Context target, String shape) throws Exception {
        boolean oldProject = shape.equals("oldProject");
        File root =
                Files.createTempDirectory(target.getFilesDir().toPath(), "manifest-build-")
                        .toFile();
        File cache = new File(root, "cache");
        if (!cache.mkdir()) throw new AssertionError("Probe cache unavailable");
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
                };
        ProjectIdentity project =
                new ProjectRegistry(new File(root, "projects"), root).create("Two screens");
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        String packageName = project.packageName();
        String java = "java/" + packageName.replace('.', '/') + "/";
        int revision;
        if (oldProject) {
            revision = source.commit(0, Map.of(), Set.of(AppManifestPolicy.PATH));
        } else {
            String manifest =
                    source.generatedManifest()
                            .replace(
                                    "<application",
                                    "<uses-permission android:name=\"android.permission.INTERNET\"/>"
                                            + "<application")
                            .replace(
                                    "</application>",
                                    "<activity android:name=\".DetailActivity\""
                                            + " android:exported=\"false\"/></application>");
            Map<String, byte[]> files = new TreeMap<>();
            files.put(AppManifestPolicy.PATH, manifest.getBytes(StandardCharsets.UTF_8));
            files.put(java + "MainActivity.java", mainActivity(packageName));
            files.put(java + "DetailActivity.java", detailActivity(packageName));
            revision = source.commit(0, files, Set.of());
        }

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
        Intent bind =
                new Intent()
                        .setComponent(
                                new ComponentName(
                                        "dev.smallwright.worker",
                                        "dev.smallwright.worker.BuildService"));
        if (!target.bindService(bind, connection, Context.BIND_AUTO_CREATE))
            throw new AssertionError("Worker bind refused");
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || worker.get() == null)
                throw new AssertionError("Worker unavailable");
            long start = SystemClock.elapsedRealtime();
            VerifiedArtifact artifact =
                    new LocalBuildCoordinator(isolated, source, project)
                            .buildCommittedRevision(revision, 1, worker.get());
            long buildMillis = SystemClock.elapsedRealtime() - start;
            artifact.verifyUnchanged();
            File apk = new File(project.directory(), "artifacts/app-v1.apk");
            PackageInfo info =
                    target.getPackageManager()
                            .getPackageArchiveInfo(
                                    apk.getAbsolutePath(),
                                    PackageManager.GET_PERMISSIONS | PackageManager.GET_ACTIVITIES);
            if (info == null) throw new AssertionError("APK unreadable");
            if (oldProject
                    ? info.activities.length != 1 || info.requestedPermissions != null
                    : info.activities.length != 2
                            || !Arrays.asList(info.requestedPermissions)
                                    .contains("android.permission.INTERNET"))
                throw new AssertionError("APK has the wrong activities or permissions");
            return "Manifest shape="
                    + shape
                    + " package="
                    + packageName
                    + " buildMs="
                    + buildMillis
                    + " activities="
                    + info.activities.length
                    + " permissions="
                    + Arrays.toString(info.requestedPermissions)
                    + " apkSha256="
                    + artifact.sha256()
                    + " apkPath="
                    + apk.getAbsolutePath()
                    + " projectRoot="
                    + root.getAbsolutePath()
                    + "\n";
        } finally {
            target.unbindService(connection);
        }
    }

    private static byte[] mainActivity(String packageName) {
        return ("package "
                        + packageName
                        + ";\n"
                        + "import android.app.Activity;\n"
                        + "import android.content.Intent;\n"
                        + "import android.os.Bundle;\n"
                        + "import android.widget.Button;\n"
                        + "import android.widget.LinearLayout;\n"
                        + "import android.widget.TextView;\n"
                        + "public final class MainActivity extends Activity {\n"
                        + "  @Override protected void onCreate(Bundle state) {\n"
                        + "    super.onCreate(state);\n"
                        + "    LinearLayout layout = new LinearLayout(this);\n"
                        + "    layout.setOrientation(LinearLayout.VERTICAL);\n"
                        + "    layout.setPadding(48, 200, 48, 48);\n"
                        + "    TextView title = new TextView(this);\n"
                        + "    title.setText(\"First screen\");\n"
                        + "    title.setTextSize(28);\n"
                        + "    Button open = new Button(this);\n"
                        + "    open.setText(\"Open second screen\");\n"
                        + "    open.setOnClickListener(v -> startActivity(new Intent(this, DetailActivity.class)));\n"
                        + "    layout.addView(title);\n"
                        + "    layout.addView(open);\n"
                        + "    setContentView(layout);\n"
                        + "  }\n"
                        + "}\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] detailActivity(String packageName) {
        return ("package "
                        + packageName
                        + ";\n"
                        + "import android.app.Activity;\n"
                        + "import android.os.Bundle;\n"
                        + "import android.widget.TextView;\n"
                        + "import java.io.ByteArrayOutputStream;\n"
                        + "import java.io.InputStream;\n"
                        + "import java.net.HttpURLConnection;\n"
                        + "import java.net.URL;\n"
                        + "public final class DetailActivity extends Activity {\n"
                        + "  @Override protected void onCreate(Bundle state) {\n"
                        + "    super.onCreate(state);\n"
                        + "    TextView view = new TextView(this);\n"
                        + "    view.setPadding(48, 200, 48, 48);\n"
                        + "    view.setTextSize(24);\n"
                        + "    view.setText(\"Second screen: fetching https://example.com ...\");\n"
                        + "    setContentView(view);\n"
                        + "    new Thread(() -> {\n"
                        + "      String result;\n"
                        + "      try {\n"
                        + "        HttpURLConnection connection = (HttpURLConnection) new URL(\"https://example.com\").openConnection();\n"
                        + "        connection.setConnectTimeout(15000);\n"
                        + "        connection.setReadTimeout(15000);\n"
                        + "        ByteArrayOutputStream body = new ByteArrayOutputStream();\n"
                        + "        try (InputStream in = connection.getInputStream()) {\n"
                        + "          byte[] buffer = new byte[4096];\n"
                        + "          int read;\n"
                        + "          while ((read = in.read(buffer)) != -1) body.write(buffer, 0, read);\n"
                        + "        }\n"
                        + "        String text = body.toString(\"UTF-8\");\n"
                        + "        int start = text.indexOf(\"<title>\");\n"
                        + "        int end = text.indexOf(\"</title>\");\n"
                        + "        String title = start >= 0 && end > start ? text.substring(start + 7, end) : \"no title\";\n"
                        + "        result = \"Fetched: \" + title + \" (HTTP \" + connection.getResponseCode() + \", \" + body.size() + \" bytes)\";\n"
                        + "      } catch (Exception failure) {\n"
                        + "        result = \"Fetch failed: \" + failure;\n"
                        + "      }\n"
                        + "      final String shown = result;\n"
                        + "      runOnUiThread(() -> view.setText(\"Second screen\\n\" + shown));\n"
                        + "    }).start();\n"
                        + "  }\n"
                        + "}\n")
                .getBytes(StandardCharsets.UTF_8);
    }
}
