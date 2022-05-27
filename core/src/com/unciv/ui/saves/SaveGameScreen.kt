package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.models.translations.tr
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.popup.YesNoPopup
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel
import com.unciv.ui.utils.toTextButton
import java.util.concurrent.CancellationException


class SaveGameScreen(val gameInfo: GameInfo) : LoadOrSaveScreen("Current saves") {
    private val gameNameTextField = TextField("", skin)

    init {
        setDefaultCloseAction()

        rightSideTable.initRightSideTable()

        rightSideButton.setText("Save game".tr())
        val saveAction = {
            if (game.gameSaver.getSave(gameNameTextField.text).exists())
                YesNoPopup("Overwrite existing file?", { saveGame() }, this).open()
            else saveGame()
        }
        rightSideButton.onClick(saveAction)
        rightSideButton.enable()

        keyPressDispatcher[KeyCharAndCode.RETURN] = saveAction
        stage.keyboardFocus = gameNameTextField
    }

    private fun Table.initRightSideTable() {
        addGameNameField()

        val copyJsonButton = "Copy to clipboard".toTextButton()
        copyJsonButton.onClick(::copyToClipboardHandler)
        val ctrlC = KeyCharAndCode.ctrl('c')
        keyPressDispatcher[ctrlC] = ::copyToClipboardHandler
        copyJsonButton.addTooltip(ctrlC)
        add(copyJsonButton).row()

        addSaveToCustomLocation()
        add(deleteSaveButton).row()
        add(showAutosavesCheckbox).row()
    }

    private fun Table.addGameNameField() {
        gameNameTextField.setTextFieldFilter { _, char -> char != '\\' && char != '/' }
        val defaultSaveName = "[${gameInfo.currentPlayer}] - [${gameInfo.turns}] turns".tr()
        gameNameTextField.text = defaultSaveName
        gameNameTextField.setSelection(0, defaultSaveName.length)

        add("Saved game name".toLabel()).row()
        add(gameNameTextField).width(300f).row()
        stage.keyboardFocus = gameNameTextField
    }

    private fun copyToClipboardHandler() {
        launchCrashHandling("Copy game to clipboard") {
            // the Gzip rarely leads to ANRs
            try {
                Gdx.app.clipboard.contents = GameSaver.gameInfoToString(gameInfo, forceZip = true)
            } catch (ex: Throwable) {
                ex.printStackTrace()
                ToastPopup("Could not save game to clipboard!", this@SaveGameScreen)
            }
        }
    }

    private fun Table.addSaveToCustomLocation() {
        if (!game.gameSaver.canLoadFromCustomSaveLocation()) return
        val saveToCustomLocation = "Save to custom location".toTextButton()
        val errorLabel = "".toLabel(Color.RED)
        saveToCustomLocation.onClick {
            errorLabel.setText("")
            saveToCustomLocation.setText("Saving...".tr())
            saveToCustomLocation.disable()
            launchCrashHandling("Save to custom location", runAsDaemon = false) {
                game.gameSaver.saveGameToCustomLocation(gameInfo, gameNameTextField.text) { result ->
                    if (result.isError()) {
                        errorLabel.setText("Could not save game to custom location!".tr())
                        result.exception?.printStackTrace()
                    } else if (result.isSuccessful()) {
                        game.resetToWorldScreen()
                    }
                    saveToCustomLocation.enable()
                }
            }
        }
        add(saveToCustomLocation).row()
        add(errorLabel).row()
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

    override fun onExistingSaveSelected(saveGameFile: FileHandle) {
        gameNameTextField.text = saveGameFile.name()
    }

}
