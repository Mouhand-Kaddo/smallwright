package dev.smallwright.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.smallwright.agent.ImageClient;
import dev.smallwright.agent.ModelClient;
import dev.smallwright.agent.QuestionRound;
import dev.smallwright.agent.RunPlan;
import dev.smallwright.agent.RunTools;
import dev.smallwright.agent.WebPages;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.libraries.LibraryList;
import dev.smallwright.project.ProjectStore;
import dev.smallwright.protocol.CompilerDiagnostics;
import dev.smallwright.security.ContextFilter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * One Send's run: a model request, then the tools it called, again and again until the model
 * answers with no tool call or the run is stopped. Edits go to a run-owned working copy at the
 * run's base revision; {@code build} and the end of the run commit them as one checked revision.
 */
final class StepRun {
    static final String PLATFORM_NEEDED =
            "Building needs the Android platform files from Google, and they are not on this phone"
                    + " yet. They are one 66 MB download. Tap \"Get Android platform files\" below"
                    + " (or find it in the menu), then send a message to build. The changes so far"
                    + " are saved.";

    static final String ASK_FAILED = "Could not ask the questions";

    private static final String PLATFORM_MISSING_RESULT =
            "Android platform files are not on this phone, so it cannot build yet. Your changes"
                    + " are saved. The run ends here and the person is asked to get them.";
    private static final String CUT_OFF_NOTE =
            "Your last reply was cut off at the model's output limit, so nothing from it was used:"
                    + " no tool ran and no file changed. It ran too long: long thinking, a long"
                    + " answer, or one tool call carrying too much, such as a whole big file. Think"
                    + " more briefly and make large changes in several smaller steps: split big"
                    + " files into more classes and files, or write a file and then extend it with"
                    + " edit_file.";
    private static final int MAX_CUT_OFFS_IN_A_ROW = 3;
    static final String CUT_OFF_STUCK =
            "The model's replies were cut off "
                    + MAX_CUT_OFFS_IN_A_ROW
                    + " times in a row at its own length limit, so nothing from them was used and"
                    + " the run ended. Try asking for a smaller change, or pick another model.";

    private static final int MAX_RESULT_BYTES = 64 * 1024;
    private static final int MAX_SEARCH_MATCHES = 100;
    private static final int MAX_FAILURE_DETAIL_BYTES = 2048;

    /** A short ownership claim orders Stop and completion without waiting on publication I/O. */
    static final class Control {
        enum StopOutcome {
            REQUESTED,
            ALREADY_FINISHED
        }

        private volatile boolean stopped;
        private volatile boolean finished;
        private volatile boolean completing;

        synchronized StopOutcome stop() {
            if (completing || finished) return StopOutcome.ALREADY_FINISHED;
            stopped = true;
            return StopOutcome.REQUESTED;
        }

        boolean isActive() {
            return !stopped;
        }

        boolean isFinished() {
            return finished;
        }

        /**
         * Publishes the verified app, then records readiness. Once completion owns the result, Stop
         * returns at once and leaves publication to settle; a crash leaves BUILDING for recovery.
         */
        void finish(
                BooleanSupplier active,
                int revision,
                VerifiedArtifact artifact,
                Publish publish,
                Journal journal)
                throws Exception {
            synchronized (this) {
                ensureActive(active);
                if (stopped) throw new IllegalStateException("Run stopped before completion");
                if (completing || finished)
                    throw new IllegalStateException("Completion already claimed");
                completing = true;
            }
            try {
                artifact.verifyUnchanged();
                publish.publish(revision, artifact);
                finished = true;
                journal.advance(RunJournal.State.READY_TO_TEST, revision, null, null);
            } finally {
                completing = false;
            }
        }
    }

    /** Persists the installable artifact receipt before the journal can claim readiness. */
    interface Publish {
        void publish(int revision, VerifiedArtifact artifact) throws Exception;
    }

    interface Journal {
        void advance(RunJournal.State state, int revision, String buildId, Integer reportedTokens);
    }

    /** The model picked at the tap. */
    interface Model {
        /**
         * One request with the conversation so far and the run's tools; {@code promptTokens} is a
         * cautious count of the tokens it holds.
         */
        ModelClient.StepReply next(JsonArray messages, int promptTokens) throws Exception;

        /** One request that offers no tools, to summarize the earlier steps. */
        ModelClient.StepReply summarize(JsonArray messages, int promptTokens) throws Exception;

        /** The model's context length, as its provider lists it. */
        int contextTokens();

        /** One image request for an app icon or a picture, with the person's key. */
        default ImageClient.Image image(String prompt, ImageClient.Shape shape) throws Exception {
            throw new IOException("Images cannot be made here");
        }

        /** One public web page, read so that Stop ends it. */
        default WebPages.Page webPage(String url) throws Exception {
            throw new IOException("Web pages cannot be read on this phone right now.");
        }
    }

    /** Builds a committed revision on the phone and returns the verified, published app. */
    interface Build {
        VerifiedArtifact build(int revision, String buildId) throws Exception;
    }

