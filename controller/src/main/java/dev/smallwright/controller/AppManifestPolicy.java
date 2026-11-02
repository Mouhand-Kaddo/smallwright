package dev.smallwright.controller;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * The rules the controller applies to the manifest the model writes, before every build. It forces
 * what the harness owns (package, version, SDK levels, backup), refuses what would weaken
 * installation, and leaves everything else, permissions and components included, to the model.
 */
final class AppManifestPolicy {
    static final String PATH = "AndroidManifest.xml";
    static final int MAX_BYTES = 64 * 1024;
    static final String DISCOVERY_ACTION = "dev.smallwright.generated.PROJECT_APP";

    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String XMLNS = "http://www.w3.org/2000/xmlns/";
    private static final String MAIN = "android.intent.action.MAIN";
    private static final String LAUNCHER = "android.intent.category.LAUNCHER";
    private static final List<String> COMPONENTS =
            List.of("activity", "service", "receiver", "provider");
    private static final List<String> FLAGS = List.of("debuggable", "testOnly", "persistent");
    private static final List<String> TASKS = List.of("application", "activity", "activity-alias");

    /** The manifest breaks a rule; the message tells the model how to fix it. */
    static final class Refused extends IOException {
        Refused(String message) {
            super(message);
        }
    }

    private AppManifestPolicy() {}

    static String normalize(String manifest, String packageName, int versionCode)
            throws IOException {
        return normalize(parse(manifest), packageName, versionCode);
    }

    /**
     * Normalizes the app's manifest with the project's Android library manifests (library name to
     * manifest text) merged in. The app's own manifest must pass alone, and the rules then apply to
     * the merged result, so a library cannot bring in what the app may not declare.
     */
    static String normalize(
            String manifest, String packageName, int versionCode, Map<String, String> libraries)
            throws IOException {
        String own = normalize(manifest, packageName, versionCode);
        if (libraries.isEmpty()) return own;
        Document document = parse(manifest);
        ManifestMerge.merge(
                document, packageName, androidPrefix(document.getDocumentElement()), libraries);
        String merged;
        try {
            merged = normalize(document, packageName, versionCode);
        } catch (Refused refused) {
            throw new Refused(
                    "With the libraries' manifest entries merged in: " + refused.getMessage());
        }
        if (merged.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new Refused(
                    "With the libraries' manifest entries merged in, AndroidManifest.xml would be"
                            + " larger than "
                            + MAX_BYTES / 1024
                            + " KB. Use fewer libraries.");
        return merged;
    }

    private static String normalize(Document document, String packageName, int versionCode)
            throws IOException {
        Element root = document.getDocumentElement();
        String prefix = androidPrefix(root);
        refuseForbiddenSettings(root, packageName);
        String declared = root.getAttribute("package");
        if (!declared.isEmpty() && !declared.equals(packageName))
            throw new Refused(
                    "AndroidManifest.xml: package must be "
                            + packageName
                            + ", not "
                            + declared
                            + ". The builder owns the package; remove the attribute or use this value.");
        root.setAttribute("package", packageName);
        set(root, prefix, "versionCode", Integer.toString(versionCode));
        setSdk(document, root, prefix);
        Element application = application(root);
        set(application, prefix, "allowBackup", "false");
        Element launcher = launcher(application);
        set(launcher, prefix, "exported", "true");
        requireExportedFlags(application);
        placeDiscoveryAction(document, application, launcher, prefix, packageName);
        AppLinkSources.refuseReserved(application, packageName);
        return Serializer.write(root);
    }

    /**
     * The manifest with the app's icon and round icon set to {@code icon}, and a launcher activity
     * that declares its own icon pointed at it too.
     */
    static String withIcon(String manifest, String icon) throws Refused {
        Element root = parse(manifest).getDocumentElement();
        String prefix = androidPrefix(root);
        Element application = application(root);
        set(application, prefix, "icon", icon);
        set(application, prefix, "roundIcon", icon);
        for (Element activity : children(application, "activity"))
            if (isLauncher(activity))
                for (String attribute : List.of("icon", "roundIcon"))
                    if (activity.hasAttributeNS(ANDROID, attribute))
                        set(activity, prefix, attribute, icon);
        return Serializer.write(root);
    }

    /** The full class name of the launcher activity, or null when the manifest names none. */
    static String launcherClass(String manifest, String packageName) {
        try {
            Document document = parse(manifest);
            Element root = document.getDocumentElement();
            Element launcher = launcher(application(root));
            return className(launcher.getAttributeNS(ANDROID, "name"), packageName);
        } catch (IOException notUsable) {
            return null;
        }
    }

    /** The application and component classes the manifest names inside the project's package. */
    static List<String> projectClasses(String manifest, String packageName) throws IOException {
        Element application = application(parse(manifest).getDocumentElement());
        List<String> names = new ArrayList<>();
        List<Element> declared = new ArrayList<>();
        declared.add(application);
        for (String kind : COMPONENTS) declared.addAll(children(application, kind));
        for (Element element : declared) {
            String name = element.getAttributeNS(ANDROID, "name");
            if (name.isEmpty()) continue;
            String resolved = className(name, packageName);
            if (resolved.startsWith(packageName + ".")) names.add(resolved);
        }
        return names;
    }

    static String className(String name, String packageName) {
        if (name.startsWith(".")) return packageName + name;
        return name.indexOf('.') < 0 ? packageName + "." + name : name;
    }

    static Document parse(String manifest) throws Refused {
        if (manifest == null || manifest.isEmpty())
            throw new Refused("AndroidManifest.xml is empty.");
        byte[] bytes = manifest.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new Refused("AndroidManifest.xml may be at most " + MAX_BYTES / 1024 + " KB.");
        if (manifest.contains("<!DOCTYPE") || manifest.contains("<!ENTITY"))
            throw new Refused("AndroidManifest.xml may not declare a DOCTYPE or entities.");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document =
                    factory.newDocumentBuilder().parse(new InputSource(new StringReader(manifest)));
            if (!"manifest".equals(document.getDocumentElement().getNodeName()))
                throw new Refused("AndroidManifest.xml must start with a <manifest> element.");
            return document;
        } catch (Refused refused) {
            throw refused;
        } catch (Exception malformed) {
            throw new Refused(
                    "AndroidManifest.xml is not well-formed XML: " + malformed.getMessage());
        }
    }

