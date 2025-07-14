package com.unciv.logic.multiplayer.storage

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.multiplayer.ServerFeatureSet
import com.unciv.logic.multiplayer.chat.ChatWebSocket

/**
 * Allows access to games stored on a server for multiplayer purposes.
 * Defaults to using UncivGame.Current.settings.multiplayerServer if fileStorageIdentifier is not given.
 *
 * For low-level access only, use [UncivGame.onlineMultiplayer] on [UncivGame.Current] if you're looking to load/save a game.
 *
 * @param fileStorageIdentifier must be given if UncivGame.Current might not be initialized
 * @see FileStorage
 * @see UncivGame.settings.multiplayer.server
 */
@Suppress("RedundantSuspendModifier") // Methods can take a long time, so force users to use them in a coroutine to not get ANRs on Android
class MultiplayerServer(
    val fileStorageIdentifier: String? = null,
    private var authenticationHeader: Map<String, String>? = null
) {
    internal var featureSet = ServerFeatureSet()
        set(value) {
            if (field != value) {
                UncivGame.Current.worldScreen?.chatButton?.refreshVisibility()
            }

            field = value
        }

    fun getServerUrl() = fileStorageIdentifier ?: UncivGame.Current.settings.multiplayer.server

    fun fileStorage(): FileStorage {
        val authHeader = if (authenticationHeader == null) {
            val settings = UncivGame.Current.settings.multiplayer
            mapOf("Authorization" to settings.getAuthHeader())
        } else authenticationHeader

        return if (getServerUrl() == Constants.dropboxMultiplayerServer) DropBox
        else UncivServerFileStorage.apply {
            serverUrl = this@MultiplayerServer.getServerUrl()
            this.authHeader = authHeader
        }
    }

    /**
     * Checks if the server is alive and sets the [serverFeatureSet] accordingly.
     * @return true if the server is alive, false otherwise
     */
    fun checkServerStatus(): Boolean {
        var statusOk = false
        SimpleHttp.sendGetRequest("${getServerUrl()}/isalive") { success, result, _ ->
            statusOk = success
            if (result.isNotEmpty()) {
                featureSet = try {
                    json().fromJson(ServerFeatureSet::class.java, result)
                } catch (_: Exception) {
                    // The server does not support server feature set - not an error!
                    ServerFeatureSet()
                }
            }
        }
        return statusOk
    }


    /**
     * @return true if the authentication was successful or the server does not support authentication.
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerAuthException if the authentication failed
     */
    fun authenticate(password: String?): Boolean {
        if (featureSet.authVersion == 0) return true

        val settings = UncivGame.Current.settings.multiplayer

        val success = fileStorage().authenticate(
            userId = settings.userId, password = password ?: settings.getCurrentServerPassword() ?: ""
        )
        if (password != null && success) {
            settings.setCurrentServerPassword(password)
        }
        return success
    }

    /**
     * @return true if setting the password was successful, false otherwise.
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerAuthException if the authentication failed
     */
    fun setPassword(password: String): Boolean {
        if (featureSet.authVersion > 0 && fileStorage().setPassword(newPassword = password)) {
            UncivGame.Current.settings.multiplayer.setCurrentServerPassword(password)
            return true
        }

        return false
    }


    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerAuthException if the authentication failed
     */
    suspend fun uploadGame(gameInfo: GameInfo, withPreview: Boolean) {
        val zippedGameInfo = UncivFiles.gameInfoToString(gameInfo, forceZip = true, updateChecksum = true)
        fileStorage().saveFileData(gameInfo.gameId, zippedGameInfo)

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
     * @throws MultiplayerAuthException if the authentication failed
     *
     * @see uploadGame
     * @see GameInfo.asPreview
     */
    suspend fun tryUploadGamePreview(gameInfo: GameInfoPreview) {
        val zippedGameInfo = UncivFiles.gameInfoToString(gameInfo)
        fileStorage().saveFileData("${gameInfo.gameId}_Preview", zippedGameInfo)
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun tryDownloadGame(gameId: String): GameInfo {
        val zippedGameInfo = fileStorage().loadFileData(gameId)
        val gameInfo = UncivFiles.gameInfoFromString(zippedGameInfo)
        gameInfo.gameParameters.multiplayerServerUrl = UncivGame.Current.settings.multiplayer.server
        return gameInfo
    }


    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun downloadGame(gameId: String): GameInfo {
        val latestGame = tryDownloadGame(gameId)
        latestGame.isUpToDate = true
        return latestGame
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