    /** The project moved past the run's revision; the run saves nothing more. */
    static final class StaleRevision extends IllegalStateException {
        StaleRevision() {
            super("Stale project revision");
        }
    }

    /** The phone has no Android platform files, so nothing can be built. */
    static final class PlatformFilesMissing extends IOException {
        PlatformFilesMissing() {
            super("Android platform files are not on this phone");
        }
    }

    static final String STORAGE_FULL_STEP = "Your phone's storage is full";

    /** The phone ran out of storage; the model cannot fix that, so the run ends. */
    static final class StorageFull extends IOException {
        StorageFull(Throwable cause) {
            super("No space left on this phone", cause);
        }

        static boolean causedBy(Throwable failure) {
            for (Throwable at = failure; at != null; at = at.getCause()) {
                if (at instanceof android.database.sqlite.SQLiteFullException) return true;
                String message = at.getMessage();
                if (message != null
                        && (message.contains("ENOSPC")
                                || message.contains("No space left on device"))) return true;
            }
            return false;
        }
    }

    /** What the run ended with. */
    static final class Outcome {
        /** The builder's reply for the chat. */
        final String reply;

        /** The project revision the run ended at. */
        final int revision;

        /** The app built from {@link #revision}, or null when the run ended without one. */
        final VerifiedArtifact artifact;

        final boolean platformNeeded;

        /** The run ended on a limit the person set, not because the model was done. */
        final boolean limitReached;

        /** The questions the run ended on, or null when it did not end on ask. */
        final QuestionRound round;

        final int requests;
        final int tokens;
        final BigDecimal costUsd;

        private Outcome(
                String reply,
                int revision,
                VerifiedArtifact artifact,
                boolean platformNeeded,
                boolean limitReached,
                QuestionRound round,
                int requests,
                int tokens,
                BigDecimal costUsd) {
            this.round = round;
            this.reply = reply;
            this.revision = revision;
            this.artifact = artifact;
            this.platformNeeded = platformNeeded;
            this.limitReached = limitReached;
            this.requests = requests;
            this.tokens = tokens;
            this.costUsd = costUsd;
        }
    }

    private final ProjectSourceStore source;
    private final Control control;
    private final String javaDirectory;
    private final LibraryTools libraries;
    private final RunLimits.Watch watch;
    private final RunSkills skills;
    private final SkillAdding adding;
    private final AppIcons icons;
    private final RequestRetry retry;
    private AppLink link = AppLink.NONE;
    private long tryStarted;
    private boolean iconAsked;
    private boolean imageFailed;

    private Model model;
    private WorkingCopy files;
    private Compaction compaction;
    private RunSteps steps;
    private Journal journal;
    private Build build;
    private int revision;
    private Integer tokens;
    private String lastBuildErrors;
    private VerifiedArtifact lastArtifact;
    private int lastBuiltRevision = -1;
    private boolean platformNeeded;
    private QuestionRound round;
    private String briefWritten;
    private RunPlan plan;
    private int requests;
    private int stepsTaken;
    private BigDecimal cost = BigDecimal.ZERO;
    private long promptTokens;
    private long completionTokens;
    private long cachedTokens;

    StepRun(ProjectSourceStore source, Control control, LibraryTools libraries) {
        this(source, control, libraries, RunLimits.OFF);
    }

    StepRun(ProjectSourceStore source, Control control, LibraryTools libraries, RunLimits limits) {
        this(source, control, libraries, limits, RunSkills.NONE);
    }

    StepRun(
            ProjectSourceStore source,
            Control control,
            LibraryTools libraries,
            RunLimits limits,
            RunSkills skills) {
        this(source, control, libraries, limits, skills, SkillAdding.NONE);
    }

    StepRun(
            ProjectSourceStore source,
            Control control,
            LibraryTools libraries,
            RunLimits limits,
            RunSkills skills,
            SkillAdding adding) {
        this(source, control, libraries, limits, skills, adding, AppIcons.NONE);
    }

    StepRun(
            ProjectSourceStore source,
            Control control,
            LibraryTools libraries,
            RunLimits limits,
            RunSkills skills,
            SkillAdding adding,
            AppIcons icons) {
        this(
                source,
                control,
                libraries,
                limits,
                skills,
                adding,
                icons,
                RequestRetry.standard(dev.smallwright.agent.ModelProvider.OPENROUTER));
    }

    StepRun(
            ProjectSourceStore source,
            Control control,
            LibraryTools libraries,
            RunLimits limits,
            RunSkills skills,
            SkillAdding adding,
            AppIcons icons,
            RequestRetry retry) {
        this.retry = java.util.Objects.requireNonNull(retry);
        this.icons = java.util.Objects.requireNonNull(icons);
        watch = limits.watch();
        this.skills = java.util.Objects.requireNonNull(skills);
        this.adding = java.util.Objects.requireNonNull(adding);
        this.source = source;
        this.control = java.util.Objects.requireNonNull(control);
        this.libraries = java.util.Objects.requireNonNull(libraries);
        javaDirectory = "java/" + source.packageName().replace('.', '/') + "/";
    }

