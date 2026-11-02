package dev.smallwright.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The counter_04 baseline fixture from beffbeb, compiled against the real platform file. */
public final class LambdaCompileTest {
    private static final String PACKAGE = "dev.smallwright.generated.counter";
    private static final File PLATFORM =
            PlatformCompileTest.repositoryFile("toolchain/input/android/android-36.jar");

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void counterLambdaFixtureCompilesAndDexesWithThePinnedLambdaStubs() throws Exception {
        String dex = compileAndDex(COUNTER_BASELINE_FIXTURE, lambdaStubs());

        assertTrue("D8 must desugar the lambdas", dex.contains("ExternalSyntheticLambda0"));
        assertTrue(dex.contains("ExternalSyntheticLambda1"));
        assertFalse(
                "No java.lang.invoke class may reach the app", dex.contains("java/lang/invoke"));
    }

    @Test
    public void counterLambdaFixtureFailsWithoutTheLambdaStubs() throws Exception {
        try {
            compile(COUNTER_BASELINE_FIXTURE, new ArrayList<>());
            fail("The platform file alone compiled a lambda");
        } catch (BuildService.JavaCompileFailure expected) {
            assertEquals("16777540/10/1", numericProblems(expected));
        }
    }

    @Test
    public void counterAnonymousListenerControlCompilesAndDexesWithoutTheLambdaStubs()
            throws Exception {
        String dex = compileAndDex(anonymousListenerControlFixture(), new ArrayList<>());

        assertFalse(dex.contains("ExternalSyntheticLambda"));
        assertFalse(dex.contains("java/lang/invoke"));
    }

    @Test
    public void serializableLambdaCompilesAndDexesWithoutInvokeReferences() throws Exception {
        String dex =
                compileAndDex(
                        "package dev.smallwright.generated.counter;\n"
                                + "public final class MainActivity extends android.app.Activity {\n"
                                + "    Runnable task = (Runnable & java.io.Serializable) () -> {};\n"
                                + "}\n",
                        lambdaStubs());

        assertTrue(dex.contains("ExternalSyntheticLambda0"));
        assertFalse(dex.contains("java/lang/invoke"));
    }

    @Test
    public void changedLambdaStubSourceIsRefused() throws Exception {
        File scratch = temporaryFolder.newFolder("changed");
        try {
            LambdaStubs.write(
                    path ->
                            new ByteArrayInputStream(
                                    "package java.lang.invoke;".getBytes(StandardCharsets.UTF_8)),
                    scratch);
            fail("A changed lambda stub source was accepted");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("does not match its pin"));
        }
        assertFalse(
                new File(scratch, "core-lambda-stubs/java/lang/invoke/LambdaMetafactory.java")
                        .exists());
    }

    private List<File> lambdaStubs() throws Exception {
        return LambdaStubs.write(
                PlatformCompileTest::stubAsset, temporaryFolder.newFolder("stubs"));
    }

    private String compileAndDex(String fixture, List<File> stubs) throws Exception {
        File classes;
        try {
            classes = compile(fixture, stubs);
        } catch (BuildService.JavaCompileFailure failure) {
            throw new AssertionError("ECJ id/category/line: " + numericProblems(failure));
        }
        File dex = temporaryFolder.newFolder("dex");
        BuildService.dex(BuildService.generatedClassFiles(classes, PACKAGE), PLATFORM, dex);
        return new String(
                Files.readAllBytes(new File(dex, "classes.dex").toPath()),
                StandardCharsets.ISO_8859_1);
    }

    private File compile(String fixture, List<File> stubs) throws Exception {
        File main = new File(temporaryFolder.newFolder("java"), "MainActivity.java");
        Files.write(main.toPath(), fixture.getBytes(StandardCharsets.UTF_8));
        List<File> sources = new ArrayList<>(stubs);
        sources.add(main);
        File classes = temporaryFolder.newFolder("classes");
        BuildService.compileJavaOrThrow(
                sources, classes, PLATFORM, new TreeMap<>(Map.of("java/MainActivity.java", main)));
        return classes;
    }

    private static String numericProblems(BuildService.JavaCompileFailure failure) {
        StringBuilder values = new StringBuilder();
        if (failure.diagnostics == null) return "none";
        for (EcjDiagnostics.Problem problem : failure.diagnostics.problems) {
            if (values.length() > 0) values.append(',');
            values.append(problem.id)
                    .append('/')
                    .append(problem.category)
                    .append('/')
                    .append(problem.line);
        }
        return values.toString();
    }

    private static String anonymousListenerControlFixture() {
        String lambda = "setOnClickListener(v -> {";
        String anonymous =
                "setOnClickListener(new android.view.View.OnClickListener() {\n"
                        + "            @Override public void onClick(android.view.View v) {";
        assertEquals(2, count(COUNTER_BASELINE_FIXTURE, lambda));
        String fixture = COUNTER_BASELINE_FIXTURE.replace(lambda, anonymous);
        assertEquals(2, count(fixture, "        });\n"));
        return fixture.replace("        });\n", "            }\n        });\n");
    }

    private static int count(String source, String value) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }

    private static final String COUNTER_BASELINE_FIXTURE =
            "package dev.smallwright.generated.counter;\n"
                    + "\n"
                    + "import android.app.Activity;\n"
                    + "import android.os.Bundle;\n"
                    + "import android.widget.Button;\n"
                    + "import android.widget.LinearLayout;\n"
                    + "import android.widget.TextView;\n"
                    + "\n"
                    + "public final class MainActivity extends Activity {\n"
                    + "    private int mainCount;\n"
                    + "    private TextView value;\n"
                    + "\n"
                    + "    private void showCount() { value.setText(\"Counter: \" + mainCount); }\n"
                    + "\n"
                    + "    @Override public void onCreate(Bundle state) {\n"
                    + "        super.onCreate(state);\n"
                    + "        mainCount = getPreferences(0).getInt(\"mainCount\", 0);\n"
                    + "        LinearLayout box = new LinearLayout(this);\n"
                    + "        box.setOrientation(LinearLayout.VERTICAL);\n"
                    + "        value = new TextView(this);\n"
                    + "        box.addView(value);\n"
                    + "        Button increment = new Button(this);\n"
                    + "        increment.setText(\"Increment\");\n"
                    + "        increment.setOnClickListener(v -> {\n"
                    + "            mainCount++;\n"
                    + "            getPreferences(0).edit().putInt(\"mainCount\", mainCount).apply();\n"
                    + "            showCount();\n"
                    + "        });\n"
                    + "        box.addView(increment);\n"
                    + "        Button reset = new Button(this);\n"
                    + "        reset.setText(\"Reset\");\n"
                    + "        reset.setOnClickListener(v -> {\n"
                    + "            mainCount = 0;\n"
                    + "            getPreferences(0).edit().putInt(\"mainCount\", mainCount).apply();\n"
                    + "            showCount();\n"
                    + "        });\n"
                    + "        box.addView(reset);\n"
                    + "        setContentView(box);\n"
                    + "        showCount();\n"
                    + "    }\n"
                    + "}\n";
}
