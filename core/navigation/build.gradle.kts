plugins {
    alias(libs.plugins.webwallet.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.andrewdev.webwallet.core.navigation"
}

dependencies {
    implementation(projects.core.model)

    api(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
}
