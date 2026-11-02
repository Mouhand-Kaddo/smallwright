package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class RunNoticesTest {
    private final List<String> shade = new ArrayList<>();
    private final RunNotices notices =
            new RunNotices(
                    new RunNotices.Shade() {
                        @Override
                        public void show(RunNotice notice) {
                            shade.add(
                                    notice.kind
                                            + "|"
                                            + notice.title
                                            + "|"
                                            + notice.text
                                            + "|"
                                            + notice.projectTitle
                                            + "|"
                                            + notice.projectId
                                            + "/"
                                            + notice.chatId);
                        }

                        @Override
                        public void clear() {
                            shade.add("clear");
                        }
                    });

    @Test
    public void aWorkingRunShowsItsLatestStep() {
        notices.started("run_1", "app1", "Notes", "chat_1");
        notices.step("Edited ListScreen.java");

        assertEquals(
                List.of(
                        "WORKING|Working|Thinking|Notes|app1/chat_1",
                        "WORKING|Working|Edited ListScreen.java|Notes|app1/chat_1"),
                shade);
    }

    @Test
    public void aRunThatBuiltSaysTheAppIsReadyAndOpensItsChat() {
        notices.started("run_1", "app1", "Notes", "chat_1");
        notices.built();

        assertEquals("DONE|Your app is ready|Tap to install it.|Notes|app1/chat_1", last());
    }

    @Test
    public void aRunThatEndedInWordsWithoutABuildNeedsTheirInput() {
        notices.started("run_1", "app1", "Notes", "chat_1");
        notices.answered("Should notes sync between phones?\n\nI can keep them local instead.");

        assertEquals(
                "NEEDS_INPUT|Needs your input|Should notes sync between phones?|Notes|app1/chat_1",
                last());
    }

    @Test
    public void aFailedRunGivesTheReason() {
        notices.started("run_1", "app1", "Notes", "chat_1");
        notices.failed("The model did not answer. Try again.");

        assertEquals(
                "FAILED|Did not finish|The model did not answer. Try again.|Notes|app1/chat_1",
                last());
    }

    @Test
    public void aRunThePersonStoppedLeavesNoNotice() {
        notices.started("run_1", "app1", "Notes", "chat_1");
        notices.stopped();

        assertEquals("clear", last());
    }

    @Test
    public void whileTheirChatIsOpenOnlyTheWorkingNoticeShows() {
        notices.viewing("app1", "chat_1");
        notices.started("run_1", "app1", "Notes", "chat_1");
        notices.built();

        assertEquals(List.of("WORKING|Working|Thinking|Notes|app1/chat_1", "clear"), shade);
    }

    @Test
    public void anotherChatOpenStillGetsTheEndNotice() {
        notices.viewing("app1", "chat_2");
        notices.started("run_1", "app1", "Notes", "chat_1");
        notices.built();

        assertEquals("DONE|Your app is ready|Tap to install it.|Notes|app1/chat_1", last());
    }

    @Test
    public void openingTheChatClearsItsEndNotice() {
        notices.started("run_1", "app1", "Notes", "chat_1");
        notices.failed("The build failed.");
        notices.viewing(null, null);
        notices.viewing("app1", "chat_2");
        int shown = shade.size();
        notices.viewing("app1", "chat_1");

        assertEquals(shown + 1, shade.size());
        assertEquals("clear", last());
    }

    @Test
    public void stopFromTheNotificationReachesOnlyTheRunItShows() {
        notices.started("run_1", "app1", "Notes", "chat_1");
        notices.built();
        notices.started("run_2", "app1", "Notes", "chat_1");

        assertFalse(notices.stops("run_1"));
        assertTrue(notices.stops("run_2"));
        notices.answered("Which colour?");
        assertFalse(notices.stops("run_2"));
    }

    @Test
    public void aQuestionNeedsTheirInputWhileTheRunStaysAliveAndWorkingAgainAfterTheAnswer() {
        notices.started("run_1", "app1", "Notes", "chat_1");
        notices.asking("Add the library Gson (com.google.code.gson:gson 2.14.0)?");

        assertEquals(
                "ASKING|Needs your input|Add the library Gson (com.google.code.gson:gson 2.14.0)?"
                        + "|Notes|app1/chat_1",
                last());
        assertTrue(notices.stops("run_1"));

        notices.step("Adding library com.google.code.gson:gson");

        assertEquals(
                "WORKING|Working|Adding library com.google.code.gson:gson|Notes|app1/chat_1",
                last());
    }

    @Test
    public void aQuestionOnTheChatOnScreenShowsAsWorkingUntilTheyLeave() {
        notices.viewing("app1", "chat_1");
        notices.started("run_1", "app1", "Notes", "chat_1");
        notices.asking("Add the library Gson?");

        assertEquals("WORKING|Working|Add the library Gson?|Notes|app1/chat_1", last());

        notices.viewing(null, null);

        assertEquals("ASKING|Needs your input|Add the library Gson?|Notes|app1/chat_1", last());
    }

    private String last() {
        return shade.get(shade.size() - 1);
    }
}
