#!/usr/bin/env python3
"""Run the artifact-policy instrumentation and enforce its reported result."""

import argparse
from pathlib import Path
import re
import subprocess
import sys

COMPONENT = (
    "dev.smallwright.controller.test/"
    "dev.smallwright.controller.ArtifactPolicyInstrumentation"
)
FIXTURE_INDEX = (
    Path(__file__).resolve().parent.parent
    / "controller/build/generated/artifact-policy-assets/fixtures.tsv"
)


def output_passed(output: str, expected_checks: int) -> bool:
    streams = re.findall(r"^INSTRUMENTATION_RESULT: stream=(.*)$", output, re.MULTILINE)
    codes = re.findall(r"^INSTRUMENTATION_CODE: (-?\d+)$", output, re.MULTILINE)
    return streams == [f"Artifact policy: {expected_checks} checks passed"] and codes == ["-1"]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--serial",
        default="emulator-5554",
        help="emulator serial (defaults to emulator-5554)",
    )
    parser.add_argument(
        "--check-output",
        action="store_true",
        help="check instrumentation text from stdin instead of invoking adb",
    )
    args = parser.parse_args()

    try:
        rows = FIXTURE_INDEX.read_text(encoding="utf-8").splitlines()
        if not rows or any(not row.startswith(("accept\t", "reject\t")) for row in rows):
            raise ValueError("invalid fixture index")
        expected_checks = len(rows)
    except (OSError, ValueError) as error:
        print(f"Could not read generated fixture index: {error}", file=sys.stderr)
        return 2

    if args.check_output:
        return 0 if output_passed(sys.stdin.read(), expected_checks) else 1
    if not re.fullmatch(r"emulator-\d+", args.serial):
        print("Refusing non-emulator serial; use an emulator serial.", file=sys.stderr)
        return 2

    try:
        completed = subprocess.run(
            ["adb", "-s", args.serial, "shell", "am", "instrument", "-r", "-w", COMPONENT],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
    except OSError as error:
        print(f"Could not run adb: {error}", file=sys.stderr)
        return 127

    sys.stdout.write(completed.stdout)
    if completed.returncode != 0:
        print(f"adb exited with status {completed.returncode}", file=sys.stderr)
        return completed.returncode if completed.returncode > 0 else 1
    if not output_passed(completed.stdout, expected_checks):
        print(
            "Instrumentation did not report the expected successful result.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
