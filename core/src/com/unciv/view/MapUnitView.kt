package com.unciv.view

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

/** View of a [MapUnit] from the perspective of [viewer] via [civView]. */
class MapUnitView(unit: MapUnit, private val civView: CivView) : ForeignMapUnitView(unit, civView.getCiv(), false, civView.gameView) {
    val due: Boolean get() = unit.due

    @Readonly fun getOtherEscortUnit(): MapUnitView? = unit.getOtherEscortUnit()?.let { MapUnitView(it, civView) }
    @Readonly fun isPreparingParadrop(): Boolean = unit.isPreparingParadrop()
    @Readonly fun hasMovement(): Boolean = unit.hasMovement()
    @Readonly fun hasUnique(uniqueType: UniqueType): Boolean = unit.hasUnique(uniqueType)
    @Readonly fun isIdle(): Boolean = unit.isIdle()
    @Readonly fun getMovementString(): String = unit.getMovementString()
    @Readonly fun isMoving(): Boolean = unit.isMoving()
    @Readonly fun getMovementDestination(): TileView = civView.gameView.tileMapView.getTile(unit.getMovementDestination())
    /** `true` if [unit] was removed from its tile (captured, killed) since being selected. */
    @Readonly fun hasDisappeared(): Boolean = unit !in unit.getTile().getUnits()

    @Readonly fun canReach(tileView: TileView): Boolean = unit.movement.canReach(tileView.getTile())
    @Readonly fun getShortestPath(tileView: TileView): List<TileView> =
        unit.movement.getShortestPath(tileView.getTile()).map { civView.gameView.tileMapView.getTile(it) }
    @Readonly fun canSwapTo(tileView: TileView): Boolean = unit.movement.canUnitSwapTo(tileView.getTile())
    @Readonly fun isPreparingAirSweep(): Boolean = unit.isPreparingAirSweep()
    @Readonly fun canMoveTo(tileView: TileView): Boolean = unit.movement.canMoveTo(tileView.getTile())
    @Readonly fun isUnknownTileWeShouldAssumeToBePassable(tileView: TileView): Boolean =
        unit.movement.isUnknownTileWeShouldAssumeToBePassable(tileView.getTile())
    @Readonly fun canAttack(): Boolean = unit.canAttack()

    // Actions
    fun trySwapMoveToTile(tileView: TileView, keepEscorting: Boolean = false): Boolean {
        unit.movement.swapMoveToTile(tileView.getTile(), keepEscorting)
        return true
    }
    fun tryResetAction(): Boolean {
        unit.action = null
        return true
    }
    fun tryHeadTowards(tileView: TileView): Boolean {
        unit.movement.headTowards(tileView.getTile())
        return true
    }
}
