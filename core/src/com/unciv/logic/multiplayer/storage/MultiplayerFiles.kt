package com.unciv.logic.multiplayer.storage

import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.BackwardCompatibility.migrateGameStatus
import com.unciv.logic.GameInfo
import com.unciv.logic.UncivFiles
import com.unciv.logic.multiplayer.Multiplayer.GameStatus
import com.unciv.logic.multiplayer.Multiplayer.ServerData

/**
 * Allows access to games stored on a server for multiplayer purposes.
 *
 * This is for low-level access only: use [UncivGame.multiplayer] on [UncivGame.Current] instead if you're looking to load/save a game.
 *
 * @see FileStorage
 */
@Suppress("RedundantSuspendModifier") // Methods can take a long time, so force users to use them in a coroutine to not get ANRs on Android
class MultiplayerFiles {
    fun fileStorage(serverData: ServerData): FileStorage {
        return if (serverData.url != null) {
            UncivServerFileStorage(serverData.url)
        } else {
            DropBox
        }
    }

    /** @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time */
    suspend fun tryUploadGame(serverData: ServerData, gameInfo: GameInfo) {
        val zippedGameInfo = UncivFiles.gameInfoToString(gameInfo, forceZip = true)
        fileStorage(serverData).saveFileData(gameInfo.gameId, zippedGameInfo, true)

        // We upload the multiplayer game info after the full game info because otherwise the following race condition will happen:
        // Current player ends turn -> Uploads multiplayer game info
        // Other player checks for updates -> Downloads multiplayer game info
        // Current player starts full game info upload
        // Other player sees update in preview -> Downloads game, gets old state
        // Current player finishes uploading game
        tryUploadGameStatus(serverData, gameInfo.gameId, GameStatus(gameInfo))
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
    suspend fun tryUploadGameStatus(serverData: ServerData, gameId: String, status: GameStatus) {
        val zippedGameInfo = json().toJson(status) // no gzip because gzip actually has more overhead than it saves in compression
        fileStorage(serverData).saveFileData("${gameId}_Preview", zippedGameInfo, true)
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun tryDownloadGame(serverData: ServerData, gameId: String): GameInfo {
        val zippedGameInfo = fileStorage(serverData).loadFileData(gameId)
        return UncivFiles.gameInfoFromString(zippedGameInfo)
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun tryDownloadGameStatus(serverData: ServerData, gameId: String): GameStatus {
        val status = fileStorage(serverData).loadFileData("${gameId}_Preview")
        val migrated = migrateGameStatus(status)
        return json().fromJson(GameStatus::class.java, migrated)
    }
}
