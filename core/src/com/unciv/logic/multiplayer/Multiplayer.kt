package com.unciv.logic.multiplayer

import com.badlogic.gdx.Net
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.GameSaver
import java.util.*

interface IFileStorage {
    fun saveFileData(fileName: String, data: String, overwrite: Boolean)
    fun loadFileData(fileName: String): String
    fun getFileMetaData(fileName: String): IFileMetaData
    fun deleteFile(fileName: String)
}

interface IFileMetaData {
    fun getLastModified(): Date?
}



class UncivServerFileStorage(val serverUrl:String):IFileStorage {
    override fun saveFileData(fileName: String, data: String, overwrite: Boolean) {
        SimpleHttp.sendRequest(Net.HttpMethods.PUT, "$serverUrl/files/$fileName", data){
            success: Boolean, result: String ->
            if (!success) {
                println(result)
                throw java.lang.Exception(result)
            }
        }
    }

    override fun loadFileData(fileName: String): String {
        var fileData = ""
        SimpleHttp.sendGetRequest("$serverUrl/files/$fileName"){
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
        SimpleHttp.sendRequest(Net.HttpMethods.DELETE, "$serverUrl/files/$fileName", ""){
                success: Boolean, result: String ->
            if (!success) throw java.lang.Exception(result)
        }
    }

}

class FileStorageConflictException: Exception()
class FileStorageRateLimitReached(rateLimit: String): Exception(rateLimit)

/**
 * Allows access to games stored on a server for multiplayer purposes.
 * Defaults to using UncivGame.Current.settings.multiplayerServer if fileStorageIdentifier is not given.
 *
 * @param fileStorageIdentifier must be given if UncivGame.Current might not be initialized
 * @see IFileStorage
 * @see UncivGame.Current.settings.multiplayerServer
 */
class OnlineMultiplayer(var fileStorageIdentifier: String? = null) {
    val fileStorage: IFileStorage
    init {
        if (fileStorageIdentifier == null)
            fileStorageIdentifier = UncivGame.Current.settings.multiplayerServer
        fileStorage = if (fileStorageIdentifier == Constants.dropboxMultiplayerServer)
            DropBox
        else UncivServerFileStorage(fileStorageIdentifier!!)
    }

    fun tryUploadGame(gameInfo: GameInfo, withPreview: Boolean) {
        // We upload the gamePreview before we upload the game as this
        // seems to be necessary for the kick functionality
        if (withPreview) {
            tryUploadGamePreview(gameInfo.asPreview())
        }

        val zippedGameInfo = GameSaver.gameInfoToString(gameInfo, forceZip = true)
        fileStorage.saveFileData(gameInfo.gameId, zippedGameInfo, true)
    }

    /**
     * Used to upload only the preview of a game. If the preview is uploaded together with (before/after)
     * the gameInfo, it is recommended to use tryUploadGame(gameInfo, withPreview = true)
     * @see tryUploadGame
     * @see GameInfo.asPreview
     */
    fun tryUploadGamePreview(gameInfo: GameInfoPreview) {
        val zippedGameInfo = GameSaver.gameInfoToString(gameInfo)
        fileStorage.saveFileData("${gameInfo.gameId}_Preview", zippedGameInfo, true)
    }

    fun tryDownloadGame(gameId: String): GameInfo {
        val zippedGameInfo = fileStorage.loadFileData(gameId)
        return GameSaver.gameInfoFromString(zippedGameInfo)
    }

    fun tryDownloadGamePreview(gameId: String): GameInfoPreview {
        val zippedGameInfo = fileStorage.loadFileData("${gameId}_Preview")
        return GameSaver.gameInfoPreviewFromString(zippedGameInfo)
    }
}