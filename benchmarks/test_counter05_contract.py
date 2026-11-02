import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class Counter05ContractTest(unittest.TestCase):
    def test_android_asset_matches_frozen_prompt_and_checklist(self):
        frozen = next(
            json.loads(line)
            for line in (ROOT / "benchmarks/prompts.jsonl").read_text().splitlines()
            if json.loads(line)["id"] == "counter_05"
        )
        asset = json.loads(
            (ROOT / "controller/src/androidTest/assets/alpha/counter_05.json").read_text()
        )
        self.assertEqual(asset, frozen)


if __name__ == "__main__":
    unittest.main()
