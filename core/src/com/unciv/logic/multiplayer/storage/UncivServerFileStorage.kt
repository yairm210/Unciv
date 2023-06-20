package com.unciv.logic.multiplayer.storage

import com.badlogic.gdx.Net
import com.badlogic.gdx.utils.Base64Coder
import com.unciv.utils.debug
import kotlin.Exception

object UncivServerFileStorage : FileStorage {
    var authHeader: Map<String, String>? = null
    var serverUrl: String = ""
    var timeout: Int = 30000

    override suspend fun saveGameData(gameId: String, data: String) {
        SimpleHttp.sendRequest(Net.HttpMethods.PUT, fileUrl(gameId), content=data, timeout=timeout, header=authHeader) {
                success, result, code ->
            if (!success) {
                debug("Error from UncivServer during save: %s", result)
                when (code) {
                    401 -> throw MultiplayerAuthException(Exception(result))
                    else -> throw Exception(result)
                }
            }
        }
    }

    override suspend fun savePreviewData(gameId: String, data: String) {
        return saveGameData(gameId + PREVIEW_FILE_SUFFIX, data)
    }

    override suspend fun loadGameData(gameId: String): String {
        var fileData = ""
        SimpleHttp.sendGetRequest(fileUrl(gameId), timeout=timeout, header=authHeader) {
                success, result, code ->
            if (!success) {
                debug("Error from UncivServer during load: %s", result)
                when (code) {
                    404 -> throw MultiplayerFileNotFoundException(Exception(result))
                    else -> throw Exception(result)
                }

            }
            else fileData = result
        }
        return fileData
    }

    override suspend fun loadPreviewData(gameId: String): String {
        return loadGameData(gameId + PREVIEW_FILE_SUFFIX)
    }

    override suspend fun getFileMetaData(fileName: String): FileMetaData {
        TODO("Not yet implemented")
    }

    override suspend fun deleteGameData(gameId: String) {
        SimpleHttp.sendRequest(Net.HttpMethods.DELETE, fileUrl(gameId), content="", timeout=timeout, header=authHeader) {
                success, result, code ->
            if (!success) {
                when (code) {
                    404 -> throw MultiplayerFileNotFoundException(Exception(result))
                    else -> throw Exception(result)
                }
            }
        }
    }

    override suspend fun deletePreviewData(gameId: String) {
        return deleteGameData(gameId + PREVIEW_FILE_SUFFIX)
    }

    override fun authenticate(userId: String, password: String): Boolean {
        var authenticated = false
        val preEncodedAuthValue = "$userId:$password"
        authHeader = mapOf("Authorization" to "Basic ${Base64Coder.encodeString(preEncodedAuthValue)}")
        SimpleHttp.sendGetRequest("$serverUrl/auth", timeout=timeout, header=authHeader) {
                success, result, code ->
            if (!success) {
                debug("Error from UncivServer during authentication: %s", result)
                authHeader = null
                when (code) {
                    401 -> throw MultiplayerAuthException(Exception(result))
                    else -> throw Exception(result)
                }
            } else {
                authenticated = true
            }
        }
        return authenticated
    }

    override fun setPassword(newPassword: String): Boolean {
        if (authHeader == null)
            return false

        var setSuccessful = false
        SimpleHttp.sendRequest(Net.HttpMethods.PUT, "$serverUrl/auth", content=newPassword, timeout=timeout, header=authHeader) {
                success, result, code ->
            if (!success) {
                debug("Error from UncivServer during password set: %s", result)
                when (code) {
                    401 -> throw MultiplayerAuthException(Exception(result))
                    else -> throw Exception(result)
                }
            } else {
                setSuccessful = true
            }
        }
        return setSuccessful
    }

    private fun fileUrl(fileName: String) = "$serverUrl/files/$fileName"
}
