# Public release readiness

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

## Before publishing

- Publish under the personal account as `Mouhand-Kaddo/smallwright`; no
  domain or organization (a free side project). The app ids keep
  `dev.smallwright.*` without owning the domain.
- The USPTO search for SMALLWRIGHT found no live or dead marks (user, 2026-11-02).
  The EU office is not searched. The earlier name, Palmwright, was dropped
  because "Palmwright Software LLC" (Florida) sells custom software at
  palmwright.com.
- Create the public repository from one clean commit with the GitHub no-reply
  address, and upload the AAPT2 binary as the release asset `bootstrap.sh`
  downloads.
- Back up `signing/`.

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
