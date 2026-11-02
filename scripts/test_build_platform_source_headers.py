#!/usr/bin/env python3
"""Regression tests for the development-only platform header builder."""

import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_platform_source_headers as builder


class HeaderBuilderTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.annotation_sources = self.root / "annotation sources"
        self.annotation_sources.mkdir()
        self.contribution = self.root / "api.txt"
        self.contribution.write_text("// pinned API\n", encoding="utf-8")
        self.manifest = self.root / "manifest.json"
        self._write_manifest()
        self.metalava = self.root / "metalava"
        self.metalava.write_text("#!/bin/sh\n", encoding="utf-8")
        self.metalava.chmod(0o755)
        self.jdk = self.root / "jdk"
        (self.jdk / "bin").mkdir(parents=True)
        javac = self.jdk / "bin" / "javac"
        javac.write_text("", encoding="utf-8")
        javac.chmod(0o755)
        self.output = self.root / "out"
        self.args = builder.BuildArguments(
            manifest=self.manifest,
            metalava=self.metalava,
            jdk=self.jdk,
            annotation_sources=self.annotation_sources,
            output=self.output,
        )

    def _write_manifest(self):
        self.manifest.write_text(json.dumps({"contributions": [{
            "path": self.contribution.name,
            "sha256": hashlib.sha256(self.contribution.read_bytes()).hexdigest(),
        }]}), encoding="utf-8")

    def test_tampered_contribution_fails_before_tools_or_output(self):
        self.contribution.write_text("changed\n", encoding="utf-8")
        with patch.object(builder.subprocess, "Popen") as popen:
            with self.assertRaises(builder.BuildError):
                builder.build(self.args)
        popen.assert_not_called()
        self.assertFalse(self.output.exists())

    def test_tampered_release_policy_fails_before_tools_or_output(self):
        policy = self.root / "api-flags.xml"
        policy.write_bytes(b"abc")
        selected = self.root / "release-filter.json"
        selected.write_text(json.dumps({
            "api_flags": {"path": policy.name, "sha256":
                          "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"},
            "history_manifest": {"path": self.manifest.name, "sha256":
                                 hashlib.sha256(self.manifest.read_bytes()).hexdigest()},
        }))
        policy.write_bytes(b"changed release policy")
        args = builder.BuildArguments(**{**self.args.__dict__, "release_filter": selected})
        with patch.object(builder.subprocess, "Popen") as popen:
            with self.assertRaisesRegex(builder.BuildError, "Release filter hash mismatch"):
                builder.build(args)
        popen.assert_not_called()
        self.assertFalse(self.output.exists())

    def test_failed_stage_keeps_logs_but_never_publishes_jar(self):
        calls = []

        class FakeProcess:
            def __init__(self, command, **kwargs):
                calls.append(command)
                read_fd, write_fd = os.pipe()
                os.write(write_fd, b"bounded output")
                os.close(write_fd)
                self.stdout = os.fdopen(read_fd, "rb", buffering=0)
                self.returncode = 0 if len(calls) == 1 else 17
                if "merge-signatures" in command:
                    combined = Path(command[command.index("--out") + 1])
                    combined.write_text("merged\n")

            def poll(self):
                return self.returncode

            def wait(self, timeout=None):
                return self.returncode

            def kill(self):
                self.returncode = -9

        with patch.object(builder.subprocess, "Popen", side_effect=FakeProcess):
            with self.assertRaises(builder.BuildError):
                builder.build(self.args)
        self.assertFalse((self.output / "headers.jar").exists())
        receipt = json.loads((self.output / "receipt.json").read_text(encoding="utf-8"))
        self.assertEqual("failed", receipt["status"])
        self.assertTrue((self.output / "logs" / "02-main-stubs.log").is_file())
        self.assertEqual(2, len(calls))

    def test_deterministic_archive_is_classes_only_and_private_replaces_public(self):
        public = self.root / "public"
        private = self.root / "private"
        public.mkdir()
        private.mkdir()
        for root, rel, content in (
                (public, "p/Public.class", b"public"),
                (public, "p/Helper0.class", b"public-helper"),
                (public, "ignored.txt", b"ignored"),
                (private, "p/Helper0.class", b"private-helper")):
            path = root / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
        first = self.root / "first.jar"
        second = self.root / "second.jar"
        builder._write_archive(public, private, first)
        builder._write_archive(public, private, second)
        self.assertEqual(first.read_bytes(), second.read_bytes())
        with zipfile.ZipFile(first) as archive:
            self.assertEqual(["p/Helper0.class", "p/Public.class"], archive.namelist())
            self.assertEqual(b"private-helper", archive.read("p/Helper0.class"))
            self.assertTrue(all(info.date_time == (1980, 1, 1, 0, 0, 0)
                                for info in archive.infolist()))

    def test_existing_output_is_refused_without_changes(self):
        existing = self.root / "existing"
        existing.mkdir()
        (existing / "marker").write_text("keep")
        args = builder.BuildArguments(**{**self.args.__dict__, "output": existing})
        with patch.object(builder.subprocess, "Popen") as popen:
            with self.assertRaises(builder.BuildError):
                builder.build(args)
        popen.assert_not_called()
        self.assertEqual("keep", (existing / "marker").read_text())

    def test_timeout_applies_after_child_closes_its_output(self):
        receipt = {"stages": []}
        command = [sys.executable, "-c",
                   "import os,time; os.close(1); os.close(2); time.sleep(1)"]
        with patch.object(builder, "TIMEOUT_SECONDS", 0.1):
            with self.assertRaisesRegex(builder.BuildError, "timed out"):
                builder._run_stage(command, dict(os.environ),
                                   self.root / "timeout.log", receipt, "closed-output")
        self.assertEqual("timeout", receipt["stages"][0]["returncode"])

    def test_reader_error_terminates_real_child(self):
        receipt = {"stages": []}
        children = []
        original_popen = builder.subprocess.Popen
        original_read = os.read

        def launch(*args, **kwargs):
            child = original_popen(*args, **kwargs)
            children.append(child)
            return child

        def read(fd, count):
            if children and fd == children[0].stdout.fileno():
                raise OSError("test reader failure")
            return original_read(fd, count)

        command = [sys.executable, "-c",
                   "import sys,time; print('ready',flush=True); time.sleep(30)"]
        try:
            with patch.object(builder.subprocess, "Popen", side_effect=launch), \
                    patch.object(builder.os, "read", side_effect=read):
                with self.assertRaisesRegex(builder.BuildError, "test reader failure"):
                    builder._run_stage(command, dict(os.environ),
                                       self.root / "reader-error.log", receipt, "reader-error")
            self.assertIsNotNone(children[0].poll())
            self.assertEqual("execution_error", receipt["stages"][0]["returncode"])
        finally:
            for child in children:
                if child.poll() is None:
                    child.kill()
                    child.wait()
                if child.stdout is not None:
                    child.stdout.close()

    def test_real_failed_child_output_is_drained_and_log_is_bounded(self):
        receipt = {"stages": []}
        count = builder.LOG_LIMIT * 3
        command = [sys.executable, "-c",
                   f"import sys; sys.stderr.write('x'*{count}); sys.exit(7)"]
        log = self.root / "bounded.log"
        with self.assertRaisesRegex(builder.BuildError, "exit code 7"):
            builder._run_stage(command, dict(os.environ), log, receipt, "noisy-failure")
        self.assertEqual(count, receipt["stages"][0]["combined_output_bytes"])
        self.assertLess(log.stat().st_size, builder.LOG_LIMIT + 1024)


