package dev.smallwright.worker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The project libraries of one build, with each Android library (AAR) unpacked into scratch. An AAR
 * is untrusted: only its manifest, {@code classes.jar}, {@code libs/*.jar}, {@code
 * res/<type>/<file>}, {@code R.txt} and {@code jni/<abi>/lib<name>.so} for the phone's ABIs are
 * unpacked, every entry name is checked, and unpacked bytes are counted as they are read, whatever
 * the archive claims. A {@code .so} anywhere else is refused. Plain JARs pass through unchanged.
 */
final class AndroidLibraries {
    static final int MAX_ENTRIES = 50_000;
    static final long MAX_UNPACKED_BYTES = 256L * 1024 * 1024;
    static final int MAX_MANIFEST_BYTES = 64 * 1024;
    static final long MAX_SYMBOL_BYTES = 8L * 1024 * 1024;

    private static final Pattern RESOURCE =
            Pattern.compile(
                    "res/([a-z][a-z0-9]{0,31}(?:-[A-Za-z0-9+]{1,32}){0,12})/"
                            + "([A-Za-z0-9_][A-Za-z0-9_.+-]{0,149})");
    private static final Pattern NATIVE =
            Pattern.compile(
                    "jni/([a-z][a-z0-9_-]{0,31})/(lib[A-Za-z0-9_][A-Za-z0-9_.+-]{0,99}\\.so)");
    private static final Pattern BUNDLED_JAR =
            Pattern.compile("libs/([A-Za-z0-9_][A-Za-z0-9_.+-]{0,99}\\.jar)");
    private static final Pattern MANIFEST_START = Pattern.compile("<manifest\\s([^>]*)>");
    private static final Pattern PACKAGE_ATTRIBUTE =
            Pattern.compile("(?:^|\\s)package\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern PACKAGE =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,99}(?:\\.[A-Za-z_][A-Za-z0-9_]{0,99}){1,20}");
    private static final String NUMBER = "(?:0x[0-9a-fA-F]{1,8}|[0-9]{1,10})";
    private static final Pattern APP_SYMBOL =
            Pattern.compile(
                    "(int|int\\[\\]) ([a-z][a-z_]{0,31}) ([A-Za-z_][A-Za-z0-9_]{0,199}) ("
                            + NUMBER
                            + "|\\{ ?(?:"
                            + NUMBER
                            + "(?:, ?"
                            + NUMBER
                            + ")*)? ?\\})");
    private static final Pattern LIBRARY_SYMBOL =
            Pattern.compile("(int|int\\[\\]) (\\S+) (\\S+) .*");

    /** One unpacked Android library. */
    static final class Android {
        final String packageName;

        /** Its {@code res} directory, or null when it has no resources. */
        final File resources;

        /** Its {@code R.txt}: the resource symbols its classes use, or null when it has none. */
        final File symbols;

        Android(String packageName, File resources, File symbols) {
            this.packageName = packageName;
            this.resources = resources;
            this.symbols = symbols;
        }
    }

    /** Class path and dex inputs, in order: plain JARs and each AAR's own JARs. */
    final List<File> jars = new ArrayList<>();

    final List<Android> android = new ArrayList<>();

    /** The native libraries to package, unpacked, by their APK path {@code lib/<abi>/<name>}. */
    final Map<String, File> nativeLibraries = new TreeMap<>();

    private final List<String> abis;
    private long unpacked;
    private int entries;

    private AndroidLibraries(List<String> abis) {
        this.abis = abis;
    }

    /** Unpacks the libraries, keeping native code only for {@code abis}, the phone's ABIs. */
    static AndroidLibraries unpack(
            List<File> libraries, File directory, String appPackage, List<String> abis)
            throws IOException {
        AndroidLibraries result = new AndroidLibraries(abis);
        for (int index = 0; index < libraries.size(); index++) {
            File library = libraries.get(index);
            try (ZipFile zip = new ZipFile(library)) {
                if (zip.getEntry("AndroidManifest.xml") == null) result.jars.add(library);
                else
                    result.unpackAar(zip, new File(directory, Integer.toString(index)), appPackage);
            } catch (java.util.zip.ZipException malformed) {
                throw new IOException("A library given to the compiler is not a readable archive");
            }
        }
        return result;
    }

    private void unpackAar(ZipFile zip, File directory, String appPackage) throws IOException {
        String packageName = packageName(zip, appPackage);
        String what = "The Android library " + packageName;
        boolean resources = false;
        boolean symbols = false;
        Map<String, File> bundledJars = new TreeMap<>();
        Set<String> otherAbis = new TreeSet<>();
        boolean usableNativeCode = false;
        Enumeration<? extends ZipEntry> all = zip.entries();
        while (all.hasMoreElements()) {
            ZipEntry entry = all.nextElement();
            String name = entry.getName();
            if (++entries > MAX_ENTRIES)
                throw new IOException(what + " holds more files than the builder accepts");
            checkName(what, name);
            if (entry.isDirectory()) continue;
            File target = null;
            Matcher resource = RESOURCE.matcher(name);
            Matcher bundled = BUNDLED_JAR.matcher(name);
            Matcher nativeLibrary = NATIVE.matcher(name);
            if (nativeLibrary.matches()) {
                String abi = nativeLibrary.group(1);
                if (!abis.contains(abi)) {
                    otherAbis.add(abi);
                    continue;
                }
                String path = "lib/" + abi + "/" + nativeLibrary.group(2);
                if (nativeLibraries.containsKey(path))
                    throw new IOException(
                            what + " holds " + path + ", which another library also holds");
                target = new File(directory, path);
                nativeLibraries.put(path, target);
                usableNativeCode = true;
            } else if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".so"))
                throw new IOException(
                        what
                                + " holds native code outside jni/<abi>/lib<name>.so: "
                                + bounded(name));
            else if (name.equals("classes.jar")) target = new File(directory, "classes.jar");
            else if (name.equals("R.txt")) {
                target = new File(directory, "R.txt");
                symbols = true;
            } else if (bundled.matches()) target = new File(directory, "libs/" + bundled.group(1));
            else if (resource.matches()) {
                target = new File(directory, "res/" + resource.group(1) + "/" + resource.group(2));
                resources = true;
            } else if (name.startsWith("res/") || name.startsWith("libs/"))
                throw new IOException(
                        what + " holds a file the builder cannot use: " + bounded(name));
            if (target == null) continue;
            Files.createDirectories(target.getParentFile().toPath());
            long limit =
                    target.getName().equals("R.txt")
                            ? Math.min(MAX_SYMBOL_BYTES, MAX_UNPACKED_BYTES - unpacked)
                            : MAX_UNPACKED_BYTES - unpacked;
            try (InputStream input = zip.getInputStream(entry);
                    OutputStream output = Files.newOutputStream(target.toPath())) {
                unpacked += copy(input, output, limit, what);
            }
            if (target.getName().endsWith(".jar")) bundledJars.put(name, target);
        }
        if (!otherAbis.isEmpty() && !usableNativeCode)
            throw new IOException(
                    what
                            + " has native code only for "
                            + String.join(", ", otherAbis)
                            + ", which this phone cannot run (it runs "
                            + String.join(", ", abis)
                            + "); choose a library without native code or one built for this"
                            + " phone");
        jars.addAll(bundledJars.values());
        android.add(
                new Android(
                        packageName,
                        resources ? new File(directory, "res") : null,
                        symbols ? new File(directory, "R.txt") : null));
    }

    private static String packageName(ZipFile zip, String appPackage) throws IOException {
        byte[] manifest;
        try (InputStream input = zip.getInputStream(zip.getEntry("AndroidManifest.xml"))) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            copy(input, bytes, MAX_MANIFEST_BYTES, "An Android library's manifest");
            manifest = bytes.toByteArray();
        }
        Matcher start = MANIFEST_START.matcher(new String(manifest, StandardCharsets.UTF_8));
        Matcher attribute = start.find() ? PACKAGE_ATTRIBUTE.matcher(start.group(1)) : null;
        String packageName = attribute != null && attribute.find() ? attribute.group(1) : "";
        if (!PACKAGE.matcher(packageName).matches()
                || packageName.equals(appPackage)
                || packageName.startsWith("dev.smallwright."))
            throw new IOException(
                    "An Android library has a package name the builder cannot use: "
                            + bounded(packageName));
        return packageName;
    }

    private static void checkName(String what, String name) throws IOException {
        String path = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        boolean unsafe = path.isEmpty() || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0;
        for (String segment : path.split("/", -1))
            unsafe |= segment.isEmpty() || segment.equals(".") || segment.equals("..");
        if (unsafe)
            throw new IOException(what + " holds a file with an unsafe name: " + bounded(name));
    }

    /**
     * Writes an R class for each Android library package, holding only the symbols the library's
     * {@code R.txt} names, with the values AAPT2 gave them when linking the app ({@code
     * appSymbols}). Returns the sources by package.
     */
    Map<String, File> writeRClasses(File appSymbols, File directory) throws IOException {
        Map<String, String[]> app = new HashMap<>();
        for (String line : lines(appSymbols)) {
            Matcher symbol = APP_SYMBOL.matcher(line);
            if (!symbol.matches())
                throw new IOException("AAPT2 wrote a resource symbol the builder cannot read");
            app.put(
                    symbol.group(2) + " " + symbol.group(3),
                    new String[] {symbol.group(1), symbol.group(4)});
        }
        Map<String, Map<String, Set<String>>> packages = new LinkedHashMap<>();
        for (Android library : android) {
            if (library.symbols == null) continue;
            Map<String, Set<String>> types =
                    packages.computeIfAbsent(library.packageName, name -> new TreeMap<>());
            for (String line : lines(library.symbols)) {
                Matcher symbol = LIBRARY_SYMBOL.matcher(line);
                if (!symbol.matches()) continue;
                String[] known = app.get(symbol.group(2) + " " + symbol.group(3));
                if (known == null || !known[0].equals(symbol.group(1))) continue;
                types.computeIfAbsent(symbol.group(2), type -> new LinkedHashSet<>())
                        .add(symbol.group(3));
            }
        }
        Map<String, File> sources = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Set<String>>> library : packages.entrySet()) {
            if (library.getValue().isEmpty()) continue;
            StringBuilder source =
                    new StringBuilder("package ")
                            .append(library.getKey())
                            .append(";\n\npublic final class R {\n    private R() {}\n");
            for (Map.Entry<String, Set<String>> type : library.getValue().entrySet()) {
                source.append("\n    public static final class ")
                        .append(type.getKey())
                        .append(" {\n        private ")
                        .append(type.getKey())
                        .append("() {}\n");
                for (String name : type.getValue()) {
                    String[] known = app.get(type.getKey() + " " + name);
                    source.append("        public static final ")
                            .append(known[0])
                            .append(' ')
                            .append(name)
                            .append(" = ")
                            .append(known[1])
                            .append(";\n");
                }
                source.append("    }\n");
            }
            File file = new File(directory, library.getKey().replace('.', '/') + "/R.java");
            Files.createDirectories(file.getParentFile().toPath());
            Files.write(
                    file.toPath(),
                    source.append("}\n").toString().getBytes(StandardCharsets.UTF_8));
            sources.put(library.getKey(), file);
        }
        return sources;
    }

    private static List<String> lines(File file) throws IOException {
        if (file.length() > MAX_SYMBOL_BYTES)
            throw new IOException("A resource symbol list is larger than the builder accepts");
        List<String> lines = new ArrayList<>();
        for (String line :
                new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n"))
            if (!line.trim().isEmpty()) lines.add(line.trim());
        return lines;
    }

    private static long copy(InputStream input, OutputStream output, long limit, String what)
            throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        for (int read; (read = input.read(buffer)) != -1; ) {
            total += read;
            if (total > limit)
                throw new IOException(what + " unpacks to more bytes than the builder accepts");
            output.write(buffer, 0, read);
        }
        return total;
    }

    private static String bounded(String text) {
        String visible = text.replaceAll("[^\\x20-\\x7e]", "?");
        return visible.length() <= 120 ? visible : visible.substring(0, 120) + "…";
    }
}
