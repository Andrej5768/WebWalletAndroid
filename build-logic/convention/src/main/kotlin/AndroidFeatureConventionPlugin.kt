import org.andrewdev.webwallet.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/** A feature module: an Android library with Compose, Hilt and the core modules every screen needs. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("webwallet.android.library")
            pluginManager.apply("webwallet.compose")
            pluginManager.apply("webwallet.hilt")

            dependencies {
                add("implementation", project(":core:common"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:designsystem"))
                add("implementation", libs.findLibrary("androidx-hilt-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("androidx-activity-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("androidx-navigation3-runtime").get())
                add("testImplementation", project(":core:testing"))
            }
        }
    }
}
