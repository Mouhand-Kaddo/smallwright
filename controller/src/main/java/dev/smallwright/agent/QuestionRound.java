package dev.smallwright.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One round of questions the builder put to the person with the ask tool: each question has the
 * answer it recommends first, then the other choices. The words it wrote with the call come first.
 */
public final class QuestionRound {
    public static final int MAX_QUESTIONS = 5;
    public static final int MAX_CHOICES = 5;
    public static final int MAX_QUESTION_CHARS = 200;
    public static final int MAX_CHOICE_CHARS = 80;
    public static final int MAX_REASON_CHARS = 160;
    private static final int MAX_SAVED_BYTES = 1024 * 1024;
    private static final Set<String> QUESTION_FIELDS =
            Set.of("question", "recommended", "other_choices", "reason");

    /** One question, with the recommended answer as the first choice. */
    public static final class Question {
        public final String text;
        public final List<String> choices;

        /** Why the first choice is recommended; empty when the builder gave no reason. */
        public final String reason;

        private Question(String text, List<String> choices, String reason) {
            this.text = text;
            this.choices = List.copyOf(choices);
            this.reason = reason;
        }
    }

    /** A round the controller does not ask; the message tells the model what to fix. */
    public static final class Refused extends Exception {
        Refused(String message) {
            super(message);
        }
    }

    public final String intro;
    public final List<Question> questions;

    private QuestionRound(String intro, List<Question> questions) {
        this.intro = intro;
        this.questions = List.copyOf(questions);
    }

    /** The round from an ask call's arguments, with the words the model wrote beside the call. */
    public static QuestionRound fromCall(String intro, String arguments) throws Refused {
        JsonObject parsed;
        try {
            parsed = ModelClient.strictJson(arguments).getAsJsonObject();
        } catch (IOException | RuntimeException malformed) {
            throw new Refused("Not asked: the arguments are not one JSON object.");
        }
        QuestionRound round = read(intro.trim(), parsed.get("questions"));
        int saved = round.saved().getBytes(StandardCharsets.UTF_8).length;
        if (saved > MAX_SAVED_BYTES)
            throw new Refused(
                    "Not asked: the words written with ask take "
                            + saved
                            + " bytes to save, and a round holds at most "
                            + MAX_SAVED_BYTES
                            + ". Write them much shorter.");
        return round;
    }

    /** A round as {@link #saved} wrote it, held to the same rules as the call. */
    public static QuestionRound fromSaved(String saved) {
        try {
            JsonObject parsed = ModelClient.strictJson(saved, MAX_SAVED_BYTES).getAsJsonObject();
            if (!Set.of("intro", "questions").equals(parsed.keySet()))
                throw new IllegalArgumentException("Invalid question round");
            return read(ModelClient.string(parsed, "intro"), parsed.get("questions"));
        } catch (IOException | Refused | RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid question round", invalid);
        }
    }

    private static QuestionRound read(String intro, JsonElement questions) throws Refused {
        String shape =
                "Not asked: questions must be a list of 1 to "
                        + MAX_QUESTIONS
                        + " questions, each with question, recommended and other_choices.";
        if (questions == null || !questions.isJsonArray()) throw new Refused(shape);
        JsonArray list = questions.getAsJsonArray();
        if (list.isEmpty() || list.size() > MAX_QUESTIONS) throw new Refused(shape);
        List<Question> read = new ArrayList<>();
        for (JsonElement element : list) read.add(question(element, read.size() + 1));
        return new QuestionRound(intro, read);
    }

