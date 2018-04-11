package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json
import com.unciv.logic.GameInfo
import com.unciv.UnCivGame

object GameSaver {
    private const val saveFilesFolder = "SaveFiles"

    fun getSave(GameName: String): FileHandle {
        return Gdx.files.local("$saveFilesFolder/$GameName")
    }

    fun saveGame(game: UnCivGame, GameName: String) {
        getSave(GameName).writeString(Json().toJson(game.gameInfo), false)
    }

    fun loadGame(game: UnCivGame, GameName: String) {
        game.gameInfo = Json().fromJson(GameInfo::class.java, getSave(GameName).readString())
        game.gameInfo.setTransients()
    }
}
