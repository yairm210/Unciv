package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.models.translations.tr
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.popup.YesNoPopup
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
        val defaultSaveName = "[${gameInfo.currentPlayer}] - [${gameInfo.turns}] turns".tr()
        gameNameTextField.text = defaultSaveName

        newSave.add("Saved game name".toLabel()).row()
        newSave.add(gameNameTextField).width(300f).row()

        val copyJsonButton = "Copy to clipboard".toTextButton()
        copyJsonButton.onClick {
            thread(name="Copy to clipboard") { // the Gzip rarely leads to ANRs
                try {
                    Gdx.app.clipboard.contents = GameSaver.gameInfoToString(gameInfo, forceZip = true)
                } catch (OOM: OutOfMemoryError) {
                    // you don't get a special toast, this isn't nearly common enough, this is a total edge-case
                }
            }
        }
        newSave.add(copyJsonButton).row()

        if (game.gameSaver.canLoadFromCustomSaveLocation()) {
            val saveText = "Save to custom location".tr()
            val saveToCustomLocation = TextButton(saveText, BaseScreen.skin)
            val errorLabel = "".toLabel(Color.RED)
            saveToCustomLocation.enable()
            saveToCustomLocation.onClick {
                errorLabel.setText("")
                saveToCustomLocation.setText("Saving...".tr())
                saveToCustomLocation.disable()
                launchCrashHandling("SaveGame", runAsDaemon = false) {
                    game.gameSaver.saveGameToCustomLocation(gameInfo, gameNameTextField.text) { location, e ->
                        if (e != null) {
                            errorLabel.setText("Could not save game to custom location!".tr())
                            e.printStackTrace()
                        } else if (location != null) {
                            game.resetToWorldScreen()
                        }
                        saveToCustomLocation.enable()
                        saveToCustomLocation.setText(saveText)
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
            if (game.gameSaver.getSave(gameNameTextField.text).exists())
                YesNoPopup("Overwrite existing file?", { saveGame() }, this).open()
            else saveGame()
        }
        rightSideButton.enable()
    }

    private fun saveGame() {
        rightSideButton.setText("Saving...".tr())
        launchCrashHandling("SaveGame", runAsDaemon = false) {
            game.gameSaver.saveGame(gameInfo, gameNameTextField.text) {
                postCrashHandlingRunnable {
                    if (it != null) ToastPopup("Could not save game!", this@SaveGameScreen)
                    else UncivGame.Current.resetToWorldScreen()
                }
            }
        }
    }

    private fun updateShownSaves(showAutosaves: Boolean) {
        currentSaves.clear()
        val saves = game.gameSaver.getSaves(autoSaves = showAutosaves)
                .sortedByDescending { it.lastModified() }
        for (saveGameFile in saves) {
            val textButton = saveGameFile.name().toTextButton()
            textButton.onClick {
                gameNameTextField.text = saveGameFile.name()
            }
            currentSaves.add(textButton).pad(5f).row()
        }
    }

}
