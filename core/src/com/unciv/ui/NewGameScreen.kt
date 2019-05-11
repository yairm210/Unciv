package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Array
import com.unciv.GameStarter
import com.unciv.UnCivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.map.MapType
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel
import com.unciv.ui.worldscreen.WorldScreen
import com.unciv.ui.worldscreen.optionstable.PopupTable
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

        for(nation in GameBasics.Nations.values.filterNot { it.name == "Barbarians" || it.isCityState() }){
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

        addMapTypeSizeAndFile(newGameOptionsTable)

        addNumberOfHumansAndEnemies(newGameOptionsTable)

        addDifficultySelectBox(newGameOptionsTable)

        val noBarbariansCheckbox = CheckBox("No barbarians".tr(),skin)
        noBarbariansCheckbox.isChecked=newGameParameters.noBarbarians
        noBarbariansCheckbox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.noBarbarians = noBarbariansCheckbox.isChecked
            }
        })
        newGameOptionsTable.add(noBarbariansCheckbox).colspan(2).row()


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
                    popup.addGoodSizedLabel("It looks like we can't make a map with the parameters you requested!").row()
                    popup.addGoodSizedLabel("Maybe you put too many players into too small a map?").row()
                    popup.open()
                }
            }
        }

        newGameOptionsTable.pack()
        return newGameOptionsTable
    }

    private fun addMapTypeSizeAndFile(newGameOptionsTable: Table) {
        newGameOptionsTable.add("{Map type}:".tr())
        val mapTypes = LinkedHashMap<String, MapType>()
        for (type in MapType.values()) {
            if (type == MapType.File && GameSaver().getMaps().isEmpty()) continue
            mapTypes[type.toString()] = type
        }

        val mapFileLabel = "{Map file}:".toLabel()
        val mapFileSelectBox = getMapFileSelectBox()
        mapFileLabel.isVisible = false
        mapFileSelectBox.isVisible = false

        val mapTypeSelectBox = TranslatedSelectBox(mapTypes.keys, newGameParameters.mapType.toString(), skin)

        val worldSizeSelectBox = getWorldSizeSelectBox()
        val worldSizeLabel = "{World size}:".toLabel()

        mapTypeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.mapType = mapTypes[mapTypeSelectBox.selected.value]!!
                if (newGameParameters.mapType == MapType.File) {
                    worldSizeSelectBox.isVisible = false
                    worldSizeLabel.isVisible = false
                    mapFileSelectBox.isVisible = true
                    mapFileLabel.isVisible = true
                    newGameParameters.mapFileName = mapFileSelectBox.selected
                } else {
                    worldSizeSelectBox.isVisible = true
                    worldSizeLabel.isVisible = true
                    mapFileSelectBox.isVisible = false
                    mapFileLabel.isVisible = false
                    newGameParameters.mapFileName = null
                }
            }
        })
        newGameOptionsTable.add(mapTypeSelectBox).pad(10f).row()


        newGameOptionsTable.add(worldSizeLabel)
        newGameOptionsTable.add(worldSizeSelectBox).pad(10f).row()

        newGameOptionsTable.add(mapFileLabel)
        newGameOptionsTable.add(mapFileSelectBox).pad(10f).row()
    }

    private fun addNumberOfHumansAndEnemies(newGameOptionsTable: Table) {
        newGameOptionsTable.add("{Number of human players}:".tr())
        val humanPlayers = SelectBox<Int>(skin)
        val humanPlayersArray = Array<Int>()
        (1..GameBasics.Nations.filter{ !it.value.isCityState() }.size).forEach { humanPlayersArray.add(it) }
        humanPlayers.items = humanPlayersArray
        humanPlayers.selected = newGameParameters.numberOfHumanPlayers
        newGameOptionsTable.add(humanPlayers).pad(10f).row()


        newGameOptionsTable.add("{Number of enemies}:".tr())
        val enemiesSelectBox = SelectBox<Int>(skin)
        val enemiesArray = Array<Int>()
        (0..GameBasics.Nations.filter{ !it.value.isCityState() }.size - 1).forEach { enemiesArray.add(it) }
        enemiesSelectBox.items = enemiesArray
        enemiesSelectBox.selected = newGameParameters.numberOfEnemies
        newGameOptionsTable.add(enemiesSelectBox).pad(10f).row()

        // Todo - re-enable this when city states are fit for players
//        addCityStatesSelectBox(newGameOptionsTable)
        newGameParameters.numberOfCityStates = 0

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

    }

    private fun addCityStatesSelectBox(newGameOptionsTable: Table) {
        newGameOptionsTable.add("{Number of city-states}:".tr())
        val cityStatesSelectBox = SelectBox<Int>(skin)
        val cityStatesArray = Array<Int>()
        (0..GameBasics.Nations.filter { it.value.isCityState() }.size).forEach { cityStatesArray.add(it) }
        cityStatesSelectBox.items = cityStatesArray
        cityStatesSelectBox.selected = newGameParameters.numberOfCityStates
        newGameOptionsTable.add(cityStatesSelectBox).pad(10f).row()
        cityStatesSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.numberOfCityStates = cityStatesSelectBox.selected
            }
        })
    }

    private fun addDifficultySelectBox(newGameOptionsTable: Table) {
        newGameOptionsTable.add("{Difficulty}:".tr())
        val difficultySelectBox = TranslatedSelectBox(GameBasics.Difficulties.keys, newGameParameters.difficulty, skin)
        difficultySelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.difficulty = difficultySelectBox.selected.value
            }
        })
        newGameOptionsTable.add(difficultySelectBox).pad(10f).row()
    }

    private fun getMapFileSelectBox(): SelectBox<String> {
        val mapFileSelectBox = SelectBox<String>(skin)
        val mapNames = Array<String>()
        for (mapName in GameSaver().getMaps()) mapNames.add(mapName)
        mapFileSelectBox.items = mapNames

        mapFileSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.mapFileName = mapFileSelectBox.selected!!
            }
        })
        return mapFileSelectBox
    }

    private fun getWorldSizeSelectBox(): TranslatedSelectBox {
        val worldSizeToRadius = LinkedHashMap<String, Int>()
        worldSizeToRadius["Tiny"] = 10
        worldSizeToRadius["Small"] = 15
        worldSizeToRadius["Medium"] = 20
        worldSizeToRadius["Large"] = 30
        worldSizeToRadius["Huge"] = 40

        val currentWorldSizeName = worldSizeToRadius.entries.first { it.value == newGameParameters.mapRadius }.key
        val worldSizeSelectBox = TranslatedSelectBox(worldSizeToRadius.keys, currentWorldSizeName, skin)

        worldSizeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.mapRadius = worldSizeToRadius[worldSizeSelectBox.selected.value]!!
            }
        })
        return worldSizeSelectBox
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
        val defaultItem = array.firstOrNull { it.value==default }
        selected = if(defaultItem!=null) defaultItem else array.first()
    }
}
