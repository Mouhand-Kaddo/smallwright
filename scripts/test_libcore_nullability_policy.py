#!/usr/bin/env python3
"""Opt-in libcore warning-nullability experiments, never worker input.

The current-text case consumes the actual r2 source API contribution and retains
future flagged declarations. The JAR case selects its exact package inventory
from the proven source-only, release-filtered baseline. Neither promotes worker
inputs or reconstructs raw modifiers. SDK is comparison-only after generation.
"""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import unittest
import uuid
import zipfile

from compare_platform_abi import ClassFile
from test_network_module_source_headers import digest, tool_identity

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "toolchains/platform-api36-source-probe"
BASELINE = "raw-modifiers-20261002/integration-aa30c0992e2a4be49fe85d890420b4fe/public-owner-probe.jar"
API = "libcore-r2-sparse/api/current.txt"
POLICY = "libcore-r2-sparse/JavaLibrary.bp"
WARNING_PACKAGES = "+*:-android.*:+android.icu.*:-dalvik.*"
INPUTS = {
    BASELINE: "17f3b655406c4138627386af95ffeaa7c591f2a31d5c97d57dd17e804c5ccf37",
    API: "5fdbc5046294287f39d030a948c28fb2da2619546da893224c2333f32bae7c39",
    POLICY: "947c58991fca96976da15ad28b579a44b17fc96c178ce2e57360e1aec72473d1",
    "metalava/stub-annotations/src/main/java/android/annotation/NonNull.java": "abfade84be1f981581becbedee722e036c4469f3593f9924311df7803ab5d3ff",
    "metalava/stub-annotations/src/main/java/android/annotation/Nullable.java": "7827195fab5740747a47666b44ea4c7bf066f904de88a844652670ea9f2cf460",
    "metalava/stub-annotations/src/main/java/android/annotation/FlaggedApi.java": "8ff1815da3e5fafd8c06e73ab92b0585f910c790903e394873fb26ea147c5d1b",
    "metalava/stub-annotations/src/main/java/androidx/annotation/RecentlyNonNull.java": "793c80c9a6d9fcf42f8dd65071596054f1b354c248f43e9833ff783e943864ec",
    "metalava/stub-annotations/src/main/java/androidx/annotation/RecentlyNullable.java": "a5abe1d570db7ebc2e99312a091ef49d7337137cd98789b297238c8aac9f4fe2",
}
NONNULL = "Landroidx/annotation/RecentlyNonNull;"
NULLABLE = "Landroidx/annotation/RecentlyNullable;"


def digest_bytes(data):
    return hashlib.sha256(data).hexdigest()


def inventory_deltas(before, after):
    owners = {}
    for name in sorted(set(before) & set(after)):
        left, right = ClassFile(before[name], name), ClassFile(after[name], name)
        changes = {}
        for kind in ("fields", "methods"):
            old, new = getattr(left, kind), getattr(right, kind)
            added, removed = sorted(set(new) - set(old)), sorted(set(old) - set(new))
            modified = {k[0] + ":" + k[1]: {"before": old[k], "after": new[k]}
                        for k in sorted(set(old) & set(new)) if old[k] != new[k]}
            if added or removed or modified:
                changes[kind] = {"added": added, "removed": removed, "modified": modified}
        if (left.flags, left.superclass, left.interfaces, left.attributes) != (
                right.flags, right.superclass, right.interfaces, right.attributes):
            changes["class_metadata"] = {
                "before": [left.flags, left.superclass, left.interfaces, left.attributes],
                "after": [right.flags, right.superclass, right.interfaces, right.attributes]}
        if changes:
            owners[name] = changes
    return {"added_classes": sorted(set(after) - set(before)),
            "removed_classes": sorted(set(before) - set(after)), "owners": owners}


@unittest.skipUnless(os.environ.get("PLB_LIBCORE_NULLABILITY_INTEGRATION") == "1",
                     "requires pinned local source API, Metalava and JDK")
