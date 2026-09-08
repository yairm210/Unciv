package com.unciv.logic.map

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import yairm210.purity.annotations.Readonly

/** Helper class for making decisions about more abstract information that may be displayed on the world map (or fair to use in AI), but which does not have any direct influence on save state, rules, or behaviour. */
class MapVisualization(val gameInfo: GameInfo, val viewingCiv: Civilization) {

    /** @return Whether a unit's past movements should be visible to the player. */
    @Readonly fun isUnitPastVisible(unit: MapUnit): Boolean {
        if (unit.civ == viewingCiv)
            return true
        val checkPositions = sequenceOf(unit.movementMemories.asSequence().map { it.position }, sequenceOf(unit.getTile().position)).flatten()
        return checkPositions.all { gameInfo.tileMap[it] in viewingCiv.viewableTiles }
                && (!unit.isInvisible(viewingCiv) || unit.getTile() in viewingCiv.viewableInvisibleUnitsTiles)
        // Past should always be visible for own units. Past should be visible for foreign units if the unit is visible and both its current tile and previous tiles are visible.
    }
}
