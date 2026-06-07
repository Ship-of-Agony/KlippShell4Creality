plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.shipofagony.klippshell4creality"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shipofagony.klippshell4creality"
        minSdk = 24
        targetSdk = 36

        // Auf 12 erhöht für den neuen Release Candidate (Upgrade-Sicherheit)
        versionCode = 12

        // Deine neue Release-Versionsnummer für den 07. Juni 2026
        versionName = "0.8.6.070626-rc"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}