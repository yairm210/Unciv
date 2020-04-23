package com.unciv.ui.saves

import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Json
import com.unciv.UncivGame
import com.unciv.logic.GameSaver
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import kotlin.concurrent.thread


class SaveGameScreen : PickerScreen() {
    val textField = TextField("", skin)
    val currentSaves = Table()

    init {
        setDefaultCloseAction()

        currentSaves.add("Current saves".toLabel()).row()
        updateShownSaves(false)
        topTable.add(ScrollPane(currentSaves)).height(stage.height*2/3)

        val newSave = Table()
        val defaultSaveName = game.gameInfo.currentPlayer+" -  "+game.gameInfo.turns+" turns"
        textField.text = defaultSaveName

        newSave.add("Saved game name".toLabel()).row()
        newSave.add(textField).width(300f).pad(10f).row()

        val copyJsonButton = "Copy to clipboard".toTextButton()
        copyJsonButton.onClick {
            val json = Json().toJson(game.gameInfo)
            val base64Gzip = Gzip.zip(json)
            Gdx.app.clipboard.contents =  base64Gzip
        }
        newSave.add(copyJsonButton).row()


        val showAutosavesCheckbox = CheckBox("Show autosaves".tr(), skin)
        showAutosavesCheckbox.isChecked = false
        showAutosavesCheckbox.onChange {
                updateShownSaves(showAutosavesCheckbox.isChecked)
            }
        newSave.add(showAutosavesCheckbox).row()

        topTable.add(newSave)
        topTable.pack()

        rightSideButton.setText("Save game".tr())
        rightSideButton.onClick {
            rightSideButton.setText("Saving...".tr())
            thread(name="SaveGame"){
                GameSaver.saveGame(UncivGame.Current.gameInfo, textField.text)
                UncivGame.Current.setWorldScreen()
            }
        }
        rightSideButton.enable()
    }

    fun updateShownSaves(showAutosaves:Boolean){
        currentSaves.clear()
        val saves = GameSaver.getSaves()
                .sortedByDescending { GameSaver.getSave(it).lastModified() }
        for (saveGameName in saves) {
            if(saveGameName.startsWith("Autosave") && !showAutosaves) continue
            val textButton = TextButton(saveGameName, skin)
            textButton.onClick {
                textField.text = saveGameName
            }
            currentSaves.add(textButton).pad(5f).row()
        }
    }

}


