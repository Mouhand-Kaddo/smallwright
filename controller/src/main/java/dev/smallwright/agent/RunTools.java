package dev.smallwright.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tools a run offers the model and the strict parsing of its calls. A parsed call only names
 * what the model asked for; the controller checks every path and runs it.
 */
public final class RunTools {
    public static final String LIST_FILES = "list_files";
    public static final String READ_FILE = "read_file";
    public static final String SEARCH_TEXT = "search_text";
    public static final String WRITE_FILE = "write_file";
    public static final String EDIT_FILE = "edit_file";
    public static final String DELETE_FILE = "delete_file";
    public static final String BUILD = "build";
    public static final String READ_BUILD_ERRORS = "read_build_errors";
    public static final String SEARCH_LIBRARIES = "search_libraries";
    public static final String ADD_LIBRARY = "add_library";
    public static final String REMOVE_LIBRARY = "remove_library";
    public static final String READ_SKILL = "read_skill";
    public static final String ADD_SKILL = "add_skill";
    public static final String MAKE_APP_ICON = "make_app_icon";
    public static final String MAKE_IMAGE = "make_image";
    public static final String ASK = "ask";
    public static final String FETCH_WEB = "fetch_web";
    public static final String LOOKUP_CODE = "lookup_code";
    public static final String READ_CRASH_REPORT = "read_crash_report";
    public static final String LOOK_AT_APP = "look_at_app";
    public static final String WRITE_BRIEF = "write_brief";
    public static final String UPDATE_PLAN = "update_plan";

    public static final int MAX_READ_LINES = 2000;
    public static final int MAX_PATH_CHARS = 300;
    public static final int MAX_QUERY_CHARS = 200;
    public static final int MAX_DESCRIPTION_CHARS = 1000;
    public static final int MAX_URL_CHARS = 2000;
    public static final int MAX_WEB_SEARCHES = 3;

    private static final Map<String, Set<String>> REQUIRED =
            Map.ofEntries(
                    Map.entry(LIST_FILES, Set.of()),
                    Map.entry(READ_FILE, Set.of("path")),
                    Map.entry(SEARCH_TEXT, Set.of("query")),
                    Map.entry(WRITE_FILE, Set.of("path", "content")),
                    Map.entry(EDIT_FILE, Set.of("path", "old_text", "new_text")),
                    Map.entry(DELETE_FILE, Set.of("path")),
                    Map.entry(BUILD, Set.of()),
                    Map.entry(READ_BUILD_ERRORS, Set.of()),
                    Map.entry(SEARCH_LIBRARIES, Set.of("query")),
                    Map.entry(ADD_LIBRARY, Set.of("library")),
                    Map.entry(REMOVE_LIBRARY, Set.of("library")),
                    Map.entry(READ_SKILL, Set.of("name")),
                    Map.entry(ADD_SKILL, Set.of("link")),
                    Map.entry(MAKE_APP_ICON, Set.of("description")),
                    Map.entry(MAKE_IMAGE, Set.of("name", "description", "shape")),
                    Map.entry(ASK, Set.of("questions")),
                    Map.entry(FETCH_WEB, Set.of("url")),
                    Map.entry(LOOKUP_CODE, Set.of("symbol")),
                    Map.entry(READ_CRASH_REPORT, Set.of()),
                    Map.entry(LOOK_AT_APP, Set.of()),
                    Map.entry(WRITE_BRIEF, Set.of("text")),
                    Map.entry(UPDATE_PLAN, Set.of("steps")));
    private static final Map<String, Set<String>> OPTIONAL =
            Map.of(
                    READ_FILE,
                    Set.of("offset", "limit"),
                    SEARCH_TEXT,
                    Set.of("path"),
                    READ_SKILL,
                    Set.of("file"),
                    ADD_SKILL,
                    Set.of("name", "project_only"),
                    FETCH_WEB,
                    Set.of("offset"),
                    LOOKUP_CODE,
                    Set.of("offset"),
                    LOOK_AT_APP,
                    Set.of("activity"));

    private RunTools() {}

    /** One tool call as the model made it. Unused fields are null, or 0 for numbers. */
    public static final class Call {
        public final String id, name, arguments;
        public final String path, query, content, oldText, newText, library, file, link;

        /** The {@code name} argument: a skill's name, or the resource name of a picture. */
        public final String nameArgument;

        public final String description;
        public final ImageClient.Shape shape;
        public final String url;
        public final String symbol;
        public final String activity;

        /** The {@code text} argument of write_brief: the whole project brief. */
        public final String brief;

