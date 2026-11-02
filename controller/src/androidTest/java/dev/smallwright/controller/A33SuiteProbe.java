package dev.smallwright.controller;

import static dev.smallwright.controller.PhoneAlphaCounterProbe.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.PowerManager;
import android.os.SystemClock;
import dev.smallwright.agent.ModelChoice;
import dev.smallwright.agent.QuestionRound;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.Picker;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.controller.ui.UiState;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * One fixed supported request per run, driven only through the normal controller UI. A run either
 * starts from a fresh project's pristine default source (built once with the normal Build action)
 * or continues the project of an earlier accepted run. Each send needs the exact previewed context
 * hash; the run then works until the model is done. Only free models and the paid model the user
 * approved for step-run tests are sent. A run that ends with a round of questions is answered with
 * each question's recommended choice through Send answers, as a person accepting them would. A
 * verified app that failed its functional checklist may get up to three observed-feedback messages
 * through the same Send.
 */
final class A33SuiteProbe {
    private static final String APPROVED_PAID_MODEL = "deepseek/deepseek-v4.1-flash";
    private static final String SEND_ANSWERS = "Send answers";
    private static final List<String> PHASES =
            List.of(
                    "prepare",
                    "restoreAccepted",
                    "baselineBuild",
                    "pinBaseline",
                    "preview",
                    "send",
                    "feedbackPreview",
                    "feedbackSend",
                    "answersPreview",
                    "answersSend",
                    "recoverOutcome",
                    "outcome",
                    "accept",
                    "restoreSelection",
                    "verifySelection",
                    "endRepair");
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String run(
            Instrumentation in,
            String phase,
            String promptId,
            String runId,
            String continueRun,
            boolean freeApproved,
            String approvedHash,
            String checklistRecord,
            String observation,
            String baselineFixture,
            String approvedModel,
            String recoverRun)
            throws Exception {
        stage = "GUARD";
        if (!PHASES.contains(phase)
                || promptId == null
                || runId == null
                || !runId.matches("(a33|s23)_[a-z0-9]+_2026[0-9]{4}_[0-9]{2}"))
            throw new AssertionError("Fixed suite policy");
        JSONObject contract = frozenRequest(in, promptId);
        String request = contract.getString("request");
        Context target = in.getTargetContext();
        if (target.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !target.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Unlock required");
        SharedPreferences pins =
                target.getSharedPreferences("a33-suite-" + runId, Context.MODE_PRIVATE);
        boolean continuing = continueRun != null && !continueRun.isEmpty();
        boolean restoring = "restoreAccepted".equals(phase);
        if (restoring && !continuing) throw new AssertionError("Restore needs an accepted run");
        boolean preparing = restoring || "prepare".equals(phase);
        if (preparing) {
            if (pins.contains("reserved")) throw new AssertionError("One-shot run reserved");
            save(pins.edit().putBoolean("reserved", true).putString("promptId", promptId));
        } else {
            if (!pins.contains("project") || !promptId.equals(pins.getString("promptId", "")))
                throw new AssertionError("Owned case missing");
            continuing = !pins.getString("continueRun", "").isEmpty();
        }
        if (phase.endsWith("Send") || "send".equals(phase))
            if (!freeApproved
                    || approvedHash == null
                    || !approvedHash.equals(pins.getString("contextSha256", "")))
                throw new AssertionError("Exact approval required");
        if ("restoreSelection".equals(phase)) {
            save(
                    target.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                            .edit()
                            .putString("active-project", pins.getString("previousProject", "")));
            return marker(phase, new JSONObject().put("restored", true));
        }
        if (preparing) {
            SharedPreferences selection =
                    target.getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
            save(
                    pins.edit()
                            .putString(
                                    "previousProject", selection.getString("active-project", "")));
            if (continuing) {
                SharedPreferences prior =
                        target.getSharedPreferences(
                                "a33-suite-" + continueRun, Context.MODE_PRIVATE);
                if (!prior.getBoolean("accepted", false))
                    throw new AssertionError("Continued run was not accepted");
                save(selection.edit().putString("active-project", prior.getString("project", "")));
            }
        }
        Activity ui =
                in.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            stage = "OPEN";
            ControllerUi.awaitProjectBinding(in, ui);
            ((java.util.concurrent.ExecutorService) field(ui, "executor"))
                    .submit(() -> {})
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);
            ActiveWorkerStopProbe.awaitWorker(in, ui);
            if ("verifySelection".equals(phase)) {
                ProjectIdentity active =
                        mainValue(in, () -> (ProjectIdentity) field(ui, "activeProject"));
                if (!active.id().equals(pins.getString("previousProject", "")))
                    throw new AssertionError("Prior project not restored");
                return marker(phase, new JSONObject().put("restored", true));
            }
            if ("endRepair".equals(phase)) {
                stage = "END_REPAIR";
                ProjectIdentity own = mainValue(in, () -> requireOwn(ui, pins));
                String pending =
                        new JSONObject(pins.getString("outcome", "{}")).getString("productionRun");
                if (new RunJournal(target, own.id()).load(pending).state
                        != RunJournal.State.REPAIR_NEEDED)
                    throw new AssertionError("No pending repair to end");
                String previous = pins.getString("previousProject", "");
                onMain(
                        in,
                        () -> {
                            @SuppressWarnings("unchecked")
                            List<ProjectIdentity> listed =
                                    (List<ProjectIdentity>) field(ui, "projectList");
                            for (int i = 0; i < listed.size(); i++)
                                if (listed.get(i).id().equals(previous))
                                    ((Picker) field(ui, "projectPicker")).select(i);
                        });
                click(in, target, "End repair and switch");
                stage = "END_REPAIR_VERIFY";
                long deadline = SystemClock.uptimeMillis() + 30_000;
                while (!previous.equals(
                        mainValue(in, () -> ((ProjectIdentity) field(ui, "activeProject")).id()))) {
                    if (SystemClock.uptimeMillis() > deadline)
                        throw new AssertionError("Prior project not selected");
                    SystemClock.sleep(200);
                }
                if (new RunJournal(target, own.id()).hasActiveAttempt())
                    throw new AssertionError("Repair still pending");
                return marker(
                        phase, new JSONObject().put("ended", pending).put("restored", previous));
            }
            if (preparing) {
                stage = "PREPARE";
                if (!continuing)
                    CompilerMeasurementProbe.createProject(
                            in, ui, "Alpha " + promptId + " " + runId);
                ProjectIdentity project =
                        mainValue(in, () -> (ProjectIdentity) field(ui, "activeProject"));
                ChatStore.Chat chat =
                        mainValue(in, () -> (ChatStore.Chat) field(ui, "selectedChat"));
                ProjectSourceStore source = new ProjectSourceStore(project);
                if (!continuing
                        && (source.revision() != 0
                                || !source.isPristineDefaultSnapshot()
                                || new RunJournal(target, project.id()).hasAnyAttempt()))
                    throw new AssertionError("Untouched baseline required");
                save(
                        pins.edit()
                                .putString("project", project.id())
                                .putString("package", project.packageName())
                                .putString("title", project.title())
                                .putString("chat", chat.id)
                                .putString("continueRun", continuing ? continueRun : "")
                                .putString(
                                        "requestSha256",
                                        sha256(request.getBytes(StandardCharsets.UTF_8))));
                JSONObject result =
                        new JSONObject()
                                .put("runId", runId)
                                .put("promptId", promptId)
                                .put("project", project.id())
                                .put("package", project.packageName())
                                .put("chat", chat.id)
                                .put("continueRun", continuing ? continueRun : JSONObject.NULL)
                                .put("sourceRevision", source.revision())
                                .put("requestSha256", pins.getString("requestSha256", ""));
                if (restoring)
                    result.put(
                            "restored",
                            restoreAccepted(
                                    in,
                                    ui,
                                    target,
                                    project,
                                    source,
                                    target.getSharedPreferences(
                                            "a33-suite-" + continueRun, Context.MODE_PRIVATE),
                                    pins));
                else if (continuing)
                    result.put("baseline", pinCurrentBaseline(target, project, source, pins));
                return marker(phase, result);
            }
            stage = "OWN_PROJECT";
            ProjectIdentity project = mainValue(in, () -> requireOwn(ui, pins));
            ProjectSourceStore source = new ProjectSourceStore(project);
            if ("baselineBuild".equals(phase)) {
                stage = "BASELINE_BUILD";
                if (continuing
                        || pins.getBoolean("baselineBuildAttempted", false)
                        || source.revision() != 0
                        || !source.isPristineDefaultSnapshot())
                    throw new AssertionError("Unused pristine baseline required");
                onMain(in, () -> requireIdle(ui));
                String authored = null;
                if (baselineFixture != null) {
                    if (!baselineFixture.matches("checklist|records")
                            || !promptId.startsWith(baselineFixture + "_"))
                        throw new AssertionError("Unknown baseline fixture");
                    try (java.io.InputStream asset =
                            in.getContext()
                                    .getAssets()
                                    .open(
                                            "suite-fixtures/"
                                                    + baselineFixture
                                                    + "-MainActivity.java")) {
                        authored =
                                new String(asset.readAllBytes(), StandardCharsets.UTF_8)
                                        .replace("$PACKAGE", project.packageName());
                    }
                    stageEditor(in, ui, authored);
                    save(
                            pins.edit()
                                    .putString(
                                            "fixtureSha256",
                                            sha256(authored.getBytes(StandardCharsets.UTF_8))));
                }
                save(pins.edit().putBoolean("baselineBuildAttempted", true));
                long clicked = buildLocally(in, ui);
                if (source.revision() != 1
                        || authored != null && !authored.equals(source.readSource())
                        || new RunJournal(target, project.id()).hasAnyAttempt())
                    throw new AssertionError(
                            "Normal baseline Build changed source or made attempt");
                SignedArtifactReceipt.Record receipt = verifiedRecord(target, project, 1);
                receipt.artifact.verifyUnchanged();
                ArtifactValidator.validateUnsigned(target, receipt.apk, 1, project.packageName());
                awaitReady(in, ui, receipt.sha256);
                long ready = SystemClock.elapsedRealtimeNanos();
                return marker(
                        phase,
                        new JSONObject()
                                .put("sourceRevision", 1)
                                .put(
                                        "baselineFixture",
                                        baselineFixture == null ? JSONObject.NULL : baselineFixture)
                                .put("fixtureSha256", pins.getString("fixtureSha256", ""))
                                .put("version", receipt.versionCode)
                                .put("apkPath", receipt.apk.getAbsolutePath())
                                .put("apkSha256", receipt.sha256)
                                .put("signerSha256", receipt.signerFingerprint)
                                .put(
                                        "buildClickToReadyMs",
                                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                                ready - clicked))
                                .put("modelRequestMade", false));
            }
            if ("pinBaseline".equals(phase)) {
                stage = "PIN_BASELINE";
                if (continuing && !pins.contains("restoredRevision")
                        || pins.contains("baselineApkSha256"))
                    throw new AssertionError("Pristine baseline already pinned");
                if (pins.contains("restoredRevision")
                        && (source.revision() != pins.getInt("restoredRevision", -1)
                                || !snapshotHash(source.readRecoverySources(source.revision()))
                                        .equals(pins.getString("restoredSnapshotSha256", ""))))
                    throw new AssertionError("Head moved after the restore");
                return marker(phase, pinCurrentBaseline(target, project, source, pins));
            }
            if ("outcome".equals(phase)) {
                if (!pins.contains("outcome")) throw new AssertionError("No settled outcome");
                return marker(phase, new JSONObject(pins.getString("outcome", "")));
            }
            if ("accept".equals(phase)) {
                stage = "ACCEPT";
                JSONObject outcome = new JSONObject(pins.getString("outcome", "{}"));
                if (!"verified".equals(outcome.optString("compileResult")))
                    throw new AssertionError("Only a verified outcome can be accepted");
                if (checklistRecord == null
                        || !checklistRecord.matches("docs/validation/[a-z0-9-]+\\.md"))
                    throw new AssertionError("Acceptance needs the functional checklist record");
                SignedArtifactReceipt.Record receipt =
                        verifiedRecord(target, project, source.revision());
                if (!receipt.sha256.equals(outcome.getString("apkSha256"))
                        || !receipt.sha256.equals(installedSha(target, project))
                        || installedVersion(target, project) != receipt.versionCode)
                    throw new AssertionError("Accepted APK is not the installed ready artifact");
                save(
                        pins.edit()
                                .putBoolean("accepted", true)
                                .putString("checklistRecord", checklistRecord));
                return marker(
                        phase,
                        new JSONObject()
                                .put("accepted", true)
                                .put("operatorAttestedChecklist", checklistRecord)
                                .put("apkSha256", receipt.sha256)
                                .put("sourceRevision", source.revision()));
            }
            stage = "EXACT_BASELINE";
            if (!pins.contains("baselineApkSha256"))
                throw new AssertionError("Baseline not pinned");
            boolean feedback = phase.startsWith("feedback");
            boolean answering = phase.startsWith("answers");
            RunJournal journal = new RunJournal(target, project.id());
            if ("recoverOutcome".equals(phase)) {
                stage = "RECOVER_OUTCOME";
                List<String> runsBefore =
                        new ArrayList<>(
                                java.util.Arrays.asList(
                                        pins.getString("initialRunsBefore", "").split(",")));
                if (!pins.getBoolean("attempted", false)
                        || pins.contains("outcome")
                        || recoverRun == null
                        || !recoverRun.equals(newRun(target, project.id(), runsBefore))
                        || !pins.getString("initialContextSha256", "").equals(approvedHash))
                    throw new AssertionError("Only a lost initial outcome can be recovered");
                ((java.util.concurrent.ExecutorService) field(ui, "executor"))
                        .submit(() -> {})
                        .get(30, java.util.concurrent.TimeUnit.SECONDS);
                return marker(
                        phase,
                        settledOutcome(
                                in,
                                ui,
                                pins,
                                project,
                                source,
                                journal,
                                runId,
                                promptId,
                                recoverRun,
                                "initial",
                                approvedHash,
                                pins.getInt("baselineRevision", -1),
                                -1,
                                new JSONArray()));
            }
            if (answering) {
                if (!pins.contains("outcome")
                        || pins.getBoolean("accepted", false)
                        || !"ANSWERED"
                                .equals(
                                        new JSONObject(pins.getString("outcome", ""))
                                                .getString("journalState")))
                    throw new AssertionError("Answers need a run that ended with questions");
            } else if (feedback) {
                if (!pins.contains("outcome")
                        || verifiedRecord(target, project, source.revision()).versionCode
                                <= pins.getInt("baselineVersion", Integer.MAX_VALUE)
                        || pins.getBoolean("accepted", false)
                        || pins.getInt("feedbackSends", 0) >= 3
                        || observation == null
                        || observation.trim().isEmpty())
                    throw new AssertionError("Feedback needs a tested, unaccepted ready app");
                if (!verifiedRecord(target, project, source.revision())
                        .sha256
                        .equals(installedSha(target, project)))
                    throw new AssertionError("Feedback must describe the installed ready app");
            } else if (pins.getBoolean("attempted", false))
                throw new AssertionError("Initial request already sent");
            int revision = source.revision();
            String answers =
                    answering
                            ? mainValue(
                                    in,
                                    () ->
                                            recommendedAnswers(
                                                    (ChatStore) field(ui, "chatStore"),
                                                    project.id(),
                                                    pins.getString("chat", "")))
                            : null;
            ContextPreviewGate.Preview preview =
                    mainValue(
                            in,
                            () -> {
                                requireIdle(ui);
                                if ((int) field(ui, "editorRevision") != revision
                                        || !source.readSource()
                                                .contentEquals(
                                                        ((TextBox) field(ui, "source")).getText()))
                                    throw new AssertionError("Source UI changed");
                                if (feedback
                                        && (field(ui, "selectedCrashReport") != null
                                                || field(ui, "selectedScreenshot") != null))
                                    throw new AssertionError("Observation-only feedback required");
                                ContextPreviewGate gate =
                                        (ContextPreviewGate) field(ui, "contextGate");
                                String message =
                                        answering ? answers : feedback ? observation : request;
                                ((TextBox) field(ui, "modelRequest"))
                                        .setText(answering ? "" : message);
                                return gate.previewChat(
                                        pins.getString("chat", ""), message, revision);
                            });
            String hash = sha256(preview.payload().getBytes(StandardCharsets.UTF_8));
            ModelChoice picked = mainValue(in, () -> ((UiState) field(ui, "ui")).getModel());
            if (!picked.isFree()
                    && !(APPROVED_PAID_MODEL.equals(picked.id) && picked.id.equals(approvedModel)))
                throw new AssertionError("Picked model is not free or the approved paid model");
            if (phase.endsWith("Send") || "send".equals(phase)) {
                if (!hash.equals(approvedHash))
                    throw new AssertionError("Approved context changed");
            }
            String action = Labels.GENERATE;
            if (phase.endsWith("review") || "preview".equals(phase)) {
                save(pins.edit().putString("contextSha256", hash));
                return marker(
                        phase,
                        new JSONObject()
                                .put("contextSha256", hash)
                                .put(
                                        "contextBytes",
                                        preview.payload().getBytes(StandardCharsets.UTF_8).length)
                                .put("sourceRevision", revision)
                                .put("numericDiagnostics", numericDiagnostics(preview.payload()))
                                .put("model", picked.id)
                                .put("answers", answering ? answers : JSONObject.NULL)
                                .put("modelRequestMade", false));
            }
            stage = answering ? "ANSWERS_SEND_ONCE" : feedback ? "FEEDBACK_SEND_ONCE" : "SEND_ONCE";
            List<String> before = runIds(target, project.id());
            long started = SystemClock.elapsedRealtimeNanos();
            if (answering)
                save(pins.edit().putInt("answerSends", pins.getInt("answerSends", 0) + 1));
            else if (feedback)
                save(
                        pins.edit()
                                .putInt("feedbackSends", pins.getInt("feedbackSends", 0) + 1)
                                .putString(
                                        "lastRequestSha256",
                                        sha256(observation.getBytes(StandardCharsets.UTF_8))));
            else
                save(
                        pins.edit()
                                .putBoolean("attempted", true)
                                .putString("initialRunsBefore", String.join(",", before))
                                .putString("initialContextSha256", hash));
            save(pins.edit().remove("contextSha256"));
            String priorApproval =
                    mainValue(
                            in,
                            () -> {
                                Ui.setField(ui, "approvedContextSha256", null);
                                return null;
                            });
            Ui.tap(in, target, answering ? SEND_ANSWERS : action);
            long approvalDeadline = SystemClock.elapsedRealtime() + 5000;
            String sentApproval = priorApproval;
            while (java.util.Objects.equals(sentApproval, priorApproval)
                    && SystemClock.elapsedRealtime() < approvalDeadline) {
                Thread.sleep(25);
                sentApproval = mainValue(in, () -> (String) field(ui, "approvedContextSha256"));
            }
            if (!hash.equals(sentApproval))
                throw new AssertionError("Sent context differs from the previewed context");
            JSONArray states = new JSONArray();
            String productionRun = null;
            String priorState = null;
            long nextPending = SystemClock.elapsedRealtime() + 30000;
            while (true) {
                if (productionRun == null) productionRun = newRun(target, project.id(), before);
                if (productionRun != null) {
                    RunJournal.Snapshot snapshot = journal.load(productionRun);
                    if (!snapshot.state.name().equals(priorState) && states.length() < 32) {
                        priorState = snapshot.state.name();
                        states.put(
                                new JSONObject()
                                        .put("state", priorState)
                                        .put(
                                                "elapsedMs",
                                                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                                        SystemClock.elapsedRealtimeNanos()
                                                                - started)));
                    }
                }
                if (!mainValue(in, () -> busy(ui))) break;
                if (SystemClock.elapsedRealtime() >= nextPending) {
                    android.os.Bundle pending = new android.os.Bundle();
                    pending.putString("stream", "A33 suite request pending; no retry/kill\n");
                    in.sendStatus(0, pending);
                    nextPending += 30000;
                }
                Thread.sleep(25);
            }
            ((java.util.concurrent.ExecutorService) field(ui, "executor"))
                    .submit(() -> {})
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);
            stage = "VERIFY_SETTLED_OUTCOME";
            if (productionRun == null) productionRun = newRun(target, project.id(), before);
            return marker(
                    phase,
                    settledOutcome(
                            in,
                            ui,
                            pins,
                            project,
                            source,
                            journal,
                            runId,
                            promptId,
                            productionRun,
                            answering ? "answers" : feedback ? "feedback" : "initial",
                            hash,
                            revision,
                            started,
                            states));
        } finally {
            while (!awaitIdle(in, ui, 30)) {
                android.os.Bundle pending = new android.os.Bundle();
                pending.putString("stream", "A33 suite pending before finish; no retry/kill\n");
                in.sendStatus(0, pending);
            }
            onMain(in, ui::finish);
        }
    }

    private static JSONObject frozenRequest(Instrumentation in, String promptId) throws Exception {
        String prompts;
        String asset =
                promptId.startsWith("bakeoff_") ? "alpha/bakeoff.jsonl" : "alpha/prompts.jsonl";
        try (java.io.InputStream stream = in.getContext().getAssets().open(asset)) {
            prompts = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String line : prompts.split("\n")) {
            if (line.isEmpty()) continue;
            JSONObject entry = new JSONObject(line);
            if (promptId.equals(entry.getString("id"))) {
                if (!"supported".equals(entry.getString("kind")))
                    throw new AssertionError("Unsupported fixed request");
                return entry;
            }
        }
        throw new AssertionError("Unknown fixed request");
    }

    /** The open round's recommended choices, as Send answers words them with an empty prompt. */
    private static String recommendedAnswers(ChatStore chats, String project, String chat)
            throws Exception {
        List<ChatStore.Message> messages = chats.listMessages(project, chat);
        int asked = -1;
        int said = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (ChatStore.QUESTION.equals(messages.get(i).role)) asked = i;
            if ("user".equals(messages.get(i).role)) said = i;
        }
        if (asked < 0 || asked < said) throw new AssertionError("No open question round");
        QuestionRound round = QuestionRound.fromSaved(messages.get(asked).text);
        List<String> picks = new ArrayList<>();
        for (QuestionRound.Question question : round.questions) picks.add(question.choices.get(0));
        return round.answers(picks, "");
    }

    private static void stageEditor(Instrumentation in, Activity ui, String text) throws Exception {
        onMain(in, () -> ((TextBox) field(ui, "source")).setText(text));
        if (!text.equals(mainValue(in, () -> ((TextBox) field(ui, "source")).getText().toString())))
            throw new AssertionError("Source was not staged in editor");
    }

    private static long buildLocally(Instrumentation in, Activity ui) throws Exception {
        Ui.showTools(in, ui, ui);
        long clicked = SystemClock.elapsedRealtimeNanos();
        Ui.tap(in, ui, Labels.BUILD_SOURCE);
        while (mainValue(in, () -> busy(ui))) Thread.sleep(25);
        return clicked;
    }

    /**
     * Rebuilds an accepted run's exact source snapshot as a new head through the editor and the
     * normal Build button, so a later failed attempt cannot become the next baseline.
     */
    private static JSONObject restoreAccepted(
            Instrumentation in,
            Activity ui,
            Context target,
            ProjectIdentity project,
            ProjectSourceStore source,
            SharedPreferences prior,
            SharedPreferences pins)
            throws Exception {
        JSONObject accepted = new JSONObject(prior.getString("outcome", "{}"));
        int acceptedRevision = accepted.getInt("sourceRevisionAfter");
        int head = source.revision();
        if (!project.id().equals(prior.getString("project", ""))
                || head <= acceptedRevision
                || new RunJournal(target, project.id()).hasActiveAttempt())
            throw new AssertionError("Restore needs an idle head after the accepted revision");
        java.util.Map<String, byte[]> wanted = new java.util.TreeMap<>();
        java.nio.file.Path root =
                new File(project.directory(), "revision-store/revisions/" + acceptedRevision)
                        .toPath();
        try (java.util.stream.Stream<java.nio.file.Path> files = Files.walk(root)) {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files::iterator)
                if (Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    wanted.put(root.relativize(file).toString(), Files.readAllBytes(file));
        }
        String mainPath = "java/" + project.packageName().replace('.', '/') + "/MainActivity.java";
        String wantedSnapshot = snapshotHash(wanted);
        if (!wantedSnapshot.equals(accepted.getString("finalSnapshotSha256")))
            throw new AssertionError("Accepted revision files changed");
        java.util.Map<String, byte[]> otherWanted = new java.util.TreeMap<>(wanted);
        java.util.Map<String, byte[]> otherHead =
                new java.util.TreeMap<>(source.readRecoverySources(head));
        otherWanted.remove(mainPath);
        otherHead.remove(mainPath);
        if (!snapshotHash(otherWanted).equals(snapshotHash(otherHead)))
            throw new AssertionError("Restore can only replace MainActivity.java");
        onMain(in, () -> requireIdle(ui));
        stageEditor(in, ui, new String(wanted.get(mainPath), StandardCharsets.UTF_8));
        buildLocally(in, ui);
        if (source.revision() != head + 1
                || !wantedSnapshot.equals(snapshotHash(source.readRecoverySources(head + 1))))
            throw new AssertionError("Build did not reproduce the accepted snapshot");
        SignedArtifactReceipt.Record receipt = verifiedRecord(target, project, head + 1);
        receipt.artifact.verifyUnchanged();
        ArtifactValidator.validateUnsigned(
                target, receipt.apk, receipt.versionCode, project.packageName());
        awaitReady(in, ui, receipt.sha256);
        save(
                pins.edit()
                        .putInt("restoredRevision", head + 1)
                        .putString("restoredSnapshotSha256", wantedSnapshot));
        return new JSONObject()
                .put("restoredFromRevision", acceptedRevision)
                .put("abandonedHeadRevision", head)
                .put("sourceRevision", head + 1)
                .put("snapshotSha256", wantedSnapshot)
                .put("version", receipt.versionCode)
                .put("apkPath", receipt.apk.getAbsolutePath())
                .put("apkSha256", receipt.sha256)
                .put("signerSha256", receipt.signerFingerprint)
                .put("modelRequestMade", false);
    }

    private static JSONObject pinCurrentBaseline(
            Context target,
            ProjectIdentity project,
            ProjectSourceStore source,
            SharedPreferences pins)
            throws Exception {
        if (source.revision() < 1) throw new AssertionError("Baseline needs a built revision");
        SignedArtifactReceipt.Record receipt = verifiedRecord(target, project, source.revision());
        receipt.artifact.verifyUnchanged();
        ArtifactValidator.validateUnsigned(
                target, receipt.apk, receipt.versionCode, project.packageName());
        if (!receipt.sha256.equals(installedSha(target, project))
                || installedVersion(target, project) != receipt.versionCode)
            throw new AssertionError("Installed baseline differs from the current receipt");
        String snapshot = snapshotHash(source.readRecoverySources(source.revision()));
        save(
                pins.edit()
                        .putInt("baselineRevision", source.revision())
                        .putInt("baselineVersion", receipt.versionCode)
                        .putString("baselineApkSha256", receipt.sha256)
                        .putString("baselineSignerSha256", receipt.signerFingerprint)
                        .putString("baselineSnapshotSha256", snapshot));
        return new JSONObject()
                .put("baselineRevision", source.revision())
                .put("baselineVersion", receipt.versionCode)
                .put("baselineApkSha256", receipt.sha256)
                .put("baselineSignerSha256", receipt.signerFingerprint)
                .put("baselineSnapshotSha256", snapshot)
                .put("installedBytesVerified", true);
    }

    private static String installedSha(Context target, ProjectIdentity project) throws Exception {
        String path =
                target.getPackageManager().getApplicationInfo(project.packageName(), 0).sourceDir;
        return sha256(Files.readAllBytes(new File(path).toPath()));
    }

    private static long installedVersion(Context target, ProjectIdentity project) throws Exception {
        return target.getPackageManager()
                .getPackageInfo(project.packageName(), 0)
                .getLongVersionCode();
    }

    private static JSONObject settledOutcome(
            Instrumentation in,
            Activity ui,
            SharedPreferences pins,
            ProjectIdentity project,
            ProjectSourceStore source,
            RunJournal journal,
            String runId,
            String promptId,
            String productionRun,
            String kind,
            String hash,
            int revision,
            long started,
            JSONArray states)
            throws Exception {
        Context target = in.getTargetContext();
        if (productionRun == null) throw new AssertionError("Production attempt missing");
        RunJournal.Snapshot run = journal.load(productionRun);
        if (run.isActive() && run.state != RunJournal.State.REPAIR_NEEDED)
            throw new AssertionError("Run not settled");
        String compile =
                run.state == RunJournal.State.READY_TO_TEST
                        ? "verified"
                        : run.state == RunJournal.State.REPAIR_NEEDED
                                ? "compile_error"
                                : run.state == RunJournal.State.ANSWERED
                                        ? "answered"
                                        : run.state == RunJournal.State.CANCELLED
                                                ? "cancelled"
                                                : "failed";
        JSONArray sends =
                pins.contains("outcome")
                        ? new JSONObject(pins.getString("outcome", "")).getJSONArray("sends")
                        : new JSONArray();
        sends.put(
                new JSONObject()
                        .put("kind", kind)
                        .put("contextSha256", hash)
                        .put("sourceRevisionBefore", revision)
                        .put("sourceRevisionAfter", whenFree(source::revision))
                        .put("journalState", run.state.name())
                        .put(
                                "observedSendToSettledMs",
                                started < 0
                                        ? JSONObject.NULL
                                        : java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                                SystemClock.elapsedRealtimeNanos() - started))
                        .put("states", states));
        JSONObject result =
                new JSONObject()
                        .put("runId", runId)
                        .put("promptId", promptId)
                        .put("project", project.id())
                        .put("package", project.packageName())
                        .put("productionRun", productionRun)
                        .put("baselineRevision", pins.getInt("baselineRevision", -1))
                        .put("baselineVersion", pins.getInt("baselineVersion", -1))
                        .put("baselineApkSha256", pins.getString("baselineApkSha256", ""))
                        .put("sourceRevisionAfter", whenFree(source::revision))
                        .put(
                                "finalSnapshotSha256",
                                snapshotHash(
                                        whenFree(
                                                () ->
                                                        source.readRecoverySources(
                                                                whenFree(source::revision)))))
                        .put(
                                "model",
                                mainValue(in, () -> ((UiState) field(ui, "ui")).getModel().id))
                        .put("requestSha256", pins.getString("requestSha256", ""))
                        .put("journalState", run.state.name())
                        .put("journalSequence", run.sequence)
                        .put("repairs", run.repairs)
                        .put(
                                "providerReportedTokens",
                                run.reportedTokens == null ? JSONObject.NULL : run.reportedTokens)
                        .put("compileResult", compile)
                        .put("sends", sends)
                        .put("developerSourceEdits", false);
        if ("verified".equals(compile)) {
            SignedArtifactReceipt.Record receipt =
                    whenFree(() -> verifiedRecord(target, project, whenFree(source::revision)));
            receipt.artifact.verifyUnchanged();
            ArtifactValidator.validateUnsigned(
                    target, receipt.apk, receipt.versionCode, project.packageName());
            if (!receipt.signerFingerprint.equals(pins.getString("baselineSignerSha256", ""))
                    || receipt.versionCode <= pins.getInt("baselineVersion", -1))
                throw new AssertionError("Ready identity changed");
            awaitReady(in, ui, receipt.sha256);
            PackageInfo info =
                    target.getPackageManager()
                            .getPackageArchiveInfo(
                                    receipt.apk.getAbsolutePath(), PackageManager.GET_PERMISSIONS);
            JSONArray permissions = new JSONArray();
            if (info != null && info.requestedPermissions != null)
                for (String permission : info.requestedPermissions) permissions.put(permission);
            result.put("version", receipt.versionCode)
                    .put("apkPath", receipt.apk.getAbsolutePath())
                    .put("apkSha256", receipt.sha256)
                    .put("apkBytes", receipt.apk.length())
                    .put("signerSha256", receipt.signerFingerprint)
                    .put("permissions", permissions);
        } else {
            String status =
                    mainValue(in, () -> ((TextBox) field(ui, "status")).getText().toString());
            result.put("failureStatus", status);
            result.put(
                    "outcomeCategory",
                    "compile_error".equals(compile) || status.contains("could not fix the build")
                            ? "java_compile"
                            : status.contains("Model request timed out")
                                    ? "model_timeout"
                                    : status.contains("OpenRouter rate limit")
                                            ? "model_rate_limit"
                                            : status.contains("Invalid model response")
                                                    ? "proposal_policy"
                                                    : "generation_failed");
        }
        save(pins.edit().putString("outcome", result.toString()));
        return result;
    }

    /**
     * Reads the project when the app is not holding it: the app takes the one project lock for
     * short reads at any time, and a read that finds it held is refused instead of waiting.
     */
    private static <T> T whenFree(java.util.concurrent.Callable<T> read) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 30_000;
        while (true) {
            try {
                return read.call();
            } catch (IllegalStateException busy) {
                if (!"Project is busy".equals(busy.getMessage())
                        || SystemClock.uptimeMillis() > deadline) throw busy;
                SystemClock.sleep(100);
            }
        }
    }

    private static List<String> runIds(Context target, String project) {
        List<String> ids = new ArrayList<>();
        File path = new File(target.getNoBackupFilesDir(), "run-journal.sqlite");
        if (!path.isFile()) return ids;
        try (SQLiteDatabase db =
                        SQLiteDatabase.openDatabase(
                                path.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                Cursor cursor =
                        db.rawQuery(
                                "SELECT DISTINCT run_id FROM attempt_events WHERE project_id=?",
                                new String[] {project})) {
            while (cursor.moveToNext()) ids.add(cursor.getString(0));
        }
        return ids;
    }

    private static String newRun(Context target, String project, List<String> before) {
        List<String> added = runIds(target, project);
        added.removeAll(before);
        if (added.size() > 1) throw new AssertionError("Ambiguous new production run");
        return added.isEmpty() ? null : added.get(0);
    }

    private static JSONArray numericDiagnostics(String payload) {
        JSONArray records = new JSONArray();
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("id=([0-9]+) category=([0-9]+) line=([0-9]+)")
                        .matcher(payload);
        while (matcher.find())
            records.put(matcher.group(1) + "/" + matcher.group(2) + "/" + matcher.group(3));
        return records;
    }

    private static String marker(String phase, JSONObject value) {
        return "A33 suite " + phase + " " + value + "\n";
    }

    private static void save(SharedPreferences.Editor value) {
        if (!value.commit()) throw new AssertionError("Pin persistence failed");
    }
}
