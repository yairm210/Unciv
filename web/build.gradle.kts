import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.GradleException
import org.gradle.api.tasks.Sync
import java.io.BufferedReader
import java.io.File

plugins {
    id("kotlin")
    id("com.github.xpenatan.gdx-teavm")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core"))
    implementation(rootProject.libs.kotlin.reflect)
    implementation(rootProject.libs.gdx)
    implementation(rootProject.libs.coroutines.core)
    testImplementation(rootProject.libs.junit)
    compileOnly("org.teavm:teavm-extension-spi:0.17.0")
}

configurations.configureEach {
    exclude(group = "io.ktor", module = "ktor-client-cio")
    exclude(group = "io.ktor", module = "ktor-client-cio-jvm")
}

val validationClasspath = layout.buildDirectory.file("validation/runtime-classpath.txt")
tasks.register("writeValidationClasspath") {
    dependsOn(tasks.named("classes"))
    inputs.files(sourceSets.main.get().runtimeClasspath)
    outputs.file(validationClasspath)
    doLast {
        val output = validationClasspath.get().asFile
        output.parentFile.mkdirs()
        output.writeText(sourceSets.main.get().runtimeClasspath.files.joinToString("\n") { it.absolutePath })
    }
}

val webSourceMaps = providers.gradleProperty("webSourceMaps")
    .map(String::toBooleanStrict)
    .orElse(false)

val pagesOutput = layout.buildDirectory.dir("pages/game")
tasks.register<Sync>("preparePages") {
    dependsOn(tasks.named("gdx_teavm_web_js_build"))
    from(layout.buildDirectory.dir("dist/js/webapp")) {
        exclude("*.map", "**/*.map", "src/**", "**/src/**", "WEB-INF/**", "**/WEB-INF/**")
    }
    into(pagesOutput)

    doLast {
        val output = pagesOutput.get().asFile
        val textExtensions = setOf("html", "js", "mjs", "css", "json", "xml", "txt", "svg", "webmanifest", "glsl", "atlas", "fnt", "properties", "yaml", "yml", "csv", "toml", "md")
        val hostPath = Regex("""(?i)(?:[A-Z]:[/\\]+(?:Users|gits)[/\\]+[^/\\\s"'<>]+|(?<![\w:./\\-])/(?:Users|home)/(?!web_user(?:[/\\]|$))[^/\\\s"'<>]+|file:/+(?:Users|home)/(?!web_user(?:[/\\]|$))[^/\\\s"'<>]+)""")
        val offenders = output.walkTopDown().filter { file ->
            file.isFile && file.extension.lowercase() in textExtensions && containsHostPath(file, hostPath)
        }.toList()
        if (offenders.isNotEmpty()) {
            val relativeFiles = offenders.joinToString(", ") { it.relativeTo(output).invariantSeparatorsPath }
            throw GradleException("Public web output contains a local host path in: $relativeFiles")
        }
    }
}

fun containsHostPath(file: File, pattern: Regex): Boolean {
    val carryLimit = 256
    BufferedReader(file.inputStream().bufferedReader(Charsets.UTF_8)).use { reader ->
        val buffer = CharArray(64 * 1024)
        var carry = ""
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            val chunk = carry + String(buffer, 0, count)
            if (pattern.findAll(chunk).any { !isAllowedVirtualHostPath(it.value) }) return true
            carry = chunk.takeLast(carryLimit)
        }
    }
    return false
}

fun isAllowedVirtualHostPath(value: String): Boolean {
    val normalized = value.replace('\\', '/')
    val path = if (normalized.startsWith("file:", ignoreCase = true)) {
        "/" + normalized.substringAfter(':').trimStart('/')
    } else {
        normalized
    }
    return path.equals("/home/web_user", ignoreCase = true) || path.startsWith("/home/web_user/", ignoreCase = true)
}

gdxTeaVM {
    assets(rootProject.file("android/assets"))

    webDefaults {
        mainClass.set("com.unciv.app.web.WebLauncher")
        htmlTitle.set("Unciv")
        processMemory.set(providers.gradleProperty("webCompilerMemory").map(String::toInt).orElse(4096))
        outOfProcess.set(providers.gradleProperty("webCompilerInProcess")
            .map(String::toBooleanStrict).map { !it }.orElse(true))
    }

    js {
        strict.set(true)
        sourceMap.set(webSourceMaps)
        obfuscated.set(false)
    }
}
