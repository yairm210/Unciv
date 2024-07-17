
import com.unciv.build.BuildConfig
import com.unciv.build.BuildConfig.gdxVersion
import com.unciv.build.BuildConfig.appVersion
import io.github.fourlastor.construo.Target

plugins {
    id("kotlin")
    id("io.github.fourlastor.construo") version "1.2.0"
}

sourceSets {
    main {
        java.srcDir("src/")
    }
}

kotlin {
    jvmToolchain(17)

    target {
        compilations.all {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
    }
}
java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    // See https://libgdx.com/news/2021/07/devlog-7-lwjgl3#do-i-need-to-do-anything-else
    api("com.badlogicgames.gdx:gdx-lwjgl3-glfw-awt-macos:$gdxVersion")
}

val mainClassName = "com.unciv.app.desktop.DesktopLauncher"
val assetsDir = file("../android/assets")
val discordDir = file("discord_rpc")
val deployFolder = file("../deploy")

tasks.register<JavaExec>("run") {
    dependsOn(tasks.getByName("classes"))
    mainClass.set(mainClassName)
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
    workingDir = assetsDir
    isIgnoreExitValue = true
}

tasks.register<JavaExec>("debug") {
    dependsOn(tasks.getByName("classes"))
    mainClass.set(mainClassName)
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
    workingDir = assetsDir
    isIgnoreExitValue = true
    debug = true
}

tasks.jar { // Compiles the jar file
    dependsOn(tasks.getByName("classes"))

    // META-INF/INDEX.LIST and META-INF/io.netty.versions.properties are duplicated, but I don't know why
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(files(sourceSets.main.get().output.resourcesDir))
    from(files(sourceSets.main.get().output.classesDirs))
    // see Laurent1967's comment on https://github.com/libgdx/libgdx/issues/5491
    from({
        (
            configurations.runtimeClasspath.get().resolve() // kotlin coroutine classes live here, thanks https://stackoverflow.com/a/59021222
            + configurations.compileClasspath.get().resolve()
        ).map { if (it.isDirectory) it else zipTree(it) }})
    from(files(assetsDir))
    exclude("mods", "SaveFiles", "MultiplayerFiles", "GameSettings.json", "lasterror.txt")
    // This is for the .dll and .so files to make the Discord RPC work on all desktops
    from(files(discordDir))
    archiveFileName.set("${BuildConfig.appName}.jar")

    manifest {
        attributes(mapOf("Main-Class" to mainClassName, "Specification-Version" to appVersion))
    }
}


enum class Platform(val desc: String) {
    Windows32("windows32"), Windows64("windows64"), Linux32("linux32"), Linux64("linux64"), MacOS("mac");
}

class PackrConfig(
    var platform: Platform? = null,
    var jdk: String? = null,
    var executable: String? = null,
    var classpath: List<String>? = null,
    var removePlatformLibs: List<String>? = null,
    var mainClass: String? = null,
    var vmArgs: List<String>? = null,
    var minimizeJre: String? = null,
    var cacheJre: File? = null,
    var resources: List<File>? = null,
    var outDir: File? = null,
    var platformLibsOutDir: File? = null,
    var iconResource: File? = null,
    var bundleIdentifier: String? = null
)

val jarFile = "$rootDir/desktop/build/libs/${BuildConfig.appName}.jar"

construo {
    // Construo fields should have docs
    // name of the executable
    name.set(BuildConfig.appName.lowercase())
    // human-readable name, used for example in the `.app` name for macOS
    humanName.set(BuildConfig.appName)
    // Optional, defaults to project version
    version.set(appVersion)
    // Optional, defaults to application.mainClass or jar task main class
    mainClass.set(mainClassName)
    // Optional, defaults to $buildDir/construo/dist
    // where to put the packaged zips
        outputDir.set(deployFolder)


    jlink {
        // add arbitrary modules to be included when running jlink
        modules.addAll("jdk.crypto.ec")
    }


    targets{
        create<Target.MacOs>("macM1") {
            architecture.set(Target.Architecture.AARCH64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.11_9.tar.gz")
            // macOS needs an identifier
            identifier.set(BuildConfig.identifier)
            // Optional: icon for macOS
//                macIcon.set(project.file("path/to/mac-icon.icns"))
        }
    }
}

for (platform in Platform.values()) {
    val platformName = platform.toString()

    tasks.create("packr${platformName}") {
        // This task assumes that 'dist' has already been called - does not 'gradle depend' on it
        // so we can run 'dist' from one job and then run the packr builds from a different job

        // Needs to be here and not in doLast because the zip task depends on the outDir
        val config = PackrConfig()
        config.platform = platform

        config.apply {
            executable = "Unciv"
            classpath = listOf(jarFile)
            removePlatformLibs = config.classpath
            mainClass = mainClassName
            vmArgs = listOf("Xmx1G")
            minimizeJre = "desktop/packrConfig.json"
            outDir = file("packr")
        }

        doLast {
            //  https://gist.github.com/seanf/58b76e278f4b7ec0a2920d8e5870eed6
            fun String.runCommand(workingDir: File) {
                val process = ProcessBuilder(*split(" ").toTypedArray())
                    .directory(workingDir)
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
                    .start()

                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroy()
                    throw RuntimeException("execution timed out: $this")
                }
                if (process.exitValue() != 0) {
                    throw RuntimeException("execution failed with code ${process.exitValue()}: $this")
                }
                println(process.inputStream.bufferedReader().readText())
            }


            if (config.outDir!!.exists()) delete(config.outDir)

            // Requires that both packr and the jre are downloaded, as per buildAndDeploy.yml, "Upload to itch.io"

            val jdkFile =
                    when (platform) {
                        Platform.Linux64 -> "jre-linux-64.tar.gz"
                        Platform.Windows64 -> "jdk-windows-64.zip"
                        else -> "jre-macOS.tar.gz"
                    }

            val platformNameForPackrCmd =
                    if (platform == Platform.MacOS) "mac"
                    else platform.name.lowercase()

            val command = "java -jar $rootDir/packr-all-4.0.0.jar" +
                    " --platform $platformNameForPackrCmd" +
                    " --jdk $jdkFile" +
                    " --executable Unciv" +
                    " --classpath $jarFile" +
                    " --mainclass $mainClassName" +
                    " --vmargs Xmx1G " +
                    " --output ${config.outDir}"
            command.runCommand(rootDir)
        }

        tasks.register<Zip>("zip${platformName}") {
            archiveFileName.set("${BuildConfig.appName}-${platformName}.zip")
            from(config.outDir)
            destinationDirectory.set(deployFolder)
        }

        finalizedBy("zip${platformName}")
    }
}

tasks.register<Zip>("zipLinuxFilesForJar") {
    archiveFileName.set("linuxFilesForJar.zip")
    from(file("linuxFilesForJar"))
    destinationDirectory.set(deployFolder)
}
