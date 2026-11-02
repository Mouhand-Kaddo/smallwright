package dev.smallwright.worker;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Runs resource-owning Java through the production worker snapshot pipeline. */
final class GeneratedResourceProbe {
    private static final String PACKAGE = "dev.smallwright.generated.counter";
    private static final String SOURCE =
            "package "
                    + PACKAGE
                    + ";"
                    + " public final class MainActivity extends android.app.Activity {"
                    + " int layout = R.layout.activity_main;"
                    + " int button = R.id.counter_button; }";
    private static final String LAYOUT =
            "<Button xmlns:android=\"http://schemas.android.com/apk/res/android\""
                    + " android:id=\"@+id/counter_button\" android:layout_width=\"match_parent\""
                    + " android:layout_height=\"wrap_content\" android:text=\"Counter\"/>";

    private GeneratedResourceProbe() {}

    static String run(Context worker) throws Exception {
        File scratch = new File(worker.getCacheDir(), "generated-resource-probe");
        removeTree(scratch);
        if (!scratch.mkdir()) throw new IOException("Could not create resource probe scratch");
        try {
            File valid = new File(scratch, "valid.zip");
            snapshot(valid, LAYOUT);
            File apk = new File(scratch, "valid.apk");
            String result = build(worker, valid, apk);
            if (!"OK".equals(result)) throw new AssertionError("R-backed build failed: " + result);
            try (ZipFile archive = new ZipFile(apk)) {
                if (archive.getEntry("res/layout/activity_main.xml") == null
                        || archive.getEntry("classes.dex") == null) {
                    throw new AssertionError("R-backed APK lacks layout or DEX");
                }
                byte[] dex = archive.getInputStream(archive.getEntry("classes.dex")).readAllBytes();
                ProductionSnapshotProbe.requireAppOnlyClassDefs(dex, PACKAGE);
                if (!new String(dex, StandardCharsets.ISO_8859_1).contains("R$layout")
                        || !new String(dex, StandardCharsets.ISO_8859_1).contains("R$id")) {
                    throw new AssertionError("DEX lacks project R classes");
                }
            }

            File missing = new File(scratch, "missing.zip");
            snapshot(
                    missing,
                    "<Button xmlns:android=\"http://schemas.android.com/apk/res/android\""
                            + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\"/>");
            File missingApk = new File(scratch, "missing.apk");
            String missingResult = build(worker, missing, missingApk);
            if (!BuildService.COMPILE_ERROR.equals(missingResult) || missingApk.length() != 0) {
                throw new AssertionError("Missing R.id built an APK: " + missingResult);
            }

            File malformed = new File(scratch, "malformed.zip");
            snapshot(
                    malformed,
                    "<Button xmlns:android=\"http://schemas.android.com/apk/res/android\"");
            File malformedApk = new File(scratch, "malformed.apk");
            String malformedResult = build(worker, malformed, malformedApk);
            if (!malformedResult.startsWith("ERROR: Packaged AAPT2 failed:")
                    || malformedApk.length() != 0) {
                throw new AssertionError("Malformed resource built an APK: " + malformedResult);
            }
            return "Generated R: layout/id build and DEX, missing id and malformed XML rejected without APK\n";
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

    private static void snapshot(File destination, String layout) throws IOException {
        try (ZipOutputStream zip =
                new ZipOutputStream(Files.newOutputStream(destination.toPath()))) {
            entry(
                    zip,
                    "AndroidManifest.xml",
                    "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                            + " package=\""
                            + PACKAGE
                            + "\"><uses-sdk android:minSdkVersion=\"31\""
                            + " android:targetSdkVersion=\"36\"/><application android:label=\"Counter\">"
                            + "<activity android:name=\".MainActivity\" android:exported=\"true\">"
                            + "<intent-filter><action android:name=\"android.intent.action.MAIN\"/>"
                            + "<category android:name=\"android.intent.category.LAUNCHER\"/>"
                            + "</intent-filter></activity></application></manifest>");
            entry(zip, "java/" + PACKAGE.replace('.', '/') + "/MainActivity.java", SOURCE);
            entry(zip, "res/layout/activity_main.xml", layout);
        }
    }

    private static void entry(ZipOutputStream zip, String name, String body) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(body.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void removeTree(File file) throws IOException {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) removeTree(child);
        if (!file.delete()) throw new IOException("Could not remove resource probe scratch");
    }
}
