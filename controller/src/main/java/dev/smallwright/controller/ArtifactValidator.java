package dev.smallwright.controller;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ArtifactValidator {
    /** The built APK breaks a rule; the message names which one. */
    static final class Refused extends IOException {
        Refused(String message) {
            super(message);
        }
    }

    private ArtifactValidator() {}

    static void validateUnsigned(Context context, File apk, int expectedVersion)
            throws IOException {
        validateUnsigned(context, apk, expectedVersion, ProjectSourceStore.PACKAGE_NAME);
    }

    static void validateUnsigned(
            Context context, File apk, int expectedVersion, String expectedPackage)
            throws IOException {
        if (expectedPackage == null
                || !expectedPackage.matches(
                        "dev\\.smallwright\\.generated\\.(?:counter|app[0-9a-f]{32})")) {
            throw new Refused("Invalid generated package identity");
        }
        if (!apk.isFile() || apk.length() == 0 || apk.length() > 32 * 1024 * 1024) {
            throw new Refused("Worker returned an invalid artifact size");
        }
        PackageInfo info =
                context.getPackageManager()
                        .getPackageArchiveInfo(
                                apk.getAbsolutePath(),
                                PackageManager.GET_PERMISSIONS
                                        | PackageManager.GET_ACTIVITIES
                                        | PackageManager.GET_SERVICES
                                        | PackageManager.GET_RECEIVERS
                                        | PackageManager.GET_PROVIDERS
                                        | PackageManager.GET_INSTRUMENTATION
                                        | PackageManager.MATCH_DISABLED_COMPONENTS);
        if (info == null
                || !expectedPackage.equals(info.packageName)
                || definesForeignPermission(info.permissions, expectedPackage)
                || info.sharedUserId != null
                || expectedVersion < 1
                || info.getLongVersionCode() != expectedVersion) {
            throw new Refused("Generated APK violates package or permission policy");
        }
        if (hasEntries(info.instrumentation) || !hasEntries(info.activities)) {
            throw new Refused("Generated APK contains unexpected components");
        }
        ApplicationInfo application = info.applicationInfo;
        int forbiddenFlags =
                ApplicationInfo.FLAG_ALLOW_BACKUP
                        | ApplicationInfo.FLAG_DEBUGGABLE
                        | ApplicationInfo.FLAG_TEST_ONLY
                        | ApplicationInfo.FLAG_PERSISTENT;
        if (application == null
                || !application.enabled
                || application.minSdkVersion != 31
                || application.targetSdkVersion != 36
                || (application.flags & forbiddenFlags) != 0
                || application.appComponentFactory != null
                || application.backupAgentName != null
                || !expectedPackage.equals(application.processName)
                || !expectedPackage.equals(application.taskAffinity)) {
            throw new Refused("Generated APK violates application or SDK policy");
        }
        for (ActivityInfo activity : info.activities)
            if (!AppLinkSources.allowsTaskAffinity(
                    expectedPackage, activity.name, activity.taskAffinity))
                throw new Refused("Generated APK violates activity task affinity policy");
        String launcherName;
        try {
            launcherName = LauncherManifestPolicy.validate(apk, expectedPackage);
        } catch (IOException refused) {
            throw new Refused(refused.getMessage());
        }
        boolean launcherFound = false;
        for (ComponentInfo component : components(info)) {
            if (component.processName == null
                    || !component.processName.startsWith(expectedPackage)) {
                throw new Refused("Generated APK violates component process policy");
            }
            if (!AppLinkSources.allowsLinkComponent(
                    expectedPackage, component.name, component.exported, component.processName)) {
                throw new Refused("Generated APK changes the builder's own components");
            }
            if (component instanceof ActivityInfo && launcherName.equals(component.name)) {
                ActivityInfo activity = (ActivityInfo) component;
                if (launcherFound
                        || !activity.enabled
                        || !activity.exported
                        || activity.targetActivity != null
                        || activity.permission != null) {
                    throw new Refused("Generated APK violates launcher activity policy");
                }
                launcherFound = true;
            }
        }
        if (!launcherFound) {
            throw new Refused("Generated APK violates launcher activity policy");
        }
    }

    private static List<ComponentInfo> components(PackageInfo info) {
        List<ComponentInfo> all = new ArrayList<>();
        for (ComponentInfo[] group :
                new ComponentInfo[][] {
                    info.activities, info.services, info.receivers, info.providers
                }) {
            if (group != null) all.addAll(Arrays.asList(group));
        }
        return all;
    }

    private static boolean definesForeignPermission(
            android.content.pm.PermissionInfo[] permissions, String expectedPackage) {
        if (permissions == null) return false;
        for (android.content.pm.PermissionInfo permission : permissions)
            if (permission.name == null || !permission.name.startsWith(expectedPackage + "."))
                return true;
        return false;
    }

    private static boolean hasEntries(Object[] values) {
        return values != null && values.length != 0;
    }
}
