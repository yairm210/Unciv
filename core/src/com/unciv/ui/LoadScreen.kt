package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Json
import com.unciv.UnCivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import java.text.SimpleDateFormat
import java.util.*

class LoadScreen : PickerScreen() {
    lateinit var selectedSave:String

    init {
        val saveTable = Table()


        val deleteSaveButton = TextButton("Delete save".tr(), CameraStageBaseScreen.skin)
        deleteSaveButton .addClickListener {
            GameSaver().deleteSave(selectedSave)
            UnCivGame.Current.screen = LoadScreen()
        }
        deleteSaveButton.disable()
        rightSideGroup.addActor(deleteSaveButton)

        topTable.add(saveTable)
        val saves = GameSaver().getSaves()
        rightSideButton.setText("Load game".tr())
        saves.forEach {
            val textButton = TextButton(it,skin)
            textButton.addClickListener {
                selectedSave=it

                var textToSet = it

                val savedAt = Date(GameSaver().getSave(it).lastModified())
                textToSet+="\n{Saved at}: ".tr()+ SimpleDateFormat("dd-MM-yy HH.mm").format(savedAt)
                try{
                    val game = GameSaver().loadGame(it)
                    textToSet+="\n"+game.getPlayerCivilization()+", {Turn} ".tr()+game.turns
                }catch (ex:Exception){
                    textToSet+="\n{Could not load game}!".tr()
                }
                descriptionLabel.setText(textToSet)
                rightSideButton.setText("Load [$it]".tr())
                rightSideButton.enable()
                deleteSaveButton.enable()
                deleteSaveButton.color= Color.RED
            }
            saveTable.add(textButton).pad(5f).row()
        }

        val loadFromClipboardTable = Table()
        val loadFromClipboardButton = TextButton("Load copied data".tr(),skin)
        val errorLabel = Label("",skin).setFontColor(Color.RED)
        loadFromClipboardButton.addClickListener {
            try{
                val clipboardContentsString = Gdx.app.clipboard.contents
                val loadedGame = Json().fromJson(GameInfo::class.java, clipboardContentsString)
                loadedGame.setTransients()
                UnCivGame.Current.loadGame(loadedGame)
            }catch (ex:Exception){
                errorLabel.setText("Could not load game from clipboard!")
            }
        }

        loadFromClipboardTable.add(loadFromClipboardButton).row()
        loadFromClipboardTable.add(errorLabel)
        topTable.add(loadFromClipboardTable)

        rightSideButton.addClickListener {
            UnCivGame.Current.loadGame(selectedSave)
        }



    }

}

