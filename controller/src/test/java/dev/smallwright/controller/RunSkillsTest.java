package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.smallwright.controller.ui.LiveRun;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.controller.ui.StepLog;
import dev.smallwright.project.ChatStore;
import dev.smallwright.skills.GitHubFixture;
import dev.smallwright.skills.GitHubSkills;
import dev.smallwright.skills.SkillFile;
import dev.smallwright.skills.SkillStore;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Skills at the tap on Send: the list and a /name skill in the first request, and read_skill. */
public final class RunSkillsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ProjectSourceStore source;
    private SkillStore store;
    private File skillsFolder;
    private RunSteps steps;
    private final List<LiveRun> published = new ArrayList<>();
    private GitHubFixture github;
    private SkillAdding.Permission ask;

    @After
    public void closeGitHub() {
        if (github != null) github.close();
    }

    private void setUp() throws Exception {
        source = new ProjectSourceStore(temporary.newFolder());
        source.initialize();
        skillsFolder = temporary.newFolder();
        store = new SkillStore(skillsFolder);
        store.add(
                "https://github.com/o/r",
                List.of(
                        skill(
                                "tdd",
                                "Test first.",
                                false,
                                "tests.md",
                                "Good tests.",
                                "references/mocking.md",
                                "Mock little."),
                        skill("grilling", "Grill the user about a plan.", false),
                        skill("grill-me", "An interview.", true),
                        skill("deploy", "Deploys with git.", false)));
        store.setOn("deploy", false);
        steps = new RunSteps("chat", "request", "Thinking", published::add);
        github = new GitHubFixture();
        github.publish(
                Map.of(
                        "skills/plan-grill/SKILL.md",
                        GitHubFixture.skill("plan-grill", "Grill a plan hard.", false),
                        "skills/interview/SKILL.md",
                        GitHubFixture.skill("interview", "Interview the person.", true),
                        "skills/interview/notes.md",
                        "Notes.",
                        "skills/grilling/SKILL.md",
                        GitHubFixture.skill("grilling", "Someone else's grilling.", false)));
    }

    private static GitHubSkills.Found skill(
            String name, String description, boolean personOnly, String... extra) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(
                "SKILL.md",
                ("---\nname: "
                                + name
                                + "\ndescription: "
                                + description
                                + (personOnly ? "\ndisable-model-invocation: true" : "")
                                + "\n---\nINSTRUCTIONS OF "
                                + name
                                + "\n")
                        .getBytes(StandardCharsets.UTF_8));
        for (int index = 0; index < extra.length; index += 2)
            files.put(extra[index], extra[index + 1].getBytes(StandardCharsets.UTF_8));
        return new GitHubSkills.Found(SkillFile.parse(files.get("SKILL.md")), files);
    }

    private ContextPreviewGate gate() {
        return new ContextPreviewGate(source, null, null, store);
    }

    private static JsonObject payload(String payload) {
        return JsonParser.parseString(payload).getAsJsonObject();
    }

    @Test
    public void theFirstRequestListsTheSkillsThatAreOnAndTheModelMayStart() throws Exception {
        setUp();
        JsonObject first =
                payload(gate().preview("Make a habit tracker", source.revision()).payload());
        assertEquals(
                "[{\"name\":\"grilling\",\"description\":\"Grill the user about a plan.\"},"
                        + "{\"name\":\"tdd\",\"description\":\"Test first.\"}]",
                first.get("skills").toString());
        String note = first.get("skills_note").getAsString();
        assertTrue(note.contains("shell, git and GitHub"));
        assertTrue(note.contains("skip every step that needs a tool you do not have"));
        assertTrue(note.contains("read_skill"));
        assertTrue(note.contains("asking questions before you build"));
        assertTrue(note.contains("never what the builder allows"));
        assertFalse(first.has("skill_in_use"));
    }

    @Test
    public void withNoSkillsTheFirstRequestSaysNothingAboutThem() throws Exception {
        setUp();
        for (SkillStore.Installed skill : store.list()) store.remove(skill.name);
        JsonObject first =
                payload(gate().preview("Make a habit tracker", source.revision()).payload());
        assertFalse(first.has("skills"));
        assertFalse(first.has("skills_note"));
        JsonObject withoutStore =
                payload(new ContextPreviewGate(source).preview("Hi", source.revision()).payload());
        assertFalse(withoutStore.has("skills_note"));
    }

    @Test
    public void aSlashNameAddsThatSkillsTextAtSend() throws Exception {
        setUp();
        JsonObject first =
                payload(gate().preview("/grill-me a habit tracker", source.revision()).payload());
        JsonObject used = first.getAsJsonObject("skill_in_use");
        assertEquals("grill-me", used.get("name").getAsString());
        assertTrue(used.get("instructions").getAsString().contains("INSTRUCTIONS OF grill-me"));
        assertEquals("/grill-me a habit tracker", first.get("user_request").getAsString());
        assertTrue(
                payload(gate().preview("/tdd", source.revision()).payload()).has("skill_in_use"));
    }

    @Test
    public void anUnknownSlashNameIsSentForTheBuilderToFindButAnOffOneIsRefused() throws Exception {
        setUp();
        JsonObject first =
                payload(gate().preview("/grill-you a habit tracker", source.revision()).payload());
        JsonObject wanted = first.getAsJsonObject("skill_wanted");
        assertEquals("grill-you", wanted.get("name").getAsString());
        assertTrue(wanted.get("note").getAsString().contains("add_skill"));
        assertFalse(first.has("skill_in_use"));
        for (String notAName : List.of("/Is it OK?", "/? help", "/" + "a".repeat(65) + " x"))
            assertThrows(
                    notAName,
                    RunSkills.Refused.class,
                    () -> gate().preview(notAName, source.revision()));
        RunSkills.Refused off =
                assertThrows(
                        RunSkills.Refused.class,
                        () -> gate().preview("/deploy now", source.revision()));
        assertTrue(off.getMessage().contains("off"));
        assertThrows(
                RunSkills.Refused.class,
                () -> new ContextPreviewGate(source).preview("/grill-me x", source.revision()));
    }

    @Test
    public void onlyALeadingSlashNameStartsASkill() throws Exception {
        setUp();
        for (String message :
                new String[] {
                    "use /grill-me later", "/sdcard/Download is empty", "/ grill-me", "grill-me"
                })
            assertFalse(
                    message,
                    payload(gate().preview(message, source.revision()).payload())
                            .has("skill_in_use"));
    }

    @Test
    public void aSkillChangedBetweenPreviewAndTheTapIsRefused() throws Exception {
        setUp();
        ContextPreviewGate gate = gate();
        ContextPreviewGate.Preview preview = gate.preview("Make it", source.revision());
        store.setOn("tdd", false);
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(preview, "Make it", source.revision()));
    }

    private interface AfterTap {
        void run() throws Exception;
    }

    private StepRun.Outcome run(String request, ScriptedModel model) throws Exception {
        return run(request, model, () -> {});
    }

    private StepRun.Outcome run(String request, ScriptedModel model, AfterTap afterTap)
            throws Exception {
        ContextPreviewGate gate = gate();
        ContextPreviewGate.Preview preview = gate.preview(request, source.revision());
        String approved = gate.approve(preview, request, source.revision());
        afterTap.run();
        return start(preview, approved, model);
    }

    private StepRun.Outcome start(
            ContextPreviewGate.Preview preview, String approved, ScriptedModel model)
            throws Exception {
        return new StepRun(
                        source,
                        new StepRun.Control(),
                        new LibraryTools(
                                new dev.smallwright.libraries.MavenRepositories(
                                        temporary.newFolder(),
                                        new dev.smallwright.libraries.MavenRepositories.Endpoints(
                                                "http://127.0.0.1:9/g/",
                                                "http://127.0.0.1:9/c/",
                                                "http://127.0.0.1:9/s/select"))),
                        RunLimits.OFF,
                        preview.skills(),
                        new SkillAdding(github.skills(), ask, "Habits"))
                .run(
                        approved,
                        source.revision(),
                        () -> true,
                        model.model(),
                        (revision, buildId) -> {
                            throw new AssertionError("No build");
                        },
                        (next, revision, buildId, tokens) -> {},
                        steps);
    }

    /** The content of tool result {@code position} in request {@code index}. */
    private static String toolResult(ScriptedModel model, int index, int position) {
        List<String> results = new ArrayList<>();
        for (com.google.gson.JsonElement message : model.messages(index)) {
            JsonObject object = message.getAsJsonObject();
            if ("tool".equals(object.get("role").getAsString()))
                results.add(object.get("content").getAsString());
        }
        return results.get(position);
    }

    private List<Step> shown() {
        return StepLog.decode(steps.saved());
    }

    private static String readSkill(String name, String file) {
        JsonObject args = new JsonObject();
        args.addProperty("name", name);
        if (file != null) args.addProperty("file", file);
        return args.toString();
    }

    private static String addSkill(String link, String name) {
        JsonObject args = new JsonObject();
        args.addProperty("link", link);
        if (name != null) args.addProperty("name", name);
        return args.toString();
    }

    private static String addSkillHere(String link, String name) {
        JsonObject args = JsonParser.parseString(addSkill(link, name)).getAsJsonObject();
        args.addProperty("project_only", true);
        return args.toString();
    }

    private List<String> downloaded() {
        List<String> raw = new ArrayList<>();
        for (String request : github.requests())
            if (request.startsWith("/raw/"))
                raw.add(
                        request.substring(
                                ("/raw/owner/repo/" + GitHubFixture.COMMIT + "/").length()));
        return raw;
    }

    private static final String LINK = "https://github.com/owner/repo";

    private SkillStore.Installed added(String name) throws Exception {
        for (SkillStore.Installed skill : store.list()) if (skill.name.equals(name)) return skill;
        return null;
    }

    @Test
    public void theBuilderAddsANamedSkillTurnedOnAndReadsItInTheSameRun() throws Exception {
        setUp();
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_skill", addSkill(LINK, "plan-grill"))
                        .calls("read_skill", readSkill("plan-grill", null))
                        .answer("Done.");
        run("Add the plan-grill skill and grill my plan", model);
        String result = toolResult(model, 1, 0);
        assertTrue(result, result.contains("Added plan-grill: Grill a plan hard."));
        assertTrue(toolResult(model, 2, 1).contains("INSTRUCTIONS OF plan-grill"));
        assertTrue(added("plan-grill").on);
        assertEquals(null, added("interview"));
        assertEquals("Added skill plan-grill", shown().get(0).getText());
        assertEquals(StepKind.DONE, shown().get(0).getKind());
        assertEquals("Read skill plan-grill", shown().get(1).getText());
        assertEquals(List.of("skills/plan-grill/SKILL.md"), downloaded());
        assertEquals(null, added("plan-grill").project);
    }

    @Test
    public void aSkillForThisProjectOnlyIsNotOfferedInOtherProjects() throws Exception {
        setUp();
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_skill", addSkillHere(LINK, "plan-grill"))
                        .answer("Done.");
        run("Add plan-grill for this project only", model);
        assertTrue(toolResult(model, 1, 0).contains("on for this project only"));
        assertEquals("Added skill plan-grill for this project", shown().get(0).getText());
        assertEquals(source.projectId(), added("plan-grill").project);
        JsonObject here = payload(gate().preview("Hello", source.revision()).payload());
        assertTrue(here.get("skills").toString().contains("plan-grill"));
        assertFalse(
                RunSkills.atSend(store, "another-project", "Hello", List.of())
                        .offered()
                        .containsKey("plan-grill"));
        assertTrue(
                RunSkills.atSend(store, "another-project", "Hello", List.of())
                        .offered()
                        .containsKey("grilling"));
    }

    @Test
    public void anotherProjectsSkillIsRefusedAtSendAndNeverAddedAgain() throws Exception {
        setUp();
        store.add(
                "https://github.com/o/r",
                List.of(skill("plan-grill", "Elsewhere.", false)),
                "other",
                "Garden");
        RunSkills.Refused refused =
                assertThrows(
                        RunSkills.Refused.class,
                        () -> gate().preview("/plan-grill my plan", source.revision()));
        assertTrue(refused.getMessage(), refused.getMessage().contains("only in Garden"));
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_skill", addSkill(LINK, "plan-grill"))
                        .answer("Done.");
        run("Add plan-grill", model);
        assertTrue(toolResult(model, 1, 0).contains("only in Garden"));
        assertEquals("other", added("plan-grill").project);
    }

    @Test
    public void aSkillInAFolderOfAnotherNameIsStillFound() throws Exception {
        setUp();
        github.publish(
                Map.of(
                        "x/grill/SKILL.md", GitHubFixture.skill("grill-v2", "Second.", false),
                        "y/real/SKILL.md", GitHubFixture.skill("grill", "First.", false)));
        ScriptedModel model =
                new ScriptedModel().calls("add_skill", addSkill(LINK, "grill")).answer("Done.");
        run("Add grill", model);
        assertTrue(
                toolResult(model, 1, 0), toolResult(model, 1, 0).contains("Added grill: First."));
        assertEquals(null, added("grill-v2"));
    }

    @Test
    public void aSkillForEveryProjectMustFitEachProjectsList() throws Exception {
        setUp();
        List<GitHubSkills.Found> theirs = new ArrayList<>();
        for (int index = 0; index < 15; index++)
            theirs.add(skill("g" + index, "g".repeat(1000), false));
        store.add("https://github.com/o/r", theirs, "other", "Garden");
        Map<String, String> two = new LinkedHashMap<>();
        for (int index = 0; index < 2; index++)
            two.put(
                    "skills/n" + index + "/SKILL.md",
                    GitHubFixture.skill("n" + index, "n".repeat(1000), false));
        github.publish(two);
        ScriptedModel everywhere =
                new ScriptedModel().calls("add_skill", addSkill(LINK, null)).answer("Done.");
        run("Add both", everywhere);
        assertTrue(toolResult(everywhere, 1, 0).contains("too long"));
        assertEquals(null, added("n0"));
        ScriptedModel here =
                new ScriptedModel().calls("add_skill", addSkillHere(LINK, null)).answer("Done.");
        run("Add both here", here);
        assertEquals(source.projectId(), added("n0").project);
    }

    @Test
    public void aSkillAlreadyOnThePhoneIsNeverReplaced() throws Exception {
        setUp();
        ScriptedModel model =
                new ScriptedModel().calls("add_skill", addSkill(LINK, "grilling")).answer("Done.");
        run("Add the grilling skill", model);
        String result = toolResult(model, 1, 0);
        assertTrue(result, result.contains("grilling is already on this phone"));
        assertEquals("Grill the user about a plan.", added("grilling").description);
        assertEquals("Skill grilling is already added", shown().get(0).getText());
    }

    @Test
    public void skillsThatWouldMakeTheListTooLongToSendAreNotAdded() throws Exception {
        setUp();
        Map<String, String> many = new LinkedHashMap<>();
        for (int index = 0; index < 20; index++)
            many.put(
                    "skills/s" + index + "/SKILL.md",
                    GitHubFixture.skill("s" + index, "d".repeat(1000), false));
        github.publish(many);
        ScriptedModel model =
                new ScriptedModel().calls("add_skill", addSkill(LINK, null)).answer("Done.");
        run("Add all of them", model);
        assertTrue(toolResult(model, 1, 0).contains("too long"));
        assertEquals(null, added("s0"));
        assertEquals(StepKind.FAILED, shown().get(0).getKind());
        gate().preview("Hello", source.revision());
    }

    @Test
    public void aDownloadThatFailsEndsTheAddingLine() throws Exception {
        setUp();
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_skill", addSkill("https://github.com/owner/missing", null))
                        .answer("Done.");
        run("Add those skills", model);
        assertTrue(toolResult(model, 1, 0).contains("owner/missing"));
        assertEquals(
                "Could not add skills from github.com/owner/missing", shown().get(0).getText());
        assertEquals(StepKind.FAILED, shown().get(0).getKind());
        for (Step step : shown()) assertFalse(step.getText().startsWith("Adding"));
    }

    @Test
    public void withoutANameEverySkillAtTheLinkIsAddedButAPersonOnlyOneStaysTheirs()
            throws Exception {
        setUp();
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_skill", addSkill(LINK, null))
                        .calls("read_skill", readSkill("interview", null))
                        .answer("Done.");
        run("Add the skills at " + LINK, model);
        String result = toolResult(model, 1, 0);
        assertTrue(result, result.contains("Added plan-grill: Grill a plan hard."));
        assertTrue(result, result.contains("begins a message with /interview"));
        assertTrue(result, result.contains("grilling is already on this phone"));
        String refused = toolResult(model, 2, 1);
        assertFalse(refused, refused.contains("INSTRUCTIONS"));
        assertTrue(added("plan-grill").on && added("interview").on);
        assertEquals("Added 2 skills", shown().get(0).getText());
        assertEquals("interview, plan-grill", shown().get(0).getDetail());
    }

    @Test
    public void aNameThatIsNotAtTheLinkAddsNothingAndSaysWhatIsThere() throws Exception {
        setUp();
        ScriptedModel model =
                new ScriptedModel().calls("add_skill", addSkill(LINK, "tdd-pro")).answer("Done.");
        run("Add tdd-pro", model);
        String result = toolResult(model, 1, 0);
        assertTrue(result, result.contains("no skill named tdd-pro"));
        assertTrue(result, result.contains("interview, plan-grill"));
        assertEquals(null, added("plan-grill"));
        assertEquals(StepKind.FAILED, shown().get(0).getKind());
    }

    @Test
    public void aLinkThatIsNotGitHubDownloadsNothing() throws Exception {
        setUp();
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_skill", addSkill("https://example.com/skills", null))
                        .answer("Done.");
        run("Add the skills at example.com", model);
        assertTrue(toolResult(model, 1, 0).contains("GitHub link"));
        assertTrue(github.requests().isEmpty());
        assertEquals("Could not add skills from example.com/skills", shown().get(0).getText());
        assertEquals(StepKind.FAILED, shown().get(0).getKind());
    }

    @Test
    public void whenTheyAskFirstTheBuilderAsksBeforeDownloadingAndANoAddsNothing()
            throws Exception {
        setUp();
        List<String> asked = new ArrayList<>();
        ask =
                (question, detail) -> {
                    asked.add(question + " | " + detail);
                    return false;
                };
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_skill", addSkill(LINK, "plan-grill"))
                        .answer("Done.");
        run("Add plan-grill", model);
        assertEquals(List.of("Add the skill plan-grill? | github.com/owner/repo"), asked);
        assertTrue(toolResult(model, 1, 0).contains("chose not to add"));
        assertTrue(github.requests().isEmpty());
        assertEquals(null, added("plan-grill"));
        assertEquals("Did not add the skill plan-grill", shown().get(0).getText());

        ask = (question, detail) -> true;
        ScriptedModel yes =
                new ScriptedModel()
                        .calls("add_skill", addSkill(LINK, "plan-grill"))
                        .answer("Done.");
        run("Add plan-grill", yes);
        assertTrue(added("plan-grill").on);
    }

    @Test
    public void aPersonOnlySkillAddedForTheirSlashNameIsFollowed() throws Exception {
        setUp();
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_skill", addSkill(LINK, "interview"))
                        .calls("read_skill", readSkill("interview", null))
                        .answer("Done.");
        run("/interview me about my app", model);
        String first = model.messages(0).get(0).getAsJsonObject().get("content").getAsString();
        assertTrue(first, first.contains("skill_wanted"));
        assertTrue(toolResult(model, 2, 1).contains("INSTRUCTIONS OF interview"));
    }

    @Test
    public void aSlashNameRunShowsTheSkillInUse() throws Exception {
        setUp();
        ScriptedModel model = new ScriptedModel().answer("What should it track?");
        run("/grill-me a habit tracker", model);
        assertEquals("Using skill grill-me", shown().get(0).getText());
        String first = model.messages(0).get(0).getAsJsonObject().get("content").getAsString();
        assertTrue(first.contains("INSTRUCTIONS OF grill-me"));
    }

    @Test
    public void readSkillReturnsTheSkillAndItsOtherFiles() throws Exception {
        setUp();
        ScriptedModel model =
                new ScriptedModel()
                        .calls(
                                "read_skill", readSkill("tdd", null),
                                "read_skill", readSkill("tdd", "tests.md"))
                        .answer("Done.");
        run("Build it test first", model);
        String skill = toolResult(model, 1, 0);
        assertTrue(skill, skill.contains("INSTRUCTIONS OF tdd"));
        assertTrue(skill, skill.contains("references/mocking.md"));
        assertTrue(skill, skill.contains("tests.md"));
        assertTrue(toolResult(model, 1, 1).contains("Good tests."));
        assertEquals("Read skill tdd", shown().get(0).getText());
        assertEquals(StepKind.DONE, shown().get(0).getKind());
        assertEquals("Read skill tdd, tests.md", shown().get(1).getText());
    }

    @Test
    public void readSkillRefusesUnknownSkillsAndPathsOutsideTheSkill() throws Exception {
        setUp();
        ScriptedModel model =
                new ScriptedModel()
                        .calls(
                                "read_skill", readSkill("nope", null),
                                "read_skill", readSkill("deploy", null),
                                "read_skill", readSkill("grill-me", null),
                                "read_skill", readSkill("tdd", "../grilling/SKILL.md"),
                                "read_skill", readSkill("tdd", "/etc/passwd"),
                                "read_skill", readSkill("tdd", "scripts/run.sh"))
                        .answer("Done.");
        run("Hello", model);
        assertTrue(toolResult(model, 1, 0).contains("no skill named nope"));
        assertTrue(toolResult(model, 1, 1).contains("no skill named deploy"));
        assertTrue(toolResult(model, 1, 2).contains("/grill-me"));
        for (int call = 3; call < 6; call++) {
            String refused = toolResult(model, 1, call);
            assertTrue(refused, refused.contains("tdd has no file"));
            assertFalse(refused, refused.contains("INSTRUCTIONS"));
        }
        for (Step step : shown().subList(0, 6)) assertEquals(StepKind.FAILED, step.getKind());
    }

    @Test
    public void readSkillUsesTheSkillsAsTheyWereAtTheTap() throws Exception {
        setUp();
        ScriptedModel model =
                new ScriptedModel()
                        .calls(
                                "read_skill",
                                readSkill("tdd", null),
                                "read_skill",
                                readSkill("grilling", null))
                        .answer("Done.");
        run(
                "Hello",
                model,
                () -> {
                    store.setOn("tdd", false);
                    store.remove("grilling");
                    store.add("https://github.com/o/r", List.of(skill("deploy", "New.", false)));
                });
        assertTrue(toolResult(model, 1, 0).contains("INSTRUCTIONS OF tdd"));
        assertTrue(toolResult(model, 1, 1).contains("removed, replaced or damaged"));
    }

    private void empty(String name) throws Exception {
        try (java.util.stream.Stream<java.nio.file.Path> files =
                Files.walk(skillsFolder.toPath())) {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files::iterator)
                if (file.endsWith("SKILL.md")
                        && new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                                .contains("INSTRUCTIONS OF " + name + "\n"))
                    Files.write(file, new byte[0]);
        }
    }

    @Test
    public void aDamagedSkillListSendsWithoutSkillsUnlessOneIsAskedFor() throws Exception {
        setUp();
        Files.write(
                new File(skillsFolder, "skills.json").toPath(),
                "{not json".getBytes(StandardCharsets.UTF_8));
        JsonObject first = payload(gate().preview("Make it", source.revision()).payload());
        assertFalse(first.has("skills"));
        assertFalse(first.has("skills_note"));
        RunSkills.Refused refused =
                assertThrows(
                        RunSkills.Refused.class,
                        () -> gate().preview("/grill-me a habit tracker", source.revision()));
        assertTrue(refused.getMessage(), refused.getMessage().contains("damaged"));
    }

    @Test
    public void anEmptiedSkillFileIsNeverSent() throws Exception {
        setUp();
        empty("grill-me");
        assertThrows(
                RunSkills.Refused.class,
                () -> gate().preview("/grill-me a habit tracker", source.revision()));
        ScriptedModel model =
                new ScriptedModel().calls("read_skill", readSkill("tdd", null)).answer("Done.");
        run("Hello", model, () -> empty("tdd"));
        String result = toolResult(model, 1, 0);
        assertFalse(result, result.contains("<skill_content"));
        assertEquals(StepKind.FAILED, shown().get(0).getKind());
    }

    @Test
    public void aPersonOnlySkillStartedEarlierInThisChatStaysReadable() throws Exception {
        setUp();
        ChatStore chats = new ChatStore(temporary.newFolder());
        ChatStore.Chat chat = chats.createChat(source.projectId(), "Habits");
        ChatStore.Chat other = chats.createChat(source.projectId(), "Other");
        chats.appendMessage(source.projectId(), chat.id, "user", "/grill-me a habit tracker", 0);
        chats.appendMessage(source.projectId(), chat.id, "assistant", "Q1: daily?", 0);
        ContextPreviewGate gate = new ContextPreviewGate(source, chats, source.projectId(), store);

        ContextPreviewGate.Preview preview =
                gate.previewChat(chat.id, "Yes, daily", source.revision());
        JsonObject first = payload(preview.payload());
        assertTrue(first.get("skills").toString().contains("\"name\":\"grill-me\""));
        assertFalse(first.has("skill_in_use"));
        String approved = gate.approve(preview, "Yes, daily", source.revision());
        assertEquals(preview.payload(), approved);
        assertFalse(
                payload(gate.previewChat(other.id, "Hi", source.revision()).payload())
                        .get("skills")
                        .toString()
                        .contains("grill-me"));

        ScriptedModel model =
                new ScriptedModel()
                        .calls("read_skill", readSkill("grill-me", null))
                        .answer("Q2: reminders?");
        start(preview, approved, model);
        assertTrue(toolResult(model, 1, 0).contains("INSTRUCTIONS OF grill-me"));
        assertEquals("Read skill grill-me", shown().get(0).getText());
    }

    @Test
    public void tooManySkillDescriptionsAreRefusedBeforeSending() throws Exception {
        setUp();
        List<GitHubSkills.Found> many = new ArrayList<>();
        for (int index = 0; index < 17; index++)
            many.add(skill("many-" + index, "d".repeat(1000), false));
        store.add("https://github.com/o/r", many);
        RunSkills.Refused refused =
                assertThrows(
                        RunSkills.Refused.class, () -> gate().preview("Hi", source.revision()));
        assertTrue(refused.getMessage(), refused.getMessage().contains("Too many skills are on"));
        store.setOn("many-0", false);
        assertTrue(payload(gate().preview("Hi", source.revision()).payload()).has("skills"));
    }
}
