package dev.smallwright.controller;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.util.Base64;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.smallwright.agent.ImageClient;
import dev.smallwright.agent.ModelChoice;
import dev.smallwright.agent.ModelClient;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepLog;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.protocol.IBuildService;
import dev.smallwright.security.CredentialStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A run on a disposable project in an isolated registry, built with the real worker. In case {@code
 * app}, scripted OpenRouter replies over a fake connection call make_app_icon and make_image, whose
 * image replies are a fixed PNG file, show the picture in a layout and rename the app; nothing
 * leaves the phone. In cases {@code named} and {@code unnamed}, DeepSeek V4.1 Flash runs with the
 * saved key and without app icons, so no image request can be made, and the builder names the app
 * from the request or picks a name.
 */
final class AppIconBuildProbe {
    static final String LABEL = "Leaf Notes";
    static final String PICTURE = "leaf_banner";
    static final String NAMED_REQUEST =
            "Make a simple app that shows a random motivational quote each time I tap a button."
                    + " Call it Daily Spark.";
    static final String UNNAMED_REQUEST =
            "Make a simple app where I tap a button to roll a six-sided die and see the number.";
    private static final byte[] KEY = "probe-key".getBytes(StandardCharsets.US_ASCII);

    private AppIconBuildProbe() {}

    static String run(Instrumentation in, String kind) throws Exception {
        if (kind.equals("decode")) return decodeChecks();
        if (!Arrays.asList("app", "named", "unnamed").contains(kind))
            throw new IllegalArgumentException("Unknown case " + kind);
        Context target = in.getTargetContext();
        File root =
                Files.createTempDirectory(target.getFilesDir().toPath(), "app-icon-build-")
                        .toFile();
        try {
            return runIn(in, target, root, kind);
        } finally {
            deleteTree(root);
        }
    }

