package com.unciv.logic.multiplayer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Net
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.GameSaver
import com.unciv.ui.saves.Gzip
import com.unciv.ui.worldscreen.mainmenu.OptionsPopup
import java.util.*

interface IFileStorage {
    fun saveFileData(fileName: String, data: String)
    fun loadFileData(fileName: String): String
    fun getFileMetaData(fileName: String): IFileMetaData
    fun deleteFile(fileName: String)
}

interface IFileMetaData {
    fun getLastModified(): Date?
}



class UncivServerFileStorage(val serverIp:String):IFileStorage {
    val serverUrl = "http://$serverIp:8080"
    override fun saveFileData(fileName: String, data: String) {
        OptionsPopup.SimpleHttp.sendRequest(Net.HttpMethods.PUT, "$serverUrl/files/$fileName", data){
            success: Boolean, result: String -> 
            if (!success) {
                println(result)
                throw java.lang.Exception(result)
            }
        }
    }

    override fun loadFileData(fileName: String): String {
        var fileData = ""
        OptionsPopup.SimpleHttp.sendGetRequest("$serverUrl/files/$fileName"){
                success: Boolean, result: String ->
            if (!success) {
                println(result)
                throw java.lang.Exception(result)
            }
            else fileData = result
        }
        return fileData
    }

    override fun getFileMetaData(fileName: String): IFileMetaData {
        TODO("Not yet implemented")
    }

    override fun deleteFile(fileName: String) {
        OptionsPopup.SimpleHttp.sendRequest(Net.HttpMethods.DELETE, "$serverUrl/files/$fileName", ""){
                success: Boolean, result: String ->
            if (!success) throw java.lang.Exception(result)
        }
    }

}

class FileStorageConflictException: Exception()

class OnlineMultiplayer {
    val fileStorage: IFileStorage
    init {
        val settings = UncivGame.Current.settings
        if (settings.multiplayerServer == Constants.dropboxMultiplayerServer)
            fileStorage = DropboxFileStorage()
        else fileStorage = UncivServerFileStorage(settings.multiplayerServer)
    }

    fun tryUploadGame(gameInfo: GameInfo, withPreview: Boolean) {
        // We upload the gamePreview before we upload the game as this
        // seems to be necessary for the kick functionality
        if (withPreview) {
            tryUploadGamePreview(gameInfo.asPreview())
        }

        val zippedGameInfo = Gzip.zip(GameSaver.json().toJson(gameInfo))
        fileStorage.saveFileData(gameInfo.gameId, zippedGameInfo)
    }

    /**
     * Used to upload only the preview of a game. If the preview is uploaded together with (before/after)
     * the gameInfo, it is recommended to use tryUploadGame(gameInfo, withPreview = true)
     * @see tryUploadGame
     * @see GameInfo.asPreview
     */
    fun tryUploadGamePreview(gameInfo: GameInfoPreview) {
        val zippedGameInfo = Gzip.zip(GameSaver.json().toJson(gameInfo))
        fileStorage.saveFileData("${gameInfo.gameId}_Preview", zippedGameInfo)
    }

    fun tryDownloadGame(gameId: String): GameInfo {
        val zippedGameInfo = fileStorage.loadFileData(gameId)
        return GameSaver.gameInfoFromString(Gzip.unzip(zippedGameInfo))
    }

    fun tryDownloadGamePreview(gameId: String): GameInfoPreview {
        val zippedGameInfo = fileStorage.loadFileData("${gameId}_Preview")
        return GameSaver.gameInfoPreviewFromString(Gzip.unzip(zippedGameInfo))
    }
}