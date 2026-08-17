package com.unciv.view

import com.unciv.logic.map.mapunit.MapUnit
import yairm210.purity.annotations.Readonly

/** View of a [MapUnit] from the perspective of [viewer] via [civView]. */
class MapUnitView(unit: MapUnit, private val civView: CivView) : ForeignMapUnitView(unit, civView.getCiv(), false, civView.gameView) {
    @Readonly fun getOtherEscortUnit(): MapUnitView? = unit.getOtherEscortUnit()?.let { MapUnitView(it, civView) }
}
