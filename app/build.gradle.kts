plugins {
    alias(libs.plugins.webwallet.android.application)
    alias(libs.plugins.webwallet.compose)
    alias(libs.plugins.webwallet.hilt)
}

android {
    namespace = "org.andrewdev.webwallet"

    defaultConfig {
        applicationId = "org.andrewdev.webwallet"
        // versionName comes from the Git tag vX.Y.Z and versionCode from it (docs/08 §2); CI passes both
        versionName = providers.gradleProperty("versionName").getOrElse("0.1.0")
        versionCode = providers.gradleProperty("versionCode").map { it.toInt() }.getOrElse(1)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The app switches its language itself (Settings → Profile), so every language ships in every install
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.auth)
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.navigation)
    implementation(projects.feature.auth)
    implementation(projects.feature.lock)
    implementation(projects.feature.overview)
    implementation(projects.feature.accounts)
    implementation(projects.feature.transactions)
    implementation(projects.feature.budget)
    implementation(projects.feature.more)
    implementation(projects.feature.settings)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.coroutines.android)

    // Firebase: Crashlytics and Analytics are initialised but collect nothing until the user consents (docs/08 §6)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    debugImplementation(libs.leakcanary)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

// The Firebase plugins need app/google-services.json, which is not committed: locally it is downloaded from the
// Firebase console, in CI it is written from the GOOGLE_SERVICES_JSON secret (docs/08 §6). Without the file the
// app builds without the plugins, so that a fresh checkout and a fork PR stay green.
val hasGoogleServices = file("google-services.json").exists()
if (hasGoogleServices) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}
