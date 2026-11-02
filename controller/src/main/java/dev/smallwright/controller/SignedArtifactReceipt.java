package dev.smallwright.controller;

import android.content.Context;
import dev.smallwright.project.ProjectIdentity;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** A bounded pointer to a signed APK; every read verifies the APK independently. */
final class SignedArtifactReceipt {
    private static final String NAME = "verified-artifact.properties";
    private static final int MAX_RECEIPT_BYTES = 2048;

    static final class Record {
        final int revision;
        final File apk;
        final int versionCode;
        final String sha256;
        final String signerFingerprint;
        final VerifiedArtifact artifact;

        Record(
                int revision,
                File apk,
                int versionCode,
                String sha256,
                String signerFingerprint,
                VerifiedArtifact artifact) {
            this.revision = revision;
            this.apk = apk;
            this.versionCode = versionCode;
            this.sha256 = sha256;
            this.signerFingerprint = signerFingerprint;
            this.artifact = artifact;
        }
    }

    private SignedArtifactReceipt() {}

    static void write(
            File project,
            String projectId,
            String packageName,
            int revision,
            int versionCode,
            VerifiedArtifact artifact,
            String signerFingerprint)
            throws IOException {
        validateIdentity(projectId, packageName, revision, versionCode, signerFingerprint);
        File artifacts = new File(project, "artifacts");
        if (Files.isSymbolicLink(project.getParentFile().toPath())
                || Files.isSymbolicLink(project.toPath())
                || Files.isSymbolicLink(artifacts.toPath()))
            throw new IOException("Artifact directory is a link");
        File apk = artifact.file();
        String relative =
                artifacts
                        .toPath()
                        .toAbsolutePath()
                        .normalize()
                        .relativize(apk.toPath().toAbsolutePath().normalize())
                        .toString()
                        .replace(File.separatorChar, '/');
        checkedApk(artifacts, relative, projectId);
        artifact.verifyUnchanged();
        Properties properties = new Properties();
        properties.setProperty("format", "1");
        properties.setProperty("project", projectId);
        properties.setProperty("package", packageName);
        properties.setProperty("revision", Integer.toString(revision));
        properties.setProperty("version", Integer.toString(versionCode));
        properties.setProperty("path", relative);
        properties.setProperty("size", Long.toString(artifact.length()));
        properties.setProperty("sha256", artifact.sha256());
        properties.setProperty("signer", signerFingerprint);
        Path destination = new File(artifacts, NAME).toPath();
        Path temporary = new File(artifacts, NAME + ".partial").toPath();
        if (Files.isSymbolicLink(destination) || Files.isSymbolicLink(temporary))
            throw new IOException("Receipt path is a link");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                properties.store(output, null);
                output.getFD().sync();
            }
            if (Files.size(temporary) > MAX_RECEIPT_BYTES)
                throw new IOException("Receipt too large");
            artifact.verifyUnchanged();
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Record read(File project, String projectId, String packageName, int revision)
            throws IOException {
        Record record = read(project, projectId, packageName);
        if (record.revision != revision)
            throw new IOException("Receipt belongs to another project revision");
        return record;
    }

