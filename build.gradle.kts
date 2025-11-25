import com.unciv.build.BuildConfig.appVersion
import java.util.Properties

buildscript {
    repositories {
        // Chinese mirrors for quicker loading for chinese devs - uncomment if you're chinese
        // maven{ url = uri("https://maven.aliyun.com/repository/central") }
        // maven{ url = uri("https://maven.aliyun.com/repository/google") }
        // maven{ url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        mavenCentral()
        google()  // needed for com.android.tools.build:gradle
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.kotlinPlugin)
        classpath(libs.agp)
    }
}

// Fixes the error "Please initialize at least one Kotlin target in 'Unciv (:)'"
kotlin {
    jvm()
    java {
        // required for building Unciv with a Java version higher than 24 (e.g. Java 25)
        sourceCompatibility = JavaVersion.VERSION_21
    }
}


// Plugins used for serialization of JSON for networking
plugins {
    val kotlinVersion: String = libs.versions.kotlin.get()
    kotlin("multiplatform") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    alias(libs.plugins.detekt)
    alias(libs.plugins.purity)
}

// Kludge to get the correct string notation for a gdx native (':' _after_ version seems beyond toml)
fun gdxNatives(platform: String) = "${libs.gdx.platform.get()}:natives-$platform"

//from here on you can't simply use `libs` anymore, see https://github.com/gradle/gradle/issues/18237#issuecomment-928079890

allprojects {
//    repositories{ // for local purity
//        mavenLocal()
//    }
    val purityId = rootProject.libs.plugins.purity.get().pluginId
    apply(plugin = purityId)
    configure<yairm210.purity.PurityConfiguration> {
        wellKnownPureFunctions = setOf(
        )
        wellKnownReadonlyFunctions = setOf(
            "com.badlogic.gdx.math.Vector2.len",
            "com.badlogic.gdx.math.Vector2.cpy",
            "com.badlogic.gdx.math.Vector2.hashCode",

            "com.badlogic.gdx.graphics.Color.cpy",
            "com.badlogic.gdx.graphics.Color.toString",

            "com.badlogic.gdx.files.FileHandle.child",
            "com.badlogic.gdx.files.FileHandle.list",
            "com.badlogic.gdx.files.FileHandle.exists",
            "com.badlogic.gdx.files.FileHandle.isDirectory",
            "com.badlogic.gdx.files.FileHandle.isFile",
            "com.badlogic.gdx.files.FileHandle.name",

            "kotlin.sequences.shuffled",
        )
        wellKnownPureClasses = setOf(
        )
        wellKnownInternalStateClasses = setOf(
            "com.badlogic.gdx.math.Vector2",
        )
        warnOnPossibleAnnotations = false
    }

    apply(plugin = "eclipse")
    apply(plugin = "idea")

    version = appVersion

    repositories {
        // Chinese mirrors for quicker loading for chinese devs - uncomment if you're chinese
        // maven{ url = uri("https://maven.aliyun.com/repository/central") }
        // maven{ url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral()
        google()
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/releases/") }
        maven { url = uri("https://jitpack.io") } // for java-discord-rpc
    }
}

project(":desktop") {
    apply(plugin = "kotlin")

    dependencies {
        "implementation"(project(":core"))
        "implementation"(rootProject.libs.coroutines.core)
        "implementation"(rootProject.libs.gdx.backend.desktop)
        "implementation"(gdxNatives("desktop"))

        "implementation"(rootProject.libs.gdx.tools) {
            exclude("com.badlogicgames.gdx", "gdx-backend-lwjgl")
        }

        // Needed to display "Playing Unciv" in Discord
        "implementation"(rootProject.libs.discord)

        // Needed for Windows turn notifiers
        "api"(rootProject.libs.bundles.jna)
    }
}

// For server-side
project(":server") {
    apply(plugin = "kotlin")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    dependencies {
        // For server-side
        "api"(rootProject.libs.bundles.ktor.server)
        "implementation"(rootProject.libs.logback)
        "implementation"(rootProject.libs.clikt)

        // clikt somehow needs this
        "api"(rootProject.libs.bundles.jna)
    }
}

private fun getSdkPath(): String? {
    // See #13566 - Android Studio has moved its primary method to store where to find its SDK
    val localProperties = project.file("local.properties")
    return if (localProperties.exists()) {
        val properties = Properties()
        localProperties.inputStream().use { properties.load(it) }

        properties.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME")
    } else {
        System.getenv("ANDROID_HOME")
    }
}

if (getSdkPath() != null) {
    project(":android") {
        apply(plugin = "com.android.application")
        apply(plugin = "kotlin-android")

        val natives by configurations.creating

        dependencies {
            "implementation"(project(":core"))
            // Not sure why I had to add this in for the upgrade to 1.12.1 to work, we can probably remove this later since it's contained in core
            "implementation"(rootProject.libs.gdx)
            "implementation"(rootProject.libs.gdx.backend.android)
            "implementation"(rootProject.libs.coroutines.android)
            natives(gdxNatives("armeabi-v7a"))
            natives(gdxNatives("arm64-v8a"))
            natives(gdxNatives("x86"))
            natives(gdxNatives("x86_64"))
        }
    }
}


project(":core") {
    apply(plugin = "kotlin")
    // Serialization features (especially JSON for multiplayer apiV2)
    apply(plugin = "kotlinx-serialization")

    dependencies {
        "implementation"(rootProject.libs.gdx)
        "implementation"(rootProject.libs.coroutines.core)
        "implementation"(rootProject.libs.kotlin.reflect)

        "implementation"(rootProject.libs.purity.annotations)

        "api"(rootProject.libs.bundles.ktor.client)
    }


    // Taken from https://github.com/TomGrill/gdx-testing
    project(":tests") {
        apply(plugin = "java")
        apply(plugin = "kotlin")

        dependencies {
            "implementation"(project(":core"))

            "implementation"(rootProject.libs.coroutines.core)
            "implementation"(rootProject.libs.kotlin.reflect)

            "implementation"(rootProject.libs.gdx)
            "implementation"(rootProject.libs.gdx.backend.headless)
            "implementation"(rootProject.libs.gdx.backend.desktop)
            "implementation"(gdxNatives("desktop"))
        }
    }
}
