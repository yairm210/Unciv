package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import yairm210.purity.annotations.Readonly

/** Should contain information that should be knowable to us about foreign units. Superclass of [MapUnitView]. */
open class ForeignMapUnitView(internal open val unit: MapUnit, viewer: Civilization, spectatorMode: Boolean = false, gameView: GameView) : GameBasedView<MapUnit>(unit, viewer, spectatorMode, gameView) {
    val name: String get() = unit.name
    val civName: String get() = unit.civ.civName
    val unitHealth: Int get() = unit.health
    val religiousStrengthLost: Int get() = unit.religiousStrengthLost
    val id: Int get() = unit.id
    val currentMovement: Float get() = unit.currentMovement
    val attacksThisTurn: Int get() = unit.attacksThisTurn

    // Navigation
    @Deprecated("Scheduled for removal - views should unwrap() instead")
    @Readonly fun getUnit(): MapUnit = unit
    @Readonly open fun civ(): ForeignCivView = gameView.getForeignCivView(unit.civ)
    /** Get from a foreign view to an inner view, if [unit] belongs to [viewer]. */
    @Readonly fun tryGetMapUnitView(): MapUnitView? {
        if (unit.civ != viewer && !viewer.isSpectator()) return null
        return gameView.getMapUnitView(unit)
    }
    @Readonly fun getTile(): TileView = gameView.tileMapView.getTile(unit.getTile())
    /** Wraps [unit] as a [MapUnitCombatantView] for battle purposes. */
    @Readonly fun asCombatant(): MapUnitCombatantView = MapUnitCombatantView(this, viewer, spectatorMode, gameView)

    // Data retrieval
    @Readonly fun isAirUnit(): Boolean = unit.baseUnit.isAirUnit()
    @Readonly fun isCivilian(): Boolean = unit.isCivilian()
    @Readonly fun canAttack(): Boolean = unit.canAttack()
    @Readonly fun isMilitary(): Boolean = unit.isMilitary()
    @Readonly fun isEmbarked(): Boolean = unit.isEmbarked()
    @Readonly fun displayName(): String = unit.displayName()
    @Readonly fun getBaseUnit(): BaseUnit = unit.baseUnit
    @Readonly fun getRange(): Int = unit.getRange()
    @Readonly fun getMaxMovement(): Int = unit.getMaxMovement()
    @Readonly fun getInterceptionRange(): Int = unit.getInterceptionRange()
    @Readonly fun getPromotions() = unit.promotions
    @Readonly fun getStatusMap() = unit.statusMap
    @Readonly fun getMovementMemories() = unit.movementMemories
    @Readonly fun getMostRecentMoveType() = unit.mostRecentMoveType
    @Readonly fun hasUnique(uniqueType: UniqueType): Boolean = unit.hasUnique(uniqueType)
    @Readonly fun hasReachedMaxXPFromBarbarians(): Boolean =
        unit.promotions.totalXpProduced() >= unit.civ.gameInfo.ruleset.modOptions.constants.maxXPfromBarbarians

    // Rendering (icons/flags) - visible regardless of whether [unit] is ours
    @Readonly fun hasMovement(): Boolean = unit.hasMovement()
    @Readonly fun isIdle(): Boolean = unit.isIdle()
    @Readonly fun isFortified(): Boolean = unit.isFortified()
    @Readonly fun isGuarding(): Boolean = unit.isGuarding()
}
