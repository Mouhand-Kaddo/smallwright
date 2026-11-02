#!/usr/bin/env python3
"""Validation and opt-in real source-only libcore header-builder regressions."""

import json
import os
from pathlib import Path
import re
import tempfile
import time
import unittest
from unittest.mock import patch
import uuid

import build_platform_source_headers as builder
from compare_platform_abi import ClassFile, classes
from test_network_module_source_headers import digest, tool_identity
from test_libcore_nullability_policy import INPUTS as POLICY_INPUTS


ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "toolchains/platform-api36-source-probe"
WARNING_POLICY = "+*:-android.*:+android.icu.*:-dalvik.*"


class LibcorePolicyValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="libcore-policy-validation-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.api = self.root / "api.txt"
        self.api.write_text("// Signature format: 2.0\npackage java.io {\n}\n")
        self.bp = self.root / "JavaLibrary.bp"
        self.bp.write_text('java_sdk_library {\n name: "art.module.public.api",\n'
                           ' droiddoc_options: ["--force-convert-to-warning-nullability-annotations '
                           + WARNING_POLICY + ' "],\n}\n')
        self.manifest = self.root / "manifest.json"
        self.document = {"contributions": [{"path": str(self.api), "sha256": digest(self.api),
                                            "module": "art.module.public.api"}],
                         "libcore_warning_policy": {"path": str(self.bp), "sha256": digest(self.bp)}}
        metalava = self.root / "metalava"
        metalava.write_text("#!/bin/sh\n")
        metalava.chmod(0o755)
        jdk = self.root / "jdk"
        (jdk / "bin").mkdir(parents=True)
        (jdk / "bin/javac").write_text("")
        annotations = self.root / "annotation-sources"
        annotations.mkdir()
        self.output = self.root / "output"
        self.arguments = builder.BuildArguments(self.manifest, metalava, jdk, annotations, self.output)

    def assert_rejected_before_tools_or_output(self):
        self.manifest.write_text(json.dumps(self.document))
        with patch.object(builder.subprocess, "Popen",
                          side_effect=AssertionError("Tool launched before policy validation")) as launch:
            with self.assertRaises(builder.BuildError):
                builder.build(self.arguments)
        launch.assert_not_called()
        self.assertFalse(self.output.exists())

    def test_tampered_libcore_policy_is_rejected_before_tools_or_output(self):
        self.bp.write_text(self.bp.read_text() + "// modified after pinning\n")
        self.assert_rejected_before_tools_or_output()

    def test_shared_libcore_package_is_rejected_before_tools_or_output(self):
        second = self.root / "framework-api.txt"
        second.write_text("// Signature format: 2.0\npackage java.io {\n}\n")
        self.document["contributions"].append({"path": str(second), "sha256": digest(second),
                                               "module": "android-non-updatable"})
        self.assert_rejected_before_tools_or_output()

    def test_policy_without_exact_libcore_module_is_rejected(self):
        self.document["contributions"][0]["module"] = "android-non-updatable"
        self.assert_rejected_before_tools_or_output()

    def test_unsupported_source_policy_is_rejected_before_tools_or_output(self):
        self.bp.write_text(self.bp.read_text().replace(WARNING_POLICY, "+*"))
        self.document["libcore_warning_policy"]["sha256"] = digest(self.bp)
        self.assert_rejected_before_tools_or_output()

    def test_policy_excludes_exact_android_dalvik_roots_and_includes_icu_root(self):
        self.api.write_text("// Signature format: 2.0\n" + "".join(
            "package " + name + " {\n}\n" for name in
            ("android", "android.deep", "android.icu", "android.icu.deep",
             "dalvik", "dalvik.deep", "java.io")))
        self.document["contributions"][0]["sha256"] = digest(self.api)
        self.manifest.write_text(json.dumps(self.document))
        # Observe the public failure receipt after validation; no tool is executed.
        with patch.object(builder.subprocess, "Popen", side_effect=OSError("intentional launch failure")):
            with self.assertRaisesRegex(builder.BuildError, "intentional launch failure"):
                builder.build(self.arguments)
        receipt = json.loads((self.output / "receipt.json").read_text())
        policy = receipt["libcore_warning_policy"]
        self.assertEqual(["android.icu", "android.icu.deep", "java.io"], policy["eligible_packages"])
        self.assertEqual("+android.icu:+android.icu.deep:+java.io", policy["package_filter"])


