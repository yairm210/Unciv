package com.unciv.view

import com.unciv.logic.map.mapunit.MapUnit

/** View of a [MapUnit] from the perspective of [viewer] via [civView]. */
class MapUnitView(unit: MapUnit, private val civView: CivView) : ForeignMapUnitView(unit, civView.getCiv())
