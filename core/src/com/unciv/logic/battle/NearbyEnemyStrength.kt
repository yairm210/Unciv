package com.unciv.logic.battle

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

/**
 * Strength malus auras carried by **enemy** units (Maori Warrior, Chile By Reason, etc.).
 *
 * Mirrors [GreatGeneralImplementation]: iterate units that cache the aura unique instead of
 * scanning map tiles around the combatant (important for radius > 1).
 */
object NearbyEnemyStrength {

    /**
     * @return Best matching malus unique affecting [combatant], or null
     */
    @Readonly
    fun getStrengthMalus(combatant: ICombatant): Unique? {
        val civ = combatant.getCivInfo()
        val combatantTile = combatant.getTile()
        var best: Unique? = null
        var bestAmount = Int.MIN_VALUE

        for (enemyCiv in civ.gameInfo.civilizations) {
            if (!enemyCiv.isAlive() || !civ.isAtWarWith(enemyCiv)) continue
            for (carrier in enemyCiv.units.getCivUnits()) {
                if (!carrier.cache.hasStrengthForNearbyEnemiesUnique) continue
                val malus = bestMalusFromCarrier(carrier, combatant, combatantTile) ?: continue
                val amount = malus.params[0].toInt()
                if (amount > bestAmount) {
                    bestAmount = amount
                    best = malus
                }
            }
        }

        return best
    }

    @Readonly
    private fun bestMalusFromCarrier(carrier: MapUnit, combatant: ICombatant, combatantTile: Tile): Unique? {
        val distance = carrier.currentTile.aerialDistanceTo(combatantTile)
        val nearby = carrier.getMatchingUniques(UniqueType.StrengthForNearbyEnemies)
            .filter { distance <= it.params[2].toInt() }
            .filter { combatant.matchesFilter(it.params[1]) && combatantTile.matchesFilter(it.params[3]) }
        val adjacent = carrier.getMatchingUniques(UniqueType.StrengthForAdjacentEnemies)
            .filter { distance == 1 }
            .filter { combatant.matchesFilter(it.params[1]) && combatantTile.matchesFilter(it.params[2]) }
        return (nearby + adjacent).maxByOrNull { it.params[0].toInt() }
    }
}
