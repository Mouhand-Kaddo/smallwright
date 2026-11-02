#!/usr/bin/env python3
"""Drive the A33 fixed suite through A33SuiteProbe on a USB phone.

Needs PHONE_SERIAL. APPROVED_MODEL is passed to every probe phase; paid models are refused
without it. Raw phase output goes to toolchains/alpha-goal-20261017/<log run>/NN-<phase>.txt.

  suite.py run <runId> <promptId> [--continue RUN [--restore]] [--fixture checklist|records]
              [--resume]
  suite.py feedback <runId> <promptId> <logRunId> <observation file>
  suite.py answers <runId> <promptId>
  suite.py recover <runId> <promptId> <productionRun> <approved context sha256>
  suite.py accept <runId> <promptId> <checklist record path>
  suite.py row <runId> <promptId> <install_result> <functional_result> <notes>
  suite.py ui <texts|click|steps|longClick|setText> [target] [value]
  suite.py ui steps "longClick:Tea|click:Rename|setText:Tea=Green tea|click:Save"
"""
import argparse
import base64
import glob
import json
import os
import re
import shlex
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LOGS = os.path.join(ROOT, "toolchains", "alpha-goal-20261017")
RUNNER = "dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation"
CONTROLLER = "dev.smallwright.controller"
WORKER = "dev.smallwright.worker"


def adb(*args, timeout=1800):
    serial = os.environ["PHONE_SERIAL"]
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    tool = os.path.join(sdk, "platform-tools", "adb") if sdk else "adb"
    return subprocess.run(
        [tool, "-s", serial, *args], capture_output=True, text=True, timeout=timeout
    ).stdout


def instrument(extra):
    command = "am instrument -w -r " + " ".join(shlex.quote(a) for a in extra) + " " + RUNNER
    return adb("shell", command)


def phase(run, prompt, name, log_run, *extra):
    directory = os.path.join(LOGS, log_run)
    os.makedirs(directory, exist_ok=True)
    path = os.path.join(directory, "%02d-%s.txt" % (len(os.listdir(directory)) + 1, name))
    args = ["-e", "a33Suite", name, "-e", "promptId", prompt, "-e", "runId", run]
    if os.environ.get("APPROVED_MODEL"):
        args += ["-e", "approvedModel", os.environ["APPROVED_MODEL"]]
    output = instrument(args + list(extra))
    with open(path, "w") as raw:
        raw.write(output)
    match = re.search(r"\{.*\}", output)
    if "A33 suite " + name not in output or not match:
        sys.exit("%s FAILED: %s" % (name, output.strip()[-600:]))
    result = json.loads(match.group(0))
    keys = ("project", "sourceRevision", "apkSha256", "contextSha256", "contextBytes",
            "journalState", "compileResult", "repairs", "providerReportedTokens",
            "outcomeCategory", "failureStatus", "numericDiagnostics", "baselineFixture",
            "answers")
    print(name, json.dumps({k: result[k] for k in keys if k in result})[:900], flush=True)
    return result


def sha_on_phone(path, run_as=None):
    prefix = ["run-as", run_as] if run_as else []
    return adb("shell", *prefix, "sha256sum", path).split(" ")[0].strip()


def installed_path(package):
    return adb("shell", "pm", "path", package).splitlines()[0].replace("package:", "").strip()


def install(result):
    apk, expected = result["apkPath"], result["apkSha256"]
    package = "dev.smallwright.generated." + apk.split("/projects/")[1].split("/")[0]
    if sha_on_phone(apk, CONTROLLER) != expected:
        sys.exit("HASH MISMATCH before install")
    size = adb("shell", "run-as", CONTROLLER, "stat", "-c", "%s", apk).strip()
    print(adb("shell", "run-as %s cat %s | pm install -r --user current --pkg %s -S %s -- -"
              % (CONTROLLER, apk, package, size)).strip())
    actual = sha_on_phone(installed_path(package))
    print("installed", package, "match" if actual == expected else "MISMATCH " + actual)
    if actual != expected:
        sys.exit(1)
    return package


def finish(result):
    if result.get("compileResult") == "verified":
        package = install(result)
        print("READY_FOR_CHECKLIST", package, result.get("sourceRevisionAfter"))
    else:
        print("TERMINAL", result.get("journalState"), result.get("outcomeCategory"))


def run_command(a):
    if a.restore and not a.continue_run:
        sys.exit("--restore needs --continue")
    if a.resume:
        phase(a.run, a.prompt, "pinBaseline", a.run)
    elif a.restore:
        restored = phase(a.run, a.prompt, "restoreAccepted", a.run, "-e", "continueRun",
                         a.continue_run)["restored"]
        install(restored)
        phase(a.run, a.prompt, "pinBaseline", a.run)
    else:
        phase(a.run, a.prompt, "prepare", a.run,
              *(["-e", "continueRun", a.continue_run] if a.continue_run else []))
        if not a.continue_run:
            built = phase(a.run, a.prompt, "baselineBuild", a.run,
                          *(["-e", "baselineFixture", a.fixture] if a.fixture else []))
            install(built)
            phase(a.run, a.prompt, "pinBaseline", a.run)
    preview = phase(a.run, a.prompt, "preview", a.run)
    result = phase(a.run, a.prompt, "send", a.run, "-e", "freeOnlyApproved", "true",
                   "-e", "approvedContextSha256", preview["contextSha256"])
    finish(result)


