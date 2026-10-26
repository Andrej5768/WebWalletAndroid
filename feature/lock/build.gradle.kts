plugins {
    alias(libs.plugins.webwallet.android.feature)
}

android {
    namespace = "org.andrewdev.webwallet.feature.lock"
}

dependencies {
    implementation(projects.core.auth)
    implementation(projects.core.datastore)
    implementation(projects.core.security)
    implementation(projects.core.navigation)
    implementation(projects.core.ui)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.activity.compose)
}
