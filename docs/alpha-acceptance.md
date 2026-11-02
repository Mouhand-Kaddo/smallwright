# Private-alpha acceptance

Use the fixed requests in `benchmarks/prompts.jsonl` and the evidence procedure
in `benchmarks/protocol.md`. The twenty `supported` requests are the measured
suite; `refusal_01` is a separate scope check. Keep the prompt set unchanged
through an evaluation round. Record the exact starting source snapshot hash for
each attempt; checklist and tracker requests need a documented baseline or a
previous accepted revision. Never silently supply developer repairs.

Current A33 evaluation (2026-10-12): `counter_01` passed the native functional
checklist on its second observed-feedback update (source3/version3), with buttons
beside each other, exact subtraction through zero, retained-1 across the update
and restart-2. The initial and first feedback rows remain **rejected**. Nineteen
supported A33 requests were pending at that checkpoint. The subsequent frozen
`counter_02` request exhausted its unchanged three-repair cap: source7/FAILED,
no verified APK or functional acceptance. All four failures remain recorded;
eighteen other supported requests are pending. See [final Reset failure](validation/suite-counter02-repair3-a33-20261012.md).
Subsequent `counter_05` passed its initial exact request on a fresh documented
normal installed baseline: retained2 across same-signer v1→v2, native ADD ONE,
one tap3 and restart3. One free-only Send/zero repairs; no developer source
edits or functional-feedback rescue. This adds a second agent-observed A33
supported pass, leaving seventeen other requests pending. See
[fixed label update](validation/suite-counter05-a33-20261012.md). The earlier
eighteen-pending count remains the prior counter_02 checkpoint, not the current
snapshot. The fresh counter_03 initial request subsequently returned actual Java
compile_error/source2/REPAIR_NEEDED. Its baseline v1/count2 remained intact;
the subsequent first compiler repair also failed at source3/REPAIR_NEEDED
sequence9. Its failed reopen/duplicate guard and retained v1/native2/restart2
passed; one actual repair Send, further capacity available under the unchanged
cap. See [Step repair1](validation/suite-counter03-repair1-a33-20261012.md). Sixteen other
supported requests are unrun, in addition to this pending repair; no new supported
pass. See [Step initial failure](validation/suite-counter03-a33-20261012.md).
The subsequent second Step repair timed out45450ms at unchanged source3/FAILED
sequence11. Its request usage/cost are unknown, and no new APK or functional
pass resulted. Failed reopen/duplicate guard, retained v1/native2/restart2 and
original selection restore/verify passed. Two repair Sends; terminal model
failure rather than repair-cap exhaustion. See [Step repair2](validation/suite-counter03-repair2-a33-20261013.md).
On 2026-10-17 a fresh `counter_04` run passed after the ticket #33 lambda fix
(one Send, zero repairs; see [counter_04 fresh](validation/suite-counter04-a33-20261017.md)).
`checklist_01` then failed: the initial request and three repairs all ended in
Java compile errors that were model mistakes against existing stubs. See
[checklist_01](validation/suite-checklist01-a33-20261017.md).
Current A33 result (2026-10-17, round complete): the A33 does not meet the
suite bar for this round.

| Result | Requests |
| --- | --- |
| pass | counter_01, counter_04, counter_05, tracker_01, tracker_02, checklist_03, checklist_04, records_02, records_04 |
| fail | counter_02, counter_03, checklist_01, checklist_02, checklist_05, tracker_03, tracker_04, records_01, records_03, records_05 |
| unknown | tracker_05: clear-day works, but the app stores no older days to keep |

Failure causes, all with free model `cohere/north-mini-code:free`:

- Java compile errors that the three-repair cap did not fix (counter_02,
  checklist_01, checklist_02, and counter_03 after a repair timeout). checklist_02
  used `Paint.STRIKETHROUGH_TEXT_FLAG`, a constant Android does not have.
