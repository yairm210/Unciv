package com.unciv.logic.map

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit

/** Helper class for making decisions about more abstract information that may be displayed on the world map (or fair to use in AI), but which does not have any direct influence on save state, rules, or behaviour. */
class MapVisualization(val gameInfo: GameInfo, val viewingCiv: Civilization) {

    /** @return Whether a unit's past movements should be visible to the player. */
    fun isUnitPastVisible(unit: MapUnit): Boolean {
        if (unit.civ == viewingCiv)
            return true
        val checkPositions = sequenceOf(unit.movementMemories.asSequence().map { it.position }, sequenceOf(unit.getTile().position)).flatten()
        return checkPositions.all { gameInfo.tileMap[it] in viewingCiv.viewableTiles }
                && (!unit.isInvisible(viewingCiv) || unit.getTile() in viewingCiv.viewableInvisibleUnitsTiles)
        // Past should always be visible for own units. Past should be visible for foreign units if the unit is visible and both its current tile and previous tiles are visible.
    }

    /** @return Whether a unit's planned movements should be visible to the player. */
    fun isUnitFutureVisible(unit: MapUnit) = (viewingCiv.isSpectator() || unit.civ == viewingCiv)
    // Plans should be visible always for own units and never for foreign units.

    /** @return Whether an attack by a unit to a target should be visible to the player. */
    fun isAttackVisible(attacker: Civilization, source: HexCoord, target: HexCoord) = (attacker == viewingCiv || gameInfo.tileMap[source] in viewingCiv.viewableTiles || gameInfo.tileMap[target] in viewingCiv.viewableTiles)
    // Attacks by the player civ should always be visible, and attacks by foreign civs should be visible if either the tile they targeted or the attacker's tile are visible. E.G. Civ V shows bombers coming out of the Fog of War.
}
