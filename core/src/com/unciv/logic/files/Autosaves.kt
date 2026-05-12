package com.unciv.logic.files

import com.unciv.logic.GameInfo
import com.unciv.logic.files.UncivFiles.Companion.AUTOSAVE_FILE_NAME
import com.unciv.logic.files.UncivFiles.Companion.SAVE_FILES_FOLDER
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import kotlinx.coroutines.Job

class Autosaves(val files: UncivFiles) {

    var autoSaveJob: Job? = null

    /**
     * Auto-saves a snapshot of the [gameInfo] in a new thread.
     */
    fun requestAutoSave(gameInfo: GameInfo, nextTurn: Boolean = false): Job {
        // The save takes a long time (up to a few seconds on large games!) and we can do it while the player continues his game.
        // On the other hand if we alter the game data while it's being serialized we could get a concurrent modification exception.
        // So what we do is we clone all the game data and serialize the clone.
        return requestAutoSaveUnCloned(gameInfo.clone(), nextTurn)
    }

    /**
     * In a new thread, auto-saves the [gameInfo] directly - only use this with [GameInfo] objects that are guaranteed not to be changed while the autosave is in progress!
     */
    fun requestAutoSaveUnCloned(gameInfo: GameInfo, nextTurn: Boolean = false): Job {
        val job = Concurrency.run("autoSaveUnCloned") {
            autoSave(gameInfo, nextTurn)
        }
        autoSaveJob = job
        return job
    }

    fun autoSave(gameInfo: GameInfo, nextTurn: Boolean = false) {
        // get GameSettings to check the maxAutosavesStored in the autoSave function
        val settings = files.getGeneralSettings()

        try {
            files.saveGame(gameInfo, AUTOSAVE_FILE_NAME)
        } catch (oom: OutOfMemoryError) {
            Log.error("Ran out of memory during autosave", oom)
            return  // not much we can do here
        }

        if (!nextTurn) return

        // keep auto-saves for the last `settings.maxAutosavesStored` turns
        val newAutosaveFile = files.pathToFileHandle(SAVE_FILES_FOLDER)
            .child("$AUTOSAVE_FILE_NAME-${gameInfo.currentPlayer}-${gameInfo.turns}")
        files.getSave(AUTOSAVE_FILE_NAME).copyTo(newAutosaveFile)

        purgeOldAutosaves(settings.maxAutosavesStored)
    }

    private fun purgeOldAutosaves(maxAutosavesStored: Int) {
        // Since the latest numbered autosave is a copy of the un-numbered autosave file,
        // we add 1 so `maxAutosavesStored` numbered files AND the un-numbered one are kept (see #12790).
        val oldAutoSaves = files.getSaves()
            .filter { it.name().startsWith(AUTOSAVE_FILE_NAME) }
            .sortedByDescending { it.lastModified() }   // youngest to the top
            .drop(maxAutosavesStored + 1)  // dropping those we want to keep
            // The following only has an effect if an exception is thrown halfway through the list
            // We delete the oldest first - principle of least surprise
            .asIterable().reversed()
        for (file in oldAutoSaves)
            files.deleteSave(file)
    }

    fun loadLatestAutosave(): GameInfo {
        return try {
            files.loadGameByName(AUTOSAVE_FILE_NAME)
        } catch (_: Exception) {
            // silent fail if we can't read the autosave for any reason - try to load the last autosave by timestamp first
            val autosaves = files.getSaves().filter {
                it.name() != AUTOSAVE_FILE_NAME &&
                it.name().startsWith(AUTOSAVE_FILE_NAME)
            }
            files.loadGameFromFile(autosaves.maxBy { it.lastModified() })
        }
    }

    fun autosaveExists(): Boolean = files.getSave(AUTOSAVE_FILE_NAME).exists()
}
