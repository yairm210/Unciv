package com.unciv.logic.battle

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

/**
 * Performant lookup for Strength uniques radiated via
 * [UniqueType.AffectingUnitsWithinTiles] / [UniqueType.AffectingEnemyUnitsWithinTiles]
 * (and deprecated dedicated Strength-aura UniqueTypes during the soak window).
 *
 * Callers pass only units that already have the relevant [com.unciv.logic.map.mapunit.MapUnitCache] flag,
 * so we never discover neighbors by scanning map tiles.
 */
object StrengthAura {

    data class AuraEffect(val carrier: MapUnit, val unique: Unique, val amount: Int)

    /**
     * @param carriers Units that may carry the aura (pre-filtered by cache flag / civ / war)
     * @param targetTile Tile of the combatant receiving the aura
     * @param targetUnit Unit receiving the aura (for mapUnitFilter matching)
     * @param aurasForCarrier Yields (unique, radius, mapUnitFilter) for that carrier
     */
    @Readonly
    fun bestAura(
        carriers: Sequence<MapUnit>,
        targetTile: Tile,
        targetUnit: MapUnit,
        aurasForCarrier: (MapUnit) -> Sequence<Triple<Unique, Int, String>>,
    ): AuraEffect? {
        var best: AuraEffect? = null
        for (carrier in carriers) {
            val distance = carrier.currentTile.aerialDistanceTo(targetTile)
            for ((unique, radius, unitFilter) in aurasForCarrier(carrier)) {
                if (distance > radius) continue
                if (unitFilter != "Military" && !targetUnit.matchesFilter(unitFilter, state = carrier.cache.state))
                    continue
                val amount = unique.params[0].toIntOrNull() ?: continue
                if (best == null || amount > best.amount)
                    best = AuraEffect(carrier, unique, amount)
            }
        }
        return best
    }

    /** Strength uniques on [carrier] that radiate to same-civ units (GG-style). */
    @Readonly
    fun friendlyAurasFrom(carrier: MapUnit, gameContext: GameContext): Sequence<Triple<Unique, Int, String>> {
        val fromModifier = carrier.getMatchingUniques(UniqueType.Strength, gameContext)
            .mapNotNull { unique ->
                val modifier = unique.getModifiers(UniqueType.AffectingUnitsWithinTiles).firstOrNull()
                    ?: return@mapNotNull null
                Triple(unique, modifier.params[1].toIntOrNull() ?: return@mapNotNull null, modifier.params[0])
            }
        val deprecated = carrier.getMatchingUniques(UniqueType.StrengthBonusInRadius, gameContext)
            .map { Triple(it, it.params[2].toIntOrNull() ?: 0, it.params[1]) }
        return fromModifier + deprecated
    }

    /** Strength uniques on [carrier] that radiate to enemy units (Haka / Chile-style). */
    @Readonly
    fun enemyAurasFrom(carrier: MapUnit, gameContext: GameContext): Sequence<Triple<Unique, Int, String>> {
        val fromModifier = carrier.getMatchingUniques(UniqueType.Strength, GameContext.IgnoreConditionals)
            .mapNotNull { unique ->
                val modifier = unique.getModifiers(UniqueType.AffectingEnemyUnitsWithinTiles).firstOrNull()
                    ?: return@mapNotNull null
                if (!unique.conditionalsApply(gameContext)) return@mapNotNull null
                Triple(unique, modifier.params[1].toIntOrNull() ?: return@mapNotNull null, modifier.params[0])
            }
        val adjacent = carrier.getMatchingUniques(UniqueType.StrengthForAdjacentEnemies, gameContext)
            .map { Triple(it, 1, it.params[1]) }
        return fromModifier + adjacent
    }
}