import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val compilerInputs =
    files(
        rootProject.file("toolchain/input/ecj-4.12.jar"),
        rootProject.file("toolchain/input/r8-8.13.23.jar"),
    )
val aapt2Binary = project.file("src/main/jniLibs/arm64-v8a/libaapt2_exec.so")
val verifyCompilerInputs =
    tasks.register<Exec>("verifyCompilerInputs") {
        group = "verification"
        description = "Verify fixed compiler JARs and AAPT2 against tracked SHA-256 pins"
        inputs.files(compilerInputs, aapt2Binary)
        inputs.file(rootProject.file("toolchain/manifest.json"))
        inputs.file(rootProject.file("scripts/verify_compiler_inputs.py"))
        // Always attest the files, even when the consuming task is already up to date.
        outputs.upToDateWhen { false }
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            rootProject.file("scripts/verify_compiler_inputs.py"),
            "--manifest",
            rootProject.file("toolchain/manifest.json"),
            "--inputs",
            rootProject.file("toolchain/input"),
            "--aapt2",
            aapt2Binary,
        )
    }

tasks.configureEach {
    if (
        name == "preBuild" ||
            name.startsWith("compile", ignoreCase = true) ||
            name.contains("dex", ignoreCase = true) ||
            name.startsWith("package", ignoreCase = true) ||
            name.contains("lint", ignoreCase = true)
    ) {
        dependsOn(verifyCompilerInputs)
    }
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

val childPolicyTests = providers.gradleProperty("childPolicyTests").orNull == "true"
val childPolicyJni = layout.buildDirectory.dir("generated/child-policy-probe/jni")
val guardJni = layout.buildDirectory.dir("generated/aapt2-process-guard/jni")
val childPolicyHeader = project.file("src/main/c/child_process_policy.h")

fun registerChildTool(name: String, kind: String, sourcePath: String, output: Provider<Directory>) =
    tasks.register<Exec>(name) {
        group = "verification"
        val ndk = rootProject.file("toolchains/android-sdk/ndk/28.2.13676358")
        val script = rootProject.file("scripts/build_child_process_tools.py")
        inputs.files(
            script,
            childPolicyHeader,
            project.file(sourcePath),
            File(ndk, "source.properties"),
        )
        val llvm = File(ndk, "toolchains/llvm/prebuilt/linux-x86_64")
        inputs.files(
            File(llvm, "bin/clang"),
            File(llvm, "bin/aarch64-linux-android31-clang"),
            File(llvm, "bin/x86_64-linux-android31-clang"),
            File(ndk, "NOTICE"),
            File(ndk, "NOTICE.toolchain"),
        )
        inputs.file(File(llvm, "bin/ld.lld"))
        inputs.dir(File(llvm, "sysroot/usr/lib"))
        inputs.dir(File(llvm, "sysroot/usr/include"))
        inputs.dir(File(llvm, "lib/clang"))
        outputs.dir(output)
        outputs.file(output.map { it.asFile.parentFile.resolve("$kind-build.json") })
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            script,
            "--ndk",
            ndk,
            "--kind",
            kind,
            "--output-directory",
            output.get().asFile,
        )
    }

val buildAapt2Guard =
    registerChildTool("buildAapt2Guard", "launcher", "src/main/c/aapt2_guard_exec.c", guardJni)
val buildChildPolicyProbe =
    registerChildTool(
        "buildChildPolicyProbe",
        "probe",
        "src/androidTest/c/child_policy_probe.c",
        childPolicyJni,
    )

tasks.configureEach {
    if (
        (name.contains("JniLib") ||
            name.contains("NativeLib") ||
            name.contains("lint", ignoreCase = true))
    ) {
        dependsOn(buildAapt2Guard)
        if (childPolicyTests && name.contains("AndroidTest")) dependsOn(buildChildPolicyProbe)
    }
}

// Trusted instrumentation fixture only; no native reader ships in the worker APK.
val kernelResourceTests = providers.gradleProperty("kernelResourceTests").orNull == "true"
val kernelUsageJni = layout.buildDirectory.dir("generated/kernel-usage-probe/jni")
val buildKernelUsageProbe =
    tasks.register<Exec>("buildKernelUsageProbe") {
        group = "verification"
        description = "Build the test-only kernel resource reader with pinned NDK r28c"
        val ndk = rootProject.file("toolchains/android-sdk/ndk/28.2.13676358")
        val source = project.file("src/androidTest/c/kernel_usage_probe.c")
        val script = rootProject.file("scripts/build_kernel_usage_probe.py")
        inputs.files(source, script, File(ndk, "source.properties"))
        outputs.dir(kernelUsageJni)
        outputs.file(
            layout.buildDirectory.file(
                "generated/kernel-usage-probe/kernel-usage-probe-build.json",
            ),
        )
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            script,
            "--ndk",
            ndk,
            "--source",
            source,
            "--output-directory",
            kernelUsageJni.get().asFile,
        )
    }

tasks.configureEach {
    if (
        kernelResourceTests &&
            name.contains("AndroidTest") &&
            (name.contains("JniLib") ||
                name.contains("NativeLib") ||
                name.contains("lint", ignoreCase = true))
    ) {
        dependsOn(buildKernelUsageProbe)
    }
}

android {
    namespace = "dev.smallwright.worker"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.smallwright.worker"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "dev.smallwright.worker.ConfinementInstrumentation"
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
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("androidTest").java.srcDir("../test-support/java")
        if (kernelResourceTests) getByName("androidTest").jniLibs.srcDir(kernelUsageJni)
        if (childPolicyTests) getByName("androidTest").jniLibs.srcDir(childPolicyJni)
        getByName("main").jniLibs.srcDir(guardJni)
        getByName("main").assets.srcDir("../toolchain/licenses")
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(compilerInputs)
    testImplementation(libs.junit)
}
