from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class AlphaSuitePromptsAssetTest(unittest.TestCase):
    def test_android_asset_is_the_frozen_prompt_set(self):
        self.assertEqual(
            (ROOT / "benchmarks/prompts.jsonl").read_bytes(),
            (ROOT / "controller/src/androidTest/assets/alpha/prompts.jsonl").read_bytes(),
        )


if __name__ == "__main__":
    unittest.main()