    /** Gives the run read_crash_report and look_at_app for the project's app. */
    StepRun appLink(AppLink link) {
        this.link = java.util.Objects.requireNonNull(link);
        return this;
    }

    /** Model requests made so far, however the run ends. */
    int requests() {
        return requests;
    }

    /** The cost OpenRouter reported for those requests, in US dollars; 0 from a model server. */
    BigDecimal costUsd() {
        return cost;
    }

    /** Token counts reported for those requests: prompt, of which cached, and reply. */
    String tokenCounts() {
        return promptTokens
                + " prompt tokens ("
                + cachedTokens
                + " cached), "
                + completionTokens
                + " completion tokens";
    }

    private void count(ModelClient.StepReply reply) {
        count(reply.totalTokens, reply.promptTokens, reply.cachedTokens, reply.costUsd);
    }

    /** Sends a model request, trying again while that can help and the run is within its limits. */
    private ModelClient.StepReply send(RequestRetry.Request request, BooleanSupplier active)
            throws Exception {
        return retry.send(
                () -> attempt(request),
                steps,
                active,
                () -> watch.reached(stepsTaken, cost) == null);
    }

    /**
     * One try of a model request, timed from its own start; a failed try counts the usage it
     * reported.
     */
    private ModelClient.StepReply attempt(RequestRetry.Request request) throws Exception {
        tryStarted = System.nanoTime();
        try {
            return request.send();
        } catch (ModelClient.RequestFailed failed) {
            ModelClient.Usage used = failed.used();
            if (used != null)
                count(used.totalTokens, used.promptTokens, used.cachedTokens, used.costUsd);
            throw failed;
        }
    }

    private void count(int total, int prompt, int cached, BigDecimal reportedCost) {
        requests++;
        tokens = (int) Math.min(Integer.MAX_VALUE, (tokens == null ? 0L : tokens) + total);
        if (reportedCost != null) cost = cost.add(reportedCost);
        promptTokens += prompt;
        cachedTokens += cached;
        completionTokens += total - prompt;
    }

    /**
     * Runs until the model answers in words. Throws when the run is stopped or fails; the journal
     * then records CANCELLED or FAILED at the last committed revision and uncommitted edits are
     * gone.
     */
    Outcome run(
            String approvedContext,
            int baseRevision,
            BooleanSupplier callerActive,
            Model model,
            Build build,
            Journal journal,
            RunSteps steps)
            throws Exception {
        return run(
                approvedContext,
                AttachedImages.NONE,
                baseRevision,
                callerActive,
                model,
                build,
                journal,
                steps);
    }

