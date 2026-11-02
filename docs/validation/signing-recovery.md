# Signing identity and recovery boundary

`ProjectSigner` generates the `generated-counter-alpha` RSA private key in
Android Keystore for its default signing path. It now also has an explicit,
explicit migration method that checks the installed app's certificate,
creates a new portable identity and signing lineage, and stores that identity
encrypted in controller no-backup storage. The recovery export action now
activates this method when needed; existing builds use the old key until then.
The current phone and
emulator probes do not prove recovery after phone loss, app removal or a move
to another device.

Android documents that [Keystore key material remains non-exportable](https://developer.android.com/privacy-and-security/keystore).
Android also requires an update to carry the same signing certificate or a
valid proof of rotation, and uninstalling an incompatible app removes its
private data ([Android update rules](https://developer.android.com/google/play/app-updates)).
Therefore the source ZIP is useful for inspection and editing, and the signed
APK export can preserve one exact build. Neither can restore the
current private signing key or guarantee a future update from another device.
A separate encrypted recovery-file export now carries a new portable signer,
but import and different-device updates are not yet verified. Do not label
the ordinary source/APK exports as a backup of the generated app's update identity.

## Disposable lineage experiment — 2026-09-26

On the API 36 x86_64 emulator, `scripts/run-signing-lineage-probe.sh` passed.
It created two fresh Android Keystore RSA aliases, used the older key through
the pinned apksig 8.13.0 JCA interface to create an old-to-new signing
certificate lineage, serialized and restored that lineage, and checked that
the older signer retained installed-data capability without rollback
capability. The test then removed only its own disposable aliases. It did not
access the project's existing signer or export any private-key bytes.

This proves the local API can create and restore a lineage proof with
Android Keystore on that emulator. No APK was signed or installed by this
lineage-only probe. A subsequent APK rotation experiment is recorded below.
Android's
[v3.1 documentation](https://source.android.com/docs/security/features/apksigning/v3-1)
states that default rotation uses the new signer on Android 13 and later while
older versions keep the original signer. Generated apps currently support
Android 12; a portable recovery design must test the explicit v3 rotation
configuration on API 31/32 and 36, including a real retained-data update,
before claiming recovery for the supported range. The same documentation
describes older-platform rotation problems, so an API 36 pass cannot settle
that choice.

The next recovery design must preserve update continuity for existing
installs. A newly generated software key alone would change the certificate
and break updates to the current package. The disposable APK experiment below
tests signing-key rotation; an encrypted export/import of a future portable
key remains a separate implementation and test.

## Real APK rotation experiment — 2026-09-26

`SigningRotationApkProbe` compiled three versions of a disposable Java/XML
counter app through the real separate-UID worker on an API 36 x86_64 emulator.
The controller test created two fresh Android Keystore signers, signed v1 with
the old signer, signed v2 with the new signer and an old-to-new lineage, then
removed the old alias. It serialized/restored the public lineage and signed
v3 using only the new signer. All three APKs verified under apksig's API 31
and 36 verification modes. The generated APKs stayed on the emulator.

Dedicated API 31 and 32 AVDs passed v1→v2→v3, preserving the tapped counter
value `1`. Android reported version 2 and 3 with two certificates in the
signing history: the old one matched the actually installed v1 certificate,
and the new current certificate stayed identical from v2 to v3. Separate clean
API 31 and 36 AVDs passed direct v1→v3 with the same retained value and
history. An earlier API 36 full sequence passed before the certificate-digest
comparison was added. All runs used test-only ADB installation. The existing
project signer and generated-app data on the earlier development AVD were
untouched.

These runs show that the tested lineage path can preserve data after losing
the old signer on emulated API 31, 32 and 36. A portable,
passphrase-encrypted private-key export/import does not exist, and the current
project's Keystore key remains non-exportable. No physical S23 Ultra result or
user-facing rotated PackageInstaller flow is claimed.

An inactive `RecoveryEnvelope` component now authenticates and encrypts dummy
payloads on API 31 and 36 emulators. It is not wired to the signer, project
storage or a file picker, and it does not make the current identity portable.

An inactive `PortableSigningIdentity` component generates a disposable
software RSA key and self-signed certificate and validates their match after
PKCS#8 restore. Its host and API 31/36 emulator tests passed. It has not been
used to sign a generated APK, and the encrypted envelope has not carried it.
The user's existing Android Keystore signer remains non-exportable.

The subsequent portable-rotation instrumentation signed three real
worker-compiled APKs. API 31 passed v1→v2→v3, and API 36 passed direct v1→v3,
both with retained counter data and matching old/new signer certificates.
The test encrypted a disposable software key before deleting its old test
Keystore alias, then signed v3 using a decrypted/restored software key and
restored lineage. The encrypted bytes stayed on each emulator and were never
exported to another device. This verifies a candidate local migration path;
it does not implement or prove a user recovery export/import. The passphrase
is a hardcoded disposable test value and the original software key object
remains live during the probe, so this test does not establish backup
confidentiality or lost-device recovery.

An inactive `RecoveryBundle` now serializes only the fixed counter project
sources, revision/version metadata, disposable portable signer and optional
public lineage inside a passphrase-authenticated envelope. Its host parser
tests passed, and combined seal/open runtime probes passed on API 31 and 36
emulators with dummy source and a disposable software signer. An inactive
`PortableSignerStore` also passed API 31 and 36 emulator tests for encrypted
controller-private persistence, tamper/missing-wrap-key rejection and a
lineage ending at the stored signer. At that checkpoint neither component
changed production signing. The probes used hardcoded disposable phrases;
user-selected export and a different-device update were still open.

`ProjectSigner` now has a production migration core. The recovery export action
activates it explicitly when a portable identity is not already stored.
Its disposable product-path probe built v1/v2/v3 with the real separate-UID
worker on API 31 and 36 emulators, kept the counter value across updates and
showed that v3 can use the encrypted portable identity after the old disposable
Keystore alias is removed. Rejection probes require the installed certificate
to match the old signer and reject missing or unrelated signers before a build
can read an unsigned APK. Separate two-phase API 31 and API 36 runs installed
v1 before activation and then updated to v2 with the count retained. The first
API 36 attempt stalled behind an emulator System UI dialog; a second fresh
AVD passed after the runner gained dialog handling. These tests use ADB for installation,
disposable identities and emulator-local APKs. They do not test cross-device
recovery or the
physical S23 Ultra.

## Explicit encrypted export — 2026-09-26

The recovery bundle was extended to format v2 so it preserves all 2–7
validated project source files, not only the starter Java/XML pair. It bounds
file sizes and total bytes, encodes paths in sorted order, and validates that
nonempty public lineage ends at the bundled signer. It still opens the earlier
two-file format. Exact-byte source snapshots are read under one project lock.

The user-facing controller action asks twice for a phrase, activates the
portable signer if necessary, seals the complete snapshot and private signing
identity, then uses Android's document picker to save only encrypted bytes.
The controller reads the selected document back before reporting success.
The file excludes model credentials, run history and generated-app private
data. Cancellation after signer activation may leave the portable signer
active without an external backup; the UI tells the user to retry.

Disposable `RecoveryExport.prepare` probes passed on API 31 and 36 emulators:
an invalid phrase caused no signer activation, and a three-file snapshot with
exact unusual bytes was sealed and opened twice using one durable portable
identity. A separate API 36 UI run saved and verified a 12,790-byte `.plbr`
file through the Android document picker. The file and signing identity stayed
on that emulator. No lost-device, cross-device import/update, physical-device
or user-generated phrase-strength result is claimed.

Android's [v3 documentation](https://source.android.com/docs/security/features/apksigning/v3)
still advises against key rotation on API 31 and earlier because signature
comparison behavior differs from Android 13+. The API 31 result here proves
this generated app's install and data retention on one emulator image; it does
not establish compatibility for apps relying on signature permissions or
`checkSignatures` on that platform.

Until import and cross-device recovery are verified, state that limitation
before any device migration claim. The recovery file must be retained with its
phrase; ordinary source/APK exports do not replace it.

Do not read, log, copy or send the existing private key during this
investigation. No key bytes were inspected for this note.
