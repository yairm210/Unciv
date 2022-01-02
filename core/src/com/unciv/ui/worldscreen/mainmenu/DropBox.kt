package com.unciv.ui.worldscreen.mainmenu

import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.GameSaver
import com.unciv.ui.saves.Gzip
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.pow


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
                val responseString = reader.readText()
                println(responseString)

                // Throw Exceptions based on the HTTP response from dropbox
                if (responseString.contains("path/not_found/"))
                    throw FileNotFoundException()
                if (responseString.contains("path/conflict/file"))
                    throw DropBoxFileConflictException()
                
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

    /**
     * @param overwrite set to true to avoid DropBoxFileConflictException
     * @throws DropBoxFileConflictException when overwrite is false and a file with the
     * same name already exists
     */
    fun uploadFile(fileName: String, data: String, overwrite: Boolean = false) {
        val overwriteModeString = if(!overwrite) "" else ""","mode":{".tag":"overwrite"}"""
        dropboxApi("https://content.dropboxapi.com/2/files/upload",
                data, "application/octet-stream", """{"path":"$fileName"$overwriteModeString}""")
    }

    fun deleteFile(fileName: String){
        dropboxApi("https://api.dropboxapi.com/2/files/delete_v2",
                "{\"path\":\"$fileName\"}", "application/json")
    }

    fun fileExists(fileName: String): Boolean {
        try {
            dropboxApi("https://api.dropboxapi.com/2/files/get_metadata",
                    "{\"path\":\"$fileName\"}", "application/json")
            return true
        } catch (ex: FileNotFoundException) {
            return false
        }

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

    fun tryUploadGame(gameInfo: GameInfo, withPreview: Boolean) {
        // We upload the gamePreview before we upload the game as this
        // seems to be necessary for the kick functionality
        if (withPreview) {
            tryUploadGamePreview(gameInfo.asPreview())
        }

        val zippedGameInfo = Gzip.zip(GameSaver.json().toJson(gameInfo))
        DropBox.uploadFile(getGameLocation(gameInfo.gameId), zippedGameInfo, true)
    }

    /**
     * Used to upload only the preview of a game. If the preview is uploaded together with (before/after)
     * the gameInfo, it is recommended to use tryUploadGame(gameInfo, withPreview = true)
     * @see tryUploadGame
     * @see GameInfo.asPreview
     */
    fun tryUploadGamePreview(gameInfo: GameInfoPreview) {
        val zippedGameInfo = Gzip.zip(GameSaver.json().toJson(gameInfo))
        DropBox.uploadFile("${getGameLocation(gameInfo.gameId)}_Preview", zippedGameInfo, true)
    }

    fun tryDownloadGame(gameId: String): GameInfo {
        val zippedGameInfo = DropBox.downloadFileAsString(getGameLocation(gameId))
        return GameSaver.gameInfoFromString(Gzip.unzip(zippedGameInfo))
    }

    fun tryDownloadGamePreview(gameId: String): GameInfoPreview {
        val zippedGameInfo = DropBox.downloadFileAsString("${getGameLocation(gameId)}_Preview")
        return GameSaver.gameInfoPreviewFromString(Gzip.unzip(zippedGameInfo))
    }
}

/**
 * Used to communicate data access between players
 */
class LockFile {
    // The lockData is necessary to make every LockFile unique
    // If Dropbox gets a file with the same content and overwrite set to false, it returns no
    // error even though the file was not uploaded as the exact file is already existing
    var lockData = UUID.randomUUID().toString()
}

/**
 *	Wrapper around OnlineMultiplayer's synchronization facilities.
 *
 *	Based on the design of Mutex from kotlinx.coroutines.sync, except that when it blocks,
 *	  it blocks the entire thread for an increasing period of time via Thread.sleep()
 */
class ServerMutex(val gameInfo: GameInfoPreview) {
    private var locked = false

    constructor(gameInfo: GameInfo) : this(gameInfo.asPreview()) { }

    /**
     * Try to obtain the server lock ONCE
     * DO NOT forget to unlock it when you're done with it!
     * Sleep between successive attempts
     * @see lock
     * @see unlock
     * @return true if lock is acquired
     */
    fun tryLock(): Boolean {
        // If we already hold the lock, return without doing anything
        if (locked) {
            return locked
        }
        
        locked = false
        val fileName = "${OnlineMultiplayer().getGameLocation(gameInfo.gameId)}_Lock"

        // We have to check if the lock file already exists before we try to upload a new
        // lock file to not overuse the dropbox file upload limit else it will return an error
        if (DropBox.fileExists(fileName)) {
            return locked
        }

        try {
            DropBox.uploadFile(fileName, Gzip.zip(GameSaver.json().toJson(LockFile())))
        } catch (ex: DropBoxFileConflictException) {
            return locked
        }

        locked = true
        return locked
    }

    /**
     * Block until this client owns the lock
     *
     * TODO: Create an alternative to the underlying tryLock or tryLockGame which checks for
     *       (and returns, when present) the value of the Retry-After header
     *
     * @see tryLock
     * @see unlock
     */
    fun lock() {
        var tries = 0

        // Try the lockfile once
        locked = tryLock()
        while (!locked) {
            // Wait exponentially longer after each attempt as per DropBox API recommendations
            var delay = 250 * 2.0.pow(tries).toLong()

            // 8 seconds is a really long time to sleep a thread, it's as good a cap as any
            if (delay > 8000) {
                delay = 8000
            }

            // Consider NOT sleeping here, instead perhaps delay or spin+yield
            Thread.sleep(delay)

            tries++

            // Retry the lock
            locked = tryLock()
        }
    }

    /**
     * Release a server lock acquired by tryLock or lock
     * @see tryLock
     * @see lock
     */
    fun unlock() {
        if (!locked)
            return

        DropBox.deleteFile("${OnlineMultiplayer().getGameLocation(gameInfo.gameId)}_Lock")
        locked = false
    }

    /**
     * See whether the client currently holds this lock
     * @return true if lock is active
     */
    fun holdsLock() = locked
}

class DropBoxFileConflictException: Exception()
