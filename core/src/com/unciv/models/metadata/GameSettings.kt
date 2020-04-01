package com.unciv.models.metadata

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.unciv.logic.GameSaver

data class WindowState (val x:Int=0, val y:Int=0, val width:Int=0, val height:Int=0, val maximized:Boolean=false, val fullscreen:Boolean=true)

class GameSettings {
    var showWorkedTiles: Boolean = false
    var showResourcesAndImprovements: Boolean = true
    var checkForDueUnits: Boolean = true
    var singleTapMove: Boolean = false
    var language: String = "English"
    var resolution: String = "900x600"
    var tutorialsShown = HashSet<String>()
    var tutorialTasksCompleted = HashSet<String>()
    var hasCrashedRecently = false
    var soundEffectsVolume = 0.5f
    var musicVolume = 0.5f
    var turnsBetweenAutosaves = 1
    var tileSet:String = "FantasyHex"
    var showTutorials: Boolean = true
    var autoAssignCityProduction: Boolean = true
    var autoBuildingRoads: Boolean = true
    var showMinimap: Boolean = true
    var showPixelUnits: Boolean = false
    var showPixelImprovements: Boolean = true
    var nuclearWeaponEnabled = true
    var continuousRendering = false
    var userId = ""
    var multiplayerTurnCheckerEnabled = true
    var multiplayerTurnCheckerPersistentNotificationEnabled = true
    var multiplayerTurnCheckerDelayInMinutes = 5
    var orderTradeOffersByAmount = true
    var windowState = WindowState()

    init {
        // 26 = Android Oreo. Versions below may display permanent icon in notification bar.
        if (Gdx.app.type == Application.ApplicationType.Android && Gdx.app.version < 26) {
            multiplayerTurnCheckerPersistentNotificationEnabled = false
        }
    }

    fun save(){
        if (Gdx.app.type == Application.ApplicationType.Desktop) {
            val gr = Gdx.graphics
            if (gr.isFullscreen) {
                // actual fullscreen should preserve the old geometry
                windowState = WindowState(windowState.x, windowState.y, windowState.width, windowState.height, windowState.maximized, true)
            } else {
                val grWindowField = gr.javaClass.getDeclaredField("window")
                grWindowField.isAccessible = true
                val grWindow = grWindowField.get(gr)
                val grGetXMethod = (grWindowField.genericType as Class<*>).getDeclaredMethod("getPositionX")
                val windowX = grGetXMethod.invoke(grWindow) as Int
                val grGetYMethod = (grWindowField.genericType as Class<*>).getDeclaredMethod("getPositionY")
                val windowY = grGetYMethod.invoke(grWindow) as Int
                val isMaximized = false     // Lwjgl3 allows detecting "iconified" but not maximized. In case they learn...
                windowState = WindowState(windowX, windowY, gr.width, gr.height, isMaximized, false)
            }
        }
        GameSaver().setGeneralSettings(this)
    }

    fun addCompletedTutorialTask(tutorialTask:String){
        tutorialTasksCompleted.add(tutorialTask)
        save()
    }
}