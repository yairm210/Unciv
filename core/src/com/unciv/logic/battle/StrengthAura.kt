package com.unciv.logic.battle

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.Unique
import yairm210.purity.annotations.Readonly

/**
 * Shared performant lookup for unit Strength auras (friendly GG buffs and enemy malus auras).
 *
 * Callers pass only units that already have the relevant cache flag set
 * (see [com.unciv.logic.map.mapunit.MapUnitCache]), so we never scan map tiles by radius.
 */
object StrengthAura {

    data class AuraEffect(val carrier: MapUnit, val unique: Unique, val amount: Int)

    /**
     * @param carriers Units that may carry the aura (pre-filtered by cache flag / civ)
     * @param targetTile Tile of the combatant receiving the aura
     * @param aurasForCarrier Yields (unique, radius) pairs for that carrier
     * @param matchesTarget Whether this unique applies to the combatant / tile
     */
    @Readonly
    fun bestAura(
        carriers: Sequence<MapUnit>,
        targetTile: Tile,
        aurasForCarrier: (MapUnit) -> Sequence<Pair<Unique, Int>>,
        matchesTarget: (MapUnit, Unique) -> Boolean,
    ): AuraEffect? {
        var best: AuraEffect? = null
        for (carrier in carriers) {
            val distance = carrier.currentTile.aerialDistanceTo(targetTile)
            for ((unique, radius) in aurasForCarrier(carrier)) {
                if (distance > radius) continue
                if (!matchesTarget(carrier, unique)) continue
                val amount = unique.params[0].toIntOrNull() ?: continue
                if (best == null || amount > best.amount)
                    best = AuraEffect(carrier, unique, amount)
            }
        }
        return best
    }
}