    /** As above, with the pictures approved with the first message sent after its text. */
    Outcome run(
            String approvedContext,
            AttachedImages images,
            int baseRevision,
            BooleanSupplier callerActive,
            Model model,
            Build build,
            Journal journal,
            RunSteps steps)
            throws Exception {
        BooleanSupplier active = () -> control.isActive() && callerActive.getAsBoolean();
        this.steps = steps;
        this.journal = journal;
        this.build = build;
        this.model = model;
        revision = baseRevision;
        try {
            ensureActive(active);
            files = new WorkingCopy(source, baseRevision);
            compaction = new Compaction(model.contextTokens(), files.files(), images);
            advance(RunJournal.State.PLANNING, null);
            advance(RunJournal.State.EDITING, null);
            if (skills.used != null)
                steps.show(StepKind.DONE, "Using skill " + skills.used.name, "");
            JsonArray messages = new JsonArray();
            JsonObject first = new JsonObject();
            first.addProperty("role", "user");
            first.add("content", images.userContent(approvedContext));
            messages.add(first);
            String answer = null;
            boolean limitReached = false;
            int cutOffsInARow = 0;
            while (answer == null) {
                ensureActive(active);
                answer = watch.reached(stepsTaken, cost);
                if (answer == null && compaction.due(messages)) {
                    messages = compact(messages, model, active);
                    answer = watch.reached(stepsTaken, cost);
                }
                if (answer != null) {
                    limitReached = true;
                    break;
                }
                if (!steps.lastIs(StepKind.THINKING)) steps.add(StepKind.THINKING, "Thinking", "");
                ModelClient.StepReply reply;
                JsonArray asking = messages;
                int promptTokens = compaction.tokens(asking);
                try {
                    reply = send(() -> model.next(asking, promptTokens), active);
                } catch (IOException failure) {
                    if (!active.getAsBoolean()) throw failure;
                    steps.finishLast(
                            StepKind.FAILED,
                            "The model did not answer",
                            GenerationFailure.safeProviderMessage(failure));
                    answer = watch.reached(stepsTaken, cost);
                    if (answer == null) throw failure;
                    limitReached = true;
                    break;
                }
                count(reply);
                stepsTaken++;
                ensureActive(active);
                if (reply.cutOff) {
                    cutOffsInARow++;
                    boolean stuck = cutOffsInARow == MAX_CUT_OFFS_IN_A_ROW;
                    steps.finishLast(
                            StepKind.FAILED,
                            "The model's reply was too long and was cut off",
                            stuck
                                    ? "Nothing from it was used. The run ended."
                                    : "Nothing from it was used. The builder asked for smaller"
                                            + " steps.",
                            reply.reasoning);
                    if (stuck) {
                        answer = CUT_OFF_STUCK;
                        limitReached = true;
                        break;
                    }
                    messages.add(message("user", CUT_OFF_NOTE));
                    continue;
                }
                cutOffsInARow = 0;
                if (reply.calls.isEmpty() || !reply.reasoning.isEmpty())
                    steps.finishLast(
                            StepKind.DONE,
                            "Thought for " + seconds(tryStarted),
                            "",
                            reply.reasoning);
                if (reply.webSearches > 0) WebReading.searched(reply.webSearches, steps);
                if (reply.calls.isEmpty()) {
                    answer = reply.text;
                    break;
                }
                messages.add(reply.assistantMessage());
                compaction.replied(reply.totalTokens, messages);
                for (RunTools.Call call : reply.calls) {
                    ensureActive(active);
                    JsonObject result =
                            message(
                                    "tool",
                                    RunTools.ASK.equals(call.name)
                                            ? ask(call, reply)
                                            : execute(call, active));
                    result.addProperty("tool_call_id", call.id);
                    messages.add(result);
                    if (round != null) {
                        answer = round.intro.isEmpty() ? round.questions.get(0).text : round.intro;
                        break;
                    }
                    if (platformNeeded) {
                        answer = PLATFORM_NEEDED;
                        break;
                    }
                    if (watch.stuck()) {
                        answer = watch.reached(stepsTaken, cost);
                        limitReached = true;
                        break;
                    }
                }
                link.addPicture(messages);
            }
            ensureActive(active);
            VerifiedArtifact artifact = null;
            if (files.changed()) {
                advance(RunJournal.State.APPLYING, null);
                revision = files.commit();
                advance(RunJournal.State.ANSWERED, null);
            } else if (platformNeeded) {
                advance(RunJournal.State.FAILED, null);
            } else if (lastArtifact != null && lastBuiltRevision == revision) {
                artifact = lastArtifact;
                control.finish(active, revision, artifact, (built, verified) -> {}, journal);
            } else {
                advance(RunJournal.State.ANSWERED, null);
            }
            return new Outcome(
                    answer,
                    revision,
                    artifact,
                    platformNeeded,
                    limitReached,
                    round,
                    requests,
                    tokens,
                    cost);
        } catch (Exception failure) {
            if (control.isFinished()) throw failure;
            try {
                journal.advance(
                        !active.getAsBoolean() || Thread.currentThread().isInterrupted()
                                ? RunJournal.State.CANCELLED
                                : RunJournal.State.FAILED,
                        revision,
                        null,
                        tokens);
            } catch (Exception journalFailure) {
                failure.addSuppressed(journalFailure);
            }
            throw failure;
        }
    }

    /**
     * The messages with earlier steps summarized, or unchanged when a failed summary took the run
     * to one of its limits.
     */
    private JsonArray compact(JsonArray messages, Model model, BooleanSupplier active)
            throws Exception {
        steps.show(StepKind.THINKING, "Summarizing earlier steps to make room", "");
        ModelClient.StepReply reply;
        try {
            JsonArray request = compaction.summaryRequest(messages);
            int promptTokens = Compaction.summaryTokens(request);
            reply = send(() -> model.summarize(request, promptTokens), active);
        } catch (IOException failure) {
            if (!active.getAsBoolean()) throw failure;
            steps.finishLast(
                    StepKind.FAILED,
                    "Could not summarize earlier steps",
                    GenerationFailure.safeProviderMessage(failure));
            if (watch.reached(stepsTaken, cost) == null) throw failure;
            return messages;
        }
        count(reply);
        ensureActive(active);
        try {
            JsonArray compacted =
                    compaction.compacted(messages, reply.text, files.files(), briefWritten, plan);
            steps.finishLast(
                    StepKind.DONE, "Summarized earlier steps to make room", seconds(tryStarted));
            return compacted;
        } catch (IOException tooLarge) {
            steps.finishLast(
                    StepKind.FAILED, "Could not summarize earlier steps", tooLarge.getMessage());
            throw tooLarge;
        }
    }

    private void advance(RunJournal.State next, String buildId) {
        journal.advance(next, revision, buildId, tokens);
    }

