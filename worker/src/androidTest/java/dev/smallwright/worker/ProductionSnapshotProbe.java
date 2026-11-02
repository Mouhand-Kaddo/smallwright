package dev.smallwright.worker;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Exercises the same snapshot build called by the authenticated Binder entrypoint. */
final class ProductionSnapshotProbe {
    private static final String SECOND_PACKAGE =
            "dev.smallwright.generated.app0123456789abcdef0123456789abcdef";

    private ProductionSnapshotProbe() {}

    static String run(Context testContext, Context workerContext) throws Exception {
        File scratch =
                Files.createTempDirectory(
                                workerContext.getCacheDir().toPath(), "production-snapshot-probe-")
                        .toFile();
        File buildCache = new File(scratch, "build-cache");
        if (!buildCache.mkdir()) throw new IOException("Could not create disposable build cache");
        Context disposableWorker =
                new ContextWrapper(workerContext) {
                    @Override
                    public File getCacheDir() {
                        return buildCache;
                    }
                };
        try {
            assertNoBundledJar(workerContext);
            File validSnapshot = new File(scratch, "valid.zip");
            byte[] source;
            try (InputStream input =
                    testContext.getAssets().open("default-generated-MainActivity.java")) {
                source = input.readAllBytes();
            }
            snapshot(validSnapshot, source);
            File artifact = new File(scratch, "unsigned.apk");
            String result = build(disposableWorker, validSnapshot, artifact);
            if (!"OK".equals(result))
                throw new AssertionError("Production build failed: " + result);
            if (new File(buildCache, "active-build").exists()) {
                throw new AssertionError("Production build retained scratch");
            }
            try (ZipFile apk = new ZipFile(artifact)) {
                for (String name :
                        new String[] {"AndroidManifest.xml", "resources.arsc", "classes.dex"}) {
                    if (apk.getEntry(name) == null) throw new AssertionError("APK missing " + name);
                }
                try (InputStream dex = apk.getInputStream(apk.getEntry("classes.dex"))) {
                    requireAppOnlyClassDefs(
                            dex.readAllBytes(), "dev.smallwright.generated.counter");
                }
            }

            File secondSnapshot = new File(scratch, "second-project.zip");
            snapshot(
                    secondSnapshot,
                    ("package "
                                    + SECOND_PACKAGE
                                    + "; "
                                    + "public final class MainActivity extends android.app.Activity {}")
                            .getBytes(StandardCharsets.UTF_8),
                    SECOND_PACKAGE);
            File secondArtifact = new File(scratch, "second-project.apk");
            String secondResult = build(disposableWorker, secondSnapshot, secondArtifact);
            if (!"OK".equals(secondResult))
                throw new AssertionError("Second project build failed: " + secondResult);
            try (ZipFile apk = new ZipFile(secondArtifact);
                    InputStream dex = apk.getInputStream(apk.getEntry("classes.dex"))) {
                requireAppOnlyClassDefs(dex.readAllBytes(), SECOND_PACKAGE);
            }

            File wrongPackageSnapshot = new File(scratch, "wrong-package.zip");
            snapshot(
                    wrongPackageSnapshot,
                    ("package dev.smallwright.generated.other; "
                                    + "public final class MainActivity extends android.app.Activity {}")
                            .getBytes(StandardCharsets.UTF_8));
            File wrongPackageArtifact = new File(scratch, "wrong-package.apk");
            String wrongPackageResult =
                    build(disposableWorker, wrongPackageSnapshot, wrongPackageArtifact);
            if (!"OK".equals(wrongPackageResult)) {
                throw new AssertionError(
                        "Wrong-package source build failed: " + wrongPackageResult);
            }
            try (ZipFile apk = new ZipFile(wrongPackageArtifact);
                    InputStream dex = apk.getInputStream(apk.getEntry("classes.dex"))) {
                if (new String(dex.readAllBytes(), StandardCharsets.ISO_8859_1)
                        .contains("dev/smallwright/generated/other/")) {
                    throw new AssertionError("Wrong-package class reached the dex");
                }
            }
            if (new File(buildCache, "active-build").exists()) {
                throw new AssertionError("Wrong-package build retained scratch");
            }
            return "Production snapshot: two package-isolated unsigned Java/XML APKs, "
                    + "app-only DEX, wrong-package source left out of the dex, scratch cleanup, no packaged android-36.jar\n";
        } finally {
            removeTree(scratch);
        }
    }

