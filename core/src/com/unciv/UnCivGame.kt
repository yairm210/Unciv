package com.unciv

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.worldscreen.WorldScreen

class UnCivGame : Game() {
    var gameInfo: GameInfo = GameInfo()
    lateinit var settings : GameSettings
    val json = Json().apply { setIgnoreDeprecated(true); setIgnoreUnknownFields(true) }

    /**
     * This exists so that when debugging we can see the entire map.
     * Remember to turn this to false before commit and upload!
     */
    val viewEntireMapForDebug = false


    lateinit var worldScreen: WorldScreen

    override fun create() {
        Current = this
        Gdx.input.isCatchBackKey=true
        GameBasics.run {  } // just to initialize
        settings = GameSaver().getGeneralSettings()
        if (GameSaver().getSave("Autosave").exists()) {
            try {
                loadGame("Autosave")
            } catch (ex: Exception) { // silent fail if we can't read the autosave
                startNewGame()
            }
        }
        else startNewGame() // screen=LanguagePickerScreen() disabled because of people's negative reviews =(
    }

    fun loadGame(gameInfo:GameInfo){
        this.gameInfo = gameInfo
        if(settings.tutorialsShown.isEmpty()  && this.gameInfo.tutorial.isNotEmpty())
            settings.tutorialsShown.addAll(this.gameInfo.tutorial)

        worldScreen = WorldScreen()
        setWorldScreen()
    }

    fun loadGame(gameName:String){
        loadGame(GameSaver().loadGame( gameName))
    }

    fun startNewGame() {
        val newGame = GameStarter().startNewGame(20, 3, "Babylon","Chieftain")
        gameInfo = newGame

        worldScreen = WorldScreen()
        setWorldScreen()

    }

    fun setWorldScreen() {
        setScreen(worldScreen)
        worldScreen.update()
        Gdx.input.inputProcessor = worldScreen.stage
    }

    override fun resume() {
        ImageGetter.refreshAltas()
        worldScreen = WorldScreen()
        setWorldScreen()
    }


    companion object {
        lateinit var Current: UnCivGame
    }

}