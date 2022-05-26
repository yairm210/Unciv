package com.unciv.logic

/**
 * Contract for platform-specific helper classes to handle saving and loading games to and from
 * arbitrary external locations.
 *
 * Implementation note: If a game is loaded with [loadGame] and the same game is saved with [saveGame],
 * the suggestedLocation in [saveGame] will be the location returned by [loadGame].
 */
interface CustomFileLocationHelper {
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
        saveCompleteCallback: ((String?, Exception?) -> Unit)? = null
    )

    /**
     * Loads a game asynchronously from a location selected by the user.
     *
     * Calls the [loadCompleteCallback] on the main thread with the [SuccessfulLoadResult] on success or the [Exception] on error or null in both on cancel.
     */
    fun loadGame(loadCompleteCallback: (SuccessfulLoadResult?, Exception?) -> Unit)
}

class SuccessfulLoadResult(
    val location: String,
    val gameData: String,
)
