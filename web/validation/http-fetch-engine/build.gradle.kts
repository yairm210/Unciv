import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
plugins {
    kotlin("jvm") version "2.4.20"
    id("com.github.xpenatan.gdx-teavm")
}

repositories { mavenCentral() }

dependencies {
    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("org.teavm:teavm-jso-apis:0.17.0-SNAPSHOT")
    teavm("io.ktor:ktor-client-core:3.5.2")
    teavm("org.teavm:teavm-jso-apis:0.17.0-SNAPSHOT")
}

kotlin { jvmToolchain(21) }

sourceSets.main {
    java.srcDir("src/main/kotlin")
}

tasks.named<ProcessResources>("processResources") {
    from(file("../../src/main/resources/META-INF/services/io.ktor.client.HttpClientEngineContainer")) {
        into("META-INF/services")
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    source("../../src/main/kotlin/com/unciv/app/web/FetchHttpClientEngine.kt")
}

gdxTeaVM {
    js {
        mainClass.set("validation.MainKt")
        outputDir.set(layout.buildDirectory.dir("generated/teavm"))
        relativePathInOutputDir.set("js")
        targetFileName.set("unciv-fetch-engine-validation.js")
        webappEnabled.set(false)
        obfuscated.set(false)
        strict.set(true)
        outOfProcess.set(false)
        processMemory.set(2048)
    }
}

tasks.register<Exec>("runJavaScriptContract") {
    dependsOn("generateJavaScript")
    workingDir = projectDir
    commandLine("node", "run.cjs")
}
