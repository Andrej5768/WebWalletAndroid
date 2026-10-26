plugins {
    alias(libs.plugins.webwallet.android.library)
    alias(libs.plugins.webwallet.hilt)
}

android {
    namespace = "org.andrewdev.webwallet.core.security"
}

dependencies {
    implementation(libs.timber)

    testImplementation(projects.core.testing)
}
