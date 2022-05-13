package com.unciv.logic.multiplayer.storage

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.GameSaver
import java.util.*


/**
 * Allows access to games stored on a server for multiplayer purposes.
 * Defaults to using UncivGame.Current.settings.multiplayerServer if fileStorageIdentifier is not given.
 *
 * @param fileStorageIdentifier must be given if UncivGame.Current might not be initialized
 * @see FileStorage
 * @see UncivGame.Current.settings.multiplayerServer
 */
class OnlineMultiplayerGameSaver(
    private var fileStorageIdentifier: String? = null
) {
    fun fileStorage(): FileStorage {
        val identifier = if (fileStorageIdentifier == null) UncivGame.Current.settings.multiplayerServer else fileStorageIdentifier

        return if (identifier == Constants.dropboxMultiplayerServer) DropBox else UncivServerFileStorage(identifier!!)
    }

    fun tryUploadGame(gameInfo: GameInfo, withPreview: Boolean) {
        // We upload the gamePreview before we upload the game as this
        // seems to be necessary for the kick functionality
        if (withPreview) {
            tryUploadGamePreview(gameInfo.asPreview())
        }

        val zippedGameInfo = GameSaver.gameInfoToString(gameInfo, forceZip = true)
        fileStorage().saveFileData(gameInfo.gameId, zippedGameInfo, true)
    }

    /**
     * Used to upload only the preview of a game. If the preview is uploaded together with (before/after)
     * the gameInfo, it is recommended to use tryUploadGame(gameInfo, withPreview = true)
     * @see tryUploadGame
     * @see GameInfo.asPreview
     */
    fun tryUploadGamePreview(gameInfo: GameInfoPreview) {
        val zippedGameInfo = GameSaver.gameInfoToString(gameInfo)
        fileStorage().saveFileData("${gameInfo.gameId}_Preview", zippedGameInfo, true)
    }

    fun tryDownloadGame(gameId: String): GameInfo {
        val zippedGameInfo = fileStorage.loadFileData(gameId)
        return GameSaver.gameInfoFromString(zippedGameInfo)
    }

    fun tryDownloadGamePreview(gameId: String): GameInfoPreview {
        val zippedGameInfo = fileStorage().loadFileData("${gameId}_Preview")
        return GameSaver.gameInfoPreviewFromString(zippedGameInfo)
    }
}