package com.unciv.ui.mapeditor

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Stage
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.map.TileGroupMap
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.*

class EditorMapHolderV2(
    parentScreen: BaseScreen,
    internal val tileMap: TileMap,
    private val ruleset: Ruleset,
    private val onTileClick: (TileInfo) -> Unit
): ZoomableScrollPane() {
    val tileGroups = HashMap<TileInfo, List<TileGroup>>()
    private lateinit var tileGroupMap: TileGroupMap<TileGroup>
    private val allTileGroups = ArrayList<TileGroup>()

    private val maxWorldZoomOut = UncivGame.Current.settings.maxWorldZoomOut
    private val minZoomScale = 1f / maxWorldZoomOut

    init {
        continuousScrollingX = tileMap.mapParameters.worldWrap
        addTiles(parentScreen.stage)
    }

    internal fun addTiles(stage: Stage) {

        val tileSetStrings = TileSetStrings()
        val daTileGroups = tileMap.values.map { TileGroup(it, tileSetStrings) }

        tileGroupMap = TileGroupMap(
            daTileGroups,
            stage.width * maxWorldZoomOut / 2,
            stage.height * maxWorldZoomOut / 2,
            continuousScrollingX)
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

/* revisit when Unit editing is re-implemented
            // This is a hack to make the unit icons render correctly on the game, even though the map isn't part of a game
            // and the units aren't assigned to any "real" CivInfo
            //to do make safe the !!
            //to do worse - don't create a whole Civ instance per unit
            tileGroup.tileInfo.getUnits().forEach {
                it.civInfo = CivilizationInfo().apply {
                    nation = ruleset.nations[it.owner]!!
                }
            }
*/
            tileGroup.showEntireMap = true
            tileGroup.update()
            tileGroup.onClick { onTileClick(tileGroup.tileInfo) }
        }

        setSize(stage.width * maxWorldZoomOut, stage.height * maxWorldZoomOut)
        setOrigin(width / 2,height / 2)
        center(stage)

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

    // This emulates `private TileMap.getOrNull(Int,Int)` and should really move there
    // still more efficient than `if (rounded in tileMap) tileMap[rounded] else null`
    private fun TileMap.getOrNull(pos: Vector2): TileInfo? {
        val x = pos.x.toInt()
        val y = pos.y.toInt()
        if (contains(x, y)) return get(x, y)
        return null
    }

    // Currently unused, drag painting will need it
    fun getClosestTileTo(stageCoords: Vector2): TileInfo? {
        val positionalCoords = tileGroupMap.getPositionalVector(stageCoords)
        val hexPosition = HexMath.world2HexCoords(positionalCoords)
        val rounded = HexMath.roundHexCoords(hexPosition)
        return tileMap.getOrNull(rounded)
    }

    fun setCenterPosition(vector: Vector2) {
        val tileGroup = allTileGroups.firstOrNull { it.tileInfo.position == vector } ?: return
        scrollX = tileGroup.x + tileGroup.width / 2 - width / 2
        scrollY = maxY - (tileGroup.y + tileGroup.width / 2 - height / 2)
    }

    override fun zoom(zoomScale: Float) {
        if (zoomScale < minZoomScale || zoomScale > 2f) return
        setScale(zoomScale)
    }
}
