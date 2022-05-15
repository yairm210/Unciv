package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.saves.Gzip
import java.io.File


object GameSaver {
    //region Data

    private const val saveFilesFolder = "SaveFiles"
    private const val multiplayerFilesFolder = "MultiplayerGames"
    const val autoSaveFileName = "Autosave"
    const val settingsFileName = "GameSettings.json"
    var saveZipped = false

    @Volatile
    var customSaveLocationHelper: CustomSaveLocationHelper? = null

    /** When set, we know we're on Android and can save to the app's personal external file directory
     * See https://developer.android.com/training/data-storage/app-specific#external-access-files */
    var externalFilesDirForAndroid = ""

    //endregion
    //region Helpers

    private fun getSavefolder(multiplayer: Boolean = false) = if (multiplayer) multiplayerFilesFolder else saveFilesFolder

    fun getSave(GameName: String, multiplayer: Boolean = false): FileHandle {
        val localFile = Gdx.files.local("${getSavefolder(multiplayer)}/$GameName")
        if (externalFilesDirForAndroid == "" || !Gdx.files.isExternalStorageAvailable) return localFile
        val externalFile = Gdx.files.absolute(externalFilesDirForAndroid + "/${getSavefolder(multiplayer)}/$GameName")
        if (localFile.exists() && !externalFile.exists()) return localFile
        return externalFile
    }

    fun getSaves(multiplayer: Boolean = false): Sequence<FileHandle> {
        val localSaves = Gdx.files.local(getSavefolder(multiplayer)).list().asSequence()
        if (externalFilesDirForAndroid == "" || !Gdx.files.isExternalStorageAvailable) return localSaves
        return localSaves + Gdx.files.absolute(externalFilesDirForAndroid + "/${getSavefolder(multiplayer)}").list().asSequence()
    }

    fun canLoadFromCustomSaveLocation() = customSaveLocationHelper != null

    fun deleteSave(GameName: String, multiplayer: Boolean = false) {
        getSave(GameName, multiplayer).delete()
    }

    /**
     * Only use this with a [FileHandle] returned by [getSaves]!
     */
    fun deleteSave(file: FileHandle) {
        file.delete()
    }

    //endregion
    //region Saving

    fun saveGame(game: GameInfo, GameName: String, saveCompletionCallback: (Exception?) -> Unit = { if (it != null) throw it }): FileHandle {
        val file = getSave(GameName)
        saveGame(game, file, saveCompletionCallback)
        return file
    }

    /**
     * Only use this with a [FileHandle] obtained by [getSaves]!
     */
    fun saveGame(game: GameInfo, file: FileHandle, saveCompletionCallback: (Exception?) -> Unit = { if (it != null) throw it }) {
        try {
            file.writeString(gameInfoToString(game), false)
            saveCompletionCallback(null)
        } catch (ex: Exception) {
            saveCompletionCallback(ex)
        }
    }

    /** Returns gzipped serialization of [game], optionally gzipped ([forceZip] overrides [saveZipped]) */
    fun gameInfoToString(game: GameInfo, forceZip: Boolean? = null): String {
        val plainJson = json().toJson(game)
        return if (forceZip ?: saveZipped) Gzip.zip(plainJson) else plainJson
    }

    /** Returns gzipped serialization of preview [game] - only called from [OnlineMultiplayerGameSaver] */
    fun gameInfoToString(game: GameInfoPreview): String {
        return Gzip.zip(json().toJson(game))
    }

    /**
     * Overload of function saveGame to save a GameInfoPreview in the MultiplayerGames folder
     */
    fun saveGame(game: GameInfoPreview, GameName: String, saveCompletionCallback: (Exception?) -> Unit = { if (it != null) throw it }): FileHandle {
        val file = getSave(GameName, true)
        saveGame(game, file, saveCompletionCallback)
        return file
    }

    /**
     * Only use this with a [FileHandle] obtained by [getSaves]!
     */
    fun saveGame(game: GameInfoPreview, file: FileHandle, saveCompletionCallback: (Exception?) -> Unit = { if (it != null) throw it }) {
        try {
            json().toJson(game, file)
            saveCompletionCallback(null)
        } catch (ex: Exception) {
            saveCompletionCallback(ex)
        }
    }

    fun saveGameToCustomLocation(game: GameInfo, GameName: String, saveCompletionCallback: (Exception?) -> Unit) {
        customSaveLocationHelper!!.saveGame(game, GameName, forcePrompt = true, saveCompleteCallback = saveCompletionCallback)
    }

    //endregion
    //region Loading

    fun loadGameByName(GameName: String) =
            loadGameFromFile(getSave(GameName))