class LibcoreNullabilityPolicyIntegrationTest(unittest.TestCase):
    def test_actual_libcore_package_policy_emits_warning_nullability(self):
        self._run_policy_experiment("source-current")

    def test_released_libcore_jar_policy_preserves_inventory(self):
        self._run_policy_experiment("released-jar")

    def _run_policy_experiment(self, model):
        for name, expected in INPUTS.items():
            self.assertEqual(expected, digest(PROBE / name), name)
        self.assertIn('--force-convert-to-warning-nullability-annotations ' + WARNING_PACKAGES,
                      (PROBE / POLICY).read_text())
        jdk = ROOT / "toolchains/jdk-21.0.12.1+1"
        metalava = PROBE / "out-r4/metalava/metalava/build/install/metalava/bin/metalava"
        tools = tool_identity(jdk, metalava)
        output = PROBE / "libcore-nullability-20261002" / ("integration-" + uuid.uuid4().hex)
        output.mkdir(parents=True)
        environment = {"JAVA_HOME": str(jdk), "JAVA_OPTS": "-Xms128m -Xmx1024m",
                       "PATH": str(jdk / "bin") + ":/usr/bin:/bin", "LANG": "C.UTF-8"}
        packages = sorted(set(re.findall(r"^package ([\w.]+) \{", (PROBE / API).read_text(), re.M)))
        self.assertEqual(79, len(packages))
        receipt = {"status": "libcore_policy_experiment_not_release_input", "model": model,
                   "module": "art.module.public.api",
                   "source_commit": "fff4fcc0cf7f080cf2511cbb57561482b13b218f",
                   "inputs": INPUTS, "tools": tools, "child_environment": environment,
                   "module_packages": packages, "warning_package_policy": WARNING_PACKAGES,
                   "release_selection": ("source-current text model; future flagged declarations may remain"
                                         if model == "source-current" else
                                         "all baseline classes in exactly source contribution packages"),
                   "stages": []}

        def save():
            (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")

        save()  # Source and actual installed tool identity precede every tool launch.

        def run(name, command):
            command = [str(v) for v in command]
            started = time.monotonic()
            result = subprocess.run(command, cwd=ROOT, env=environment,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
            (output / (name + ".log")).write_bytes(result.stdout)
            receipt["stages"].append({"name": name, "command": command,
                                      "exit_code": result.returncode,
                                      "seconds": time.monotonic() - started})
            save()
            self.assertEqual(0, result.returncode,
                             name + ": " + result.stdout.decode(errors="replace")[-4000:])

        def compile_sources(name, classpath, sources):
            directory = output / name
            directory.mkdir()
            run(name, [jdk / "bin/javac", "-J-Xmx1024m", "-source", "8", "-target", "8",
                       "-proc:none", "-bootclasspath", classpath, "-classpath", "",
                       "-d", directory, *sources])
            return directory

        baseline = PROBE / BASELINE
        with zipfile.ZipFile(baseline) as archive:
            baseline_classes = {n: archive.read(n) for n in archive.namelist() if n.endswith(".class")}
        selected = {n: data for n, data in baseline_classes.items()
                    if n.rsplit("/", 1)[0].replace("/", ".") in packages}
        if model == "released-jar":
            selection = output / "package-selection.json"
            selection.write_text(json.dumps({"contribution": API, "sha256": INPUTS[API],
                                             "packages": packages,
                                             "selected_classes": {n: digest_bytes(data)
                                                                  for n, data in sorted(selected.items())}},
                                            indent=2) + "\n")
            projected = output / "released-libcore-input.jar"
            with zipfile.ZipFile(projected, "x") as archive:
                for n, data in sorted(selected.items()):
                    archive.writestr(n, data)
            receipt["package_selection"] = {"path": str(selection), "sha256": digest(selection)}
            receipt["module_input"] = {"path": str(projected), "sha256": digest(projected),
                                       "class_count": len(selected)}
            save()  # Exact source-only package projection is pinned before tool launch.

        def checks(classes):
            stream = classes["java/io/ByteArrayOutputStream.class"]
            objects = classes["java/util/Objects.class"]
            os = classes["android/system/Os.class"]
            return {
                "nonnull_field": stream.fields[("buf", "[B")][1]["RuntimeInvisibleAnnotations"],
                "nonnull_return": stream.methods[("toByteArray", "()[B")][1]["RuntimeInvisibleAnnotations"],
                "nullable_parameter": objects.methods[("isNull", "(Ljava/lang/Object;)Z")][1]
                    ["RuntimeInvisibleParameterAnnotations"],
                "excluded_android_return": os.methods[("getsockoptTimeval", "(Ljava/io/FileDescriptor;II)Landroid/system/StructTimeval;")][1]
                    ["RuntimeInvisibleAnnotations"],
            }

        owners = ["java/io/ByteArrayOutputStream.class", "java/util/Objects.class", "android/system/Os.class"]
        with zipfile.ZipFile(baseline) as archive:
            before = checks({n: ClassFile(archive.read(n), n) for n in owners})
        expected = {"nonnull_field": ((NONNULL, ()),), "nonnull_return": ((NONNULL, ()),),
                    "nullable_parameter": (((NULLABLE, ()),),),
                    "excluded_android_return": before["excluded_android_return"]}
        with self.assertRaises(AssertionError) as red:
            self.assertEqual(expected, before)
        receipt["red"] = {"expected": expected, "actual": before, "assertion_failure": str(red.exception)}
        (output / "red-annotation-assertion.log").write_text(str(red.exception) + "\n")
        save()

        helpers = compile_sources("annotation-classes", str(baseline),
                                  [PROBE / n for n in INPUTS if n.endswith(".java")])
        classpath = str(helpers) + ":" + str(baseline)
        stubs = output / "stubs"
        command = [metalava, "--quiet", "main", "--include-annotations",
                   "--force-convert-to-warning-nullability-annotations", WARNING_PACKAGES,
                   "--stubs", stubs]
        command.extend(["--api-class-resolution", "api", PROBE / API] if model == "source-current"
                       else ["--classpath", classpath, projected])
        run("libcore-warning-stubs", command)
        sources = sorted(stubs.rglob("*.java"))
        self.assertTrue(sources)
        emitted_packages = {p.parent.relative_to(stubs).as_posix().replace("/", ".") for p in sources}
        expected_packages = (set(packages) if model == "source-current" else
                             {n.rsplit("/", 1)[0].replace("/", ".") for n in selected})
        self.assertEqual(expected_packages, emitted_packages)
        compiled = compile_sources("libcore-classes", classpath, sources)
        after = checks({n: ClassFile((compiled / n).read_bytes(), n) for n in owners})
        receipt["green"] = after
        save()
        replacements = {p.relative_to(compiled).as_posix(): p.read_bytes()
                        for p in compiled.rglob("*.class")}
        if model == "released-jar":
            deltas = inventory_deltas(selected, replacements)
            receipt["inventory"] = deltas
            save()
            self.assertFalse(deltas["added_classes"] or deltas["removed_classes"])
            self.assertFalse(any(d["added"] or d["removed"]
                                 for owner in deltas["owners"].values()
                                 for key, d in owner.items() if key in ("fields", "methods")),
                             "JAR model changed member key inventory; see receipt")
        self.assertEqual(expected, after)
        candidate = output / ("whole-libcore-policy-probe.jar" if model == "source-current"
                              else "released-libcore-policy-probe.jar")
        with zipfile.ZipFile(baseline) as original, zipfile.ZipFile(candidate, "x") as revised:
            receipt["added_classes"] = sorted(set(replacements) - set(original.namelist()))
            for info in original.infolist():
                revised.writestr(info, replacements.pop(info.filename, original.read(info.filename)))
            for name, data in sorted(replacements.items()):
                revised.writestr(name, data)
        receipt["stub_count"] = len(sources)
        receipt["compiled_class_count"] = sum(1 for _ in compiled.rglob("*.class"))
        receipt["output"] = {"path": str(candidate), "sha256": digest(candidate)}
        if model == "released-jar":
            with zipfile.ZipFile(candidate) as archive:
                final_keys = {n for n in archive.namelist() if n.endswith(".class")}
            self.assertEqual(set(baseline_classes), final_keys)
        receipt["result"] = ("whole module warning policy RED then GREEN; release flags unproven"
                             if model == "source-current" else
                             "released package projection warning policy RED then GREEN; inventories unchanged")
        save()


if __name__ == "__main__":
    unittest.main()
