package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.utils.ImageGetter
import java.io.File
import kotlin.concurrent.thread

class GameSaver {
    private val saveFilesFolder = "SaveFiles"
    private val multiplayerFilesFolder = "MultiplayerGames"

    fun json() = Json().apply { setIgnoreDeprecated(true); ignoreUnknownFields = true } // Json() is NOT THREAD SAFE so we need to create a new one for each function


    fun getSave(GameName: String, multiplayer: Boolean = false): FileHandle {
        if (multiplayer)
            return Gdx.files.local("$multiplayerFilesFolder/$GameName")
        return Gdx.files.local("$saveFilesFolder/$GameName")
    }

    fun getSaves(multiplayer: Boolean = false): List<String> {
        if (multiplayer)
            return Gdx.files.local(multiplayerFilesFolder).list().map { it.name() }
        return Gdx.files.local(saveFilesFolder).list().map { it.name() }
    }

    fun saveGame(game: GameInfo, GameName: String, multiplayer: Boolean = false) {
        var name = GameName
        if (multiplayer)
            name = addGame(game.gameId, name)
        json().toJson(game,getSave(name, multiplayer))
    }

    fun loadGameByName(GameName: String, multiplayer: Boolean = false) : GameInfo {
        val game = json().fromJson(GameInfo::class.java, getSave(GameName, multiplayer))
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
        if (multiplayer)
            removeGameFromListByValue(GameName)
    }

    fun deleteMultplayerGameById(gameId: String){
        val fileName = multiplayerGameList[gameId]
        if (fileName != null) {
            getSave(fileName, true).delete()
            removeGameFromList(gameId)
        }
    }

    fun getGeneralSettingsFile(): FileHandle {
        return Gdx.files.local("GameSettings.json")
    }

    fun getGeneralSettings(): GameSettings {
        val settingsFile = getGeneralSettingsFile()
        if(!settingsFile.exists()) return GameSettings()
        var settings = json().fromJson(GameSettings::class.java, settingsFile)
        // I'm not sure of the circumstances,
        // but some people were getting null settings, even though the file existed??? Very odd.
        if(settings==null) settings = GameSettings()

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
        thread(name="Autosave") {
            saveGame(gameInfoClone, "Autosave")

            // keep auto-saves for the last 10 turns for debugging purposes
            val newAutosaveFilename = saveFilesFolder + File.separator + "Autosave-${gameInfo.currentPlayer}-${gameInfoClone.turns}"
            getSave("Autosave").copyTo(Gdx.files.local(newAutosaveFilename))

            fun getAutosaves(): List<String> { return getSaves().filter { it.startsWith("Autosave") } }
            while(getAutosaves().size>10){
                val saveToDelete = getAutosaves().minBy { getSave(it).lastModified() }!!
                deleteSave(saveToDelete)
            }

            // do this on main thread
            Gdx.app.postRunnable {
                postRunnable()
            }
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
  
    //The file manager allows easy access to the gameID and the name of its corresponding file for every saved multiplayer game
    companion object MultiplayerFileManager{

        private var multiplayerGameList = mutableMapOf<String, String>()

        init {
            loadAllGamesIntoList()
        }

        fun gameIsAlreadySavedAsMultiplayer(gameId: String) : Boolean{
            return multiplayerGameList.containsKey(gameId)
        }

        //returns a read only map
        fun getMutliplayerGameList(): Map<String, String>{
            return multiplayerGameList.toMap()
        }

        //adds the given game to the file manager and returns the new filename in case the given name was already taken
        private fun addGame(gameId: String, gameName: String): String{
            val oldFileName = multiplayerGameList[gameId]
            var newFileName = gameName
            //There is already a game saved with this ID and the filename got not changed so no need to change anything
            if (oldFileName == gameName)
                return gameName

            val count = getNumberForUsedGameName(gameName)
            //Check if there is already a game saved with this name
            if (count != 0)
                newFileName = "$newFileName($count)"

            //Delete old save file since we will created a new file
            if (oldFileName != null)
                GameSaver().getSave(oldFileName, true).delete()

            multiplayerGameList[gameId] = newFileName

            return newFileName
        }

        //returns the first unused number for a already used fileName
        private fun getNumberForUsedGameName(gameFileName: String): Int{
            val gameNames = multiplayerGameList.values
            //name is not used
            if (!gameNames.contains(gameFileName))
                return 0

            //test for all games with a name like "[gamename]([count])"
            var count = 1
            while (gameNames.contains("$gameFileName($count)")){
                count++
            }
            return count
        }

        private fun removeGameFromList(gameId: String){
            multiplayerGameList.remove(gameId)
        }

        private fun removeGameFromListByValue(gameFileName: String){
            //This should always have just one entry because files can't have the same name
            val keySet = multiplayerGameList.filterValues { it == gameFileName }.keys
            //if its > 1 something crazy happened so we better don't delete anything
            //if its < 1 then there was a SaveFile without list entry. This happens when File got renamed
            if (keySet.size != 1)
                return
            multiplayerGameList.remove(keySet.elementAt(0))
        }

        private fun loadAllGamesIntoList(){
            val gameFileNames : List<String>?

            multiplayerGameList.clear()

            try {
                gameFileNames = GameSaver().getSaves(true)
            }catch (ex: Exception) {
                return
            }

            for (gameFileName in gameFileNames){
                try {
                    val game = GameSaver().loadGameByName(gameFileName, true)
                    multiplayerGameList[game.gameId] = gameFileName
                }catch (ex: Exception){
                    continue
                }
            }
        }    
    }
}

