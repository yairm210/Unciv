package com.unciv.ui.screens.mainmenuscreen

import com.badlogic.gdx.files.FileHandle
import com.unciv.logic.UncivShowableException
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.screens.savescreens.VerticalFileListScrollPane
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

class ScenarioScreen: PickerScreen(disableScroll = true) {
    private val scenarioScrollPane = VerticalFileListScrollPane()

    private var scenarioToLoad: FileHandle? = null

    /** Caches the mod names while the getScenarioFiles Sequence is iterated */
    private val sourceMods = mutableMapOf<FileHandle, String>()

    init {
        scenarioScrollPane.onChange(::selectScenario)
        scenarioScrollPane.onDoubleClick(action = ::loadSelectedScenario)
        topTable.defaults().pad(5f)
        topTable.add(scenarioScrollPane).grow().row()

        rightSideButton.setText("Choose scenario")
        rightSideButton.onActivation(::loadSelectedScenario)
        rightSideButton.keyShortcuts.add(KeyCharAndCode.RETURN)

        setDefaultCloseAction()

        scenarioScrollPane.update(
            game.files.getScenarioFiles().map {
                sourceMods[it.first] = it.second.name
                it.first
            }
        )
    }

    private fun selectScenario(file: FileHandle) {
        descriptionLabel.setText(sourceMods[file]?.let { "Mod: [$it]".tr() })
        scenarioToLoad = file
        rightSideButton.setText(file.name())
        rightSideButton.enable()
    }

    private fun loadSelectedScenario() {
        val file = scenarioToLoad ?: return
        Concurrency.run("LoadScenario") {
            try {
                val scenario = game.files.loadScenario(file)
                game.loadGame(scenario)
            } catch (ex: UncivShowableException) {
                launchOnGLThread {
                    ToastPopup("{Error loading scenario:}\n{${ex.message}}", this@ScenarioScreen)
                }
            }
        }
    }
}