    static String androidPrefix(Element root) throws Refused {
        NamedNodeMap attributes = root.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Attr attribute = (Attr) attributes.item(index);
            if (XMLNS.equals(attribute.getNamespaceURI()) && ANDROID.equals(attribute.getValue()))
                return attribute.getLocalName();
        }
        throw new Refused(
                "AndroidManifest.xml must declare xmlns:android=\""
                        + ANDROID
                        + "\" on <manifest>.");
    }

    private static void refuseForbiddenSettings(Element element, String packageName)
            throws Refused {
        if (element.hasAttributeNS(ANDROID, "sharedUserId"))
            throw new Refused(
                    "AndroidManifest.xml: android:sharedUserId is not allowed; the app keeps its own"
                            + " user id.");
        String affinity = element.getAttributeNS(ANDROID, "taskAffinity");
        if (TASKS.contains(element.getNodeName())
                && element.hasAttributeNS(ANDROID, "taskAffinity")
                && !packageName.equals(affinity))
            throw new Refused(
                    "AndroidManifest.xml: android:taskAffinity=\""
                            + affinity
                            + "\" is not allowed on <"
                            + (element.getNodeName()
                                            + " "
                                            + element.getAttributeNS(ANDROID, "name"))
                                    .trim()
                            + ">. Remove it, or use the app's own package ("
                            + packageName
                            + "); the app may not join another app's task.");
        for (String flag : FLAGS) {
            String value = element.getAttributeNS(ANDROID, flag);
            if (!value.isEmpty() && !"false".equals(value))
                throw new Refused(
                        "AndroidManifest.xml: android:"
                                + flag
                                + " is not allowed on <"
                                + element.getNodeName()
                                + "> (only false is accepted).");
        }
        String name = element.getNodeName();
        boolean ownPermission =
                name.equals("permission")
                        && element.getAttributeNS(ANDROID, "name").startsWith(packageName + ".");
        if (!ownPermission
                && (name.equals("instrumentation")
                        || name.equals("permission")
                        || name.equals("permission-tree")
                        || name.equals("permission-group")))
            throw new Refused(
                    "AndroidManifest.xml: <"
                            + name
                            + "> is not allowed. The app may request"
                            + " permissions with <uses-permission> and define permissions only"
                            + " under its own package ("
                            + packageName
                            + ".NAME), and may not declare instrumentation.");
        for (Element child : children(element)) refuseForbiddenSettings(child, packageName);
    }

    private static void setSdk(Document document, Element root, String prefix) {
        for (Element existing : children(root, "uses-sdk")) root.removeChild(existing);
        Element sdk = document.createElement("uses-sdk");
        set(sdk, prefix, "minSdkVersion", "31");
        set(sdk, prefix, "targetSdkVersion", "36");
        root.insertBefore(sdk, root.getFirstChild());
    }

    private static Element application(Element root) throws Refused {
        List<Element> applications = children(root, "application");
        if (applications.size() != 1)
            throw new Refused("AndroidManifest.xml needs exactly one <application> element.");
        return applications.get(0);
    }

    private static Element launcher(Element application) throws Refused {
        List<Element> launchers = new ArrayList<>();
        for (Element alias : children(application, "activity-alias"))
            if (isLauncher(alias))
                throw new Refused(
                        "AndroidManifest.xml: the launcher must be an <activity>, not an"
                                + " <activity-alias>.");
        for (Element activity : children(application, "activity"))
            for (Element filter : children(activity, "intent-filter"))
                if (isLauncherFilter(filter)) {
                    if (!children(filter, "data").isEmpty()
                            || filter.getAttributes().getLength() != 0)
                        throw new Refused(
                                "AndroidManifest.xml: the launcher intent-filter may hold only"
                                        + " actions and categories, with no data and no attributes.");
                    launchers.add(activity);
                }
        if (launchers.size() != 1)
            throw new Refused(
                    "AndroidManifest.xml needs exactly one launcher activity (an <activity> with an"
                            + " intent-filter holding action android.intent.action.MAIN and category"
                            + " android.intent.category.LAUNCHER); it has "
                            + launchers.size()
                            + ".");
        return launchers.get(0);
    }

    private static boolean isLauncher(Element component) {
        for (Element filter : children(component, "intent-filter"))
            if (isLauncherFilter(filter)) return true;
        return false;
    }

    private static boolean isLauncherFilter(Element filter) {
        return hasEntry(filter, "action", MAIN) && hasEntry(filter, "category", LAUNCHER);
    }

    private static boolean hasEntry(Element filter, String tag, String name) {
        for (Element entry : children(filter, tag))
            if (name.equals(entry.getAttributeNS(ANDROID, "name"))) return true;
        return false;
    }

    private static void requireExportedFlags(Element application) throws Refused {
        for (String kind : COMPONENTS)
            for (Element component : children(application, kind))
                if (!children(component, "intent-filter").isEmpty()
                        && !component.hasAttributeNS(ANDROID, "exported"))
                    throw new Refused(
                            "AndroidManifest.xml: <"
                                    + kind
                                    + " "
                                    + component.getAttributeNS(ANDROID, "name")
                                    + "> has an"
                                    + " intent-filter, so it needs android:exported=\"true\" or"
                                    + " \"false\".");
    }

    private static void placeDiscoveryAction(
            Document document,
            Element application,
            Element launcher,
            String prefix,
            String packageName) {
        for (String kind : COMPONENTS)
            for (Element component : children(application, kind))
                for (Element filter : children(component, "intent-filter"))
                    for (Element action : children(filter, "action"))
                        if (DISCOVERY_ACTION.equals(action.getAttributeNS(ANDROID, "name"))) {
                            filter.removeChild(action);
                            if (children(filter).isEmpty()) component.removeChild(filter);
                        }
        if (ProjectSourceStore.PACKAGE_NAME.equals(packageName)) return;
        for (Element filter : children(launcher, "intent-filter"))
            if (isLauncherFilter(filter)) {
                Element action = document.createElement("action");
                set(action, prefix, "name", DISCOVERY_ACTION);
                filter.appendChild(action);
                return;
            }
    }

    private static void set(Element element, String prefix, String name, String value) {
        element.removeAttributeNS(ANDROID, name);
        element.setAttributeNS(ANDROID, prefix + ":" + name, value);
    }

    static String write(Element root) {
        return Serializer.write(root);
    }

    static List<Element> children(Element parent) {
        List<Element> found = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling())
            if (child instanceof Element) found.add((Element) child);
        return found;
    }

    static List<Element> children(Element parent, String tag) {
        List<Element> found = new ArrayList<>();
        for (Element child : children(parent))
            if (tag.equals(child.getNodeName())) found.add(child);
        return found;
    }

    private static final class Serializer {
        private final StringBuilder out = new StringBuilder();

        static String write(Element root) {
            Serializer serializer = new Serializer();
            serializer.element(root);
            return serializer.out.append('\n').toString();
        }

        private void element(Element element) {
            out.append('<').append(element.getNodeName());
            NamedNodeMap attributes = element.getAttributes();
            for (int index = 0; index < attributes.getLength(); index++) {
                Attr attribute = (Attr) attributes.item(index);
                out.append(' ').append(attribute.getName()).append("=\"");
                escape(attribute.getValue());
                out.append('"');
            }
            List<Node> content = new ArrayList<>();
            for (Node child = element.getFirstChild();
                    child != null;
                    child = child.getNextSibling())
                if (child instanceof Element
                        || (child.getNodeType() == Node.TEXT_NODE
                                        || child.getNodeType() == Node.CDATA_SECTION_NODE)
                                && !child.getNodeValue().trim().isEmpty()) content.add(child);
            if (content.isEmpty()) {
                out.append("/>");
                return;
            }
            out.append('>');
            for (Node child : content)
                if (child instanceof Element) element((Element) child);
                else escape(child.getNodeValue());
            out.append("</").append(element.getNodeName()).append('>');
        }

        private void escape(String text) {
            for (int index = 0; index < text.length(); index++) {
                char c = text.charAt(index);
                if (c == '&') out.append("&amp;");
                else if (c == '<') out.append("&lt;");
                else if (c == '>') out.append("&gt;");
                else if (c == '"') out.append("&quot;");
                else if (c == '\n') out.append("&#10;");
                else if (c == '\r') out.append("&#13;");
                else if (c == '\t') out.append("&#9;");
                else out.append(c);
            }
        }
    }
}
