package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.UncivFiles
import com.unciv.models.translations.tr
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.UncivTextField
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread


class SaveGameScreen(val gameInfo: GameInfo) : LoadOrSaveScreen("Current saves") {
    private val gameNameTextField = UncivTextField.create("Saved game name")

    init {
        setDefaultCloseAction()

        rightSideTable.initRightSideTable()

        rightSideButton.setText("Save game".tr())
        rightSideButton.onActivation {
            if (game.files.getSave(gameNameTextField.text).exists())
                ConfirmPopup(
                    this,
                    "Overwrite existing file?",
                    "Overwrite",
                ) { saveGame() }.open()
            else saveGame()
        }
        rightSideButton.keyShortcuts.add(KeyCharAndCode.RETURN)
        rightSideButton.enable()

        stage.keyboardFocus = gameNameTextField
    }

    private fun Table.initRightSideTable() {
        addGameNameField()

        val copyJsonButton = "Copy to clipboard".toTextButton()
        copyJsonButton.onActivation { copyToClipboardHandler() }
        val ctrlC = KeyCharAndCode.ctrl('c')
        copyJsonButton.keyShortcuts.add(ctrlC)
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
        Concurrency.run("Copy game to clipboard") {
            // the Gzip rarely leads to ANRs
            try {
                Gdx.app.clipboard.contents = UncivFiles.gameInfoToString(gameInfo, forceZip = true)
            } catch (ex: Throwable) {
                ex.printStackTrace()
                launchOnGLThread {
                    ToastPopup("Could not save game to clipboard!", this@SaveGameScreen)
                }
            }
        }
    }

    private fun Table.addSaveToCustomLocation() {
        if (!game.files.canLoadFromCustomSaveLocation()) return
        val saveToCustomLocation = "Save to custom location".toTextButton()
        val errorLabel = "".toLabel(Color.RED)
        saveToCustomLocation.onClick {
            errorLabel.setText("")
            saveToCustomLocation.setText("Saving...".tr())
            saveToCustomLocation.disable()
            Concurrency.runOnNonDaemonThreadPool("Save to custom location") {
                game.files.saveGameToCustomLocation(gameInfo, gameNameTextField.text) { result ->
                    if (result.isError()) {
                        errorLabel.setText("Could not save game to custom location!".tr())
                        result.exception?.printStackTrace()
                    } else if (result.isSuccessful()) {
                        game.popScreen()
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
        Concurrency.runOnNonDaemonThreadPool("SaveGame") {
            game.files.saveGame(gameInfo, gameNameTextField.text) {
                launchOnGLThread {
                    if (it != null) ToastPopup("Could not save game!", this@SaveGameScreen)
                    else UncivGame.Current.popScreen()
                }
            }
        }
    }

    override fun onExistingSaveSelected(saveGameFile: FileHandle) {
        gameNameTextField.text = saveGameFile.name()
    }

}
