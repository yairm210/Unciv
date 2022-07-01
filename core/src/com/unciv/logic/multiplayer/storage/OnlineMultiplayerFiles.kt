package com.unciv.logic.multiplayer.storage

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
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
    suspend fun tryUploadGame(gameInfo: GameInfo, withPreview: Boolean) {
        val zippedGameInfo = UncivFiles.gameInfoToString(gameInfo, forceZip = true)
        fileStorage().saveFileData(gameInfo.gameId, zippedGameInfo, true)

        // We upload the preview after the game because otherwise the following race condition will happen:
        // Current player ends turn -> Uploads Game Preview
        // Other player checks for updates -> Downloads Game Preview
        // Current player starts game upload
        // Other player sees update in preview -> Downloads game, gets old state
        // Current player finishes uploading game
        if (withPreview) {
            tryUploadGamePreview(gameInfo.asPreview())
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
     * @see GameInfo.asPreview
     */
    suspend fun tryUploadGamePreview(gameInfo: GameInfoPreview) {
        val zippedGameInfo = UncivFiles.gameInfoToString(gameInfo)
        fileStorage().saveFileData("${gameInfo.gameId}_Preview", zippedGameInfo, true)
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
    suspend fun tryDownloadGamePreview(gameId: String): GameInfoPreview {
        val zippedGameInfo = fileStorage().loadFileData("${gameId}_Preview")
        return UncivFiles.gameInfoPreviewFromString(zippedGameInfo)
    }
}
