import hashlib
import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
PROMPTS = ROOT / "benchmarks/prompts.jsonl"
ASSET = ROOT / "controller/src/androidTest/assets/alpha/counter_04.json"
REQUEST_SHA256 = "c2dfa3094bc90bf3142eeb540888fc2aff30c76f36c428eb0610b1acc3028a6f"


class Counter04ContractTest(unittest.TestCase):
    def test_android_asset_matches_the_frozen_prompt_entry(self):
        prompt = next(
            json.loads(line)
            for line in PROMPTS.read_text(encoding="utf-8").splitlines()
            if json.loads(line).get("id") == "counter_04"
        )
        asset = json.loads(ASSET.read_text(encoding="utf-8"))

        self.assertEqual(prompt, asset)
        self.assertEqual("supported", prompt["kind"])
        self.assertEqual(REQUEST_SHA256, hashlib.sha256(prompt["request"].encode()).hexdigest())


if __name__ == "__main__":
    unittest.main()
