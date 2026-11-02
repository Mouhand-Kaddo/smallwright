#!/usr/bin/env python3
"""Opt-in raw-source modifier experiments; never select worker inputs.

Source-built headers supply resolution only. SDK classes never feed extraction
or output. The narrow classpath is not a complete upstream Soong assembly.
"""

import json
import os
from pathlib import Path
import subprocess
import time
import unittest
import uuid
import zipfile
import xml.etree.ElementTree as ET

from compare_platform_abi import ClassFile
from test_network_module_source_headers import digest, tool_identity


ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "toolchains/platform-api36-source-probe"
BASELINE = "network-module-20261002/integration-f36abd5f9ce3481c809e5002e527be5c/public-six-probe.jar"
RAW = "base-r2-sparse/core/java/android/util/Log.java"
INPUTS = {
    BASELINE: "f00ef96d0b25990779b7a67ae276f427c2a0d6de974ba73ff99bdc33eec44b1f",
    RAW: "5c56ee1b4a0b8b59624a20e3bb31b7f41bf6b3171dfce6a1c21d1cab09a205c1",
    "metalava/stub-annotations/src/main/java/android/annotation/NonNull.java": "abfade84be1f981581becbedee722e036c4469f3593f9924311df7803ab5d3ff",
    "metalava/stub-annotations/src/main/java/android/annotation/Nullable.java": "7827195fab5740747a47666b44ea4c7bf066f904de88a844652670ea9f2cf460",
    "modules-utils-r4-sparse/java/android/annotation/SystemApi.java": "f8aa06a7b0493669fe35914b5aa17e278235e760804a167b0b701d8857aae0df",
    "modules-utils-r4-sparse/java/android/annotation/IntDef.java": "ee6b3ad22ca7c8313ca275d7210f0722996404c7d1d6fbb6704d0df4e36a3235",
    "modules-utils-r4-sparse/java/android/ravenwood/annotation/RavenwoodKeepWholeClass.java": "e83f0522e594091d97e1fba274ceab0d840138b6aa4245b067b83b15022a5f11",
    "modules-utils-r4-sparse/java/android/ravenwood/annotation/RavenwoodClassLoadHook.java": "122430691eb3aa8317a42c6007329357fef58aa963e0ed39ab6efaf70c2db795",
    "modules-utils-r4-sparse/java/android/ravenwood/annotation/RavenwoodRedirectionClass.java": "74fe94f2619e29cae2e2ed043f81bd1666989fc094af97f6c91879beb43d3699",
    "modules-utils-r4-sparse/java/android/ravenwood/annotation/RavenwoodRedirect.java": "60a9a610a74b814391f48ef8e3e7a6f04cda1f404137872fe326d6d1a9d1e3f2",
}
ENTRY = "android/util/Log.class"
METHOD = ("isLoggable", "(Ljava/lang/String;I)Z")
PROGRESS_BASELINE = "raw-modifiers-20261002/integration-b7bdfbe178d3405f96cf67ec90b8638c/public-log-probe.jar"
PROGRESS_RAW = "base-r2-sparse/core/java/android/widget/ProgressBar.java"
PROGRESS_INPUTS = {
    name: value for name, value in INPUTS.items() if name not in (BASELINE, RAW)
}
PROGRESS_INPUTS.update({
    PROGRESS_BASELINE: "4102e5dfc07ad5d9d5712e76db18684c347dfd5787b716551ab028f574851425",
    PROGRESS_RAW: "c818445213da8424b41f2ef6a3a9e25d5e24471552af518bb257f6fa434e8151",
    "modules-utils-r4-sparse/java/android/annotation/Px.java": "48c3f226f46290c9dcbcfc93369c5aef792c53974590190e2cbae20f7c676673",
    "modules-utils-r4-sparse/java/android/annotation/Dimension.java": "a610de9c8457a126707f36ad62b16409896986614a4ab7b3088cf361dbb9d59a",
    "modules-utils-r4-sparse/java/android/annotation/InterpolatorRes.java": "c1e9a98f2eac0af602b26928d18e235795ddce4d58056739bec3460ff323d131",
})
# Exact overrides declared by the pinned raw ProgressBar source, independently
# checked at lines 1483, 1489, 1513, 1636, 2061, 2083, 2099, 2151, 2198,
# 2219, 2244, 2317, 2337, 2352. These were inherited in the signature baseline.
PROGRESS_OVERRIDES = {
    ("drawableHotspotChanged", "(FF)V"),
    ("drawableStateChanged", "()V"),
    ("getAccessibilityClassName", "()Ljava/lang/CharSequence;"),
    ("invalidateDrawable", "(Landroid/graphics/drawable/Drawable;)V"),
    ("jumpDrawablesToCurrentState", "()V"),
    ("onAttachedToWindow", "()V"),
    ("onDetachedFromWindow", "()V"),
    ("onDraw", "(Landroid/graphics/Canvas;)V"),
    ("onMeasure", "(II)V"),
    ("onSizeChanged", "(IIII)V"),
    ("onVisibilityAggregated", "(Z)V"),
    ("postInvalidate", "()V"),
    ("setStateDescription", "(Ljava/lang/CharSequence;)V"),
    ("verifyDrawable", "(Landroid/graphics/drawable/Drawable;)Z"),
}
CLOCK_BASELINE = "raw-modifiers-20261002/integration-7482ce09aa7e4a55b2f007c950b5954b/public-owner-probe.jar"
CLOCK_RAW = "base-r2-sparse/core/java/android/os/SystemClock.java"
CLOCK_POLICY = "framework-flags-r2-public-modules/exportable-flags-config.xml"
CLOCK_HISTORY = "api35-release-20261002/ap3a-filtered/filtered-api.txt"
CLOCK_FLAG_SOURCES = {
    "framework-flags-r2-public-modules/java/android/os/Flags.java": "035edffc4a910cb086076a1f718af3a3e543fc9384b91837d16d7b272d886944",
    "framework-flags-r2-public-modules/java/android/os/FeatureFlags.java": "f3cbf3ac248145c461360cbbd72ad235b535eb9fbce66880e935d21486116c35",
    "framework-flags-r2-public-modules/java/android/os/FeatureFlagsImpl.java": "6312c0e90adb14b6340883ebb0bed3baa5ece58cf917704426a0a6d772aac7ba",
    "framework-flags-r2-public-modules/java/android/os/CustomFeatureFlags.java": "06aab3122f3a126f7755b3fe7b4f20e726a46012de350a5e5e36b015c39b44d3",
    "framework-flags-r2-public-modules/java/android/os/FakeFeatureFlagsImpl.java": "257aa008aed42f6173bc6a1c3960aebdcff20c6f08d5103ef634c7b46d65603c",
}
CLOCK_INPUTS = {name: value for name, value in INPUTS.items() if name not in (BASELINE, RAW)}
CLOCK_INPUTS.update({
    CLOCK_BASELINE: "1a3fc474a80f3585bed0864eb39f127b22c31f6633f7bb60421409773b040019",
    CLOCK_RAW: "684a2ac171f35187f9b1722a81f9454bb3f9f2363a84faf7f1fc6eee6bb515bb",
    CLOCK_POLICY: "671edeeefdea02de7552383e34dfd64059f556ec738be5eb319d4ab7b162d2e3",
    CLOCK_HISTORY: "22f960867194fbdfcbc03ca09cec36468f847524966af5f572e43f1d315950ab",
    "metalava/stub-annotations/src/main/java/android/annotation/FlaggedApi.java": "8ff1815da3e5fafd8c06e73ab92b0585f910c790903e394873fb26ea147c5d1b",
    "modules-utils-r4-sparse/java/android/ravenwood/annotation/RavenwoodKeep.java": "64abdd61bdc4deb87ad4d7b0af8b2a0add647d86da5be4445e33da6108f08198",
    "modules-utils-r4-sparse/java/android/ravenwood/annotation/RavenwoodReplace.java": "3a361b1d186ce69b97097e36bc3e7999759e0fcf0a1f083aed30ee77832dac24",
    **CLOCK_FLAG_SOURCES,
})