    private String execute(RunTools.Call call, BooleanSupplier active) throws Exception {
        switch (call.name) {
            case RunTools.LIST_FILES:
                return listFiles();
            case RunTools.READ_FILE:
                return readFile(call);
            case RunTools.SEARCH_TEXT:
                return searchText(call);
            case RunTools.WRITE_FILE:
                return writeFile(call);
            case RunTools.EDIT_FILE:
                return editFile(call);
            case RunTools.DELETE_FILE:
                return deleteFile(call);
            case RunTools.BUILD:
                return build(active);
            case RunTools.READ_BUILD_ERRORS:
                steps.show(StepKind.DONE, "Read the build errors", "");
                return lastBuildErrors == null
                        ? lastArtifact != null && lastBuiltRevision == revision
                                ? "The last build succeeded."
                                : "No build has failed in this run."
                        : lastBuildErrors;
            case RunTools.SEARCH_LIBRARIES:
                return libraries.search(call.query, steps);
            case RunTools.ADD_LIBRARY:
                return libraries.add(call.library, files, steps);
            case RunTools.REMOVE_LIBRARY:
                return libraries.remove(call.library, files, steps);
            case RunTools.READ_SKILL:
                return skills.read(call, steps);
            case RunTools.ADD_SKILL:
                return adding.add(call, skills, steps);
            case RunTools.MAKE_APP_ICON:
                return makeAppIcon(call, active);
            case RunTools.MAKE_IMAGE:
                return makePicture(call, active);
            case RunTools.FETCH_WEB:
                return WebReading.read(call, model, steps, active);
            case RunTools.LOOKUP_CODE:
                return libraries.lookUp(call.symbol, call.offset, files, steps, active);
            case RunTools.READ_CRASH_REPORT:
                return link.readCrashReport(steps);
            case RunTools.LOOK_AT_APP:
                return link.look(call.activity, files.changed() ? -1 : revision, active, steps);
            case RunTools.WRITE_BRIEF:
                return writeBrief(call);
            case RunTools.UPDATE_PLAN:
                return updatePlan(call);
            default:
                throw new IllegalStateException("Unknown tool");
        }
    }

    private String ask(RunTools.Call call, ModelClient.StepReply reply) {
        if (reply.calls.size() > 1)
            return refused(
                    ASK_FAILED,
                    "Not asked: call ask alone, as the only tool call in its reply. The other calls"
                            + " in this reply were handled as usual.");
        try {
            round = QuestionRound.fromCall(reply.text, call.arguments);
        } catch (QuestionRound.Refused refusal) {
            return refused(
                    ASK_FAILED, refusal.getMessage() + " Call ask again with the whole round.");
        }
        return "Asked. The run ends here; the person's answers come as their next message.";
    }

    private String writeBrief(RunTools.Call call) {
        String what = "Could not save the project brief";
        String refusal = ProjectBrief.refusal(call.brief);
        if (refusal != null) return refused(what, refusal);
        ProjectBrief brief = source.brief();
        try {
            brief.write(call.brief);
            briefWritten = brief.read();
        } catch (IOException failed) {
            return refused(what, "Not saved: " + bounded(failed.getMessage()) + ".");
        }
        steps.show(StepKind.DONE, "Wrote the project brief", "");
        return "Saved the project brief. Every later chat in this project starts with it.";
    }

    private String updatePlan(RunTools.Call call) {
        try {
            plan = RunPlan.fromCall(call.arguments);
        } catch (RunPlan.Refused refusal) {
            return refused(
                    "Could not update the plan",
                    refusal.getMessage() + " Call update_plan again with the whole list.");
        }
        steps.plan(plan);
        return "Plan updated: " + plan.progress() + ".";
    }

    private String makeAppIcon(RunTools.Call call, BooleanSupplier active) throws Exception {
        String what = "an app icon";
        if (!icons.available())
            return refused(
                    "Could not make " + what, "App icons cannot be made on this phone right now.");
        if (iconAsked)
            return refused(
                    "Could not make " + what,
                    "Not made: this run already asked for an icon once. The person can ask again"
                            + " in their next message.");
        iconAsked = true;
        return makeImage(
                what,
                AppIcons.prompt(call.description),
                ImageClient.Shape.SQUARE,
                png -> icons.write(png, files),
                "The icon could not be made: ",
                " The app keeps its icon.",
                active);
    }

    private String makePicture(RunTools.Call call, BooleanSupplier active) throws Exception {
        String what = "a picture";
        if (!icons.available())
            return refused(
                    "Could not make " + what, "Pictures cannot be made on this phone right now.");
        String refusal = AppIcons.pictureNameRefusal(call.nameArgument, files.files());
        if (refusal != null) return refused("Could not make " + what, refusal);
        return makeImage(
                what,
                AppIcons.picturePrompt(call.description),
                call.shape,
                png -> icons.writePicture(call.nameArgument, png, files),
                "The picture could not be made: ",
                " Nothing was written.",
                active);
    }

    /** Writes a checked PNG into the working copy; returns the tool result for the model. */
    private interface ImageWrite {
        String write(byte[] png) throws IOException;
    }

