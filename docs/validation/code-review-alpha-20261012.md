# Alpha integration review — 2026-10-12

The `implement-spec` workflow uses separate Standards and Spec reviewers.
This is a checkpoint review; open alpha/toolchain requirements remain open.
Fixed point: `08025d9114e73fadabe1385c837bb06b01cea830`.
First frozen tip: `201bed7e7d274b77b787bec99e057e5454850de8`.
Both refs resolved and the diff was nonempty:

```bash
git diff 08025d9...201bed7
git log 08025d9..201bed7 --oneline
```

The eleven-commit range includes detached A33 verification, two-project chat
acceptance, a raw source-owner slice, the complete published-source reference,
component notices, and direct-local-Build chat provenance. The two source/API
checkpoints are independently scoped evidence, not normal-worker promotion.
Reviewers read the approved plan, repository standards, and actual originating
GitHub issue bodies #1, #2, #19 and #20. Reports/diff/commit list remain in
`/tmp/phone-local-alpha-{standards,spec}-review-20261012.md` and
`/tmp/phone-local-alpha-spec-{diff,commits}-20261012.*`.

## Standards

No documented-standard violations were found. One low-confidence Fowler
Duplicated Code heuristic: `ManualBuildOriginStore.digestSource` repeats the
sorted, length-framed source SHA-256 encoding in `RepairOriginStore`. The
reviewer distinguished separate provenance domains from a hard violation.
The parent requested one small shared digest definition to prevent encoding
drift, with unchanged record/publication rules and existing meaningful tests.
Remediation `37c5142` extracts only `SourceSnapshotDigest.compute(Map)` and
uses it in `ProjectSourceStore` and `RepairOriginStore`; manual/repair record
checksums, serialization and publication rules remain unchanged.

## Spec

Zero actionable checkpoint findings: direct Build captures project/chat before
queueing, saves staged-source origin before HEAD, and binds candidate bytes
before the signed receipt. Stop and IO guards avoid verified-success claims;
legacy origins remain unknown. Current-revision access and retained historical
metadata are explicitly distinguished.

The reviewer checked real RED/GREEN/fresh-process/guard/neighbor records, source
and APK pins, and actual origin-worktree XML: 223 controller, 57 worker and four
protocol tests, zero failures/errors. Root test XML was older, so it was not
used for the 284-test claim. Seeded chat histories, controlled queue switching,
user-reported detached actions and fresh physical observations are kept distinct.

Explicit remaining spec requirements: #2 complete conversational acceptance;
#19 observed transport disappearance; #1 licensed complete API/resource inputs,
source delivery and phone promotion. The two-device fixed twenty-request suite,
broader resources/recovery and distribution gates remain open.

First round: Standards one heuristic, zero documented violations; Spec zero
actionable findings, with the above remaining gates.

## Final checkpoint re-review

Frozen tip: `37c514245e295be8ba1563219f9e1315c922cfa4`; the same fixed point.
Both reviewers inspected the complete fourteen-commit range independently.
Reports are `/tmp/phone-local-alpha-{standards,spec}-review-02-20261012.md`;
the full final diff and actual commit list are the corresponding
`/tmp/phone-local-alpha-spec-{diff,commits}-02-20261012.*` files.

### Standards

Zero hard documented violations. The duplicated source-digest concern is
resolved: sorted String paths, UTF-8 bytes, big-endian lengths, exact source
bytes and SHA-256 framing remain identical. The final reviewer also noted a
borderline STATUS-density concern, explicitly not a clear violation. The parent
shortened the current summaries and retained links to full evidence; the
user-owned tooling footer was preserved. The Standards documentation follow-up
(`/tmp/phone-local-alpha-standards-doc-followup-20261012.md`) found no new hard
violation or material evidence omission. Its minor RED/GREEN wording ambiguity
was clarified: the initial missing-origin failure was reproduced, then the fix
passed.

### Spec

Zero actionable findings or evidenced incorrect implementation/scope creep.
Captured identity, source binding, immutable candidate-before-receipt ordering,
Stop/IO suppression and unknown legacy records remain intact. The reviewer
matched changed source/APK pins, retained source2/version2 metadata recovery,
two physical neighbor streams and actual XML for 223 freshly executed controller
tests; worker57/protocol4 are correctly cached results. This compatibility check
is separate from the earlier fresh generated Build RED/GREEN.

New five-file framework license research explicitly keeps grants/lineage
unresolved; it performs no API runtime work or worker promotion. The open spec
gates above remain open. Neither reviewer ran a build, phone action, model call,
network mutation or issue update.

Final code checkpoint: Standards zero documented violations, digest heuristic
resolved (one borderline documentation-density observation addressed); Spec zero
actionable findings. No whole-alpha completion is claimed.


## Exact implementation paths and retained evidence

The originating-chat checkpoint changes these files, relative to the repo root:

- `controller/src/main/java/dev/smallwright/controller/MainActivity.java`
- `controller/src/main/java/dev/smallwright/controller/ProjectSourceStore.java`
- `controller/src/main/java/dev/smallwright/controller/ManualBuildOriginStore.java`
- `controller/src/main/java/dev/smallwright/controller/RepairOriginStore.java`
- `controller/src/main/java/dev/smallwright/controller/SourceSnapshotDigest.java`
- `controller/src/test/java/dev/smallwright/controller/ManualBuildOriginTest.java`
- `controller/src/androidTest/java/dev/smallwright/controller/ManualBuildOriginProbe.java`
- `controller/src/androidTest/java/dev/smallwright/controller/ArtifactPolicyInstrumentation.java`
- `docs/TESTING.md`
- `docs/STATUS.md`
- `docs/validation/manual-build-origin-20261012.md`
- `docs/validation/code-review-alpha-20261012.md`

Detailed related checkpoint paths are in their linked validation records.
Three owned clean implementer worktrees were removed without force after all
non-secret host APK/test/lint/log backups were rehashed; feature branches remain.
Backup manifest and cleanup receipt are in the ignored manual-origin case.
Final preserved XML was independently re-parsed; module counts remain
223/57/4 with zero failures/errors, and execution/cached distinctions remain
explicit. No user files, signing targets or phone projects/artifacts were removed.
