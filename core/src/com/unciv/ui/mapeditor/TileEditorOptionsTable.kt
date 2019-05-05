package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UnCivGame
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.Terrain
import com.unciv.models.gamebasics.tile.TerrainType
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.center
import com.unciv.ui.utils.onClick

class TileEditorOptionsTable(mapEditorScreen: MapEditorScreen): Table(){
    val tileSetLocation = "TileSets/"+ UnCivGame.Current.settings.tileSet +"/"

    var clearTerrainFeature=false
    var selectedTerrain : Terrain?=null
    var clearResource=false
    var selectedResource: TileResource?=null

    private var currentHex: Actor = Group()

    init{
        val baseTerrains = ArrayList<Actor>()
        val terrainFeatures=ArrayList<Actor>()
        terrainFeatures.add(getHex(Color.WHITE).apply {
            onClick {
                clearSelection()
                clearTerrainFeature = true
                setCurrentHex(null)
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
                setCurrentHex(null)
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

        val baseTerrainTable = Table().apply { defaults().pad(20f) }
        for(baseTerrain in baseTerrains) baseTerrainTable.add(baseTerrain).row()
        baseTerrainTable.pack()
        add(baseTerrainTable)

        val terrainFeaturesTable = Table().apply { defaults().pad(20f) }
        for(terrainFeature in terrainFeatures) terrainFeaturesTable.add(terrainFeature).row()
        terrainFeaturesTable.pack()
        add(terrainFeaturesTable)

        val resourcesTable = Table()
        for(resource in resources) resourcesTable.add(resource).row()
        resourcesTable.pack()
        add(ScrollPane(resourcesTable)).height(mapEditorScreen.stage.height/2).row()

        height=mapEditorScreen.stage.height
        pack()
        setPosition(mapEditorScreen.stage.width - width, 0f)
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
    }

    fun updateTile(tileInfo: TileInfo) {
        when {
            clearTerrainFeature -> tileInfo.terrainFeature = null
            clearResource -> tileInfo.resource = null
            selectedResource != null -> tileInfo.resource = selectedResource!!.name
            selectedTerrain != null -> {
                if (selectedTerrain!!.type == TerrainType.TerrainFeature)
                    tileInfo.terrainFeature = selectedTerrain!!.name
                else tileInfo.baseTerrain = selectedTerrain!!.name
            }
        }
    }


    fun setCurrentHex(tileInfo: TileInfo?){
        currentHex.remove()
        if(tileInfo!=null)currentHex= TileGroup(tileInfo)
                .apply {
                    showEntireMap=true
                    forMapEditorIcon=true
                    update(true,true,true)
                }
        else currentHex = ImageGetter.getCircle().apply { setSize(60f,60f) }
        currentHex.setPosition(stage.width-currentHex.width-10, 10f)
        stage.addActor(currentHex)
    }

}