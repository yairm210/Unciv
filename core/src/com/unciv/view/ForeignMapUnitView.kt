package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import yairm210.purity.annotations.Readonly

/** Should contain information that should be knowable to us about foreign units. Superclass of [MapUnitView]. */
open class ForeignMapUnitView(internal open val unit: MapUnit, viewer: Civilization) : GameBasedView<MapUnit>(unit, viewer) {
    val name: String get() = unit.name
    val civName: String get() = unit.civ.civName
    val health: Int get() = unit.health

    // Navigation
    @Readonly fun getUnit(): MapUnit = unit
    @Readonly fun civ(): ForeignCivView = ForeignCivView(unit.civ, viewer)

    // Data retrieval
    @Readonly fun isAirUnit(): Boolean = unit.baseUnit.isAirUnit()
}