    /**
     * One paid image request within the run's spending limit, counted like a model request. A
     * request that fails without a reply to count stops further image requests in this run.
     */
    private String makeImage(
            String what,
            String prompt,
            ImageClient.Shape shape,
            ImageWrite write,
            String notMade,
            String unchanged,
            BooleanSupplier active)
            throws Exception {
        String failed = "Could not make " + what;
        if (imageFailed)
            return refused(
                    failed,
                    "Not made: an image request already failed in this run, so no more are asked"
                            + " for. The person can ask again in their next message.");
        if (watch.reached(0, cost) != null)
            return refused(
                    failed,
                    "Not made: the run reached the spending limit the person set, so no image was"
                            + " asked for.");
        steps.show(StepKind.RUNNING, "Making " + what, "");
        ImageClient.Image image;
        try {
            image = model.image(prompt, shape);
        } catch (IOException failure) {
            ensureActive(active);
            imageFailed = true;
            String why = GenerationFailure.safeProviderMessage(failure);
            steps.finishLast(StepKind.FAILED, failed, why);
            return notMade + why + "." + unchanged;
        }
        count(image.totalTokens, image.promptTokens, 0, image.costUsd);
        ensureActive(active);
        String why = image.refusal;
        if (why == null)
            try {
                String result = write.write(image.bytes);
                steps.finishLast(StepKind.DONE, "Made " + what, "");
                return result;
            } catch (IOException unusable) {
                why = bounded(unusable.getMessage());
            }
        steps.finishLast(StepKind.FAILED, failed, why);
        return notMade + why + unchanged;
    }

    private String listFiles() {
        StringBuilder listing = new StringBuilder();
        for (Map.Entry<String, byte[]> file : files.files().entrySet())
            listing.append(file.getKey())
                    .append(" (")
                    .append(file.getValue().length)
                    .append(" bytes)\n");
        steps.show(StepKind.DONE, "Listed the project's files", "");
        return listing.length() == 0 ? "The project has no files." : listing.toString();
    }

    private String readFile(RunTools.Call call) {
        byte[] bytes = files.read(call.path);
        if (bytes == null) return refused("Could not read " + shown(call.path), missing(call.path));
        String text = text(bytes);
        if (text == null)
            return refused(
                    "Could not read " + shown(call.path),
                    call.path + " is not a text file (" + bytes.length + " bytes).");
        text = ContextFilter.redact(text);
        String[] lines = text.split("\n", -1);
        int total = text.endsWith("\n") ? lines.length - 1 : lines.length;
        int first = Math.min(call.offset, Math.max(total, 1));
        StringBuilder shown = new StringBuilder();
        int last = first - 1;
        for (int index = first - 1; index < total && last - first + 1 < call.limit; index++) {
            String line = lines[index] + "\n";
            if (utf8(shown.toString()) + utf8(line) > MAX_RESULT_BYTES) break;
            shown.append(line);
            last = index + 1;
        }
        steps.show(StepKind.DONE, "Read " + shown(call.path), "");
        String header =
                total == 0
                        ? call.path + " is empty.\n"
                        : call.path
                                + ", lines "
                                + first
                                + " to "
                                + last
                                + " of "
                                + total
                                + (last < total
                                        ? " (read again with offset " + (last + 1) + " for more)"
                                        : "")
                                + ":\n";
        return header + shown;
    }