    fun loadGameFromFile(gameFile: FileHandle): GameInfo {
        return gameInfoFromString(gameFile.readString())
    }

    fun loadGamePreviewByName(GameName: String) =
            loadGamePreviewFromFile(getSave(GameName, true))

    fun loadGamePreviewFromFile(gameFile: FileHandle): GameInfoPreview {
        return json().fromJson(GameInfoPreview::class.java, gameFile)
    }

    fun loadGameFromCustomLocation(loadCompletionCallback: (GameInfo?, Exception?) -> Unit) {
        customSaveLocationHelper!!.loadGame { game, e ->
            loadCompletionCallback(game?.apply { setTransients() }, e)
        }
    }

    fun gameInfoFromString(gameData: String): GameInfo {
        return gameInfoFromStringWithoutTransients(gameData).apply {
            setTransients()
        }
    }

    /**
     * Parses [gameData] as gzipped serialization of a [GameInfoPreview] - only called from [OnlineMultiplayerGameSaver]
     * @throws SerializationException
     */
    fun gameInfoPreviewFromString(gameData: String): GameInfoPreview {
        return json().fromJson(GameInfoPreview::class.java, Gzip.unzip(gameData))
    }

    /**
     * WARNING! transitive GameInfo data not initialized
     * The returned GameInfo can not be used for most circumstances because its not initialized!
     * It is therefore stateless and save to call for Multiplayer Turn Notifier, unlike gameInfoFromString().
     *
     * @throws SerializationException
     */
    private fun gameInfoFromStringWithoutTransients(gameData: String): GameInfo {
        val unzippedJson = try {
            Gzip.unzip(gameData)
        } catch (ex: Exception) {
            gameData
        }
        return json().fromJson(GameInfo::class.java, unzippedJson)
    }

    //endregion
    //region Settings

    private fun getGeneralSettingsFile(): FileHandle {
        return if (UncivGame.Current.consoleMode) FileHandle(settingsFileName)
        else Gdx.files.local(settingsFileName)
    }

    fun getGeneralSettings(): GameSettings {
        val settingsFile = getGeneralSettingsFile()
        val settings: GameSettings =
                if (!settingsFile.exists())
                    GameSettings().apply { isFreshlyCreated = true }
                else try {
                    json().fromJson(GameSettings::class.java, settingsFile)
                } catch (ex: Exception) {
                    // I'm not sure of the circumstances,
                    // but some people were getting null settings, even though the file existed??? Very odd.
                    // ...Json broken or otherwise unreadable is the only possible reason.
                    println("Error reading settings file: ${ex.localizedMessage}")
                    println("  cause: ${ex.cause}")
                    GameSettings().apply { isFreshlyCreated = true }
                }


        return settings
    }

    fun setGeneralSettings(gameSettings: GameSettings) {
        getGeneralSettingsFile().writeString(json().toJson(gameSettings), false)
    }

    //endregion
    //region Autosave

    fun autoSave(gameInfo: GameInfo, postRunnable: () -> Unit = {}) {
        // The save takes a long time (up to a few seconds on large games!) and we can do it while the player continues his game.
        // On the other hand if we alter the game data while it's being serialized we could get a concurrent modification exception.
        // So what we do is we clone all the game data and serialize the clone.
        autoSaveUnCloned(gameInfo.clone(), postRunnable)
    }

    fun autoSaveUnCloned(gameInfo: GameInfo, postRunnable: () -> Unit = {}) {
        // This is used when returning from WorldScreen to MainMenuScreen - no clone since UI access to it should be gone
        launchCrashHandling(autoSaveFileName, runAsDaemon = false) {
            autoSaveSingleThreaded(gameInfo)
            // do this on main thread
            postCrashHandlingRunnable ( postRunnable )
        }
    }

    fun autoSaveSingleThreaded(gameInfo: GameInfo) {
        try {
            saveGame(gameInfo, autoSaveFileName)
        } catch (oom: OutOfMemoryError) {
            return  // not much we can do here
        }

        // keep auto-saves for the last 10 turns for debugging purposes
        val newAutosaveFilename =
            saveFilesFolder + File.separator + autoSaveFileName + "-${gameInfo.currentPlayer}-${gameInfo.turns}"
        getSave(autoSaveFileName).copyTo(Gdx.files.local(newAutosaveFilename))

        fun getAutosaves(): Sequence<FileHandle> {
            return getSaves().filter { it.name().startsWith(autoSaveFileName) }
        }
        while (getAutosaves().count() > 10) {
            val saveToDelete = getAutosaves().minByOrNull { it.lastModified() }!!
            deleteSave(saveToDelete.name())
        }
    }
}
