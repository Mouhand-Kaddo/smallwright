import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VALIDATOR = ROOT / "scripts" / "validate_benchmarks.py"


def example():
    return {
        "run_id": "trial_1", "prompt_id": "counter_01", "device_model": "test device",
        "android_version": "16", "controller_apk_sha256": "a" * 64,
        "worker_apk_sha256": "b" * 64, "source_revision_before": 0,
        "source_revision_after": None, "model_id": None, "free_only": None,
        "approved_context_sha256": None, "provider_reported_tokens": None,
        "provider_reported_cost_usd": None, "worker_uid": None,
        "compile_result": "unknown", "artifact_sha256": None,
        "generated_package": None, "generated_version": None,
        "install_result": "not_attempted", "functional_result": "untested",
        "phone_build_elapsed_ms": None, "adb_detached": False,
        "network_disabled_for_build": False, "notes": "",
    }


class ResultsValidationTest(unittest.TestCase):
    def run_validator(self, *rows):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "attempts.jsonl"
            path.write_text("".join(json.dumps(row) + "\n" for row in rows))
            return subprocess.run(["python3", str(VALIDATOR), str(path)],
                                  cwd=ROOT, capture_output=True, text=True)

    def test_valid_attempt_passes(self):
        result = self.run_validator(example())
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("1 valid", result.stdout)

    def test_missing_unknown_and_malformed_fields_fail_without_echoing_values(self):
        missing = example()
        missing.pop("run_id")
        unknown = example()
        unknown["private_value"] = "DO_NOT_PRINT_SECRET_SENTINEL"
        wrong = example()
        wrong["worker_apk_sha256"] = "not-a-hash"
        wrong["source_revision_before"] = True
        result = self.run_validator(missing, unknown, wrong)
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("DO_NOT_PRINT_SECRET_SENTINEL", result.stderr)
        self.assertIn("3 invalid", result.stderr)

    def test_nonfinite_cost_is_rejected(self):
        invalid = example()
        invalid["provider_reported_cost_usd"] = float("inf")
        result = self.run_validator(invalid)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("1 invalid", result.stderr)

    def test_attempt_must_use_a_fixed_prompt_id(self):
        invalid = example()
        invalid["prompt_id"] = "invented_after_results"
        result = self.run_validator(invalid)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("1 invalid", result.stderr)

    def test_missing_results_file_reports_a_safe_error(self):
        with tempfile.TemporaryDirectory() as directory:
            missing = Path(directory) / "missing.jsonl"
            result = subprocess.run(["python3", str(VALIDATOR), str(missing)],
                                    cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertIn("Could not read results file", result.stderr)
        self.assertNotIn("Traceback", result.stderr)


if __name__ == "__main__":
    unittest.main()
