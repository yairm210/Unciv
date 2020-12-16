package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.GameSaver
import com.unciv.logic.UncivShowableException
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CancellationException
import kotlin.concurrent.thread
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class LoadGameScreen(previousScreen:CameraStageBaseScreen) : PickerScreen() {
    lateinit var selectedSave:String
    private val copySavedGameToClipboardButton = "Copy saved game to clipboard".toTextButton()
    private val saveTable = Table()
    private val deleteSaveButton = "Delete save".toTextButton()
    private val showAutosavesCheckbox = CheckBox("Show autosaves".tr(), skin)

    init {
        setDefaultCloseAction(previousScreen)

        resetWindowState()
        topTable.add(ScrollPane(saveTable)).height(stage.height*2/3)

        val rightSideTable = getRightSideTable()

        topTable.add(rightSideTable)

        rightSideButton.onClick {
            ToastPopup("Loading...", this)
            thread {
                try {
                    // This is what can lead to ANRs - reading the file and setting the transients, that's why this is in another thread
                    val loadedGame = GameSaver.loadGameByName(selectedSave)
                    Gdx.app.postRunnable { UncivGame.Current.loadGame(loadedGame) }
                } catch (ex: Exception) {
                    Gdx.app.postRunnable {
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
        }

    }

    private fun getRightSideTable(): Table {
        val rightSideTable = Table()

        val errorLabel = "".toLabel(Color.RED)
        val loadFromClipboardButton = "Load copied data".toTextButton()
        loadFromClipboardButton.onClick {
            try {
                val clipboardContentsString = Gdx.app.clipboard.contents.trim()
                val decoded = Gzip.unzip(clipboardContentsString)
                val loadedGame = GameSaver.gameInfoFromString(decoded)
                UncivGame.Current.loadGame(loadedGame)
            } catch (ex: Exception) {
                var text = "Could not load game from clipboard!".tr()
                if (ex is UncivShowableException) text += "\n"+ex.message
                errorLabel.setText(text)

                ex.printStackTrace()
            }
        }
        rightSideTable.add(loadFromClipboardButton).row()
        if (GameSaver.canLoadFromCustomSaveLocation()) {
            val loadFromCustomLocation = "Load from custom location".toTextButton()
            loadFromCustomLocation.onClick {
                GameSaver.loadGameFromCustomLocation { gameInfo, exception ->
                    if (gameInfo != null) {
                        game.loadGame(gameInfo)
                    } else if (exception !is CancellationException) {
                        errorLabel.setText("Could not load game from custom location!".tr())
                        exception?.printStackTrace()
                    }
                }
            }
            rightSideTable.add(loadFromCustomLocation).pad(10f).row()
        }
        rightSideTable.add(errorLabel).row()

        deleteSaveButton.onClick {
            GameSaver.deleteSave(selectedSave)
            resetWindowState()
        }
        deleteSaveButton.disable()
        rightSideTable.add(deleteSaveButton).row()

        copySavedGameToClipboardButton.disable()
        copySavedGameToClipboardButton.onClick {
            val gameText = GameSaver.getSave(selectedSave).readString()
            val gzippedGameText = Gzip.zip(gameText)
            Gdx.app.clipboard.contents = gzippedGameText
        }
        rightSideTable.add(copySavedGameToClipboardButton).row()

        showAutosavesCheckbox.isChecked = false
        showAutosavesCheckbox.onChange {
                updateLoadableGames(showAutosavesCheckbox.isChecked)
            }
        rightSideTable.add(showAutosavesCheckbox).row()
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

        val loadImage =ImageGetter.getImage("OtherIcons/Load")
        loadImage.setSize(50f,50f) // So the origin sets correctly
        loadImage.setOrigin(Align.center)
        loadImage.addAction(Actions.rotateBy(360f, 2f))
        saveTable.add(loadImage).size(50f)

        thread { // Apparently, even jut getting the list of saves can cause ANRs -
            // not sure how many saves these guys had but Google Play reports this to have happened hundreds of times
            // .toList() because otherwise the lastModified will only be checked inside the postRunnable
            val saves = GameSaver.getSaves().sortedByDescending { it.lastModified() }.toList()

            Gdx.app.postRunnable {
                saveTable.clear()
                for (save in saves) {
                    if (save.name().startsWith("Autosave") && !showAutosaves) continue
                    val textButton = TextButton(save.name(), skin)
                    textButton.onClick { onSaveSelected(save) }
                    saveTable.add(textButton).pad(5f).row()
                }
            }
        }
    }

    private fun onSaveSelected(save: FileHandle) {
        selectedSave = save.name()
        copySavedGameToClipboardButton.enable()
        var textToSet = save.name()

        val savedAt = Date(save.lastModified())
        descriptionLabel.setText("Loading...".tr())
        textToSet += "\n{Saved at}: ".tr() + SimpleDateFormat("yyyy-MM-dd HH:mm").format(savedAt)
        thread { // Even loading the game to get its metadata can take a long time on older phones
            try {
                val game = GameSaver.loadGameFromFile(save)
                val playerCivNames = game.civilizations.filter { it.isPlayerCivilization() }.joinToString { it.civName.tr() }
                textToSet += "\n" + playerCivNames +
                        ", " + game.difficulty.tr() + ", ${Fonts.turn}" + game.turns
            } catch (ex: Exception) {
                textToSet += "\n{Could not load game}!".tr()
            }

            Gdx.app.postRunnable {
                descriptionLabel.setText(textToSet)
                rightSideButton.setText("Load [${save.name()}]".tr())
                rightSideButton.enable()
                deleteSaveButton.enable()
                deleteSaveButton.color = Color.RED
            }
        }
    }

}

