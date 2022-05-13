package com.unciv.logic.multiplayer.storage

import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.ui.saves.Gzip
import java.io.FileNotFoundException
import java.util.*
import kotlin.math.pow

/**
 * Used to communicate data access between players
 */
class LockFile {
    // The lockData is necessary to make every LockFile unique
    // If Dropbox gets a file with the same content and overwrite set to false, it returns no
    // error even though the file was not uploaded as the exact file is already existing
    var lockData = UUID.randomUUID().toString()
}

/**
 *	Wrapper around OnlineMultiplayer's synchronization facilities.
 *
 *	Based on the design of Mutex from kotlinx.coroutines.sync, except that when it blocks,
 *	  it blocks the entire thread for an increasing period of time via Thread.sleep()
 */
class ServerMutex(val gameInfo: GameInfoPreview) {
    private var locked = false

    constructor(gameInfo: GameInfo) : this(gameInfo.asPreview()) { }

    /**
     * Try to obtain the server lock ONCE
     * DO NOT forget to unlock it when you're done with it!
     * Sleep between successive attempts
     * @see lock
     * @see unlock
     * @return true if lock is acquired
     */
    fun tryLock(): Boolean {
        // If we already hold the lock, return without doing anything
        if (locked) {
            return locked
        }

        locked = false
        val fileName = "${gameInfo.gameId}_Lock"

        // We have to check if the lock file already exists before we try to upload a new
        // lock file to not overuse the dropbox file upload limit else it will return an error
        try {
            val metaData = OnlineMultiplayerGameSaver().fileStorage().getFileMetaData(fileName)

            val date = metaData.getLastModified()
            // 30 seconds should be more than sufficient for everything lock related
            // so we can assume the lock file was forgotten if it is older than 30 sec
            if (date != null && System.currentTimeMillis() - date.time < 30000) {
                return locked
            } else {
                OnlineMultiplayerGameSaver().fileStorage().deleteFile(fileName)
            }
        } catch (ex: FileNotFoundException) {
            // Catching this exception means no lock file is present
            // so we can just continue with locking
        }

        try {
            OnlineMultiplayerGameSaver().fileStorage().saveFileData(fileName, Gzip.zip(json().toJson(LockFile())), false)
        } catch (ex: FileStorageConflictException) {
            return locked
        }

        locked = true
        return locked
    }

    /**
     * Block until this client owns the lock
     *
     * TODO: Create an alternative to the underlying tryLock or tryLockGame which checks for
     *       (and returns, when present) the value of the Retry-After header
     *
     * @see tryLock
     * @see unlock
     */
    fun lock() {
        var tries = 0

        // Try the lockfile once
        locked = tryLock()
        while (!locked) {
            // Wait exponentially longer after each attempt as per DropBox API recommendations
            var delay = 250 * 2.0.pow(tries).toLong()

            // 8 seconds is a really long time to sleep a thread, it's as good a cap as any
            if (delay > 8000) {
                delay = 8000
            }

            // Consider NOT sleeping here, instead perhaps delay or spin+yield
            Thread.sleep(delay)

            tries++

            // Retry the lock
            locked = tryLock()
        }
    }

    /**
     * Release a server lock acquired by tryLock or lock
     * @see tryLock
     * @see lock
     */
    fun unlock() {
        if (!locked)
            return

        OnlineMultiplayerGameSaver().fileStorage().deleteFile("${gameInfo.gameId}_Lock")
        locked = false
    }

    /**
     * See whether the client currently holds this lock
     * @return true if lock is active
     */
    fun holdsLock() = locked
}