    private static String runIn(Instrumentation in, Context target, File root, String kind)
            throws Exception {
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
                new ProjectRegistry(new File(root, "projects"), root).create("Icon probe");
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        Deque<String> replies = new ArrayDeque<>();
        byte[] key = null;
        StepRun.Model model;
        String request;
        if (kind.equals("app")) {
            model = scripted(in, replies);
            request = "Call it " + LABEL + " and make it an icon with a leaf";
        } else {
            key = CredentialStore.openRouter(target.getApplicationContext()).load();
            if (key == null) throw new AssertionError("No saved model key");
            model = deepSeek(key);
            request = kind.equals("named") ? NAMED_REQUEST : UNNAMED_REQUEST;
        }
        RunSteps shown = new RunSteps("chat", "request", "Thinking", snapshot -> {});
        StepRun run =
                new StepRun(
                        source,
                        new StepRun.Control(),
                        new LibraryTools(LibraryTools.repositories(isolated)),
                        RunLimits.OFF,
                        RunSkills.NONE,
                        SkillAdding.NONE,
                        kind.equals("app")
                                ? new AppIcons(LauncherIcon::square, LauncherIcon::picture)
                                : AppIcons.NONE);
        ContextPreviewGate gate = new ContextPreviewGate(source);
        String approved =
                gate.approve(gate.preview(request, source.revision()), request, source.revision());
        java.util.concurrent.atomic.AtomicInteger versions =
                new java.util.concurrent.atomic.AtomicInteger();
        StepRun.Outcome outcome;
        try {
            outcome =
                    withWorker(
                            target,
                            worker ->
                                    run.run(
                                            approved,
                                            source.revision(),
                                            () -> true,
                                            model,
                                            (revision, buildId) -> {
                                                VerifiedArtifact built =
                                                        new LocalBuildCoordinator(
                                                                        isolated, source, project)
                                                                .buildCommittedRevision(
                                                                        revision,
                                                                        versions.incrementAndGet(),
                                                                        worker);
                                                built.verifyUnchanged();
                                                return built;
                                            },
                                            (next, revision, buildId, tokens) -> {},
                                            shown));
        } finally {
            if (key != null) Arrays.fill(key, (byte) 0);
        }
        StringBuilder steps = new StringBuilder();
        for (Step step : StepLog.decode(shown.saved()))
            steps.append(step.getKind()).append(' ').append(step.getText()).append('|');
        String head =
                "AppIconBuild case="
                        + kind
                        + " package="
                        + project.packageName()
                        + " requests="
                        + outcome.requests
                        + " costUsd="
                        + outcome.costUsd.toPlainString()
                        + " steps="
                        + steps
                        + " reply="
                        + outcome.reply.replace('\n', ' ');
        if (outcome.artifact == null) {
            if (kind.equals("app")) throw new AssertionError("No app was built: " + head);
            return head + " label=none (no app was built)\n";
        }
        if (kind.equals("app")) {
            if (!replies.isEmpty()) throw new AssertionError("Unused scripted replies");
            if (outcome.requests != 8) throw new AssertionError("Requests " + outcome.requests);
            if (outcome.costUsd.compareTo(new BigDecimal("0.0786")) != 0)
                throw new AssertionError("Cost " + outcome.costUsd);
            if (!steps.toString().contains("DONE Made an app icon|")
                    || !steps.toString().contains("DONE Made a picture|"))
                throw new AssertionError("Steps " + steps);
        }
        File apk = outcome.artifact.file();
        PackageManager packages = target.getPackageManager();
        ApplicationInfo app =
                packages.getPackageArchiveInfo(apk.getAbsolutePath(), 0).applicationInfo;
        app.sourceDir = apk.getAbsolutePath();
        app.publicSourceDir = apk.getAbsolutePath();
        String label = String.valueOf(app.loadLabel(packages));
        Drawable icon = app.loadIcon(packages);
        String entries = entries(apk, "app_icon");
        String pictureEntries = entries(apk, PICTURE);
        if (kind.equals("app")) {
            if (!LABEL.equals(label)) throw new AssertionError("Label " + label);
            if (!(icon instanceof AdaptiveIconDrawable))
                throw new AssertionError("Icon " + icon.getClass().getName());
            if (entries.indexOf("app_icon_image.png") < 0 || entries.indexOf("app_icon.xml") < 0)
                throw new AssertionError("APK icon entries " + entries);
            if (pictureEntries.indexOf(PICTURE + ".webp") < 0)
                throw new AssertionError("APK picture entries " + pictureEntries);
        }
        File kept = new File(target.getFilesDir(), "app-icon-" + project.packageName() + ".apk");
        Files.copy(apk.toPath(), kept.toPath());
        return head
                + " label="
                + label
                + " icon="
                + icon.getClass().getSimpleName()
                + " apkIconEntries="
                + entries
                + " apkPictureEntries="
                + pictureEntries
                + " apkBytes="
                + kept.length()
                + " apkSha256="
                + outcome.artifact.sha256()
                + " apkPath="
                + kept.getAbsolutePath()
                + "\n";
    }

    private static StepRun.Model scripted(Instrumentation in, Deque<String> replies)
            throws Exception {
        byte[] fixture;
        try (InputStream asset = in.getContext().getAssets().open("icon-fixtures/leaf.png")) {
            fixture = asset.readAllBytes();
        }
        replies.add(calls("make_app_icon", "{\"description\":\"A green leaf on orange\"}"));
        replies.add(image(reencoded(fixture, Bitmap.CompressFormat.JPEG), "image/jpeg"));
        replies.add(
                calls(
                        "make_image",
                        "{\"name\":\""
                                + PICTURE
                                + "\",\"description\":\"A leaf banner\",\"shape\":\"wide\"}"));
        replies.add(image(reencoded(fixture, Bitmap.CompressFormat.WEBP_LOSSY), "image/webp"));
        replies.add(
                calls(
                        "write_file",
                        "{\"path\":\"res/layout/banner.xml\",\"content\":\"<ImageView"
                                + " xmlns:android=\\\"http://schemas.android.com/apk/res/android\\\""
                                + " android:layout_width=\\\"match_parent\\\""
                                + " android:layout_height=\\\"wrap_content\\\""
                                + " android:src=\\\"@drawable/"
                                + PICTURE
                                + "\\\"/>\"}"));
        replies.add(
                calls(
                        "edit_file",
                        "{\"path\":\"AndroidManifest.xml\","
                                + "\"old_text\":\"android:label=\\\"Smallwright App\\\"\","
                                + "\"new_text\":\"android:label=\\\""
                                + LABEL
                                + "\\\"\"}"));
        replies.add(calls("build", "{}"));
        replies.add(answer(LABEL + " now has a leaf icon."));
        ModelClient.ConnectionFactory openRouter = url -> new Scripted(url, replies);
        ModelClient steps = new ModelClient(openRouter, ModelChoice.DEFAULT);
        ImageClient images = new ImageClient(openRouter);
        return new StepRun.Model() {
            @Override
            public ModelClient.StepReply next(JsonArray messages, int promptTokens)
                    throws Exception {
                return steps.completeStep(
                        KEY, messages, promptTokens, new ModelClient.Cancellation());
            }

            @Override
            public ModelClient.StepReply summarize(JsonArray messages, int promptTokens) {
                throw new AssertionError("No summary was expected");
            }

            @Override
            public int contextTokens() {
                return ModelChoice.DEFAULT.contextTokens;
            }

            @Override
            public ImageClient.Image image(String prompt, ImageClient.Shape shape)
                    throws Exception {
                return images.make(KEY, prompt, shape, new ModelClient.Cancellation());
            }
        };
    }

