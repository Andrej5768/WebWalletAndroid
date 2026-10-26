plugins {
    alias(libs.plugins.webwallet.android.library)
    alias(libs.plugins.webwallet.compose)
}

android {
    namespace = "org.andrewdev.webwallet.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.common)
    api(projects.core.model)
    implementation(projects.core.network)

    implementation(libs.androidx.core.ktx)
}
