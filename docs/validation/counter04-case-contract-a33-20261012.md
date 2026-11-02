# A33 fixed `counter_04` case contract

This adds one route for the frozen supported request in
`benchmarks/prompts.jsonl`. The prompt set and existing result rows remain
unchanged. The Android test asset is
`controller/src/androidTest/assets/alpha/counter_04.json`; the host contract
test checks semantic equality with the prompt entry and pins request SHA-256
`c2dfa3094bc90bf3142eeb540888fc2aff30c76f36c428eb0610b1acc3028a6f`.

The one-shot instrumentation argument is `a33Counter04`; its phases are
`prepare`, `baselineBuild`, `pinBaseline`, `preview`, `send`, `ready`, `failed`,
`restoreSelection` and `verifySelection`. The fresh project/run is
`a33_counter04_20261012_01`; it has separate preferences from the other fixed
cases.

1. `prepare` saves the prior project/chat selection, creates a fresh project
   and chat, verifies pristine source R0 and empty chat/journal, then returns
   with the ordinary source editor ready. It does not supply or seed source.
2. `baselineBuild` authors a fixed, independently specified native Java TEST
   fixture into the ordinary editor in that phase, so it does not depend on
   unsaved text surviving an Activity/process restart. The fixture has
   Increment and Reset controls, persists its main count, displays `Counter: N`,
   and has no lifetime-tap value. This is explicit test assistance, not human
   authored baseline evidence. It enters the editor with `setText`; only the
   ordinary visible Build action commits R1. The probe never writes or seeds
   ProjectSourceStore or a revision snapshot. Its source outline is:

   ```java
   package dev.smallwright.generated.<fresh-project-id>;

   import android.app.Activity;
   import android.os.Bundle;
   import android.widget.Button;
   import android.widget.LinearLayout;
   import android.widget.TextView;

   public final class MainActivity extends Activity {
     private int mainCount;
     private TextView value;
     private void showCount() { value.setText("Counter: " + mainCount); }
     @Override public void onCreate(Bundle state) {
       super.onCreate(state);
       mainCount = getPreferences(0).getInt("mainCount", 0);
       LinearLayout box = new LinearLayout(this);
       box.setOrientation(LinearLayout.VERTICAL);
       value = new TextView(this);
       box.addView(value);
       Button increment = new Button(this);
       increment.setText("Increment");
       increment.setOnClickListener(v -> {
         mainCount++;
         getPreferences(0).edit().putInt("mainCount", mainCount).apply();
         showCount();
       });
       box.addView(increment);
       Button reset = new Button(this);
       reset.setText("Reset");
       reset.setOnClickListener(v -> {
         mainCount = 0;
         getPreferences(0).edit().putInt("mainCount", mainCount).apply();
         showCount();
       });
       box.addView(reset);
       setContentView(box);
       showCount();
     }
   }
   ```

   Replace the package placeholder with the fresh project's package. The probe
   stages this fixed fixture, hashes the exact authored source and complete R1
   snapshot, and reports `testFixtureAuthoredByProbe=true`,
   `sourceStoreSeededByProbe=false`, and `testAssisted=true`.
3. `baselineBuild` first verifies pristine R0 in storage and no journal entry,
   then stages the fixed fixture in the editor at revision 0. It checks the
   package and Increment/Reset labels, then clicks the visible normal Build
   action once. It requires R1 bytes to equal the fixture exactly, no model
   journal, a chat-bound manual-build origin, policy-verified v1 receipt and
   same-project signer. It pins both the raw authored-source hash and complete
   R1 snapshot hash.
4. The parent hash-checks and debug-installs that exact phone APK. With the
   controller and worker stopped, native UI observations must show main count
   `0 → 1 → 2`, Reset to `0`, relaunch at `0`, then Increment twice and relaunch
   at `2`. Record these observations separately from compiler evidence. Invoke
   `pinBaseline` after installation; it verifies installed bytes/version/signer
   against the phone receipt and pins immutable v1 identity.
5. `preview` loads only the untouched fixed request, requires source R1 and the
   pinned v1/chat/origin, opens the normal context/approval dialog, then cancels
   it and records the exact context hash. For `send`, first make the live
   provider catalog check that the selected model remains free, then pass the
   existing exact preview hash and `freeOnlyApproved=true`. The route retains
   exact context, revision and one-shot guards. It makes one initial request;
   it never launches a compiler repair or functional-feedback rescue.
6. `ready` or `failed` reopens the pinned outcome and checks its real journal,
   source revision and result. On ready it independently validates the phone
   v2 receipt and same package/signer while retaining the immutable v1
   artifact identity. After HEAD advances, the route reads only bounded R1
   source paths directly from the pinned revision directory, rejects symlinks
   and foreign paths, and checks the full snapshot, authored-source, and
   manual-origin source digests; it does not ask the current-revision API to
   read historical bytes. The route does not install generated output. The
   parent verifies and debug-installs that exact v2 APK without clearing app
   data.
7. With both builders stopped, record the retained main count `2` and the
   displayed lifetime value as `L0`; do not assume L0 is zero or define its
   migration. Check one Increment gives main `3` and lifetime `L0+1`; Reset
   gives main `0` while lifetime remains `L0+1`; two Increments give main `2`
   and lifetime `L0+3`; relaunch retains `2` and `L0+3`. Mark functional
   acceptance only after every observation passes. Finally call
   `restoreSelection` and `verifySelection` and retain their output.

`restoreSelection` is a separate one-shot phase: it requires the exact owned
project/chat already selected in both UI and active-project preference, a
settled source/journal/outcome, the immutable R1 origin and v1 receipt, and the
installed v1 artifact after failure or the exact settled receipt after success.
It checks the immutable v1 artifact and full R1 origin in all outcomes, while
requiring installed v1 only before the update (and after a failed outcome).
After success, it checks the exact settled receipt against installed bytes,
version, signer, and the loaded ready UI. It persists its once flag before
restoring the prior project/chat. A separate one-shot `verifySelection` checks
both UI and preference state.

The global `UsageBudget.MAX_REPAIRS=3` remains unchanged. Any eligible compiler
repair belongs in a separate reviewed slice with an exact request context,
fresh free-only approval and preserved original outcome. Compile errors,
timeouts, cancellations and policy failures remain their observed outcomes.
Physical device/model/build/install evidence is owned by the parent and is not
claimed by this source proposal. `debug_installed` remains distinct from
Android-confirmed production installation and human alpha signoff.

## Combined host candidate

The source branch also contains the reviewed counter03 repair2 continuation.
The combined offline host gate passed on 2026-10-13: one `spotlessApply`, then
`spotlessCheck :controller:lintDebug :controller:assembleDebugAndroidTest`,
with one Gradle worker and 1536 MiB heap/512 MiB metaspace. The combined
immutable controller test APK is retained at
`toolchains/repair2-counter04-hostproof-20261013/controller-debug-androidTest-0e035252afb631574b7fe1c6a259da553952929be97a424325c964f48a66a41e.apk`.
SHA-256 is
`0e035252afb631574b7fe1c6a259da553952929be97a424325c964f48a66a41e`; package
is `dev.smallwright.controller.test`, min SDK 31, target SDK 36, and signer
certificate SHA-256 is
`19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25`. The
manifest has no explicit version code/name. Lint reported zero errors and 77
warnings. Inspection, Gradle logs and lint output are retained in that ignored
host-proof directory. No phone, model, parser or generated-app action occurred
in this host checkpoint; the parent owns any later device validation.
