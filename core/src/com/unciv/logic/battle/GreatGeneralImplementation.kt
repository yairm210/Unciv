package com.unciv.logic.battle

import com.unciv.logic.automation.unit.HeadTowardsEnemyCityAutomation.getEnemyCitiesByPriority
import com.unciv.logic.automation.unit.SpecificUnitAutomation
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly


object GreatGeneralImplementation {

    /**
     * Determine the "Great General" bonus for [ourUnitCombatant] by searching for units carrying the [UniqueType.StrengthBonusInRadius] in the vicinity.
     *
     * Uses [StrengthAura] — same performant carrier-cache path as enemy Strength malus auras.
     *
     * Used by [BattleDamage.getGeneralModifiers].
     *
     * @return A pair of unit's name and bonus (percentage) as Int (typically 15), or 0 if no applicable Great General equivalents found
     */
    @Readonly
    fun getGreatGeneralBonus(
        ourUnitCombatant: MapUnitCombatant,
        enemy: ICombatant,
        combatAction: CombatAction
    ): Pair<String, Int> {
        val unit = ourUnitCombatant.unit
        val civInfo = ourUnitCombatant.unit.civ
        val gameContext = GameContext(
            unit.civ,
            ourCombatant = ourUnitCombatant,
            theirCombatant = enemy,
            combatAction = combatAction
        )

        val carriers = civInfo.units.getCivUnits().asSequence()
            .filter { it.cache.hasStrengthBonusInRadiusUnique }

        // Support several GreatGeneralAura uniques on one unit (e.g. +50% radius 1, +25% radius 2).
        // The "Military" shortcut avoids matchesFilter for the common case.
        val best = StrengthAura.bestAura(
            carriers = carriers,
            targetTile = unit.getTile(),
            aurasForCarrier = { general ->
                general.getMatchingUniques(UniqueType.StrengthBonusInRadius, gameContext)
                    .map { it to (it.params[2].toIntOrNull() ?: 0) }
            },
            matchesTarget = { general, unique ->
                unique.params[1] == "Military" ||
                    unit.matchesFilter(unique.params[1], state = general.cache.state)
            }
        ) ?: return Pair("", 0)

        if (unit.hasUnique(UniqueType.GreatGeneralProvidesDoubleCombatBonus, checkCivInfoUniques = true)
            && best.carrier.isGreatPersonOfType("War")) // apply only on "true" generals
            return Pair(best.carrier.name, best.amount * 2)
        return Pair(best.carrier.name, best.amount)
    }

    /**
     * Find a tile for accompanying a military unit where the total bonus for all affected units is maximized.
     *
     * Used by [SpecificUnitAutomation.automateGreatGeneral].
     */
    @Readonly
    fun getBestAffectedTroopsTile(general: MapUnit): Tile? {
        // Normally we have only one Unique here. But a mix is not forbidden,
        // (imagine several GreatGeneralAura uniques - +50% at radius 1, +25% at radius 2, +5% at radius 3 - possibly learnable from promotions via buildings or natural wonders?)
        // However, rang-1 generals are difficult to use fully without getting it killed, so it's probably best placed to support 2nd and 3rd row ranged units
        // a 3 or more range generals are less sensitive to positioning, and thus probably suffice with 2-range general logic

        val  militaryUnitTilesInDistance = general.movement.getDistanceToTiles().asSequence()
            .map { it.key }
            .filter { tile ->
                val militaryUnit = tile.militaryUnit
                militaryUnit != null && militaryUnit.civ == general.civ
                    && (tile.civilianUnit == null || tile.civilianUnit == general)
            }

        val closestReachableEnemyCity = getEnemyCitiesByPriority(general)
            .firstOrNull { general.movement.canReach(it.getCenterTile()) }
        // Send generals to the same place as our units, so they don't get stuck at the wrong side of our empire. Update this when changing global unit movement

        val militaryUnitTile = militaryUnitTilesInDistance.maxByOrNull { unitTile ->
            (2 * unitTile.getTilesInDistance(2).count { it.militaryUnit?.civ == general.civ }
                - unitTile.getTilesInDistance(2).count { it.militaryUnit?.civ != general.civ }
                - if (closestReachableEnemyCity != null) 3 * unitTile.aerialDistanceTo(closestReachableEnemyCity.getCenterTile()) else 0)
            // Scoring here is found to help AI defeat former AI,
            // a more robust scoring may be necessary to avoid leaving generals en-prise,
            // and improve handling of multiple generals, e.g. use one to support bomber stacks and the other for frontline troops.
        }
        return militaryUnitTile
    }
}
