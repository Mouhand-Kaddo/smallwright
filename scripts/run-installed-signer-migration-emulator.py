#!/usr/bin/env python3
"""Exercise ProjectSigner migration after v1 is installed on a fresh emulator."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parent.parent
ADB = Path(os.environ["ANDROID_HOME"]) / "platform-tools" / "adb"
SERIAL = "emulator-5554"
PACKAGE = "dev.smallwright.generated.counter"
TEST = "dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation"


def adb(*args: str, timeout: int = 45, check: bool = True) -> str:
    result = subprocess.run(
        [str(ADB), "-s", SERIAL, *args], text=True, capture_output=True,
        timeout=timeout, check=False,
    )
    if check and result.returncode:
        raise RuntimeError(f"ADB {args[0]} failed: {(result.stdout + result.stderr).strip()}")
    return result.stdout.strip()


def instrument(key: str, value: str, expected: str) -> str:
    output = adb("shell", "am", "instrument", "-r", "-w", "-e", key, value,
                 TEST, timeout=180)
    if output.count("INSTRUMENTATION_CODE: -1") != 1 or expected not in output:
        raise RuntimeError(f"Instrumentation failed: {output}")
    print(output)
    return output


def require_fresh_emulator(api: int) -> None:
    expected = {
        "ro.kernel.qemu": "1",
        "ro.build.version.sdk": str(api),
        "ro.product.cpu.abi": "x86_64",
    }
    for name, value in expected.items():
        if adb("shell", "getprop", name) != value:
            raise RuntimeError("Refusing a nonmatching emulator")
    if not adb("shell", "getprop", "ro.boot.qemu.avd_name").startswith(
        "smallwright-rotation-installed-"
    ):
        raise RuntimeError("Refusing an emulator outside the dedicated installed-migration AVDs")
    if adb("shell", "pm", "path", PACKAGE, check=False):
        raise RuntimeError("Generated app already installed; refusing to replace its data")
    if f"package:{PACKAGE}" in adb(
        "shell", "cmd", "package", "list", "packages", "-u", "--user", "0", PACKAGE
    ).splitlines():
        raise RuntimeError("Generated app has a retained package setting; refusing this AVD")


def install(directory: str, version: int) -> None:
    source = f"{directory}/app-v{version}.apk"
    destination = f"/data/local/tmp/{Path(directory).name}-v{version}.apk"
    adb("shell", "cp", source, destination)
    if adb("shell", "pm", "install", "-r", destination) != "Success":
        raise RuntimeError(f"Android rejected version {version}")


def signers(version: int) -> tuple[str, str]:
    output = instrument("rotationInstalledVersion", str(version),
                        f"Installed rotation: version {version}")
    match = re.search(r"old signer ([0-9a-f]{64}) current signer ([0-9a-f]{64})", output)
    if match is None:
        raise RuntimeError("Installed signer digests were absent")
    return match.groups()


def tap(bounds: str) -> None:
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if match is None:
        raise RuntimeError("Invalid UI bounds")
    x1, y1, x2, y2 = map(int, match.groups())
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def require_label(expected: str) -> ET.Element:
    adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    for _ in range(12):
        adb("shell", "uiautomator", "dump", "/data/local/tmp/rotation-installed-ui.xml")
        try:
            root = ET.fromstring(adb("shell", "cat", "/data/local/tmp/rotation-installed-ui.xml"))
        except ET.ParseError:
            time.sleep(1)
            continue
        wait = next(
            (node for node in root.iter("node") if node.get("resource-id") == "android:id/aerr_wait"),
            None,
        )
        if wait is not None:
            tap(wait.get("bounds", ""))
            time.sleep(1)
            continue
        if any(node.get("text") == expected for node in root.iter("node")):
            print(f"Visible generated app label: {expected}")
            return root
        time.sleep(1)
    raise RuntimeError(f"Generated app did not display {expected!r}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api", type=int, choices=(31, 36), required=True)
    args = parser.parse_args()
    require_fresh_emulator(args.api)
    for apk in (
        "worker/build/outputs/apk/debug/worker-debug.apk",
        "controller/build/outputs/apk/debug/controller-debug.apk",
        "controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk",
    ):
        adb("install", "-r", str(ROOT / apk))

    output = instrument("rotationInstalledProductV1Only", "true", "Installed migration v1:")
    match = re.search(r"phone paths (\S+)", output)
    if match is None:
        raise RuntimeError("Probe omitted emulator-local APK directory")
    directory = match.group(1)
    if not directory.startswith(
        "/storage/emulated/0/Android/data/dev.smallwright.controller/files/rotation-installed-"
    ):
        raise RuntimeError("Probe reported an unexpected APK directory")
    install(directory, 1)
    old, current = signers(1)
    if old != current:
        raise RuntimeError("Version 1 signer is inconsistent")
    first = require_label("Counter: 0")
    button = next((node for node in first.iter("node") if node.get("text") == "INCREMENT"), None)
    if button is None:
        raise RuntimeError("Generated counter has no Increment button")
    tap(button.get("bounds", ""))
    require_label("Counter: 1")

    output = instrument("rotationInstalledProductV2Only", "true", "Installed migration v2:")
    if f"phone paths {directory}" not in output:
        raise RuntimeError("Second phase changed the APK directory")
    install(directory, 2)
    require_label("Rotated: 1")
    history_old, rotated = signers(2)
    if history_old != old or rotated == old:
        raise RuntimeError("Version 2 did not retain the old signer history")
    print(f"Installed-signer migration passed on API {args.api}; APKs stayed on the emulator.")


if __name__ == "__main__":
    main()
