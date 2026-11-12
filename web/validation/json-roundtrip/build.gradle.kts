import org.teavm.gradle.tasks.GenerateJavaScriptTask

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.teavm") version "0.16.0"
}

repositories { mavenCentral() }
kotlin { jvmToolchain(21) }

val exportedClasspath = file("../../build/validation/runtime-classpath.txt")
require(exportedClasspath.isFile) { "Run :web:writeValidationClasspath in Unciv first" }
dependencies {
    implementation(files(exportedClasspath.readLines().filter { it.isNotBlank() }))
    teavm(teavm.libs.jsoApis)
}

teavm.js {
    mainClass = "validation.JsonContract"
    obfuscated = false
    strict = true
}

tasks.register<JavaExec>("jvmContract") {
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("validation.JsonContract")
}

tasks.named<GenerateJavaScriptTask>("generateJavaScript") {
    val classlib = file(providers.gradleProperty("classlibJar").get())
    val core = file(providers.gradleProperty("coreJar").get())
    val jso = file(providers.gradleProperty("jsoJar").get())
    require(classlib.isFile && core.isFile && jso.isFile) { "Pass built, immutable TeaVM fork jars" }
    val originalTarget = objects.fileCollection().from(classpath.from.toList())
    val target = originalTarget.filter {
        it.name != "teavm-classlib-0.16.0.jar" && it.name != "teavm-jso-apis-0.16.0.jar"
    }
    classpath.setFrom(target, classlib, jso)
    val originalDaemon = objects.fileCollection().from(daemonClasspath.from.toList())
    daemonClasspath.setFrom(originalDaemon.filter { it.name != "teavm-core-0.16.0.jar" }, core,
        target.filter { it.name == "teavm-platform-0.16.0.jar" })
    outOfProcess.set(true)
    processMemory.set(4096)
}

tasks.register<Exec>("runJavaScriptContract") {
    dependsOn("generateJavaScript")
    workingDir = projectDir
    commandLine("node", "run.cjs")
}
