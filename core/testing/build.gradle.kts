plugins {
    alias(libs.plugins.webwallet.jvm.library)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)

    api(libs.kotlinx.coroutines.test)
    api(libs.junit.jupiter.api)
    api(libs.mockk)
    api(libs.turbine)
}
