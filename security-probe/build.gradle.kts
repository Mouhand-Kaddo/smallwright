import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val probeSigning =
    Properties().apply {
        val file = rootProject.file("signing/security-probe.properties")
        if (!file.isFile)
            throw GradleException(
                "Missing ignored security probe identity. Run scripts/create-security-probe-signing.sh",
            )
        FileInputStream(file).use(::load)
    }

android {
    namespace = "dev.smallwright.securityprobe"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.smallwright.securityprobe"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "dev.smallwright.securityprobe.HostileCallerInstrumentation"
    }

    signingConfigs {
        create("probe") {
            storeFile = rootProject.file("signing/security-probe.jks")
            storePassword = probeSigning.getProperty("storePassword")
            keyAlias = probeSigning.getProperty("keyAlias")
            keyPassword = probeSigning.getProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("probe") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":protocol"))
}
