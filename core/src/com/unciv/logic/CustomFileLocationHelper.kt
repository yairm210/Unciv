package com.unciv.logic

import com.unciv.logic.UncivFiles.CustomLoadResult
import com.unciv.logic.UncivFiles.CustomSaveResult
import com.unciv.utils.concurrency.Concurrency
import java.io.InputStream
import java.io.OutputStream

/**
 * Contract for platform-specific helper classes to handle saving and loading games to and from
 * arbitrary external locations.
 *
 * Implementation note: If a game is loaded with [loadGame] and the same game is saved with [saveGame],
 * the suggestedLocation in [saveGame] will be the location returned by [loadGame].
 */
abstract class CustomFileLocationHelper {
    /**
     * Saves a game asynchronously to a location selected by the user.
     *
     * Prefills their UI with a [suggestedLocation].
     *
     * Calls the [saveCompleteCallback] on the main thread with the save location on success or the [Exception] on error or null in both on cancel.
     */
    fun saveGame(
        gameData: String,
        suggestedLocation: String,
        saveCompleteCallback: (CustomSaveResult) -> Unit = {}
    ) {
        createOutputStream(suggestedLocation) { location, outputStream, exception ->
            if (outputStream == null) {
                callSaveCallback(saveCompleteCallback, exception = exception)
                return@createOutputStream
            }

            try {
                outputStream.writer().use { it.write(gameData) }
                callSaveCallback(saveCompleteCallback, location)
            } catch (ex: Exception) {
                callSaveCallback(saveCompleteCallback, exception = ex)
            }
        }
    }

    /**
     * Loads a game asynchronously from a location selected by the user.
     *
     * Calls the [loadCompleteCallback] on the main thread.
     */
    fun loadGame(loadCompleteCallback: (CustomLoadResult<String>) -> Unit) {
        createInputStream { location, inputStream, exception ->
            if (inputStream == null) {
                callLoadCallback(loadCompleteCallback, exception = exception)
                return@createInputStream
            }

            try {
                val gameData = inputStream.reader().use { it.readText() }
                callLoadCallback(loadCompleteCallback, location, gameData)
            } catch (ex: Exception) {
                callLoadCallback(loadCompleteCallback, exception = ex)
            }
        }
    }

    /**
     * [callback] should be called with the actual selected location and an OutputStream to the location, or an exception if something failed.
     */
    protected abstract fun createOutputStream(suggestedLocation: String, callback: (String?, OutputStream?, Exception?) -> Unit)

    /**
     * [callback] should be called with the actual selected location and an InputStream to read the location, or an exception if something failed.
     */
    protected abstract fun createInputStream(callback: (String?, InputStream?, Exception?) -> Unit)
}

private fun callLoadCallback(loadCompleteCallback: (CustomLoadResult<String>) -> Unit,
                             location: String? = null,
                             gameData: String? = null,
                             exception: Exception? = null) {
    val result = if (location != null && gameData != null && exception == null) {
        CustomLoadResult(location to gameData)
    } else {
        CustomLoadResult(null, exception)
    }
    Concurrency.runOnGLThread {
        loadCompleteCallback(result)
    }
}
private fun callSaveCallback(saveCompleteCallback: (CustomSaveResult) -> Unit,
                             location: String? = null,
                             exception: Exception? = null) {
    Concurrency.runOnGLThread {
        saveCompleteCallback(CustomSaveResult(location, exception))
    }
}
