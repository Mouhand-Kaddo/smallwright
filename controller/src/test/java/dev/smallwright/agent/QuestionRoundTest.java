package dev.smallwright.agent;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;

/** The ask tool's round: its schema, strict argument checks, saved form and answer message. */
public final class QuestionRoundTest {
    private static JsonObject question(String text, String recommended, String... others) {
        JsonObject question = new JsonObject();
        question.addProperty("question", text);
        question.addProperty("recommended", recommended);
        JsonArray choices = new JsonArray();
        for (String other : others) choices.add(other);
        question.add("other_choices", choices);
        return question;
    }

    private static String round(JsonObject... questions) {
        JsonArray list = new JsonArray();
        for (JsonObject question : questions) list.add(question);
        JsonObject arguments = new JsonObject();
        arguments.add("questions", list);
        return arguments.toString();
    }

    private static String refusal(String arguments) {
        try {
            QuestionRound.fromCall("", arguments);
        } catch (QuestionRound.Refused refused) {
            return refused.getMessage();
        }
        throw new AssertionError("The round was accepted: " + arguments);
    }

    private static JsonObject askSchema() {
        for (JsonElement tool : RunTools.schemas()) {
            JsonObject function = tool.getAsJsonObject().getAsJsonObject("function");
            if (RunTools.ASK.equals(function.get("name").getAsString())) return function;
        }
        throw new AssertionError("No ask tool");
    }

    @Test
    public void wordsTooLongForTheChatToSaveAreRefusedWithAFixableMessage() throws Exception {
        String arguments = round(question("Who will use it?", "Just me", "My family"));
        for (String intro : List.of("x".repeat(1_100_000), "a" + "\u0001".repeat(200_000) + "b")) {
            QuestionRound.Refused refused =
                    assertThrows(
                            QuestionRound.Refused.class,
                            () -> QuestionRound.fromCall(intro, arguments));
            assertTrue(refused.getMessage(), refused.getMessage().startsWith("Not asked: "));
            assertTrue(refused.getMessage(), refused.getMessage().contains("shorter"));
        }
        QuestionRound accepted = QuestionRound.fromCall("y".repeat(500_000), arguments);
        assertEquals(accepted.intro, QuestionRound.fromSaved(accepted.saved()).intro);
    }

    @Test
    public void theAskToolOffersAListOfQuestionsEachWithARecommendedAnswerAndOtherChoices() {
        JsonObject parameters = askSchema().getAsJsonObject("parameters");
        assertEquals("[\"questions\"]", parameters.get("required").toString());
        assertFalse(parameters.get("additionalProperties").getAsBoolean());
        JsonObject questions =
                parameters.getAsJsonObject("properties").getAsJsonObject("questions");
        assertEquals("array", questions.get("type").getAsString());
        assertEquals(QuestionRound.MAX_QUESTIONS, questions.get("maxItems").getAsInt());
        JsonObject item = questions.getAsJsonObject("items");
        assertEquals(
                "[\"question\",\"recommended\",\"other_choices\"]",
                item.get("required").toString());
        assertFalse(item.get("additionalProperties").getAsBoolean());
        JsonObject others = item.getAsJsonObject("properties").getAsJsonObject("other_choices");
        assertEquals("string", others.getAsJsonObject("items").get("type").getAsString());
        assertEquals(1, others.get("minItems").getAsInt());
        assertEquals(QuestionRound.MAX_CHOICES - 1, others.get("maxItems").getAsInt());
        assertTrue(item.getAsJsonObject("properties").has("reason"));
    }

    @Test
    public void aRoundKeepsTheRecommendedAnswerFirstAndTheReasonWhenGiven() throws Exception {
        JsonObject first = question("Who will use the app?", "Just me", "My family", "Anyone");
        first.addProperty("reason", "Nothing to sign in to.");
        QuestionRound round =
                QuestionRound.fromCall(
                        "A few questions first.",
                        round(first, question("Should it work offline?", "Yes", "No")));
        assertEquals("A few questions first.", round.intro);
        assertEquals(2, round.questions.size());
        QuestionRound.Question who = round.questions.get(0);
        assertEquals("Who will use the app?", who.text);
        assertEquals(List.of("Just me", "My family", "Anyone"), who.choices);
        assertEquals("Nothing to sign in to.", who.reason);
        assertEquals("", round.questions.get(1).reason);
    }

    @Test
    public void wordsAreTrimmedAndABlankReasonMeansNone() throws Exception {
        JsonObject asked = question("  Dark screen?  ", " Yes ", " No ");
        asked.addProperty("reason", "  ");
        QuestionRound.Question dark = QuestionRound.fromCall("", round(asked)).questions.get(0);
        assertEquals("Dark screen?", dark.text);
        assertEquals(List.of("Yes", "No"), dark.choices);
        assertEquals("", dark.reason);
    }

