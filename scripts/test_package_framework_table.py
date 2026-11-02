import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

from package_framework_table import package_table


class FrameworkTablePackageTest(unittest.TestCase):
    def source(self, path, entries):
        with ZipFile(path, "w") as archive:
            for name, data in entries:
                archive.writestr(name, data)

    def test_retains_exact_table_and_manifest_without_asset_payloads(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.apk"
            self.source(source, [("AndroidManifest.xml", b"manifest"),
                                 ("resources.arsc", b"table"),
                                 ("res/drawable/icon.png", b"image"),
                                 ("classes.dex", b"dex")])
            first, second = root / "first.apk", root / "second.apk"
            package_table(source, first)
            package_table(source, second)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            with ZipFile(first) as archive:
                self.assertEqual(archive.namelist(), ["AndroidManifest.xml", "resources.arsc"])
                self.assertEqual(archive.read("AndroidManifest.xml"), b"manifest")
                self.assertEqual(archive.read("resources.arsc"), b"table")
            with self.assertRaises(FileExistsError):
                package_table(source, first)

    def test_rejects_incomplete_or_ambiguous_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, output = root / "source.apk", root / "table.apk"
            self.source(source, [("resources.arsc", b"table")])
            with self.assertRaisesRegex(ValueError, "AndroidManifest.xml"):
                package_table(source, output)
            self.assertFalse(output.exists())
            self.source(source, [("AndroidManifest.xml", b"manifest"),
                                 ("resources.arsc", b"one"), ("resources.arsc", b"two")])
            with self.assertRaisesRegex(ValueError, "duplicate"):
                package_table(source, output)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
