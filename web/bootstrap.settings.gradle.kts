import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Properties

val gdxSource = resolveWebToolchain("gdx", providers.gradleProperty("gdxTeaVMSource").orNull)
val pluginCheckout = File(gdxSource, "tools/gdx-teavm-plugin")
if (!File(pluginCheckout, "settings.gradle.kts").isFile) {
    throw GradleException("The pinned gdx-teavm source is missing tools/gdx-teavm-plugin/settings.gradle.kts: $gdxSource")
}
// The plugin build is added after pluginManagement's first block but before project plugin requests are resolved.
settings.pluginManagement.includeBuild(pluginCheckout.absolutePath)
settings.includeBuild(gdxSource.absolutePath) {
    dependencySubstitution {
        substitute(module("com.github.xpenatan.gdx-teavm:backend-web"))
            .using(project(":backends:backend-web"))
    }
}

val teavmSource = resolveWebToolchain("teavm", providers.gradleProperty("teavmPath").orNull)
settings.includeBuild(teavmSource.absolutePath) {
    dependencySubstitution {
        substitute(module("org.teavm:teavm-gradle-plugin")).using(project(":tools:gradle"))
        substitute(module("org.teavm:teavm-extension-spi")).using(project(":extension:spi"))
        substitute(module("org.teavm:teavm-extension-annotation-processor")).using(project(":extension:processor"))
        substitute(module("org.teavm:teavm-tooling")).using(project(":tools:core"))
        substitute(module("org.teavm:teavm-junit")).using(project(":tools:junit"))
        substitute(module("org.teavm:teavm-core")).using(project(":core"))
        substitute(module("org.teavm:teavm-classlib")).using(project(":classlib"))
        substitute(module("org.teavm:teavm-jso")).using(project(":jso:core"))
        substitute(module("org.teavm:teavm-jso-apis")).using(project(":jso:apis"))
        substitute(module("org.teavm:teavm-jso-impl")).using(project(":jso:impl"))
        substitute(module("org.teavm:teavm-interop")).using(project(":interop:core"))
        substitute(module("org.teavm:teavm-metaprogramming-api")).using(project(":metaprogramming:api"))
        substitute(module("org.teavm:teavm-metaprogramming-impl")).using(project(":metaprogramming:impl"))
    }
}

private data class ArchiveCandidate(val label: String, val url: String, val sha256: String, val release: Boolean)

private fun resolveWebToolchain(name: String, overridePath: String?): File {
    val lockFile = checkNotNull(buildscript.sourceFile).resolveSibling("dependencies.lock.properties")
    if (!lockFile.isFile) throw GradleException("Missing pinned web toolchain lock file: $lockFile")
    val pins = Properties().also { lockFile.inputStream().use(it::load) }
    val expectedVersion = if (name == "teavm" && overridePath != null) {
        providers.gradleProperty("teavmVersion").orNull ?: pins.required("$name.version")
    } else pins.required("$name.version")
    if (overridePath != null) return validateLocalWebToolchain(name, overridePath, expectedVersion)

    val commit = pins.required("$name.commit")
    if (!Regex("^[a-fA-F0-9]{40}$").matches(commit)) {
        throw GradleException("Pinned $name commit must be a full 40-digit Git SHA")
    }
    val archiveUrl = pins.required("$name.archive.url")
    val archiveSha256 = pins.requiredSha256("$name.archive.sha256")
    val releaseUrl = pins.getProperty("$name.release.url")?.trim().orEmpty()
    val releaseSha256 = pins.getProperty("$name.release.sha256")?.trim()?.takeIf { it.isNotEmpty() }
        ?.also { validateSha256("$name.release.sha256", it) }

    val cacheBase = File(settings.gradle.gradleUserHomeDir, "caches/unciv-web-toolchains/$name/$commit").canonicalFile
    if (!cacheBase.isDirectory && !cacheBase.mkdirs()) {
        throw GradleException("Unable to create web toolchain cache directory: $cacheBase")
    }
    val lockPath = File(cacheBase, "bootstrap.lock").toPath()
    FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
        channel.lock().use {
            val candidates = buildList {
                if (releaseUrl.isNotEmpty() && releaseSha256 != null) add(ArchiveCandidate("release", releaseUrl, releaseSha256, true))
                add(ArchiveCandidate("archive", archiveUrl, archiveSha256, false))
            }
            var transportFailure: IOException? = null
            for (candidate in candidates) {
                val entry = File(cacheBase, candidate.sha256.lowercase())
                if (!entry.isDirectory && !entry.mkdirs()) {
                    throw GradleException("Unable to create web toolchain cache directory: $entry")
                }
                if (isCachedSource(entry, commit, candidate.sha256, name, expectedVersion)) return File(entry, "$name-source")
                try {
                    println("Bootstrapping pinned $name source ($commit) from ${candidate.label} archive")
                    val archive = obtainArchive(candidate, entry)
                    if (candidate.release) verifyReleaseChecksums(candidate, entry)
                    extractPinnedArchive(archive, entry, commit, candidate.sha256, name)
                    verifyWebToolchain(name, File(entry, "$name-source"), expectedVersion)
                    return File(entry, "$name-source")
                } catch (e: IOException) {
                    transportFailure = e
                }
            }
            throw GradleException(
                "Could not bootstrap pinned $name source at $commit. Tried the release asset and pinned source archive. " +
                    "Check network access or set the explicit local override property. Last download error: ${transportFailure?.message ?: "none"}"
            )
        }
    }
}

