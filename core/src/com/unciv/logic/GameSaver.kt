package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.utils.ImageGetter
import java.io.File
import kotlin.concurrent.thread

object GameSaver {
    private const val saveFilesFolder = "SaveFiles"
    private const val multiplayerFilesFolder = "MultiplayerGames"
    private const val settingsFileName = "GameSettings.json"

    /** When set, we know we're on Android and can save to the app's personal external file directory
     * See https://developer.android.com/training/data-storage/app-specific#external-access-files */
    var externalFilesDirForAndroid = ""

    fun json() = Json().apply { setIgnoreDeprecated(true); ignoreUnknownFields = true } // Json() is NOT THREAD SAFE so we need to create a new one for each function

    fun getSubfolder(multiplayer: Boolean=false) = if(multiplayer) multiplayerFilesFolder else saveFilesFolder

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

    fun saveGame(game: GameInfo, GameName: String, multiplayer: Boolean = false) {
        json().toJson(game,getSave(GameName, multiplayer))
    }

    fun loadGameByName(GameName: String, multiplayer: Boolean = false) =
            loadGameFromFile(getSave(GameName, multiplayer))

    fun loadGameFromFile(gameFile: FileHandle): GameInfo {
        val game = json().fromJson(GameInfo::class.java, gameFile)
        game.setTransients()
        return game
    }

    fun gameInfoFromString(gameData:String): GameInfo {
        val game = json().fromJson(GameInfo::class.java, gameData)
        game.setTransients()
        return game
    }

    fun deleteSave(GameName: String, multiplayer: Boolean = false){
        getSave(GameName, multiplayer).delete()
    }

    fun getGeneralSettingsFile(): FileHandle {
        return if (UncivGame.Current.consoleMode) FileHandle(settingsFileName)
        else Gdx.files.local(settingsFileName)
    }

    fun getGeneralSettings(): GameSettings {
        val settingsFile = getGeneralSettingsFile()
        val settings: GameSettings =
            if(!settingsFile.exists())
                GameSettings().apply { isFreshlyCreated = true }
            else try {
                json().fromJson(GameSettings::class.java, settingsFile)
            } catch (ex:Exception){
                // I'm not sure of the circumstances,
                // but some people were getting null settings, even though the file existed??? Very odd.
                // ...Json broken or otherwise unreadable is the only possible reason.
                println("Error reading settings file: ${ex.localizedMessage}")
                println("  cause: ${ex.cause}")
                GameSettings().apply { isFreshlyCreated = true }
            }

        val currentTileSets = ImageGetter.atlas.regions.asSequence()
                .filter { it.name.startsWith("TileSets") }
                .map { it.name.split("/")[1] }.distinct()
        if(settings.tileSet !in currentTileSets) settings.tileSet = "Default"
        return settings
    }

    fun setGeneralSettings(gameSettings: GameSettings){
        getGeneralSettingsFile().writeString(json().toJson(gameSettings), false)
    }

    fun autoSave(gameInfo: GameInfo, postRunnable: () -> Unit = {}) {
        // The save takes a long time (up to a few seconds on large games!) and we can do it while the player continues his game.
        // On the other hand if we alter the game data while it's being serialized we could get a concurrent modification exception.
        // So what we do is we clone all the game data and serialize the clone.
        val gameInfoClone = gameInfo.clone()
        thread(name = "Autosave") {
            autoSaveSingleThreaded(gameInfoClone)
            // do this on main thread
            Gdx.app.postRunnable {
                postRunnable()
            }
        }
    }

    fun autoSaveSingleThreaded (gameInfo: GameInfo) {
        saveGame(gameInfo, "Autosave")

        // keep auto-saves for the last 10 turns for debugging purposes
        val newAutosaveFilename = saveFilesFolder + File.separator + "Autosave-${gameInfo.currentPlayer}-${gameInfo.turns}"
        getSave("Autosave").copyTo(Gdx.files.local(newAutosaveFilename))

        fun getAutosaves(): Sequence<FileHandle> { return getSaves().filter { it.name().startsWith("Autosave") } }
        while(getAutosaves().count()>10){
            val saveToDelete = getAutosaves().minBy { it.lastModified() }!!
            deleteSave(saveToDelete.name())
        }
    }

    /**
     * Returns current turn's player from GameInfo JSON-String for multiplayer.
     * Does not initialize transitive GameInfo data.
     * It is therefore stateless and save to call for Multiplayer Turn Notifier, unlike gameInfoFromString().
     */
    fun currentTurnCivFromString(gameData: String): CivilizationInfo {
        val game = json().fromJson(GameInfo::class.java, gameData)
        return game.getCivilization(game.currentPlayer)
    }
}

