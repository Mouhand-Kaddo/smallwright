#!/usr/bin/env python3
"""Profile a guarded owned A33 workspace from the host; no worker instrumentation.

Run from the repository root after verifying installed harness APKs. No model,
credentials, generated installation/export, resets, retries or timeout kills.
Numeric process observations and scoped scratch metadata remain ignored. A
sampled maximum is not an exact simultaneous pipeline peak. On any failure
inspect the recorded original controller handle before taking another action.
"""
import argparse
import datetime
import json
import pathlib
import re
import shlex
import subprocess
import time
import uuid

CONTROLLER = 'dev.smallwright.controller'
WORKER = 'dev.smallwright.worker'


def parse_children(text, worker_pid, worker_uid):
    lines = [line.split() for line in text.splitlines() if line.strip()]
    assert lines and lines[0] == ['PID', 'PPID', 'UID'], 'Unexpected numeric ps schema'
    assert len(lines) <= 17, 'Direct-child observation bound'
    children = []
    for fields in lines[1:]:
        assert len(fields) == 3 and all(re.fullmatch(r'[0-9]+', v) for v in fields), 'Non-numeric process selector'
        pid, parent, uid = map(int, fields)
        assert pid > 0 and pid != worker_pid and parent == worker_pid and uid == worker_uid, 'Foreign process selector rejected before reads'
        assert pid not in children, 'Duplicate child identity'
        children.append(pid)
    return children


def parse_selection(returncode, stdout, stderr, worker_pid, worker_uid):
    children = parse_children(stdout, worker_pid, worker_uid)
    # Android toybox ps reports no matching rows with exit 1 and only its header.
    assert not stderr.strip() and (returncode == 0 or returncode == 1 and not children), 'Numeric direct-child selector unavailable'
    return children


