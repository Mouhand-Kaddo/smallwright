package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class AppManifestPolicyTest {
    private static final String PACKAGE =
            "dev.smallwright.generated.app0123456789abcdef0123456789abcdef";
    private static final String LEGACY = ProjectSourceStore.PACKAGE_NAME;
    private static final String HEAD =
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"";
    private static final String LAUNCHER =
            "<activity android:name=\".MainActivity\" android:exported=\"true\">"
                    + "<intent-filter><action android:name=\"android.intent.action.MAIN\"/>"
                    + "<category android:name=\"android.intent.category.LAUNCHER\"/>"
                    + "</intent-filter></activity>";

    private static String manifest(String rootAttributes, String sdk, String application) {
        return HEAD + rootAttributes + ">" + sdk + application + "</manifest>";
    }

    private static String app(String attributes, String children) {
        return "<application " + attributes + ">" + children + "</application>";
    }

    private static String normalized(String manifest) throws Exception {
        return AppManifestPolicy.normalize(manifest, PACKAGE, 7);
    }

    private static String refusal(String manifest) {
        try {
            AppManifestPolicy.normalize(manifest, PACKAGE, 7);
        } catch (AppManifestPolicy.Refused refused) {
            return refused.getMessage();
        } catch (Exception other) {
            fail("expected a refusal, got " + other);
        }
        fail("expected a refusal");
        return null;
    }

    @Test
    public void forcesTheProjectPackageWhenTheModelOmitsIt() throws Exception {
        String result = normalized(manifest("", "", app("", LAUNCHER)));
        assertTrue(result, result.contains("package=\"" + PACKAGE + "\""));
        assertTrue(result, result.startsWith("<manifest"));
    }

    @Test
    public void keepsTheAppNameAndIconTheModelSet() throws Exception {
        String result =
                normalized(
                        manifest(
                                "",
                                "",
                                app(
                                        "android:label=\"Tide &amp; Time\""
                                                + " android:icon=\"@mipmap/app_icon\"",
                                        LAUNCHER)));
        assertTrue(result, result.contains("android:label=\"Tide &amp; Time\""));
        assertTrue(result, result.contains("android:icon=\"@mipmap/app_icon\""));
    }

    @Test
    public void pointsTheApplicationAndALauncherWithItsOwnIconAtTheAppIcon() throws Exception {
        String withOwnIcon =
                manifest(
                        "",
                        "",
                        app(
                                "android:label=\"Tide\" android:icon=\"@drawable/old\"",
                                LAUNCHER.replace(
                                                "android:exported",
                                                "android:icon=\"@drawable/old\" android:exported")
                                        + "<activity android:name=\".Other\""
                                        + " android:icon=\"@drawable/other\"/>"));
        String result = AppManifestPolicy.withIcon(withOwnIcon, "@mipmap/app_icon");
        assertFalse(result, result.contains("@drawable/old"));
        assertTrue(result, result.contains("@drawable/other"));
        String application = tag(result, "application", null);
        assertTrue(application, application.contains("android:label=\"Tide\""));
        assertTrue(application, application.contains("android:icon=\"@mipmap/app_icon\""));
        assertTrue(application, application.contains("android:roundIcon=\"@mipmap/app_icon\""));
        String launcher = tag(result, "activity", ".MainActivity");
        assertTrue(launcher, launcher.contains("android:icon=\"@mipmap/app_icon\""));
        assertFalse(launcher, launcher.contains("roundIcon"));
        assertEquals(result, AppManifestPolicy.withIcon(result, "@mipmap/app_icon"));
        String plain =
                AppManifestPolicy.withIcon(manifest("", "", app("", LAUNCHER)), "@mipmap/app_icon");
        assertFalse(plain, tag(plain, "activity", ".MainActivity").contains("icon"));
        assertTrue(normalized(plain).contains("android:roundIcon=\"@mipmap/app_icon\""));
        assertEquals(
                "AndroidManifest.xml needs exactly one <application> element.",
                refusalOf(() -> AppManifestPolicy.withIcon(manifest("", "", ""), "@mipmap/x")));
        assertTrue(refusalOf(() -> AppManifestPolicy.withIcon("<manifest", "@mipmap/x")) != null);
    }

    private static String tag(String xml, String element, String name) {
        for (String part : xml.split("(?=<)"))
            if (part.startsWith("<" + element + " ")
                    && (name == null || part.contains("android:name=\"" + name + "\"")))
                return part;
        throw new AssertionError("No <" + element + "> " + name + " in " + xml);
    }

    private interface Change {
        String apply() throws Exception;
    }

    private static String refusalOf(Change change) {
        try {
            change.apply();
        } catch (AppManifestPolicy.Refused refused) {
            return refused.getMessage();
        } catch (Exception other) {
            fail("expected a refusal, got " + other);
        }
        fail("expected a refusal");
        return null;
    }

    @Test
    public void readsTheCheckedTextWhateverEncodingItDeclares() throws Exception {
        String declared =
                "<?xml version=\"1.0\" encoding=\"UTF-16\"?>" + manifest("", "", app("", LAUNCHER));
        assertTrue(normalized(declared).contains("package=\"" + PACKAGE + "\""));
    }

    @Test
    public void refusesADifferentPackage() {
        String message = refusal(manifest(" package=\"com.example.other\"", "", app("", LAUNCHER)));
        assertTrue(message, message.contains(PACKAGE));
    }

    @Test
    public void forcesSdkVersionsOverWhatTheModelWrote() throws Exception {
        String sdk =
                "<uses-sdk android:minSdkVersion=\"21\" android:targetSdkVersion=\"28\""
                        + " android:maxSdkVersion=\"30\"/>";
        String result = normalized(manifest("", sdk, app("", LAUNCHER)));
        assertTrue(result, result.contains("android:minSdkVersion=\"31\""));
        assertTrue(result, result.contains("android:targetSdkVersion=\"36\""));
        assertFalse(result, result.contains("maxSdkVersion"));
        assertEquals(result, 1, result.split("<uses-sdk", -1).length - 1);
    }

    @Test
    public void addsSdkVersionsWhenMissing() throws Exception {
        String result = normalized(manifest("", "", app("", LAUNCHER)));
        assertTrue(result, result.contains("android:minSdkVersion=\"31\""));
        assertTrue(result, result.contains("android:targetSdkVersion=\"36\""));
    }

    @Test
    public void forcesBackupOffAndTheBuildVersion() throws Exception {
        String result =
                normalized(
                        manifest(
                                " android:versionCode=\"99\"",
                                "",
                                app("android:allowBackup=\"true\"", LAUNCHER)));
        assertTrue(result, result.contains("android:allowBackup=\"false\""));
        assertFalse(result, result.contains("allowBackup=\"true\""));
        assertTrue(result, result.contains("android:versionCode=\"7\""));
        assertFalse(result, result.contains("\"99\""));
    }

    @Test
    public void addsBackupOffWhenMissing() throws Exception {
        String result = normalized(manifest("", "", app("", LAUNCHER)));
        assertTrue(result, result.contains("android:allowBackup=\"false\""));
    }

    @Test
    public void refusesSharedUserId() {
        String message =
                refusal(
                        manifest(
                                " android:sharedUserId=\"android.uid.system\"",
                                "",
                                app("", LAUNCHER)));
        assertTrue(message, message.contains("sharedUserId"));
    }

    @Test
    public void refusesATaskAffinityThatIsNotTheAppsOwnPackage() {
        String other = "<activity android:name=\".Other\"%s/>";
        String alias =
                "<activity-alias android:name=\".Alias\" android:targetActivity=\".Other\"%s/>";
        for (String affinity :
                new String[] {
                    " android:taskAffinity=\"com.bank.app\"", " android:taskAffinity=\"\""
                }) {
            for (String manifest :
                    new String[] {
                        manifest("", "", app(affinity, LAUNCHER)),
                        manifest(
                                "",
                                "",
                                app(
                                        "",
                                        LAUNCHER.replace(
                                                " android:exported",
                                                affinity + " android:exported"))),
                        manifest("", "", app("", LAUNCHER + String.format(other, affinity))),
                        manifest(
                                "",
                                "",
                                app(
                                        "",
                                        LAUNCHER
                                                + String.format(other, "")
                                                + String.format(alias, affinity)))
                    }) {
                String message = refusal(manifest);
                assertTrue(message, message.contains("android:taskAffinity"));
                assertTrue(message, message.contains(PACKAGE));
            }
        }
    }

    @Test
    public void acceptsTheAppsOwnPackageAsTaskAffinity() throws Exception {
        String own = " android:taskAffinity=\"" + PACKAGE + "\"";
        normalized(
                manifest(
                        "",
                        "",
                        app(own, LAUNCHER + "<activity android:name=\".Other\"" + own + "/>")));
    }

    @Test
    public void refusesDebuggableTestOnlyAndPersistent() {
        for (String flag : new String[] {"debuggable", "testOnly", "persistent"}) {
            String message =
                    refusal(manifest("", "", app("android:" + flag + "=\"true\"", LAUNCHER)));
            assertTrue(message, message.contains(flag));
        }
    }

    @Test
    public void refusesAFlagThatIsNotLiterallyFalse() {
        refusal(manifest("", "", app("android:debuggable=\"@bool/debug\"", LAUNCHER)));
    }

    @Test
    public void acceptsAFlagThatIsLiterallyFalse() throws Exception {
        normalized(manifest("", "", app("android:debuggable=\"false\"", LAUNCHER)));
    }

    @Test
    public void refusesNoLauncherActivity() {
        String message =
                refusal(manifest("", "", app("", "<activity android:name=\".MainActivity\"/>")));
        assertTrue(message, message.contains("launcher"));
    }

    @Test
    public void refusesTwoLauncherActivities() {
        String second = LAUNCHER.replace(".MainActivity", ".SecondActivity");
        String message = refusal(manifest("", "", app("", LAUNCHER + second)));
        assertTrue(message, message.contains("launcher"));
    }

    @Test
    public void refusesALauncherAlias() {
        String alias =
                "<activity-alias android:name=\".Alias\" android:targetActivity=\".MainActivity\""
                        + " android:exported=\"true\"><intent-filter>"
                        + "<action android:name=\"android.intent.action.MAIN\"/>"
                        + "<category android:name=\"android.intent.category.LAUNCHER\"/>"
                        + "</intent-filter></activity-alias>";
        refusal(manifest("", "", app("", "<activity android:name=\".MainActivity\"/>" + alias)));
    }

    @Test
    public void refusesDataOnTheLauncherFilter() {
        String launcher =
                LAUNCHER.replace(
                        "</intent-filter>", "<data android:scheme=\"https\"/></intent-filter>");
        refusal(manifest("", "", app("", launcher)));
    }

    @Test
    public void allowsInternetAndOtherPermissionsWithoutComment() throws Exception {
        String permissions =
                "<uses-permission android:name=\"android.permission.INTERNET\"/>"
                        + "<uses-permission android:name=\"android.permission.CAMERA\"/>";
        String result = normalized(manifest("", permissions, app("", LAUNCHER)));
        assertTrue(result, result.contains("android.permission.INTERNET"));
        assertTrue(result, result.contains("android.permission.CAMERA"));
    }

    @Test
    public void allowsSeveralActivitiesServicesReceiversAndProviders() throws Exception {
        String more =
                "<activity android:name=\".DetailActivity\"/>"
                        + "<service android:name=\".SyncService\"/>"
                        + "<receiver android:name=\".BootReceiver\" android:exported=\"false\"/>"
                        + "<provider android:name=\".Notes\" android:authorities=\"x.notes\""
                        + " android:exported=\"false\"/>";
        String result = normalized(manifest("", "", app("", LAUNCHER + more)));
        assertTrue(result, result.contains(".DetailActivity"));
        assertTrue(result, result.contains(".SyncService"));
        assertTrue(result, result.contains(".BootReceiver"));
        assertTrue(result, result.contains(".Notes"));
    }

    @Test
    public void refusesAComponentWithAFilterAndNoExportedFlag() {
        String receiver =
                "<receiver android:name=\".R\"><intent-filter>"
                        + "<action android:name=\"android.intent.action.BOOT_COMPLETED\"/>"
                        + "</intent-filter></receiver>";
        String message = refusal(manifest("", "", app("", LAUNCHER + receiver)));
        assertTrue(message, message.contains("exported"));
    }

    @Test
    public void marksTheLauncherExported() throws Exception {
        String launcher = LAUNCHER.replace(" android:exported=\"true\"", "");
        String result = normalized(manifest("", "", app("", launcher)));
        assertTrue(result, result.contains("android:exported=\"true\""));
    }

    @Test
    public void refusesDefiningPermissionsAndInstrumentation() {
        refusal(manifest("", "<permission android:name=\"x.P\"/>", app("", LAUNCHER)));
        refusal(
                manifest(
                        "",
                        "<instrumentation android:name=\".T\" android:targetPackage=\""
                                + PACKAGE
                                + "\"/>",
                        app("", LAUNCHER)));
    }

    @Test
    public void discoveryActionIsOnlyOnTheLauncherOfProjectApps() throws Exception {
        String discovery = "dev.smallwright.generated.PROJECT_APP";
        String stray =
                "<activity android:name=\".Other\" android:exported=\"true\"><intent-filter>"
                        + "<action android:name=\""
                        + discovery
                        + "\"/></intent-filter></activity>";
        String result = normalized(manifest("", "", app("", LAUNCHER + stray)));
        assertEquals(result, 1, result.split(discovery, -1).length - 1);
        assertTrue(result, result.indexOf(discovery) < result.indexOf(".Other"));
        String legacy =
                AppManifestPolicy.normalize(manifest("", "", app("", LAUNCHER + stray)), LEGACY, 7);
        assertFalse(legacy, legacy.contains(discovery));
    }

    @Test
    public void refusesMalformedOversizedAndDoctypeManifests() {
        refusal("<manifest");
        refusal("");
        refusal("<application/>");
        refusal("<!DOCTYPE manifest [<!ENTITY x \"y\">]>" + manifest("", "", app("", LAUNCHER)));
        char[] filler = new char[70 * 1024];
        java.util.Arrays.fill(filler, 'a');
        refusal(manifest("", "", app("", LAUNCHER + "<!--" + new String(filler) + "-->")));
    }

    @Test
    public void refusesAManifestWithoutTheAndroidNamespace() {
        refusal("<manifest><application><activity name=\"a\"/></application></manifest>");
    }

    @Test
    public void escapesAttributeValuesWhenWritingBack() throws Exception {
        String result =
                normalized(
                        manifest(
                                "",
                                "",
                                app(
                                        "android:label=\"A &amp; &lt;B&gt; &quot;q&quot;\"",
                                        LAUNCHER)));
        assertTrue(result, result.contains("android:label=\"A &amp; &lt;B&gt; &quot;q&quot;\""));
    }

    @Test
    public void namesTheLauncherActivityClass() throws Exception {
        String dotted = LAUNCHER.replace(".MainActivity", ".ui.Home");
        assertEquals(
                PACKAGE + ".ui.Home",
                AppManifestPolicy.launcherClass(manifest("", "", app("", dotted)), PACKAGE));
        String bare = LAUNCHER.replace(".MainActivity", "Home");
        assertEquals(
                PACKAGE + ".Home",
                AppManifestPolicy.launcherClass(manifest("", "", app("", bare)), PACKAGE));
        String full = LAUNCHER.replace(".MainActivity", "com.example.Home");
        assertEquals(
                "com.example.Home",
                AppManifestPolicy.launcherClass(manifest("", "", app("", full)), PACKAGE));
        assertNull(AppManifestPolicy.launcherClass("not xml", PACKAGE));
    }

    @Test
    public void listsTheDeclaredClassesThatBelongToTheProject() throws Exception {
        String more =
                "<activity android:name=\".ui.Detail\"/>"
                        + "<service android:name=\"Sync\"/>"
                        + "<receiver android:name=\"com.library.Boot\" android:exported=\"false\"/>"
                        + "<provider android:name=\".Notes$Inner\" android:authorities=\"x\""
                        + " android:exported=\"false\"/>";
        assertEquals(
                java.util.List.of(
                        PACKAGE + ".MainActivity",
                        PACKAGE + ".ui.Detail",
                        PACKAGE + ".Sync",
                        PACKAGE + ".Notes$Inner"),
                AppManifestPolicy.projectClasses(
                        manifest("", "", app("", LAUNCHER + more)), PACKAGE));
    }
}
