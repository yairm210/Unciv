package com.unciv.view

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.stats.Stats
import yairm210.purity.annotations.Readonly

/** View of a [Tile] from the perspective of [viewer] via [tileMapView]. */
class TileView internal constructor(private val tile: Tile, val tileMapView: TileMapView,
               private val viewer: Civilization?,
               private val spectatorMode: Boolean = false) {

    // Navigation
    @Readonly fun getTile(): Tile = tile
    @Readonly fun getViewer(): Civilization? = viewer
    @Readonly fun owningCity(): ForeignCityView? {
        val city = tile.owningCity ?: return null
        return toForeignCityView(city)
    }
    @Readonly fun getWorkingCity(): ForeignCityView? {
        val city = tile.getWorkingCity() ?: return null
        return toForeignCityView(city)
    }
    @Readonly private fun toForeignCityView(city: City): ForeignCityView? {
        val viewer = viewer ?: return null
        val gameView = tileMapView.gameView ?: return null
        return ForeignCityView(city, viewer, spectatorMode, gameView)
    }
    @Readonly fun getOwner(): ForeignCivView? {
        val owner = tile.getOwner() ?: return null
        if (viewer == null) return null
        return ForeignCivView(owner, viewer, spectatorMode)
    }
    @Readonly private fun isVisible(unit: MapUnit): Boolean {
        if (viewer == null) return false
        if (!tile.isVisible(viewer)) return false
        return !unit.isInvisible(viewer) || tile in viewer.viewableInvisibleUnitsTiles
    }
    val civilianUnit: ForeignMapUnitView?
        get() {
            val unit = tile.civilianUnit ?: return null
            if (!isVisible(unit)) return null
            return ForeignMapUnitView(unit, viewer!!)
        }
    val militaryUnit: ForeignMapUnitView?
        get() {
            val unit = tile.militaryUnit ?: return null
            if (!isVisible(unit)) return null
            return ForeignMapUnitView(unit, viewer!!)
        }
    @Readonly fun getVisibleUnits(): List<ForeignMapUnitView> {
        if (viewer == null) return emptyList()
        return tile.getUnits()
            .filter { isVisible(it) }
            .map { ForeignMapUnitView(it, viewer) }
            .toList()
    }

    // Data retrieval
    @Readonly fun position() = tile.position
    @Readonly fun getVisibleNeighbors(): Sequence<TileView> =
        tile.neighbors
            .filter { viewer == null || it.isExplored(viewer) }
            .map { tileMapView.getTile(it) }
    @Readonly fun getVisibleTilesInDistance(distance: Int): Sequence<TileView> =
        tile.getTilesInDistance(distance)
            .filter { viewer == null || it.isExplored(viewer) }
            .map { tileMapView.getTile(it) }

    @Readonly fun isCityCenter(): Boolean = tile.isCityCenter()
    @Readonly fun isWorked(): Boolean = tile.isWorked()
    @Readonly fun isBlockaded(): Boolean = tile.isBlockaded()
    @Readonly fun providesYield(): Boolean = tile.providesYield()
    @Readonly fun isLocked(): Boolean = tile.isLocked()
    @Readonly fun isImpassible(): Boolean = tile.isImpassible()
    @Readonly fun isAdjacentTo(terrainFilter: String): Boolean = tile.isAdjacentTo(terrainFilter)
    @Readonly fun getDefensiveBonus(): Float = tile.getDefensiveBonus()
    @Readonly fun getShownImprovement(): String? = tile.getShownImprovement(viewer)

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

    val isLand: Boolean get() = tile.isLand
    val hasBottomRightRiver: Boolean get() = tile.hasBottomRightRiver
    val hasBottomRiver: Boolean get() = tile.hasBottomRiver
    val hasBottomLeftRiver: Boolean get() = tile.hasBottomLeftRiver
    @Readonly fun isPillaged(): Boolean = tile.isPillaged()
    @Readonly fun getBaseTerrain(): Terrain = tile.getBaseTerrain()
    @Readonly fun getRuleset(): Ruleset = tile.ruleset

    @Readonly fun getTileStats(viewingCiv: CivView?, cityView: CityView? = null): Stats {
        val city = cityView?.getCity() ?: tile.getCity()
        return tile.stats.getTileStats(city, viewingCiv?.getCiv())
    }
    @Readonly fun providesResources(viewingCiv: CivView): Boolean = tile.providesResources(viewingCiv.getCiv())

    @Readonly fun getTileMap(): TileMapView = tileMapView

    override fun equals(other: Any?) = other is TileView && other.tile === tile
    override fun hashCode() = tile.hashCode()

    companion object {
        /** For icon/preview rendering of a single tile that has no backing [TileMap]. */
        fun forSingleTile(tile: Tile): TileView {
            val tileMap = TileMap(1).also { it.tileList.add(tile) }
            return TileMapView(tileMap, null, false).getTile(tile)
        }
    }
}
