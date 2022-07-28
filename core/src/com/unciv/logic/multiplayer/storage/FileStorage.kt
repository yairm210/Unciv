package com.unciv.logic.multiplayer.storage

import com.unciv.logic.UncivShowableException
import java.util.*

class FileStorageConflictException : Exception()
class FileStorageRateLimitReached(val limitRemainingSeconds: Int) : UncivShowableException("Server limit reached! Please wait for [${limitRemainingSeconds}] seconds")
class MultiplayerFileNotFoundException(cause: Throwable?) : UncivShowableException("File could not be found on the multiplayer server", cause)

interface FileMetaData {
    fun getLastModified(): Date?
}

interface FileStorage {
    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileStorageConflictException if the file already exists and [overwrite] is false
     */
    fun saveFileData(fileName: String, data: String, overwrite: Boolean)
    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    fun loadFileData(fileName: String): String
    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    fun getFileMetaData(fileName: String): FileMetaData
    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    fun deleteFile(fileName: String)
}
