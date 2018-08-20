package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json
import com.unciv.GameSettings
import com.unciv.OldGameSettings

class GameSaver {
    private val saveFilesFolder = "SaveFiles"

    fun json() = Json().apply { setIgnoreDeprecated(true); setIgnoreUnknownFields(true) } // Json() is NOT THREAD SAFE so we need to create a new one for each function

    fun getSave(GameName: String): FileHandle {
        return Gdx.files.local("$saveFilesFolder/$GameName")
    }

    fun getSaves(): List<String> {
        return Gdx.files.local(saveFilesFolder).list().map { it.name() }
    }

    fun saveGame(game: GameInfo, GameName: String) {
        getSave(GameName).writeString(json().toJson(game), false)
    }

    fun loadGame(GameName: String) : GameInfo {
        val game = json().fromJson(GameInfo::class.java, getSave(GameName).readString())
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
        try {
            return json().fromJson(GameSettings::class.java, settingsFile)
        }
        catch(ex:Exception) {
            return json().fromJson(OldGameSettings::class.java, settingsFile).toGameSettings()
        }
    }

    fun setGeneralSettings(gameSettings: GameSettings){
        getGeneralSettingsFile().writeString(json().toJson(gameSettings), false)
    }
}
