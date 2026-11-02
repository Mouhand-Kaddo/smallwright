#!/usr/bin/env python3
"""Build authored ARM64 launcher or test probe with pinned NDK r28c; no target builds."""
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
    parser.add_argument("--kind", choices=["launcher", "probe"], required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    props = args.ndk / "source.properties"
    if re.findall(r"^Pkg.Revision\s*=\s*(\S+)\s*$", props.read_text(), re.M) != [NDK_REVISION]:
        raise SystemExit("Child process tools require pinned NDK " + NDK_REVISION)
    toolchain = args.ndk / "toolchains/llvm/prebuilt/linux-x86_64"
    compilers = {"arm64-v8a": toolchain / "bin/aarch64-linux-android31-clang",
                 "x86_64": toolchain / "bin/x86_64-linux-android31-clang"}
    header = root / "worker/src/main/c/child_process_policy.h"
    source = root / ("worker/src/main/c/aapt2_guard_exec.c" if args.kind == "launcher"
                     else "worker/src/androidTest/c/child_policy_probe.c")
    name = "libaapt2_guard_exec.so" if args.kind == "launcher" else "libchild_policy_probe_exec.so"
    outputs = {abi: args.output_directory / abi / name for abi in compilers}
    flags = ["-D_GNU_SOURCE", "-std=c11", "-O2", "-fPIE", "-pie", "-Wall", "-Wextra", "-Werror",
             "-Wl,-z,max-page-size=16384", "-Wl,-z,common-page-size=16384", "-Wl,--build-id=sha1"]
    dependencies = []
    for abi, compiler in compilers.items():
        outputs[abi].parent.mkdir(parents=True, exist_ok=True)
        depfile = outputs[abi].with_suffix(".d")
        subprocess.run([str(compiler), *flags, "-MD", "-MF", str(depfile), str(source), "-o", str(outputs[abi])], check=True)
        import shlex
        deptext = depfile.read_text().replace("\\\n", " ")
        dependencies += [Path(p) for p in shlex.split(deptext.split(":", 1)[1])]

    files = [source, header, props, args.ndk / "NOTICE", args.ndk / "NOTICE.toolchain",
             toolchain / "bin/clang", toolchain / "bin/ld.lld", *compilers.values(), *outputs.values(), *dependencies]
    files += [toolchain / ("sysroot/usr/include/" + value) for value in
              ["linux/seccomp.h", "linux/filter.h", "linux/audit.h", "linux/sched.h", "sys/prctl.h", "sys/syscall.h"]]
    def digest(path):
        value = hashlib.sha256()
        with path.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""): value.update(block)
        return value.hexdigest()
    record = {"kind": args.kind, "ndkRevision": NDK_REVISION, "abis": list(compilers), "minimumApi": 31,
              "compilerVersion": subprocess.check_output([str(compilers["arm64-v8a"]), "--version"], text=True).splitlines()[0],
              "flags": flags, "outputBytes": {abi: path.stat().st_size for abi, path in outputs.items()},
              "sha256": {str(p): digest(p) for p in files}}
    path = args.output_directory.parent / (args.kind + "-build.json")
    path.write_text(json.dumps(record, indent=2) + "\n")
    print(json.dumps(record, indent=2))

if __name__ == "__main__":
    main()