        public final int offset, limit;
        public final boolean projectOnly;

        private Call(String id, String name, String arguments, JsonObject args) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
            path = text(args, "path", MAX_PATH_CHARS * 4);
            query = text(args, "query", MAX_QUERY_CHARS * 4);
            content = text(args, "content");
            oldText = text(args, "old_text");
            newText = text(args, "new_text");
            library = text(args, "library", MAX_PATH_CHARS * 4);
            nameArgument = text(args, "name", MAX_PATH_CHARS * 4);
            file = text(args, "file", MAX_PATH_CHARS * 4);
            link = text(args, "link", MAX_PATH_CHARS * 4);
            description = text(args, "description", MAX_DESCRIPTION_CHARS * 4);
            url = text(args, "url", MAX_URL_CHARS * 4);
            symbol = text(args, "symbol", MAX_QUERY_CHARS * 4);
            activity = text(args, "activity", MAX_PATH_CHARS * 4);
            brief = text(args, "text");
            offset =
                    args.has("offset") ? ModelClient.integer(args, "offset", Integer.MAX_VALUE) : 1;
            limit =
                    args.has("limit")
                            ? ModelClient.integer(args, "limit", MAX_READ_LINES)
                            : MAX_READ_LINES;
            if (path != null && (path.isEmpty() || path.length() > MAX_PATH_CHARS))
                throw new IllegalArgumentException();
            if (query != null && (query.isEmpty() || query.length() > MAX_QUERY_CHARS))
                throw new IllegalArgumentException();
            if (library != null && (library.isEmpty() || library.length() > MAX_PATH_CHARS))
                throw new IllegalArgumentException();
            if (nameArgument != null
                    && (nameArgument.isEmpty() || nameArgument.length() > MAX_PATH_CHARS))
                throw new IllegalArgumentException();
            if (file != null && (file.isEmpty() || file.length() > MAX_PATH_CHARS))
                throw new IllegalArgumentException();
            if (link != null && (link.isEmpty() || link.length() > MAX_PATH_CHARS))
                throw new IllegalArgumentException();
            if (description != null
                    && (description.trim().isEmpty()
                            || description.length() > MAX_DESCRIPTION_CHARS))
                throw new IllegalArgumentException();
            String shapeName = text(args, "shape", MAX_QUERY_CHARS);
            shape = shapeName == null ? null : ImageClient.Shape.named(shapeName);
            if (shapeName != null && shape == null) throw new IllegalArgumentException();
            if (symbol != null && (symbol.isEmpty() || symbol.length() > MAX_QUERY_CHARS))
                throw new IllegalArgumentException();
            if (url != null && (url.isBlank() || url.length() > MAX_URL_CHARS))
                throw new IllegalArgumentException();
            if (activity != null && (activity.isEmpty() || activity.length() > MAX_PATH_CHARS))
                throw new IllegalArgumentException();
            if (offset < 1 || limit < 1) throw new IllegalArgumentException();
            if (args.has("project_only") && !args.getAsJsonPrimitive("project_only").isBoolean())
                throw new IllegalArgumentException();
            projectOnly = args.has("project_only") && args.get("project_only").getAsBoolean();
        }

        private static String text(JsonObject args, String name) {
            return args.has(name) ? ModelClient.string(args, name) : null;
        }

        private static String text(JsonObject args, String name, int maxBytes) {
            String value = text(args, name);
            if (value != null && value.getBytes(StandardCharsets.UTF_8).length > maxBytes)
                throw new IllegalArgumentException();
            return value;
        }

