package com.unciv.ui.mapeditor

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.TileInfo
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.onClick
import com.unciv.ui.map.TileGroupMap
import com.unciv.ui.utils.*
import com.unciv.logic.HexMath

class EditorMapHolder(internal val mapEditorScreen: MapEditorScreen, internal val tileMap: TileMap): ZoomableScrollPane() {
    val tileGroups = HashMap<TileInfo, TileGroup>()
    lateinit var tileGroupMap: TileGroupMap<TileGroup>

    internal fun addTiles() {

        val tileSetStrings = TileSetStrings()
        for (tileGroup in tileMap.values.map { TileGroup(it, tileSetStrings) })
            tileGroups[tileGroup.tileInfo] = tileGroup

        tileGroupMap = TileGroupMap(tileGroups.values, mapEditorScreen.stage.width)
        actor = tileGroupMap

        for (tileGroup in tileGroups.values) {
            tileGroup.showEntireMap = true
            tileGroup.update()
            tileGroup.onClick {

                val distance = mapEditorScreen.tileEditorOptions.brushSize - 1

                for (tileInfo in mapEditorScreen.tileMap.getTilesInDistance(tileGroup.tileInfo.position, distance)) {
                    mapEditorScreen.tileEditorOptions.updateTileWhenClicked(tileInfo)

                    tileInfo.setTransients()
                    tileGroups[tileInfo]!!.update()
                }
            }
        }

        setSize(mapEditorScreen.stage.width * 2, mapEditorScreen.stage.height * 2)
        setOrigin(width / 2,height / 2)
        center(mapEditorScreen.stage)

        layout()

        scrollPercentX = .5f
        scrollPercentY = .5f
        updateVisualScroll()
    }

    fun updateTileGroups() {
        for (tileGroup in tileGroups.values)
            tileGroup.update()
    }

    fun setTransients() {
        for (tileInfo in tileGroups.keys)
            tileInfo.setTransients()
    }

    fun getClosestTileTo(stageCoords: Vector2): TileInfo? {
        val positionalCoords = tileGroupMap.getPositionalVector(stageCoords)
        val hexPosition = HexMath.world2HexCoords(positionalCoords)
        val rounded = HexMath.roundHexCoords(hexPosition)

        if (tileMap.contains(rounded))
            return tileMap.get(rounded)
        else
            return null
    }
}