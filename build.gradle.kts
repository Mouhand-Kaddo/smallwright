plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.spotless)
}

spotless {
    // Fixed pre-adoption commit: committing a change never exempts it from formatting.
    ratchetFrom("4e3a340e4217a927453a4cf5aade623f8f0a6b83")
    java {
        target(
            "controller/src/**/*.java",
            "worker/src/**/*.java",
            "protocol/src/**/*.java",
            "security-probe/src/**/*.java",
            "test-support/java/**/*.java",
        )
        targetExclude("worker/src/main/assets/**", "worker/src/androidTest/assets/**")
        googleJavaFormat(libs.versions.google.java.format.get()).aosp()
    }
    kotlin {
        target(
            "controller/src/**/*.kt",
            "worker/src/**/*.kt",
            "protocol/src/**/*.kt",
            "security-probe/src/**/*.kt",
        )
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    kotlinGradle {
        target(
            "*.gradle.kts",
            "controller/*.gradle.kts",
            "worker/*.gradle.kts",
            "protocol/*.gradle.kts",
            "security-probe/*.gradle.kts",
        )
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}

tasks.register("format") {
    group = "formatting"
    description = "Format changed harness Java, Kotlin and Gradle Kotlin files."
    dependsOn("spotlessApply")
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Check changed harness formatting and run Android Lint for all normal modules."
    dependsOn("spotlessCheck", ":controller:lintDebug", ":worker:lintDebug", ":protocol:lintDebug")
}
