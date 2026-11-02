import os
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
TABLE = ROOT / "controller/src/main/java/dev/smallwright/controller/EcjProblemNames.java"


class EcjProblemNamesTest(unittest.TestCase):
    def test_every_entry_matches_the_pinned_compiler(self):
        javap = Path(os.environ.get("JAVA_HOME", "")) / "bin" / "javap"
        output = subprocess.run(
            [str(javap) if javap.is_file() else "javap", "-cp",
             str(ROOT / "toolchain/input/ecj-4.12.jar"), "-constants",
             "org.eclipse.jdt.core.compiler.IProblem"],
            check=True, capture_output=True, text=True).stdout
        constants = dict(re.findall(r"int (\w+) = (-?\d+);", output))
        entries = re.findall(r'Map\.entry\((-?\d+), "(\w+)"\)', TABLE.read_text())
        self.assertGreater(len(entries), 40)
        for value, name in entries:
            self.assertEqual(constants.get(name), value, name)


if __name__ == "__main__":
    unittest.main()
