package dev.smallwright.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.smallwright.agent.ImageClient;
import dev.smallwright.agent.ImageServer;
import dev.smallwright.agent.ModelClient;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.controller.ui.StepLog;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * make_app_icon and make_image in a run: a scripted model, a stand-in image server and a real
 * project store.
 */
public final class AppIconRunTest {
    private static final byte[] KEY = "dummy-key".getBytes(UTF_8);
    private static final String ICON_CALL = "{\"description\":\"A green leaf on white\"}";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ProjectSourceStore source;
    private final Map<String, byte[]> prefilled = new java.util.TreeMap<>();
    private RunSteps steps;
    private StepRun run;

    private interface Images {
        ImageClient.Image make(String prompt, ImageClient.Shape shape) throws IOException;
    }

    private StepRun.Outcome run(
            ScriptedModel scripted, ImageServer images, RunLimits limits, AppIcons icons)
            throws Exception {
        return run(
                scripted,
                (prompt, shape) ->
                        images.client().make(KEY, prompt, shape, new ModelClient.Cancellation()),
                limits,
                icons);
    }

    private StepRun.Outcome run(
            ScriptedModel scripted, Images images, RunLimits limits, AppIcons icons)
            throws Exception {
        source = new ProjectSourceStore(temporary.newFolder());
        source.initialize();
        if (!prefilled.isEmpty()) source.commit(source.revision(), prefilled, java.util.Set.of());
        steps = new RunSteps("chat", "request", "Thinking", snapshot -> {});
        ContextPreviewGate gate = new ContextPreviewGate(source);
        String request = "Make it an icon with a green leaf";
        String approved =
                gate.approve(gate.preview(request, source.revision()), request, source.revision());
        run =
                new StepRun(
                        source,
                        new StepRun.Control(),
                        new LibraryTools(
                                new dev.smallwright.libraries.MavenRepositories(
                                        temporary.newFolder(),
                                        new dev.smallwright.libraries.MavenRepositories.Endpoints(
                                                "http://127.0.0.1:9/google/",
                                                "http://127.0.0.1:9/central/",
                                                "http://127.0.0.1:9/search/select"))),
                        limits,
                        RunSkills.NONE,
                        SkillAdding.NONE,
                        icons);
        StepRun.Model steps = scripted.model();
        StepRun.Model model =
                new StepRun.Model() {
                    @Override
                    public ModelClient.StepReply next(JsonArray messages, int promptTokens)
                            throws Exception {
                        return steps.next(messages, promptTokens);
                    }

                    @Override
                    public ModelClient.StepReply summarize(JsonArray messages, int promptTokens)
                            throws Exception {
                        return steps.summarize(messages, promptTokens);
                    }

                    @Override
                    public int contextTokens() {
                        return steps.contextTokens();
                    }

                    @Override
                    public ImageClient.Image image(String prompt, ImageClient.Shape shape)
                            throws IOException {
                        return images.make(prompt, shape);
                    }
                };
        return run.run(
                approved,
                source.revision(),
                () -> true,
                model,
                (revision, buildId) -> {
                    throw new AssertionError("No build was asked for");
                },
                (next, revision, buildId, tokens) -> {},
                this.steps);
    }

    private static AppIcons squares() {
        return new AppIcons((png, side) -> ImageServer.png(side, side), AppIconRunTest::fitted);
    }

    private static final byte[] WEBP = "RIFF fitted picture WEBP".getBytes(UTF_8);

    private static AppIcons.Picture fitted(byte[] png, int maxSide) {
        java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(png, 16, 8);
        return new AppIcons.Picture(WEBP, header.getInt(), header.getInt());
    }

    private static String picture(String name, String shape) {
        return "{\"name\":\""
                + name
                + "\",\"description\":\"A sunny beach with palm trees\",\"shape\":\""
                + shape
                + "\"}";
    }

    private static String sent(ImageServer images, int index, String field) {
        com.google.gson.JsonObject body =
                JsonParser.parseString(images.bodies.get(index)).getAsJsonObject();
        return "aspect_ratio".equals(field)
                ? body.getAsJsonObject("image_config").get("aspect_ratio").getAsString()
                : body.getAsJsonArray("messages")
                        .get(0)
                        .getAsJsonObject()
                        .get("content")
                        .getAsString();
    }

    private Map<String, byte[]> saved() throws Exception {
        return source.readRecoverySources(source.revision());
    }

