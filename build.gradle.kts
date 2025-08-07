
import com.unciv.build.BuildConfig.appVersion
import com.unciv.build.BuildConfig.coroutinesVersion
import com.unciv.build.BuildConfig.gdxVersion
import com.unciv.build.BuildConfig.jnaVersion
import com.unciv.build.BuildConfig.kotlinVersion
import com.unciv.build.BuildConfig.ktorVersion
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
    id("io.github.yairm210.purity-plugin") version "0.0.46" apply(false)
}

allprojects {
//    repositories{ // for local purity
//        mavenLocal()
//    }
    
    apply(plugin = "io.github.yairm210.purity-plugin")
    configure<yairm210.purity.PurityConfiguration>{
        wellKnownPureFunctions = setOf(
//            "kotlin.lazy", // moved
//            "kotlin.getValue", // moved
//            "kotlin.error", // moved
//            "kotlin.collections.mutableMapOf", // moved
        )
        wellKnownReadonlyFunctions = setOf(
            "com.badlogic.gdx.math.Vector2.len",
            "com.badlogic.gdx.math.Vector2.cpy",
            "com.badlogic.gdx.math.Vector2.hashCode",

            // all moved
//            "kotlin.collections.getValue",
//            "java.util.BitSet.get",
//            "kotlin.collections.randomOrNull",
//            "kotlin.collections.Collection.isEmpty",
//            "kotlin.collections.subtract",
//            "kotlin.collections.union",
//            "kotlin.collections.intersect",
//            "kotlin.collections.List.indexOf",
            
        )
        wellKnownPureClasses = setOf<String>(
//            "java.lang.Integer" // moved
        )
        wellKnownInternalStateClasses = setOf<String>(
            // Moved all
//            "kotlin.collections.MutableList",
//            "kotlin.collections.MutableSet",
//            "kotlin.collections.MutableMap",
//            "kotlin.collections.List",
//            "kotlin.collections.Set",
//            "kotlin.collections.Map",
//            "kotlin.collections.ArrayDequeue",
//            "java.util.BitSet",
            
            "com.unciv.models.stats.Stats",
            "com.unciv.models.Counter",
            "com.unciv.models.ruleset.tile.ResourceSupplyList",
            "com.badlogic.gdx.math.Vector2",
            "com.unciv.models.ruleset.validation.RulesetErrorList"
        )
        warnOnPossibleAnnotations = true
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
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        "implementation"("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
        "implementation"("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")

        "implementation"("com.badlogicgames.gdx:gdx-tools:$gdxVersion") {
            exclude("com.badlogicgames.gdx", "gdx-backend-lwjgl")
        }

        // Needed to display "Playing Unciv" in Discord
        "implementation"("com.github.MinnDevelopment:java-discord-rpc:v2.0.1")

        // Needed for Windows turn notifiers
        "implementation"("net.java.dev.jna:jna:$jnaVersion")
        "implementation"("net.java.dev.jna:jna-platform:$jnaVersion")
    }
}

// For server-side
project(":server") {
    apply(plugin = "kotlin")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    dependencies {
        // For server-side
        "implementation"("io.ktor:ktor-server-core:$ktorVersion")
        "implementation"("io.ktor:ktor-server-netty:$ktorVersion")
        "implementation"("io.ktor:ktor-server-auth:${ktorVersion}")
        "implementation"("io.ktor:ktor-server-content-negotiation:$ktorVersion")
        "implementation"("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
        "implementation"("io.ktor:ktor-server-websockets:${ktorVersion}")
        "implementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        "implementation"("ch.qos.logback:logback-classic:1.5.18")
        "implementation"("com.github.ajalt.clikt:clikt:4.4.0")

        // clikt somehow needs this
        "implementation"("net.java.dev.jna:jna:$jnaVersion")
        "implementation"("net.java.dev.jna:jna-platform:$jnaVersion")
    }

}

private fun getSdkPath(): String? {
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
        
        "implementation"("io.github.yairm210:purity-annotations:0.0.40")

        "implementation"("io.ktor:ktor-client-core:$ktorVersion")
        "implementation"("io.ktor:ktor-client-cio:$ktorVersion")
        "implementation"("io.ktor:ktor-client-websockets:$ktorVersion")
        // Gzip transport encoding
        "implementation"("io.ktor:ktor-client-encoding:$ktorVersion")
        "implementation"("io.ktor:ktor-client-content-negotiation:$ktorVersion")
        // JSON serialization and de-serialization
        "implementation"("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
        "implementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
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
