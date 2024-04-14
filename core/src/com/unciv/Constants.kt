package com.unciv

object Constants {
    const val settler = "Settler"
    const val eraSpecificUnit = "Era Starting Unit"
    val all = setOf("All", "all")

    const val english = "English"

    const val impassable = "Impassable"
    const val ocean = "Ocean"

    /** The "Coast" _terrain_ */
    const val coast = "Coast"
    /** The "Coastal" terrain _filter_ */
    const val coastal = "Coastal"

    /** Used as filter and the name of the pseudo-TerrainFeature defining river Stats */
    const val river = "River"

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
    const val ice = "Ice"
    val vegetation = arrayOf(forest, jungle)

    // Note the difference in case. **Not** interchangeable!
    // TODO this is very opaque behaviour to modders
    /** The "Fresh water" terrain _unique_ */
    const val freshWater = "Fresh water"
    /** The "Fresh Water" terrain _filter_ */
    const val freshWaterFilter = "Fresh Water"

    const val barbarianEncampment = "Barbarian encampment"
    const val cityCenter = "City center"

    const val peaceTreaty = "Peace Treaty"
    const val peaceNegotiation = "Peace Negotiation"
    const val researchAgreement = "Research Agreement"
    const val openBorders = "Open Borders"
    const val defensivePact = "Defensive Pact"
    /** Used as origin in StatMap or ResourceSupplyList, or the toggle button in DiplomacyOverviewTab */
    const val cityStates = "City-States"
    /** Used as origin in ResourceSupplyList */
    const val tradable = "Tradable"

    const val random = "Random"
    const val unknownNationName = "???"
    const val unknownCityName = "???"

    const val fort = "Fort"

    const val futureTech = "Future Tech"
    // Easter egg name. Is to avoid conflicts when players name their own religions.
    // This religion name should never be displayed.
    const val noReligionName = "The religion of TheLegend27"
    const val spyHideout = "Spy Hideout"

    const val neutralVictoryType = "Neutral"

    const val cancelImprovementOrder = "Cancel improvement order"
    const val tutorialPopupNamePrefix = "Tutorial: "

    const val OK = "OK"
    const val close = "Close"
    const val cancel = "Cancel"
    const val yes = "Yes"
    const val no = "No"
    const val loading = "Loading..."
    const val working = "Working..."

    const val barbarians = "Barbarians"
    const val spectator = "Spectator"

    const val embarked = "Embarked"
    const val wounded = "Wounded"

    const val remove = "Remove "
    const val repair = "Repair"

    const val uniqueOrDelimiter = "\" OR \""

    const val dropboxMultiplayerServer = "Dropbox"
    const val uncivXyzServer = "https://uncivserver.xyz"

    const val defaultTileset = "HexaRealm"
    /** Default for TileSetConfig.fallbackTileSet - Don't change unless you've also moved the crosshatch, borders, and arrows as well */
    const val defaultFallbackTileset = "FantasyHex"
    const val defaultUnitset = "AbsoluteUnits"
    const val defaultSkin = "Minimal"

    /**
     * Use this to determine whether a [MapUnit][com.unciv.logic.map.MapUnit]'s movement is exhausted
     * (currentMovement <= this) if and only if a fuzzy comparison is needed to account for Float rounding errors.
     * _Most_ checks do compare to 0!
     */
    const val minimumMovementEpsilon = 0.05f  // 0.1f was used previously, too - here for global searches
    const val aiPreferInquisitorOverMissionaryPressureDifference = 3000f

    const val defaultFontSize = 18
    const val headingFontSize = 24

    /** URL to the root of the Unciv repository, including trailing slash */
    // Note: Should the project move, this covers external links, but not comments e.g. mentioning issues
    const val uncivRepoURL = "https://github.com/yairm210/Unciv/"
    /** URL to the wiki, including trailing slash */
    const val wikiURL = "https://yairm210.github.io/Unciv/"
}
