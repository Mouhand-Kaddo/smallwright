package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class CrashReportImportTest {
    private static final String VALID =
            "Generated app crash\n"
                    + "Exception messages and values omitted.\n"
                    + "Cause 0: java.lang.IllegalStateException\n"
                    + "  at dev.smallwright.generated.counter.MainActivity$1.onClick:33\n"
                    + "  at android.view.View.-$$Nest$mperformClickInternal:0\n"
                    + "  at android.view.View.performClick:900\n";

    @Test
    public void acceptsBoundedGeneratedReportForExplicitFeedback() throws Exception {
        String imported =
                CrashReportImport.read(
                        new ByteArrayInputStream(VALID.getBytes(StandardCharsets.UTF_8)));
        assertEquals(VALID, imported);
        assertTrue(
                CrashReportImport.composeFeedback("The counter closed", imported)
                        .contains("Cause 0: java.lang.IllegalStateException"));
    }

    @Test
    public void rejectsMessagesAndUnknownTextEvenWithExpectedHeader() {
        String leaked = VALID + "  at /sdcard/secret.txt\n";
        assertThrows(
                IOException.class,
                () ->
                        CrashReportImport.read(
                                new ByteArrayInputStream(leaked.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void acceptsTheNamesKotlinAndOtherScriptsGiveFrames() throws Exception {
        String report =
                "Generated app crash\n"
                        + "Exception messages and values omitted.\n"
                        + "Cause 0: dev.app.Ошибка\n"
                        + "  at dev.app.ui.Écran.getCount-impl:12\n"
                        + "  at dev.app.Counter.plus-WZ4Q5Ns:3\n"
                        + "  at dev.app.MainActivity.lambda$onCreate$0:-1\n"
                        + "  at dev.app.Counter.<init>:5\n";
        assertEquals(
                report,
                CrashReportImport.read(
                        new ByteArrayInputStream(report.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void stillRejectsWordsSpacesAndPathsInAFrame() {
        for (String line :
                new String[] {
                    "  at dev.app.Main.a name:1\n",
                    "  at dev/app/Main.run:1\n",
                    "  at dev.app.Main.run: 1\n",
                    "  at dev.app.Main.run:1 note\n"
                }) {
            String report = VALID + line;
            assertThrows(
                    line,
                    IOException.class,
                    () ->
                            CrashReportImport.read(
                                    new ByteArrayInputStream(
                                            report.getBytes(StandardCharsets.UTF_8))));
        }
    }

    @Test
    public void rejectsOversizeAndInvalidUtf8() {
        byte[] oversize = new byte[16_385];
        assertThrows(
                IOException.class,
                () -> CrashReportImport.read(new ByteArrayInputStream(oversize)));
        assertThrows(
                IOException.class,
                () ->
                        CrashReportImport.read(
                                new ByteArrayInputStream(new byte[] {(byte) 0xc3, (byte) 0x28})));
    }

    @Test
    public void rejectsFeedbackThatWouldExceedRequestBudget() throws Exception {
        String imported =
                CrashReportImport.read(
                        new ByteArrayInputStream(VALID.getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                IllegalArgumentException.class,
                () -> CrashReportImport.composeFeedback("x".repeat(4096), imported));
    }
}
