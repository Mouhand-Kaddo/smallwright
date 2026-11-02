#!/usr/bin/env python3
"""Verify fixed compiler JARs and the AAPT2 binary against tracked SHA-256 pins."""

import argparse
import hashlib
import json
import sys
from pathlib import Path


COMPILER_JARS = {"ecj": "ecj-4.12.jar", "r8": "r8-8.13.23.jar"}


def verify(manifest: Path, inputs: Path, aapt2: Path) -> None:
    pins = json.loads(manifest.read_text())
    for compiler, filename in COMPILER_JARS.items():
        check(inputs / filename, pins[compiler]["jar_sha256"])
    check(aapt2, pins["aapt2"]["so_sha256"])


def check(path: Path, expected: str) -> None:
    if path.is_symlink() or not path.is_file():
        raise ValueError(path.name + ": expected a regular file")
    with path.open("rb") as source:
        actual = hashlib.file_digest(source, "sha256").hexdigest()
    if actual != expected:
        raise ValueError(path.name + ": checksum mismatch")


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=root / "toolchain/manifest.json")
    parser.add_argument("--inputs", type=Path, default=root / "toolchain/input")
    parser.add_argument(
        "--aapt2",
        type=Path,
        default=root / "worker/src/main/jniLibs/arm64-v8a/libaapt2_exec.so",
    )
    args = parser.parse_args()
    try:
        verify(args.manifest, args.inputs, args.aapt2)
    except (OSError, ValueError, KeyError, TypeError) as error:
        print("Compiler input verification failed: " + str(error), file=sys.stderr)
        return 1
    print("Verified pinned ECJ, R8 and AAPT2 compiler inputs")
    return 0


if __name__ == "__main__":
    sys.exit(main())
