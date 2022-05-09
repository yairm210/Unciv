package com.unciv.logic.battle

import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.logic.automation.SpecificUnitAutomation  // for Kdoc
import com.unciv.logic.civilization.CivilizationInfo


object GreatGeneralImplementation {
    private data class GeneralBonusData(val general: MapUnit, val radius: Int, val filter: String, val bonus: Int) {
        constructor(general: MapUnit, unique: Unique) : this(
            general,
            radius = unique.params[2].toIntOrNull() ?: 0,
            filter = unique.params[1],
            bonus = unique.params[0].toIntOrNull() ?: 0
        )
        @Deprecated("Remove with UniqueType.BonusForUnitsInRadius")
        constructor(general: MapUnit) : this(general, 2, "All", 15)
    }

    /**
     * Determine the "Great General" bonus for [unit] by searching for units carrying the [UniqueType.GreatGeneralAura] in the vicinity.
     *
     * Used by [BattleDamage.getGeneralModifiers].
     *
     * @return Percentage bonus as Int (typically 15), or 0 if no applicable Great General equivalents found 
     */
    fun getGreatGeneralBonus(unit: MapUnit): Int {
        val civInfo = unit.civInfo
        val nearbyGenerals = unit.getTile()
            .getTilesInDistance(civInfo.maxGeneralBonusRadius)
            .flatMap { it.getUnits() }
            .filter { it.civInfo == civInfo && it.hasGreatGeneralUnique }
        if (nearbyGenerals.none()) return 0

        val greatGeneralModifier = nearbyGenerals
            .flatMap { general ->
                general.getMatchingUniques(UniqueType.GreatGeneralAura)
                    .map { GeneralBonusData(general, it) } +
                        general.getMatchingUniques(UniqueType.BonusForUnitsInRadius)
                            .map { GeneralBonusData(general) }
            }.filter {
                // Support the border case when a mod unit has several
                // GreatGeneralAura uniques (e.g. +50% as radius 1, +25% at radius 2, +5% at radius 3)
                it.general.currentTile.aerialDistanceTo(unit.getTile()) <= it.radius
                        && (it.filter == "All" || unit.matchesFilter(it.filter))
            }.maxOfOrNull { it.bonus } ?: 0
        if (unit.hasUnique(UniqueType.GreatGeneralProvidesDoubleCombatBonus, checkCivInfoUniques = true))
            return greatGeneralModifier * 2
        return greatGeneralModifier
    }

    /**
     * Find a tile for accompanying a military unit where the total bonus for all affected units is maximized.
     *
     * Used by [SpecificUnitAutomation.automateGreatGeneral].
     */
    fun getBestAffectedTroopsTile(general: MapUnit): TileInfo? {
        // Normally we have only one Unique here. But a mix is not forbidden, so let's try to support mad modders.
        // (imagine several GreatGeneralAura uniques - +50% at radius 1, +25% at radius 2, +5% at radius 3 - possibly learnable from promotions via buildings or natural wonders?)

        // Map out the uniques sorted by bonus, as later only the best bonus will apply.
        val generalBonusData = (
                general.getMatchingUniques(UniqueType.GreatGeneralAura).map { GeneralBonusData(general, it) } +
                general.getMatchingUniques(UniqueType.BonusForUnitsInRadius).map { GeneralBonusData(general) }
            ).sortedWith(compareByDescending<GeneralBonusData> { it.bonus }.thenBy { it.radius })
            .toList()

        // Get candidate units to 'follow', coarsely.
        // The mapUnitFilter of the unique won't apply here but in the ranking of the "Aura" effectiveness.
        val unitMaxMovement = general.getMaxMovement()
        val militaryUnitTilesInDistance = general.movement.getDistanceToTiles().asSequence()
            .map { it.key }
            .filter { tile ->
                val militaryUnit = tile.militaryUnit
                militaryUnit != null && militaryUnit.civInfo == general.civInfo
                        && (tile.civilianUnit == null || tile.civilianUnit == general)
                        && militaryUnit.getMaxMovement() <= unitMaxMovement
                        && !tile.isCityCenter()
            }

        // rank tiles and find best 
        val unitBonusRadius = generalBonusData.maxOfOrNull { it.radius }
            ?: return null
        return militaryUnitTilesInDistance
            .maxByOrNull { unitTile ->
                unitTile.getTilesInDistance(unitBonusRadius).sumOf { auraTile ->
                    val militaryUnit = auraTile.militaryUnit
                    if (militaryUnit == null || militaryUnit.civInfo != general.civInfo) 0
                    else generalBonusData.firstOrNull {
                        auraTile.aerialDistanceTo(unitTile) <= it.radius
                                && (it.filter == "All" || militaryUnit.matchesFilter(it.filter))
                    }?.bonus ?: 0
                }
            }
    }

    /** Find and cache the maximum Great General _Radius_ for a [civInfo]'s _BaseUnits_ */
    fun setMaxGeneralBonusRadiusBase(civInfo: CivilizationInfo) {
        civInfo.maxGeneralBonusRadiusBase = civInfo.gameInfo.ruleSet.units.values.asSequence()
            .filter { it.isGreatGeneral() &&
                    (it.uniqueTo == civInfo.civName || it.uniqueTo == null && civInfo.getEquivalentUnit(it) == it)
            }.flatMap {
                it.getMatchingUniques(UniqueType.GreatGeneralAura) +
                        it.getMatchingUniques(UniqueType.BonusForUnitsInRadius)
            }.maxOfOrNull {
                if (it.type == UniqueType.BonusForUnitsInRadius) 2
                else it.params[2].toIntOrNull() ?: 0
            } ?: 0
        updateMaxGeneralBonusRadius(civInfo)
    }

    /** Update [CivilizationInfo.maxGeneralBonusRadius] from [CivilizationInfo.maxGeneralBonusRadiusBase]
     *  and all units having a promotion granting Great General abilities. Should be called whenever
     *  a unit is destroyed or otherwise removed from the civ, or a unit gains a promotion. */
    fun updateMaxGeneralBonusRadius(civInfo: CivilizationInfo) {
        // If there are no promotions granting a Great General power, then we don't need to scan the actual MapUnits
        val promotionsGrantingGeneralBonus = civInfo.gameInfo.promotionsGrantingGeneralBonus
        if (promotionsGrantingGeneralBonus.isEmpty()) {
            civInfo.maxGeneralBonusRadius = civInfo.maxGeneralBonusRadiusBase
            return
        }

        // Need to scan the units - might be faster looping getCivUnits only once, but far less readable
        val maxPromotionGeneralBonusRadius = promotionsGrantingGeneralBonus.asSequence()
            .filter { promotion ->
                civInfo.getCivUnits().any { unit->
                    promotion.name in unit.promotions.promotions
                }
            }.flatMap {
                it.getMatchingUniques(UniqueType.GreatGeneralAura)
            }.maxOfOrNull {
                it.params[2].toIntOrNull() ?: 0
            } ?: 0
        civInfo.maxGeneralBonusRadius = civInfo.maxGeneralBonusRadiusBase
            .coerceAtLeast(maxPromotionGeneralBonusRadius)
    }
}
