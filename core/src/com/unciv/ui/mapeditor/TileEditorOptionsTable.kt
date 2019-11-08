package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.UnCivGame
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.Terrain
import com.unciv.models.gamebasics.tile.TerrainType
import com.unciv.models.gamebasics.tile.TileImprovement
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.models.gamebasics.tr
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.*

class TileEditorOptionsTable(val mapEditorScreen: MapEditorScreen): Table(CameraStageBaseScreen.skin){
    val tileSetLocation = "TileSets/"+ UnCivGame.Current.settings.tileSet +"/"

    var clearTerrainFeature=false
    var selectedTerrain : Terrain?=null

    var clearResource=false
    var selectedResource: TileResource?=null

    var clearImprovement=false
    var selectedImprovement:TileImprovement?=null

    var toggleBottomRightRiver=false
    var toggleBottomRiver=false
    var toggleBottomLeftRiver=false

    val editorPickTable = Table()

    private var currentHex: Actor = Group()

    init{
        height=mapEditorScreen.stage.height
        width=mapEditorScreen.stage.width/3

        setTerrainsAndResources()

        val tabPickerTable = Table().apply { defaults().pad(10f) }
        val terrainsAndResourcesTabButton = TextButton("Terrains & Resources".tr(),skin)
                .onClick { setTerrainsAndResources() }
        tabPickerTable.add(terrainsAndResourcesTabButton)

        val civLocationsButton = TextButton("Improvements".tr(),skin)
                .onClick { setImprovements() }
        tabPickerTable.add(civLocationsButton)
        tabPickerTable.pack()
        add(ScrollPane(tabPickerTable).apply { this.width= mapEditorScreen.stage.width/3}).row()

        add(editorPickTable).row()
    }

    private fun setImprovements() {

        editorPickTable.clear()

        val improvementsTable = Table()
        improvementsTable.add(getHex(Color.WHITE).apply {
            onClick {
                clearSelection()
                clearImprovement = true
                setCurrentHex(getHex(Color.WHITE), "Clear improvements")
            }
        }).row()

        for(improvement in GameBasics.TileImprovements.values){
            if(improvement.name.startsWith("Remove")) continue
            val improvementImage = getHex(Color.WHITE,ImageGetter.getImprovementIcon(improvement.name,40f))
            improvementImage.onClick {
                clearSelection()
                selectedImprovement=improvement
                val improvementIcon = getHex(Color.WHITE,ImageGetter.getImprovementIcon(improvement.name,40f))
                setCurrentHex(improvementIcon, improvement.name.tr()+"\n"+improvement.clone().toString())
            }
            improvementsTable.add(improvementImage).row()
        }
        editorPickTable.add(ScrollPane(improvementsTable)).height(mapEditorScreen.stage.height*0.7f)

        val nationsTable = Table()
        for(nation in GameBasics.Nations.values){
            val nationImage = getHex(Color.WHITE,ImageGetter.getNationIndicator(nation,40f))
            nationImage.onClick {
                clearSelection()
                selectedImprovement=TileImprovement().apply { name="StartingLocation "+nation.name }
                val nationIcon = getHex(Color.WHITE,ImageGetter.getNationIndicator(nation,40f))
                setCurrentHex(nationIcon, "[${nation.name}] starting location")
            }
            nationsTable.add(nationImage).row()
        }

        editorPickTable.add(ScrollPane(nationsTable)).height(mapEditorScreen.stage.height*0.7f)
    }


