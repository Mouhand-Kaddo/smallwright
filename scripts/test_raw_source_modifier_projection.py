#!/usr/bin/env python3
"""Test a bounded raw-model correction to three filtered API owner stubs.

This opt-in host experiment never changes the worker, Metalava, release
filter, or ABI comparator. It source-compiles three model-derived declarations
into isolated copies of the current public API projection and never executes
the resulting classes.
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
from test_raw_source_modifier_model import (
    BASELINE_PINS,
    CASES,
    COMMIT,
    METALAVA_COMMIT,
    NOTICE_SHA256,
    RAW,
    SDK_SHA256,
)


ROOT = Path(__file__).resolve().parents[1]
INPUTS = Path(os.environ.get(
    "PLB_RAW_SOURCE_MODIFIER_INPUT_ROOT", ROOT
)).resolve()
PROBE = INPUTS / "toolchains/platform-api36-source-probe"
SDK = INPUTS / "toolchains/android-sdk/platforms/android-36/android.jar"
BASELINE = PROBE / "full-source-jar-filtered-classes.jar"
SOURCE_STUBS = PROBE / "full-source-jar-filtered-stubs"
NOTICE = PROBE / "base-r2-sparse/NOTICE"
PIN_FILE = ROOT / "scripts/activity-layout-api/tool-pins.json"
EXTRACTOR = ROOT / "scripts/raw-source-modifier-api/RawSourceModifierExtractor.java"
RUNNER = Path(__file__)
COMPARATOR = ROOT / "scripts/compare_platform_abi.py"

EXPECTED_STUBS = {
    "android/util/Log.java": "5be81800f50bfe8885a7e085d9494a52f5a737f9ee8473e1a138196a0fbf3dc5",
    "android/app/ServiceStartNotAllowedException.java":
        "ad055b5012dd0431e9947d324c7f15777fcb50a8dcd6192a69254c75fe326d2c",
    "android/text/SpannedString.java":
        "397ad46f96de996db63ad6fa653a10020a7840ba3137dfd67bbde479a264fd7a",
}
SOURCE_FILES = {
    "native": ["android/util/Log.java"],
    "synchronized": ["android/app/ServiceStartNotAllowedException.java"],
    "inherited_final": [
        "android/text/SpannableStringInternal.java",
        "android/text/SpannedString.java",
    ],
}
MUTATION = {"native": "native", "synchronized": "synchronized", "inherited_final": "final"}
SOURCE_STUB_OWNER = {
    "native": "android/util/Log.java",
    "synchronized": "android/app/ServiceStartNotAllowedException.java",
    "inherited_final": "android/text/SpannedString.java",
}
ANNOTATION_SUPPORT = {
    "stub-annotations/src/main/java/android/annotation/NonNull.java": {
        "gitBlob": "c826461be7ee6d1209a7470bada9e91d1f532765",
        "sha256": "abfade84be1f981581becbedee722e036c4469f3593f9924311df7803ab5d3ff",
    },
    "stub-annotations/src/main/java/android/annotation/Nullable.java": {
        "gitBlob": "93b73e3de5ad45606241749e69c1f08cfb08cf3d",
        "sha256": "7827195fab5740747a47666b44ea4c7bf066f904de88a844652670ea9f2cf460",
    },
}
EXPECTED_MODEL = {
    "native": {
        "owner": "android.util.Log",
        "method": "isLoggable",
        "descriptor": "(Ljava/lang/String;I)Z",
        "return": "boolean",
        "parameters": ["java.lang.String", "int"],
        "parameterNames": ["tag", "level"],
        "flags": "0x109",
        "containingClass": "android.util.Log",
    },
    "synchronized": {
        "owner": "android.app.ServiceStartNotAllowedException",
        "method": "getCause",
        "descriptor": "()Ljava/lang/Throwable;",
        "return": "java.lang.Throwable",
        "parameters": [],
        "parameterNames": [],
        "flags": "0x21",
        "containingClass": "android.app.ServiceStartNotAllowedException",
    },
    "inherited_final": {
        "owner": "android.text.SpannedString",
        "method": "length",
        "descriptor": "()I",
        "return": "int",
        "parameters": [],
        "parameterNames": [],
        "flags": "0x11",
        "containingClass": "android.text.SpannableStringInternal",
    },
}
METHOD_CLASS_ENTRY = {key: value[0] for key, value in CASES.items()}
METHOD_KEY = {key: value[1] for key, value in CASES.items()}
EXPECTED_FLAGS = {key: value[2] for key, value in CASES.items()}


@unittest.skipUnless(os.environ.get("PLB_RAW_SOURCE_MODIFIER_PROJECTION") == "1",
                     "requires pinned local raw sources, Metalava, JDK and SDK reference")
class RawSourceModifierProjectionIntegrationTest(unittest.TestCase):
    def test_raw_models_correct_only_three_filtered_public_owner_stubs(self):
        pins = json.loads(PIN_FILE.read_text())
        for path, expected in BASELINE_PINS.items():
            self.assertEqual(expected, digest(path), str(path))
        for relative, expected in EXPECTED_STUBS.items():
            self.assertEqual(expected, digest(SOURCE_STUBS / relative), relative)
        self.assertEqual(NOTICE_SHA256, digest(NOTICE), str(NOTICE))
        for group in pins.values():
            installation = INPUTS / group["installation"]
            for item in group["files"]:
                self.assertEqual(item["sha256"], digest(installation / item["path"]), item["path"])

        jdk = INPUTS / pins["jdk"]["installation"]
        metalava_root = INPUTS / pins["metalava"]["installation"]
        metalava = metalava_root / "bin/metalava"
        tools = tool_identity(jdk, metalava)
        output = ROOT / "toolchains/platform-api36-source-projection-20261012" / (
            "integration-" + uuid.uuid4().hex
        )
        output.mkdir(parents=True)
        raw_root = PROBE / "base-r2-sparse/core/java"
        notice_copy = output / "provenance/base-r2-sparse-NOTICE"
        notice_copy.parent.mkdir(parents=True)
        notice_copy.write_bytes(NOTICE.read_bytes())
        self.assertEqual(NOTICE.read_bytes(), notice_copy.read_bytes())

        receipt = {
            "scope": "three selected raw-model method declarations projected into isolated filtered owner stubs",
            "frameworksBaseCommit": COMMIT,
            "metalavaSourceCommitForInterpretation": METALAVA_COMMIT,
            "toolPins": pins,
            "toolIdentity": tools,
            "toolPinsSha256": digest(PIN_FILE),
            "extractorSource": str(EXTRACTOR),
            "extractorSourceSha256": digest(EXTRACTOR),
            "testRunner": str(RUNNER),
            "testRunnerSha256": digest(RUNNER),
            "comparator": str(COMPARATOR),
            "comparatorSha256": digest(COMPARATOR),
            "filteredCandidateJar": str(BASELINE),
            "filteredCandidateJarSha256": digest(BASELINE),
            "filteredStubSources": [],
            "candidateProvenance": "pinned existing filtered projection; whole provenance and redistribution remain unresolved",
            "frameworkNotice": {"path": str(NOTICE), "sha256": NOTICE_SHA256},
            "frameworkNoticeCopy": {"path": str(notice_copy), "sha256": digest(notice_copy),
                                    "byteEqualToOriginal": True},
            "rawSourceInventory": [],
            "sourceModelClasspath": [],
            "sdkGenerationInputs": [],
            "sdkReference": {"path": str(SDK), "opened": False},
            "childEnvironment": {"JAVA_HOME": str(jdk), "PATH": str(jdk / "bin") + ":/usr/bin:/bin",
                                 "LANG": "C.UTF-8"},
            "compilerIdentity": {"path": str(jdk / "bin/javac"),
                                 "pinnedJdkReleaseSha256": digest(jdk / "release")},
            "compilerFlags": [],
            "stages": [],
            "status": "pinned inputs verified before source model or output generation",
        }

        def save():
            (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")

        def run(name, command, expected=0):
            command = [str(item) for item in command]
            save()
            started = time.monotonic()
            result = subprocess.run(command, cwd=output, env=environment,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=90)
            (output / (name + ".log")).write_bytes(result.stdout)
            receipt["stages"].append({"name": name, "command": command,
                                      "exitCode": result.returncode,
                                      "observedHostSeconds": time.monotonic() - started})
            save()
            self.assertEqual(expected, result.returncode,
                             name + ": " + result.stdout.decode(errors="replace")[-5000:])
            return result

        environment = {"JAVA_HOME": str(jdk), "PATH": str(jdk / "bin") + ":/usr/bin:/bin",
                       "LANG": "C.UTF-8"}
        save()

        # Baseline REDs precede parser execution and every output write.
        with zipfile.ZipFile(BASELINE) as archive:
            originals = {name: ClassFile(archive.read(name), name)
                         for name in METHOD_CLASS_ENTRY.values()}
        red = {}
        with self.assertRaises(AssertionError) as native_red:
            self.assertEqual(0x109, originals[METHOD_CLASS_ENTRY["native"]].methods[
                METHOD_KEY["native"]][0])
        red["native"] = {"assertion": str(native_red.exception),
                         "actualFlags": hex(originals[METHOD_CLASS_ENTRY["native"]].methods[
                             METHOD_KEY["native"]][0]), "expectedFlags": "0x109"}
        with self.assertRaises(AssertionError) as sync_red:
            self.assertIn(METHOD_KEY["synchronized"], originals[
                METHOD_CLASS_ENTRY["synchronized"]].methods)
        red["synchronized"] = {"assertion": str(sync_red.exception),
                               "directMethodPresent": METHOD_KEY["synchronized"] in originals[
                                   METHOD_CLASS_ENTRY["synchronized"]].methods}
        with self.assertRaises(AssertionError) as final_red:
            self.assertEqual(0x11, originals[METHOD_CLASS_ENTRY["inherited_final"]].methods[
                METHOD_KEY["inherited_final"]][0])
        red["inherited_final"] = {"assertion": str(final_red.exception),
                                  "actualFlags": hex(originals[METHOD_CLASS_ENTRY[
                                      "inherited_final"]].methods[METHOD_KEY["inherited_final"]][0]),
                                  "expectedFlags": "0x11"}
        receipt["baselineRed"] = red
        save()

        raw_inventory = []
        copied_notice = notice_copy.read_bytes()
        for name, (blob, expected_sha) in RAW.items():
            original = raw_root / name
            self.assertEqual(expected_sha, digest(original), name)
            relative = "core/java/" + name
            git_env = {"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "GIT_NO_LAZY_FETCH": "1",
                       "GIT_CONFIG_COUNT": "1", "GIT_CONFIG_KEY_0": "protocol.allow",
                       "GIT_CONFIG_VALUE_0": "never"}
            git_bytes = subprocess.run(["git", "-C", str(PROBE / "base-r2-sparse"), "show",
                                        COMMIT + ":" + relative], env=git_env, check=True,
                                       capture_output=True, timeout=20).stdout
            self.assertEqual(original.read_bytes(), git_bytes, name)
            actual_blob = subprocess.run(["git", "-C", str(PROBE / "base-r2-sparse"), "rev-parse",
                                          COMMIT + ":" + relative], env=git_env, check=True,
                                         capture_output=True, timeout=20).stdout.decode().strip()
            self.assertEqual(blob, actual_blob, name)
            header = original.read_bytes().split(b"*/", 1)[0] + b"*/\n"
            self.assertTrue(header.startswith(b"/*\n"), name)
            self.assertIn(b"Licensed under the Apache License, Version 2.0", header, name)
            raw_inventory.append({"repository": "platform/frameworks/base", "path": relative,
                                  "commit": COMMIT, "gitBlob": blob, "sha256": expected_sha,
                                  "licensePrologueSha256": hashlib.sha256(header).hexdigest(),
                                  "licensePrologue": "original AOSP Apache-2.0 prologue retained verbatim; no clearance claim"})
        receipt["rawSourceInventory"] = raw_inventory
        receipt["frameworkNoticeCopy"]["byteEqualToOriginal"] = copied_notice == NOTICE.read_bytes()
        self.assertTrue(receipt["frameworkNoticeCopy"]["byteEqualToOriginal"])

        for relative, expected_sha in EXPECTED_STUBS.items():
            receipt["filteredStubSources"].append({"path": str(SOURCE_STUBS / relative),
                                                   "sha256": expected_sha,
                                                   "candidateProvenance": "current pinned filtered source projection"})

        classes = output / "extractor-classes"
        classes.mkdir()
        run("extractor-javac", [jdk / "bin/javac", "-J-Xmx512m", "-proc:none", "-cp",
                                str(metalava_root / "lib/*"), "-d", classes, EXTRACTOR])
        receipt["extractorClassFiles"] = [
            {"path": path.relative_to(output).as_posix(), "sha256": digest(path)}
            for path in sorted(classes.rglob("*.class"))
        ]
        tmp = output / "tmp"
        tmp.mkdir()

        def copy_sources(case, mutation=None):
            source_root = output / (case + ("-mutated" if mutation else "") + "-source")
            source_root.mkdir()
            copies = []
            for relative in SOURCE_FILES[case]:
                source = raw_root / relative
                original = source.read_bytes()
                expected_sha = RAW[relative][1]
                self.assertEqual(expected_sha, digest(source), relative)
                text = original.decode()
                token_change = None
                if mutation == "native" and relative.endswith("/Log.java"):
                    old_declaration = "public static native boolean isLoggable("
                    new_declaration = "public static boolean isLoggable("
                    changed, count = re.subn(re.escape(old_declaration), new_declaration, text, count=1)
                    self.assertEqual(1, count, relative)
                    text = changed
                    token_change = {"oldToken": "native", "newToken": "(removed)",
                                    "declaration": "Log.isLoggable(String,int)",
                                    "oldDeclaration": old_declaration,
                                    "newDeclaration": new_declaration}
                elif mutation == "synchronized" and relative.endswith("ServiceStartNotAllowedException.java"):
                    old_declaration = "public synchronized Throwable getCause()"
                    new_declaration = "public Throwable getCause()"
                    changed, count = re.subn(re.escape(old_declaration), new_declaration, text, count=1)
                    self.assertEqual(1, count, relative)
                    text = changed
                    token_change = {"oldToken": "synchronized", "newToken": "(removed)",
                                    "declaration": "ServiceStartNotAllowedException.getCause()",
                                    "oldDeclaration": old_declaration,
                                    "newDeclaration": new_declaration}
                elif mutation == "final" and relative.endswith("SpannableStringInternal.java"):
                    old_declaration = "public final int length()"
                    new_declaration = "public int length()"
                    changed, count = re.subn(re.escape(old_declaration), new_declaration, text, count=1)
                    self.assertEqual(1, count, relative)
                    text = changed
                    token_change = {"oldToken": "final", "newToken": "(removed)",
                                    "declaration": "SpannableStringInternal.length()",
                                    "oldDeclaration": old_declaration,
                                    "newDeclaration": new_declaration}
                target = source_root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text)
                if token_change is None:
                    self.assertEqual(original, target.read_bytes(), "unmutated copy changed: " + relative)
                    self.assertEqual(expected_sha, digest(target), "unmutated copy hash: " + relative)
                else:
                    self.assertEqual(
                        original.replace(token_change["oldDeclaration"].encode(),
                                         token_change["newDeclaration"].encode(), 1),
                        target.read_bytes(), "mutation changed more than the selected declaration: " + relative,
                    )
                copies.append({"original": str(source), "isolatedCopy": str(target),
                               "originalSha256": expected_sha, "copySha256": digest(target),
                               "tokenMutation": token_change})
            receipt.setdefault("sourceCopies", {})[case + ("_mutation" if mutation else "_pinned")] = copies
            save()
            return source_root

        def extract(case, mutated=False):
            source_root = copy_sources(case, MUTATION[case] if mutated else None)
            model_path = output / (case + ("-mutation" if mutated else "") + ".json")
            run(case + ("-mutation" if mutated else "-raw-model"), [
                jdk / "bin/java", "-ea", "-Xms128m", "-Xmx1024m", "-Djava.awt.headless=true",
                "-Djava.io.tmpdir=" + str(tmp), "-cp", str(classes) + ":" + str(metalava_root / "lib/*"),
                "RawSourceModifierExtractor", case, source_root, model_path, jdk,
            ])
            model = json.loads(model_path.read_text())
            receipt.setdefault("models", {})[case + ("_mutation" if mutated else "")] = model
            save()
            return model

        def assert_expected_flags(case, row):
            self.assertEqual(EXPECTED_MODEL[case]["flags"], row["flags"], case)

        models = {}
        for case in CASES:
            model = extract(case)
            expected = EXPECTED_MODEL[case]
            self.assertEqual(expected["owner"], model["ownerName"], case)
            self.assertEqual("COMMAND_LINE", model["ownerOrigin"], case)
            self.assertEqual([], model["sourceModelClasspath"], case)
            self.assertEqual([], model["sdkGenerationInputs"], case)
            source_root = Path(model["sourceRoot"]).resolve()
            expected_sources = sorted(str(source_root / relative) for relative in SOURCE_FILES[case])
            self.assertEqual(expected_sources, sorted(source["path"] for source in model["sources"]), case)
            expected_owner_source = str(source_root / {
                "native": "android/util/Log.java",
                "synchronized": "android/app/ServiceStartNotAllowedException.java",
                "inherited_final": "android/text/SpannedString.java",
            }[case])
            self.assertEqual(expected_owner_source, model["ownerSource"], case)
            selected = model["selected"]
            self.assertEqual(expected["method"], selected["name"], case)
            self.assertEqual(expected["descriptor"], selected["descriptor"], case)
            self.assertEqual(expected["return"], selected["return"], case)
            self.assertEqual(expected["parameters"], [item["type"] for item in selected["parameters"]], case)
            self.assertEqual(expected["parameterNames"], [item["name"] for item in selected["parameters"]], case)
            self.assertEqual(expected["containingClass"], selected["containingClass"], case)
            assert_expected_flags(case, selected)
            if case == "inherited_final":
                base_path = (source_root / "android/text/SpannableStringInternal.java").resolve()
                self.assertEqual("SOURCE_PATH", model["baseOrigin"])
                self.assertEqual("package private", model["baseVisibility"])
                self.assertEqual(str(base_path), model["baseSource"])
                self.assertTrue(model["inheritedLookup"]["final"])
                self.assertEqual("SOURCE_PATH", model["inheritedLookup"]["containingOrigin"])
                self.assertEqual(str(base_path), model["inheritedLookup"]["containingSource"])
            else:
                self.assertEqual("COMMAND_LINE", selected["containingOrigin"], case)
            models[case] = model

        # Each one-token mutation runs the same expected-flags assertion and
        # must fail before any corrected stub or candidate JAR is emitted.
        mutation_red = {}
        for case in CASES:
            mutated = extract(case, mutated=True)
            selected = mutated["selected"]
            with self.assertRaises(AssertionError) as failure:
                assert_expected_flags(case, selected)
            mutation_red[case] = {"expectedFlags": EXPECTED_MODEL[case]["flags"],
                                  "actualFlags": selected["flags"],
                                  "assertionFailure": str(failure.exception)}
            self.assertNotEqual(EXPECTED_MODEL[case]["flags"], selected["flags"], case)
        receipt["mutationNegative"] = mutation_red
        self.assertFalse((output / "corrected-candidate.jar").exists())
        save()

        # Model-derived method text is the only selected source change. The raw
        # prologue(s) travel with each emitted declaration and the source
        # copies above remain the exact pinned AOSP inputs.
        def render_method(row, case):
            modifiers = [row["access"]]
            for key in ("static", "final", "synchronized", "native", "abstract"):
                if row[key]:
                    modifiers.append(key)
            parameters = ", ".join(item["type"] + " " + item["name"]
                                    for item in row["parameters"])
            type_parameters = ""
            if row["typeParameters"]:
                rendered = []
                for item in row["typeParameters"]:
                    value = item["name"]
                    if item["bounds"]:
                        value += " extends " + " & ".join(item["bounds"])
                    rendered.append(value)
                type_parameters = "<" + ", ".join(rendered) + "> "
            declaration = " ".join(modifiers) + " " + type_parameters + row["return"] + " "
            declaration += row["name"] + "(" + parameters + ")"
            if row["throws"]:
                declaration += " throws " + ", ".join(row["throws"])
            if row["native"] or row["abstract"]:
                return declaration + ";"
            return declaration + " { throw new RuntimeException(\"Stub!\"); }"

        def source_prologue(relative):
            raw = raw_root / relative
            return raw.read_bytes().split(b"*/", 1)[0] + b"*/\n"

        source_copy_root = output / "corrected-source"
        source_copy_root.mkdir()
        method_fragments = {}
        for case, model in models.items():
            row = model["selected"]
            expected = EXPECTED_MODEL[case]
            self.assertEqual(expected["method"], row["name"], case)
            self.assertEqual(expected["descriptor"], row["descriptor"], case)
            owner_stub_relative = SOURCE_STUB_OWNER[case]
            stub_input = SOURCE_STUBS / owner_stub_relative
            stub_bytes = stub_input.read_bytes()
            if case == "native":
                # Keep the pinned projection's complete parameter annotation
                # syntax. The raw model independently validated the method's
                # owner, descriptor, erased types, names, and native modifier.
                old_declaration = re.compile(
                    rb"(?m)^public static boolean isLoggable\([^\n]*\) \{ throw new RuntimeException\(\"Stub!\"\); \}$"
                )
                matches = old_declaration.findall(stub_bytes)
                self.assertEqual(1, len(matches), case + " pinned selected declaration")
                declaration_body = b' { throw new RuntimeException("Stub!"); }'
                self.assertTrue(matches[0].endswith(declaration_body), case + " pinned method body")
                signature = matches[0][:-len(declaration_body)] + b";"
                declaration = signature.replace(
                    b"public static boolean", b"public static native boolean", 1
                ).decode()
                self.assertIn("@android.annotation.Nullable java.lang.String", declaration)
            else:
                declaration = render_method(row, case)
            headers = [source_prologue(owner_stub_relative)]
            if case == "inherited_final":
                headers.append(source_prologue("android/text/SpannableStringInternal.java"))
            fragment = b"".join(headers) + (
                "// Source-derived declaration; compile-only owner projection.\n" + declaration + "\n"
            ).encode()
            fragment_path = output / "source-fragments" / owner_stub_relative.replace(".java", ".java.inc")
            fragment_path.parent.mkdir(parents=True, exist_ok=True)
            fragment_path.write_bytes(fragment)
            method_fragments[case] = {"path": str(fragment_path), "sha256": digest(fragment_path),
                                      "declaration": declaration,
                                      "sourceOwner": row["containingClass"],
                                      "outputOwner": expected["owner"]}
            if case == "native":
                old = old_declaration
                new = declaration.encode() + b"\n"
            elif case == "inherited_final":
                old = re.compile(rb"(?m)^public int length\(\) \{ throw new RuntimeException\(\"Stub!\"\); \}$")
                new = declaration.encode() + b"\n"
            else:
                old = None
                new = declaration.encode() + b"\n"
            if old is not None:
                changed, count = old.subn(new, stub_bytes)
                self.assertEqual(1, count, case + " selected baseline declaration")
            else:
                # This pinned source projection has one exact blank line after
                # its class brace. Fail closed if its pinned bytes ever change.
                suffix = b"}\n\n"
                self.assertTrue(stub_bytes.endswith(suffix), case + " source stub shape")
                closing_brace = len(stub_bytes) - len(suffix)
                self.assertEqual(b"}", stub_bytes[closing_brace:closing_brace + 1], case)
                changed = stub_bytes[:closing_brace] + b"\n\n" + new + stub_bytes[closing_brace:]
                self.assertTrue(changed.endswith(suffix), case + " original class suffix")
                self.assertEqual(stub_bytes[:closing_brace], changed[:closing_brace], case + " original prefix")
            # Preserve the exact original AOSP prologue(s) in generated source,
            # while all non-selected candidate-stub bytes remain unchanged.
            corrected = b"".join(headers) + changed
            destination = source_copy_root / owner_stub_relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(corrected)
            self.assertEqual(b"".join(headers), corrected[:sum(map(len, headers))], case)
            restored = corrected[sum(map(len, headers)):]
            self.assertEqual(changed, restored, case)
            method_fragments[case]["sourceCopy"] = str(destination)
            method_fragments[case]["sourceCopySha256"] = digest(destination)
            method_fragments[case]["baselineStubSha256"] = digest(stub_input)
        receipt["sourceDerivedDeclarations"] = method_fragments
        save()

        # Compile only the exact pinned annotation sources used by the
        # existing raw-source modifier experiment. Keep these support classes
        # separate from the candidate JAR and place them first only while
        # compiling the isolated owner source copies.
        metalava_source = PROBE / "metalava-r4"
        git_env = {"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "GIT_NO_LAZY_FETCH": "1",
                   "GIT_CONFIG_COUNT": "1", "GIT_CONFIG_KEY_0": "protocol.allow",
                   "GIT_CONFIG_VALUE_0": "never"}
        actual_metalava_commit = subprocess.run(
            ["git", "-C", str(metalava_source), "rev-parse", "HEAD"], env=git_env,
            check=True, capture_output=True, timeout=20,
        ).stdout.decode().strip()
        self.assertEqual(METALAVA_COMMIT, actual_metalava_commit, "pinned Metalava source commit")
        tracked_metalava_root = subprocess.run(
            ["git", "-C", str(metalava_source), "ls-tree", "--name-only", METALAVA_COMMIT],
            env=git_env, check=True, capture_output=True, timeout=20,
        ).stdout.decode().splitlines()
        self.assertNotIn("NOTICE", tracked_metalava_root, "Metalava root NOTICE inventory changed")
        annotation_source_root = output / "annotation-support-source"
        annotation_sources = []
        annotation_inventory = []
        for relative, pins_for_file in ANNOTATION_SUPPORT.items():
            source = metalava_source / relative
            raw = source.read_bytes()
            self.assertEqual(pins_for_file["sha256"], digest(source), relative)
            git_bytes = subprocess.run(
                ["git", "-C", str(metalava_source), "show", METALAVA_COMMIT + ":" + relative],
                env=git_env, check=True, capture_output=True, timeout=20,
            ).stdout
            self.assertEqual(raw, git_bytes, relative)
            actual_blob = subprocess.run(
                ["git", "-C", str(metalava_source), "rev-parse", METALAVA_COMMIT + ":" + relative],
                env=git_env, check=True, capture_output=True, timeout=20,
            ).stdout.decode().strip()
            self.assertEqual(pins_for_file["gitBlob"], actual_blob, relative)
            header = raw.split(b"*/", 1)[0] + b"*/\n"
            self.assertTrue(header.startswith(b"/*\n"), relative)
            self.assertIn(b"Licensed under the Apache License, Version 2.0", header, relative)
            self.assertIn(b"@Retention(CLASS)", raw, relative)
            self.assertIn(b"@Target({METHOD, PARAMETER, FIELD})", raw, relative)
            destination = annotation_source_root / relative.split("/java/", 1)[1]
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(raw)
            self.assertEqual(raw, destination.read_bytes(), relative)
            annotation_sources.append(destination)
            annotation_inventory.append({
                "repository": "platform/tools/metalava",
                "path": relative,
                "commit": METALAVA_COMMIT,
                "gitBlob": actual_blob,
                "sha256": pins_for_file["sha256"],
                "licensePrologueSha256": hashlib.sha256(header).hexdigest(),
                "licensePrologue": "original AOSP Apache-2.0 prologue retained verbatim; no clearance claim",
                "retention": "CLASS",
                "target": ["METHOD", "PARAMETER", "FIELD"],
                "isolatedCopy": str(destination),
                "copyByteEqual": True,
            })
        receipt["annotationSupportInventory"] = annotation_inventory
        receipt["metalavaRootNotice"] = {"commit": METALAVA_COMMIT, "tracked": False}
        save()
        annotation_classes = output / "annotation-support-classes"
        annotation_classes.mkdir()
        run("compile-pinned-annotation-support", [
            jdk / "bin/javac", "-J-Xmx512m", "-source", "8", "-target", "8", "-proc:none",
            "-bootclasspath", BASELINE, "-classpath", "", "-d", annotation_classes,
            *annotation_sources,
        ])
        annotation_class_files = sorted(
            path.relative_to(annotation_classes).as_posix()
            for path in annotation_classes.rglob("*.class")
        )
        self.assertEqual([
            "android/annotation/NonNull.class", "android/annotation/Nullable.class",
        ], annotation_class_files)
        receipt["annotationSupportClasses"] = [
            {"path": relative, "sha256": digest(annotation_classes / relative)}
            for relative in annotation_class_files
        ]
        save()

        compiled = output / "owner-classes"
        compiled.mkdir()
        compiler_flags = ["-J-Xmx1024m", "-source", "8", "-target", "8", "-proc:none",
                          "-classpath", str(annotation_classes) + ":" + str(BASELINE),
                          "-d", str(compiled)]
        receipt["compilerFlags"] = compiler_flags
        run("compile-corrected-owner-stubs", [jdk / "bin/javac", *compiler_flags,
                                               *(source_copy_root / rel for rel in SOURCE_STUB_OWNER.values())])
        class_files = sorted(path.relative_to(compiled).as_posix() for path in compiled.rglob("*.class"))
        expected_class_files = sorted(METHOD_CLASS_ENTRY.values())
        self.assertEqual(expected_class_files, class_files, "unexpected compiler output entry")
        receipt["compiledClassFiles"] = [
            {"path": relative, "sha256": digest(compiled / relative)} for relative in class_files
        ]

        revised_classes = {entry: ClassFile((compiled / entry).read_bytes(), entry)
                           for entry in expected_class_files}
        for case, revised in ((key, revised_classes[entry])
                              for key, entry in METHOD_CLASS_ENTRY.items()):
            original = originals[METHOD_CLASS_ENTRY[case]]
            selected_key = METHOD_KEY[case]
            self.assertEqual(original.flags, revised.flags, case + " owner flags")
            self.assertEqual(original.superclass, revised.superclass, case + " superclass")
            self.assertEqual(original.interfaces, revised.interfaces, case + " interfaces")
            self.assertEqual(original.fields, revised.fields, case + " fields")
            self.assertEqual(original.attributes, revised.attributes, case + " class attributes")
            expected_method_keys = set(original.methods)
            if case == "synchronized":
                expected_method_keys.add(selected_key)
            self.assertEqual(expected_method_keys, set(revised.methods), case + " method inventory")
            for key, value in original.methods.items():
                if key != selected_key:
                    self.assertEqual(value, revised.methods[key], case + " unaffected method " + str(key))
            self.assertEqual(EXPECTED_FLAGS[case], revised.methods[selected_key][0], case + " selected flags")
            if case != "synchronized":
                self.assertEqual(original.methods[selected_key][1], revised.methods[selected_key][1],
                                 case + " selected method attributes")

        candidate = output / "corrected-candidate.jar"
        with zipfile.ZipFile(BASELINE) as original, zipfile.ZipFile(candidate, "x") as revised:
            expected_entries = set(METHOD_CLASS_ENTRY.values())
            for info in original.infolist():
                if info.filename in expected_entries:
                    revised.writestr(info, (compiled / info.filename).read_bytes())
                else:
                    revised.writestr(info, original.read(info.filename))
        with zipfile.ZipFile(BASELINE) as original, zipfile.ZipFile(candidate) as revised:
            self.assertEqual(original.namelist(), revised.namelist())
            changed_entries = [name for name in original.namelist()
                               if original.read(name) != revised.read(name)]
        self.assertEqual(sorted(METHOD_CLASS_ENTRY.values()), sorted(changed_entries))
        receipt["correctedCandidate"] = {"path": str(candidate), "sha256": digest(candidate),
                                         "changedEntries": changed_entries}
        receipt["status"] = "corrected source candidate compiled; caller and SDK-last comparison pending"
        save()

        caller = output / "RawModifierProjectionCaller.java"
        caller.write_text("""final class RawModifierProjectionCaller {
    static boolean log() { return android.util.Log.isLoggable(\"T\", 3); }
    static Throwable cause(android.app.ServiceStartNotAllowedException error) {
        return error.getCause();
    }
    static int length() { return new android.text.SpannedString(\"x\").length(); }
}
""")
        caller_classes = output / "caller-classes"
        caller_classes.mkdir()
        run("caller-without-sdk", [jdk / "bin/javac", "-J-Xmx512m", "-proc:none", "-classpath",
                                   candidate, "-d", caller_classes, caller])
        self.assertEqual(["RawModifierProjectionCaller.class"],
                         sorted(path.name for path in caller_classes.rglob("*.class")))
        receipt["caller"] = {"path": str(caller), "sha256": digest(caller),
                             "classpath": str(candidate), "sdkReferenceOpened": False,
                             "result": "all selected public call shapes compile from corrected candidate only"}
        receipt["status"] = "caller passed without SDK; API reference remains unopened"
        save()

        # The SDK is first opened after model checks, mutation negatives,
        # source recompilation/classfile checks, and caller compilation.
        actual_sdk_sha = digest(SDK)
        self.assertEqual(SDK_SHA256, actual_sdk_sha, "API 36 SDK reference changed")
        selected = {}
        with zipfile.ZipFile(SDK) as reference_archive, zipfile.ZipFile(candidate) as candidate_archive:
            for case, entry in METHOD_CLASS_ENTRY.items():
                key = METHOD_KEY[case]
                reference = ClassFile(reference_archive.read(entry), entry)
                actual = ClassFile(candidate_archive.read(entry), entry)
                self.assertIn(key, reference.methods, case + " SDK method missing")
                self.assertEqual(EXPECTED_FLAGS[case], reference.methods[key][0], case + " SDK flags")
                self.assertEqual(reference.methods[key], actual.methods[key], case + " full selected parity")
                selected[case] = {"entry": entry, "method": key,
                                  "referenceFlags": hex(reference.methods[key][0]),
                                  "candidateFlags": hex(actual.methods[key][0]),
                                  "fullMethodTupleEqual": reference.methods[key] == actual.methods[key]}
        receipt["sdkReference"] = {"path": str(SDK), "sha256": actual_sdk_sha, "openedLast": True}
        receipt["sdkSelectedComparison"] = selected
        receipt["status"] = "three selected source-derived owner methods match pinned API 36 full method tuples; whole API and licensing remain open"
        save()
        print("RAW_SOURCE_MODIFIER_PROJECTION=" + str(output))


if __name__ == "__main__":
    unittest.main()
