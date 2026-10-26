plugins {
    alias(libs.plugins.webwallet.android.feature)
}

android {
    namespace = "org.andrewdev.webwallet.feature.transactions"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.navigation)
    implementation(projects.core.ui)
}