@unittest.skipUnless(os.environ.get("PLB_HEADER_FILTER_INTEGRATION") == "1",
                     "Opt-in integration needs the pinned local AOSP/Metalava/JDK inputs")
class SourceReleaseFilterIntegrationTest(unittest.TestCase):
    def test_empty_export_policy_removes_future_nfc_class_from_real_source_build(self):
        repo = Path(__file__).resolve().parents[1]
        probe = repo / "toolchains/platform-api36-source-probe"
        with tempfile.TemporaryDirectory(prefix="release-filter-test-", dir=probe) as folder:
            root = Path(folder)
            policy = root / "flags.xml"
            policy.write_text('<config xmlns="http://www.google.com/tools/metalava/config">'
                              '<api-flags/></config>\n')
            history = repo / "toolchain/platform-api34-source-history-inputs.json"
            selection = root / "filter.json"
            selection.write_text(json.dumps({
                "api_flags": {"path": str(policy), "sha256": hashlib.sha256(policy.read_bytes()).hexdigest()},
                "history_manifest": {"path": str(history), "sha256": hashlib.sha256(history.read_bytes()).hexdigest()},
            }))
            artifact = builder.build(builder.BuildArguments(
                manifest=repo / "toolchain/platform-api35-source-history-inputs.json",
                metalava=probe / "out-r4/metalava/metalava/build/install/metalava/bin/metalava",
                jdk=repo / "toolchains/jdk-21.0.12.1+1",
                annotation_sources=probe / "metalava/stub-annotations/src/main/java",
                output=root / "built",
                release_filter=selection,
            ))
            # Actual r15 source marks this newly introduced class with enable_nfc_charging.
            # The unflagged r14 source history has no such class; an empty export policy
            # must not publish it. This is a test policy, not the AP3A release selection.
            with zipfile.ZipFile(artifact) as archive:
                self.assertFalse("android/nfc/WlcListenerDeviceInfo.class" in archive.namelist(),
                                 "The empty policy published a future NFC class")
                self.assertIn("android/app/Activity.class", archive.namelist())
                self.assertIn("java/lang/Object.class", archive.namelist())


if __name__ == "__main__":
    unittest.main()
