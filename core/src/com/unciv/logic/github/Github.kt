package com.unciv.logic.github

import com.badlogic.gdx.Files
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.BackwardCompatibility.updateDeprecations
import com.unciv.logic.UncivShowableException
import com.unciv.logic.github.GithubAPI.getUrlForTreeQuery
import com.unciv.models.ruleset.ModOptions
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.FileFilter
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.zip.ZipException


/**
 * Utility managing Github access (except the link in WorldScreenCommunityPopup)
 *
 * Singleton - RateLimit is shared app-wide and has local variables, and is not tested for thread safety.
 * Therefore, additional effort is required should [tryGetGithubReposWithTopic] ever be called non-sequentially.
 * [download] and [downloadAndExtract] should be thread-safe as they are self-contained.
 * They do not join in the [RateLimit] handling because Github doc suggests each API
 * has a separate limit (and I found none for cloning via a zip).
 */
object Github {
    private const val contentDispositionHeader = "Content-Disposition"
    private val parseAttachmentDispositionRegex = Regex("""attachment;\s*filename\s*=\s*(["'])?(.*?)\1(;|$)""")
    private val redirectingResponseCodes = setOf(301, 302, 303, 307, 308)
    private class RedirectionException : Exception()

    // These are used in isValidModFolder to check an archive's content
    private val goodFolders = listOf("Images", "jsons", "maps", "music", "sounds", "Images\\..*", "scenarios")
        .map { Regex(it, RegexOption.IGNORE_CASE) }
    private val goodFiles = listOf(".*\\.atlas", ".*\\.png", "preview.jpg", ".*\\.md", "Atlases.json", ".nomedia", "license", "contribute.md", "readme.md", "credits.md")
        .map { Regex(it, RegexOption.IGNORE_CASE) }

    // Consider merging this with the Dropbox function
    /**
     * Helper opens an url and accesses its input stream, logging errors to the console
     * @param url String representing a [URL] to download.
     * @param preDownloadAction Optional callback that will be executed between opening the connection and
     *          accessing its data - passes the [connection][HttpURLConnection] and allows e.g. reading the response headers.
     * @return The [InputStream] if successful, `null` otherwise.
     */
    fun download(url: String, preDownloadAction: (HttpURLConnection) -> Unit = {}): InputStream? {
        try {
            // Problem type 1 - opening the URL connection
            @Suppress("DEPRECATION") // We still support Java < 20
            with(URL(url).openConnection() as HttpURLConnection)
            {
                preDownloadAction(this)
                // Problem type 2 - getting the information
                try {
                    return inputStream
                } catch (ex: Exception) {
                    // No error handling, just log the message.
                    // NOTE that this 'read error stream' CAN ALSO fail, but will be caught by the big try/catch
                    val reader = BufferedReader(InputStreamReader(errorStream, Charsets.UTF_8))
                    Log.error("Message from GitHub: %s", reader.readText())
                    throw ex
                }
            }
        } catch (ex: UncivShowableException) {
            throw ex
        } catch (_: RedirectionException) {
            return null
        } catch (ex: Exception) {
            Log.error("Exception during GitHub download", ex)
            return null
        }
    }

