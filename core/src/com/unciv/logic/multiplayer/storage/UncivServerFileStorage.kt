package com.unciv.logic.multiplayer.storage

import com.badlogic.gdx.Net
import com.unciv.utils.debug
import kotlin.Exception

class UncivServerFileStorage(val serverUrl: String, val timeout: Int = 30000) : FileStorage {
    override suspend fun saveFileData(fileName: String, data: String, overwrite: Boolean) {
        val (success, error, code) = SimpleHttp.sendRequest(Net.HttpMethods.PUT, fileUrl(fileName), data, timeout)
        if (!success) {
            debug("Error from UncivServer during save: %s", error)
            throwException(error, code)
        }
    }

    override suspend fun loadFileData(fileName: String): String {
        val (success, body, code) = SimpleHttp.sendGetRequest(fileUrl(fileName), timeout = timeout)
        if (!success) {
            debug("Error from UncivServer during load: %s", body)
            throwException(body, code)
        }
        return body
    }

    override suspend fun getFileMetaData(fileName: String): FileMetaData {
        TODO("Not yet implemented")
    }

    override suspend fun deleteFile(fileName: String) {
        val (success, error, code) = SimpleHttp.sendRequest(Net.HttpMethods.DELETE, fileUrl(fileName), "", timeout)
        if (!success) {
            debug("Error from UncivServer during delete: %s", error)
            throwException(error, code)
        }
    }

    private fun throwException(errorMessage: String, code: Int?) {
        when (code) {
            404 -> throw MultiplayerFileNotFoundException(Exception(errorMessage))
            else -> throw Exception(errorMessage)
        }
    }

    private fun fileUrl(fileName: String) = SimpleHttp.buildURL(serverUrl, "files", fileName)
}
