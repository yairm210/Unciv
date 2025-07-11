
pluginManagement {
    repositories {
        mavenLocal() // To get the compiler plugin locally
        gradlePluginPortal() // So other plugins can be resolved
    }
}

include("desktop", "core", "tests", "server")
if (System.getenv("ANDROID_HOME") != null) include("android")
