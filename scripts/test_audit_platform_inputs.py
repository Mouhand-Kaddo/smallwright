import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

from audit_platform_inputs import audit


class PlatformInputAuditTest(unittest.TestCase):
    def archive(self, path, entries):
        with ZipFile(path, "w") as output:
            for name, contents in entries.items():
                output.writestr(name, contents)

    def test_accepts_exact_reference_classes_and_framework_resources(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "platform.jar"
            self.archive(archive, {
                "android/app/Activity.class": b"class",
                "android/R.class": b"rclass",
                "resources.arsc": b"table",
                "AndroidManifest.xml": b"manifest",
            })
            result = audit(archive, archive, archive)
            self.assertEqual(result["class_count"], 2)
            self.assertEqual(result["class_names_match"], True)
            self.assertEqual(result["resource_table_matches"], True)
            self.assertEqual(result["status"], "inventory_only")

    def test_detects_missing_class_and_mismatched_resource_table(self):
        with tempfile.TemporaryDirectory() as directory:
            reference = Path(directory) / "reference.jar"
            candidate = Path(directory) / "candidate.jar"
            resources = Path(directory) / "resources.apk"
            self.archive(reference, {
                "android/app/Activity.class": b"class",
                "android/R.class": b"rclass",
                "resources.arsc": b"table",
                "AndroidManifest.xml": b"manifest",
            })
            self.archive(candidate, {"android/app/Activity.class": b"different bytecode"})
            self.archive(resources, {"resources.arsc": b"different table",
                                     "AndroidManifest.xml": b"manifest"})
            result = audit(reference, candidate, resources)
            self.assertEqual(result["missing_classes"], ["android/R.class"])
            self.assertEqual(result["resource_table_matches"], False)
            self.assertEqual(result["class_bytes_match"], False)

    def test_requires_real_resource_table_and_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            reference = Path(directory) / "reference.jar"
            candidate = Path(directory) / "candidate.jar"
            resources = Path(directory) / "resources.apk"
            self.archive(reference, {
                "android/R.class": b"r", "resources.arsc": b"table",
                                     "AndroidManifest.xml": b"manifest"})
            self.archive(candidate, {"android/R.class": b"r"})
            self.archive(resources, {"resources.arsc": b"table"})
            with self.assertRaisesRegex(ValueError, "AndroidManifest.xml"):
                audit(reference, candidate, resources)


if __name__ == "__main__":
    unittest.main()
