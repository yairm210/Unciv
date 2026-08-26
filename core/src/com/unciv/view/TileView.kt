package com.unciv.view

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.stats.Stats
import yairm210.purity.annotations.Readonly

/** View of a [Tile] from the perspective of [viewer] via [tileMapView]. */
class TileView internal constructor(private val tile: Tile, val tileMapView: TileMapView,
               viewer: Civilization?,
               spectatorMode: Boolean = false) : View<Tile>(tile, viewer, spectatorMode) {

    // Navigation
    @Readonly fun getTile(): Tile = tile
    @Readonly fun getCivView(): CivView? = tileMapView.gameView?.civView
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
        return unit.isVisibleTo(viewer)
    }
    @Readonly private fun toForeignMapUnitView(unit: MapUnit): ForeignMapUnitView =
        tileMapView.gameView!!.getForeignMapUnitView(unit)
    val civilianUnit: ForeignMapUnitView?
        get() {
            val unit = tile.civilianUnit ?: return null
            if (!isVisible(unit)) return null
            return toForeignMapUnitView(unit)
        }
    val militaryUnit: ForeignMapUnitView?
        get() {
            val unit = tile.militaryUnit ?: return null
            if (!isVisible(unit)) return null
            return toForeignMapUnitView(unit)
        }
    @Readonly fun getVisibleUnits(): List<ForeignMapUnitView> {
        if (viewer == null) return emptyList()
        return tile.getUnits()
            .filter { isVisible(it) }
            .map { toForeignMapUnitView(it) }
            .toList()
    }

    // Data retrieval
    @Readonly fun position() = tile.position
    /** Ideally this function should not exist - you should never be able to get a tileview of an unexplored tile
     * However, currently the way the map works is we set up a tilegroup for all players and use the tileview for that tile
     * That means that *in order to allow clicking on an unexplored tile* we currently need to accept tileviews of unexplored tiles
     * */
    @Readonly fun isExplored() = viewer == null || tile.isExplored(viewer)
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
    @Readonly fun aerialDistanceTo(other: TileView): Int = tile.aerialDistanceTo(other.unwrap())
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
    val improvement: String? get() = tile.improvement
    val tileImprovement: TileImprovement? get() = tile.tileImprovement
    val turnsToImprovement: Int get() = tile.turnsToImprovement
    @Readonly fun isMarkedForCreatesOneImprovement(): Boolean = tile.isMarkedForCreatesOneImprovement()

    val isLand: Boolean get() = tile.isLand
    val hasBottomRightRiver: Boolean get() = tile.hasBottomRightRiver
    val hasBottomRiver: Boolean get() = tile.hasBottomRiver
    val hasBottomLeftRiver: Boolean get() = tile.hasBottomLeftRiver
    @Readonly fun isPillaged(): Boolean = tile.isPillaged()
    @Readonly fun getBaseTerrain(): Terrain = tile.getBaseTerrain()
    @Readonly fun getRuleset(): Ruleset = tile.ruleset

    @Readonly fun getTileStats(viewingCiv: CivView?, cityView: CityView? = null): Stats {
        val city = cityView?.unwrap() ?: tile.getCity()
        return tile.stats.getTileStats(city, viewingCiv?.unwrap())
    }
    @Readonly fun providesResources(viewingCiv: CivView): Boolean = tile.providesResources(viewingCiv.unwrap())

    @Readonly fun getTileMap(): TileMapView = tileMapView

    companion object {
        /** For icon/preview rendering of a single tile that has no backing [TileMap]. */
        fun forSingleTile(tile: Tile): TileView {
            val tileMap = TileMap(1).also { it.tileList.add(tile) }
            return TileMapView(tileMap, null, false).getTile(tile)
        }
    }
}
