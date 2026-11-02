#!/usr/bin/env python3
"""Bounded raw-source ActivityWindowInfo owner-resolution checkpoint.

The opt-in integration uses the pinned stock Metalava parser and source graph
under toolchains/. It creates no API output and never opens SDK definitions.
"""

import collections
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

from test_network_module_source_headers import digest


ROOT = Path(__file__).resolve().parents[1]
INPUTS = Path(os.environ.get("PLB_ACTIVITY_WINDOW_INFO_INPUT_ROOT", ROOT)).resolve()
PROBE = INPUTS / "toolchains/platform-api36-source-probe"
BASELINE_GRAPH = PROBE / "owner-signature-graph-20261012/experiment-d52b5c8a74c34e419dbcd7a4a2922408"
ACTIVITY_WINDOW_INFO = "ActivityWindowInfo"
EXPECTED_SOURCE_CONTEXT_COUNT = 9
EXPECTED_SOURCE_SHA256 = "7589b07cf9e5229d5803b3dcc410c3fa17db2b10b4e4fc2c06af0021c429886f"


class ActivityWindowInfoBaselineTest(unittest.TestCase):
    """Check the archived measured predecessor, without parser or SDK work."""

    def test_pinned_unresolved_inventory_has_nine_activity_window_info_contexts(self):
        inventory_path = BASELINE_GRAPH / "unresolved-signature-types.json"
        self.assertEqual(
            "05f8fa09942490e85a98e62899d1b5e82b55e197665062ee728ac4cb599c2221",
            digest(inventory_path),
        )
        inventory = json.loads(inventory_path.read_text())
        contexts = unresolved_contexts(inventory, ACTIVITY_WINDOW_INFO)
        self.assertEqual(EXPECTED_SOURCE_CONTEXT_COUNT, len(contexts))
        self.assertEqual(len(contexts), len(set(contexts)))
        self.assertTrue(all(context.startswith("android.app.ActivityThread") for context in contexts))

    def test_pinned_model_schema_and_experiment_paths(self):
        self.assertEqual("experiment-d52b5c8a74c34e419dbcd7a4a2922408",
                         BASELINE_GRAPH.name)
        receipt_path = BASELINE_GRAPH / "receipt.json"
        receipt = json_file(receipt_path)
        self.assertEqual(BASELINE_GRAPH_HASHES["receipt.json"], digest(receipt_path))
        self.assertTrue({"driver", "baseline", "original_sources", "isolated_copies",
                         "source_commits"}.issubset(receipt))
        self.assertNotIn("owner_resolutions", receipt)
        self.assertEqual(BASELINE_GRAPH_HASHES["model-result.json"],
                         digest(BASELINE_GRAPH / "model-result.json"))
        model_path = BASELINE_GRAPH / "model-result.json"
        self.assertTrue(file_contains(model_path, b'"owner_resolutions"'))
        self.assertTrue(file_contains(model_path, b'"generated_models"'))
        self.assertEqual(BASELINE_GRAPH_HASHES["owner-names.txt"],
                         digest(BASELINE_GRAPH / "owner-names.txt"))
        self.assertEqual(BASELINE_GRAPH_HASHES["scan-names.txt"],
                         digest(BASELINE_GRAPH / "scan-names.txt"))
        relative_sources = [item for item in receipt["original_sources"]
                            if not Path(item["path"]).is_absolute()]
        self.assertGreater(len(relative_sources), 0)
        relative_source = next(item for item in relative_sources
                               if "core/java/android/app/ActivityThread.java" in item["path"])
        resolved_source = input_path(relative_source["path"])
        self.assertTrue(resolved_source.is_file())
        self.assertEqual(relative_source["sha256"], digest(resolved_source))

    def test_pinned_hidden_owner_belongs_to_framework_core_source_group(self):
        source = PROBE / "base-r2-sparse" / ACTIVITY_WINDOW_INFO_RELATIVE
        self.assertEqual(EXPECTED_SOURCE_SHA256, digest(source))
        text = source.read_text()
        self.assertIsNotNone(re.search(
            r"/\*\*.*?@hide.*?\*/\s*public\s+final\s+class\s+ActivityWindowInfo\b",
            text, re.S,
        ))
        root = PROBE / "base-r2-sparse"
        core = named_soong_block((root / "core/java/Android.bp").read_text(),
                                 "framework-core-sources")
        self.assertIn('"**/*.java"', core)
        non_updatable = named_soong_block((root / "Android.bp").read_text(),
                                          "framework-non-updatable-sources")
        self.assertIn('":framework-core-sources"', non_updatable)


@unittest.skipUnless(os.environ.get("PLB_ACTIVITY_WINDOW_INFO_INTEGRATION") == "1",
                     "requires pinned source graph, Metalava and JDK; host-only")
class ActivityWindowInfoOwnerResolutionIntegrationTest(unittest.TestCase):
    def test_only_exact_raw_owner_resolves_all_nine_predecessor_contexts(self):
        run_checkpoint(self)