    /** The project's one receipt, for whichever revision was built last. */
    static Record read(File project, String projectId, String packageName) throws IOException {
        File artifacts = new File(project, "artifacts");
        if (Files.isSymbolicLink(project.getParentFile().toPath())
                || Files.isSymbolicLink(project.toPath())
                || Files.isSymbolicLink(artifacts.toPath()))
            throw new IOException("Artifact directory is a link");
        Path receipt = new File(artifacts, NAME).toPath();
        if (Files.isSymbolicLink(receipt)
                || !Files.isRegularFile(receipt)
                || Files.size(receipt) > MAX_RECEIPT_BYTES)
            throw new IOException("No valid artifact receipt");
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(receipt.toFile())) {
            properties.load(input);
        }
        if (properties.size() != 9
                || !"1".equals(properties.getProperty("format"))
                || !projectId.equals(properties.getProperty("project"))
                || !packageName.equals(properties.getProperty("package"))
                || !properties.getProperty("revision", "").matches("0|[1-9][0-9]{0,9}"))
            throw new IOException("Receipt belongs to another project revision");
        int revision;
        int version;
        long size;
        try {
            revision = Integer.parseInt(properties.getProperty("revision"));
            version = Integer.parseInt(properties.getProperty("version"));
            size = Long.parseLong(properties.getProperty("size"));
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid receipt numbers", invalid);
        }
        String fingerprint = properties.getProperty("signer");
        validateIdentity(projectId, packageName, revision, version, fingerprint);
        String sha = properties.getProperty("sha256");
        if (sha == null || !sha.matches("[0-9a-f]{64}") || size < 1 || size > 32 * 1024 * 1024)
            throw new IOException("Invalid receipt artifact identity");
        File apk = checkedApk(artifacts, properties.getProperty("path"), projectId);
        VerifiedArtifact artifact = VerifiedArtifact.capture(apk);
        if (artifact.length() != size || !artifact.sha256().equals(sha))
            throw new IOException("Signed APK differs from receipt");
        return new Record(revision, apk, version, sha, fingerprint, artifact);
    }

    static void publish(
            Context context,
            File project,
            String projectId,
            String packageName,
            int revision,
            int versionCode,
            VerifiedArtifact artifact)
            throws Exception {
        ProjectSigner signer = new ProjectSigner();
        ArtifactValidator.validateUnsigned(context, artifact.file(), versionCode, packageName);
        String fingerprint = signer.verifyExisting(context, artifact.file());
        write(project, projectId, packageName, revision, versionCode, artifact, fingerprint);
    }

    static void publish(
            Context context,
            ProjectIdentity identity,
            int revision,
            int versionCode,
            VerifiedArtifact artifact)
            throws Exception {
        ArtifactValidator.validateUnsigned(
                context, artifact.file(), versionCode, identity.packageName());
        String fingerprint = new ProjectSigner(identity).verifyExisting(context, artifact.file());
        write(
                identity.directory(),
                identity.id(),
                identity.packageName(),
                revision,
                versionCode,
                artifact,
                fingerprint);
    }

    static VerifiedArtifact restore(
            Context context, File project, String projectId, String packageName, int revision)
            throws Exception {
        Record record = read(project, projectId, packageName, revision);
        ArtifactValidator.validateUnsigned(context, record.apk, record.versionCode, packageName);
        String currentFingerprint = new ProjectSigner().verifyExisting(context, record.apk);
        if (!record.signerFingerprint.equals(currentFingerprint))
            throw new IOException("Project signer differs from receipt");
        record.artifact.verifyUnchanged();
        return record.artifact;
    }

    static VerifiedArtifact restore(Context context, ProjectIdentity identity, int revision)
            throws Exception {
        Record record = read(identity.directory(), identity.id(), identity.packageName(), revision);
        ArtifactValidator.validateUnsigned(
                context, record.apk, record.versionCode, identity.packageName());
        String currentFingerprint = new ProjectSigner(identity).verifyExisting(context, record.apk);
        if (!record.signerFingerprint.equals(currentFingerprint))
            throw new IOException("Project signer differs from receipt");
        record.artifact.verifyUnchanged();
        return record.artifact;
    }

    private static void validateIdentity(
            String projectId,
            String packageName,
            int revision,
            int version,
            String signerFingerprint)
            throws IOException {
        if (projectId == null
                || !("counter-alpha".equals(projectId) || projectId.matches("app[0-9a-f]{32}"))
                || !("dev.smallwright.generated."
                                + ("counter-alpha".equals(projectId) ? "counter" : projectId))
                        .equals(packageName)
                || revision < 0
                || version < 1
                || version == Integer.MAX_VALUE
                || signerFingerprint == null
                || !signerFingerprint.matches("[0-9a-f]{64}"))
            throw new IOException("Invalid receipt identity");
    }

    private static File checkedApk(File artifacts, String relative, String projectId)
            throws IOException {
        String direct = "counter-alpha".equals(projectId) ? "counter" : "app";
        if (relative == null
                || !(relative.matches(direct + "-v[1-9][0-9]*\\.apk")
                        || relative.matches("[A-Za-z0-9_-]{1,64}/app\\.apk")))
            throw new IOException("Invalid artifact path");
        Path root = artifacts.toPath().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root))
            throw new IOException("Invalid artifact directory");
        Path child = root.resolve(relative).normalize();
        if (!child.startsWith(root) || !child.getParent().startsWith(root))
            throw new IOException("Artifact escapes project");
        for (Path current = child; !current.equals(root); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IOException("Artifact path is a link");
        }
        if (!Files.isRegularFile(child) || !child.toRealPath().startsWith(root.toRealPath()))
            throw new IOException("Artifact escapes project");
        return child.toFile();
    }
}
