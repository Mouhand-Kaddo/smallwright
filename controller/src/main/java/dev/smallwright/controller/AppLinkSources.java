package dev.smallwright.controller;

import dev.smallwright.project.ProjectStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The builder's own code in every generated app (ADR 0008): one Java file in the reserved
 * sub-package, added to each build, and the manifest entries that start it. The model can neither
 * write the file nor declare, replace or remove the entries.
 */
final class AppLinkSources {
    static final String CONTROLLER_PACKAGE = "dev.smallwright.controller";
    static final String EXTRA_REPORT = "dev.smallwright.controller.extra.CRASH_REPORT";
    static final String EXTRA_ACTIVITY = "dev.smallwright.controller.extra.ACTIVITY";
    static final String LOOK = "Look";
    static final String CRASH = "Crash";
    static final String STARTER = "Starter";
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String TEMPLATE = "BuilderLink.java.txt";
    private static final String CONFIG_CHANGES =
            "orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode|density"
                    + "|fontScale|locale|layoutDirection";
    private static final List<String> COMPONENTS =
            List.of("activity", "activity-alias", "service", "receiver", "provider");

    private AppLinkSources() {}

    /** Legacy counter projects stay as they were, like the discovery action. */
    static boolean applies(String packageName) {
        return !ProjectSourceStore.PACKAGE_NAME.equals(packageName);
    }

    static String component(String packageName, String nested) {
        return packageName + "." + ProjectStore.BUILDER_SUBPACKAGE + ".BuilderLink$" + nested;
    }

    /**
     * Whether an activity of a built app may have {@code affinity} as its task affinity: the app's
     * own package, or for the builder's crash dialog alone, the builder's separate task, so that
     * the dialog opened after a crash does not clear the app's own task.
     */
    static boolean allowsTaskAffinity(String packageName, String activity, String affinity) {
        return packageName.equals(affinity)
                || applies(packageName)
                        && component(packageName, CRASH).equals(activity)
                        && reserved(packageName).equals(affinity);
    }

    /**
     * Whether a component of a built app keeps the exposure and process the builder gave it: the
     * look is exported in the app's process, the crash dialog private in its own process, the
     * starter private. Components that are not the builder's are left to the other rules.
     */
    static boolean allowsLinkComponent(
            String packageName, String component, boolean exported, String process) {
        boolean ours = applies(packageName);
        if (component(packageName, LOOK).equals(component))
            return ours && exported && packageName.equals(process);
        if (component(packageName, CRASH).equals(component))
            return ours
                    && !exported
                    && (packageName + ":" + ProjectStore.BUILDER_SUBPACKAGE).equals(process);
        if (component(packageName, STARTER).equals(component))
            return ours && !exported && packageName.equals(process);
        return true;
    }