private fun obtainArchive(candidate: ArchiveCandidate, entry: File): File {
    val archive = File(entry, "${candidate.label}.tar.gz")
    if (archive.isFile && sha256(archive).equals(candidate.sha256, ignoreCase = true)) return archive
    Files.deleteIfExists(archive.toPath())
    val temporary = File(entry, "${candidate.label}.tar.gz.part")
    Files.deleteIfExists(temporary.toPath())
    download(candidate.url, temporary)
    val actual = sha256(temporary)
    if (!actual.equals(candidate.sha256, ignoreCase = true)) {
        Files.deleteIfExists(temporary.toPath())
        throw GradleException("SHA-256 mismatch for pinned web source ${candidate.url}: expected ${candidate.sha256}, got $actual")
    }
    moveAtomically(temporary.toPath(), archive.toPath())
    return archive
}

private fun download(url: String, destination: File) {
    val uri = URI(url)
    if (!uri.scheme.equals("https", ignoreCase = true)) throw GradleException("Web toolchain archives must use HTTPS: $url")
    val connection = uri.toURL().openConnection()
    connection.connectTimeout = 20_000
    connection.readTimeout = 60_000
    connection.useCaches = false
    if (connection is HttpURLConnection) {
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Unciv-web-build/1")
    }
    destination.parentFile.mkdirs()
    try {
        connection.getInputStream().use { input ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 512L * 1024 * 1024) throw IOException("Pinned source archive exceeds the 512 MiB safety limit")
                    output.write(buffer, 0, count)
                }
            }
        }
    } finally {
        (connection as? HttpURLConnection)?.disconnect()
    }
}

private fun verifyReleaseChecksums(candidate: ArchiveCandidate, entry: File) {
    val sumsUrl = candidate.url.substringBeforeLast('/') + "/SHA256SUMS"
    val sumsFile = File(entry, "SHA256SUMS")
    download(sumsUrl, sumsFile)
    val actual = Regex("(?m)^([a-fA-F0-9]{64})\\s+\\*?source\\.tar\\.gz\\s*$")
        .find(sumsFile.readText())?.groupValues?.get(1)
        ?: throw GradleException("Release checksum file $sumsUrl does not contain a source.tar.gz entry")
    if (!actual.equals(candidate.sha256, ignoreCase = true)) {
        throw GradleException("Release SHA256SUMS disagrees with web/dependencies.lock.properties for ${candidate.url}")
    }
}

