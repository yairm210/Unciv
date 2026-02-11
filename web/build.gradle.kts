import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

val generateWebJsTestSuite by tasks.registering {
    group = "web"
    description = "Generate deterministic browser JS test suite from tests/src classes."

    val testsSourceRoot = rootProject.file("tests/src")
    inputs.dir(testsSourceRoot)
    outputs.dir(generatedWebJsTestsDir)

    doLast {
        val outputRoot = generatedWebJsTestsDir.get().asFile
        val outputFile = outputRoot.resolve("com/unciv/app/web/WebJsTestSuite.kt")
        outputFile.parentFile.mkdirs()

        val packageRegex = Regex("^\\s*package\\s+([A-Za-z0-9_.]+)", setOf(RegexOption.MULTILINE))
        val classRegex = Regex("^\\s*(?:class|object)\\s+([A-Za-z0-9_]+)", setOf(RegexOption.MULTILINE))
        val methodRegex = Regex("^\\s*(?:public\\s+|private\\s+|internal\\s+|protected\\s+)?(?:suspend\\s+)?fun\\s+([A-Za-z0-9_]+)\\s*\\(")

        val candidateFiles = testsSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { file ->
                val normalized = file.invariantSeparatorsPath
                normalized.contains("/com/unciv/dev/")
                    || normalized.endsWith("/com/unciv/testing/GdxTestRunner.kt")
            }
            .sortedBy { it.invariantSeparatorsPath }
            .toList()

        val body = buildString {
            appendLine("package com.unciv.app.web")
            appendLine()
            appendLine("data class WebJsGeneratedTestMethod(")
            appendLine("    val name: String,")
            appendLine("    val ignoredReason: String?,")
            appendLine("    val execute: (Any) -> Unit,")
            appendLine(")")
            appendLine()
            appendLine("data class WebJsGeneratedTestClass(")
            appendLine("    val className: String,")
            appendLine("    val createInstance: () -> Any,")
            appendLine("    val beforeMethods: List<(Any) -> Unit>,")
            appendLine("    val afterMethods: List<(Any) -> Unit>,")
            appendLine("    val testMethods: List<WebJsGeneratedTestMethod>,")
            appendLine(")")
            appendLine()
            appendLine("object WebJsTestSuite {")
            appendLine("    val classes: List<WebJsGeneratedTestClass> = listOf(")

            var classCount = 0
            for (file in candidateFiles) {
                val text = file.readText()
                if (!text.contains("@Test")) continue
                val packageName = packageRegex.find(text)?.groupValues?.get(1) ?: continue
                val className = classRegex.find(text)?.groupValues?.get(1) ?: continue
                val pendingAnnotations = mutableListOf<String>()
                val beforeMethods = mutableListOf<String>()
                val afterMethods = mutableListOf<String>()
                val testMethods = mutableListOf<Pair<String, String?>>()

                for (rawLine in text.lineSequence()) {
                    val line = rawLine.trim()
                    if (line.contains("@Before")) pendingAnnotations += "@Before"
                    if (line.contains("@After")) pendingAnnotations += "@After"
                    if (line.contains("@Test")) pendingAnnotations += "@Test"
                    if (line.contains("@Ignore")) pendingAnnotations += "@Ignore"

                    val methodName = methodRegex.find(line)?.groupValues?.get(1)
                    if (methodName != null) {
                        if (pendingAnnotations.any { it == "@Before" }) beforeMethods += methodName
                        if (pendingAnnotations.any { it == "@After" }) afterMethods += methodName
                        if (pendingAnnotations.any { it == "@Test" }) {
                            val ignored = pendingAnnotations.any { it == "@Ignore" }
                            testMethods += methodName to if (ignored) "ignored" else null
                        }
                        pendingAnnotations.clear()
                        continue
                    }

                    if (line.isBlank() || line.startsWith("//") || line.startsWith("*") || line.startsWith("/*") || line.startsWith("*/")) {
                        continue
                    }
                    if (!line.startsWith("@")) {
                        pendingAnnotations.clear()
                    }
                }

                if (testMethods.isEmpty()) continue
                classCount++
                val fqn = "$packageName.$className"
                appendLine("        WebJsGeneratedTestClass(")
                appendLine("            className = \"$fqn\",")
                appendLine("            createInstance = { $fqn() },")

                if (beforeMethods.isEmpty()) {
                    appendLine("            beforeMethods = emptyList(),")
                } else {
                    appendLine("            beforeMethods = listOf(")
                    beforeMethods.forEach { method ->
                        appendLine("                { instance -> (instance as $fqn).$method() },")
                    }
                    appendLine("            ),")
                }

                if (afterMethods.isEmpty()) {
                    appendLine("            afterMethods = emptyList(),")
                } else {
                    appendLine("            afterMethods = listOf(")
                    afterMethods.forEach { method ->
                        appendLine("                { instance -> (instance as $fqn).$method() },")
                    }
                    appendLine("            ),")
                }

                appendLine("            testMethods = listOf(")
                testMethods.forEach { (method, ignoredReason) ->
                    val ignoredValue = ignoredReason?.let { "\"$it\"" } ?: "null"
                    appendLine("                WebJsGeneratedTestMethod(\"$method\", $ignoredValue, { instance -> (instance as $fqn).$method() }),")
                }
                appendLine("            ),")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("}")
            logger.lifecycle("Generated WebJsTestSuite with $classCount classes at ${outputFile.invariantSeparatorsPath}")
        }
        outputFile.writeText(body)
    }
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

fun hardenIndexBootstrap(indexFile: File) {
    if (!indexFile.isFile) return
    var content = indexFile.readText()
    if (!content.contains("rel=\"icon\"")) {
        content = content.replace("<head>", "<head>\n        <link rel=\"icon\" href=\"data:,\">")
    }
    val hardened = """
        <script>
            (function () {
                function boot() {
                    if (window.__uncivBootStarted) return;
                    if (typeof window.main !== 'function') {
                        setTimeout(boot, 25);
                        return;
                    }
                    window.__uncivBootStarted = true;
                    window.main();
                }
                if (document.readyState === 'complete') {
                    setTimeout(boot, 0);
                } else {
                    window.addEventListener('load', boot, { once: true });
                }
            })();
        </script>
    """.trimIndent()
    val legacyRegex = Regex(
        "<script>\\s*async function start\\(\\) \\{\\s*main\\(\\)\\s*}\\s*window.addEventListener\\(\\\"load\\\", start\\);\\s*</script>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    content = if (legacyRegex.containsMatchIn(content)) {
        content.replace(legacyRegex, hardened)
    } else if (!content.contains("__uncivBootStarted")) {
        content.replace("</body>", "$hardened\n    </body>")
    } else {
        content
    }
    indexFile.writeText(content)
}

fun promoteWebappToRoot(outputDir: File) {
    val webappDir = File(outputDir, "webapp")
    if (!webappDir.isDirectory) return
    if (File(outputDir, "index.html").isFile) return
    webappDir.listFiles()?.forEach { child ->
        val target = File(outputDir, child.name)
        if (target.exists()) {
            target.deleteRecursively()
        }
        Files.move(child.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    webappDir.deleteRecursively()
}

tasks.register("webPostProcessDist") {
    doLast {
        val outputDir = rootProject.file("web/build/dist")
        promoteWebappToRoot(outputDir)
        hardenIndexBootstrap(File(outputDir, "index.html"))
    }
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