    private String searchText(RunTools.Call call) {
        StringBuilder found = new StringBuilder();
        int matches = 0;
        boolean more = false;
        for (Map.Entry<String, byte[]> file : files.files().entrySet()) {
            if (call.path != null && !file.getKey().startsWith(call.path)) continue;
            String text = text(file.getValue());
            if (text == null) continue;
            String[] lines = ContextFilter.redact(text).split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                if (!lines[index].contains(call.query)) continue;
                if (matches == MAX_SEARCH_MATCHES) {
                    more = true;
                    break;
                }
                String line = lines[index].trim();
                if (line.length() > 200) line = line.substring(0, 200) + "…";
                found.append(file.getKey())
                        .append(':')
                        .append(index + 1)
                        .append(": ")
                        .append(line)
                        .append('\n');
                matches++;
            }
        }
        steps.show(StepKind.DONE, "Searched for \"" + call.query + "\"", matches + " found");
        if (matches == 0) return "No line contains that text.";
        return found + (more ? "More matches were left out; search for something narrower.\n" : "");
    }

    private String writeFile(RunTools.Call call) {
        String refusal = editRefusal(call.path);
        if (refusal != null) return refused("Could not write " + shown(call.path), refusal);
        boolean created = files.read(call.path) == null;
        byte[] content = call.content.getBytes(StandardCharsets.UTF_8);
        if (content.length > ProjectStore.MAX_FILE_BYTES)
            return refused("Could not write " + shown(call.path), tooLarge(call.path, content));
        try {
            files.write(call.path, content);
        } catch (IOException full) {
            return refused("Could not write " + shown(call.path), projectFull(full));
        }
        steps.show(StepKind.DONE, (created ? "Created " : "Wrote ") + shown(call.path), "");
        return (created ? "Created " : "Replaced ")
                + call.path
                + " ("
                + content.length
                + " bytes). It is saved when you build.";
    }

    private String editFile(RunTools.Call call) {
        String what = "Could not edit " + shown(call.path);
        String refusal = editRefusal(call.path);
        if (refusal != null) return refused(what, refusal);
        byte[] bytes = files.read(call.path);
        if (bytes == null) return refused(what, missing(call.path));
        String text = text(bytes);
        if (text == null) return refused(what, call.path + " is not a text file.");
        if (call.oldText.isEmpty()) return refused(what, "old_text is empty.");
        int first = text.indexOf(call.oldText);
        if (first < 0)
            return refused(
                    what,
                    "old_text does not occur in "
                            + call.path
                            + ". Read the file again and copy the exact text.");
        if (text.indexOf(call.oldText, first + 1) >= 0)
            return refused(
                    what,
                    "old_text occurs more than once in "
                            + call.path
                            + ". Include more of the surrounding lines so it occurs exactly once.");
        String edited =
                text.substring(0, first)
                        + call.newText
                        + text.substring(first + call.oldText.length());
        byte[] content = edited.getBytes(StandardCharsets.UTF_8);
        if (content.length > ProjectStore.MAX_FILE_BYTES)
            return refused(what, tooLarge(call.path, content));
        try {
            files.write(call.path, content);
        } catch (IOException full) {
            return refused(what, projectFull(full));
        }
        steps.show(StepKind.DONE, "Edited " + shown(call.path), "");
        return "Edited " + call.path + ". It is saved when you build.";
    }

    private String deleteFile(RunTools.Call call) {
        String what = "Could not delete " + shown(call.path);
        String refusal = editRefusal(call.path);
        if (refusal != null) return refused(what, refusal);
        if (files.read(call.path) == null) return refused(what, missing(call.path));
        if (AppManifestPolicy.PATH.equals(call.path))
            return refused(
                    what, "The app needs AndroidManifest.xml; edit it instead of deleting it.");
        if (call.path.equals(source.mainPath()) && launchesMainActivity())
            return refused(
                    what,
                    "MainActivity.java is the app's launcher activity in AndroidManifest.xml. Point"
                            + " the launcher at another activity first, or keep it.");
        files.delete(call.path);
        steps.show(StepKind.DONE, "Deleted " + shown(call.path), "");
        return "Deleted " + call.path + ". It is saved when you build.";
    }

    private boolean launchesMainActivity() {
        byte[] manifest = files.read(AppManifestPolicy.PATH);
        String launcher =
                manifest == null
                        ? null
                        : AppManifestPolicy.launcherClass(text(manifest), source.packageName());
        return launcher == null || launcher.equals(source.packageName() + ".MainActivity");
    }

    private String manifestRefusal() {
        byte[] manifest = files.read(AppManifestPolicy.PATH);
        if (manifest == null) return null;
        String content = text(manifest);
        try {
            AppManifestPolicy.normalize(
                    content == null ? "" : content,
                    source.packageName(),
                    1,
                    libraries.manifests(files.read(LibraryList.PATH)));
            for (String name : AppManifestPolicy.projectClasses(content, source.packageName())) {
                String outer = name.indexOf('$') < 0 ? name : name.substring(0, name.indexOf('$'));
                String path = "java/" + outer.replace('.', '/') + ".java";
                if (files.read(path) == null)
                    return "AndroidManifest.xml names "
                            + name
                            + ", but "
                            + path
                            + " does not exist. Create that file or fix the name.";
            }
            return null;
        } catch (IOException refused) {
            return bounded(refused.getMessage());
        }
    }

    private String build(BooleanSupplier active) throws Exception {
        String manifestRefusal = manifestRefusal();
        if (manifestRefusal != null) {
            steps.show(StepKind.FAILED, "The manifest was refused", manifestRefusal);
            return "The build did not start. "
                    + manifestRefusal
                    + " Fix AndroidManifest.xml or the libraries and build again.";
        }
        steps.show(StepKind.RUNNING, "Building on this phone", "");
        if (files.changed()) {
            advance(RunJournal.State.APPLYING, null);
            try {
                revision = files.commit();
            } catch (IOException refused) {
                if (StorageFull.causedBy(refused)) throw storageFull(refused);
                advance(RunJournal.State.EDITING, null);
                String why = bounded(refused.getMessage());
                steps.finishLast(StepKind.FAILED, "Could not save the changes", why);
                return "The changes could not be saved, so nothing was built: "
                        + why
                        + ". Each file may hold at most 1 MB, and a project at most 500 files and"
                        + " 16 MB.";
            }
        }
        String buildId = "build_" + UUID.randomUUID().toString().replace("-", "");
        advance(RunJournal.State.BUILDING, buildId);
        long started = System.nanoTime();
        String result;
        try {
            VerifiedArtifact artifact = build.build(revision, buildId);
            lastArtifact = artifact;
            lastBuiltRevision = revision;
            lastBuildErrors = null;
            watch.buildSucceeded();
            steps.finishLast(StepKind.DONE, "Built on this phone in " + seconds(started), "");
            result = "The build succeeded. The app is ready to install.";
        } catch (PlatformFilesMissing missing) {
            platformNeeded = true;
            steps.finishLast(StepKind.FAILED, "Could not build", "Android platform files needed");
            result = PLATFORM_MISSING_RESULT;
        } catch (JavaCompileFailure failure) {
            lastBuildErrors = compileErrors(failure.diagnostics);
            watch.buildFailed(errorSignature(failure.diagnostics));
            int count = failure.diagnostics == null ? 0 : failure.diagnostics.problems.size();
            steps.finishLast(
                    StepKind.FAILED,
                    "The build failed",
                    count == 0 ? "" : count + (count == 1 ? " error" : " errors"));
            result = lastBuildErrors;
        } catch (IOException failure) {
            if (StorageFull.causedBy(failure)) throw storageFull(failure);
            lastBuildErrors = "The build failed: " + bounded(failure.getMessage());
            watch.buildFailed(lastBuildErrors);
            steps.finishLast(StepKind.FAILED, "The build failed", "");
            result = lastBuildErrors;
        }
        ensureActive(active);
        advance(RunJournal.State.EDITING, null);
        return result;
    }

    private StorageFull storageFull(IOException cause) {
        steps.finishLast(StepKind.FAILED, STORAGE_FULL_STEP, "");
        return new StorageFull(cause);
    }

    private String compileErrors(CompilerDiagnostics.Report report) {
        if (report == null)
            return "The build failed: the Java code did not compile, and the compiler gave no"
                    + " details.";
        StringBuilder errors =
                new StringBuilder("The build failed. Java compiler errors (ECJ problem names):\n");
        for (CompilerDiagnostics.Problem problem : report.problems) {
            String path = problem.file < report.paths.size() ? report.paths.get(problem.file) : "?";
            errors.append(path).append(':').append(problem.line).append(": ");
            String name = EcjProblemNames.name(problem.id);
            errors.append(name == null ? "problem " + problem.id : name);
            String code = quotedLine(files.read(path), problem.line);
            if (code != null) errors.append(" in: ").append(code);
            errors.append('\n');
        }
        if (report.truncated) errors.append("More errors were left out.\n");
        return errors.toString();
    }

    private static String errorSignature(CompilerDiagnostics.Report report) {
        if (report == null) return "";
        List<String> problems = new ArrayList<>();
        for (CompilerDiagnostics.Problem problem : report.problems)
            problems.add(
                    (problem.file < report.paths.size() ? report.paths.get(problem.file) : "?")
                            + " "
                            + problem.id);
        Collections.sort(problems);
        return String.join("\n", problems);
    }

    private static String quotedLine(byte[] bytes, int line) {
        String text = text(bytes);
        if (text == null || !ContextFilter.redact(text).equals(text)) return null;
        String[] lines = text.split("\r\n|\r|\n", -1);
        if (line < 1 || line > lines.length) return null;
        String code = lines[line - 1].trim();
        return code.length() <= 200 ? code : code.substring(0, 200) + "…";
    }

    private String editRefusal(String path) {
        if (LibraryList.PATH.equals(path))
            return LibraryList.PATH
                    + " is changed only with add_library and remove_library, which download and"
                    + " check each library.";
        if (path.startsWith(javaDirectory + ProjectStore.BUILDER_SUBPACKAGE + "/"))
            return path
                    + " is in the builder's own code, which it adds to every app by itself;"
                    + " use another package.";
        if (!source.isEditablePath(path))
            return path
                    + " is not a path a project may hold. Java files go under "
                    + javaDirectory
                    + " (sub-packages allowed), resources under res/<type>/ with lower-case names,"
                    + " other files under assets/.";
        return null;
    }

    private String refused(String what, String why) {
        steps.show(StepKind.FAILED, what, why);
        return why;
    }

    private static String tooLarge(String path, byte[] content) {
        return path
                + " would be "
                + content.length
                + " bytes, and a project file holds at most "
                + ProjectStore.MAX_FILE_BYTES
                + ". Nothing was changed; split it into more classes or files.";
    }

    private static String projectFull(IOException full) {
        return full.getMessage()
                + " Nothing was changed; delete files the app no longer needs, or make them"
                + " smaller.";
    }

    private static String missing(String path) {
        return "There is no file " + path + ". list_files shows the project's files.";
    }

    private String shown(String path) {
        return path.startsWith(javaDirectory) ? path.substring(javaDirectory.length()) : path;
    }

    static String text(byte[] bytes) {
        if (bytes == null) return null;
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException binary) {
            return null;
        }
    }

    private static String bounded(String message) {
        String text = ContextFilter.redact(message == null ? "no details" : message);
        return utf8(text) <= MAX_FAILURE_DETAIL_BYTES
                ? text
                : text.substring(0, MAX_FAILURE_DETAIL_BYTES / 4) + "…";
    }

    private static int utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static String seconds(long startedNanos) {
        long tenths = (System.nanoTime() - startedNanos) / 100_000_000L;
        return tenths < 100 ? (tenths / 10) + "." + (tenths % 10) + " s" : (tenths / 10) + " s";
    }

    private static void ensureActive(BooleanSupplier active) {
        if (!active.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new IllegalStateException("Run is no longer active");
    }
}
