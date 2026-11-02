package dev.smallwright.controller;

import static org.junit.Assert.*;

import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.controller.ui.StepLog;
import dev.smallwright.libraries.MavenFixture;
import java.io.IOException;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** lookup_code in a run: the platform jar and the run's own libraries, shown as plain steps. */
public final class CodeLookupRunTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private MavenFixture fixture;
    private ProjectSourceStore source;
    private RunSteps steps;

    @Before
    public void start() throws Exception {
        fixture = new MavenFixture();
        source = new ProjectSourceStore(temporary.newFolder());
        source.initialize();
        fixture.jar("central", "org.example:sample:1", "");
        fixture.put(
                "central",
                "org/example/sample/1/sample-1.jar",
                MavenFixture.zip(JavaFixtures.sample(temporary.newFolder())));
    }

    @After
    public void stop() {
        fixture.close();
    }

    private void run(ScriptedModel model, LibraryTools.PlatformJar platform) throws Exception {
        steps = new RunSteps("chat", "request", "Thinking", snapshot -> {});
        ContextPreviewGate gate = new ContextPreviewGate(source);
        String request = "Cross out done items";
        String approved =
                gate.approve(gate.preview(request, source.revision()), request, source.revision());
        new StepRun(
                        source,
                        new StepRun.Control(),
                        new LibraryTools(
                                fixture.repositories(temporary.newFolder()),
                                library -> true,
                                platform))
                .run(
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

    private static String symbol(String name) {
        return "{\"symbol\":\"" + name + "\"}";
    }

    @Test
    public void looksUpThePlatformAndTheLibrariesAddedInThisRun() throws Exception {
        ScriptedModel model =
                new ScriptedModel()
                        .calls(
                                "lookup_code",
                                symbol("android.graphics.Paint.STRIKETHROUGH_TEXT_FLAG"),
                                "lookup_code",
                                symbol("Paint.STRIKE_THRU_TEXT_FLAG"))
                        .calls(
                                "add_library",
                                "{\"library\":\"org.example:sample:1\"}",
                                "lookup_code",
                                "{\"symbol\":\"fixture.Sample#twice\",\"offset\":1}")
                        .answer("Done.");
        run(model, JavaFixtures::platformJar);
        String missing =
                model.messages(1)
                        .get(model.messages(1).size() - 2)
                        .getAsJsonObject()
                        .get("content")
                        .getAsString();
        assertTrue(missing, missing.contains("has no member STRIKETHROUGH_TEXT_FLAG"));
        assertTrue(missing, missing.contains("STRIKE_THRU_TEXT_FLAG = 16"));
        assertTrue(
                model.lastToolResult(1),
                model.lastToolResult(1)
                        .contains("\n  public static final int STRIKE_THRU_TEXT_FLAG = 16\n"));
        assertTrue(
                model.lastToolResult(2),
                model.lastToolResult(2)
                        .endsWith(
                                ", in library org.example:sample:1:\n"
                                        + "  public static int twice(int value)\n"));
        List<Step> shown = StepLog.decode(steps.saved());
        assertEquals("Looked up Paint.STRIKETHROUGH_TEXT_FLAG", shown.get(0).getText());
        assertEquals("Not found", shown.get(0).getDetail());
        assertEquals(StepKind.DONE, shown.get(0).getKind());
        assertEquals("Looked up Paint.STRIKE_THRU_TEXT_FLAG", shown.get(1).getText());
        assertEquals("", shown.get(1).getDetail());
        assertEquals("Looked up Sample.twice", shown.get(3).getText());
    }

    @Test
    public void withoutPlatformFilesItSaysSoAndStillSearchesTheLibraries() throws Exception {
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_library", "{\"library\":\"org.example:sample:1\"}")
                        .calls(
                                "lookup_code",
                                symbol("Paint"),
                                "lookup_code",
                                symbol("fixture.Sample.LETTER"))
                        .answer("Done.");
        run(
                model,
                () -> {
                    throw new IOException("The Android platform files are missing or changed");
                });
        String paint =
                model.messages(2)
                        .get(model.messages(2).size() - 2)
                        .getAsJsonObject()
                        .get("content")
                        .getAsString();
        assertTrue(
                paint,
                paint.startsWith(
                        "The Android platform files are not on this phone, so only the project's"
                                + " libraries were searched.\nNo class Paint"));
        assertTrue(
                model.lastToolResult(2),
                model.lastToolResult(2).endsWith("  public static final char LETTER = 'x'\n"));
    }

    @Test
    public void aBrokenLibraryFailsTheLookupStepWithItsReason() throws Exception {
        fixture.jar("central", "org.example:broken:1", "");
        fixture.put(
                "central",
                "org/example/broken/1/broken-1.jar",
                MavenFixture.zip(java.util.Map.of("bad/Broken.class", new byte[] {1, 2, 3})));
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_library", "{\"library\":\"org.example:broken:1\"}")
                        .calls("lookup_code", symbol("bad.Broken"))
                        .answer("Done.");
        run(model, JavaFixtures::platformJar);
        assertEquals(
                "bad/Broken.class in library org.example:broken:1 is not a valid class file",
                model.lastToolResult(2));
        Step step = StepLog.decode(steps.saved()).get(1);
        assertEquals(StepKind.FAILED, step.getKind());
        assertEquals("Could not look up Broken", step.getText());
    }
}
