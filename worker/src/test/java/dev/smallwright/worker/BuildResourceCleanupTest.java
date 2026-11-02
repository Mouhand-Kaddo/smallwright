package dev.smallwright.worker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class BuildResourceCleanupTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void preparationFailureClosesBothDescriptorsAndRemovesPartialScratch() throws Exception {
        File scratch = new File(temporary.getRoot(), "active-build");
        TrackedDescriptor snapshot = new TrackedDescriptor();
        TrackedDescriptor artifact = new TrackedDescriptor();
        IllegalStateException failure = new IllegalStateException("scratch preparation failed");
        assertTrue(scratch.mkdirs());
        Files.write(new File(scratch, "partial.zip").toPath(), new byte[] {1});

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                BuildService.withBuildResources(
                                        scratch,
                                        snapshot,
                                        artifact,
                                        () -> {
                                            throw failure;
                                        }));

        assertSame(failure, thrown);
        assertTrue(snapshot.closed);
        assertTrue(artifact.closed);
        assertFalse(scratch.exists());
    }

    @Test
    public void fatalBuildFailureStillClosesBothDescriptorsWithoutMaskingFailure()
            throws Exception {
        File scratch = new File(temporary.getRoot(), "active-build");
        assertTrue(scratch.mkdirs());
        Files.write(new File(scratch, "partial.apk").toPath(), new byte[] {1});
        TrackedDescriptor snapshot = new TrackedDescriptor();
        TrackedDescriptor artifact = new TrackedDescriptor();
        snapshot.closeFailure = new IOException("close failed");
        AssertionError failure = new AssertionError("compiler failed fatally");

        AssertionError thrown =
                assertThrows(
                        AssertionError.class,
                        () ->
                                BuildService.withBuildResources(
                                        scratch,
                                        snapshot,
                                        artifact,
                                        () -> {
                                            throw failure;
                                        }));

        assertSame(failure, thrown);
        assertTrue(snapshot.closed);
        assertTrue(artifact.closed);
        assertFalse(scratch.exists());
    }

    private static final class TrackedDescriptor implements Closeable {
        boolean closed;
        IOException closeFailure;

        @Override
        public void close() throws IOException {
            closed = true;
            if (closeFailure != null) throw closeFailure;
        }
    }
}