    private List<Step> shown() {
        return StepLog.decode(steps.saved());
    }

    private static String manifest(Map<String, byte[]> files) {
        return new String(files.get(AppManifestPolicy.PATH), UTF_8);
    }

    @Test
    public void makesTheIconWritesItsResourcesPointsTheManifestAtItAndCountsItsCost()
            throws Exception {
        ScriptedModel scripted =
                new ScriptedModel()
                        .calls("make_app_icon", ICON_CALL)
                        .answer("Your app has a leaf.");
        try (ImageServer images = new ImageServer().png(ImageServer.png(1024, 1024), "0.0390")) {
            StepRun.Outcome outcome = run(scripted, images, RunLimits.OFF, squares());
            assertEquals("Your app has a leaf.", outcome.reply);
            assertEquals(3, outcome.requests);
            assertEquals(3, run.requests());
            assertEquals(new BigDecimal("0.0392"), run.costUsd().stripTrailingZeros());
            assertEquals(1, images.bodies.size());
            String prompt =
                    JsonParser.parseString(images.bodies.get(0))
                            .getAsJsonObject()
                            .getAsJsonArray("messages")
                            .get(0)
                            .getAsJsonObject()
                            .get("content")
                            .getAsString();
            assertTrue(prompt, prompt.contains("A green leaf on white"));
            assertTrue(prompt, prompt.contains("no text"));
            Map<String, byte[]> files = saved();
            assertArrayEquals(ImageServer.png(288, 288), files.get(AppIcons.IMAGE_PATH));
            String icon = new String(files.get(AppIcons.ICON_PATH), UTF_8);
            assertTrue(icon, icon.contains("<adaptive-icon"));
            assertTrue(icon, icon.contains("@drawable/app_icon_image"));
            assertTrue(
                    manifest(files), manifest(files).contains("android:icon=\"@mipmap/app_icon\""));
            String result = scripted.lastToolResult(1);
            assertTrue(result, result.contains("android:icon=\"@mipmap/app_icon\""));
            assertTrue(result, result.contains("Tell the person"));
            Step made = shown().get(0);
            assertEquals(StepKind.DONE, made.getKind());
            assertEquals("Made an app icon", made.getText());
        }
    }

    @Test
    public void anUnusableImageIsAPlainToolResultItsCostCountsAndNothingIsWritten()
            throws Exception {
        ScriptedModel scripted =
                new ScriptedModel().calls("make_app_icon", ICON_CALL).answer("No icon this time.");
        try (ImageServer images =
                new ImageServer().image("data:image/jpeg;base64,AAAA", "0.0390")) {
            StepRun.Outcome outcome = run(scripted, images, RunLimits.OFF, squares());
            assertEquals("No icon this time.", outcome.reply);
            assertEquals(3, outcome.requests);
            assertEquals(new BigDecimal("0.0392"), run.costUsd().stripTrailingZeros());
            assertTrue(
                    scripted.lastToolResult(1),
                    scripted.lastToolResult(1).contains("could not be read"));
            assertNull(saved().get(AppIcons.IMAGE_PATH));
            assertEquals(0, source.revision());
            Step failed = shown().get(0);
            assertEquals(StepKind.FAILED, failed.getKind());
            assertEquals("Could not make an app icon", failed.getText());
        }
    }

    @Test
    public void anImageAndroidCannotDecodeIsAPlainToolResultAndStillCounted() throws Exception {
        ScriptedModel scripted =
                new ScriptedModel().calls("make_app_icon", ICON_CALL).answer("No icon.");
        AppIcons undecodable =
                new AppIcons(
                        (png, side) -> {
                            throw new IOException("Android could not decode the image.");
                        },
                        AppIconRunTest::fitted);
        try (ImageServer images = new ImageServer().png(ImageServer.png(512, 512), "0.0390")) {
            run(scripted, images, RunLimits.OFF, undecodable);
            assertEquals(3, run.requests());
            assertEquals(new BigDecimal("0.0392"), run.costUsd().stripTrailingZeros());
            assertTrue(
                    scripted.lastToolResult(1),
                    scripted.lastToolResult(1).contains("could not decode"));
            assertEquals(0, source.revision());
        }
    }

