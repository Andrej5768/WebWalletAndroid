import com.android.build.api.dsl.LibraryExtension
import org.andrewdev.webwallet.buildlogic.TARGET_SDK
import org.andrewdev.webwallet.buildlogic.configureKotlinAndroid
import org.andrewdev.webwallet.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** An Android library module (core:* and widget): the shared Kotlin setup, no flavors of its own. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                testOptions.targetSdk = TARGET_SDK
                // The resource prefix keeps the resources of the modules apart
                resourcePrefix = path.split(":").filter { it.isNotBlank() }.joinToString("_") + "_"
                testOptions.unitTests.isIncludeAndroidResources = true
            }

            dependencies {
                add("implementation", libs.findLibrary("kotlinx-coroutines-core").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            }
        }
    }
}
