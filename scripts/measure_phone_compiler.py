#!/usr/bin/env python3
"""Measure the guarded A33 native compiler fixture from the repository root.

Requires verified harness APKs and a fresh, unlocked physical A33. The app's
measure phase is one-shot: this runner never clears fixtures or retries. Raw
results stay under the ignored checkpoint directory. On timeout inspect the
original hostAdbPid/handle before taking another action.
"""
import argparse, datetime, json, pathlib, re, subprocess, time
parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--label', required=True)
a = parser.parse_args()
assert re.fullmatch('[0-9]{2}', a.label)
p = pathlib.Path('toolchains/alpha-compiler-measurement-20261011')
adb = [str(pathlib.Path('toolchains/android-sdk/platform-tools/adb').resolve()), '-s', a.serial]

def call(*args):
    return subprocess.run(adb + list(args), capture_output=True, text=True, timeout=12)

def run(*args):
    r = call(*args)
    assert r.returncode == 0, (args, r.returncode)
    return r.stdout.strip()

def pid(package):
    value = call('shell', 'pidof', package).stdout.strip()
    assert re.fullmatch('\\d+', value)
    return value
assert run('get-state') == 'device' and run('shell', 'getprop', 'ro.product.model') == 'SM-A336E'
for package in ['dev.smallwright.controller', 'dev.smallwright.controller.test', 'dev.smallwright.worker', 'dev.smallwright.worker.test']:
    assert not call('shell', 'pidof', package).stdout.strip(), 'Own baseline process exists; inspect rather than terminating unrelated work'