    private static StepRun.Model deepSeek(byte[] key) {
        ModelClient client =
                new ModelClient(ModelChoice.DEFAULT, dev.smallwright.agent.ReasoningLevel.DEFAULT);
        ModelClient.Cancellation cancellation = new ModelClient.Cancellation();
        return new StepRun.Model() {
            @Override
            public ModelClient.StepReply next(JsonArray messages, int promptTokens)
                    throws Exception {
                return client.completeStep(key, messages, promptTokens, cancellation);
            }

            @Override
            public ModelClient.StepReply summarize(JsonArray messages, int promptTokens)
                    throws Exception {
                return client.completeSummary(key, messages, promptTokens, cancellation);
            }

            @Override
            public int contextTokens() {
                return ModelChoice.DEFAULT.contextTokens;
            }
        };
    }

    private static String entries(File apk, String part) throws Exception {
        StringBuilder found = new StringBuilder();
        try (ZipFile zip = new ZipFile(apk)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().contains(part))
                    found.append(entry.getName()).append(':').append(entry.getSize()).append(',');
            }
        }
        return found.toString();
    }

    private static String calls(String name, String arguments) {
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("arguments", arguments);
        JsonObject call = new JsonObject();
        call.addProperty("id", "call_" + name);
        call.addProperty("type", "function");
        call.add("function", function);
        JsonArray toolCalls = new JsonArray();
        toolCalls.add(call);
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.add("content", com.google.gson.JsonNull.INSTANCE);
        message.add("tool_calls", toolCalls);
        return streamed(reply(ModelChoice.DEFAULT.id, "tool_calls", message, "0.0001"));
    }

    private static String answer(String text) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", text);
        return streamed(reply(ModelChoice.DEFAULT.id, "stop", message, "0.0001"));
    }

    /** The reply as OpenRouter streams it: one chunk, the usage chunk, then [DONE]. */
    private static String streamed(String reply) {
        JsonObject root = com.google.gson.JsonParser.parseString(reply).getAsJsonObject();
        JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
        JsonObject delta = choice.remove("message").getAsJsonObject();
        JsonArray calls = delta.getAsJsonArray("tool_calls");
        if (calls != null)
            for (int index = 0; index < calls.size(); index++)
                calls.get(index).getAsJsonObject().addProperty("index", index);
        choice.add("delta", delta);
        com.google.gson.JsonElement usage = root.remove("usage");
        String words = root.toString();
        choice.add("delta", new JsonObject());
        root.add("usage", usage);
        return "data: " + words + "\n\ndata: " + root + "\n\ndata: [DONE]\n\n";
    }

    /** Android's decoder, as the builder uses it, refuses what the image model must not send. */
    private static String decodeChecks() throws Exception {
        StringBuilder seen = new StringBuilder();
        byte[] jpeg = encoded(256, Bitmap.CompressFormat.JPEG);
        byte[] webp = encoded(256, Bitmap.CompressFormat.WEBP_LOSSY);
        if (LauncherIcon.square(jpeg, 64).length == 0
                || LauncherIcon.picture(webp, 128).bytes.length == 0)
            throw new AssertionError("A JPEG or WebP was not made into an icon and a picture");
        seen.append("jpeg,webp=ok");
        byte[] gif = {
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
            '!',
            (byte) 0xf9,
            4,
            1,
            0,
            0,
            0,
            0,
            ',',
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
            'D',
            1,
            0,
            ';'
        };
        seen.append(" gif=").append(refusal(gif, "PNG, JPEG or WebP"));
        seen.append(" unknown=").append(refusal(new byte[256], "PNG, JPEG or WebP"));
        seen.append(" small=").append(refusal(encoded(32, Bitmap.CompressFormat.JPEG), "32×32"));
        byte[] huge = encoded(64, Bitmap.CompressFormat.JPEG);
        int frame = -1;
        for (int index = 2; index + 8 < huge.length; index++)
            if ((huge[index] & 0xff) == 0xff && (huge[index + 1] & 0xff) == 0xc0) {
                frame = index;
                break;
            }
        if (frame < 0) throw new AssertionError("No JPEG frame header");
        huge[frame + 5] = (byte) (5000 >> 8);
        huge[frame + 6] = (byte) (5000 & 0xff);
        huge[frame + 7] = (byte) (5000 >> 8);
        huge[frame + 8] = (byte) (5000 & 0xff);
        seen.append(" huge=").append(refusal(huge, "5000×5000"));
        byte[] cut = Arrays.copyOf(jpeg, jpeg.length / 3);
        try {
            LauncherIcon.square(cut, 64);
            seen.append(" cut=decoded");
        } catch (java.io.IOException refused) {
            seen.append(" cut=refused");
        }
        return seen.toString();
    }

    private static String refusal(byte[] image, String expected) {
        try {
            LauncherIcon.square(image, 64);
        } catch (java.io.IOException refused) {
            if (!refused.getMessage().contains(expected))
                throw new AssertionError("Refused for another reason: " + refused.getMessage());
            return "refused";
        }
        throw new AssertionError("Accepted an image that names " + expected);
    }

    private static byte[] encoded(int side, Bitmap.CompressFormat format) {
        Bitmap bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xff2a9d8f);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        if (!bitmap.compress(format, 90, out)) throw new AssertionError("Not encoded");
        bitmap.recycle();
        return out.toByteArray();
    }

    private static byte[] reencoded(byte[] png, Bitmap.CompressFormat format) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(png, 0, png.length);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        if (!bitmap.compress(format, 90, out)) throw new AssertionError("Fixture not re-encoded");
        bitmap.recycle();
        return out.toByteArray();
    }

    private static String image(byte[] bytes, String type) {
        JsonObject url = new JsonObject();
        url.addProperty(
                "url", "data:" + type + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP));
        JsonObject picture = new JsonObject();
        picture.addProperty("type", "image_url");
        picture.add("image_url", url);
        JsonArray pictures = new JsonArray();
        pictures.add(picture);
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", "");
        message.add("images", pictures);
        return reply(ImageClient.MODEL, "stop", message, "0.039");
    }

    private static String reply(String model, String finish, JsonObject message, String cost) {
        JsonObject choice = new JsonObject();
        choice.addProperty("finish_reason", finish);
        choice.add("message", message);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        JsonObject usage = new JsonObject();
        usage.addProperty("prompt_tokens", 100);
        usage.addProperty("completion_tokens", 20);
        usage.addProperty("total_tokens", 120);
        usage.addProperty("cost", new BigDecimal(cost));
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.add("choices", choices);
        root.add("usage", usage);
        return root.toString();
    }

    /** Answers each request with the next scripted reply; nothing is sent anywhere. */
    private static final class Scripted extends HttpURLConnection {
        private final Deque<String> replies;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private String reply;

        Scripted(URL url, Deque<String> replies) {
            super(url);
            this.replies = replies;
        }

        @Override
        public void connect() {}

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void disconnect() {}

        @Override
        public OutputStream getOutputStream() {
            return body;
        }

        @Override
        public int getResponseCode() {
            reply = replies.pollFirst();
            return reply == null ? 500 : 200;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(reply.getBytes(StandardCharsets.UTF_8));
        }
    }

    interface WorkerRun {
        StepRun.Outcome run(IBuildService worker) throws Exception;
    }

    private static StepRun.Outcome withWorker(Context target, WorkerRun work) throws Exception {
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
            return work.run(worker.get());
        } finally {
            target.unbindService(connection);
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