@unittest.skipUnless(os.environ.get("PLB_RAW_SOURCE_MODIFIERS_INTEGRATION") == "1",
                     "requires pinned local source headers, Metalava and JDK")
class RawPlatformSourceModifiersIntegrationTest(unittest.TestCase):
    def test_raw_log_source_preserves_native_public_method(self):
        self._check_raw_modifier(INPUTS, BASELINE, RAW, ENTRY, METHOD, 0x109)

    def test_raw_progress_bar_source_preserves_synchronized_public_method(self):
        self._check_raw_modifier(PROGRESS_INPUTS, PROGRESS_BASELINE, PROGRESS_RAW,
                                 "android/widget/ProgressBar.class", ("getMax", "()I"), 0x21)

    def test_raw_system_clock_release_filter_preserves_flagged_native_method(self):
        self._check_raw_modifier(CLOCK_INPUTS, CLOCK_BASELINE, CLOCK_RAW,
                                 "android/os/SystemClock.class", ("uptimeNanos", "()J"), 0x109,
                                 flag_sources=CLOCK_FLAG_SOURCES, release=(CLOCK_POLICY, CLOCK_HISTORY))

    def _check_raw_modifier(self, inputs, baseline_name, raw_name, entry, method, flags,
                            flag_sources=(), release=()):
        for name, expected in inputs.items():
            self.assertEqual(expected, digest(PROBE / name), name)
        jdk = ROOT / "toolchains/jdk-21.0.12.1+1"
        metalava = PROBE / "out-r4/metalava/metalava/build/install/metalava/bin/metalava"
        tools = tool_identity(jdk, metalava)
        output = PROBE / "raw-modifiers-20261002" / ("integration-" + uuid.uuid4().hex)
        output.mkdir(parents=True)
        source_root = output / "source"
        copied_source = source_root / entry.replace(".class", ".java")
        copied_source.parent.mkdir(parents=True)
        copied_source.write_bytes((PROBE / raw_name).read_bytes())
        self.assertEqual(inputs[raw_name], digest(copied_source))
        source_copies = [{"original": str(PROBE / raw_name), "path": str(copied_source),
                          "sha256": digest(copied_source)}]
        copied_flags = []
        for name in flag_sources:
            target = source_root / name.split("/java/", 1)[1]
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((PROBE / name).read_bytes())
            self.assertEqual(inputs[name], digest(target))
            copied_flags.append(target)
            source_copies.append({"original": str(PROBE / name), "path": str(target),
                                  "sha256": digest(target)})
        self.assertEqual(sorted([copied_source, *copied_flags]), sorted(source_root.rglob("*.java")))
        child_environment = {"JAVA_HOME": str(jdk), "JAVA_OPTS": "-Xms128m -Xmx1024m",
                             "PATH": str(jdk / "bin") + ":/usr/bin:/bin", "LANG": "C.UTF-8"}
        receipt = {"status": "host_mechanism_experiment_not_release_input",
                   "inputs": inputs, "tools": tools, "child_environment": child_environment,
                   "source_copy": {"original": str(PROBE / raw_name),
                                   "path": str(copied_source), "sha256": digest(copied_source),
                                   "source_root": str(source_root)},
                   "source_copies": source_copies,
                   "stages": [], "source_pins": {
                       "frameworks/base_r2": "99b01a65cc4c104933788b3143285ab6bae65827",
                       "modules-utils_r4": "e945bbce0ccb83e1111b6031a3bba89e6b5cac6f",
                       "metalava_r4": "9809e0d70a5b807be3d7070d51f9c4714f2fdcb4"}}

        def save():
            (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")

        save()  # Pin sources and actual installed runtime identity before tool launch.
        release_options = []
        if release:
            policy, history = release
            selected = [flag.attrib for flag in ET.parse(PROBE / policy).getroot().iter()
                        if flag.tag.rsplit("}", 1)[-1] == "api-flag" and
                        flag.get("package") == "android.os" and
                        flag.get("name") == "adpf_gpu_report_actual_work_duration"]
            self.assertEqual([{"package": "android.os", "name": "adpf_gpu_report_actual_work_duration",
                               "mutability": "immutable", "status": "enabled"}], selected)
            receipt["selected_release_flag"] = selected[0]
            release_options = ["--config-file", PROBE / policy, "--check-compatibility", "disabled",
                               "--check-compatibility:api:released", PROBE / history]
            save()
        baseline = PROBE / baseline_name
        with zipfile.ZipFile(baseline) as original:
            original_class = ClassFile(original.read(entry), entry)
        with self.assertRaises(AssertionError) as red:
            self.assertEqual(flags, original_class.methods[method][0])
        receipt["red"] = {"entry": entry, "method": method, "expected_flags": hex(flags),
                          "actual_flags": hex(original_class.methods[method][0]),
                          "assertion_failure": str(red.exception)}
        (output / "red-modifier-assertion.log").write_text(str(red.exception) + "\n")
        save()

        def run(name, command):
            started = time.monotonic()
            command = [str(value) for value in command]
            result = subprocess.run(command, cwd=ROOT, env=child_environment,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
            (output / (name + ".log")).write_bytes(result.stdout)
            receipt["stages"].append({"name": name, "command": command,
                                      "exit_code": result.returncode,
                                      "seconds": time.monotonic() - started})
            save()
            self.assertEqual(0, result.returncode,
                             name + ": " + result.stdout.decode(errors="replace")[-3000:])

        def compile_sources(name, classpath, sources):
            directory = output / name
            directory.mkdir()
            run(name, [jdk / "bin/javac", "-source", "8", "-target", "8", "-proc:none",
                       "-bootclasspath", classpath, "-classpath", "", "-d", directory,
                       *sources])
            return directory

        helpers = [PROBE / name for name in inputs
                   if name not in (baseline_name, raw_name, *flag_sources, *release)]
        annotations = compile_sources("annotation-classes", str(baseline), helpers)
        classpath = str(annotations) + ":" + str(baseline)
        stubs = output / "stubs"
        run("raw-source-metalava", [metalava, "--quiet", "main", "--include-annotations",
                                 "--exclude-documentation-from-stubs", "--stub-packages",
                                 entry.rsplit("/", 1)[0].replace("/", "."),
                                 "--source-path", source_root, "--classpath", classpath,
                                 "--stubs", stubs, *release_options, copied_source, *copied_flags])
        sources = sorted(stubs.rglob("*.java"))
        self.assertEqual([entry.replace(".class", ".java")],
                         [p.relative_to(stubs).as_posix() for p in sources])
        compiled = compile_sources("owner-classes", classpath, sources)
        replacement = (compiled / entry).read_bytes()
        actual = ClassFile(replacement, entry)
        self.assertEqual(flags, actual.methods[method][0])
        receipt["green"] = {"expected_flags": hex(flags), "actual_flags": hex(actual.methods[method][0])}
        save()
        additions = PROGRESS_OVERRIDES if entry == "android/widget/ProgressBar.class" else set()
        self.assertEqual(set(original_class.methods) | additions, set(actual.methods))
        self.assertFalse(set(original_class.methods) & additions)
        self.assertEqual(original_class.fields, actual.fields)
        self.assertEqual(original_class.flags, actual.flags)
        self.assertEqual(original_class.superclass, actual.superclass)
        self.assertEqual(original_class.interfaces, actual.interfaces)
        self.assertEqual([entry], sorted(p.relative_to(compiled).as_posix()
                                        for p in compiled.rglob("*.class")))
        candidate = output / "public-owner-probe.jar"
        with zipfile.ZipFile(baseline) as original, zipfile.ZipFile(candidate, "x") as revised:
            for info in original.infolist():
                revised.writestr(info, replacement if info.filename == entry
                                 else original.read(info.filename))
        with zipfile.ZipFile(baseline) as original, zipfile.ZipFile(candidate) as revised:
            self.assertEqual(original.namelist(), revised.namelist())
            receipt["changed_entries"] = [name for name in original.namelist()
                                          if original.read(name) != revised.read(name)]
        self.assertEqual([entry], receipt["changed_entries"])
        receipt["materialized_overrides"] = sorted(additions)
        receipt["output"] = {"path": str(candidate), "sha256": digest(candidate)}
        receipt["result"] = "exact modifier RED then GREEN; exact source-derived method inventory"
        save()


if __name__ == "__main__":
    unittest.main()
