package com.unciv

object Constants {
    const val worker = "Worker"
    const val settler = "Settler"
    const val greatGeneral = "Great General"

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
    const val ancientRuins = "Ancient ruins"

    const val peaceTreaty = "Peace Treaty"
    const val researchAgreement = "Research Agreement"
    const val openBorders = "Open Borders"
    const val random = "Random"
    val greatImprovements = listOf("Academy", "Landmark", "Manufactory", "Customs house", "Citadel")

    const val unitActionSetUp = "Set Up"
    const val unitActionSleep = "Sleep"
    const val unitActionSleepUntilHealed = "Sleep until healed"
    const val unitActionAutomation = "Automate"
    const val unitActionExplore = "Explore"
    const val futureTech = "Future Tech"

    const val cancelImprovementOrder = "Cancel improvement order"
    const val tutorialPopupNamePrefix = "Tutorial: "

    const val asciiEscape = '\u001B'        // Character Escape (not to be confused with Input.Keys.ESCAPE, a virtual key code)
    const val asciiDelete = '\u007F'        // Character Delete
}