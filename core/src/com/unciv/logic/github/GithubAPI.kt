package com.unciv.logic.github

import com.badlogic.gdx.Files
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.UncivKtor
import com.unciv.logic.UncivShowableException
import com.unciv.logic.github.Github.repoNameToFolderName
import com.unciv.logic.github.GithubAPI.parseUrl
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import java.io.FileFilter
import java.util.zip.ZipException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 *  "Namespace" collects all Github API structural knowledge
 *  - Response schema
 *  - Query URL builders
 *
 *  ### Collected doc links:
 *  - https://docs.github.com/en/repositories/working-with-files/using-files/downloading-source-code-archives#source-code-archive-urls
 *  - https://docs.github.com/en/rest/reference/search#search-repositories--code-samples
 *  - https://docs.github.com/en/rest/repos/repos
 *  - https://docs.github.com/en/rest/releases/releases
 *  - https://docs.github.com/en/rest/git/trees#get-a-tree
 */
@Suppress("PropertyName")  // We're declaring an external API schema
object GithubAPI {
    // region URL formatters
    /**
     * @see <a href="https://ktor.io/docs/client-default-request.html#url">
     *          Ktor Client > Developing applications > Requests > Default request > Base URL
     *      </a>
     */
    const val baseUrl = "https://api.github.com"

    /**
     * Add a bearer token here if needed
     *
     * @see <a href="https://github.com/yairm210/Unciv/issues/13951#issuecomment-3326406877">#13951 (comment)</a>
     */
    const val bearerToken = ""

    private val client = UncivKtor.client.config {
        defaultRequest {
            url(baseUrl)
            header("X-GitHub-Api-Version", "2022-11-28")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            userAgent(UncivGame.getUserAgent("Github"))
            if (bearerToken.isNotBlank()) bearerAuth(bearerToken)
        }
    }

    /**
     * Make a ktor request handling rate limits automatically
     */
    suspend fun request(
        maxRateLimitedRetries: Int = 3,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val resp = client.request(block)
        val rateLimited = consumeRateLimit(resp)

        return if (rateLimited) {
            if (maxRateLimitedRetries <= 0) return resp
            return request(maxRateLimitedRetries - 1, block)
        } else resp
    }

    private suspend fun paginatedRequest(
        page: Int, amountPerPage: Int, block: HttpRequestBuilder.() -> Unit
    ) = request {
        parameter("page", page)
        parameter("per_page", amountPerPage)
        block()
    }

    /** Format a download URL for a branch archive */
    // URL format see: https://docs.github.com/en/repositories/working-with-files/using-files/downloading-source-code-archives#source-code-archive-urls
    // Note: https://api.github.com/repos/owner/mod/zipball would be an alternative. Its response is a redirect, but our lib follows that and delivers the zip just fine.
    // Problems with the latter: Internal zip structure different, finalDestinationName would need a patch. Plus, normal URL escaping for owner/reponame does not work.
    internal fun getUrlForBranchZip(gitRepoUrl: String, branch: String) = "$gitRepoUrl/archive/refs/heads/$branch.zip"

    /** Format a download URL for a release archive */
    private fun Repo.getUrlForReleaseZip() = "$html_url/archive/refs/tags/$release_tag.zip"

    /** Format a URL to query a repo tree - to calculate actual size */
    // It's hard to see in the doc this not only accepts a commit SHA, but either branch (used here) or tag names too
    internal suspend fun Repo.fetchReleaseZip() = request {
        url("/repos/$full_name/git/trees/$default_branch")
        parameter("recursive", "true")
    }