    fun setTerrainsAndResources(){

        val baseTerrainTable = Table().apply { defaults().pad(20f) }
        val terrainFeaturesTable = Table().apply { defaults().pad(20f) }

        terrainFeaturesTable.add(getHex(Color.WHITE).apply {
            onClick {
                clearSelection()
                clearTerrainFeature = true
                setCurrentHex(getHex(Color.WHITE), "Clear terrain features")
            }
        }).row()


        addTerrainOptions(terrainFeaturesTable, baseTerrainTable)
//        addRiverToggleOptions(baseTerrainTable)


        val resources = getResourceActors()

        background = ImageGetter.getBackground(Color.GRAY.cpy().apply { a = 0.7f })

        val terrainsAndResourcesTable = Table()
        terrainsAndResourcesTable.add(ScrollPane(baseTerrainTable).apply { setScrollingDisabled(true,false) }).height(mapEditorScreen.stage.height*0.7f)

        terrainsAndResourcesTable.add(ScrollPane(terrainFeaturesTable).apply { setScrollingDisabled(true,false) }).height(mapEditorScreen.stage.height*0.7f)

        val resourcesTable = Table()
        for(resource in resources) resourcesTable.add(resource).row()
        resourcesTable.pack()
        terrainsAndResourcesTable.add(ScrollPane(resourcesTable).apply { setScrollingDisabled(true,false) }).height(mapEditorScreen.stage.height*0.7f).row()

        terrainsAndResourcesTable.pack()

        editorPickTable.clear()
        editorPickTable.add(terrainsAndResourcesTable)
    }

    private fun getResourceActors(): ArrayList<Actor> {
        val resources = ArrayList<Actor>()
        resources.add(getHex(Color.WHITE).apply {
            onClick {
                clearSelection()
                clearResource = true
                setCurrentHex(getHex(Color.WHITE), "Clear resource")
            }
        })

        for (resource in GameBasics.TileResources.values) {
            val resourceHex = getHex(Color.WHITE, ImageGetter.getResourceImage(resource.name, 40f))
            resourceHex.onClick {
                clearSelection()
                selectedResource = resource
                val tileInfo = TileInfo()

                val terrain = resource.terrainsCanBeFoundOn.first()
                val terrainObject = GameBasics.Terrains[terrain]!!
                if (terrainObject.type == TerrainType.TerrainFeature) {
                    tileInfo.baseTerrain = when {
                        terrainObject.occursOn == null -> terrainObject.occursOn!!.first()
                        else -> "Grassland"
                    }
                    tileInfo.terrainFeature = terrain
                } else tileInfo.baseTerrain = terrain

                tileInfo.resource = resource.name
                tileInfo.setTransients()
                setCurrentHex(tileInfo, resource.name.tr() + "\n" + resource.clone().toString())
            }
            resources.add(resourceHex)
        }
        return resources
    }

    private fun addTerrainOptions(terrainFeaturesTable: Table, baseTerrainTable: Table) {
        for (terrain in GameBasics.Terrains.values) {
            val tileInfo = TileInfo()
            if (terrain.type == TerrainType.TerrainFeature) {
                tileInfo.baseTerrain = when {
                    terrain.occursOn == null -> terrain.occursOn!!.first()
                    else -> "Grassland"
                }
                tileInfo.terrainFeature = terrain.name
            } else tileInfo.baseTerrain = terrain.name

            val group = makeTileGroup(tileInfo)

            group.onClick {
                clearSelection()
                selectedTerrain = terrain
                setCurrentHex(tileInfo, terrain.name.tr() + "\n" + terrain.clone().toString())
            }

            if (terrain.type == TerrainType.TerrainFeature)
                terrainFeaturesTable.add(group).row()
            else baseTerrainTable.add(group).row()
        }


        baseTerrainTable.pack()
        terrainFeaturesTable.pack()
    }

