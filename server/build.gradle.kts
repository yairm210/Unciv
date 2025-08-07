import com.unciv.build.BuildConfig

plugins {
    id("kotlin")
}

sourceSets {
    main {
        java.srcDir("src/")
    }
}

val mainClassName = "com.unciv.app.server.UncivServer"
val assetsDir = file("../android/assets")
val deployFolder = file("../deploy")

// See https://github.com/libgdx/libgdx/wiki/Starter-classes-and-configuration#common-issues
// and https://github.com/yairm210/Unciv/issues/5679
val jvmArgsForMac = listOf("-XstartOnFirstThread", "-Djava.awt.headless=true")
tasks.register<JavaExec>("run") {
    jvmArgs = mutableListOf<String>()
    if ("mac" in System.getProperty("os.name").lowercase())
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
    from({ configurations.compileClasspath.get().resolve().map { if (it.isDirectory) it else zipTree(it) } })
    archiveFileName.set("UncivServer.jar")

    manifest {
        attributes(mapOf("Main-Class" to mainClassName, "Specification-Version" to BuildConfig.appVersion))
    }
}

enum class Platform() {
    Windows32, Windows64, Linux32, Linux64, MacOS;
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

for (platform in Platform.entries) {
    tasks.register("packr${platform.name}") {
        dependsOn(tasks.getByName("dist"))

        // Needs to be here and not in doLast because the zip task depends on the outDir
        val jarFile = "$rootDir/server/build/libs/UncivServer.jar"
        val config = PackrConfig()
        config.platform = platform

        config.apply {
            executable = "UncivServer"
            classpath = listOf(jarFile)
            removePlatformLibs = config.classpath
            mainClass = mainClassName
            vmArgs = listOf("Xmx1G")
            minimizeJre = "server/packrConfig.json"
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
                    println("execution returned code ${process.exitValue()}: $this")
                }
                println(process.inputStream.bufferedReader().readText())
            }


            if (config.outDir!!.exists()) delete(config.outDir)

            // Requires that both packr and the jre are downloaded, as per buildAndDeploy.yml, "Upload to itch.io"

            val jdkFile = when (platform) {
                Platform.Linux64 -> "jre-linux-64.tar.gz"
                Platform.Windows64 -> "jdk-windows-64.zip"
                else -> "jre-macOS.tar.gz"
            }

            val platformNameForPackrCmd = when (platform) {
                Platform.MacOS -> "mac"
                else -> platform.name.lowercase()
            }

            val command = "java -jar $rootDir/packr-all-4.0.0.jar" +
                    " --platform $platformNameForPackrCmd" +
                    " --jdk $jdkFile" +
                    " --executable UncivServer" +
                    " --classpath $jarFile" +
                    " --mainclass $mainClassName" +
                    " --vmargs Xmx1G " +
                    (if (platform == Platform.MacOS) jvmArgsForMac.joinToString(" ") {
                        it.removePrefix("-")
                    }
                    else "") +
                    " --output ${config.outDir}"

            command.runCommand(rootDir)
        }

        tasks.register<Zip>("zip${platform.name}") {
            archiveFileName.set("UncivServer-${platform.name}.zip")
            from(config.outDir)
            destinationDirectory.set(deployFolder)
        }

        finalizedBy("zip${platform.name}")
    }
}

tasks.register<Zip>("zipLinuxFilesForJar") {
    archiveFileName.set("linuxFilesForJar.zip")
    from(file("linuxFilesForJar"))
    destinationDirectory.set(deployFolder)
}
