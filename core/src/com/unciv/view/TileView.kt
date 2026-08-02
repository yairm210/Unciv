package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.stats.Stats
import yairm210.purity.annotations.Readonly

/** View of a [Tile] from the perspective of [viewer]. */
class TileView(private val tile: Tile, private val viewer: Civilization) {
    @Readonly fun position() = tile.position
    @Readonly fun owningCity(): ForeignCityView? = tile.owningCity?.let { ForeignCityView(it, viewer) }
    @Readonly fun getWorkingCity(): ForeignCityView? = tile.getWorkingCity()?.let { ForeignCityView(it, viewer) }
    val neighbors: Sequence<TileView> @Readonly get() = tile.neighbors.asSequence().map { TileView(it, viewer) }
    @Readonly fun getTilesInDistance(distance: Int): Sequence<TileView> =
        tile.getTilesInDistance(distance).map { TileView(it, viewer) }

    @Readonly fun isCityCenter(): Boolean = tile.isCityCenter()
    @Readonly fun isLocked(): Boolean = tile.isLocked()

    @Readonly fun getTileStats(cityView: CityView): Stats = tile.stats.getTileStats(cityView.getCity(), cityView.getCity().civ)

    @Readonly fun getTile(): Tile = tile
    @Readonly fun getViewer(): Civilization = viewer
}
