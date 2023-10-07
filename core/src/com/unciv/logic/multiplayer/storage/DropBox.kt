package com.unciv.logic.multiplayer.storage

import com.unciv.json.json
import com.unciv.ui.components.extensions.UncivDateFormat.parseDate
import com.unciv.utils.Log
import com.unciv.utils.debug
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.Date
import java.util.Timer
import kotlin.concurrent.timer


object DropBox: FileStorage {
    private var remainingRateLimitSeconds = 0
    private var rateLimitTimer: Timer? = null

    private fun dropboxApi(url: String, data: String = "", contentType: String = "", dropboxApiArg: String = ""): InputStream? {

        if (remainingRateLimitSeconds > 0)
            throw FileStorageRateLimitReached(remainingRateLimitSeconds)

        with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = "POST"  // default is GET

            @Suppress("SpellCheckingInspection")
            setRequestProperty("Authorization", "Bearer LTdBbopPUQ0AAAAAAAACxh4_Qd1eVMM7IBK3ULV3BgxzWZDMfhmgFbuUNF_rXQWb")

            if (dropboxApiArg != "") setRequestProperty("Dropbox-API-Arg", dropboxApiArg)
            if (contentType != "") setRequestProperty("Content-Type", contentType)

            doOutput = true

            try {
                if (data != "") {
                    val postData: ByteArray = data.toByteArray(Charsets.UTF_8)
                    val outputStream = DataOutputStream(outputStream)
                    outputStream.write(postData)
                    outputStream.flush()
                }

                return inputStream
            } catch (ex: Exception) {
                debug("Dropbox exception", ex)
                val reader = BufferedReader(InputStreamReader(errorStream, Charsets.UTF_8))
                val responseString = reader.readText()
                debug("Response: %s", responseString)

                val error = json().fromJson(ErrorResponse::class.java, responseString)
                // Throw Exceptions based on the HTTP response from dropbox
                when {
                    error.error_summary.startsWith("too_many_requests/") -> triggerRateLimit(error)
                    error.error_summary.startsWith("path/not_found/") -> throw MultiplayerFileNotFoundException(ex)
                    error.error_summary.startsWith("path/conflict/file") -> throw FileStorageConflictException()
                }

                return null
            } catch (error: Error) {
                Log.error("Dropbox error", error)
                debug("Error stream: %s", { BufferedReader(InputStreamReader(errorStream, Charsets.UTF_8)).readText() })
                return null
            }
        }
    }

    // This is the location in Dropbox only
    private fun getLocalGameLocation(fileName: String) = "/MultiplayerGames/$fileName"

    override suspend fun deleteGameData(gameId: String){
        dropboxApi(
            url="https://api.dropboxapi.com/2/files/delete_v2",
            data="{\"path\":\"${getLocalGameLocation(gameId)}\"}",
            contentType="application/json"
        )
    }

    override suspend fun deletePreviewData(gameId: String){
        dropboxApi(
            url="https://api.dropboxapi.com/2/files/delete_v2",
            data="{\"path\":\"${getLocalGameLocation(gameId + PREVIEW_FILE_SUFFIX)}\"}",
            contentType="application/json"
        )
    }

    override suspend fun getFileMetaData(fileName: String): FileMetaData {
        val stream = dropboxApi(
            url="https://api.dropboxapi.com/2/files/get_metadata",
            data="{\"path\":\"${getLocalGameLocation(fileName)}\"}",
            contentType="application/json"
        )!!
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        return json().fromJson(MetaData::class.java, reader.readText())
    }

    override suspend fun saveGameData(gameId: String, data: String) {
        dropboxApi(
            url="https://content.dropboxapi.com/2/files/upload",
            data=data,
            contentType="application/octet-stream",
            dropboxApiArg = """{"path":"${getLocalGameLocation(gameId)}","mode":{".tag":"overwrite"}}"""
        )!!
    }

    override suspend fun savePreviewData(gameId: String, data: String) {
        dropboxApi(
            url="https://content.dropboxapi.com/2/files/upload",
            data=data,
            contentType="application/octet-stream",
            dropboxApiArg = """{"path":"${getLocalGameLocation(gameId + PREVIEW_FILE_SUFFIX)}","mode":{".tag":"overwrite"}}"""
        )
    }

    override suspend fun loadGameData(gameId: String): String {
        val inputStream = downloadFile(getLocalGameLocation(gameId))
        return BufferedReader(InputStreamReader(inputStream)).readText()
    }

    override suspend fun loadPreviewData(gameId: String): String {
        val inputStream = downloadFile(getLocalGameLocation(gameId + PREVIEW_FILE_SUFFIX))
        return BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
    }

    override fun authenticate(userId: String, password: String): Boolean {
        throw NotImplementedError()
    }

    override fun setPassword(newPassword: String): Boolean {
        throw NotImplementedError()
    }

    fun downloadFile(fileName: String): InputStream {
        val response = dropboxApi("https://content.dropboxapi.com/2/files/download",
                contentType = "text/plain", dropboxApiArg = "{\"path\":\"$fileName\"}")
        return response!!
    }

    /**
     * If the dropbox rate limit is reached for this bearer token we strictly have to wait for the
     * specified retry_after seconds before trying again. If non is supplied or can not be parsed
     * the default value of 5 minutes will be used.
     * Any attempt before the rate limit is dropped again will also contribute to the rate limit
     */
    private fun triggerRateLimit(response: ErrorResponse) {
        remainingRateLimitSeconds = response.error?.retry_after?.toIntOrNull() ?: 300

        rateLimitTimer = timer("RateLimitTimer", true, 0, 1000) {
            remainingRateLimitSeconds--
            if (remainingRateLimitSeconds == 0)
                rateLimitTimer?.cancel()
        }
        throw FileStorageRateLimitReached(remainingRateLimitSeconds)
    }

//     fun fileExists(fileName: String): Boolean = try {
//             dropboxApi("https://api.dropboxapi.com/2/files/get_metadata",
//                 "{\"path\":\"$fileName\"}", "application/json")
//             true
//         } catch (ex: MultiplayerFileNotFoundException) {
//             false
//         }

//
//    fun createTemplate(): String {
//        val result =  dropboxApi("https://api.dropboxapi.com/2/file_properties/templates/add_for_user",
//                "{\"name\": \"Security\",\"description\": \"These properties describe how confidential this file or folder is.\",\"fields\": [{\"name\": \"Security Policy\",\"description\": \"This is the security policy of the file or folder described.\nPolicies can be Confidential, Public or Internal.\",\"type\": \"string\"}]}"
//                ,"application/json")
//        return BufferedReader(InputStreamReader(result, Charsets.UTF_8)).readText()
//    }

//    private class FolderList{
//        var entries = ArrayList<MetaData>()
//        var cursor = ""
//        var has_more = false
//    }

    @Suppress("PropertyName")  // and don't make that private or this suppress won't work
    private class MetaData: FileMetaData {
//        var name = ""
        var server_modified = ""

        override fun getLastModified(): Date {
            return server_modified.parseDate()
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