    /**
     * Download a mod and extract, deleting any pre-existing version.
     * @param repo A repository descriptor with either `direct_zip_url` set or the fields `name`, `html_url` and `default_branch` populated.
     * @param modsFolder Destination handle of mods folder - also controls Android internal/external.
     * @param updateProgressPercent A function recieving a download progress percentage (0-100) roughly every 100ms. Call will run under a Coroutine context.
     * @author **Warning**: This took a long time to get just right, so if you're changing this, ***TEST IT THOROUGHLY*** on _both_ Desktop _and_ Phone.
     * @return FileHandle for the downloaded Mod's folder or null if download failed.
     */
    fun downloadAndExtract(
        repo: GithubAPI.Repo,
        modsFolder: FileHandle,
        /** Should accept a number 0-100 */
        updateProgressPercent: ((Int)->Unit)? = null
    ): FileHandle? {
        var modNameFromFileName = repo.name

        val defaultBranch = repo.default_branch
        val zipUrl: String
        val tempName: String
        if (repo.direct_zip_url.isEmpty()) {
            val gitRepoUrl = repo.html_url
            // Initiate download - the helper returns null when it fails
            zipUrl = GithubAPI.getUrlForBranchZip(gitRepoUrl, defaultBranch)

            // Get a mod-specific temp file name
            tempName = "temp-" + gitRepoUrl.hashCode().toString(16)
        } else {
            zipUrl = repo.direct_zip_url
            tempName = "temp-" + repo.toString().hashCode().toString(16)
        }

        var contentLength = 0
        var redirectCount = 0
        var currentUrl = zipUrl
        var inputStream: InputStream? = null
        while (redirectCount < 2) {
            inputStream = download(currentUrl) {
                // We DO NOT want to accept "Transfer-Encoding: chunked" here, as we need to know the size for progress tracking
                // So this attempts to limit the encoding to gzip only
                // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Transfer-Encoding
                // HOWEVER it doesn't seem to work - the server still sends chunked data sometimes
                // which means we don't actually know the total length :(
                it.setRequestProperty("Accept-Encoding", "gzip")

                val responseCode = it.responseCode
                val location = it.getHeaderField("Location")
                when {
                    responseCode in redirectingResponseCodes && !location.isNullOrEmpty() -> {
                        currentUrl = location
                        redirectCount++
                        throw RedirectionException()
                    }
                    responseCode == 403 && it.getHeaderField("CF-RAY") != null && it.getHeaderField("cf-mitigated").orEmpty() == "challenge" ->
                        throw UncivShowableException("Blocked by Cloudflare")
                    responseCode in 401..403 || responseCode == 407 ->
                        throw UncivShowableException("Servers requiring authentication are not supported")
                    responseCode in 300..499 ->
                        throw UncivShowableException("Unexpected response: [${it.responseMessage}]")
                    responseCode in 500..599 ->
                        throw UncivShowableException("Server failure: [${it.responseMessage}]")
                    else -> {
                        modNameFromFileName = parseNameFromDisposition(it.getHeaderField(contentDispositionHeader), modNameFromFileName)
                        // We could check Content-Type=[application/x-zip-compressed] here, but the ZipFile will catch that anyway. Would save some bandwidth, however.

                        contentLength = it.getHeaderField("Content-Length")?.toInt()
                            ?: 0 // repo.length is a total lie
                    }
                }
            }
            if (inputStream != null) break
        }
        if (inputStream == null) return null

        // Download to temporary zip

        // minimum viable bytes-read tracking
        class CountingInputStream(originalStream: InputStream): InputStream() {
            private var count = 0
            private val wrapped = originalStream
            override fun read(): Int {
                count++
                return wrapped.read()
            }
            fun bytesRead() = count
        }

        var trackerThread: Job? = null
        val countingStream = CountingInputStream(inputStream)
        if (updateProgressPercent != null && contentLength > 0) {
            trackerThread = Concurrency.run("Downloading mod progress") {
                while (this.isActive) {
                    val percentage = countingStream.bytesRead() * 100 / contentLength
                    updateProgressPercent(percentage)
                    delay(100)
                }
            }
        }

        val tempZipFileHandle = modsFolder.child("$tempName.zip")
        tempZipFileHandle.write(countingStream, false)
        trackerThread?.cancel()

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
            .sortedBy { file -> file.extension() == "atlas" } ) {
            innerFileOrFolder.renameOrMove(finalDestination)
        }

        // clean up
        tempZipFileHandle.delete()
        unzipDestination.deleteDirectory()
        if (tempBackup != null)
            if (tempBackup.isDirectory) tempBackup.deleteDirectory() else tempBackup.delete()

