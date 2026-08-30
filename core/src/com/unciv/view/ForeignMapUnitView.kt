package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unit.BaseUnit
import yairm210.purity.annotations.Readonly

/** Should contain information that should be knowable to us about foreign units. Superclass of [MapUnitView]. */
open class ForeignMapUnitView(internal open val unit: MapUnit, viewer: Civilization, spectatorMode: Boolean = false, open val gameView: GameView) : GameBasedView<MapUnit>(unit, viewer, spectatorMode) {
    val name: String get() = unit.name
    val civName: String get() = unit.civ.civName
    val health: Int get() = unit.health
    val religiousStrengthLost: Int get() = unit.religiousStrengthLost

    // Navigation
    @Readonly fun getUnit(): MapUnit = unit
    @Readonly fun civ(): ForeignCivView = ForeignCivView(unit.civ, viewer, spectatorMode)
    /** Get from a foreign view to an inner view, if [unit] belongs to [viewer]. */
    @Readonly fun tryGetMapUnitView(): MapUnitView? {
        if (unit.civ != viewer && !viewer.isSpectator()) return null
        return MapUnitView(unit, gameView.civView)
    }
    @Readonly fun getTile(): TileView = gameView.tileMapView.getTile(unit.getTile())

    // Data retrieval
    @Readonly fun isAirUnit(): Boolean = unit.baseUnit.isAirUnit()
    @Readonly fun isCivilian(): Boolean = unit.isCivilian()
    @Readonly fun displayName(): String = unit.displayName()
    @Readonly fun getBaseUnit(): BaseUnit = unit.baseUnit
    @Readonly fun getRange(): Int = unit.getRange()
    @Readonly fun getInterceptionRange(): Int = unit.getInterceptionRange()
    @Readonly fun getPromotions() = unit.promotions
    @Readonly fun getStatusMap() = unit.statusMap
    @Readonly fun getMovementMemories() = unit.movementMemories
    @Readonly fun getMostRecentMoveType() = unit.mostRecentMoveType
}
