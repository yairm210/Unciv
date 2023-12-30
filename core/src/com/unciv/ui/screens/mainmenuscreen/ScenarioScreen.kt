package com.unciv.ui.screens.mainmenuscreen

import com.badlogic.gdx.files.FileHandle
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.utils.Concurrency

class ScenarioScreen: PickerScreen() {

    private var scenarioToLoad: FileHandle? = null

    init {
        val scenarioFiles = game.files.getScenarioFiles()
        rightSideButton.setText("Choose scenario")
        topTable.defaults().pad(5f)
        Concurrency.run {
            for ((file, mod) in scenarioFiles) {
                try {
                    val scenarioPreview = game.files.loadGamePreviewFromFile(file)
                    Concurrency.runOnGLThread {
                        topTable.add(file.name().toTextButton().onClick {
                            descriptionLabel.setText("Mod: [${mod.name}]".tr())
                            scenarioToLoad = file
                            rightSideButton.setText(file.name())
                            rightSideButton.enable()
                        }).row()
                    }
                } catch (ex: Exception) { } // invalid, couldn't even load preview, probably invalid json
            }
        }

        rightSideButton.onClick {
            if (scenarioToLoad != null)
                Concurrency.run {
                    val scenario = game.files.loadScenario(scenarioToLoad!!)
                    game.loadGame(scenario)
                }
        }

        setDefaultCloseAction()
    }
}