    /**
     * The file to add to a build, by project path, with the controller's signing certificate
     * (SHA-256) written in; empty for legacy projects.
     */
    static Map<String, byte[]> files(String packageName, byte[] controllerCertificateSha256)
            throws IOException {
        if (!applies(packageName)) return Map.of();
        if (controllerCertificateSha256 == null || controllerCertificateSha256.length != 32)
            throw new IOException("The builder's signing certificate is unknown");
        String source;
        try (InputStream template = AppLinkSources.class.getResourceAsStream(TEMPLATE)) {
            if (template == null) throw new IOException("The builder's app code is missing");
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = template.read(buffer)) != -1) bytes.write(buffer, 0, read);
            source = bytes.toString(StandardCharsets.UTF_8.name());
        }
        source =
                source.replace("@PACKAGE@", packageName)
                        .replace("@CONTROLLER@", CONTROLLER_PACKAGE)
                        .replace("@CONTROLLER_CERT_SHA256@", hex(controllerCertificateSha256))
                        .replace("@REPORT_ACTIVITY@", AppReportActivity.class.getName())
                        .replace("@EXTRA_REPORT@", EXTRA_REPORT)
                        .replace("@EXTRA_ACTIVITY@", EXTRA_ACTIVITY)
                        .replace(
                                "@MAX_REPORT_BYTES@",
                                Integer.toString(CrashReportImport.MAX_BYTES));
        if (source.matches("(?s).*@[A-Z_0-9]+@.*"))
            throw new IOException("The builder's app code is incomplete");
        return Map.of(
                "java/"
                        + packageName.replace('.', '/')
                        + "/"
                        + ProjectStore.BUILDER_SUBPACKAGE
                        + "/BuilderLink.java",
                source.getBytes(StandardCharsets.UTF_8));
    }

    /** Refuses components the model or a library named in the builder's sub-package. */
    static void refuseReserved(Element application, String packageName)
            throws AppManifestPolicy.Refused {
        if (!applies(packageName)) return;
        String reserved = reserved(packageName);
        for (String kind : COMPONENTS)
            for (Element component : AppManifestPolicy.children(application, kind))
                for (String attribute : List.of("name", "targetActivity", "authorities"))
                    for (String value : component.getAttributeNS(ANDROID, attribute).split(";")) {
                        String named =
                                attribute.equals("authorities")
                                        ? value.trim()
                                        : AppManifestPolicy.className(value.trim(), packageName);
                        if (!value.isBlank()
                                && (named.equals(reserved) || named.startsWith(reserved + ".")))
                            throw new AppManifestPolicy.Refused(
                                    "AndroidManifest.xml: "
                                            + reserved
                                            + " belongs to the builder, which adds it to every"
                                            + " app by itself; remove the <"
                                            + kind
                                            + "> that names it.");
                    }
    }

    /**
     * The checked manifest a build uses, with the entries that start the builder's code added;
     * unchanged for legacy projects. Goes with {@link #files} in the same build.
     */
    static String withEntries(String manifest, String packageName)
            throws AppManifestPolicy.Refused {
        if (!applies(packageName)) return manifest;
        Document document = AppManifestPolicy.parse(manifest);
        Element root = document.getDocumentElement();
        String prefix = AppManifestPolicy.androidPrefix(root);
        List<Element> applications = AppManifestPolicy.children(root, "application");
        if (applications.size() != 1)
            throw new AppManifestPolicy.Refused(
                    "AndroidManifest.xml needs exactly one <application> element.");
        Element application = applications.get(0);
        refuseReserved(application, packageName);
        String reserved = reserved(packageName);
        Element queries = first(root, "queries");
        if (queries == null) {
            queries = document.createElement("queries");
            root.insertBefore(queries, application);
        }
        Element controller = document.createElement("package");
        set(controller, prefix, "name", CONTROLLER_PACKAGE);
        queries.appendChild(controller);
        Element starter = document.createElement("provider");
        set(starter, prefix, "name", component(packageName, STARTER));
        set(starter, prefix, "authorities", reserved);
        set(starter, prefix, "exported", "false");
        set(starter, prefix, "grantUriPermissions", "true");
        set(starter, prefix, "initOrder", "1000");
        application.appendChild(starter);
        Element look = document.createElement("activity");
        set(look, prefix, "name", component(packageName, LOOK));
        set(look, prefix, "exported", "true");
        set(look, prefix, "excludeFromRecents", "true");
        set(look, prefix, "configChanges", CONFIG_CHANGES);
        set(look, prefix, "theme", "@android:style/Theme.Translucent.NoTitleBar");
        application.appendChild(look);
        Element crash = document.createElement("activity");
        set(crash, prefix, "name", component(packageName, CRASH));
        set(crash, prefix, "exported", "false");
        set(crash, prefix, "process", ":" + ProjectStore.BUILDER_SUBPACKAGE);
        set(crash, prefix, "taskAffinity", reserved);
        set(crash, prefix, "excludeFromRecents", "true");
        set(crash, prefix, "theme", "@android:style/Theme.Translucent.NoTitleBar");
        application.appendChild(crash);
        return AppManifestPolicy.write(root);
    }

    private static String reserved(String packageName) {
        return packageName + "." + ProjectStore.BUILDER_SUBPACKAGE;
    }

    private static Element first(Element parent, String tag) {
        List<Element> found = AppManifestPolicy.children(parent, tag);
        return found.isEmpty() ? null : found.get(0);
    }

    private static void set(Element element, String prefix, String name, String value) {
        element.setAttributeNS(ANDROID, prefix + ":" + name, value);
    }

    static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
            hex.append(Character.forDigit((value >> 4) & 15, 16))
                    .append(Character.forDigit(value & 15, 16));
        return hex.toString();
    }
}
