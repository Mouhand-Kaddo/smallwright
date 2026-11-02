#!/usr/bin/env python3
"""Build a host-only Java header archive from pinned Metalava signatures.

This development helper has no Android SDK inputs and makes no ABI or license
clearance claim. Its optional release filter consumes pinned source history
and a pinned Metalava flag configuration, not SDK history or platform classes.
"""

import argparse
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import subprocess
import sys
import time
import zipfile


SUCCESS_STATUS = "host_headers_only_not_abi_or_license_clearance"
LOG_LIMIT = 64 * 1024
TIMEOUT_SECONDS = 900
JAVA_OPTIONS = "-Xms128m -Xmx1536m"
EXPECTED_HELPERS = 5
LIBCORE_WARNING_PATTERN = "+*:-android.*:+android.icu.*:-dalvik.*"


class BuildError(Exception):
    """Input validation or pipeline failure."""


@dataclass(frozen=True)
class BuildArguments:
    manifest: Path
    metalava: Path
    jdk: Path
    annotation_sources: Path
    output: Path
    release_filter: Path | None = None


def _digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def _validated_contributions(manifest: Path) -> list[tuple[Path, str]]:
    try:
        document = json.loads(manifest.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise BuildError(f"Cannot read signature manifest: {error}") from error
    contributions = document.get("contributions") if isinstance(document, dict) else None
    if not isinstance(contributions, list) or not contributions:
        raise BuildError("Manifest must contain a non-empty contributions array")
    resolved: set[Path] = set()
    checked = []
    for item in contributions:
        if not isinstance(item, dict) or not isinstance(item.get("path"), str):
            raise BuildError("Each contribution must have a path and sha256")
        expected = item.get("sha256")
        if not isinstance(expected, str) or len(expected) != 64 or any(
                char not in "0123456789abcdef" for char in expected):
            raise BuildError("Each contribution requires a lowercase SHA-256")
        path = (manifest.parent / item["path"]).resolve(strict=True)
        if path in resolved:
            raise BuildError(f"Duplicate signature contribution: {item['path']}")
        resolved.add(path)
        if not path.is_file():
            raise BuildError(f"Signature contribution is not a regular file: {path}")
        actual = _digest(path)
        if actual != expected:
            raise BuildError(f"Signature hash mismatch: {path}")
        try:
            path.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as error:
            raise BuildError(f"Signature contribution is not UTF-8 text: {path}") from error
        checked.append((path, actual))
    return checked


def _validated_release_filter(manifest: Path | None) -> dict | None:
    if manifest is None:
        return None
    manifest = manifest.resolve(strict=True)
    try:
        selection = json.loads(manifest.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise BuildError(f"Cannot read release filter manifest: {error}") from error
    if not isinstance(selection, dict):
        raise BuildError("Release filter manifest must be an object")
    checked = {"manifest": str(manifest)}
    for name in ("api_flags", "history_manifest"):
        item = selection.get(name)
        if not isinstance(item, dict) or not isinstance(item.get("path"), str):
            raise BuildError(f"Release filter requires pinned {name}")
        expected = item.get("sha256")
        if not isinstance(expected, str) or len(expected) != 64 or any(
                char not in "0123456789abcdef" for char in expected):
            raise BuildError(f"Release filter requires lowercase SHA-256 for {name}")
        path = (manifest.parent / item["path"]).resolve(strict=True)
        if not path.is_file() or _digest(path) != expected:
            raise BuildError(f"Release filter hash mismatch: {name}")
        checked[name] = {"path": str(path), "sha256": expected}
    checked["history_contributions"] = [
        {"path": str(path), "sha256": digest} for path, digest in
        _validated_contributions(Path(checked["history_manifest"]["path"]))]
    return checked


def _validated_libcore_warning_policy(manifest: Path,
                                     contributions: list[tuple[Path, str]]) -> dict | None:
    document = json.loads(manifest.read_text(encoding="utf-8"))
    if "libcore_warning_policy" not in document:
        return None
    selection = document["libcore_warning_policy"]
    if not isinstance(selection, dict) or not isinstance(selection.get("path"), str):
        raise BuildError("Libcore warning policy requires a pinned source path")
    expected = selection.get("sha256")
    if not isinstance(expected, str) or len(expected) != 64 or any(
            char not in "0123456789abcdef" for char in expected):
        raise BuildError("Libcore warning policy requires lowercase SHA-256")
    policy = (manifest.parent / selection["path"]).resolve(strict=True)
    if not policy.is_file() or _digest(policy) != expected:
        raise BuildError("Libcore warning policy hash mismatch")
    option = "--force-convert-to-warning-nullability-annotations " + LIBCORE_WARNING_PATTERN
    if option not in policy.read_text(encoding="utf-8"):
        raise BuildError("Libcore source does not declare the supported warning policy")
    items = document["contributions"]
    selected = [index for index, item in enumerate(items)
                if item.get("module") == "art.module.public.api"]
    if len(selected) != 1:
        raise BuildError("Libcore warning policy requires exactly one art.module.public.api contribution")
    package_sets = [set(re.findall(r"^package ([\w.]+) \{", path.read_text(encoding="utf-8"), re.M))
                    for path, _ in contributions]
    index = selected[0]
    owned = package_sets[index]
    if not owned:
        raise BuildError("Libcore signature contribution declares no packages")
    other = set().union(*(packages for i, packages in enumerate(package_sets) if i != index))
    shared = owned & other
    if shared:
        raise BuildError("Libcore packages have multiple contribution owners: " + ", ".join(sorted(shared)))

    def in_package(package: str, prefix: str) -> bool:
        return package == prefix or package.startswith(prefix + ".")

    eligible = sorted(package for package in owned
                      if (not in_package(package, "android") or in_package(package, "android.icu"))
                      and not in_package(package, "dalvik"))
    if not eligible:
        raise BuildError("Libcore warning policy selects no contributed packages")
    path, source_digest = contributions[index]
    # Exact selectors scope the upstream pattern to this contribution. Applying
    # its broad positive wildcard to aggregated stubs would affect other modules.
    return {"path": str(policy), "sha256": expected,
            "upstream_pattern": LIBCORE_WARNING_PATTERN,
            "contribution": {"path": str(path), "sha256": source_digest,
                             "module": "art.module.public.api"},
            "module_packages": sorted(owned), "eligible_packages": eligible,
            "package_filter": ":".join("+" + package for package in eligible)}


def _quote_argfile(value: str) -> str:
    """Quote one javac argument using the JDK argument-file grammar."""
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def _javac_argfile(path: Path, arguments: list[str]) -> list[str]:
    if any("\n" in value or "\r" in value for value in arguments):
        raise BuildError("Newlines in javac arguments are unsupported")
    path.write_text("\n".join(_quote_argfile(value) for value in arguments) + "\n",
                    encoding="utf-8")
    return ["@" + str(path)]


def _run_stage(command: list[str], env: dict[str, str], log_path: Path,
               receipt: dict, name: str) -> None:
    retained = bytearray()
    total_bytes = 0
    process = None
    selector = None
    try:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   env=env)
        assert process.stdout is not None
        selector = selectors.DefaultSelector()
        selector.register(process.stdout, selectors.EVENT_READ)
        deadline = time.monotonic() + TIMEOUT_SECONDS
        timed_out = False
        while selector.get_map():
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                timed_out = True
                process.kill()
                break
            for key, _ in selector.select(min(0.2, remaining)):
                chunk = os.read(key.fd, 8192)
                if not chunk:
                    selector.unregister(key.fileobj)
                    continue
                total_bytes += len(chunk)
                if len(retained) < LOG_LIMIT:
                    retained.extend(chunk[:LOG_LIMIT - len(retained)])
        selector.close()
        process.stdout.close()
        try:
            process.wait(timeout=max(0, deadline - time.monotonic()))
        except subprocess.TimeoutExpired:
            timed_out = True
            process.kill()
            process.wait()
        code = "timeout" if timed_out else process.returncode
        receipt["stages"].append({"name": name, "command": command,
                                  "returncode": code, "combined_output_bytes": total_bytes})
        log_path.write_text("COMMAND: " + json.dumps(command) + "\nEXIT: " + str(code) +
                            "\n--- combined output (bounded) ---\n" +
                            retained.decode("utf-8", errors="replace"), encoding="utf-8")
        if timed_out:
            raise BuildError(f"Stage {name} timed out")
        if process.returncode:
            raise BuildError(f"Stage {name} failed with exit code {process.returncode}")
    except OSError as error:
        log_path.write_text("COMMAND: " + json.dumps(command) +
                            "\nEXECUTION ERROR: " + str(error), encoding="utf-8")
        receipt["stages"].append({"name": name, "command": command,
                                  "returncode": "execution_error"})
        raise BuildError(f"Cannot run stage {name}: {error}") from error
    finally:
        if selector is not None:
            selector.close()
        if process is not None:
            if process.poll() is None:
                process.kill()
                process.wait()
            if process.stdout is not None:
                process.stdout.close()


def _class_files(root: Path) -> dict[str, Path]:
    return {path.relative_to(root).as_posix(): path
            for path in root.rglob("*.class") if path.is_file()}


def _write_archive(public_root: Path, private_root: Path, destination: Path) -> None:
    entries = _class_files(public_root)
    entries.update(_class_files(private_root))
    if not entries:
        raise BuildError("Compilation produced no class files")
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, path in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes())


