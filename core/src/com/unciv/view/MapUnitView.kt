package com.unciv.view

import com.unciv.logic.map.MapPathing
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

/** View of a [MapUnit] from the perspective of [viewer] via [civView]. */
class MapUnitView(unit: MapUnit, private val civView: CivView) : ForeignMapUnitView(unit, civView.getCiv(), false, civView.gameView) {
    val due: Boolean get() = unit.due

    @Readonly fun getOtherEscortUnit(): MapUnitView? = unit.getOtherEscortUnit()?.let { MapUnitView(it, civView) }
    // All "prepare and then choose tile" logic is actually UI stuff, and should be migrated out of logic layer
    @Readonly fun isPreparingParadrop(): Boolean = unit.isPreparingParadrop()
    @Readonly fun hasMovement(): Boolean = unit.hasMovement()
    @Readonly fun hasUnique(uniqueType: UniqueType): Boolean = unit.hasUnique(uniqueType)
    @Readonly fun isIdle(): Boolean = unit.isIdle()
    // This is pure UI and should be migrated somewhere where it can be shared by both its usages
    @Readonly fun getMovementString(): String = unit.getMovementString()
    @Readonly fun isMoving(): Boolean = unit.isMoving()
    @Readonly fun isExploring(): Boolean = unit.isExploring()
    @Readonly fun isEscorting(): Boolean = unit.isEscorting()
    @Readonly fun getMovementDestination(): TileView = civView.gameView.tileMapView.getTile(unit.getMovementDestination())
    /** `true` if [unit] was removed from its tile (captured, killed) since being selected. */
    @Readonly fun hasDisappeared(): Boolean = unit !in unit.getTile().getUnits()

    @Readonly fun canReach(tileView: TileView): Boolean = unit.movement.canReach(tileView.unwrap())
    @Readonly fun getShortestPath(tileView: TileView): List<TileView> =
        unit.movement.getShortestPath(tileView.unwrap()).map { civView.gameView.tileMapView.getTile(it) }
    @Readonly fun canSwapTo(tileView: TileView): Boolean = unit.movement.canUnitSwapTo(tileView.unwrap())
    // All "prepare and then choose tile" logic is actually UI stuff, and should be migrated out of logic layer
    @Readonly fun isPreparingAirSweep(): Boolean = unit.isPreparingAirSweep()
    @Readonly fun canMoveTo(tileView: TileView): Boolean = unit.movement.canMoveTo(tileView.unwrap())
    // This reads as "logic leaking through to UI"
    @Readonly fun isUnknownTileWeShouldAssumeToBePassable(tileView: TileView): Boolean =
        unit.movement.isUnknownTileWeShouldAssumeToBePassable(tileView.unwrap())
    @Readonly fun canAttack(): Boolean = unit.canAttack()
    @Readonly fun isMilitary(): Boolean = unit.isMilitary()
    @Readonly fun isNuclearWeapon(): Boolean = unit.isNuclearWeapon()
    @Readonly fun getNukeBlastRadius(): Int = unit.getNukeBlastRadius()
    @Readonly fun cannotMove(): Boolean = unit.cache.cannotMove
    @Readonly fun isAutomatingRoadConnection(): Boolean = unit.isAutomatingRoadConnection()
    @Readonly fun rulesetHasRoadImprovement(): Boolean = unit.currentTile.ruleset.roadImprovement != null
    @Readonly fun getReachableTilesInCurrentTurn(): List<TileView> =
        unit.movement.getReachableTilesInCurrentTurn().map { civView.gameView.tileMapView.getTile(it) }.toList()
    @Readonly fun getUnitSwappableTiles(): List<TileView> =
        unit.movement.getUnitSwappableTiles().map { civView.gameView.tileMapView.getTile(it) }.toList()
    @Readonly fun getValidRoadConnectionTiles(): List<TileView> =
        unit.civ.gameInfo.tileMap.tileList.filter { MapPathing.isValidRoadPathTile(unit.civ, it) }
            .map { civView.gameView.tileMapView.getTile(it) }
    @Readonly fun getTilesInAttackRange(): List<TileView> =
        unit.getTile().getTilesInDistanceRange(IntRange(1, unit.getRange())).map { civView.gameView.tileMapView.getTile(it) }.toList()
    @Readonly fun isExplored(tileView: TileView): Boolean = unit.civ.hasExplored(tileView.unwrap())
    /** `null` if the unit isn't currently pathing a road; otherwise the tiles still to come on that path. */
    @Readonly fun getFutureAutomatedRoadConnectionTiles(): List<TileView>? {
        val path = unit.automatedRoadConnectionPath ?: return null
        val currTileIndex = path.indexOf(unit.currentTile.position)
        if (currTileIndex == -1) return emptyList()
        return path.filterIndexed { index, _ -> index > currTileIndex }
            .mapNotNull { civView.gameView.tileMapView.getTile(it) }
    }

    // Actions
    fun trySwapMoveToTile(tileView: TileView, keepEscorting: Boolean = false): Boolean {
        unit.movement.swapMoveToTile(tileView.unwrap(), keepEscorting)
        return true
    }
    fun tryResetAction(): Boolean {
        unit.action = null
        return true
    }
    fun tryHeadTowards(tileView: TileView): Boolean {
        unit.movement.headTowards(tileView.unwrap())
        return true
    }
    fun trySetMoveToAction(tileView: TileView): Boolean {
        val position = tileView.position()
        unit.action = "moveTo ${position.x},${position.y}"
        return true
    }
}
