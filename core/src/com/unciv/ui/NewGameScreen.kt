package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Array
import com.unciv.GameStarter
import com.unciv.logic.GameInfo
import com.unciv.logic.map.MapType
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.Nation
import com.unciv.models.gamebasics.Translations
import com.unciv.models.gamebasics.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen
import kotlin.concurrent.thread

class NewGameScreen: PickerScreen(){

    class NewGameParameters{
        var difficulty="Prince"
        var nation="Babylon"
        var mapRadius=20
        var numberOfEnemies=3
        var mapType=MapType.Perlin
    }

    val newGameParameters=NewGameParameters()

    class NationTable(val nation:Nation,val newGameParameters: NewGameParameters, skin:Skin, width:Float, onClick:()->Unit):Table(skin){
        init {
            pad(10f)
            background=ImageGetter.getBackground(nation.getColor().apply { a=0.5f })
            add(Label(nation.name.tr(), skin).apply { setFontColor(nation.getSecondaryColor())}).row()
            add(Label(getUniqueLabel(nation), skin).apply { setWrap(true);setFontColor(nation.getSecondaryColor())}).width(width)
            onClick { newGameParameters.nation=nation.name; onClick() }
            touchable=Touchable.enabled
            update()
        }

        private fun getUniqueLabel(nation: Nation): String {
            val textList = ArrayList<String>()

            if(nation.unique!=null) {
                textList += nation.unique!!.tr()
                textList += ""
            }

            for (building in GameBasics.Buildings.values)
                if (building.uniqueTo == nation.name) {
                    val originalBuilding = GameBasics.Buildings[building.replaces!!]!!

                    textList += building.name.tr() + " - {replaces} " + originalBuilding.name.tr()
                    val originalBuildingStatMap = originalBuilding.toHashMap()
                    for (stat in building.toHashMap())
                        if (stat.value != originalBuildingStatMap[stat.key])
                            textList += "  "+stat.key.toString().tr() +" "+stat.value.toInt() + " vs " + originalBuildingStatMap[stat.key]!!.toInt()
                    for(unique in building.uniques.filter { it !in originalBuilding.uniques })
                        textList += "  "+unique.tr()
                    if (building.maintenance != originalBuilding.maintenance)
                        textList += "  {Maintenance} " + building.maintenance + " vs " + originalBuilding.maintenance
                    textList+=""
                }

            for (unit in GameBasics.Units.values)
                if (unit.uniqueTo == nation.name) {
                    val originalUnit = GameBasics.Units[unit.replaces!!]!!

                    textList += unit.name.tr() + " - {replaces} " + originalUnit.name.tr()
                    if (unit.strength != originalUnit.strength)
                        textList += "  {Strength} " + unit.strength + " vs " + originalUnit.strength
                    if (unit.rangedStrength!= originalUnit.rangedStrength)
                        textList+= "  {Ranged strength} " + unit.rangedStrength+ " vs " + originalUnit.rangedStrength
                    if (unit.range!= originalUnit.range)
                        textList+= "  {Range} " + unit.range+ " vs " + originalUnit.range
                    if (unit.movement!= originalUnit.movement)
                        textList+= "  {Movement} " + unit.movement+ " vs " + originalUnit.movement
                    val newUniques = unit.uniques.filterNot { it in originalUnit.uniques }
                    if(newUniques.isNotEmpty())
                        textList+="  {Uniques}: "+newUniques.joinToString{ Translations.translateBonusOrPenalty(it)}
                    textList+=""
                }


            return textList.joinToString("\n").tr().trim()
        }


        fun update(){
            val color = nation.getColor()
            if(newGameParameters.nation!=nation.name) color.a=0.5f
            background=ImageGetter.getBackground(color)
        }
    }

    val nationTables = ArrayList<NationTable>()

    init {
        setDefaultCloseAction()
        val mainTable = Table()
        mainTable.add(getOptionsTable())
        val civPickerTable = Table().apply { defaults().pad(5f) }
        for(nation in GameBasics.Nations.values.filterNot { it.name == "Barbarians" }){
            val nationTable = NationTable(nation,newGameParameters,skin,stage.width/3 ){updateNationTables()}
            nationTables.add(nationTable)
            civPickerTable.add(nationTable).row()
        }
        mainTable.setFillParent(true)
        mainTable.add(ScrollPane(civPickerTable).apply { setScrollingDisabled(true,false) })
        topTable.addActor(mainTable)
    }

    private fun updateNationTables(){
        nationTables.forEach { it.update() }
    }

    private fun getOptionsTable(): Table {
        val newGameOptionsTable = Table()
        newGameOptionsTable.skin = skin

        newGameOptionsTable.add("{Map type}:".tr())
        val mapTypes = LinkedHashMap<String, MapType>()
        for (type in MapType.values()) {
            mapTypes[type.toString()] = type
        }
        val mapTypeSelectBox = TranslatedSelectBox(mapTypes.keys, "Perlin", skin)
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
        val worldSizeSelectBox = TranslatedSelectBox(worldSizeToRadius.keys, "Medium", skin)

        worldSizeSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.mapRadius = worldSizeToRadius[worldSizeSelectBox.selected.value]!!
            }
        })
        newGameOptionsTable.add(worldSizeSelectBox).pad(10f).row()


        newGameOptionsTable.add("{Number of enemies}:".tr())
        val enemiesSelectBox = SelectBox<Int>(skin)
        val enemiesArray = Array<Int>()
        (0..GameBasics.Nations.size).forEach { enemiesArray.add(it) }
        enemiesSelectBox.items = enemiesArray
        enemiesSelectBox.selected = newGameParameters.numberOfEnemies

        enemiesSelectBox.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                newGameParameters.numberOfEnemies = enemiesSelectBox.selected
            }
        })
        newGameOptionsTable.add(enemiesSelectBox).pad(10f).row()


        newGameOptionsTable.add("{Difficulty}:".tr())
        val difficultySelectBox = TranslatedSelectBox(GameBasics.Difficulties.keys, newGameParameters.difficulty, skin)
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