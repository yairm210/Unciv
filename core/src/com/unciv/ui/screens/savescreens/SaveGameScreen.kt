package com.unciv.ui.screens.savescreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.files.UncivFiles
import com.unciv.models.translations.tr
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.keyShortcuts
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
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
        gameNameTextField.setTextFieldFilter { _, char -> char != '\\' && char != '/' }
        val defaultSaveName = "[${gameInfo.currentPlayer}] - [${gameInfo.turns}] turns".tr()
        gameNameTextField.text = defaultSaveName
        gameNameTextField.setSelection(0, defaultSaveName.length)

        add("Saved game name".toLabel()).row()
        add(gameNameTextField).width(300f).row()
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
        val saveToCustomLocation = "Save to custom location".toTextButton()
        val errorLabel = "".toLabel(Color.RED)
        saveToCustomLocation.onClick {
            errorLabel.setText("")
            saveToCustomLocation.setText("Saving...".tr())
            saveToCustomLocation.disable()
            Concurrency.runOnNonDaemonThreadPool("Save to custom location") {

                game.files.saveGameToCustomLocation(gameInfo, gameNameTextField.text,
                    {
                        game.popScreen()
                        saveToCustomLocation.enable()
                    },
                    {
                        errorLabel.setText("Could not save game to custom location!".tr())
                        it.printStackTrace()
                        saveToCustomLocation.enable()
                    }
                )
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

    override fun doubleClickAction() {
        ConfirmPopup(
            this,
            "Overwrite existing file?",
            "Overwrite",
        ) { saveGame() }.open()
    }

}
