import com.unciv.build.BuildConfig
import java.util.*

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-android-extensions")
}

android {
    buildToolsVersion("29.0.2")
    compileSdkVersion(29)
    sourceSets {
        getByName("main").apply {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs("src")
            aidl.srcDirs("src")
            renderscript.srcDirs("src")
            res.srcDirs("res")
            assets.srcDirs("assets")
            jniLibs.srcDirs("libs")
        }
    }
    packagingOptions {
        exclude("META-INF/robovm/ios/robovm.xml")
    }
    defaultConfig {
        applicationId = "com.unciv.app"
        minSdkVersion(14)
        targetSdkVersion(29)
        versionCode = BuildConfig.appCodeNumber
        versionName = BuildConfig.appVersion

        base.archivesBaseName = "Unciv"
    }

    // necessary for Android Work lib
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    // Had to add this crap for Travis to build, it wanted to sign the app
    // but couldn't create the debug keystore for some reason

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storePassword = "android"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }

    }
    aaptOptions {
        // Don't add local save files and fonts to release, obviously
        ignoreAssetsPattern = "!SaveFiles:!fonts:!maps:!music:!mods"
    }
    lintOptions {
        disable("MissingTranslation")
    }
}


// called every time gradle gets executed, takes the native dependencies of
// the natives configuration, and extracts them to the proper libs/ folders
// so they get packed with the APK.
task("copyAndroidNatives") {
    val natives: Configuration by configurations

    doFirst {
        file("libs/armeabi/").mkdirs()
        file("libs/armeabi-v7a/").mkdirs()
        file("libs/arm64-v8a/").mkdirs()
        file("libs/x86_64/").mkdirs()
        file("libs/x86/").mkdirs()
        natives.forEach { jar ->
            val outputDir: File? = when {
                jar.name.endsWith("natives-arm64-v8a.jar") -> file("libs/arm64-v8a")
                jar.name.endsWith("natives-armeabi-v7a.jar") -> file("libs/armeabi-v7a")
                jar.name.endsWith("natives-armeabi.jar") -> file("libs/armeabi")
                jar.name.endsWith("natives-x86_64.jar") -> file("libs/x86_64")
                jar.name.endsWith("natives-x86.jar") -> file("libs/x86")
                else -> null
            }
            outputDir?.let {
                copy {
                    from(zipTree(jar))
                    into(outputDir)
                    include("*.so")
                }
            }
        }
    }
}

tasks.whenTaskAdded {
    if ("package" in name) {
        dependsOn("copyAndroidNatives")
    }
}

tasks.register<JavaExec>("run") {
    val localProperties = project.file("../local.properties")
    val path = if (localProperties.exists()) {
        val properties = Properties()
        localProperties.inputStream().use { properties.load(it) }

        properties.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME")
    } else {
        System.getenv("ANDROID_HOME")
    }

    val adb = "$path/platform-tools/adb"

    doFirst {
        project.exec {
            commandLine(adb, "shell", "am", "start", "-n", "com.unciv.app/AndroidLauncher")
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.3.2")
}

// sets up the Android Eclipse project, using the old Ant based build.
eclipse {
    jdt {
        sourceCompatibility = JavaVersion.VERSION_1_6
        targetCompatibility = JavaVersion.VERSION_1_6
    }

    classpath {
        plusConfigurations = plusConfigurations.apply { add(project.configurations.compile.get()) }
        containers("com.android.ide.eclipse.adt.ANDROID_FRAMEWORK", "com.android.ide.eclipse.adt.LIBRARIES")
    }

    project {
        name = "${BuildConfig.appName}-android"
        natures("com.android.ide.eclipse.adt.AndroidNature")
        buildCommands.clear()
        buildCommand("com.android.ide.eclipse.adt.ResourceManagerBuilder")
        buildCommand("com.android.ide.eclipse.adt.PreCompilerBuilder")
        buildCommand("org.eclipse.jdt.core.javabuilder")
        buildCommand("com.android.ide.eclipse.adt.ApkBuilder")
    }
}
