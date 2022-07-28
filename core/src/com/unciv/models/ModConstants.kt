package com.unciv.models

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

    // Constants used to calculate Unit Upgrade gold Cost (can only be modded all-or-nothing)
    class UnitUpgradeCost {
        val base = 10f
        val perProduction = 2f
        val eraMultiplier = 0f  // 0.3 in Civ5 cpp sources but 0 in xml
        val exponent = 1f
        val roundTo = 5
    }
    var unitUpgradeCost = UnitUpgradeCost()

    // NaturalWonderGenerator uses these to determine the number of Natural Wonders to spawn for a given map size.
    // With these values, radius * mul + add gives a 1-2-3-4-5 progression for Unciv predefined map sizes and a 2-3-4-5-6-7 progression for the original Civ5 map sizes.
    // 0.124 = (Civ5.Huge.getHexagonalRadiusForArea(w*h) - Civ5.Duel.getHexagonalRadiusForArea(w*h)) / 5 (if you do not round in the radius function)
    // The other constant is empiric to avoid an ugly jump in the progression.
    var naturalWonderCountMultiplier = 0.124f
    var naturalWonderCountAddedConstant = 0.1f

    // MapGenerator.spreadAncientRuins: number of ruins = suitable tile count * this
    var ancientRuinCountMultiplier = 0.02f
    // MapGenerator.spawnIce: spawn Ice where T < this, with T calculated from temperatureExtremeness, latitude and perlin noise.
    var spawnIceBelowTemperature = -0.8f
    // MapGenerator.spawnLakesAndCoasts: Water bodies up to this tile count become Lakes
    var maxLakeSize = 10
    // RiverGenerator: river frequency and length bounds
    var riverCountMultiplier = 0.01f
    var minRiverLength = 5
    var maxRiverLength = 666  // Do not set < max map radius

    fun merge(other: ModConstants) {
        if (other.maxXPfromBarbarians != defaults.maxXPfromBarbarians) maxXPfromBarbarians = other.maxXPfromBarbarians
        if (other.cityStrengthBase != defaults.cityStrengthBase) cityStrengthBase = other.cityStrengthBase
        if (other.cityStrengthPerPop != defaults.cityStrengthPerPop) cityStrengthPerPop = other.cityStrengthPerPop
        if (other.cityStrengthFromTechsMultiplier != defaults.cityStrengthFromTechsMultiplier) cityStrengthFromTechsMultiplier = other.cityStrengthFromTechsMultiplier
        if (other.cityStrengthFromTechsExponent != defaults.cityStrengthFromTechsExponent) cityStrengthFromTechsExponent = other.cityStrengthFromTechsExponent
        if (other.cityStrengthFromTechsFullMultiplier != defaults.cityStrengthFromTechsFullMultiplier) cityStrengthFromTechsFullMultiplier = other.cityStrengthFromTechsFullMultiplier
        if (other.cityStrengthFromGarrison != defaults.cityStrengthFromGarrison) cityStrengthFromGarrison = other.cityStrengthFromGarrison
        if (other.unitSupplyPerPopulation != defaults.unitSupplyPerPopulation) unitSupplyPerPopulation = other.unitSupplyPerPopulation
        if (other.minimalCityDistance != defaults.minimalCityDistance) minimalCityDistance = other.minimalCityDistance
        if (other.minimalCityDistanceOnDifferentContinents != defaults.minimalCityDistanceOnDifferentContinents) minimalCityDistanceOnDifferentContinents = other.minimalCityDistanceOnDifferentContinents
        if (other.unitUpgradeCost != defaults.unitUpgradeCost) unitUpgradeCost = other.unitUpgradeCost
        if (other.naturalWonderCountMultiplier != defaults.naturalWonderCountMultiplier) naturalWonderCountMultiplier = other.naturalWonderCountMultiplier
        if (other.naturalWonderCountAddedConstant != defaults.naturalWonderCountAddedConstant) naturalWonderCountAddedConstant = other.naturalWonderCountAddedConstant
        if (other.ancientRuinCountMultiplier != defaults.ancientRuinCountMultiplier) ancientRuinCountMultiplier = other.ancientRuinCountMultiplier
        if (other.spawnIceBelowTemperature != defaults.spawnIceBelowTemperature) spawnIceBelowTemperature = other.spawnIceBelowTemperature
        if (other.maxLakeSize != defaults.maxLakeSize) maxLakeSize = other.maxLakeSize
        if (other.riverCountMultiplier != defaults.riverCountMultiplier) riverCountMultiplier = other.riverCountMultiplier
        if (other.minRiverLength != defaults.minRiverLength) minRiverLength = other.minRiverLength
        if (other.maxRiverLength != defaults.maxRiverLength) maxRiverLength = other.maxRiverLength
    }

    companion object {
        val defaults = ModConstants()
    }
}
