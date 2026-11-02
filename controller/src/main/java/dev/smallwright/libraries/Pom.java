package dev.smallwright.libraries;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** The parts of a Maven POM that dependency resolution reads, as written (not interpolated). */
final class Pom {
    /** One {@code <dependency>} entry. Absent fields are null. */
    static final class Dependency {
        final String group, artifact, version, scope, type, classifier, optional;

        /** {@code group:artifact} patterns, where either side may be {@code *}. */
        final List<String> exclusions;

        private Dependency(Element element) {
            group = text(element, "groupId");
            artifact = text(element, "artifactId");
            version = text(element, "version");
            scope = text(element, "scope");
            type = text(element, "type");
            classifier = text(element, "classifier");
            optional = text(element, "optional");
            exclusions = new ArrayList<>();
            Element list = child(element, "exclusions");
            if (list != null)
                for (Element exclusion : children(list, "exclusion"))
                    exclusions.add(
                            orStar(text(exclusion, "groupId"))
                                    + ":"
                                    + orStar(text(exclusion, "artifactId")));
        }

        private Dependency(Dependency raw, java.util.function.UnaryOperator<String> value) {
            group = value.apply(raw.group);
            artifact = value.apply(raw.artifact);
            version = value.apply(raw.version);
            scope = value.apply(raw.scope);
            type = value.apply(raw.type);
            classifier = value.apply(raw.classifier);
            optional = value.apply(raw.optional);
            exclusions = new ArrayList<>();
            for (String exclusion : raw.exclusions) {
                String resolved = value.apply(exclusion);
                if (resolved != null) exclusions.add(resolved);
            }
        }

        /** This entry with its properties replaced by {@code value}. */
        Dependency with(java.util.function.UnaryOperator<String> value) {
            return new Dependency(this, value);
        }

        private static String orStar(String value) {
            return value == null ? "*" : value;
        }
    }

    final String group, artifact, version, packaging;
    final String parentGroup, parentArtifact, parentVersion;
    final Map<String, String> properties = new LinkedHashMap<>();
    final List<Dependency> dependencies = new ArrayList<>();
    final List<Dependency> managed = new ArrayList<>();

    private Pom(Element project) {
        group = text(project, "groupId");
        artifact = text(project, "artifactId");
        version = text(project, "version");
        packaging = text(project, "packaging");
        Element parent = child(project, "parent");
        parentGroup = parent == null ? null : text(parent, "groupId");
        parentArtifact = parent == null ? null : text(parent, "artifactId");
        parentVersion = parent == null ? null : text(parent, "version");
        Element values = child(project, "properties");
        if (values != null)
            for (Node node = values.getFirstChild(); node != null; node = node.getNextSibling())
                if (node instanceof Element)
                    properties.put(name((Element) node), node.getTextContent().trim());
        addDependencies(child(project, "dependencies"), dependencies);
        Element management = child(project, "dependencyManagement");
        if (management != null) addDependencies(child(management, "dependencies"), managed);
    }

    /** Reads a POM; files with a document type declaration are refused unread. */
    static Pom parse(byte[] bytes, String what) throws IOException {
        refuseDocumentType(bytes, what + " declares a document type, which a POM never needs");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Element root = builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            if (!"project".equals(name(root))) throw new IOException(what + " is not a POM");
            return new Pom(root);
        } catch (IOException refused) {
            throw refused;
        } catch (Exception malformed) {
            throw new IOException(what + " is not a readable POM");
        }
    }

    /** Reads a {@code maven-metadata.xml} file's version list. */
    static List<String> versions(byte[] bytes, String what) throws IOException {
        refuseDocumentType(bytes, what + " declares a document type");
        try {
            Element root =
                    DocumentBuilderFactory.newInstance()
                            .newDocumentBuilder()
                            .parse(new ByteArrayInputStream(bytes))
                            .getDocumentElement();
            List<String> versions = new ArrayList<>();
            Element versioning = child(root, "versioning");
            Element list = versioning == null ? null : child(versioning, "versions");
            if (list != null)
                for (Element version : children(list, "version"))
                    versions.add(version.getTextContent().trim());
            return versions;
        } catch (Exception malformed) {
            throw new IOException(what + " is not readable");
        }
    }

    private static void addDependencies(Element list, List<Dependency> into) {
        if (list == null) return;
        for (Element dependency : children(list, "dependency"))
            into.add(new Dependency(dependency));
    }

    private static String name(Element element) {
        String name = element.getTagName();
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }

    private static Element child(Element parent, String wanted) {
        List<Element> found = children(parent, wanted);
        return found.isEmpty() ? null : found.get(0);
    }

    private static List<Element> children(Element parent, String wanted) {
        List<Element> found = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element && wanted.equals(name((Element) node)))
                found.add((Element) node);
        return found;
    }

    private static String text(Element parent, String wanted) {
        Element element = child(parent, wanted);
        if (element == null) return null;
        String value = element.getTextContent().trim();
        return value.isEmpty() ? null : value;
    }

    /** Refuses a declaration the parser would act on, including in encodings other than UTF-8. */
    private static void refuseDocumentType(byte[] bytes, String refusal) throws IOException {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.indexOf('\u0000') >= 0 || text.contains("<!DOCTYPE") || text.contains("<!ENTITY"))
            throw new IOException(refusal);
    }
}
