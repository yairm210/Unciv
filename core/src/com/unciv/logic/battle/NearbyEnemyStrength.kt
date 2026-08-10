package com.unciv.logic.battle

import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

/**
 * Strength malus auras carried by **enemy** units (Maori Warrior, Chile By Reason, etc.).
 *
 * Prefer `[relativeAmount]% Strength <affecting enemy [mapUnitFilter] units within [N] tiles>`.
 * Deprecated [UniqueType.StrengthForAdjacentEnemies] is still honored.
 */
object NearbyEnemyStrength {

    /**
     * @return Best matching malus unique affecting [combatant], or null
     */
    @Readonly
    fun getStrengthMalus(combatant: ICombatant): Unique? {
        if (combatant !is MapUnitCombatant) return null
        val civ = combatant.getCivInfo()
        val combatantTile = combatant.getTile()
        val targetUnit = combatant.unit
        val gameContext = GameContext(civ, ourCombatant = combatant)

        val carriers = civ.gameInfo.civilizations.asSequence()
            .filter { it.isAlive() && civ.isAtWarWith(it) }
            .flatMap { it.units.getCivUnits().asSequence() }
            .filter { it.cache.hasEnemyStrengthAuraUnique }

        return StrengthAura.bestAura(
            carriers = carriers,
            targetTile = combatantTile,
            targetUnit = targetUnit,
            aurasForCarrier = { carrier ->
                StrengthAura.enemyAurasFrom(carrier, gameContext)
                    .filter { (unique, _, _) ->
                        // Deprecated adjacent form also required a tileFilter on the combatant's tile
                        unique.type != UniqueType.StrengthForAdjacentEnemies ||
                            combatantTile.matchesFilter(unique.params[2])
                    }
            },
        )?.unique
    }
}
