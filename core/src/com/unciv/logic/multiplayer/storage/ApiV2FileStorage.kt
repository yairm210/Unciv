package com.unciv.logic.multiplayer.storage

import com.unciv.logic.files.UncivFiles
import com.unciv.logic.multiplayer.ApiVersion
import com.unciv.logic.multiplayer.OnlineMultiplayer
import com.unciv.logic.multiplayer.apiv2.ApiV2
import com.unciv.logic.multiplayer.storage.ApiV2FileStorage.api
import com.unciv.logic.multiplayer.storage.ApiV2FileStorage.setApi
import com.unciv.logic.multiplayer.storage.ApiV2FileStorage.unsetApi
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import java.util.UUID

private const val PREVIEW_SUFFIX = "_Preview"

/**
 * Transition helper that emulates file storage behavior using the [ApiVersion.APIv2]
 *
 * This storage implementation requires the initialization of its [api] instance,
 * which must be provided by [setApi] before its first usage. If the [OnlineMultiplayer]
 * is disposed, this object will be cleaned up as well via [unsetApi]. Afterwards, using
 * this object to access only storage will **not** be possible anymore (NullPointerException).
 * You need to call [setApi] again, which is automatically done when [OnlineMultiplayer] is
 * initialized and [ApiVersion.APIv2] was detected. Take counter-measures to avoid
 * race conditions of releasing [OnlineMultiplayer] and using this object concurrently.
 */
object ApiV2FileStorage : FileStorage {
    private var api: ApiV2? = null

    internal fun setApi(api: ApiV2) {
        this.api = api
    }

    internal fun unsetApi() {
        api = null
    }

    override suspend fun saveGameData(gameId: String, data: String) {
        val uuid = UUID.fromString(gameId.lowercase())
        api!!.game.upload(uuid, data)
    }

    override suspend fun savePreviewData(gameId: String, data: String) {
        // Not implemented for this API
        Log.debug("Call to deprecated API 'savePreviewData'")
    }

    override suspend fun loadGameData(gameId: String): String {
        val uuid = UUID.fromString(gameId.lowercase())
        return api!!.game.get(uuid, cache = false)!!.gameData
    }

    override suspend fun loadPreviewData(gameId: String): String {
        // Not implemented for this API
        Log.debug("Call to deprecated API 'loadPreviewData'")
        // TODO: This could be improved, since this consumes more resources than necessary
        return UncivFiles.gameInfoToString(UncivFiles.gameInfoFromString(loadGameData(gameId)).asPreview())
    }

    fun loadFileData(fileName: String): String {
        return Concurrency.runBlocking {
            if (fileName.endsWith(PREVIEW_SUFFIX)) {
                loadPreviewData(fileName.dropLast(8))
            } else {
                loadGameData(fileName)
            }
        }!!
    }

    override suspend fun getFileMetaData(fileName: String): FileMetaData {
        TODO("Not yet implemented")
    }

    fun deleteFile(fileName: String) {
        return Concurrency.runBlocking {
            if (fileName.endsWith(PREVIEW_SUFFIX)) {
                deletePreviewData(fileName.dropLast(8))
            } else {
                deleteGameData(fileName)
            }
        }!!
    }

    override suspend fun deleteGameData(gameId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun deletePreviewData(gameId: String) {
        // Not implemented for this API
        Log.debug("Call to deprecated API 'deletedPreviewData'")
        deleteGameData(gameId)
    }

    override fun authenticate(userId: String, password: String): Boolean {
        return Concurrency.runBlocking { api!!.auth.loginOnly(userId, password) }!!
    }

    override fun setPassword(newPassword: String): Boolean {
        return Concurrency.runBlocking { api!!.account.setPassword(newPassword, suppress = true) }!!
    }

}
