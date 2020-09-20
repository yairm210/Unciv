package com.unciv.logic

/**
 * Contract for platform-specific helper classes to handle saving and loading games to and from
 * arbitrary external locations
 */
interface CustomSaveLocationHelper {
    /**
     * Saves a game asynchronously with a given default name and then calls the [block] callback
     * upon completion. The [block] callback will be called from the same thread that this method
     * is called from. If the [GameInfo] object already has the
     * [customSaveLocation][GameInfo.customSaveLocation] property defined (not null), then the user
     * will not be prompted to select a location for the save unless [forcePrompt] is set to true
     * (think of this like "Save as...")
     */
    fun saveGame(
            gameInfo: GameInfo,
            gameName: String,
            forcePrompt: Boolean = false,
            block: (() -> Unit)? = null
    )

    /**
     * Loads a game from an external source asynchronously, then calls [block] with the loaded
     * [GameInfo]
     */
    fun loadGame(block: (GameInfo) -> Unit)
}
