package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.GameParameters
import com.unciv.UnCivGame
import com.unciv.logic.GameSaver
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.Terrain
import com.unciv.models.gamebasics.tile.TerrainType
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.models.gamebasics.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.center
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.TileGroupMap

class MapEditorScreen(): CameraStageBaseScreen(){
    val tileSetLocation = "TileSets/"+UnCivGame.Current.settings.tileSet +"/"

    var clearTerrainFeature=false
    var selectedTerrain : Terrain?=null
    var clearResource=false
    var selectedResource:TileResource?=null
    var tileMap = TileMap(GameParameters())
    var mapName = "My first map"
    var currentHex=Group()

    fun clearSelection(){
        clearTerrainFeature=false
        selectedTerrain=null
        clearResource=false
        selectedResource=null
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

    constructor(mapNameToLoad:String?):this(){
        var mapToLoad = mapNameToLoad
        if (mapToLoad == null) {
            val existingSaves = GameSaver().getMaps()
            if(existingSaves.isNotEmpty())
                mapToLoad = existingSaves.first()
        }
        if(mapToLoad!=null){
            mapName=mapToLoad
            tileMap=GameSaver().loadMap(mapName)
        }
        initialize()
    }

    constructor(map: TileMap):this(){
        tileMap = map
        initialize()
    }

    fun initialize(){
        tileMap.setTransients()
        val mapHolder = getMapHolder(tileMap)

        stage.addActor(mapHolder)

        val scrollTable = getTileEditorOptions()
        stage.addActor(scrollTable)


        val saveMapButton = TextButton("Options".tr(),skin)
        saveMapButton.onClick {
            MapEditorOptionsTable(this)
        }
        stage.addActor(saveMapButton)
    }

    private fun getMapHolder(tileMap: TileMap): ScrollPane {
        val tileGroups = tileMap.values.map { TileGroup(it) }
        for (tileGroup in tileGroups) {
            tileGroup.showEntireMap = true
            tileGroup.update(true, true, true)
            tileGroup.onClick {
                val tileInfo = tileGroup.tileInfo
                when {
                    clearTerrainFeature -> tileInfo.terrainFeature = null
                    clearResource -> tileInfo.resource = null
                    selectedResource != null -> tileInfo.resource = selectedResource!!.name
                    selectedTerrain != null -> {
                        if (selectedTerrain!!.type == TerrainType.TerrainFeature)
                            tileGroup.tileInfo.terrainFeature = selectedTerrain!!.name
                        else tileGroup.tileInfo.baseTerrain = selectedTerrain!!.name
                    }
                }
                tileGroup.tileInfo.setTransients()
                tileGroup.update(true, true, true)
            }
        }

        val mapHolder = TileGroupMap(tileGroups, 300f)
        val scrollPane = ScrollPane(mapHolder)
        scrollPane.setSize(stage.width, stage.height)
        scrollPane.layout()
        scrollPane.scrollPercentX=0.5f
        scrollPane.scrollPercentY=0.5f
        scrollPane.updateVisualScroll()
        return scrollPane
    }


    fun setCurrentHex(color: Color, image: Actor?=null){
        currentHex.remove()
        currentHex=getHex(color,image)
        currentHex.setPosition(stage.width-currentHex.width-10, 10f)
        stage.addActor(currentHex)
    }

    private fun getTileEditorOptions(): Table {

        val baseTerrainHolder = Table()
        val terrainFeatureHolder = Table()
        terrainFeatureHolder.add(getHex(Color.WHITE).apply {
            onClick {
                clearSelection()
                clearTerrainFeature = true
                setCurrentHex(Color.WHITE)
            }
        })

        for (terrain in GameBasics.Terrains.values) {
            var iconPath: String? = null
            var color = Color.WHITE

            if (terrain.type == TerrainType.TerrainFeature)
                iconPath = tileSetLocation + terrain.name+"Overlay"
            else {
                color = terrain.getColor()
                val imagePath = tileSetLocation + terrain.name+"Overlay"
                if (ImageGetter.imageExists(imagePath)) {
                    iconPath = imagePath
                }
            }

            val group = getHex(color, if(iconPath==null) null else ImageGetter.getImage(iconPath))
            group.onClick {
                clearSelection()
                selectedTerrain = terrain
                setCurrentHex(color, if(iconPath==null) null else ImageGetter.getImage(iconPath))
            }

            if (terrain.type == TerrainType.TerrainFeature)
                terrainFeatureHolder.add(group)
            else baseTerrainHolder.add(group)
        }

        baseTerrainHolder.pack()
        terrainFeatureHolder.pack()

        val resourcesHolder = Table()
        resourcesHolder.add(getHex(Color.WHITE).apply { onClick { clearSelection(); clearResource = true } })

        for (resource in GameBasics.TileResources.values) {
            val resourceHex = getHex(Color.WHITE, ImageGetter.getResourceImage(resource.name, 40f))
            resourceHex.onClick {
                clearSelection()
                selectedResource = resource
                setCurrentHex(Color.WHITE, ImageGetter.getResourceImage(resource.name, 40f))
            }
            resourcesHolder.add(resourceHex)
        }

        val scrollTable = Table()
        scrollTable.background = ImageGetter.getBackground(Color.GRAY.cpy().apply { a = 0.7f })
        scrollTable.add(baseTerrainHolder).width(stage.width).row()
        scrollTable.add(terrainFeatureHolder).width(stage.width).row()
        scrollTable.add(ScrollPane(resourcesHolder)).width(stage.width).row()
        scrollTable.pack()
        scrollTable.setPosition(0f, stage.height - scrollTable.height)
        return scrollTable
    }
}

class NewMapScreen:PickerScreen(){
    init{

    }
}