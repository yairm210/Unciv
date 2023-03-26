package com.unciv.logic.multiplayer.storage

import com.unciv.logic.files.UncivFiles
import com.unciv.logic.multiplayer.apiv2.ApiV2Wrapper
import com.unciv.utils.Log
import java.util.*

/**
 * Transition helper that emulates file storage behavior using the API v2
 */
class ApiV2FileStorageEmulator(private val api: ApiV2Wrapper): FileStorage {

    override suspend fun saveGameData(gameId: String, data: String) {
        val uuid = UUID.fromString(gameId.lowercase())
        api.game.upload(uuid, data)
    }

    override suspend fun savePreviewData(gameId: String, data: String) {
        // Not implemented for this API
        Log.debug("Call to deprecated API 'savePreviewData'")
    }

    override suspend fun loadGameData(gameId: String): String {
        val uuid = UUID.fromString(gameId.lowercase())
        return api.game.get(uuid).gameData
    }

    override suspend fun loadPreviewData(gameId: String): String {
        // Not implemented for this API
        Log.debug("Call to deprecated API 'loadPreviewData'")
        // TODO: This could be improved, since this consumes more resources than necessary
        return UncivFiles.gameInfoToString(UncivFiles.gameInfoFromString(loadGameData(gameId)).asPreview())
    }

    override suspend fun getFileMetaData(fileName: String): FileMetaData {
        TODO("Not implemented for this API")
    }

    override suspend fun deleteGameData(gameId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun deletePreviewData(gameId: String) {
        // Not implemented for this API
        Log.debug("Call to deprecated API 'deletedPreviewData'")
        deleteGameData(gameId)
    }

    override suspend fun authenticate(userId: String, password: String): Boolean {
        return api.auth.loginOnly(userId, password)
    }

    override suspend fun setPassword(newPassword: String): Boolean {
        api.account.setPassword("", newPassword)
        // TODO: Not yet implemented
        return false
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
    var api: ApiV2Wrapper? = null
    var storage: ApiV2FileStorageEmulator? = null
}
