package com.unciv.ui.worldscreen.mainmenu

import com.badlogic.gdx.files.FileHandle
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.ui.saves.Gzip
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile


object DropBox {

    fun dropboxApi(url: String, data: String = "", contentType: String = "", dropboxApiArg: String = ""): InputStream? {

        with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = "POST"  // default is GET

            @Suppress("SpellCheckingInspection")
            setRequestProperty("Authorization", "Bearer LTdBbopPUQ0AAAAAAAACxh4_Qd1eVMM7IBK3ULV3BgxzWZDMfhmgFbuUNF_rXQWb")

            if (dropboxApiArg != "") setRequestProperty("Dropbox-API-Arg", dropboxApiArg)
            if (contentType != "") setRequestProperty("Content-Type", contentType)

            doOutput = true

            try {
                if (data != "") {
                    // StandardCharsets.UTF_8 requires API 19
                    val postData: ByteArray = data.toByteArray(Charset.forName("UTF-8"))
                    val outputStream = DataOutputStream(outputStream)
                    outputStream.write(postData)
                    outputStream.flush()
                }

                return inputStream
            } catch (ex: Exception) {
                println(ex.message)
                val reader = BufferedReader(InputStreamReader(errorStream))
                println(reader.readText())
                return null
            } catch (error: Error) {
                println(error.message)
                val reader = BufferedReader(InputStreamReader(errorStream))
                println(reader.readText())
                return null
            }
        }
    }

    fun getFolderList(folder: String): ArrayList<FolderListEntry> {
        val folderList = ArrayList<FolderListEntry>()
        // The DropBox API returns only partial file listings from one request. list_folder and
        // list_folder/continue return similar responses, but list_folder/continue requires a cursor
        // instead of the path.
        val response = dropboxApi("https://api.dropboxapi.com/2/files/list_folder",
                "{\"path\":\"$folder\"}", "application/json")
        var currentFolderListChunk = GameSaver.json().fromJson(FolderList::class.java, response)
        folderList.addAll(currentFolderListChunk.entries)
        while (currentFolderListChunk.has_more) {
            val continuationResponse = dropboxApi("https://api.dropboxapi.com/2/files/list_folder/continue",
                    "{\"cursor\":\"${currentFolderListChunk.cursor}\"}", "application/json")
            currentFolderListChunk = GameSaver.json().fromJson(FolderList::class.java, continuationResponse)
            folderList.addAll(currentFolderListChunk.entries)
        }
        return folderList
    }

    fun downloadFile(fileName: String): InputStream {
        val response = dropboxApi("https://content.dropboxapi.com/2/files/download",
                contentType = "text/plain", dropboxApiArg = "{\"path\":\"$fileName\"}")
        return response!!
    }

    fun downloadFileAsString(fileName: String): String {
        val inputStream = downloadFile(fileName)
        return BufferedReader(InputStreamReader(inputStream)).readText()
    }

    fun uploadFile(fileName: String, data: String, overwrite: Boolean = false){
        val overwriteModeString = if(!overwrite) "" else ""","mode":{".tag":"overwrite"}"""
        dropboxApi("https://content.dropboxapi.com/2/files/upload",
                data, "application/octet-stream", """{"path":"$fileName"$overwriteModeString}""")
    }

    fun deleteFile(fileName: String){
        dropboxApi("https://api.dropboxapi.com/2/files/delete_v2",
                "{\"path\":\"$fileName\"}", "application/json")
    }
//
//    fun createTemplate(): String {
//        val result =  dropboxApi("https://api.dropboxapi.com/2/file_properties/templates/add_for_user",
//                "{\"name\": \"Security\",\"description\": \"These properties describe how confidential this file or folder is.\",\"fields\": [{\"name\": \"Security Policy\",\"description\": \"This is the security policy of the file or folder described.\nPolicies can be Confidential, Public or Internal.\",\"type\": \"string\"}]}"
//                ,"application/json")
//        return BufferedReader(InputStreamReader(result)).readText()
//    }

    @Suppress("PropertyName")
    class FolderList{
        var entries = ArrayList<FolderListEntry>()
        var cursor = ""
        var has_more = false
    }

    @Suppress("PropertyName")
    class FolderListEntry{
        var name=""
        var path_display=""
    }

}

class OnlineMultiplayer {
    fun getGameLocation(gameId: String) = "/MultiplayerGames/$gameId"

    fun tryUploadGame(gameInfo: GameInfo){
        val zippedGameInfo = Gzip.zip(GameSaver.json().toJson(gameInfo))
        DropBox.uploadFile(getGameLocation(gameInfo.gameId), zippedGameInfo, true)
    }

    fun tryDownloadGame(gameId: String): GameInfo {
        val zippedGameInfo = DropBox.downloadFileAsString(getGameLocation(gameId))
        return GameSaver.gameInfoFromString(Gzip.unzip(zippedGameInfo))
    }

    /**
     * WARNING!
     * Does not initialize transitive GameInfo data.
     * It is therefore stateless and save to call for Multiplayer Turn Notifier, unlike tryDownloadGame().
     */
    fun tryDownloadGameUninitialized(gameId: String): GameInfo {
        val zippedGameInfo = DropBox.downloadFileAsString(getGameLocation(gameId))
        return GameSaver.gameInfoFromStringWithoutTransients(Gzip.unzip(zippedGameInfo))
    }
}

