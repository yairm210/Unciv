import com.badlogicgames.packr.Packr
import com.badlogicgames.packr.PackrConfig
import com.unciv.build.BuildConfig

plugins {
    id("kotlin")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

sourceSets {
    main {
        java.srcDir("src/")
    }
}

val mainClassName = "com.unciv.app.desktop.DesktopLauncher"
val assetsDir = file("../android/assets")
val discordDir = file("discord_rpc")
val deployFolder = file("../deploy")

// See https://github.com/libgdx/libgdx/wiki/Starter-classes-and-configuration#common-issues
// and https://github.com/yairm210/Unciv/issues/5679
val jvmArgsForMac = listOf("-XstartOnFirstThread", "-Djava.awt.headless=true")
tasks.register<JavaExec>("run") {
    jvmArgs = mutableListOf<String>()
    if ("mac" in System.getProperty("os.name").toLowerCase())
        (jvmArgs as MutableList<String>).addAll(jvmArgsForMac)
        // These are non-standard, only available/necessary on Mac.

    dependsOn(tasks.getByName("classes"))

    mainClass.set(mainClassName)
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
    workingDir = assetsDir
    isIgnoreExitValue = true
}

tasks.register<JavaExec>("debug") {
    jvmArgs = jvmArgsForMac
    dependsOn(tasks.getByName("classes"))
    mainClass.set(mainClassName)
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
    workingDir = assetsDir
    isIgnoreExitValue = true
    debug = true
}

tasks.register<Jar>("dist") { // Compiles the jar file
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
    // This is for the .dll and .so files to make the Discord RPC work on all desktops
    from(files(discordDir))
    archiveFileName.set("${BuildConfig.appName}.jar")

    manifest {
        attributes(mapOf("Main-Class" to mainClassName, "Specification-Version" to BuildConfig.appVersion))
    }
}

for (platform in PackrConfig.Platform.values()) {
    val platformName = platform.toString()

    tasks.create("packr${platformName}") {
        dependsOn(tasks.getByName("dist"))

        // Needs to be here and not in doLast because the zip task depends on the outDir
        val jarFile = "$rootDir/desktop/build/libs/${BuildConfig.appName}.jar"
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


            if (config.outDir.exists()) delete(config.outDir)

            // Requires that both packr and the jre are downloaded, as per buildAndDeploy.yml, "Upload to itch.io"

            // Use old version of packr - newer versions aren't Windows32-compliant
            if (platform == PackrConfig.Platform.Windows32) {
                config.jdk = "jdk-windows-32.zip"
                Packr().pack(config)
            } else {
                val jdkFile =
                    when (platform) {
                        PackrConfig.Platform.Linux64 -> "jre-linux-64.tar.gz"
                        PackrConfig.Platform.Windows64 -> "jdk-windows-64.zip"
                        else -> "jre-macOS.tar.gz"
                    }

                val platformNameForPackrCmd =
                    if (platform == PackrConfig.Platform.MacOS) "mac"
                    else platform.name.toLowerCase()

                val command = "java -jar $rootDir/packr-all-4.0.0.jar" +
                        " --platform $platformNameForPackrCmd" +
                        " --jdk $jdkFile" +
                        " --executable Unciv" +
                        " --classpath $jarFile" +
                        " --mainclass $mainClassName" +
                        " --vmargs Xmx1G " +
                        (if (platform == PackrConfig.Platform.MacOS) jvmArgsForMac.joinToString(" ") {
                            it.removePrefix("-")
                        }
                        else "") +
                        " --output ${config.outDir}"
                command.runCommand(rootDir)
            }
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
