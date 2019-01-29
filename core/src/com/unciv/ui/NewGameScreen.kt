package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Array
import com.unciv.GameStarter
import com.unciv.UnCivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.map.MapType
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.WorldScreen
import kotlin.concurrent.thread
import kotlin.math.min

class NewGameScreen: PickerScreen(){

    val newGameParameters= UnCivGame.Current.gameInfo.gameParameters

    val nationTables = ArrayList<NationTable>()

    val civPickerTable = Table().apply { defaults().pad(15f) }
    init {
        setDefaultCloseAction()
        val mainTable = Table()
        mainTable.add(getOptionsTable())

        for(nation in GameBasics.Nations.values.filterNot { it.name == "Barbarians" }){
            val nationTable = NationTable(nation,newGameParameters,skin,stage.width/3 ){updateNationTables()}
            nationTables.add(nationTable)
            civPickerTable.add(nationTable).row()
        }
        civPickerTable.pack()
        mainTable.setFillParent(true)
        mainTable.add(ScrollPane(civPickerTable).apply { setScrollingDisabled(true,false) })
        topTable.addActor(mainTable)
        updateNationTables()
    }

    private fun updateNationTables(){
        nationTables.forEach { it.update() }
        civPickerTable.pack()
        if(newGameParameters.humanNations.size==newGameParameters.numberOfHumanPlayers)
            rightSideButton.enable()
        else rightSideButton.disable()
    }

    fun removeExtraHumanNations(humanPlayers: SelectBox<Int>) {
        val maxNumberOfHumanPlayers = GameBasics.Nations.size - newGameParameters.numberOfEnemies
        if(newGameParameters.numberOfHumanPlayers>maxNumberOfHumanPlayers){
            newGameParameters.numberOfHumanPlayers=maxNumberOfHumanPlayers
            humanPlayers.selected=maxNumberOfHumanPlayers
        }
        if(newGameParameters.humanNations.size>newGameParameters.numberOfHumanPlayers) {
            val nationsOverAllowed = newGameParameters.humanNations.size - newGameParameters.numberOfHumanPlayers
            newGameParameters.humanNations.removeAll(newGameParameters.humanNations.take(nationsOverAllowed))
            updateNationTables()
        }
    }

    private fun getOptionsTable(): Table {
        val newGameOptionsTable = Table()
        newGameOptionsTable.skin = skin

        newGameOptionsTable.add("{Map type}:".tr())
        val mapTypes = LinkedHashMap<String, MapType>()
        for (type in MapType.values()) {
            mapTypes[type.toString()] = type
        }
        val mapTypeSelectBox = TranslatedSelectBox(mapTypes.keys, newGameParameters.mapType.toString(), skin)
        mapTypeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.mapType = mapTypes[mapTypeSelectBox.selected.value]!!
            }
        })
        newGameOptionsTable.add(mapTypeSelectBox).pad(10f).row()

        newGameOptionsTable.add("{World size}:".tr())
        val worldSizeToRadius = LinkedHashMap<String, Int>()
        worldSizeToRadius["Tiny"] = 10
        worldSizeToRadius["Small"] = 15
        worldSizeToRadius["Medium"] = 20
        worldSizeToRadius["Large"] = 30
        worldSizeToRadius["Huge"] = 40

        val currentWorldSizeName = worldSizeToRadius.entries.first { it.value==newGameParameters.mapRadius }.key
        val worldSizeSelectBox = TranslatedSelectBox(worldSizeToRadius.keys, currentWorldSizeName, skin)

        worldSizeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.mapRadius = worldSizeToRadius[worldSizeSelectBox.selected.value]!!
            }
        })
        newGameOptionsTable.add(worldSizeSelectBox).pad(10f).row()



        newGameOptionsTable.add("{Number of human players}:".tr())
        val humanPlayers = SelectBox<Int>(skin)
        val humanPlayersArray = Array<Int>()
        (1..GameBasics.Nations.size).forEach { humanPlayersArray .add(it) }
        humanPlayers.items = humanPlayersArray
        humanPlayers.selected = newGameParameters.numberOfHumanPlayers
        newGameOptionsTable.add(humanPlayers).pad(10f).row()


        newGameOptionsTable.add("{Number of enemies}:".tr())
        val enemiesSelectBox = SelectBox<Int>(skin)
        val enemiesArray = Array<Int>()
        (0..GameBasics.Nations.size).forEach { enemiesArray.add(it) }
        enemiesSelectBox.items = enemiesArray
        enemiesSelectBox.selected = newGameParameters.numberOfEnemies
        newGameOptionsTable.add(enemiesSelectBox).pad(10f).row()

        humanPlayers.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.numberOfHumanPlayers = humanPlayers.selected
                removeExtraHumanNations(humanPlayers)

                val maxNumberOfEnemies = GameBasics.Nations.size - newGameParameters.numberOfHumanPlayers
                newGameParameters.numberOfEnemies = min(newGameParameters.numberOfEnemies, maxNumberOfEnemies)
                enemiesSelectBox.selected = newGameParameters.numberOfEnemies
            }
        })

        enemiesSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.numberOfEnemies = enemiesSelectBox.selected
                removeExtraHumanNations(humanPlayers)
            }
        })

        newGameOptionsTable.add("{Difficulty}:".tr())
        val difficultySelectBox = TranslatedSelectBox(GameBasics.Difficulties.keys, newGameParameters.difficulty , skin)
        difficultySelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.difficulty = difficultySelectBox.selected.value
            }
        })
        newGameOptionsTable.add(difficultySelectBox).pad(10f).row()


        rightSideButton.enable()
        rightSideButton.setText("Start game!".tr())
        rightSideButton.onClick {
            Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!
            rightSideButton.disable()
            rightSideButton.setText("Working...".tr())

            thread {
                // Creating a new game can tke a while and we don't want ANRs
                newGame = GameStarter().startNewGame(newGameParameters)
            }
        }

        newGameOptionsTable.pack()
        return newGameOptionsTable
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