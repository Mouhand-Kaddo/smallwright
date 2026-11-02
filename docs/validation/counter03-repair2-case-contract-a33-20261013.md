# A33 frozen `counter_03` repair 2 contract

This is one test-only continuation of the actual A33 frozen `counter_03`
compiler failure and its actual first repair failure. It uses the same project,
chat, frozen request, production run, free-only model and v1 baseline. It does
not create a project or generation, change the request or cap, add feedback,
allow developer edits, install an APK, or alter the prior repair probe, pins or
results.

## Required predecessors

The initial case is
`a33-suite-counter-a33_counter03_20261012_01`: project
`appc7df5a374abf4be69cb4367c58e88e01`, chat
`7a8d3451430e494996e5ab9187a4c51a`, production run
`run_68b777296e0248c7b7e3d2d6a5acdb31`, source R1→R2,
`REPAIR_NEEDED` sequence 5, one compiler-failure count and no repair Send.
Its full R2 source hash is
`df0b926a1fd629ec9a016bf7bb133bd354565ea64dea530d9630dcc9d4cdbb65`.

Repair1 is the saved actual result at
`toolchains/alpha-goal-20261012/counter03-repair1/send-handle-actual-01.json`.
The phone probe independently requires its pinned result: the same run advanced
R2→R3, remained `REPAIR_NEEDED` at sequence 9, recorded two compiler
failures, and made one repair Send. Its full R3 source hash is
`d1e048ca0e27efb11f51667a0cf95539f65aa1463f9fe192fc6fd4d5cb0d0625`.
Repair1 recorded 6,871 provider tokens for that repair and 12,303 cumulative;
cost is unknown. The R3 result has no artifact. The original failed project and
chat must still be selected when repair2 starts; repair1 selection restoration
must not have run.

Repair2 outcomes retain the original initial-failure journal sequence 5 in
`initialFailureJournalSequence` and record repair1's sequence 9 separately as
`predecessorJournalSequence`.

The immutable manual v1 APK, package, signer, source R1 origin and installed
bytes/version/signer remain pinned from the initial case. Every phase validates
the original failed R2 and repair1 failed R3 history and this baseline.

## Instrumentation contract

Instrumentation argument `a33Counter03Repair2` selects
`inspect`, `preview`, `send`, `ready`, `failed`,
`restoreSelection`, or `verifySelection`. It uses the new pins namespace
`a33-counter03-repair2-20261013`; it does not write repair1 or initial-case
pins.

The history reader inventories R1, R2 and R3 without changing HEAD. It rejects
symlinks and paths outside the target app's private files directory, non-regular
or foreign files/directories, and snapshots exceeding seven files, 64 KiB per
file or 252 KiB total. Every inventoried file is read using
`ProjectStore.readFile(revision, path)`. It checks complete snapshot hashes,
and uses R1's exact bytes for the pinned manual-origin digest.

`inspect` revalidates both saved predecessors, the same production run and
sequence 9, R3 hash, chat-bound repair origin, diagnostics and v1 baseline. It
returns numeric problem IDs, categories and line numbers only. It does not
return source, diagnostic messages or UI hierarchy.

`preview` builds the ordinary repair context from the frozen original
request, source R3 and the verified failed run, shows it, cancels it, then saves
only its exact hash and byte count. Before the one Send, the parent must verify
the device, native count 2 after relaunch, and a fresh provider catalog showing
zero prompt and completion price for
`cohere/north-mini-code:free`. The probe still requires the exact preview
hash, chat-bound origin, baseline, parent-passed free-only approval and ordinary
`Send & repair` confirmation. Missing approval, wrong or stale hash, changed
context and duplicate Send must fail before model mutation. The attempt flag is
durably reserved before the sole click. There is no retry, Stop, paid fallback,
feedback update, developer source edit, source export or second Send.

That one Send uses the same production run and may advance source R3→R4 once.
On readiness, the probe verifies the actual R4 receipt and settled journal,
checks the actual version is above v1 and the signer matches the project key,
and leaves the APK uninstalled. It never assumes a version number from the
source revision.

If the Send ends in `REPAIR_NEEDED`, it must preserve source R4, journal R4,
the complete R4 hash and no artifact. The second repair compiler failure raises
`RunJournal.repairs` to 3; the global `MAX_REPAIRS=3` and
`MAX_ATTEMPTS=4` stay unchanged, leaving one final repair Send permitted for
a separate reviewed action. For `FAILED` or `CANCELLED`, preserve the exact current
source hash, current and journal revisions, journal state/sequence, bounded
outcome category and no-artifact result. Token delta is null unless both counts
are present and the final count is greater than the prior count. No outcome is
reinterpreted as success.