        /** The call as the assistant message the conversation repeats back to the model. */
        JsonObject toJson() {
            JsonObject function = new JsonObject();
            function.addProperty("name", name);
            function.addProperty("arguments", arguments);
            JsonObject call = new JsonObject();
            call.addProperty("id", id);
            call.addProperty("type", "function");
            call.add("function", function);
            return call;
        }
    }

    /** Fixed structural category only; never contains model arguments or project text. */
    static final class InvalidCall extends IOException {
        final String category;

        InvalidCall(String category) {
            super("Invalid tool call");
            this.category = category;
        }
    }

    static List<Call> parse(JsonArray calls) throws InvalidCall {
        if (calls == null || calls.isEmpty()) throw new InvalidCall("count");
        List<Call> parsed = new ArrayList<>(calls.size());
        Set<String> ids = new java.util.HashSet<>();
        for (JsonElement element : calls) {
            Call call = parse(element);
            if (!ids.add(call.id)) throw new InvalidCall("id");
            parsed.add(call);
        }
        return parsed;
    }

    private static Call parse(JsonElement element) throws InvalidCall {
        String category = "fields";
        try {
            JsonObject call = element.getAsJsonObject();
            if (!call.keySet().containsAll(Set.of("id", "type", "function")))
                throw new IllegalArgumentException();
            category = "type";
            if (!"function".equals(ModelClient.string(call, "type")))
                throw new IllegalArgumentException();
            category = "id";
            String id = ModelClient.string(call, "id");
            if (!id.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException();
            category = "function fields";
            JsonObject function = call.getAsJsonObject("function");
            if (!function.keySet().containsAll(Set.of("name", "arguments")))
                throw new IllegalArgumentException();
            category = "name";
            String name = ModelClient.string(function, "name");
            if (!REQUIRED.containsKey(name)) throw new IllegalArgumentException();
            category = "arguments type";
            String arguments = ModelClient.string(function, "arguments");
            category = "arguments json";
            JsonObject args;
            try {
                args =
                        arguments.trim().isEmpty()
                                ? new JsonObject()
                                : ModelClient.strictJson(arguments).getAsJsonObject();
            } catch (IOException malformed) {
                throw new IllegalArgumentException(malformed);
            }
            category = "arguments fields";
            Set<String> allowed = new java.util.HashSet<>(REQUIRED.get(name));
            allowed.addAll(OPTIONAL.getOrDefault(name, Set.of()));
            if (!args.keySet().containsAll(REQUIRED.get(name))
                    || !allowed.containsAll(args.keySet())) throw new IllegalArgumentException();
            category = "arguments values";
            if (!ASK.equals(name) && !UPDATE_PLAN.equals(name))
                for (JsonElement value : args.asMap().values())
                    if (!(value instanceof JsonPrimitive)) throw new IllegalArgumentException();
            return new Call(id, name, arguments.trim().isEmpty() ? "{}" : arguments, args);
        } catch (RuntimeException malformed) {
            throw new InvalidCall(category);
        }
    }

    /** The tool definitions sent with every request of a run. */
    public static JsonArray schemas() {
        JsonArray tools = new JsonArray();
        tools.add(tool(LIST_FILES, "List every file in the project with its size in bytes."));
        tools.add(
                tool(
                        READ_FILE,
                        "Read a project file's text. offset is the first line to read, counting"
                                + " from 1; limit is how many lines, at most "
                                + MAX_READ_LINES
                                + ".",
                        "path",
                        "string",
                        "Project path, as list_files shows it",
                        "offset",
                        "integer",
                        "First line, from 1 (optional)",
                        "limit",
                        "integer",
                        "Number of lines (optional)"));
        tools.add(
                tool(
                        SEARCH_TEXT,
                        "Find lines containing this exact text in the project's text files.",
                        "query",
                        "string",
                        "Exact text to find, case-sensitive",
                        "path",
                        "string",
                        "Only search paths starting with this (optional)"));
        tools.add(
                tool(
                        WRITE_FILE,
                        "Create a project file, or replace all of its text.",
                        "path",
                        "string",
                        "Project path",
                        "content",
                        "string",
                        "The whole new file text"));
        tools.add(
                tool(
                        EDIT_FILE,
                        "Replace one exact piece of a file's text. old_text must occur exactly once"
                                + " in the file; include enough surrounding lines to make it unique.",
                        "path",
                        "string",
                        "Project path",
                        "old_text",
                        "string",
                        "The exact text to replace",
                        "new_text",
                        "string",
                        "The text to put in its place"));
        tools.add(tool(DELETE_FILE, "Delete a project file.", "path", "string", "Project path"));
        tools.add(
                tool(
                        BUILD,
                        "Save your changes and build the app on the phone. Returns the result and"
                                + " any build errors."));
        tools.add(tool(READ_BUILD_ERRORS, "Show the errors of the last failed build again."));
        tools.add(
                tool(
                        LOOKUP_CODE,
                        "Show what a class really has, from the Android platform files and the"
                                + " project's libraries: its kind, superclass, interfaces,"
                                + " constructors, methods and fields, with constant values. A"
                                + " name that does not exist gets close names. A big class comes"
                                + " in pages.",
                        "symbol",
                        "string",
                        "A class, or a class and member, such as android.graphics.Paint, Paint,"
                                + " Paint.STRIKE_THRU_TEXT_FLAG or Paint#setFlags",
                        "offset",
                        "integer",
                        "First member to show, from 1, for the next page (optional)"));
        tools.add(
                tool(
                        SEARCH_LIBRARIES,
                        "Search Google's Maven repository and Maven Central for libraries by"
                                + " name. It matches names, not what a library does, and does not"
                                + " rank by popularity: search for the name of the library you"
                                + " want and pick the one its makers publish.",
                        "query",
                        "string",
                        "A library's name, such as glide, lottie or material"));
        tools.add(
                tool(
                        ADD_LIBRARY,
                        "Add a library to the project, or change its version. The phone downloads"
                                + " it and what it needs, checks them and returns what was added."
                                + " Without a version, the newest stable version is added.",
                        "library",
                        "string",
                        "group:artifact, or group:artifact:version for an exact version"));
        tools.add(
                tool(
                        REMOVE_LIBRARY,
                        "Remove a library from the project.",
                        "library",
                        "string",
                        "group:artifact"));
        tools.add(
                tool(
                        READ_SKILL,
                        "Read a skill listed under skills: its SKILL.md, or another of its files.",
                        "name",
                        "string",
                        "The skill's name, as skills lists it",
                        "file",
                        "string",
                        "Another file of the skill, as read_skill listed it (optional)"));
        tools.add(
                tool(
                        ADD_SKILL,
                        "Add skills from GitHub to this phone, turned on. Use it when the person"
                                + " asks for a skill, by link or by name. For a name, use the"
                                + " GitHub link you know for it. Skills are for every project"
                                + " unless the person asks for this project only. You may read"
                                + " what you added with read_skill in this run.",
                        "link",
                        "string",
                        "GitHub link to a repository, a folder or a SKILL.md, such as"
                                + " https://github.com/mattpocock/skills",
                        "name",
                        "string",
                        "Add only the skill with this name from that link (optional)",
                        "project_only",
                        "boolean",
                        "true only when the person asks for the skill in this project only"
                                + " (optional)"));
        tools.add(
                tool(
                        MAKE_APP_ICON,
                        "Make the app's launcher icon with an image model and point"
                                + " AndroidManifest.xml at it. Call it only when the person asks"
                                + " for an icon or a logo: each call is a paid image request. It"
                                + " replaces the icon made before.",
                        "description",
                        "string",
                        "The picture in one or two sentences: the subject, colours and style"));
        tools.add(
                tool(
                        MAKE_IMAGE,
                        "Make a picture the app shows, such as a background, an illustration,"
                                + " art for an empty screen or a photo of an item, with an image"
                                + " model. Call it only when the person asked for pictures or"
                                + " agreed to them: each call is a paid image request. It returns"
                                + " the drawable to use in layouts and code. Making a name again"
                                + " replaces its picture.",
                        "name",
                        "string",
                        "Lower-case resource name: letters, digits and _, such as beach_background",
                        "description",
                        "string",
                        "The picture in one to three sentences: the subject, colours, style and"
                                + " mood",
                        "shape",
                        "string",
                        "square, wide (16:9, such as a header or background on its side) or tall"
                                + " (9:16, such as a full-screen background)"));
        tools.add(
                tool(
                        FETCH_WEB,
                        "Read one public web page as text: documentation, a README, an Android"
                                + " guide or an API reference. A long page comes in parts; offset"
                                + " is the first character to read, counting from 1.",
                        "url",
                        "string",
                        "The page's https:// address",
                        "offset",
                        "integer",
                        "First character, from 1 (optional)"));
        tools.add(askTool());
        tools.add(
                tool(
                        WRITE_BRIEF,
                        "Save the project brief: the person's decisions about the app in short"
                                + " plain words. It replaces the whole brief, and every later chat"
                                + " in this project starts with it as project_brief.",
                        "text",
                        "string",
                        "The whole brief, at most "
                                + dev.smallwright.security.ContextFilter.MAX_BRIEF_BYTES
                                + " bytes: plain words, with **bold**, -"
                                + " bullets and 1. numbered lists if they help"));
        tools.add(planTool());
        tools.add(
                tool(
                        READ_CRASH_REPORT,
                        "Read the latest crash report the person sent from the app with Tell the"
                                + " builder: the exception classes and the code lines, without"
                                + " messages or values."));
        tools.add(
                tool(
                        LOOK_AT_APP,
                        "See the installed app: the phone opens it for a moment, and a picture of"
                                + " its screen follows in the next message. It needs the current"
                                + " version installed and a model that reads pictures.",
                        "activity",
                        "string",
                        "The activity to open, as AndroidManifest.xml names it, such as"
                                + " .DetailActivity (optional; the launcher activity without it)"));
        return tools;
    }

    private static JsonObject askTool() {
        JsonObject question = new JsonObject();
        question.addProperty("type", "object");
        question.addProperty("additionalProperties", false);
        JsonObject fields = new JsonObject();
        fields.add(
                "question",
                text(
                        "One short question in everyday words, worded so that yes accepts your"
                                + " recommendation"));
        fields.add("recommended", text("The answer you recommend, in a few plain words"));
        JsonObject others = new JsonObject();
        others.addProperty("type", "array");
        others.addProperty(
                "description",
                "The other answers to choose from, in a few plain words each. The person can"
                        + " always type their own, so do not add one for that.");
        others.add("items", text("One answer"));
        others.addProperty("minItems", 1);
        others.addProperty("maxItems", QuestionRound.MAX_CHOICES - 1);
        fields.add("other_choices", others);
        fields.add("reason", text("Why you recommend it, in one short sentence (optional)"));
        question.add("properties", fields);
        JsonArray required = new JsonArray();
        required.add("question");
        required.add("recommended");
        required.add("other_choices");
        question.add("required", required);
        JsonObject questions = new JsonObject();
        questions.addProperty("type", "array");
        questions.addProperty("description", "The questions of this round, in order");
        questions.add("items", question);
        questions.addProperty("minItems", 1);
        questions.addProperty("maxItems", QuestionRound.MAX_QUESTIONS);
        JsonObject properties = new JsonObject();
        properties.add("questions", questions);
        return tool(
                ASK,
                "Ask the person one round of questions, each with answers to tap and the one you"
                        + " recommend. Call it alone, as the only tool call in its reply; any words"
                        + " you write with it are shown above the questions. It ends the run: the"
                        + " person's answers come as their next message.",
                properties);
    }

    private static JsonObject planTool() {
        JsonObject step = new JsonObject();
        step.addProperty("type", "object");
        step.addProperty("additionalProperties", false);
        JsonObject fields = new JsonObject();
        JsonObject text = text("One step in a few plain words the person understands");
        text.addProperty("maxLength", RunPlan.MAX_STEP_CHARS);
        fields.add("text", text);
        JsonObject status = text("todo, doing for the step you work on now, or done");
        JsonArray statuses = new JsonArray();
        for (RunPlan.Status value : RunPlan.Status.values())
            statuses.add(value.name().toLowerCase(java.util.Locale.ROOT));
        status.add("enum", statuses);
        fields.add("status", status);
        step.add("properties", fields);
        JsonArray required = new JsonArray();
        required.add("text");
        required.add("status");
        step.add("required", required);
        JsonObject steps = new JsonObject();
        steps.addProperty("type", "array");
        steps.addProperty("description", "Every step of the job, in order");
        steps.add("items", step);
        steps.addProperty("minItems", 1);
        steps.addProperty("maxItems", RunPlan.MAX_STEPS);
        JsonObject properties = new JsonObject();
        properties.add("steps", steps);
        return tool(
                UPDATE_PLAN,
                "Show the person your plan for a big job as a checklist they watch while you work."
                        + " Each call replaces the whole list: send every step again with where it"
                        + " stands.",
                properties);
    }

    private static JsonObject text(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        return property;
    }

    /**
     * OpenRouter's own web search, run by OpenRouter within a request on Exa at $0.007 a search, at
     * most three searches a request; the reply's cost includes it.
     */
    static JsonObject webSearch() {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("engine", "exa");
        parameters.addProperty("mode", "auto");
        parameters.addProperty("max_results", 5);
        parameters.addProperty("max_uses", MAX_WEB_SEARCHES);
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "openrouter:web_search");
        tool.add("parameters", parameters);
        return tool;
    }

    private static JsonObject tool(String name, String description, String... fields) {
        JsonObject properties = new JsonObject();
        for (int index = 0; index < fields.length; index += 3) {
            JsonObject property = new JsonObject();
            property.addProperty("type", fields[index + 1]);
            property.addProperty("description", fields[index + 2]);
            properties.add(fields[index], property);
        }
        return tool(name, description, properties);
    }

    private static JsonObject tool(String name, String description, JsonObject properties) {
        JsonArray required = new JsonArray();
        for (String field : properties.keySet())
            if (REQUIRED.get(name).contains(field)) required.add(field);
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.addProperty("additionalProperties", false);
        parameters.add("properties", properties);
        parameters.add("required", required);
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        function.add("parameters", parameters);
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        return tool;
    }
}
