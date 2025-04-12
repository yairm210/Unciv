package com.unciv.logic.multiplayer.storage

import com.badlogic.gdx.Net
import com.badlogic.gdx.utils.Base64Coder
import com.unciv.utils.debug
import kotlin.Exception

object UncivServerFileStorage : FileStorage {
    var authHeader: Map<String, String>? = null
    var serverUrl: String = ""
    var timeout: Int = 30000

    override fun saveFileData(fileName: String, data: String) {
        SimpleHttp.sendRequest(Net.HttpMethods.PUT, fileUrl(fileName), content=data, timeout=timeout, header=authHeader) {
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

    override fun loadFileData(fileName: String): String {
        var fileData = ""
        SimpleHttp.sendGetRequest(fileUrl(fileName), timeout=timeout, header=authHeader) {
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

    override fun getFileMetaData(fileName: String): FileMetaData {
        TODO("Not yet implemented")
    }

    override fun deleteFile(fileName: String) {
        SimpleHttp.sendRequest(Net.HttpMethods.DELETE, fileUrl(fileName), content="", timeout=timeout, header=authHeader) {
                success, result, code ->
            if (!success) {
                when (code) {
                    404 -> throw MultiplayerFileNotFoundException(Exception(result))
                    else -> throw Exception(result)
                }
            }
        }
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

    override fun checkAuthStatus(userId: String, password: String): AuthStatus {
        var authStatus = AuthStatus.UNKNOWN
        val preEncodedAuthValue = "$userId:$password"
        authHeader = mapOf("Authorization" to "Basic ${Base64Coder.encodeString(preEncodedAuthValue)}")
        SimpleHttp.sendGetRequest("$serverUrl/auth", timeout = timeout, header = authHeader) { success, result, code ->
            if (success) {
                authStatus = if (result.lowercase().contains("unregistered")) {
                    AuthStatus.UNREGISTERED
                } else {
                    AuthStatus.VERIFIED
                }
            } else if (code == 401) {
                authStatus = AuthStatus.UNAUTHORIZED
            }
        }
        return authStatus
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
