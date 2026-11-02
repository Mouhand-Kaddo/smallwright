package dev.smallwright.controller;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import dev.smallwright.project.ProjectIdentity;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

/** Recognition only: does not install, launch, infer consent or infer functional success. */
final class InstalledArtifact {
    private InstalledArtifact() {}

    static boolean matches(Context context, ProjectIdentity project, VerifiedArtifact artifact)
            throws IOException {
        artifact.verifyPackage(context, project.packageName());
        PackageManager packages = context.getPackageManager();
        PackageInfo archive = packages.getPackageArchiveInfo(artifact.file().getAbsolutePath(), 0);
        if (archive == null || archive.getLongVersionCode() < 1)
            throw new IOException("Verified package version unavailable");
        try {
            PackageInfo installed = packages.getPackageInfo(project.packageName(), 0);
            if (!singleApk(installed)
                    || installed.getLongVersionCode() != archive.getLongVersionCode()) return false;
            VerifiedArtifact base =
                    VerifiedArtifact.capture(new File(installed.applicationInfo.sourceDir));
            if (!base.sha256().equals(artifact.sha256())) return false;
            PackageInfo after = packages.getPackageInfo(project.packageName(), 0);
            if (!singleApk(after)
                    || after.getLongVersionCode() != installed.getLongVersionCode()
                    || !Objects.equals(
                            after.applicationInfo.sourceDir, installed.applicationInfo.sourceDir))
                return false;
            base.verifyUnchanged();
            artifact.verifyUnchanged();
            return true;
        } catch (PackageManager.NameNotFoundException absent) {
            return false;
        }
    }

    private static boolean singleApk(PackageInfo info) {
        return info.applicationInfo != null
                && info.applicationInfo.sourceDir != null
                && (info.splitNames == null || info.splitNames.length == 0)
                && (info.applicationInfo.splitSourceDirs == null
                        || info.applicationInfo.splitSourceDirs.length == 0);
    }
}
