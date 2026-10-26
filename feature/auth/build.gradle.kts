plugins {
    alias(libs.plugins.webwallet.android.feature)
}

android {
    namespace = "org.andrewdev.webwallet.feature.auth"
}

dependencies {
    implementation(projects.core.auth)
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(projects.core.navigation)
    implementation(projects.core.ui)

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.browser)
}
