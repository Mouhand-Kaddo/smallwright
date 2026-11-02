package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/** Library manifests merged into the app's before the manifest policy checks the result. */
public final class LibraryManifestMergeTest {
    private static final String PACKAGE =
            "dev.smallwright.generated.app0123456789abcdef0123456789abcdef";
    private static final String APP =
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                    + " xmlns:tools=\"http://schemas.android.com/tools\">"
                    + "<application android:label=\"Notes\">"
                    + "<activity android:name=\".MainActivity\" android:exported=\"true\">"
                    + "<intent-filter><action android:name=\"android.intent.action.MAIN\"/>"
                    + "<category android:name=\"android.intent.category.LAUNCHER\"/>"
                    + "</intent-filter></activity>%s</application></manifest>";

    private static String app(String components) {
        return String.format(APP, components);
    }

    private static String library(String packageName, String body) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                + " xmlns:tools=\"http://schemas.android.com/tools\" package=\""
                + packageName
                + "\"><uses-sdk android:minSdkVersion=\"21\"/>"
                + body
                + "</manifest>";
    }

    private static final String STARTUP =
            "<provider android:name=\"androidx.startup.InitializationProvider\""
                    + " android:authorities=\"${applicationId}.androidx-startup\""
                    + " android:exported=\"false\" tools:node=\"merge\">"
                    + "<meta-data android:name=\"%s\" android:value=\"androidx.startup\"/>"
                    + "</provider>";

    private static final String WORK_MANAGER =
            library(
                    "androidx.work",
                    "<uses-permission android:name=\"android.permission.WAKE_LOCK\"/>"
                            + "<uses-permission"
                            + " android:name=\"android.permission.ACCESS_NETWORK_STATE\"/>"
                            + "<application>"
                            + String.format(STARTUP, "androidx.work.WorkManagerInitializer")
                            + "<service android:name=\"androidx.work.impl.background.systemjob"
                            + ".SystemJobService\" android:exported=\"true\""
                            + " android:permission=\"android.permission.BIND_JOB_SERVICE\"/>"
                            + "<receiver android:name=\".impl.diagnostics.DiagnosticsReceiver\""
                            + " android:exported=\"true\""
                            + " android:permission=\"android.permission.DUMP\">"
                            + "<intent-filter><action"
                            + " android:name=\"androidx.work.diagnostics.REQUEST_DIAGNOSTICS\"/>"
                            + "</intent-filter></receiver></application>");

    private static final String CORE =
            library(
                    "androidx.core",
                    "<permission"
                            + " android:name=\"${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION\""
                            + " android:protectionLevel=\"signature\"/>"
                            + "<uses-permission"
                            + " android:name=\"${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION\"/>"
                            + "<application"
                            + " android:appComponentFactory=\"androidx.core.app.CoreComponentFactory\"/>");

    private static final String EMOJI =
            library(
                    "androidx.emoji2",
                    "<application>"
                            + String.format(STARTUP, "androidx.emoji2.text.EmojiCompatInitializer")
                            + "</application>");

    private static Map<String, String> libraries(String... nameAndManifest) {
        Map<String, String> libraries = new LinkedHashMap<>();
        for (int index = 0; index < nameAndManifest.length; index += 2)
            libraries.put(nameAndManifest[index], nameAndManifest[index + 1]);
        return libraries;
    }

    private static String merged(String manifest, Map<String, String> libraries) throws Exception {
        return AppManifestPolicy.normalize(manifest, PACKAGE, 4, libraries);
    }

    private static String refusal(String manifest, Map<String, String> libraries) {
        try {
            merged(manifest, libraries);
        } catch (AppManifestPolicy.Refused refused) {
            return refused.getMessage();
        } catch (Exception other) {
            fail("expected a refusal, got " + other);
        }
        fail("expected a refusal");
        return null;
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    @Test
    public void libraryComponentsAndPermissionsJoinTheAppWithFullNamesAndTheAppPackage()
            throws Exception {
        String result =
                merged(
                        app(""),
                        libraries(
                                "androidx.work:work-runtime:2.10.0",
                                WORK_MANAGER,
                                "androidx.core:core:1.16.0",
                                CORE));
        assertTrue(result, result.contains("android:name=\"android.permission.WAKE_LOCK\""));
        assertTrue(
                result,
                result.contains(
                        "android:name=\"androidx.work.impl.background.systemjob.SystemJobService\""));
        assertTrue(
                result,
                result.contains(
                        "android:name=\"androidx.work.impl.diagnostics.DiagnosticsReceiver\""));
        assertTrue(
                result,
                result.contains("android:authorities=\"" + PACKAGE + ".androidx-startup\""));
        assertTrue(
                result,
                result.contains(
                        "<permission android:name=\""
                                + PACKAGE
                                + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION\""));
        assertFalse(result, result.contains("${"));
        assertFalse(result, result.contains("tools:"));
        assertFalse(result, result.contains("appComponentFactory"));
        assertTrue(result, result.contains("android:minSdkVersion=\"31\""));
        assertEquals(result, 1, count(result, "<uses-sdk"));
        assertTrue(result, result.contains("android:label=\"Notes\""));
    }

    @Test
    public void theSameComponentFromSeveralLibrariesIsMergedIntoOne() throws Exception {
        String result =
                merged(
                        app(""),
                        libraries(
                                "androidx.work:work-runtime:2.10.0",
                                WORK_MANAGER,
                                "androidx.emoji2:emoji2:1.3.0",
                                EMOJI,
                                "androidx.emoji2:emoji2-copy:1.3.0",
                                EMOJI));
        assertEquals(result, 1, count(result, "androidx.startup.InitializationProvider"));
        assertEquals(result, 1, count(result, "androidx.work.WorkManagerInitializer"));
        assertEquals(result, 1, count(result, "androidx.emoji2.text.EmojiCompatInitializer"));
    }

    @Test
    public void theAppCanRemoveALibraryEntryAndKeepsItsOwnValues() throws Exception {
        String own =
                "<provider android:name=\"androidx.startup.InitializationProvider\""
                        + " android:authorities=\"${applicationId}.androidx-startup\""
                        + " android:exported=\"false\" tools:node=\"merge\">"
                        + "<meta-data android:name=\"androidx.work.WorkManagerInitializer\""
                        + " tools:node=\"remove\"/></provider>"
                        + "<receiver"
                        + " android:name=\"androidx.work.impl.diagnostics.DiagnosticsReceiver\""
                        + " tools:node=\"remove\"/>"
                        + "<service android:name=\"androidx.work.impl.background.systemjob"
                        + ".SystemJobService\" android:exported=\"false\"/>";
        String result =
                merged(app(own), libraries("androidx.work:work-runtime:2.10.0", WORK_MANAGER));
        assertFalse(result, result.contains("WorkManagerInitializer"));
        assertFalse(result, result.contains("DiagnosticsReceiver"));
        assertEquals(result, 1, count(result, "SystemJobService"));
        java.util.regex.Matcher service =
                java.util.regex.Pattern.compile("<service [^>]*>").matcher(result);
        assertTrue(result, service.find());
        assertTrue(service.group(), service.group().contains("android:exported=\"false\""));
        assertTrue(
                service.group(),
                service.group()
                        .contains("android:permission=\"android.permission.BIND_JOB_SERVICE\""));
        assertFalse(result, result.contains("tools:"));
    }

    @Test
    public void thePolicyChecksWhatTheLibrariesBringToo() {
        String debuggable =
                library(
                        "org.example.bad",
                        "<application><activity android:name=\".Spy\""
                                + " android:exported=\"true\"><intent-filter><action"
                                + " android:name=\"android.intent.action.MAIN\"/><category"
                                + " android:name=\"android.intent.category.LAUNCHER\"/>"
                                + "</intent-filter></activity></application>");
        String message = refusal(app(""), libraries("org.example:bad:1", debuggable));
        assertTrue(message, message.contains("exactly one launcher activity"));
        assertTrue(message, message.contains("libraries"));
        String foreign = library("org.example.bad", "<permission android:name=\"com.bank.READ\"/>");
        assertTrue(
                refusal(app(""), libraries("org.example:bad:1", foreign)).contains("<permission>"));
        String instrumentation =
                library(
                        "org.example.bad",
                        "<instrumentation android:name=\".T\" android:targetPackage=\"x\"/>");
        refusal(app(""), libraries("org.example:bad:1", instrumentation));
        String hijack =
                library(
                        "org.example.bad",
                        "<application><activity android:name=\".Hijack\""
                                + " android:taskAffinity=\"com.bank.app\"/></application>");
        String affinity = refusal(app(""), libraries("org.example:bad:1", hijack));
        assertTrue(affinity, affinity.contains("android:taskAffinity"));
        assertTrue(affinity, affinity.contains("libraries"));
    }

    @Test
    public void refusesWhatTheBuilderCannotFillInOrSupport() {
        String placeholder =
                library(
                        "org.example.maps",
                        "<application><meta-data android:name=\"key\""
                                + " android:value=\"${mapsKey}\"/></application>");
        String message = refusal(app(""), libraries("org.example:maps:1", placeholder));
        assertTrue(message, message.contains("org.example:maps:1"));
        assertTrue(message, message.contains("${mapsKey}"));
        String newer =
                library("org.example.newer", "")
                        .replace("minSdkVersion=\"21\"", "minSdkVersion=\"33\"");
        message = refusal(app(""), libraries("org.example:newer:1", newer));
        assertTrue(message, message.contains("org.example:newer:1"));
        assertTrue(message, message.contains("33"));
        message = refusal(app(""), libraries("org.example:broken:1", "<manifest"));
        assertTrue(message, message.contains("org.example:broken:1"));
        message =
                refusal(
                        app(""),
                        libraries(
                                "org.example:doctype:1",
                                "<!DOCTYPE manifest>" + library("org.example.doctype", "")));
        assertTrue(message, message.contains("org.example:doctype:1"));
    }

    @Test
    public void theMergedManifestMustStayWithinTheSizeLimit() {
        StringBuilder many = new StringBuilder("<application>");
        for (int index = 0; index < 900; index++)
            many.append("<service android:name=\".Service")
                    .append(index)
                    .append("\" android:exported=\"false\"/>");
        String large = library("org.example.large", many.append("</application>").toString());
        String message = refusal(app(""), libraries("org.example:large:1", large));
        assertTrue(message, message.contains("64 KB"));
    }

    @Test
    public void withoutLibrariesTheManifestIsUnchangedByMerging() throws Exception {
        String plain = app("").replace(" xmlns:tools=\"http://schemas.android.com/tools\"", "");
        assertEquals(
                AppManifestPolicy.normalize(plain, PACKAGE, 4),
                merged(plain, new LinkedHashMap<>()));
    }
}
