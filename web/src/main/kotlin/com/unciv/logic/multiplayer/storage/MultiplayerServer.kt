package com.unciv.logic.multiplayer.storage

import com.badlogic.gdx.utils.Base64Coder
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.UncivShowableException
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.multiplayer.ServerFeatureSet
import com.unciv.logic.web.WebHttp
import com.unciv.logic.web.WebHttpResponse
import com.unciv.utils.Concurrency
import java.util.Date

class MultiplayerServer(
    val fileStorageIdentifier: String? = null,
    private var authenticationHeader: Map<String, String>? = null,
) : AsyncAuthProvider {
    private var featureSet = ServerFeatureSet()

    fun getFeatureSet() = featureSet

    fun setFeatureSet(value: ServerFeatureSet) {
        if (featureSet != value) {
            UncivGame.Current.worldScreen?.refreshChatButtonVisibility()
            featureSet = value
        }
    }

    fun getServerUrl() = fileStorageIdentifier ?: UncivGame.Current.settings.multiplayer.getServer()

    fun fileStorage(): FileStorage = DisabledFileStorage

    suspend fun checkServerStatus(): Boolean {
        val server = getServerUrl()
        val isAliveUrl = "$server/isalive"
        val resp = WebHttp.requestText("GET", isAliveUrl, headers = authHeaderFromSettings())
        if (!resp.ok) return false

        val parsed = try {
            json().fromJson(ServerFeatureSet::class.java, resp.text)
        } catch (_: Exception) {
            ServerFeatureSet()
        }
        setFeatureSet(parsed)

        val current = UncivGame.Current.settings.multiplayer
        if (current.getServer() == server) {
            val updatedUrl = resp.url.removeSuffix("/isalive")
            if (updatedUrl.isNotBlank()) current.setServer(updatedUrl)
        }
        return true
    }

    override fun authenticateAsync(password: String?, callback: (Result<Boolean>) -> Unit) {
        if (featureSet.authVersion == 0) {
            callback(Result.success(true))
            return
        }
        val settings = UncivGame.Current.settings.multiplayer
        val header = basicAuth(settings.getUserId(), password ?: settings.getCurrentServerPassword().orEmpty())
        val url = "${getServerUrl()}/auth"
        Concurrency.run("WebAuth") {
            val resp = WebHttp.requestText("GET", url, headers = header)
            val result = when (resp.status) {
                200, 204 -> {
                    if (password != null) settings.setCurrentServerPassword(password)
                    authenticationHeader = header
                    Result.success(true)
                }
                401 -> Result.failure(MultiplayerAuthException(UnsupportedOperationException(resp.text ?: "")))
                429 -> Result.failure(rateLimitError(resp))
                else -> Result.failure(UncivShowableException("Authentication failed: ${resp.status}"))
            }
            Concurrency.runOnGLThread { callback(result) }
        }
    }

    override fun setPasswordAsync(password: String, callback: (Result<Boolean>) -> Unit) {
        if (featureSet.authVersion == 0) {
            callback(Result.failure(UncivShowableException("This server does not support authentication")))
            return
        }
        val settings = UncivGame.Current.settings.multiplayer
        val header = authenticationHeader ?: authHeaderFromSettings()
        if (header.isEmpty()) {
            callback(Result.failure(MultiplayerAuthException(UnsupportedOperationException("Missing auth header"))))
            return
        }
        val url = "${getServerUrl()}/auth"
        Concurrency.run("WebAuthSetPassword") {
            val resp = WebHttp.requestText("PUT", url, headers = header, body = password)
            val result = when (resp.status) {
                200, 204 -> {
                    settings.setCurrentServerPassword(password)
                    Result.success(true)
                }
                401 -> Result.failure(MultiplayerAuthException(UnsupportedOperationException(resp.text ?: "")))
                429 -> Result.failure(rateLimitError(resp))
                else -> Result.failure(UncivShowableException("Failed to set password: ${resp.status}"))
            }
            Concurrency.runOnGLThread { callback(result) }
        }
    }

    override fun checkAuthStatusAsync(userId: String, password: String, callback: (AuthStatus) -> Unit) {
        val header = basicAuth(userId, password)
        val url = "${getServerUrl()}/auth"
        Concurrency.run("WebAuthStatus") {
            val resp = WebHttp.requestText("GET", url, headers = header)
            val status = when (resp.status) {
                200 -> AuthStatus.VERIFIED
                204 -> AuthStatus.UNREGISTERED
                401 -> AuthStatus.UNAUTHORIZED
                else -> AuthStatus.UNKNOWN
            }
            Concurrency.runOnGLThread { callback(status) }
        }
    }

    fun authenticate(password: String?): Boolean {
        throw MultiplayerAuthException(UnsupportedOperationException("Use authenticateAsync on web"))
    }

    fun setPassword(password: String): Boolean {
        throw MultiplayerAuthException(UnsupportedOperationException("Use setPasswordAsync on web"))
    }

    suspend fun uploadGame(gameInfo: GameInfo, withPreview: Boolean) {
        val zippedGameInfo = UncivFiles.gameInfoToString(gameInfo, forceZip = true, updateChecksum = true)
        saveFile(gameInfo.gameId, zippedGameInfo)
        if (withPreview) {
            tryUploadGamePreview(gameInfo.asPreview())
        }
    }

    suspend fun tryUploadGamePreview(gameInfo: GameInfoPreview) {
        val zippedGameInfo = UncivFiles.gameInfoToString(gameInfo)
        saveFile("${gameInfo.gameId}_Preview", zippedGameInfo)
    }

    suspend fun tryDownloadGame(gameId: String): GameInfo {
        val zippedGameInfo = loadFile(gameId)
        val gameInfo = UncivFiles.gameInfoFromString(zippedGameInfo)
        gameInfo.gameParameters.multiplayerServerUrl = UncivGame.Current.settings.multiplayer.getServer()
        return gameInfo
    }

    suspend fun downloadGame(gameId: String): GameInfo {
        val latest = tryDownloadGame(gameId)
        latest.isUpToDate = true
        return latest
    }

    suspend fun tryDownloadGamePreview(gameId: String): GameInfoPreview {
        val zippedGameInfo = loadFile("${gameId}_Preview")
        return UncivFiles.gameInfoPreviewFromString(zippedGameInfo)
    }

    private suspend fun saveFile(fileName: String, data: String) {
        val resp = WebHttp.requestText(
            "PUT",
            fileUrl(fileName),
            headers = authHeaderFromSettings(),
            body = data
        )
        handleStorageErrors(resp)
    }

    private suspend fun loadFile(fileName: String): String {
        val resp = WebHttp.requestText(
            "GET",
            fileUrl(fileName),
            headers = authHeaderFromSettings()
        )
        handleStorageErrors(resp, requireBody = true)
        return resp.text.orEmpty()
    }

    private fun fileUrl(fileName: String) = "${getServerUrl()}/files/$fileName"

    private fun authHeaderFromSettings(): Map<String, String> {
        if (authenticationHeader != null) return authenticationHeader!!
        val settings = UncivGame.Current.settings.multiplayer
        val authHeader = settings.getAuthHeader()
        return if (authHeader.isBlank()) emptyMap() else mapOf("Authorization" to authHeader)
    }

    private fun basicAuth(userId: String, password: String): Map<String, String> {
        val encoded = Base64Coder.encodeString("$userId:$password")
        return mapOf("Authorization" to "Basic $encoded")
    }

    private fun rateLimitError(resp: WebHttpResponse): FileStorageRateLimitReached {
        val retry = resp.headers["retry-after"]?.toIntOrNull()
        return FileStorageRateLimitReached(retry ?: 60)
    }

    private fun handleStorageErrors(resp: WebHttpResponse, requireBody: Boolean = false) {
        if (resp.ok && (!requireBody || !resp.text.isNullOrEmpty())) return
        when (resp.status) {
            401 -> throw MultiplayerAuthException(UnsupportedOperationException(resp.text ?: "Unauthorized"))
            404 -> throw MultiplayerFileNotFoundException(UnsupportedOperationException(resp.text ?: "Not found"))
            429 -> throw rateLimitError(resp)
        }
        throw UncivShowableException("Multiplayer storage error ${resp.status}: ${resp.text ?: ""}".trim())
    }

    private object DisabledFileStorage : FileStorage {
        override fun saveFileData(fileName: String, data: String) {
            throw MultiplayerAuthException(UnsupportedOperationException("Online multiplayer is disabled on web"))
        }

        override fun loadFileData(fileName: String): String {
            throw MultiplayerFileNotFoundException(UnsupportedOperationException("Online multiplayer is disabled on web"))
        }

        override fun getFileMetaData(fileName: String): FileMetaData = object : FileMetaData {
            override fun getLastModified(): Date? = null
        }

        override fun deleteFile(fileName: String) {
            throw MultiplayerAuthException(UnsupportedOperationException("Online multiplayer is disabled on web"))
        }

        override fun authenticate(userId: String, password: String): Boolean = false

        override fun setPassword(newPassword: String): Boolean = false

        override fun checkAuthStatus(userId: String, password: String): AuthStatus = AuthStatus.UNKNOWN
    }
}
