package com.unciv.logic.automation.unit

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.getTargetableUnitActions

/**
 * Queue of actions available for a unit 
 */
class UniqueActionQueue(val unit: MapUnit) {
    val actions = ArrayDeque(getTargetableUnitActions(unit)
        .sortedWith { l, r -> r.useFrequency.compareTo(l.useFrequency) }
        .toList())
    
    fun automateRemainingUniqueActions(turnsToMove: Int = 0) = automateUniqueActionsUntilUseFrequency(-Float.MAX_VALUE, turnsToMove)
    
    fun automateUniqueActionsUntilUseFrequency(useFrequency: Float, turnsToMove: Int = 0): Boolean {
        while (unit.hasMovement() && actions.isNotEmpty() && actions.first().useFrequency > useFrequency) {
            val action = actions.removeFirst()
            // if tile specific, then search for a valid tile
            if (action.targetIsTileSpecific()) {
                // search for a target
                val target = unit.movement.bfsUntilMatchingTile(turnsToMove) { tile, _ -> action.validTarget(tile) }
                if (target == null) continue // failed to find valid target. try next action
                // found a target. Move toward it.
                unit.movement.headTowards(target)
                if (unit.movement.canUnitSwapTo(target)) {
                    unit.movement.swapMoveToTile(target)
                }
            }
            // if we can use the Action, then do so
            if (action.canUse() && action.validTarget(unit.currentTile)) {
                action.invokeAction()
            }
        }
        return !unit.hasMovement() || unit.isDestroyed
    }
}
