package com.unciv

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.LanguagePickerScreen
import com.unciv.ui.NewGameScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.worldscreen.WorldScreen

class UnCivGame : Game() {
    var gameInfo: GameInfo = GameInfo()
    lateinit var settings : GameSettings

    /**
     * This exists so that when debugging we can see the entire map.
     * Remember to turn this to false before commit and upload!
     */
    val viewEntireMapForDebug = false

    // For when you need to test something in an advanced game and don't have time to faff around
    val superchargedForDebug = false

    lateinit var worldScreen: WorldScreen

    override fun create() {
        Current = this
        Gdx.input.isCatchBackKey=true
        GameBasics.run {  } // just to initialize the GameBasics
        settings = GameSaver().getGeneralSettings()
        if (GameSaver().getSave("Autosave").exists()) {
            try {
                loadGame("Autosave")
            } catch (ex: Exception) { // silent fail if we can't read the autosave
                startNewGame()
            }
        }
        //startNewGame() //
        else screen= LanguagePickerScreen() // disabled because of people's negative reviews =(
    }

    fun loadGame(gameInfo:GameInfo){
        this.gameInfo = gameInfo
        worldScreen = WorldScreen()
        setWorldScreen()
    }

    fun loadGame(gameName:String){
        loadGame(GameSaver().loadGame( gameName))
    }

    fun startNewGame() {
        val newGame = GameStarter().startNewGame(NewGameScreen.NewGameParameters().apply { difficulty="Chieftain" })
        gameInfo = newGame

        worldScreen = WorldScreen()
        setWorldScreen()
    }

    fun setWorldScreen() {
        setScreen(worldScreen)
        Gdx.input.inputProcessor = worldScreen.stage
        worldScreen.shouldUpdate=true // This can set the screen to the policy picker or tech picker screen, so the input processor must come before
    }

    override fun resume() {
        ImageGetter.refreshAltas()

        // This is to solve a rare problem that I still on't understand its cause -
        // Sometimes, resume() is called and the gameInfo doesn't have any civilizations.
        // My guess is that resume() was called but create() wasn't, or perhaps was aborted too early,
        // and the original (and empty) initial GameInfo remained.
        if(gameInfo.civilizations.isEmpty())
            return create()

        worldScreen = WorldScreen()
        setWorldScreen()
    }

    // Maybe this will solve the resume error on chrome OS, issue 322? Worth a shot
    override fun resize(width: Int, height: Int) {
        resume()
    }

    companion object {
        lateinit var Current: UnCivGame
    }
}