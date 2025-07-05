import java.util.Properties

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
