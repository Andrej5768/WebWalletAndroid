plugins {
    alias(libs.plugins.webwallet.android.library)
    alias(libs.plugins.webwallet.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.andrewdev.webwallet.core.datastore"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.security)

    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
}