class ActivityWindowInfoQualificationTest(unittest.TestCase):
    def test_only_exact_parameter_type_tokens_are_qualified(self):
        inventory = json_file(BASELINE_GRAPH / "unresolved-signature-types.json")
        contexts = unresolved_contexts(inventory, ACTIVITY_WINDOW_INFO)
        mapped = [qualify_activity_window_info_context(context) for context in contexts]
        self.assertEqual(len(contexts), len(set(mapped)))
        for before, after in zip(contexts, mapped):
            call = before.partition("/call:")[2].partition(")/")[0]
            params = call.partition("(")[2]
            if re.search(r"(?<![\w$.])ActivityWindowInfo(?![\w$])", params):
                self.assertEqual(1, after.count(ACTIVITY_WINDOW_INFO_PUBLIC_NAME))
                self.assertNotIn("/call:get" + ACTIVITY_WINDOW_INFO, after)
                self.assertTrue(after.endswith(before.split(")", 1)[1]))
            else:
                self.assertEqual(before, after)
        self.assertEqual(ACTIVITY_WINDOW_INFO_PUBLIC_NAME,
                         qualify_activity_window_info_type(ACTIVITY_WINDOW_INFO))
        self.assertEqual("getActivityWindowInfo",
                         qualify_activity_window_info_type("getActivityWindowInfo"))
        self.assertEqual("mActivityWindowInfo",
                         qualify_activity_window_info_type("mActivityWindowInfo"))
        self.assertEqual("prefix.ActivityWindowInfo",
                         qualify_activity_window_info_type("prefix.ActivityWindowInfo"))
        qualified_context = (
            "android.app.ActivityThread/call:handle(android.window.ActivityWindowInfo,)/parameter:info"
        )
        self.assertEqual(qualified_context,
                         qualify_activity_window_info_context(qualified_context))

    def test_field_and_getter_contexts_keep_identifier_spelling(self):
        self.assertEqual("android.app.ActivityThread.ActivityClientRecord/field:mActivityWindowInfo",
                         qualify_activity_window_info_context(
                             "android.app.ActivityThread.ActivityClientRecord/field:mActivityWindowInfo"))
        self.assertEqual("android.app.ActivityThread.ActivityClientRecord/call:getActivityWindowInfo()/return",
                         qualify_activity_window_info_context(
                             "android.app.ActivityThread.ActivityClientRecord/call:getActivityWindowInfo()/return"))


FRAMEWORKS_BASE_COMMIT = "99b01a65cc4c104933788b3143285ab6bae65827"
ACTIVITY_WINDOW_INFO_GIT_BLOB = "71c500cd1bfac7c8b42c691beb60652a036edfeb"
METALAVA_COMMIT = "9809e0d70a5b807be3d7070d51f9c4714f2fdcb4"
DRIVER_SHA256 = "d6426283c0ee6eaa71dc97d53fc71dc1956ab795c79b01b8a9edbb1604f38f13"
DRIVER_CLASS_SHA256 = "f2036b489aac7e56e91306b710261ad1f3353451019182106fff5ce32c0fefdc"
BASELINE_JAR_SHA256 = "e36f41ffc131afab50024cdc1d269ba1bd96c06387d3662580c7d7af868fe27a"
NOTICE_SHA256 = "cf4dd7cea5b6bcd2622ddd3529b3c74d16ba2b4db444575bc6f5c33f8a960530"
ACTIVITY_WINDOW_INFO_RELATIVE = "core/java/android/window/ActivityWindowInfo.java"
ACTIVITY_WINDOW_INFO_PUBLIC_NAME = "android.window.ActivityWindowInfo"
BASELINE_GRAPH_HASHES = {
    "receipt.json": "82d2bfe0d876f1498f62ad202133c5a1ae6c304e9b32e72785878f21821abed1",
    "model-result.json": "c4ec561fe1e7c03ee742e1a494bb012e6262c31dc9d519ee9f9f64b85f4e3989",
    "unresolved-signature-types.json": "05f8fa09942490e85a98e62899d1b5e82b55e197665062ee728ac4cb599c2221",
    "sources.txt": "8ce18f223caa55f03bf87e0542a709f27de9fa34844f912116f09fbf714fb050",
    "scan-names.txt": "69a2c948516b39cc5d30ca1be0afaade5ac17d8181385af7df5ce0660a2b2fe5",
    "owner-names.txt": "76deca573e50425555c231bbd9e0ca0f10a30d9c92d59addbb2b95195d67bd5d",
    "actual-model.log": "6551b39102a356c62197b1d9854ff178e6ac4c7c9c1bd4867860a97ec8ca631a",
}


def unresolved_contexts(inventory, owner_name):
    """Return the measured unresolved contexts for one exact symbolic type."""
    matches = [row for row in inventory if row.get("type") == owner_name]
    if len(matches) != 1:
        raise AssertionError(f"Expected one unresolved row for {owner_name}, found {len(matches)}")
    row = matches[0]
    contexts = row.get("contexts")
    if not isinstance(contexts, list) or row.get("contextCount") != len(contexts):
        raise AssertionError(f"Malformed unresolved row for {owner_name}")
    return sorted(contexts)


def json_file(path):
    return json.loads(Path(path).read_text())


def input_path(path):
    """Resolve archived receipt paths against the pinned input checkout."""
    path = Path(path)
    return path if path.is_absolute() else INPUTS / path


def file_contains(path, needle):
    overlap = len(needle) - 1
    with Path(path).open("rb") as source:
        previous = b""
        while True:
            chunk = source.read(65536)
            if not chunk:
                return False
            data = previous + chunk
            if needle in data:
                return True
            previous = data[-overlap:] if overlap else b""


def package_and_name(path):
    source = Path(path).read_text()
    package = re.search(r"^\s*package\s+([\w.]+)\s*;", source, re.M)
    if not package:
        raise AssertionError(f"No package declaration in {path}")
    return package.group(1), Path(path).stem


