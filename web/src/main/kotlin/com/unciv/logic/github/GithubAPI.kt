package com.unciv.logic.github

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.UncivShowableException
import com.unciv.logic.github.Github.repoNameToFolderName
import com.unciv.logic.web.WebHttp
import com.unciv.logic.web.WebHttpResponse
import com.unciv.utils.delayMillis
import org.teavm.jso.typedarrays.ArrayBuffer
import org.teavm.jso.typedarrays.Int8Array
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

enum class DownloadAndExtractState {
    Downloading {
        override fun message(progress: Int?) = if (progress == null) "Downloading..." else "{Downloading...} ${progress.coerceIn(0, 100)}%"
    },
    Finishing {
        override fun message(progress: Int?) = "Finishing..."
    };

    abstract fun message(progress: Int?): String
}

@Suppress("PropertyName")
object GithubAPI {
    const val baseUrl = "https://api.github.com"
    const val bearerToken = ""

    private const val githubApiVersion = "2022-11-28"
    private val jsonReader = JsonReader()

    private fun defaultHeaders(): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        headers["X-GitHub-Api-Version"] = githubApiVersion
        headers["Accept"] = "application/vnd.github+json"
        headers["User-Agent"] = UncivGame.getUserAgent("Github")
        if (bearerToken.isNotBlank()) headers["Authorization"] = "Bearer $bearerToken"
        return headers
    }

    suspend fun request(
        maxRateLimitedRetries: Int = 3,
        block: RequestBuilder.() -> Unit,
    ): WebHttpResponse {
        var retries = maxRateLimitedRetries
        while (true) {
            val builder = RequestBuilder()
            block(builder)
            val resp = when (builder.method.uppercase(Locale.ROOT)) {
                "GET" -> WebHttp.requestText("GET", builder.buildUrl(), headers = builder.headers)
                "POST" -> WebHttp.requestText("POST", builder.buildUrl(), headers = builder.headers, body = builder.body)
                "PUT" -> WebHttp.requestText("PUT", builder.buildUrl(), headers = builder.headers, body = builder.body)
                "DELETE" -> WebHttp.requestText("DELETE", builder.buildUrl(), headers = builder.headers)
                else -> WebHttp.requestText(builder.method, builder.buildUrl(), headers = builder.headers, body = builder.body)
            }
            if (!consumeRateLimit(resp) || retries <= 0) return resp
            retries -= 1
        }
    }

    private suspend fun consumeRateLimit(resp: WebHttpResponse): Boolean {
        if (resp.status != 403 && resp.status != 429) return false
        val remaining = resp.headers["x-ratelimit-remaining"]?.toIntOrNull() ?: 0
        if (remaining > 0) return false
        val resetEpochSeconds = resp.headers["x-ratelimit-reset"]?.toLongOrNull() ?: return false
        val delayMs = ((resetEpochSeconds * 1000L) - System.currentTimeMillis()).coerceAtLeast(0L)
        if (delayMs > 0) {
            delayMillis(delayMs)
        }
        return true
    }

    private suspend fun paginatedRequest(page: Int, amountPerPage: Int, block: RequestBuilder.() -> Unit): WebHttpResponse {
        return request {
            query["page"] = page.toString()
            query["per_page"] = amountPerPage.toString()
            block()
        }
    }

    internal fun getUrlForBranchZip(gitRepoUrl: String, branch: String) = "$gitRepoUrl/archive/refs/heads/$branch.zip"

    private fun Repo.getUrlForReleaseZip() = "$html_url/archive/refs/tags/$release_tag.zip"

    internal suspend fun Repo.fetchReleaseZip() = request {
        path = "/repos/$full_name/git/trees/$default_branch"
        query["recursive"] = "true"
    }

    suspend fun fetchGithubReposWithTopic(search: String, page: Int, amountPerPage: Int) =
        paginatedRequest(page, amountPerPage) {
            path = "/search/repositories"
            query["sort"] = "stars"
            query["q"] = "$search topic:unciv-mod fork:true"
        }

    suspend fun fetchGithubTopics() = request {
        path = "/search/topics"
        query["sort"] = "name"
        query["order"] = "asc"
        query["q"] = "unciv-mod repositories:>1"
    }

    suspend fun fetchSingleRepo(owner: String, repoName: String) = request {
        path = "/repos/$owner/$repoName"
    }

    suspend fun fetchSingleRepoOwner(owner: String) = request {
        path = "/users/$owner"
    }

    suspend fun fetchPreviewImageOrNull(modUrl: String, branch: String, ext: String): WebHttpResponse? {
        val rawBase = modUrl
            .removeSuffix("/")
            .replace("https://github.com/", "https://raw.githubusercontent.com/")
            .replace("http://github.com/", "https://raw.githubusercontent.com/")
        val url = "$rawBase/$branch/preview.$ext"
        val resp = WebHttp.requestBytes("GET", url)
        return if (resp.ok) resp else null
    }

    private fun parseJsonObject(text: String): JsonValue? {
        val parsed = try {
            jsonReader.parse(text)
        } catch (_: Throwable) {
            return null
        }
        if (!parsed.isObject) return null
        return parsed
    }

    private fun parseRepoFromApiJson(text: String): Repo? {
        val root = parseJsonObject(text) ?: return null
        val parsedRepo = Repo()
        parsedRepo.name = root.getString("name", "")
        parsedRepo.full_name = root.getString("full_name", "")
        parsedRepo.description = root.getString("description", null)
        parsedRepo.default_branch = root.getString("default_branch", "")
        parsedRepo.html_url = root.getString("html_url", "")
        parsedRepo.pushed_at = root.getString("pushed_at", "")
        parsedRepo.size = root.getInt("size", 0)

        val ownerNode = root.get("owner")
        if (ownerNode != null && ownerNode.isObject) {
            parsedRepo.owner.login = ownerNode.getString("login", "")
            parsedRepo.owner.avatar_url = ownerNode.getString("avatar_url", null)
        }

        val topicsNode = root.get("topics")
        if (topicsNode != null && topicsNode.isArray) {
            var child = topicsNode.child
            while (child != null) {
                parsedRepo.topics += child.asString()
                child = child.next
            }
        }
        return parsedRepo
    }

    private fun parseRepoOwnerFromApiJson(text: String): RepoOwner? {
        val root = parseJsonObject(text) ?: return null
        val parsedOwner = RepoOwner()
        parsedOwner.login = root.getString("login", "")
        parsedOwner.avatar_url = root.getString("avatar_url", null)
        return parsedOwner
    }

    private fun parseTreeFromApiJson(text: String): Tree? {
        val root = parseJsonObject(text) ?: return null
        val parsedTree = Tree()
        parsedTree.truncated = root.getBoolean("truncated", false)
        val treeNode = root.get("tree")
        if (treeNode != null && treeNode.isArray) {
            var child = treeNode.child
            while (child != null) {
                val treeFile = Tree.TreeFile()
                treeFile.path = child.getString("path", "")
                treeFile.type = child.getString("type", "")
                treeFile.size = child.getLong("size", 0L)
                parsedTree.tree += treeFile
                child = child.next
            }
        }
        return parsedTree
    }

    class RepoSearch {
        var total_count = 0
        var incomplete_results = false
        var items = ArrayList<Repo>()
    }

    class Repo {
        var hasUpdatedSize = false
        var direct_zip_url = ""
        var release_tag = ""

        var name = ""
        var full_name = ""
        var description: String? = null
        var owner = RepoOwner()
        var stargazers_count = 0
        var default_branch = ""
        var html_url = ""
        var pushed_at = ""
        var size = 0
        var topics = mutableListOf<String>()

        override fun toString() = name.ifEmpty { direct_zip_url }

        companion object {
            suspend fun parseUrl(url: String): Repo? = Repo().parseUrl(url)
            suspend fun query(owner: String, repoName: String): Repo? {
                val resp = fetchSingleRepo(owner, repoName)
                if (!resp.ok || resp.text.isNullOrBlank()) return null
                return parseRepoFromApiJson(resp.text) ?: json().fromJson(Repo::class.java, resp.text)
            }
        }
    }

    class RepoOwner {
        var login = ""
        var avatar_url: String? = null

        companion object {
            suspend fun query(owner: String): RepoOwner? {
                val resp = fetchSingleRepoOwner(owner)
                if (!resp.ok || resp.text.isNullOrBlank()) return null
                return parseRepoOwnerFromApiJson(resp.text) ?: json().fromJson(RepoOwner::class.java, resp.text)
            }
        }
    }

    class TopicSearchResponse {
        var items = ArrayList<Topic>()
        class Topic {
            var name = ""
            var display_name: String? = null
            var created_at = ""
            var updated_at = ""
        }
    }

    internal class Tree {
        class TreeFile {
            var path = ""
            var type = ""
            var size: Long = 0L
        }

        var tree = ArrayList<TreeFile>()
        var truncated = false
    }

    private suspend fun Repo.parseUrl(url: String): Repo? {
        fun processMatch(matchResult: MatchResult): Repo {
            html_url = matchResult.groups[1]!!.value
            owner.login = matchResult.groups[2]!!.value
            name = matchResult.groups[3]!!.value
            default_branch = matchResult.groups[4]!!.value
            full_name = "${owner.login}/$name"
            return this
        }

        html_url = url
        default_branch = "master"
        val matchZip = Regex("""^(.*/(.*)/(.*))/archive/(?:.*/)?heads/([^.]+).zip$""").matchEntire(url)
        if (matchZip != null && matchZip.groups.size > 4) return processMatch(matchZip)

        val matchBranch = Regex("""^(.*/(.*)/(.*))/tree/(.+?)/?$""").matchEntire(url)
        if (matchBranch != null && matchBranch.groups.size > 4) return processMatch(matchBranch)

        val matchTagArchive = Regex("""^(.*/(.*)/(.*))/archive/(?:.*/)?tags/(.+).zip$""").matchEntire(url)
        if (matchTagArchive != null && matchTagArchive.groups.size > 4) {
            processMatch(matchTagArchive)
            release_tag = default_branch
            direct_zip_url = url
            return this
        }
        val matchTagPage = Regex("""^(.*/(.*)/(.*))/releases/(?:.*/)?tag/(.+?)/?$""").matchEntire(url)
        if (matchTagPage != null && matchTagPage.groups.size > 4) {
            processMatch(matchTagPage)
            release_tag = default_branch
            direct_zip_url = getUrlForReleaseZip()
            return this
        }

        val matchCommit = Regex("""^(.*/(.*)/(.*))/archive/([0-9a-z]{40})\.zip$""").matchEntire(url)
        if (matchCommit != null && matchCommit.groups.size > 4) {
            processMatch(matchCommit)
            direct_zip_url = url
            return this
        }

        val matchAnyZip = Regex("""^.*//(?:.*/)*([^/]+\.zip)$""").matchEntire(url)
        if (matchAnyZip != null && matchAnyZip.groups.size > 1) {
            html_url = ""
            direct_zip_url = url
            owner.login = "-unknown-"
            default_branch = "master"
            name = matchAnyZip.groups[1]!!.value
            full_name = name
            return this
        }

        val matchRepo = Regex("""^.*//.*/(.+)/(.+?)/?$""").matchEntire(url)
        if (matchRepo != null && matchRepo.groups.size > 2) {
            val repo = Repo.query(matchRepo.groups[1]!!.value, matchRepo.groups[2]!!.value)
            if (repo != null) return repo
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) return null

        html_url = ""
        direct_zip_url = url
        owner.login = "-unknown-"
        default_branch = "master"
        if (matchAnyZip != null && matchAnyZip.groups.size > 1) name = matchAnyZip.groups[1]!!.value
        full_name = name
        return this
    }

    suspend fun Repo.downloadAndExtract(
        updateProgressPercent: ((DownloadAndExtractState, Int?) -> Unit)? = null,
    ): FileHandle? {
        val modsFolder = UncivGame.Current.files.getModsFolder()
        var modNameFromFileName = name
        val defaultBranch = default_branch
        val githubTreeSource = resolveGithubTreeSource()

        val downloadKey = when {
            githubTreeSource != null -> githubTreeSource.fullName + ":" + githubTreeSource.ref
            direct_zip_url.isEmpty() -> html_url
            else -> direct_zip_url
        }
        val unpackDestination = modsFolder.child("temp-" + downloadKey.hashCode().toString(16))
        deleteRecursively(unpackDestination)

        if (githubTreeSource != null) {
            unpackDestination.mkdirs()
            downloadGithubTree(githubTreeSource, unpackDestination, updateProgressPercent)
        } else {
            val zipUrl = if (direct_zip_url.isEmpty()) {
                getUrlForBranchZip(html_url, defaultBranch)
            } else {
                direct_zip_url
            }
            updateProgressPercent?.invoke(DownloadAndExtractState.Downloading, 0)
            val resp = WebHttp.requestBytes("GET", zipUrl, timeoutMs = 120000)
            if (resp.ok && resp.bytes != null) {
                modNameFromFileName = parseNameFromDisposition(resp.headers["content-disposition"], modNameFromFileName)
                unpackDestination.mkdirs()
                unzipToFolder(resp.bytes, unpackDestination, updateProgressPercent)
            } else {
                val zipFallbackSource = resolveGithubTreeSource(zipUrl)
                if (zipFallbackSource != null) {
                    unpackDestination.mkdirs()
                    downloadGithubTree(zipFallbackSource, unpackDestination, updateProgressPercent)
                } else {
                    throw UncivShowableException("Failed to download mod archive: ${resp.status} ${resp.statusText}")
                }
            }
        }

        if (!unpackDestination.exists() || unpackDestination.list().isEmpty()) {
            if (unpackDestination.exists()) deleteRecursively(unpackDestination)
            return null
        }

        val (innerFolder, modName) = resolveZipStructure(unpackDestination, modNameFromFileName)
        val finalDestinationName = modName.replace("-$defaultBranch", "").repoNameToFolderName()
        val finalDestination = modsFolder.child(finalDestinationName)

        var tempBackup: FileHandle? = null
        if (finalDestination.exists()) {
            tempBackup = finalDestination.sibling("$finalDestinationName.updating")
            deleteRecursively(tempBackup)
            copyRecursively(finalDestination, tempBackup)
            deleteRecursively(finalDestination)
        }

        finalDestination.mkdirs()
        for (innerFileOrFolder in innerFolder.list().sortedBy { file -> file.extension() == "atlas" }) {
            val dest = finalDestination.child(innerFileOrFolder.name())
            copyRecursively(innerFileOrFolder, dest)
        }

        deleteRecursively(unpackDestination)
        if (tempBackup != null) {
            deleteRecursively(tempBackup)
        }

        Github.rewriteModOptions(this, finalDestination)
        updateProgressPercent?.invoke(DownloadAndExtractState.Finishing, 100)
        return finalDestination
    }

    private fun Repo.resolveGithubTreeSource(candidateUrl: String? = null): GithubTreeSource? {
        val ref = default_branch.ifBlank { release_tag }

        val ownerAndRepoFromHtml = parseGithubOwnerRepo(html_url)
        if (ref.isNotBlank() && owner.login.isNotBlank() && owner.login != "-unknown-" && name.isNotBlank()) {
            val urlToCheck = when {
                candidateUrl != null -> candidateUrl
                direct_zip_url.isNotBlank() -> direct_zip_url
                html_url.isNotBlank() -> html_url
                else -> null
            }
            if (urlToCheck == null || isGithubUrl(urlToCheck)) {
                val repoFullName = full_name.ifBlank { "${owner.login}/${name}" }
                return GithubTreeSource(owner.login, name, ref, repoFullName)
            }
        }
        if (ref.isNotBlank() && ownerAndRepoFromHtml != null) {
            val (parsedOwner, parsedRepo) = ownerAndRepoFromHtml
            return GithubTreeSource(parsedOwner, parsedRepo, ref, "$parsedOwner/$parsedRepo")
        }

        val sourcesToParse = listOfNotNull(candidateUrl, direct_zip_url, html_url)
        for (url in sourcesToParse) {
            val parsed = parseGithubTreeSourceFromUrl(url)
            if (parsed != null) return parsed
        }
        return null
    }

    private data class GithubTreeSource(
        val owner: String,
        val repo: String,
        val ref: String,
        val fullName: String,
    )

    private fun isGithubUrl(url: String): Boolean {
        val normalizedUrl = url.lowercase(Locale.ROOT)
        return normalizedUrl.startsWith("https://github.com/")
            || normalizedUrl.startsWith("http://github.com/")
            || normalizedUrl.startsWith("https://codeload.github.com/")
            || normalizedUrl.startsWith("http://codeload.github.com/")
    }

    private fun parseGithubOwnerRepo(url: String): Pair<String, String>? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val match = Regex("""^https?://(?:www\.)?github\.com/([^/]+)/([^/]+?)(?:\.git)?/?(?:\?.*)?$""", RegexOption.IGNORE_CASE)
            .matchEntire(url)
            ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    private fun parseGithubTreeSourceFromUrl(url: String): GithubTreeSource? {
        val githubTree = Regex("""^https?://(?:www\.)?github\.com/([^/]+)/([^/]+)/tree/(.+?)/?$""", RegexOption.IGNORE_CASE)
            .matchEntire(url)
        if (githubTree != null) {
            val owner = githubTree.groupValues[1]
            val repo = githubTree.groupValues[2]
            val ref = githubTree.groupValues[3]
            return GithubTreeSource(owner, repo, ref, "$owner/$repo")
        }

        val githubArchiveHeads = Regex("""^https?://(?:www\.)?github\.com/([^/]+)/([^/]+)/archive/(?:refs/)?heads/([^.]+)\.zip$""", RegexOption.IGNORE_CASE)
            .matchEntire(url)
        if (githubArchiveHeads != null) {
            val owner = githubArchiveHeads.groupValues[1]
            val repo = githubArchiveHeads.groupValues[2]
            val ref = githubArchiveHeads.groupValues[3]
            return GithubTreeSource(owner, repo, ref, "$owner/$repo")
        }

        val githubArchiveTags = Regex("""^https?://(?:www\.)?github\.com/([^/]+)/([^/]+)/archive/(?:refs/)?tags/(.+)\.zip$""", RegexOption.IGNORE_CASE)
            .matchEntire(url)
        if (githubArchiveTags != null) {
            val owner = githubArchiveTags.groupValues[1]
            val repo = githubArchiveTags.groupValues[2]
            val ref = githubArchiveTags.groupValues[3]
            return GithubTreeSource(owner, repo, ref, "$owner/$repo")
        }

        val githubArchiveCommit = Regex("""^https?://(?:www\.)?github\.com/([^/]+)/([^/]+)/archive/([0-9a-z]{40})\.zip$""", RegexOption.IGNORE_CASE)
            .matchEntire(url)
        if (githubArchiveCommit != null) {
            val owner = githubArchiveCommit.groupValues[1]
            val repo = githubArchiveCommit.groupValues[2]
            val ref = githubArchiveCommit.groupValues[3]
            return GithubTreeSource(owner, repo, ref, "$owner/$repo")
        }

        val codeloadArchive = Regex("""^https?://codeload\.github\.com/([^/]+)/([^/]+)/(?:zip|legacy\.zip)/refs/(?:heads|tags)/([^/?#]+).*$""", RegexOption.IGNORE_CASE)
            .matchEntire(url)
        if (codeloadArchive != null) {
            val owner = codeloadArchive.groupValues[1]
            val repo = codeloadArchive.groupValues[2]
            val ref = codeloadArchive.groupValues[3]
            return GithubTreeSource(owner, repo, ref, "$owner/$repo")
        }
        return null
    }

    private suspend fun Repo.downloadGithubTree(
        source: GithubTreeSource,
        destination: FileHandle,
        updateProgressPercent: ((DownloadAndExtractState, Int?) -> Unit)?,
    ) {
        val encodedRef = encodePathSegment(source.ref)
        val treeResponse = request {
            path = "/repos/${source.fullName}/git/trees/$encodedRef"
            query["recursive"] = "true"
        }
        if (!treeResponse.ok || treeResponse.text.isNullOrBlank()) {
            throw UncivShowableException("Failed to fetch mod repository tree: ${treeResponse.status} ${treeResponse.statusText}")
        }
        val tree = parseTreeFromApiJson(treeResponse.text) ?: json().fromJson(Tree::class.java, treeResponse.text)
        if (tree.truncated) {
            throw UncivShowableException("Mod repository tree is too large for browser download.")
        }

        val files = tree.tree
            .asSequence()
            .filter { it.type.equals("blob", ignoreCase = true) && it.path.isNotBlank() }
            .map {
                val safePath = sanitizeEntryName(it.path)
                TreeDownloadFile(safePath, it.size.coerceAtLeast(0L))
            }
            .filter { it.path.isNotBlank() }
            .toList()
        if (files.isEmpty()) {
            throw UncivShowableException("Invalid Mod archive structure")
        }

        updateProgressPercent?.invoke(DownloadAndExtractState.Downloading, 0)
        val totalBytes = files.sumOf { it.size.coerceAtLeast(0L) }
        var downloadedBytes = 0L
        for ((index, file) in files.withIndex()) {
            val rawUrl = buildRawGithubFileUrl(source.owner, source.repo, source.ref, file.path)
            val fileResponse = WebHttp.requestBytes("GET", rawUrl, timeoutMs = 120000)
            if (!fileResponse.ok || fileResponse.bytes == null) {
                throw UncivShowableException("Failed to download mod file [${file.path}]: ${fileResponse.status} ${fileResponse.statusText}")
            }
            val destinationFile = destination.child(file.path)
            destinationFile.parent().mkdirs()
            destinationFile.writeBytes(fileResponse.bytes, false)

            downloadedBytes += if (file.size > 0L) file.size else fileResponse.bytes.size.toLong()
            val percent = if (totalBytes > 0L) {
                ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
            } else {
                (((index + 1) * 100) / files.size).coerceIn(0, 100)
            }
            updateProgressPercent?.invoke(DownloadAndExtractState.Downloading, percent)
        }
    }

    private data class TreeDownloadFile(
        val path: String,
        val size: Long,
    )

    private fun buildRawGithubFileUrl(owner: String, repo: String, ref: String, filePath: String): String {
        val encodedRef = encodePathSegment(ref)
        val encodedPath = filePath
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { encodePathSegment(it) }
        return "https://raw.githubusercontent.com/$owner/$repo/$encodedRef/$encodedPath"
    }

    private fun encodePathSegment(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")

    private suspend fun unzipToFolder(
        bytes: ByteArray,
        unzipDestination: FileHandle,
        updateProgressPercent: ((DownloadAndExtractState, Int?) -> Unit)?,
    ) {
        val buffer = bytesToBuffer(bytes)
        val totalEntries = intArrayOf(0)
        val processedEntries = intArrayOf(0)
        suspendCoroutine<Unit> { continuation ->
            WebZipInterop.unzip(
                buffer,
                { rawName, data ->
                    val entryName = sanitizeEntryName(rawName)
                    if (entryName.isBlank()) return@unzip
                    val dest = unzipDestination.child(entryName)
                    dest.parent().mkdirs()
                    dest.writeBytes(bufferToBytes(data), false)
                },
                { processed, total ->
                    totalEntries[0] = total
                    processedEntries[0] = processed
                    if (updateProgressPercent != null && total > 0) {
                        val percent = (processed * 100 / total).coerceIn(0, 100)
                        updateProgressPercent(DownloadAndExtractState.Downloading, percent)
                    }
                },
                {
                    continuation.resume(Unit)
                },
                { message ->
                    continuation.resumeWithException(UncivShowableException(message))
                }
            )
        }
        if (totalEntries[0] == 0) {
            throw UncivShowableException("Invalid Mod archive structure")
        }
    }

    private fun sanitizeEntryName(rawName: String): String {
        var name = rawName.replace('\\', '/')
        while (name.startsWith("../")) name = name.removePrefix("../")
        while (name.startsWith("/")) name = name.removePrefix("/")
        name = name.replace("/../", "/")
        return name
    }

    private fun bytesToBuffer(bytes: ByteArray): ArrayBuffer {
        val view = Int8Array(bytes.size)
        for (i in bytes.indices) view[i] = bytes[i]
        return view.buffer
    }

    private fun bufferToBytes(buffer: ArrayBuffer): ByteArray {
        val view = Int8Array(buffer)
        val out = ByteArray(view.length)
        for (i in 0 until view.length) out[i] = view[i].toByte()
        return out
    }

    private val parseAttachmentDispositionRegex =
        Regex("""attachment;\s*filename\s*=\s*(["'])?(.*?)\1(;|$)""")

    private fun parseNameFromDisposition(disposition: String?, default: String): String {
        fun String.removeZipExtension() = removeSuffix(".zip").replace('.', ' ')
        if (disposition == null) return default.removeZipExtension()
        val match = parseAttachmentDispositionRegex.matchAt(disposition, 0)
            ?: return default.removeZipExtension()
        return match.groups[2]!!.value.removeZipExtension()
    }

    private fun copyRecursively(source: FileHandle, dest: FileHandle) {
        if (source.isDirectory) {
            dest.mkdirs()
            for (child in source.list()) {
                copyRecursively(child, dest.child(child.name()))
            }
        } else {
            dest.parent().mkdirs()
            dest.writeBytes(source.readBytes(), false)
        }
    }

    private fun deleteRecursively(target: FileHandle?) {
        if (target == null || !target.exists()) return
        if (target.isDirectory) {
            for (child in target.list()) {
                deleteRecursively(child)
            }
        }
        target.delete()
    }

    private fun regexListOf(vararg pattern: String) =
        pattern.map { Regex(it, RegexOption.IGNORE_CASE) }

    private val goodFolders = regexListOf(
        "Images",
        "jsons",
        "maps",
        "music",
        "scenarios",
        "sounds",
        "voices",
        "Images\\..*",
        "\\.github"
    )
    private val goodFiles = regexListOf(
        ".*\\.atlas",
        ".*\\.png",
        "preview.jpg",
        ".*\\.md",
        "Atlases.json",
        "\\.nomedia",
        "license"
    )

    private fun isValidModFolder(dir: FileHandle): Boolean {
        var good = 0
        var bad = 0
        for (file in dir.list()) {
            val goodList = if (file.isDirectory) goodFolders else goodFiles
            if (goodList.any { file.name().matches(it) }) good++ else bad++
        }
        return good > 0 && good > bad
    }

    private fun resolveZipStructure(
        dir: FileHandle,
        defaultModName: String
    ): Pair<FileHandle, String> {
        if (isValidModFolder(dir)) return dir to defaultModName
        val subdirs = dir.list().filter { it.isDirectory }
        if (subdirs.size != 1 || !isValidModFolder(subdirs[0])) {
            throw UncivShowableException("Invalid Mod archive structure")
        }
        val subdir = subdirs[0]
        return subdir to choosePrettierName(subdir.name(), defaultModName)
    }

    private fun choosePrettierName(folderName: String, defaultModName: String): String {
        fun Char.isHex() = ((this - '0') and 0xFFFF) < 10 || ((this - 'A') and 0xFFDF) < 6
        if (defaultModName.all { it.isHex() } && folderName.endsWith(defaultModName)) {
            return folderName.removeSuffix(defaultModName).removeSuffix("-")
        }
        fun String.isMixedCase() = this != lowercase() && this != uppercase()
        if (defaultModName.startsWith(folderName, true) && defaultModName.isMixedCase() && !folderName.isMixedCase()) {
            return defaultModName.removeSuffix("-main").removeSuffix("-master")
        }
        return folderName
    }

    class RequestBuilder {
        var method: String = "GET"
        var path: String = ""
        var body: String? = null
        val query: MutableMap<String, String> = LinkedHashMap()
        val headers: MutableMap<String, String> = defaultHeaders().toMutableMap()

        fun buildUrl(): String {
            val base = baseUrl.trimEnd('/')
            val fullPath = if (path.startsWith("/")) path else "/$path"
            val url = StringBuilder(base).append(fullPath)
            if (query.isNotEmpty()) {
                url.append('?')
                query.entries.joinToString("&") { entry ->
                    "${encode(entry.key)}=${encode(entry.value)}"
                }.also { url.append(it) }
            }
            return url.toString()
        }

        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8")
    }
}
