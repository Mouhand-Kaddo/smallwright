package dev.smallwright.controller;

import com.android.apksig.internal.apk.AndroidBinXmlParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Complements PackageManager, which omits archive intent filters from PackageInfo. */
final class LauncherManifestPolicy {
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final List<String> ACTIVITY = List.of("manifest", "application", "activity");
    private static final String MAIN_ACTION = "android.intent.action.MAIN";
    private static final String LAUNCHER_CATEGORY = "android.intent.category.LAUNCHER";

    private LauncherManifestPolicy() {}

    static String validate(File apk) throws IOException {
        return validate(apk, ProjectSourceStore.PACKAGE_NAME);
    }

    /**
     * Checks the compiled manifest has exactly one launcher activity, without data or attributes on
     * its intent-filter, and that the project discovery action sits on that filter alone (and not
     * at all for the legacy project). Returns the launcher activity's full class name.
     */
    static String validate(File apk, String expectedPackage) throws IOException {
        // This parser ships in the already pinned apksig library; no hidden platform API is used.
        try {
            AndroidBinXmlParser parser =
                    new AndroidBinXmlParser(ByteBuffer.wrap(readManifest(apk)));
            List<String> path = new ArrayList<>();
            int roots = 0;
            int launchers = 0;
            int discoveryActions = 0;
            String activityName = null;
            String launcherName = null;
            boolean launcherDiscovery = false;
            int filterAttributes = 0;
            boolean main = false;
            boolean launcher = false;
            boolean data = false;
            boolean discovery = false;
            int event;
            while ((event = parser.next()) != AndroidBinXmlParser.EVENT_END_DOCUMENT) {
                String name = parser.getName();
                require("".equals(parser.getNamespace()));
                if (event == AndroidBinXmlParser.EVENT_START_ELEMENT) {
                    require(path.size() < 16);
                    path.add(name);
                    require(parser.getDepth() == path.size() && "manifest".equals(path.get(0)));
                    if (path.size() == 1) require(++roots == 1);
                    if (path.equals(ACTIVITY)) activityName = androidName(parser);
                    if (path.size() == 4 && "intent-filter".equals(name)) {
                        filterAttributes = parser.getAttributeCount();
                        main = false;
                        launcher = false;
                        data = false;
                        discovery = false;
                    } else if (path.size() == 5 && "intent-filter".equals(path.get(3))) {
                        if ("action".equals(name)) {
                            String action = androidName(parser);
                            if (MAIN_ACTION.equals(action)) main = true;
                            if (AppManifestPolicy.DISCOVERY_ACTION.equals(action)) {
                                discovery = true;
                                discoveryActions++;
                            }
                        } else if ("category".equals(name)) {
                            if (LAUNCHER_CATEGORY.equals(androidName(parser))) launcher = true;
                        } else if ("data".equals(name)) {
                            data = true;
                        }
                    }
                } else if (event == AndroidBinXmlParser.EVENT_END_ELEMENT) {
                    require(
                            !path.isEmpty()
                                    && parser.getDepth() == path.size()
                                    && name.equals(path.get(path.size() - 1)));
                    if (path.size() == 4 && "intent-filter".equals(name) && main && launcher) {
                        require(
                                path.get(2).equals("activity")
                                        && ++launchers == 1
                                        && !data
                                        && filterAttributes == 0);
                        launcherName = activityName;
                        launcherDiscovery = discovery;
                    }
                    path.remove(path.size() - 1);
                } else {
                    throw violation();
                }
            }
            boolean legacy = ProjectSourceStore.PACKAGE_NAME.equals(expectedPackage);
            require(
                    path.isEmpty()
                            && roots == 1
                            && launchers == 1
                            && launcherName != null
                            && (legacy
                                    ? discoveryActions == 0
                                    : discoveryActions == 1 && launcherDiscovery));
            return AppManifestPolicy.className(launcherName, expectedPackage);
        } catch (AndroidBinXmlParser.XmlParserException | RuntimeException malformed) {
            throw new IOException("Generated APK has an invalid compiled manifest", malformed);
        }
    }

    private static String androidName(AndroidBinXmlParser parser)
            throws AndroidBinXmlParser.XmlParserException, IOException {
        String value = null;
        for (int index = 0; index < parser.getAttributeCount(); index++) {
            if (parser.getAttributeNameResourceId(index) == android.R.attr.name) {
                require(
                        value == null
                                && "name".equals(parser.getAttributeName(index))
                                && ANDROID_NAMESPACE.equals(parser.getAttributeNamespace(index))
                                && parser.getAttributeValueType(index)
                                        == AndroidBinXmlParser.VALUE_TYPE_STRING);
                value = parser.getAttributeStringValue(index);
            }
        }
        require(value != null);
        return value;
    }

    private static byte[] readManifest(File apk) throws IOException {
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry manifest = null;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if ("AndroidManifest.xml".equals(entry.getName())) {
                    require(manifest == null);
                    manifest = entry;
                }
            }
            require(
                    manifest != null
                            && !manifest.isDirectory()
                            && manifest.getSize() > 0
                            && manifest.getSize() <= MAX_MANIFEST_BYTES);
            try (InputStream input = zip.getInputStream(manifest);
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    require(count <= MAX_MANIFEST_BYTES - output.size());
                    output.write(buffer, 0, count);
                }
                require(output.size() == manifest.getSize());
                return output.toByteArray();
            }
        }
    }

    private static void require(boolean allowed) throws IOException {
        if (!allowed) throw violation();
    }

    private static IOException violation() {
        return new IOException("Generated APK violates compiled launcher intent-filter policy");
    }
}
