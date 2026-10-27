plugins {
    alias(libs.plugins.webwallet.android.feature)
}

android {
    namespace = "org.andrewdev.webwallet.feature.budget"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.navigation)
    implementation(projects.core.ui)
}
