package com.unciv

import com.badlogic.gdx.Application
import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.GameStarter
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.LanguagePickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.worldscreen.WorldScreen
import java.util.*

class UnCivGame(val version: String) : Game() {
    var gameInfo: GameInfo = GameInfo()
    lateinit var settings : GameSettings
    /**
     * This exists so that when debugging we can see the entire map.
     * Remember to turn this to false before commit and upload!
     */
    var viewEntireMapForDebug = false

    /** For when you need to test something in an advanced game and don't have time to faff around */
    val superchargedForDebug = false

    lateinit var worldScreen: WorldScreen

    override fun create() {
        Current = this
        if(Gdx.app.type!= Application.ApplicationType.Desktop)
            viewEntireMapForDebug=false
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        GameBasics.run {  } // just to initialize the GameBasics
        settings = GameSaver().getGeneralSettings()
        if(settings.userId=="") { // assign permanent user id
            settings.userId = UUID.randomUUID().toString()
            settings.save()
        }
        if (GameSaver().getSave("Autosave").exists()) {
            try {
                loadGame("Autosave")
            } catch (ex: Exception) { // silent fail if we can't read the autosave
                startNewGame()
            }
        }
        else setScreen(LanguagePickerScreen())
    }

    fun setScreen(screen: CameraStageBaseScreen) {
        Gdx.input.inputProcessor = screen.stage
        super.setScreen(screen)
    }

    fun loadGame(gameInfo:GameInfo){
        this.gameInfo = gameInfo

        worldScreen = WorldScreen(gameInfo.getPlayerToViewAs())
        setWorldScreen()
    }

    fun loadGame(gameName:String){
        loadGame(GameSaver().loadGameByName(gameName))
    }

    fun startNewGame() {
        val newGame = GameStarter().startNewGame(GameParameters().apply { difficulty="Chieftain" })
        loadGame(newGame)
    }

    fun setWorldScreen() {
        if(screen != null && screen != worldScreen) screen.dispose()
        setScreen(worldScreen)
        worldScreen.shouldUpdate=true // This can set the screen to the policy picker or tech picker screen, so the input processor must come before
    }

    override fun resume() {
        super.resume()
        ImageGetter.refreshAltas()

        // This is to solve a rare problem that I still on't understand its cause -
        // Sometimes, resume() is called and the gameInfo doesn't have any civilizations.
        // My guess is that resume() was called but create() wasn't, or perhaps was aborted too early,
        // and the original (and empty) initial GameInfo remained.
        if(gameInfo.civilizations.isEmpty())
            return create()

        if(::worldScreen.isInitialized) worldScreen.dispose() // I hope this will solve some of the many OuOfMemory exceptions...
        loadGame(gameInfo)
    }

    // Maybe this will solve the resume error on chrome OS, issue 322? Worth a shot
    override fun resize(width: Int, height: Int) {
        resume()
    }

    override fun dispose() {
        GameSaver().autoSave(gameInfo)
    }

    companion object {
        lateinit var Current: UnCivGame
    }
}