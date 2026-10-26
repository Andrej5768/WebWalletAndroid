plugins {
    alias(libs.plugins.webwallet.android.library)
    alias(libs.plugins.webwallet.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.andrewdev.webwallet.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.network)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.auth)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
