import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


class BenchmarkInstallationOutcomeTest(unittest.TestCase):
    def test_approved_debug_install_has_a_distinct_valid_outcome(self):
        checked = self.validate_outcome("debug_installed")
        self.assertEqual(0, checked.returncode, checked.stderr)
        self.assertEqual("1 valid results\n", checked.stdout)

    def test_observed_android_confirmation_remains_valid(self):
        checked = self.validate_outcome("confirmed")
        self.assertEqual(0, checked.returncode, checked.stderr)

    def test_unrecognized_installation_outcome_stays_invalid(self):
        checked = self.validate_outcome("success")
        self.assertEqual(1, checked.returncode)
        self.assertIn("invalid benchmark result", checked.stderr)

    def validate_outcome(self, outcome):
        observed = ROOT / "benchmarks/results/s23_counter02_20261002_01.jsonl"
        result = json.loads(observed.read_text(encoding="utf-8"))
        result["install_result"] = outcome
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "test-only-result.jsonl"
            path.write_text(json.dumps(result) + "\n", encoding="utf-8")
            checked = subprocess.run(
                [sys.executable, str(ROOT / "scripts/validate_benchmarks.py"), str(path)],
                capture_output=True, text=True, check=False,
            )
        return checked


if __name__ == "__main__":
    unittest.main()
