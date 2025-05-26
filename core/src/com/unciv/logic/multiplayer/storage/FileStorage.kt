package com.unciv.logic.multiplayer.storage

import com.unciv.logic.UncivShowableException
import java.util.Date
import java.io.FileNotFoundException  // Kdoc only

class FileStorageConflictException : Exception()
class FileStorageRateLimitReached(val limitRemainingSeconds: Int) : UncivShowableException("Server limit reached! Please wait for [${limitRemainingSeconds}] seconds")
class MultiplayerFileNotFoundException(cause: Throwable?) : UncivShowableException("File could not be found on the multiplayer server", cause)
class MultiplayerAuthException(cause: Throwable?) : UncivShowableException("Authentication failed", cause)

interface FileMetaData {
    fun getLastModified(): Date?
}

enum class AuthStatus {
    UNAUTHORIZED,
    UNREGISTERED,
    VERIFIED,
    UNKNOWN
}

interface FileStorage {
    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerAuthException if the authentication failed
     */
    fun saveFileData(fileName: String, data: String)
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
     * @throws MultiplayerAuthException if the authentication failed
     */
    fun deleteFile(fileName: String)
    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerAuthException if the authentication failed
     */
    fun authenticate(userId: String, password: String): Boolean
    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerAuthException if the authentication failed
     */
    fun setPassword(newPassword: String): Boolean
    
    fun checkAuthStatus(userId: String, password: String): AuthStatus
}
