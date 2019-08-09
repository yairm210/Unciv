package com.unciv.ui.newgamescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.Constants
import com.unciv.GameStarter
import com.unciv.UnCivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.gamebasics.GameBasics
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

    val nationTables = ArrayList<NationTable>()

    var playerPickerTable = PlayerPickerTable()

    val civPickerTable = Table().apply { defaults().pad(15f) }

    init {
        setDefaultCloseAction()
        val mainTable = Table()
        mainTable.add(NewGameScreenOptionsTable(newGameParameters) { updateNationTables() })

//        mainTable.add(playerPickerTable)

        for(nation in GameBasics.Nations.values.filterNot { it.name == "Barbarians" || it.isCityState() }) {
            val nationTable = NationTable(nation, newGameParameters,stage.width/3) {
                if (nation.name in newGameParameters.humanNations) {
                    newGameParameters.humanNations.remove(nation.name)
                } else {
                    newGameParameters.humanNations.add(nation.name)
                    if (newGameParameters.humanNations.size > newGameParameters.numberOfHumanPlayers)
                        newGameParameters.humanNations.removeAt(0)
                }
                updateNationTables()
            }
            nationTables.add(nationTable)
            civPickerTable.add(nationTable).row()
        }
        civPickerTable.pack()
        mainTable.setFillParent(true)
        mainTable.add(ScrollPane(civPickerTable).apply { setScrollingDisabled(true,false) })
        topTable.addActor(mainTable)
        updateNationTables()


        rightSideButton.enable()
        rightSideButton.setText("Start game!".tr())
        rightSideButton.onClick {
            Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!
            rightSideButton.disable()
            rightSideButton.setText("Working...".tr())

            thread { // Creating a new game can take a while and we don't want ANRs
                try {
                    newGame = GameStarter().startNewGame(newGameParameters)
                }
                catch (exception:Exception){
                    val popup = PopupTable(this)
                    popup.addGoodSizedLabel("It looks like we can't make a map with the parameters you requested!".tr()).row()
                    popup.addGoodSizedLabel("Maybe you put too many players into too small a map?".tr()).row()
                    popup.open()
                }
            }
        }
    }



    private fun updateNationTables(){
        nationTables.forEach { it.update() }
        civPickerTable.pack()
        if(newGameParameters.humanNations.size==newGameParameters.numberOfHumanPlayers)
            rightSideButton.enable()
        else rightSideButton.disable()
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

