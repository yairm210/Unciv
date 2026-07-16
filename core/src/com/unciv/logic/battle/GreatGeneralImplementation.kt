package com.unciv.logic.battle

import com.unciv.logic.automation.unit.HeadTowardsEnemyCityAutomation.getEnemyCitiesByPriority
import com.unciv.logic.automation.unit.SpecificUnitAutomation
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly


object GreatGeneralImplementation {

    private data class GeneralBonusData(val general: MapUnit, val radius: Int, val filter: String, val bonus: Int, val source: String) {
        constructor(general: MapUnit, unique: Unique, source: String) : this(
            general,
            radius = unique.params[2].toIntOrNull() ?: 0,
            filter = unique.params[1],
            bonus = unique.params[0].toIntOrNull() ?: 0,
            source
        )
    }

    /**
     * Determine the "Great General" bonuses for [ourUnitCombatant] by searching for units carrying the [UniqueType.StrengthBonusInRadius] in the vicinity.
     * Applies only the largest bonuses found attached to each baseUnit and promotion.
     * 
     * Used by [BattleDamage.getGeneralModifiers].
     *
     * @return A map of unit names and bonuses (percentage) as Int (typically 15)
     */
    @Readonly
    fun getGreatGeneralBonus(
        ourUnitCombatant: MapUnitCombatant,
        enemy: ICombatant,
        combatAction: CombatAction
    ): Map<String, Int> {
        val unit = ourUnitCombatant.unit
        val civInfo = ourUnitCombatant.unit.civ
        val allGenerals = civInfo.units.getCivUnits()
            .filter { it.cache.hasStrengthBonusInRadiusUnique}
        if (allGenerals.none()) return emptyMap()

        val greatGenerals = allGenerals
            .flatMap { general ->
                val context = GameContext(unit.civ, ourCombatant = ourUnitCombatant, theirCombatant = enemy, combatAction = combatAction)
                // Uniques from the unit type itself
                val base = general.baseUnit.getMatchingUniques(UniqueType.StrengthBonusInRadius, context)
                    .map { GeneralBonusData(general, it, general.name) }
                // Uniques from each individual promotion
                val promos = general.promotions.getPromotions().flatMap { promotion ->
                         promotion.getMatchingUniques(UniqueType.StrengthBonusInRadius, context)
                             .map { unique -> GeneralBonusData(general, unique, promotion.name)}
                }
                (base + promos)
            }
            .filter { data ->
                // Support the border case when a mod unit has several
                // GreatGeneralAura uniques (e.g. +50% as radius 1, +25% at radius 2, +5% at radius 3)
                // The "Military" test is also supported deep down in unit.matchesFilter, a small
                // optimization for the most common case, as this function is only called for `MapUnitCombatant`s
                data.general.currentTile.aerialDistanceTo(unit.getTile()) <= data.radius
                    && (data.filter == "Military" || unit.matchesFilter(data.filter, state = data.general.cache.state))
            }
            .groupBy { it.source }
            .mapValues { (_, bonusDataList) ->
                bonusDataList.maxOf { data ->
                    val multiplier = if (unit.hasUnique(UniqueType.GreatGeneralProvidesDoubleCombatBonus) && data.general.isGreatPersonOfType("War")) 2 else 1
                    data.bonus * multiplier
                }
            }

        return greatGenerals
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
