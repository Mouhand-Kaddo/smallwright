#!/usr/bin/env python3
"""Test raw modifier retention through the pinned packaged Metalava source model.

This is an opt-in, host-only mechanism check. It never supplies SDK inputs to
Metalava's source parser and does not alter the production worker or comparator.
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
from test_network_module_source_headers import digest


ROOT = Path(__file__).resolve().parents[1]
INPUTS = Path(os.environ.get("PLB_RAW_SOURCE_MODIFIER_INPUT_ROOT", ROOT)).resolve()
PROBE = INPUTS / "toolchains/platform-api36-source-probe"
COMMIT = "99b01a65cc4c104933788b3143285ab6bae65827"
METALAVA_COMMIT = "9809e0d70a5b807be3d7070d51f9c4714f2fdcb4"
SDK = INPUTS / "toolchains/android-sdk/platforms/android-36/android.jar"
SDK_SHA256 = "d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a"
PINS = ROOT / "scripts/activity-layout-api/tool-pins.json"
EXTRACTOR = ROOT / "scripts/raw-source-modifier-api/RawSourceModifierExtractor.java"
RUNNER = Path(__file__)
NOTICE = PROBE / "base-r2-sparse/NOTICE"
NOTICE_SHA256 = "cf4dd7cea5b6bcd2622ddd3529b3c74d16ba2b4db444575bc6f5c33f8a960530"

RAW = {
    "android/util/Log.java": (
        "f8737a55a5b09329a125b7f3bdf7cffec8d27068",
        "5c56ee1b4a0b8b59624a20e3bb31b7f41bf6b3171dfce6a1c21d1cab09a205c1",
    ),
    "android/app/ServiceStartNotAllowedException.java": (
        "b1f47eee4bfd71c75461333a18f8169942262ca0",
        "c1e26714d3be0ee713fc4fc6b700f7864cea31be8e34bedb32deedcd0457f13b",
    ),
    "android/text/SpannableStringInternal.java": (
        "90d83d59eb6560a9226af7088268a601e276f7cd",
        "0fd8223fa03a9047a64dca22a4bb06a0baa5ff5a7d2bfd8154bf6659eeffa454",
    ),
    "android/text/SpannedString.java": (
        "a3f1ee2e3f209fc888e7c325a275a84c3721bf1e",
        "db521f71d53631ce35b7861f9d04ad0c96194be286d56d07456f39b1f7de3598",
    ),
}
BASELINE = PROBE / "full-source-jar-filtered-classes.jar"
LOG_BASELINE = PROBE / "network-module-20261002/completed-six/public-six-probe.jar"
BASELINE_PINS = {
    BASELINE: "88644cba114c3574384b4d36833106a435f96cb4c63e6bc7e0b06293853a649a",
    LOG_BASELINE: "f00ef96d0b25990779b7a67ae276f427c2a0d6de974ba73ff99bdc33eec44b1f",
}
CASES = {
    "native": ("android/util/Log.class", ("isLoggable", "(Ljava/lang/String;I)Z"), 0x109),
    "synchronized": ("android/app/ServiceStartNotAllowedException.class",
                      ("getCause", "()Ljava/lang/Throwable;"), 0x21),
    "inherited_final": ("android/text/SpannedString.class", ("length", "()I"), 0x11),
}


@unittest.skipUnless(os.environ.get("PLB_RAW_SOURCE_MODIFIER_MODEL_INTEGRATION") == "1",
                     "requires pinned local raw sources, Metalava, JDK and SDK reference")
class RawSourceModifierModelIntegrationTest(unittest.TestCase):
    def test_source_model_retains_selected_raw_modifiers(self):
        self._run_checkpoint()

    def _run_checkpoint(self):
        for path, expected in BASELINE_PINS.items():
            self.assertEqual(expected, digest(path), str(path))
        raw_root = PROBE / "base-r2-sparse"
        self.assertEqual(NOTICE_SHA256, digest(NOTICE), "base-r2-sparse/NOTICE")
        raw_inventory = []
        for name, (blob, expected) in RAW.items():
            source = raw_root / "core/java" / name
            self.assertEqual(expected, digest(source), name)
            git_environment = {
                "PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "GIT_NO_LAZY_FETCH": "1",
                "GIT_CONFIG_COUNT": "1", "GIT_CONFIG_KEY_0": "protocol.allow",
                "GIT_CONFIG_VALUE_0": "never",
            }
            content = subprocess.run(
                ["git", "-C", str(raw_root), "show", COMMIT + ":core/java/" + name],
                env=git_environment, check=True, capture_output=True, timeout=20,
            ).stdout
            self.assertEqual(source.read_bytes(), content, name)
            actual_blob = subprocess.run(
                ["git", "-C", str(raw_root), "rev-parse", COMMIT + ":core/java/" + name],
                env=git_environment, check=True, capture_output=True, timeout=20,
            ).stdout.decode().strip()
            self.assertEqual(blob, actual_blob, name)
            header = source.read_bytes().split(b"*/", 1)[0] + b"*/\n"
            self.assertTrue(header.startswith(b"/*\n"), name)
            self.assertIn(b"Licensed under the Apache License, Version 2.0", header, name)
            raw_inventory.append({
                "repository": "platform/frameworks/base",
                "path": "core/java/" + name,
                "commit": COMMIT,
                "gitBlob": blob,
                "sha256": expected,
                "licensePrologueSha256": hashlib.sha256(header).hexdigest(),
                "licensePrologue": "AOSP Apache-2.0 header retained verbatim; not a redistribution-clearance claim",
            })

        pins = json.loads(PINS.read_text())
        for group in pins.values():
            installation = INPUTS / group["installation"]
            for item in group["files"]:
                self.assertEqual(item["sha256"], digest(installation / item["path"]), item["path"])
        jdk = INPUTS / pins["jdk"]["installation"]
        metalava = INPUTS / pins["metalava"]["installation"]
        environment = {"JAVA_HOME": str(jdk), "PATH": str(jdk / "bin") + ":/usr/bin:/bin",
                       "LANG": "C.UTF-8"}
        output = PROBE / "raw-source-model-20261012" / ("integration-" + uuid.uuid4().hex)
        output.mkdir(parents=True)
        notice_copy = output / "provenance" / "base-r2-sparse-NOTICE"
        notice_copy.parent.mkdir()
        notice_copy.write_bytes(NOTICE.read_bytes())
        self.assertEqual(NOTICE.read_bytes(), notice_copy.read_bytes())
        receipt = {
            "scope": "three selected raw modifier accessors through packaged Metalava SourceParser",
            "frameworksBaseCommit": COMMIT,
            "metalavaSourceCommitForInterpretation": METALAVA_COMMIT,
            "toolPins": pins,
            "toolPinsSha256": digest(PINS),
            "frameworkNotice": {"path": str(NOTICE), "sha256": NOTICE_SHA256},
            "frameworkNoticeCopy": {"path": str(notice_copy), "sha256": digest(notice_copy),
                                    "byteEqualToOriginal": True},
            "rawSourceInventory": raw_inventory,
            "extractorSourceSha256": digest(EXTRACTOR),
            "runnerSha256": digest(RUNNER),
            "sourceModelClasspath": [],
            "sdkGenerationInputs": [],
            "sdkReference": "not opened until after raw model, mutations and caller compilation",
            "childEnvironment": environment,
            "stages": [],
            "inputs": {str(path): value for path, value in BASELINE_PINS.items()},
            "status": "pinned inputs verified before tool launch",
        }

        def save():
            (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")

        def run(name, command, expected=0):
            command = [str(value) for value in command]
            started = time.monotonic()
            result = subprocess.run(command, cwd=output, env=environment,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                    timeout=90)
            (output / (name + ".log")).write_bytes(result.stdout)
            receipt["stages"].append({"name": name, "command": command,
                                      "exitCode": result.returncode,
                                      "observedHostSeconds": time.monotonic() - started})
            save()
            self.assertEqual(expected, result.returncode,
                             name + ": " + result.stdout.decode(errors="replace")[-3000:])
            return result

        def copy_case(case, mutation=None):
            source_names = {
                "native": ["android/util/Log.java"],
                "synchronized": ["android/app/ServiceStartNotAllowedException.java"],
                "inherited_final": ["android/text/SpannableStringInternal.java",
                                    "android/text/SpannedString.java"],
            }[case]
            source_root = output / (case + ("-mutated" if mutation else "") + "-source")
            source_root.mkdir()
            copies = []
            for name in source_names:
                original = raw_root / "core/java" / name
                data = original.read_bytes()
                expected_sha = RAW[name][1]
                self.assertEqual(expected_sha, digest(original), name)
                text = data.decode()
                token_change = None
                if mutation == "native" and name.endswith("Log.java"):
                    changed, count = re.subn(r"public static native boolean isLoggable\(",
                                             "public static boolean isLoggable(", text, count=1)
                    self.assertEqual(1, count)
                    text = changed
                    token_change = {"declaration": "Log.isLoggable(String,int)",
                                    "oldToken": "native", "newToken": "(removed)"}
                elif mutation == "synchronized" and name.endswith("ServiceStartNotAllowedException.java"):
                    changed, count = re.subn(r"public synchronized Throwable getCause\(\)",
                                             "public Throwable getCause()", text, count=1)
                    self.assertEqual(1, count)
                    text = changed
                    token_change = {"declaration": "ServiceStartNotAllowedException.getCause()",
                                    "oldToken": "synchronized", "newToken": "(removed)"}
                elif mutation == "final" and name.endswith("SpannableStringInternal.java"):
                    changed, count = re.subn(r"public final int length\(\)",
                                             "public int length()", text, count=1)
                    self.assertEqual(1, count)
                    text = changed
                    token_change = {"declaration": "SpannableStringInternal.length()",
                                    "oldToken": "final", "newToken": "(removed)"}
                copied = source_root / name
                copied.parent.mkdir(parents=True, exist_ok=True)
                copied.write_text(text)
                if token_change is None:
                    self.assertEqual(data, copied.read_bytes(), "unmutated raw copy changed: " + name)
                    self.assertEqual(expected_sha, digest(copied), "unmutated copy checksum: " + name)
                copies.append({"original": str(original), "isolatedCopy": str(copied),
                               "originalSha256": digest(original), "copySha256": digest(copied),
                               "tokenMutation": token_change})
            receipt.setdefault("sourceCopies", {})[case + ("_mutation" if mutation else "_pinned")] = copies
            save()
            return source_root

        # Preserve each baseline RED separately from the direct source-model result.
        with zipfile.ZipFile(LOG_BASELINE) as archive:
            log = ClassFile(archive.read(CASES["native"][0]), CASES["native"][0])
        with self.assertRaises(AssertionError) as native_red:
            self.assertEqual(0x109, log.methods[CASES["native"][1]][0])
        with zipfile.ZipFile(BASELINE) as archive:
            service = ClassFile(archive.read(CASES["synchronized"][0]), CASES["synchronized"][0])
            spanned = ClassFile(archive.read(CASES["inherited_final"][0]), CASES["inherited_final"][0])
        with self.assertRaises(AssertionError) as sync_red:
            self.assertIn(CASES["synchronized"][1], service.methods)
        with self.assertRaises(AssertionError) as final_red:
            self.assertEqual(0x11, spanned.methods[CASES["inherited_final"][1]][0])
        receipt["baselineRed"] = {
            "native": {"actualFlags": hex(log.methods[CASES["native"][1]][0]),
                       "expectedFlags": "0x109", "assertion": str(native_red.exception)},
            "synchronized": {"directMemberPresent": CASES["synchronized"][1] in service.methods,
                             "assertion": str(sync_red.exception)},
            "inheritedFinal": {"actualFlags": hex(spanned.methods[CASES["inherited_final"][1]][0]),
                               "expectedFlags": "0x11", "assertion": str(final_red.exception)},
        }
        with zipfile.ZipFile(BASELINE) as archive:
            log_candidate = ClassFile(archive.read(CASES["native"][0]), CASES["native"][0])
        receipt["candidateBeforeRawModel"] = {
            "native": {"entryPresent": True,
                       "methodFlags": hex(log_candidate.methods[CASES["native"][1]][0])},
            "synchronized": {"entryPresent": True,
                             "directMethodPresent": CASES["synchronized"][1] in service.methods,
                             "inheritedMethodFlags": hex(service.methods[CASES["synchronized"][1]][0])
                             if CASES["synchronized"][1] in service.methods else None},
            "inherited_final": {"entryPresent": True,
                                "methodFlags": hex(spanned.methods[CASES["inherited_final"][1]][0])},
        }
        (output / "baseline-red.log").write_text(json.dumps(receipt["baselineRed"], indent=2) + "\n")
        save()

        classes = output / "extractor-classes"
        classes.mkdir()
        run("extractor-javac", [jdk / "bin/javac", "-J-Xmx512m", "-proc:none", "-cp",
                                 str(metalava / "lib/*"), "-d", classes, EXTRACTOR])
        receipt["extractorClassFiles"] = [
            {"path": path.relative_to(output).as_posix(), "sha256": digest(path)}
            for path in sorted(classes.rglob("*.class"))
        ]
        tmp = output / "tmp"
        tmp.mkdir()

        def extract(case, mutated=False):
            source_root = copy_case(case, {"native": "native", "synchronized": "synchronized",
                                           "inherited_final": "final"}[case] if mutated else None)
            expected_source_names = {
                "native": ["android/util/Log.java"],
                "synchronized": ["android/app/ServiceStartNotAllowedException.java"],
                "inherited_final": ["android/text/SpannableStringInternal.java",
                                    "android/text/SpannedString.java"],
            }[case]
            self.assertEqual(sorted(expected_source_names),
                             sorted(path.relative_to(source_root).as_posix()
                                    for path in source_root.rglob("*.java")))
            destination = output / (case + ("-mutation" if mutated else "") + ".json")
            run(case + ("-mutation" if mutated else "-raw-model"), [
                jdk / "bin/java", "-ea", "-Xms128m", "-Xmx1024m", "-Djava.awt.headless=true",
                "-Djava.io.tmpdir=" + str(tmp), "-cp", str(classes) + ":" + str(metalava / "lib/*"),
                "RawSourceModifierExtractor", case, source_root, destination, jdk,
            ])
            model = json.loads(destination.read_text())
            receipt.setdefault("models", {})[case + ("_mutation" if mutated else "")] = model
            save()
            return model

        for case, expected in CASES.items():
            model = extract(case)
            self.assertEqual("COMMAND_LINE", model["ownerOrigin"], case)
            expected_method_origin = "SOURCE_PATH" if case == "inherited_final" else "COMMAND_LINE"
            self.assertEqual(expected_method_origin, model["selected"]["containingOrigin"], case)
            self.assertEqual("[]", json.dumps(model["sourceModelClasspath"]), case)
            self.assertEqual("[]", json.dumps(model["sdkGenerationInputs"]), case)
            self.assertTrue(Path(model["ownerSource"]).is_relative_to(
                Path(model["sourceRoot"]).resolve()), case)
            self.assertEqual(hex(expected[2]), model["selected"]["flags"], case)
            if case == "inherited_final":
                source_root = Path(model["sourceRoot"]).resolve()
                expected_sources = sorted(str(source_root / name) for name in (
                    "android/text/SpannableStringInternal.java", "android/text/SpannedString.java"))
                self.assertEqual(expected_sources,
                                 sorted(source["path"] for source in model["sources"]))
                self.assertEqual("SOURCE_PATH", model["baseOrigin"])
                self.assertEqual("package private", model["baseVisibility"])
                self.assertEqual("public", model["ownerVisibility"])
                self.assertTrue(model["inheritedLookup"]["final"])
                self.assertEqual("android.text.SpannableStringInternal",
                                 model["inheritedLookup"]["containingClass"])
                self.assertEqual(model["baseSource"], model["inheritedLookup"]["containingSource"])
                self.assertEqual("SOURCE_PATH", model["inheritedLookup"]["containingOrigin"])
            if case == "synchronized":
                self.assertTrue(model["selected"]["synchronized"])
            if case == "native":
                self.assertTrue(model["selected"]["native"])

        for case in ("native", "synchronized", "inherited_final"):
            mutated = extract(case, mutated=True)
            key = {"native": "native", "synchronized": "synchronized",
                   "inherited_final": "final"}[case]
            if case == "inherited_final":
                self.assertFalse(mutated["baseMethod"][key])
                self.assertFalse(mutated["inheritedLookup"][key])
                self.assertNotEqual(hex(CASES[case][2]), mutated["inheritedLookup"]["flags"])
                with self.assertRaises(AssertionError) as mutation_red:
                    self.assertEqual(hex(CASES[case][2]), mutated["inheritedLookup"]["flags"])
            else:
                self.assertFalse(mutated["selected"][key])
                self.assertNotEqual(hex(CASES[case][2]), mutated["selected"]["flags"])
                with self.assertRaises(AssertionError) as mutation_red:
                    self.assertEqual(hex(CASES[case][2]), mutated["selected"]["flags"])
            receipt.setdefault("mutationNegative", {})[case] = {
                "modifier": key, "mutatedValue": False,
                "expectedFlags": hex(CASES[case][2]),
                "actualFlags": mutated["selected"]["flags"],
                "expectedFlagsAssertionFailure": str(mutation_red.exception),
                "sourceModelPath": str(output / (case + "-mutation.json"))}
            save()

        # Compile call sites against the pre-existing filtered candidate. This
        # checks callable shape only; it does not claim modifier preservation.
        caller = output / "RawModifierCaller.java"
        caller.write_text("""final class RawModifierCaller {
    static int call(android.app.ServiceStartNotAllowedException error) {
        android.util.Log.isLoggable(\"T\", 3);
        error.getCause();
        return new android.text.SpannedString(\"x\").length();
    }
}
""")
        caller_classes = output / "caller-classes"
        caller_classes.mkdir()
        run("caller-without-sdk", [jdk / "bin/javac", "-J-Xmx512m", "-proc:none", "-classpath",
                                    BASELINE, "-d", caller_classes, caller])
        receipt["caller"] = {"path": str(caller), "sha256": digest(caller),
                             "classpath": str(BASELINE), "sdkReferenceOpened": False,
                             "result": "callable selected public shapes compile; modifier retention checked separately"}
        save()

        # Open the SDK reference only after source-model checks, mutations and
        # candidate caller compilation have completed.
        sdk_sha = digest(SDK)
        self.assertEqual(SDK_SHA256, sdk_sha, "API 36 SDK reference changed")
        receipt["sdkReference"] = {"path": str(SDK), "sha256": sdk_sha, "openedLast": True}
        save()
        selected = {}
        with zipfile.ZipFile(SDK) as archive:
            for case, (entry, method, expected) in CASES.items():
                reference = ClassFile(archive.read(entry), entry)
                self.assertIn(method, reference.methods, "SDK reference missing " + case)
                self.assertEqual(expected, reference.methods[method][0], "SDK flags changed for " + case)
                with zipfile.ZipFile(BASELINE) as candidate_archive:
                    candidate = ClassFile(candidate_archive.read(entry), entry)
                candidate_flags = candidate.methods[method][0] if method in candidate.methods else None
                selected[case] = {"entry": entry, "method": method,
                                  "referenceMethodPresent": method in reference.methods,
                                  "candidateMethodPresent": method in candidate.methods,
                                  "referenceFlags": hex(reference.methods[method][0]),
                                  "candidateFlags": hex(candidate_flags) if candidate_flags is not None else None,
                                  "expectedFlags": hex(expected)}
        receipt["sdkSelectedComparison"] = selected
        receipt["status"] = "source-model modifier retention checked; whole API closure remains out of scope"
        save()
        print("RAW_SOURCE_MODIFIER_MODEL=" + str(output))


if __name__ == "__main__":
    unittest.main()
