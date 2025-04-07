package com.unciv.ui.screens.savescreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
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
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread


class SaveGameScreen(private val gameInfo: GameInfo) : LoadOrSaveScreen("Current saves") {
    private val gameNameTextField = UncivTextField(nameFieldLabelText)

    companion object : Helpers {
        const val nameFieldLabelText = "Saved game name"
        const val saveButtonText = "Save game"
        const val savingText = "Saving..."
        const val saveToCustomText = "Save to custom location"
    }

    init {
        errorLabel.isVisible = false
        errorLabel.wrap = true

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
        copyJsonButton.onActivation(::copyToClipboardHandler)
        val ctrlC = KeyCharAndCode.ctrl('c')
        copyJsonButton.keyShortcuts.add(ctrlC)
        copyJsonButton.addTooltip(ctrlC)
        add(copyJsonButton).row()

        addSaveToCustomLocation()
        add(errorLabel).width(stage.width / 2).center().row()
        row() // For uniformity with LoadScreen which has a load missing mods button here
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
                launchOnGLThread {
                    ToastPopup("Current game copied to clipboard!", this@SaveGameScreen)
                }
            } catch (ex: Throwable) {
                Log.error(saveToClipboardErrorMessage, ex)
                launchOnGLThread {
                    ToastPopup(saveToClipboardErrorMessage, this@SaveGameScreen)
                }
            }
        }
    }

    private fun Table.addSaveToCustomLocation() {
        val saveToCustomLocation = saveToCustomText.toTextButton()
        saveToCustomLocation.onClick {
            saveToCustomLocation.setText(savingText.tr())
            saveToCustomLocation.disable()
            errorLabel.isVisible = false
            Concurrency.runOnNonDaemonThreadPool(saveToCustomText) {

                game.files.saveGameToCustomLocation(gameInfo, gameNameTextField.text,
                    {
                        game.popScreen()
                    },
                    {
                        if (it !is PlatformSaverLoader.Cancelled) {
                            handleException(it, "Could not save game to custom location!")
                        }
                        saveToCustomLocation.setText(saveToCustomText.tr())
                        saveToCustomLocation.enable()
                    }
                )
            }
        }
        add(saveToCustomLocation).row()
    }

    private fun saveGame() {
        rightSideButton.setText(savingText.tr())
        errorLabel.isVisible = false
        Concurrency.runOnNonDaemonThreadPool("SaveGame") {
            game.files.saveGame(gameInfo, gameNameTextField.text) {
                launchOnGLThread {
                    if (it != null) {
                        handleException(it, "Could not save game!", game.files.getSave(gameNameTextField.text))
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
