package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
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
import java.util.Arrays;
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
 * Disposable project in an isolated registry using Conscrypt, an Android library with prebuilt
 * native code, built once by the real worker; no model request. The app keeps its native code in
 * the APK ({@code extractNativeLibs="false"}), hashes a text through BoringSSL and shows the
 * result.
 */
final class NativeLibraryBuildProbe {
    static final String LIBRARIES = "org.conscrypt:conscrypt-android:2.7.0\n";

    private NativeLibraryBuildProbe() {}

    static String run(Context target) throws Exception {
        File root =
                Files.createTempDirectory(target.getFilesDir().toPath(), "native-build-").toFile();
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
                new ProjectRegistry(new File(root, "projects"), root).create("Native code");
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        String packageName = project.packageName();
        String manifest =
                source.generatedManifest()
                        .replace(
                                "<application", "<application android:extractNativeLibs=\"false\"");
        if (!manifest.contains("extractNativeLibs"))
            throw new AssertionError("Probe manifest lacks extractNativeLibs");
        Map<String, byte[]> files = new TreeMap<>();
        files.put(AppManifestPolicy.PATH, bytes(manifest));
        files.put(LibraryList.PATH, bytes(LIBRARIES));
        files.put(
                "java/" + packageName.replace('.', '/') + "/MainActivity.java",
                bytes(mainActivity(packageName)));
        int revision = source.commit(0, files, Set.of());

        long resolveStart = SystemClock.elapsedRealtime();
        List<LibraryResolver.Resolved> resolved =
                LibraryTools.resolve(LibraryTools.repositories(target), bytes(LIBRARIES));
        long resolveMillis = SystemClock.elapsedRealtime() - resolveStart;

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
            File apk = artifact.file();
            List<String> nativeEntries = new ArrayList<>();
            try (ZipFile zip = new ZipFile(apk)) {
                for (ZipEntry entry : Collections.list(zip.entries()))
                    if (entry.getName().endsWith(".so"))
                        nativeEntries.add(
                                entry.getName()
                                        + ":"
                                        + entry.getSize()
                                        + (entry.getMethod() == ZipEntry.STORED
                                                ? ":stored"
                                                : ":compressed"));
            }
            if (nativeEntries.isEmpty()) throw new AssertionError("APK holds no native library");
            return "Native package="
                    + packageName
                    + " abis="
                    + Arrays.toString(Build.SUPPORTED_ABIS)
                    + " resolved="
                    + resolved.size()
                    + " resolveMs="
                    + resolveMillis
                    + " buildMs="
                    + buildMillis
                    + " native="
                    + nativeEntries
                    + " apkBytes="
                    + apk.length()
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

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String mainActivity(String packageName) {
        return "package "
                + packageName
                + ";\n"
                + "import android.app.Activity;\n"
                + "import android.os.Bundle;\n"
                + "import android.util.Log;\n"
                + "import android.widget.TextView;\n"
                + "import java.io.BufferedReader;\n"
                + "import java.io.FileReader;\n"
                + "import java.nio.charset.StandardCharsets;\n"
                + "import java.security.MessageDigest;\n"
                + "import java.security.Provider;\n"
                + "import javax.net.ssl.SSLContext;\n"
                + "import org.conscrypt.Conscrypt;\n"
                + "public final class MainActivity extends Activity {\n"
                + "  @Override protected void onCreate(Bundle state) {\n"
                + "    super.onCreate(state);\n"
                + "    String report;\n"
                + "    try {\n"
                + "      Provider provider = Conscrypt.newProvider();\n"
                + "      byte[] hash = MessageDigest.getInstance(\"SHA-256\", provider)\n"
                + "          .digest(\"phone-local\".getBytes(StandardCharsets.UTF_8));\n"
                + "      StringBuilder hex = new StringBuilder();\n"
                + "      for (byte value : hash) hex.append(String.format(\"%02x\", value));\n"
                + "      SSLContext tls = SSLContext.getInstance(\"TLSv1.3\", provider);\n"
                + "      tls.init(null, null, null);\n"
                + "      int mapped = 0;\n"
                + "      try (BufferedReader maps = new BufferedReader(new FileReader(\"/proc/self/maps\"))) {\n"
                + "        for (String line; (line = maps.readLine()) != null; )\n"
                + "          if (line.contains(\" r-xp \") && line.endsWith(\"base.apk\")) mapped++;\n"
                + "      }\n"
                + "      Conscrypt.Version version = Conscrypt.version();\n"
                + "      report = \"Conscrypt \" + version.major() + \".\" + version.minor() + \".\" + version.patch()\n"
                + "          + \" native code loaded: \" + Conscrypt.isAvailable()\n"
                + "          + \"\\nSHA-256 of phone-local by BoringSSL:\\n\" + hex\n"
                + "          + \"\\nTLS context: \" + tls.getProvider().getName() + \" \" + tls.getProtocol()\n"
                + "          + \"\\nCode mapped from base.apk: \" + mapped\n"
                + "          + \"\\nnativeLibraryDir: \" + getApplicationInfo().nativeLibraryDir;\n"
                + "    } catch (Throwable failure) {\n"
                + "      report = \"Native library failed: \" + failure;\n"
                + "    }\n"
                + "    Log.i(\"NativeProbeApp\", report);\n"
                + "    TextView text = new TextView(this);\n"
                + "    text.setPadding(40, 160, 40, 40);\n"
                + "    text.setTextSize(18);\n"
                + "    text.setText(report);\n"
                + "    setContentView(text);\n"
                + "  }\n"
                + "}\n";
    }
}