        return finalDestination
    }

    private fun parseNameFromDisposition(disposition: String?, default: String): String {
        fun String.removeZipExtension() = removeSuffix(".zip").replace('.', ' ')
        if (disposition == null) return default.removeZipExtension()
        val match = parseAttachmentDispositionRegex.matchAt(disposition, 0)
            ?: return default.removeZipExtension()
        return match.groups[2]!!.value.removeZipExtension()
    }

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
    private fun resolveZipStructure(dir: FileHandle, defaultModName: String): Pair<FileHandle, String> {
        if (isValidModFolder(dir))
            return dir to defaultModName
        val subdirs = dir.list(FileFilter { it.isDirectory })  // See detekt/#6822 - a direct lambda-to-SAM with typed `it` fails detektAnalysis
        if (subdirs.size != 1 || !isValidModFolder(subdirs[0]))
            throw UncivShowableException("Invalid Mod archive structure")
        return subdirs[0] to choosePrettierName(subdirs[0].name(), defaultModName)
    }
    private fun choosePrettierName(folderName: String, defaultModName: String): String {
        fun String.isMixedCase() = this != lowercase() && this != uppercase()
        if (defaultModName.startsWith(folderName, true) && defaultModName.isMixedCase() && !folderName.isMixedCase())
            return defaultModName.removeSuffix("-main").removeSuffix("-master")
        return folderName
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

    /**
     * Query GitHub for repositories marked "unciv-mod"
     * @param amountPerPage Number of search results to return for this request.
     * @param page          The "page" number, starting at 1.
     * @return              Parsed [RepoSearch][GithubAPI.RepoSearch] json on success, `null` on failure.
     * @see <a href="https://docs.github.com/en/rest/reference/search#search-repositories">Github API doc</a>
     */
    fun tryGetGithubReposWithTopic(amountPerPage: Int, page: Int, searchRequest: String = ""): GithubAPI.RepoSearch? {
        val link = GithubAPI.getUrlForModListing(searchRequest, amountPerPage, page)
        var retries = 2
        while (retries > 0) {
            retries--
            // obey rate limit
            if (RateLimit.waitForLimit()) return null
            // try download
            val inputStream = download(link) {
                if (it.responseCode == 403 || it.responseCode == 200 && page == 1 && retries == 1) {
                    // Pass the response headers to the rate limit handler so it can process the rate limit headers
                    RateLimit.notifyHttpResponse(it)
                    retries++   // An extra retry so the 403 is ignored in the retry count
                }
            } ?: continue
            val text = inputStream.bufferedReader().readText()
            try {
                return json().fromJson(GithubAPI.RepoSearch::class.java, text)
            } catch (_: Throwable) {
                throw Exception("Failed to parse Github response as json - $text")
            }
        }
        return null
    }

    /** Get a Pixmap from a "preview" png or jpg file at the root of the repo, falling back to the
     *  repo owner's avatar [avatarUrl]. The file content url is constructed from [modUrl] and [defaultBranch]
     *  by replacing the host with `raw.githubusercontent.com`.
     */
    fun tryGetPreviewImage(modUrl: String, defaultBranch: String, avatarUrl: String?): Pixmap? {
        // Side note: github repos also have a "Social Preview" optionally assignable on the repo's
        // settings page, but that info is inaccessible using the v3 API anonymously. The easiest way
        // to get it would be to query the the repo's frontend page (modUrl), and parse out
        // `head/meta[property=og:image]/@content`, which is one extra spurious roundtrip and a
        // non-trivial waste of bandwidth.
        // Thus we ask for a "preview" file as part of the repo contents instead.
        val fileLocation = GithubAPI.getUrlForPreview(modUrl, defaultBranch)
        try {
            val file = download("$fileLocation.jpg")
                ?: download("$fileLocation.png")
                    // Note: avatar urls look like: https://avatars.githubusercontent.com/u/<number>?v=4
                    // So the image format is only recognizable from the response "Content-Type" header
                    // or by looking for magic markers in the bits - which the Pixmap constructor below does.
                ?: avatarUrl?.let { download(it) }
                ?: return null
            val byteArray = file.readBytes()
            val buffer = ByteBuffer.allocateDirect(byteArray.size).put(byteArray).position(0)
            return Pixmap(buffer)
        } catch (_: Throwable) {
            return null
        }
    }

    /** Queries github for a tree and calculates the sum of the blob sizes.
     *  @return -1 on failure, else size rounded to kB
     *  @see <a href="https://docs.github.com/en/rest/git/trees#get-a-tree">Github API "Get a tree"</a>
     */
    fun getRepoSize(repo: GithubAPI.Repo): Int {
        val link = repo.getUrlForTreeQuery()

        var retries = 2
        while (retries > 0) {
            retries--
            // obey rate limit
            if (RateLimit.waitForLimit()) return -1
            // try download
            val inputStream = download(link) {
                if (it.responseCode == 403 || it.responseCode == 200 && retries == 1) {
                    // Pass the response headers to the rate limit handler so it can process the rate limit headers
                    RateLimit.notifyHttpResponse(it)
                    retries++   // An extra retry so the 403 is ignored in the retry count
                }
            } ?: continue

            val tree = json().fromJson(GithubAPI.Tree::class.java, inputStream.bufferedReader().readText())
            if (tree.truncated) return -1  // unlikely: >100k blobs or blob > 7MB

            var totalSizeBytes = 0L
            for (file in tree.tree)
                totalSizeBytes += file.size

            // overflow unlikely: >2TB
            return ((totalSizeBytes + 512) / 1024).toInt()
        }
        return -1
    }

    /**
     * Query GitHub for topics named "unciv-mod*"
     * @return Parsed [TopicSearchResponse][GithubAPI.TopicSearchResponse] json on success, `null` on failure.
     */
    fun tryGetGithubTopics(): GithubAPI.TopicSearchResponse? {
        val link = GithubAPI.urlToQueryModTopics
        var retries = 2
        while (retries > 0) {
            retries--
            // obey rate limit
            if (RateLimit.waitForLimit()) return null
            // try download
            val inputStream = download(link) {
                if (it.responseCode == 403 || it.responseCode == 200 && retries == 1) {
                    // Pass the response headers to the rate limit handler so it can process the rate limit headers
                    RateLimit.notifyHttpResponse(it)
                    retries++   // An extra retry so the 403 is ignored in the retry count
                }
            } ?: continue
            return json().fromJson(GithubAPI.TopicSearchResponse::class.java, inputStream.bufferedReader().readText())
        }
        return null
    }

    /** Rewrite modOptions file for a mod we just installed to include metadata we got from the GitHub api
     *
     *  (called on background thread)
     */
    fun rewriteModOptions(repo: GithubAPI.Repo, modFolder: FileHandle) {
        val modOptionsFile = modFolder.child("jsons/ModOptions.json")
        val modOptions = if (modOptionsFile.exists()) json().fromJsonFile(ModOptions::class.java, modOptionsFile) else ModOptions()

        // If this is false we didn't get github repo info, do a defensive merge so the Repo.parseUrl or download
        // code can decide defaults but leave any meaningful field of a zip-included ModOptions alone.
        val overwriteAlways = repo.direct_zip_url.isEmpty()

        if (overwriteAlways || modOptions.modUrl.isEmpty()) modOptions.modUrl = repo.html_url
        if (overwriteAlways || modOptions.defaultBranch == "master" && repo.default_branch.isNotEmpty())
            modOptions.defaultBranch = repo.default_branch
        if (overwriteAlways || modOptions.lastUpdated.isEmpty()) modOptions.lastUpdated = repo.pushed_at
        if (overwriteAlways || modOptions.author.isEmpty()) modOptions.author = repo.owner.login
        if (overwriteAlways || modOptions.modSize == 0) modOptions.modSize = repo.size
        if (overwriteAlways || modOptions.topics.isEmpty()) modOptions.topics = repo.topics

        modOptions.updateDeprecations()
        json().toJson(modOptions, modOptionsFile)
    }

    private const val outerBlankReplacement = '='
    // Github disallows **any** special chars and replaces them with '-' - so use something ascii the
    // OS accepts but still is recognizable as non-original, to avoid confusion

    /** Convert a [Repo][[GithubAPI.Repo] name to a local name for both display and folder name
     *
     *  Replaces '-' with blanks but ensures no leading or trailing blanks.
     *  As mad modders know no limits, trailing "-" did indeed happen, causing things to break due to trailing blanks on a folder name.
     *  As "test-" and "test" are different allowed repository names, trimmed blanks are replaced with one equals sign per side.
     *  @param onlyOuterBlanks If `true` ignores inner dashes - only start and end are treated. Useful when modders have manually created local folder names using dashes.
     */
    fun String.repoNameToFolderName(onlyOuterBlanks: Boolean = false): String {
        var result = if (onlyOuterBlanks) this else replace('-', ' ')
        if (result.endsWith(' ')) result = result.trimEnd() + outerBlankReplacement
        if (result.startsWith(' ')) result = outerBlankReplacement + result.trimStart()
        return result
    }

    /** Inverse of [repoNameToFolderName] */
    // As of this writing, only used for loadMissingMods
    fun String.folderNameToRepoName(): String {
        var result = replace(' ', '-')
        if (result.endsWith(outerBlankReplacement)) result = result.trimEnd(outerBlankReplacement) + '-'
        if (result.startsWith(outerBlankReplacement)) result = '-' + result.trimStart(outerBlankReplacement)
        return result
    }
}
