package com.unciv.models

class ModConstants {
    // Max amount of experience that can be gained from combat with barbarians
    var maxXPfromBarbarians = 30

    // Formula for city Strength:
    // Strength = baseStrength + strengthPerPop + strengthFromTiles + 
    //            ((%techs * multiplier) ^ exponent) * fullMultiplier + 
    //            (garrisonBonus * garrisonUnitStrength * garrisonUnitHealth/100) + 
    //            defensiveBuildingStrength
    // where %techs is the percentage of techs in the tech tree that are complete
    // If no techs exist in this ruleset, %techs = 0.5 (=50%)
    val cityStrengthBase = 8.0
    val cityStrengthPerPop = 0.4
    val cityStrengthFromTechsMultiplier = 5.5
    val cityStrengthFromTechsExponent = 2.8
    val cityStrengthFromTechsFullMultiplier = 1.0
    val cityStrengthFromGarrison = 0.2
 
    // Formula for Unit Supply:
    // Supply = unitSupplyBase (difficulties.json)
    //          unitSupplyPerCity * amountOfCities + (difficulties.json) 
    //          unitSupplyPerPopulation * amountOfPopulationInAllCities
    // unitSupplyBase and unitSupplyPerCity can be found in difficulties.json
    // unitSupplyBase, unitSupplyPerCity and unitSupplyPerPopulation can also be increased through uniques
    val unitSupplyPerPopulation = 0.5
    
    // The minimal distance that must be between any two cities, not counting the tiles cities are on
    // The number is the amount of tiles between two cities, not counting the tiles the cities are on.
    // e.g. "C__C", where "C" is a tile with a city and "_" is a tile without a city, has a distance of 2.
    // First constant is for cities on the same landmass, the second is for cities on different continents.
    val minimalCityDistance = 3
    val minimalCityDistanceOnDifferentContinents = 2
}