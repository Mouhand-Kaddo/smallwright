package dev.smallwright.controller;

import static org.junit.Assert.*;

import dev.smallwright.libraries.MavenFixture;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Lookups against the real Android platform jar and library JAR and AAR files. */
public final class CodeLookupTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final CodeLookup.Archive PLATFORM =
            new CodeLookup.Archive("the Android platform files", JavaFixtures.platformJar(), false);

    private static String find(String symbol) throws IOException {
        return find(symbol, 1).text;
    }

    private static CodeLookup.Result find(String symbol, int offset) throws IOException {
        return CodeLookup.find(List.of(PLATFORM), symbol, offset, () -> true);
    }

    private File file(String name, byte[] bytes) throws IOException {
        File file = new File(temporary.getRoot(), name);
        Files.write(file.toPath(), bytes);
        return file;
    }

    @Test
    public void findsAConstantByClassAndMember() throws Exception {
        CodeLookup.Result result = find("Paint.STRIKE_THRU_TEXT_FLAG", 1);
        assertEquals(
                "public class android.graphics.Paint, in the Android platform files:\n"
                        + "  public static final int STRIKE_THRU_TEXT_FLAG = 16\n",
                result.text);
        assertEquals("", result.detail);
        assertEquals(result.text, find("android.graphics.Paint.STRIKE_THRU_TEXT_FLAG"));
        assertEquals(result.text, find("android.graphics.Paint#STRIKE_THRU_TEXT_FLAG"));
    }

    @Test
    public void findsMethodsConstructorsAndInheritedMembers() throws Exception {
        assertTrue(find("Paint#setFlags").contains("\n  public void setFlags(int flags)\n"));
        assertTrue(find("Paint#setFlags(int)").contains("\n  public void setFlags(int flags)\n"));
        String constructors = find("Paint.Paint");
        assertTrue(constructors, constructors.contains("\n  public Paint()\n"));
        assertTrue(constructors, constructors.contains("\n  public Paint(int flags)\n"));
        String inherited = find("android.app.Activity#getString");
        assertTrue(
                inherited,
                inherited.startsWith(
                        "public class android.app.Activity extends"
                                + " android.view.ContextThemeWrapper implements"));
        assertTrue(
                inherited,
                inherited.contains(
                        "\n  public final String getString(int resId)  (from"
                                + " android.content.Context)\n"));
    }

    @Test
    public void listsAWholeClassWithItsNestedClasses() throws Exception {
        String paint = find("android.graphics.Paint");
        assertTrue(
                paint,
                paint.startsWith(
                        "public class android.graphics.Paint, in the Android platform files.\n"
                                + "Nested classes: Paint.Align, Paint.Cap, Paint.FontMetrics,"
                                + " Paint.FontMetricsInt, Paint.Join, Paint.Style\n"
                                + "  public Paint()\n"));
        assertTrue(
                paint, paint.contains("\n  public static final int STRIKE_THRU_TEXT_FLAG = 16\n"));
        assertTrue(paint, paint.contains("\n  public void setFlags(int flags)\n"));
        assertTrue(
                find("Paint.Style"),
                find("Paint.Style").startsWith("public enum android.graphics.Paint.Style,"));
    }

    @Test
    public void pagesAHugeClassInsteadOfCuttingIt() throws Exception {
        String first = find("android.view.View", 1).text;
        assertTrue(first.getBytes(StandardCharsets.UTF_8).length <= CodeLookup.MAX_PAGE_BYTES);
        java.util.regex.Matcher page =
                java.util.regex.Pattern.compile(
                                "\nMembers 1 to (\\d+) of (\\d+)\\. Call lookup_code with offset"
                                        + " (\\d+) for more\\.\n$")
                        .matcher(first);
        assertTrue(first.substring(first.length() - 200), page.find());
        int shown = Integer.parseInt(page.group(1));
        int total = Integer.parseInt(page.group(2));
        assertEquals(shown + 1, Integer.parseInt(page.group(3)));
        assertTrue(total > 800);
        String second = find("View", shown + 1).text;
        assertTrue(second, second.contains("\nMembers " + (shown + 1) + " to "));
        String firstLast = first.split("\n")[first.split("\n").length - 2];
        assertFalse(second.contains(firstLast + "\n"));
        String past = find("View", total + 1).text;
        assertTrue(past, past.contains("has " + total + " members"));
    }

    @Test
    public void suggestsCloseMembersWhenAMemberDoesNotExist() throws Exception {
        CodeLookup.Result missing = find("Paint.STRIKETHROUGH_TEXT_FLAG", 1);
        assertEquals("Not found", missing.detail);
        assertEquals(
                "android.graphics.Paint, in the Android platform files, has no member"
                        + " STRIKETHROUGH_TEXT_FLAG, nor do the classes it extends. Close names:\n"
                        + "  public static final int STRIKE_THRU_TEXT_FLAG = 16\n",
                missing.text);
        assertTrue(
                find("Paint.STRIKE")
                        .contains("\n  public static final int STRIKE_THRU_TEXT_FLAG = 16\n"));
        String none = find("Paint#qqqqqqqqqqqq");
        assertTrue(none, none.contains("has no member qqqqqqqqqqqq"));
        assertTrue(none, none.contains("Look up android.graphics.Paint alone"));
    }

    @Test
    public void suggestsCloseClassesWhenAClassDoesNotExist() throws Exception {
        CodeLookup.Result missing = find("android.graphics.Paints", 1);
        assertEquals("Not found", missing.detail);
        assertTrue(
                missing.text,
                missing.text.startsWith(
                        "No class android.graphics.Paints in the Android platform files or the"
                                + " project's libraries. Close names: android.graphics.Paint,"));
        assertTrue(find("android.widget.Paint").contains("Close names: android.graphics.Paint"));
        assertTrue(find("TextVeiw").contains("android.widget.TextView"));
        assertTrue(find("TextVeiw.setText").contains("android.widget.TextView"));
        String none = find("Qqqqqqqqqqqqqqq");
        assertTrue(none, none.contains("search_libraries"));
    }

    @Test
    public void listsEveryClassASimpleNameMatches() throws Exception {
        CodeLookup.Result several = find("OnClickListener", 1);
        assertEquals("Several classes", several.detail);
        assertTrue(
                several.text, several.text.startsWith("Several classes are named OnClickListener"));
        assertTrue(several.text, several.text.contains("\n  android.view.View.OnClickListener\n"));
        assertTrue(
                several.text,
                several.text.contains("\n  android.content.DialogInterface.OnClickListener\n"));
        assertTrue(
                find("View.OnClickListener#onClick")
                        .contains("\n  public abstract void onClick(android.view.View)\n"));
        assertTrue(find("View").startsWith("public class android.view.View "));
    }

    @Test
    public void refusesSomethingThatIsNotAJavaName() throws Exception {
        CodeLookup.Result refused = find("Paint; rm -rf /", 1);
        assertEquals("Not a class name", refused.detail);
        assertTrue(refused.text, refused.text.contains("android.graphics.Paint"));
    }

    @Test
    public void readsLibraryJarsAndTheClassesJarAndLibsJarsOfAnAndroidLibrary() throws Exception {
        Map<String, byte[]> sample = JavaFixtures.sample(temporary.newFolder());
        Map<String, byte[]> extra =
                JavaFixtures.compile(
                        temporary.newFolder(),
                        Map.of(
                                "extra/Helper.java",
                                "package extra; public class Helper extends fixture.Sample<String>"
                                        + " { public Helper() { super(1); }"
                                        + " public String get(int i) { return null; }"
                                        + " public int size() { return 0; }"
                                        + " public <T extends Runnable & java.io.Closeable> T"
                                        + " pick(java.util.Map<String, ? extends T> f, long a)"
                                        + " { return null; } }",
                                "fixture/Sample.java",
                                JavaFixtures.SAMPLE));
        extra.keySet().removeIf(name -> !name.startsWith("extra/"));
        Map<String, byte[]> aar = new LinkedHashMap<>();
        aar.put("AndroidManifest.xml", MavenFixture.manifest("view", ""));
        aar.put("classes.jar", MavenFixture.zip(sample));
        aar.put("libs/extra.jar", MavenFixture.zip(extra));
        aar.put("libs/../escape.jar", MavenFixture.zip(Map.of("escape/Out.class", new byte[4])));
        List<CodeLookup.Archive> archives =
                List.of(
                        PLATFORM,
                        new CodeLookup.Archive(
                                "library androidx.example:view:1",
                                file("view-1.aar", MavenFixture.zip(aar)),
                                true));
        String twice = CodeLookup.find(archives, "Sample#twice", 1, () -> true).text;
        assertEquals(
                "public abstract class fixture.Sample<K extends Comparable<? super K>> extends"
                        + " java.util.AbstractList<K> implements java.io.Serializable, in library"
                        + " androidx.example:view:1:\n"
                        + "  public static int twice(int value)\n",
                twice);
        String inherited = CodeLookup.find(archives, "extra.Helper#twice", 1, () -> true).text;
        assertTrue(
                inherited,
                inherited.contains(
                        "\n  public static int twice(int value)  (from fixture.Sample)\n"));
        assertTrue(
                CodeLookup.find(archives, "Sample.Mode", 1, () -> true)
                        .text
                        .contains("public enum fixture.Sample.Mode, in library"));
        assertEquals("Not found", CodeLookup.find(archives, "escape.Out", 1, () -> true).detail);
        Map<String, byte[]> plain = new LinkedHashMap<>(sample);
        plain.put("META-INF/versions/11/fixture/Sample.class", new byte[4]);
        plain.put("renamed/Copy.class", sample.get("fixture/Sample$Mode.class"));
        List<CodeLookup.Archive> jar =
                List.of(
                        new CodeLookup.Archive(
                                "library org.example:sample:1",
                                file("sample-1.jar", MavenFixture.zip(plain)),
                                false));
        String fromJar = CodeLookup.find(jar, "fixture.Sample.NAME", 1, () -> true).text;
        assertTrue(
                fromJar,
                fromJar.endsWith("\n  public static final String NAME = \"say \\\"hi\\\"\\n\"\n"));
        String renamed = CodeLookup.find(jar, "renamed.Copy", 1, () -> true).text;
        assertTrue(
                renamed,
                renamed.startsWith(
                        "public enum fixture.Sample.Mode, in library org.example:sample:1.\n"));
    }

    @Test
    public void refusesAMalformedClassFileAndAnUnreadableArchive() throws Exception {
        List<CodeLookup.Archive> broken =
                List.of(
                        new CodeLookup.Archive(
                                "library org.example:broken:1",
                                file(
                                        "broken-1.jar",
                                        MavenFixture.zip(
                                                Map.of(
                                                        "bad/Broken.class",
                                                        new byte[] {
                                                            (byte) 0xCA, (byte) 0xFE, 1, 2
                                                        }))),
                                false));
        IOException malformed =
                assertThrows(
                        IOException.class,
                        () -> CodeLookup.find(broken, "bad.Broken", 1, () -> true));
        assertEquals(
                "bad/Broken.class in library org.example:broken:1 is not a valid class file",
                malformed.getMessage());
    }

    @Test
    public void anUnreadableLibraryIsSkippedWithANoteAndThePlatformStillAnswers() throws Exception {
        CodeLookup.Archive notZip =
                new CodeLookup.Archive(
                        "library org.example:text:1",
                        file("text-1.jar", "not a zip".getBytes(StandardCharsets.UTF_8)),
                        false);
        CodeLookup.Result found =
                CodeLookup.find(
                        List.of(notZip, PLATFORM), "Paint.STRIKE_THRU_TEXT_FLAG", 1, () -> true);
        assertEquals(
                "library org.example:text:1 could not be read, so it was not searched.\n"
                        + "public class android.graphics.Paint, in the Android platform files:\n"
                        + "  public static final int STRIKE_THRU_TEXT_FLAG = 16\n",
                found.text);
        assertEquals("", found.detail);
    }

    @Test
    public void stopEndsALookupWhileItReadsTheArchives() {
        java.util.concurrent.atomic.AtomicInteger checks =
                new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(
                java.util.concurrent.CancellationException.class,
                () ->
                        CodeLookup.find(
                                List.of(PLATFORM),
                                "Paint",
                                1,
                                () -> checks.incrementAndGet() < 100));
        assertEquals(100, checks.get());
    }
}
