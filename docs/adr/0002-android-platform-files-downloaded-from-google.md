# 0002. Android platform files are downloaded from Google during setup

Date: 2026-10-18. Status: accepted.

Generated apps could use only the parts of Android in our own hand-written
copy (about 128 classes in `SourceStubs`), so builds failed on ordinary
Android calls and the user judged the result "a toy". We can't ship Google's
`android.jar` inside the harness: the SDK terms (sections 3.1 to 3.5) allow use
to develop Android apps but not copying or redistribution. So setup gets one
step, "Accept and download": it shows Google's SDK terms, and on acceptance the
controller downloads Google's official `platform-36_r02.zip` straight from
`dl.google.com/android/repository/` (65,878,410 bytes, published SHA-1
`2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576`). It checks the checksum, keeps
`android-36/android.jar` (27,768,026 bytes, with `resources.arsc`) unchanged,
and hands it to the worker as a compile-only input for ECJ and `aapt2 link -I`.
The worker keeps no Internet, and nothing in the file is loaded or run. The
hand-written copy and the experimental bundled inputs are deleted, so the real
platform is the only one built and tested against.

## Considered options

- Bundle `android.jar` in the APK, as Code on the Go, CodeAssist and Sketchware
  Pro do, or re-host it, as AndroidIDE did. Rejected: that is the
  redistribution the terms forbid.
- Build an equivalent jar from AOSP source. A host build matched all 6,227
  classes, but shipping it needs per-component license work. It stays the
  fallback if Google's terms are ever read against the download route.
- Read the framework from the phone itself. Rejected: it needs a dex-to-class
  converter and hidden-API filtering, the riskiest engineering of the four.

## Consequences

- First setup needs a 66 MB download and the person's acceptance; until it
  finishes, chat answers work but builds don't.
- Using the file unchanged is part of the decision. Trimming or repacking it
  would be the "modify" the terms forbid.
- A Play edition needs review: Play bans downloading JAR files from outside
  Play, even though this one is only read as data.
