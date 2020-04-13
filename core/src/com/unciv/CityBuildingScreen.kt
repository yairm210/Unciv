package com.unciv

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Json
import com.unciv.ui.utils.*
import java.util.HashMap


class TestTile {
    @Transient
    var building = Building.nothing
        set(value) {
            field = value
            buildingName=value.name
        }

    private var buildingName = "Nothing"
    fun setTransients(){
        building = Building.allBuildings.first { it.name==buildingName }
    }
}

class Resources() : HashMap<String, Int>() {
    constructor(vararg pairs: Pair<String, Int>) : this() {
        putAll(pairs)
    }

    override fun get(key: String): Int {
        if (containsKey(key)) return super.get(key)!!
        return 0
    }

    fun add(string: String, amount: Int) {
        this[string] = get(string) + amount
    }

    fun add(resources: Resources){
        for(entry in resources)
            add(entry.key, entry.value)
    }
    fun remove(resources: Resources){
        for(entry in resources)
            add(entry.key, -entry.value)
    }

    override fun toString():String {
        return entries.filter { it.value!=0 }.joinToString { it.key + ": " + it.value }
    }
}

class Building(var name:String,var color:Color, var imageLocation:String, var production:Resources, val rank:Int=0) {
    companion object {
        val nothing = Building("Nothing", Color.WHITE,"",Resources())
        var allBuildings = listOf(
                Building("Wheat Farm", Color.GREEN, "ResourceIcons/Wheat",
                        Resources("Population" to -1, "Wheat" to 3, "Population" to -1)),
                Building("Small House", Color.GREEN, "BuildingIcons/Longhouse",
                        Resources("Wheat" to -1, "Population" to 2)),
                Building("Woodsman's Hut", Color.BROWN, "BuildingIcons/Workshop",
                        Resources("Population" to -1, "Wood" to 1)),
                Building("Iron Mine", Color.GRAY, "ResourceIcons/Stone",
                        Resources("Population" to -1, "Iron Ore" to 1)),
                Building("Charcoal Burner", Color.DARK_GRAY, "ResourceIcons/Coal",
                        Resources("Population" to -1, "Coal" to 1, "Wood" to -1)),
                Building("Iron Smelter", Color.GRAY, "ResourceIcons/Iron",
                        Resources("Population" to -1, "Iron Ore" to -1, "Coal" to -1, "Iron" to 1)),
                Building("Tool Workshop", Color.GRAY, "BuildingIcons/Forge",
                        Resources("Population" to -1, "Iron" to -1, "Tools" to 2)),
                Building("Lumber Mill", Color.BROWN, "BuildingIcons/Workshop",
                        Resources("Population" to -1, "Wood" to 3, "Tools" to -1),1),
                Building.nothing
        )
    }
}

// Arrays in LibGDX json parsing are yukky
class CityBuildingMap() :ArrayList<ArrayList<TestTile>>(){
    fun getArrayOfArrays(): Array<Array<TestTile>> {
        val aOa = Array<Array<TestTile>>()
        for(row in this){
            val newRow = Array<TestTile>()
            for(item in row) newRow.add(item)
            aOa.add(newRow)
        }
        return aOa
    }

    constructor(aOa:Array<Array<TestTile>>) : this() {
        for(row in aOa){
            val newRow = ArrayList<TestTile>()
            for(item in row) {
                item.setTransients()
                newRow.add(item)
            }
            add(newRow)
        }
    }
}
class CityBuildingGameInfo(){
    var map=com.badlogic.gdx.utils.Array<Array<TestTile>>()
    constructor(map:CityBuildingMap):this(){
        this.map = map.getArrayOfArrays()
    }
}
class CityBuildingScreen: CameraStageBaseScreen() {
    var tiles = CityBuildingMap()

    val saveFileLocation = Gdx.files.local("CityBuildingSavefile.json")
    fun save(){
        saveFileLocation.writeString(Json().apply { ignoreUnknownFields = true }
                .prettyPrint(CityBuildingGameInfo(tiles)),false)
    }
    fun load(){
        val savedFile = JsonParser().getFromJson(CityBuildingGameInfo::class.java,saveFileLocation)
        tiles = CityBuildingMap(savedFile.map)
    }

    fun getResources(): Resources {
        val resources = Resources()
        for (tile in tiles.flatten())
            resources.add(tile.building.production)
        return resources
    }

