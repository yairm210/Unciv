package com.unciv.logic.automation.ai

import com.badlogic.gdx.graphics.Color
import com.unciv.UncivGame
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.toGroup
import com.unciv.utils.Log

class TacticalAI : IsPartOfGameInfoSerialization {

    private val debug: Boolean = false

    @Transient private val tacticalAnalysisMap = TacticalAnalysisMap()
    @Transient private var player: CivilizationInfo? = null

    fun init(player: CivilizationInfo) {
        this.player = player
        tacticalAnalysisMap.reset(player)
    }

    fun showZonesDebug(tile: TileInfo) {

        if (!debug)
            return

        val zone = tacticalAnalysisMap.getZoneByTile(tile)
        val zoneId = zone?.id

        Log.debug("MYTAG Zone $zoneId City: ${zone?.city} Area: ${zone?.area} Area size: ${
            tile.tileMap.continentSizes[tile.getContinent()]} Zone size: ${zone?.tileCount}")

        val mapHolder = UncivGame.Current.worldScreen!!.mapHolder

        for (otherTile in mapHolder.tileMap.values.asSequence()) {

            val otherZoneId = tacticalAnalysisMap.plotPositionToZoneId[otherTile.position]
            if (otherZoneId == zoneId) {
                mapHolder.tileGroups[otherTile]?.forEach {
                    mapHolder.addOverlayOnTileGroup(it, ImageGetter.getCircle().apply {
                        color = when (zone?.territoryType) {
                            TacticalTerritoryType.FRIENDLY -> Color.GREEN
                            TacticalTerritoryType.ENEMY -> Color.RED
                            else -> Color.WHITE
                        }
                    }.toGroup(20f)) }
            }

            if (zone?.neighboringZones?.contains(otherZoneId) == true) {
                mapHolder.tileGroups[otherTile]?.forEach {
                    mapHolder.addOverlayOnTileGroup(it, ImageGetter.getCircle().apply { color = Color.GRAY }.toGroup(20f)) }
            }
        }
    }
}