assert re.findall('^\\s+showing=(true|false)\\s*$', run('shell', 'dumpsys', 'window', 'policy'), re.M) == ['false'], 'User must unlock first'
assert re.findall('^\\s*mWakefulness=(\\w+)\\s*$', run('shell', 'dumpsys', 'power'), re.M) == ['Awake']
raw_path = p / ('measure-' + a.label + '.txt')
record_path = p / ('measurement-' + a.label + '.json')
assert not raw_path.exists() and (not record_path.exists()), 'Never overwrite or restart a previous attempt'
record = {'baselineOwnProcessesAbsent': True, 'samples': [], 'observerPlacement': 'all memory samples and gates outside timed intervals', 'duringBuildSampling': False}
with raw_path.open('x') as out:
    proc = subprocess.Popen(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'compilerMeasurementOnly', 'measure', 'dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation'], stdout=out, stderr=subprocess.STDOUT, text=True)
    record['hostAdbPid'] = proc.pid
    record_path.write_text(json.dumps(record, indent=2) + '\n')
    worker_pid = None
    worker_start = None
    controller_pid = None
    root = None
    prior_batch_end = None
    for phase in ['before1', 'after1', 'before2', 'after2']:
        deadline = time.monotonic() + 170 if phase.startswith('after') else time.monotonic() + 25
        while True:
            raw = raw_path.read_text()
            markers = re.findall('COMPILER_MEASURE_GATE root=([0-9a-f]{32}) phase=' + phase + ' controllerPid=(\\d+)', raw)
            if markers:
                break
            if proc.poll() is not None:
                print(raw)
                raise AssertionError('Original measurement handle terminal before ' + phase)
            assert time.monotonic() < deadline, 'Observation timeout: inspect original handle, never restart'
            time.sleep(0.03)
        assert len(markers) == 1 and proc.poll() is None
        observed_root, observed_controller = markers[0]
        cp = pid('dev.smallwright.controller')
        wp = pid('dev.smallwright.worker')
        assert cp == observed_controller
        if controller_pid is None:
            controller_pid = cp
            worker_pid = wp
            root = observed_root
        assert cp == controller_pid and wp == worker_pid and (observed_root == root), 'Process/root changed between builds'
        batch_started = time.monotonic_ns()
        sample = {'gapSincePriorBatchMs': None if prior_batch_end is None else (batch_started - prior_batch_end) / 1000000, 'phase': phase, 'observedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'controllerPid': cp, 'workerPid': wp, 'processes': []}
        for package, process in [('dev.smallwright.controller', cp), ('dev.smallwright.worker', wp)]:
            process_started = time.monotonic_ns()
            process_started_utc = datetime.datetime.now(datetime.timezone.utc).isoformat()
            status = call('shell', 'run-as', package, 'cat', '/proc/' + process + '/status')
            stat = call('shell', 'run-as', package, 'cat', '/proc/' + process + '/stat')
            entry = {'package': package, 'pid': process, 'vmHwmKiB': None, 'vmRssKiB': None, 'startTicks': None, 'statusAvailable': status.returncode == 0, 'statAvailable': stat.returncode == 0, 'sampleStartedAtUtc': process_started_utc, 'unavailableReasons': []}
            if status.returncode == 0:
                for source, target in [('VmHWM', 'vmHwmKiB'), ('VmRSS', 'vmRssKiB')]:
                    found = re.findall('^' + source + ':\\s+(\\d+) kB$', status.stdout, re.M)
                    if len(found) == 1:
                        entry[target] = int(found[0])
                    else:
                        entry['unavailableReasons'].append(target + ' missing')
            else:
                entry['unavailableReasons'].append('proc status denied/nonzero')
            if stat.returncode == 0:
                fields = stat.stdout.strip().rsplit(')', 1)[1].split()
                entry['startTicks'] = int(fields[19])
            else:
                entry['unavailableReasons'].append('proc stat denied/nonzero')
            if package == 'dev.smallwright.worker' and entry['startTicks'] is not None:
                if worker_start is None:
                    worker_start = entry['startTicks']
                assert worker_start == entry['startTicks'], 'Worker restarted; cannot call second build warm'
            meminfo_started = time.monotonic_ns()
            entry['meminfoStartedAtUtc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
            meminfo = call('shell', 'dumpsys', 'meminfo', '--local', '-s', process)
            entry['meminfoEndedAtUtc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
            entry['meminfoDurationMs'] = (time.monotonic_ns() - meminfo_started) / 1000000
            entry['meminfoAvailable'] = meminfo.returncode == 0
            entry['pssKiB'] = None
            entry['summaryRssKiB'] = None
            if meminfo.returncode == 0:
                header = re.findall('MEMINFO in pid (\\d+)', meminfo.stdout)
                assert header == [process]
                for source, target in [('TOTAL PSS', 'pssKiB'), ('TOTAL RSS', 'summaryRssKiB')]:
                    found = re.findall(source + ':\\s+(\\d+)', meminfo.stdout)
                    if len(found) == 1:
                        entry[target] = int(found[0])
                    else:
                        entry['unavailableReasons'].append(target + ' missing')
            else:
                entry['unavailableReasons'].append('meminfo denied/nonzero')
            entry['sampleEndedAtUtc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
            entry['sampleDurationMs'] = (time.monotonic_ns() - process_started) / 1000000
            sample['processes'].append(entry)
        prior_batch_end = time.monotonic_ns()
        sample['endedAtUtc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        sample['batchDurationMs'] = (time.monotonic_ns() - batch_started) / 1000000
        record['samples'].append(sample)
        record_path.write_text(json.dumps(record, indent=2) + '\n')
        assert proc.poll() is None and pid('dev.smallwright.controller') == cp and (pid('dev.smallwright.worker') == wp)
        ack = 'files/binding-ui-probe-' + root + '/cache/compiler-measure-' + phase + '.ack'
        run('shell', 'run-as', 'dev.smallwright.controller', 'touch', ack)
    returncode = proc.wait(timeout=30)
raw = raw_path.read_text()
print(raw)
codes = re.findall('INSTRUMENTATION_CODE: (-?\\d+)', raw)
record.update(hostExitCode=returncode, instrumentationCodes=codes)
record_path.write_text(json.dumps(record, indent=2) + '\n')
assert returncode == 0 and codes == ['-1'] and ('Compiler measurement PASS ' in raw)
result = json.loads(re.search('Compiler measurement PASS (\\{.*\\})', raw).group(1))
assert result['root'] == root
(p / ('phone-build-measurements-' + a.label + '.json')).write_text(json.dumps(result, indent=2) + '\n')
for build in result['builds']:
    assert build['endElapsedRealtimeNanos'] > build['startElapsedRealtimeNanos']
    assert build['observedClickToVisibleReadyMs'] == (build['endElapsedRealtimeNanos'] - build['startElapsedRealtimeNanos']) // 1000000
