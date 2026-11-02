package dev.smallwright.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.Test;

public final class ContextFilterTest {
    private static final String PACKAGE = "dev.smallwright.generated.counter";

    @Test
    public void firstRequestCarriesTheContractRequestAndListingWithSecretsRedacted() {
        String request =
                ContextFilter.firstRequest(
                        "Add a counter. api_key=dummy-prompt-secret sk-test-DUMMYAPIKEY123456789",
                        Map.of(
                                "res/layout/empty.xml", 7,
                                "java/dev/smallwright/generated/counter/MainActivity.java", 120),
                        PACKAGE);
        JsonObject parsed = JsonParser.parseString(request).getAsJsonObject();
        assertEquals(PACKAGE, parsed.get("package").getAsString());
        assertEquals(
                "java/dev/smallwright/generated/counter/",
                parsed.get("java_directory").getAsString());
        assertEquals(
                JsonParser.parseString(
                        "[{\"path\":\"java/dev/smallwright/generated/counter/MainActivity.java\","
                                + "\"bytes\":120},{\"path\":\"res/layout/empty.xml\",\"bytes\":7}]"),
                parsed.get("files"));
        String userRequest = parsed.get("user_request").getAsString();
        assertTrue(userRequest.startsWith("Add a counter."));
        assertFalse(request.contains("dummy-prompt-secret"));
        assertFalse(request.contains("DUMMYAPIKEY123456789"));
    }

    @Test
    public void contractTellsTheModelItOwnsTheManifestWithinTheBuildersRules() {
        String contract =
                JsonParser.parseString(ContextFilter.firstRequest("Hi", Map.of(), PACKAGE))
                        .getAsJsonObject()
                        .get("build_contract")
                        .getAsString();
        for (String tool :
                new String[] {
                    "list_files",
                    "read_file",
                    "search_text",
                    "write_file",
                    "edit_file",
                    "delete_file",
                    "build",
                    "read_build_errors",
                    "search_libraries",
                    "add_library",
                    "remove_library"
                }) assertTrue(tool, contract.contains(tool));
        assertTrue(
                contract.contains(
                        "Before you use a class, method or constant you are not sure exists, check"
                                + " it with lookup_code, and look up the exact name a build error"
                                + " complains about."));
        assertTrue(contract.contains("any public Android API 36 platform API"));
        assertTrue(contract.contains("no var, records, switch expressions or text blocks"));
        assertTrue(contract.contains("as many classes, packages and resource files"));
        assertTrue(contract.contains("Write Java, not Kotlin, and no build files"));
        assertTrue(contract.contains("plain Java libraries (JAR files)"));
        assertTrue(contract.contains("Android libraries (AAR files, such as AndroidX"));
        assertTrue(contract.contains("manifest entries are merged into the app's"));
        assertFalse(contract.contains("Android libraries (AAR files, such as most"));
        assertTrue(contract.contains("native code (.so files) work"));
        assertTrue(contract.contains("annotation processors"));
        assertTrue(contract.contains("You own the app's AndroidManifest.xml"));
        assertTrue(contract.contains("android.permission.INTERNET"));
        assertTrue(contract.contains("minSdk 31, targetSdk 36 and allowBackup=false"));
        assertTrue(contract.contains("sharedUserId, debuggable, testOnly, a different package"));
        assertFalse(contract.contains("you cannot change it"));
        assertFalse(contract.contains("no internet access"));
        assertTrue(contract.contains("build again until the build succeeds"));
        assertTrue(contract.contains("short plain summary of what the app now does"));
        assertTrue(contract.contains("without file, class or library names unless the person"));
        assertTrue(contract.contains("question at the end only when you need a decision"));
        assertFalse(contract.contains("without code. If you need a decision"));
        assertFalse(contract.contains("replace_source"));
        assertFalse(contract.contains("Only these APIs compile"));
    }

    @Test
    public void contractNamesTheAppAndMakesAnIconOnlyWhenThePersonAsks() {
        String contract =
                JsonParser.parseString(ContextFilter.firstRequest("Hi", Map.of(), PACKAGE))
                        .getAsJsonObject()
                        .get("build_contract")
                        .getAsString();
        assertTrue(contract.contains("android:label on <application>"));
        assertTrue(contract.contains("the name the person gave"));
        assertTrue(contract.contains("still Smallwright App"));
        assertTrue(contract.contains("project_name"));
        assertTrue(contract.contains("pick a short fitting name"));
        assertTrue(contract.contains("tell the person the name"));
        assertTrue(contract.contains("make_app_icon"));
        assertTrue(contract.contains("only when the person asks for an icon"));
    }

