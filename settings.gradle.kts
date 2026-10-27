pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Downloads a JDK matching the toolchain when the machine has none
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "WebWalletAndroid"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:model")
include(":core:network")
include(":core:designsystem")
include(":core:testing")
include(":core:security")
include(":core:datastore")
include(":core:auth")
include(":core:database")
include(":core:data")
include(":core:navigation")
include(":core:ui")
include(":feature:auth")
include(":feature:lock")
include(":feature:overview")
include(":feature:accounts")
include(":feature:transactions")
include(":feature:budget")
include(":feature:more")
include(":feature:settings")
