package com.unciv.models

import java.lang.reflect.Modifier

/** Used as a member of [ModOptions][com.unciv.models.ruleset.ModOptions] for moddable "constants" - factors in formulae and such.
 *
 *  When combining mods, this is [merge]d _per constant/field_, not as entire object like other RulesetObjects.
 *  Merging happens on a very simple basis: If a Mod comes with a non-default value, it is copied, otherwise the parent value is left intact.
 *  If several mods change the same field, the last one wins.
 *
 *  Supports equality contract to enable the Json serializer to recognize unchanged defaults.
 *
 *  Methods [merge], [equals], [hashCode] and [toString] are done through reflection!
 *  Therefore, adding a field will not need to update these methods, but all members ***must*** conform to the equality contract.
 *  (overhead is not a factor, these routines run very rarely. The alternative would be to make the entire thing a data class.)
 */
class ModConstants {
    // Max amount of experience that can be gained from combat with barbarians
    @Suppress("SpellCheckingInspection") // Pfrom is not a word ;)
    var maxXPfromBarbarians = 30

    // Formula for city Strength:
    // Strength = baseStrength + strengthPerPop + strengthFromTiles +
    //            ((%techs * multiplier) ^ exponent) * fullMultiplier +
    //            (garrisonBonus * garrisonUnitStrength * garrisonUnitHealth/100) +
    //            defensiveBuildingStrength
    // where %techs is the percentage of techs in the tech tree that are complete
    // If no techs exist in this ruleset, %techs = 0.5 (=50%)
    var cityStrengthBase = 8.0
    var cityStrengthPerPop = 0.4
    var cityStrengthFromTechsMultiplier = 5.5
    var cityStrengthFromTechsExponent = 2.8
    var cityStrengthFromTechsFullMultiplier = 1.0
    var cityStrengthFromGarrison = 0.2

    // Formula for Unit Supply:
    // Supply = unitSupplyBase (difficulties.json)
    //          unitSupplyPerCity * amountOfCities + (difficulties.json)
    //          unitSupplyPerPopulation * amountOfPopulationInAllCities
    // unitSupplyBase and unitSupplyPerCity can be found in difficulties.json
    // unitSupplyBase, unitSupplyPerCity and unitSupplyPerPopulation can also be increased through uniques
    var unitSupplyPerPopulation = 0.5

    // The minimal distance that must be between any two cities, not counting the tiles cities are on
    // The number is the amount of tiles between two cities, not counting the tiles the cities are on.
    // e.g. "C__C", where "C" is a tile with a city and "_" is a tile without a city, has a distance of 2.
    // First constant is for cities on the same landmass, the second is for cities on different continents.
    var minimalCityDistance = 3
    var minimalCityDistanceOnDifferentContinents = 2

    var baseCityBombardRange = 2
    var cityWorkRange = 3
    var cityExpandRange = 5

    // Modifies how much the gold value of a one-sided trade is applied to the gifts diplomatic modifier.
    // Eg: One side offers a city, resource or gold for nothing in return.
    var goldGiftMultiplier = 1f
    // Modifies how much the gold value of a regular trade is applied to the gifts diplomatic modifier.
    var goldGiftTradeMultiplier = .8f
    // Modifies how quickly the GaveUsGifts dimplomacy modifier runs out. A higher value makes it run out quicker.
    // Normally the gifts reduced by ~2.5% per turn depending on the diplomatic relations with the default value.
    var goldGiftDegradationMultiplier = 1f

    // Constants used to calculate Unit Upgrade gold Cost (can only be modded all-or-nothing)
    // This is a data class for one reason only: The equality implementation enables Gdx Json to omit it when default (otherwise only the individual fields are omitted)
    data class UnitUpgradeCost(
        val base: Float = 10f,
        val perProduction: Float = 2f,
        val eraMultiplier: Float = 0f,  // 0.3 in Civ5 cpp sources but 0 in xml
        val exponent: Float = 1f,
        val roundTo: Int = 5
    )
    var unitUpgradeCost = UnitUpgradeCost()

