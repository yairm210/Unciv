package com.unciv.ui.pickerscreens

import com.badlogic.gdx.Files
import com.badlogic.gdx.files.FileHandle
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.BackwardCompatibility.updateDeprecations
import com.unciv.models.ruleset.ModOptions
import com.unciv.utils.Log
import com.unciv.utils.debug
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipFile


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

    // Consider merging this with the Dropbox function
    /**
     * Helper opens am url and accesses its input stream, logging errors to the console
     * @param url String representing a [URL] to download.
     * @param action Optional callback that will be executed between opening the connection and
     *          accessing its data - passes the [connection][HttpURLConnection] and allows e.g. reading the response headers.
     * @return The [InputStream] if successful, `null` otherwise.
     */
    fun download(url: String, action: (HttpURLConnection) -> Unit = {}): InputStream? {
        with(URL(url).openConnection() as HttpURLConnection)
        {
            action(this)

            return try {
                inputStream
            } catch (ex: Exception) {
                Log.error("Exception during GitHub download", ex)
                val reader = BufferedReader(InputStreamReader(errorStream))
                Log.error("Message from GitHub: %s", reader.readText())
                null
            }
        }
    }

    /**
     * Download a mod and extract, deleting any pre-existing version.
     * @param gitRepoUrl Url of the repository as delivered by the Github search query
     * @param defaultBranch Branch name as delivered by the Github search query
     * @param folderFileHandle Destination handle of mods folder - also controls Android internal/external
     * @author **Warning**: This took a long time to get just right, so if you're changing this, ***TEST IT THOROUGHLY*** on _both_ Desktop _and_ Phone
     * @return FileHandle for the downloaded Mod's folder or null if download failed
     */
    fun downloadAndExtract(
        gitRepoUrl: String,
        defaultBranch: String,
        folderFileHandle: FileHandle
    ): FileHandle? {
        // Initiate download - the helper returns null when it fails
        val zipUrl = "$gitRepoUrl/archive/$defaultBranch.zip"
        val inputStream = download(zipUrl) ?: return null

        // Get a mod-specific temp file name
        val tempName = "temp-" + gitRepoUrl.hashCode().toString(16)

        // Download to temporary zip
        val tempZipFileHandle = folderFileHandle.child("$tempName.zip")
        tempZipFileHandle.write(inputStream, false)

        // prepare temp unpacking folder
        val unzipDestination = tempZipFileHandle.sibling(tempName) // folder, not file
        // prevent mixing new content with old - hopefully there will never be cadavers of our tempZip stuff
        if (unzipDestination.exists())
            if (unzipDestination.isDirectory) unzipDestination.deleteDirectory() else unzipDestination.delete()

        Zip.extractFolder(tempZipFileHandle, unzipDestination)

        val innerFolder = unzipDestination.list().first()
        // innerFolder should now be "$tempName/$repoName-$defaultBranch/" - use this to get mod name
        val finalDestinationName = innerFolder.name().replace("-$defaultBranch", "").replace('-', ' ')
        // finalDestinationName is now the mod name as we display it. Folder name needs to be identical.
        val finalDestination = folderFileHandle.child(finalDestinationName)

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
     * Implements the ability wo work with GitHub's rate limit, recognize blocks from previous attempts, wait and retry.
     */
    object RateLimit {
        // https://docs.github.com/en/rest/reference/search#rate-limit
        private const val maxRequestsPerInterval = 10
        private const val intervalInMilliSeconds = 60000L
        private const val maxWaitLoop = 3

        private var account = 0         // used requests
        private var firstRequest = 0L   // timestamp window start (java epoch millisecond)

        /*
            Github rate limits do not use sliding windows - you (if anonymous) get one window
            which starts with the first request (if a window is not already active)
            and ends 60s later, and a budget of 10 requests in that window. Once it expires,
            everything is forgotten and the process starts from scratch
         */

        private val millis: Long
            get() = System.currentTimeMillis()

        /** calculate required wait in ms
         * @return Estimated number of milliseconds to wait for the rate limit window to expire
         */
        private fun getWaitLength()
                = (firstRequest + intervalInMilliSeconds - millis)

        /** Maintain and check a rate-limit
         *  @return **true** if rate-limited, **false** if another request is allowed
         */
        private fun isLimitReached(): Boolean {
            val now = millis
            val elapsed = if (firstRequest == 0L) intervalInMilliSeconds else now - firstRequest
            if (elapsed >= intervalInMilliSeconds) {
                firstRequest = now
                account = 1
                return false
            }
            if (account >= maxRequestsPerInterval) return true
            account++
            return false
        }

        /** If rate limit in effect, sleep long enough to allow next request.
         *
         *  @return **true** if waiting did not clear isLimitReached() (can only happen if the clock is broken),
         *                  or the wait has been interrupted by Thread.interrupt()
         *          **false** if we were below the limit or slept long enough to drop out of it.
         */
        fun waitForLimit(): Boolean {
            var loopCount = 0
            while (isLimitReached()) {
                val waitLength = getWaitLength()
                try {
                    Thread.sleep(waitLength)
                } catch ( ex: InterruptedException ) {
                    return true
                }
                if (++loopCount >= maxWaitLoop) return true
            }
            return false
        }

        /** http responses should be passed to this so the actual rate limit window can be evaluated and used.
         *  The very first response and all 403 ones are good candidates if they can be expected to contain GitHub's rate limit headers.
         *
         *  see: https://docs.github.com/en/rest/overview/resources-in-the-rest-api#rate-limiting
         */
        fun notifyHttpResponse(response: HttpURLConnection) {
            if (response.responseMessage != "rate limit exceeded" && response.responseCode != 200) return

            fun getHeaderLong(name: String, default: Long = 0L) =
                response.headerFields[name]?.get(0)?.toLongOrNull() ?: default
            val limit = getHeaderLong("X-RateLimit-Limit", maxRequestsPerInterval.toLong()).toInt()
            val remaining = getHeaderLong("X-RateLimit-Remaining").toInt()
            val reset = getHeaderLong("X-RateLimit-Reset")

            if (limit != maxRequestsPerInterval)
                debug("GitHub API Limit reported via http (%s) not equal assumed value (%s)", limit, maxRequestsPerInterval)
            account = maxRequestsPerInterval - remaining
            if (reset == 0L) return
            firstRequest = (reset + 1L) * 1000L - intervalInMilliSeconds
        }
    }

    /**
     * Query GitHub for repositories marked "unciv-mod"
     * @param amountPerPage Number of search results to return for this request.
     * @param page          The "page" number, starting at 1.
     * @return              Parsed [RepoSearch] json on success, `null` on failure.
     * @see <a href="https://docs.github.com/en/rest/reference/search#search-repositories">Github API doc</a>
     */
    fun tryGetGithubReposWithTopic(amountPerPage:Int, page:Int, searchRequest: String = ""): RepoSearch? {
        // Add + here to separate the query text from its parameters
        val searchText = if (searchRequest != "") "$searchRequest+" else ""
        val link = "https://api.github.com/search/repositories?q=${searchText}topic:unciv-mod&sort:stars&per_page=$amountPerPage&page=$page"
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
            return json().fromJson(RepoSearch::class.java, inputStream.bufferedReader().readText())
        }
        return null
    }

    /**
     * Parsed GitHub repo search response
     * @property total_count Total number of hits for the search (ignoring paging window)
     * @property incomplete_results A flag set by github to indicate search was incomplete (never seen it on)
     * @property items Array of [repositories][Repo]
     * @see <a href="https://docs.github.com/en/rest/reference/search#search-repositories--code-samples">Github API doc</a>
     */
    @Suppress("PropertyName")
    class RepoSearch {
        @Suppress("MemberVisibilityCanBePrivate")
        var total_count = 0
        var incomplete_results = false
        var items = ArrayList<Repo>()
    }

    /** Part of [RepoSearch] in Github API response - one repository entry in [items][RepoSearch.items] */
    @Suppress("PropertyName")
    class Repo {
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

        /**
         * Initialize `this` with an url, extracting all possible fields from it.
         *
         * Allows basic repo url or complete 'zip' url from github's code->download zip menu
         *
         * @return `this` to allow chaining
         */
        fun parseUrl(url: String): Repo {
            // Allow url formats
            //  https://github.com/author/repoName
            // or
            //  https://github.com/author/repoName/archive/refs/heads/branchName.zip
            // and extract author, repoName, branchName

            html_url = url
            default_branch = "master"
            val matchZip = Regex("""^(.*/(.*)/(.*))/archive/(?:.*/)?([^.]+).zip$""").matchEntire(url)
            if (matchZip != null && matchZip.groups.size > 3) {
                html_url = matchZip.groups[1]!!.value
                owner.login = matchZip.groups[2]!!.value
                name = matchZip.groups[3]!!.value
                default_branch = matchZip.groups[4]!!.value
            } else {
                val matchRepo = Regex("""^.*/(.*)/(.*)/?$""").matchEntire(url)
                if (matchRepo != null && matchRepo.groups.size > 2) {
                    owner.login = matchRepo.groups[1]!!.value
                    name = matchRepo.groups[2]!!.value
                }
            }
            return this
        }
    }

    /** Part of [Repo] in Github API response */
    @Suppress("PropertyName")
    class RepoOwner {
        var login = ""
        var avatar_url: String? = null
    }

    /** Rewrite modOptions file for a mod we just installed to include metadata we got from the GitHub api
     *
     *  (called on background thread)
     */
    fun rewriteModOptions(repo: Repo, modFolder: FileHandle) {
        val modOptionsFile = modFolder.child("jsons/ModOptions.json")
        val modOptions = if (modOptionsFile.exists()) json().fromJsonFile(ModOptions::class.java, modOptionsFile) else ModOptions()
        modOptions.modUrl = repo.html_url
        modOptions.lastUpdated = repo.pushed_at
        modOptions.author = repo.owner.login
        modOptions.modSize = repo.size
        modOptions.topics = repo.topics
        modOptions.updateDeprecations()
        json().toJson(modOptions, modOptionsFile)
    }
}

