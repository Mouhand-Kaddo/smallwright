#!/usr/bin/env python3
"""Correlate visible Stop with actual ECJ execution on the guarded physical A33.

Run from the repo root after verifying/installing the harness APKs. This is a
one-shot local probe, not a performance benchmark. It never reads credentials,
clears app data, retries or terminates a timed-out target. Inspect recorded
original host handles after any failure; only release the observer after its
original controller handle is terminal. Raw records stay in ignored toolchains.
"""
import argparse, datetime, json, pathlib, re, subprocess, time, uuid
p = pathlib.Path('toolchains/alpha-ecj-stop-20261011')
parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--label', required=True)
a = parser.parse_args()
assert re.fullmatch('[0-9]{2}', a.label)
adb = [str(pathlib.Path('toolchains/android-sdk/platform-tools/adb').resolve()), '-s', a.serial]

def call(*v):
    return subprocess.run(adb + list(v), capture_output=True, text=True, timeout=12)

def run(*v):
    r = call(*v)
    assert r.returncode == 0, (v, r.returncode)
    return r.stdout.strip()

def pid(pkg):
    value = call('shell', 'pidof', pkg).stdout.strip()
    assert re.fullmatch('\\d+', value)
    return value
assert run('get-state') == 'device' and run('shell', 'getprop', 'ro.product.model') == 'SM-A336E'
assert re.findall('^\\s+showing=(true|false)\\s*$', run('shell', 'dumpsys', 'window', 'policy'), re.M) == ['false']
assert re.findall('^\\s*mWakefulness=(\\w+)\\s*$', run('shell', 'dumpsys', 'power'), re.M) == ['Awake']
for pkg in ['dev.smallwright.controller', 'dev.smallwright.controller.test', 'dev.smallwright.worker', 'dev.smallwright.worker.test']:
    assert not call('shell', 'pidof', pkg).stdout.strip(), 'Own baseline process exists; inspect original handles'
