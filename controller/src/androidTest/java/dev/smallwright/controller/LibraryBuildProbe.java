package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.SystemClock;
import dev.smallwright.libraries.LibraryList;
import dev.smallwright.libraries.LibraryResolver;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Disposable project in an isolated registry using AndroidX RecyclerView, Material cards and
 * WorkManager, built twice by the real worker (cold, then with kept library dex); no model request.
 * Libraries are resolved into the controller's real library cache, as a run would.
 */
final class LibraryBuildProbe {
    static final String LIBRARIES =
            "androidx.recyclerview:recyclerview:1.4.0\n"
                    + "androidx.work:work-runtime:2.12.0\n"
                    + "com.google.android.material:material:1.14.0\n";

    private LibraryBuildProbe() {}

    static String run(Context target, String shape) throws Exception {
        File root =
                Files.createTempDirectory(target.getFilesDir().toPath(), "library-build-").toFile();
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
                new ProjectRegistry(new File(root, "projects"), root).create("Material cards");
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        String packageName = project.packageName();
        String java = "java/" + packageName.replace('.', '/') + "/";
        String manifest =
                source.generatedManifest()
                        .replace(
                                "android:theme=\"@android:style/Theme.Material.Light.NoActionBar\"",
                                "android:theme=\"@style/Theme.Material3.Light.NoActionBar\"");
        Map<String, byte[]> files = new TreeMap<>();
        files.put(AppManifestPolicy.PATH, bytes(manifest));
        files.put(LibraryList.PATH, bytes(LIBRARIES));
        files.put(java + "MainActivity.java", bytes(mainActivity(packageName)));
        files.put(java + "Refresh.java", bytes(refresh(packageName)));
        files.put("res/layout/card.xml", bytes(CARD));
        files.put("res/values/strings.xml", bytes(STRINGS));
        int revision = source.commit(0, files, Set.of());

        long resolveStart = SystemClock.elapsedRealtime();
        List<LibraryResolver.Resolved> resolved =
                LibraryTools.resolve(LibraryTools.repositories(target), bytes(LIBRARIES));
        long resolveMillis = SystemClock.elapsedRealtime() - resolveStart;
        long libraryBytes = 0;
        int aars = 0;
        for (LibraryResolver.Resolved library : resolved) {
            libraryBytes += library.file.length();
            if (library.manifest != null) aars++;
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
            LocalBuildCoordinator builds = new LocalBuildCoordinator(isolated, source, project);
            StringBuilder report =
                    new StringBuilder("Libraries shape=")
                            .append(shape)
                            .append(" package=")
                            .append(packageName)
                            .append(" resolved=")
                            .append(resolved.size())
                            .append(" aars=")
                            .append(aars)
                            .append(" libraryBytes=")
                            .append(libraryBytes)
                            .append(" resolveMs=")
                            .append(resolveMillis);
            VerifiedArtifact artifact = null;
            int builds_ = "twice".equals(shape) ? 2 : 1;
            for (int build = 1; build <= builds_; build++) {
                long start = SystemClock.elapsedRealtime();
                artifact = builds.buildCommittedRevision(revision, build, worker.get());
                report.append(" build")
                        .append(build)
                        .append("Ms=")
                        .append(SystemClock.elapsedRealtime() - start);
            }
            artifact.verifyUnchanged();
            File apk = artifact.file();
            List<String> dexEntries = new ArrayList<>();
            try (ZipFile zip = new ZipFile(apk)) {
                for (ZipEntry entry : Collections.list(zip.entries()))
                    if (entry.getName().matches("classes[0-9]*\\.dex"))
                        dexEntries.add(entry.getName() + ":" + entry.getSize());
            }
            PackageInfo info =
                    target.getPackageManager()
                            .getPackageArchiveInfo(
                                    apk.getAbsolutePath(),
                                    PackageManager.GET_PERMISSIONS
                                            | PackageManager.GET_SERVICES
                                            | PackageManager.GET_RECEIVERS
                                            | PackageManager.GET_PROVIDERS
                                            | PackageManager.MATCH_DISABLED_COMPONENTS);
            if (info == null) throw new AssertionError("APK unreadable");
            List<String> components = new ArrayList<>();
            for (ComponentInfo[] group :
                    new ComponentInfo[][] {info.services, info.receivers, info.providers})
                if (group != null)
                    for (ComponentInfo component : group) components.add(component.name);
            if (dexEntries.size() < 2
                    || !components.contains(
                            "androidx.work.impl.background.systemjob.SystemJobService")
                    || !components.contains("androidx.startup.InitializationProvider"))
                throw new AssertionError(
                        "APK lacks several dex files or merged components: "
                                + dexEntries
                                + components);
            return report.append(" dex=")
                    .append(dexEntries)
                    .append(" components=")
                    .append(components)
                    .append(" permissions=")
                    .append(
                            info.requestedPermissions == null
                                    ? "[]"
                                    : String.join(",", info.requestedPermissions))
                    .append(" apkBytes=")
                    .append(apk.length())
                    .append(" apkSha256=")
                    .append(artifact.sha256())
                    .append(" apkPath=")
                    .append(apk.getAbsolutePath())
                    .append(" projectRoot=")
                    .append(root.getAbsolutePath())
                    .append("\n")
                    .toString();
        } finally {
            target.unbindService(connection);
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static final String CARD =
            "<com.google.android.material.card.MaterialCardView"
                    + " xmlns:android=\"http://schemas.android.com/apk/res/android\""
                    + " xmlns:app=\"http://schemas.android.com/apk/res-auto\""
                    + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\""
                    + " android:layout_margin=\"8dp\" app:cardElevation=\"4dp\" app:cardCornerRadius=\"12dp\">"
                    + "<TextView android:id=\"@+id/text\" android:layout_width=\"match_parent\""
                    + " android:layout_height=\"wrap_content\" android:padding=\"20dp\""
                    + " android:textAppearance=\"?attr/textAppearanceTitleMedium\"/>"
                    + "</com.google.android.material.card.MaterialCardView>\n";

    private static final String STRINGS =
            "<resources><string name=\"title\">Material cards</string>"
                    + "<string name=\"character_counter_pattern\">Probe override</string>"
                    + "</resources>\n";

    private static String mainActivity(String packageName) {
        return "package "
                + packageName
                + ";\n"
                + "import android.os.Bundle;\n"
                + "import android.view.LayoutInflater;\n"
                + "import android.view.View;\n"
                + "import android.view.ViewGroup;\n"
                + "import android.widget.TextView;\n"
                + "import androidx.appcompat.app.AppCompatActivity;\n"
                + "import androidx.recyclerview.widget.LinearLayoutManager;\n"
                + "import androidx.recyclerview.widget.RecyclerView;\n"
                + "import androidx.work.OneTimeWorkRequest;\n"
                + "import androidx.work.WorkManager;\n"
                + "public final class MainActivity extends AppCompatActivity {\n"
                + "  @Override protected void onCreate(Bundle state) {\n"
                + "    super.onCreate(state);\n"
                + "    RecyclerView list = new RecyclerView(this);\n"
                + "    list.setPadding(0, 120, 0, 0);\n"
                + "    list.setClipToPadding(false);\n"
                + "    list.setLayoutManager(new LinearLayoutManager(this));\n"
                + "    final String override = getString(com.google.android.material.R.string.character_counter_pattern);\n"
                + "    list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {\n"
                + "      @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {\n"
                + "        View card = LayoutInflater.from(parent.getContext()).inflate(R.layout.card, parent, false);\n"
                + "        return new RecyclerView.ViewHolder(card) {};\n"
                + "      }\n"
                + "      @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {\n"
                + "        TextView text = holder.itemView.findViewById(R.id.text);\n"
                + "        text.setText(position == 0 ? getString(R.string.title) + \" (\" + override + \")\" : \"Card \" + position);\n"
                + "      }\n"
                + "      @Override public int getItemCount() { return 40; }\n"
                + "    });\n"
                + "    setContentView(list);\n"
                + "    WorkManager.getInstance(this).enqueue(OneTimeWorkRequest.from(Refresh.class));\n"
                + "  }\n"
                + "}\n";
    }

    private static String refresh(String packageName) {
        return "package "
                + packageName
                + ";\n"
                + "import android.content.Context;\n"
                + "import android.util.Log;\n"
                + "import androidx.work.Worker;\n"
                + "import androidx.work.WorkerParameters;\n"
                + "public final class Refresh extends Worker {\n"
                + "  public Refresh(Context context, WorkerParameters parameters) { super(context, parameters); }\n"
                + "  @Override public Result doWork() { Log.i(\"LibraryProbeApp\", \"Refresh ran\"); return Result.success(); }\n"
                + "}\n";
    }
}
