plugins {
    alias(libs.plugins.webwallet.android.feature)
}

android {
    namespace = "org.andrewdev.webwallet.feature.settings"
}

dependencies {
    implementation(projects.core.auth)
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.navigation)
    implementation(projects.core.ui)

    implementation(libs.androidx.browser)
    implementation(libs.zxing.core)
}
