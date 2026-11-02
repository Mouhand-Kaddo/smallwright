#!/usr/bin/env python3
"""Opt-in source-only regression for module-classpath hidden override extraction.

This preserves diagnostic archives/logs under ignored toolchains. It is not an
SDK compatibility or redistribution pass and never changes worker inputs.
Pinned annotation support includes existing upstream r4 module-utils sources;
this focused extraction is not a canonical complete r2 Soong build.
"""

import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import unittest
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "toolchains/platform-api36-source-probe"
INPUTS = {
    "api35-release-20261002/api36-filtered-with-source35-history/headers.jar": "a705b40a43b7067735b33ba97d83dc676f54810bb43bb692534c60df0ad3e2a8",
    "network-raw-source-overlays/core/java/android/net/NetworkSpecifier.java": "047752f858d3ee879b69ff953fd8f6f55d484ec01c502a25b965f98e2bff125f",
    "network-raw-source-overlays/framework-t/src/android/net/EthernetNetworkSpecifier.java": "2ae78d16bbdcc9b5f6224b34de350acd7df9259ff806f2f580575f3678e9e8ec",
    "network-raw-source-overlays/framework/src/android/net/L2capNetworkSpecifier.java": "4a6071181a0e57cd094db097cf496ad1dabb4c4290a1c5f89ce339a76eae9ab1",
    "network-raw-source-overlays/framework/java/android/net/wifi/WifiNetworkSpecifier.java": "c764ebdbdb13da5f9941fcb7aee23ea3aed6007d8d2f1c32597ebd3c4984395e",
    "network-raw-source-overlays/framework/java/android/net/wifi/aware/WifiAwareNetworkSpecifier.java": "2138a48ce8c82f00b5e473a1d71f3fb1ce0156f8c04188be359c4a34246187d0",
    "framework-flags-r2-public-modules/exportable-flags-config.xml": "671edeeefdea02de7552383e34dfd64059f556ec738be5eb319d4ab7b162d2e3",
    "api35-release-20261002/ap3a-filtered/filtered-api.txt": "22f960867194fbdfcbc03ca09cec36468f847524966af5f572e43f1d315950ab",
    "metalava/stub-annotations/src/main/java/android/annotation/NonNull.java": "abfade84be1f981581becbedee722e036c4469f3593f9924311df7803ab5d3ff",
    "metalava/stub-annotations/src/main/java/android/annotation/Nullable.java": "7827195fab5740747a47666b44ea4c7bf066f904de88a844652670ea9f2cf460",
    "metalava/stub-annotations/src/main/java/android/annotation/FlaggedApi.java": "8ff1815da3e5fafd8c06e73ab92b0585f910c790903e394873fb26ea147c5d1b",
    "modules-utils-r4-sparse/java/android/annotation/SystemApi.java": "f8aa06a7b0493669fe35914b5aa17e278235e760804a167b0b701d8857aae0df",
    "modules-utils-r4-sparse/java/android/annotation/IntDef.java": "ee6b3ad22ca7c8313ca275d7210f0722996404c7d1d6fbb6704d0df4e36a3235",
    "modules-utils-r4-sparse/java/android/annotation/IntRange.java": "0b98c3636d72e8a3e5b99f042d52023a9d219bb6a23fd3b34992195b2eb53710",
    "framework-flags-r2-public-modules/java/com/android/net/flags/CustomFeatureFlags.java": "c483fab863a92595bc5ccc9e0429ab589db160e5711b7a603cc98bd0b5a5334e",
    "framework-flags-r2-public-modules/java/com/android/net/flags/FakeFeatureFlagsImpl.java": "6393eb35ee82d011bed0319b6b94d9885ceb3294ae26dc1c88e650784fa11a89",
    "framework-flags-r2-public-modules/java/com/android/net/flags/FeatureFlags.java": "baa87d88cf3622759dbda9c3f1bb95bcb51157f61354039763391049e87fbcea",
    "framework-flags-r2-public-modules/java/com/android/net/flags/FeatureFlagsImpl.java": "86b182d212d43ab4e5f0c7dc41c0a18232c2065d15dcd36b504b8c48a13be52c",
    "framework-flags-r2-public-modules/java/com/android/net/flags/Flags.java": "412f5df68331f2559262646f1b912b6c04c1fb0d9a467c48ef9039bdaa2a148d"
}


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def tool_identity(jdk, metalava):
    """Fingerprint installed host tools without executing them or reading environment."""
    installation = metalava.parent.parent
    jars = sorted((installation / "lib").glob("*.jar"))
    if not jars:
        raise AssertionError("Metalava runtime JARs are missing")
    required_jdk = [jdk / name for name in (
        "bin/java", "bin/javac", "release", "lib/modules", "lib/jspawnhelper",
        "lib/libjava.so", "lib/libjli.so", "lib/server/libjvm.so")]
    runtime_libraries = sorted((jdk / "lib").rglob("*.so"))
    jdk_files = sorted(set(required_jdk + runtime_libraries))

    def entries(root, files):
        result = []
        for path in files:
            if not path.is_file():
                raise AssertionError("Host tool file is missing: " + str(path))
            result.append({"path": path.relative_to(root).as_posix(),
                           "sha256": digest(path)})
        return result

    return {
        "metalava": {"installation": str(installation),
                     "files": entries(installation, [metalava, *jars])},
        "jdk": {"installation": str(jdk), "files": entries(jdk, jdk_files)},
    }


