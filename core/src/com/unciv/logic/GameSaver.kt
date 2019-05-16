package com.unciv.logic

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json
import com.unciv.GameSettings
import com.unciv.logic.map.TileMap
import com.unciv.ui.saves.Gzip
import com.unciv.ui.utils.ImageGetter

class GameSaver {
    private val saveFilesFolder = "SaveFiles"
    private val mapsFolder = "maps"

    fun json() = Json().apply { setIgnoreDeprecated(true); ignoreUnknownFields = true } // Json() is NOT THREAD SAFE so we need to create a new one for each function

    fun getMap(mapName:String) = Gdx.files.local("$mapsFolder/$mapName")
    fun saveMap(mapName: String,tileMap: TileMap){
        getMap(mapName).writeString(Gzip.zip(json().toJson(tileMap)), false)
    }
    fun loadMap(mapName: String): TileMap {
        val gzippedString = getMap(mapName).readString()
        val unzippedJson = Gzip.unzip(gzippedString)
        return json().fromJson(TileMap::class.java, unzippedJson)
    }
    fun getMaps() = Gdx.files.local(mapsFolder).list().map { it.name() }


    fun getSave(GameName: String): FileHandle {
        return Gdx.files.local("$saveFilesFolder/$GameName")
    }

    fun getSaves(): List<String> {
        return Gdx.files.local(saveFilesFolder).list().map { it.name() }
    }

    fun saveGame(game: GameInfo, GameName: String) {
        json().toJson(game,getSave(GameName))
        // if we are on debug level, check saved game immediately, so we catch errors early
        if (Gdx.app.logLevel >= Application.LOG_DEBUG) {
            try {
                loadGame(GameName)
            } catch (ex: Exception) {
                Gdx.app.debug("save", "verifying saved game failed", ex)
                throw ex
            }
        }
    }

    fun loadGame(GameName: String) : GameInfo {
        val game = json().fromJson(GameInfo::class.java, getSave(GameName))
        game.setTransients()
        return game
    }

    fun deleteSave(GameName: String){
        getSave(GameName).delete()
    }

    fun getGeneralSettingsFile(): FileHandle {
        return Gdx.files.local("GameSettings.json")
    }

    fun getGeneralSettings(): GameSettings {
        val settingsFile = getGeneralSettingsFile()
        if(!settingsFile.exists()) return GameSettings()
        val settings = json().fromJson(GameSettings::class.java, settingsFile)

        val currentTileSets = ImageGetter.atlas.regions.filter { it.name.startsWith("TileSets") }
                .map { it.name.split("/")[1] }.distinct()
        if(settings.tileSet !in currentTileSets) settings.tileSet = "Default"
        return settings
    }

    fun setGeneralSettings(gameSettings: GameSettings){
        getGeneralSettingsFile().writeString(json().toJson(gameSettings), false)
    }
}
