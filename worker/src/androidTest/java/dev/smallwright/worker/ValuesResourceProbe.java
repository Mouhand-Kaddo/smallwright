package dev.smallwright.worker;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;

/** Disposable actual guarded AAPT2 differential and normal-pipeline values regression. */
final class ValuesResourceProbe {
    private static final String LAYOUT_SHA =
            "23ae78adc6d88761f1c4cb40f648916c6bb68a783c5550851c845de1908fa9c6";
    private static final String VALUES_SHA =
            "784ffe824d172ffaa412a9d31e0234a0a3db13b1fc756a641b4525e22c76b290";
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String run(
            Instrumentation in, String phase, String id, boolean approved, String workerHash)
            throws Exception {
        stage = "GUARD";
        Context worker = in.getTargetContext();
        if (!approved
                || id == null
                || !id.matches("[0-9a-f]{32}")
                || !List.of("differential", "regression").contains(phase)
                || !"dev.smallwright.worker".equals(worker.getPackageName())
                || Process.myUid() != worker.getApplicationInfo().uid
                || workerHash == null
                || !workerHash.matches("[0-9a-f]{64}")
                || !workerHash.equals(
                        sha(
                                Files.readAllBytes(
                                        new File(worker.getApplicationInfo().sourceDir).toPath()))))
            throw new AssertionError("Approved exact normal worker and independent case required");
        byte[] layout = asset(in, "empty.xml", LAYOUT_SHA);
        byte[] values = asset(in, "dialog_labels.xml", VALUES_SHA);
        if (!new File(worker.getFilesDir(), "values-resource-issue29-" + id + "-" + phase).mkdir())
            throw new AssertionError("Already reserved; never reset a consumed case");
        String pkg = "dev.smallwright.generated.app" + id;
        File scratch =
                Files.createTempDirectory(worker.getCacheDir().toPath(), "values-resource-")
                        .toFile();
        JSONObject record =
                new JSONObject()
                        .put("case", id)
                        .put("phase", phase)
                        .put("workerSha256", workerHash)
                        .put("layoutSha256", LAYOUT_SHA)
                        .put("valuesSha256", VALUES_SHA)
                        .put("modelRequestMade", false)
                        .put("generatedSourceExported", false)
                        .put("generatedInstallationAttempted", false)
                        .put("signedArtifactPublished", false);
        try {
            if ("differential".equals(phase))
                differential(in, worker, scratch, pkg, layout, values, record);
            else regression(in, worker, scratch, pkg, layout, values, record);
            return "Values resources " + record.put("result", "passed") + "\n";
        } finally {
            removeOwnTree(scratch);
        }
    }

