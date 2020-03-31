package com.unciv.models.metadata

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.unciv.logic.GameSaver

data class WindowState (val x:Int=0, val y:Int=0, val width:Int=0, val height:Int=0, val fullscreen:Boolean=true)

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
            val gm = Gdx.graphics.displayMode
            val gr = Gdx.graphics
            // A maximized window is *not* fullscreen, and the taskbar often stays visible.
            // Taskbar height samples: 79px (w10 @ fhd), 67px (mint 19.3 cinnamon @ 2.5k)
            windowState = when {
                gr.isFullscreen ->
                    // actual fullscreen should preserve the old geometry
                    WindowState(windowState.x, windowState.y, windowState.width, windowState.height, true)
                gm.width == gr.width && gr.height >= gm.height-90 && gr.height <= gm.height-32 ->
                    // Don't center when it looks suspiciously like maximized
                    WindowState(0, 0, gr.width, gr.height, false)
                else ->
                    // center assuming a conservative 32px taskbar height
                    WindowState((gm.width - gr.width) / 2, (gm.height - 32 - gr.height) / 2, gr.width, gr.height, false)
            }
        }
        GameSaver().setGeneralSettings(this)
    }

    fun addCompletedTutorialTask(tutorialTask:String){
        tutorialTasksCompleted.add(tutorialTask)
        save()
    }
}