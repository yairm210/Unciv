package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Json
import com.unciv.UnCivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.models.gamebasics.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import java.text.SimpleDateFormat
import java.util.*

class LoadScreen : PickerScreen() {
    lateinit var selectedSave:String

    init {
        setDefaultCloseAction()
        val saveTable = Table()

        val deleteSaveButton = TextButton("Delete save".tr(), CameraStageBaseScreen.skin)
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
        val loadFromClipboardButton = TextButton("Load copied data".tr(),skin)
        val errorLabel = "".toLabel().setFontColor(Color.RED)
        loadFromClipboardButton.onClick {
            try{
                val clipboardContentsString = Gdx.app.clipboard.contents
                val decoded = Gzip.unzip(clipboardContentsString)
                val loadedGame = Json().fromJson(GameInfo::class.java, decoded)
                loadedGame.setTransients()
                UnCivGame.Current.loadGame(loadedGame)
            }catch (ex:Exception){
                errorLabel.setText("Could not load game from clipboard!".tr())
            }
        }

        rightSideTable.add(loadFromClipboardButton).row()
        rightSideTable.add(errorLabel).row()
        rightSideTable.add(deleteSaveButton)
        topTable.add(rightSideTable)

        rightSideButton.onClick {
            UnCivGame.Current.loadGame(selectedSave)
        }


    }

}