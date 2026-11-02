#!/usr/bin/env python3
"""Build the trusted ARM64 androidTest JNI reader with the existing pinned NDK."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

NDK_REVISION = "28.2.13676358"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    args = parser.parse_args()
    properties = args.ndk / "source.properties"
    revision = re.findall(r"^Pkg.Revision\s*=\s*(\S+)\s*$", properties.read_text(), re.M)
    if revision != [NDK_REVISION]:
        raise SystemExit("Native test reader requires the pinned NDK " + NDK_REVISION)
    toolchain = args.ndk / "toolchains/llvm/prebuilt/linux-x86_64"
    compiler = toolchain / "bin/aarch64-linux-android31-clang"
    if not compiler.is_file() or not args.source.is_file():
        raise SystemExit("Pinned compiler or test source is missing")
    output = args.output_directory / "arm64-v8a/libkernel_usage_probe.so"
    output.parent.mkdir(parents=True, exist_ok=True)
    flags = ["-std=c11", "-O2", "-fPIC", "-shared", "-Wall", "-Wextra", "-Werror",
             "-Wl,-z,max-page-size=16384", "-Wl,-z,common-page-size=16384",
             "-Wl,--build-id=sha1", "-Wl,-soname,libkernel_usage_probe.so"]
    subprocess.run([str(compiler), *flags, str(args.source), "-o", str(output)], check=True)
    version = subprocess.check_output([str(compiler), "--version"], text=True).splitlines()[0]
    record = {"ndkRevision": NDK_REVISION, "abi": "arm64-v8a", "minimumApi": 31,
              "compilerVersion": version, "flags": flags, "sha256": {}}
    for file in [args.source, properties, toolchain / "sysroot/usr/include/sys/resource.h",
                 toolchain / "sysroot/usr/include/jni.h", args.ndk / "NOTICE",
                 args.ndk / "NOTICE.toolchain", output]:
        record["sha256"][str(file)] = hashlib.sha256(file.read_bytes()).hexdigest()
    record["outputBytes"] = output.stat().st_size
    record_path = args.output_directory.parent / "kernel-usage-probe-build.json"
    record_path.write_text(json.dumps(record, indent=2) + "\n")
    print(json.dumps(record, indent=2))


if __name__ == "__main__":
    main()
