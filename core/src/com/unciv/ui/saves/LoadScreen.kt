package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.models.gamebasics.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.optionstable.PopupTable
import java.text.SimpleDateFormat
import java.util.*

class LoadScreen : PickerScreen() {
    lateinit var selectedSave:String
    val copySavedGameToClipboardButton = TextButton("Copy saved game to clipboard",skin)

    init {
        setDefaultCloseAction()
        val saveTable = Table()

        val deleteSaveButton = TextButton("Delete save".tr(), skin)
        deleteSaveButton .onClick {
            GameSaver().deleteSave(selectedSave)
            UnCivGame.Current.screen = LoadScreen()
        }
        deleteSaveButton.disable()


        topTable.add(saveTable)
        val saves = GameSaver().getSaves()
        rightSideButton.setText("Load game".tr())
        for (save in saves) {
            val textButton = TextButton(save,skin)
            textButton.onClick {
                selectedSave=save
                copySavedGameToClipboardButton.enable()
                var textToSet = save

                val savedAt = Date(GameSaver().getSave(save).lastModified())
                textToSet+="\n{Saved at}: ".tr()+ SimpleDateFormat("dd-MM-yy HH.mm").format(savedAt)
                try{
                    val game = GameSaver().loadGame(save)
                    val playerCivNames = game.civilizations.filter { it.isPlayerCivilization() }.joinToString{it.civName.tr()}
                    textToSet+="\n"+playerCivNames+
                            ", "+game.difficulty.tr()+", {Turn} ".tr()+game.turns
                }catch (ex:Exception){
                    textToSet+="\n{Could not load game}!".tr()
                }
                descriptionLabel.setText(textToSet)
                rightSideButton.setText("Load [$save]".tr())
                rightSideButton.enable()
                deleteSaveButton.enable()
                deleteSaveButton.color= Color.RED
            }
            saveTable.add(textButton).pad(5f).row()
        }

        val rightSideTable = Table()

        val errorLabel = "".toLabel().setFontColor(Color.RED)

        val loadFromClipboardButton = TextButton("Load copied data".tr(),skin)
        loadFromClipboardButton.onClick {
            try{
                val clipboardContentsString = Gdx.app.clipboard.contents.trim()
                val decoded = Gzip.unzip(clipboardContentsString)
                val loadedGame = GameSaver().json().fromJson(GameInfo::class.java, decoded)
                loadedGame.setTransients()
                UnCivGame.Current.loadGame(loadedGame)
            }catch (ex:Exception){
                errorLabel.setText("Could not load game from clipboard!".tr())
                ex.printStackTrace()
            }
        }

        rightSideTable.add(loadFromClipboardButton).row()
        rightSideTable.add(errorLabel).row()
        rightSideTable.add(deleteSaveButton).row()

        copySavedGameToClipboardButton.disable()
        copySavedGameToClipboardButton.onClick {
            val gameText = GameSaver().getSave(selectedSave).readString()
            val gzippedGameText = Gzip.zip(gameText)
            Gdx.app.clipboard.contents = gzippedGameText
        }
        rightSideTable.add(copySavedGameToClipboardButton)

        topTable.add(rightSideTable)

        rightSideButton.onClick {
            try {
                UnCivGame.Current.loadGame(selectedSave)
            }
            catch (ex:Exception){
                val popup = PopupTable(this)
                popup.addGoodSizedLabel("It looks like your saved game can't be loaded!").row()
                popup.addGoodSizedLabel("If you could copy your game data (\"Copy saved game to clipboard\" - ").row()
                popup.addGoodSizedLabel("  paste into an email to yairm210@hotmail.com)").row()
                popup.addGoodSizedLabel("I could maybe help you figure out what went wrong, since this isn't supposed to happen!").row()
                popup.open()
                ex.printStackTrace()
            }
        }

    }

}