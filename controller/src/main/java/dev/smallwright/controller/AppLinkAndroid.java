package dev.smallwright.controller;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** The Android side of the app link (ADR 0008): package and signer facts, and the look itself. */
final class AppLinkAndroid implements AppLink.Phone {
    private final Context context;
    private final ProjectIdentity project;

    AppLinkAndroid(Context context, ProjectIdentity project) {
        this.context = context.getApplicationContext();
        this.project = project;
    }

    /** SHA-256 of the controller's own single signing certificate. */
    static byte[] controllerCertificateSha256(Context context) throws IOException {
        byte[][] signers = installedSigners(context, context.getPackageName());
        if (signers == null || signers.length != 1)
            throw new IOException("The builder's signing certificate is unknown");
        try {
            return MessageDigest.getInstance("SHA-256").digest(signers[0]);
        } catch (NoSuchAlgorithmException missing) {
            throw new IOException(missing);
        }
    }

    /** The installed package's current signers, or null when it is not installed. */
    static byte[][] installedSigners(Context context, String packageName) {
        try {
            PackageInfo info =
                    context.getPackageManager()
                            .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo signing = info.signingInfo;
            if (signing == null) return null;
            Signature[] signers = signing.getApkContentsSigners();
            if (signers == null) return null;
            byte[][] encoded = new byte[signers.length][];
            for (int index = 0; index < signers.length; index++)
                encoded[index] = signers[index].toByteArray();
            return encoded;
        } catch (PackageManager.NameNotFoundException absent) {
            return null;
        }
    }

    static long installedVersion(Context context, String packageName) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException absent) {
            return 0;
        }
    }

    /**
     * The project whose installed app Android named as the caller, when that app is signed by the
     * project's current certificate alone; null otherwise.
     */
    static ProjectIdentity sender(Context context, String callingPackage) throws Exception {
        if (callingPackage == null) return null;
        File files = context.getFilesDir();
        for (ProjectIdentity project :
                new ProjectRegistry(new File(files, "projects"), files).list())
            if (project.packageName().equals(callingPackage))
                return signedByProject(context, project) ? project : null;
        return null;
    }

    private static boolean signedByProject(Context context, ProjectIdentity project)
            throws Exception {
        return AppLinkRules.acceptsSender(
                project.packageName(),
                project.packageName(),
                installedSigners(context, project.packageName()),
                new ProjectSigner(project).currentCertificate(context));
    }

    @Override
    public VerifiedArtifact build(int revision) {
        try {
            return SignedArtifactReceipt.restore(context, project, revision);
        } catch (Exception notBuilt) {
            return null;
        }
    }

    @Override
    public boolean installed(VerifiedArtifact artifact) {
        try {
            return InstalledArtifact.matches(context, project, artifact)
                    && signedByProject(context, project);
        } catch (Exception unknown) {
            return false;
        }
    }

    @Override
    public List<String> activities() {
        try {
            ActivityInfo[] declared =
                    context.getPackageManager()
                            .getPackageInfo(project.packageName(), PackageManager.GET_ACTIVITIES)
                            .activities;
            List<String> names = new ArrayList<>();
            if (declared != null) for (ActivityInfo activity : declared) names.add(activity.name);
            return names;
        } catch (PackageManager.NameNotFoundException absent) {
            return List.of();
        }
    }

    @Override
    public String launcher() {
        Intent launch =
                context.getPackageManager().getLaunchIntentForPackage(project.packageName());
        return launch == null || launch.getComponent() == null
                ? null
                : launch.getComponent().getClassName();
    }

    @Override
    public boolean builderOnScreen() {
        ActivityManager.RunningAppProcessInfo state = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(state);
        return state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    }

    @Override
    public AppLink.Reply look(String activity, BooleanSupplier active) throws Exception {
        return LookActivity.look(context, project.packageName(), activity, active);
    }

    @Override
    public byte[] jpeg(byte[] png) throws IOException {
        return PictureShrink.jpeg(png);
    }
}
