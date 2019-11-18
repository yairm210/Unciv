package com.unciv.ui.worldscreen.optionstable

import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.ui.saves.Gzip
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class DropBox {

    fun dropboxApi(url:String, data:String="",contentType:String="",dropboxApiArg:String=""): InputStream? {

        with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = "POST"  // default is GET

            setRequestProperty("Authorization", "Bearer LTdBbopPUQ0AAAAAAAACxh4_Qd1eVMM7IBK3ULV3BgxzWZDMfhmgFbuUNF_rXQWb")

            if (dropboxApiArg != "") setRequestProperty("Dropbox-API-Arg", dropboxApiArg)
            if (contentType != "") setRequestProperty("Content-Type", contentType)

            doOutput = true

            try {
                if (data != "") {
                    val postData: ByteArray = data.toByteArray(StandardCharsets.UTF_8)
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

    fun getFolderList(folder:String):FolderList{
        val response = dropboxApi("https://api.dropboxapi.com/2/files/list_folder",
                "{\"path\":\"$folder\"}","application/json")
        return GameSaver().json().fromJson(FolderList::class.java,response)
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


    class FolderList{
        var entries = ArrayList<FolderListEntry>()
    }

    class FolderListEntry{
        var name=""
        var path_display=""
    }

}

class OnlineMultiplayer {
    fun getGameLocation(gameId:String) = "/MultiplayerGames/$gameId"

    fun tryUploadGame(gameInfo: GameInfo){
        val zippedGameInfo = Gzip.zip(GameSaver().json().toJson(gameInfo))
        DropBox().uploadFile(getGameLocation(gameInfo.gameId),zippedGameInfo,true)
    }

    fun tryDownloadGame(gameId: String): GameInfo {
        val zippedGameInfo = DropBox().downloadFileAsString(getGameLocation(gameId))
        return GameSaver().gameInfoFromString(Gzip.unzip(zippedGameInfo))
    }
}