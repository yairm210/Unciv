
import com.google.common.io.Files
import com.unciv.build.BuildConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("kotlin")
}

sourceSets {
    main {
        java.srcDir("src/")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}
java {
    // required for building Unciv with a Java version higher than 24 (e.g. Java 25)
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_1_8
}

private enum class Platform(
    val packrName: String,
    val jdkFile: String,
    val unixPermissions: Boolean,
    val downloadUrl: String,
    val vmArgs: Array<String> = emptyArray()
) {
    //Windows32("windows32", "jre-windows-32.zip", false, "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x86/jre/hotspot/normal/eclipse"), // dropped by packr
    Windows64("windows64", "jre-windows-64.zip", false, "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse"),
    Linux64("linux64", "jre-linux-64.tar.gz", true, "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jre/hotspot/normal/eclipse"),
    MacOS("mac", "jre-macOS.tar.gz", true, "https://api.adoptium.net/v3/binary/latest/21/ga/mac/x64/jre/hotspot/normal/eclipse",
        // See https://github.com/libgdx/libgdx/wiki/Starter-classes-and-configuration#common-issues
        // and https://github.com/yairm210/Unciv/issues/5679
        vmArgs = arrayOf("XstartOnFirstThread", "Djava.awt.headless=true")
    )
    // Linux32 is dropped by packr and there's no jre's anymore
    ;
    companion object {
        fun current(): Platform {
            //val os = System.getProperty("os.name")?.lowercase() ?: ""
            val os = org.gradle.internal.os.OperatingSystem.current()
            return when {
                os.isWindows -> Windows64
                os.isMacOsX -> MacOS
                else -> Linux64
            }
        }
    }
}

val mainClassName = "com.unciv.app.desktop.DesktopLauncher"
val assetsDir = file("../android/assets")
val discordDir = file("discord_rpc")
val deployFolder = file("../deploy")

private fun JavaExec.configureCommon() {
    dependsOn(tasks.getByName("classes"))
    mainClass.set(mainClassName)
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
    workingDir = assetsDir
    jvmArgs(*Platform.current().vmArgs.map { "-$it" }.toTypedArray())
    isIgnoreExitValue = true
}

tasks.register<JavaExec>("run") {
    description = "Build and run"
    configureCommon()
}

tasks.register<JavaExec>("debug") {
    description = "Build and debug"
    configureCommon()
    debug = true
}

tasks.register<Jar>("dist") {
    description = "Compiles the jar file"
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
        attributes(mapOf("Main-Class" to mainClassName, "Specification-Version" to BuildConfig.appVersion))
    }
}


private val packrDownloadUrl = "https://github.com/libgdx/packr/releases/download/4.0.0/packr-all-4.0.0.jar"
private val packrLocalName = "packr-all-4.0.0.jar"
private val cacheDirectory = ".jre-cache" // relative to desktop, the place for downloaded archives

private fun downloadIfOutdated(url: String, dest: File) {
    dest.parentFile?.mkdirs()
    ant.invokeMethod("get", mapOf(
        "src" to url,
        "dest" to dest.absolutePath,
        "usetimestamp" to true,
        "verbose" to true
    ))
}

//  https://gist.github.com/seanf/58b76e278f4b7ec0a2920d8e5870eed6
private fun runCommand(workingDir: File, vararg args: String) {
    val command = args.joinToString(" ")
    val outputFile = File.createTempFile("packr-", ".log")
    try {
        // Capture both streams directly to a file so pipe buffers cannot block Packr.
        val process = ProcessBuilder(*args)
            .directory(workingDir)
            .redirectErrorStream(true)
            .redirectOutput(outputFile)
            .start()

        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
        }
        val output = outputFile.readText()
        if (!finished) {
            throw RuntimeException("execution timed out: $command\\n$output")
        }
        if (process.exitValue() != 0) {
            throw RuntimeException("execution failed with code ${process.exitValue()}: $command\\n$output")
        }
        print(output)
    } finally {
        outputFile.delete()
    }
}

for (platform in Platform.entries) {
    val outputDir = layout.buildDirectory.dir("packr/${platform.packrName}").get().asFile

    tasks.register("packr$platform") {
        description = "Run packr for $platform to ${outputDir.path}"
        mustRunAfter("dist")

        val jarFile = file("$rootDir/desktop/build/libs/${BuildConfig.appName}.jar")
        val packrJarFile = file("$cacheDirectory/$packrLocalName")
        val jreArchiveFile = file("$cacheDirectory/${platform.jdkFile}")

        doFirst {
            // This task assumes that 'dist' has already been called - does not 'gradle depend' on it
            // so we can run 'dist' from one job and then run the packr builds from a different job
            // Note we only guard for existence without checking staleness, which would be complex
            if (!jarFile.exists())
                throw GradleException("${jarFile.path} not found — run 'desktop:dist' before packr tasks")
            downloadIfOutdated(packrDownloadUrl, packrJarFile)
            downloadIfOutdated(platform.downloadUrl, jreArchiveFile)
        }

        doLast {
            // packr demands its output directory must be at least empty, but it can be nonexistent
            if (outputDir.exists()) {
                // JRE/JDK distributions routinely ship many files without owner-write permission, which a simple delete can't delete.
                outputDir.walkBottomUp().forEach { it.setWritable(true) }
                if (!outputDir.deleteRecursively())
                    throw GradleException("Could not fully clear $outputDir — check for locked or read-only files")
            }

            runCommand(rootDir,
                "java",
                "-jar", packrJarFile.path,
                "--platform", platform.packrName,
                "--jdk", jreArchiveFile.path,
                "--executable", "Unciv",
                "--classpath", jarFile.path,
                "--mainclass", mainClassName,
                "--vmargs", *platform.vmArgs, "Xmx4G", "Dunciv.packr=true",
                "--output", outputDir.path
            )

            // This is only kinda wrong
            // - Windows needs packr to embed an icon as resource which packr doesn't support
            // - Linux needs e.g. the desktop file refer to it depending on distro, and the packr release doesn't manage one
            // - Mac: packr's --icon parameter is for mac only, but needs icns format
            // Leaving it in, to allow clever users to fix that themselves
            Files.copy(File("$rootDir/extraImages/Icons/Unciv.ico"), File(outputDir, "Unciv.ico"))
        }

    }

    tasks.register<Zip>("zip$platform") {
        dependsOn("packr$platform")
        description = "Zip packr output for $platform into a distribution archive"
        archiveFileName.set("${BuildConfig.appName}-$platform.zip")
        from(outputDir) {
            if (platform.unixPermissions) {
                filesMatching(listOf(
                    "Unciv", "jre/bin/*",
                    "Contents/MacOS/Unciv",
                    "Contents/Resources/jre/bin/*",
                )) {
                    permissions { unix("rwxr-xr-x") }
                }
            }
        }
        destinationDirectory.set(deployFolder)
    }
}

tasks.register<Zip>("zipLinuxFilesForJar") {
    description = "Zip the Linux support files"
    archiveFileName.set("linuxFilesForJar.zip")
    from(file("linuxFilesForJar")) {
        filesMatching("Unciv.sh") {
            permissions { unix("rwxr-xr-x") }
        }
    }
    destinationDirectory.set(deployFolder)
}
