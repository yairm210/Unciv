package com.unciv.logic.battle

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.components.UnitMovementMemoryType
import yairm210.purity.annotations.Readonly

/**
 * Civ5 Heavy Charge fall-back helpers (`CanFallBackFromMelee` / `DoFallBackFromMelee`).
 */
object MeleeFallback {

    /** True if [defender] has at least one tile it could be pushed into away from [attacker]. */
    @Readonly
    fun canFallBack(defender: MapUnit, attacker: MapUnit): Boolean {
        if (defender.isEmbarked()) return false
        if (defender.cache.cannotMove) return false
        return getFallBackCandidateTiles(defender, attacker).any { canFallBackTo(defender, it) }
    }

    /** Push [defender] one tile away from [attacker]. Returns true if moved. */
    fun doFallBack(defender: MapUnit, attacker: MapUnit): Boolean {
        val toTile = getFallBackCandidateTiles(defender, attacker).firstOrNull { canFallBackTo(defender, it) }
            ?: return false
        defender.removeFromTile()
        defender.putInTile(toTile)
        defender.mostRecentMoveType = UnitMovementMemoryType.UnitWithdrew
        return true
    }

    @Readonly
    private fun canFallBackTo(defender: MapUnit, tile: Tile): Boolean {
        if (!defender.movement.canMoveTo(tile)) return false
        if (defender.baseUnit.isLandUnit && !tile.isLand) return false
        if (tile.isCityCenter() && tile.getOwner() != defender.civ) return false
        return true
    }

    /**
     * Prefer tiles opposite the attacker (higher aerial distance), matching Civ5's
     * three-hex arc away from the attack direction.
     */
    @Readonly
    private fun getFallBackCandidateTiles(defender: MapUnit, attacker: MapUnit): Sequence<Tile> {
        val fromTile = defender.getTile()
        val attackerTile = attacker.getTile()
        return fromTile.neighbors
            .filter { it != attackerTile }
            .sortedByDescending { it.aerialDistanceTo(attackerTile) }
            .asSequence()
    }
}
