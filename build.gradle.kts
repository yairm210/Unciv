import com.unciv.build.BuildConfig.gdxVersion
import com.unciv.build.BuildConfig
import java.net.URL

plugins {
    kotlin("jvm") version com.unciv.build.BuildConfig.kotlinVersion
    id("org.jetbrains.dokka") version (com.unciv.build.BuildConfig.dokkaVersion) apply false
}

// You'll still get kotlin-reflect-1.3.70.jar in your classpath, but will no longer be used
configurations.all { resolutionStrategy {
    force("org.jetbrains.kotlin:kotlin-reflect:${com.unciv.build.BuildConfig.kotlinVersion}")
} }


buildscript {

    repositories {
        // Chinese mirrors for quicker loading for chinese devs - uncomment if you're chinese
        // maven{ url = uri("https://maven.aliyun.com/repository/jcenter") }
        // maven{ url = uri("https://maven.aliyun.com/repository/google") }
        // maven{ url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // maven{ url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        gradlePluginPortal()
        maven{ url = uri("https://jitpack.io") } // for the anuken packr
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${com.unciv.build.BuildConfig.kotlinVersion}")
        classpath("de.richsource.gradle.plugins:gwt-gradle-plugin:0.6")
        classpath("com.android.tools.build:gradle:7.0.2")
        classpath("com.mobidevelop.robovm:robovm-gradle-plugin:2.3.1")

        // This is for wrapping the .jar file into a standalone executable
        classpath("com.github.anuken:packr:-SNAPSHOT")

        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${com.unciv.build.BuildConfig.dokkaVersion}")
    }
}
        
allprojects {
    apply(plugin  = "eclipse")
    apply(plugin  = "idea")


    version = "1.0.1"

    repositories {
        // Chinese mirrors for quicker loading for chinese devs - uncomment if you're chinese
        // maven{ url = uri("https://maven.aliyun.com/repository/jcenter") }
        // maven{ url = uri("https://maven.aliyun.com/repository/google") }
        // maven{ url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // maven{ url = uri("https://maven.aliyun.com/repository/public") }
        google()
        gradlePluginPortal()
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/releases/") }
        maven{ url = uri("https://jitpack.io") } // for java-discord-rpc
    }

    val subProject = this
    if (name != "android") {
        apply(plugin = "org.jetbrains.kotlin.jvm")
    }
    apply(plugin = "org.jetbrains.dokka")
    tasks.withType<org.jetbrains.dokka.gradle.DokkaTaskPartial>().configureEach {
        // See: https://kotlin.github.io/dokka/1.6.0/user_guide/gradle/usage/#configuration-options
        suppressInheritedMembers.set(false)
        suppressObviousFunctions.set(false)
        offlineMode.set(false)
        dokkaSourceSets {
            configureEach {
                includeNonPublic.set(true)
//                suppress.set(false)
                skipDeprecated.set(false) // They're crossed out anyway just like in the official Kotlin docs.
//                reportUndocumented.set(true)
                skipEmptyPackages.set(true)
                platform.set(org.jetbrains.dokka.Platform.jvm)
//                classpath.from(file(""))
//                if (subProject.file("Module.md").exists()) { // Works, but honestly I'm not sure it's necessary.
//                    includes.from("Module.md")
//                }
//                samples.from("")
                sourceLink {
                    localDirectory.set(file(".")) // I think this is just used to figure out what to append to the GH source URL. Also the source of Gradle warnings.
                    remoteUrl.set(URL("https://github.com/yairm210/Unciv/tree/master/${rootProject.relativeProjectPath(subProject.name)}"))
                    remoteLineSuffix.set("#L")
                }
                jdkVersion.set(7)
                noStdlibLink.set(false)
                noJdkLink.set(false)
                noAndroidSdkLink.set(false)
                externalDocumentationLink {
                    url.set(URL("https://libgdx.badlogicgames.com/ci/nightlies/docs/api/"))
                }
                suppressGeneratedFiles.set(false)
            }
        }
    }
}

apply(plugin = "org.jetbrains.kotlin.jvm")
apply(plugin = "org.jetbrains.dokka")
val dokkaPlugin by configurations
tasks.register<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>("dokkaRoot") {
    dependencies {
        dokkaPlugin("org.jetbrains.dokka:all-modules-page-plugin:${com.unciv.build.BuildConfig.dokkaVersion}")
    }
    outputDirectory.set(file(BuildConfig.dokkaOutpath))
    addChildTasks(childProjects.values, "dokkaHtmlPartial")
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
    }

}

project(":android") {
    apply(plugin = "com.android.application")
    apply(plugin = "kotlin-android")

    val natives by configurations.creating

    dependencies {
        "implementation"(project(":core"))
        "implementation"("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
    }
}

//project(":ios") { // Entire build script got red squigglies on enabling Dokka multimodule, but IOS is explicitly unsupported anyway.
//    apply(plugin = "kotlin")
//    apply(plugin = "robovm")
//
//    dependencies {
//        "implementation"(project(":core"))
//        "implementation"("com.mobidevelop.robovm:robovm-rt:$roboVMVersion")
//        "implementation"("com.mobidevelop.robovm:robovm-cocoatouch:$roboVMVersion")
//        "implementation"("com.badlogicgames.gdx:gdx-backend-robovm:$gdxVersion")
//        "implementation"("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-ios")
//    }
//}


project(":core") {
    apply(plugin = "kotlin")

    dependencies {
        "implementation"("com.badlogicgames.gdx:gdx:$gdxVersion")
    }


    // Taken from https://github.com/TomGrill/gdx-testing
    project(":tests") {
        apply(plugin = "java")
        apply(plugin = "kotlin")

        dependencies {
            "implementation"(project(":core"))

            "implementation"("junit:junit:4.13.1")
            "implementation"("org.mockito:mockito-all:1.10.19")

            "implementation"("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
            "implementation"("com.badlogicgames.gdx:gdx:$gdxVersion")

            "testImplementation"("junit:junit:4.13.1")
            "testImplementation"("org.mockito:mockito-all:1.10.19")
            "testImplementation"("io.mockk:mockk:1.9.3")

            "testImplementation"("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
            "testImplementation"("com.badlogicgames.gdx:gdx:$gdxVersion")
            "testImplementation"("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
        }
    }
}
