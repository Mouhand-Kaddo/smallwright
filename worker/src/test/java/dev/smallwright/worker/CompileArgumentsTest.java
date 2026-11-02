package dev.smallwright.worker;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Test;

public final class CompileArgumentsTest {
    @Test
    public void usesPlatformFileForBothBootAndApplicationClasspath() {
        File source = new File("source/Valid.java");
        File classes = new File("output/classes");
        File platform = new File("toolchain/android-36.jar");

        assertArrayEquals(
                new String[] {
                    "-1.8",
                    "-proc:none",
                    "-bootclasspath",
                    platform.getAbsolutePath(),
                    "-classpath",
                    platform.getAbsolutePath(),
                    "-d",
                    classes.getAbsolutePath(),
                    source.getAbsolutePath()
                },
                BuildService.compileArguments(source, classes, platform));
    }

    @Test
    public void alignmentExtraIsTheZipalignFieldThatEndsTheLocalHeaderOnTheBoundary() {
        long outputOffset = 37;
        for (String name : new String[] {"resources.arsc", "lib/arm64-v8a/libconscrypt_jni.so"})
            for (int alignment : new int[] {4, 16_384}) {
                byte[] extra = BuildService.alignmentExtra(outputOffset, name, alignment);
                assertEquals(0, (outputOffset + 30 + name.length() + extra.length) % alignment);
                assertTrue(extra.length >= 6 && extra.length < 6 + alignment);
                assertEquals((byte) 0x35, extra[0]);
                assertEquals((byte) 0xd9, extra[1]);
                assertEquals(extra.length - 4, (extra[2] & 0xff) | (extra[3] & 0xff) << 8);
                assertEquals(alignment, (extra[4] & 0xff) | (extra[5] & 0xff) << 8);
            }
    }
}
