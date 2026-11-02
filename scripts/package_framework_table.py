#!/usr/bin/env python3
"""Extract a deterministic compile-only framework table and manifest archive.

This transformation preserves bytes; it does not establish license clearance
or SDK compatibility. The CLI aligns the output with a supplied host zipalign.
"""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
from zipfile import BadZipFile, ZipFile, ZipInfo, ZIP_STORED


ENTRIES = ("AndroidManifest.xml", "resources.arsc")
MAX_BYTES = 24 * 1024 * 1024


def package_table(source, output):
    with ZipFile(source) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("source archive has duplicate entries")
        for name in ENTRIES:
            if name not in names:
                raise ValueError(f"source lacks {name}")
            if not 0 < archive.getinfo(name).file_size <= MAX_BYTES:
                raise ValueError(f"invalid size for {name}")
        if sum(archive.getinfo(name).file_size for name in ENTRIES) > MAX_BYTES:
            raise ValueError("framework table and manifest exceed limit")
        contents = {name: archive.read(name) for name in ENTRIES}
    with Path(output).open("xb") as stream, ZipFile(stream, "w", ZIP_STORED) as archive:
        for name in ENTRIES:
            info = ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o644 << 16
            info.compress_type = ZIP_STORED
            archive.writestr(info, contents[name])
    return contents


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--zipalign", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.output.exists():
            raise FileExistsError(f"output already exists: {args.output}")
        with tempfile.TemporaryDirectory(dir=args.output.parent) as directory:
            unaligned = Path(directory) / "table.apk"
            contents = package_table(args.input, unaligned)
            subprocess.run([str(args.zipalign), "4", str(unaligned), str(args.output)],
                           check=True, capture_output=True, text=True)
        with ZipFile(args.output) as archive:
            if archive.namelist() != list(ENTRIES) or any(
                    archive.read(name) != contents[name] for name in ENTRIES):
                raise ValueError("aligned output changed framework entries")
        subprocess.run([str(args.zipalign), "-c", "4", str(args.output)],
                       check=True, capture_output=True, text=True)
        output = args.output.read_bytes()
        print(json.dumps({
            "status": "table_and_manifest_only",
            "bytes": len(output),
            "sha256": hashlib.sha256(output).hexdigest(),
            "entries": {name: {
                "bytes": len(contents[name]),
                "sha256": hashlib.sha256(contents[name]).hexdigest(),
            } for name in ENTRIES},
        }, indent=2))
    except (OSError, ValueError, BadZipFile, subprocess.CalledProcessError) as error:
        parser.exit(2, f"framework table packaging failed: {error}\n")


if __name__ == "__main__":
    main()
