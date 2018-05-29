package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Array
import com.unciv.UnCivGame
import com.unciv.logic.GameSaver
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.enable

class LoadScreen : PickerScreen() {
    lateinit var selectedSave:String

    init {
        val saveTable = Table()


        val deleteSaveButton = TextButton("Delete save", CameraStageBaseScreen.skin)
        deleteSaveButton .addClickListener {
            GameSaver().deleteSave(selectedSave)
            UnCivGame.Current.screen = LoadScreen()
        }
        deleteSaveButton.disable()
        rightSideGroup.addActor(deleteSaveButton)

        topTable.add(saveTable)
        val saves = GameSaver().getSaves()
        rightSideButton.setText("Load game")
        saves.forEach {
            val textButton = TextButton(it,skin)
            textButton.addClickListener {
                selectedSave=it

                descriptionLabel.setText(it)
                rightSideButton.setText("Load\r\n$it")
                rightSideButton.enable()
                deleteSaveButton.enable()
                deleteSaveButton.color= Color.RED
            }
            saveTable.add(textButton).pad(5f).row()
        }

        rightSideButton.addClickListener {
            UnCivGame.Current.loadGame(selectedSave)
        }



    }

}

class NewGameScreen:CameraStageBaseScreen(){
    init {
        val table = Table()
        table.skin=skin

        table.add("Civilization: ")
        val civSelectBox = SelectBox<String>(skin)
        val civArray = Array<String>()
        GameBasics.Civilizations.keys.filterNot { it=="Barbarians" }.forEach{civArray.add(it)}
        civSelectBox.setItems(civArray)
        civSelectBox.selected = civSelectBox.items.first()
        table.add(civSelectBox).pad(10f).row()

        table.add("World size: ")
        val worldSizeToRadius=LinkedHashMap<String,Int>()
        worldSizeToRadius.put("Small",10)
        worldSizeToRadius.put("Medium",20)
        worldSizeToRadius.put("Large",30)
        val worldSizeSelectBox = SelectBox<String>(skin)
        val worldSizeArray = Array<String>()
        worldSizeToRadius.keys.forEach{worldSizeArray.add(it)}
        worldSizeSelectBox.setItems(worldSizeArray)
        worldSizeSelectBox.selected = "Medium"
        table.add(worldSizeSelectBox)

        val createButton = TextButton("Start game!",skin)
        createButton.addClickListener {  }

        table.setFillParent(true)
        table.pack()
        stage.addActor(table)
    }
}