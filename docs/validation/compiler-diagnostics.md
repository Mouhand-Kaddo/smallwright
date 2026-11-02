# Source-bound compiler diagnostics

The worker captures up to eight actual ECJ 4.12 error records for the exact
admitted MainActivity file: numeric problem ID, category and source line. The
immutable report includes the SHA-256 of that source and an explicit truncation
flag. It contains no compiler messages, arguments, file paths, identifiers or
source snippets. Other compilation units, including authored platform headers
and generated resource classes, do not supply MainActivity diagnostics.

`EcjDiagnostics.compile` is the small worker-local interface. Its private ECJ
`Main` requestor captures real `CategorizedProblem` objects and delegates to
ECJ's original requestor, which owns error counting, logging and class output.
Existing fixed Java arguments and bounded local diagnostic writer are retained.
The actual snapshot compiler calls this interface with its admitted launcher
source. The existing three-argument Java compiler seam remains compatible.

Legacy protocol1 and its exact `COMPILE_ERROR` status remain supported.
Protocol2 negotiates a bounded numeric-report descriptor before one actual
build. The controller verifies it against the leased snapshot, pins it to
project/run/build/revision, and independently revalidates it for repair preview
and approval. Physical A33 restart, Binder and local repair evidence is recorded
in [repair diagnostic integration](repair-diagnostics-20261011.md). No live
model request ran. The pending S23 source6 failure has no retrospective detailed
report; old runs remain generic.

## Local evidence

The first regression genuinely fails before implementation: the explicit
admitted-source compiler overload and typed report are missing. After capture
is implemented, the same actual malformed Java fixture passes. It compiles
against the worker's authored source headers and empty boot directory, with no
SDK JAR. Its fixed status remains unchanged and its report has actual syntax
category20 at line2 and an exact independently computed source hash.

Seven actual ECJ tests pass, covering syntax, pinned UndefinedType ID16777218 /
category40, immutable records, foreign-unit exclusion, eight-record truncation,
valid class emission with warnings, carriage-return line numbers and an absent
MainActivity in the admitted inventory. That last regression genuinely fails
with NoSuchFileException before its fix; only sources in the actual inventory
enable collection, preserving the original compile/missing-launcher behavior
and fixed compile-failure sentinel. Existing
worker privacy and source-header compilation regressions pass alongside them.
An intermediate test compilation fails because Android's compile interface
lacks `Files.writeString`; the test is corrected to `Files.write`, and that
failed log is retained. It is not a compiler-behavior RED result.

```bash
source scripts/env.sh
./gradlew :worker:testDebugUnitTest --tests dev.smallwright.worker.CompileDiagnosticsTest --tests dev.smallwright.worker.WorkerFailureClassificationTest --tests dev.smallwright.worker.SourceStubsCompileTest --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

Ignored evidence: `toolchains/product-phone-20261002/compiler-diagnostics-red-01.log`,
`compiler-diagnostics-green-01.log`, `compiler-diagnostics-validation-02.log`
and `compiler-diagnostics-validation-03.log`.
The absent-source RED is `compiler-diagnostics-red-absent-main.log`; complete
checkpoint checks are `compiler-diagnostics-checkpoint.log` (32 seconds):
210 controller / 56 worker tests pass, no failures/errors/skips; controller
lint has zero errors/73 warnings and worker zero errors/two warnings. No
warnings are suppressed. Worker and worker-test APK assembly passes.

## Physical gate and subsequent repair integration

On 2026-10-11, the unchanged `compileDiagnostics` instrumentation passed on the
user-authorized Galaxy A33 5G / Android16 after a real path-alias regression
identified and fixed missing diagnostic capture. See [physical evidence](alpha-a33-20261011.md).
Under the actual worker UID it checks numeric errors/hash,
unchanged source, real snapshot COMPILE_ERROR with zero artifact and scratch
cleanup, then valid class recovery. It subsequently runs the existing actual
two-project Java/XML snapshot/APK/DEX probe. The ART gate has physical second-device evidence. The subsequent repair
integration is recorded separately below; the S23 Ultra frozen Step flow was not run.

```bash
adb -s "$PHONE_SERIAL" shell am instrument -w -r -e mode compileDiagnostics dev.smallwright.worker.test/dev.smallwright.worker.ConfinementInstrumentation
```

The implemented integration preserves version1 status behavior and adds an
optional diagnostic output negotiated before dispatch. Its descriptor must be
an empty, writable, singly linked regular file distinct from source and APK
inodes. The existing caller authentication, admission lock and snapshot deadline
apply; all transferred descriptors close on failure and busy rejection. Exact
wire framing caps reports at135 bytes and eight records, rejects wrong source
hashes/line numbers, and contains no worker prose or paths. Reports are checked
against actual source bytes from the still-held build lease, never a second
build. Signed ECJ problem IDs remain supported.

Detailed records are persisted only from actual compiler failures with the
matching BUILDING identity. Repair provenance includes the whole source map,
chat/request, revision and build ID. Preview and both proposal approval checks
reload current provenance; only the captured REPAIR_NEEDED snapshot or its
single EDITING advance is permitted. Source/report replacement invalidates
approval. Old origins stay generic and are never backfilled from a new fixture.
See [actual host/phone results and limitations](repair-diagnostics-20261011.md).
