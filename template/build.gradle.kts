plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ir.html2apk.template"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.html2apk.template"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    lint {
        abortOnError = false
    }
}

// No dependencies — the template is a minimal WebView shell (Java only, zero stdlib
// footprint) because it becomes the base APK that HTML2APK patches and re-signs at runtime.
