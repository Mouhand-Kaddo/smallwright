package dev.smallwright.controller;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Real class files compiled by ECJ for tests, and the real Android platform jar. */
final class JavaFixtures {
    static final String SAMPLE =
            "package fixture;\n"
                    + "import java.io.Closeable;\n"
                    + "import java.io.IOException;\n"
                    + "import java.util.AbstractList;\n"
                    + "import java.util.List;\n"
                    + "import java.util.Map;\n"
                    + "public abstract class Sample<K extends Comparable<? super K>>"
                    + " extends AbstractList<K> implements java.io.Serializable {\n"
                    + "  public static final String NAME = \"say \\\"hi\\\"\\n\";\n"
                    + "  public static final long BIG = 1L << 40;\n"
                    + "  public static final char LETTER = 'x';\n"
                    + "  public static final boolean ON = true;\n"
                    + "  public static final float HALF = 0.5f;\n"
                    + "  public static final double NOTHING = Double.NaN;\n"
                    + "  protected int counter;\n"
                    + "  private int hidden;\n"
                    + "  int packagePrivate;\n"
                    + "  public Sample(int size, String... names) {}\n"
                    + "  @Deprecated public void old() {}\n"
                    + "  public abstract <T extends Runnable & Closeable> T pick("
                    + "Map<String, ? extends T> from, long at) throws IOException;\n"
                    + "  public <V> V first(List<V> items) { return items.get(0); }\n"
                    + "  public static int twice(int value) { return value * 2; }\n"
                    + "  private void secret() {}\n"
                    + "  public interface Listener {\n"
                    + "    void heard(long at, double level);\n"
                    + "    default void reset() {}\n"
                    + "  }\n"
                    + "  public enum Mode { ON, OFF }\n"
                    + "}\n";

    private JavaFixtures() {}

    /**
     * Compiles {@code sources} (path to text) with ECJ against the Android platform jar, with debug
     * names; returns entry name to bytes.
     */
    static Map<String, byte[]> compile(File scratch, Map<String, String> sources)
            throws IOException {
        Path input = scratch.toPath().resolve("src");
        Path output = scratch.toPath().resolve("classes");
        Files.createDirectories(output);
        String platform = platformJar().getAbsolutePath();
        List<String> arguments =
                new java.util.ArrayList<>(
                        List.of(
                                "-1.8",
                                "-g",
                                "-proc:none",
                                "-bootclasspath",
                                platform,
                                "-d",
                                output.toString()));
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = input.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, source.getValue().getBytes(StandardCharsets.UTF_8));
            arguments.add(file.toString());
        }
        java.io.StringWriter diagnostics = new java.io.StringWriter();
        try (java.net.URLClassLoader loader =
                        new java.net.URLClassLoader(
                                new java.net.URL[] {toolchainFile("ecj-4.12.jar").toURI().toURL()},
                                null);
                java.io.PrintWriter out = new java.io.PrintWriter(diagnostics, true)) {
            Class<?> compiler =
                    Class.forName(
                            "org.eclipse.jdt.core.compiler.batch.BatchCompiler", true, loader);
            Class<?> progress =
                    Class.forName(
                            "org.eclipse.jdt.core.compiler.CompilationProgress", true, loader);
            Object compiled =
                    compiler.getMethod(
                                    "compile",
                                    String[].class,
                                    java.io.PrintWriter.class,
                                    java.io.PrintWriter.class,
                                    progress)
                            .invoke(null, arguments.toArray(new String[0]), out, out, null);
            if (!Boolean.TRUE.equals(compiled)) throw new AssertionError(diagnostics.toString());
        } catch (ReflectiveOperationException unavailable) {
            throw new AssertionError(unavailable);
        }
        Map<String, byte[]> classes = new TreeMap<>();
        try (Stream<Path> files = Files.walk(output)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator)
                classes.put(
                        output.relativize(file).toString().replace(File.separatorChar, '/'),
                        Files.readAllBytes(file));
        }
        return classes;
    }

    static Map<String, byte[]> sample(File scratch) throws IOException {
        return compile(scratch, Map.of("fixture/Sample.java", SAMPLE));
    }

    static File platformJar() {
        return toolchainFile("android/android-36.jar");
    }

    private static File toolchainFile(String name) {
        File directory = new File(System.getProperty("user.dir"));
        while (directory != null) {
            File candidate = new File(directory, "toolchain/input/" + name);
            if (candidate.isFile()) return candidate;
            directory = directory.getParentFile();
        }
        throw new IllegalStateException("Toolchain input is missing: " + name);
    }

    static byte[] platformClass(String entry) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(platformJar())) {
            return zip.getInputStream(zip.getEntry(entry)).readAllBytes();
        }
    }
}
