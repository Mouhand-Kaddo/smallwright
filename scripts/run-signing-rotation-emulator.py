#!/usr/bin/env python3
"""Verify phone-compiled signing rotation on a clean, dedicated emulator."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


SERIAL = "emulator-5554"
PACKAGE = "dev.smallwright.generated.counter"
CONTROLLER_TEST = (
    "dev.smallwright.controller.test/"
    "dev.smallwright.controller.ArtifactPolicyInstrumentation"
)
ROOT = Path(__file__).resolve().parent.parent
ADB = Path(os.environ["ANDROID_HOME"]) / "platform-tools" / "adb"


def adb(*args: str, timeout: int = 45, check: bool = True) -> str:
    result = subprocess.run(
        [str(ADB), "-s", SERIAL, *args],
        text=True,
        capture_output=True,
        timeout=timeout,
        check=False,
    )
    if check and result.returncode:
        raise RuntimeError(f"ADB {args[0]} failed: {(result.stdout + result.stderr).strip()}")
    return result.stdout.strip()


def require_dedicated_clean_emulator(api: int) -> None:
    expected = {
        "ro.kernel.qemu": "1",
        "ro.build.version.sdk": str(api),
        "ro.product.cpu.abi": "x86_64",
    }
    for property_name, wanted in expected.items():
        if adb("shell", "getprop", property_name) != wanted:
            raise RuntimeError("Refusing a nonmatching emulator")
    if not adb("shell", "getprop", "ro.boot.qemu.avd_name").startswith(
        "smallwright-rotation-"
    ):
        raise RuntimeError("Refusing an emulator outside the dedicated rotation AVDs")
    if adb("shell", "pm", "path", PACKAGE, check=False):
        raise RuntimeError("Generated package already installed; no existing data will be replaced")
    if f"package:{PACKAGE}" in adb(
        "shell", "cmd", "package", "list", "packages", "-u", "--user", "0", PACKAGE
    ).splitlines():
        raise RuntimeError("Generated package has retained settings; refusing this AVD")


def instrument(key: str, value: str, expected: str, timeout: int = 180) -> str:
    output = adb(
        "shell", "am", "instrument", "-r", "-w", "-e", key, value,
        CONTROLLER_TEST, timeout=timeout,
    )
    if output.count("INSTRUMENTATION_CODE: -1") != 1 or expected not in output:
        raise RuntimeError(f"Instrumentation failed: {output}")
    print(output)
    return output


def install_from_emulator_storage(directory: str, version: int) -> None:
    source = f"{directory}/app-v{version}.apk"
    destination = f"/data/local/tmp/{Path(directory).name}-v{version}.apk"
    adb("shell", "cp", source, destination)
    if adb("shell", "pm", "install", "-r", destination) != "Success":
        raise RuntimeError(f"Android rejected version {version} update")


def installed_signers(version: int) -> tuple[str, str]:
    output = instrument("rotationInstalledVersion", str(version),
                        f"Installed rotation: version {version}")
    match = re.search(r"old signer ([0-9a-f]{64}) current signer ([0-9a-f]{64})", output)
    if match is None:
        raise RuntimeError("Installed signer certificate digests were absent")
    return match.groups()


def hierarchy() -> ET.Element:
    for _ in range(12):
        adb("shell", "uiautomator", "dump", "/data/local/tmp/rotation-ui.xml")
        try:
            root = ET.fromstring(adb("shell", "cat", "/data/local/tmp/rotation-ui.xml"))
        except ET.ParseError:
            time.sleep(1)
            continue
        wait = next(
            (node for node in root.iter("node") if node.get("resource-id") == "android:id/aerr_wait"),
            None,
        )
        if wait is not None:
            tap_bounds(wait.get("bounds", ""))
            time.sleep(1)
            continue
        if any(node.get("package") == PACKAGE for node in root.iter("node")):
            return root
        time.sleep(1)
    raise RuntimeError("Generated app UI did not become visible")


def tap_bounds(bounds: str) -> None:
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if match is None:
        raise RuntimeError("UI node has invalid bounds")
    x1, y1, x2, y2 = map(int, match.groups())
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def require_label(expected: str) -> ET.Element:
    adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    for _ in range(10):
        root = hierarchy()
        if any(node.get("text") == expected for node in root.iter("node")):
            print(f"Visible generated app label: {expected}")
            return root
        time.sleep(1)
    raise RuntimeError(f"Generated app did not display {expected!r}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--direct", action="store_true", help="update directly from v1 to v3")
    parser.add_argument("--portable", action="store_true",
                        help="rotate to an encrypted-and-restored disposable software signer")
    parser.add_argument("--product", action="store_true",
                        help="exercise ProjectSigner migration with an encrypted disposable signer store")
    parser.add_argument("--api", type=int, choices=(31, 32, 36), default=36)
    args = parser.parse_args()
    if args.product and args.portable:
        parser.error("--product and --portable are separate rotation modes")
    require_dedicated_clean_emulator(args.api)
    for apk in (
        "worker/build/outputs/apk/debug/worker-debug.apk",
        "controller/build/outputs/apk/debug/controller-debug.apk",
        "controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk",
    ):
        adb("install", "-r", str(ROOT / apk))
    mode = "rotationProductOnly" if args.product else (
        "rotationPortableOnly" if args.portable else "rotationApksOnly")
    expected = "Product migration APKs:" if args.product else (
        "Portable rotation APKs:" if args.portable else "Rotation APKs: three real worker builds")
    output = instrument(mode, "true", expected)
    match = re.search(r"phone paths (\S+)", output)
    if match is None:
        raise RuntimeError("Probe did not report the emulator-local APK directory")
    directory = match.group(1)
    if not directory.startswith(
        "/storage/emulated/0/Android/data/dev.smallwright.controller/files/rotation-apk-probe-"
    ):
        raise RuntimeError("Probe reported an unexpected APK directory")

    install_from_emulator_storage(directory, 1)
    original_old, original_current = installed_signers(1)
    if original_old != original_current:
        raise RuntimeError("Version 1 has inconsistent signer identity")
    if args.product:
        instrument("rotationProductPreflightOnly", "true",
                   "Product migration preflight: absent and wrong old signers rejected")
        instrument("rotationProductSigningOnly", "true",
                   "Product signing preflight: absent and unrelated installed signers rejected")
    first = require_label("Counter: 0")
    button = next((node for node in first.iter("node") if node.get("text") == "INCREMENT"), None)
    if button is None:
        raise RuntimeError("Generated counter has no Increment button")
    tap_bounds(button.get("bounds", ""))
    require_label("Counter: 1")

    rotated_current = None
    if not args.direct:
        install_from_emulator_storage(directory, 2)
        require_label("Rotated: 1")
        old, current = installed_signers(2)
        if old != original_old or current == old:
            raise RuntimeError("Version 2 did not rotate the installed version 1 signer")
        rotated_current = current

    install_from_emulator_storage(directory, 3)
    require_label("Recovered: 1")
    old, current = installed_signers(3)
    if old != original_old or current == old:
        raise RuntimeError("Version 3 lost the installed version 1 signer history")
    if rotated_current is not None and current != rotated_current:
        raise RuntimeError("Version 3 changed the rotated signer")
    print(f"Rotation update passed on API {args.api}; APKs stayed on the emulator.")


if __name__ == "__main__":
    main()
