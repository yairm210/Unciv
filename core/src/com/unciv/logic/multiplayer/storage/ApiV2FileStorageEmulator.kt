package com.unciv.logic.multiplayer.storage

import com.unciv.logic.files.UncivFiles
import com.unciv.logic.multiplayer.apiv2.ApiV2
import com.unciv.utils.Log
import kotlinx.coroutines.runBlocking
import java.util.UUID

private const val PREVIEW_SUFFIX = "_Preview"

/**
 * Transition helper that emulates file storage behavior using the API v2
 */
class ApiV2FileStorageEmulator(private val api: ApiV2) : FileStorage {

    private suspend fun saveGameData(gameId: String, data: String) {
        val uuid = UUID.fromString(gameId.lowercase())
        api.game.upload(uuid, data)
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun savePreviewData(gameId: String, data: String) {
        // Not implemented for this API
        Log.debug("Call to deprecated API 'savePreviewData'")
    }

    override fun saveFileData(fileName: String, data: String) {
        return runBlocking {
            if (fileName.endsWith(PREVIEW_SUFFIX)) {
                savePreviewData(fileName.dropLast(8), data)
            } else {
                saveGameData(fileName, data)
            }
        }
    }

    private suspend fun loadGameData(gameId: String): String {
        val uuid = UUID.fromString(gameId.lowercase())
        return api.game.get(uuid, cache = false)!!.gameData
    }

    private suspend fun loadPreviewData(gameId: String): String {
        // Not implemented for this API
        Log.debug("Call to deprecated API 'loadPreviewData'")
        // TODO: This could be improved, since this consumes more resources than necessary
        return UncivFiles.gameInfoToString(UncivFiles.gameInfoFromString(loadGameData(gameId)).asPreview())
    }

    override fun loadFileData(fileName: String): String {
        return runBlocking {
            if (fileName.endsWith(PREVIEW_SUFFIX)) {
                loadPreviewData(fileName.dropLast(8))
            } else {
                loadGameData(fileName)
            }
        }
    }

    override fun getFileMetaData(fileName: String): FileMetaData {
        TODO("Not yet implemented")
    }

    override fun deleteFile(fileName: String) {
        return runBlocking {
            if (fileName.endsWith(PREVIEW_SUFFIX)) {
                deletePreviewData(fileName.dropLast(8))
            } else {
                deleteGameData(fileName)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun deleteGameData(gameId: String) {
        TODO("Not yet implemented")
    }

    private suspend fun deletePreviewData(gameId: String) {
        // Not implemented for this API
        Log.debug("Call to deprecated API 'deletedPreviewData'")
        deleteGameData(gameId)
    }

    override fun authenticate(userId: String, password: String): Boolean {
        return runBlocking { api.auth.loginOnly(userId, password) }
    }

    override fun checkAuthStatus(userId: String, password: String): AuthStatus {
        TODO("Not yet implemented")
    }

    override fun setPassword(newPassword: String): Boolean {
        return runBlocking { api.account.setPassword(newPassword, suppress = true) }
    }

}

/**
 * Workaround to "just get" the file storage handler and the API, but without initializing
 *
 * TODO: This wrapper should be replaced by better file storage initialization handling.
 *
 * This object keeps references which are populated during program startup at runtime.
 */
object ApiV2FileStorageWrapper {
    var api: ApiV2? = null
    var storage: ApiV2FileStorageEmulator? = null
}
