# A33 retained-project selection: fresh checks pass, cause unresolved

2026-10-12 physical Galaxy A33 5G/SM-A336E, Android16/API36/ARM64.
Fresh authorized connection preflight passed05:07:39UTC. The prior checkpoint
preserves one `testHandoff verify` failure at SELECT/IllegalStateException before
source/receipt verification. This diagnostic does not reinterpret that failure.

The unchanged installed helper ran the same retained XML verification at05:07:47UTC
and passed source4/version4, exact phone receipt/installed APK, and saved acceptance.
No new acceptance tap, source edit, build, model request or generated installation.
Then a test-only temporary `traceVerify` phase captured expected versus observed
project ID, current revision and binding flag immediately after the normal picker
selection, before the original receipt assertion. Five bounded physical runs all
observed the expected project, revision4 and bindingInProgress=false, and passed
the original receipt/installed/saved-acceptance checks. It did not reproduce the
failure; timing instrumentation may affect an intermittent race.

Ranked possibilities were a premature observation of the previous project,
changed XML source, or a stale/missing receipt. The successful fresh checks show
that revision4/receipt/installed bytes/saved acceptance were present at those
observations. They do not establish the cause of the prior failure or a fix.
No product change was made. Temporary tagged instrumentation was removed before
the next harness build; exact original TestHandoffProbe bytes were restored.
The original failed record remains unchanged. Treat the intermittent observer
failure as unresolved; add a targeted regression only when its cause is proven.

Ignored evidence: `toolchains/alpha-retained-selection-20261012/` contains fresh
preflight-related metadata, unchanged `retained-handoff-01.txt` and terminal
handle, five `retained-trace-*.txt`/handles, `diagnostic-summary.json`, and the
clearly marked temporary `trace-TestHandoffProbe.java`/trace APK. Private serial
stays local. Commands: explicit-serial `run_retained.py` once, then `run_trace.py
--label 01` through `05`; each original handle reached terminal, no restart on
observation expiry. Host `format :controller:assembleDebugAndroidTest` passed;
trace test-APK update was verified in place with app data retained.

Temporary controller.test SHA256
`87ba27806dee2e124ae1c33d41b006a037008908b5acebe73208f6cecc573eaa`;
production controller `45adb0b429c5660c822e1b115fb68161131cbda0bb8f0acaa3e02cded41e658b`
and worker `0fc753e43a7d3c4b1ca259ef9f320528bc16320cd74754f8396b4cb6378f39a7`
remained unchanged. XML generated package
`dev.smallwright.generated.app7ee6a7cc1bfa4762b40a3e0e3c2ac410`, installed v4 SHA256
`283ee00a4796164671c58baf9b5667b00e76ef7f58dac8a6d71ec00f65df7800`.
This verifies retained historical test-assisted acceptance, not fresh human signoff,
installer consent, spontaneous eviction or a benchmark/full-alpha pass.
