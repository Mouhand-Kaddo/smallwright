#!/usr/bin/env python3
"""Profile the one-shot physical A33 larger-build fixture from the repository root.

Requires verified installed harness APKs. Raw results remain in ignored toolchains.
No credentials, generated install/export, data clearing, retry or timeout kill.
On any observation failure inspect the original recorded host handles; only an
original terminal controller permits release of the worker observer. Memory is
sampled, includes instrumentation overhead, and is not an exact pipeline peak.
"""
import argparse, datetime, json, pathlib, re, subprocess, time, uuid
p = pathlib.Path('toolchains/alpha-build-resources-20261011')
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
controller_path = p / ('measure-' + a.label + '.txt')
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
    worker = subprocess.Popen(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'mode', 'buildResourceObserver', '-e', 'observationId', id, 'dev.smallwright.worker.test/dev.smallwright.worker.ConfinementInstrumentation'], stdout=wo, stderr=subprocess.STDOUT, text=True)
    record['observerHostAdbPid'] = worker.pid
    persist()
    ready = wait_marker(worker, worker_path, 'RESOURCE_OBSERVER_READY id=(' + id + ') workerPid=(\\d+)', 20)
    assert len(ready) == 1
    wp = ready[0][1]
    assert pid('dev.smallwright.worker') == wp
    start_stat = run('shell', 'run-as', 'dev.smallwright.worker', 'cat', '/proc/' + wp + '/stat')
    ticks = int(start_stat.rsplit(')', 1)[1].split()[19])
    record.update(workerPid=wp, workerStartTicks=ticks)
    persist()
    controller = subprocess.Popen(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'buildResourcesOnly', 'measure', '-e', 'observationId', id, 'dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation'], stdout=co, stderr=subprocess.STDOUT, text=True)
    record['controllerHostAdbPid'] = controller.pid
    persist()
    roots = wait_marker(controller, controller_path, 'BUILD_RESOURCE_GATE root=([0-9a-f]{32}) id=' + id + ' phase=before1 controllerPid=(\\d+)', 20)
    assert len(roots) == 1
    root, cp = roots[0]
    assert pid('dev.smallwright.controller') == cp
    record.update(root=root, controllerPid=cp)
    persist()
    run('shell', 'run-as', 'dev.smallwright.controller', 'touch', 'files/binding-ui-probe-' + root + '/cache/build-resource-before1.ack')
    try:
        after = wait_marker(controller, controller_path, 'BUILD_RESOURCE_GATE root=' + root + ' id=' + id + ' phase=after6 controllerPid=' + cp, 100)
        assert len(after) == 1 and controller.poll() is None and worker.poll() is None
        run('shell', 'run-as', 'dev.smallwright.worker', 'touch', 'files/build-resource-observer-' + id + '/stop-sampling.ack')
        markers = wait_marker(worker, worker_path, '^INSTRUMENTATION_STATUS: stream=(RESOURCE_OBSERVER_RESULT (\\{[^\\n]*\\})|RESOURCE_OBSERVER_INVALID id=' + id + ' category=\\w+)\\n', 5)
        assert len(markers) == 1
        marker_text = markers[0][0]
        window = None
        invalid = marker_text.startswith('RESOURCE_OBSERVER_INVALID')
        record['observerInvalid'] = invalid
        if invalid:
            record['observerInvalidMarker'] = marker_text
            persist()
        else:
            window = json.loads(markers[0][1])
            assert window['id'] == id and str(window['workerPid']) == wp
            assert pid('dev.smallwright.worker') == wp
            after_stat = run('shell', 'run-as', 'dev.smallwright.worker', 'cat', '/proc/' + wp + '/stat')
            assert int(after_stat.rsplit(')', 1)[1].split()[19]) == ticks
        run('shell', 'run-as', 'dev.smallwright.controller', 'touch', 'files/binding-ui-probe-' + root + '/cache/build-resource-after6.ack')
        rc = controller.wait(timeout=20)
        raw = controller_path.read_text()
        codes = re.findall('INSTRUMENTATION_CODE: (-?\\d+)', raw)
        record.update(controllerHostExitCode=rc, controllerCodes=codes)
        persist()
        assert rc == 0 and codes == ['-1'] and 'Build resources PASS ' in raw
        result = json.loads(re.search('Build resources PASS (\\{.*\\})', raw).group(1))
        assert result['root'] == root and str(result['controllerPid']) == cp
        assert result['controllerUid'] != result['workerUid']
        if window is not None:
            assert result['workerUid'] == window['workerUid']
        assert len(result['builds']) == 6
        for version, build in enumerate(result['builds'], 1):
            assert build['version'] == build['sourceRevision'] == version
            assert build['startNanos'] < build['endNanos']
            if window is not None:
                assert window['startedNanos'] <= build['startNanos'] < build['endNanos'] <= window['endedNanos']
            assert build['observedClickToVisibleReadyMs'] == (build['endNanos'] - build['startNanos']) // 1000000
            assert build['sourceSha256'] == result['builds'][0]['sourceSha256']
            assert build['signerSha256'] == result['builds'][0]['signerSha256']
        record.update(controllerResult=result, observerResult=window, sameWorkerProcessConfirmed=not invalid,
                      unchangedLatencyCaps={'firstMs':10000,'warmMs':5000},
                      observedLatencyWithinCaps=all(b['observedClickToVisibleReadyMs'] <= (10000 if b['version']==1 else 5000) for b in result['builds']))
    finally:
        if controller.poll() is not None and worker.poll() is None:
            run('shell', 'run-as', 'dev.smallwright.worker', 'touch', 'files/build-resource-observer-' + id + '/release.ack')
            record['observerReleasedAfterControllerTerminal'] = True
            persist()
    wrc = worker.wait(timeout=10)
    wraw = worker_path.read_text()
    wcodes = re.findall('INSTRUMENTATION_CODE: (-?\\d+)', wraw)
    record.update(workerHostExitCode=wrc, workerCodes=wcodes)
    persist()
    assert not invalid, 'Worker observations invalid; build evidence retained separately'
    assert wrc == 0 and wcodes == ['-1'] and ('Resource observer PASS id=' + id in wraw)
    assert record['observedLatencyWithinCaps'], 'Larger-build latency cap missed; limits unchanged'
    record['sixBuildResourcesVerified'] = True
    persist()
print(json.dumps({k: v for k, v in record.items() if k not in ['controllerResult','observerResult']}, indent=2))
print('Six actual larger-build resource observations PASS')
