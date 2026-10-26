plugins {
    alias(libs.plugins.webwallet.android.library)
    alias(libs.plugins.webwallet.hilt)
    alias(libs.plugins.webwallet.room)
}

android {
    namespace = "org.andrewdev.webwallet.core.database"
}

dependencies {
    implementation(projects.core.model)

    api(libs.androidx.room.runtime)

    testImplementation(projects.core.testing)
}
