package dev.smallwright.controller;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PlatformFilesTest {
    private static final String ENTRY = "android-36/android.jar";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private FixtureServer server;
    private byte[] jar;
    private byte[] zip;

    @Before
    public void startServer() throws Exception {
        jar = new byte[40_000];
        new Random(7).nextBytes(jar);
        zip = zipOf(jar);
        server = new FixtureServer((start, out) -> FixtureServer.sendAll(out, zip));
    }

    @After
    public void stopServer() throws IOException {
        server.close();
    }

    @Test
    public void downloadsVerifiesAndExtractsTheJarUnchanged() throws Exception {
        File directory = temporary.newFolder("platform");
        PlatformFiles files = new PlatformFiles(directory, fixturePackage());
        List<Long> reported = new ArrayList<>();

        assertFalse(files.isPresent());
        files.download((done, total) -> reported.add(done));

        assertTrue(files.isPresent());
        assertArrayEquals(jar, Files.readAllBytes(files.verifiedJar().toPath()));
        assertEquals(Long.valueOf(zip.length), reported.get(reported.size() - 1));
        assertFalse(new File(directory, "platform.zip.part").exists());
        assertFalse(new File(directory, "platform.zip").exists());
    }

    @Test
    public void aDownloadCutShortFailsAndKeepsWhatArrivedForResuming() throws Exception {
        File directory = temporary.newFolder("platform");
        server.handler(
                (start, out) ->
                        FixtureServer.send(
                                out, 200, "", zip.length, java.util.Arrays.copyOf(zip, 1000)));
        PlatformFiles files = new PlatformFiles(directory, fixturePackage());

        try {
            files.download((done, total) -> {});
            fail("a cut-short download must not be accepted");
        } catch (IOException expected) {
            assertFalse(expected instanceof PlatformFiles.Refused);
        }

        assertFalse(files.isPresent());
        assertEquals(1000, new File(directory, "platform.zip.part").length());
    }

    @Test
    public void aLargerFileThanGooglePublishedIsRefusedAndDiscarded() throws Exception {
        File directory = temporary.newFolder("platform");
        byte[] bigger = java.util.Arrays.copyOf(zip, zip.length + 5000);
        server.handler((start, out) -> FixtureServer.send(out, 200, "", -1, bigger));
        PlatformFiles files = new PlatformFiles(directory, fixturePackage());

        assertRefused(files);
        server.handler((start, out) -> FixtureServer.sendAll(out, bigger));
        assertRefused(files);
    }

    @Test
    public void aFileWithTheWrongChecksumIsRefusedAndDiscarded() throws Exception {
        File directory = temporary.newFolder("platform");
        byte[] altered = zip.clone();
        altered[altered.length / 2] ^= 1;
        server.handler((start, out) -> FixtureServer.sendAll(out, altered));
        PlatformFiles files = new PlatformFiles(directory, fixturePackage());

        assertRefused(files);
    }

    @Test
    public void aJarThatDiffersFromThePinnedOneIsRefusedAndNotKept() throws Exception {
        File directory = temporary.newFolder("platform");
        PlatformFiles.Package pinned = fixturePackage();
        PlatformFiles files =
                new PlatformFiles(
                        directory,
                        new PlatformFiles.Package(
                                pinned.revision(),
                                pinned.url(),
                                pinned.size(),
                                pinned.sha1(),
                                pinned.sha256(),
                                pinned.entry(),
                                pinned.entrySize(),
                                hex("SHA-256", new byte[] {1})));

        assertRefused(files);
    }

    @Test
    public void aCutShortDownloadResumesFromWhatArrived() throws Exception {
        File directory = temporary.newFolder("platform");
        long[] rangeStarts = {-1, -1};
        int[] call = {0};
        server.handler(
                (start, out) -> {
                    rangeStarts[call[0]] = start;
                    if (call[0]++ == 0)
                        FixtureServer.send(
                                out, 200, "", zip.length, java.util.Arrays.copyOf(zip, 1000));
                    else
                        FixtureServer.send(
                                out,
                                206,
                                "Content-Range: bytes "
                                        + start
                                        + "-"
                                        + (zip.length - 1)
                                        + "/"
                                        + zip.length
                                        + "\r\n",
                                zip.length - start,
                                java.util.Arrays.copyOfRange(zip, (int) start, zip.length));
                });
        PlatformFiles files = new PlatformFiles(directory, fixturePackage());
        List<Long> reported = new ArrayList<>();

        try {
            files.download((done, total) -> {});
            fail("the first attempt is cut short");
        } catch (IOException expected) {
        }
        files.download((done, total) -> reported.add(done));

        assertEquals(1000, rangeStarts[1]);
        assertTrue(reported.get(0) > 1000);
        assertArrayEquals(jar, Files.readAllBytes(files.verifiedJar().toPath()));
    }

    @Test
    public void aServerThatIgnoresTheResumeRequestIsStartedOverCleanly() throws Exception {
        File directory = temporary.newFolder("platform");
        Files.write(new File(directory, "platform.zip.part").toPath(), new byte[] {9, 9, 9});
        PlatformFiles files = new PlatformFiles(directory, fixturePackage());

        files.download((done, total) -> {});

        assertArrayEquals(jar, Files.readAllBytes(files.verifiedJar().toPath()));
    }

    @Test
    public void aLeftoverLargerThanGoogleFileIsDiscardedBeforeAsking() throws Exception {
        File directory = temporary.newFolder("platform");
        Files.write(new File(directory, "platform.zip.part").toPath(), new byte[zip.length + 10]);
        PlatformFiles files = new PlatformFiles(directory, fixturePackage());

        files.download((done, total) -> {});

        assertArrayEquals(jar, Files.readAllBytes(files.verifiedJar().toPath()));
    }

    @Test
    public void acceptanceIsRecordedOnceWithDateAndPackageRevision() throws Exception {
        PlatformFiles files = new PlatformFiles(temporary.newFolder("platform"), fixturePackage());

        assertNull(files.acceptance());
        files.recordAcceptance("2026-10-18", "April 28, 2026");
        files.recordAcceptance("2026-10-19", "May 1, 2026");

        PlatformFiles.Acceptance recorded = files.acceptance();
        assertEquals("platform-test", recorded.revision());
        assertEquals("2026-10-18", recorded.acceptedOn());
        assertEquals("April 28, 2026", recorded.termsDate());
    }

    @Test
    public void aKeptJarThatWasChangedIsNotPresentAndNotHanded() throws Exception {
        File directory = temporary.newFolder("platform");
        PlatformFiles files = new PlatformFiles(directory, fixturePackage());
        files.download((done, total) -> {});
        byte[] changed = jar.clone();
        changed[100] ^= 1;
        Files.write(new File(directory, ENTRY).toPath(), changed);

        assertTrue(files.isPresent());
        try {
            files.verifiedJar();
            fail("a changed jar must not be handed on");
        } catch (IOException expected) {
        }
        Files.write(new File(directory, ENTRY).toPath(), new byte[3]);
        assertFalse(files.isPresent());
    }

    @Test
    public void theShippedTermsCarryTheDateTheAcceptanceRecords() throws Exception {
        String terms =
                new String(
                        Files.readAllBytes(
                                new File("src/main/assets/android-sdk-terms.txt").toPath()),
                        java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(terms.contains("April 28, 2026"));
        assertTrue(terms.contains("2026-10-18"));
        assertTrue(terms.contains("3.4 You may not use the SDK for any purpose"));
        assertTrue(terms.trim().endsWith("April 28, 2026"));
        assertTrue(PlatformFiles.TERMS_VERSION.contains("April 28, 2026"));
        assertTrue(PlatformFiles.TERMS_VERSION.contains("2026-10-18"));
    }

    private void assertRefused(PlatformFiles files) {
        try {
            files.download((done, total) -> {});
            fail("the download must be refused");
        } catch (PlatformFiles.Refused expected) {
            assertNotNull(expected.getMessage());
        } catch (IOException other) {
            fail("expected a refusal, got " + other);
        }
        assertFalse(files.isPresent());
        assertFalse(new File(temporary.getRoot(), "platform/platform.zip.part").exists());
    }

    private PlatformFiles.Package fixturePackage() throws Exception {
        return new PlatformFiles.Package(
                "platform-test",
                server.url(),
                zip.length,
                hex("SHA-1", zip),
                hex("SHA-256", zip),
                ENTRY,
                jar.length,
                hex("SHA-256", jar));
    }

    private static byte[] zipOf(byte[] jar) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry("android-36/data/notes.txt"));
            out.write("not the jar".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry(ENTRY));
            out.write(jar);
            out.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static String hex(String algorithm, byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte value : MessageDigest.getInstance(algorithm).digest(bytes))
            text.append(String.format("%02x", value));
        return text.toString();
    }
}
