package com.unciv.logic.automation.ai

import com.badlogic.gdx.graphics.Color
import com.unciv.GUI
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.components.extensions.toGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.utils.Log

class TacticalAI : IsPartOfGameInfoSerialization {

    private val debug: Boolean = false

    @Transient private val tacticalAnalysisMap = TacticalAnalysisMap()
    @Transient private var player: Civilization? = null

    fun init(player: Civilization) {
        this.player = player
        tacticalAnalysisMap.reset(player)
    }

    fun showZonesDebug(tile: Tile) {

        if (!debug)
            return

        val zone = tacticalAnalysisMap.getZoneByTile(tile)
        val zoneId = zone?.id

        Log.debug("MYTAG Zone $zoneId City: ${zone?.city} Area: ${zone?.area} Area size: ${
            tile.tileMap.continentSizes[tile.getContinent()]} Zone size: ${zone?.tileCount}")

        val mapHolder = GUI.getMap()

        for (otherTile in mapHolder.tileMap.values.asSequence()) {

            val otherZoneId = tacticalAnalysisMap.plotPositionToZoneId[otherTile.position]
            if (otherZoneId == zoneId) {
                mapHolder.tileGroups[otherTile]?.let {
                    mapHolder.addOverlayOnTileGroup(it, ImageGetter.getCircle(
                        color = when (zone?.territoryType) {
                            TacticalTerritoryType.FRIENDLY -> Color.GREEN
                            TacticalTerritoryType.ENEMY -> Color.RED
                            else -> Color.WHITE
                        }
                    ).toGroup(20f)) }
            }

            if (zone?.neighboringZones?.contains(otherZoneId) == true) {
                mapHolder.tileGroups[otherTile]?.let {
                    mapHolder.addOverlayOnTileGroup(it, ImageGetter.getCircle(color = Color.GRAY).toGroup(20f)) }
            }
        }
    }
}
