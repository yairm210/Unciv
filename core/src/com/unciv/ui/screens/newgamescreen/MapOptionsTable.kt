package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.MapGeneratedMainType
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.widgets.TranslatedSelectBox
import com.unciv.ui.screens.basescreen.BaseScreen

class MapOptionsTable(private val newGameScreen: NewGameScreen) : Table() {

    private val mapParameters = newGameScreen.gameSetupInfo.mapParameters
    private var mapTypeSpecificTable = Table()
    internal lateinit var generatedMapOptionsTable: MapParametersTable
    private lateinit var randomMapOptionsTable: MapParametersTable
    private lateinit var savedMapOptionsTable: MapFileSelectTable
    private lateinit var scenarioOptionsTable: ScenarioSelectTable
    internal val mapTypeSelectBox: TranslatedSelectBox

    init {
        var step = "start"
        try {
            //defaults().pad(5f) - each nested table having the same can give 'stairs' effects,
            // better control directly. Besides, the first Labels/Buttons should have 10f to look nice
            step = "background"
            background = BaseScreen.skinStrings.getUiBackground("NewGameScreen/MapOptionsTable", tintColor = BaseScreen.skinStrings.skinConfig.clearColor)

            step = "create generated map options"
            generatedMapOptionsTable = MapParametersTable(newGameScreen, mapParameters, MapGeneratedMainType.generated)
            step = "create random map options"
            randomMapOptionsTable = MapParametersTable(newGameScreen, mapParameters, MapGeneratedMainType.randomGenerated)
            step = "create saved map options"
            savedMapOptionsTable = MapFileSelectTable(newGameScreen, mapParameters)
            step = "create scenario options"
            scenarioOptionsTable = ScenarioSelectTable(newGameScreen)

            step = "collect map types"
            val mapTypes = arrayListOf(MapGeneratedMainType.generated, MapGeneratedMainType.randomGenerated)
            if (savedMapOptionsTable.isNotEmpty()) mapTypes.add(MapGeneratedMainType.custom)
            if (newGameScreen.game.files.getScenarioFiles().any()) mapTypes.add(MapGeneratedMainType.scenario)

            step = "create map type select"
            mapTypeSelectBox = TranslatedSelectBox(mapTypes, MapGeneratedMainType.generated)

            fun updateOnMapTypeChange() {
                mapTypeSpecificTable.clear()
                when (mapTypeSelectBox.selected.value) {
                    MapGeneratedMainType.custom -> {
                        mapParameters.type = MapGeneratedMainType.custom
                        mapTypeSpecificTable.add(savedMapOptionsTable)
                        savedMapOptionsTable.activateCustomMaps()
                        newGameScreen.unlockTables()
                    }
                    MapGeneratedMainType.generated -> {
                        mapParameters.name = ""
                        mapParameters.type = generatedMapOptionsTable.mapTypeSelectBox.selected.value
                        mapTypeSpecificTable.add(generatedMapOptionsTable)
                        newGameScreen.unlockTables()
                    }
                    MapGeneratedMainType.randomGenerated -> {
                        mapParameters.name = ""
                        mapTypeSpecificTable.add(randomMapOptionsTable)
                        newGameScreen.unlockTables()
                    }
                    MapGeneratedMainType.scenario -> {
                        mapParameters.name = ""
                        mapTypeSpecificTable.add(scenarioOptionsTable)
                        scenarioOptionsTable.selectScenario()
                        newGameScreen.lockTables()
                    }
                }
                newGameScreen.gameSetupInfo.gameParameters.godMode = false
                newGameScreen.updateTables()
            }

            step = "activate selected map type"
            // activate once, so the MapGeneratedMainType.generated controls show
            updateOnMapTypeChange()

            step = "wire map type select change"
            mapTypeSelectBox.onChange { updateOnMapTypeChange() }

            step = "build map options table layout"
            val mapTypeSelectWrapper = Table()  // wrap to center-align Label and SelectBox easier
            mapTypeSelectWrapper.add("{Map Type}:".toLabel()).left().expandX()
            mapTypeSelectWrapper.add(mapTypeSelectBox).right()
            add(mapTypeSelectWrapper).pad(10f).fillX().row()
            add(mapTypeSpecificTable).row()
        } catch (ex: Exception) {
            throw IllegalStateException("MapOptionsTable init failed at step: $step", ex)
        }
    }

    internal fun getSelectedScenario(): ScenarioSelectTable.ScenarioData? {
        if (mapTypeSelectBox.selected.value != MapGeneratedMainType.scenario) return null
        return scenarioOptionsTable.selectedScenario
    }

    internal fun cancelBackgroundJobs() = savedMapOptionsTable.cancelBackgroundJobs()
}
