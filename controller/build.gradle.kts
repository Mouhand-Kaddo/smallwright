import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

fun signingIdentity(name: String): Properties? =
    rootProject
        .file("signing/$name.properties")
        .takeIf { it.isFile }
        ?.let { file ->
            Properties().apply { FileInputStream(file).use(::load) }
        }

val alphaSigning = signingIdentity("harness-alpha")
val releaseSigning = signingIdentity("release")

android {
    namespace = "dev.smallwright.controller"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.smallwright.controller"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "dev.smallwright.controller.ArtifactPolicyInstrumentation"
    }

    signingConfigs {
        if (alphaSigning != null)
            create("alpha") {
                storeFile = rootProject.file("signing/harness-alpha.jks")
                storePassword = alphaSigning.getProperty("storePassword")
                keyAlias = alphaSigning.getProperty("keyAlias")
                keyPassword = alphaSigning.getProperty("keyPassword")
            }
        if (releaseSigning != null)
            create("release") {
                storeFile = rootProject.file("signing/release.jks")
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
    }

    buildTypes {
        getByName("debug") { signingConfigs.findByName("alpha")?.let { signingConfig = it } }
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    sourceSets {
        getByName("androidTest").java.srcDir("../test-support/java")
        getByName("main").assets.srcDir("../toolchain/licenses")
        getByName("androidTest").assets.srcDir("build/generated/artifact-policy-assets")
    }

    packaging {
        resources.pickFirsts += "META-INF/LICENSE.md"
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":protocol"))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation("com.android.tools.build:apksig:8.13.0")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.86")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.bouncycastle:bcutil-jdk18on:1.86")
    implementation("com.google.code.gson:gson:2.11.0") {
        // Compile-time annotations only; the runtime parser is already cached.
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
    }
    testImplementation(libs.junit)
}
