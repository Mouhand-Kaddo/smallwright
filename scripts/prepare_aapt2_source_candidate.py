#!/usr/bin/env python3
"""Apply pinned AAPT2 source compatibility patches in an existing local checkout.

This script does not fetch sources, build binaries, or touch product APKs.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys


WRAPPER_COMMIT = "c4edf8539a34a8600538e6642c1ecb170452a79e"
COMPAT_PATCHES = (
    ("wrapper.patch", None),
    ("incremental_delivery.patch", "incremental_delivery"),
    ("logging.patch", "logging"),
)
PATCH_ROOT = Path(__file__).resolve().parents[1] / "toolchain" / "aapt2-source-compat"
PATCHED_FILES = PATCH_ROOT / "patched-files.json"
GIT_ENV = dict(os.environ, GIT_TERMINAL_PROMPT="0", GIT_CONFIG_GLOBAL="/dev/null",
               GIT_CONFIG_NOSYSTEM="1")


def git(repo: Path, *args: str) -> str:
    result = subprocess.run(["git", "-C", str(repo), *args], check=True,
                            text=True, capture_output=True, env=GIT_ENV)
    return result.stdout.rstrip("\n")


def check_checkout(root: Path) -> dict[str, Path]:
    if git(root, "rev-parse", "HEAD") != WRAPPER_COMMIT:
        raise ValueError("AAPT2 wrapper checkout is not pinned commit " + WRAPPER_COMMIT)
    modules = {}
    for line in git(root, "submodule", "status", "--cached").splitlines():
        fields = line[1:].split()
        if len(fields) < 2:
            raise ValueError("Unrecognized submodule status: " + line)
        expected, name = fields[:2]
        repo = root / name
        if git(repo, "rev-parse", "HEAD") != expected:
            raise ValueError("Submodule differs from pinned Gitlink: " + name)
        modules[Path(name).name] = repo
    if len(modules) != 11:
        raise ValueError(f"Expected 11 pinned AOSP submodules, found {len(modules)}")
    return modules


def check_clean(root: Path, modules: dict[str, Path]) -> None:
    if git(root, "status", "--porcelain", "--untracked-files=all",
           "--ignore-submodules=dirty"):
        raise ValueError("Wrapper checkout has tracked edits")
    for name, repo in modules.items():
        if git(repo, "status", "--porcelain", "--untracked-files=all"):
            raise ValueError("Submodule has tracked edits: " + name)


def patch(repo: Path, path: Path, reverse: bool = False, apply: bool = True) -> None:
    direction = ["--reverse"] if reverse else []
    git(repo, "apply", *direction, "--check", str(path))
    if apply:
        git(repo, "apply", *direction, str(path))


def verify_files(root: Path, modules: dict[str, Path]) -> None:
    manifest = json.loads(PATCHED_FILES.read_text())
    if manifest["wrapper_commit"] != WRAPPER_COMMIT:
        raise ValueError("Patched-file manifest uses another wrapper commit")
    expected = manifest["patched_files"]
    changed = set()
    for name, repo in [("", root), *(("vendor/" + name, path)
                                       for name, path in modules.items())]:
        if git(repo, "diff", "--cached", "--name-only"):
            raise ValueError("Staged source changes are not part of the patch set")
        args = ["diff", "--name-only"]
        if not name:
            args.append("--ignore-submodules=dirty")
        changed.update(f"{name}/{path}".lstrip("/") for path in git(repo, *args).splitlines())
        changed.update(f"{name}/{path}".lstrip("/") for path in
                       git(repo, "ls-files", "--others").splitlines())
    if changed != set(expected):
        raise ValueError("Patched source file set differs from the pinned manifest")
    for name, digest in expected.items():
        path = root / name
        if path.is_symlink() or not path.is_file():
            raise ValueError("Patched source is not a regular file: " + name)
        if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError("Patched source hash differs: " + name)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkout", type=Path, help="existing pinned wrapper checkout")
    parser.add_argument("--verify", action="store_true",
                        help="verify patches are already applied, without edits")
    args = parser.parse_args()
    root = args.checkout.resolve(strict=True)
    modules = check_checkout(root)
    wrapper_patches = sorted((root / "patches").glob("*/*.patch"))
    if len(wrapper_patches) != 13:
        raise ValueError(f"Expected 13 pinned wrapper patches, found {len(wrapper_patches)}")
    if args.verify:
        for name, module in COMPAT_PATCHES:
            patch(root if module is None else modules[module], PATCH_ROOT / name,
                  reverse=True, apply=False)
        for path in reversed(wrapper_patches):
            patch(modules[path.parent.name], path, reverse=True, apply=False)
        verify_files(root, modules)
        print("Verified 13 wrapper and 3 compatibility patches in pinned checkout")
        return
    check_clean(root, modules)
    for path in wrapper_patches:
        patch(modules[path.parent.name], path)
    for name, module in COMPAT_PATCHES:
        patch(root if module is None else modules[module], PATCH_ROOT / name)
    git(root, "diff", "--check", "--ignore-submodules=dirty")
    for repo in modules.values():
        git(repo, "diff", "--check")
    verify_files(root, modules)
    print("Applied 13 wrapper and 3 compatibility patches in pinned checkout")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, subprocess.CalledProcessError) as error:
        print(f"AAPT2 source preparation failed: {error}", file=sys.stderr)
        sys.exit(1)