    // NaturalWonderGenerator uses these to determine the number of Natural Wonders to spawn for a given map size.
    // With these values, radius * mul + add gives a 1-2-3-4-5 progression for Unciv predefined map sizes and a 2-3-4-5-6-7 progression for the original Civ5 map sizes.
    // 0.124 = (Civ5.Huge.getHexagonalRadiusForArea(w*h) - Civ5.Duel.getHexagonalRadiusForArea(w*h)) / 5 (if you do not round in the radius function)
    // The other constant is empiric to avoid an ugly jump in the progression.
    var naturalWonderCountMultiplier = 0.124f
    var naturalWonderCountAddedConstant = 0.1f

    // MapGenerator.spreadAncientRuins: number of ruins = suitable tile count * this
    var ancientRuinCountMultiplier = 0.02f
    // MapGenerator.spawnIce: spawn Ice where T < this, with T calculated from temperatureintensity, latitude and perlin noise.
    var spawnIceBelowTemperature = -0.8f
    // MapGenerator.spawnLakesAndCoasts: Water bodies up to this tile count become Lakes
    var maxLakeSize = 10
    // RiverGenerator: river frequency and length bounds
    var riverCountMultiplier = 0.01f
    var minRiverLength = 5
    var maxRiverLength = 666  // Do not set to less than the maximal map radius

    // Factors in formula for Maximum Number of foundable Religions
    var religionLimitBase = 1
    var religionLimitMultiplier = 0.5f

    // Factors in formula for pantheon cost
    var pantheonBase = 10
    var pantheonGrowth = 5

    var workboatAutomationSearchMaxTiles = 20

    // Civilization
    var minimumWarDuration = 10
    var baseTurnsUntilRevolt = 4
    var cityStateElectionTurns = 15

    // Espionage
    var maxSpyRank = 3
    // How much of a skill bonus each rank gives.
    // Rank 0 is 100%, rank 1 is 130%, and so on for stealing technology.
    // Half as much for a coup.
    var spyRankSkillPercentBonus = 30

    // UI: If set >= 0, ImprovementPicker will silently skip improvements whose tech requirement is more advanced than your current Era + this
    var maxImprovementTechErasForward = -1

    fun merge(other: ModConstants) {
        for (field in this::class.java.declaredFields) {
            if (field.modifiers and Modifier.STATIC != 0) continue
            val value = field.get(other)
            if (field.get(defaults).equals(value)) continue
            field.set(this, value)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModConstants) return false
        return equalsReflected(other)
    }

    override fun hashCode(): Int {
        // Note: This is of course heavily dependent on iteration order.
        // Java reflection uses declaration order, but its doc claims "The elements in the returned array are not sorted and are not in any particular order."
        // kotlin reflection is alphabetically sorted. Embarrassingly, the best documentation guarantee seems to be: https://youtrack.jetbrains.com/issue/KT-41042
        // But - let's rely on at least the order being deterministic over instances of the same class in both reflection engines, so which we use is moot.
        // A kotlin version of this (different result!): `this::class.declaredMemberProperties.fold(0) { a, b -> a * 31 + b.getter.call(this).hashCode() }`
        var result = 0
        for (field in this::class.java.declaredFields) {
            if (field.modifiers and Modifier.STATIC != 0) continue
            result = result * 31 + field.get(this).hashCode()
        }
        return result
    }

    private fun equalsReflected(other: ModConstants): Boolean {
        for (field in this::class.java.declaredFields) {
            if (field.modifiers and Modifier.STATIC != 0) continue
            if (!field.get(this).equals(field.get(other))) return false
        }
        return true
    }

    /** Debug only so far */
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append('{')
        for (field in this::class.java.declaredFields) {
            if (field.modifiers and Modifier.STATIC != 0) continue
            if (field.get(this).equals(field.get(defaults))) continue
            sb.append(field.name)
            sb.append(':')
            sb.append(field.get(this))
            sb.append(',')
        }
        sb.deleteCharAt(sb.length - 1) // remove extra ',' with StringBuilder method
        sb.append('}')
        return sb.toString().takeUnless { it == "}" } ?: "defaults"
    }

    companion object {
        /** As merge will need a default instance repeatedly, store it as static */
        // Note Json will not use this but get a fresh instance every time. A fix, if possible, could get messy.
        val defaults = ModConstants()
    }
}
