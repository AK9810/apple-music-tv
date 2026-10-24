plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Deliberately minimal: no dependencies at all. The shell is one Activity with a programmatic
// UI — all the real UI lives on the TV's :8080 page, so changes there don't need a new APK here.
android {
    namespace = "com.applemusicktv.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.applemusicktv.companion"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
