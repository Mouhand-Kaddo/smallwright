package dev.smallwright.controller;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Declarations read from real class files, and malformed class files refused cleanly. */
public final class ClassFileTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static List<String> declarations(ClassFile file) {
        List<String> lines = new ArrayList<>();
        for (ClassFile.Member member : file.members()) lines.add(file.declaration(member));
        return lines;
    }

    private Map<String, byte[]> sample() throws IOException {
        return JavaFixtures.sample(temporary.newFolder());
    }

    @Test
    public void readsConstantsGenericsVarargsDeprecationAndParameterNames() throws Exception {
        ClassFile sample = ClassFile.parse(sample().get("fixture/Sample.class"));
        assertEquals("fixture/Sample", sample.name);
        assertEquals("java/util/AbstractList", sample.superName);
        assertEquals(
                "public abstract class fixture.Sample<K extends Comparable<? super K>>"
                        + " extends java.util.AbstractList<K> implements java.io.Serializable",
                sample.declaration());
        assertEquals(
                List.of(
                        "public Sample(int size, String... names)",
                        "public static final long BIG = 1099511627776L",
                        "public static final float HALF = 0.5f",
                        "public static final char LETTER = 'x'",
                        "public static final String NAME = \"say \\\"hi\\\"\\n\"",
                        "public static final double NOTHING = Double.NaN",
                        "public static final boolean ON = true",
                        "protected int counter",
                        "public <V> V first(java.util.List<V> items)",
                        "@Deprecated public void old()",
                        "public abstract <T extends Runnable & java.io.Closeable> T pick("
                                + "java.util.Map<String, ? extends T>, long)"
                                + " throws java.io.IOException",
                        "public static int twice(int value)"),
                declarations(sample));
    }

    @Test
    public void readsNestedInterfacesAndEnums() throws Exception {
        Map<String, byte[]> classes = sample();
        ClassFile listener = ClassFile.parse(classes.get("fixture/Sample$Listener.class"));
        assertEquals("public interface fixture.Sample.Listener", listener.declaration());
        assertEquals(
                List.of("public abstract void heard(long, double)", "public default void reset()"),
                declarations(listener));
        ClassFile mode = ClassFile.parse(classes.get("fixture/Sample$Mode.class"));
        assertEquals("public enum fixture.Sample.Mode", mode.declaration());
        assertEquals(
                List.of(
                        "public static final fixture.Sample.Mode OFF",
                        "public static final fixture.Sample.Mode ON",
                        "public static fixture.Sample.Mode valueOf(String)",
                        "public static fixture.Sample.Mode[] values()"),
                declarations(mode));
    }

    @Test
    public void readsTheAndroidPlatformJarsClasses() throws Exception {
        ClassFile paint =
                ClassFile.parse(JavaFixtures.platformClass("android/graphics/Paint.class"));
        assertEquals("public class android.graphics.Paint", paint.declaration());
        List<String> lines = declarations(paint);
        assertTrue(lines.contains("public static final int STRIKE_THRU_TEXT_FLAG = 16"));
        assertTrue(lines.contains("public void setFlags(int flags)"));
        assertTrue(lines.contains("public Paint(android.graphics.Paint paint)"));
        ClassFile adapter =
                ClassFile.parse(JavaFixtures.platformClass("android/widget/ArrayAdapter.class"));
        assertEquals(
                "public class android.widget.ArrayAdapter<T> extends android.widget.BaseAdapter"
                        + " implements android.widget.Filterable,"
                        + " android.widget.ThemedSpinnerAdapter",
                adapter.declaration());
        assertTrue(
                declarations(adapter)
                        .contains(
                                "public ArrayAdapter(android.content.Context context, int resource,"
                                        + " java.util.List<T> objects)"));
        ClassFile view =
                ClassFile.parse(
                        JavaFixtures.platformClass("android/view/View$OnClickListener.class"));
        assertEquals(
                List.of("public abstract void onClick(android.view.View)"), declarations(view));
    }

    @Test
    public void refusesEveryTruncationTrailingBytesAndABadMagicNumber() throws Exception {
        byte[] valid = sample().get("fixture/Sample.class");
        for (int length = 0; length < valid.length; length++) {
            byte[] cut = Arrays.copyOf(valid, length);
            assertThrows("length " + length, IOException.class, () -> ClassFile.parse(cut));
        }
        byte[] longer = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IOException.class, () -> ClassFile.parse(longer));
        byte[] magic = valid.clone();
        magic[0] = 0;
        assertThrows(IOException.class, () -> ClassFile.parse(magic));
        assertThrows(IOException.class, () -> ClassFile.parse(new byte[ClassFile.MAX_BYTES + 1]));
    }

    private static byte[] classWithField(String descriptor, String signature) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
        out.writeInt(0xCAFEBABE);
        out.writeShort(0);
        out.writeShort(52);
        List<String> texts =
                List.of("Deep", "java/lang/Object", "f", descriptor, "Signature", signature);
        out.writeShort(texts.size() + 3);
        for (String text : texts) {
            out.writeByte(1);
            out.writeUTF(text);
        }
        out.writeByte(7);
        out.writeShort(1);
        out.writeByte(7);
        out.writeShort(2);
        out.writeShort(0x0001);
        out.writeShort(7);
        out.writeShort(8);
        out.writeShort(0);
        out.writeShort(1);
        out.writeShort(0x0001);
        out.writeShort(3);
        out.writeShort(4);
        out.writeShort(1);
        out.writeShort(5);
        out.writeInt(2);
        out.writeShort(6);
        out.writeShort(0);
        out.writeShort(0);
        return bytes.toByteArray();
    }

    @Test
    public void arraysPastTheJvmsLimitOf255DimensionsAreRefusedNotOverflowed() throws Exception {
        ClassFile widest =
                ClassFile.parse(classWithField("[".repeat(255) + "I", "[".repeat(255) + "I"));
        assertEquals(List.of("public int" + "[]".repeat(255) + " f"), declarations(widest));
        assertThrows(
                IOException.class,
                () -> ClassFile.parse(classWithField("[".repeat(256) + "I", "I")));
        assertThrows(
                IOException.class,
                () -> ClassFile.parse(classWithField("[".repeat(60_000) + "I", "I")));
    }

    @Test
    public void aDeeplyNestedGenericSignatureFallsBackToTheErasedType() throws Exception {
        String deep = "Ljava/util/List<".repeat(3_000) + "Ljava/lang/String;" + ">;".repeat(3_000);
        ClassFile file = ClassFile.parse(classWithField("Ljava/util/List;", deep));
        assertEquals(List.of("public java.util.List f"), declarations(file));
        ClassFile nested =
                ClassFile.parse(
                        classWithField(
                                "Ljava/util/List;",
                                "Ljava/util/List<Ljava/util/List<Ljava/lang/String;>;>;"));
        assertEquals(
                List.of("public java.util.List<java.util.List<String>> f"), declarations(nested));
    }

    @Test
    public void corruptedClassFilesAreReadOrRefusedNeverCrash() throws Exception {
        byte[] valid = JavaFixtures.platformClass("android/graphics/Paint.class");
        Random random = new Random(70);
        for (int round = 0; round < 3000; round++) {
            byte[] corrupt = valid.clone();
            for (int flips = 1 + random.nextInt(4); flips > 0; flips--)
                corrupt[8 + random.nextInt(corrupt.length - 8)] = (byte) random.nextInt(256);
            try {
                ClassFile file = ClassFile.parse(corrupt);
                file.declaration();
                declarations(file);
            } catch (IOException refused) {
                assertNotNull(refused.getMessage());
            }
        }
    }
}
