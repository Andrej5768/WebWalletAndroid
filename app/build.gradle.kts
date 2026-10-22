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
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.designsystem)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Firebase: Crashlytics and Analytics are initialised but collect nothing until the user consents (docs/08 §6)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    debugImplementation(libs.leakcanary)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

// The Firebase plugins need google-services.json, which exists only once the Firebase project is set up
// (docs/08 §6). Until then the app builds without them, so that a fresh checkout and CI stay green.
val hasGoogleServices = listOf("dev", "prod").any { file("src/$it/google-services.json").exists() }
if (hasGoogleServices) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}