    @Test
    public void aFailedImageRequestIsAPlainToolResultAndTheRunGoesOn() throws Exception {
        ScriptedModel scripted =
                new ScriptedModel().calls("make_app_icon", ICON_CALL).answer("Your key is out.");
        try (ImageServer images = new ImageServer().answer(402, "{}")) {
            StepRun.Outcome outcome = run(scripted, images, RunLimits.OFF, squares());
            assertEquals("Your key is out.", outcome.reply);
            assertEquals(2, outcome.requests);
            assertTrue(
                    scripted.lastToolResult(1),
                    scripted.lastToolResult(1).contains("OpenRouter credit unavailable"));
            assertEquals(StepKind.FAILED, shown().get(0).getKind());
        }
    }

    @Test
    public void withoutOpenRouterTheRunSaysPlainlyThatIconsAndPicturesNeedIt() throws Exception {
        ScriptedModel scripted =
                new ScriptedModel()
                        .calls("make_app_icon", "{\"description\":\"A green leaf\"}")
                        .answer("Icons need OpenRouter.");
        StepRun.Outcome outcome =
                run(
                        scripted,
                        (prompt, shape) -> {
                            throw new IOException(ImageClient.NEEDS_OPENROUTER);
                        },
                        RunLimits.OFF,
                        squares());
        assertEquals("Icons need OpenRouter.", outcome.reply);
        String result = scripted.lastToolResult(1);
        assertTrue(result, result.contains(ImageClient.NEEDS_OPENROUTER));
        assertEquals(StepKind.FAILED, shown().get(0).getKind());
        assertEquals(ImageClient.NEEDS_OPENROUTER, shown().get(0).getDetail());
        assertEquals(0, source.revision());
    }

    @Test
    public void aRunAsksForAtMostOneIcon() throws Exception {
        ScriptedModel scripted =
                new ScriptedModel()
                        .calls("make_app_icon", ICON_CALL, "make_app_icon", ICON_CALL)
                        .answer("Done.");
        try (ImageServer images = new ImageServer().answer(402, "{}")) {
            run(scripted, images, RunLimits.OFF, squares());
            assertEquals(1, images.bodies.size());
            assertTrue(
                    scripted.lastToolResult(1),
                    scripted.lastToolResult(1).contains("already asked for an icon"));
        }
    }

    @Test
    public void theSpendingLimitStopsTheIconBeforeAnyImageRequest() throws Exception {
        ScriptedModel scripted =
                new ScriptedModel().calls("make_app_icon", ICON_CALL).answer("Not reached.");
        try (ImageServer images = new ImageServer().png(ImageServer.png(512, 512), "0.0390")) {
            StepRun.Outcome outcome =
                    run(
                            scripted,
                            images,
                            new RunLimits(0, new BigDecimal("0.0001"), false),
                            squares());
            assertTrue(outcome.limitReached);
            assertEquals(0, images.bodies.size());
            assertEquals(1, run.requests());
            assertTrue(outcome.reply, outcome.reply.contains("spending limit"));
        }
    }

    @Test
    public void withoutIconsTheToolSaysSoAndNoImageIsRequested() throws Exception {
        ScriptedModel scripted =
                new ScriptedModel().calls("make_app_icon", ICON_CALL).answer("No icons here.");
        try (ImageServer images = new ImageServer().png(ImageServer.png(512, 512), "0.0390")) {
            run(scripted, images, RunLimits.OFF, AppIcons.NONE);
            assertEquals(0, images.bodies.size());
            assertTrue(
                    scripted.lastToolResult(1),
                    scripted.lastToolResult(1).contains("cannot be made"));
            assertFalse(saved().containsKey(AppIcons.IMAGE_PATH));
        }
    }

    @Test
    public void makesAWidePictureWritesItAndReturnsItsResourceAndCountsItsCost() throws Exception {
        ScriptedModel scripted =
                new ScriptedModel()
                        .calls("make_image", picture("beach", "wide"))
                        .answer("The home screen shows a beach.");
        try (ImageServer images = new ImageServer().png(ImageServer.png(1344, 768), "0.0390")) {
            StepRun.Outcome outcome = run(scripted, images, RunLimits.OFF, squares());
            assertEquals("The home screen shows a beach.", outcome.reply);
            assertEquals(3, outcome.requests);
            assertEquals(new BigDecimal("0.0392"), run.costUsd().stripTrailingZeros());
            assertEquals(1, images.bodies.size());
            assertEquals("16:9", sent(images, 0, "aspect_ratio"));
            String prompt = sent(images, 0, "content");
            assertTrue(prompt, prompt.contains("A sunny beach with palm trees"));
            assertArrayEquals(WEBP, saved().get("res/drawable-nodpi/beach.webp"));
            String result = scripted.lastToolResult(1);
            assertTrue(result, result.contains("@drawable/beach"));
            assertTrue(result, result.contains("R.drawable.beach"));
            assertTrue(result, result.contains("1344×768"));
            Step made = shown().get(0);
            assertEquals(StepKind.DONE, made.getKind());
            assertEquals("Made a picture", made.getText());
        }
    }

