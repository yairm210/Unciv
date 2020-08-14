package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Json
import com.unciv.UncivGame
import com.unciv.logic.GameSaver
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import kotlin.concurrent.thread
import com.unciv.ui.utils.AutoScrollPane as ScrollPane


class SaveGameScreen : PickerScreen() {
    val textField = TextField("", skin)
    val currentSaves = Table()

    init {
        setDefaultCloseAction()

        textField.textFieldFilter = TextField.TextFieldFilter { _, char -> char != '\\' && char != '/' }
        currentSaves.add("Current saves".toLabel()).row()
        updateShownSaves(false)
        topTable.add(ScrollPane(currentSaves)).height(stage.height * 2 / 3)

        val newSave = Table()
        val defaultSaveName = game.gameInfo.currentPlayer + " -  " + game.gameInfo.turns + " turns"
        textField.text = defaultSaveName

        newSave.add("Saved game name".toLabel()).row()
        newSave.add(textField).width(300f).pad(10f).row()

        val copyJsonButton = "Copy to clipboard".toTextButton()
        copyJsonButton.onClick {
            val json = Json().toJson(game.gameInfo)
            val base64Gzip = Gzip.zip(json)
            Gdx.app.clipboard.contents = base64Gzip
        }
        newSave.add(copyJsonButton).row()


        val showAutosavesCheckbox = CheckBox("Show autosaves".tr(), skin)
        showAutosavesCheckbox.isChecked = false
        showAutosavesCheckbox.onChange {
            updateShownSaves(showAutosavesCheckbox.isChecked)
        }
        newSave.add(showAutosavesCheckbox).row()

        topTable.add(newSave)
        topTable.pack()

        rightSideButton.setText("Save game".tr())
        rightSideButton.onClick {
            rightSideButton.setText("Saving...".tr())
            thread(name = "SaveGame") {
                GameSaver.saveGame(UncivGame.Current.gameInfo, textField.text)
                Gdx.app.postRunnable { UncivGame.Current.setWorldScreen() }
            }
        }
        rightSideButton.enable()
    }

    fun updateShownSaves(showAutosaves: Boolean) {
        currentSaves.clear()
        val saves = GameSaver.getSaves()
                .sortedByDescending { it.lastModified() }
        for (saveGameFile in saves) {
            if (saveGameFile.name().startsWith("Autosave") && !showAutosaves) continue
            val textButton = saveGameFile.name().toTextButton()
            textButton.onClick {
                textField.text = saveGameFile.name()
            }
            currentSaves.add(textButton).pad(5f).row()
        }
    }

}