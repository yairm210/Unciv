import com.badlogicgames.packr.Packr
import com.badlogicgames.packr.PackrConfig
import com.unciv.build.BuildConfig
import groovy.util.Node
import groovy.util.XmlNodePrinter
import groovy.util.XmlParser
import java.io.FileWriter
import java.io.PrintWriter


plugins {
    id("kotlin")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_7
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

tasks.register<JavaExec>("run") {
    dependsOn(tasks.getByName("classes"))

    main = mainClassName
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
    workingDir = assetsDir
    isIgnoreExitValue = true
}

tasks.register<JavaExec>("debug") {
    dependsOn(tasks.getByName("classes"))
    main = mainClassName
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
    from(files(assetsDir))
    // This is for the .dll and .so files to make the Discord RPC work on all desktops
    from(files(discordDir))
    archiveFileName.set("${BuildConfig.appName}.jar")

    manifest {
        attributes(mapOf("Main-Class" to mainClassName, "Specification-Version" to BuildConfig.appVersion))
    }
}

for(platform in PackrConfig.Platform.values()) {
    val platformName = platform.toString()

    tasks.create("packr${platformName}") {
        dependsOn(tasks.getByName("dist"))

        val jarFile = "$rootDir/desktop/build/libs/${BuildConfig.appName}.jar"
        val config = PackrConfig()
        config.platform = platform

        if (platform == PackrConfig.Platform.Linux32 || platform == PackrConfig.Platform.Linux64)
            config.jdk = "jdk-linux-64.zip"
        // take the jdk straight from the building linux computer

        if (platform == PackrConfig.Platform.Windows64)
            config.jdk = "jdk-windows-64.zip" // see how we download and name this in travis yml
        if (platform == PackrConfig.Platform.Windows32)
            config.jdk = "jdk-windows-32.zip" // see how we download and name this in travis yml

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

            // Requires that both packr and the linux jre are downloaded, as per buildAndDeploy.yml, "Upload to itch.io"
            if (platform == PackrConfig.Platform.Linux64 || platform == PackrConfig.Platform.MacOS) {
                val jdkFile = if (platform == PackrConfig.Platform.Linux64) "jre-linux-64.tar.gz"
                else "jre-macOS.tar.gz"
                val platformNameForPackrCmd =
                    if (platform == PackrConfig.Platform.Linux64) "linux64"
                    else "mac"
                val command = "java -jar $rootDir/packr-all-4.0.0.jar" +
                        " --platform $platformNameForPackrCmd" +
                        " --jdk $jdkFile" +
                        " --executable Unciv" +
                        " --classpath $jarFile" +
                        " --mainclass $mainClassName" +
                        " --vmargs Xmx1G " + (if (platform == PackrConfig.Platform.MacOS) "XstartOnFirstThread" else "") +
                        " --output ${config.outDir}"
                command.runCommand(rootDir)
            } else Packr().pack(config)
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

tasks.register("packr") {
    for(platform in PackrConfig.Platform.values())
        finalizedBy("packr$platform")
}



eclipse {
    project {
        name = "${BuildConfig.appName}-desktop"
        linkedResource(mapOf("name" to "assets", "type" to "2", "location" to "PARENT-1-PROJECT_LOC/android/assets"))
    }
}

tasks.register("afterEclipseImport") {
    description =  "Post processing after project generation"
    group = "IDE"

    doLast {
        val classpath = XmlParser().parse(file(".classpath"))
        Node(classpath, "classpathentry", mapOf("kind" to "src", "path" to "assets"))
        val writer = FileWriter(file(".classpath"))
        val printer = XmlNodePrinter(PrintWriter(writer))
        printer.isPreserveWhitespace = true
        printer.print(classpath)
    }
}