- A reply that changed only the unused layout file, or returned the file
  unchanged (records_01, checklist_05, and tracker_03's third feedback).
- Generated logic that was wrong after three feedback sends (tracker_03: Undo
  toggles between two saved totals).
- Invalid `replace_source` argument JSON (tracker_04, records_03).
- A repair reply without a tool call ("envelope": records_01, checklist_05), and
  one repair failure whose cause the status did not show (records_05).

checklist_02 to 05 and records_02 to 05 started from documented
developer-written level-1 baselines, because checklist_01 and records_01 failed.
Checklist input in generated apps used accessibility actions from the controller
test APK. Samsung's keyboard closed dialogs under injected shell input. Details:
[trackers](validation/suite-tracker-a33-20261017.md),
[checklists](validation/suite-checklist-a33-20261017.md),
[records](validation/suite-records-a33-20261017.md),
[project delete](validation/delete-project-a33-20261017.md). The S23 fixed suite
remains pending.

Paid A33 round (2026-10-17, user-approved `deepseek/deepseek-v4.1-flash`):
the A33 meets the suite bar, agent observed. Every request started from a pristine
project or the previous accepted paid revision; no developer-written fixtures.
checklist_02 passed on its fifth attempt. Its four failed attempts stay in the
ledger: three broken replies and one HTTP 404 from a routing change. Every other
request passed on its first attempt. tracker_05's Cancel passed on 2026-10-17. Its
Confirm ran after midnight Dubai on 2026-10-18: Clear reset today to 0 and kept
the 2026-10-17 entry of 2 glasses, also after a relaunch. The probe's accept
step did not run, because the user's own project was open in the controller.

| Result | Requests |
| --- | --- |
| pass | counter_01 to 05, tracker_01 to 05, checklist_01 to 05, records_01 to 05 |

From checklist_02's fifth attempt onward,
requests go only to the `deepinfra`, `together` and `nebius` providers. Under
default routing, three replies arrived with every backslash missing. Details:
[paid round](validation/suite-paid-a33-20261017.md).

Paid S23 Ultra round (2026-10-17, same model and provider policy, fresh
controller install): tracker_05's Confirm is blocked (the S23 was not connected
at the 2026-10-18 00:10 check), and the S23 also meets the suite bar, agent
observed. checklist_02
passed on attempt 2, after a worker stub fix for common `org.json` methods. Four
requests passed after one compiler-error repair; two of those failures came from
missing compile stubs. Locality and update continuity probes passed on the same
harness. User-install consent was not run in this round. See
[S23 paid round](validation/suite-paid-s23-20261017.md).

| Result | Requests |
| --- | --- |
| pass | counter_01 to 05, tracker_01 to 04, checklist_01 to 05, records_01 to 05 |
| blocked | tracker_05: confirmation and Cancel pass; Confirm waits for the phone to be connected |

Both devices met the suite bar on the earlier one-file harness. On 2026-11-02
the user reported that their own hands-on testing of the current builder on the
A33 was good; this is user-reported human acceptance, with no separate agent
record of what was tried.

