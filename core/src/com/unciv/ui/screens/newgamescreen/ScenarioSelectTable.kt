package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.SerializationException
import com.unciv.Constants
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.files.UncivFiles
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.widgets.TranslatedSelectBox
import com.unciv.ui.popups.ToastPopup
import com.unciv.utils.Concurrency

internal class ScenarioSelectTable(private val newGameScreen: NewGameScreen) : Table() {

    data class ScenarioData(val name:String, val file: FileHandle) {
        var preview: GameInfoPreview? = null
    }

    val scenarios = HashMap<String, ScenarioData>()
    var selectedScenario: ScenarioData? = null
    private var scenarioSelectBox: TranslatedSelectBox? = null

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
        for ((file, _) in scenarioFiles)
            scenarios[file.name()] = ScenarioData(file.name(), file)

        scenarioSelectBox = TranslatedSelectBox(scenarios.keys.sorted(), scenarios.keys.first())
        scenarioSelectBox!!.onChange { selectScenario() }
        clear()
        add(scenarioSelectBox)
    }

    fun selectScenario() {
        val scenario = scenarios[scenarioSelectBox!!.selected.value]!!
        val preload = scenario.getCachedPreview() ?: return
        newGameScreen.gameSetupInfo.gameParameters.players = preload.gameParameters.players
            .apply { removeAll { it.chosenCiv == Constants.spectator } }
        newGameScreen.gameSetupInfo.gameParameters.baseRuleset = preload.gameParameters.baseRuleset
        newGameScreen.gameSetupInfo.gameParameters.mods = preload.gameParameters.mods
        newGameScreen.tryUpdateRuleset(true)
        newGameScreen.playerPickerTable.update()
        selectedScenario = scenario
    }

    private fun ScenarioData.getCachedPreview(): GameInfoPreview? {
        if (preview == null) {
            try {
                preview = newGameScreen.game.files.loadGamePreviewFromFile(file)
            } catch (_: SerializationException) {
                ToastPopup("Scenario file [${file.name()}] is invalid.", newGameScreen)
            }
        }
        return preview
    }
}
