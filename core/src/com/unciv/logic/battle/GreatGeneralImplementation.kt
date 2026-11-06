package com.unciv.logic.battle

import com.unciv.logic.map.mapunit.MapUnit
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
                    .map {
                        GeneralBonusData(general, it, if (general.isGreatPersonOfType("War")) "Great General" else general.name)
                    }
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
}
