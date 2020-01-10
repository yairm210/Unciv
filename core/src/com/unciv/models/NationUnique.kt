package com.unciv.models

enum class NationUnique(val value: String) {
    Ingenuity("Ingenuity"),
    HellenicLeague("Hellenic League"),
    ArtOfWar("Art of War"),
    MonumentBuilders("Monument Builders"),
    SunNeverSets("Sun Never Sets"),
    AncientRegime("Ancien Régime"),
    SiberianRiches("Siberian Riches"),
    TheGloryOfRome("The Glory of Rome"),
    TradeCaravans("Trade Caravans"),
    ManifestDestiny("Manifest Destiny"),
    Bushido("Bushido"),
    FurorTeutonicus("Furor Teutonicus"),
    BarbaryCorsairs("Barbary Corsairs"),
    ScholarsOfTheJadeHall("Scholars of the Jade Hall"),
    AchaemenidLegacy("Achaemenid Legacy"),
    Wayfinding("Wayfinding"),
    FatherGovernsChildren("Father Governs Children"),
    SevenCitiesOfGold("Seven Cities of Gold"),
    PopulationGrowth("Population Growth"),
    RiverWarlord("Receive triple Gold from Barbarian encampments and pillaging Cities. Embarked units can defend themselves."),
    TheGreatWarpath("The Great Warpath");

    companion object{
        fun findByName(name: String): NationUnique? = values().find { it.value == name }
    }

}