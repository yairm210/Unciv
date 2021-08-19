package com.unciv

object Constants {
    const val worker = "Worker"
    const val canBuildImprovements = "Can build [] improvements on tiles"
    @Deprecated("as of 3.15.5")
        const val workerUnique = "Can build improvements on tiles"
    const val workBoatsUnique = "May create improvements on water resources"
    const val settler = "Settler"
    const val settlerUnique = "Founds a new city"
    const val eraSpecificUnit = "Era Starting Unit"
    const val spreadReligionAbilityCount = "Religion Spread"
    const val removeHeresyAbilityCount = "Remove Heresy"

    const val impassable = "Impassable"
    const val ocean = "Ocean"
    const val coast = "Coast"
    const val mountain = "Mountain"
    const val hill = "Hill"
    const val plains = "Plains"
    const val lakes = "Lakes"
    const val desert = "Desert"
    const val grassland = "Grassland"
    const val tundra = "Tundra"
    const val snow = "Snow"

    const val forest = "Forest"
    const val jungle = "Jungle"

    const val marsh = "Marsh"
    const val oasis = "Oasis"
    const val atoll = "Atoll"
    const val ice = "Ice"
    const val floodPlains = "Flood plains"
    val vegetation = arrayOf(forest, jungle)
    val sea = arrayOf(ocean, coast)

    const val barringerCrater = "Barringer Crater"
    const val grandMesa = "Grand Mesa"
    const val greatBarrierReef = "Great Barrier Reef"
    const val krakatoa = "Krakatoa"
    const val mountFuji = "Mount Fuji"
    const val oldFaithful = "Old Faithful"
    const val rockOfGibraltar = "Rock of Gibraltar"
    const val cerroDePotosi = "Cerro de Potosi"
    const val elDorado = "El Dorado"
    const val fountainOfYouth = "Fountain of Youth"

    const val barbarianEncampment = "Barbarian encampment"

    const val peaceTreaty = "Peace Treaty"
    const val researchAgreement = "Research Agreement"
    const val openBorders = "Open Borders"
    const val random = "Random"

    const val fort = "Fort"
    const val citadel = "Citadel"
    const val tradingPost = "Trading post"

    const val futureTech = "Future Tech"
    // Easter egg name. Hopefully is to hopefully avoid conflicts when later players can name their own religions.
    // This religion name should never be displayed.
    const val noReligionName = "The religion of TheLegend27" 

    const val cancelImprovementOrder = "Cancel improvement order"
    const val tutorialPopupNamePrefix = "Tutorial: "

    const val OK = "OK"
    const val close = "Close"
    const val yes = "Yes"
    const val no = "No"
    const val enabled = "enabled"
    const val disabled = "disabled"

    const val barbarians = "Barbarians"
    const val spectator = "Spectator"
    const val custom = "Custom"

    const val rising = "Rising"
    const val lowering = "Lowering"
}
