package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import yairm210.purity.annotations.Readonly

/** View of a [MapUnit] from the perspective of [viewer]. */
class MapUnitView(private val unit: MapUnit, private val viewer: Civilization) {
    @Readonly fun getUnit(): MapUnit = unit
    @Readonly fun getViewer(): Civilization = viewer
}
