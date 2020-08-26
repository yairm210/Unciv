package com.unciv.ui.worldscreen.mainmenu

import com.badlogic.gdx.files.FileHandle
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.metadata.GameSettings
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
        val text = BufferedReader(InputStreamReader(inputStream)).readText()
        return text
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


    class FolderList{
        var entries = ArrayList<FolderListEntry>()
        var cursor = ""
        var has_more = false
    }

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
     * Returns current turn's player.
     * Does not initialize transitive GameInfo data.
     * It is therefore stateless and save to call for Multiplayer Turn Notifier, unlike tryDownloadGame().
     */
    fun tryDownloadCurrentTurnCiv(gameId: String): CivilizationInfo {
        val zippedGameInfo = DropBox.downloadFileAsString(getGameLocation(gameId))
        return GameSaver.currentTurnCivFromString(Gzip.unzip(zippedGameInfo))
    }
}

object Github {
    // Consider merging this with the Dropbox function
    fun download(url: String, action: (HttpURLConnection) -> Unit = {}): InputStream? {
        with(URL(url).openConnection() as HttpURLConnection)
        {
            action(this)

            doOutput = true

            try {
                return inputStream
            } catch (ex: Exception) {
                println(ex.message)
                val reader = BufferedReader(InputStreamReader(errorStream))
                println(reader.readText())
                return null
            }
        }
    }

    // This took a long time to get just right, so if you're changing this, TEST IT THOROUGHLY on both Desktop and Phone
    fun downloadAndExtract(url:String, folderFileHandle:FileHandle) {
        val inputStream = download(url)
        if (inputStream == null) return

        val tempZipFileHandle = folderFileHandle.child("tempZip.zip")
        tempZipFileHandle.write(inputStream, false)
        val unzipDestination = tempZipFileHandle.sibling("tempZip") // folder, not file
        Zip.extractFolder(tempZipFileHandle, unzipDestination)
        val innerFolder = unzipDestination.list().first() // tempZip/<repoName>-master/

        val finalDestinationName = innerFolder.name().replace("-master","").replace('-',' ')
        val finalDestination = folderFileHandle.child(finalDestinationName)
        finalDestination.mkdirs() // If we don't create this as a directory, it will think this is a file and nothing will work.
        for(innerFileOrFolder in innerFolder.list()){
            innerFileOrFolder.moveTo(finalDestination)
        }

        tempZipFileHandle.delete()
        unzipDestination.deleteDirectory()
    }


    fun tryGetGithubReposWithTopic(): ArrayList<Repo> {
        val inputStream = download("https://api.github.com/search/repositories?q=topic:unciv-mod")
        if (inputStream == null) return ArrayList()
        return GameSaver.json().fromJson(RepoSearch::class.java, inputStream!!.bufferedReader().readText()).items
    }

    class RepoSearch{
        var items=ArrayList<Repo>()
    }

    class Repo {
        var name = ""
        var description = ""
        var svn_url = ""
        var stargazers_count = 0
    }
}

object Zip {

    // I went through a lot of similar answers that didn't work until I got to this gem by NeilMonday
    //  (with mild changes to fit the FileHandles)
    // https://stackoverflow.com/questions/981578/how-to-unzip-files-recursively-in-java
    fun extractFolder(zipFile: FileHandle, unzipDestination: FileHandle) {
        println(zipFile)
        val BUFFER = 2048
        val file = zipFile.file()
        val zip = ZipFile(file)
        unzipDestination.mkdirs()
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
                val inputStream = BufferedInputStream(zip
                        .getInputStream(entry))
                var currentByte: Int
                // establish buffer for writing file
                val data = ByteArray(BUFFER)

                // write the current file to disk
                val fos = FileOutputStream(destFile.file())
                val dest = BufferedOutputStream(fos,
                        BUFFER)

                // read and write until last byte is encountered
                while (inputStream.read(data, 0, BUFFER).also { currentByte = it } != -1) {
                    dest.write(data, 0, currentByte)
                }
                dest.flush()
                dest.close()
                inputStream.close()
            }
            if (currentEntry.endsWith(".zip")) {
                // found a zip file, try to open
                extractFolder(destFile, unzipDestination)
            }
        }
        zip.close() // Needed so we can delete the zip file later
    }
}