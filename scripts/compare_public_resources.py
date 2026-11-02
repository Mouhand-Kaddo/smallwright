#!/usr/bin/env python3
"""Compare public Android framework resource names and first-listed IDs.

This is a symbol preflight. It does not compare values, private entries, or
the complete framework resource table.
"""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys


RESOURCE = re.compile(r"^    resource (0x[0-9a-fA-F]{8}) (\S+) (.*)$")


def parse_public_resources(lines):
    names = {}
    aliases = 0
    seen_framework = False
    for line in lines:
        if line.startswith("Package name="):
            if not line.startswith("Package name=android id="):
                raise ValueError("expected android framework resource package")
            seen_framework = True
        match = RESOURCE.match(line)
        if match and "PUBLIC" in match.group(3).split():
            if not seen_framework:
                raise ValueError("expected android framework resource package")
            resource_id, name = match.group(1), match.group(2)
            if name in names:
                aliases += 1
            else:
                names[name] = resource_id.lower()
    if not seen_framework or not names:
        raise ValueError("expected android framework public resources")
    return names, aliases


def compare(reference, candidate):
    shared = reference.keys() & candidate.keys()
    return {
        "status": "public_names_and_first_ids_only",
        "reference_count": len(reference),
        "candidate_count": len(candidate),
        "missing": sorted(reference.keys() - candidate.keys()),
        "extra": sorted(candidate.keys() - reference.keys()),
        "different_ids": [
            {"name": name, "reference": reference[name], "candidate": candidate[name]}
            for name in sorted(shared) if reference[name] != candidate[name]
        ],
    }


def dump_public_resources(aapt2, path):
    command = subprocess.run(
        [str(aapt2), "dump", "resources", str(path)],
        text=True, capture_output=True, check=False,
    )
    if command.returncode:
        raise ValueError(f"aapt2 dump resources failed for {path}: {command.stderr.strip()}")
    return parse_public_resources(command.stdout.splitlines())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aapt2", type=Path, required=True)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    args = parser.parse_args()
    try:
        reference, reference_aliases = dump_public_resources(args.aapt2, args.reference)
        candidate, candidate_aliases = dump_public_resources(args.aapt2, args.candidate)
        result = compare(reference, candidate)
        result["reference_aliases"] = reference_aliases
        result["candidate_aliases"] = candidate_aliases
        print(json.dumps(result, indent=2))
        return int(bool(result["missing"] or result["extra"] or result["different_ids"]))
    except (OSError, ValueError) as error:
        parser.exit(2, f"public resource comparison failed: {error}\n")


if __name__ == "__main__":
    sys.exit(main())
