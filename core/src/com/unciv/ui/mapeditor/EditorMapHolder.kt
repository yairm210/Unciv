package com.unciv.ui.mapeditor

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.ui.map.TileGroupMap
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.ZoomableScrollPane
import com.unciv.ui.utils.center
import com.unciv.ui.utils.onClick

class EditorMapHolder(private val mapEditorScreen: MapEditorScreen, internal val tileMap: TileMap): ZoomableScrollPane() {
    val tileGroups = HashMap<TileInfo, List<TileGroup>>()
    lateinit var tileGroupMap: TileGroupMap<TileGroup>
    private val allTileGroups = ArrayList<TileGroup>()

    init {
        continuousScrollingX = tileMap.mapParameters.worldWrap
    }

    internal fun addTiles(leftAndRightPadding: Float, topAndBottomPadding: Float) {

        val tileSetStrings = TileSetStrings()
        val daTileGroups = tileMap.values.map { TileGroup(it, tileSetStrings) }

        tileGroupMap = TileGroupMap(daTileGroups, leftAndRightPadding, topAndBottomPadding, continuousScrollingX)
        actor = tileGroupMap
        val mirrorTileGroups = tileGroupMap.getMirrorTiles()

        for (tileGroup in daTileGroups) {
            if (continuousScrollingX){
                val mirrorTileGroupLeft = mirrorTileGroups[tileGroup.tileInfo]!!.first
                val mirrorTileGroupRight = mirrorTileGroups[tileGroup.tileInfo]!!.second

                allTileGroups.add(tileGroup)
                allTileGroups.add(mirrorTileGroupLeft)
                allTileGroups.add(mirrorTileGroupRight)

                tileGroups[tileGroup.tileInfo] = listOf(tileGroup, mirrorTileGroupLeft, mirrorTileGroupRight)
            } else {
                tileGroups[tileGroup.tileInfo] = listOf(tileGroup)
                allTileGroups.add(tileGroup)
            }
        }

        for (tileGroup in allTileGroups) {

            // This is a hack to make the unit icons render correctly on the game, even though the map isn't part of a game
            // and the units aren't assigned to any "real" CivInfo
            tileGroup.tileInfo.getUnits().forEach { it.civInfo= CivilizationInfo()
                    .apply { nation=mapEditorScreen.ruleset.nations[it.owner]!! } }

            tileGroup.showEntireMap = true
            tileGroup.update()
            tileGroup.onClick {

                val distance = mapEditorScreen.mapEditorOptionsTable.brushSize - 1

                for (tileInfo in mapEditorScreen.tileMap.getTilesInDistance(tileGroup.tileInfo.position, distance)) {
                    mapEditorScreen.mapEditorOptionsTable.updateTileWhenClicked(tileInfo)

                    tileInfo.setTerrainTransients()
                    tileGroups[tileInfo]!!.forEach { it.update() }
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
        for (tileGroup in allTileGroups)
            tileGroup.update()
    }

    fun setTransients() {
        for (tileInfo in tileGroups.keys)
            tileInfo.setTerrainTransients()
    }

    fun getClosestTileTo(stageCoords: Vector2): TileInfo? {
        val positionalCoords = tileGroupMap.getPositionalVector(stageCoords)
        val hexPosition = HexMath.world2HexCoords(positionalCoords)
        val rounded = HexMath.roundHexCoords(hexPosition)

        return if (rounded in tileMap) tileMap[rounded] else null
    }
}
