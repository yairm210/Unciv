plugins {
    kotlin("jvm") version "2.4.20"
    id("com.github.xpenatan.gdx-teavm")
}

repositories { mavenCentral() }
kotlin { jvmToolchain(21) }

val exportedClasspath = file("../../build/validation/runtime-classpath.txt")
require(exportedClasspath.isFile) { "Run :web:writeValidationClasspath in Unciv first" }
dependencies {
    implementation(files(exportedClasspath.readLines().filter { it.isNotBlank() }))
}

gdxTeaVM {
    js {
        mainClass.set("validation.JsonContract")
        outputDir.set(layout.buildDirectory.dir("generated/teavm"))
        relativePathInOutputDir.set("js")
        targetFileName.set("unciv-json-validation.js")
        webappEnabled.set(false)
        obfuscated.set(false)
        strict.set(true)
        outOfProcess.set(false)
    }
}

tasks.register<JavaExec>("jvmContract") {
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("validation.JsonContract")
}

tasks.register<Exec>("runJavaScriptContract") {
    dependsOn("generateJavaScript")
    workingDir = projectDir
    commandLine("node", "run.cjs")
}