object Github {
    // Consider merging this with the Dropbox function
    fun download(url: String, action: (HttpURLConnection) -> Unit = {}): InputStream? {
        with(URL(url).openConnection() as HttpURLConnection)
        {
            action(this)

            return try {
                inputStream
            } catch (ex: Exception) {
                println(ex.message)
                val reader = BufferedReader(InputStreamReader(errorStream))
                println(reader.readText())
                null
            }
        }
    }

    /**
     * Download a mod and extract, deleting any pre-existing version.
     * @param gitRepoUrl Url of the repository as delivered by the Github search query
     * @param defaultBranch Branch name as delivered by the Github search query
     * @param folderFileHandle Destination handle of mods folder - also controls Android internal/external
     * @param atlasErrorAction A callback - if specified, the mod zip will be checked whether its
     *              atlas is current, and if not, this will be invoked. Should the callback return
     *              false, the extraction will be cancelled and the download discarded.
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

        // Download to temporary zip
        val tempZipFileHandle = folderFileHandle.child("tempZip.zip")
        tempZipFileHandle.write(inputStream, false)

        // prepare temp unpacking folder
        val unzipDestination = tempZipFileHandle.sibling("tempZip") // folder, not file
        // prevent mixing new content with old - hopefully there will never be cadavers of our tempZip stuff
        if (unzipDestination.exists())
            if (unzipDestination.isDirectory) unzipDestination.deleteDirectory() else unzipDestination.delete()
        
        Zip.extractFolder(tempZipFileHandle, unzipDestination)

        val innerFolder = unzipDestination.list().first()
        // innerFolder should now be "tempZip/$repoName-$defaultBranch/" - use this to get mod name
        val finalDestinationName = innerFolder.name().replace("-$defaultBranch", "").replace('-', ' ')
        // finalDestinationName is now the mod name as we display it. Folder name needs to be identical.
        val finalDestination = folderFileHandle.child(finalDestinationName)

        // prevent mixing new content with old
        var tempBackup: FileHandle? = null
        if (finalDestination.exists()) {
            tempBackup = finalDestination.sibling("$finalDestinationName.updating")
            finalDestination.moveTo(tempBackup)
        }

        // Move temp unpacked content to their final place
        finalDestination.mkdirs() // If we don't create this as a directory, it will think this is a file and nothing will work.
        // The move will reset the last modified time (recursively, at least on Linux)
        // This sort will guarantee the desktop launcher will not re-pack textures and overwrite the atlas as delivered by the mod
        for (innerFileOrFolder in innerFolder.list()
                .sortedBy { file -> file.extension() == "atlas" } ) {
            innerFileOrFolder.moveTo(finalDestination)
        }

        // clean up
        tempZipFileHandle.delete()
        unzipDestination.deleteDirectory()
        if (tempBackup != null)
            if (tempBackup.isDirectory) tempBackup.deleteDirectory() else tempBackup.delete()

        return finalDestination
    }

    /**
     * Implements the ability wo work with GitHub's rate limit, recognize blocks from previous attempts, wait and retry.
     */
    object RateLimit {
        // https://docs.github.com/en/rest/reference/search#rate-limit
        const val maxRequestsPerInterval = 10
        const val intervalInMilliSeconds = 60000L
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
                println("GitHub API Limit reported via http ($limit) not equal assumed value ($maxRequestsPerInterval)")
            account = maxRequestsPerInterval - remaining
            if (reset == 0L) return
            firstRequest = (reset + 1L) * 1000L - intervalInMilliSeconds
        }
    }

    /**
     * Query GitHub for repositories marked "unciv-mod"
     * @param amountPerPage Number of hits to return for this request
     * @param page          The "page" number, starting at 1.
     * @see <a href="https://docs.github.com/en/rest/reference/search#search-repositories">Github API doc</a>
     */
    fun tryGetGithubReposWithTopic(amountPerPage:Int, page:Int): RepoSearch? {
        val link = "https://api.github.com/search/repositories?q=topic:unciv-mod&sort:stars&per_page=$amountPerPage&page=$page"
        var retries = 2
        while (retries > 0) {
            retries--
            // obey rate limit
            if (RateLimit.waitForLimit()) return null
            // try download
            val inputStream = download(link) {
                if (it.responseCode == 403 || it.responseCode == 200 && page == 1 && retries == 1) {
                    RateLimit.notifyHttpResponse(it)
                    retries++
                }
            } ?: continue
            return GameSaver.json().fromJson(RepoSearch::class.java, inputStream.bufferedReader().readText())
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
        var updated_at = ""
        //var pushed_at = ""                // if > updated_at might indicate an update soon?
        var size = 0
        //var stargazers_url = ""
        //var homepage: String? = null      // might use instead of go to repo?
        //var has_wiki = false              // a wiki could mean proper documentation for the mod?
    }

    /** Part of [Repo] in Github API response */
    @Suppress("PropertyName")
    class RepoOwner {
        var login = ""
        var avatar_url: String? = null
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

        println("Extracting $zipFile to $unzipDestination")
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