    @Test
    public void contractInterviewsBeforeBuildingFindsLibrariesAndMakesPicturesOnlyWhenAgreed() {
        String contract =
                JsonParser.parseString(ContextFilter.firstRequest("Hi", Map.of(), PACKAGE))
                        .getAsJsonObject()
                        .get("build_contract")
                        .getAsString();
        assertTrue(contract.contains("interview the person"));
        assertTrue(contract.contains("How much you ask depends on what you still do not know"));
        assertTrue(contract.contains("Never build a new app before the person says yes"));
        assertTrue(contract.contains("even when the request settled everything"));
        assertTrue(contract.contains("as a round of its own after every other answer is in"));
        assertFalse(contract.contains("three or four questions"));
        assertTrue(contract.contains("Ask in rounds with the ask tool"));
        assertTrue(contract.contains("word them as what the app gains"));
        assertTrue(contract.contains("worded so that yes accepts the answer you"));
        assertTrue(contract.contains("just build it"));
        assertTrue(contract.contains("paid per picture"));
        assertTrue(contract.contains("add it yourself without asking"));
        assertTrue(contract.contains("search for the name you know"));
        assertTrue(contract.contains("make_image"));
        assertTrue(contract.contains("asked for pictures or agreed to them"));
        assertFalse(contract.contains("call no tools"));
    }

    @Test
    public void theProjectBriefGoesBesideTheProjectNameRedactedAndOnlyWhenThereIsOne() {
        String brief = "For: me.\n- Dark colours\n- password=hunter2-dummy";
        JsonObject parsed =
                JsonParser.parseString(
                                ContextFilter.firstRequest(
                                        "Hi", Map.of(), PACKAGE, "Water", brief, Map.of(), null,
                                        null, null))
                        .getAsJsonObject();
        String sent = parsed.get("project_brief").getAsString();
        assertTrue(sent.startsWith("For: me.\n- Dark colours"));
        assertFalse(sent.contains("hunter2-dummy"));
        for (String none : new String[] {null, "", "  \n"})
            assertFalse(
                    JsonParser.parseString(
                                    ContextFilter.firstRequest(
                                            "Hi", Map.of(), PACKAGE, "Water", none, Map.of(), null,
                                            null, null))
                            .getAsJsonObject()
                            .has("project_brief"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContextFilter.firstRequest(
                                "Hi",
                                Map.of(),
                                PACKAGE,
                                "Water",
                                "x".repeat(ContextFilter.MAX_BRIEF_BYTES + 1),
                                Map.of(),
                                null,
                                null,
                                null));
    }

    @Test
    public void contractKeepsTheBriefOfDecisionsAndAPlanForBigJobs() {
        String contract =
                JsonParser.parseString(ContextFilter.firstRequest("Hi", Map.of(), PACKAGE))
                        .getAsJsonObject()
                        .get("build_contract")
                        .getAsString();
        assertTrue(contract.contains("write_brief"));
        assertTrue(contract.contains("project_brief"));
        assertTrue(contract.contains("settled"));
        assertTrue(contract.contains("update_plan"));
        assertTrue(contract.contains("more than about three steps"));
    }

    @Test
    public void refusesAnOversizedRequestAndAnotherPackage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ContextFilter.firstRequest("x".repeat(8 * 1024 + 1), Map.of(), PACKAGE));
        assertThrows(
                IllegalArgumentException.class,
                () -> ContextFilter.firstRequest("Hi", Map.of(), "com.example.other"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ContextFilter.firstRequest("Hi", null, PACKAGE));
    }

    @Test
    public void redactsKnownSecretFormsInWholeText() {
        String redacted =
                ContextFilter.redact(
                        "String key = \"sk-test-DUMMYAPIKEY123456789\";\n"
                                + "storePassword=dummy-store-password\n"
                                + "Authorization: Bearer dummy-diagnostic-token\n"
                                + "-----BEGIN PRIVATE KEY-----\nDUMMYKEYBODY\n-----END PRIVATE KEY-----\n"
                                + "AIzaDUMMYGOOGLEKEY0123456789 ghp_DUMMYGITHUBTOKEN0123456789");
        for (String secret :
                new String[] {
                    "DUMMYAPIKEY123456789",
                    "dummy-store-password",
                    "dummy-diagnostic-token",
                    "DUMMYKEYBODY",
                    "DUMMYGOOGLEKEY",
                    "DUMMYGITHUBTOKEN"
                }) assertFalse(secret, redacted.contains(secret));
    }
}
