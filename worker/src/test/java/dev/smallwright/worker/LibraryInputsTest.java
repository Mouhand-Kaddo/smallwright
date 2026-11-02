package dev.smallwright.worker;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class LibraryInputsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final byte[] GSON = "gson jar bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HELPER = "helper jar bytes".getBytes(StandardCharsets.UTF_8);

    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
            text.append(String.format("%02x", value));
        return text.toString();
    }

    private static LibraryInputs.Opener serving(byte[]... files) {
        return index -> new ByteArrayInputStream(files[index]);
    }

    @Test
    public void keepsCheckedLibrariesAndTheNextBuildTakesThemOnce() throws Exception {
        LibraryInputs inputs = new LibraryInputs(temporary.newFolder("libraries"));
        inputs.accept(List.of(sha256(GSON), sha256(HELPER)), serving(GSON, HELPER));
        List<File> taken = inputs.takeSelected();
        assertEquals(2, taken.size());
        assertArrayEquals(GSON, Files.readAllBytes(taken.get(0).toPath()));
        assertArrayEquals(HELPER, Files.readAllBytes(taken.get(1).toPath()));
        assertEquals(List.of(), inputs.takeSelected());

        inputs.accept(List.of(), serving());
        assertEquals(List.of(), inputs.takeSelected());
    }

    @Test
    public void refusesBytesThatDoNotMatchTheGivenHashAndKeepsNothing() throws Exception {
        File directory = temporary.newFolder("libraries");
        LibraryInputs inputs = new LibraryInputs(directory);
        inputs.accept(List.of(sha256(GSON)), serving(GSON));
        IOException refused =
                assertThrows(
                        IOException.class,
                        () -> inputs.accept(List.of(sha256(HELPER)), serving(GSON)));
        assertTrue(refused.getMessage(), refused.getMessage().contains("do not match"));
        assertEquals(List.of(), inputs.takeSelected());
        assertFalse(new File(directory, sha256(HELPER) + ".jar").exists());
    }

    @Test
    public void refusesMalformedHashesDuplicatesAndOversizedLibraries() throws Exception {
        LibraryInputs inputs = new LibraryInputs(temporary.newFolder("libraries"));
        assertThrows(IOException.class, () -> inputs.accept(List.of("../x"), serving(GSON)));
        assertThrows(
                IOException.class,
                () -> inputs.accept(List.of(sha256(GSON), sha256(GSON)), serving(GSON, GSON)));
        InputStream endless =
                new InputStream() {
                    @Override
                    public int read() {
                        return 0;
                    }

                    @Override
                    public int read(byte[] buffer, int offset, int length) {
                        return length;
                    }
                };
        IOException large =
                assertThrows(
                        IOException.class,
                        () -> inputs.accept(List.of("0".repeat(64)), index -> endless));
        assertTrue(large.getMessage(), large.getMessage().contains("larger"));
    }

    @Test
    public void aKeptLibraryThatChangedIsRefusedAtBuildTime() throws Exception {
        File directory = temporary.newFolder("libraries");
        LibraryInputs inputs = new LibraryInputs(directory);
        inputs.accept(List.of(sha256(GSON)), serving(GSON));
        Files.write(new File(directory, sha256(GSON) + ".jar").toPath(), HELPER);
        IOException changed = assertThrows(IOException.class, inputs::takeSelected);
        assertTrue(changed.getMessage(), changed.getMessage().contains("changed"));
    }
}
