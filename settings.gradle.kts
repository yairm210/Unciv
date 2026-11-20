import java.io.File
import java.util.Properties

pluginManagement {
    val webEnabled = providers.gradleProperty("web").isPresent
    repositories {
        if (!webEnabled) mavenLocal() // Preserve the existing JVM/Android plugin workflow.
        mavenCentral()
        gradlePluginPortal() // So other plugins can be resolved
    }
}

include("desktop", "core", "tests", "server")

// Browser support is opt-in so ordinary builds do not bootstrap browser toolchains.
if (providers.gradleProperty("web").isPresent) {
    apply(from = "web/bootstrap.settings.gradle.kts")
    include("web")
} else if (providers.gradleProperty("includeTeaVMSource").map(String::toBoolean).getOrElse(false)) {
    val teavmPath = providers.gradleProperty("teavmPath").orNull
        ?: throw GradleException("-PincludeTeaVMSource=true requires -PteavmPath")
    val teavmSource = File(teavmPath)
    if (!teavmSource.isAbsolute || !teavmSource.isDirectory) {
        throw GradleException("-PteavmPath must be an absolute TeaVM source checkout: $teavmPath")
    }
    includeBuild(teavmSource.absolutePath) {
        dependencySubstitution {
            substitute(module("org.teavm:teavm-tooling")).using(project(":tools:core"))
            substitute(module("org.teavm:teavm-core")).using(project(":core"))
            substitute(module("org.teavm:teavm-classlib")).using(project(":classlib"))
            substitute(module("org.teavm:teavm-jso")).using(project(":jso:core"))
            substitute(module("org.teavm:teavm-jso-apis")).using(project(":jso:apis"))
            substitute(module("org.teavm:teavm-jso-impl")).using(project(":jso:impl"))
            substitute(module("org.teavm:teavm-interop")).using(project(":interop:core"))
            substitute(module("org.teavm:teavm-metaprogramming-api")).using(project(":metaprogramming:api"))
            substitute(module("org.teavm:teavm-metaprogramming-impl")).using(project(":metaprogramming:impl"))
        }
    }
}

private fun getSdkPath(settingsDir: File): String? {
    val localProperties = File(settingsDir, "local.properties")
    return if (localProperties.exists()) {
        val properties = Properties()
        localProperties.inputStream().use { properties.load(it) }
        properties.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME")
    } else {
        System.getenv("ANDROID_HOME")
    }
}

if (getSdkPath(settingsDir) != null) include("android")