    private static void differential(
            Instrumentation in,
            Context worker,
            File scratch,
            String pkg,
            byte[] layout,
            byte[] values,
            JSONObject record)
            throws Exception {
        File resource = new File(scratch, "res");
        Files.createDirectories(new File(resource, "layout").toPath());
        Files.createDirectories(new File(resource, "values").toPath());
        Files.write(new File(resource, "layout/empty.xml").toPath(), layout);
        Files.write(new File(resource, "values/dialog_labels.xml").toPath(), values);
        File manifest = new File(scratch, "AndroidManifest.xml");
        Files.write(manifest.toPath(), manifest(pkg).getBytes(StandardCharsets.UTF_8));
        File compiled = new File(scratch, "compiled.zip");
        String executable = worker.getApplicationInfo().nativeLibraryDir + "/libaapt2_exec.so";
        stage = "DIFFERENTIAL_COMPILE";
        JSONObject compile =
                guarded(
                        executable,
                        "compile",
                        "--dir",
                        resource.getAbsolutePath(),
                        "-o",
                        compiled.getAbsolutePath());
        record.put("compile", compile);
        marker(in, record);
        if (!"OK".equals(compile.getString("category")) || !compiled.isFile())
            throw new AssertionError("Independent actual resource compile failed");
        String compiledHash = sha(Files.readAllBytes(compiled.toPath()));
        record.put("compiledSha256", compiledHash);
        stage = "DIFFERENTIAL_OVERLAY_LINK";
        File overlayR = new File(scratch, "overlay-r");
        Files.createDirectory(overlayR.toPath());
        JSONObject overlay =
                guarded(
                        executable,
                        "link",
                        "-I",
                        "/system/framework/framework-res.apk",
                        "--manifest",
                        manifest.getAbsolutePath(),
                        "-R",
                        compiled.getAbsolutePath(),
                        "--java",
                        overlayR.getAbsolutePath(),
                        "-o",
                        new File(scratch, "overlay.apk").getAbsolutePath());
        record.put("overlayLink", overlay);
        marker(in, record);
        if (!"EXIT_NONZERO".equals(overlay.getString("category"))
                || !overlay.getBoolean("newResourceOverlayRejected"))
            throw new AssertionError("Current overlay rejection not independently established");
        stage = "DIFFERENTIAL_BASE_LINK";
        File baseR = new File(scratch, "base-r");
        Files.createDirectory(baseR.toPath());
        File linked = new File(scratch, "base.apk");
        JSONObject base =
                guarded(
                        executable,
                        "link",
                        "-I",
                        "/system/framework/framework-res.apk",
                        "--manifest",
                        manifest.getAbsolutePath(),
                        compiled.getAbsolutePath(),
                        "--java",
                        baseR.getAbsolutePath(),
                        "-o",
                        linked.getAbsolutePath());
        record.put("baseLink", base);
        marker(in, record);
        if (!"OK".equals(base.getString("category"))
                || !compiledHash.equals(sha(Files.readAllBytes(compiled.toPath()))))
            throw new AssertionError("Identical base-input link not successful");
        File r = new File(baseR, pkg.replace('.', '/') + "/R.java");
        if (!r.isFile() || r.length() > 256 * 1024)
            throw new AssertionError("Bounded R output absent");
        byte[] rBytes = Files.readAllBytes(r.toPath());
        Matcher stringClass =
                Pattern.compile(
                                "public\\s+static\\s+final\\s+class\\s+string\\s*\\{([^}]*)\\}",
                                Pattern.DOTALL)
                        .matcher(new String(rBytes, StandardCharsets.UTF_8));
        if (!stringClass.find()) throw new AssertionError("Application R.string absent");
        Matcher fields =
                Pattern.compile(
                                "public\\s+static\\s+final\\s+int\\s+(dialog_confirm|dialog_cancel)\\s*=")
                        .matcher(stringClass.group(1));
        Set<String> symbols = new HashSet<>();
        while (fields.find()) symbols.add(fields.group(1));
        if (!symbols.equals(Set.of("dialog_confirm", "dialog_cancel")))
            throw new AssertionError("Expected two application string fields absent");
        try (ZipFile apk = new ZipFile(linked)) {
            if (apk.getEntry("resources.arsc") == null
                    || apk.getEntry("AndroidManifest.xml") == null)
                throw new AssertionError("Linked resource output incomplete");
        }
        record.put("rSha256", sha(rBytes))
                .put("stringSymbolCount", symbols.size())
                .put("resourceOutputBytes", linked.length());
    }