def unresolved_inventory(result):
    unresolved = collections.defaultdict(list)
    for link in result["generated_signature_links"]:
        if link["resolved"] is None:
            unresolved[link["type"]].append(link["context"])
    return [
        {"type": name, "contexts": sorted(set(contexts)),
         "contextCount": len(set(contexts))}
        for name, contexts in sorted(unresolved.items())
    ]


def diagnostic_counts(log_text):
    counts = collections.Counter()
    for line in log_text.splitlines():
        match = re.search(r"\[([A-Za-z][A-Za-z0-9_]*)\]", line)
        if match:
            counts[match.group(1)] += 1
    return dict(sorted(counts.items()))


def qualify_activity_window_info_type(type_name):
    """Qualify only the exact symbolic owner token, never a member identifier."""
    if type_name == ACTIVITY_WINDOW_INFO:
        return ACTIVITY_WINDOW_INFO_PUBLIC_NAME
    return type_name


def qualify_activity_window_info_context(context):
    """Map only ActivityWindowInfo tokens in method parameter type lists."""
    owner, marker, suffix = context.partition("/call:")
    if not marker:
        return context
    call, close_marker, tail = suffix.partition(")/")
    if not close_marker or "(" not in call:
        return context
    method, params = call.split("(", 1)
    if not params:
        return context
    qualified_params = re.sub(
        r"(?<![\w$.])ActivityWindowInfo(?![\w$])",
        ACTIVITY_WINDOW_INFO_PUBLIC_NAME,
        params,
    )
    if qualified_params == params:
        return context
    return owner + marker + method + "(" + qualified_params + ")/" + tail


def normalized_link_identity(link):
    return (qualify_activity_window_info_context(link["context"]),
            qualify_activity_window_info_type(link["type"]))


def normalized_link_index(result):
    indexed = {}
    for link in result["generated_signature_links"]:
        key = normalized_link_identity(link)
        if key in indexed:
            raise AssertionError(f"Duplicate normalized generated signature link: {key}")
        indexed[key] = link
    return indexed


def named_soong_block(text, name):
    marker = f'name: "{name}"'
    position = text.index(marker)
    start = text.rfind("{", 0, position)
    if start < 0:
        raise AssertionError(f"No enclosing Soong block for {name}")
    depth = 0
    for end in range(start, len(text)):
        if text[end] == "{":
            depth += 1
        elif text[end] == "}":
            depth -= 1
            if depth == 0:
                return text[start:end + 1]
    raise AssertionError(f"Unclosed Soong block for {name}")


def link_accounting(result, source_paths, baseline_jar):
    """Verify parser-reported origins against exact copied sources/classpath."""
    counts = collections.Counter()
    baseline_prefix = str(baseline_jar) + "!/"
    with zipfile.ZipFile(baseline_jar) as archive:
        baseline_entries = set(archive.namelist())
    classpath_origins = {}
    for link in result["generated_signature_links"]:
        resolved = link["resolved"]
        if resolved is None:
            counts["unresolved"] += 1
            continue
        if resolved["qualified_name"] != link["type"]:
            raise AssertionError(("Parser returned a different class identity", link))
        if resolved["origin"] == "CLASS_PATH":
            path = resolved["psi_virtual_file_path"]
            if resolved["psi_virtual_file_protocol"] != "jar" or not path.startswith(baseline_prefix):
                raise AssertionError(("Non-pinned classpath owner", link))
            entry = path[len(baseline_prefix):]
            if entry not in baseline_entries:
                raise AssertionError(("Parser classpath path is not an entry", link))
            counts["baseline_jar"] += 1
            classpath_origins[link["type"]] = entry
        elif resolved["origin"] in {"COMMAND_LINE", "SOURCE_PATH"}:
            path = resolved["file_location"]
            if path not in source_paths:
                raise AssertionError(("Source owner is outside isolated input copies", link))
            if (resolved["psi_virtual_file_protocol"] != "file"
                    or resolved["psi_virtual_file_path"] != path):
                raise AssertionError(("Parser PSI source identity mismatch", link))
            counts["source"] += 1
        else:
            raise AssertionError(("Unexpected owner origin", link))
    if sum(counts.values()) != len(result["generated_signature_links"]):
        raise AssertionError("Resolved/unresolved link accounting is incomplete")
    return dict(sorted(counts.items())), classpath_origins