NONNULL = "Landroidx/annotation/RecentlyNonNull;"
NULLABLE = "Landroidx/annotation/RecentlyNullable;"
SOURCE_PINS = {
    "toolchains/platform-api36-source-probe/module-map/libcore/api/current.txt":
        "5fdbc5046294287f39d030a948c28fb2da2619546da893224c2333f32bae7c39",
    "toolchains/platform-api36-source-probe/module-map/libcore/JavaLibrary.bp":
        "947c58991fca96976da15ad28b579a44b17fc96c178ce2e57360e1aec72473d1",
    "toolchains/platform-api36-source-probe/framework-flags-r2-public-modules/exportable-flags-config.xml":
        "671edeeefdea02de7552383e34dfd64059f556ec738be5eb319d4ab7b162d2e3",
    "toolchain/platform-api35-filtered-source-history.json":
        "05e854ec9a74f12c46713382a5be849e076914fd4dcb7e3790a740490ed9157e",
    "toolchains/platform-api36-source-probe/api35-release-20261002/ap3a-filtered/filtered-api.txt":
        "22f960867194fbdfcbc03ca09cec36468f847524966af5f572e43f1d315950ab",
}
SOURCE_REFERENCE = "raw-modifiers-20261002/integration-aa30c0992e2a4be49fe85d890420b4fe/public-owner-probe.jar"


def warning_attributes(attributes):
    rename = {"Landroid/annotation/NonNull;": NONNULL,
              "Landroid/annotation/Nullable;": NULLABLE}

    def substitute(value):
        if isinstance(value, str):
            return rename.get(value, value)
        if isinstance(value, tuple):
            return tuple(substitute(v) for v in value)
        return value

    expected = dict(attributes)
    if "RuntimeInvisibleAnnotations" in expected:
        expected["RuntimeInvisibleAnnotations"] = tuple(sorted(
            substitute(expected["RuntimeInvisibleAnnotations"]), key=repr))
    if "RuntimeInvisibleParameterAnnotations" in expected:
        expected["RuntimeInvisibleParameterAnnotations"] = tuple(
            tuple(sorted(substitute(group), key=repr))
            for group in expected["RuntimeInvisibleParameterAnnotations"])
    return expected


def warning_examples(archive):
    stream = ClassFile(archive["java/io/ByteArrayOutputStream.class"], "java/io/ByteArrayOutputStream.class")
    objects = ClassFile(archive["java/util/Objects.class"], "java/util/Objects.class")
    return {"nonnull_field": stream.fields[("buf", "[B")][1]["RuntimeInvisibleAnnotations"],
            "nonnull_return": stream.methods[("toByteArray", "()[B")][1]["RuntimeInvisibleAnnotations"],
            "nullable_parameter": objects.methods[("isNull", "(Ljava/lang/Object;)Z")][1]
                ["RuntimeInvisibleParameterAnnotations"]}


@unittest.skipUnless(os.environ.get("PLB_LIBCORE_BUILDER_INTEGRATION") == "1",
                     "requires pinned source contributions, Metalava and JDK")
