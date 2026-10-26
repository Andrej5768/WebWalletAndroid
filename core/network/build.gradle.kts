plugins {
    alias(libs.plugins.webwallet.android.library)
    alias(libs.plugins.webwallet.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

android {
    namespace = "org.andrewdev.webwallet.core.network"
}

// The API models and interfaces are generated from the copy of the backend contract (docs/02 §4.4) and never
// hand-written. Generated sources live under build/ and are not committed.
val openApiOutput = layout.buildDirectory.dir("generated/openapi")
val cookieFreeSpec = layout.buildDirectory.file("openapi/openapi-without-cookies.json")

// The contract documents the cookies of the web client (refresh token, trusted device); the app never sends them
// and the Kotlin generator cannot express them anyway, so they are dropped from the copy it reads.
val stripCookieParameters = tasks.register("stripCookieParameters") {
    val source = rootProject.file("openapi/openapi.json")
    val target = cookieFreeSpec
    inputs.file(source)
    outputs.file(target)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val spec = groovy.json.JsonSlurper().parse(source) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val paths = spec["paths"] as Map<String, Map<String, Any?>>
        paths.values.forEach { operations ->
            operations.values.forEach { operation ->
                @Suppress("UNCHECKED_CAST")
                val mutable = operation as? MutableMap<String, Any?> ?: return@forEach

                @Suppress("UNCHECKED_CAST")
                val parameters = mutable["parameters"] as? MutableList<Map<String, Any?>> ?: return@forEach
                parameters.removeAll { it["in"] == "cookie" }
                if (parameters.isEmpty()) mutable.remove("parameters")
            }
        }
        val file = target.get().asFile
        file.parentFile.mkdirs()
        file.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(spec)))
    }
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(cookieFreeSpec)
    configFile.set(rootProject.file("openapi/config.yaml").invariantSeparatorsPath)
    outputDir.set(openApiOutput)
    packageName.set("org.andrewdev.webwallet.core.network.generated")
    apiPackage.set("org.andrewdev.webwallet.core.network.generated.api")
    modelPackage.set("org.andrewdev.webwallet.core.network.generated.model")
    invokerPackage.set("org.andrewdev.webwallet.core.network.generated.infrastructure")
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    typeMappings.set(mapOf("number" to "java.math.BigDecimal"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addStaticSourceDirectory("build/generated/openapi/src/main/kotlin")
    }
}

tasks.openApiGenerate {
    dependsOn(stripCookieParameters)
}

tasks.named("preBuild") {
    dependsOn(tasks.openApiGenerate)
}

// The generated code is not ours to lint
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude { it.file.path.contains("generated") }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)

    api(libs.kotlinx.serialization.json)
    api(libs.retrofit)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    api(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.timber)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
