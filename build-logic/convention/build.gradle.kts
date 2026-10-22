plugins {
    `kotlin-dsl`
}

group = "org.andrewdev.webwallet.buildlogic"

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.compose.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    // KSP and the JUnit 5 plugin are applied by id only: their jars are compiled with a newer Kotlin than the one
    // embedded in Gradle, so they cannot be on the compile classpath of the convention plugins
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "webwallet.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "webwallet.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "webwallet.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("compose") {
            id = "webwallet.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("hilt") {
            id = "webwallet.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("room") {
            id = "webwallet.room"
            implementationClass = "RoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "webwallet.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
