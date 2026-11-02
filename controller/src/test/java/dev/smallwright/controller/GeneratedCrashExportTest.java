package dev.smallwright.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import org.junit.Test;

public final class GeneratedCrashExportTest {
    @Test
    public void generatedAppKeepsCrashReportPrivateUntilExplicitDocumentExport() throws Exception {
        Method sourceMethod = ProjectSourceStore.class.getDeclaredMethod("defaultSource");
        sourceMethod.setAccessible(true);
        String source = (String) sourceMethod.invoke(null);
        Method manifestMethod = ProjectSourceStore.class.getDeclaredMethod("manifest", int.class);
        manifestMethod.setAccessible(true);
        String manifest = (String) manifestMethod.invoke(null, 1);

        assertTrue(source.contains("Thread.setDefaultUncaughtExceptionHandler"));
        assertTrue(source.contains("REPORT = \"crash-report.txt\""));
        assertTrue(source.contains("openFileOutput(REPORT, MODE_PRIVATE)"));
        assertTrue(source.contains("Preview crash report"));
        assertTrue(source.contains("Intent.ACTION_CREATE_DOCUMENT"));
        assertTrue(source.contains("getContentResolver().openOutputStream"));
        assertTrue(source.contains("No crash report saved"));
        assertTrue(source.contains("pendingReport = report;"));
        assertTrue(source.contains("byte[] report = pendingReport;"));
        assertTrue(source.contains("\"content\".equals(data.getData().getScheme())"));
        assertFalse(
                source.contains("byte[] report = readReport();\n      if (report.length == 0)"));
        assertFalse(source.contains("current.getMessage()"));
        assertFalse(source.contains("frames[i].toString()"));
        assertFalse(source.contains("Intent.ACTION_SEND"));
        assertFalse(manifest.contains("uses-permission"));
        assertFalse(manifest.contains("provider"));
    }
}
