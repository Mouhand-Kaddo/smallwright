#!/usr/bin/env python3
"""Build unsigned manifest-only APKs solely for the archive policy device test.

These exercise manifest parsing. They are never installed or presented as generated apps.
"""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parent.parent
sdk = root / "toolchains/android-sdk"
output = root / "controller/build/generated/artifact-policy-assets"
output.mkdir(parents=True, exist_ok=True)
manifest = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="dev.smallwright.generated.counter" android:versionCode="1">
    <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="36"/>
    <application android:allowBackup="false" android:label="Smallwright Counter">
        <activity android:name=".MainActivity" android:exported="true"
            android:theme="@android:style/Theme.Material.Light.NoActionBar">
            <intent-filter><action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/></intent-filter>
        </activity>
        {components}
    </application>
</manifest>
'''
fixtures = {name: manifest.format(components=components) for name, components in {
    "valid": "",
    "service": '<service android:name=".UnexpectedService" android:exported="true"/>',
    "disabled-service": '<service android:name=".DisabledService" android:enabled="false"/>',
    "receiver": '<receiver android:name=".UnexpectedReceiver" android:exported="true"/>',
    "provider": '<provider android:name=".UnexpectedProvider" android:authorities="dev.smallwright.test.provider"/>',
    "extra-activity": '<activity android:name=".OtherActivity" android:exported="true"/>',
    "own-affinity-activity": '<activity android:name=".OtherActivity" android:taskAffinity="dev.smallwright.generated.counter"/>',
    "foreign-affinity-activity": '<activity android:name=".OtherActivity" android:taskAffinity="com.bank.app"/>',
    "empty-affinity-activity": '<activity android:name=".OtherActivity" android:taskAffinity=""/>',
    "activity-alias": '<activity-alias android:name=".Alias" android:targetActivity=".MainActivity" android:exported="true"/>',
}.items()}
valid = fixtures["valid"]
launcher_filter = '''<intent-filter><action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/></intent-filter>'''
for name, before, after in [
    ("missing-launcher-filter", launcher_filter, ""),
    ("wrong-launcher-action", 'android.intent.action.MAIN', 'android.intent.action.VIEW'),
    ("permission", "<uses-sdk", '<uses-permission android:name="android.permission.INTERNET"/><uses-sdk'),
    ("two-activities-internet", "<uses-sdk", '<uses-permission android:name="android.permission.INTERNET"/><uses-sdk'),
    ("second-launcher", "</application>", '<activity android:name=".OtherActivity" android:exported="true">' + launcher_filter + '</activity></application>'),
    ("launcher-data", launcher_filter, '<intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/><data android:scheme="https"/></intent-filter>'),
    ("launcher-alias", "</application>", '<activity-alias android:name=".LauncherAlias" android:targetActivity=".MainActivity" android:exported="true">' + launcher_filter + '</activity-alias></application>'),
    ("custom-permission", "<uses-sdk", '<permission android:name="dev.smallwright.generated.counter.CUSTOM"/><uses-sdk'),
    ("instrumentation", "<uses-sdk", '<instrumentation android:name=".UnexpectedInstrumentation" android:targetPackage="dev.smallwright.controller"/><uses-sdk'),
    ("backup", 'android:allowBackup="false"', 'android:allowBackup="true"'),
    ("foreign-affinity-application", "<application ", '<application android:taskAffinity="com.bank.app" '),
    ("debuggable", "<application ", '<application android:debuggable="true" '),
    ("test-only", "<application ", '<application android:testOnly="true" '),
    ("application-class", "<application ", '<application android:name=".UnexpectedApplication" '),
    ("app-component-factory", "<application ", '<application android:appComponentFactory=".UnexpectedFactory" '),
    ("application-process", "<application ", '<application android:process=":other" '),
    ("activity-process", "<activity ", '<activity android:process=":other" '),
    ("activity-permission", "<activity ", '<activity android:permission="android.permission.INTERNET" '),
    ("activity-not-exported", 'android:exported="true"', 'android:exported="false"'),
    ("activity-disabled", "<activity ", '<activity android:enabled="false" '),
    ("application-disabled", "<application ", '<application android:enabled="false" '),
    ("activity-direct-boot", "<activity ", '<activity android:directBootAware="true" '),
    ("wrong-package", 'package="dev.smallwright.generated.counter"', 'package="dev.smallwright.generated.other"'),
    ("shared-uid", 'android:versionCode="1"', 'android:versionCode="1" android:sharedUserId="dev.smallwright.shared"'),
    ("wrong-version", 'android:versionCode="1"', 'android:versionCode="2"'),
    ("min-sdk", 'android:minSdkVersion="31"', 'android:minSdkVersion="30"'),
    ("target-sdk", 'android:targetSdkVersion="36"', 'android:targetSdkVersion="35"'),
]:
    fixtures[name] = valid.replace(before, after)
fixtures["two-activities-internet"] = fixtures["two-activities-internet"].replace(
    "</application>", '<activity android:name=".DetailActivity"/></application>')
accepted = {
    "valid", "service", "disabled-service", "receiver", "provider", "extra-activity",
    "own-affinity-activity",
    "activity-alias", "permission", "two-activities-internet", "activity-process",
    "activity-direct-boot", "application-class",
}
for name, xml in fixtures.items():
    sources = root / "toolchains/artifact-policy-fixtures"
    sources.mkdir(parents=True, exist_ok=True)
    source = sources / f"{name}.xml"
    source.write_text(xml)
    subprocess.run([
        str(sdk / "build-tools/36.0.0/aapt2"), "link", "--manifest", str(source),
        "-I", str(sdk / "platforms/android-36/android.jar"),
        "-o", str(output / f"{name}.apk"),
    ], check=True)
(output / "fixtures.tsv").write_text("".join(
    f"{'accept' if name in accepted else 'reject'}\t{name}.apk\n" for name in fixtures
))
print(output)
