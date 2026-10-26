plugins {
    alias(libs.plugins.webwallet.android.library)
    alias(libs.plugins.webwallet.hilt)
}

android {
    namespace = "org.andrewdev.webwallet.core.auth"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    api(projects.core.network)
    implementation(projects.core.datastore)
    implementation(projects.core.security)

    implementation(libs.timber)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
