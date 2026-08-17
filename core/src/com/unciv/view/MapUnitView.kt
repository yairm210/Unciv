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
}
