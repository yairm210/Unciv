package com.unciv.logic.map.action

import com.unciv.logic.map.MapUnit

open class MapUnitAction(
        @Transient var unit: MapUnit = MapUnit()
) {
    /** return true if this action is possible in the given conditions */
    open fun isAvailable(): Boolean = true
    open fun doPreTurnAction() {}
    open fun shouldStopOnEnemyInSight(): Boolean = false
}


