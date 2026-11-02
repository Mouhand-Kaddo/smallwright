"""Focused provenance checks for the pinned AAPT2 patch verifier."""

import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import prepare_aapt2_source_candidate as candidate


class SourceCandidateVerificationTest(unittest.TestCase):
    def test_rejects_extra_untracked_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            (root / "unexpected.h").write_text("// unreviewed input\n")
            manifest = Path(directory) / "manifest.json"
            manifest.write_text(json.dumps({
                "wrapper_commit": candidate.WRAPPER_COMMIT,
                "patched_files": {},
            }))
            with patch.object(candidate, "PATCHED_FILES", manifest):
                with self.assertRaisesRegex(ValueError, "Patched source file set"):
                    candidate.verify_files(root, {})

    def test_accepts_hash_pinned_new_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "checkout"
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            contents = b"// pinned compatibility input\n"
            (root / "shim.h").write_bytes(contents)
            manifest = Path(directory) / "manifest.json"
            manifest.write_text(json.dumps({
                "wrapper_commit": candidate.WRAPPER_COMMIT,
                "patched_files": {"shim.h": hashlib.sha256(contents).hexdigest()},
            }))
            with patch.object(candidate, "PATCHED_FILES", manifest):
                candidate.verify_files(root, {})


if __name__ == "__main__":
    unittest.main()
