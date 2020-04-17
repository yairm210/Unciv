package com.unciv

enum class UniqueAbility(val description: String, val displayName: String) {
    GREAT_EXPANSE("Founded cities start with additional territory, Units receive a combat bonus when fighting within their own territory (+15% unit strength)", "Great Expanse"),
    INGENUITY("Receive free Great Scientist when you discover Writing, Earn Great Scientists 50% faster", "Ingenuity"),
    HELLENIC_LEAGUE("City-State Influence degrades at half and recovers at twice the normal rate", "Hellenic League"),
    ART_OF_WAR("Great general provides double combat bonus, and spawns 50% faster", "Art of War"),
    MONUMENT_BUILDERS("+20% production towards Wonder construction", "Monument Builders"),
    SUN_NEVER_SETS("+2 movement for all naval units", "Sun Never Sets"),
    ANCIEN_REGIME("+2 Culture per turn from cities before discovering Steam Power", "Ancien Régime"),
    SIBERIAN_RICHES("Strategic Resources provide +1 Production, and Horses, Iron and Uranium Resources provide double quantity", "Siberian Riches"),
    GLORY_OF_ROME("+25% Production towards any buildings that already exist in the Capital", "The Glory of Rome"),
    TRADE_CARAVANS("+1 Gold from each Trade Route, Oil resources provide double quantity", "Trade Caravans"),
    MANIFEST_DESTINY("All land military units have +1 sight, 50% discount when purchasing tiles", "Manifest Destiny"),
    BUSHIDO("Units fight as though they were at full strength even when damaged", "Bushido"),
    FUROR_TEUTONICUS("67% chance to earn 25 Gold and recruit a Barbarian unit from a conquered encampment, -25% land units maintenance.", "Furor Teutonicus"),
    BARBARY_CORSAIRS("Pay only one third the usual cost for naval unit maintenance. Melee naval units have a 1/3 chance to capture defeated naval units.", "Barbary Corsairs"),
    SCHOLARS_OF_THE_JADE_HALL("+2 Science for all specialists and Great Person tile improvements", "Scholars of the Jade Hall"),
    GREAT_WARPATH("All units move through Forest and Jungle Tiles in friendly territory as if they have roads. These tiles can be used to establish City Connections upon researching the Wheel.", "The Great Warpath"),
    ACHAEMENID_LEGACY("Golden Ages last 50% longer. During a Golden Age, units receive +1 Movement and +10% Strength", "Achaemenid Legacy"),
    WAYFINDING("Can embark and move over Coasts and Oceans immediately. +1 Sight when embarked. +10% Combat Strength bonus if within 2 tiles of a Moai.", "Wayfinding"),
    FATHER_GOVERNS_CHILDREN("Food and Culture from Friendly City-States are increased by 50%", "Father Governs Children"),
    SEVEN_CITIES_OF_GOLD("100 Gold for discovering a Natural Wonder (bonus enhanced to 500 Gold if first to discover it). Culture, Happiness and tile yields from Natural Wonders doubled.", "Seven Cities of Gold"),
    RIVER_WARLORD("Receive triple Gold from Barbarian encampments and pillaging Cities. Embarked units can defend themselves.", "River Warlord"),
    MONGOL_TERROR("Combat Strength +30% when fighting City-State units or attacking a City-State itself. All mounted units have +1 Movement.", "Mongol Terror"),
    SACRIFICIAL_CAPTIVES("Gain Culture for the empire from each enemy unit killed.", "Sacrificial Captives"),
    GREAT_ANDEAN_ROAD("Units ignore terrain costs when moving into any tile with Hills. No maintenance costs for improvements in Hills; half cost elsewhere.", "Great Andean Road"),
    VIKING_FURY("+1 Movement to all embarked units, units pay only 1 movement point to embark and disembark. Melee units pay no movement cost to pillage.", "Viking Fury"),
    LION_OF_THE_NORTH("Gain 90 Influence with a Great Person gift to a City-State,When declaring friendship, Sweden and their friend gain a +10% boost to Great Person generation", "The Lion of the North"),
    POPULATION_GROWTH("Unhappiness from number of Cities doubled, Unhappiness from number of Citizens halved.", "Population Growth")
}