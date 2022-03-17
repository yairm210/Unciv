package com.unciv.logic.multiplayer

import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.GameSaver
import com.unciv.ui.saves.Gzip
import java.util.*

interface IFileStorage {
    fun saveFileData(fileName: String, data: String)
    fun loadFileData(fileName: String): String
    fun getFileMetaData(fileName: String): IFileMetaData
    fun deleteFile(fileName: String)
}

interface IFileMetaData {
    fun getFileName(): String?
    fun getLastModified(): Date?
}

class FileStorageConflictException: Exception()

class OnlineMultiplayer {
    val fileStorage: IFileStorage = DropboxFileStorage()

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