def run_checkpoint(testcase):
    probe = PROBE
    graph = BASELINE_GRAPH
    baseline_receipt_path = graph / "receipt.json"
    baseline_model_path = graph / "model-result.json"
    baseline_unresolved_path = graph / "unresolved-signature-types.json"
    baseline_receipt = json_file(baseline_receipt_path)

    preserved = {}
    for name, expected in BASELINE_GRAPH_HASHES.items():
        path = graph / name
        testcase.assertEqual(expected, digest(path), str(path))
        preserved[name] = expected

    baseline_model = json_file(baseline_model_path)
    testcase.assertEqual(92, len(baseline_receipt["original_sources"]))
    testcase.assertEqual(92, len(baseline_receipt["isolated_copies"]))
    testcase.assertTrue({"owner_resolutions", "generated_signature_links"}.issubset(
        baseline_model))
    testcase.assertEqual(92, len(baseline_model["source_files"]))
    expected_owner_resolutions = set(baseline_model["owner_resolutions"])
    testcase.assertEqual(set((graph / "owner-names.txt").read_text().splitlines()),
                         expected_owner_resolutions)
    del baseline_model
    source_contexts = unresolved_contexts(
        json_file(baseline_unresolved_path), ACTIVITY_WINDOW_INFO
    )
    testcase.assertEqual(EXPECTED_SOURCE_CONTEXT_COUNT, len(source_contexts))

    base_path = input_path(baseline_receipt["baseline"]["path"])
    testcase.assertEqual(BASELINE_JAR_SHA256, digest(base_path))
    driver_path = input_path(baseline_receipt["driver"]["path"])
    resolved_driver_classes = [input_path(item["path"])
                               for item in baseline_receipt["driver_classes"]]
    for item, class_path in zip(baseline_receipt["driver_classes"], resolved_driver_classes):
        testcase.assertEqual(item["sha256"], digest(class_path), item["path"])
    driver_class_path = resolved_driver_classes[0].parent
    testcase.assertEqual(DRIVER_SHA256, digest(driver_path))
    testcase.assertEqual(DRIVER_CLASS_SHA256,
                         digest(driver_class_path / "OwnerResolutionProbe.class"))
    testcase.assertEqual(FRAMEWORKS_BASE_COMMIT,
                         baseline_receipt["source_commits"]["frameworks/base"])
    testcase.assertEqual("db0de6104d8527114a7ae441b0a9c35f885d2876",
                         baseline_receipt["source_commits"]["tools/platform-compat"])

    raw_root = probe / "base-r2-sparse"
    activity_source = raw_root / ACTIVITY_WINDOW_INFO_RELATIVE
    testcase.assertEqual(EXPECTED_SOURCE_SHA256, digest(activity_source))
    core_bp = raw_root / "core/java/Android.bp"
    root_bp = raw_root / "Android.bp"
    testcase.assertEqual("c4194f6dca0d3c2e535af00a463a6d42e485c00eb28ae0c4aebf2d38882a86b5",
                         digest(core_bp))
    testcase.assertEqual("66e418a71da9eeb993c71938cb89297e401fb5ebdb1c692b4e294b2590d04e79",
                         digest(root_bp))
    core_bp_text = core_bp.read_text()
    source_group = named_soong_block(core_bp_text, "framework-core-sources")
    testcase.assertIn('"**/*.java"', source_group)
    root_bp_text = root_bp.read_text()
    non_updatable_group = named_soong_block(root_bp_text, "framework-non-updatable-sources")
    testcase.assertIn('":framework-core-sources"', non_updatable_group)

    source_text = activity_source.read_text()
    hidden_decl = re.search(
        r'/\*\*.*?@hide.*?\*/\s*public\s+final\s+class\s+ActivityWindowInfo\b',
        source_text, re.S,
    )
    testcase.assertIsNotNone(hidden_decl, "pinned source must retain its @hide class declaration")
    prologue = activity_source.read_bytes().split(b"*/", 1)[0] + b"*/\n"
    testcase.assertIn(b"Licensed under the Apache License, Version 2.0", prologue)

    git_env = {
        "PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "GIT_NO_LAZY_FETCH": "1",
        "GIT_CONFIG_COUNT": "1", "GIT_CONFIG_KEY_0": "protocol.allow",
        "GIT_CONFIG_VALUE_0": "never",
    }
    git_bytes = subprocess.run(
        ["git", "-C", str(raw_root), "show",
         FRAMEWORKS_BASE_COMMIT + ":" + ACTIVITY_WINDOW_INFO_RELATIVE],
        env=git_env, check=True, capture_output=True, timeout=20,
    ).stdout
    testcase.assertEqual(activity_source.read_bytes(), git_bytes)
    actual_blob = subprocess.run(
        ["git", "-C", str(raw_root), "rev-parse",
         FRAMEWORKS_BASE_COMMIT + ":" + ACTIVITY_WINDOW_INFO_RELATIVE],
        env=git_env, check=True, capture_output=True, timeout=20,
    ).stdout.decode().strip()
    testcase.assertEqual(ACTIVITY_WINDOW_INFO_GIT_BLOB, actual_blob)

    notice = probe / "base-r2-sparse/NOTICE"
    testcase.assertEqual(NOTICE_SHA256, digest(notice))
    pins = json.loads((ROOT / "scripts/activity-layout-api/tool-pins.json").read_text())
    for group in pins.values():
        installation = INPUTS / group["installation"]
        for item in group["files"]:
            testcase.assertEqual(item["sha256"], digest(installation / item["path"]),
                                 item["path"])

    for item in baseline_receipt["original_sources"]:
        testcase.assertEqual(item["sha256"], digest(input_path(item["path"])), item["path"])
    for item in baseline_receipt["isolated_copies"]:
        testcase.assertEqual(item["sha256"], digest(input_path(item["path"])), item["path"])
        testcase.assertEqual(item["sha256"], digest(input_path(item["original"])),
                             item["original"])

    jdk = INPUTS / pins["jdk"]["installation"]
    metalava_root = INPUTS / pins["metalava"]["installation"]
    evidence_root = probe / "activity-window-info-20261013"
    evidence_root.mkdir(parents=True, exist_ok=True)
    output = evidence_root / ("experiment-" + uuid.uuid4().hex)
    output.mkdir()
    notice_copy = output / "provenance/base-r2-sparse-NOTICE"
    notice_copy.parent.mkdir(parents=True)
    notice_copy.write_bytes(notice.read_bytes())
    testcase.assertEqual(notice.read_bytes(), notice_copy.read_bytes())

    receipt = {
        "phase": "actual direct raw-source signature graph, no Android compilation or output headers",
        "scope": "fresh 92-source RED then 93-source candidate; direct declared signature links only; excludes bodies, type-variable bounds and annotations",
        "source_variant": baseline_receipt["source_variant"],
        "frameworksBaseCommit": FRAMEWORKS_BASE_COMMIT,
        "metalavaSourceCommitForInterpretation": METALAVA_COMMIT,
        "baselineEvidence": {
            name: {"path": str(graph / name), "sha256": preserved[name]}
            for name in BASELINE_GRAPH_HASHES
        },
        "originalBaselineReceiptSha256": digest(baseline_receipt_path),
        "driver": {**baseline_receipt["driver"], "resolvedPath": str(driver_path)},
        "driverClasses": [
            {**item, "resolvedPath": str(input_path(item["path"]))}
            for item in baseline_receipt["driver_classes"]
        ],
        "baselineJar": {"recordedPath": baseline_receipt["baseline"]["path"],
                        "path": str(base_path), "sha256": BASELINE_JAR_SHA256},
        "archivedOriginalSources": [
            {"recordedPath": item["path"], "resolvedPath": str(input_path(item["path"])),
             "sha256": item["sha256"]}
            for item in baseline_receipt["original_sources"]
        ],
        "archivedIsolatedCopies": [
            {"recordedPath": item["path"], "resolvedPath": str(input_path(item["path"])),
             "recordedOriginalPath": item["original"],
             "resolvedOriginalPath": str(input_path(item["original"])),
             "sha256": item["sha256"]}
            for item in baseline_receipt["isolated_copies"]
        ],
        "tools": baseline_receipt["tools"],
        "toolPinManifest": {"path": str(ROOT / "scripts/activity-layout-api/tool-pins.json"),
                            "sha256": digest(ROOT / "scripts/activity-layout-api/tool-pins.json")},
        "sourceModelClasspath": [str(base_path)],
        "sdkGenerationInputs": [],
        "activityWindowInfoSource": {
            "repository": "platform/frameworks/base",
            "path": ACTIVITY_WINDOW_INFO_RELATIVE,
            "commit": FRAMEWORKS_BASE_COMMIT,
            "gitBlob": ACTIVITY_WINDOW_INFO_GIT_BLOB,
            "sha256": EXPECTED_SOURCE_SHA256,
            "licensePrologueSha256": hashlib.sha256(prologue).hexdigest(),
            "licensePrologue": "Original AOSP Apache-2.0 prologue; no redistribution clearance claim",
            "declaration": "public final class ActivityWindowInfo",
            "sourceDocumentationTag": "@hide",
            "role": "hidden resolver/model dependency; not a public API output owner",
            "coreSourceGroup": {"path": str(core_bp), "sha256": digest(core_bp),
                                "name": "framework-core-sources", "glob": "**/*.java"},
            "frameworkSourceGroup": {"path": str(root_bp), "sha256": digest(root_bp),
                                     "name": "framework-non-updatable-sources",
                                     "includes": "framework-core-sources"},
            "verifiedPinnedGitBlob": True,
            "canonicalCompleteAssembly": False,
        },
        "frameworkNotice": {"path": str(notice), "sha256": NOTICE_SHA256},
        "frameworkNoticeCopy": {"path": str(notice_copy), "sha256": digest(notice_copy),
                                "byteEqualToOriginal": True},
        "childEnvironment": {"JAVA_HOME": str(jdk),
                             "PATH": str(jdk / "bin") + ":/usr/bin:/bin",
                             "LANG": "C.UTF-8"},
        "runs": [],
        "status": "pinned inputs verified; parser stages pending",
    }

    def save():
        (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")

    def run_parser(stage, sources, source_root, scan_names):
        source_list = output / (stage + "-sources.txt")
        source_list.write_text("".join(str(path) + "\n" for path in sources))
        names_file = output / (stage + "-scan-names.txt")
        names_file.write_text("".join(name + "\n" for name in scan_names))
        owner_names = graph / "owner-names.txt"
        tmp = output / (stage + "-tmp")
        tmp.mkdir()
        result_path = output / (stage + "-model-result.json")
        command = [
            jdk / "bin/java", "-ea", "-Xms128m", "-Xmx1024m",
            "-Djava.awt.headless=true", "-Djava.io.tmpdir=" + str(tmp),
            "-classpath", str(driver_class_path) + ":" + str(metalava_root / "lib/*"),
            "OwnerResolutionProbe", str(source_list), str(source_root), str(base_path),
            str(names_file), str(owner_names), str(result_path), str(jdk),
        ]
        run_env = {"JAVA_HOME": str(jdk),
                   "PATH": str(jdk / "bin") + ":/usr/bin:/bin", "LANG": "C.UTF-8"}
        save()
        start = time.monotonic()
        log_path = output / (stage + "-parser.log")
        try:
            completed = subprocess.run(command, cwd=output, env=run_env,
                                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                       timeout=90)
        except subprocess.TimeoutExpired as error:
            elapsed = time.monotonic() - start
            captured = error.stdout or b""
            if isinstance(captured, str):
                captured = captured.encode()
            log_path.write_bytes(captured)
            failed_row = {
                "name": stage,
                "sourceCount": len(sources),
                "command": [str(arg) for arg in command],
                "exitCode": None,
                "timedOut": True,
                "timeoutSeconds": 90,
                "observedHostSeconds": elapsed,
                "parserLog": {"path": str(log_path), "sha256": digest(log_path)},
                "modelResult": ({"path": str(result_path), "sha256": digest(result_path)}
                                if result_path.exists() else None),
            }
            receipt["runs"].append(failed_row)
            receipt["status"] = f"{stage} parser timed out; original evidence retained"
            save()
            raise
        elapsed = time.monotonic() - start
        log_path.write_bytes(completed.stdout)
        run_row = {
            "name": stage,
            "sourceCount": len(sources),
            "sourcesFile": {"path": str(source_list), "sha256": digest(source_list)},
            "scanNamesFile": {"path": str(names_file), "sha256": digest(names_file)},
            "ownerNamesFile": {"path": str(owner_names), "sha256": digest(owner_names)},
            "command": [str(arg) for arg in command],
            "exitCode": completed.returncode,
            "observedHostSeconds": elapsed,
            "parserLog": {"path": str(log_path), "sha256": digest(log_path)},
            "modelResult": ({"path": str(result_path), "sha256": digest(result_path)}
                            if result_path.exists() else None),
        }
        receipt["runs"].append(run_row)
        receipt["status"] = f"{stage} parser exited {completed.returncode}; analyzing retained output"
        save()
        testcase.assertEqual(0, completed.returncode,
                             stage + ": " + completed.stdout.decode(errors="replace")[-4000:])
        result = json_file(result_path)
        if "failure" in result:
            raise AssertionError(f"Parser returned failure model: {result['failure']}")
        testcase.assertEqual(str(base_path.resolve()), result["model_classpath"][0])
        testcase.assertEqual(sorted(str(path.resolve()) for path in sources),
                             sorted(result["source_files"]))
        testcase.assertEqual(92 if stage == "fresh-92-red" else 93,
                             len(result["generated_models"]))
        testcase.assertTrue(all(result["generated_models"].values()))
        testcase.assertEqual(expected_owner_resolutions, set(result["owner_resolutions"]))
        testcase.assertTrue(all(result["owner_resolutions"].values()))
        source_paths = {str(path.resolve()) for path in sources}
        counts, classpath_origins = link_accounting(result, source_paths, base_path)
        inv = unresolved_inventory(result)
        inventory_path = output / (stage + "-unresolved-signature-types.json")
        inventory_path.write_text(json.dumps(inv, indent=2) + "\n")
        log_text = completed.stdout.decode(errors="replace")
        run_row.update({
            "signatureLinkCount": len(result["generated_signature_links"]),
            "linkProvenanceCounts": counts,
            "classPathOrigins": classpath_origins,
            "unresolvedTypeCount": len(inv),
            "unresolvedLinkCount": counts.get("unresolved", 0),
            "unresolvedInventory": {"path": str(inventory_path), "sha256": digest(inventory_path)},
            "diagnosticCounts": diagnostic_counts(log_text),
            "generatedModelNames": sorted(result["generated_models"]),
        })
        run_row["modelResult"] = {"path": str(result_path), "sha256": digest(result_path)}
        receipt["status"] = f"{stage} parser result analyzed"
        save()
        return result, inv, run_row

    # Build new isolated roots from the exact original sources in the frozen receipt.
    originals = []
    for item in baseline_receipt["original_sources"]:
        source = input_path(item["path"])
        testcase.assertEqual(item["sha256"], digest(source), str(source))
        originals.append({"path": source, "recordedPath": item["path"],
                          "sha256": item["sha256"]})
    testcase.assertEqual(92, len(originals))
    testcase.assertEqual(92, len({str(item["path"]) for item in originals}))
    old_names_bytes = (graph / "scan-names.txt").read_bytes()
    old_names = old_names_bytes.decode().splitlines()
    testcase.assertEqual(92, len(old_names))

    def make_isolated_sources(stage, inputs):
        root = output / (stage + "-source")
        root.mkdir()
        records = []
        names = []
        for item in inputs:
            origin = item["path"]
            package, name = package_and_name(origin)
            copied = root.joinpath(*package.split("."), origin.name)
            copied.parent.mkdir(parents=True, exist_ok=True)
            copied.write_bytes(origin.read_bytes())
            testcase.assertEqual(origin.read_bytes(), copied.read_bytes(),
                                 "isolated source bytes differ: " + str(origin))
            testcase.assertEqual(item["sha256"], digest(copied), str(origin))
            records.append({"original": str(origin),
                            "recordedOriginalPath": item.get("recordedPath", str(origin)),
                            "isolatedCopy": str(copied),
                            "sha256": digest(copied), "package": package, "class": name})
            names.append(package + "." + name)
        testcase.assertEqual(len(inputs), len(records))
        testcase.assertEqual(len(inputs), len(set(names)))
        actual_files = {path for path in root.rglob("*.java")}
        testcase.assertEqual({Path(row["isolatedCopy"]) for row in records}, actual_files)
        return root, records, sorted(names)

    red_root, red_copies, red_names = make_isolated_sources("fresh-92-red", originals)
    testcase.assertEqual(old_names, red_names)
    receipt["fresh92Sources"] = red_copies
    save()
    red_result, red_inventory, _red_row = run_parser("fresh-92-red", sorted(
        Path(row["isolatedCopy"]) for row in red_copies
    ), red_root, red_names)
    actual_red_contexts = unresolved_contexts(red_inventory, ACTIVITY_WINDOW_INFO)
    testcase.assertEqual(source_contexts, actual_red_contexts)
    testcase.assertEqual(EXPECTED_SOURCE_CONTEXT_COUNT, len(actual_red_contexts))
    expected_identities = {
        (qualify_activity_window_info_context(context), ACTIVITY_WINDOW_INFO_PUBLIC_NAME)
        for context in source_contexts
    }
    red_target_links = [link for link in red_result["generated_signature_links"]
                        if normalized_link_identity(link) in expected_identities]
    testcase.assertEqual(EXPECTED_SOURCE_CONTEXT_COUNT, len(red_target_links))
    testcase.assertTrue(all(link["resolved"] is None for link in red_target_links))
    testcase.assertEqual(set(source_contexts), {link["context"] for link in red_target_links})
    receipt["fresh92Red"] = {
        "owner": ACTIVITY_WINDOW_INFO_PUBLIC_NAME,
        "unresolvedContexts": actual_red_contexts,
        "allNineRemainUnresolved": True,
    }
    testcase.assertTrue(receipt["fresh92Red"]["allNineRemainUnresolved"])
    save()
    red_index = normalized_link_index(red_result)
    del red_result

    extra = {"path": activity_source, "sha256": EXPECTED_SOURCE_SHA256}
    candidate_inputs = originals + [extra]
    testcase.assertEqual(
        {str(item["path"]) for item in originals} | {str(activity_source)},
        {str(item["path"]) for item in candidate_inputs},
    )
    testcase.assertEqual(1, len(candidate_inputs) - len(originals))
    green_root, green_copies, green_names = make_isolated_sources("candidate-93", candidate_inputs)
    testcase.assertEqual(93, len(green_copies))
    testcase.assertEqual(set(old_names) | {ACTIVITY_WINDOW_INFO_PUBLIC_NAME}, set(green_names))
    for row in green_copies:
        if row["class"] == "ActivityWindowInfo":
            testcase.assertEqual(str(activity_source), row["original"])
            testcase.assertEqual(EXPECTED_SOURCE_SHA256, row["sha256"])
    receipt["candidate93Sources"] = green_copies
    receipt["old92InputsByteIdenticalAcrossRuns"] = all(
        next(row["sha256"] for row in red_copies if row["original"] == prior["original"])
        == next(row["sha256"] for row in green_copies if row["original"] == prior["original"])
        == prior["sha256"]
        for prior in red_copies
    )
    testcase.assertTrue(receipt["old92InputsByteIdenticalAcrossRuns"])
    save()

    candidate_sources = sorted(Path(row["isolatedCopy"]) for row in green_copies)
    green_result, green_inventory, green_row = run_parser(
        "candidate-93", candidate_sources, green_root, green_names
    )
    candidate_links = [link for link in green_result["generated_signature_links"]
                       if normalized_link_identity(link) in expected_identities]
    testcase.assertEqual(EXPECTED_SOURCE_CONTEXT_COUNT, len(candidate_links))
    owner_copy = next(Path(row["isolatedCopy"]) for row in green_copies
                      if row["class"] == "ActivityWindowInfo")
    owner_links = []
    for link in candidate_links:
        resolved = link["resolved"]
        testcase.assertIsNotNone(resolved, link["context"])
        testcase.assertEqual(ACTIVITY_WINDOW_INFO_PUBLIC_NAME,
                             resolved["qualified_name"], link["context"])
        testcase.assertEqual(str(owner_copy.resolve()), resolved["file_location"], link["context"])
        testcase.assertEqual(str(owner_copy.resolve()), resolved["psi_virtual_file_path"],
                             link["context"])
        testcase.assertEqual("file", resolved["psi_virtual_file_protocol"], link["context"])
        testcase.assertIn(resolved["origin"], {"SOURCE_PATH", "COMMAND_LINE"}, link["context"])
        owner_links.append({"rawContext": link["context"], "rawType": link["type"],
                            "normalizedIdentity": list(normalized_link_identity(link)),
                            "resolvedOwner": resolved})
    predecessor_resolutions = []
    red_target_by_identity = {normalized_link_identity(link): link for link in red_target_links}
    candidate_target_by_identity = {
        normalized_link_identity(link): link for link in candidate_links
    }
    testcase.assertEqual(expected_identities, set(red_target_by_identity))
    testcase.assertEqual(expected_identities, set(candidate_target_by_identity))
    for identity in sorted(expected_identities):
        before = red_target_by_identity[identity]
        after = candidate_target_by_identity[identity]
        testcase.assertIsNone(before["resolved"], before["context"])
        testcase.assertIsNotNone(after["resolved"], after["context"])
        predecessor_resolutions.append({
            "rawContextBefore": before["context"],
            "rawTypeBefore": before["type"],
            "rawContextAfter": after["context"],
            "rawTypeAfter": after["type"],
            "normalizedIdentity": list(identity),
            "owner": after["resolved"],
        })

    model_owner = green_result["generated_models"].get(ACTIVITY_WINDOW_INFO_PUBLIC_NAME)
    testcase.assertIsNotNone(model_owner)
    testcase.assertEqual(str(owner_copy.resolve()), model_owner["file_location"])
    testcase.assertEqual(str(owner_copy.resolve()), model_owner["psi_virtual_file_path"])
    testcase.assertIn(model_owner["origin"], {"SOURCE_PATH", "COMMAND_LINE"})
    testcase.assertEqual(ACTIVITY_WINDOW_INFO_PUBLIC_NAME,
                         package_and_name(activity_source)[0] + "." + package_and_name(activity_source)[1])

    green_index = normalized_link_index(green_result)
    transitioned = []
    for key, old_link in red_index.items():
        new_link = green_index.get(key)
        if old_link["resolved"] is None and new_link is not None and new_link["resolved"] is not None:
            transitioned.append({"rawContextBefore": old_link["context"],
                                 "rawTypeBefore": old_link["type"],
                                 "rawContextAfter": new_link["context"],
                                 "rawTypeAfter": new_link["type"],
                                 "normalizedIdentity": list(key),
                                 "resolved": new_link["resolved"]})
    qualification_renames = []
    for key in sorted(set(red_index) & set(green_index)):
        before = red_index[key]
        after = green_index[key]
        if (before["context"], before["type"]) != (after["context"], after["type"]):
            qualification_renames.append({
                "rawContextBefore": before["context"],
                "rawTypeBefore": before["type"],
                "rawContextAfter": after["context"],
                "rawTypeAfter": after["type"],
                "normalizedIdentity": list(key),
            })
    new_links = [green_index[key] for key in sorted(set(green_index) - set(red_index))]
    unresolved_after = [link for link in green_result["generated_signature_links"]
                        if link["resolved"] is None]
    newly_unresolved = [link for link in new_links if link["resolved"] is None]
    newly_unresolved.extend(
        {"rawContextBefore": red_index[key]["context"],
         "rawTypeBefore": red_index[key]["type"],
         "rawContextAfter": new_link["context"],
         "rawTypeAfter": new_link["type"],
         "normalizedIdentity": list(key),
         "resolved": new_link["resolved"]}
        for key, new_link in green_index.items()
        if key in red_index and red_index[key]["resolved"] is not None
        and new_link["resolved"] is None
    )
    removed_links = [red_index[key] for key in sorted(set(red_index) - set(green_index))]
    red_log = (output / "fresh-92-red-parser.log").read_text(errors="replace")
    green_log = (output / "candidate-93-parser.log").read_text(errors="replace")
    red_diags = diagnostic_counts(red_log)
    green_diags = diagnostic_counts(green_log)
    diag_names = sorted(set(red_diags) | set(green_diags))
    diag_deltas = {name: green_diags.get(name, 0) - red_diags.get(name, 0)
                   for name in diag_names}
    red_lines = collections.Counter(
        line for line in red_log.splitlines() if re.search(r"\[[A-Za-z][A-Za-z0-9_]*\]", line)
    )
    green_lines = collections.Counter(
        line for line in green_log.splitlines() if re.search(r"\[[A-Za-z][A-Za-z0-9_]*\]", line)
    )
    receipt["comparison"] = {
        "resolvedFromPrevious92": transitioned,
        "allNinePredecessorContextsResolved": predecessor_resolutions,
        "newlyObservedLinks": new_links,
        "qualificationRenames": qualification_renames,
        "newlyUnresolvedLinks": newly_unresolved,
        "removedPreviousLinks": removed_links,
        "remainingUnresolvedLinkCount": len(unresolved_after),
        "remainingUnresolvedTypeCount": len(green_inventory),
        "diagnostics92": red_diags,
        "diagnostics93": green_diags,
        "diagnosticCountDeltas": diag_deltas,
        "diagnosticLinesAdded": sorted((green_lines - red_lines).elements()),
        "diagnosticLinesRemoved": sorted((red_lines - green_lines).elements()),
        "activityWindowInfoOwnerLinks": owner_links,
        "activityWindowInfoModel": model_owner,
        "sourceDeclaredVisibility": "public final class, documented @hide",
        "publicOutputGenerated": False,
        "sdkGenerationInputs": [],
        "wholeApiComparatorRun": False,
    }
    testcase.assertEqual(len(green_result["generated_signature_links"]),
                         sum(green_row["linkProvenanceCounts"].values()))
    testcase.assertEqual(EXPECTED_SOURCE_CONTEXT_COUNT,
                         len(receipt["comparison"]["activityWindowInfoOwnerLinks"]))
    receipt["status"] = "candidate analysis passed; predecessor immutability verification pending"
    save()

    for row in red_copies + green_copies:
        origin = Path(row["original"])
        copied = Path(row["isolatedCopy"])
        testcase.assertEqual(row["sha256"], digest(origin), str(origin))
        testcase.assertEqual(row["sha256"], digest(copied), str(copied))
        testcase.assertEqual(origin.read_bytes(), copied.read_bytes(), str(copied))
    red_by_origin = {row["original"]: Path(row["isolatedCopy"]) for row in red_copies}
    green_by_origin = {row["original"]: Path(row["isolatedCopy"]) for row in green_copies}
    testcase.assertEqual(set(red_by_origin), set(green_by_origin) - {str(activity_source)})
    for original in red_by_origin:
        testcase.assertEqual(red_by_origin[original].read_bytes(),
                             green_by_origin[original].read_bytes(), original)
    for item in baseline_receipt["original_sources"]:
        testcase.assertEqual(item["sha256"], digest(input_path(item["path"])), item["path"])
    for item in baseline_receipt["isolated_copies"]:
        testcase.assertEqual(item["sha256"], digest(input_path(item["path"])), item["path"])
        testcase.assertEqual(item["sha256"], digest(input_path(item["original"])),
                             item["original"])
    for name, expected in preserved.items():
        testcase.assertEqual(expected, digest(graph / name), name)
    receipt["status"] = (
        "bounded raw source owner resolution passed; all nine predecessor contexts now resolve "
        "to the exact isolated @hide source; broader graph, API output and redistribution remain open"
    )
    receipt["preservedPredecessorEvidenceVerifiedAtEnd"] = True
    save()
