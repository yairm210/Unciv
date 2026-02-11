package com.unciv.logic.github

import com.badlogic.gdx.files.FileHandle
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
        val resp = WebHttp.requestBytes("GET", url, headers = defaultHeaders())
        return if (resp.ok) resp else null
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
                return json().fromJson(Repo::class.java, resp.text)
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
                return json().fromJson(RepoOwner::class.java, resp.text)
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
        val zipUrl: String
        val tempName: String
        if (direct_zip_url.isEmpty()) {
            val gitRepoUrl = html_url
            zipUrl = getUrlForBranchZip(gitRepoUrl, defaultBranch)
            tempName = "temp-" + gitRepoUrl.hashCode().toString(16)
        } else {
            zipUrl = direct_zip_url
            tempName = "temp-" + toString().hashCode().toString(16)
        }

        val unzipDestination = modsFolder.child(tempName)
        if (unzipDestination.exists()) {
            if (unzipDestination.isDirectory) unzipDestination.deleteDirectory() else unzipDestination.delete()
        }

        updateProgressPercent?.invoke(DownloadAndExtractState.Downloading, 0)
        val resp = WebHttp.requestBytes("GET", zipUrl, headers = defaultHeaders(), timeoutMs = 120000)
        if (!resp.ok || resp.bytes == null) {
            throw UncivShowableException("Failed to download mod archive: ${resp.status} ${resp.statusText}")
        }
        modNameFromFileName = parseNameFromDisposition(resp.headers["content-disposition"], modNameFromFileName)

        unzipDestination.mkdirs()
        unzipToFolder(resp.bytes, unzipDestination, updateProgressPercent)

        if (!unzipDestination.exists() || unzipDestination.list().isEmpty()) {
            if (unzipDestination.exists()) unzipDestination.deleteDirectory()
            return null
        }

        val (innerFolder, modName) = resolveZipStructure(unzipDestination, modNameFromFileName)
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

        deleteRecursively(unzipDestination)
        if (tempBackup != null) {
            deleteRecursively(tempBackup)
        }

        Github.rewriteModOptions(this, finalDestination)
        updateProgressPercent?.invoke(DownloadAndExtractState.Finishing, 100)
        return finalDestination
    }

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
