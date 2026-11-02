package dev.smallwright.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.smallwright.agent.RunTools;
import dev.smallwright.controller.ui.LiveRun;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.controller.ui.StepLog;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** read_crash_report and look_at_app in a run: a scripted model, a stand-in phone, a real store. */
public final class AppLinkRunTest {
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02};

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ProjectIdentity project;
    private ProjectSourceStore source;
    private RunSteps steps;
    private final Phone phone = new Phone();
    private final List<LiveRun> published = new ArrayList<>();

    private final class Phone implements AppLink.Phone {
        VerifiedArtifact built;
        boolean installed = true;
        boolean onScreen = true;
        AppLink.Reply reply = AppLink.Reply.picture(AppLinkRulesTest.png(720, 1568));
        final List<String> opened = new ArrayList<>();
        final List<Integer> askedRevisions = new ArrayList<>();

        @Override
        public VerifiedArtifact build(int revision) {
            askedRevisions.add(revision);
            return built;
        }

        @Override
        public boolean installed(VerifiedArtifact artifact) {
            return installed && artifact == built;
        }

        @Override
        public List<String> activities() {
            return List.of(
                    project.packageName() + ".MainActivity",
                    project.packageName() + ".DetailActivity",
                    project.packageName() + ".builderlink.BuilderLink$Look");
        }

        @Override
        public String launcher() {
            return project.packageName() + ".MainActivity";
        }

        @Override
        public boolean builderOnScreen() {
            return onScreen;
        }

        @Override
        public AppLink.Reply look(String activity, BooleanSupplier active) {
            opened.add(activity);
            return reply;
        }

        @Override
        public byte[] jpeg(byte[] png) {
            return JPEG.clone();
        }
    }

    @Before
    public void createProject() throws Exception {
        project = new ProjectRegistry(temporary.newFolder("projects")).create("Notes");
        source = new ProjectSourceStore(project);
        source.initialize();
        File apk = temporary.newFile("app.apk");
        Files.write(apk.toPath(), new byte[] {1, 2, 3});
        phone.built = VerifiedArtifact.capture(apk);
    }

    private StepRun.Outcome run(ScriptedModel model, boolean readsImages) throws Exception {
        steps = new RunSteps("chat", "request", "Thinking", published::add);
        ContextPreviewGate gate = new ContextPreviewGate(source);
        String request = "It crashed when I opened it";
        String approved =
                gate.approve(gate.preview(request, source.revision()), request, source.revision());
        StepRun run =
                new StepRun(
                                source,
                                new StepRun.Control(),
                                new LibraryTools(
                                        new dev.smallwright.libraries.MavenRepositories(
                                                temporary.newFolder(),
                                                new dev.smallwright.libraries.MavenRepositories
                                                        .Endpoints(
                                                        "http://127.0.0.1:9/google/",
                                                        "http://127.0.0.1:9/central/",
                                                        "http://127.0.0.1:9/search/select"))))
                        .appLink(
                                new AppLink(
                                        phone,
                                        new AppCrashReports(project.directory()),
                                        project.packageName(),
                                        readsImages,
                                        "DeepSeek V4.1 Flash"));
        return run.run(
                approved,
                source.revision(),
                () -> true,
                model.model(),
                (revision, buildId) -> {
                    throw new AssertionError("No build was asked for");
                },
                (next, revision, buildId, tokens) -> {},
                steps);
    }

    private List<Step> shown() {
        return StepLog.decode(steps.saved());
    }

    private static String toolResult(JsonArray messages, int index) {
        int seen = 0;
        for (JsonElement message : messages) {
            JsonObject object = message.getAsJsonObject();
            if (!"tool".equals(object.get("role").getAsString())) continue;
            if (seen++ == index) return object.get("content").getAsString();
        }
        throw new AssertionError("No tool result " + index + " in " + messages);
    }

    @Test
    public void bothToolsAreOfferedWithTheirArguments() {
        JsonObject look = null;
        boolean crash = false;
        for (JsonElement tool : RunTools.schemas()) {
            JsonObject function = tool.getAsJsonObject().getAsJsonObject("function");
            String name = function.get("name").getAsString();
            if (name.equals(RunTools.READ_CRASH_REPORT)) {
                crash = true;
                assertEquals(
                        0,
                        function.getAsJsonObject("parameters")
                                .getAsJsonObject("properties")
                                .size());
            }
            if (name.equals(RunTools.LOOK_AT_APP)) look = function.getAsJsonObject("parameters");
        }
        assertTrue(crash);
        assertTrue(look.getAsJsonObject("properties").has("activity"));
        assertEquals(0, look.getAsJsonArray("required").size());
    }

    @Test
    public void readsTheLatestReportTheAppSentOrSaysThereIsNone() throws Exception {
        ScriptedModel none =
                new ScriptedModel().calls(RunTools.READ_CRASH_REPORT, "{}").answer("No report.");
        run(none, true);
        assertEquals(AppCrashReports.NONE, toolResult(none.messages(1), 0));

        new AppCrashReports(project.directory())
                .receive(AppCrashReportsTest.REPORT.getBytes(UTF_8), 6);
        ScriptedModel sent =
                new ScriptedModel().calls(RunTools.READ_CRASH_REPORT, "{}").answer("Fixed it.");
        run(sent, false);
        String result = toolResult(sent.messages(1), 0);
        assertTrue(result, result.contains("version 6"));
        assertTrue(result, result.contains("Cause 0: java.lang.NullPointerException"));
        assertTrue(
                shown().stream().anyMatch(step -> step.getText().equals("Read the crash report")));
    }

    @Test
    public void looksAtTheLauncherAndSendsThePictureAfterTheToolResults() throws Exception {
        ScriptedModel model =
                new ScriptedModel()
                        .calls(RunTools.LOOK_AT_APP, "{}", RunTools.LIST_FILES, "{}")
                        .answer("It shows the counter.");

        run(model, true);

        assertEquals(List.of(project.packageName() + ".MainActivity"), phone.opened);
        assertEquals(List.of(0), phone.askedRevisions);
        JsonArray next = model.messages(1);
        JsonObject last = next.get(next.size() - 1).getAsJsonObject();
        assertEquals("user", last.get("role").getAsString());
        JsonArray parts = last.getAsJsonArray("content");
        assertEquals(
                AppLink.PICTURE_NOTE, parts.get(0).getAsJsonObject().get("text").getAsString());
        assertTrue(
                parts.get(1)
                        .getAsJsonObject()
                        .getAsJsonObject("image_url")
                        .get("url")
                        .getAsString()
                        .startsWith("data:image/jpeg;base64,"));
        assertEquals("tool", next.get(next.size() - 2).getAsJsonObject().get("role").getAsString());
        assertEquals("tool", next.get(next.size() - 3).getAsJsonObject().get("role").getAsString());
        String result = toolResult(next, 0);
        assertTrue(result, result.contains(".MainActivity (720 by 1568 pixels)"));
        assertTrue(shown().stream().anyMatch(step -> step.getText().equals("Looked at the app")));
    }

    @Test
    public void aNewerLookReplacesTheEarlierPicture() throws Exception {
        ScriptedModel model =
                new ScriptedModel()
                        .calls(RunTools.LOOK_AT_APP, "{}")
                        .calls(RunTools.LOOK_AT_APP, "{\"activity\":\"DetailActivity\"}")
                        .answer("Both screens look right.");

        run(model, true);

        assertEquals(
                List.of(
                        project.packageName() + ".MainActivity",
                        project.packageName() + ".DetailActivity"),
                phone.opened);
        assertEquals(1, AttachedImages.pictures(model.messages(2))[0]);
        boolean replaced = false;
        for (JsonElement message : model.messages(2))
            if (message.getAsJsonObject().get("content").isJsonPrimitive()
                    && AppLink.EARLIER_PICTURE.equals(
                            message.getAsJsonObject().get("content").getAsString()))
                replaced = true;
        assertTrue(replaced);
    }

    @Test
    public void theLookStepShowsThePictureSentToTheModelAndOnlyTheNewestKeepsIt() throws Exception {
        ScriptedModel model =
                new ScriptedModel()
                        .calls(RunTools.LOOK_AT_APP, "{}")
                        .calls(RunTools.LOOK_AT_APP, "{\"activity\":\"DetailActivity\"}")
                        .answer("Both screens look right.");

        run(model, true);

        assertTrue(
                published.stream()
                        .anyMatch(
                                live ->
                                        looks(live).size() == 1
                                                && java.util.Arrays.equals(
                                                        JPEG, looks(live).get(0).getPicture())));
        List<Step> last = looks(published.get(published.size() - 1));
        assertEquals(2, last.size());
        assertNull(last.get(0).getPicture());
        assertArrayEquals(JPEG, last.get(1).getPicture());
    }

    private static List<Step> looks(LiveRun live) {
        List<Step> found = new ArrayList<>();
        for (Step step : live.getSteps())
            if (step.getText().equals("Looked at the app")) found.add(step);
        return found;
    }

    @Test
    public void refusesInPlainWordsWhenTheLookCannotBeMade() throws Exception {
        assertLookRefused(false, () -> {}, "can't look at pictures");
        assertLookRefused(true, () -> phone.installed = false, "not the current version");
        assertLookRefused(true, () -> phone.built = null, "not built yet");
        assertLookRefused(true, () -> phone.onScreen = false, "not in the builder right now");
        assertLookRefused(
                true,
                () -> phone.reply = AppLink.Reply.failed(AppLink.Reply.CLOSED),
                "closed before a picture was taken");
        assertLookRefused(
                true,
                () -> phone.reply = AppLink.Reply.picture(new byte[] {1, 2, 3}),
                "could not be used");
        assertLookRefused(
                true,
                () -> phone.reply = AppLink.Reply.failed(AppLink.Reply.NOT_OPENED),
                "Android did not let the builder open the app");
    }

    @Test
    public void refusesAnActivityTheAppDoesNotDeclare() throws Exception {
        ScriptedModel model =
                new ScriptedModel()
                        .calls(
                                RunTools.LOOK_AT_APP,
                                "{\"activity\":\".builderlink.BuilderLink$Look\"}")
                        .answer("Done.");
        run(model, true);
        String result = toolResult(model.messages(1), 0);
        assertTrue(result, result.contains("not an activity the installed app declares"));
        assertTrue(phone.opened.isEmpty());
    }

    @Test
    public void editedFilesMustBeBuiltBeforeALook() throws Exception {
        ScriptedModel model =
                new ScriptedModel()
                        .calls(
                                RunTools.WRITE_FILE,
                                "{\"path\":\"assets/a.txt\",\"content\":\"x\"}",
                                RunTools.LOOK_AT_APP,
                                "{}")
                        .answer("Done.");
        run(model, true);
        assertTrue(toolResult(model.messages(1), 1).contains("changes that are not built yet"));
        assertTrue(phone.askedRevisions.isEmpty());
    }

    @Test
    public void theModelCannotWriteTheBuildersCode() throws Exception {
        String path =
                "java/" + project.packageName().replace('.', '/') + "/builderlink/BuilderLink.java";
        ScriptedModel model =
                new ScriptedModel()
                        .calls(
                                RunTools.WRITE_FILE,
                                "{\"path\":\"" + path + "\",\"content\":\"class X {}\"}")
                        .answer("Done.");
        run(model, true);
        assertTrue(toolResult(model.messages(1), 0).contains("the builder's own code"));
        assertNull(source.readCurrentFiles(source.revision()).get(path));
    }

    private void assertLookRefused(boolean readsImages, Runnable setUp, String why)
            throws Exception {
        phone.installed = true;
        phone.onScreen = true;
        phone.reply = AppLink.Reply.picture(AppLinkRulesTest.png(10, 10));
        File apk = temporary.newFile();
        Files.write(apk.toPath(), new byte[] {4});
        phone.built = VerifiedArtifact.capture(apk);
        setUp.run();
        ScriptedModel model =
                new ScriptedModel().calls(RunTools.LOOK_AT_APP, "{}").answer("Told them.");
        run(model, readsImages);
        String result = toolResult(model.messages(1), 0);
        assertTrue(result, result.contains(why));
        assertEquals(0, AttachedImages.pictures(model.messages(1))[0]);
        Step failed = null;
        for (Step step : shown()) if (step.getKind() == StepKind.FAILED) failed = step;
        assertEquals("Could not look at the app", failed.getText());
    }
}