    private fun addRiverToggleOptions(baseTerrainTable: Table) {
        baseTerrainTable.addSeparator()


        val tileInfoBottomRightRiver = TileInfo()
        tileInfoBottomRightRiver.baseTerrain = Constants.plains
        tileInfoBottomRightRiver.hasBottomRightRiver = true
        val tileGroupBottomRightRiver = makeTileGroup(tileInfoBottomRightRiver)
        tileGroupBottomRightRiver.onClick {
            clearSelection()
            toggleBottomRightRiver = true
            setCurrentHex(tileInfoBottomRightRiver, "Bottom right river")
        }
        baseTerrainTable.add(tileGroupBottomRightRiver).row()


        val tileInfoBottomRiver = TileInfo()
        tileInfoBottomRiver.baseTerrain = Constants.plains
        tileInfoBottomRiver.hasBottomRiver = true
        val tileGroupBottomRiver = makeTileGroup(tileInfoBottomRiver)
        tileGroupBottomRiver.onClick {
            clearSelection()
            toggleBottomRiver = true
            setCurrentHex(tileInfoBottomRiver, "Bottom river")
        }
        baseTerrainTable.add(tileGroupBottomRiver).row()


        val tileInfoBottomLeftRiver = TileInfo()
        tileInfoBottomLeftRiver.hasBottomLeftRiver = true
        tileInfoBottomLeftRiver.baseTerrain = Constants.plains
        val tileGroupBottomLeftRiver = makeTileGroup(tileInfoBottomLeftRiver)
        tileGroupBottomLeftRiver.onClick {
            clearSelection()
            toggleBottomLeftRiver = true
            setCurrentHex(tileInfoBottomLeftRiver, "Bottom left river")
        }
        baseTerrainTable.add(tileGroupBottomLeftRiver).row()

        baseTerrainTable.pack()
    }

    private fun makeTileGroup(tileInfo: TileInfo): TileGroup {
        tileInfo.setTransients()
        val group = TileGroup(tileInfo, TileSetStrings())
        group.showEntireMap = true
        group.forMapEditorIcon = true
        group.update()
        return group
    }


    fun getHex(color: Color, image: Actor?=null): Group {
        val hex = ImageGetter.getImage(tileSetLocation + "Hexagon")
        hex.color = color
        hex.width*=0.3f
        hex.height*=0.3f
        val group = Group()
        group.setSize(hex.width,hex.height)
        hex.center(group)
        group.addActor(hex)

        if(image!=null) {
            image.setSize(40f, 40f)
            image.center(group)
            group.addActor(image)
        }
        return group
    }


    fun clearSelection(){
        clearTerrainFeature=false
        selectedTerrain=null
        clearResource=false
        selectedResource=null
        clearImprovement=false
        selectedImprovement=null
        toggleBottomLeftRiver = false
        toggleBottomRightRiver = false
        toggleBottomRiver = false
    }

    fun updateTileWhenClicked(tileInfo: TileInfo) {
        when {
            clearTerrainFeature -> tileInfo.terrainFeature = null
            clearResource -> tileInfo.resource = null
            selectedResource != null -> tileInfo.resource = selectedResource!!.name
            selectedTerrain != null -> {
                if (selectedTerrain!!.type == TerrainType.TerrainFeature)
                    tileInfo.terrainFeature = selectedTerrain!!.name
                else tileInfo.baseTerrain = selectedTerrain!!.name
            }
            clearImprovement -> {
                tileInfo.improvement = null
                tileInfo.roadStatus = RoadStatus.None
            }
            selectedImprovement != null -> {
                val improvement = selectedImprovement!!
                if (improvement.name == "Road") tileInfo.roadStatus = RoadStatus.Road
                else if (improvement.name == "Railroad") tileInfo.roadStatus = RoadStatus.Railroad
                else tileInfo.improvement = improvement.name
            }
            toggleBottomLeftRiver -> tileInfo.hasBottomLeftRiver = !tileInfo.hasBottomLeftRiver
            toggleBottomRiver -> tileInfo.hasBottomRiver = !tileInfo.hasBottomRiver
            toggleBottomRightRiver -> tileInfo.hasBottomRightRiver = !tileInfo.hasBottomRightRiver
        }
    }


    fun setCurrentHex(tileInfo: TileInfo, text:String){
        val tileGroup = TileGroup(tileInfo,TileSetStrings())
                .apply {
                    showEntireMap=true
                    forMapEditorIcon=true
                    update()
                }
        setCurrentHex(tileGroup,text)
    }

    fun setCurrentHex(actor:Actor, text:String){
        currentHex.remove()
        val currentHexTable = Table()
        currentHexTable.add(text.toLabel()).padRight(20f)
        currentHexTable.add(actor).pad(10f)
        currentHexTable.pack()
        currentHex=currentHexTable
        currentHex.setPosition(stage.width-currentHex.width-10, 10f)
        stage.addActor(currentHex)
    }


}