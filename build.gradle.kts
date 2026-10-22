// Plugins are declared here once so that every module resolves the same versions; they are applied by the
// convention plugins in build-logic.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.junit5) apply false
    alias(libs.plugins.openapi.generator) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.detekt)
}

// detekt runs once from the root over every module, with the Compose rules and ktlint-style formatting
dependencies {
    detektPlugins(libs.detekt.formatting)
    detektPlugins(libs.detekt.compose.rules)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(
        files(
            "app/src",
            "core",
            "feature",
            "widget",
            "build-logic/convention/src",
        ),
    )
    parallel = true
    autoCorrect = providers.gradleProperty("detektAutoCorrect").map { it.toBoolean() }.getOrElse(false)
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude("**/build/**", "**/generated/**")
    jvmTarget = "21"
    reports {
        html.required.set(true)
        sarif.required.set(true)
    }
}
