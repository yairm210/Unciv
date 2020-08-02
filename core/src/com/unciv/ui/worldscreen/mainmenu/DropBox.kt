package com.unciv.ui.worldscreen.mainmenu

import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.saves.Gzip
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

object DropBox {

    fun dropboxApi(url:String, data:String="",contentType:String="",dropboxApiArg:String=""): InputStream? {

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

    fun getFolderList(folder:String): ArrayList<FolderListEntry> {
        val folderList = ArrayList<FolderListEntry>()
        // The DropBox API returns only partial file listings from one request. list_folder and
        // list_folder/continue return similar responses, but list_folder/continue requires a cursor
        // instead of the path.
        val response = dropboxApi("https://api.dropboxapi.com/2/files/list_folder",
                "{\"path\":\"$folder\"}","application/json")
        var currentFolderListChunk = GameSaver.json().fromJson(FolderList::class.java, response)
        folderList.addAll(currentFolderListChunk.entries)
        while (currentFolderListChunk.has_more) {
            val continuationResponse = dropboxApi("https://api.dropboxapi.com/2/files/list_folder/continue",
                    "{\"cursor\":\"${currentFolderListChunk.cursor}\"}","application/json")
            currentFolderListChunk = GameSaver.json().fromJson(FolderList::class.java, continuationResponse)
            folderList.addAll(currentFolderListChunk.entries)
        }
        return folderList
    }

    fun downloadFile(fileName:String): InputStream {
        val response = dropboxApi("https://content.dropboxapi.com/2/files/download",
                contentType = "text/plain",dropboxApiArg = "{\"path\":\"$fileName\"}")
        return response!!
    }

    fun downloadFileAsString(fileName:String): String {
        val inputStream = downloadFile(fileName)
        val text = BufferedReader(InputStreamReader(inputStream)).readText()
        return text
    }

    fun uploadFile(fileName: String, data: String, overwrite:Boolean=false){
        val overwriteModeString = if(!overwrite) "" else ""","mode":{".tag":"overwrite"}"""
        dropboxApi("https://content.dropboxapi.com/2/files/upload",
                data,"application/octet-stream", """{"path":"$fileName"$overwriteModeString}""")
    }

    fun deleteFile(fileName:String){
        dropboxApi("https://api.dropboxapi.com/2/files/delete_v2",
                "{\"path\":\"$fileName\"}","application/json")
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
    fun getGameLocation(gameId:String) = "/MultiplayerGames/$gameId"

    fun tryUploadGame(gameInfo: GameInfo){
        val zippedGameInfo = Gzip.zip(GameSaver.json().toJson(gameInfo))
        DropBox.uploadFile(getGameLocation(gameInfo.gameId),zippedGameInfo,true)
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