class LibcoreHeaderBuilderIntegrationTest(unittest.TestCase):
    def test_real_builder_policy_preserves_release_selection_and_all_metadata(self):
        for relative, expected in SOURCE_PINS.items():
            self.assertEqual(expected, digest(ROOT / relative), relative)
        self.assertEqual(POLICY_INPUTS[SOURCE_REFERENCE], digest(PROBE / SOURCE_REFERENCE))
        for relative, expected in POLICY_INPUTS.items():
            if relative.endswith(".java"):
                self.assertEqual(expected, digest(PROBE / relative), relative)
        api = ROOT / next(iter(SOURCE_PINS))
        bp = api.parent.parent / "JavaLibrary.bp"
        packages = sorted(set(re.findall(r"^package ([\w.]+) \{", api.read_text(), re.M)))
        self.assertEqual(79, len(packages))
        # Upstream PackageFilter foo.* includes foo itself, not just descendants.
        eligible = {p for p in packages
                    if not (p == "android" or p.startswith("android.") or
                            p == "dalvik" or p.startswith("dalvik."))
                    or p == "android.icu" or p.startswith("android.icu.")}
        self.assertEqual(73, len(eligible))
        jdk = ROOT / "toolchains/jdk-21.0.12.1+1"
        metalava = PROBE / "out-r4/metalava/metalava/build/install/metalava/bin/metalava"
        annotations = PROBE / "metalava/stub-annotations/src/main/java"
        identity = tool_identity(jdk, metalava)
        output = PROBE / "libcore-builder-20261002" / ("integration-" + uuid.uuid4().hex)
        output.mkdir(parents=True)
        receipt = {"status": "experimental_real_builder_comparison_not_worker_input",
                   "builder_sha256": digest(Path(builder.__file__)),
                   "source_pins": SOURCE_PINS, "tools": identity,
                   "source_reference": {"path": str(PROBE / SOURCE_REFERENCE),
                                        "sha256": POLICY_INPUTS[SOURCE_REFERENCE]},
                   "annotation_sources": {str(p): digest(p) for p in sorted(annotations.rglob("*.java"))},
                   "packages": packages, "eligible_packages": sorted(eligible),
                   "upstream_warning_policy": WARNING_POLICY, "stages": []}

        def save():
            (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")

        save()  # All source, policy, history, helper and actual tool pins precede tools.
        history = ROOT / "toolchain/platform-api35-filtered-source-history.json"
        flags = PROBE / "framework-flags-r2-public-modules/exportable-flags-config.xml"
        selection = output / "release-filter.json"
        selection.write_text(json.dumps({"api_flags": {"path": str(flags), "sha256": digest(flags)},
                                         "history_manifest": {"path": str(history), "sha256": digest(history)}}))
        contributions = [{"path": str(api), "sha256": digest(api), "module": "art.module.public.api"}]

        def build(name, policy):
            manifest = output / (name + "-manifest.json")
            document = {"contributions": contributions}
            if policy:
                document["libcore_warning_policy"] = {"path": str(bp), "sha256": digest(bp)}
            manifest.write_text(json.dumps(document, indent=2) + "\n")
            started = time.monotonic()
            try:
                archive = builder.build(builder.BuildArguments(manifest, metalava, jdk, annotations,
                                                                output / name, selection))
            except Exception as error:
                receipt["stages"].append({"name": name, "seconds": time.monotonic() - started,
                                          "error": str(error)})
                save()
                raise
            receipt["stages"].append({"name": name, "seconds": time.monotonic() - started,
                                      "output": str(archive), "sha256": digest(archive)})
            save()
            return classes(archive)

        before = build("no-policy", False)
        expected_examples = {"nonnull_field": ((NONNULL, ()),), "nonnull_return": ((NONNULL, ()),),
                             "nullable_parameter": (((NULLABLE, ()),),)}
        actual_examples = warning_examples(before)
        with self.assertRaises(AssertionError) as red:
            self.assertEqual(expected_examples, actual_examples)
        receipt["red"] = {"expected": expected_examples, "actual": actual_examples,
                          "failure": str(red.exception)}
        save()
        after = build("with-policy", True)
        receipt["green"] = warning_examples(after)
        save()
        self.assertEqual(expected_examples, receipt["green"])
        self.assertEqual(set(before), set(after))
        module_keys = {n for n in before if n.rsplit("/", 1)[0].replace("/", ".") in packages}
        reference = classes(PROBE / SOURCE_REFERENCE)
        reference_keys = {n for n in reference if n.rsplit("/", 1)[0].replace("/", ".") in packages}
        self.assertEqual(1506, len(module_keys))
        self.assertEqual(reference_keys, module_keys)
        changes = []
        for name in sorted(before):
            left, right = ClassFile(before[name], name), ClassFile(after[name], name)
            self.assertEqual(left.abi()[:4], right.abi()[:4], name + " class metadata")
            package = name.rsplit("/", 1)[0].replace("/", ".")
            for kind in ("fields", "methods"):
                old, new = getattr(left, kind), getattr(right, kind)
                self.assertEqual(set(old), set(new), name + " " + kind + " inventory")
                for key, (flags_before, attrs_before) in old.items():
                    self.assertEqual(flags_before, new[key][0], name + str(key) + " flags")
                    expected = warning_attributes(attrs_before) if package in eligible else attrs_before
                    if expected != new[key][1]:
                        changes.append({"owner": name, "kind": kind, "member": key,
                                        "expected": expected, "actual": new[key][1]})
        receipt["metadata_mismatches"] = changes
        receipt["release_inventory"] = {"module_class_count": len(module_keys),
                                         "whole_class_count": len(before),
                                         "added_classes": sorted(set(after) - set(before)),
                                         "removed_classes": sorted(set(before) - set(after))}
        save()
        self.assertFalse(changes, "Final metadata, including history replacements, differs; see receipt")
        # Literal upstream flagged declarations absent from the proven released profile.
        self.assertNotIn("android/crypto/hpke/Hpke.class", after)
        os_class = ClassFile(after["android/system/Os.class"], "android/system/Os.class")
        self.assertNotIn(("madvise", "(JJI)V"), os_class.methods)
        duration = ClassFile(after["java/time/Duration.class"], "java/time/Duration.class")
        self.assertNotIn(("isPositive", "()Z"), duration.methods)
        constants = ClassFile(after["android/system/OsConstants.class"], "android/system/OsConstants.class")
        self.assertFalse(any(k[0].startswith("MADV_") for k in constants.fields))
        receipt["result"] = "real builder RED then GREEN; released inventory and other metadata unchanged"
        save()


if __name__ == "__main__":
    unittest.main()
