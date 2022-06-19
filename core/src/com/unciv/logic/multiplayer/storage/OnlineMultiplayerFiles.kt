package com.unciv.logic.multiplayer.storage

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.multiplayer.MultiplayerGameStatus
import com.unciv.logic.UncivFiles

/**
 * Allows access to games stored on a server for multiplayer purposes.
 * Defaults to using UncivGame.Current.settings.multiplayerServer if fileStorageIdentifier is not given.
 *
 * For low-level access only, use [UncivGame.onlineMultiplayer] on [UncivGame.Current] if you're looking to load/save a game.
 *
 * @param fileStorageIdentifier must be given if UncivGame.Current might not be initialized
 * @see FileStorage
 * @see UncivGame.Current.settings.multiplayerServer
 */
@Suppress("RedundantSuspendModifier") // Methods can take a long time, so force users to use them in a coroutine to not get ANRs on Android
class OnlineMultiplayerFiles(
    private var fileStorageIdentifier: String? = null
) {
    fun fileStorage(): FileStorage {
        val identifier = if (fileStorageIdentifier == null) UncivGame.Current.settings.multiplayer.server else fileStorageIdentifier

        return if (identifier == Constants.dropboxMultiplayerServer) DropBox else UncivServerFileStorage(identifier!!)
    }

    /** @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time */
    suspend fun tryUploadGame(gameInfo: GameInfo, withGameStatus: Boolean) {
        val zippedGameInfo = UncivFiles.gameInfoToString(gameInfo, forceZip = true)
        fileStorage().saveFileData(gameInfo.gameId, zippedGameInfo, true)

        // We upload the multiplayer game info after the full game info because otherwise the following race condition will happen:
        // Current player ends turn -> Uploads multiplayer game info
        // Other player checks for updates -> Downloads multiplayer game info
        // Current player starts full game info upload
        // Other player sees update in preview -> Downloads game, gets old state
        // Current player finishes uploading game
        if (withGameStatus) {
            tryUploadGameStatus(MultiplayerGameStatus(gameInfo))
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    /**
     * Used to upload only the preview of a game. If the preview is uploaded together with (before/after)
     * the gameInfo, it is recommended to use tryUploadGame(gameInfo, withPreview = true)
     *
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     *
     * @see tryUploadGame
     */
    suspend fun tryUploadGameStatus(status: MultiplayerGameStatus) {
        val zippedGameInfo = UncivFiles.multiplayerGameStatusToString(status)
        fileStorage().saveFileData("${status.gameId}_Preview", zippedGameInfo, true)
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun tryDownloadGame(gameId: String): GameInfo {
        val zippedGameInfo = fileStorage().loadFileData(gameId)
        return UncivFiles.gameInfoFromString(zippedGameInfo)
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun tryDownloadGameStatus(gameId: String): MultiplayerGameStatus {
        val zippedGameInfo = fileStorage().loadFileData("${gameId}_Preview")
        return UncivFiles.multiplayerGameStatusFromString(zippedGameInfo)
    }
}
