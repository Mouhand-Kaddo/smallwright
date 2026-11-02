plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.smallwright.protocol"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
