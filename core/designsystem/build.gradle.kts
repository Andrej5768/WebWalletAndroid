plugins {
    alias(libs.plugins.webwallet.android.library)
    alias(libs.plugins.webwallet.compose)
}

android {
    namespace = "org.andrewdev.webwallet.core.designsystem"
}

dependencies {
    implementation(projects.core.model)

    api(libs.lucide.icons)
    implementation(libs.androidx.core.ktx)
}