p.mkdir(parents=True, exist_ok=True)
record_path = p / ('correlation-' + a.label + '.json')
worker_path = p / ('observer-' + a.label + '.txt')
controller_path = p / ('prepare-' + a.label + '.txt')
assert not any((x.exists() for x in [record_path, worker_path, controller_path])), 'Never overwrite/restart original attempts'
id = uuid.uuid4().hex
record = {'id': id, 'observedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'baselineOwnProcessesAbsent': True}

def persist():
    record_path.write_text(json.dumps(record, indent=2) + '\n')

def wait_marker(proc, path, pattern, timeout):
    end = time.monotonic() + timeout
    while True:
        matches = re.findall(pattern, path.read_text(), re.M)
        if matches:
            return matches
        assert proc.poll() is None, 'Original handle terminal before required marker'
        assert time.monotonic() < end, 'Observation timeout: inspect original handle, never restart'
        time.sleep(0.005)
with worker_path.open('x') as wo, controller_path.open('x') as co:
    worker = subprocess.Popen(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'mode', 'ecjStopObserver', '-e', 'observationId', id, 'dev.smallwright.worker.test/dev.smallwright.worker.ConfinementInstrumentation'], stdout=wo, stderr=subprocess.STDOUT, text=True)
    record['observerHostAdbPid'] = worker.pid
    persist()
    ready = wait_marker(worker, worker_path, 'ECJ_OBSERVER_READY id=(' + id + ') workerPid=(\\d+)', 20)
    assert len(ready) == 1
    wp = ready[0][1]
    assert pid('dev.smallwright.worker') == wp
    start_stat = run('shell', 'run-as', 'dev.smallwright.worker', 'cat', '/proc/' + wp + '/stat')
    ticks = int(start_stat.rsplit(')', 1)[1].split()[19])
    record.update(workerPid=wp, workerStartTicks=ticks)
    persist()
    controller = subprocess.Popen(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'ecjStopOnly', 'prepare', '-e', 'observationId', id, 'dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation'], stdout=co, stderr=subprocess.STDOUT, text=True)
    record['controllerHostAdbPid'] = controller.pid
    persist()
    roots = wait_marker(controller, controller_path, 'ECJ_STOP_CONTROLLER_READY root=([0-9a-f]{32}) observationId=' + id + ' controllerPid=(\\d+)', 20)
    assert len(roots) == 1
    root, cp = roots[0]
    assert pid('dev.smallwright.controller') == cp
    record.update(root=root, controllerPid=cp)
    persist()
    active = wait_marker(worker, worker_path, 'ECJ_OBSERVER_ACTIVE id=(' + id + ')', 20)
    assert active == [id] and worker.poll() is None and (controller.poll() is None)
    record['hostSignalUtc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    persist()
    run('shell', 'run-as', 'dev.smallwright.controller', 'touch', 'files/binding-ui-probe-' + root + '/cache/ecj-observed.ack')
    try:
        rc = controller.wait(timeout=45)
        raw = controller_path.read_text()
        codes = re.findall('INSTRUMENTATION_CODE: (-?\\d+)', raw)
        record.update(controllerHostExitCode=rc, controllerCodes=codes)
        persist()
        assert rc == 0 and codes == ['-1'] and ('ECJ Stop prepare PASS ' in raw), 'Controller preparation failed; retain original worker handle'
        result = json.loads(re.search('ECJ Stop prepare PASS (\\{.*\\})', raw).group(1))
        assert result['root'] == root
        windows = wait_marker(worker, worker_path, '^INSTRUMENTATION_STATUS: stream=ECJ_OBSERVER_WINDOW (\\{[^\\n]*\\})\\n', 5)
        assert len(windows) == 1
        window = json.loads(windows[0])
        assert window['id'] == id and str(window['workerPid']) == wp
        assert pid('dev.smallwright.worker') == wp
        after_stat = run('shell', 'run-as', 'dev.smallwright.worker', 'cat', '/proc/' + wp + '/stat')
        assert int(after_stat.rsplit(')', 1)[1].split()[19]) == ticks
        samples = window['samples']
        assert all((s['startNanos'] <= s['endNanos'] for s in samples))
        assert all((left['endNanos'] <= right['startNanos'] for left, right in zip(samples, samples[1:])))
        before = [s for s in samples if s['endNanos'] < result['stopStartNanos']]
        after = [s for s in samples if s['startNanos'] > result['stopEndNanos']]
        before_gap = None if not before else (result['stopStartNanos'] - before[-1]['startNanos']) / 1000000.0
        after_gap = None if not after else (after[0]['endNanos'] - result['stopEndNanos']) / 1000000.0
        record.update(controllerResult=result, sampleCount=len(samples), beforeStopGapMs=before_gap, afterStopGapMs=after_gap, maximumSnapshotDurationMs=max(((s['endNanos'] - s['startNanos']) / 1000000.0 for s in samples)), maximumInterSampleGapMs=max(((right['startNanos'] - left['endNanos']) / 1000000.0 for left, right in zip(samples, samples[1:]))), sameWorkerProcessConfirmed=True)
    finally:
        if controller.poll() is not None and worker.poll() is None:
            run('shell', 'run-as', 'dev.smallwright.worker', 'touch', 'files/ecj-stop-observer-' + id + '/release.ack')
            record['observerReleasedAfterControllerTerminal'] = True
            persist()
    wrc = worker.wait(timeout=10)
    wraw = worker_path.read_text()
    wcodes = re.findall('INSTRUMENTATION_CODE: (-?\\d+)', wraw)
    record.update(workerHostExitCode=wrc, workerCodes=wcodes)
    persist()
    assert wrc == 0 and wcodes == ['-1'] and ('ECJ observer PASS id=' + id in wraw)
    assert before_gap is not None and after_gap is not None and (0 <= min(before_gap, after_gap) <= max(before_gap, after_gap) <= 50), 'Stop missed strict actual ECJ phase bracket; never claim phase cancellation'
    record['actualEcjStopBracketVerified'] = True
    persist()
print(json.dumps({k: v for k, v in record.items() if k not in ['controllerResult']}, indent=2))
print('Actual ECJ-phase Stop and late-artifact suppression PASS')