    val tileTable = Table().apply { defaults().pad(5f) }
    val label = "".toLabel()
    val tileActionTable = Table()
    var selectedTile: TestTile? = null

    init {
        if(saveFileLocation.exists()) load()
        else initializeGame()

        update()
        tileTable.centerY(stage)
        tileTable.setX(stage.width-10,Align.right)
        stage.addActor(tileTable)
        label.setPosition(10f, stage.height - 10, Align.topLeft)
        stage.addActor(label)
        tileActionTable.defaults().pad(5f)
        tileActionTable.background = ImageGetter.getBackground(ImageGetter.getBlue())
        stage.addActor(tileActionTable)
    }

    private fun initializeGame() {
        for (i in 1..5) {
            val row = ArrayList<TestTile>()
            for (tile in 1..5)
                row.add(TestTile())
            tiles.add(row)
        }
        tiles[0][0].building = Building.allBuildings.first { it.name == "Wheat Farm" }
        tiles[0][1].building = Building.allBuildings.first { it.name == "Small House" }
    }

    private fun update() {
        updateTileTable()

        label.setText(getResources().toString())

        updateTileActionTable()
    }

    private fun updateTileTable() {
        tileTable.clear()
        for (row in tiles) {
            for (tile in row) {
                val tileGroup = Group()
                val groupSize = 100f
                tileGroup.setSize(groupSize, groupSize)
                val building = tile.building
                val tileImage = ImageGetter.getDot(building.color)
                tileImage.setSize(groupSize, groupSize)
                tileGroup.addActor(tileImage)
                if(building.imageLocation!="") {
                    val buildingImage = ImageGetter.getImage(building.imageLocation)
                    buildingImage.setSize(groupSize,groupSize)
                    tileGroup.addActor(buildingImage)
                }
                if(building.rank>0) {
                    val rankTable = Table().apply { defaults().pad(3f) }
                    for (i in 1..building.rank)
                        rankTable.add(ImageGetter.getImage("OtherIcons/Star").apply { color = Color.GOLD }).size(10f)
                    rankTable.pack()
                    rankTable.centerX(tileGroup)
                    tileGroup.addActor(rankTable)
                }
                if (selectedTile == tile) {
                    val redCircle = ImageGetter.getCircle().apply { color = Color.RED }
                    redCircle.setSize(groupSize / 3, groupSize / 3)
                    redCircle.setPosition(0f, groupSize, Align.topLeft)
                    tileGroup.addActor(redCircle)
                }
                tileGroup.onClick {
                    selectedTile = tile
                    update()
                }
                tileTable.add(tileGroup)
            }
            tileTable.row()
        }
        tileTable.pack()
    }

    private fun updateTileActionTable() {
        tileActionTable.clear()
        val currentTile = selectedTile
        if (currentTile != null) {
            val labelText = currentTile.building.name + "\n" + currentTile.building.production.toString()
            tileActionTable.add(labelText.toLabel()).row()
            val currentResources = getResources()
            val buttons = ArrayList<Button>()
            for (building in Building.allBuildings) {
                if (building == currentTile.building) continue

                val resourcesWithBuildingSwitch = Resources()
                resourcesWithBuildingSwitch.add(currentResources)
                resourcesWithBuildingSwitch.remove(currentTile.building.production)
                resourcesWithBuildingSwitch.add(building.production)

                var text = "Make " + building.name
                if (building != Building.nothing) text += "\n" + building.production.toString()
                val buildBuildingButton = Button(skin)
                buildBuildingButton.add(ImageGetter.getImage(building.imageLocation).surroundWithCircle(30f))
                        .padRight(5f)
                buildBuildingButton.add(text.toLabel())
                //TextButton(text, skin)
                if (resourcesWithBuildingSwitch.values.any { it < 0 })
                    buildBuildingButton.disable()
                buildBuildingButton.onClick {
                    currentTile.building = building
                    save()
                    update()
                }
                buttons.add(buildBuildingButton)
            }
            val buttonTable = Table().apply { defaults().pad(5f) }
            val tileButtonScroll = ScrollPane(buttonTable)
            for(button in buttons.sortedByDescending { it.isEnabled })
                buttonTable.add(button).row()
            tileActionTable.add(tileButtonScroll).maxHeight(stage.height/2)
            tileActionTable.pack()
            tileActionTable.setPosition(10f, 10f)
        }
    }
}