def parse_status(text, pid, uid, parent=None):
    values = {}
    for key in ['Pid', 'PPid', 'Uid', 'VmRSS', 'VmHWM']:
        matches = re.findall(r'^' + key + r':\s+([0-9]+)(?:\s+[0-9]+){0,3}(?: kB)?$', text, re.M)
        assert len(matches) == 1, 'Numeric process field unavailable'
        values[key] = int(matches[0])
    assert values['Pid'] == pid and values['Uid'] == uid, 'Process ownership changed'
    if parent is not None:
        assert values['PPid'] == parent, 'Direct-child parent changed'
    return values


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--label', required=True)
    args = parser.parse_args()
    assert re.fullmatch(r'[0-9]{2}', args.label)
    root_dir = pathlib.Path('toolchains/alpha-workspace-resources-20261011')
    adb = [str(pathlib.Path('toolchains/android-sdk/platform-tools/adb').resolve()), '-s', args.serial]

    def call(*arguments):
        return subprocess.run(adb + list(arguments), capture_output=True, text=True, timeout=12)

    def shell(*arguments):
        # ADB joins remote arguments: quote each literal, including sed programs.
        return call('shell', shlex.join(arguments))

    def run(*arguments):
        result = shell(*arguments)
        assert result.returncode == 0, 'Own observation command failed'
        return result.stdout.strip()

    assert call('get-state').stdout.strip() == 'device'
    assert run('getprop', 'ro.product.model') == 'SM-A336E'
    assert re.findall(r'^\s+showing=(true|false)\s*$', run('dumpsys', 'window', 'policy'), re.M) == ['false']
    assert re.findall(r'^\s*mWakefulness=(\w+)\s*$', run('dumpsys', 'power'), re.M) == ['Awake']
    for package in [CONTROLLER, CONTROLLER + '.test', WORKER, WORKER + '.test']:
        assert not shell('pidof', package).stdout.strip(), 'Own process exists; inspect original handles'
    root_dir.mkdir(parents=True, exist_ok=True)
    record_path = root_dir / ('host-profile-' + args.label + '.json')
    raw_path = root_dir / ('measure-' + args.label + '.txt')
    assert not record_path.exists() and not raw_path.exists(), 'Never overwrite/restart original attempts'
    observation = uuid.uuid4().hex
    record = {'id': observation, 'observedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'baselineOwnProcessesAbsent': True, 'workerInstrumentationProfiler': False, 'samples': []}

    def persist():
        record_path.write_text(json.dumps(record, indent=2) + '\n')

    def pid(package):
        value = run('pidof', package)
        assert re.fullmatch(r'[0-9]+', value), 'Missing/ambiguous own PID'
        return int(value)

    def app_uid(package):
        rows = run('pm', 'list', 'packages', '-U', package)
        matches = re.findall(r'^package:' + re.escape(package) + r' uid:([0-9]+)$', rows, re.M)
        assert len(matches) == 1
        return int(matches[0])

    def status(package, process, uid, parent=None):
        started = time.monotonic_ns()
        selected = shell('run-as', package, 'sed', '-n', '-e', '/^Pid:/p', '-e', '/^PPid:/p', '-e', '/^Uid:/p', '-e', '/^VmRSS:/p', '-e', '/^VmHWM:/p', '/proc/' + str(process) + '/status')
        age = shell('run-as', package, 'sed', '-E', r's/^.*\) //', '/proc/' + str(process) + '/stat')
        if selected.returncode != 0 or age.returncode != 0:
            return {'pid': process, 'available': False, 'durationMs': (time.monotonic_ns() - started) / 1e6}
        fields = parse_status(selected.stdout.strip(), process, uid, parent)
        numeric = age.stdout.split()
        assert len(numeric) >= 20 and re.fullmatch('[0-9]+', numeric[19])
        return {'pid': process, 'uid': uid, 'parentPid': fields['PPid'], 'available': True, 'startTicks': int(numeric[19]), 'rssKiB': fields['VmRSS'], 'vmHwmKiB': fields['VmHWM'], 'durationMs': (time.monotonic_ns() - started) / 1e6}

    controller = None
    try:
        with raw_path.open('x') as out:
            controller = subprocess.Popen(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'workspaceResourcesOnly', 'measure', '-e', 'observationId', observation, CONTROLLER + '.test/dev.smallwright.controller.ArtifactPolicyInstrumentation'], stdout=out, stderr=subprocess.STDOUT, text=True)
            record['controllerHostAdbPid'] = controller.pid
            persist()
            deadline = time.monotonic() + 20
            while True:
                gates = re.findall(r'BUILD_RESOURCE_GATE root=([0-9a-f]{32}) id=' + observation + r' phase=before1 controllerPid=([0-9]+)', raw_path.read_text())
                if gates: break
                assert controller.poll() is None and time.monotonic() < deadline, 'Inspect original handle; never restart on observation timeout'
                time.sleep(.01)
            assert len(gates) == 1
            fixture, controller_text = gates[0]
            cp, wp = pid(CONTROLLER), pid(WORKER)
            assert cp == int(controller_text)
            cu, wu = app_uid(CONTROLLER), app_uid(WORKER)
            assert cu != wu
            before = [status(CONTROLLER, cp, cu), status(WORKER, wp, wu)]
            assert all(item['available'] for item in before)
            identities = {item['pid']: item['startTicks'] for item in before}
            record.update(root=fixture, controllerPid=cp, workerPid=wp, controllerUid=cu, workerUid=wu, beforeBuildProcesses=before)
            persist()
            run('run-as', CONTROLLER, 'touch', 'files/binding-ui-probe-' + fixture + '/cache/build-resource-before1.ack')
            started, previous_end = time.monotonic(), time.monotonic_ns()
            final_gate = r'BUILD_RESOURCE_GATE root=' + fixture + r' id=' + observation + r' phase=after6 controllerPid=' + str(cp)
            while True:
                raw = raw_path.read_text()
                if re.search(final_gate, raw): break
                assert controller.poll() is None and time.monotonic() - started < 100, 'Inspect original handle; never restart on observation timeout'
                batch_start = time.monotonic_ns()
                item = {'startedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'gapSincePriorBatchMs': (batch_start - previous_end) / 1e6, 'processes': [status(CONTROLLER, cp, cu), status(WORKER, wp, wu)]}
                for process in item['processes']:
                    if process['available']: assert process['startTicks'] == identities[process['pid']], 'Root process identity changed'
                selected = shell('ps', '-P', str(wp), '-o', 'PID,PPID,UID', '-n')
                children = parse_selection(selected.returncode, selected.stdout, selected.stderr, wp, wu)
                item['children'] = [status(WORKER, child, wu, wp) for child in children]
                scratch = shell('run-as', WORKER, 'du', '-sk', 'cache/active-build')
                if scratch.returncode == 0:
                    size = re.fullmatch(r'([0-9]+)\s+cache/active-build\s*', scratch.stdout)
                    assert size, 'Unexpected scoped du result'
                    item['workerScratch'] = {'available': True, 'allocatedKiB': int(size[1])}
                else:
                    absent = shell('run-as', WORKER, 'test', '!', '-e', 'cache/active-build')
                    item['workerScratch'] = {'available': False, 'absentAtFollowup': absent.returncode == 0}
                previous_end = time.monotonic_ns()
                item['durationMs'] = (previous_end - batch_start) / 1e6
                record['samples'].append(item)
                assert len(record['samples']) <= 512, 'Host sample bound'
                persist()
                time.sleep(.1)
            after = [status(CONTROLLER, cp, cu), status(WORKER, wp, wu)]
            assert all(item['available'] and item['startTicks'] == identities[item['pid']] for item in after)
            record['afterBuildProcesses'] = after
            persist()
            run('run-as', CONTROLLER, 'touch', 'files/binding-ui-probe-' + fixture + '/cache/build-resource-after6.ack')
            rc = controller.wait(timeout=20)
            raw = raw_path.read_text()
            codes = re.findall(r'INSTRUMENTATION_CODE: (-?[0-9]+)', raw)
            record.update(controllerHostExitCode=rc, controllerCodes=codes)
            persist()
            assert rc == 0 and codes == ['-1'] and 'Workspace resources PASS ' in raw
            result = json.loads(re.search(r'Workspace resources PASS (\{.*\})', raw)[1])
            assert result['root'] == fixture and result['workspaceMode'] and result['controllerUid'] == cu and result['workerUid'] == wu
            assert len(result['builds']) == 6
            for version, build in enumerate(result['builds'], 1):
                assert build['version'] == build['sourceRevision'] == version
                assert build['startNanos'] < build['endNanos']
                assert build['observedClickToVisibleReadyMs'] == (build['endNanos'] - build['startNanos']) // 1000000
                assert build['sourceSha256'] == result['builds'][0]['sourceSha256'] and build['signerSha256'] == result['builds'][0]['signerSha256']
                assert build['ownedWorkspaceStorage']['allocatedBytesIncludingDirectories'] >= build['projectStorage']['allocatedBytesIncludingDirectories']
            record['controllerResult'] = result
            record['observedLatencyWithinCaps'] = all(b['observedClickToVisibleReadyMs'] <= (10000 if b['version'] == 1 else 5000) for b in result['builds'])
            persist()
            assert record['observedLatencyWithinCaps'], 'Existing latency cap missed; limits unchanged'
            assert record['samples'] and any(sample['workerScratch']['available'] for sample in record['samples']), 'No in-window workspace resource observation'
            record['sixBuildWorkspaceVerified'] = True
            persist()
    except BaseException as failure:
        record['hostFailureClass'] = type(failure).__name__
        persist()
        if controller is not None:
            try:
                record['controllerHostExitCode'] = controller.wait(timeout=45)
                record['controllerCodes'] = re.findall(r'INSTRUMENTATION_CODE: (-?[0-9]+)', raw_path.read_text())
            except subprocess.TimeoutExpired:
                record['originalControllerHandleStillPending'] = True
            persist()
        raise
    print('Six actual owned-workspace builds and numeric host observations PASS')


if __name__ == '__main__': main()
