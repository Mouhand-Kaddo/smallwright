import importlib.util
import unittest
from pathlib import Path


spec = importlib.util.spec_from_file_location(
    "debt_phone_runner", Path(__file__).with_name("run-tech-debt-phone-tests.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class PhoneProbeResultTest(unittest.TestCase):
    def test_accepts_verified_failure_handling_in_successful_completion_probe(self):
        output = ("INSTRUMENTATION_RESULT: stream=Generation completion: "
                  "real receipt write failure stays FAILED\nINSTRUMENTATION_CODE: -1\n")
        self.assertTrue(runner.instrumentation_passed(0, output, "Generation completion:"))

    def test_rejects_actual_controller_and_worker_failure_streams(self):
        for failure in ["Artifact policy FAILED:", "Worker confinement FAILED:"]:
            with self.subTest(failure=failure):
                output = ("INSTRUMENTATION_RESULT: stream=Generation completion: "
                          + failure + " assertion\nINSTRUMENTATION_CODE: -1\n")
                self.assertFalse(runner.instrumentation_passed(0, output, "Generation completion:"))

    def test_requires_success_code_expected_probe_and_successful_adb(self):
        for returncode, output in [
                (0, "Generation completion:\nINSTRUMENTATION_CODE: 0\n"),
                (0, "Other probe:\nINSTRUMENTATION_CODE: -1\n"),
                (1, "Generation completion:\nINSTRUMENTATION_CODE: -1\n")]:
            with self.subTest(returncode=returncode, output=output):
                self.assertFalse(runner.instrumentation_passed(returncode, output, "Generation completion:"))


if __name__ == "__main__":
    unittest.main()
