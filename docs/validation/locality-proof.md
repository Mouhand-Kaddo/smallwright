# Locality evidence for the phone compiler alpha

This record distinguishes observed phone-local effects from the remaining
end-to-end tests. The target was the connected Samsung Galaxy S23 Ultra on
Android 16. The harness APKs were built on the laptop; the generated APK in
this record was compiled and signed on the phone.

## Observed on 2026-09-26

The instrumentation command was:

```sh
toolchains/android-sdk/platform-tools/adb -s <S23 serial> shell am instrument -w -r \
  -e localityProofOnly true \
  dev.smallwright.controller.test/dev.smallwright.controller.ArtifactPolicyInstrumentation
```

It returned `INSTRUMENTATION_CODE: -1`. The probe created an isolated source
revision in controller-private test storage, changed the default button label,
bound the actual installed worker through Binder, and called the local build
coordinator. It checked that the worker UID differed from the controller UID,
that Android denied the worker `INTERNET` permission, and that the worker
manifest did not request it. The controller UID was `10465` and the worker UID
was `10464` in the final run.

The controller verified the signed generated APK. Android's archive parser
reported no requested or declared permissions, and the APK's DEX contained the
changed label marker. SHA-256 of that test APK was
`36f24eb99307d005a3b241a50ad78cbfcc2a73bf1c8c40bfbd86a138a54b1ef2`.
It remained at
`/data/user/0/dev.smallwright.controller/files/locality-proof-6561830453805040844/projects/counter-alpha/artifacts/counter-v1.apk`.
The probe did not install it, request a model, or read a credential.

A later `localityProofOnly` run on the same S23 Ultra with the updated crash
template again observed controller UID `10465`, worker UID `10464`, denied
worker Internet permission, changed revision 1 and a signed APK declaring no
permissions. The private APK's SHA-256 was
`9ad6876bec1590f92e7176322d3a6c40af6a0a551daf8a70d6c3e46f7e998153`.
The installed worker APK's on-device SHA-256 matched the local artifact:
`1132c1f0e7e97dca3a2a9393778dff8ec3d1661be456832e805d8c83cdc0649b`.
This repeat did not install the generated APK or disable phone networking.

The compiler worker is a separate installed package with a signature-protected
Binder service. The controller passes one snapshot and receives an unsigned
APK through bounded file descriptors; the controller validates and signs the
output. Relevant implementations are `worker/src/main/AndroidManifest.xml`,
`worker/src/main/java/dev/smallwright/worker/BuildService.java`,
`controller/src/main/java/dev/smallwright/controller/LocalBuildCoordinator.java`,
and `controller/src/androidTest/java/dev/smallwright/controller/LocalityProofProbe.java`.

## Evidence still needed

- Capture controller network destinations during one approved model request.
  The worker's denied Internet permission is strong enforcement evidence, but
  this run did not inspect actual network traffic.
- Build a changed revision while phone networking is disabled, with the cached
  packaged toolchain. This run left normal device networking in place.
- Install or update a generated app with explicit Android confirmation, stop
  the controller, and confirm the generated app still launches and keeps its
  data. This probe did not install its APK.
- Repeat the user-facing build/install flow after detaching the laptop. ADB
  here was used only to start and inspect a test; it is not a required runtime
  component of the controller or worker.
- Distinguish the model context sent to OpenRouter from local tool effects in
  one live evidence record. The specifically approved first `read_file`
  attempt on 2026-09-26 ended in a provider-failure category before any tool
  preview, so it did not complete this evidence record. A retry needs its own
  approval and later phone access.

No benchmark timing, energy, or network-volume measurement is inferred from
this probe.
