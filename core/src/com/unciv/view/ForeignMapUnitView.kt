package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import yairm210.purity.annotations.Readonly

/** Should contain information that should be knowable to us about foreign units. Superclass of [MapUnitView]. */
open class ForeignMapUnitView(internal open val unit: MapUnit, internal open val viewer: Civilization) {
    val name: String get() = unit.name
    val civName: String get() = unit.civ.civName
    val health: Int get() = unit.health

    @Readonly fun civ(): ForeignCivView = ForeignCivView(unit.civ, viewer)
    @Readonly fun getUnit(): MapUnit = unit
}
