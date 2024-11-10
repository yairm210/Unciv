package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.map.MapGeneratedMainType
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.widgets.TranslatedSelectBox
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency

class ScenarioSelectTable(val newGameScreen: NewGameScreen) : Table() {
    
    data class ScenarioData(val name:String, val file: FileHandle){
        var preview: GameInfoPreview? = null
    }
    
    val scenarios = HashMap<String, ScenarioData>()
    lateinit var selectedScenario: ScenarioData
    var scenarioSelectBox: TranslatedSelectBox? = null
    
    init {
        // Only the first so it's fast
        val firstScenarioFile = newGameScreen.game.files.getScenarioFiles().firstOrNull()
        if (firstScenarioFile != null) {
            createScenarioSelectBox(listOf(firstScenarioFile))
            Concurrency.run {
                val scenarioFiles = newGameScreen.game.files.getScenarioFiles().toList()
                Concurrency.runOnGLThread {
                    createScenarioSelectBox(scenarioFiles)
                }
            }
        }
    }

    private fun createScenarioSelectBox(scenarioFiles: List<Pair<FileHandle, Ruleset>>) {
        for ((file, ruleset) in scenarioFiles)
            scenarios[file.name()] = ScenarioData(file.name(), file)

        scenarioSelectBox = TranslatedSelectBox(scenarios.keys.sorted(), scenarios.keys.first())
        scenarioSelectBox!!.onChange { selectScenario() }
        clear()
        add(scenarioSelectBox)
    }

    fun selectScenario(){
        val scenario = scenarios[scenarioSelectBox!!.selected.value]!!
        val preload = if (scenario.preview != null) scenario.preview!! else {
            val preview = newGameScreen.game.files.loadGamePreviewFromFile(scenario.file)
            scenario.preview = preview
            preview
        }
        newGameScreen.gameSetupInfo.gameParameters.players = preload.gameParameters.players
            .apply { removeAll { it.chosenCiv == Constants.spectator } }
        newGameScreen.gameSetupInfo.gameParameters.baseRuleset = preload.gameParameters.baseRuleset
        newGameScreen.gameSetupInfo.gameParameters.mods = preload.gameParameters.mods
        newGameScreen.tryUpdateRuleset(true)
        newGameScreen.playerPickerTable.update()
        selectedScenario = scenario
    }
}

class MapOptionsTable(private val newGameScreen: NewGameScreen) : Table() {

    private val mapParameters = newGameScreen.gameSetupInfo.mapParameters
    private var mapTypeSpecificTable = Table()
    internal val generatedMapOptionsTable = MapParametersTable(newGameScreen, mapParameters, MapGeneratedMainType.generated)
    private val randomMapOptionsTable = MapParametersTable(newGameScreen, mapParameters, MapGeneratedMainType.randomGenerated)
    private val savedMapOptionsTable = MapFileSelectTable(newGameScreen, mapParameters)
    private val scenarioOptionsTable = ScenarioSelectTable(newGameScreen)
    internal val mapTypeSelectBox: TranslatedSelectBox

    init {
        //defaults().pad(5f) - each nested table having the same can give 'stairs' effects,
        // better control directly. Besides, the first Labels/Buttons should have 10f to look nice
        background = BaseScreen.skinStrings.getUiBackground("NewGameScreen/MapOptionsTable", tintColor = BaseScreen.skinStrings.skinConfig.clearColor)

        val mapTypes = arrayListOf(MapGeneratedMainType.generated, MapGeneratedMainType.randomGenerated)
        if (savedMapOptionsTable.isNotEmpty()) mapTypes.add(MapGeneratedMainType.custom)
        if (newGameScreen.game.files.getScenarioFiles().any()) mapTypes.add(MapGeneratedMainType.scenario)

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

        // activate once, so the MapGeneratedMainType.generated controls show
        updateOnMapTypeChange()

        mapTypeSelectBox.onChange { updateOnMapTypeChange() }

        val mapTypeSelectWrapper = Table()  // wrap to center-align Label and SelectBox easier
        mapTypeSelectWrapper.add("{Map Type}:".toLabel()).left().expandX()
        mapTypeSelectWrapper.add(mapTypeSelectBox).right()
        add(mapTypeSelectWrapper).pad(10f).fillX().row()
        add(mapTypeSpecificTable).row()
    }
    
    fun getSelectedScenario(): ScenarioSelectTable.ScenarioData? {
        if (mapTypeSelectBox.selected.value != MapGeneratedMainType.scenario) return null
        return scenarioOptionsTable.selectedScenario
    }

    internal fun cancelBackgroundJobs() = savedMapOptionsTable.cancelBackgroundJobs()
}
