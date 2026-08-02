package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.stats.Stats
import yairm210.purity.annotations.Readonly

/** View of a [Tile] from the perspective of [viewer]. */
class TileView(private val tile: Tile, private val viewer: Civilization) {
    @Readonly fun position() = tile.position
    @Readonly fun owningCity(): ForeignCityView? = tile.owningCity?.let { ForeignCityView(it, viewer) }
    @Readonly fun getWorkingCity(): ForeignCityView? = tile.getWorkingCity()?.let { ForeignCityView(it, viewer) }
    val neighbors: Sequence<TileView> @Readonly get() = tile.neighbors.map { TileView(it, viewer) }
    @Readonly fun getTilesInDistance(distance: Int): Sequence<TileView> =
        tile.getTilesInDistance(distance).map { TileView(it, viewer) }

    @Readonly fun isCityCenter(): Boolean = tile.isCityCenter()
    @Readonly fun isLocked(): Boolean = tile.isLocked()
    @Readonly fun isImpassible(): Boolean = tile.isImpassible()
    @Readonly fun isAdjacentTo(terrainFilter: String): Boolean = tile.isAdjacentTo(terrainFilter)
    @Readonly fun getDefensiveBonus(): Float = tile.getDefensiveBonus()
    @Readonly fun getShownImprovement(): String? = tile.getShownImprovement(null)

    val baseTerrain: String get() = tile.baseTerrain
    val terrainFeatures: List<String> get() = tile.terrainFeatures
    @Readonly fun getViewableResource(viewingCiv: CivView?): TileResource? {
        val resource = tile.tileResource ?: return null
        return if (viewingCiv == null || viewingCiv.canSeeResource(resource)) resource else null
    }
    val resource: String? get() = tile.resource
    val resourceAmount: Int get() = tile.resourceAmount
    val naturalWonder: String? get() = tile.naturalWonder
    val roadStatus: RoadStatus get() = tile.roadStatus
    val roadIsPillaged: Boolean get() = tile.roadIsPillaged
    val improvementIsPillaged: Boolean get() = tile.improvementIsPillaged
    val improvementInProgress: String? get() = tile.improvementInProgress
    val turnsToImprovement: Int get() = tile.turnsToImprovement
    val civilianUnit: ForeignMapUnitView? get() = tile.civilianUnit?.let { ForeignMapUnitView(it, viewer) }
    val militaryUnit: ForeignMapUnitView? get() = tile.militaryUnit?.let { ForeignMapUnitView(it, viewer) }
    val isLand: Boolean get() = tile.isLand
    @Readonly fun getRuleset(): Ruleset = tile.ruleset

    @Readonly fun getTileStats(cityView: CityView): Stats = tile.stats.getTileStats(cityView.getCity(), cityView.getCity().civ)

    @Readonly fun getTile(): Tile = tile
    @Readonly fun getViewer(): Civilization = viewer
}
