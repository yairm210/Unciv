package com.unciv.ui.newgamescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Array
import com.unciv.UnCivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.gamebasics.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.WorldScreen
import com.unciv.ui.worldscreen.optionstable.PopupTable
import kotlin.concurrent.thread

class NewGameScreen: PickerScreen(){

    val newGameParameters= UnCivGame.Current.gameInfo.gameParameters

    init {
        setDefaultCloseAction()

        val playerPickerTable = PlayerPickerTable(this, newGameParameters)
        topTable.add(NewGameScreenOptionsTable(newGameParameters) { playerPickerTable.update() })
        topTable.add(playerPickerTable).pad(10f)
        topTable.pack()
        topTable.setFillParent(true)

        rightSideButton.enable()
        rightSideButton.setText("Start game!".tr())
        rightSideButton.onClick {
            if (newGameParameters.players.none { it.playerType == PlayerType.Human }) {
                val popup = PopupTable(this)
                popup.addGoodSizedLabel("No human players selected!").row()
                popup.addButton("Close") { popup.remove() }
                popup.open()
                return@onClick
            }

            Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!
            rightSideButton.disable()
            rightSideButton.setText("Working...".tr())

            thread {
                // Creating a new game can take a while and we don't want ANRs
                try {
                    newGame = GameStarter().startNewGame(newGameParameters)
                } catch (exception: Exception) {
                    val popup = PopupTable(this)
                    popup.addGoodSizedLabel("It looks like we can't make a map with the parameters you requested!".tr()).row()
                    popup.addGoodSizedLabel("Maybe you put too many players into too small a map?".tr()).row()
                    popup.open()
                }
            }
        }
    }


    var newGame:GameInfo?=null

    override fun render(delta: Float) {
        if(newGame!=null){
            game.gameInfo=newGame!!
            game.worldScreen = WorldScreen(newGame!!.currentPlayerCiv)
            game.setWorldScreen()
        }
        super.render(delta)
    }
}

class TranslatedSelectBox(values : Collection<String>, default:String, skin: Skin) : SelectBox<TranslatedSelectBox.TranslatedString>(skin) {
    class TranslatedString(val value: String) {
        val translation = value.tr()
        override fun toString() = translation
    }

    init {
        val array = Array<TranslatedString>()
        values.forEach { array.add(TranslatedString(it)) }
        items = array
        val defaultItem = array.firstOrNull { it.value == default }
        selected = if (defaultItem != null) defaultItem else array.first()
    }
}

