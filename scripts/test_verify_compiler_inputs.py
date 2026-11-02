import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from verify_compiler_inputs import verify


class CompilerInputVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.inputs = self.root / "input"
        self.inputs.mkdir()
        (self.inputs / "ecj-4.12.jar").write_bytes(b"abc")
        (self.inputs / "r8-8.13.23.jar").write_bytes(b"hello")
        self.aapt2 = self.root / "libaapt2_exec.so"
        self.aapt2.write_bytes(b"")
        toolchain = self.root / "toolchain"
        toolchain.mkdir()
        self.manifest = toolchain / "manifest.json"
        self.manifest.write_text(json.dumps({
            "ecj": {"jar_sha256":
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"},
            "r8": {"jar_sha256":
                   "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"},
            "aapt2": {"so_sha256":
                      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"},
        }))

    def test_rejects_replaced_ecj_jar(self):
        (self.inputs / "ecj-4.12.jar").write_bytes(b"changed compiler")

        with self.assertRaisesRegex(ValueError, "ecj-4.12.jar.*checksum mismatch"):
            verify(self.manifest, self.inputs, self.aapt2)

    def test_accepts_both_exact_pinned_inputs(self):
        verify(self.manifest, self.inputs, self.aapt2)

    def test_rejects_replaced_r8_jar(self):
        (self.inputs / "r8-8.13.23.jar").write_bytes(b"changed compiler")

        with self.assertRaisesRegex(ValueError, "r8-8.13.23.jar.*checksum mismatch"):
            verify(self.manifest, self.inputs, self.aapt2)

    def test_rejects_missing_jar_with_a_named_error(self):
        (self.inputs / "r8-8.13.23.jar").unlink()

        with self.assertRaisesRegex(ValueError, "r8-8.13.23.jar.*regular file"):
            verify(self.manifest, self.inputs, self.aapt2)

    def test_rejects_replaced_aapt2(self):
        self.aapt2.write_bytes(b"another build")

        with self.assertRaisesRegex(ValueError, "libaapt2_exec.so.*checksum mismatch"):
            verify(self.manifest, self.inputs, self.aapt2)

    def test_rejects_missing_aapt2_with_a_named_error(self):
        self.aapt2.unlink()

        with self.assertRaisesRegex(ValueError, "libaapt2_exec.so.*regular file"):
            verify(self.manifest, self.inputs, self.aapt2)

    def test_cli_fails_closed_on_a_compiler_checksum_mismatch(self):
        (self.inputs / "ecj-4.12.jar").write_bytes(b"changed compiler")

        result = subprocess.run([
            sys.executable, str(Path(__file__).with_name("verify_compiler_inputs.py")),
            "--manifest", str(self.manifest), "--inputs", str(self.inputs),
            "--aapt2", str(self.aapt2),
        ], capture_output=True, text=True)

        self.assertEqual(result.returncode, 1)
        self.assertIn("ecj-4.12.jar: checksum mismatch", result.stderr)
        self.assertNotIn("Traceback", result.stderr)

    def test_rejects_symlinked_compiler_without_reading_its_target(self):
        compiler = self.inputs / "ecj-4.12.jar"
        compiler.unlink()
        compiler.symlink_to(self.root / "absent-target")

        with self.assertRaisesRegex(ValueError, "ecj-4.12.jar.*regular file"):
            verify(self.manifest, self.inputs, self.aapt2)

    def test_cli_accepts_exact_pinned_inputs(self):
        result = subprocess.run([
            sys.executable, str(Path(__file__).with_name("verify_compiler_inputs.py")),
            "--manifest", str(self.manifest), "--inputs", str(self.inputs),
            "--aapt2", str(self.aapt2),
        ], capture_output=True, text=True)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Verified pinned ECJ, R8 and AAPT2 compiler inputs", result.stdout)


if __name__ == "__main__":
    unittest.main()