def feedback_command(a):
    with open(a.observation, "rb") as text:
        observation = base64.b64encode(text.read().strip()).decode()
    preview = phase(a.run, a.prompt, "feedbackPreview", a.log_run, "-e", "observationB64",
                    observation)
    result = phase(a.run, a.prompt, "feedbackSend", a.log_run, "-e", "observationB64",
                   observation, "-e", "freeOnlyApproved", "true", "-e",
                   "approvedContextSha256", preview["contextSha256"])
    finish(result)


def answers_command(a):
    preview = phase(a.run, a.prompt, "answersPreview", a.run)
    result = phase(a.run, a.prompt, "answersSend", a.run, "-e", "freeOnlyApproved", "true",
                   "-e", "approvedContextSha256", preview["contextSha256"])
    finish(result)


def recover_command(a):
    result = phase(a.run, a.prompt, "recoverOutcome", a.run, "-e", "productionRun",
                   a.production_run, "-e", "approvedContextSha256", a.context)
    finish(result)


def accept_command(a):
    phase(a.run, a.prompt, "accept", a.run, "-e", "checklistRecord", a.record)


def row_command(a):
    files = sorted(glob.glob(os.path.join(LOGS, a.run, "*-*Send.txt"))
                   + glob.glob(os.path.join(LOGS, a.run, "*-send.txt"))
                   + glob.glob(os.path.join(LOGS, a.run, "*-recoverOutcome.txt")))
    outcomes = [json.loads(m.group(0)) for m in
                (re.search(r"\{.*\}", open(f).read()) for f in files) if m]
    last, first = outcomes[-1], outcomes[0]["sends"][-1]
    uid = re.search(r"(?:userId|appId)=(\d+)", adb("shell", "dumpsys", "package", WORKER))
    row = {
        "run_id": a.run, "prompt_id": a.prompt,
        "device_model": adb("shell", "getprop", "ro.product.model").strip(),
        "android_version": adb("shell", "getprop", "ro.build.version.release").strip(),
        "controller_apk_sha256": sha_on_phone(installed_path(CONTROLLER)),
        "worker_apk_sha256": sha_on_phone(installed_path(WORKER)),
        "source_revision_before": first["sourceRevisionBefore"],
        "source_revision_after": last["sourceRevisionAfter"],
        "model_id": last["model"], "free_only": last["model"].endswith(":free"),
        "approved_context_sha256": first["contextSha256"],
        "provider_reported_tokens": last.get("providerReportedTokens"),
        "provider_reported_cost_usd": None, "worker_uid": int(uid.group(1)) if uid else None,
        "compile_result": last["compileResult"], "artifact_sha256": last.get("apkSha256"),
        "generated_package": last["package"], "generated_version": last.get("version"),
        "install_result": a.install, "functional_result": a.functional,
        "phone_build_elapsed_ms": None, "adb_detached": False,
        "network_disabled_for_build": False, "notes": a.notes,
    }
    if len(a.notes) > 500:
        sys.exit("notes exceed 500 characters: %d" % len(a.notes))
    path = os.path.join(ROOT, "benchmarks", "results", a.run + ".jsonl")
    with open(path, "w") as out:
        out.write(json.dumps(row) + "\n")
    print(path)


def ui_command(a):
    args = ["-e", "foreignUi", a.action]
    if a.target is not None:
        args += ["-e", "target", a.target]
    if a.value is not None:
        args += ["-e", "value", a.value]
    output = instrument(args)
    match = re.search(r"INSTRUMENTATION_RESULT: stream=(.*?)INSTRUMENTATION_CODE", output, re.S)
    print((match.group(1) if match else output).strip())


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    run = commands.add_parser("run")
    run.add_argument("run"), run.add_argument("prompt")
    run.add_argument("--continue", dest="continue_run")
    run.add_argument("--fixture", choices=["checklist", "records"])
    run.add_argument("--resume", action="store_true")
    run.add_argument("--restore", action="store_true")
    feedback = commands.add_parser("feedback")
    for name in ("run", "prompt", "log_run", "observation"):
        feedback.add_argument(name)
    answers = commands.add_parser("answers")
    answers.add_argument("run"), answers.add_argument("prompt")
    recover = commands.add_parser("recover")
    for name in ("run", "prompt", "production_run", "context"):
        recover.add_argument(name)
    accept = commands.add_parser("accept")
    for name in ("run", "prompt", "record"):
        accept.add_argument(name)
    row = commands.add_parser("row")
    for name in ("run", "prompt", "install", "functional", "notes"):
        row.add_argument(name)
    ui = commands.add_parser("ui")
    ui.add_argument("action", choices=["texts", "click", "steps", "longClick", "setText"])
    ui.add_argument("target", nargs="?")
    ui.add_argument("value", nargs="?")
    a = parser.parse_args()
    {"run": run_command, "feedback": feedback_command, "answers": answers_command,
     "recover": recover_command, "accept": accept_command, "row": row_command, "ui": ui_command}[a.command](a)


if __name__ == "__main__":
    main()