    private static String build(Context context, File snapshot, File artifact) throws IOException {
        ParcelFileDescriptor input =
                ParcelFileDescriptor.open(snapshot, ParcelFileDescriptor.MODE_READ_ONLY);
        ParcelFileDescriptor output =
                ParcelFileDescriptor.open(
                        artifact,
                        ParcelFileDescriptor.MODE_CREATE
                                | ParcelFileDescriptor.MODE_TRUNCATE
                                | ParcelFileDescriptor.MODE_READ_WRITE);
        return BuildService.buildSnapshot(context, input, output);
    }

    static void snapshot(File destination, byte[] source) throws IOException {
        snapshot(destination, source, "dev.smallwright.generated.counter");
    }

    private static void snapshot(File destination, byte[] source, String packageName)
            throws IOException {
        try (ZipOutputStream zip =
                new ZipOutputStream(Files.newOutputStream(destination.toPath()))) {
            entry(zip, "java/" + packageName.replace('.', '/') + "/MainActivity.java", source);
            entry(
                    zip,
                    "AndroidManifest.xml",
                    ("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                                    + "package=\""
                                    + packageName
                                    + "\">"
                                    + "<uses-sdk android:minSdkVersion=\"31\" android:targetSdkVersion=\"36\"/>"
                                    + "<application android:label=\"Counter\">"
                                    + "<activity android:name=\".MainActivity\" android:exported=\"true\">"
                                    + "<intent-filter><action android:name=\"android.intent.action.MAIN\"/>"
                                    + "<category android:name=\"android.intent.category.LAUNCHER\"/>"
                                    + "</intent-filter></activity></application></manifest>")
                            .getBytes(StandardCharsets.UTF_8));
            entry(
                    zip,
                    "res/layout/activity_main.xml",
                    ("<View "
                                    + "xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                                    + "android:layout_width=\"1dp\" android:layout_height=\"1dp\"/>")
                            .getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void entry(ZipOutputStream zip, String name, byte[] contents)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents);
        zip.closeEntry();
    }

    private static void assertNoBundledJar(Context context) throws IOException {
        try (ZipFile apk = new ZipFile(context.getApplicationInfo().sourceDir)) {
            if (apk.stream().anyMatch(e -> e.getName().endsWith("android-36.jar"))) {
                throw new AssertionError("Worker packages Android 36 compile JAR");
            }
        }
    }

    static void requireAppOnlyClassDefs(byte[] bytes, String packageName) {
        ByteBuffer dex = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 0x70 || bytes[0] != 'd' || bytes[1] != 'e' || bytes[2] != 'x') {
            throw new AssertionError("Invalid DEX header");
        }
        int strings = dex.getInt(0x3c);
        int types = dex.getInt(0x44);
        int classCount = dex.getInt(0x60);
        int classes = dex.getInt(0x64);
        if (classCount < 1 || classCount > 32)
            throw new AssertionError("Unexpected DEX class count");
        boolean mainFound = false;
        for (int i = 0; i < classCount; i++) {
            int typeIndex = dex.getInt(classes + i * 32);
            int stringIndex = dex.getInt(types + typeIndex * 4);
            int stringOffset = dex.getInt(strings + stringIndex * 4);
            int cursor = stringOffset;
            while ((bytes[cursor++] & 0x80) != 0) {
                /* UTF-16 length prefix. */
            }
            int end = cursor;
            while (bytes[end] != 0) end++;
            String name = new String(bytes, cursor, end - cursor, StandardCharsets.UTF_8);
            String packagePath = "L" + packageName.replace('.', '/') + "/";
            if (!name.startsWith(packagePath)) {
                throw new AssertionError("Platform or foreign class entered DEX: " + name);
            }
            if (name.equals(packagePath + "MainActivity;")) mainFound = true;
        }
        if (!mainFound) throw new AssertionError("DEX lacks generated MainActivity");
    }

    private static void removeTree(File file) throws IOException {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) removeTree(child);
        if (!file.delete()) throw new IOException("Could not remove production probe scratch");
    }
}
