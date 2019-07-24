package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Json
import com.unciv.UnCivGame
import com.unciv.logic.GameSaver
import com.unciv.models.gamebasics.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel


class SaveScreen : PickerScreen() {
    val textField = TextField("", skin)

    init {
        setDefaultCloseAction()
        val currentSaves = Table()

        currentSaves.add("Current saves".toLabel()).row()
        val saves = GameSaver().getSaves().sortedByDescending { GameSaver().getSave(it).lastModified() }
        saves.forEach {
            val textButton = TextButton(it, skin)
            textButton.onClick {
                textField.text = it
            }
            currentSaves.add(textButton).pad(5f).row()

        }
        topTable.add(ScrollPane(currentSaves)).height(stage.height*2/3)


        val newSave = Table()
        val defaultSaveName = game.gameInfo.currentPlayer+" -  "+game.gameInfo.turns+" turns"
        textField.text = defaultSaveName

        newSave.add("Saved game name".toLabel()).row()
        newSave.add(textField).width(300f).pad(10f).row()

        val copyJsonButton = TextButton("Copy to clipboard".tr(),skin)
        copyJsonButton.onClick {
            val json = Json().toJson(game.gameInfo)
            val base64Gzip = Gzip.zip(json)
            Gdx.app.clipboard.contents =  base64Gzip
        }
        newSave.add(copyJsonButton)

        topTable.add(newSave)
        topTable.pack()

        rightSideButton.setText("Save game".tr())
        rightSideButton.onClick {
            GameSaver().saveGame(UnCivGame.Current.gameInfo, textField.text)
            UnCivGame.Current.setWorldScreen()
        }
        rightSideButton.enable()
    }

}


