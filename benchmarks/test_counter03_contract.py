import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class Counter03ContractTest(unittest.TestCase):
    def test_android_asset_matches_frozen_prompt_and_checklist(self):
        frozen_rows = [
            json.loads(line)
            for line in (ROOT / "benchmarks/prompts.jsonl").read_text().splitlines()
        ]
        frozen = next(row for row in frozen_rows if row["id"] == "counter_03")
        asset = json.loads(
            (ROOT / "controller/src/androidTest/assets/alpha/counter_03.json").read_text()
        )
        self.assertEqual(asset, frozen)


if __name__ == "__main__":
    unittest.main()
