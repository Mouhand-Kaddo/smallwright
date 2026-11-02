#!/usr/bin/env python3
"""Inventory a proposed Android platform input against a pinned SDK reference.

This is a mechanical preflight, not an API-signature comparison or license grant.
"""

import argparse
import hashlib
import json
from pathlib import Path
import sys
from zipfile import BadZipFile, ZipFile


def sha256(contents):
    return hashlib.sha256(contents).hexdigest()


def archive_entries(path):
    with ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError(f"duplicate ZIP entry in {path}")
        return {name: archive.read(name) for name in names if not name.endswith("/")}


def audit(reference_path, api_path, resources_path):
    reference = archive_entries(reference_path)
    api = archive_entries(api_path)
    resources = archive_entries(resources_path)
    for required in ("resources.arsc", "AndroidManifest.xml"):
        if required not in reference:
            raise ValueError(f"reference lacks {required}")
        if required not in resources:
            raise ValueError(f"resource input lacks {required}")
    expected = {name for name in reference if name.endswith(".class")}
    actual = {name for name in api if name.endswith(".class")}
    if not expected:
        raise ValueError("reference contains no classes")
    common = expected & actual
    return {
        "status": "inventory_only",
        "reference_sha256": sha256(Path(reference_path).read_bytes()),
        "api_sha256": sha256(Path(api_path).read_bytes()),
        "resources_sha256": sha256(Path(resources_path).read_bytes()),
        "class_count": len(actual),
        "reference_class_count": len(expected),
        "class_names_match": expected == actual,
        "class_bytes_match": expected == actual and all(
            reference[name] == api[name] for name in common),
        "missing_classes": sorted(expected - actual),
        "extra_classes": sorted(actual - expected),
        "resource_table_sha256": sha256(resources["resources.arsc"]),
        "reference_resource_table_sha256": sha256(reference["resources.arsc"]),
        "resource_table_matches": resources["resources.arsc"] == reference["resources.arsc"],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--api", type=Path, required=True)
    parser.add_argument("--resources", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = audit(args.reference, args.api, args.resources)
        print(json.dumps(result, indent=2))
        if not result["class_names_match"] or not result["resource_table_matches"]:
            return 1
    except (OSError, BadZipFile, ValueError) as error:
        parser.exit(2, f"platform input audit failed: {error}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