    @Test
    public void aRunMakesEveryPictureAskedForInTheShapeAskedFor() throws Exception {
        ScriptedModel scripted =
                new ScriptedModel()
                        .calls(
                                "make_image",
                                picture("cover", "square"),
                                "make_image",
                                picture("poster", "tall"),
                                "make_image",
                                picture("banner", "wide"),
                                "make_image",
                                picture("cover", "square"))
                        .answer("Four pictures.");
        try (ImageServer images = new ImageServer().png(ImageServer.png(1024, 1024), "0.0390")) {
            run(scripted, images, RunLimits.OFF, squares());
            assertEquals(4, images.bodies.size());
            assertEquals("1:1", sent(images, 0, "aspect_ratio"));
            assertEquals("9:16", sent(images, 1, "aspect_ratio"));
            assertEquals("16:9", sent(images, 2, "aspect_ratio"));
            assertEquals(new BigDecimal("0.1562"), run.costUsd().stripTrailingZeros());
            Map<String, byte[]> files = saved();
            for (String name : new String[] {"cover", "poster", "banner"})
                assertArrayEquals(name, WEBP, files.get("res/drawable-nodpi/" + name + ".webp"));
            for (String result : scripted.toolResults(1))
                assertTrue(result, result.startsWith("Made the picture"));
        }
    }

    @Test
    public void refusesANameThatIsNotAFreeDrawableNameBeforeAnyImageRequest() throws Exception {
        ScriptedModel scripted =
                new ScriptedModel()
                        .calls(
                                "write_file",
                                "{\"path\":\"res/drawable/logo.xml\",\"content\":\"<shape/>\"}",
                                "make_image",
                                picture("Beach", "wide"),
                                "make_image",
                                picture("2beach", "wide"),
                                "make_image",
                                picture("beach-day", "wide"),
                                "make_image",
                                picture("class", "wide"),
                                "make_image",
                                picture("app_icon_image", "square"),
                                "make_image",
                                picture("logo", "square"))
                        .answer("No pictures.");
        try (ImageServer images = new ImageServer().png(ImageServer.png(512, 512), "0.0390")) {
            run(scripted, images, RunLimits.OFF, squares());
            assertEquals(0, images.bodies.size());
            List<String> results = scripted.toolResults(1);
            assertEquals(7, results.size());
            for (String result : results.subList(1, 7))
                assertTrue(result, result.startsWith("Not made"));
            assertTrue(results.get(6), results.get(6).contains("res/drawable/logo.xml"));
            assertEquals(new BigDecimal("0.0002"), run.costUsd().stripTrailingZeros());
            for (String path : saved().keySet())
                assertFalse(path, path.startsWith("res/drawable-nodpi/"));
        }
    }

    @Test
    public void aFailedPictureRequestIsAPlainResultAndNoMorePicturesAreAskedForInTheRun()
            throws Exception {
        ScriptedModel scripted =
                new ScriptedModel()
                        .calls(
                                "make_image",
                                picture("beach", "wide"),
                                "make_image",
                                picture("forest", "wide"))
                        .answer("Your key is out.");
        try (ImageServer images = new ImageServer().answer(402, "{}")) {
            StepRun.Outcome outcome = run(scripted, images, RunLimits.OFF, squares());
            assertEquals("Your key is out.", outcome.reply);
            assertEquals(1, images.bodies.size());
            List<String> results = scripted.toolResults(1);
            assertTrue(results.get(0), results.get(0).contains("OpenRouter credit unavailable"));
            assertTrue(results.get(1), results.get(1).startsWith("Not made"));
            assertEquals(StepKind.FAILED, shown().get(0).getKind());
            assertEquals(0, source.revision());
        }
    }