`restoreSelection` requires the repair project/chat to remain selected and
revalidates the original v1 baseline and settled outcome. It durably reserves a
one-shot restore before returning to the pinned prior project/chat. If a
parent-owned authorized debug update happened after a ready repair, it verifies
the exact installed R4 receipt bytes/version/signer; after a failed or
cancelled repair, it verifies the original installed v1 bytes/version/signer.
`verifySelection` independently verifies the one-shot restore and the saved
selection.

## Host checkpoint

On 2026-10-13, project JDK 21 and the root-local Android SDK passed offline
`spotlessApply`, then `spotlessCheck :controller:lintDebug
:controller:assembleDebugAndroidTest`, with Gradle workers capped at one and
the JVM capped at 1536 MiB heap/512 MiB metaspace. The compile gate compiled the
repair2 instrumentation source. Lint reported zero errors and 77 warnings.

The immutable test APK copy is
`toolchains/repair2-hostproof-20261013/controller-debug-androidTest-e22827a51ecc5eb276645914197345ac72c2da33c900b0acea642dec8c26a7a6.apk`.
Its SHA-256 is
`e22827a51ecc5eb276645914197345ac72c2da33c900b0acea642dec8c26a7a6`, size
1,021,816 bytes, package `dev.smallwright.controller.test`, min SDK 31, target
SDK 36, and signer certificate SHA-256
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`. The
manifest omits an explicit versionCode; Android's effective test APK version
code is 0. Inspection logs, lint output and the APK are retained in the ignored
host-proof directory. The first `spotlessApply` attempt failed before
formatting because this worktree had no signing links; that failure is retained,
and the approved ignored name-only links allowed the successful retry.

## Preserved runtime guard diagnosis

The parent installed only the test APK and exercised the premature `ready` and
actual `inspect` guards. Both retained results report
`stage=GUARD category=JSONException` with instrumentation result code 0:
`toolchains/alpha-goal-20261013/counter03-repair2/ready-premature-01.txt` and
`toolchains/alpha-goal-20261013/counter03-repair2/inspect-actual-01.txt`.
These are preserved guard failures, not passing phase results.

The initial saved outcome does not contain `baselineSignerSha256`; that pin
exists in the original case's SharedPreferences and was independently checked
by repair1. The repair2 predecessor validator now compares the saved repair1
signer to that authoritative preference. The subsequent baseline validator
still checks the immutable APK, signer and installed v1 bytes/version/signer.
No saved outcomes, source, journal, artifact or guard was loosened.

Both attempts stopped before preview or Send. No model request, source change,
compiler build, generated-app installation or functional check occurred. The
corrected probe needs parent source review before another host build or phone
action.

## Combined repair2 and counter04 host candidate

After the signer-binding correction was reviewed, the reviewed `counter_04`
route was merged into the same source branch. On 2026-10-13 the combined tree
passed one capped offline `spotlessApply`, followed by
`spotlessCheck :controller:lintDebug :controller:assembleDebugAndroidTest`.
Gradle used one worker, a 1536 MiB heap and 512 MiB metaspace. The logs and
lint report are in the ignored directory
`toolchains/repair2-counter04-hostproof-20261013/`. Lint reported zero errors
and 77 warnings.

The combined immutable test APK is
`toolchains/repair2-counter04-hostproof-20261013/controller-debug-androidTest-0e035252afb631574b7fe1c6a259da553952929be97a424325c964f48a66a41e.apk`.
Its SHA-256 is
`0e035252afb631574b7fe1c6a259da553952929be97a424325c964f48a66a41e`, size
2,076,258 bytes, package `dev.smallwright.controller.test`, min SDK 31,
target SDK 36, and signer certificate SHA-256
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`. The
manifest omits an explicit version code and name; this is a test APK. Static
inspection confirms the new `assets/alpha/counter_04.json` is packaged. The
previous APK `e22827a51ecc5eb276645914197345ac72c2da33c900b0acea642dec8c26a7a6`
and the two actual `JSONException` guard-failure outputs remain unchanged;
their verified digests are recorded in
`toolchains/repair2-counter04-hostproof-20261013/preserved-prior-evidence-sha256.txt`.
This host checkpoint did not install an APK or exercise a phone, model, parser,
or generated app. The parent owns independent APK verification and any later
test-only device update and guard retry.