    @Test
    public void badRoundsAreRefusedWithWordsTheModelCanActOn() {
        assertTrue(refusal("{\"questions\":[]}").contains("1 to " + QuestionRound.MAX_QUESTIONS));
        assertTrue(refusal("{\"questions\":\"Who?\"}").contains("list"));
        JsonObject[] six = new JsonObject[QuestionRound.MAX_QUESTIONS + 1];
        for (int index = 0; index < six.length; index++)
            six[index] = question("Question " + index + "?", "Yes", "No");
        assertTrue(refusal(round(six)).contains("1 to " + QuestionRound.MAX_QUESTIONS));
        assertTrue(refusal(round(question("Who?", "Me"))).contains("question 1"));
        assertTrue(
                refusal(round(question("Who?", "A", "B", "C", "D", "E", "F")))
                        .contains(QuestionRound.MAX_CHOICES + ""));
        assertTrue(refusal(round(question(" ", "Yes", "No"))).contains("question 1"));
        assertTrue(refusal(round(question("Who?", "", "No"))).contains("recommended"));
        assertTrue(refusal(round(question("Who?", "Yes", " "))).contains("other_choices"));
        assertTrue(refusal(round(question("Who?", "Yes", "yes "))).contains("twice"));
        assertTrue(refusal(round(question("Who?\nAnd why?", "Yes", "No"))).contains("one line"));
        assertTrue(
                refusal(round(question("a".repeat(QuestionRound.MAX_QUESTION_CHARS + 1), "Y", "N")))
                        .contains(QuestionRound.MAX_QUESTION_CHARS + ""));
        assertTrue(
                refusal(
                                round(
                                        question(
                                                "Who?",
                                                "a".repeat(QuestionRound.MAX_CHOICE_CHARS + 1),
                                                "N")))
                        .contains(QuestionRound.MAX_CHOICE_CHARS + ""));
        JsonObject longReason = question("Who?", "Yes", "No");
        longReason.addProperty("reason", "a".repeat(QuestionRound.MAX_REASON_CHARS + 1));
        assertTrue(refusal(round(longReason)).contains(QuestionRound.MAX_REASON_CHARS + ""));
        JsonObject extra = question("Who?", "Yes", "No");
        extra.addProperty("default", 0);
        assertTrue(refusal(round(extra)).contains("default"));
        JsonObject numbered = question("Who?", "Yes", "No");
        numbered.addProperty("recommended", 1);
        assertTrue(refusal(round(numbered)).contains("recommended"));
        JsonObject second = question("Why?", "Yes", "No");
        second.remove("other_choices");
        assertTrue(refusal(round(question("Who?", "Yes", "No"), second)).contains("question 2"));
    }

    @Test
    public void theSavedRoundReadsBackTheSame() throws Exception {
        JsonObject first = question("Who will use the app?", "Just me", "My family");
        first.addProperty("reason", "Simplest.");
        QuestionRound asked = QuestionRound.fromCall("Before I build:", round(first));
        QuestionRound read = QuestionRound.fromSaved(asked.saved());
        assertEquals(asked.saved(), read.saved());
        assertEquals("Before I build:", read.intro);
        assertEquals(List.of("Just me", "My family"), read.questions.get(0).choices);
        assertEquals("Simplest.", read.questions.get(0).reason);
    }

    @Test
    public void aSavedRoundThatBreaksTheRulesIsNotRead() throws Exception {
        String saved = QuestionRound.fromCall("", round(question("Who?", "Me", "You"))).saved();
        assertThrows(
                IllegalArgumentException.class,
                () -> QuestionRound.fromSaved(saved.replace("\"You\"", "\"me\"")));
        assertThrows(IllegalArgumentException.class, () -> QuestionRound.fromSaved("Who?"));
        assertThrows(IllegalArgumentException.class, () -> QuestionRound.fromSaved("{}"));
        JsonObject withExtra = JsonParser.parseString(saved).getAsJsonObject();
        withExtra.addProperty("answered", true);
        assertThrows(
                IllegalArgumentException.class,
                () -> QuestionRound.fromSaved(withExtra.toString()));
    }

    @Test
    public void theHistoryShowsTheModelEveryQuestionChoiceAndRecommendation() throws Exception {
        JsonObject first = question("Who will use the app?", "Just me", "My family");
        first.addProperty("reason", "Nothing to sign in to.");
        String history =
                QuestionRound.fromCall(
                                "Before I build:", round(first, question("Offline?", "Yes", "No")))
                        .historyText();
        assertEquals(
                "Before I build:\n"
                        + "Questions asked with ask:\n"
                        + "1. Who will use the app?\n"
                        + "- Just me (recommended: Nothing to sign in to.)\n"
                        + "- My family\n"
                        + "2. Offline?\n"
                        + "- Yes (recommended)\n"
                        + "- No",
                history);
    }

    @Test
    public void theAnswerMessageListsEachQuestionWithItsAnswerThenAnyTypedWords() throws Exception {
        QuestionRound round =
                QuestionRound.fromCall(
                        "",
                        round(
                                question("Who will use the app?", "Just me", "My family"),
                                question("Offline?", "Yes", "No")));
        assertEquals(
                "1. Who will use the app?: My family\n2. Offline?: Yes",
                round.answers(List.of("My family", "Yes"), ""));
        assertEquals(
                "1. Who will use the app?: My dog\n2. Offline?: Yes\n\nAlso make it blue.",
                round.answers(List.of(" My\n dog ", "Yes"), "  Also make it blue. "));
        assertThrows(IllegalArgumentException.class, () -> round.answers(List.of("Yes", " "), ""));
        assertThrows(IllegalArgumentException.class, () -> round.answers(List.of("Yes"), ""));
    }
}
