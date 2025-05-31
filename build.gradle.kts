
import com.unciv.build.BuildConfig.coroutinesVersion
import com.unciv.build.BuildConfig.gdxVersion
import com.unciv.build.BuildConfig.kotlinVersion
import com.unciv.build.BuildConfig.ktorVersion
import com.unciv.build.BuildConfig.appVersion


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
        classpath("com.android.tools.build:gradle:8.9.1")
    }
}

// Fixes the error "Please initialize at least one Kotlin target in 'Unciv (:)'"
kotlin {
    jvm()
}

// Plugins used for serialization of JSON for networking
plugins {
    id("io.gitlab.arturbosch.detekt").version("1.23.0-RC3")
    // For some weird reason, the *docker build* fails to recognize linking to the shared kotlinVersion in plugins
    // This is *with* gradle 8.2 downloaded according the project specs, no idea what that's about
    kotlin("multiplatform") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
}

allprojects {
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
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        "implementation"("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
        "implementation"("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")

        "implementation"("com.badlogicgames.gdx:gdx-tools:$gdxVersion") {
            exclude("com.badlogicgames.gdx", "gdx-backend-lwjgl")
        }

        // Needed to display "Playing Unciv" in Discord
        "implementation"("com.github.MinnDevelopment:java-discord-rpc:v2.0.1")

        // Needed for Windows turn notifiers
        "implementation"("net.java.dev.jna:jna:5.11.0")
        "implementation"("net.java.dev.jna:jna-platform:5.11.0")
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

if (System.getenv("ANDROID_HOME") != null) {
    project(":android") {
        apply(plugin = "com.android.application")
        apply(plugin = "kotlin-android")

        val natives by configurations.creating

        dependencies {
            "implementation"(project(":core"))
            // Not sure why I had to add this in for the upgrade to 1.12.1 to work, we can probably remove this later since it's contained in core
            "implementation"("com.badlogicgames.gdx:gdx:$gdxVersion")
            "implementation"("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
            "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
            natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
            natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
            natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
            natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
        }
    }
}


project(":core") {
    apply(plugin = "kotlin")
    // Serialization features (especially JSON)
    apply(plugin = "kotlinx-serialization")

    dependencies {
        "implementation"("com.badlogicgames.gdx:gdx:$gdxVersion")
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

        "implementation"("io.ktor:ktor-client-core:$ktorVersion")
        "implementation"("io.ktor:ktor-client-cio:$ktorVersion")
        "implementation"("io.ktor:ktor-client-websockets:$ktorVersion")
        // Gzip transport encoding
        "implementation"("io.ktor:ktor-client-encoding:$ktorVersion")
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

            "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

            "implementation"("junit:junit:4.13.2")
            "implementation"("org.mockito:mockito-core:5.13.0")

            "implementation"("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
            "implementation"("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
            "implementation"("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
            "implementation"("com.badlogicgames.gdx:gdx:$gdxVersion")
        }
    }
}
