package com.unciv.logic.battle

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.components.UnitMovementMemoryType
import yairm210.purity.annotations.Readonly
import kotlin.random.Random

/**
 * Shared melee withdraw / Heavy Charge fall-back (Civ5 Slinger retreat and push).
 * Free teleport to a neighboring tile; keeps carried units; does not break escort (blocked instead).
 */
object MeleeRetreat {

    /** True if [unit] has somewhere to withdraw/fall back to, away from [fromAttacker]. */
    @Readonly
    fun canRetreat(unit: MapUnit, fromAttacker: MapUnit): Boolean =
        findRetreatTile(unit, fromAttacker, random = null) != null

    /**
     * Teleport [unit] one tile away from [fromAttacker].
     * @param random if non-null, pick randomly among preferred then secondary tiles (Slinger withdraw);
     *   if null, pick the furthest tile from the attacker (Heavy Charge).
     */
    fun doRetreat(unit: MapUnit, fromAttacker: MapUnit, random: Random? = null): Tile? {
        val toTile = findRetreatTile(unit, fromAttacker, random) ?: return null
        teleportRetainPayload(unit, toTile)
        return toTile
    }

    @Readonly
    fun findRetreatTile(unit: MapUnit, fromAttacker: MapUnit, random: Random?): Tile? {
        if (unit.isEmbarked()) return null
        if (unit.cache.cannotMove) return null
        // Same as existing withdraw: do not abandon an escort partner or a guard post
        if (unit.isEscorting()) return null
        if (unit.isGuarding()) return null

        val fromTile = unit.getTile()
        val attackerTile = fromAttacker.getTile()

        val preferred = fromTile.neighbors
            .filterNot { it == attackerTile || it in attackerTile.neighbors }
            .filter { unit.movement.canMoveTo(it) }
        val secondary = fromTile.neighbors
            .filter { it in attackerTile.neighbors }
            .filter { unit.movement.canMoveTo(it) }

        if (random != null) {
            if (preferred.any()) return preferred.toList().random(random)
            if (secondary.any()) return secondary.toList().random(random)
            return null
        }

        // Heavy Charge: prefer tiles opposite the attacker
        return (preferred + secondary)
            .sortedByDescending { it.aerialDistanceTo(attackerTile) }
            .firstOrNull()
    }

    /** Free teleport that keeps carried units, matching other UnitMovement teleports. */
    fun teleportRetainPayload(unit: MapUnit, toTile: Tile) {
        val origin = unit.getTile()
        val payloadUnits = origin.getUnits().filter { it.isTransported && unit.canTransport(it) }.toList()
        unit.removeFromTile()
        unit.putInTile(toTile)
        unit.mostRecentMoveType = UnitMovementMemoryType.UnitWithdrew
        for (payload in payloadUnits) {
            payload.removeFromTile()
            payload.putInTile(toTile)
            payload.isTransported = true
            payload.mostRecentMoveType = UnitMovementMemoryType.UnitTeleported
        }
    }
}
