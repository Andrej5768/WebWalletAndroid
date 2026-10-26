package org.andrewdev.webwallet.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** The version catalog of the repository, so that the convention plugins pin the same versions as the modules. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Target SDK the app ships with; bumped within a month of a new stable Android release (see docs/08). */
const val TARGET_SDK = 37

const val MIN_SDK = 31

/** The Java toolchain of the JVM modules; the Android modules compile for the same level. */
const val JAVA_VERSION = 21

/**
 * The SDK levels, Java 21 and the Kotlin compiler flags shared by every Android module. Kotlin is compiled by
 * AGP's built-in support, so the options are set on the compile tasks rather than on a Kotlin extension.
 */
internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = TARGET_SDK
        defaultConfig.minSdk = MIN_SDK
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
        lint.apply {
            // Missing Ukrainian or English strings fail the build: both languages must be complete
            error.add("MissingTranslation")
            // Dependency updates are the job of Dependabot; these checks need the network and change by themselves
            disable.addAll(listOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion"))
            warningsAsErrors = true
            abortOnError = true
        }
    }
    configureKotlinCompileTasks()
    configureJUnit5()
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(JAVA_VERSION)
    }
    configureKotlinCompileTasks()
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // A module without tests yet is not a misconfiguration
        failOnNoDiscoveredTests.set(false)
    }
    dependencies {
        add("testImplementation", libs.findLibrary("junit-jupiter").get())
        add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
    }
}

private fun Project.configureKotlinCompileTasks() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            allWarningsAsErrors.set(warningsAsErrorsFromProperty())
        }
    }
}

/** JUnit 5 for the unit tests of an Android module. */
private fun Project.configureJUnit5() {
    pluginManager.apply("de.mannodermaus.android-junit5")
    tasks.withType<Test>().configureEach {
        // A module without tests yet is not a misconfiguration
        failOnNoDiscoveredTests.set(false)
    }
    dependencies {
        add("testImplementation", libs.findLibrary("junit-jupiter").get())
        add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
    }
}

/** CI passes -PwarningsAsErrors=true; a developer machine only warns. */
private fun Project.warningsAsErrorsFromProperty() =
    providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false)

/** Compose with the Kotlin compiler plugin, the BOM and the tooling for previews. */
internal fun Project.configureCompose(extension: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    extension.buildFeatures.compose = true
    dependencies {
        val bom = libs.findLibrary("androidx-compose-bom").get()
        add("implementation", platform(bom))
        add("implementation", libs.findLibrary("androidx-compose-foundation").get())
        add("implementation", libs.findLibrary("androidx-compose-material3").get())
        add("implementation", libs.findLibrary("androidx-compose-ui").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        add("androidTestImplementation", platform(bom))
        add("androidTestImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
    }
    // Compiler stability metrics for CI (ci.yml passes -PcomposeReports=true)
    if (providers.gradleProperty("composeReports").map { it.toBoolean() }.getOrElse(false)) {
        val dir = layout.buildDirectory.dir("compose-reports").get().asFile.absolutePath
        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions.freeCompilerArgs.addAll(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$dir",
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$dir",
            )
        }
    }
}
