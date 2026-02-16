package com.unciv.logic.multiplayer.storage

import com.unciv.json.json
import com.unciv.logic.web.WebHttp
import java.io.InputStream
import java.util.Date

/**
 * Web-specific Dropbox implementation.
 *
 * The JVM HttpURLConnection path in core is not browser-safe, and the generic Gdx.net
 * binary path may produce unusable audio payloads on some browser backends.
 * This implementation uses WebHttp ArrayBuffer bytes to preserve binary fidelity.
 */
object DropBox : FileStorage {
    private const val bearerToken = "LTdBbopPUQ0AAAAAAAACxh4_Qd1eVMM7IBK3ULV3BgxzWZDMfhmgFbuUNF_rXQWb"
    private const val downloadUrl = "https://content.dropboxapi.com/2/files/download"

    override fun deleteFile(fileName: String) {
        throw NotImplementedError("Dropbox file API is not used on web.")
    }

    override fun getFileMetaData(fileName: String): FileMetaData = object : FileMetaData {
        override fun getLastModified(): Date? = null
    }

    override fun saveFileData(fileName: String, data: String) {
        throw NotImplementedError("Dropbox file API is not used on web.")
    }

    override fun loadFileData(fileName: String): String {
        throw NotImplementedError("Dropbox file API is not used on web.")
    }

    override fun authenticate(userId: String, password: String): Boolean = false

    override fun checkAuthStatus(userId: String, password: String): AuthStatus = AuthStatus.UNKNOWN

    override fun setPassword(newPassword: String): Boolean = false

    fun downloadFile(fileName: String): InputStream {
        throw UnsupportedOperationException("Use downloadFileBytesAsync on web.")
    }

    suspend fun downloadFileBytesAsync(fileName: String): ByteArray {
        val response = WebHttp.requestBytes(
            method = "POST",
            url = downloadUrl,
            headers = mapOf(
                "Authorization" to "Bearer $bearerToken",
                "Dropbox-API-Arg" to "{\"path\":\"$fileName\"}",
                "Content-Type" to "text/plain",
            ),
        )
        if (response.ok && response.bytes != null) return response.bytes

        val responseText = response.bytes?.toString(Charsets.UTF_8).orEmpty()
        val error = runCatching { json().fromJson(ErrorResponse::class.java, responseText) }.getOrNull()
        when {
            error?.error_summary?.startsWith("too_many_requests/") == true ->
                throw FileStorageRateLimitReached(error.error?.retry_after?.toIntOrNull() ?: 300)
            error?.error_summary?.startsWith("path/not_found/") == true ->
                throw MultiplayerFileNotFoundException(IllegalStateException(responseText))
            error?.error_summary?.startsWith("path/conflict/file") == true ->
                throw FileStorageConflictException()
            else -> throw IllegalStateException("Dropbox download failed with HTTP ${response.status}")
        }
    }

    @Suppress("PropertyName")
    private class ErrorResponse {
        var error_summary = ""
        var error: Details? = null

        class Details {
            var retry_after = ""
        }
    }
}
