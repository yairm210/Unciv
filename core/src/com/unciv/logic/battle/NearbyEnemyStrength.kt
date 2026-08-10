package com.unciv.logic.battle

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

/**
 * Strength malus auras carried by **enemy** units (Maori Warrior, Chile By Reason, etc.).
 *
 * Uses [StrengthAura] — same performant carrier-cache path as Great General buffs.
 */
object NearbyEnemyStrength {

    /**
     * @return Best matching malus unique affecting [combatant], or null
     */
    @Readonly
    fun getStrengthMalus(combatant: ICombatant): Unique? {
        val civ = combatant.getCivInfo()
        val combatantTile = combatant.getTile()

        val carriers = civ.gameInfo.civilizations.asSequence()
            .filter { it.isAlive() && civ.isAtWarWith(it) }
            .flatMap { it.units.getCivUnits().asSequence() }
            .filter { it.cache.hasStrengthForNearbyEnemiesUnique }

        return StrengthAura.bestAura(
            carriers = carriers,
            targetTile = combatantTile,
            aurasForCarrier = { carrier -> aurasFromCarrier(carrier) },
            matchesTarget = { _, unique ->
                when (unique.type) {
                    UniqueType.StrengthForNearbyEnemies ->
                        combatant.matchesFilter(unique.params[1]) &&
                            combatantTile.matchesFilter(unique.params[3])
                    UniqueType.StrengthForAdjacentEnemies ->
                        combatant.matchesFilter(unique.params[1]) &&
                            combatantTile.matchesFilter(unique.params[2])
                    else -> false
                }
            }
        )?.unique
    }

    @Readonly
    private fun aurasFromCarrier(carrier: MapUnit): Sequence<Pair<Unique, Int>> {
        val nearby = carrier.getMatchingUniques(UniqueType.StrengthForNearbyEnemies)
            .map { it to it.params[2].toInt() }
        val adjacent = carrier.getMatchingUniques(UniqueType.StrengthForAdjacentEnemies)
            .map { it to 1 }
        return nearby + adjacent
    }
}
