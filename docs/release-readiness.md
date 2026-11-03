# Public release

Updated 2026-11-02. The app is renamed Smallwright (`dev.smallwright.*`).

## Done

- Apache-2.0 `LICENSE`, root `NOTICE`, `README.md`.
- Personal paths, phone serials and the informal project name removed from
  tracked files.
- Third-party notices: R8 holds no Trove4J or JAXB code; ECJ's EPL-2.0 text and
  source location, apksig's LICENSE, and every controller library are in
  `toolchain/licenses/`.
- The worker ships the AAPT2 built from AOSP source (`ed917a…`), pinned in
  `toolchain/manifest.json` and checked at build time. Release builds are
  arm64-v8a only.
- Release key created (`signing/release.jks`, SHA-256 `8f8190a9…363ee4`);
  release APKs built, installed on the A33 and used for one build and install
  ([record](validation/smallwright-a33-release-20261102.md)).
- The builder binds the worker only when both are signed with the same key.
- `scripts/bootstrap.sh` downloads and checks the build tools.
- Adaptive launcher icons for both apps (a phone slab with a ">" for the
  builder, a cursor square for the worker), also used as the themed icon, and
  a 512 px store icon in `fastlane/metadata/android/en-US/images/icon.png`.
- OpenAI-compatible model servers besides OpenRouter (ADR 0009), tested on the
  A33 with Claude Haiku 5.5 through Anthropic's endpoint.

## Published 2026-11-02

- Public repository https://github.com/Mouhand-Kaddo/smallwright, from one
  commit with the GitHub no-reply address; no domain or organization. The app
  ids keep `dev.smallwright.*` without owning the domain.
- Releases: `v0.1.0` (pre-release) with both APKs and `SHA256SUMS`, and
  `aapt2-android-16.0.0_r4` with the AAPT2 binary `bootstrap.sh` downloads.
  Anonymous downloads matched the tested hashes.
- The release key is backed up by the user, encrypted, off this laptop.
- The USPTO search for SMALLWRIGHT found no live or dead marks (user). The EU
  office is not searched. The earlier name, Palmwright, was dropped because
  "Palmwright Software LLC" (Florida) sells custom software at palmwright.com.

## 0.1.1, 2026-11-03

- `v0.1.1` is GitHub's latest release, with `smallwright-0.1.1.apk`
  (`774671e9…`, version code 4) and `SHA256SUMS`. The builder carries the
  worker, so it is the only download
  ([record](validation/bundled-worker-a33-20261103.md)). The site's Download
  button and the README link to `/releases/latest`.
- On the A33 it installed over the update-test builds, updated the worker from
  its main screen and built the starter app.

## 0.1.2, 2026-11-03

- `v0.1.2` is GitHub's latest release, with `smallwright-0.1.2.apk`
  (`305ac165…`, version code 5) and `SHA256SUMS`. It adds OpenRouter sign-in
  by a pasted code ([ADR 0010](adr/0010-openrouter-sign-in-by-pasted-code.md)).
- On the A33 it installed over the sign-in test build, updated the worker from
  its main screen, built the starter app and kept the signed-in key.

## Known gaps

- The exact AAPT2 build was a manual configure; its record is
  `docs/validation/aapt2-source-build.md`, and the committed scripts do not
  replay it byte for byte.
- `controller/src/androidTest/assets/icon-fixtures/leaf.png` has no recorded
  origin.
- Tested on one phone only.
- No licenses screen in the app.
- When a reinstall meets an app built with the old project keys, the build is
  refused at signing, but the screen only says "Local build failed".
