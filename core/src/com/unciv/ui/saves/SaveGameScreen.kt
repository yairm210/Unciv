package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Json
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import java.util.concurrent.CancellationException
import kotlin.concurrent.thread
import com.unciv.ui.utils.AutoScrollPane as ScrollPane


class SaveGameScreen(val gameInfo: GameInfo) : PickerScreen(disableScroll = true) {
    private val gameNameTextField = TextField("", skin)
    val currentSaves = Table()

    init {
        setDefaultCloseAction()

        gameNameTextField.textFieldFilter = TextField.TextFieldFilter { _, char -> char != '\\' && char != '/' }
        topTable.add("Current saves".toLabel()).pad(10f).row()
        updateShownSaves(false)
        topTable.add(ScrollPane(currentSaves))

        val newSave = Table()
        newSave.defaults().pad(5f, 10f)
        val defaultSaveName = gameInfo.currentPlayer + " -  " + gameInfo.turns + " turns"
        gameNameTextField.text = defaultSaveName

        newSave.add("Saved game name".toLabel()).row()
        newSave.add(gameNameTextField).width(300f).row()

        val copyJsonButton = "Copy to clipboard".toTextButton()
        copyJsonButton.onClick {
            val json = Json().toJson(gameInfo)
            val base64Gzip = Gzip.zip(json)
            Gdx.app.clipboard.contents = base64Gzip
        }
        newSave.add(copyJsonButton).row()
        if (GameSaver.canLoadFromCustomSaveLocation()) {
            val saveToCustomLocation = "Save to custom location".toTextButton()
            val errorLabel = "".toLabel(Color.RED)
            saveToCustomLocation.enable()
            saveToCustomLocation.onClick {
                errorLabel.setText("")
                saveToCustomLocation.setText("Saving...".tr())
                saveToCustomLocation.disable()
                thread(name = "SaveGame") {
                    GameSaver.saveGameToCustomLocation(gameInfo, gameNameTextField.text) { e ->
                        if (e == null) {
                            Gdx.app.postRunnable { game.setWorldScreen() }
                        } else if (e !is CancellationException) {
                            errorLabel.setText("Could not save game to custom location!".tr())
                            e.printStackTrace()
                        }
                        saveToCustomLocation.enable()
                    }
                }
            }
            newSave.add(saveToCustomLocation).row()
            newSave.add(errorLabel).row()
        }


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
            if (GameSaver.getSave(gameNameTextField.text).exists())
                YesNoPopup("Overwrite existing file?", { saveGame() }, this).open()
            else saveGame()
        }
        rightSideButton.enable()
    }

    private fun saveGame() {
        rightSideButton.setText("Saving...".tr())
        thread(name = "SaveGame") {
            GameSaver.saveGame(gameInfo, gameNameTextField.text) {
                Gdx.app.postRunnable {
                    if (it != null) ToastPopup("Could not save game!", this)
                    else UncivGame.Current.setWorldScreen()
                }
            }
        }
    }

    private fun updateShownSaves(showAutosaves: Boolean) {
        currentSaves.clear()
        val saves = GameSaver.getSaves()
                .sortedByDescending { it.lastModified() }
        for (saveGameFile in saves) {
            if (saveGameFile.name().startsWith("Autosave") && !showAutosaves) continue
            val textButton = saveGameFile.name().toTextButton()
            textButton.onClick {
                gameNameTextField.text = saveGameFile.name()
            }
            currentSaves.add(textButton).pad(5f).row()
        }
    }

}