Step-by-step builder round on the A33 (2026-11-02, paid DeepSeek V4.1 Flash,
question rounds answered with the builder's recommended choices): the A33
meets the suite bar on the current builder, agent observed; the user closed
tracker_05's last item (older days stay) without an agent observation.
tracker_02 passed after one observed-feedback message; no request needed a
build repair. See [the round](validation/suite-step-run-a33-20261102.md).
This is agent-observed, with human
acceptance and exact feedback resource measurements not run; no full-alpha pass.
See [passing second feedback](validation/suite-counter01-feedback2-a33-20261012.md)
and [retained unresolved selection diagnosis](validation/retained-selection-diagnostic-a33-20261012.md).

Run the suite on the S23 Ultra and a representative second unrooted physical
Android device. For each device, at least 16 of 20 supported requests must pass
their functional checklist within the approved repair cap. Count failures and
unknowns in the denominator. Installation, standalone launch, retained-data
checks and user-observed behavior are separate outcomes; a signed APK or model
claim alone does not pass a functional checklist.

The locality, credential-isolation, user-install-consent and data-preserving
update gates must also pass. A user-facing failure must allow recovery without
losing all project work. Record cold/warm build time, peak memory, storage and
thermal observations only when measured on the phone. Set performance limits
after the first measured compiler spike and before evaluating the full suite.

The first A33 native-template spike observed 2174ms for a first build in a new
worker process with new project signing, then 867ms for a same-worker/signer
warm build. See [exact measurements and resource limits](validation/compiler-measurement-a33-20261011.md).
Before the twenty-request evaluation, provisional build caps are **10000ms first**
and **5000ms warm**, from actual Build click to visible verified Install readiness,
including UI observation overhead and excluding inference and pre-click setup.
These are working caps with headroom over two observations, not measured
percentiles or production deadlines. A first build starts in a newly started
worker; warm requires unchanged worker process/start identity. Do not call first
OS-cache-cold or relax caps after an evaluation failure.

Before the full suite, a further provisional target is **1GiB for the sum of
kernel-reported controller/worker/largest-waited-child RSS high-water counters**
over a guarded build sequence. A fresh A33 thirty-build run measured612.574MiB,
first6632ms/warm1759..3059ms and thermal0, with all30 retained artifacts matching;
see [guarded resource evidence](validation/guarded-resources-a33-20261012.md).
AAPT2 separate-process creation is now restricted, which makes the component sum
useful as an envelope of reported counters. Linux RSS accounting is imprecise;
this is neither a page-exact/simultaneous physical-memory peak nor an enforced
RSS ceiling. Do not report it as exact benchmark peak memory or relax this target
after a failure. Total retained-storage, longer sustained-thermal, exact-memory
precision and full-suite performance gates remain pending.

Own-package storage accounting and a real2MiB cache conformance check passed on
A33 for each main builder UID. Separate after-snapshots summed71.877MiB;
cache is included in data. Before longer/full-suite evaluation, freeze a
**256MiB combined controller+worker appBytes+dataBytes target** for their own
user/data volume, including existing history and the retained4MiB cache fixtures.
This leaves about184MiB headroom from the measured spike. Excludes other-UID
installed generated apps/test packages, host toolchains and storage outside
queried volumes/packages. This is a provisional acceptance target, not an
enforced quota or whole-device footprint; no automatic pruning. Do not relax
it after failure. See [storage evidence](validation/package-storage-a33-20261012.md).
A fresh120-build guarded case passed in240.021s under unchanged10s/5s,
1GiB reported-counter and256MiB retained-package targets: first5519ms,
warm1669..3055ms, thermal0, reported sum614.676MiB, final package stats84.178MiB.
All120 retained APK hashes/sizes/source/signer receipts matched.600s sequence/
900s observer windows and MODERATE2 stop were configured, not deliberately expired
or triggered. See [longer evidence](validation/long-resources-a33-20261012.md).
These are bounded synthetic checkpoints; full-suite retention/performance,
long-term thermal, exact-memory, transient storage and eviction gates remain open.

No alpha pass is claimed yet. On 2026-10-02, the S23 Ultra reconnected after a temporary
ADB interruption. A specifically
approved live counter-label change compiled on the phone, survived controller
restart, was installed with user confirmation, and passed standalone increment
and saved-count checks on 2026-10-02. A same-signer phone-built version2 update
also retained that count and passed another increment/restart check; see
[the bounded acceptance record](validation/phone-conversation-20261002.md).
The intentionally generated crash report's local export, import, clear and
reimport also passed; previews were cancelled without a provider upload.
An independently created project then compiled/signed a changed snapshot with
airplane mode on, Wi-Fi off and no active default network. Its exact receipt
survived controller restart; explicit installation and standalone counter/
persistence checks passed with controller/worker stopped. Root also verified
user-restored networking. The observed Build-click-to-ready was1351ms; no
cold/warm, peak-memory or detached-laptop result is claimed.
Later live Decrement feedback passes single-label, subtraction and saved-count
checks after two separately recorded failed functional iterations. The frozen
Reset request compiles/signs on the phone and its verified USB debug update
retains count2, but Confirm leaves a stale visible value until restart. That
attempt remains rejected. Its separate observed-feedback fix passes immediate
Reset refresh, Cancel, restart persistence and the shared Increment/Decrement
state on version5. These functional trials have no performance cutoff yet.
Production installation still requires Android confirmation; verified USB test
updates have the user's persistent authorization. See STATUS and individual
benchmark ledgers for exact results. On 2026-10-11, the user-authorized Galaxy A33 5G passed bounded compiler,
confinement, locality and native same-signer update/persistence checks; see
[second-device evidence](validation/alpha-a33-20261011.md). These used synthetic
phone-side sources and no live model calls. The complete 20-request suite has not been
run on either device. No alpha pass is claimed.

The A33 subsequently passed numeric compiler-report transport, persisted repair
preview after controller force-stop, corrupt-origin approval rejection, Binder
busy/timeout recovery and a simulated-proposal repair through the actual phone
compiler. See [repair diagnostics evidence](validation/repair-diagnostics-20261011.md).
These checks made no model requests, do not measure the twenty-request suite,
and do not close the live free-only repair gate. A worker hard-link descriptor
case could not run because Android denied creation of that fixture.

The A33 also passed visible Stop during admitted worker input, source/no-readiness
restoration after force-stop, a fresh real compile/sign retry and receipt restart,
plus neighboring queued/lifecycle Stop and chat preview checks. See
[Stop and recovery evidence](validation/active-worker-stop-a33-20261011.md).
The input transport was controlled by a bounded test relay; this does not close
ECJ-phase or provider cancellation, process-death during an active build, live
repair, or the full alpha suite. No model request or generated installation ran.

The next A33 case passed actual controller force-stop during an admitted active
worker request, immediate UI recovery of the exact source/build/version, removal
of partial outputs, real native compile/sign readiness, and another receipt
restart. See [active-build termination evidence](validation/active-build-interruption-a33-20261011.md).
This bounded controlled-input case does not establish ECJ-phase cancellation,
spontaneous OS eviction, busy-on-reconnect behavior, low-storage recovery or the
full alpha suite. No model request or generated installation ran. After a
temporary host access restriction ended, completion ordering, all-four installed
APK comparison and own-process cleanup were verified.

The subsequent A33 busy-worker case reproduced and fixed irreversible FAILED
state on saved-build admission refusal. Automatic recovery and manual Retry after
bounded exhaustion preserved exact source/build/version through real native
compilation/signature/receipt; visible Stop during and after waiting retained
source without late readiness. Existing recovery/Stop/completion neighbors passed
on the final harness. See [busy-worker recovery evidence](validation/busy-worker-recovery-a33-20261011.md).
Pending setup was synthetic, while worker admission and compilation were real.
No model request, generated installation or performance measurement ran. These
bounded checks do not close actual low storage, spontaneous eviction, ECJ-phase
cancellation, live repair or the full alpha suite.

The A33 then passed a real receipt-write failure after native compilation/signing:
saved source and usable Build survived without Install/export readiness. An
externally verified controller force-stop preserved this state; a visible fresh
Build after preserving the fixture obstruction produced a valid same-signer v2,
whose exact receipt/APK survived another force-stop. See
[native receipt-write recovery evidence](validation/receipt-write-recovery-a33-20261011.md).
This uses a tiny controlled filesystem obstruction, not actual low storage or
ENOSPC. Production code was unchanged. No model request, generated installation
or performance measurement ran; the full alpha gates remain open.

The A33 next passed actual worker force-stop during admitted input: the controller
survived genuine Binder death/DeadObjectException, retained exact source1 and
offered no APK/receipt/Install/export. Fresh-process reopen rebound the worker;
visible fresh Build produced unchanged content at source2/version1 through the
real native compiler, signing and immutable receipt. Another reopen restored
that exact artifact. Recovery neighbors and one-shot rejection also passed. See
[worker Binder-death recovery evidence](validation/worker-binder-death-a33-20261011.md).
Production code was unchanged. Controlled input interruption does not establish
ECJ-phase cancellation, spontaneous eviction or screen-lock recovery. No model
request, generated installation or performance measurement ran; live free-only
repair and the full alpha suite remain pending.

The A33 also passed actual screen lock during admitted input: keyguard showing,
noninteractive state and lifecycle cancellation preceded release of the real
worker input. A late native build/sign completed without receipt/Install/export
readiness while the phone stayed locked. User unlock and fresh-process reopen
retained source1 without automatic rebuilding; visible fresh Build produced
source2/version1 with unchanged content and another restart restored the exact
APK/public signer. See [screen-lock recovery evidence](validation/screen-lock-recovery-a33-20261011.md).
Production code was unchanged. No model request, generated installation or
phone performance measurement ran. This does not establish DeviceIdle/Doze,
ECJ-phase cancellation, detached execution or the full alpha acceptance suite.

The A33 subsequently passed actual visible Stop during observed real ECJ
compilation: 603 same-thread stack samples bracketed the entire click, with
conservative nearest gaps 1.575038/0.6975ms. The late real signed APK retained
source1 but published no receipt/Install/export readiness. Fresh-process reopen,
a separate actual Build of unchanged content at source2/version1 with the same
signer, and exact ready reopen passed. See [ECJ Stop evidence](validation/ecj-phase-stop-a33-20261011.md).
Worker-test stack sampling and host activation signal are explicit assistance;
this verifies controller readiness cancellation during ECJ execution, while the
worker completes. It establishes neither immediate CPU abort nor a compiler
performance measurement. No production change, model request, generated install
or alpha ledger entry occurred. Full-suite, live repair, ENOSPC, spontaneous
OS eviction and broader resource gates remain open.


A further A33 local spike built 53400-byte synthetic Java source six times, with
exact source1..6/version1..6 package/policy/signature/receipt and same signer:
profiled first 6660ms, warm 2990/2511/2363/2173/2046ms, within unchanged 10s/5s caps.
Ready reopen and recovery/completion neighbors passed. Final owned project
retained 528761 logical file bytes/829440 allocated bytes including directories;
each warm iteration retained another 86765–86766/126976 bytes. Worker sampling observed
nine direct children over 866 batches, with max sequential batch RSS 217704KiB,
worker observed VmHWM 219380KiB and controller VmHWM 186536KiB. Two discovery reads
failed; profiler/verification overhead and missed children remain limitations.
See [exact larger-build resource evidence](validation/build-resources-a33-20261011.md).
These are not exact simultaneous whole-pipeline peaks, total workspace size,
long-term retention or sustained thermal limits. Thermal status 0 at build
boundaries is not measured temperature. Resource caps remain unset where evidence
is inadequate; no alpha denominator entries or full resource gate closure are
claimed. No model or generated installation ran.


On 2026-10-11 a fresh A33 owned-workspace run passed six actual53400-byte native
builds at5901/3064/2652/2414/2259/2238ms, unchanged10s/5s limits, exact source1..6/
version1..6 signatures/receipts/signer and restart/guard/neighbors. Owned root
allocated950272bytes, selected project829440bytes; sampled scratch maximum771KiB.
Host numeric sampling avoided worker profiler instrumentation but obtained no
complete child memory reads. Exact whole-pipeline memory, long-term retention,
sustained thermal and full alpha gates remain open. No model request, generated
installation or alpha-ledger entry. See [workspace evidence and exclusions](validation/workspace-resources-a33-20261011.md).


On 2026-10-11 UTC (2026-10-12 Dubai), the A33 passed one live free-only compiler-
error repair of a test-assisted real compiler failure through production Send &
repair, cloud response, phone source2/version2 compile/sign/receipt, exact USB
debug install and standalone native Counter0→1→2/restart2. No developer source
repair or second Send; original test/verifier failures remain preserved. Usage
4673tokens, cost unknown; no live latency retained. See [live repair evidence](validation/live-repair-a33-20261011.md).
This is neither the frozen S23 Step repair nor an alpha-suite denominator entry.
Full-suite, resource/thermal, actual low-storage/spontaneous eviction, provider
cancellation, detached-laptop and distribution/cohort gates remain open.


On 2026-10-11 UTC (2026-10-12 Dubai), bounded live free-only provider Stop passed
on A33 during observed real production HTTPS response waiting. The original
executor settled CANCELLED/source0 with exact saved content, no receipt/Install/
export/repair or automatic build. Fresh reopen and visible local Build produced
unchanged content at source1/version1 through actual phone compilation/signing;
another reopen verified the exact artifact/receipt/signer. Two distinct Sends
occurred: an original test wrongly expected PLANNING rather than EDITING and
failed before Stop; its independently verified READY artifact/history remains
preserved. A later local recovery click-driver failure also remains recorded.
No paid fallback or model request after the successful cancellation; cancelled
usage/cost unknown. This proves client cancellation/readiness suppression, not
remote inference abort. See [provider Stop and recovery evidence](validation/provider-cancellation-a33-20261011.md).
No generated installation or alpha-ledger entry occurred. Full-suite, broader
resource/thermal, actual low-storage/spontaneous eviction, cross-device encrypted
recovery, detached-laptop and distribution/cohort gates remain open.


On 2026-10-11 UTC (2026-10-12 Dubai), the final A33 thirty-build sequence passed
in66.038082375s: first6278ms, warm maximum3013ms, unchanged10s/5s caps, exact
source1..30/version1..30 signed APK policy/receipts and same project signer.
Real thermal listener/before/after pressure readings remained0. Worker2830
samples explicitly recorded unavailable child memory3, vanished status1 and
six incomplete batches; maximum available/complete-observed RSS batch217668KiB
is not an exact whole-pipeline peak. Final owned root allocated3997696bytes
is a bounded synthetic project's storage observation, not a retention budget.
The original observer failure remains failed; an actual child-exit regression
was reproduced red then passed after strict-identity/unavailable-memory handling.
All three projects/90 APKs were preserved. Final ready reopen, duplicate guard
and six neighbors passed. No model, generated installation or alpha-ledger entry.
See [sustained evidence and limitations](validation/sustained-resources-a33-20261011.md).
Whole-pipeline peak/retention/longer thermal limits, actual low storage/spontaneous
eviction, detached-laptop/cross-device recovery, two-device20-request suite,
redistribution and user-cohort gates remain open.


On October12 Dubai (October11 UTC), the user reported the prepared A33 project
completed local Build, normal Android-confirmed installation, two increments and
reopen at count2 while detached. Post-reconnect checks independently matched
source2/version1, exact signed receipt/APK/package/public signer/installed hash,
and fresh-process native Counter:2 with controller/worker stopped. Prior selection
was restored and all data retained. The earlier USB observer had expired while
connected; no USB-absence event was recorded for this attempt, so #19’s stricter
transport criterion remains open. No duration, alpha denominator entry, network
offline or full detached gate closure is claimed. See
[detached evidence and its limits](validation/detached-build-a33-20261011.md).
