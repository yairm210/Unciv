package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.UncivGame
import com.unciv.logic.MapSaver
import com.unciv.logic.map.MapType
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onChange
import com.unciv.ui.utils.toLabel

class MapOptionsTable(val newGameScreen: NewGameScreen): Table() {

    val mapParameters = newGameScreen.gameSetupInfo.mapParameters
    private var mapTypeSpecificTable = Table()
    private val generatedMapOptionsTable = MapParametersTable(mapParameters)
    private val savedMapOptionsTable = Table()
    private val savedScenarioOptionsTable = Table()

    init {
        defaults().pad(5f)
        add("Map Options".toLabel(fontSize = 24)).top().padBottom(20f).colspan(2).row()
        addMapTypeSelection()
    }


    private fun addMapTypeSelection() {
        add("{Map Type}:".toLabel())
        val mapTypes = arrayListOf("Generated")
        if (MapSaver.getMaps().isNotEmpty()) mapTypes.add(MapType.custom)
        if (MapSaver.getScenarios().isNotEmpty() && UncivGame.Current.settings.extendedMapEditor) mapTypes.add(MapType.scenario)
        val mapTypeSelectBox = TranslatedSelectBox(mapTypes, "Generated", CameraStageBaseScreen.skin)

        val mapFileSelectBox = getMapFileSelectBox()
        savedMapOptionsTable.defaults().pad(5f)
        savedMapOptionsTable.add("{Map file}:".toLabel()).left()
        // because SOME people gotta give the hugest names to their maps
        savedMapOptionsTable.add(mapFileSelectBox).maxWidth(newGameScreen.stage.width / 2)
                .right().row()

        val scenarioFileSelectBox = getScenarioFileSelectBox()
        savedScenarioOptionsTable.defaults().pad(5f)
        savedScenarioOptionsTable.add("{Scenario file}:".toLabel()).left()
        // because SOME people gotta give the hugest names to their maps
        savedScenarioOptionsTable.add(scenarioFileSelectBox).maxWidth(newGameScreen.stage.width / 2)
                .right().row()

        fun updateOnMapTypeChange() {
            mapTypeSpecificTable.clear()
            if (mapTypeSelectBox.selected.value == MapType.custom) {
                mapParameters.type = MapType.custom
                mapParameters.name = mapFileSelectBox.selected
                mapTypeSpecificTable.add(savedMapOptionsTable)
                newGameScreen.unlockTables()
                newGameScreen.updateTables()
            } else if (mapTypeSelectBox.selected.value == MapType.scenario) {
                mapParameters.type = MapType.scenario
                mapParameters.name = scenarioFileSelectBox.selected
                mapTypeSpecificTable.add(savedScenarioOptionsTable)
                val scenario = MapSaver.loadScenario(mapParameters.name)
                newGameScreen.gameSetupInfo.gameParameters = scenario.gameParameters
                newGameScreen.gameSetupInfo.mapParameters = mapParameters
                // update PlayerTable and GameOptionsTable
                newGameScreen.lockTables()
                newGameScreen.updateTables()
            } else {
                mapParameters.name = ""
                mapParameters.type = generatedMapOptionsTable.mapTypeSelectBox.selected.value
                mapTypeSpecificTable.add(generatedMapOptionsTable)
                newGameScreen.unlockTables()
                newGameScreen.updateTables()
            }
        }

        // activate once, so when we had a file map before we'll have the right things set for another one
        updateOnMapTypeChange()

        mapTypeSelectBox.onChange { updateOnMapTypeChange() }

        add(mapTypeSelectBox).row()
        add(mapTypeSpecificTable).colspan(2).row()
    }

    private fun getMapFileSelectBox(): SelectBox<String> {
        val mapFileSelectBox = SelectBox<String>(CameraStageBaseScreen.skin)
        val mapNames = Array<String>()
        for (mapName in MapSaver.getMaps()) mapNames.add(mapName)
        mapFileSelectBox.items = mapNames
        if (mapParameters.name in mapNames) mapFileSelectBox.selected = mapParameters.name

        mapFileSelectBox.onChange { mapParameters.name = mapFileSelectBox.selected!! }
        return mapFileSelectBox
    }

    private fun getScenarioFileSelectBox(): SelectBox<String> {
        val scenarioFileSelectBox = SelectBox<String>(CameraStageBaseScreen.skin)
        val scenarioNames = Array<String>()
        for (scenarioName in MapSaver.getScenarios()) scenarioNames.add(scenarioName)
        scenarioFileSelectBox.items = scenarioNames
        if (mapParameters.name in scenarioNames) scenarioFileSelectBox.selected = mapParameters.name

        scenarioFileSelectBox.onChange {
            mapParameters.name = scenarioFileSelectBox.selected!!
            val scenario = MapSaver.loadScenario(mapParameters.name)
            newGameScreen.apply {
                gameSetupInfo.gameParameters = scenario.gameParameters
                newGameOptionsTable.gameParameters = scenario.gameParameters
                newGameOptionsTable.reloadRuleset()
                updateTables()
            }
        }
        return scenarioFileSelectBox
    }


}