    @Test
    public void anUnusablePictureIsAPlainResultItsCostCountsAndNothingIsWritten() throws Exception {
        ScriptedModel scripted =
                new ScriptedModel()
                        .calls("make_image", picture("beach", "wide"))
                        .answer("No picture.");
        try (ImageServer images =
                new ImageServer().image("data:image/jpeg;base64,AAAA", "0.0390")) {
            run(scripted, images, RunLimits.OFF, squares());
            assertEquals(new BigDecimal("0.0392"), run.costUsd().stripTrailingZeros());
            assertTrue(
                    scripted.lastToolResult(1),
                    scripted.lastToolResult(1).contains("could not be read"));
            assertEquals(0, source.revision());
            Step failed = shown().get(0);
            assertEquals(StepKind.FAILED, failed.getKind());
            assertEquals("Could not make a picture", failed.getText());
        }
    }

    @Test
    public void aPictureTooLargeForAProjectFileIsRefusedAndStillCounted() throws Exception {
        ScriptedModel scripted =
                new ScriptedModel()
                        .calls("make_image", picture("beach", "wide"))
                        .answer("Too large.");
        AppIcons large =
                new AppIcons(
                        (png, side) -> ImageServer.png(side, side),
                        (png, side) -> new AppIcons.Picture(new byte[1024 * 1024 + 1], 1536, 864));
        try (ImageServer images = new ImageServer().png(ImageServer.png(1344, 768), "0.0390")) {
            run(scripted, images, RunLimits.OFF, large);
            assertEquals(new BigDecimal("0.0392"), run.costUsd().stripTrailingZeros());
            assertTrue(
                    scripted.lastToolResult(1), scripted.lastToolResult(1).contains("too large"));
            assertEquals(0, source.revision());
        }
    }

    @Test
    public void aPictureThatWouldTakeTheProjectPastSixteenMegabytesIsRefusedAndTheRunKeepsItsWork()
            throws Exception {
        for (int file = 0; file < 16; file++)
            prefilled.put("assets/blob_" + file + ".txt", new byte[1_000_000]);
        ScriptedModel scripted =
                new ScriptedModel()
                        .calls("make_image", picture("beach", "wide"))
                        .answer("Saved what fits.");
        AppIcons full =
                new AppIcons(
                        (png, side) -> ImageServer.png(side, side),
                        (png, side) -> new AppIcons.Picture(new byte[1024 * 1024], 1536, 864));
        try (ImageServer images = new ImageServer().png(ImageServer.png(1344, 768), "0.0390")) {
            StepRun.Outcome outcome = run(scripted, images, RunLimits.OFF, full);
            assertEquals("Saved what fits.", outcome.reply);
            String refusal = scripted.lastToolResult(1);
            assertTrue(refusal, refusal.contains("16 MB"));
            assertTrue(refusal, refusal.endsWith("Nothing was written."));
            assertTrue(saved().containsKey("assets/blob_15.txt"));
            assertFalse(saved().containsKey(AppIcons.picturePath("beach")));
        }
    }

    @Test
    public void theSpendingLimitStopsAPictureBeforeAnyImageRequest() throws Exception {
        ScriptedModel scripted =
                new ScriptedModel()
                        .calls("make_image", picture("beach", "wide"))
                        .answer("Not reached.");
        try (ImageServer images = new ImageServer().png(ImageServer.png(512, 512), "0.0390")) {
            StepRun.Outcome outcome =
                    run(
                            scripted,
                            images,
                            new RunLimits(0, new BigDecimal("0.0001"), false),
                            squares());
            assertTrue(outcome.limitReached);
            assertEquals(0, images.bodies.size());
            assertTrue(outcome.reply, outcome.reply.contains("spending limit"));
        }
    }

    @Test
    public void withoutImagesMakeImageSaysSoAndNoImageIsRequested() throws Exception {
        ScriptedModel scripted =
                new ScriptedModel()
                        .calls("make_image", picture("beach", "wide"))
                        .answer("No pictures here.");
        try (ImageServer images = new ImageServer().png(ImageServer.png(512, 512), "0.0390")) {
            run(scripted, images, RunLimits.OFF, AppIcons.NONE);
            assertEquals(0, images.bodies.size());
            assertTrue(
                    scripted.lastToolResult(1),
                    scripted.lastToolResult(1).contains("cannot be made"));
        }
    }
}