    /**
     * Wait for rate limit to end if any and returns true if there was any rate limit
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun consumeRateLimit(resp: HttpResponse): Boolean {
        if (resp.status != HttpStatusCode.Forbidden && resp.status != HttpStatusCode.TooManyRequests) return false

        val remainingRequests = resp.headers["x-ratelimit-remaining"]?.toIntOrNull() ?: 0
        if (remainingRequests < 1) return false

        val resetEpoch = resp.headers["x-ratelimit-reset"]?.toLongOrNull() ?: 0
        delay(Instant.fromEpochSeconds(resetEpoch) - Clock.System.now())

        return true
    }

    suspend fun fetchGithubReposWithTopic(search: String, page: Int, amountPerPage: Int) =
        paginatedRequest(page, amountPerPage) {
            url("/search/repositories")
            parameter("sort", "stars")
            parameter("q", "$search topic:unciv-mod fork:true")
        }

    suspend fun fetchGithubTopics() = request {
        url("/search/topics")
        parameter("sort", "name")
        parameter("order", "asc")

        /**
         * `repositories:>1` means ignore unused or practically unused topics
         */
        parameter("q", "unciv-mod repositories:>1")
    }

    suspend fun fetchSingleRepo(owner: String, repoName: String) =
        request { url("/repos/$owner/$repoName") }

    /**
     * We are not using KtorGithubAPI here because the URL provided is not an API URL
     */
    suspend fun fetchPreviewImageOrNull(modUrl: String, branch: String, ext: String) =
        UncivKtor.getOrNull("$modUrl/$branch/preview.${ext}") { host = "raw.githubusercontent.com" }

    //endregion
    //region responses

    /**
     * Parsed Github repo search response
     * @property total_count Total number of hits for the search (ignoring paging window)
     * @property incomplete_results A flag set by github to indicate search was incomplete (never seen it on)
     * @property items Array of [repositories][Repo]
     * @see <a href="https://docs.github.com/en/rest/reference/search#search-repositories--code-samples">Github API doc</a>
     */
    class RepoSearch {
        @Suppress("MemberVisibilityCanBePrivate")
        var total_count = 0
        var incomplete_results = false
        var items = ArrayList<Repo>()
    }

    /** Part of [RepoSearch] in Github API response - one repository entry in [items][RepoSearch.items] */
    class Repo {

        /** Unlike the rest of this class, this is not part of the API but added by us locally
         *  to track whether [getRepoSize][Github.getRepoSize] has been run successfully for this repo */
        var hasUpdatedSize = false

        /** Not part of the github schema: Explicit final zip download URL for non-github or release downloads */
        var direct_zip_url = ""
        /** Not part of the github schema: release tag, for debugging (DL via direct_zip_url) */
        var release_tag = ""

        var name = ""
        var full_name = ""
        var description: String? = null
        var owner = RepoOwner()
        var stargazers_count = 0
        var default_branch = ""
        var html_url = ""
        var pushed_at = "" // don't use updated_at - see https://github.com/yairm210/Unciv/issues/6106
        var size = 0
        var topics = mutableListOf<String>()
        //var stargazers_url = ""
        //var homepage: String? = null      // might use instead of go to repo?
        //var has_wiki = false              // a wiki could mean proper documentation for the mod?

        /** String representation to be used for logging */
        override fun toString() = name.ifEmpty { direct_zip_url }

        companion object {
            /** Create a [Repo] metadata instance from a [url], supporting various formats
             *  from a repository landing page url to a free non-github zip download.
             *
             *  @see GithubAPI.parseUrl
             *  @return `null` for invalid links or any other failures
             */
            suspend fun parseUrl(url: String): Repo? = Repo().parseUrl(url)

            /** Query Github API for [owner]'s [repoName] repository metadata */
            suspend fun query(owner: String, repoName: String): Repo? {
                val resp = fetchSingleRepo(owner, repoName)
                return if (!resp.status.isSuccess()) null
                else json().fromJson(Repo::class.java, resp.bodyAsText())
            }
        }
    }

    /** Part of [Repo] in Github API response */
    class RepoOwner {
        var login = ""
        var avatar_url: String? = null
    }

    /** Topic search response */
    class TopicSearchResponse {
        // Commented out: Github returns them, but we're not interested
//         var total_count = 0
//         var incomplete_results = false
        var items = ArrayList<Topic>()
        class Topic {
            var name = ""
            var display_name: String? = null  // Would need to be curated, which is alottawork
//             var featured = false
//             var curated = false
            var created_at = "" // iso datetime with "Z" timezone
            var updated_at = "" // iso datetime with "Z" timezone
        }
    }

    /** Class to receive a github API "Get a tree" response parsed as json */
    // Parts of the response we ignore are commented out
    internal class Tree {
        //val sha = ""
        //val url = ""

        class TreeFile {
            //val path = ""
            //val mode = 0
            //val type = "" // blob / tree
            //val sha = ""
            //val url = ""
            var size: Long = 0L
        }

        @Suppress("MemberNameEqualsClassName")
        var tree = ArrayList<TreeFile>()
        var truncated = false
    }

    //endregion

    //region Flexible URL parsing
    /**
     * Initialize `this` with an url, extracting all possible fields from it
     * (html_url, author, repoName, branchName).
     *
     * Allow url formats:
     * * Basic repo url:
     *   https://github.com/author/repoName
     * * or complete 'zip' url from github's code->download zip menu:
     *   https://github.com/author/repoName/archive/refs/heads/branchName.zip
     * * or the branch url same as one navigates to on github through the "branches" menu:
     *   https://github.com/author/repoName/tree/branchName
     * * or release tag
     *   https://github.com/author/repoName/releases/tag/tagname
     *   https://github.com/author/repoName/archive/refs/tags/tagname.zip
     *
     * In the case of the basic repo url, an [API query](https://docs.github.com/en/rest/repos/repos#get-a-repository) is sent to determine the default branch.
     * Other url forms will not go online.
     *
     * @return a new Repo instance for the 'Basic repo url' case, otherwise `this`, modified, to allow chaining, `null` for invalid links or any other failures
     * @see <a href="https://docs.github.com/en/rest/repos/repos#get-a-repository--code-samples">Github API Repository Code Samples</a>
     */
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
        if (matchZip != null && matchZip.groups.size > 4)
            return processMatch(matchZip)

        val matchBranch = Regex("""^(.*/(.*)/(.*))/tree/(.*)$""").matchEntire(url)
        if (matchBranch != null && matchBranch.groups.size > 4)
            return processMatch(matchBranch)

        // Releases and tags -
        // TODO Query for latest release and save as Mod Version?
        // https://docs.github.com/en/rest/releases/releases#get-the-latest-release
        // TODO Query a specific release for its name attribute - the page will link the tag
        // https://docs.github.com/en/rest/releases/releases#get-a-release-by-tag-name

        val matchTagArchive = Regex("""^(.*/(.*)/(.*))/archive/(?:.*/)?tags/([^.]+).zip$""").matchEntire(url)
        if (matchTagArchive != null && matchTagArchive.groups.size > 4) {
            processMatch(matchTagArchive)
            release_tag = default_branch
            // leave default_branch even if it's actually a tag not a branch name
            // so the suffix of the inner first level folder inside the zip can be removed later
            direct_zip_url = url
            return this
        }
        val matchTagPage = Regex("""^(.*/(.*)/(.*))/releases/(?:.*/)?tag/([^.]+)$""").matchEntire(url)
        if (matchTagPage != null && matchTagPage.groups.size > 4) {
            processMatch(matchTagPage)
            release_tag = default_branch
            direct_zip_url = getUrlForReleaseZip()
            return this
        }

        val matchRepo = Regex("""^.*//.*/(.+)/(.+)/?$""").matchEntire(url)
        if (matchRepo != null && matchRepo.groups.size > 2) {
            // Query API if we got the 'https://github.com/author/repoName' URL format to get the correct default branch
            val repo = Repo.query(matchRepo.groups[1]!!.value, matchRepo.groups[2]!!.value)
            if (repo != null) return repo
        }

        // Only complain about invalid link if it isn't a http protocol (to think about: android document protocol? file protocol?)
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            return null

        // From here, we'll always return success and treat the url as direct-downloadable zip.
        // The Repo instance will be a pseudo-repo not corresponding to an actual github repo.
        html_url = ""
        direct_zip_url = url
        owner.login = "-unknown-"
        default_branch = "master" // only used to remove this suffix should the zip contain a inner folder
        // But see if we can extract a file name from the url
        // Will use filename from response headers, if content-disposition is sent, for the Mod name instead, done in downloadAndExtract
        val matchAnyZip = Regex("""^.*//(?:.*/)*([^/]+\.zip)$""").matchEntire(url)
        if (matchAnyZip != null && matchAnyZip.groups.size > 1)
            name = matchAnyZip.groups[1]!!.value
        full_name = name
        return this
    }
    //endregion

    /**
     * Download a mod and extract, deleting any pre-existing version.
     * @param modsFolder Destination handle of mods folder - also controls Android internal/external.
     * @param updateProgressPercent A function recieving a download progress percentage (0-100) roughly every 100ms. Call will run under a Coroutine context.
     * @author **Warning**: This took a long time to get just right, so if you're changing this, ***TEST IT THOROUGHLY*** on _both_ Desktop _and_ Phone.
     * @return FileHandle for the downloaded Mod's folder or null if download failed.
     */
    suspend fun Repo.downloadAndExtract(
        modsFolder: FileHandle,
        /** Should accept a number 0-100 */
        updateProgressPercent: ((Int) -> Unit)? = null
    ): FileHandle? {
        var modNameFromFileName = name

        val defaultBranch = default_branch
        val zipUrl: String
        val tempName: String
        if (direct_zip_url.isEmpty()) {
            val gitRepoUrl = html_url
            // Initiate download - the helper returns null when it fails
            zipUrl = getUrlForBranchZip(gitRepoUrl, defaultBranch)

            // Get a mod-specific temp file name
            tempName = "temp-" + gitRepoUrl.hashCode().toString(16)
        } else {
            zipUrl = direct_zip_url
            tempName = "temp-" + toString().hashCode().toString(16)
        }

        // If the Content-Length header was missing, fall back to the reported repo size (which should be in kB)
        // and assume low compression efficiency - bigger mods typically have mostly images.
        val fallbackSize = size.toLong() * 1024L * 4L / 5L
        @Pure
        fun reportProgress(bytesReceivedTotal: Long, contentLength: Long?) {
            val progressEndsAtSize = contentLength ?: fallbackSize
            if (progressEndsAtSize <= 0L) return
            val percent = bytesReceivedTotal * 100L / progressEndsAtSize
            updateProgressPercent!!(percent.toInt().coerceIn(0, 100))
        }

        val resp = UncivKtor.client.get(zipUrl) {
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
            }
            if (updateProgressPercent != null)
                onDownload(::reportProgress)
        }

        /**
         * This block is borrowed from the deleted method `processRequestHandlingRedirects()`
         * See: https://github.com/yairm210/Unciv/blob/1cb3f94d36009719b63f6d8e9d2e49c1bd594a8f/core/src/com/unciv/logic/github/Github.kt#L197-L216
         */
        when {
            resp.status == HttpStatusCode.NotFound -> return null
            (resp.status == HttpStatusCode.Forbidden) &&
                resp.headers["CF-RAY"] != null && resp.headers["cf-mitigated"].orEmpty() == "challenge" ->
                throw UncivShowableException("Blocked by Cloudflare")

            (resp.status.value in 401..403) || resp.status == HttpStatusCode.ProxyAuthenticationRequired ->
                throw UncivShowableException("Servers requiring authentication are not supported")

            resp.status.value in 300..499 ->
                throw UncivShowableException("Unexpected response: [${resp.bodyAsText()}]")

            resp.status.value in 500..599 ->
                throw UncivShowableException("Server failure: [${resp.bodyAsText()}}]")

            !resp.status.isSuccess() ->
                throw UncivShowableException("Unknown Issue!\nStatus: ${resp.status}\nBody:[${resp.bodyAsText()}}]")
        }

        val content = resp.bodyAsBytes()
        if (content.isEmpty()) return null

        val disposition = resp.headers[HttpHeaders.ContentDisposition]
        modNameFromFileName = parseNameFromDisposition(disposition, modNameFromFileName)

        // Download to temporary zip
        // If the Content-Length header was missing, fall back to the reported repo size (which should be in kB)
        // and assume low compression efficiency - bigger mods typically have mostly images.
        val tempZipFileHandle = modsFolder.child("$tempName.zip")
        tempZipFileHandle.writeBytes(content, false)

        // prepare temp unpacking folder
        val unzipDestination = tempZipFileHandle.sibling(tempName) // folder, not file
        // prevent mixing new content with old - hopefully there will never be cadavers of our tempZip stuff
        if (unzipDestination.exists())
            if (unzipDestination.isDirectory) unzipDestination.deleteDirectory() else unzipDestination.delete()

        try {
            Zip.extractFolder(tempZipFileHandle, unzipDestination)
        } catch (ex: ZipException) {
            throw UncivShowableException("That is not a valid ZIP file", ex)
        }

        val (innerFolder, modName) = resolveZipStructure(unzipDestination, modNameFromFileName)

        // modName can be "$repoName-$defaultBranch"
        val finalDestinationName = modName.replace("-$defaultBranch", "").repoNameToFolderName()
        // finalDestinationName is now the mod name as we display it. Folder name needs to be identical.
        val finalDestination = modsFolder.child(finalDestinationName)

        // prevent mixing new content with old
        var tempBackup: FileHandle? = null
        if (finalDestination.exists()) {
            tempBackup = finalDestination.sibling("$finalDestinationName.updating")
            finalDestination.renameOrMove(tempBackup)
        }

        // Move temp unpacked content to their final place
        finalDestination.mkdirs() // If we don't create this as a directory, it will think this is a file and nothing will work.
        // The move will reset the last modified time (recursively, at least on Linux)
        // This sort will guarantee the desktop launcher will not re-pack textures and overwrite the atlas as delivered by the mod
        for (innerFileOrFolder in innerFolder.list()
            .sortedBy { file -> file.extension() == "atlas" }) {
            innerFileOrFolder.renameOrMove(finalDestination)
        }

        // clean up
        tempZipFileHandle.delete()
        unzipDestination.deleteDirectory()
        if (tempBackup != null)
            if (tempBackup.isDirectory) tempBackup.deleteDirectory() else tempBackup.delete()

        return finalDestination
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

    private fun FileHandle.renameOrMove(dest: FileHandle) {
        // Gdx tries a java rename for Absolute and External, but NOT for Local - rectify that
        if (type() == Files.FileType.Local) {
            // See #5346: renameTo 'unpacks' a source folder if the destination exists and is an
            // empty folder, dropping the name of the source entirely.
            // Safer to ask for a move to the not-yet-existing full resulting path instead.
            if (isDirectory)
                if (file().renameTo(dest.child(name()).file())) return
                else
                    if (file().renameTo(dest.file())) return
        }
        moveTo(dest)
    }

    private val goodFolders =
        listOf("Images", "jsons", "maps", "music", "sounds", "Images\\..*", "scenarios", ".github")
            .map { Regex(it, RegexOption.IGNORE_CASE) }
    private val goodFiles = listOf(
        ".*\\.atlas",
        ".*\\.png",
        "preview.jpg",
        ".*\\.md",
        "Atlases.json",
        ".nomedia",
        "license",
        "contribute.md",
        "readme.md",
        "credits.md"
    )
        .map { Regex(it, RegexOption.IGNORE_CASE) }

    private fun isValidModFolder(dir: FileHandle): Boolean {
        var good = 0
        var bad = 0
        for (file in dir.list()) {
            val goodList = if (file.isDirectory) goodFolders else goodFiles
            if (goodList.any { file.name().matches(it) }) good++ else bad++
        }
        return good > 0 && good > bad
    }

    /** Check whether the unpacked zip contains a subfolder with mod content or is already the mod.
     *  If there's a subfolder we'll assume it is the mod name, optionally suffixed with branch or release-tag name like github does.
     *  @return Pair: actual mod content folder to name (subfolder name or [defaultModName])
     */
    private fun resolveZipStructure(
        dir: FileHandle,
        defaultModName: String
    ): Pair<FileHandle, String> {
        if (isValidModFolder(dir))
            return dir to defaultModName
        val subdirs =
            dir.list(FileFilter { it.isDirectory })  // See detekt/#6822 - a direct lambda-to-SAM with typed `it` fails detektAnalysis
        if (subdirs.size != 1 || !isValidModFolder(subdirs[0]))
            throw UncivShowableException("Invalid Mod archive structure")
        return subdirs[0] to choosePrettierName(subdirs[0].name(), defaultModName)
    }

    private fun choosePrettierName(folderName: String, defaultModName: String): String {
        fun String.isMixedCase() = this != lowercase() && this != uppercase()
        if (defaultModName.startsWith(
                folderName,
                true
            ) && defaultModName.isMixedCase() && !folderName.isMixedCase()
        )
            return defaultModName.removeSuffix("-main").removeSuffix("-master")
        return folderName
    }
}
