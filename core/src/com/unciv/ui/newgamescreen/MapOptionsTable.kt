package com.unciv.ui.newgamescreen

import com.badlogic.gdx.Gdx
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

    fun getScenarioFiles(): Sequence<FileHandle> {
        val localSaveScenarios = GameSaver.getSaves().filter { it.name().toLowerCase().endsWith("scenario") }
        val modScenarios = Gdx.files.local("mods").list().asSequence()
                .filter { it.child("scenarios").exists() }.flatMap { it.child("scenarios").list().asSequence() }
        return localSaveScenarios + modScenarios
    }

    private fun addMapTypeSelection() {
        add("{Map Type}:".toLabel())
        val mapTypes = arrayListOf("Generated")
        if (MapSaver.getMaps().isNotEmpty()) mapTypes.add(MapType.custom)
        if (MapSaver.getScenarios().isNotEmpty() && UncivGame.Current.settings.extendedMapEditor)
            mapTypes.add(MapType.scenarioMap)
        if (getScenarioFiles().any())
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


        val scenarioFiles = getScenarioFiles()
        val scenarioSelectBox = SelectBox<FileHandleWrapper>(CameraStageBaseScreen.skin)
        if (scenarioFiles.any()) {
            for (savedGame in getScenarioFiles()) {
                scenarioSelectBox.items.add(FileHandleWrapper(savedGame))
            }
            scenarioSelectBox.items = scenarioSelectBox.items // it doesn't register them until you do this.
            scenarioSelectBox.selected = scenarioSelectBox.items.first()
            // needs to be after the item change, so it doesn't activate before we choose the Scenario maptype
            scenarioSelectBox.onChange { selectSavedGameAsScenario(scenarioSelectBox.selected.fileHandle) }
            scenarioOptionsTable.add("{Scenario file}:".toLabel()).left()
            scenarioOptionsTable.add(scenarioSelectBox)
        }

        fun updateOnMapTypeChange() {
            mapTypeSpecificTable.clear()
            if (mapTypeSelectBox.selected.value == MapType.custom) {
                mapParameters.type = MapType.custom
                mapParameters.name = mapFileSelectBox.selected.toString()
                mapTypeSpecificTable.add(savedMapOptionsTable)
                newGameScreen.gameSetupInfo.gameParameters.godMode = false
                newGameScreen.unlockTables()
                newGameScreen.updateTables()
            } else if (mapTypeSelectBox.selected.value == MapType.scenarioMap) {
                mapParameters.type = MapType.scenarioMap
                mapParameters.name = scenarioMapSelectBox.selected.toString()
                mapTypeSpecificTable.add(scenarioMapOptionsTable)
                val scenario = MapSaver.loadScenario(mapParameters.name)
                newGameScreen.gameSetupInfo.gameParameters = scenario.gameParameters
                newGameScreen.gameSetupInfo.mapParameters = mapParameters
                newGameScreen.updateRuleset()
                // update PlayerTable and GameOptionsTable
                newGameScreen.lockTables()
                newGameScreen.updateTables()
            } else if(mapTypeSelectBox.selected.value == MapType.scenario){
                selectSavedGameAsScenario(scenarioSelectBox.selected.fileHandle)
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

    private fun getMapFileSelectBox(): SelectBox<FileHandleWrapper> {
        val mapFileSelectBox = SelectBox<FileHandleWrapper>(CameraStageBaseScreen.skin)
        val mapFiles = Array<FileHandleWrapper>()
        for (mapFile in MapSaver.getMaps())
            mapFiles.add(FileHandleWrapper(mapFile))
        for(mod in Gdx.files.local("mods").list()){
            val mapsFolder = mod.child("maps")
            if(mapsFolder.exists())
                for(map in mapsFolder.list())
                    mapFiles.add(FileHandleWrapper(map))
        }
        mapFileSelectBox.items = mapFiles
        val selectedItem = mapFiles.firstOrNull { it.fileHandle.name()==mapParameters.name }
        if(selectedItem!=null) mapFileSelectBox.selected = selectedItem

        mapFileSelectBox.onChange {
            val mapFile =  mapFileSelectBox.selected.fileHandle
            mapParameters.name = mapFile.name()
            newGameScreen.gameSetupInfo.mapFile = mapFile
        }
        return mapFileSelectBox
    }

    private fun getScenarioFileSelectBox(): SelectBox<FileHandleWrapper> {
        val scenarioFileSelectBox = SelectBox<FileHandleWrapper>(CameraStageBaseScreen.skin)
        val scenarioFiles = Array<FileHandleWrapper>()
        for (scenarioName in MapSaver.getScenarios()) scenarioFiles.add(FileHandleWrapper(scenarioName))
        scenarioFileSelectBox.items = scenarioFiles
        val selectedItem = scenarioFiles.firstOrNull { it.fileHandle.name()==mapParameters.name }
        if(selectedItem!=null ) scenarioFileSelectBox.selected = selectedItem

        scenarioFileSelectBox.onChange {
            mapParameters.name = scenarioFileSelectBox.selected!!.fileHandle.name()
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

    // The SelectBox auto displays the text a object.toString(), which on the FileHandle itself includes the folder path.
    //  So we wrap it in another object with a custom toString()
    class FileHandleWrapper(val fileHandle: FileHandle){
        override fun toString() = fileHandle.name()
    }
}