package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json
import com.unciv.logic.GameInfo
import com.unciv.ui.UnCivGame

object GameSaver {
    val saveFilesFolder = "SaveFiles"

    fun GetSave(GameName: String): FileHandle {
        return Gdx.files.local(saveFilesFolder + "/" + GameName)
    }

    fun SaveGame(game: UnCivGame, GameName: String) {
        GetSave(GameName).writeString(Json().toJson(game.gameInfo), false)
    }

    fun LoadGame(game: UnCivGame, GameName: String) {
        game.gameInfo = Json().fromJson(GameInfo::class.java, GetSave(GameName).readString())
        game.gameInfo.setTransients()
    }
}
