package com.unciv.ui.saves

import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameSaver
import com.unciv.logic.UncivShowableException
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import java.text.SimpleDateFormat
import java.util.*

class LoadGameScreen : PickerScreen() {
    lateinit var selectedSave:String
    private val copySavedGameToClipboardButton = TextButton("Copy saved game to clipboard".tr(),skin)
    private val saveTable = Table()
    private val deleteSaveButton = TextButton("Delete save".tr(), skin)
    private val showAutosavesCheckbox = CheckBox("Show autosaves".tr(), skin)

    init {
        setDefaultCloseAction()

        resetWindowState()
        topTable.add(ScrollPane(saveTable)).height(stage.height*2/3)

        val rightSideTable = getRightSideTable()

        topTable.add(rightSideTable)

        setAcceptButtonAction("") {
            try {
                UncivGame.Current.loadGame(selectedSave)
            }
            catch (ex:Exception){
                val cantLoadGamePopup = Popup(this)
                cantLoadGamePopup.addGoodSizedLabel("It looks like your saved game can't be loaded!").row()
                if (ex is UncivShowableException && ex.localizedMessage != null) {
                    // thrown exceptions are our own tests and can be shown to the user
                    cantLoadGamePopup.addGoodSizedLabel(ex.localizedMessage).row()
                    cantLoadGamePopup.addCloseButton()
                    cantLoadGamePopup.open()
                } else {
                    cantLoadGamePopup.addGoodSizedLabel("If you could copy your game data (\"Copy saved game to clipboard\" - ").row()
                    cantLoadGamePopup.addGoodSizedLabel("  paste into an email to yairm210@hotmail.com)").row()
                    cantLoadGamePopup.addGoodSizedLabel("I could maybe help you figure out what went wrong, since this isn't supposed to happen!").row()
                    cantLoadGamePopup.addCloseButton()
                    cantLoadGamePopup.open()
                    ex.printStackTrace()
                }
            }
        }

    }

    private fun getRightSideTable(): Table {
        val rightSideTable = Table()

        val errorLabel = "".toLabel(Color.RED)
        val loadFromClipboardButton = TextButton("Load copied data".tr(), skin)
        val loadFromClipboardAction = {
            try {
                val clipboardContentsString = Gdx.app.clipboard.contents.trim()
                val decoded = Gzip.unzip(clipboardContentsString)
                val loadedGame = GameSaver.gameInfoFromString(decoded)
                UncivGame.Current.loadGame(loadedGame)
            } catch (ex: Exception) {
                errorLabel.setText("Could not load game from clipboard!".tr())
                ex.printStackTrace()
            }
        }
        loadFromClipboardButton.onClick (loadFromClipboardAction)
        rightSideTable.add(loadFromClipboardButton).pad(5f).row()
        rightSideTable.add(errorLabel).pad(5f).row()
        registerKeyHandler(Constants.asciiCtrlV, loadFromClipboardAction)

        val deleteAction =  {
            YesNoPopup("Are you sure you want to delete this save?", {
                GameSaver.deleteSave(selectedSave)
                resetWindowState()
            }, this).open()
        }
        deleteSaveButton.onClick (deleteAction)
        deleteSaveButton.disable()
        rightSideTable.add(deleteSaveButton).pad(5f).row()
        val checkedDelAction = { if (deleteSaveButton.touchable == Touchable.enabled) deleteAction() }
        registerKeyHandler (Constants.asciiDelete, checkedDelAction)

        copySavedGameToClipboardButton.disable()
        copySavedGameToClipboardButton.onClick {
            val gameText = GameSaver.getSave(selectedSave).readString()
            val gzippedGameText = Gzip.zip(gameText)
            Gdx.app.clipboard.contents = gzippedGameText
        }
        rightSideTable.add(copySavedGameToClipboardButton).pad(5f).row()

        showAutosavesCheckbox.isChecked = false
        showAutosavesCheckbox.onChange {
                updateLoadableGames(showAutosavesCheckbox.isChecked)
            }
        rightSideTable.add(showAutosavesCheckbox).pad(5f).row()
        return rightSideTable
    }

    private fun resetWindowState() {
        updateLoadableGames(showAutosavesCheckbox.isChecked)
        deleteSaveButton.disable()
        copySavedGameToClipboardButton.disable()
        rightSideButton.setText("Load game".tr())
        rightSideButton.disable()
        descriptionLabel.setText("")
    }

    private fun updateLoadableGames(showAutosaves:Boolean) {
        saveTable.clear()
        clearKeyHandlers()
        for (save in GameSaver.getSaves().sortedByDescending { GameSaver.getSave(it).lastModified() }) {
            if(save.startsWith("Autosave") && !showAutosaves) continue
            val textButton = TextButton(save, skin)
            val action = {
                selectedSave = save
                copySavedGameToClipboardButton.enable()
                var textToSet = save

                val savedAt = Date(GameSaver.getSave(save).lastModified())
                textToSet += "\n{Saved at}: ".tr() + SimpleDateFormat("dd-MM-yy HH.mm").format(savedAt)
                try {
                    val game = GameSaver.loadGameByName(save)
                    val playerCivNames = game.civilizations.filter { it.isPlayerCivilization() }.joinToString { it.civName.tr() }
                    textToSet += "\n" + playerCivNames +
                            ", " + game.difficulty.tr() + ", {Turn} ".tr() + game.turns
                } catch (ex: Exception) {
                    textToSet += "\n{Could not load game}!".tr()
                }
                descriptionLabel.setText(textToSet)
                rightSideButton.setText("Load [$save]".tr())
                rightSideButton.enable()
                deleteSaveButton.enable()
                deleteSaveButton.color = Color.RED
            }
            textButton.onClick (action)
            registerKeyHandler (save, action)
            saveTable.add(textButton).pad(5f).row()
        }
    }

}

