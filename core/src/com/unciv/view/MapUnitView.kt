package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import yairm210.purity.annotations.Readonly

/** View of a [MapUnit] from the perspective of [viewer]. */
class MapUnitView(unit: MapUnit, private val civView: CivView) : ForeignMapUnitView(unit, civView.getViewer()) {
    constructor(unit: MapUnit, viewer: Civilization) : this(unit, CivView(viewer, viewer))

    @Readonly fun getViewer(): Civilization = viewer
}
