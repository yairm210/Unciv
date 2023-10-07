package com.unciv.logic.multiplayer.storage

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.multiplayer.ApiVersion
import com.unciv.logic.multiplayer.ServerFeatureSet
import com.unciv.utils.Log
import java.io.FileNotFoundException

/**
 * Allows access to games stored on a server for multiplayer purposes.
 * Defaults to using [UncivGame.Current.settings.multiplayerServer] if `fileStorageIdentifier` is not given.
 *
 * For low-level access only, use [UncivGame.onlineMultiplayer] on [UncivGame.Current] if you're looking to load/save a game.
 *
 * @param fileStorageIdentifier is a server base URL and must be given if UncivGame.Current might not be initialized
 * @see FileStorage
 * @see UncivGame.Current.settings.multiplayerServer
 */
@Suppress("RedundantSuspendModifier") // Methods can take a long time, so force users to use them in a coroutine to not get ANRs on Android
class OnlineMultiplayerServer(
    fileStorageIdentifier: String? = null,
    private var authenticationHeader: Map<String, String>? = null
) {
    internal var featureSet = ServerFeatureSet()
    val serverUrl = fileStorageIdentifier ?: UncivGame.Current.settings.multiplayer.server

    fun fileStorage(): FileStorage {
        val authHeader = if (authenticationHeader == null) {
            val settings = UncivGame.Current.settings.multiplayer
            mapOf("Authorization" to settings.getAuthHeader())
        } else authenticationHeader

        return if (serverUrl == Constants.dropboxMultiplayerServer) {
            DropBox
        } else {
            if (!UncivGame.Current.onlineMultiplayer.isInitialized()) {
                Log.debug("Uninitialized online multiplayer instance might result in errors later")
            }
            if (UncivGame.Current.onlineMultiplayer.apiVersion == ApiVersion.APIv2) {
                if (!UncivGame.Current.onlineMultiplayer.hasAuthentication() && !UncivGame.Current.onlineMultiplayer.api.isAuthenticated()) {
                    Log.error("User credentials not available, further execution may result in errors!")
                }
                return ApiV2FileStorage
            }
            UncivServerFileStorage.apply {
                serverUrl = this@OnlineMultiplayerServer.serverUrl
                this.authHeader = authHeader
            }
        }
    }

    /**
     * Checks if the server is alive and sets the [serverFeatureSet] accordingly.
     * @return true if the server is alive, false otherwise
     */
    fun checkServerStatus(): Boolean {
        var statusOk = false
        SimpleHttp.sendGetRequest("${serverUrl}/isalive") { success, result, _ ->
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
    suspend fun authenticate(password: String?): Boolean {
        if (featureSet.authVersion == 0) return true

        val settings = UncivGame.Current.settings.multiplayer

        val success = fileStorage().authenticate(
            userId=settings.userId,
            password=password ?: settings.passwords[settings.server] ?: ""
        )
        if (password != null && success) {
            settings.passwords[settings.server] = password
        }
        return success
    }

    /**
     * @return true if setting the password was successful, false otherwise.
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerAuthException if the authentication failed
     */
    suspend fun setPassword(password: String): Boolean {
        if (featureSet.authVersion > 0 && fileStorage().setPassword(newPassword = password)) {
            val settings = UncivGame.Current.settings.multiplayer
            settings.passwords[settings.server] = password
            return true
        }

        return false
    }


    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerAuthException if the authentication failed
     */
    suspend fun tryUploadGame(gameInfo: GameInfo, withPreview: Boolean) {
        // For APIv2 games, the JSON data needs to be valid JSON instead of minimal
        val zippedGameInfo = if (UncivGame.Current.onlineMultiplayer.apiVersion == ApiVersion.APIv2) {
            UncivFiles.gameInfoToPrettyString(gameInfo, useZip = true)
        } else {
            UncivFiles.gameInfoToString(gameInfo, forceZip = true, updateChecksum = true)
        }
        fileStorage().saveGameData(gameInfo.gameId, zippedGameInfo)

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
     * @see tryUploadGame
     * @see GameInfo.asPreview
     */
    suspend fun tryUploadGamePreview(gameInfo: GameInfoPreview) {
        val zippedGameInfo = UncivFiles.gameInfoToString(gameInfo)
        fileStorage().savePreviewData(gameInfo.gameId, zippedGameInfo)
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun tryDownloadGame(gameId: String): GameInfo {
        val zippedGameInfo = fileStorage().loadGameData(gameId)
        val gameInfo = UncivFiles.gameInfoFromString(zippedGameInfo)
        gameInfo.gameParameters.multiplayerServerUrl = UncivGame.Current.settings.multiplayer.server
        return gameInfo
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun tryDownloadGamePreview(gameId: String): GameInfoPreview {
        val zippedGameInfo = fileStorage().loadPreviewData(gameId)
        return UncivFiles.gameInfoPreviewFromString(zippedGameInfo)
    }
}