    private static void regression(
            Instrumentation in,
            Context worker,
            File scratch,
            String pkg,
            byte[] layout,
            byte[] values,
            JSONObject record)
            throws Exception {
        stage = "NORMAL_PIPELINE_SNAPSHOT";
        File cache = new File(scratch, "pipeline-cache");
        Files.createDirectory(cache.toPath());
        Context disposable =
                new ContextWrapper(worker) {
                    @Override
                    public File getCacheDir() {
                        return cache;
                    }
                };
        File snapshot = new File(scratch, "values.zip");
        String java =
                "package "
                        + pkg
                        + "; public final class MainActivity extends android.app.Activity {"
                        + " int confirm = R.string.dialog_confirm; int cancel = R.string.dialog_cancel; }";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(snapshot.toPath()))) {
            entry(zip, "AndroidManifest.xml", manifest(pkg).getBytes(StandardCharsets.UTF_8));
            entry(
                    zip,
                    "java/" + pkg.replace('.', '/') + "/MainActivity.java",
                    java.getBytes(StandardCharsets.UTF_8));
            entry(zip, "res/layout/empty.xml", layout);
            entry(zip, "res/values/dialog_labels.xml", values);
        }
        File output = new File(scratch, "unsigned.apk");
        stage = "NORMAL_PIPELINE_BUILD";
        String result;
        try (ParcelFileDescriptor input =
                        ParcelFileDescriptor.open(snapshot, ParcelFileDescriptor.MODE_READ_ONLY);
                ParcelFileDescriptor artifact =
                        ParcelFileDescriptor.open(
                                output,
                                ParcelFileDescriptor.MODE_CREATE
                                        | ParcelFileDescriptor.MODE_READ_WRITE)) {
            result = BuildService.buildSnapshot(disposable, input, artifact);
        }
        record.put("actualOutcome", outcome(result))
                .put("expectedOutcome", "OK")
                .put("unsignedOutputBytes", output.length())
                .put("pipelineScratchCleaned", !new File(cache, "active-build").exists());
        marker(in, record);
        if (!"OK".equals(result))
            throw new AssertionError("Actual normal application values build failed");
        stage = "NORMAL_PIPELINE_APP_ONLY_DEX";
        try (ZipFile apk = new ZipFile(output)) {
            if (apk.getEntry("resources.arsc") == null || apk.getEntry("classes.dex") == null)
                throw new AssertionError("Actual normal output incomplete");
            byte[] dex;
            try (java.io.InputStream input = apk.getInputStream(apk.getEntry("classes.dex"))) {
                dex = input.readAllBytes();
            }
            ProductionSnapshotProbe.requireAppOnlyClassDefs(dex, pkg);
            if (!new String(dex, StandardCharsets.ISO_8859_1).contains("R$string"))
                throw new AssertionError("Application R.string class absent");
        }
        if (new File(cache, "active-build").exists())
            throw new AssertionError("Normal scratch retained");
        record.put("unsignedSha256", sha(Files.readAllBytes(output.toPath())))
                .put("appOnlyDex", true)
                .put("stringFieldReferences", 2);
    }

    private static JSONObject guarded(String executable, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(List.of(args));
        java.lang.Process child = WorkerProcesses.startAapt2(new ProcessBuilder(command));
        JSONObject result = new JSONObject().put("newResourceOverlayRejected", false);
        try {
            try {
                BuildService.runBounded(child, 30_000, 16_384);
                result.put("category", "OK");
            } catch (IllegalStateException failure) {
                String message = failure.getMessage();
                if (message != null && message.startsWith("Packaged AAPT2 failed: ")) {
                    result.put("category", "EXIT_NONZERO")
                            .put(
                                    "newResourceOverlayRejected",
                                    message.contains(" does not override an existing resource"));
                } else if ("Packaged AAPT2 timed out".equals(message))
                    result.put("category", "TIMEOUT");
                else result.put("category", "CHILD_IO_OR_INTERRUPTED");
            }
        } finally {
            WorkerProcesses.finished(child);
        }
        return result.put("exitCode", child.exitValue());
    }

    private static String outcome(String result) {
        if ("OK".equals(result)) return "OK";
        if ("COMPILE_ERROR".equals(result)) return "COMPILE_ERROR";
        if ("SNAPSHOT_TIMEOUT".equals(result)) return "SNAPSHOT_TIMEOUT";
        if ("ERROR: Worker busy".equals(result)) return "WORKER_BUSY";
        if (result != null && result.startsWith("ERROR: Packaged AAPT2 failed: "))
            return "AAPT2_FAILURE";
        return "OTHER_WORKER_ERROR";
    }

    private static byte[] asset(Instrumentation in, String name, String hash) throws Exception {
        byte[] bytes;
        try (java.io.InputStream input =
                in.getContext().getAssets().open("values-resource/" + name)) {
            bytes = input.readAllBytes();
        }
        if (!hash.equals(sha(bytes))) throw new AssertionError("Authored resource bytes changed");
        return bytes;
    }

    private static String manifest(String pkg) {
        return "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\""
                + pkg
                + "\">"
                + "<uses-sdk android:minSdkVersion=\"31\" android:targetSdkVersion=\"36\"/>"
                + "<application android:allowBackup=\"false\" android:label=\"Values fixture\">"
                + "<activity android:name=\".MainActivity\" android:exported=\"true\">"
                + "<intent-filter><action android:name=\"android.intent.action.MAIN\"/>"
                + "<category android:name=\"android.intent.category.LAUNCHER\"/>"
                + "</intent-filter></activity></application></manifest>";
    }

    private static void entry(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String sha(byte[] bytes) throws Exception {
        StringBuilder hash = new StringBuilder();
        for (byte item : MessageDigest.getInstance("SHA-256").digest(bytes))
            hash.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
        return hash.toString();
    }

    private static void marker(Instrumentation in, JSONObject record) {
        Bundle status = new Bundle();
        status.putString("stream", "VALUES_RESOURCE_OBSERVATION " + record + "\n");
        in.sendStatus(0, status);
    }

    private static void removeOwnTree(File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath()))
            throw new SecurityException("Unexpected fixture symlink");
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) removeOwnTree(child);
        Files.delete(file.toPath());
    }
}
