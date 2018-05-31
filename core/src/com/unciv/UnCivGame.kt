package com.unciv

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.GameSettings
import com.unciv.ui.worldscreen.WorldScreen

class UnCivGame : Game() {
    var gameInfo: GameInfo = GameInfo()
    var settings = GameSettings()

    lateinit var worldScreen: WorldScreen

    override fun create() {
        GameBasics.run {  } // just to initialize
        Current = this
        if (GameSaver().getSave("Autosave").exists()) {
            try {
                loadGame("Autosave")
            } catch (ex: Exception) { // silent fail if we can't read the autosave
                startNewGame()
            }
        }
        else startNewGame()
    }

    fun loadGame(gameName:String){
        gameInfo = GameSaver().loadGame( gameName)
        worldScreen = WorldScreen()
        setWorldScreen()
    }

    fun startNewGame(saveTutorialState:Boolean = false) {
        val newGame = GameStarter().startNewGame(20, 3, "Babylon")
        if(saveTutorialState) {
            newGame.tutorial = gameInfo.tutorial
        }
        gameInfo = newGame

        worldScreen = WorldScreen()
        setWorldScreen()

    }

    fun setWorldScreen() {
        setScreen(worldScreen)
        worldScreen.update()
        Gdx.input.inputProcessor = worldScreen.stage
    }


    companion object {
        lateinit var Current: UnCivGame
    }

}