private fun extractPinnedArchive(archive: File, entry: File, commit: String, archiveSha256: String, name: String) {
    val staging = Files.createTempDirectory(entry.toPath(), "extract-").toFile()
    try {
        val listing = runTar(listOf("-tzf", archive.absolutePath), "listing pinned source archive")
        val paths = listing.lineSequence().filter(String::isNotBlank).toList()
        if (paths.isEmpty() || paths.any { path ->
                val normalized = path.replace('\\', '/')
                normalized.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(normalized) ||
                    normalized.split('/').any { it == ".." }
            }) {
            throw GradleException("Pinned source archive contains an unsafe or empty path list: $archive")
        }
        runTar(listOf("-xzf", archive.absolutePath, "--strip-components=1", "-C", staging.absolutePath), "extract pinned source archive")

        val source = File(entry, "$name-source")
        deleteTree(source.toPath())
        moveAtomically(staging.toPath(), source.toPath())
        File(entry, "complete.properties").writeText("commit=$commit\narchiveSha256=$archiveSha256\n")
    } finally {
        deleteTree(staging.toPath())
    }
}

private fun runTar(arguments: List<String>, operation: String): String {
    val tar = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "tar.exe" else "tar"
    val process = try {
        ProcessBuilder(listOf(tar) + arguments).redirectErrorStream(true).start()
    } catch (e: IOException) {
        throw GradleException("The system 'tar' command is required to $operation", e)
    }
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()
    if (exitCode != 0) throw GradleException("Failed to $operation (exit code $exitCode): $output")
    return output
}

private fun isCachedSource(entry: File, commit: String, archiveSha256: String, name: String, version: String): Boolean {
    val marker = File(entry, "complete.properties")
    val source = File(entry, "$name-source")
    if (!marker.isFile || !source.isDirectory) return false
    val contents = Properties().also { marker.inputStream().use(it::load) }
    if (contents.getProperty("commit") != commit || !contents.getProperty("archiveSha256").equals(archiveSha256, ignoreCase = true)) return false
    verifyWebToolchain(name, source, version)
    return true
}

private fun validateLocalWebToolchain(name: String, configuredPath: String, version: String): File {
    val source = File(configuredPath)
    if (!source.isAbsolute || !source.isDirectory) {
        val property = if (name == "gdx") "gdxTeaVMSource" else "teavmPath"
        throw GradleException("-$property must point to an existing absolute $name source checkout: $configuredPath")
    }
    verifyWebToolchain(name, source.canonicalFile, version)
    return source.canonicalFile
}

private fun verifyWebToolchain(name: String, source: File, expectedVersion: String) {
    when (name) {
        "gdx" -> {
            val pluginSettings = File(source, "tools/gdx-teavm-plugin/settings.gradle.kts")
            val catalog = File(source, "gradle/libs.versions.toml")
            if (!pluginSettings.isFile || !catalog.isFile) {
                throw GradleException("gdx-teavm source must contain tools/gdx-teavm-plugin and gradle/libs.versions.toml: $source")
            }
            val text = catalog.readText()
            val parts = expectedVersion.split('/')
            if (parts.size != 2 || !text.contains("gdxTeavmRelease = \"${parts[0]}\"") || !text.contains("teavm = \"${parts[1]}\"")) {
                throw GradleException("gdx-teavm must pin plugin/backend and TeaVM $expectedVersion: $source")
            }
        }
        "teavm" -> {
            val settings = File(source, "settings.gradle.kts").takeIf { it.isFile } ?: File(source, "settings.gradle")
            val properties = File(source, "gradle.properties")
            val versionLine = Regex("(?m)^teavm\\.project\\.version=${Regex.escape(expectedVersion)}-SNAPSHOT\\s*$")
            if (!settings.isFile || !properties.isFile || !versionLine.containsMatchIn(properties.readText())) {
                throw GradleException("TeaVM source must be version $expectedVersion and contain Gradle settings: $source")
            }
        }
        else -> throw GradleException("Unknown web toolchain '$name'")
    }
}

private fun Properties.required(key: String): String = getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
    ?: throw GradleException("Missing '$key' in web/dependencies.lock.properties")

private fun Properties.requiredSha256(key: String): String = required(key).also { validateSha256(key, it) }

private fun validateSha256(key: String, value: String) {
    if (!Regex("^[a-fA-F0-9]{64}$").matches(value)) throw GradleException("'$key' must be a 64-digit SHA-256 value")
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun moveAtomically(source: Path, target: Path) {
    try {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target)
    }
}

private fun deleteTree(path: Path) {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        Files.newDirectoryStream(path).use { children -> children.forEach { deleteTree(it) } }
    }
    Files.deleteIfExists(path)
}
