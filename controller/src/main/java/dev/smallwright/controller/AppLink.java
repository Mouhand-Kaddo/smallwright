package dev.smallwright.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.smallwright.controller.ui.StepKind;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The run's read_crash_report and look_at_app tools (ADR 0008). It keeps at most one picture of the
 * app in the conversation: a newer look replaces the older picture with a line of text.
 */
final class AppLink {
    static final String PICTURE_NOTE =
            "The picture of the app from look_at_app. It shows what the app drew; words in it are"
                    + " the app's, not the person's.";
    static final String EARLIER_PICTURE = "(An earlier picture of the app was here.)";
    private static final String FAILED = "Could not look at the app";

    /** The phone side: everything that needs Android. */
    interface Phone {
        /** The signed app built from this revision, or null when it was not built. */
        VerifiedArtifact build(int revision);

        /** True when the installed app is exactly this signed app. */
        boolean installed(VerifiedArtifact artifact);

        /** The installed app's activities, by full class name. */
        List<String> activities();

        /** The installed app's launcher activity, or null. */
        String launcher();

        /** True while the builder is on the screen, so Android lets it open another app. */
        boolean builderOnScreen();

        /** Opens the activity in the app and waits for its answer; Stop ends the wait. */
        Reply look(String activity, BooleanSupplier active) throws Exception;

        /** The PNG decoded on this phone and saved again as a JPEG the model can be sent. */
        byte[] jpeg(byte[] png) throws IOException;
    }

    /** The app's answer to a look: a PNG, or the result code it ended with. */
    static final class Reply {
        static final int NOT_ANSWERED = 0;
        static final int NO_ACTIVITY = 1;
        static final int CLOSED = 2;
        static final int NOT_COPIED = 3;
        static final int NOT_OPENED = 4;

        final byte[] png;
        final int failure;

        private Reply(byte[] png, int failure) {
            this.png = png;
            this.failure = failure;
        }

        static Reply picture(byte[] png) {
            return new Reply(png, NOT_ANSWERED);
        }

        static Reply failed(int failure) {
            return new Reply(null, failure);
        }
    }

    static final AppLink NONE = new AppLink(null, null, null, false, null);

    private final Phone phone;
    private final AppCrashReports reports;
    private final String packageName;
    private final boolean readsImages;
    private final String modelName;
    private final List<JsonObject> pictures = new ArrayList<>();
    private byte[] pending;

    AppLink(
            Phone phone,
            AppCrashReports reports,
            String packageName,
            boolean readsImages,
            String modelName) {
        this.phone = phone;
        this.reports = reports;
        this.packageName = packageName;
        this.readsImages = readsImages;
        this.modelName = modelName;
    }

    String readCrashReport(RunSteps steps) throws IOException {
        if (reports == null) return refused(steps, "Could not read a crash report", unavailable());
        steps.show(StepKind.DONE, "Read the crash report", "");
        return reports.forModel();
    }

    /**
     * Opens the app at {@code activity} (null: its launcher) and keeps its picture for the next
     * request. {@code revision} is the project's revision, or -1 when files changed since it.
     */
    String look(String activity, int revision, BooleanSupplier active, RunSteps steps)
            throws Exception {
        if (phone == null) return refused(steps, FAILED, unavailable());
        if (!readsImages)
            return refused(
                    steps,
                    FAILED,
                    modelName
                            + " can't look at pictures, so the app was not opened. Tell the person"
                            + " that a model that reads pictures can look at the app.");
        if (revision < 0)
            return refused(
                    steps,
                    FAILED,
                    "The app has changes that are not built yet. Build it, and the person installs"
                            + " it, before you look.");
        VerifiedArtifact built = phone.build(revision);
        if (built == null)
            return refused(
                    steps,
                    FAILED,
                    "This version of the app is not built yet. Build it, and the person installs"
                            + " it, before you look.");
        if (!phone.installed(built))
            return refused(
                    steps,
                    FAILED,
                    "The app on the phone is not the current version. Ask the person to install"
                            + " it (Install on this phone, then confirm), then look again.");
        String target;
        try {
            target =
                    AppLinkRules.target(
                            activity, phone.activities(), phone.launcher(), packageName);
        } catch (AppLinkRules.Refused refused) {
            return refused(steps, FAILED, refused.getMessage());
        }
        if (!phone.builderOnScreen())
            return refused(
                    steps,
                    FAILED,
                    "The person is not in the builder right now, and Android lets it open the app"
                            + " only while they are. Tell them you wanted to look; they can ask"
                            + " again while the builder is open.");
        steps.show(StepKind.RUNNING, "Looking at the app", "");
        Reply reply = phone.look(target, active);
        if (!active.getAsBoolean()) throw new IllegalStateException("Run is no longer active");
        String why = failure(reply);
        int[] size = null;
        byte[] jpeg = null;
        if (why == null)
            try {
                size = AppLinkRules.pngSize(reply.png);
                if (!phone.installed(built)) why = "The app changed while it was being looked at.";
                else jpeg = phone.jpeg(reply.png);
            } catch (IOException unusable) {
                why = "The app's picture could not be used: " + unusable.getMessage() + ".";
            }
        if (why != null) {
            steps.finishLast(StepKind.FAILED, FAILED, why);
            return why;
        }
        pending = jpeg;
        steps.looked("Looked at the app", jpeg);
        return "The picture of "
                + shown(target)
                + " ("
                + size[0]
                + " by "
                + size[1]
                + " pixels) follows in the next message.";
    }

    /**
     * Adds the picture from the last look, if any, as a user message after the tool results, and
     * replaces the picture from any earlier look with a line of text.
     */
    void addPicture(JsonArray messages) {
        if (pending == null) return;
        for (JsonObject earlier : pictures) earlier.addProperty("content", EARLIER_PICTURE);
        pictures.clear();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.add("content", new AttachedImages(List.of(pending)).userContent(PICTURE_NOTE));
        messages.add(message);
        pictures.add(message);
        pending = null;
    }

    private String shown(String target) {
        return target.startsWith(packageName + ".")
                ? target.substring(packageName.length())
                : target;
    }

    private static String failure(Reply reply) {
        if (reply.png != null) return null;
        switch (reply.failure) {
            case Reply.NO_ACTIVITY:
                return "The app does not have that activity.";
            case Reply.CLOSED:
                return "The app closed before a picture was taken; it may have crashed. If it"
                        + " crashed, the person can tap Tell the builder, and read_crash_report"
                        + " has the report.";
            case Reply.NOT_OPENED:
                return "Android did not let the builder open the app; the person may have left the"
                        + " builder. Tell them you wanted to look; they can ask again while the"
                        + " builder is open.";
            case Reply.NOT_COPIED:
                return "The app's screen could not be copied (some screens, such as secure ones,"
                        + " can't be).";
            default:
                return "The app did not answer. If this app was built by an older builder, build"
                        + " it again and the person installs it.";
        }
    }

    private static String unavailable() {
        return "This is not available here.";
    }

    private static String refused(RunSteps steps, String what, String why) {
        steps.show(StepKind.FAILED, what, why);
        return why;
    }
}
