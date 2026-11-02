# Code quality tooling

Selected 2026-10-11 for the development harness. These tools run on the host;
they are not packaged in either APK or added to the generated-app compiler.

## Selected tools

| Purpose | Selection | Version / status |
| --- | --- | --- |
| Android correctness, manifest, resource, API and security checks | Android Lint | Existing AGP 8.13.0 |
| Gradle formatting integration | Spotless | 8.10.4 |
| Kotlin and Gradle Kotlin formatting | ktfmt, Kotlin style | 0.65 |
| Existing Java formatting | google-java-format, AOSP style | 1.36.1 |
| Kotlin complexity and code smells | Detekt | Selected for Kotlin introduction; not installed or version-pinned yet |

Spotless requires Java 17+ and Gradle 7.3+. google-java-format 1.36.1 requires
Java 21. The existing project-local JDK 21 and Gradle 8.13 meet these requirements.
The three new tools declare Apache-2.0 licenses in their upstream projects.
They resolve into the ignored Gradle dependency cache. Their versions are pinned
in `gradle/libs.versions.toml`; this is not a complete transitive license audit.

Use one Kotlin formatter. ktfmt supplies deterministic layout; Android Lint
supplies Android-aware analysis. Detekt will add Kotlin code-smell analysis
when actual Kotlin sources arrive, with its formatting rules disabled to avoid
competing formatters. Select its version against the chosen Kotlin/AGP stack
and verify Compose rule compatibility then. No Kotlin/Compose migration occurs
in this tooling checkpoint.

## Commands

```bash
source scripts/env.sh
./gradlew format --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
./gradlew qualityCheck --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m' --console=plain
```

The first dependency resolution requires running without `--offline`.
`format` modifies admitted harness source/build-script files. `qualityCheck`
checks formatting and runs debug Android Lint in controller, worker and protocol.
Run JVM and physical boundary tests separately as described in [TESTING](TESTING.md).
Lint retains its existing error failure behavior and visible warnings; no
warning baseline or suppression is introduced. There is no CI workflow yet;
these are executable local checks, suitable for a later CI invocation.

Formatting covers only files changed since the fixed pre-adoption commit
`4e3a340e4217a927453a4cf5aade623f8f0a6b83`, including new files. Keep that commit
available in the checkout (CI needs sufficient Git history). The baseline is
not moved after each commit. Existing untouched source is not certified as
formatted. This gradual adoption avoids a repository-wide rewrite and makes
subsequent changed files conform to the selected styles.

Templates, compiler inputs, caches, signing directories,
generated outputs and unrelated local files are outside the explicit targets.
The optional security-probe source is formatted, but its opt-in runtime checks
are not part of the normal quality task.

Gradle tracks formatter inputs/outputs and caches formatter work. Use the small
formatting check during editing, and Android Lint before checkpoint handoff.
No relative speed claim is made without measurements on this workspace.

Formatting does not resolve structural debt. As the controller adopts Compose,
keep UI rendering and screen state separate from project storage, provider
requests, compiler IPC and signing/installation policy. Preserve runtime tests
for those boundaries instead of relying on a static checker to prove them.

## Primary references

- [Android Lint](https://developer.android.com/studio/write/lint)
- [Spotless Gradle setup, caching and gradual enforcement](https://github.com/diffplug/spotless/tree/main/plugin-gradle)
- [Spotless releases](https://github.com/diffplug/spotless/blob/main/plugin-gradle/CHANGES.md)
- [ktfmt](https://github.com/Kotlin/ktfmt)
- [google-java-format](https://github.com/google/google-java-format)
- [Detekt compatibility](https://detekt.dev/docs/introduction/compatibility/)
