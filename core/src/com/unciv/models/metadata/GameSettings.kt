package com.unciv.models.metadata

import com.unciv.logic.GameSaver

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
    var autoCompleteTurn: Boolean = false
    var autoAssignCityProduction: Boolean = true
    var autoBuildingRoads: Boolean = true
    var showMinimap: Boolean = true
    var showPixelUnits: Boolean = false
    var showPixelImprovements: Boolean = true
    var showPixelResources: Boolean = true
    var nuclearWeaponEnabled = false

    var userId = ""

    fun save(){
        GameSaver().setGeneralSettings(this)
    }

    fun addCompletedTutorialTask(tutorialTask:String){
        tutorialTasksCompleted.add(tutorialTask)
        save()
    }
}