package com.unciv.logic.battle

import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.logic.automation.unit.SpecificUnitAutomation  // for Kdoc


object GreatGeneralImplementation {

    private data class GeneralBonusData(val general: MapUnit, val radius: Int, val filter: String, val bonus: Int) {
        constructor(general: MapUnit, unique: Unique) : this(
            general,
            radius = unique.params[2].toIntOrNull() ?: 0,
            filter = unique.params[1],
            bonus = unique.params[0].toIntOrNull() ?: 0
        )
    }

    /**
     * Determine the "Great General" bonus for [unit] by searching for units carrying the [UniqueType.StrengthBonusInRadius] in the vicinity.
     *
     * Used by [BattleDamage.getGeneralModifiers].
     *
     * @return A pair of unit's name and bonus (percentage) as Int (typically 15), or 0 if no applicable Great General equivalents found
     */
    fun getGreatGeneralBonus(unit: MapUnit): Pair<String, Int> {
        val civInfo = unit.civInfo
        val allGenerals = civInfo.getCivUnits()
            .filter { it.hasStrengthBonusInRadiusUnique }
        if (allGenerals.none()) return Pair("", 0)

        val greatGeneral = allGenerals
            .flatMap { general ->
                general.getMatchingUniques(UniqueType.StrengthBonusInRadius)
                    .map { GeneralBonusData(general, it) }
            }.filter {
                // Support the border case when a mod unit has several
                // GreatGeneralAura uniques (e.g. +50% as radius 1, +25% at radius 2, +5% at radius 3)
                // The "Military" test is also supported deep down in unit.matchesFilter, a small
                // optimization for the most common case, as this function is only called for `MapUnitCombatant`s
                it.general.currentTile.aerialDistanceTo(unit.getTile()) <= it.radius
                        && (it.filter == "Military" || unit.matchesFilter(it.filter))
            }
        val greatGeneralModifier = greatGeneral.maxByOrNull { it.bonus } ?: return Pair("",0)

        if (unit.hasUnique(UniqueType.GreatGeneralProvidesDoubleCombatBonus, checkCivInfoUniques = true)
            && greatGeneralModifier.general.isGreatPersonOfType("War")) // apply only on "true" generals
            return Pair(greatGeneralModifier.general.name, greatGeneralModifier.bonus * 2)
        return Pair(greatGeneralModifier.general.name, greatGeneralModifier.bonus)
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
                general.getMatchingUniques(UniqueType.StrengthBonusInRadius).map { GeneralBonusData(general, it) }
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
                        // "Military" as commented above only a small optimization
                        auraTile.aerialDistanceTo(unitTile) <= it.radius
                                && (it.filter == "Military" || militaryUnit.matchesFilter(it.filter))
                    }?.bonus ?: 0
                }
            }
    }
}
