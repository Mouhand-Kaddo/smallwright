# Host code-quality tooling — 2026-10-11

## Result and scope

Pinned Spotless 8.10.4, ktfmt 0.65 (Kotlin style) and google-java-format
1.36.1 (AOSP style). Existing Android Lint remains on AGP 8.13.0.
See [selection and commands](../CODE_QUALITY.md).

Exact changed paths for this checkpoint:

- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `docs/CODE_QUALITY.md`
- `docs/TESTING.md`
- `docs/STATUS.md` (preserving the pre-existing tooling handoff changes)
- `docs/validation/code-quality-20261011.md`

The pre-existing untracked real-phone-test skill/evidence were not edited.
No application Java source, generated templates or compiler pins were changed.
No JVM tests, APK assembly, installation, model request or device check ran.
There are no new APK paths for this checkpoint. Host harness class/resource
compilation occurred as dependencies of lint; no generated app was built here.

## Actual execution

All Gradle commands used `source scripts/env.sh`, the project-local JDK
21.0.12.1+1 and Gradle 8.13, with:

```text
--no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m --console=plain
```

The JVM argument above was passed as one quoted shell argument. Initial
dependency resolution and the Java version adjustment used network access;
subsequent checks used `--offline`.

1. `spotlessCheck` initially failed on the root build script's actual layout.
2. Two newly created untracked fixtures under protocol test source roots
   supplied deliberately unformatted Java/Kotlin. `spotlessCheck --offline`
   exposed an `InvocationTargetException` through Spotless with Java formatter
   1.37.0. `spotlessJavaCheck spotlessKotlinCheck --continue --offline --info`
   reproduced it and separately rejected the Kotlin layout. The 1.37.0 CLI
   worked directly on the same source with required JVM exports; the precise
   integration cause was not established. No suppression was added.
3. With Java formatter pinned to 1.36.1, `spotlessJavaCheck spotlessKotlinCheck
   --continue` correctly failed on both fixture layouts (21 seconds).
4. `format spotlessCheck --offline` passed (16 seconds; three apply tasks
   executed, six tasks UP-TO-DATE). Exact fixture contents were independently
   compared with the expected formatted text. Only those two fixtures were
   removed. The compiler header SHA-256 was independently checked unchanged.
5. `qualityCheck --offline` passed (69 seconds; 105 tasks executed). Formatting
   tasks and Android lint analyses ran. XML reports show zero errors, controller
   72 warnings, worker two warnings, protocol zero warnings. Existing warnings
   were not suppressed. `:worker:verifyCompilerInputs` confirmed the pinned
   ECJ/R8 artifacts and source headers.
6. `git diff --check` passed. Final configuration/targets and status were reviewed.

Ignored raw logs in `toolchains/`:

- `code-quality-check-initial.log`
- `code-quality-fixtures-before.log`
- `code-quality-fixtures-detail.log`
- `code-quality-fixtures-compatible.log`
- `code-quality-fixtures-after.log`
- `code-quality-final.log`

The full check also retained SDK XML-version, native-library manifest and Java
deprecation warnings. This checkpoint did not change their underlying behavior.
The warning count is the current report, not evidence of source warnings fixed.

## Downloaded host artifacts

Versions are pinned in the version catalog. Main artifacts resolved into the
ignored Gradle dependency cache; the SHA-256 values below were measured locally.
Each tool declares Apache-2.0 in its upstream project/POM (google-java-format's
declaration is inherited from its parent). These are host tools, not redistributed
APK inputs; this record is not a transitive dependency audit.

| Artifact | SHA-256 |
| --- | --- |
| `spotless-plugin-gradle-8.10.4.jar` | `fab4152714c6087e36fd676c58ad15a5beaf71daea557b3e021950723284ccd7` |
| `ktfmt-0.65.jar` | `fbe750c85291dd2f67f9af912482a624f8ca87797bcedd44341e882f6e79cf55` |
| `google-java-format-1.36.1.jar` | `57f9b550a662dcbbdf344073e3f5812b837a0e6e430548d8a0a3c9090cada65d` |

## Limits and next work

Formatting uses fixed commit `4e3a340e4217a927453a4cf5aade623f8f0a6b83`;
untouched older files are not claimed to pass formatting. Compiler headers
remain separately checksum-gated and excluded from automatic rewriting.
Detekt and Compose-specific rule integration await the actual Kotlin/Compose
stack; no Kotlin migration or CI workflow is claimed. Formatting does not
prove worker isolation, signing safety, functional behavior or physical locality.
Next app checkpoint remains the physical `compileDiagnostics` probe and repair.
