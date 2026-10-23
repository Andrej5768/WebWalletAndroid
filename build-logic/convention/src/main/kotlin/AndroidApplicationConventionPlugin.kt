import com.android.build.api.dsl.ApplicationExtension
import org.andrewdev.webwallet.buildlogic.TARGET_SDK
import org.andrewdev.webwallet.buildlogic.configureKotlinAndroid
import org.andrewdev.webwallet.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** The app module: flavors, build types, signing and the shared Kotlin setup. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = TARGET_SDK

                signingConfigs {
                    // Shared debug key, so that every machine and CI have the same fingerprints for App Links
                    getByName("debug") {
                        storeFile = file("debug.keystore")
                        storePassword = "android"
                        keyAlias = "androiddebugkey"
                        keyPassword = "android"
                    }
                    // Play App Signing upload key from the Gradle properties uploadKeystore, uploadKeystorePassword,
                    // uploadKeyAlias and uploadKeyPassword: ~/.gradle/gradle.properties locally, ORG_GRADLE_PROJECT_*
                    // from the repository secrets in CI. Without them release builds stay unsigned (docs/08 §3).
                    providers.gradleProperty("uploadKeystore").orNull?.let { keystore ->
                        create("upload") {
                            storeFile = file(keystore)
                            storePassword = providers.gradleProperty("uploadKeystorePassword").get()
                            keyAlias = providers.gradleProperty("uploadKeyAlias").getOrElse("upload")
                            keyPassword = providers.gradleProperty("uploadKeyPassword").get()
                        }
                    }
                }

                buildTypes {
                    getByName("debug") {
                        isDebuggable = true
                        signingConfig = signingConfigs.getByName("debug")
                    }
                    getByName("release") {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                        signingConfig = signingConfigs.findByName("upload")
                    }
                }

                flavorDimensions += "backend"
                productFlavors {
                    create("dev") {
                        dimension = "backend"
                        applicationIdSuffix = ".dev"
                        val baseUrl = providers.gradleProperty("apiBaseUrl").getOrElse("http://10.0.2.2:8080")
                        buildConfigField("String", "API_BASE_URL", "\"$baseUrl\"")
                    }
                    create("prod") {
                        dimension = "backend"
                        buildConfigField("String", "API_BASE_URL", "\"https://wallet.andrew-dev.org\"")
                    }
                }

                buildFeatures {
                    buildConfig = true
                }

                packaging {
                    resources.excludes += setOf("META-INF/LICENSE*", "META-INF/AL2.0", "META-INF/LGPL2.1")
                }
            }

            dependencies {
                add("implementation", libs.findLibrary("androidx-core-ktx").get())
                add("implementation", libs.findLibrary("timber").get())
            }
        }
    }
}
