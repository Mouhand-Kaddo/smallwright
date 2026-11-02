import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from zipfile import ZipFile

from compare_platform_abi import compare


class PlatformAbiComparisonTest(unittest.TestCase):
    def compile_jar(self, directory, name, api_source):
        root = Path(directory) / name
        source = root / "src" / "probe"
        classes = root / "classes"
        source.mkdir(parents=True)
        classes.mkdir()
        (source / "Api.java").write_text(api_source)
        (source / "Mark.java").write_text(
            "package probe; public @interface Mark { String value(); }"
        )
        javac = Path(os.environ.get("JAVA_HOME", "")) / "bin" / "javac"
        if not javac.is_file():
            javac = "javac"
        subprocess.run([str(javac), "--release", "17", "-d", str(classes),
                        str(source / "Api.java"), str(source / "Mark.java")],
                       check=True, capture_output=True, text=True)
        archive = root / "api.jar"
        with ZipFile(archive, "w") as output:
            for file in classes.rglob("*.class"):
                output.write(file, file.relative_to(classes))
        return archive

    def test_ignores_method_bodies_and_private_methods(self):
        with tempfile.TemporaryDirectory() as directory:
            reference = self.compile_jar(directory, "reference", """
                package probe;
                @Mark("stable") public class Api {
                  public static final int VALUE = 7;
                  public <T> T identity(T value) { return value; }
                  private int implementation() { return 1; }
                }
            """)
            candidate = self.compile_jar(directory, "candidate", """
                package probe;
                @Mark("stable") public class Api {
                  public static final int VALUE = 7;
                  public <T> T identity(T value) { return value; }
                  private int changedInternals() { return 99; }
                }
            """)
            result = compare(reference, candidate)
            self.assertTrue(result["public_api_match"])
            self.assertEqual(result["different_classes"], [])

    def test_reports_constant_method_generic_superclass_and_annotation_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            base = ("package probe; @Mark(\"stable\") public class Api {"
                    " public static final int VALUE = 7;"
                    " public <T> T identity(T value) { return value; } }")
            reference = self.compile_jar(directory, "reference", base)
            changes = {
                "constant": base.replace("VALUE = 7", "VALUE = 8"),
                "method": base.replace("identity(T value)", "renamed(T value)"),
                "generic": base.replace("<T> T identity(T value)",
                                        "Object identity(Object value)"),
                "superclass": base.replace("class Api {",
                                           "class Api extends RuntimeException {"),
                "annotation": base.replace('@Mark("stable")', '@Mark("changed")'),
            }
            for name, source in changes.items():
                with self.subTest(change=name):
                    candidate = self.compile_jar(directory, name, source)
                    result = compare(reference, candidate)
                    self.assertFalse(result["public_api_match"])
                    self.assertEqual(result["different_classes"], ["probe/Api.class"])
                    self.assertEqual(result["missing_classes"], [])

    def test_reports_missing_public_class_and_rejects_duplicate_archive_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            reference = self.compile_jar(directory, "reference",
                                         "package probe; public class Api {}")
            candidate = Path(directory) / "empty.jar"
            with ZipFile(reference) as source, ZipFile(candidate, "w") as output:
                output.writestr("probe/Mark.class", source.read("probe/Mark.class"))
            self.assertEqual(compare(reference, candidate)["missing_classes"],
                             ["probe/Api.class"])
            duplicate = Path(directory) / "duplicate.jar"
            with ZipFile(duplicate, "w") as output:
                output.writestr("probe/Api.class", b"a")
                output.writestr("probe/Api.class", b"b")
            with self.assertRaisesRegex(ValueError, "duplicate ZIP entry"):
                compare(reference, duplicate)


if __name__ == "__main__":
    unittest.main()
