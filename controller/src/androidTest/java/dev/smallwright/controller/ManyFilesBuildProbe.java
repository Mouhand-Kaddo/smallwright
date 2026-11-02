package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.IBinder;
import android.os.SystemClock;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.project.ProjectStore;
import dev.smallwright.protocol.IBuildService;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Disposable many-file project built by the real worker; no model request is made. */
final class ManyFilesBuildProbe {
    private static final int MEBIBYTE = 1024 * 1024;
    private static final int PROJECT_BYTES = 16 * MEBIBYTE;
    private static final int PROJECT_FILES = 500;
    private static final int BULK_CLASS_BYTES = 7000;
    private static final int NOTE_CLASSES = 30;
    private static final String XMLNS =
            "xmlns:android=\"http://schemas.android.com/apk/res/android\"";

    private ManyFilesBuildProbe() {}

    static String run(Context target, String shape) throws Exception {
        if (!shape.equals("app") && !shape.equals("limits"))
            throw new IllegalArgumentException("Unknown shape " + shape);
        File root =
                Files.createTempDirectory(target.getFilesDir().toPath(), "many-files-build-")
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

                    @Override
                    public File getNoBackupFilesDir() {
                        return root;
                    }
                };
        ProjectIdentity project =
                new ProjectRegistry(new File(root, "projects"), root).create("Many files " + shape);
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        String packageName = project.packageName();
        Map<String, byte[]> files = new TreeMap<>(appFiles(packageName, shape.equals("limits")));
        if (shape.equals("limits")) addBulk(files, packageName);
        long totalBytes = 0;
        for (byte[] content : files.values()) totalBytes += content.length;
        int revision =
                new ProjectStore(new File(project.directory(), "revision-store"), packageName)
                        .applyPatch(0, files);

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
            int resourceEntries = requireApkContents(apk, files);
            return "ManyFiles shape="
                    + shape
                    + " package="
                    + packageName
                    + " files="
                    + files.size()
                    + " projectBytes="
                    + totalBytes
                    + " buildMs="
                    + buildMillis
                    + " apkBytes="
                    + apk.length()
                    + " apkResourceEntries="
                    + resourceEntries
                    + " apkSha256="
                    + artifact.sha256()
                    + " apkPath="
                    + apk.getAbsolutePath()
                    + " expectedScreen="
                    + expectedScreen().replace('\n', '|')
                    + "\n";
        } finally {
            target.unbindService(connection);
        }
    }

    private static int requireApkContents(File apk, Map<String, byte[]> files) throws Exception {
        int resources = 0;
        try (ZipFile zip = new ZipFile(apk)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                if (!file.getKey().startsWith("assets/")) continue;
                ZipEntry entry = zip.getEntry(file.getKey());
                if (entry == null) throw new AssertionError("APK lacks " + file.getKey());
                if (!Arrays.equals(zip.getInputStream(entry).readAllBytes(), file.getValue()))
                    throw new AssertionError("APK asset differs: " + file.getKey());
            }
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (entries.nextElement().getName().startsWith("res/")) resources++;
            }
            if (zip.getEntry("classes.dex") == null || zip.getEntry("resources.arsc") == null)
                throw new AssertionError("APK lacks code or resource table");
        }
        long expectedResources = files.keySet().stream().filter(p -> p.startsWith("res/")).count();
        if (resources < expectedResources - 20)
            throw new AssertionError("APK holds too few res entries: " + resources);
        return resources;
    }

    private static String expectedScreen() {
        int sum = 0;
        for (int i = 0; i < NOTE_CLASSES; i++) sum += i * 3;
        return "Hello, Many files\nnotes=" + NOTE_CLASSES + " sum=" + sum + "\njoined=a-b-c";
    }

    private static Map<String, byte[]> appFiles(String packageName, boolean paddedMain)
            throws Exception {
        Map<String, byte[]> files = new TreeMap<>();
        String java = "java/" + packageName.replace('.', '/') + "/";
        put(files, java + "MainActivity.java", mainActivity(packageName, paddedMain));
        put(
                files,
                java + "ui/Greeter.java",
                "package "
                        + packageName
                        + ".ui;\n"
                        + "public final class Greeter {\n"
                        + "  public String greeting(String title) { return \"Hello, \" + title; }\n}\n");
        put(
                files,
                java + "util/deep/er/Joiner.java",
                "package "
                        + packageName
                        + ".util.deep.er;\n"
                        + "public final class Joiner {\n"
                        + "  public static String join(String a, String b, String c) {"
                        + " return a + \"-\" + b + \"-\" + c; }\n}\n");
        StringBuilder repository =
                new StringBuilder(
                        "package "
                                + packageName
                                + ".data.model;\n"
                                + "public final class NoteRepo {\n  public static int count() { return "
                                + NOTE_CLASSES
                                + "; }\n  public static int sum() {\n    int total = 0;\n");
        for (int i = 0; i < NOTE_CLASSES; i++) {
            put(
                    files,
                    java + "data/model/Note" + i + ".java",
                    "package "
                            + packageName
                            + ".data.model;\npublic final class Note"
                            + i
                            + " {\n  public static int value() { return "
                            + (i * 3)
                            + "; }\n}\n");
            repository.append("    total += Note").append(i).append(".value();\n");
        }
        repository.append("    return total;\n  }\n}\n");
        put(files, java + "data/model/NoteRepo.java", repository.toString());

        put(
                files,
                "res/layout/empty.xml",
                "<View "
                        + XMLNS
                        + " android:layout_width=\"1dp\""
                        + " android:layout_height=\"1dp\"/>\n");
        put(
                files,
                "res/layout/activity_main.xml",
                "<LinearLayout "
                        + XMLNS
                        + " android:layout_width=\"match_parent\" android:layout_height=\"match_parent\""
                        + " android:orientation=\"vertical\" android:background=\"@drawable/badge_shape\""
                        + " android:padding=\"@dimen/gap\">"
                        + "<include layout=\"@layout/header\"/>"
                        + "<TextView android:id=\"@+id/summary\" android:layout_width=\"match_parent\""
                        + " android:layout_height=\"wrap_content\" android:textColor=\"@color/ink\"/>"
                        + "<ImageView android:layout_width=\"48dp\" android:layout_height=\"48dp\""
                        + " android:src=\"@drawable/logo\"/>"
                        + "<Button android:id=\"@+id/action\" android:layout_width=\"wrap_content\""
                        + " android:layout_height=\"wrap_content\" android:text=\"@string/tap\""
                        + " android:background=\"@drawable/button_state\"/>"
                        + "<TextView android:id=\"@+id/counter\" android:layout_width=\"wrap_content\""
                        + " android:layout_height=\"wrap_content\" android:text=\"@string/taps_zero\"/>"
                        + "</LinearLayout>\n");
        put(
                files,
                "res/layout-land/activity_main.xml",
                "<LinearLayout "
                        + XMLNS
                        + " android:layout_width=\"match_parent\" android:layout_height=\"match_parent\""
                        + " android:orientation=\"vertical\" android:background=\"@drawable/badge_shape\">"
                        + "<include layout=\"@layout/header\"/>"
                        + "<TextView android:id=\"@+id/summary\" android:layout_width=\"match_parent\""
                        + " android:layout_height=\"wrap_content\"/>"
                        + "<Button android:id=\"@+id/action\" android:layout_width=\"wrap_content\""
                        + " android:layout_height=\"wrap_content\" android:text=\"@string/tap\"/>"
                        + "<TextView android:id=\"@+id/counter\" android:layout_width=\"wrap_content\""
                        + " android:layout_height=\"wrap_content\"/>"
                        + "</LinearLayout>\n");
        put(
                files,
                "res/layout/header.xml",
                "<TextView "
                        + XMLNS
                        + " android:layout_width=\"match_parent\" android:layout_height=\"wrap_content\""
                        + " android:text=\"@string/app_title\" android:textSize=\"@dimen/title_size\"/>\n");
        put(
                files,
                "res/values/strings.xml",
                "<resources>"
                        + "<string name=\"title\">Many files</string>"
                        + "<string name=\"app_title\">Many files app</string>"
                        + "<string name=\"tap\">Tap</string><string name=\"taps_zero\">taps=0</string>"
                        + "<plurals name=\"files\"><item quantity=\"one\">%d file</item>"
                        + "<item quantity=\"other\">%d files</item></plurals>"
                        + "<string-array name=\"letters\"><item>a</item><item>b</item></string-array>"
                        + "<integer-array name=\"numbers\"><item>1</item><item>2</item></integer-array>"
                        + "</resources>\n");
        put(
                files,
                "res/values/colors.xml",
                "<resources><color name=\"ink\">#ff112233</color>"
                        + "<color name=\"paper\">#ffeeeeee</color></resources>\n");
        put(
                files,
                "res/values/dimens.xml",
                "<resources><dimen name=\"gap\">16dp</dimen>"
                        + "<dimen name=\"title_size\">22sp</dimen></resources>\n");
        put(
                files,
                "res/values/styles.xml",
                "<resources><style name=\"Heading\""
                        + " parent=\"@android:style/TextAppearance\"><item name=\"android:textSize\">"
                        + "@dimen/title_size</item></style></resources>\n");
        put(
                files,
                "res/values/attrs.xml",
                "<resources><declare-styleable name=\"Badge\">"
                        + "<attr name=\"badgeColor\" format=\"color\"/></declare-styleable></resources>\n");
        put(
                files,
                "res/values-night/colors.xml",
                "<resources><color name=\"ink\">#ffddeeff"
                        + "</color><color name=\"paper\">#ff111111</color></resources>\n");
        put(
                files,
                "res/values-b+sr+Latn/strings.xml",
                "<resources><string name=\"tap\">Dodirni" + "</string></resources>\n");
        put(
                files,
                "res/drawable/badge_shape.xml",
                "<shape "
                        + XMLNS
                        + "><solid android:color="
                        + "\"@color/paper\"/><corners android:radius=\"8dp\"/></shape>\n");
        put(
                files,
                "res/drawable/button_state.xml",
                "<selector "
                        + XMLNS
                        + "><item android:state_"
                        + "pressed=\"true\" android:drawable=\"@color/ink\"/><item android:drawable="
                        + "\"@color/paper\"/></selector>\n");
        put(
                files,
                "res/drawable/ic_vector.xml",
                "<vector "
                        + XMLNS
                        + " android:width=\"24dp\""
                        + " android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight="
                        + "\"24\"><path android:fillColor=\"#ff112233\" android:pathData=\"M2,2h20v20h-20z\""
                        + "/></vector>\n");
        put(files, "res/drawable/logo.png", bitmap(Bitmap.CompressFormat.PNG, 16, false));
        put(files, "res/drawable-xhdpi/logo.png", bitmap(Bitmap.CompressFormat.PNG, 32, false));
        put(files, "res/drawable/frame.9.png", bitmap(Bitmap.CompressFormat.PNG, 7, true));
        put(files, "res/drawable/photo.jpg", bitmap(Bitmap.CompressFormat.JPEG, 16, false));
        put(
                files,
                "res/drawable/banner.webp",
                bitmap(Bitmap.CompressFormat.WEBP_LOSSLESS, 16, false));
        put(
                files,
                "res/drawable/spinner.gif",
                new byte[] {
                    'G',
                    'I',
                    'F',
                    '8',
                    '9',
                    'a',
                    1,
                    0,
                    1,
                    0,
                    (byte) 0x80,
                    0,
                    0,
                    0,
                    0,
                    0,
                    (byte) 0xff,
                    (byte) 0xff,
                    (byte) 0xff,
                    0x21,
                    (byte) 0xf9,
                    4,
                    1,
                    0,
                    0,
                    0,
                    0,
                    0x2c,
                    0,
                    0,
                    0,
                    0,
                    1,
                    0,
                    1,
                    0,
                    0,
                    2,
                    2,
                    0x44,
                    1,
                    0,
                    0x3b
                });
        put(
                files,
                "res/mipmap-xxhdpi/ic_launcher.png",
                bitmap(Bitmap.CompressFormat.PNG, 48, false));
        put(
                files,
                "res/mipmap-anydpi-v26/ic_launcher_round.xml",
                "<adaptive-icon "
                        + XMLNS
                        + "><background android:drawable=\"@drawable/badge_shape\"/><foreground"
                        + " android:drawable=\"@drawable/ic_vector\"/></adaptive-icon>\n");
        put(
                files,
                "res/menu/main_menu.xml",
                "<menu "
                        + XMLNS
                        + "><item android:id=\"@+id/open\""
                        + " android:title=\"@string/title\"/></menu>\n");
        put(
                files,
                "res/anim/fade_in.xml",
                "<alpha "
                        + XMLNS
                        + " android:fromAlpha=\"0\""
                        + " android:toAlpha=\"1\" android:duration=\"200\"/>\n");
        put(
                files,
                "res/animator/pulse.xml",
                "<objectAnimator "
                        + XMLNS
                        + " android:propertyName=\"alpha\" android:valueFrom=\"0\" android:valueTo=\"1\""
                        + " android:duration=\"300\"/>\n");
        put(
                files,
                "res/color/tint.xml",
                "<selector " + XMLNS + "><item android:color=" + "\"@color/ink\"/></selector>\n");
        put(files, "res/interpolator/ease.xml", "<accelerateInterpolator " + XMLNS + "/>\n");
        put(files, "res/transition/slide.xml", "<fade " + XMLNS + " android:duration=\"200\"/>\n");
        put(files, "res/navigation/graph.xml", "<navigation " + XMLNS + "/>\n");
        put(files, "res/xml/preferences.xml", "<PreferenceScreen " + XMLNS + "/>\n");
        put(
                files,
                "res/font/family.xml",
                "<font-family "
                        + XMLNS
                        + "><font android:font="
                        + "\"@font/body\" android:fontStyle=\"normal\" android:fontWeight=\"400\"/>"
                        + "</font-family>\n");
        put(files, "res/font/body.ttf", smallestSystemFont());
        put(files, "res/raw/hello.txt", "hello raw\n");
        put(files, "res/raw/data.json", "{\"k\":1}\n");
        put(files, "res/raw/clip.bin", random(4096, 7));
        put(files, "assets/readme.txt", "plain asset\n");
        put(files, "assets/config/defaults.json", "{\"mode\":\"fast\"}\n");
        put(files, "assets/data/deep/er/blob.bin", random(50_000, 11));
        return files;
    }

    private static String mainActivity(String packageName, boolean padded) {
        StringBuilder source =
                new StringBuilder(
                        "package "
                                + packageName
                                + ";\n\n"
                                + "import "
                                + packageName
                                + ".data.model.NoteRepo;\n"
                                + "import "
                                + packageName
                                + ".ui.Greeter;\n"
                                + "import "
                                + packageName
                                + ".util.deep.er.Joiner;\n\n"
                                + "public final class MainActivity extends android.app.Activity {\n"
                                + "  private int taps;\n\n"
                                + "  @Override public void onCreate(android.os.Bundle state) {\n"
                                + "    super.onCreate(state);\n"
                                + "    setContentView(R.layout.activity_main);\n"
                                + "    android.widget.TextView summary = findViewById(R.id.summary);\n"
                                + "    summary.setText(new Greeter().greeting(getString(R.string.title))"
                                + " + \"\\nnotes=\" + NoteRepo.count() + \" sum=\" + NoteRepo.sum()"
                                + " + \"\\njoined=\" + Joiner.join(\"a\", \"b\", \"c\"));\n"
                                + "    android.widget.Button action = findViewById(R.id.action);\n"
                                + "    android.widget.TextView counter = findViewById(R.id.counter);\n"
                                + "    action.setOnClickListener(v -> counter.setText(\"taps=\" + (++taps)));\n"
                                + "  }\n}\n");
        if (padded) {
            while (source.length() < MEBIBYTE - 81) {
                source.append(
                        "// padding line to make this launcher source exactly one mebibyte\n");
            }
            while (source.length() < MEBIBYTE) source.append('\n');
        }
        return source.toString();
    }

    private static void addBulk(Map<String, byte[]> files, String packageName) {
        long fixed = 0;
        for (byte[] content : files.values()) fixed += content.length;
        String java = "java/" + packageName.replace('.', '/') + "/bulk/";
        for (int bulk = 0; bulk <= PROJECT_FILES; bulk++) {
            long remaining = (long) PROJECT_BYTES - fixed - (long) bulk * BULK_CLASS_BYTES;
            if (remaining < 0) throw new AssertionError("Bulk classes exceed the project budget");
            long blobs = remaining / MEBIBYTE;
            long filler = remaining % MEBIBYTE;
            if (files.size() + bulk + blobs + (filler > 0 ? 1 : 0) != PROJECT_FILES) continue;
            for (int i = 0; i < bulk; i++) {
                put(files, java + "Bulk" + i + ".java", bulkClass(packageName, i));
            }
            for (int i = 0; i < blobs; i++) {
                put(files, "assets/blobs/blob" + i + ".bin", random(MEBIBYTE, 100 + i));
            }
            if (filler > 0) put(files, "assets/blobs/filler.bin", random((int) filler, 999));
            return;
        }
        throw new AssertionError("No bulk size reaches exactly the file and byte limits");
    }

    private static String bulkClass(String packageName, int index) {
        StringBuilder source =
                new StringBuilder(
                        "package "
                                + packageName
                                + ".bulk;\n"
                                + "public final class Bulk"
                                + index
                                + " {\n  private Bulk"
                                + index
                                + "() {}\n");
        for (int method = 0; method < 40; method++) {
            source.append("  public static int f")
                    .append(method)
                    .append("(int x) { return x * ")
                    .append(method + 1)
                    .append(" + ")
                    .append(index)
                    .append("; }\n");
        }
        String closing = "}\n";
        while (source.length() + closing.length() < BULK_CLASS_BYTES) source.append(' ');
        return source.append(closing).toString();
    }

    private static byte[] smallestSystemFont() throws Exception {
        File[] fonts = new File("/system/fonts").listFiles((dir, name) -> name.endsWith(".ttf"));
        File smallest = null;
        if (fonts != null) {
            for (File font : fonts) {
                if (font.length() <= MEBIBYTE
                        && (smallest == null || font.length() < smallest.length())) smallest = font;
            }
        }
        if (smallest == null) throw new AssertionError("No system font under one mebibyte");
        return Files.readAllBytes(smallest.toPath());
    }

    private static byte[] bitmap(Bitmap.CompressFormat format, int side, boolean ninePatch)
            throws Exception {
        Bitmap image = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.BLUE);
        if (ninePatch) {
            image.eraseColor(Color.TRANSPARENT);
            for (int i = 1; i < side - 1; i++) {
                image.setPixel(i, 0, Color.BLACK);
                image.setPixel(0, i, Color.BLACK);
                image.setPixel(i, side - 1, Color.TRANSPARENT);
            }
            for (int x = 1; x < side - 1; x++) {
                for (int y = 1; y < side - 1; y++) image.setPixel(x, y, Color.RED);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        image.compress(format, 90, out);
        return out.toByteArray();
    }

    private static byte[] random(int length, long seed) {
        byte[] bytes = new byte[length];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    private static void put(Map<String, byte[]> files, String path, String text) {
        put(files, path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void put(Map<String, byte[]> files, String path, byte[] content) {
        if (files.put(path, content) != null) throw new AssertionError("Duplicate " + path);
    }
}
