#!/usr/bin/env python3
"""Install the local harness and run only disposable technical-debt probes on Android 16 ARM64."""

import argparse
import datetime
import json
import re
import subprocess
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONTROLLER = "dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation"
WORKER = "dev.smallwright.worker.test/dev.smallwright.worker.ConfinementInstrumentation"
APKS = [
    "worker/build/outputs/apk/debug/worker-debug.apk",
    "controller/build/outputs/apk/debug/controller-debug.apk",
    "controller/build/outputs/apk/androidTest/debug/controller-debug-androidTest.apk",
    "worker/build/outputs/apk/androidTest/debug/worker-debug-androidTest.apk",
]


def instrumentation_passed(returncode, output, expected):
    return (returncode == 0 and "INSTRUMENTATION_CODE: -1" in output
            and expected in output
            and "Artifact policy FAILED:" not in output
            and "Worker confinement FAILED:" not in output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    timestamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    evidence = ROOT / "toolchains/debt-validation" / (timestamp + "-" + uuid.uuid4().hex[:8])
    evidence.mkdir(parents=True)

    def adb(*command, timeout=300):
        return subprocess.run(["adb", "-s", args.serial, *command], capture_output=True,
                              text=True, timeout=timeout)

    for property_name, expected in [("ro.build.version.sdk", "36"),
                                    ("ro.product.cpu.abi", "arm64-v8a")]:
        result = adb("shell", "getprop", property_name)
        if result.returncode != 0 or result.stdout.strip() != expected:
            raise RuntimeError("This runner requires the authorized Android 16 ARM64 test phone")
    for relative in APKS:
        if not (ROOT / relative).is_file():
            raise RuntimeError("Build the missing local harness APK: " + relative)
    for relative in APKS:
        result = adb("install", "-r", str(ROOT / relative))
        (evidence / (Path(relative).name + "-install.txt")).write_text(result.stdout + result.stderr)
        if result.returncode != 0 or "Success" not in result.stdout:
            raise RuntimeError("Local harness installation failed: " + relative)
        print("Installed " + Path(relative).name, flush=True)

    inventory = adb("shell", "pm", "list", "packages", "-U", "dev.smallwright")
    (evidence / "package-uids.txt").write_text(inventory.stdout + inventory.stderr)
    uids = dict(re.findall(r"package:(dev\.smallwright\.(?:controller|worker)) uid:(\d+)", inventory.stdout))
    if inventory.returncode != 0 or len(uids) != 2 or len(set(uids.values())) != 2:
        raise RuntimeError("Controller and worker must have distinct package UIDs")

    checks = []

    def probe(name, target, parameters, expected, intentional_death=False):
        command = ["shell", "am", "instrument", "-r", "-w"]
        for key, value in parameters.items():
            command += ["-e", key, value]
        result = adb(*command, target)
        output = result.stdout + result.stderr
        (evidence / (name + ".txt")).write_text(output)
        if intentional_death:
            passed = result.returncode == 0 and "Process crashed" in output
        else:
            passed = instrumentation_passed(result.returncode, output, expected)
        checks.append({"probe": name, "passed": passed, "intentional_death": intentional_death})
        (evidence / "results.json").write_text(json.dumps(checks, indent=2) + "\n")
        print(name + (": passed" if passed else ": FAILED") + "\n" + output, flush=True)
        if not passed:
            raise RuntimeError("Probe failed; evidence: " + str(evidence))

    probe("snapshot-deadline", CONTROLLER, {"workerSnapshotDeadlineOnly": "true"}, "Binder deadline:")
    probe("project-binding", CONTROLLER, {"projectBindingResponsiveOnly": "true"}, "Project binding:")
    probe("generation-completion", CONTROLLER, {"generationCompletionOnly": "true"}, "Generation completion:")
    probe("local-compile-sign", CONTROLLER, {"localBuildOnly": "true"}, "Simulated model proposal; real local build:")
    probe_id = "deadline-probe-" + uuid.uuid4().hex
    probe("worker-deadline-kill", WORKER,
          {"mode": "requestDeadlineKill", "deadlineProbeId": probe_id}, "", intentional_death=True)
    probe("worker-deadline-recovery", WORKER,
          {"mode": "requestDeadlineRecovery", "deadlineProbeId": probe_id}, "Worker deadline:")
    print("Physical technical-debt probes passed. Evidence: " + str(evidence), flush=True)


if __name__ == "__main__":
    main()
