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

        // Interner Code für Updates (auf 10 erhöht für den Hotfix)
        versionCode = 10
        // Deine neue Hotfix RC-Versionsnummer
        versionName = "0.8.4.300526-rc"

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