    private static Question question(JsonElement element, int number) throws Refused {
        String where = "Not asked: question " + number;
        if (!element.isJsonObject()) throw new Refused(where + " is not an object.");
        JsonObject question = element.getAsJsonObject();
        for (String field : question.keySet())
            if (!QUESTION_FIELDS.contains(field))
                throw new Refused(where + " has a field ask does not take: " + field + ".");
        String text = line(question, "question", MAX_QUESTION_CHARS, where);
        if (text.isEmpty()) throw new Refused(where + " has no question words.");
        List<String> choices = new ArrayList<>();
        String recommended = line(question, "recommended", MAX_CHOICE_CHARS, where);
        if (recommended.isEmpty()) throw new Refused(where + " has an empty recommended answer.");
        choices.add(recommended);
        JsonElement others = question.get("other_choices");
        if (others == null
                || !others.isJsonArray()
                || others.getAsJsonArray().isEmpty()
                || others.getAsJsonArray().size() > MAX_CHOICES - 1)
            throw new Refused(
                    where
                            + " needs 2 to "
                            + MAX_CHOICES
                            + " choices in all: recommended and 1 to "
                            + (MAX_CHOICES - 1)
                            + " other_choices.");
        Set<String> seen = new HashSet<>();
        seen.add(recommended.toLowerCase(Locale.ROOT));
        for (JsonElement other : others.getAsJsonArray()) {
            String choice = line(other, "other_choices", MAX_CHOICE_CHARS, where);
            if (choice.isEmpty())
                throw new Refused(where + " has an empty choice in other_choices.");
            if (!seen.add(choice.toLowerCase(Locale.ROOT)))
                throw new Refused(where + " offers the same choice twice: " + choice + ".");
            choices.add(choice);
        }
        String reason =
                question.has("reason") && !question.get("reason").isJsonNull()
                        ? line(question, "reason", MAX_REASON_CHARS, where)
                        : "";
        return new Question(text, choices, reason);
    }

    private static String line(JsonObject object, String field, int maxChars, String where)
            throws Refused {
        if (!object.has(field)) throw new Refused(where + " has no " + field + ".");
        return line(object.get(field), field, maxChars, where);
    }

    private static String line(JsonElement value, String field, int maxChars, String where)
            throws Refused {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new Refused(where + ": " + field + " must be text.");
        String text = value.getAsString().trim();
        if (text.codePoints().anyMatch(Character::isISOControl))
            throw new Refused(where + ": " + field + " must be one line.");
        if (text.length() > maxChars)
            throw new Refused(
                    where + ": " + field + " is longer than " + maxChars + " characters.");
        return text;
    }

    /** The round as the chat keeps it. */
    public String saved() {
        JsonArray list = new JsonArray();
        for (Question question : questions) {
            JsonObject saved = new JsonObject();
            saved.addProperty("question", question.text);
            saved.addProperty("recommended", question.choices.get(0));
            JsonArray others = new JsonArray();
            for (String choice : question.choices.subList(1, question.choices.size()))
                others.add(choice);
            saved.add("other_choices", others);
            if (!question.reason.isEmpty()) saved.addProperty("reason", question.reason);
            list.add(saved);
        }
        JsonObject round = new JsonObject();
        round.addProperty("intro", intro);
        round.add("questions", list);
        return round.toString();
    }

    /** The round as later requests show it to the model, in the chat history. */
    public String historyText() {
        StringBuilder text = new StringBuilder();
        if (!intro.isEmpty()) text.append(intro).append('\n');
        text.append("Questions asked with ask:");
        for (int index = 0; index < questions.size(); index++) {
            Question question = questions.get(index);
            text.append('\n').append(index + 1).append(". ").append(question.text);
            text.append("\n- ").append(question.choices.get(0)).append(" (recommended");
            if (!question.reason.isEmpty()) text.append(": ").append(question.reason);
            text.append(')');
            for (String choice : question.choices.subList(1, question.choices.size()))
                text.append("\n- ").append(choice);
        }
        return text.toString();
    }

    /**
     * The person's reply: a line per question with the answer they gave, in order, then any words
     * they typed in the prompt.
     */
    public String answers(List<String> given, String typed) {
        if (given.size() != questions.size())
            throw new IllegalArgumentException("One answer per question");
        StringBuilder message = new StringBuilder();
        for (int index = 0; index < questions.size(); index++) {
            String answer = given.get(index).trim().replaceAll("\\s+", " ");
            if (answer.isEmpty()) throw new IllegalArgumentException("Empty answer");
            if (index > 0) message.append('\n');
            message.append(index + 1)
                    .append(". ")
                    .append(questions.get(index).text)
                    .append(": ")
                    .append(answer);
        }
        if (!typed.isBlank()) message.append("\n\n").append(typed.trim());
        return message.toString();
    }
}
