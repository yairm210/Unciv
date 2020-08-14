package com.unciv.ui.newgamescreen

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
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
    private val scenarioMapOptionsTable = Table()
    private val scenarioOptionsTable = Table()
    var selectedScenarioSaveGame: GameInfo? = null
    lateinit var mapTypeSelectBox: TranslatedSelectBox

    init {
        defaults().pad(5f)
        add("Map Options".toLabel(fontSize = 24)).top().padBottom(20f).colspan(2).row()
        addMapTypeSelection()
    }

    fun selectSavedGameAsScenario(gameFile: FileHandle){
        val savedGame = GameSaver.loadGameFromFile(gameFile)
        mapParameters.type = MapType.scenario
        mapParameters.name = gameFile.name()
        newGameScreen.gameSetupInfo.gameParameters = savedGame.gameParameters
        newGameScreen.gameSetupInfo.mapParameters = savedGame.tileMap.mapParameters
        newGameScreen.updateRuleset()
        newGameScreen.updateTables()
        selectedScenarioSaveGame = savedGame
    }

    private fun addMapTypeSelection() {
        add("{Map Type}:".toLabel())
        val mapTypes = arrayListOf("Generated")
        if (MapSaver.getMaps().isNotEmpty()) mapTypes.add(MapType.custom)
        if (MapSaver.getScenarios().isNotEmpty() && UncivGame.Current.settings.extendedMapEditor)
            mapTypes.add(MapType.scenarioMap)
        if (GameSaver.getSaves().any { it.name().toLowerCase().endsWith("scenario") })
            mapTypes.add(MapType.scenario)
        mapTypeSelectBox = TranslatedSelectBox(mapTypes, "Generated", CameraStageBaseScreen.skin)

        val mapFileSelectBox = getMapFileSelectBox()
        savedMapOptionsTable.defaults().pad(5f)
        savedMapOptionsTable.add("{Map file}:".toLabel()).left()
        // because SOME people gotta give the hugest names to their maps
        savedMapOptionsTable.add(mapFileSelectBox).maxWidth(newGameScreen.stage.width / 2)
                .right().row()


        val scenarioMapSelectBox = getScenarioFileSelectBox()
        scenarioMapOptionsTable.defaults().pad(5f)
        scenarioMapOptionsTable.add("{Scenario file}:".toLabel()).left()
        // because SOME people gotta give the hugest names to their maps
        scenarioMapOptionsTable.add(scenarioMapSelectBox).maxWidth(newGameScreen.stage.width / 2)
                .right().row()


        val scenarioSelectBox = SelectBox<FileHandle>(CameraStageBaseScreen.skin)
        for (savedGame in GameSaver.getSaves()) {
            if (savedGame.name().toLowerCase().endsWith("scenario"))
                scenarioSelectBox.items.add(savedGame)
        }
        scenarioSelectBox.items = scenarioSelectBox.items // it doesn't register them until you do this.
        scenarioSelectBox.selected = scenarioSelectBox.items.first()
        // needs to be after the item change, so it doesn't activate before we choose the Scenario maptype
        scenarioSelectBox.onChange { selectSavedGameAsScenario(scenarioSelectBox.selected) }
        scenarioOptionsTable.add("{Scenario file}:".toLabel()).left()
        scenarioOptionsTable.add(scenarioSelectBox)

        fun updateOnMapTypeChange() {
            mapTypeSpecificTable.clear()
            if (mapTypeSelectBox.selected.value == MapType.custom) {
                mapParameters.type = MapType.custom
                mapParameters.name = mapFileSelectBox.selected
                mapTypeSpecificTable.add(savedMapOptionsTable)
                newGameScreen.gameSetupInfo.gameParameters.godMode = false
                newGameScreen.unlockTables()
                newGameScreen.updateTables()
            } else if (mapTypeSelectBox.selected.value == MapType.scenarioMap) {
                mapParameters.type = MapType.scenarioMap
                mapParameters.name = scenarioMapSelectBox.selected
                mapTypeSpecificTable.add(scenarioMapOptionsTable)
                val scenario = MapSaver.loadScenario(mapParameters.name)
                newGameScreen.gameSetupInfo.gameParameters = scenario.gameParameters
                newGameScreen.gameSetupInfo.mapParameters = mapParameters
                newGameScreen.updateRuleset()
                // update PlayerTable and GameOptionsTable
                newGameScreen.lockTables()
                newGameScreen.updateTables()
            } else if(mapTypeSelectBox.selected.value == MapType.scenario){
                selectSavedGameAsScenario(scenarioSelectBox.selected)
                mapTypeSpecificTable.add(scenarioOptionsTable)
                newGameScreen.lockTables()
            } else { // generated map
                mapParameters.name = ""
                mapParameters.type = generatedMapOptionsTable.mapTypeSelectBox.selected.value
                newGameScreen.gameSetupInfo.gameParameters.godMode = false
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