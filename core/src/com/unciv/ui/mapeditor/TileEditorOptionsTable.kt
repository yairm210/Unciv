package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.Terrain
import com.unciv.models.gamebasics.tile.TerrainType
import com.unciv.models.gamebasics.tile.TileImprovement
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.center
import com.unciv.ui.utils.onClick

class TileEditorOptionsTable(val mapEditorScreen: MapEditorScreen): Table(CameraStageBaseScreen.skin){
    val tileSetLocation = "TileSets/"+ UnCivGame.Current.settings.tileSet +"/"

    var clearTerrainFeature=false
    var selectedTerrain : Terrain?=null

    var clearResource=false
    var selectedResource: TileResource?=null

    var clearImprovement=false
    var selectedImprovement:TileImprovement?=null

    val editorPickTable = Table()

    private var currentHex: Actor = Group()

    init{
        height=mapEditorScreen.stage.height
        width=mapEditorScreen.stage.width/3

        setTerrainsAndResources()

        val tabPickerTable = Table().apply { defaults().pad(10f) }
        val terrainsAndResourcesTabButton = TextButton("Terrains & Resources",skin)
                .onClick { setTerrainsAndResources() }
        tabPickerTable.add(terrainsAndResourcesTabButton)

        val civLocationsButton = TextButton("Improvements",skin)
                .onClick { setImprovements() }
        tabPickerTable.add(civLocationsButton)
        tabPickerTable.pack()
        add(ScrollPane(tabPickerTable).apply { this.width= mapEditorScreen.stage.width/3}).row()

        add(editorPickTable).row()

        setPosition(mapEditorScreen.stage.width - width, 0f)
    }

    private fun setImprovements() {

        editorPickTable.clear()

        val improvementsTable = Table()
        improvementsTable.add(getHex(Color.WHITE).apply {
            onClick {
                clearSelection()
                clearImprovement = true
                setCurrentHex(ImageGetter.getCircle().apply { setSize(60f,60f) })
            }
        }).row()

        for(improvement in GameBasics.TileImprovements.values){
            if(improvement.name.startsWith("Remove")) continue
            val improvementImage = getHex(Color.WHITE,ImageGetter.getImprovementIcon(improvement.name,40f))
            improvementImage.onClick {
                clearSelection()
                selectedImprovement=improvement
                setCurrentHex(getHex(Color.WHITE,ImageGetter.getImprovementIcon(improvement.name,40f)))
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
                setCurrentHex(getHex(Color.WHITE,ImageGetter.getNationIndicator(nation,40f)))
            }
            nationsTable.add(nationImage).row()
        }

        editorPickTable.add(ScrollPane(nationsTable)).height(mapEditorScreen.stage.height*0.7f)
    }

    fun setTerrainsAndResources(){

        val baseTerrains = ArrayList<Actor>()
        val terrainFeatures=ArrayList<Actor>()
        terrainFeatures.add(getHex(Color.WHITE).apply {
            onClick {
                clearSelection()
                clearTerrainFeature = true
                setCurrentHex(ImageGetter.getCircle().apply { setSize(60f,60f) })
            }
        })

        for (terrain in GameBasics.Terrains.values) {
            val tileInfo = TileInfo()
            if (terrain.type == TerrainType.TerrainFeature) {
                tileInfo.baseTerrain = terrain.occursOn!!.first()
                tileInfo.terrainFeature=terrain.name
            }
            else tileInfo.baseTerrain=terrain.name

            tileInfo.setTransients()
            val group = TileGroup(tileInfo)
            group.showEntireMap=true
            group.forMapEditorIcon=true
            group.update(true,true,true)

            group.onClick {
                clearSelection()
                selectedTerrain = terrain
                setCurrentHex(tileInfo)
            }

            if (terrain.type == TerrainType.TerrainFeature)
                terrainFeatures.add(group)
            else baseTerrains.add(group)
        }


        val resources = ArrayList<Actor>()
        resources.add(getHex(Color.WHITE).apply {
            onClick {
                clearSelection()
                clearResource = true
                setCurrentHex(ImageGetter.getCircle().apply { setSize(60f,60f) })
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
                if(terrainObject.type== TerrainType.TerrainFeature){
                    tileInfo.baseTerrain=terrainObject.occursOn!!.first()
                    tileInfo.terrainFeature=terrain
                }
                else tileInfo.baseTerrain=terrain

                tileInfo.resource = resource.name
                tileInfo.setTransients()
                setCurrentHex(tileInfo)
            }
            resources.add(resourceHex)
        }

        background = ImageGetter.getBackground(Color.GRAY.cpy().apply { a = 0.7f })

        val terrainsAndResourcesTable = Table()
        val baseTerrainTable = Table().apply { defaults().pad(20f) }
        for(baseTerrain in baseTerrains) baseTerrainTable.add(baseTerrain).row()
        baseTerrainTable.pack()
        terrainsAndResourcesTable.add(ScrollPane(baseTerrainTable).apply { setScrollingDisabled(true,false) }).height(mapEditorScreen.stage.height*0.7f)

        val terrainFeaturesTable = Table().apply { defaults().pad(20f) }
        for(terrainFeature in terrainFeatures) terrainFeaturesTable.add(terrainFeature).row()
        terrainFeaturesTable.pack()
        terrainsAndResourcesTable.add(terrainFeaturesTable)

        val resourcesTable = Table()
        for(resource in resources) resourcesTable.add(resource).row()
        resourcesTable.pack()
        terrainsAndResourcesTable.add(ScrollPane(resourcesTable).apply { setScrollingDisabled(true,false) }).height(mapEditorScreen.stage.height*0.7f).row()

        terrainsAndResourcesTable.pack()

        editorPickTable.clear()
        editorPickTable.add(terrainsAndResourcesTable)
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
                tileInfo.improvement=null
                tileInfo.roadStatus=RoadStatus.None
            }
            selectedImprovement!=null -> {
                val improvement = selectedImprovement!!
                if(improvement.name=="Road") tileInfo.roadStatus=RoadStatus.Road
                else if(improvement.name=="Railroad") tileInfo.roadStatus=RoadStatus.Railroad
                else tileInfo.improvement=improvement.name
            }
        }
    }


    fun setCurrentHex(tileInfo: TileInfo){
        val tileGroup = TileGroup(tileInfo)
                .apply {
                    showEntireMap=true
                    forMapEditorIcon=true
                    update(true,true,true)
                }
        setCurrentHex(tileGroup)
    }

    fun setCurrentHex(actor:Actor){
        currentHex.remove()
        currentHex=actor
        currentHex.setPosition(stage.width-currentHex.width-10, 10f)
        stage.addActor(currentHex)
    }


}