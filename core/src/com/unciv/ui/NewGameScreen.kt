package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.GameStarter
import com.unciv.logic.GameInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.tr
import com.unciv.ui.worldscreen.WorldScreen

class NewGameScreen: PickerScreen(){
    init {
        val table = Table()
        table.skin= skin


        table.add("{Civilization}:".tr())
        val civSelectBox = TranslatedSelectBox(GameBasics.Nations.keys.filterNot { it=="Barbarians" },
                "Babylon",skin)
        table.add(civSelectBox).pad(10f).row()

        table.add("{World size}:".tr())
        val worldSizeToRadius=LinkedHashMap<String,Int>()
        worldSizeToRadius["Small"] = 10
        worldSizeToRadius["Medium"] = 20
        worldSizeToRadius["Large"] = 30
        val worldSizeSelectBox = TranslatedSelectBox(worldSizeToRadius.keys,"Medium",skin)
        table.add(worldSizeSelectBox).pad(10f).row()


        table.add("{Number of enemies}:".tr())
        val enemiesSelectBox = SelectBox<Int>(skin)
        val enemiesArray=Array<Int>()
        (1..5).forEach { enemiesArray.add(it) }
        enemiesSelectBox.items=enemiesArray
        enemiesSelectBox.selected=3
        table.add(enemiesSelectBox).pad(10f).row()


        table.add("{Difficulty}:".tr())
        val difficultySelectBox = TranslatedSelectBox(GameBasics.Difficulties.keys, "Prince", skin)
        table.add(difficultySelectBox).pad(10f).row()


        rightSideButton.enable()
        rightSideButton.setText("Start game!".tr())
        rightSideButton.addClickListener {
            Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!
            rightSideButton.disable()
            rightSideButton.setText("Working...".tr())

            kotlin.concurrent.thread { // Creating a new game can tke a while and we don't want ANRs
                newGame = GameStarter().startNewGame(
                        worldSizeToRadius[worldSizeSelectBox.selected.value]!!, enemiesSelectBox.selected,
                        civSelectBox.selected.value, difficultySelectBox.selected.value )
            }
        }

        table.setFillParent(true)
        table.pack()
        stage.addActor(table)
    }
    var newGame:GameInfo?=null

    override fun render(delta: Float) {
        if(newGame!=null){
            game.gameInfo=newGame!!
            game.worldScreen = WorldScreen()
            game.setWorldScreen()
        }
        super.render(delta)
    }
}

class TranslatedSelectBox(values : Collection<String>, default:String, skin: Skin) : SelectBox<TranslatedSelectBox.TranslatedString>(skin){
    class TranslatedString(val value: String){
        val translation = value.tr()
        override fun toString()=translation
    }
    init {
        val array = Array<TranslatedString>()
        values.forEach{array.add(TranslatedString(it))}
        items = array
        selected = array.first { it.value==default }
    }
}