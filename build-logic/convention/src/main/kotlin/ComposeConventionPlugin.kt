import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.andrewdev.webwallet.buildlogic.configureCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Jetpack Compose for an app or library module. */
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            if (pluginManager.hasPlugin("com.android.application")) {
                extensions.configure<ApplicationExtension> { configureCompose(this) }
            } else {
                extensions.configure<LibraryExtension> { configureCompose(this) }
            }
        }
    }
}
