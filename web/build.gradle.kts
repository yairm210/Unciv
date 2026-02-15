import com.unciv.build.GenerateWebJsTestSuiteTask
import com.unciv.build.WebPostProcessDistTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("kotlin")
}

val gdxVersion: String by project
val coroutinesVersion: String by project
val ktorVersion: String by project
val gdxTeaVMVersion = "-SNAPSHOT"
val generatedWebJsTestsDir = layout.buildDirectory.dir("generated/web-jstests/kotlin")

sourceSets {
    main {
        java.srcDir("src/main/java")
        java.srcDir("src/main/kotlin")
        java.srcDir("../tests/src")
        java.srcDir(generatedWebJsTestsDir)
        java.exclude("com/unciv/dev/**")
        java.exclude("com/unciv/testing/GdxTestRunner.kt")
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
    maven { url = uri("https://jitpack.io") }
    maven {
        url = uri("http://teavm.org/maven/repository/")
        isAllowInsecureProtocol = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(project(":core"))
    implementation(kotlin("reflect"))
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("com.github.xpenatan.gdx-teavm:backend-teavm:$gdxTeaVMVersion")
    implementation("com.github.xpenatan.gdx-teavm:gdx-freetype-teavm:$gdxTeaVMVersion")
}

val generateWebJsTestSuite by tasks.registering(GenerateWebJsTestSuiteTask::class) {
    group = "web"
    description = "Generate deterministic browser JS test suite from tests/src classes."
    testsSourceRoot.set(rootProject.layout.projectDirectory.dir("tests/src"))
    generatedTestsDir.set(generatedWebJsTestsDir)
}

tasks.named("compileKotlin") {
    dependsOn(generateWebJsTestSuite)
}

tasks.register<JavaExec>("webBuildWasm") {
    dependsOn("classes")
    group = "web"
    description = "Build WASM web bundle with TeaVM"
    mainClass.set("com.unciv.app.web.BuildWebWasm")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
    maxHeapSize = "4g"
    jvmArgs("-Xms1g", "-XX:+UseG1GC")
}

tasks.register<WebPostProcessDistTask>("webPostProcessDist") {
    outputDir.set(rootProject.layout.projectDirectory.dir("web/build/dist"))
}

tasks.register<JavaExec>("webBuildJs") {
    dependsOn("classes")
    group = "web"
    description = "Build JS web bundle with TeaVM"
    mainClass.set("com.unciv.app.web.BuildWebJs")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
    maxHeapSize = "4g"
    jvmArgs("-Xms1g", "-XX:+UseG1GC")
}

tasks.named("webBuildJs") {
    finalizedBy("webPostProcessDist")
}

tasks.named("webBuildWasm") {
    finalizedBy("webPostProcessDist")
}

tasks.register<Exec>("webServeDist") {
    dependsOn("webBuildWasm")
    group = "web"
    description = "Serve web/build/dist on http://0.0.0.0:8080"
    workingDir = rootProject.projectDir
    commandLine("python3", "-m", "http.server", "8080", "--directory", "web/build/dist")
}
