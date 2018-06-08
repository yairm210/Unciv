package com.unciv.ui

import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.GameStarter
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.enable
import com.unciv.ui.worldscreen.WorldScreen

class NewGameScreen: PickerScreen(){
    init {
        val table = Table()
        table.skin= skin

        table.add("Civilization: ")
        val civSelectBox = SelectBox<String>(skin)
        val civArray = Array<String>()
        GameBasics.Civilizations.keys.filterNot { it=="Barbarians" }.forEach{civArray.add(it)}
        civSelectBox.setItems(civArray)
        civSelectBox.selected = civSelectBox.items.first()
        table.add(civSelectBox).pad(10f).row()

        table.add("World size: ")
        val worldSizeToRadius=LinkedHashMap<String,Int>()
        worldSizeToRadius["Small"] = 10
        worldSizeToRadius["Medium"] = 20
        worldSizeToRadius["Large"] = 30
        val worldSizeSelectBox = SelectBox<String>(skin)
        val worldSizeArray = Array<String>()
        worldSizeToRadius.keys.forEach{worldSizeArray.add(it)}
        worldSizeSelectBox.items = worldSizeArray
        worldSizeSelectBox.selected = "Medium"
        table.add(worldSizeSelectBox).pad(10f).row()


        table.add("Number of enemies: ")
        val enemiesSelectBox = SelectBox<Int>(skin)
        val enemiesArray=Array<Int>()
        (1..5).forEach { enemiesArray.add(it) }
        enemiesSelectBox.items=enemiesArray
        enemiesSelectBox.selected=3
        table.add(enemiesSelectBox).pad(10f).row()

        rightSideButton.enable()
        rightSideButton.setText("Start game!")
        rightSideButton.addClickListener {
            val newGame = GameStarter().startNewGame(
                    worldSizeToRadius[worldSizeSelectBox.selected]!!, enemiesSelectBox.selected, civSelectBox.selected )
            newGame.tutorial =game.gameInfo.tutorial
            game.gameInfo=newGame
            game.worldScreen = WorldScreen()
            game.setWorldScreen()
        }

        table.setFillParent(true)
        table.pack()
        stage.addActor(table)
    }
}