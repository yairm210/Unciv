package com.unciv.logic.files

/**
 * Contract for platform-specific helper classes to handle saving and loading games to and from
 * arbitrary external locations.
 *
 * Implementation note: If a game is loaded with [loadGame] and the same game is saved with [saveGame],
 * the suggestedLocation in [saveGame] will be the location returned by [loadGame].
 */
interface PlatformSaverLoader {

    fun saveGame(
        data: String,                               // Data to save
        suggestedLocation: String,                  // Proposed location
        onSaved: (location: String) -> Unit = {},   // On-save-complete callback
        onError: (ex: Exception) -> Unit = {}       // On-save-error callback
    )

    fun loadGame(
        onLoaded: (data: String, location: String) -> Unit,  // On-load-complete callback
        onError: (Exception) -> Unit = {}                    // On-load-error callback
    )

    /** Invisible Exception can be used with onError callbacks to indicate the User cancelled the operation and needs no message */
    class Cancelled : Exception()

    companion object {
        val None = object : PlatformSaverLoader {
            override fun saveGame(
                data: String,
                suggestedLocation: String,
                onSaved: (location: String) -> Unit,
                onError: (ex: Exception) -> Unit
            ) {}

            override fun loadGame(
                onLoaded: (data: String, location: String) -> Unit,
                onError: (Exception) -> Unit
            ) {}
        }
    }
}
