package dev.smallwright.controller.ui;

import static org.junit.Assert.*;

import dev.smallwright.agent.QuestionRound;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;
import org.junit.Test;

/** The question card's picks: the recommended answers preselected, an own answer typed. */
public final class RoundAnswersTest {
    private static QuestionRound round() throws Exception {
        return QuestionRound.fromCall(
                "Before I build:",
                "{\"questions\":["
                        + "{\"question\":\"Who will use it?\",\"recommended\":\"Just me\","
                        + "\"other_choices\":[\"My family\",\"Anyone\"]},"
                        + "{\"question\":\"Offline?\",\"recommended\":\"Yes\","
                        + "\"other_choices\":[\"No\"]}]}");
    }

    @Test
    public void untouchedTheCardSendsEveryRecommendedAnswer() throws Exception {
        RoundAnswers answers = new RoundAnswers(round());
        assertEquals(0, answers.picked(0));
        assertTrue(answers.getComplete());
        assertEquals("1. Who will use it?: Just me\n2. Offline?: Yes", answers.message(""));
    }

    @Test
    public void aPickedChoiceReplacesTheRecommendedOne() throws Exception {
        RoundAnswers answers = new RoundAnswers(round());
        answers.pick(0, 2);
        answers.pick(1, 1);
        assertEquals(2, answers.picked(0));
        assertEquals("1. Who will use it?: Anyone\n2. Offline?: No", answers.message(""));
    }

    @Test
    public void somethingElseSendsTheTypedWordsAndNeedsSomeTyped() throws Exception {
        RoundAnswers answers = new RoundAnswers(round());
        answers.pick(0, 3);
        assertTrue(answers.typing(0));
        assertFalse(answers.typing(1));
        assertFalse(answers.getComplete());
        answers.getOwn().get(0).setText("My class at school");
        assertTrue(answers.getComplete());
        assertEquals(
                "1. Who will use it?: My class at school\n2. Offline?: Yes\n\nMake it green.",
                answers.message("Make it green."));
    }

    @Test
    public void wordsTypedForSomethingElseAreKeptWhenAnotherChoiceIsPickedAndBack()
            throws Exception {
        RoundAnswers answers = new RoundAnswers(round());
        answers.pick(0, 3);
        answers.getOwn().get(0).setText("My class");
        answers.pick(0, 1);
        assertEquals("1. Who will use it?: My family\n2. Offline?: Yes", answers.message(""));
        answers.pick(0, 3);
        assertTrue(answers.message("").startsWith("1. Who will use it?: My class\n"));
    }

    @Test
    public void sendingAgainAfterARefusedSendSendsTheAnswersOnceWithThePersonsOwnWords()
            throws Exception {
        UiState ui = new UiState();
        List<String> sent = new ArrayList<>();
        ui.setSendAnswers(
                message -> {
                    sent.add(message);
                    return Unit.INSTANCE;
                });
        ui.getModelRequest().setText("Make it green.");
        RoundAnswers answers = new RoundAnswers(round());

        QuestionCardKt.sendAnswers(ui, answers);
        ui.getModelRequest().setText(ui.getModelRequest().getText() + " And big.");
        answers.pick(1, 1);
        QuestionCardKt.sendAnswers(ui, answers);

        assertEquals(
                List.of(
                        "1. Who will use it?: Just me\n2. Offline?: Yes\n\nMake it green.",
                        "1. Who will use it?: Just me\n2. Offline?: No\n\nMake it green. And big."),
                sent);
        assertEquals("Make it green. And big.", ui.getModelRequest().getText());
    }

    @Test
    public void sendLooksLikeTheNextStepWhileARoundWaitsOnlyWhenThereAreWordsToSend() {
        UiState ui = new UiState();
        Turn person = new Turn("user", 0, "Make a tracker");
        ui.setTranscript(List.of(person, new Turn("question", 0, "{}")));
        assertFalse(ChatKt.sendIsNext(ui));
        ui.getModelRequest().setText("Make it blue");
        assertTrue(ChatKt.sendIsNext(ui));
        ui.getModelRequest().setText("");
        ui.setTranscript(List.of(person, new Turn("assistant", 0, "Here it is.")));
        assertTrue(ChatKt.sendIsNext(ui));
    }

    @Test
    public void onlyTheLatestRoundWithNoReplyAfterItIsOpen() {
        Turn asked = new Turn("question", 0, "{}");
        Turn person = new Turn("user", 0, "Make a tracker");
        Turn steps = new Turn("tool", 0, "done\tThought\t");
        Turn status = new Turn("status", 0, "Ready.");
        Turn reply = new Turn("assistant", 0, "Here it is.");
        assertEquals(2, QuestionCardKt.openRound(List.of(person, steps, asked, status), false));
        assertEquals(-1, QuestionCardKt.openRound(List.of(person, steps, asked, status), true));
        assertEquals(-1, QuestionCardKt.openRound(List.of(asked, person, steps, reply), false));
        assertEquals(3, QuestionCardKt.openRound(List.of(asked, person, steps, asked), false));
        assertEquals(-1, QuestionCardKt.openRound(List.of(person, reply), false));
    }
}
