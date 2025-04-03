package com.unciv.ui.screens.savescreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.files.PlatformSaverLoader
import com.unciv.logic.files.UncivFiles
import com.unciv.models.translations.tr
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.savescreens.LoadGameScreen.Companion.getLoadExceptionMessage
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread


class SaveGameScreen(val gameInfo: GameInfo) : LoadOrSaveScreen("Current saves") {
    companion object {
        const val nameFieldLabelText = "Saved game name"
        const val saveButtonText = "Save game"
        const val savingText = "Saving..."
        const val saveToCustomText = "Save to custom location"
    }

    private val gameNameTextField = UncivTextField(nameFieldLabelText)

    init {
        setDefaultCloseAction()

        rightSideTable.initRightSideTable()

        rightSideButton.setText(saveButtonText.tr())
        rightSideButton.onActivation {
            if (game.files.getSave(gameNameTextField.text).exists())
                doubleClickAction()
            else saveGame()
        }
        rightSideButton.keyShortcuts.add(KeyCharAndCode.RETURN)
        rightSideButton.enable()
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
        gameNameTextField.textFieldFilter = UncivFiles.fileNameTextFieldFilter()
        gameNameTextField.setTextFieldListener { textField, _ -> enableSaveButton(textField.text) }
        val defaultSaveName = "[${gameInfo.currentPlayer}] - [${gameInfo.turns}] turns".tr(hideIcons = true)
        gameNameTextField.text = defaultSaveName
        gameNameTextField.setSelection(0, defaultSaveName.length)

        add(nameFieldLabelText.toLabel()).row()
        add(gameNameTextField).width(300f).row()
    }

    private fun enableSaveButton(text: String) {
        rightSideButton.isEnabled = UncivFiles.isValidFileName(text)
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
        val saveToCustomLocation = saveToCustomText.toTextButton()
        val errorLabel = "".toLabel(Color.RED)
        saveToCustomLocation.onClick {
            errorLabel.setText("")
            saveToCustomLocation.setText(savingText.tr())
            saveToCustomLocation.disable()
            Concurrency.runOnNonDaemonThreadPool(saveToCustomText) {

                game.files.saveGameToCustomLocation(gameInfo, gameNameTextField.text,
                    {
                        game.popScreen()
                    },
                    {
                        if (it !is PlatformSaverLoader.Cancelled) {
                            errorLabel.setText("Could not save game to custom location!".tr())
                            it.printStackTrace()
                        }
                        saveToCustomLocation.setText(saveToCustomText.tr())
                        saveToCustomLocation.enable()
                    }
                )
            }
        }
        add(saveToCustomLocation).row()
        add(errorLabel).row()
    }

    private fun saveGame() {
        rightSideButton.setText(savingText.tr())
        Concurrency.runOnNonDaemonThreadPool("SaveGame") {
            game.files.saveGame(gameInfo, gameNameTextField.text) {
                launchOnGLThread {
                    if (it != null) {
                        Log.error("Error saving loading game", it)
                        val (errorText, _) = getLoadExceptionMessage(it, "Could not save game!", game.files.getSave(gameNameTextField.text))
                        ToastPopup(errorText, this@SaveGameScreen)
                        rightSideButton.setText(saveButtonText.tr())
                    }
                    else UncivGame.Current.popScreen()
                }
            }
        }
    }

    override fun onExistingSaveSelected(saveGameFile: FileHandle) {
        gameNameTextField.text = saveGameFile.name()
    }

    override fun doubleClickAction() {
        ConfirmPopup(
            this,
            "Overwrite existing file?",
            "Overwrite",
        ) { saveGame() }.open()
    }

}
