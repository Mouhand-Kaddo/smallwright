package dev.smallwright.controller.ui;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public final class StepSummaryTest {
    private static Step done(String text) {
        return new Step(StepKind.DONE, text, "");
    }

    private static Step failed(String text) {
        return new Step(StepKind.FAILED, text, "");
    }

    @Test
    public void countsWhatWasDoneAndEachFileOnce() {
        StepSummary summary =
                StepSummaries.of(
                        List.of(
                                done("Thought for 2.1 s"),
                                done("Listed the project's files"),
                                done("Read MainActivity.java"),
                                done("Read res/layout/empty.xml"),
                                done("Read MainActivity.java"),
                                done("Searched for \"button\""),
                                done("Edited MainActivity.java"),
                                done("Created res/values/strings.xml"),
                                done("Wrote MainActivity.java"),
                                done("Built on this phone in 12.0 s"),
                                done("Thought for 1.0 s")),
                        true);
        assertEquals(
                "Listed the files, read 2 files, searched once, changed 2 files, built once",
                summary.getLine());
        assertEquals("", summary.getFailure());
    }

    @Test
    public void countsEachWebSearchNotEachRequestThatSearched() {
        assertEquals(
                "Searched the web 3 times",
                StepSummaries.of(
                                List.of(
                                        new Step(StepKind.DONE, "Searched the web", "2 searches"),
                                        done("Searched the web")),
                                true)
                        .getLine());
    }

    @Test
    public void namesLooksCrashReportsWebReadsLookupsBriefsAndPictures() {
        assertEquals(
                "Looked at the app",
                StepSummaries.of(
                                List.of(
                                        done("Thought for 2.2 s"),
                                        done("Looked at the app"),
                                        done("Thought for 1.0 s")),
                                true)
                        .getLine());
        Step page =
                new Step(
                        StepKind.DONE,
                        "Read developer.android.com/guide",
                        "https://developer.android.com/guide?hl=en");
        assertEquals(
                "Made 2 pictures, wrote the project brief, read the crash report, looked at the"
                        + " app twice, read 1 file, searched the web once, read 1 web page, looked"
                        + " up code once",
                StepSummaries.of(
                                List.of(
                                        done("Read the crash report"),
                                        done("Looked at the app"),
                                        done("Looked at the app"),
                                        done("Searched the web"),
                                        page,
                                        page,
                                        done("Looked up RecyclerView"),
                                        done("Wrote the project brief"),
                                        done("Made a picture"),
                                        done("Made a picture"),
                                        done("Read MainActivity.java")),
                                true)
                        .getLine());
        assertEquals(
                "Read 1 file, did not finish",
                StepSummaries.of(
                                List.of(
                                        done("Read MainActivity.java"),
                                        failed("Looking at the app")),
                                true)
                        .getLine());
    }

    @Test
    public void aMadeIconIsSaidAndAnUnfinishedOneDidNotFinish() {
        assertEquals(
                "Made an app icon, read 1 file, built once",
                StepSummaries.of(
                                List.of(
                                        done("Read AndroidManifest.xml"),
                                        done("Made an app icon"),
                                        done("Built on this phone in 3.0 s"),
                                        done("Thought for 1.0 s")),
                                true)
                        .getLine());
        assertEquals(
                "Read 1 file, did not finish",
                StepSummaries.of(
                                List.of(
                                        done("Read MainActivity.java"),
                                        failed("Making an app icon")),
                                true)
                        .getLine());
    }

    @Test
    public void skillsAreCountedAsSkillsNotFiles() {
        assertEquals(
                "Used skill grill-me, read skill grilling, read 2 files",
                StepSummaries.of(
                                List.of(
                                        done("Using skill grill-me"),
                                        done("Read skill grilling"),
                                        done("Read MainActivity.java"),
                                        done("Read AndroidManifest.xml"),
                                        done("Thought for 16 s")),
                                true)
                        .getLine());
        assertEquals(
                "Read 2 skills",
                StepSummaries.of(
                                List.of(
                                        done("Read skill tdd"),
                                        done("Read skill tdd, tests.md"),
                                        done("Read skill grilling"),
                                        failed("Could not read skill nope")),
                                false)
                        .getLine());
    }

    @Test
    public void addedSkillsAreNamedOrCountedAndAnUnansweredOneDidNotFinish() {
        assertEquals(
                "Added skill frontend-design",
                StepSummaries.of(
                                List.of(
                                        done("Added skill frontend-design for this project"),
                                        done("Thought for 9.0 s")),
                                true)
                        .getLine());
        assertEquals(
                "Added 2 skills, read skill pdf",
                StepSummaries.of(
                                List.of(
                                        new Step(StepKind.DONE, "Added 2 skills", "pdf, xlsx"),
                                        done("Read skill pdf")),
                                true)
                        .getLine());
        assertEquals(
                "Did not finish",
                StepSummaries.of(List.of(new Step(StepKind.FAILED, "Add the skill pdf?", "")), true)
                        .getLine());
    }

    @Test
    public void aLastFailedBuildIsTheFailureOnceTheRunEnds() {
        List<Step> steps =
                List.of(
                        done("Edited MainActivity.java"),
                        new Step(StepKind.FAILED, "The build failed", "3 errors"),
                        done("Read the build errors"),
                        done("Edited MainActivity.java"),
                        failed("The build failed"),
                        done("Read the build errors"),
                        done("Deleted Old.java"),
                        failed("The build failed"),
                        done("Thought for 3.0 s"));
        StepSummary ended = StepSummaries.of(steps, true);
        assertEquals(
                "Changed 1 file, deleted 1 file, built 3 times, read the build errors twice",
                ended.getDone());
        assertEquals("the last build failed", ended.getFailure());
        assertEquals("", StepSummaries.of(steps, false).getFailure());
    }

    @Test
    public void aSingleFailedBuildAndAnyRefusedBuildCountAsBuilds() {
        StepSummary summary =
                StepSummaries.of(
                        List.of(
                                done("Edited AndroidManifest.xml"),
                                failed("The manifest was refused"),
                                done("Edited AndroidManifest.xml")),
                        true);
        assertEquals("Changed 1 file, built once, the build failed", summary.getLine());
    }

    @Test
    public void aBuildThatWorksAfterFailingIsNoFailure() {
        StepSummary summary =
                StepSummaries.of(
                        List.of(
                                failed("The build failed"),
                                done("Edited MainActivity.java"),
                                done("Built on this phone in 9.1 s")),
                        true);
        assertEquals("Changed 1 file, built twice", summary.getLine());
        assertEquals("", summary.getFailure());
    }

    @Test
    public void librariesAreCountedApartFromFiles() {
        StepSummary summary =
                StepSummaries.of(
                        List.of(
                                new Step(
                                        StepKind.DONE,
                                        "Searched libraries for \"material\"",
                                        "20 found"),
                                new Step(
                                        StepKind.DONE,
                                        "Added library com.google.android.material:material 1.13.0",
                                        "Also androidx.core:core 1.16.0, androidx.appcompat:appcompat 1.7.0"),
                                done("Changed library com.google.code.gson:gson to 2.13.1"),
                                done("Did not add library androidx.room:room-runtime 2.7.0"),
                                done("Removed library com.squareup.okio:okio")),
                        true);
        assertEquals(
                "Searched for libraries once, added 2 libraries, removed 1 library",
                summary.getLine());
    }

    @Test
    public void refusedStepsAreNotCountedAsDone() {
        StepSummary summary =
                StepSummaries.of(
                        List.of(
                                failed("Could not edit MainActivity.java"),
                                failed("Could not read Missing.java"),
                                done("Read MainActivity.java")),
                        true);
        assertEquals("Read 1 file", summary.getLine());
    }

    @Test
    public void aRunThatEndsOnAnotherFailureSaysWhatWentWrong() {
        assertEquals(
                "Read 1 file, the model did not answer",
                StepSummaries.of(
                                List.of(
                                        done("Read MainActivity.java"),
                                        failed("The model did not answer")),
                                true)
                        .getLine());
        StepSummary stopped =
                StepSummaries.of(List.of(done("Thought for 4.0 s"), failed("Stopped")), true);
        assertEquals("", stopped.getDone());
        assertEquals("Stopped", stopped.getFailure());
    }

    @Test
    public void aStepSavedUnfinishedSaysTheRunDidNotFinish() {
        assertEquals(
                "Read 1 file, did not finish",
                StepSummaries.of(
                                StepLog.decode(
                                        "done\tRead MainActivity.java\t\nfailed\tThinking\t"),
                                true)
                        .getLine());
        assertEquals(
                "Changed 1 file, built twice, did not finish",
                StepSummaries.of(
                                StepLog.decode(
                                        "done\tBuilt on this phone in 2.0 s\t\n"
                                                + "done\tEdited MainActivity.java\t\n"
                                                + "failed\tBuilding on this phone\t"),
                                true)
                        .getLine());
        assertEquals(
                "Did not finish",
                StepSummaries.of(
                                StepLog.decode("done\tThought for 1.0 s\t\nfailed\tThinking\t"),
                                true)
                        .getLine());
    }

    @Test
    public void stepsThatDidNothingCountableShowTheLastStep() {
        assertEquals(
                "Summarized earlier steps to make room",
                StepSummaries.of(
                                List.of(
                                        done("Thought for 4.0 s"),
                                        done("Summarized earlier steps to make room")),
                                true)
                        .getLine());
        assertEquals("", StepSummaries.of(List.of(), true).getLine());
    }

    @Test
    public void sayWhenTheProjectBriefWasWritten() {
        assertEquals(
                "Wrote the project brief, read 1 file",
                StepSummaries.of(
                                List.of(
                                        done("Thought for 1.0 s"),
                                        done("Wrote the project brief"),
                                        done("Read MainActivity.java")),
                                true)
                        .getLine());
    }
}
