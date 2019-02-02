package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.GameSaver
import com.unciv.logic.map.MapType
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.Terrain
import com.unciv.models.gamebasics.tile.TerrainType
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.center
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.TileGroupMap

class MapEditorScreen: CameraStageBaseScreen(){
    var clearTerrainFeature=false
    var selectedTerrain : Terrain?=null
    var clearResource=false
    var selectedResource:TileResource?=null

    fun clearSelection(){
        clearTerrainFeature=false
        selectedTerrain=null
        clearResource=false
        selectedResource=null
    }

    fun getHex(color: Color, image: Actor?=null): Table {
        val hex = ImageGetter.getImage("TerrainIcons/Hexagon.png")
        hex.color = color
        val group = Table().apply { add(hex).size(hex.width*0.3f,hex.height*0.3f); pack() }

        if(image!=null) {
            image.setSize(40f, 40f)
            image.center(group)
            group.addActor(image)
        }
        return group
    }

    init{
        val tileMap = TileMap(20, MapType.Default)
        val scrollPane = getMapHolder(tileMap)

        stage.addActor(scrollPane)

        val scrollTable = getTileEditorOptions()
        stage.addActor(scrollTable)

        val saveMapButton = TextButton("Save map",skin)
        saveMapButton.onClick {
            GameSaver().saveMap("Test",tileMap)
            UnCivGame.Current.setWorldScreen()
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
        return scrollPane
    }

    private fun getTileEditorOptions(): Table {

        val baseTerrainHolder = Table()
        val terrainFeatureHolder = Table()
        terrainFeatureHolder.add(getHex(Color.WHITE).apply { onClick { clearSelection(); clearTerrainFeature = true } })

        for (terrain in GameBasics.Terrains.values) {
            var icon: Image? = null
            var color = Color.WHITE

            if (terrain.type == TerrainType.TerrainFeature)
                icon = ImageGetter.getImage("TerrainIcons/${terrain.name}.png")
            else {
                color = terrain.getColor()
                val imagePath = "TerrainIcons/" + terrain.name
                if (ImageGetter.imageExists(imagePath)) {
                    icon = ImageGetter.getImage(imagePath)
                }
            }

            val group = getHex(color, icon)
            group.onClick { clearSelection(); selectedTerrain = terrain }

            if (terrain.type == TerrainType.TerrainFeature) terrainFeatureHolder.add(group)
            else baseTerrainHolder.add(group)
        }

        baseTerrainHolder.pack()
        terrainFeatureHolder.pack()

        val resourcesHolder = Table()
        resourcesHolder.add(getHex(Color.WHITE).apply { onClick { clearSelection(); clearResource = true } })

        for (resource in GameBasics.TileResources.values) {
            val resourceHex = getHex(Color.WHITE, ImageGetter.getResourceImage(resource.name, 40f))
            resourceHex.onClick { clearSelection(); selectedResource = resource }
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