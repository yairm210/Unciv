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
    }

    companion object {
        val defaults = ModConstants()
    }
}
