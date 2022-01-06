package com.unciv.models

class ModConstants {
    // Max amount of experience that can be gained from combat with barbarians
    val maxXPfromBarbarians = 30

    // Formula for city Strength:
    // Strength = baseStrength * (%techs * multiplier) ^ exponent
    // If no techs exist in this ruleset, %techs = 0.5
    val cityStrengthFromTechsMultiplier = 5.5
    val cityStrengthFromTechsExponent = 2.8
    
}