def build(args: BuildArguments) -> Path:
    manifest = args.manifest.resolve(strict=True)
    contributions = _validated_contributions(manifest)
    release_filter = _validated_release_filter(args.release_filter)
    libcore_policy = _validated_libcore_warning_policy(manifest, contributions)
    output = args.output.absolute()
    if output.exists():
        raise BuildError(f"Output directory already exists: {output}")
    metalava = args.metalava.resolve(strict=True)
    jdk = args.jdk.resolve(strict=True)
    annotation_sources = args.annotation_sources.resolve(strict=True)
    javac = jdk / "bin" / "javac"
    if not metalava.is_file() or not os.access(metalava, os.X_OK):
        raise BuildError(f"Metalava executable is not executable: {metalava}")
    if not javac.is_file() or not annotation_sources.is_dir():
        raise BuildError("Expected JDK bin/javac and annotation source directory")

    # Hash validation is complete before this first output creation.
    output.mkdir(parents=True)
    (output / "logs").mkdir()
    for directory in ("stubs", "classes-public", "classes-private"):
        (output / directory).mkdir()
    env = {"JAVA_HOME": str(jdk),
           "PATH": str(jdk / "bin") + os.pathsep + os.defpath,
           "JAVA_OPTS": JAVA_OPTIONS,
           "LANG": "C.UTF-8"}
    temporary_archive = output / "headers.jar.tmp"
    receipt = {
        "status": "running",
        "manifest": str(manifest),
        "contributions": [{"path": str(path), "sha256": digest}
                           for path, digest in contributions],
        "tools": {"metalava": str(metalava), "jdk": str(jdk)},
        "stages": [],
    }
    if release_filter is not None:
        receipt["release_filter"] = release_filter
    warning_arguments = []
    if libcore_policy is not None:
        receipt["libcore_warning_policy"] = libcore_policy
        warning_arguments = ["--force-convert-to-warning-nullability-annotations",
                             libcore_policy["package_filter"]]

    def stage(label: str, command: list[str]) -> None:
        _run_stage(command, env, output / "logs" / f"{len(receipt['stages']) + 1:02d}-{label}.log",
                   receipt, label)

    try:
        combined = output / "combined-api.txt"
        stage("merge-signatures", [str(metalava), "merge-signatures", "--format=2.0",
                                    *(str(path) for path, _ in contributions),
                                    "--out", str(combined)])
        receipt["merged_api"] = {"path": str(combined), "sha256": _digest(combined)}
        stage("main-stubs", [str(metalava), "main", "--api-class-resolution", "api",
                              "--include-annotations", *warning_arguments,
                              "--stubs", str(output / "stubs"),
                              str(combined)])
        transformed = output / "annotations-private"
        stage("make-annotations-package-private", [
            str(metalava), "make-annotations-package-private", str(annotation_sources),
            str(transformed)])
        helper_sources = sorted(path for path in transformed.rglob("*.java") if path.is_file())
        if len(helper_sources) != EXPECTED_HELPERS:
            raise BuildError(f"Expected exactly {EXPECTED_HELPERS} non-SOURCE annotation helpers; "
                             f"Metalava produced {len(helper_sources)}")
        helper_relatives = [path.relative_to(transformed) for path in helper_sources]
        public_helpers = [annotation_sources / relative for relative in helper_relatives]
        missing = [str(path) for path in public_helpers if not path.is_file()]
        if missing:
            raise BuildError("Missing public annotation helper source(s): " + ", ".join(missing))
        receipt["annotation_helpers"] = [
            {"private_source": str(private_source),
             "private_sha256": _digest(private_source),
             "public_source": str(public_source),
             "public_sha256": _digest(public_source)}
            for private_source, public_source in zip(helper_sources, public_helpers)]

        def compile_stubs(stubs: Path, public: Path, private: Path, prefix: str = "") -> None:
            stub_sources = sorted(stubs.rglob("*.java"))
            if not stub_sources:
                raise BuildError("Metalava produced no Java stubs")
            public_args = ["-source", "8", "-target", "8", "-proc:none", "-bootclasspath", "",
                           "-classpath", "", "-d", str(public),
                           *(str(path) for path in stub_sources),
                           *(str(path) for path in public_helpers)]
            stage(prefix + "javac-public", [str(javac), "-J-Xmx1536m", *_javac_argfile(
                output / (prefix + "javac-public.args"), public_args)])
            private_args = ["-source", "8", "-target", "8", "-proc:none", "-bootclasspath",
                            str(public), "-classpath", "", "-d", str(private),
                            *(str(path) for path in helper_sources)]
            stage(prefix + "javac-private", [str(javac), "-J-Xmx1536m", *_javac_argfile(
                output / (prefix + "javac-private.args"), private_args)])

        public = output / "classes-public"
        private = output / "classes-private"
        compile_stubs(output / "stubs", public, private)
        if release_filter is not None:
            # Signature-text codebases are prefiltered in Metalava. Load the actual
            # source-built JAR model so stock flag reversion applies to declarations.
            unfiltered = output / "unfiltered-headers.jar"
            _write_archive(public, private, unfiltered)
            receipt["unfiltered_archive"] = {"path": str(unfiltered), "sha256": _digest(unfiltered)}
            history = output / "previous-source-api.txt"
            stage("merge-release-history", [str(metalava), "merge-signatures", "--format=2.0",
                  *(item["path"] for item in release_filter["history_contributions"]),
                  "--out", str(history)])
            filtered_stubs = output / "filtered-stubs"
            filtered_stubs.mkdir()
            filtered_api = output / "filtered-api.txt"
            # Source-current history is an experiment, not a released-ABI assertion.
            # The separate strict archive comparison remains required for release.
            stage("filter-release-api", [str(metalava), "main", "--include-annotations",
                  *warning_arguments,
                  "--exclude-documentation-from-stubs", "--config-file",
                  release_filter["api_flags"]["path"], "--migrate-nullness", str(combined),
                  "--check-compatibility", "disabled", "--check-compatibility:api:released",
                  str(history), "--api", str(filtered_api), "--stubs", str(filtered_stubs),
                  str(unfiltered)])
            receipt["filtered_api"] = {"path": str(filtered_api), "sha256": _digest(filtered_api)}
            public = output / "filtered-classes-public"
            private = output / "filtered-classes-private"
            public.mkdir()
            private.mkdir()
            compile_stubs(filtered_stubs, public, private, "filtered-")
        archive = output / "headers.jar"
        _write_archive(public, private, temporary_archive)
        temporary_archive.replace(archive)
        receipt["status"] = SUCCESS_STATUS
        receipt["archive"] = {"path": str(archive), "sha256": _digest(archive),
                              "class_entries": sorted(_class_files(public) | _class_files(private))}
        (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n",
                                             encoding="utf-8")
        return archive
    except Exception as error:
        archive = output / "headers.jar"
        if archive.exists():
            archive.unlink()
        temporary_archive = output / "headers.jar.tmp"
        if temporary_archive.exists():
            temporary_archive.unlink()
        receipt["status"] = "failed"
        receipt["error"] = str(error)
        (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n",
                                             encoding="utf-8")
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--metalava", required=True, type=Path)
    parser.add_argument("--jdk", required=True, type=Path)
    parser.add_argument("--annotation-sources", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--release-filter", type=Path,
                        help="Optional manifest pinning source-history inputs and API flag XML")
    options = parser.parse_args()
    try:
        archive = build(BuildArguments(options.manifest, options.metalava, options.jdk,
                                       options.annotation_sources, options.output, options.release_filter))
        print(f"Built host-only source headers: {archive}")
        return 0
    except (BuildError, OSError, ValueError) as error:
        print(f"Platform source header build failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
