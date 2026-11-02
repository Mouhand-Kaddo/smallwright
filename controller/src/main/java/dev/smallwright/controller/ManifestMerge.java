package dev.smallwright.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

/**
 * Merges the manifests of a project's Android libraries into the app's, as Android's build tools do
 * for the common cases. Each library's permissions, features, queries and components join the
 * app's, with relative class names made full and {@code ${applicationId}} filled in; the same entry
 * from several libraries becomes one. The app's own values win, and an app entry marked {@code
 * tools:node="remove"} keeps the libraries' entry out. Library {@code <application>} attributes and
 * {@code <uses-sdk>} are not merged, and every {@code tools:} attribute is removed. The manifest
 * policy then checks the merged result.
 */
final class ManifestMerge {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String TOOLS = "http://schemas.android.com/tools";
    private static final String XMLNS = "http://www.w3.org/2000/xmlns/";
    private static final int APP_MIN_SDK = 31;
    private static final Pattern PACKAGE =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,99}(?:\\.[A-Za-z_][A-Za-z0-9_]{0,99}){0,20}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]{0,100})\\}");
    private static final Set<String> COMPONENTS =
            Set.of("activity", "activity-alias", "service", "receiver", "provider");
    private static final Set<String> CLASS_ATTRIBUTES =
            Set.of("name", "targetActivity", "parentActivityName");

    private final Document app;
    private final String packageName;
    private final String prefix;
    private String library;
    private String libraryPackage;

    private ManifestMerge(Document app, String packageName, String prefix) {
        this.app = app;
        this.packageName = packageName;
        this.prefix = prefix;
    }

    /** Merges {@code libraries} (name to manifest text, in order) into {@code app} in place. */
    static void merge(
            Document app, String packageName, String prefix, Map<String, String> libraries)
            throws AppManifestPolicy.Refused {
        ManifestMerge merge = new ManifestMerge(app, packageName, prefix);
        Element root = app.getDocumentElement();
        List<Element> applications = AppManifestPolicy.children(root, "application");
        Element application = applications.isEmpty() ? null : applications.get(0);
        for (Map.Entry<String, String> library : libraries.entrySet())
            merge.mergeLibrary(root, application, library.getKey(), library.getValue());
        removeMarked(root);
    }

    private void mergeLibrary(Element root, Element application, String name, String manifest)
            throws AppManifestPolicy.Refused {
        library = name;
        Element manifestRoot;
        try {
            manifestRoot = AppManifestPolicy.parse(manifest).getDocumentElement();
        } catch (AppManifestPolicy.Refused refused) {
            throw refused(
                    "its manifest cannot be read: "
                            + refused.getMessage().replace("AndroidManifest.xml", "the manifest"));
        }
        libraryPackage = manifestRoot.getAttribute("package");
        if (!PACKAGE.matcher(libraryPackage).matches())
            throw refused("its manifest has no valid package name");
        for (Element child : AppManifestPolicy.children(manifestRoot)) {
            String tag = child.getNodeName();
            if (tag.equals("uses-sdk")) checkMinSdk(child);
            else if (tag.equals("application")) {
                if (application != null)
                    for (Element part : AppManifestPolicy.children(child))
                        mergeChild(application, part);
            } else mergeChild(root, child);
        }
    }

    private void checkMinSdk(Element sdk) throws AppManifestPolicy.Refused {
        String value = sdk.getAttributeNS(ANDROID, "minSdkVersion");
        if (value.isEmpty()) return;
        int level;
        try {
            level = Integer.parseInt(value);
        } catch (NumberFormatException preview) {
            throw refused("it needs the preview Android version " + value);
        }
        if (level > APP_MIN_SDK)
            throw refused(
                    "it needs Android API level "
                            + level
                            + ", and the app supports API level "
                            + APP_MIN_SDK
                            + " and up. Choose an older version of the library.");
    }

    private void mergeChild(Element parent, Element incoming) throws AppManifestPolicy.Refused {
        if (isRemoval(incoming)) return;
        Element copy = copy(incoming);
        place(parent, copy);
    }

    private void place(Element parent, Element copy) {
        Element existing = find(parent, key(copy));
        if (existing == null) {
            parent.appendChild(copy);
            return;
        }
        if (isRemoval(existing)) return;
        NamedNodeMap attributes = copy.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Attr attribute = (Attr) attributes.item(index);
            if (attribute.getNamespaceURI() == null) {
                if (!existing.hasAttribute(attribute.getName()))
                    existing.setAttribute(attribute.getName(), attribute.getValue());
            } else if (!existing.hasAttributeNS(
                    attribute.getNamespaceURI(), attribute.getLocalName()))
                existing.setAttributeNS(
                        attribute.getNamespaceURI(), attribute.getName(), attribute.getValue());
        }
        for (Element child : AppManifestPolicy.children(copy)) place(existing, child);
    }

    private Element find(Element parent, String key) {
        for (Element child : AppManifestPolicy.children(parent))
            if (key.equals(key(child))) return child;
        return null;
    }

    private String key(Element element) {
        String name = element.getAttributeNS(ANDROID, "name");
        if (name.isEmpty()) return element.getNodeName() + "|" + canonical(element);
        if (COMPONENTS.contains(element.getNodeName()))
            name = AppManifestPolicy.className(name, packageName);
        return element.getNodeName() + "#" + name;
    }

    private static String canonical(Element element) {
        StringBuilder text = new StringBuilder("<").append(element.getNodeName());
        NamedNodeMap attributes = element.getAttributes();
        Map<String, String> sorted = new TreeMap<>();
        for (int index = 0; index < attributes.getLength(); index++) {
            Attr attribute = (Attr) attributes.item(index);
            if (TOOLS.equals(attribute.getNamespaceURI())
                    || XMLNS.equals(attribute.getNamespaceURI())) continue;
            String local =
                    attribute.getLocalName() == null
                            ? attribute.getName()
                            : attribute.getLocalName();
            sorted.put(attribute.getNamespaceURI() + ":" + local, attribute.getValue());
        }
        text.append(sorted).append('>');
        for (Element child : AppManifestPolicy.children(element)) text.append(canonical(child));
        return text.append("</>").toString();
    }

    private Element copy(Element source) throws AppManifestPolicy.Refused {
        Element target = app.createElement(source.getNodeName());
        boolean component = COMPONENTS.contains(source.getNodeName());
        NamedNodeMap attributes = source.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Attr attribute = (Attr) attributes.item(index);
            String namespace = attribute.getNamespaceURI();
            if (ANDROID.equals(namespace)) {
                String local = attribute.getLocalName();
                String value = filled(attribute.getValue());
                if (component && CLASS_ATTRIBUTES.contains(local))
                    value = AppManifestPolicy.className(value, libraryPackage);
                target.setAttributeNS(ANDROID, prefix + ":" + local, value);
            } else if (namespace == null)
                target.setAttribute(attribute.getName(), filled(attribute.getValue()));
        }
        for (Element child : AppManifestPolicy.children(source))
            if (!isRemoval(child)) target.appendChild(copy(child));
        return target;
    }

    private String filled(String value) throws AppManifestPolicy.Refused {
        Matcher placeholder = PLACEHOLDER.matcher(value);
        StringBuffer filled = new StringBuffer();
        while (placeholder.find()) {
            String name = placeholder.group(1);
            if (!name.equals("applicationId") && !name.equals("packageName"))
                throw refused(
                        "its manifest uses "
                                + placeholder.group()
                                + ", which the builder cannot fill in. Choose another library.");
            placeholder.appendReplacement(filled, Matcher.quoteReplacement(packageName));
        }
        placeholder.appendTail(filled);
        if (filled.indexOf("${") >= 0)
            throw refused("its manifest uses a placeholder the builder cannot fill in");
        return filled.toString();
    }

    private static boolean isRemoval(Element element) {
        String node = element.getAttributeNS(TOOLS, "node");
        return node.equals("remove") || node.equals("removeAll");
    }

    private static void removeMarked(Element element) {
        List<Attr> tools = new ArrayList<>();
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Attr attribute = (Attr) attributes.item(index);
            if (TOOLS.equals(attribute.getNamespaceURI())
                    || XMLNS.equals(attribute.getNamespaceURI())
                            && TOOLS.equals(attribute.getValue())) tools.add(attribute);
        }
        for (Attr attribute : tools) element.removeAttributeNode(attribute);
        for (Element child : AppManifestPolicy.children(element)) {
            if (isRemoval(child)) element.removeChild(child);
            else removeMarked(child);
        }
    }

    private AppManifestPolicy.Refused refused(String why) {
        return new AppManifestPolicy.Refused("The library " + library + " cannot be used: " + why);
    }
}
