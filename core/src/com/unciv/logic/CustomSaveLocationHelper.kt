package com.unciv.logic

/**
 * Contract for platform-specific helper classes to handle saving and loading games to and from
 * arbitrary external locations
 */
interface CustomSaveLocationHelper {
    /**### Save to custom location
     * Saves a game asynchronously with a given default name and then calls the [saveCompleteCallback] callback
     * upon completion. The [saveCompleteCallback] callback will be called from the same thread that this method
     * is called from. If the [GameInfo] object already has the
     * [customSaveLocation][GameInfo.customSaveLocation] property defined (not null), then the user
     * will not be prompted to select a location for the save unless [forcePrompt] is set to true
     * (think of this like "Save as...")
     * On success, this is also expected to set [customSaveLocation][GameInfo.customSaveLocation].
     *
     *  @param  gameInfo Game data to save
     *  @param  gameName Suggestion for the save name
     *  @param  forcePrompt Bypass UI if location contained in [gameInfo] and [forcePrompt]==`false`
     *  @param  saveCompleteCallback Action to call upon completion (success _and_ failure)
     */
    fun saveGame(
            gameSaver: GameSaver,
            gameInfo: GameInfo,
            gameName: String,
            forcePrompt: Boolean = false,
            saveCompleteCallback: ((Exception?) -> Unit)? = null
    )

    /**### Load from custom location
     * Loads a game from an external source asynchronously, then calls [loadCompleteCallback] with the loaded [GameInfo].
     * On success, this is also expected to set the loaded [GameInfo]'s property [customSaveLocation][GameInfo.customSaveLocation].
     * Note that there is no hint so pass a default location or a way to remember the folder the user chose last time.
     *
     *  @param  loadCompleteCallback  Action to call upon completion (success _and_ failure)
     */
    fun loadGame(gameSaver: GameSaver, loadCompleteCallback: (GameInfo?, Exception?) -> Unit)
}