@unittest.skipUnless(os.environ.get("PLB_NETWORK_MODULE_INTEGRATION") == "1",
                     "requires pinned local source headers, Metalava and JDK")
class NetworkModuleHeadersIntegrationTest(unittest.TestCase):
    def test_hidden_overrides_follow_source_module_classpath(self):
        for name, expected in INPUTS.items():
            self.assertEqual(expected, digest(PROBE / name), name)
        jdk = ROOT / "toolchains/jdk-21.0.12.1+1"
        metalava = PROBE / "out-r4/metalava/metalava/build/install/metalava/bin/metalava"
        self.assertTrue((jdk / "bin/javac").is_file())
        self.assertTrue(metalava.is_file())
        tools = tool_identity(jdk, metalava)
        output = PROBE / "network-module-20261002" / ("integration-" + uuid.uuid4().hex)
        output.mkdir(parents=True)
        receipt = {"status": "host_mechanism_experiment_not_release_input",
                   "inputs": INPUTS, "tools": tools, "stages": []}
        child_environment = {"JAVA_HOME": str(jdk), "JAVA_OPTS": "-Xms128m -Xmx1024m",
                             "PATH": str(jdk / "bin") + ":/usr/bin:/bin",
                             "LANG": "C.UTF-8"}
        receipt["child_environment"] = child_environment

        def save():
            (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")

        save()  # Preserve source/tool identity before the first tool launch.

        def run(name, command, expected=0):
            started = time.monotonic()
            command = [str(value) for value in command]
            result = subprocess.run(
                command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                env=child_environment, timeout=60)
            (output / (name + ".log")).write_bytes(result.stdout)
            receipt["stages"].append({"name": name, "command": command,
                                      "exit_code": result.returncode,
                                      "seconds": time.monotonic() - started})
            save()
            self.assertEqual(expected, result.returncode,
                             name + ": " + result.stdout.decode(errors="replace")[-3000:])
            return result.stdout

        def compile_sources(name, classpath, sources, expected=0):
            directory = output / name
            directory.mkdir()
            run(name, [jdk / "bin/javac", "-source", "8", "-target", "8", "-proc:none",
                       "-bootclasspath", classpath, "-classpath", "", "-d", directory,
                       *sources], expected)
            return directory

        baseline = PROBE / "api35-release-20261002/api36-filtered-with-source35-history/headers.jar"
        raw = PROBE / "network-raw-source-overlays"
        callsite = output / "SixCalls.java"
        callsite.write_text("""import android.net.*;
import android.net.wifi.*;
import android.net.wifi.aware.*;
public class SixCalls {
 static boolean ethernet(EthernetNetworkSpecifier v, NetworkSpecifier o) { return v.canBeSatisfiedBy(o); }
 static boolean l2cap(L2capNetworkSpecifier v, NetworkSpecifier o) { return v.canBeSatisfiedBy(o); }
 static boolean wifi(WifiNetworkSpecifier v, NetworkSpecifier o) { return v.canBeSatisfiedBy(o); }
 static boolean aware(WifiAwareNetworkSpecifier v, NetworkSpecifier o) { return v.canBeSatisfiedBy(o); }
 static NetworkSpecifier redactL2cap(L2capNetworkSpecifier v) { return v.redact(); }
 static NetworkSpecifier redactWifi(WifiNetworkSpecifier v) { return v.redact(); }
}
""")
        compile_sources("red-six", str(baseline), [callsite], 1)
        self.assertEqual(6, (output / "red-six.log").read_bytes().count(b"cannot find symbol"))
        annotation_sources = [PROBE / name for name in INPUTS
                              if "/java/android/annotation/" in name]
        annotations = compile_sources("source-annotation-classes", str(baseline),
                                      annotation_sources)
        annotation_classpath = str(annotations) + ":" + str(baseline)
        common = [metalava, "--quiet", "main", "--include-annotations",
                  "--exclude-documentation-from-stubs"]
        support_stubs = output / "support-stubs"
        run("support-metalava", [*common, "--show-annotation", "android.annotation.SystemApi",
                                "--show-unannotated", "--classpath", annotation_classpath,
                                "--stubs", support_stubs,
                                raw / "core/java/android/net/NetworkSpecifier.java"])
        support_classes = compile_sources("support-classes", annotation_classpath,
                                           list(support_stubs.rglob("*.java")))
        support = output / "support.jar"
        with zipfile.ZipFile(support, "x") as archive:
            for source in support_classes.rglob("*.class"):
                archive.write(source, source.relative_to(support_classes).as_posix())
        classpath = str(support) + ":" + annotation_classpath
        flags = [PROBE / name for name in INPUTS
                 if name.startswith("framework-flags-r2-public-modules/java/")]
        policy = PROBE / "framework-flags-r2-public-modules/exportable-flags-config.xml"
        history = PROBE / "api35-release-20261002/ap3a-filtered/filtered-api.txt"
        # Actual module extraction classpaths are scope-specific. This proves the
        # base module_current seam only, retaining bounded source graph limits.
        # Wi-Fi receives no module flag filter: these two selected raw files have
        # no FlaggedApi declarations. This is not full Wi-Fi release selection.
        modules = [
            ("connectivity-t", "android.net", [],
             [raw / "framework-t/src/android/net/EthernetNetworkSpecifier.java"]),
            ("connectivity", "android.net",
             ["--config-file", policy, "--check-compatibility", "disabled",
              "--check-compatibility:api:released", history],
             [raw / "framework/src/android/net/L2capNetworkSpecifier.java", *flags]),
            ("wifi", "android.net.wifi:android.net.wifi.aware", [],
             [raw / "framework/java/android/net/wifi/WifiNetworkSpecifier.java",
              raw / "framework/java/android/net/wifi/aware/WifiAwareNetworkSpecifier.java"]),
        ]
        compiled = []
        for name, packages, options, sources in modules:
            stubs = output / (name + "-stubs")
            run(name + "-metalava", [*common, "--stub-packages", packages,
                                     "--classpath", classpath, "--stubs", stubs,
                                     *options, *sources])
            stub_sources = sorted(stubs.rglob("*.java"))
            self.assertTrue(stub_sources, name)
            compiled.append(compile_sources(name + "-classes", classpath, stub_sources))
        replacement = {source.relative_to(directory).as_posix(): source.read_bytes()
                       for directory in compiled for source in directory.rglob("*.class")}
        candidate = output / "public-six-probe.jar"
        with zipfile.ZipFile(baseline) as original, zipfile.ZipFile(candidate, "x") as revised:
            self.assertTrue(set(replacement).issubset(set(original.namelist())))
            for info in original.infolist():
                revised.writestr(info, replacement.pop(info.filename, original.read(info.filename)))
        compile_sources("green-six", str(candidate), [callsite])
        base_signature = run("public-base-javap", [jdk / "bin/javap", "-classpath", candidate,
                                                   "android.net.NetworkSpecifier"])
        self.assertNotIn(b"canBeSatisfiedBy", base_signature)
        self.assertNotIn(b"redact", base_signature)
        with zipfile.ZipFile(baseline) as original, zipfile.ZipFile(candidate) as revised:
            self.assertEqual(original.namelist(), revised.namelist())
            self.assertEqual(original.read("android/net/NetworkSpecifier.class"),
                             revised.read("android/net/NetworkSpecifier.class"))
            receipt["changed_entries"] = [name for name in original.namelist()
                                          if original.read(name) != revised.read(name)]
        receipt["output"] = {"path": str(candidate), "sha256": digest(candidate)}
        receipt["result"] = "all six calls RED then GREEN; public base unchanged"
        save()


if __name__ == "__main__":
    unittest.main()
