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
    sourceCompatibility = JavaVersion.VERSION_1_6
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
    from({configurations.compileClasspath.get().resolve().map { if(it.isDirectory) it else zipTree(it) }})
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

        val jarFile = "desktop/build/libs/${BuildConfig.appName}.jar"
        val config = PackrConfig()
        config.platform = platform

        val forTravis = true // change to false for local build
        if(forTravis) {
            if (platform == PackrConfig.Platform.Linux32 || platform == PackrConfig.Platform.Linux64)
                config.jdk = System.getenv("JAVA_HOME")
            // take the jdk straight from the building linux computer

            if (platform == PackrConfig.Platform.Windows64)
                config.jdk = "jdk-windows-64.zip" // see how we download and name this in travis yml
            if (platform == PackrConfig.Platform.Windows32)
                config.jdk = "jdk-windows-32.zip" // see how we download and name this in travis yml
        }
        else {
            // for my computer
            config.jdk = "C:/Users/LENOVO/Downloads/java-1.8.0-openjdk-1.8.0.232-1.b09.ojdkbuild.windows.x86_64.zip"
        }
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
            if(config.outDir.exists()) delete(config.outDir)
            Packr().pack(config)
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
        finalizedBy("packr${platform.toString()}")
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
