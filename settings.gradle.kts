import java.util.Properties

pluginManagement {
    repositories {
        mavenLocal() // To get the compiler plugin locally
        gradlePluginPortal() // So other plugins can be resolved
    }
}

include("desktop", "core", "tests", "server")

private fun getSdkPath(): String? {
    val localProperties = file("local.properties")
    return if (localProperties.exists()) {
        val properties = Properties()
        localProperties.inputStream().use { properties.load(it) }

        properties.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME")
    } else {
        System.getenv("ANDROID_HOME")
    }
}
if (getSdkPath() != null) include("android")