/** Utility - extract Zip archives
 * @see  [Zip.extractFolder]
 */
object Zip {
    private const val bufferSize = 2048

    /**
     * Extract one Zip file recursively (nested Zip files are extracted in turn).
     *
     * The source Zip is not deleted, but successfully extracted nested ones are.
     *
     * **Warning**: Extracting into a non-empty destination folder will merge contents. Existing
     * files also included in the archive will be partially overwritten, when the new data is shorter
     * than the old you will get _mixed contents!_
     *
     * @param zipFile The Zip file to extract
     * @param unzipDestination The folder to extract into, preferably empty (not enforced).
     */
    fun extractFolder(zipFile: FileHandle, unzipDestination: FileHandle) {
        // I went through a lot of similar answers that didn't work until I got to this gem by NeilMonday
        //  (with mild changes to fit the FileHandles)
        // https://stackoverflow.com/questions/981578/how-to-unzip-files-recursively-in-java

        debug("Extracting %s to %s", zipFile, unzipDestination)
        // establish buffer for writing file
        val data = ByteArray(bufferSize)

        fun streamCopy(fromStream: InputStream, toHandle: FileHandle) {
            val inputStream = BufferedInputStream(fromStream)
            var currentByte: Int

            // write the current file to disk
            val fos = FileOutputStream(toHandle.file())
            val dest = BufferedOutputStream(fos, bufferSize)

            // read and write until last byte is encountered
            while (inputStream.read(data, 0, bufferSize).also { currentByte = it } != -1) {
                dest.write(data, 0, currentByte)
            }
            dest.flush()
            dest.close()
            inputStream.close()
        }

        val file = zipFile.file()
        val zip = ZipFile(file)
        //unzipDestination.mkdirs()
        val zipFileEntries = zip.entries()

        // Process each entry
        while (zipFileEntries.hasMoreElements()) {
            // grab a zip file entry
            val entry = zipFileEntries.nextElement() as ZipEntry
            val currentEntry = entry.name
            val destFile = unzipDestination.child(currentEntry)
            val destinationParent = destFile.parent()

            // create the parent directory structure if needed
            destinationParent.mkdirs()
            if (!entry.isDirectory) {
                streamCopy ( zip.getInputStream(entry), destFile)
            }
            // The new file has a current last modification time
            // and not the  one stored in the archive - we could:
            //  'destFile.file().setLastModified(entry.time)'
            // but later handling will throw these away anyway,
            // and GitHub sets all timestamps to the download time.

            if (currentEntry.endsWith(".zip")) {
                // found a zip file, try to open
                extractFolder(destFile, destinationParent)
                destFile.delete()
            }
        }
        zip.close() // Needed so we can delete the zip file later
    }
}
