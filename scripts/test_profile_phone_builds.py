"""Synthetic host orchestration tests. No ADB, APK, compiler or physical evidence."""
import contextlib
import io
import json
import os
from pathlib import Path
import runpy
import subprocess
import tempfile
import unittest
from unittest.mock import patch

RUNNER = Path(__file__).with_name('profile_phone_builds.py').resolve()

class Harness:
    def __init__(self, invalid=False, over_cap=False):
        self.invalid = invalid
        self.over_cap = over_cap
        self.worker = None
        self.controller = None
        self.acknowledged = False
        self.released = False

    def launch(self, argv, stdout, **kwargs):
        is_worker = 'buildResourceObserver' in argv
        observer_id = argv[argv.index('observationId') + 1]
        proc = Handle(self, is_worker, observer_id, stdout)
        if is_worker:
            self.worker = proc
            proc.write('INSTRUMENTATION_STATUS: stream=RESOURCE_OBSERVER_READY id=' + observer_id + ' workerPid=51\n')
        else:
            self.controller = proc
            for phase in ['before1', 'after6']:
                proc.write('INSTRUMENTATION_STATUS: stream=BUILD_RESOURCE_GATE root=' + 'a' * 32 + ' id=' + observer_id + ' phase=' + phase + ' controllerPid=52\n')
        return proc

    def call(self, argv, **kwargs):
        args = argv[3:]
        output = ''
        if args == ['get-state']: output = 'device'
        elif args == ['shell', 'getprop', 'ro.product.model']: output = 'SM-A336E'
        elif args[:3] == ['shell', 'dumpsys', 'window']: output = 'Policy\n  showing=false\n'
        elif args[:3] == ['shell', 'dumpsys', 'power']: output = 'Power\n  mWakefulness=Awake\n'
        elif args[:2] == ['shell', 'pidof']:
            proc = self.worker if args[2] == 'dev.smallwright.worker' else self.controller if args[2] == 'dev.smallwright.controller' else None
            output = str(51 if proc is self.worker else 52) if proc and proc.live else ''
        elif args[:4] == ['shell', 'run-as', 'dev.smallwright.worker', 'cat']:
            output = '51 (own) ' + ' '.join(['S'] + ['0'] * 18 + ['99'])
        elif args[:2] == ['shell', 'run-as'] and args[3] == 'touch':
            target = args[4]
            if target.endswith('stop-sampling.ack'):
                if self.invalid:
                    self.worker.write('INSTRUMENTATION_STATUS: stream=RESOURCE_OBSERVER_INVALID id=' + self.worker.id + ' category=AssertionError\n')
                else:
                    self.worker.write('INSTRUMENTATION_STATUS: stream=RESOURCE_OBSERVER_RESULT ' + json.dumps({'id': self.worker.id, 'workerPid': 51, 'workerUid': 11, 'startedNanos': 0, 'endedNanos': 100000000000}) + '\n')
            elif target.endswith('build-resource-after6.ack'):
                self.acknowledged = True
            elif target.endswith('release.ack'):
                if self.controller is None or self.controller.live:
                    raise AssertionError('Observer released before original controller terminal')
                self.released = True
        else:
            raise AssertionError('Unexpected synthetic tool command: ' + repr(args))
        return subprocess.CompletedProcess(argv, 0, output, '')

class Handle:
    def __init__(self, harness, worker, observer_id, stream):
        self.harness, self.worker, self.id, self.stream = harness, worker, observer_id, stream
        self.pid = 901 if worker else 902
        self.live = True

    def write(self, text):
        self.stream.write(text)
        self.stream.flush()

    def poll(self):
        return None if self.live else 0

    def wait(self, timeout):
        h = self.harness
        if self.worker:
            if not h.released: raise AssertionError('Worker finished before explicit release')
            self.write('INSTRUMENTATION_RESULT: stream=Resource observer ' + ('FAILED' if h.invalid else 'PASS id=' + self.id) + '\nINSTRUMENTATION_CODE: ' + ('0' if h.invalid else '-1') + '\n')
        else:
            if not h.acknowledged: raise AssertionError('Controller final gate not acknowledged')
            builds = []
            for version in range(1, 7):
                duration = 6000 if h.over_cap and version == 2 else 1000
                builds.append({'version': version, 'sourceRevision': version, 'startNanos': version * 10000000000, 'endNanos': version * 10000000000 + duration * 1000000, 'observedClickToVisibleReadyMs': duration, 'sourceSha256': 'same-source', 'signerSha256': 'same-signer'})
            self.write('INSTRUMENTATION_RESULT: stream=Build resources PASS ' + json.dumps({'root': 'a' * 32, 'controllerPid': 52, 'controllerUid': 12, 'workerUid': 11, 'builds': builds}) + '\nINSTRUMENTATION_CODE: -1\n')
        self.live = False
        return 0

class ResourceRunnerTests(unittest.TestCase):
    def scenario(self, **kwargs):
        h = Harness(**kwargs)
        output = io.StringIO()
        original = Path.cwd()
        evidence = original / 'toolchains/alpha-build-resources-20261011'
        with tempfile.TemporaryDirectory(prefix='host-protocol-', dir=evidence) as directory:
            os.chdir(directory)
            try:
                with patch('sys.argv', [str(RUNNER), '--serial', 'synthetic-no-device', '--label', '99']), patch('subprocess.run', h.call), patch('subprocess.Popen', h.launch), contextlib.redirect_stdout(output):
                    try:
                        runpy.run_path(str(RUNNER), run_name='__main__')
                    except AssertionError:
                        failed = True
                    else:
                        failed = False
                record = json.loads(Path('toolchains/alpha-build-resources-20261011/correlation-99.json').read_text())
            finally:
                os.chdir(original)
        return h, failed, output.getvalue(), record

    def test_invalid_observation_finishes_controller_then_releases_observer_and_fails(self):
        h, failed, output, record = self.scenario(invalid=True)
        self.assertTrue(h.acknowledged, 'Invalid observation must still release final controller gate')
        self.assertFalse(h.controller.live)
        self.assertTrue(h.released)
        self.assertFalse(h.worker.live)
        self.assertTrue(failed)
        self.assertNotIn('Six actual larger-build resource observations PASS', output)

    def test_warm_latency_cap_miss_fails_after_safe_observer_release(self):
        h, failed, output, record = self.scenario(over_cap=True)
        self.assertTrue(h.released)
        self.assertFalse(h.controller.live)
        self.assertFalse(h.worker.live)
        self.assertFalse(record['observedLatencyWithinCaps'])
        self.assertTrue(failed, 'Over-cap warm Build must not print final PASS')
        self.assertNotIn('Six actual larger-build resource observations PASS', output)

    def test_valid_observation_releases_only_after_controller_terminal(self):
        h, failed, output, record = self.scenario()
        self.assertFalse(failed)
        self.assertTrue(record['sixBuildResourcesVerified'])
        self.assertTrue(record['observedLatencyWithinCaps'])
        self.assertTrue(h.released)
        self.assertFalse(h.worker.live)

if __name__ == '__main__': unittest.main()
