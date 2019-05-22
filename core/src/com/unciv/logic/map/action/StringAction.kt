package com.unciv.logic.map.action

import com.unciv.logic.map.MapUnit

/**
 * this class represents all actions that are identified by string only.
 * this is the traditional way of handling actions in UnCiv: by coding relevant information
 * into a string. This class is here to maintain compatibility to that method, preventing from a huge
 * refactoring going on here.
 */
class StringAction(
        unit: MapUnit = MapUnit(),
        val action: String = "" // traditional string-encoded action like "moveTo x,y"
): MapUnitAction(unit) {

    override fun shouldStopOnEnemyInSight(): Boolean = action.startsWith("moveTo")

}