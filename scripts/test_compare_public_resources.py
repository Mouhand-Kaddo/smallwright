import unittest

from compare_public_resources import compare, parse_public_resources


class PublicResourceComparisonTest(unittest.TestCase):
    def test_includes_staged_public_ids_and_counts_aliases_once(self):
        dump = """Binary APK
Package name=android id=01
  type attr id=01 entryCount=2
    resource 0x01010001 attr/name PUBLIC STAGED_ID=0x01b70001
      () (attr) type=string
    resource 0x01010002 attr/privateName
  type attr id=b7 entryCount=1
    resource 0x01b70001 attr/name PUBLIC STAGED
"""
        names, aliases = parse_public_resources(dump.splitlines())
        self.assertEqual(names, {"attr/name": "0x01010001"})
        self.assertEqual(aliases, 1)

    def test_reports_missing_extra_and_changed_first_id(self):
        reference = {"attr/name": "0x01010001", "attr/old": "0x01010002"}
        candidate = {"attr/name": "0x01010003", "attr/new": "0x01010004"}
        result = compare(reference, candidate)
        self.assertEqual(result["missing"], ["attr/old"])
        self.assertEqual(result["extra"], ["attr/new"])
        self.assertEqual(result["different_ids"], [{
            "name": "attr/name", "reference": "0x01010001", "candidate": "0x01010003"}])

    def test_rejects_non_framework_or_empty_dump(self):
        with self.assertRaisesRegex(ValueError, "android framework"):
            parse_public_resources(["Package name=example id=7f"])
        with self.assertRaisesRegex(ValueError, "android framework"):
            parse_public_resources([])


if __name__ == "__main__":
    unittest.main()
