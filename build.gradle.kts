import com.unciv.build.BuildConfig.gdxVersion
import com.unciv.build.BuildConfig.roboVMVersion

val ktorVersion: String by project


// You'll still get kotlin-reflect-1.3.70.jar in your classpath, but will no longer be used
configurations.all { resolutionStrategy {
    force("org.jetbrains.kotlin:kotlin-reflect:${com.unciv.build.BuildConfig.kotlinVersion}")
} }


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
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${com.unciv.build.BuildConfig.kotlinVersion}")
        classpath("de.richsource.gradle.plugins:gwt-gradle-plugin:0.6")
        classpath("com.android.tools.build:gradle:7.3.1")
        classpath("com.mobidevelop.robovm:robovm-gradle-plugin:2.3.1")
    }
}

// Fixes the error "Please initialize at least one Kotlin target in 'Unciv (:)'"
kotlin {
    jvm()
}

// Plugins used for serialization of JSON for networking
plugins {
    kotlin("multiplatform") version "1.8.10"
    kotlin("plugin.serialization") version "1.8.10"
}


allprojects {
    apply(plugin = "eclipse")
    apply(plugin = "idea")


    version = "1.0.1"

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
        "implementation"("com.badlogicgames.gdx:gdx-backend-lwjgl3:${gdxVersion}")
        "implementation"("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")

        "implementation"("com.badlogicgames.gdx:gdx-tools:$gdxVersion") {
            exclude("com.badlogicgames.gdx", "gdx-backend-lwjgl")
        }

        "implementation"("com.github.MinnDevelopment:java-discord-rpc:v2.0.1")

        "implementation"("net.java.dev.jna:jna:5.11.0")
        "implementation"("net.java.dev.jna:jna-platform:5.11.0")

        // Logging for easier desktop development
        "implementation"("ch.qos.logback:logback-classic:1.2.5")
    }
}

// For server-side
project(":server") {
    apply(plugin = "kotlin")

    dependencies {
        // For server-side
        "implementation"("io.ktor:ktor-server-core:1.6.8")
        "implementation"("io.ktor:ktor-server-netty:1.6.8")
        "implementation"("ch.qos.logback:logback-classic:1.2.5")
        "implementation"("com.github.ajalt.clikt:clikt:3.4.0")
    }

}

project(":android") {
    apply(plugin = "com.android.application")
    apply(plugin = "kotlin-android")

    val natives by configurations.creating

    dependencies {
        "implementation"(project(":core"))
        "implementation"("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
    }
}

project(":ios") {
    apply(plugin = "kotlin")
    apply(plugin = "robovm")

    dependencies {
        "implementation"(project(":core"))
        "implementation"("com.mobidevelop.robovm:robovm-rt:$roboVMVersion")
        "implementation"("com.mobidevelop.robovm:robovm-cocoatouch:$roboVMVersion")
        "implementation"("com.badlogicgames.gdx:gdx-backend-robovm:$gdxVersion")
        "implementation"("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-ios")
    }
}


project(":core") {
    apply(plugin = "kotlin")
    // Serialization features (especially JSON)
    apply(plugin = "kotlinx-serialization")

    dependencies {
        "implementation"("com.badlogicgames.gdx:gdx:$gdxVersion")
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect:${com.unciv.build.BuildConfig.kotlinVersion}")

        // Ktor core
        "implementation"("io.ktor:ktor-client-core:$ktorVersion")
        // CIO engine
        "implementation"("io.ktor:ktor-client-cio:$ktorVersion")
        // WebSocket support
        "implementation"("io.ktor:ktor-client-websockets:$ktorVersion")
        // Gzip transport encoding
        "implementation"("io.ktor:ktor-client-encoding:$ktorVersion")
        // Logging support
        "implementation"("io.ktor:ktor-client-logging:$ktorVersion")
        // Content negotiation
        "implementation"("io.ktor:ktor-client-content-negotiation:$ktorVersion")
        // JSON serialization and de-serialization
        "implementation"("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    }


    // Taken from https://github.com/TomGrill/gdx-testing
    project(":tests") {
        apply(plugin = "java")
        apply(plugin = "kotlin")

        dependencies {
            "implementation"(project(":core"))

            "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

            "implementation"("junit:junit:4.13.2")
            "implementation"("org.mockito:mockito-core:5.1.1")

            "implementation"("com.badlogicgames.gdx:gdx-backend-lwjgl3:${gdxVersion}")
            "implementation"("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
            "implementation"("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
            "implementation"("com.badlogicgames.gdx:gdx:$gdxVersion")

            "testImplementation"("junit:junit:4.13.2")
            "testImplementation"("org.mockito:mockito-core:5.1.1")
            "testImplementation"("io.mockk:mockk:1.9.3")

            "testImplementation"("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
            "testImplementation"("com.badlogicgames.gdx:gdx:$gdxVersion")
            "testImplementation"("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
        }
    }
}
