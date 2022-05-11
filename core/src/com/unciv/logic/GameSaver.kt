package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.crashhandling.crashHandlingThread
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import java.io.File

object GameSaver {
    private const val saveFilesFolder = "SaveFiles"
    private const val multiplayerFilesFolder = "MultiplayerGames"
    const val settingsFileName = "GameSettings.json"

    @Volatile
    var customSaveLocationHelper: CustomSaveLocationHelper? = null

    /** When set, we know we're on Android and can save to the app's personal external file directory
     * See https://developer.android.com/training/data-storage/app-specific#external-access-files */
    var externalFilesDirForAndroid = ""

    fun getSubfolder(multiplayer: Boolean = false) = if (multiplayer) multiplayerFilesFolder else saveFilesFolder

    fun getSave(GameName: String, multiplayer: Boolean = false): FileHandle {
        val localfile = Gdx.files.local("${getSubfolder(multiplayer)}/$GameName")
        if (externalFilesDirForAndroid == "" || !Gdx.files.isExternalStorageAvailable) return localfile
        val externalFile = Gdx.files.absolute(externalFilesDirForAndroid + "/${getSubfolder(multiplayer)}/$GameName")
        if (localfile.exists() && !externalFile.exists()) return localfile
        return externalFile
    }

    fun getSaves(multiplayer: Boolean = false): Sequence<FileHandle> {
        val localSaves = Gdx.files.local(getSubfolder(multiplayer)).list().asSequence()
        if (externalFilesDirForAndroid == "" || !Gdx.files.isExternalStorageAvailable) return localSaves
        return localSaves + Gdx.files.absolute(externalFilesDirForAndroid + "/${getSubfolder(multiplayer)}").list().asSequence()
    }

    fun saveGame(game: GameInfo, GameName: String, saveCompletionCallback: ((Exception?) -> Unit)? = null) {
        try {
            json().toJson(game, getSave(GameName))
            saveCompletionCallback?.invoke(null)
        } catch (ex: Exception) {
            saveCompletionCallback?.invoke(ex)
        }
    }

    /**
     * Overload of function saveGame to save a GameInfoPreview in the MultiplayerGames folder
     */
    fun saveGame(game: GameInfoPreview, GameName: String, saveCompletionCallback: ((Exception?) -> Unit)? = null) {
        try {
            json().toJson(game, getSave(GameName, true))
            saveCompletionCallback?.invoke(null)
        } catch (ex: Exception) {
            saveCompletionCallback?.invoke(ex)
        }
    }

    fun saveGameToCustomLocation(game: GameInfo, GameName: String, saveCompletionCallback: (Exception?) -> Unit) {
        customSaveLocationHelper!!.saveGame(game, GameName, forcePrompt = true, saveCompleteCallback = saveCompletionCallback)
    }

    fun loadGameByName(GameName: String) =
            loadGameFromFile(getSave(GameName))

    fun loadGameFromFile(gameFile: FileHandle): GameInfo {
        val game = json().fromJson(GameInfo::class.java, gameFile)
        game.setTransients()
        return game
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

    fun canLoadFromCustomSaveLocation() = customSaveLocationHelper != null

    fun gameInfoFromString(gameData: String): GameInfo {
        val game = json().fromJson(GameInfo::class.java, gameData)
        game.setTransients()
        return game
    }

    fun gameInfoPreviewFromString(gameData: String): GameInfoPreview {
        return json().fromJson(GameInfoPreview::class.java, gameData)
    }

    /**
     * WARNING! transitive GameInfo data not initialized
     * The returned GameInfo can not be used for most circumstances because its not initialized!
     * It is therefore stateless and save to call for Multiplayer Turn Notifier, unlike gameInfoFromString().
     */
    fun gameInfoFromStringWithoutTransients(gameData: String): GameInfo {
        return json().fromJson(GameInfo::class.java, gameData)
    }

    fun deleteSave(GameName: String, multiplayer: Boolean = false) {
        getSave(GameName, multiplayer).delete()
    }

    fun getGeneralSettingsFile(): FileHandle {
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

    fun autoSave(gameInfo: GameInfo, postRunnable: () -> Unit = {}) {
        // The save takes a long time (up to a few seconds on large games!) and we can do it while the player continues his game.
        // On the other hand if we alter the game data while it's being serialized we could get a concurrent modification exception.
        // So what we do is we clone all the game data and serialize the clone.
        val gameInfoClone = gameInfo.clone()
        crashHandlingThread(name = "Autosave") {
            autoSaveSingleThreaded(gameInfoClone)
            // do this on main thread
            postCrashHandlingRunnable ( postRunnable )
        }
    }

    fun autoSaveSingleThreaded(gameInfo: GameInfo) {
        try {
            saveGame(gameInfo, "Autosave")
        } catch (oom: OutOfMemoryError) {
            return  // not much we can do here
        }

        // keep auto-saves for the last 10 turns for debugging purposes
        val newAutosaveFilename =
            saveFilesFolder + File.separator + "Autosave-${gameInfo.currentPlayer}-${gameInfo.turns}"
        getSave("Autosave").copyTo(Gdx.files.local(newAutosaveFilename))

        fun getAutosaves(): Sequence<FileHandle> {
            return getSaves().filter { it.name().startsWith("Autosave") }
        }
        while (getAutosaves().count() > 10) {
            val saveToDelete = getAutosaves().minByOrNull { it: FileHandle -> it.lastModified() }!!
            deleteSave(saveToDelete.name())
        }
    }
}
