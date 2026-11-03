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
        versionCode = providers.gradleProperty("smallwright.versionCode").get().toInt()
        versionName = providers.gradleProperty("smallwright.versionName").get()
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

/** Copies the worker APK of the same build type into this app's assets as worker.apk. */
abstract class BundleWorkerApk : DefaultTask() {
    @get:InputDirectory abstract val workerApks: DirectoryProperty

    @get:OutputDirectory abstract val assets: DirectoryProperty

    @TaskAction
    fun copy() {
        val apks = workerApks.get().asFile.listFiles { file -> file.extension == "apk" }.orEmpty()
        val apk =
            apks.singleOrNull()
                ?: throw GradleException(
                    "Expected one worker APK in ${workerApks.get()}, found ${apks.size}",
                )
        apk.copyTo(assets.get().file("worker.apk").asFile, overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        val buildType = variant.name.replaceFirstChar(Char::uppercaseChar)
        val bundle =
            tasks.register<BundleWorkerApk>("bundle${buildType}WorkerApk") {
                dependsOn(":worker:assemble$buildType")
                workerApks.set(
                    project(":worker").layout.buildDirectory.dir("outputs/apk/${variant.name}"),
                )
            }
        variant.sources.assets?.addGeneratedSourceDirectory(bundle, BundleWorkerApk::assets)
    }
}

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
