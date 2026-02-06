package com.unciv.logic.multiplayer.storage

import com.unciv.Constants
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.multiplayer.ServerFeatureSet
import java.util.Date

class MultiplayerServer(
    val fileStorageIdentifier: String? = null,
    private var authenticationHeader: Map<String, String>? = null,
) {
    private var featureSet = ServerFeatureSet()

    fun getFeatureSet() = featureSet
    fun setFeatureSet(value: ServerFeatureSet) {
        featureSet = value
    }

    fun getServerUrl() = fileStorageIdentifier ?: Constants.uncivXyzServer

    fun fileStorage(): FileStorage = DisabledFileStorage

    suspend fun checkServerStatus(): Boolean = false

    fun authenticate(password: String?): Boolean = false

    fun setPassword(password: String): Boolean = false

    suspend fun uploadGame(gameInfo: GameInfo, withPreview: Boolean) {
        throw MultiplayerAuthException(UnsupportedOperationException("Online multiplayer is disabled on web phase-1"))
    }

    suspend fun tryUploadGamePreview(gameInfo: GameInfoPreview) {
        throw MultiplayerAuthException(UnsupportedOperationException("Online multiplayer is disabled on web phase-1"))
    }

    suspend fun tryDownloadGame(gameId: String): GameInfo {
        throw MultiplayerFileNotFoundException(UnsupportedOperationException("Online multiplayer is disabled on web phase-1"))
    }

    suspend fun downloadGame(gameId: String): GameInfo = tryDownloadGame(gameId)

    suspend fun tryDownloadGamePreview(gameId: String): GameInfoPreview {
        throw MultiplayerFileNotFoundException(UnsupportedOperationException("Online multiplayer is disabled on web phase-1"))
    }

    private object DisabledFileStorage : FileStorage {
        override fun saveFileData(fileName: String, data: String) {
            throw MultiplayerAuthException(UnsupportedOperationException("Online multiplayer is disabled on web phase-1"))
        }

        override fun loadFileData(fileName: String): String {
            throw MultiplayerFileNotFoundException(UnsupportedOperationException("Online multiplayer is disabled on web phase-1"))
        }

        override fun getFileMetaData(fileName: String): FileMetaData = object : FileMetaData {
            override fun getLastModified(): Date? = null
        }

        override fun deleteFile(fileName: String) {
            throw MultiplayerAuthException(UnsupportedOperationException("Online multiplayer is disabled on web phase-1"))
        }

        override fun authenticate(userId: String, password: String): Boolean = false

        override fun setPassword(newPassword: String): Boolean = false

        override fun checkAuthStatus(userId: String, password: String): AuthStatus = AuthStatus.UNKNOWN
    }
}
