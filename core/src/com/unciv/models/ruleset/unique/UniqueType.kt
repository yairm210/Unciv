package com.unciv.models.ruleset.unique

import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/** inheritsFrom means that all such uniques are acceptable as well.
 * For example, all Global uniques are acceptable for Nations, Eras, etc. */
enum class UniqueTarget(val inheritsFrom: UniqueTarget? = null) {

    /** Only includes uniques that have immediate effects, caused by UniqueTriggerActivation */
    Triggerable,
    /** Buildings, units, nations, policies, religions, techs etc.
     * Basically anything caught by CivInfo.getMatchingUniques. */
    Global(Triggerable),

    // Civilization-specific
    Nation(Global),
    Era(Global),
    Speed(Global),
    Tech(Global),
    Policy(Global),
    FounderBelief(Global),
    /** These apply only to cities where the religion is the majority religion */
    FollowerBelief,

    // City-specific
    /** This is used as the base when checking buildings */
    Building(Global),
    Wonder(Building),

    // Unit-specific
    // These are a bit of a lie. There's no "Promotion only" or "UnitType only" uniques,
    //  they're all just Unit uniques in different places.
    //  So there should be no uniqueType that has a Promotion or UnitType target.
    //  Except meta-level uniques, such as 'incompatible with [promotion]', of course
    Unit,
    UnitType(Unit),
    Promotion(Unit),

    // Tile-specific
    Terrain,
    Improvement,
    Resource(Global),
    Ruins(Triggerable),

    // Other
    CityState,
    ModOptions,
    Conditional,
    ;

    fun canAcceptUniqueTarget(uniqueTarget: UniqueTarget): Boolean {
        if (this == uniqueTarget) return true
        if (inheritsFrom != null) return inheritsFrom.canAcceptUniqueTarget(uniqueTarget)
        return false
    }
}

enum class UniqueFlag {
    HiddenToUsers,
    ;
    companion object {
        val setOfHiddenToUsers = listOf(HiddenToUsers)
    }
}

enum class UniqueType(val text: String, vararg targets: UniqueTarget, val flags: List<UniqueFlag> = emptyList()) {

    //////////////////////////////////////// region GLOBAL UNIQUES ////////////////////////////////////////

    // region Stat providing uniques

    Stats("[stats]", UniqueTarget.Global, UniqueTarget.FollowerBelief, UniqueTarget.Improvement, UniqueTarget.Terrain),
    StatsPerCity("[stats] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    StatsFromSpecialist("[stats] from every specialist [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsPerPopulation("[stats] per [amount] population [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    // ToDo: Reword to `[stats] <in cities with [amount] or more population>` for consistency with other conditionals
    @Deprecated("as of 3.19.19", ReplaceWith("[stats] <in cities with at least [amount] [Population]>"))
    StatsFromXPopulation("[stats] in cities with [amount] or more population", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    StatsFromCitiesOnSpecificTiles("[stats] in cities on [terrainFilter] tiles", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsFromBuildings("[stats] from all [buildingFilter] buildings", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsFromTiles("[stats] from [tileFilter] tiles [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsFromTilesWithout("[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    // This is a doozy
    StatsFromObject("[stats] from every [tileFilter/specialist/buildingFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsFromTradeRoute("[stats] from each Trade Route", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsFromGlobalCitiesFollowingReligion("[stats] for each global city following this religion", UniqueTarget.FounderBelief),
    StatsFromGlobalFollowers("[stats] from every [amount] global followers [cityFilter]", UniqueTarget.FounderBelief),

    // Stat percentage boosts
    StatPercentBonus("[relativeAmount]% [stat]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatPercentBonusCities("[relativeAmount]% [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    // Todo: Add support for specialist next to buildingName
    StatPercentFromObject("[relativeAmount]% [stat] from every [tileFilter/buildingFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    AllStatsPercentFromObject("[relativeAmount]% Yield from every [tileFilter/buildingFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatPercentFromReligionFollowers("[relativeAmount]% [stat] from every follower, up to [relativeAmount]%", UniqueTarget.FollowerBelief),
    BonusStatsFromCityStates("[relativeAmount]% [stat] from City-States", UniqueTarget.Global),
    StatPercentFromTradeRoutes("[relativeAmount]% [stat] from Trade Routes", UniqueTarget.Global),
    @Deprecated("as of 3.19.19", ReplaceWith("[+25]% [Gold] from Trade Routes"))
    GoldBonusFromTradeRoutesDeprecated("Gold from all trade routes +25%", UniqueTarget.Global),

    NullifiesStat("Nullifies [stat] [cityFilter]", UniqueTarget.Global),
    NullifiesGrowth("Nullifies Growth [cityFilter]", UniqueTarget.Global),

    PercentProductionWonders("[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    PercentProductionBuildings("[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    PercentProductionUnits("[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    PercentProductionBuildingsInCapital("[relativeAmount]% Production towards any buildings that already exist in the Capital", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    // todo: maybe should be converted to "[+100]% Yield from every [Natural Wonder]"?
    DoubleStatsFromNaturalWonders("Tile yields from Natural Wonders doubled", UniqueTarget.Global),

    //endregion Stat providing uniques


    // region City-State related uniques

    // I don't like the fact that currently "city state bonuses" are separate from the "global bonuses",
    // todo: merge city state bonuses into global bonuses
    CityStateStatsPerTurn("Provides [stats] per turn", UniqueTarget.CityState), // Should not be Happiness!
    CityStateStatsPerCity("Provides [stats] [cityFilter] per turn", UniqueTarget.CityState),
    CityStateHappiness("Provides [amount] Happiness", UniqueTarget.CityState),
    CityStateMilitaryUnits("Provides military units every ≈[amount] turns", UniqueTarget.CityState), // No conditional support as of yet
    CityStateUniqueLuxury("Provides a unique luxury", UniqueTarget.CityState), // No conditional support as of yet
    CityStateGiftedUnitsStartWithXp("Military Units gifted from City-States start with [amount] XP", UniqueTarget.Global),
    CityStateMoreGiftedUnits("Militaristic City-States grant units [amount] times as fast when you are at war with a common nation", UniqueTarget.Global),

    CityStateGoldGiftsProvideMoreInfluence("Gifts of Gold to City-States generate [relativeAmount]% more Influence", UniqueTarget.Global),
    CityStateCanBeBoughtForGold("Can spend Gold to annex or puppet a City-State that has been your ally for [amount] turns.", UniqueTarget.Global),
    CityStateTerritoryAlwaysFriendly("City-State territory always counts as friendly territory", UniqueTarget.Global),

    CityStateCanGiftGreatPeople("Allied City-States will occasionally gift Great People", UniqueTarget.Global),  // used in Policy
    CityStateDeprecated("Will not be chosen for new games", UniqueTarget.Nation), // implemented for CS only for now
    CityStateInfluenceDegradation("[relativeAmount]% City-State Influence degradation", UniqueTarget.Global),
    CityStateRestingPoint("Resting point for Influence with City-States is increased by [amount]", UniqueTarget.Global),

    CityStateStatPercent("Allied City-States provide [stat] equal to [relativeAmount]% of what they produce for themselves", UniqueTarget.Global),
    CityStateResources("[relativeAmount]% resources gifted by City-States",  UniqueTarget.Global),
    CityStateLuxuryHappiness("[relativeAmount]% Happiness from luxury resources gifted by City-States", UniqueTarget.Global),
    CityStateInfluenceRecoversTwiceNormalRate("City-State Influence recovers at twice the normal rate", UniqueTarget.Global),
    // endregion


    /////// region Other global uniques

    FreeUnits("[amount] units cost no maintenance", UniqueTarget.Global),
    CannotBuildUnits("Cannot build [baseUnitFilter] units", UniqueTarget.Global),

    ConsumesResources("Consumes [amount] [resource]", UniqueTarget.Improvement, UniqueTarget.Building, UniqueTarget.Unit),
    ProvidesResources("Provides [amount] [resource]", UniqueTarget.Improvement, UniqueTarget.Building),

    GrowthPercentBonus("[relativeAmount]% growth [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    CarryOverFood("[relativeAmount]% Food is carried over after population increases [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    GainFreeBuildings("Gain a free [buildingName] [cityFilter]", UniqueTarget.Global),
    GreatPersonPointPercentage("[relativeAmount]% Great Person generation [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("As of 3.19.19", ReplaceWith("[relativeAmount]% Great Person generation [cityFilter]"))
    GreatPersonPointPercentageDeprecated("[relativeAmount]% great person generation [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    DisablesReligion("Starting in this era disables religion", UniqueTarget.Era),
    FreeExtraBeliefs("May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion", UniqueTarget.Global),
    FreeExtraAnyBeliefs("May choose [amount] additional belief(s) of any type when [foundingOrEnhancing] a religion", UniqueTarget.Global),
    StatsWhenAdoptingReligionSpeed("[stats] when a city adopts this religion for the first time (modified by game speed)", UniqueTarget.Global),
    StatsWhenAdoptingReligion("[stats] when a city adopts this religion for the first time", UniqueTarget.Global),
    StatsSpendingGreatPeople("[stats] whenever a Great Person is expended", UniqueTarget.Global),

    UnhappinessFromPopulationTypePercentageChange("[relativeAmount]% Unhappiness from [populationFilter] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("As of 3.19.19", ReplaceWith("[relativeAmount]% Unhappiness from [Population] [cityFilter]"))
    UnhappinessFromPopulationPercentageChange("[relativeAmount]% unhappiness from population [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("As of 3.19.19", ReplaceWith("[relativeAmount]% Unhappiness from [Specialists] [cityFilter]"))
    UnhappinessFromSpecialistsPercentageChange("[relativeAmount]% unhappiness from specialists [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    FoodConsumptionBySpecialists("[relativeAmount]% Food consumption by specialists [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    HappinessPer2Policies("Provides 1 happiness per 2 additional social policies adopted", UniqueTarget.Global),
    ExcessHappinessToGlobalStat("[relativeAmount]% of excess happiness converted to [stat]", UniqueTarget.Global),

    BorderGrowthPercentage("[relativeAmount]% Culture cost of natural border growth [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    TileCostPercentage("[relativeAmount]% Gold cost of acquiring tiles [cityFilter]", UniqueTarget.FollowerBelief, UniqueTarget.Global),
    // There is potential to merge these
    BuyUnitsIncreasingCost("May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] at an increasing price ([amount])", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsIncreasingCost("May buy [buildingFilter] buildings for [amount] [stat] [cityFilter] at an increasing price ([amount])", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyUnitsForAmountStat("May buy [baseUnitFilter] units for [amount] [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsForAmountStat("May buy [buildingFilter] buildings for [amount] [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyUnitsWithStat("May buy [baseUnitFilter] units with [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsWithStat("May buy [buildingFilter] buildings with [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    BuyUnitsByProductionCost("May buy [baseUnitFilter] units with [stat] for [amount] times their normal Production cost", UniqueTarget.FollowerBelief, UniqueTarget.Global),
    BuyBuildingsByProductionCost("May buy [buildingFilter] buildings with [stat] for [amount] times their normal Production cost", UniqueTarget.FollowerBelief, UniqueTarget.Global),


    // ToDo: Unify
    EnablesGoldProduction("Enables conversion of city production to gold", UniqueTarget.Global),
    EnablesScienceProduction("Enables conversion of city production to science", UniqueTarget.Global),

    BuyItemsDiscount("[stat] cost of purchasing items in cities [relativeAmount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsDiscount("[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyUnitsDiscount("[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    // Should be replaced with moddable improvements when roads become moddable
    RoadMovementSpeed("Improves movement speed on roads",UniqueTarget.Global),
    RoadsConnectAcrossRivers("Roads connect tiles across rivers", UniqueTarget.Global),
    RoadMaintenance("[relativeAmount]% maintenance on road & railroads", UniqueTarget.Global),
    BuildingMaintenance("[relativeAmount]% maintenance cost for buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),


    // This should probably support conditionals, e.g. <after discovering [tech]>
    MayanGainGreatPerson("Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once.", UniqueTarget.Global),
    MayanCalendarDisplay("Once The Long Count activates, the year on the world screen displays as the traditional Mayan Long Count.", UniqueTarget.Global),

    RetainHappinessFromLuxury("Retain [relativeAmount]% of the happiness from a luxury after the last copy has been traded away", UniqueTarget.Global),
    BonusHappinessFromLuxury("[amount] Happiness from each type of luxury resource", UniqueTarget.Global),
    LessPolicyCostFromCities("Each city founded increases culture cost of policies [relativeAmount]% less than normal", UniqueTarget.Global),
    LessPolicyCost("[relativeAmount]% Culture cost of adopting new Policies", UniqueTarget.Global),


    // Todo: Sign should not be part of the unique placeholder
    StrategicResourcesIncrease("Quantity of strategic resources produced by the empire +[relativeAmount]%", UniqueTarget.Global),  // used in Policy
    DoubleResourceProduced("Double quantity of [resource] produced", UniqueTarget.Global),
    // Todo: should probably be changed to "[stats] from every known Natural Wonder", and that'll give us the global unique as well
    DoubleHappinessFromNaturalWonders("Double Happiness from Natural Wonders", UniqueTarget.Global),

    EnablesConstructionOfSpaceshipParts("Enables construction of Spaceship parts", UniqueTarget.Global),
    EnemyLandUnitsSpendExtraMovement("Enemy land units must spend 1 extra movement point when inside your territory (obsolete upon Dynamite)", UniqueTarget.Global),

    ProductionToScienceConversionBonus("Production to science conversion in cities increased by 33%", UniqueTarget.Global),

    // Misc national uniques
    NotifiedOfBarbarianEncampments("Notified of new Barbarian encampments", UniqueTarget.Global),
    BorrowsCityNames("\"Borrows\" city names from other civilizations in the game", UniqueTarget.Global),
    @Deprecated("as of 4.0.3", ReplaceWith("Damage is ignored when determining unit Strength <for [All] units>"))
    UnitsFightFullStrengthWhenDamaged("Units fight as though they were at full strength even when damaged", UniqueTarget.Global),
    GoldWhenDiscoveringNaturalWonder("100 Gold for discovering a Natural Wonder (bonus enhanced to 500 Gold if first to discover it)", UniqueTarget.Global),
    UnhappinessFromCitiesDoubled("Unhappiness from number of Cities doubled", UniqueTarget.Global),
    GreatGeneralProvidesDoubleCombatBonus("Great General provides double combat bonus", UniqueTarget.Unit, UniqueTarget.Global),
    TechBoostWhenScientificBuildingsBuiltInCapital("Receive a tech boost when scientific buildings/wonders are built in capital", UniqueTarget.Global),
    MayNotGenerateGreatProphet("May not generate great prophet equivalents naturally", UniqueTarget.Global),
    @Deprecated("as of 4.0.3", ReplaceWith("When conquering an encampment, earn [25] Gold and recruit a Barbarian unit <with [67]% chance>"))
    ChanceToRecruitBarbarianFromEncampment("67% chance to earn 25 Gold and recruit a Barbarian unit from a conquered encampment", UniqueTarget.Global),
    GainFromEncampment("When conquering an encampment, earn [amount] Gold and recruit a Barbarian unit", UniqueTarget.Global),
    @Deprecated("as of 4.0.3", ReplaceWith("When defeating a [{Barbarian} {Water}] unit, earn [25] Gold and recruit it <with [50]% chance>"))
    ChanceToRecruitNavalBarbarian("50% chance of capturing defeated Barbarian naval units and earning 25 Gold", UniqueTarget.Global),
    GainFromDefeatingUnit("When defeating a [mapUnitFilter] unit, earn [amount] Gold and recruit it", UniqueTarget.Global),
    TripleGoldFromEncampmentsAndCities("Receive triple Gold from Barbarian encampments and pillaging Cities", UniqueTarget.Global),
    CitiesAreRazedXTimesFaster("Cities are razed [amount] times as fast", UniqueTarget.Global),
    GreatPersonBoostWithFriendship("When declaring friendship, both parties gain a [relativeAmount]% boost to great person generation", UniqueTarget.Global),
    NoImprovementMaintenanceInSpecificTiles("No Maintenance costs for improvements in [tileFilter] tiles", UniqueTarget.Global),
    OtherCivsCityStateRelationsDegradeFaster("Influence of all other civilizations with all city-states degrades [relativeAmount]% faster", UniqueTarget.Global),
    LandUnitsCrossTerrainAfterUnitGained("Land units may cross [terrainName] tiles after the first [baseUnitFilter] is earned", UniqueTarget.Global),
    GainInfluenceWithUnitGiftToCityState("Gain [amount] Influence with a [baseUnitFilter] gift to a City-State", UniqueTarget.Global),
    FaithCostOfGreatProphetChange("[relativeAmount]% Faith cost of generating Great Prophet equivalents", UniqueTarget.Global),
    RestingPointOfCityStatesFollowingReligionChange("Resting point for Influence with City-States following this religion [amount]", UniqueTarget.Global),
    @Deprecated("as of 4.0.3", ReplaceWith("[+amount]% Strength <within [amount2] tiles of a [tileFilter]>"))
    StrengthWithinTilesOfTile("+[amount]% Strength if within [amount2] tiles of a [tileFilter]", UniqueTarget.Global),
    StatBonusPercentFromCityStates("[relativeAmount]% [stat] from City-States", UniqueTarget.Global),

    ProvidesGoldWheneverGreatPersonExpended("Provides a sum of gold each time you spend a Great Person", UniqueTarget.Global),
    ProvidesStatsWheneverGreatPersonExpended("[stats] whenever a Great Person is expended", UniqueTarget.Global),

    // Acts as a trigger - this should be generalized somehow but the current setup does not allow this
    // It would currently mean cycling through EVERY unique type to find ones with a specific conditional...
    ReceiveFreeUnitWhenDiscoveringTech("Receive free [unit] when you discover [tech]", UniqueTarget.Global),

    EnablesOpenBorders("Enables Open Borders agreements", UniqueTarget.Global),
    // Should the 'R' in 'Research agreements' be capitalized?
    EnablesResearchAgreements("Enables Research agreements", UniqueTarget.Global),
    ScienceFromResearchAgreements("Science gained from research agreements [relativeAmount]%", UniqueTarget.Global),
    TriggersVictory("Triggers victory", UniqueTarget.Global),
    TriggersCulturalVictory("Triggers a Cultural Victory upon completion", UniqueTarget.Global),

    BetterDefensiveBuildings("[relativeAmount]% City Strength from defensive buildings", UniqueTarget.Global),
    TileImprovementTime("[relativeAmount]% tile improvement construction time", UniqueTarget.Global, UniqueTarget.Unit),
    PercentGoldFromTradeMissions("[relativeAmount]% Gold from Great Merchant trade missions", UniqueTarget.Global),
    // Todo: Lowercase the 'U' of 'Units' in this unique
    CityHealingUnits("[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    GoldenAgeLength("[relativeAmount]% Golden Age length", UniqueTarget.Global),

    StrengthForCities("[relativeAmount]% Strength for cities", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    UnitStartingExperience("New [baseUnitFilter] units start with [amount] Experience [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    UnitStartingPromotions("All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    UnitStartingActions("[baseUnitFilter] units built [cityFilter] can [action] [amount] extra times", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    // ToDo: make per unit and use unit filters?
    LandUnitEmbarkation("Enables embarkation for land units", UniqueTarget.Global),
    UnitsMayEnterOcean("Enables [mapUnitFilter] units to enter ocean tiles", UniqueTarget.Global),
    @Deprecated("as of 3.19.13", ReplaceWith("Enables [Embarked] units to enter ocean tiles <starting from the [Ancient era]>"))
    EmbarkedUnitsMayEnterOcean("Enables embarked units to enter ocean tiles", UniqueTarget.Global),
    @Deprecated("as of 3.19.9", ReplaceWith("Enables embarkation for land units <starting from the [Ancient era]>\", \"Enables [All] units to enter ocean tiles <starting from the [Ancient era]>"))
    EmbarkAndEnterOcean("Can embark and move over Coasts and Oceans immediately", UniqueTarget.Global),

    PopulationLossFromNukes("Population loss from nuclear attacks [relativeAmount]% [cityFilter]", UniqueTarget.Global),

    NaturalReligionSpreadStrength("[relativeAmount]% Natural religion spread [cityFilter]", UniqueTarget.FollowerBelief, UniqueTarget.Global),
    ReligionSpreadDistance("Religion naturally spreads to cities [amount] tiles away", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    @Deprecated("as of 3.19.8", ReplaceWith("Only available <before adopting [policy/tech/promotion]>" +
            "\" OR \"Only available <before discovering [policy/tech/promotion]>" +
            "\" OR \"Only available <for units without [policy/tech/promotion]>"))
    IncompatibleWith("Incompatible with [policy/tech/promotion]", UniqueTarget.Policy, UniqueTarget.Tech, UniqueTarget.Promotion),
    StartingTech("Starting tech", UniqueTarget.Tech),
    StartsWithTech("Starts with [tech]", UniqueTarget.Nation),
    StartsWithPolicy("Starts with [policy] adopted", UniqueTarget.Nation),
    ResearchableMultipleTimes("Can be continually researched", UniqueTarget.Global),

    BaseUnitSupply("[amount] Unit Supply", UniqueTarget.Global),
    UnitSupplyPerPop("[amount] Unit Supply per [amount] population [cityFilter]", UniqueTarget.Global),
    UnitSupplyPerCity("[amount] Unit Supply per city", UniqueTarget.Global),
    UnitsInCitiesNoMaintenance("Units in cities cost no Maintenance", UniqueTarget.Global),

    SpawnRebels("Rebel units may spawn", UniqueTarget.Global),

    //endregion

    //endregion Global uniques

    ///////////////////////////////////////// region CONSTRUCTION UNIQUES /////////////////////////////////////////


    Unbuildable("Unbuildable", UniqueTarget.Building, UniqueTarget.Unit, UniqueTarget.Improvement),
    CannotBePurchased("Cannot be purchased", UniqueTarget.Building, UniqueTarget.Unit),
    CanBePurchasedWithStat("Can be purchased with [stat] [cityFilter]", UniqueTarget.Building, UniqueTarget.Unit),
    CanBePurchasedForAmountStat("Can be purchased for [amount] [stat] [cityFilter]", UniqueTarget.Building, UniqueTarget.Unit),
    MaxNumberBuildable("Limited to [amount] per Civilization", UniqueTarget.Building, UniqueTarget.Unit),
    HiddenBeforeAmountPolicies("Hidden until [amount] social policy branches have been completed", UniqueTarget.Building, UniqueTarget.Unit),
    // Meant to be used together with conditionals, like "Only available <after adopting [policy]> <while the empire is happy>"
    OnlyAvailableWhen("Only available", UniqueTarget.Unit, UniqueTarget.Building, UniqueTarget.Improvement,
        UniqueTarget.Policy, UniqueTarget.Tech, UniqueTarget.Promotion),
    @Deprecated("as of 3.19.8", ReplaceWith("Only available <after adopting [buildingName/tech/resource/policy]>\"" +
            " OR \"Only available <with [buildingName/tech/resource/policy]>\"" +
            " OR \"Only available <if [buildingName/tech/resource/policy] is constructed>\"" +
            " OR \"Only available <after discovering [buildingName/tech/resource/policy]>"))
    NotDisplayedWithout("Not displayed as an available construction without [buildingName/tech/resource/policy]", UniqueTarget.Building, UniqueTarget.Unit),

    @Deprecated("as of 3.19.12", ReplaceWith("Only available <after adopting [buildingName/tech/era/policy]>\"" +
            " OR \"Only available <if [buildingName/tech/era/policy] is constructed>\"" +
            " OR \"Only available <starting from the [buildingName/tech/era/policy]>\"" +
            " OR \"Only available <after discovering [buildingName/tech/era/policy]>"))
    UnlockedWith("Unlocked with [buildingName/tech/era/policy]", UniqueTarget.Building, UniqueTarget.Unit),


    @Deprecated("as of 3.19.12", ReplaceWith("Only available <after adopting [buildingName/tech/era/policy]>\"" +
            " OR \"Only available <if [buildingName/tech/era/policy] is constructed>\"" +
            " OR \"Only available <starting from the [buildingName/tech/era/policy]>\"" +
            " OR \"Only available <after discovering [buildingName/tech/era/policy]>"))
    Requires("Requires [buildingName/tech/era/policy]", UniqueTarget.Building, UniqueTarget.Unit),

    ConvertFoodToProductionWhenConstructed("Excess Food converted to Production when under construction", UniqueTarget.Building, UniqueTarget.Unit),
    RequiresPopulation("Requires at least [amount] population", UniqueTarget.Building, UniqueTarget.Unit),

    TriggersAlertOnStart("Triggers a global alert upon build start", UniqueTarget.Building, UniqueTarget.Unit),
    TriggersAlertOnCompletion("Triggers a global alert upon completion", UniqueTarget.Building, UniqueTarget.Unit),
    //endregion


    ///////////////////////////////////////// region BUILDING UNIQUES /////////////////////////////////////////


    CostIncreasesPerCity("Cost increases by [amount] per owned city", UniqueTarget.Building),

    @Deprecated("as of 3.19.9", ReplaceWith("Only available <in cities without a [buildingName]>"))
    CannotBeBuiltWith("Cannot be built with [buildingName]", UniqueTarget.Building),
    @Deprecated("as of 3.19.9", ReplaceWith("Only available <in cities with a [buildingName]>"))
    RequiresAnotherBuilding("Requires a [buildingName] in this city", UniqueTarget.Building),
    RequiresBuildingInAllCities("Requires a [buildingName] in all cities", UniqueTarget.Building),
    RequiresBuildingInSomeCities("Requires a [buildingName] in at least [amount] cities", UniqueTarget.Building),
    CanOnlyBeBuiltInCertainCities("Can only be built [cityFilter]", UniqueTarget.Building),
    @Deprecated("as of 3.19.16", ReplaceWith("Can only be built [in annexed cities]"))
    CanOnlyBeBuiltInAnnexedCities("Can only be built in annexed cities", UniqueTarget.Building),

    MustHaveOwnedWithinTiles("Must have an owned [tileFilter] within [amount] tiles", UniqueTarget.Building),

    @Deprecated("as of 3.19.7", ReplaceWith("[stats] <with [resource]>"))
    StatsWithResource("[stats] with [resource]", UniqueTarget.Building),

    // Todo nuclear weapon and spaceship enabling requires a rethink.
    // This doesn't actually directly affect anything, the "Only available <if [Manhattan Project] is constructed>" of the nuclear weapons does that.
    EnablesNuclearWeapons("Enables nuclear weapon", UniqueTarget.Building),

    MustBeOn("Must be on [terrainFilter]", UniqueTarget.Building),
    MustNotBeOn("Must not be on [terrainFilter]", UniqueTarget.Building),
    MustBeNextTo("Must be next to [terrainFilter]", UniqueTarget.Building, UniqueTarget.Improvement),
    MustNotBeNextTo("Must not be next to [terrainFilter]", UniqueTarget.Building),

    Unsellable("Unsellable", UniqueTarget.Building),
    ObsoleteWith("Obsolete with [tech]", UniqueTarget.Building, UniqueTarget.Resource, UniqueTarget.Improvement),
    IndicatesCapital("Indicates the capital city", UniqueTarget.Building),
    ProvidesExtraLuxuryFromCityResources("Provides 1 extra copy of each improved luxury resource near this City", UniqueTarget.Building),

    DestroyedWhenCityCaptured("Destroyed when the city is captured", UniqueTarget.Building),
    NotDestroyedWhenCityCaptured("Never destroyed when the city is captured", UniqueTarget.Building),
    DoublesGoldFromCapturingCity("Doubles Gold given to enemy if city is captured", UniqueTarget.Building),

    RemoveAnnexUnhappiness("Remove extra unhappiness from annexed cities", UniqueTarget.Building),
    ConnectTradeRoutes("Connects trade routes over water", UniqueTarget.Building),

    CreatesOneImprovement("Creates a [improvementName] improvement on a specific tile", UniqueTarget.Building),
    //endregion


    ///////////////////////////////////////// region UNIT UNIQUES /////////////////////////////////////////

    FoundCity("Founds a new city", UniqueTarget.Unit),
    ConstructImprovementConsumingUnit("Can construct [improvementName]", UniqueTarget.Unit),
    @Deprecated("as of 4.1.7", ReplaceWith("Can construct [improvementName] <if it hasn't used other actions yet>"))
    CanConstructIfNoOtherActions("Can construct [improvementName] if it hasn't used other actions yet", UniqueTarget.Unit),
    BuildImprovements("Can build [improvementFilter/terrainFilter] improvements on tiles", UniqueTarget.Unit),
    CreateWaterImprovements("May create improvements on water resources", UniqueTarget.Unit),

    Strength("[relativeAmount]% Strength", UniqueTarget.Unit, UniqueTarget.Global),
    StrengthNearCapital("[relativeAmount]% Strength decreasing with distance from the capital", UniqueTarget.Unit, UniqueTarget.Global),
    FlankAttackBonus("[relativeAmount]% to Flank Attack bonuses", UniqueTarget.Unit, UniqueTarget.Global),
    // There's currently no conditional that would allow you strength vs city-state *cities* and that's why this isn't deprecated yet
    StrengthBonusVsCityStates("+30% Strength when fighting City-State units and cities", UniqueTarget.Global),
    StrengthForAdjacentEnemies("[relativeAmount]% Strength for enemy [combatantFilter] units in adjacent [tileFilter] tiles", UniqueTarget.Unit),
    StrengthWhenStacked("[relativeAmount]% Strength when stacked with [mapUnitFilter]", UniqueTarget.Unit),  // candidate for conditional!
    StrengthBonusInRadius("[relativeAmount]% Strength bonus for [mapUnitFilter] units within [amount] tiles", UniqueTarget.Unit),

    AdditionalAttacks("[amount] additional attacks per turn", UniqueTarget.Unit, UniqueTarget.Global),
    Movement("[amount] Movement", UniqueTarget.Unit, UniqueTarget.Global),
    Sight("[amount] Sight", UniqueTarget.Unit, UniqueTarget.Global, UniqueTarget.Terrain),
    Range("[amount] Range", UniqueTarget.Unit, UniqueTarget.Global),
    Heal("[amount] HP when healing", UniqueTarget.Unit, UniqueTarget.Global),

    SpreadReligionStrength("[relativeAmount]% Spread Religion Strength", UniqueTarget.Unit, UniqueTarget.Global),
    MayFoundReligion("May found a religion", UniqueTarget.Unit),
    MayEnhanceReligion("May enhance a religion", UniqueTarget.Unit),
    StatsWhenSpreading("When spreading religion to a city, gain [amount] times the amount of followers of other religions as [stat]", UniqueTarget.Unit, UniqueTarget.Global),

    CanOnlyAttackUnits("Can only attack [combatantFilter] units", UniqueTarget.Unit),
    CanOnlyAttackTiles("Can only attack [tileFilter] tiles", UniqueTarget.Unit),
    CannotAttack("Cannot attack", UniqueTarget.Unit),
    MustSetUp("Must set up to ranged attack", UniqueTarget.Unit),
    SelfDestructs("Self-destructs when attacking", UniqueTarget.Unit),
    BlastRadius("Blast radius [amount]", UniqueTarget.Unit),
    IndirectFire("Ranged attacks may be performed over obstacles", UniqueTarget.Unit),
    NuclearWeapon("Nuclear weapon of Strength [amount]", UniqueTarget.Unit),

    NoDefensiveTerrainBonus("No defensive terrain bonus", UniqueTarget.Unit, UniqueTarget.Global),
    NoDefensiveTerrainPenalty("No defensive terrain penalty", UniqueTarget.Unit, UniqueTarget.Global),
    NoDamagePenalty("Damage is ignored when determining unit Strength", UniqueTarget.Unit, UniqueTarget.Global),
    Uncapturable("Uncapturable", UniqueTarget.Unit),
    // Replace with "Withdraws before melee combat <with [amount]% chance>"?
    MayWithdraw("May withdraw before melee ([amount]%)", UniqueTarget.Unit),
    CannotCaptureCities("Unable to capture cities", UniqueTarget.Unit),

    NoMovementToPillage("No movement cost to pillage", UniqueTarget.Unit, UniqueTarget.Global),
    CanMoveAfterAttacking("Can move after attacking", UniqueTarget.Unit),
    TransferMovement("Transfer Movement to [unit]", UniqueTarget.Unit),
    MoveImmediatelyOnceBought("Can move immediately once bought", UniqueTarget.Unit),
    MayParadrop("May Paradrop up to [amount] tiles from inside friendly territory", UniqueTarget.Unit),

    HealsOutsideFriendlyTerritory("May heal outside of friendly territory", UniqueTarget.Unit, UniqueTarget.Global),
    HealingEffectsDoubled("All healing effects doubled", UniqueTarget.Unit, UniqueTarget.Global),
    HealsAfterKilling("Heals [amount] damage if it kills a unit", UniqueTarget.Unit, UniqueTarget.Global),
    HealOnlyByPillaging("Can only heal by pillaging", UniqueTarget.Unit, UniqueTarget.Global),
    HealsEvenAfterAction("Unit will heal every turn, even if it performs an action", UniqueTarget.Unit),
    HealAdjacentUnits("All adjacent units heal [amount] HP when healing", UniqueTarget.Unit),

    NormalVisionWhenEmbarked("Normal vision when embarked", UniqueTarget.Unit, UniqueTarget.Global),
    DefenceBonusWhenEmbarked("Defense bonus when embarked", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("as of 4.0.3", ReplaceWith("Defense bonus when embarked <for [All] units>"))
    DefenceBonusWhenEmbarkedCivwide("Embarked units can defend themselves", UniqueTarget.Global),
    @Deprecated("as of 3.19.8", ReplaceWith("Eliminates combat penalty for attacking across a coast"))
    AttackFromSea("Eliminates combat penalty for attacking from the sea", UniqueTarget.Unit),
    AttackAcrossCoast("Eliminates combat penalty for attacking across a coast", UniqueTarget.Unit),
    AttackOnSea("May attack when embarked", UniqueTarget.Unit),
    AttackAcrossRiver("Eliminates combat penalty for attacking over a river", UniqueTarget.Unit),

    NoSight("No Sight", UniqueTarget.Unit),
    CanSeeOverObstacles("Can see over obstacles", UniqueTarget.Unit),
    @Deprecated("as of 3.19.19", ReplaceWith("[+4] Sight\", \"Can see over obstacles"))
    SixTilesAlwaysVisible("6 tiles in every direction always visible", UniqueTarget.Unit),

    CarryAirUnits("Can carry [amount] [mapUnitFilter] units", UniqueTarget.Unit),
    CarryExtraAirUnits("Can carry [amount] extra [mapUnitFilter] units", UniqueTarget.Unit),
    CannotBeCarriedBy("Cannot be carried by [mapUnitFilter] units", UniqueTarget.Unit),

    ChanceInterceptAirAttacks("[relativeAmount]% chance to intercept air attacks", UniqueTarget.Unit),
    DamageFromInterceptionReduced("Damage taken from interception reduced by [relativeAmount]%", UniqueTarget.Unit),
    DamageWhenIntercepting("[relativeAmount]% Damage when intercepting", UniqueTarget.Unit),
    ExtraInterceptionsPerTurn("[amount] extra interceptions may be made per turn", UniqueTarget.Unit),
    CannotBeIntercepted("Cannot be intercepted", UniqueTarget.Unit),

    UnitMaintenanceDiscount("[relativeAmount]% maintenance costs", UniqueTarget.Unit, UniqueTarget.Global),
    UnitUpgradeCost("[relativeAmount]% Gold cost of upgrading", UniqueTarget.Unit, UniqueTarget.Global),
    GreatPersonEarnedFaster("[greatPerson] is earned [relativeAmount]% faster", UniqueTarget.Unit, UniqueTarget.Global),

    DamageUnitsPlunder("Earn [amount]% of the damage done to [combatantFilter] units as [plunderableStat]", UniqueTarget.Unit, UniqueTarget.Global),
    CaptureCityPlunder("Upon capturing a city, receive [amount] times its [stat] production as [plunderableStat] immediately", UniqueTarget.Unit, UniqueTarget.Global),
    KillUnitPlunder("Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [plunderableStat]", UniqueTarget.Unit, UniqueTarget.Global),
    KillUnitPlunderNearCity("Earn [amount]% of [mapUnitFilter] unit's [costOrStrength] as [plunderableStat] when killed within 4 tiles of a city following this religion", UniqueTarget.FollowerBelief),
    KillUnitCapture("May capture killed [mapUnitFilter] units", UniqueTarget.Unit),

    FlatXPGain("[amount] XP gained from combat", UniqueTarget.Unit, UniqueTarget.Global),
    PercentageXPGain("[relativeAmount]% XP gained from combat", UniqueTarget.Unit, UniqueTarget.Global),

    Invisible("Invisible to others", UniqueTarget.Unit),
    InvisibleToNonAdjacent("Invisible to non-adjacent units", UniqueTarget.Unit),
    CanSeeInvisibleUnits("Can see invisible [mapUnitFilter] units", UniqueTarget.Unit),

    RuinsUpgrade("May upgrade to [baseUnitFilter] through ruins-like effects", UniqueTarget.Unit),

    // The following block gets cached in MapUnit for faster getMovementCostBetweenAdjacentTiles
    DoubleMovementOnTerrain("Double movement in [terrainFilter]", UniqueTarget.Unit),
    AllTilesCost1Move("All tiles cost 1 movement", UniqueTarget.Unit),
    CanPassImpassable("Can pass through impassable tiles", UniqueTarget.Unit),
    IgnoresTerrainCost("Ignores terrain cost", UniqueTarget.Unit),
    IgnoresZOC("Ignores Zone of Control", UniqueTarget.Unit),
    RoughTerrainPenalty("Rough terrain penalty", UniqueTarget.Unit),
    CanEnterIceTiles("Can enter ice tiles", UniqueTarget.Unit),
    CannotEnterOcean("Cannot enter ocean tiles", UniqueTarget.Unit),
    CanEnterForeignTiles("May enter foreign tiles without open borders", UniqueTarget.Unit),
    CanEnterForeignTilesButLosesReligiousStrength("May enter foreign tiles without open borders, but loses [amount] religious strength each turn it ends there", UniqueTarget.Unit),
    ReducedDisembarkCost("[amount] Movement point cost to disembark", UniqueTarget.Global, UniqueTarget.Unit),
    ReducedEmbarkCost("[amount] Movement point cost to embark", UniqueTarget.Global, UniqueTarget.Unit),
    @Deprecated("as of 4.0.3", ReplaceWith("[1] Movement point cost to disembark <for [All] units>"))
    DisembarkCostDeprecated("Units pay only 1 movement point to disembark", UniqueTarget.Global),

    CannotBeBarbarian("Never appears as a Barbarian unit", UniqueTarget.Unit, flags = UniqueFlag.setOfHiddenToUsers),

    ReligiousUnit("Religious Unit", UniqueTarget.Unit),
    SpaceshipPart("Spaceship part", UniqueTarget.Unit, UniqueTarget.Building), // Should be deprecated in the near future
    AddInCapital("Can be added to [comment] in the Capital", UniqueTarget.Unit),

    StartGoldenAge("Can start an [amount]-turn golden age", UniqueTarget.Unit),
    GreatPerson("Great Person - [comment]", UniqueTarget.Unit),

    PreventSpreadingReligion("Prevents spreading of religion to the city it is next to", UniqueTarget.Unit),
    TakeReligionOverBirthCity("Takes your religion over the one in their birth city", UniqueTarget.Unit),
    RemoveOtherReligions("Removes other religions when spreading religion", UniqueTarget.Unit),

    CanActionSeveralTimes("Can [action] [amount] times", UniqueTarget.Unit),

    CannotBeHurried("Cannot be hurried", UniqueTarget.Building, UniqueTarget.Tech),
    CanSpeedupConstruction("Can speed up construction of a building", UniqueTarget.Unit),
    CanSpeedupWonderConstruction("Can speed up the construction of a wonder", UniqueTarget.Unit),
    CanHurryResearch("Can hurry technology research", UniqueTarget.Unit),
    CanTradeWithCityStateForGoldAndInfluence("Can undertake a trade mission with City-State, giving a large sum of gold and [amount] Influence", UniqueTarget.Unit),



    //endregion

    ///////////////////////////////////////// region TILE UNIQUES /////////////////////////////////////////

    // Natural wonders
    NaturalWonderNeighborCount("Must be adjacent to [amount] [simpleTerrain] tiles", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    NaturalWonderNeighborsRange("Must be adjacent to [amount] to [amount] [simpleTerrain] tiles", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    NaturalWonderSmallerLandmass("Must not be on [amount] largest landmasses", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    NaturalWonderLargerLandmass("Must be on [amount] largest landmasses", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    NaturalWonderLatitude("Occurs on latitudes from [amount] to [amount] percent of distance equator to pole", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    NaturalWonderGroups("Occurs in groups of [amount] to [amount] tiles", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    NaturalWonderConvertNeighbors("Neighboring tiles will convert to [baseTerrain]", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    // The "Except [terrainFilter]" could theoretically be implemented with a conditional
    NaturalWonderConvertNeighborsExcept("Neighboring tiles except [baseTerrain] will convert to [baseTerrain]", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    GrantsGoldToFirstToDiscover("Grants 500 Gold to the first civilization to discover it", UniqueTarget.Terrain),

    // General terrain
    DamagesContainingUnits("Units ending their turn on this terrain take [amount] damage", UniqueTarget.Terrain),
    TerrainGrantsPromotion("Grants [promotion] ([comment]) to adjacent [mapUnitFilter] units for the rest of the game", UniqueTarget.Terrain),
    GrantsCityStrength("[amount] Strength for cities built on this terrain", UniqueTarget.Terrain),
    ProductionBonusWhenRemoved("Provides a one-time Production bonus to the closest city when cut down", UniqueTarget.Terrain),
    Vegetation("Vegetation", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),


    TileProvidesYieldWithoutPopulation("Tile provides yield without assigned population", UniqueTarget.Terrain, UniqueTarget.Improvement),
    NullifyYields("Nullifies all other stats this tile provides", UniqueTarget.Terrain),
    RestrictedBuildableImprovements("Only [improvementFilter] improvements may be built on this tile", UniqueTarget.Terrain),

    BlocksLineOfSightAtSameElevation("Blocks line-of-sight from tiles at same elevation", UniqueTarget.Terrain),
    VisibilityElevation("Has an elevation of [amount] for visibility calculations", UniqueTarget.Terrain),

    OverrideFertility("Always Fertility [amount] for Map Generation", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    AddFertility("[amount] to Fertility for Map Generation", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),

    RegionRequirePercentSingleType("A Region is formed with at least [amount]% [simpleTerrain] tiles, with priority [amount]", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    RegionRequirePercentTwoTypes("A Region is formed with at least [amount]% [simpleTerrain] tiles and [simpleTerrain] tiles, with priority [amount]",
            UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    RegionRequireFirstLessThanSecond("A Region can not contain more [simpleTerrain] tiles than [simpleTerrain] tiles", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    IgnoreBaseTerrainForRegion("Base Terrain on this tile is not counted for Region determination", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    RegionExtraResource("Starts in regions of this type receive an extra [resource]", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    BlocksResources("Never receives any resources", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    ChangesTerrain("Becomes [terrainName] when adjacent to [terrainFilter]", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),

    HasQuality("Considered [terrainQuality] when determining start locations", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),

    NoNaturalGeneration("Doesn't generate naturally", UniqueTarget.Terrain, UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),
    TileGenerationConditions("Occurs at temperature between [amount] and [amount] and humidity between [amount] and [amount]", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    OccursInChains("Occurs in chains at high elevations", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    OccursInGroups("Occurs in groups around high elevations", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    MajorStrategicFrequency("Every [amount] tiles with this terrain will receive a major deposit of a strategic resource.", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),

    RareFeature("Rare feature", UniqueTarget.Terrain),

    DestroyableByNukesChance("[amount]% Chance to be destroyed by nukes", UniqueTarget.Terrain),
    @Deprecated("as of 3.19.19", ReplaceWith("[25]% Chance to be destroyed by nukes"))
    ResistsNukes("Resistant to nukes", UniqueTarget.Terrain),
    @Deprecated("as of 3.19.19", ReplaceWith("[50]% Chance to be destroyed by nukes"))
    DestroyableByNukes("Can be destroyed by nukes", UniqueTarget.Terrain),

    FreshWater(Constants.freshWater, UniqueTarget.Terrain),
    RoughTerrain("Rough terrain", UniqueTarget.Terrain),

    /////// Resource uniques
    ResourceAmountOnTiles("Deposits in [tileFilter] tiles always provide [amount] resources", UniqueTarget.Resource),
    CityStateOnlyResource("Can only be created by Mercantile City-States", UniqueTarget.Resource),

    ResourceWeighting("Generated with weight [amount]", UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),
    MinorDepositWeighting("Minor deposits generated with weight [amount]", UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),
    LuxuryWeightingForCityStates("Generated near City States with weight [amount]", UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),
    LuxurySpecialPlacement("Special placement during map generation", UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),
    ResourceFrequency("Generated on every [amount] tiles", UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),
    StrategicBalanceResource("Guaranteed with Strategic Balance resource option", UniqueTarget.Resource),

    ////// Improvement uniques
    ImprovementBuildableByFreshWater("Can also be built on tiles adjacent to fresh water", UniqueTarget.Improvement),
    ImprovementStatsOnTile("[stats] from [tileFilter] tiles", UniqueTarget.Improvement),
    ImprovementStatsForAdjacencies("[stats] for each adjacent [tileFilter]", UniqueTarget.Improvement),

    CanBuildOutsideBorders("Can be built outside your borders", UniqueTarget.Improvement),
    CanBuildJustOutsideBorders("Can be built just outside your borders", UniqueTarget.Improvement),
    CanOnlyBeBuiltOnTile("Can only be built on [tileFilter] tiles", UniqueTarget.Improvement),
    CannotBuildOnTile("Cannot be built on [tileFilter] tiles", UniqueTarget.Improvement),
    CanOnlyImproveResource("Can only be built to improve a resource", UniqueTarget.Improvement),
    NoFeatureRemovalNeeded("Does not need removal of [tileFilter]", UniqueTarget.Improvement),
    RemovesFeaturesIfBuilt("Removes removable features when built", UniqueTarget.Improvement),

    DefensiveBonus("Gives a defensive bonus of [relativeAmount]%", UniqueTarget.Improvement),
    ImprovementMaintenance("Costs [amount] gold per turn when in your territory", UniqueTarget.Improvement), // Unused
    DamagesAdjacentEnemyUnits("Adjacent enemy units ending their turn take [amount] damage", UniqueTarget.Improvement),
    TakeOverTilesAroundWhenBuilt("Constructing it will take over the tiles around it and assign them to your closest city", UniqueTarget.Improvement),

    GreatImprovement("Great Improvement", UniqueTarget.Improvement),
    IsAncientRuinsEquivalent("Provides a random bonus when entered", UniqueTarget.Improvement),
    TakesOverAdjacentTiles("Constructing it will take over the tiles around it and assign them to your closest city", UniqueTarget.Improvement),

    Unpillagable("Unpillagable", UniqueTarget.Improvement),
    PillageYieldRandom("Pillaging this improvement yields approximately [stats]", UniqueTarget.Improvement),
    PillageYieldFixed("Pillaging this improvement yields [stats]", UniqueTarget.Improvement),
    Irremovable("Irremovable", UniqueTarget.Improvement),
    //endregion

    ///////////////////////////////////////// region CONDITIONALS /////////////////////////////////////////


    /////// general conditionals
    ConditionalTimedUnique("for [amount] turns", UniqueTarget.Conditional),
    ConditionalConsumeUnit("by consuming this unit", UniqueTarget.Conditional),
    ConditionalChance("with [amount]% chance", UniqueTarget.Conditional),

    /////// civ conditionals
    ConditionalWar("when at war", UniqueTarget.Conditional),
    ConditionalNotWar("when not at war", UniqueTarget.Conditional),
    ConditionalGoldenAge("during a Golden Age", UniqueTarget.Conditional),
    ConditionalWithResource("with [resource]", UniqueTarget.Conditional),

    ConditionalHappy("while the empire is happy", UniqueTarget.Conditional),
    ConditionalBetweenHappiness("when between [amount] and [amount] Happiness", UniqueTarget.Conditional),
    ConditionalBelowHappiness("when below [amount] Happiness", UniqueTarget.Conditional),

    ConditionalDuringEra("during the [era]", UniqueTarget.Conditional),
    ConditionalBeforeEra("before the [era]", UniqueTarget.Conditional),
    ConditionalStartingFromEra("starting from the [era]", UniqueTarget.Conditional),

    ConditionalFirstCivToResearch("if no other Civilization has researched this", UniqueTarget.Conditional),
    ConditionalTech("after discovering [tech]", UniqueTarget.Conditional),
    ConditionalNoTech("before discovering [tech]", UniqueTarget.Conditional),
    ConditionalWhenTech("upon discovering [tech]", UniqueTarget.Conditional), //todo no references anywhere
    ConditionalPolicy("after adopting [policy]", UniqueTarget.Conditional),
    ConditionalNoPolicy("before adopting [policy]", UniqueTarget.Conditional),
    ConditionalBuildingBuilt("if [buildingName] is constructed", UniqueTarget.Conditional),

    /////// city conditionals
    ConditionalCityWithBuilding("in cities with a [buildingFilter]", UniqueTarget.Conditional),
    ConditionalCityWithoutBuilding("in cities without a [buildingFilter]", UniqueTarget.Conditional),
    ConditionalPopulationFilter("in cities with at least [amount] [populationFilter]", UniqueTarget.Conditional),
    @Deprecated("as of 3.19.19", ReplaceWith("in cities with at least [amount] [Specialists]"))
    ConditionalSpecialistCount("if this city has at least [amount] specialists", UniqueTarget.Conditional),
    @Deprecated("as of 3.19.19", ReplaceWith("in cities with at least [amount] [Followers of the Majority Religion]"))
    ConditionalFollowerCount("in cities where this religion has at least [amount] followers", UniqueTarget.Conditional),
    ConditionalWhenGarrisoned("with a garrison", UniqueTarget.Conditional),

    /////// unit conditionals
    ConditionalOurUnit("for [mapUnitFilter] units", UniqueTarget.Conditional),
    ConditionalUnitWithPromotion("for units with [promotion]", UniqueTarget.Conditional),
    ConditionalUnitWithoutPromotion("for units without [promotion]", UniqueTarget.Conditional),
    ConditionalVsCity("vs cities", UniqueTarget.Conditional),
    ConditionalVsUnits("vs [mapUnitFilter] units", UniqueTarget.Conditional),
    ConditionalVsLargerCiv("when fighting units from a Civilization with more Cities than you", UniqueTarget.Conditional),
    ConditionalAttacking("when attacking", UniqueTarget.Conditional),
    ConditionalDefending("when defending", UniqueTarget.Conditional),
    ConditionalFightingInTiles("when fighting in [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalForeignContinent("on foreign continents", UniqueTarget.Conditional),
    ConditionalAdjacentUnit("when adjacent to a [mapUnitFilter] unit", UniqueTarget.Conditional),
    ConditionalAboveHP("when above [amount] HP", UniqueTarget.Conditional),
    ConditionalBelowHP("when below [amount] HP", UniqueTarget.Conditional),
    ConditionalHasNotUsedOtherActions("if it hasn't used other actions yet", UniqueTarget.Conditional),

    /////// tile conditionals
    ConditionalNeighborTiles("with [amount] to [amount] neighboring [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalNeighborTilesAnd("with [amount] to [amount] neighboring [tileFilter] [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalInTiles("in [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalInTilesAnd("in [tileFilter] [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalInTilesNot("in tiles without [tileFilter]", UniqueTarget.Conditional),
    ConditionalNearTiles("within [amount] tiles of a [tileFilter]", UniqueTarget.Conditional),

    /////// area conditionals
    ConditionalOnWaterMaps("on water maps", UniqueTarget.Conditional),
    ConditionalInRegionOfType("in [regionType] Regions", UniqueTarget.Conditional),
    ConditionalInRegionExceptOfType("in all except [regionType] Regions", UniqueTarget.Conditional),

    //endregion

    ///////////////////////////////////////// region TRIGGERED ONE-TIME /////////////////////////////////////////


    OneTimeFreeUnit("Free [baseUnitFilter] appears", UniqueTarget.Triggerable),  // used in Policies, Buildings
    OneTimeAmountFreeUnits("[amount] free [baseUnitFilter] units appear", UniqueTarget.Triggerable), // used in Buildings
    OneTimeFreeUnitRuins("Free [baseUnitFilter] found in the ruins", UniqueTarget.Ruins), // Differs from "Free [] appears" in that it spawns near the ruins instead of in a city
    OneTimeFreePolicy("Free Social Policy", UniqueTarget.Triggerable), // used in Buildings
    OneTimeAmountFreePolicies("[amount] Free Social Policies", UniqueTarget.Triggerable),  // Not used in Vanilla
    OneTimeEnterGoldenAge("Empire enters golden age", UniqueTarget.Triggerable),  // used in Policies, Buildings
    OneTimeFreeGreatPerson("Free Great Person", UniqueTarget.Triggerable),  // used in Policies, Buildings
    OneTimeGainPopulation("[amount] population [cityFilter]", UniqueTarget.Triggerable),  // used in CN tower
    OneTimeGainPopulationRandomCity("[amount] population in a random city", UniqueTarget.Ruins),
    OneTimeFreeTech("Free Technology", UniqueTarget.Triggerable),  // used in Buildings
    OneTimeAmountFreeTechs("[amount] Free Technologies", UniqueTarget.Triggerable),  // used in Policy
    OneTimeFreeTechRuins("[amount] free random researchable Tech(s) from the [era]", UniqueTarget.Ruins),
    OneTimeRevealEntireMap("Reveals the entire map", UniqueTarget.Triggerable),  // used in tech
    OneTimeGainStat("Gain [amount] [stat]", UniqueTarget.Ruins),
    OneTimeGainStatRange("Gain [amount]-[amount] [stat]", UniqueTarget.Ruins),
    OneTimeGainPantheon("Gain enough Faith for a Pantheon", UniqueTarget.Ruins),
    OneTimeGainProphet("Gain enough Faith for [amount]% of a Great Prophet", UniqueTarget.Ruins),
    // todo: The "up to [All]" used in vanilla json is not nice to read. Split?
    OneTimeRevealSpecificMapTiles("Reveal up to [amount/'all'] [tileFilter] within a [amount] tile radius", UniqueTarget.Ruins),
    OneTimeRevealCrudeMap("From a randomly chosen tile [amount] tiles away from the ruins, reveal tiles up to [amount] tiles away with [amount]% chance", UniqueTarget.Ruins),
    OneTimeTriggerVoting("Triggers voting for the Diplomatic Victory", UniqueTarget.Triggerable),  // used in Building
    OneTimeGlobalAlert("Triggers the following global alert: [comment]", UniqueTarget.Triggerable), // used in Policy

    OneTimeUnitHeal("Heal this unit by [amount] HP", UniqueTarget.Promotion),
    OneTimeUnitGainXP("This Unit gains [amount] XP", UniqueTarget.Ruins),
    OneTimeUnitUpgrade("This Unit upgrades for free", UniqueTarget.Global),  // Not used in Vanilla
    OneTimeUnitSpecialUpgrade("This Unit upgrades for free including special upgrades", UniqueTarget.Ruins),
    OneTimeUnitGainPromotion("This Unit gains the [promotion] promotion", UniqueTarget.Triggerable),  // Not used in Vanilla
    SkipPromotion("Doing so will consume this opportunity to choose a Promotion", UniqueTarget.Promotion),

    UnitsGainPromotion("[mapUnitFilter] units gain the [promotion] promotion", UniqueTarget.Triggerable),  // Not used in Vanilla
    @Deprecated("as of 3.19.8", ReplaceWith("[+amount]% Strength <when attacking> <for [mapUnitFilter] units> <for [amount2] turns>"))
    TimedAttackStrength("+[amount]% attack strength to all [mapUnitFilter] units for [amount2] turns", UniqueTarget.Global),  // used in Policy
    FreeStatBuildings("Provides the cheapest [stat] building in your first [amount] cities for free", UniqueTarget.Triggerable),  // used in Policy
    FreeSpecificBuildings("Provides a [buildingName] in your first [amount] cities for free", UniqueTarget.Triggerable),  // used in Policy

    //endregion

    ///////////////////////////////////////////// region META /////////////////////////////////////////////

    AvailableAfterCertainTurns("Only available after [amount] turns", UniqueTarget.Ruins),
    HiddenWithoutReligion("Hidden when religion is disabled", UniqueTarget.Unit, UniqueTarget.Building, UniqueTarget.Ruins, flags = UniqueFlag.setOfHiddenToUsers),
    HiddenBeforePantheon("Hidden before founding a Pantheon", UniqueTarget.Ruins),
    HiddenAfterPantheon("Hidden after founding a Pantheon", UniqueTarget.Ruins),
    HiddenAfterGreatProphet("Hidden after generating a Great Prophet", UniqueTarget.Ruins),
    HiddenWithoutVictoryType("Hidden when [victoryType] Victory is disabled", UniqueTarget.Building, UniqueTarget.Unit, flags = UniqueFlag.setOfHiddenToUsers),
    HiddenFromCivilopedia("Will not be displayed in Civilopedia", *UniqueTarget.values(), flags = UniqueFlag.setOfHiddenToUsers),

    // endregion

    // region DEPRECATED AND REMOVED

    @Deprecated("as of 4.1.0", ReplaceWith("[+15]% Strength bonus for [Military] units within [2] tiles"), DeprecationLevel.ERROR)
    BonusForUnitsInRadius("Bonus for units in 2 tile radius 15%", UniqueTarget.Unit),
    @Deprecated("as of 4.0.15", ReplaceWith("Irremovable"), DeprecationLevel.ERROR)
    Indestructible("Indestructible", UniqueTarget.Improvement),

    @Deprecated("as of 3.19.1", ReplaceWith("[stats] from every [Wonder]"), DeprecationLevel.ERROR)
    StatsFromWondersDeprecated("[stats] from every Wonder", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.3", ReplaceWith("[stats] from every [buildingFilter] <in cities where this religion has at least [amount] followers>"), DeprecationLevel.ERROR)
    StatsForBuildingsWithFollowers("[stats] from every [buildingFilter] in cities where this religion has at least [amount] followers", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.3", ReplaceWith("[+25]% Production towards any buildings that already exist in the Capital"), DeprecationLevel.ERROR)
    PercentProductionBuildingsInCapitalDeprecated("+25% Production towards any buildings that already exist in the Capital", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.2", ReplaceWith("[amount]% Food is carried over after population increases [in this city]"), DeprecationLevel.ERROR)
    CarryOverFoodDeprecated("[amount]% of food is carried over after population increases", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.2", ReplaceWith("[amount]% Food is carried over after population increases [cityFilter]"), DeprecationLevel.ERROR)
    CarryOverFoodAlsoDeprecated("[amount]% of food is carried over [cityFilter] after population increases", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.2", ReplaceWith("[amount]% Culture cost of natural border growth [cityFilter]"), DeprecationLevel.ERROR)
    BorderGrowthPercentageWithoutPercentageSign("[amount]% Culture cost of natural border growth [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.1", ReplaceWith("[-amount]% Culture cost of natural border growth [cityFilter]"), DeprecationLevel.ERROR)
    DecreasedAcquiringTilesCost("-[amount]% Culture cost of acquiring tiles [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.1", ReplaceWith("[amount]% Culture cost of natural border growth [in all cities]"), DeprecationLevel.ERROR)
    CostOfNaturalBorderGrowth("[amount]% cost of natural border growth", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.1", ReplaceWith("[-amount]% Gold cost of acquiring tiles [cityFilter]"), DeprecationLevel.ERROR)
    TileCostPercentageDiscount("-[amount]% Gold cost of acquiring tiles [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.3", ReplaceWith("[stat] cost of purchasing [baseUnitFilter] units [amount]%"), DeprecationLevel.ERROR)
    BuyUnitsDiscountDeprecated("[stat] cost of purchasing [baseUnitFilter] units in cities [amount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.1", ReplaceWith("[+amount]% Strength for cities <with a garrison> <when attacking>"), DeprecationLevel.ERROR)
    StrengthForGarrisonedCitiesAttacking("+[amount]% attacking strength for cities with garrisoned units", UniqueTarget.Global),
    @Deprecated("as of 3.19.2", ReplaceWith("Population loss from nuclear attacks [-amount]% [in this city]"), DeprecationLevel.ERROR)
    PopulationLossFromNukesDeprecated("Population loss from nuclear attacks -[amount]%", UniqueTarget.Global),
    @Deprecated("as of 3.19.3", ReplaceWith("[amount]% Natural religion spread [cityFilter] <after discovering [tech/policy]>\"" +
            " OR \"[amount]% Natural religion spread [cityFilter] <after adopting [tech/policy]>"), DeprecationLevel.ERROR)
    NaturalReligionSpreadStrengthWith("[amount]% Natural religion spread [cityFilter] with [tech/policy]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.4", ReplaceWith("[amount] HP when healing <in [tileFilter] tiles>"), DeprecationLevel.ERROR)
    HealInTiles("[amount] HP when healing in [tileFilter] tiles", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("No movement cost to pillage <for [Melee] units>"), DeprecationLevel.ERROR)
    NoMovementToPillageMelee("Melee units pay no movement cost to pillage", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("as of 3.19.3", ReplaceWith("All adjacent units heal [+15] HP when healing"), DeprecationLevel.ERROR)
    HealAdjacentUnitsDeprecated("Heal adjacent units for an additional 15 HP per turn", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("Adjacent enemy units ending their turn take [amount] damage"), DeprecationLevel.ERROR)
    DamagesAdjacentEnemyUnitsOld("Deal [amount] damage to adjacent enemy units", UniqueTarget.Improvement),


    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% Golden Age length"), DeprecationLevel.ERROR)
    GoldenAgeLengthIncreased("Golden Age length increased by [amount]%", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% Strength for cities <when defending>"), DeprecationLevel.ERROR)
    StrengthForCitiesDefending("+[amount]% Defensive Strength for cities", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% Strength for cities <when attacking>"), DeprecationLevel.ERROR)
    StrengthForCitiesAttacking("[amount]% Attacking Strength for cities", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[amount]% Strength <for [mapUnitFilter] units> <when adjacent to a [mapUnitFilter] unit>"), DeprecationLevel.ERROR)
    StrengthFromAdjacentUnits("[amount]% Strength for [mapUnitFilter] units which have another [mapUnitFilter] unit in an adjacent tile", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[-amount]% Gold cost of upgrading <for [baseUnitFilter] units>"), DeprecationLevel.ERROR)
    ReducedUpgradingGoldCost("Gold cost of upgrading [baseUnitFilter] units reduced by [amount]%", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+100]% Gold from Great Merchant trade missions"), DeprecationLevel.ERROR)
    DoubleGoldFromTradeMissions("Double gold from Great Merchant trade missions", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+25]% City Strength from defensive buildings"), DeprecationLevel.ERROR)
    DefensiveBuilding25("Defensive buildings in all cities are 25% more effective", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[-amount]% maintenance on road & railroads"), DeprecationLevel.ERROR)
    DecreasedRoadMaintenanceDeprecated("Maintenance on roads & railroads reduced by [amount]%", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[-amount]% maintenance cost for buildings [cityFilter]"), DeprecationLevel.ERROR)
    DecreasedBuildingMaintenanceDeprecated("-[amount]% maintenance cost for buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount] Happiness from each type of luxury resource"), DeprecationLevel.ERROR)
    BonusHappinessFromLuxuryDeprecated("+[amount] happiness from each type of luxury resource", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[-amount]% Culture cost of adopting new Policies"), DeprecationLevel.ERROR)
    LessPolicyCostDeprecated("Culture cost of adopting new Policies reduced by [amount]%", UniqueTarget.Global),
    @Deprecated("as of 3.19.1", ReplaceWith("[amount]% Culture cost of adopting new Policies"), DeprecationLevel.ERROR)
    LessPolicyCostDeprecated2("[amount]% Culture cost of adopting new policies", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% resources gifted by City-States"), DeprecationLevel.ERROR)
    CityStateResourcesDeprecated("Quantity of Resources gifted by City-States increased by [amount]%", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[-amount]% City-State Influence degradation"), DeprecationLevel.ERROR)
    CityStateInfluenceDegradationDeprecated("City-State Influence degrades [amount]% slower", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% Happiness from luxury resources gifted by City-States"), DeprecationLevel.ERROR)
    CityStateLuxuryHappinessDeprecated("Happiness from Luxury Resources gifted by City-States increased by [amount]%", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% [stat] from every [tileFilter/specialist/buildingName]"), DeprecationLevel.ERROR)
    StatPercentSignedFromObject("+[amount]% [stat] from every [tileFilter/specialist/buildingName]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% Yield from every [tileFilter]"), DeprecationLevel.ERROR)
    AllStatsSignedPercentFromObject("+[amount]% yield from every [tileFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    @Deprecated("as of 3.18.14", ReplaceWith("[stats] [in all cities] <before discovering [tech]>\" OR \"[stats] [in all cities] <before adopting [policy]>"), DeprecationLevel.ERROR)
    StatsFromCitiesBefore("[stats] per turn from cities before [tech/policy]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    @Deprecated("as of 3.18.12", ReplaceWith("[amount]% XP gained from combat"), DeprecationLevel.WARNING)
    BonuxXPGain("[amount]% Bonus XP gain", UniqueTarget.Unit),
    @Deprecated("as of 3.18.12", ReplaceWith("[amount]% XP gained from combat <for [mapUnitFilter] units>"), DeprecationLevel.WARNING)
    BonusXPGainForUnits("[mapUnitFilter] units gain [amount]% more Experience from combat", UniqueTarget.Global),

    @Deprecated("as of 3.18.14", ReplaceWith("[amount]% maintenance costs <for [mapUnitFilter] units>"), DeprecationLevel.WARNING)
    UnitMaintenanceDiscountGlobal("[amount]% maintenance costs for [mapUnitFilter] units", UniqueTarget.Global),

    @Deprecated("as of 3.18.2", ReplaceWith("[50]% of excess happiness converted to [Culture]"), DeprecationLevel.ERROR)
    ExcessHappinessToCultureDeprecated("50% of excess happiness added to culture towards policies", UniqueTarget.Global),
    @Deprecated("as of 3.16.11", ReplaceWith("Not displayed as an available construction without [buildingName]"), DeprecationLevel.ERROR)
    NotDisplayedUnlessOtherBuildingBuilt("Not displayed as an available construction unless [buildingName] is built", UniqueTarget.Building),
    @Deprecated("as of 3.18.2", ReplaceWith("[-amount]% Food consumption by specialists [cityFilter]"), DeprecationLevel.ERROR)
    FoodConsumptionBySpecialistsDeprecated("-[amount]% food consumption by specialists [cityFilter]", UniqueTarget.Global),
    @Deprecated("as of 3.18.6", ReplaceWith("Cannot enter ocean tiles <before discovering [Astronomy]>"), DeprecationLevel.ERROR)
    CannotEnterOceanUntilAstronomy("Cannot enter ocean tiles until Astronomy", UniqueTarget.Unit),
    @Deprecated("as of 3.18.5", ReplaceWith("Cannot be built on [tileFilter] tiles <before discovering [tech]>"), DeprecationLevel.ERROR)
    RequiresTechToBuildOnTile("Cannot be built on [tileFilter] tiles until [tech] is discovered", UniqueTarget.Improvement),

    @Deprecated("as of 3.17.9, removed as of 3.19.3", ReplaceWith ("May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] at an increasing price ([amount]) <starting from the [era]>"), DeprecationLevel.ERROR)
    BuyUnitsIncreasingCostEra("May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] starting from the [era] at an increasing price ([amount])", UniqueTarget.Global),

    @Deprecated("as of 3.17.10 - removed 3.18.19", ReplaceWith("[stats] from [tileFilter] tiles <after discovering [tech]>"), DeprecationLevel.ERROR)
    StatsOnTileWithTech("[stats] on [tileFilter] tiles once [tech] is discovered", UniqueTarget.Improvement),
    @Deprecated("as of 3.17.10 - removed 3.18.19", ReplaceWith("[stats] <after discovering [tech]>"), DeprecationLevel.ERROR)
    StatsWithTech("[stats] once [tech] is discovered", UniqueTarget.Improvement, UniqueTarget.Building),
    @Deprecated("as of 3.17.10 - removed 3.18.19", ReplaceWith("Adjacent enemy units ending their turn take [30] damage"), DeprecationLevel.ERROR)
    DamagesAdjacentEnemyUnitsForExactlyThirtyDamage("Deal 30 damage to adjacent enemy units", UniqueTarget.Improvement),
    @Deprecated("as of 3.17.7 - removed 3.18.19", ReplaceWith("Gain a free [buildingName] [cityFilter]"), DeprecationLevel.ERROR)
    ProvidesFreeBuildings("Provides a free [buildingName] [cityFilter]", UniqueTarget.Global),
    @Deprecated("as of 3.17.10 - removed 3.18.18", ReplaceWith("[+amount]% [stat] [cityFilter]"), DeprecationLevel.ERROR)
    StatPercentBonusCitiesDeprecated("+[amount]% [stat] [cityFilter]", UniqueTarget.Global),
    @Deprecated("as of 3.17.10 - removed 3.18.18", ReplaceWith("[+amount]% [stat] [in all cities]"), DeprecationLevel.ERROR)
    StatPercentBonusCitiesDeprecated2("+[amount]% [stat] in all cities", UniqueTarget.Global),
    // type added 3.18.5
    @Deprecated("as of 3.17.1 - removed 3.18.18", ReplaceWith("[amount]% [stat] [in all cities] <while the empire is happy>"), DeprecationLevel.ERROR)
    StatPercentBonusCitiesDeprecatedWhileEmpireHappy("[amount]% [stat] while the empire is happy", UniqueTarget.Global),

    @Deprecated("as of 3.16.15 - removed 3.18.4", ReplaceWith("Provides the cheapest [stat] building in your first [amount] cities for free"), DeprecationLevel.ERROR)
    FreeStatBuildingsDeprecated("Immediately creates the cheapest available cultural building in each of your first [amount] cities for free", UniqueTarget.Global),
    @Deprecated("as of 3.16.15 - removed 3.18.4", ReplaceWith("Provides a [buildingName] in your first [amount] cities for free"), DeprecationLevel.ERROR)
    FreeSpecificBuildingsDeprecated("Immediately creates a [buildingName] in each of your first [amount] cities for free", UniqueTarget.Global),



    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+amount]% Strength <when attacking>"), DeprecationLevel.ERROR)
    StrengthAttacking("+[amount]% Strength when attacking", UniqueTarget.Unit),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+amount]% Strength <when defending>"), DeprecationLevel.ERROR)
    StrengthDefending("+[amount]% Strength when defending", UniqueTarget.Unit),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <when defending> <vs [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    StrengthDefendingUnitFilter("[amount]% Strength when defending vs [mapUnitFilter] units", UniqueTarget.Unit),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+amount]% Strength <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    DamageForUnits("[mapUnitFilter] units deal +[amount]% damage", UniqueTarget.Global),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+10]% Strength <for [All] units> <during a Golden Age>"), DeprecationLevel.ERROR)
    StrengthGoldenAge("+10% Strength for all units during Golden Age", UniqueTarget.Global),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <when fighting in [tileFilter] tiles> <when defending>"), DeprecationLevel.ERROR)
    StrengthDefenseTiles("+[amount]% defence in [tileFilter] tiles", UniqueTarget.Unit),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <when fighting in [tileFilter] tiles>"), DeprecationLevel.ERROR)
    StrengthIn("+[amount]% Strength in [tileFilter]", UniqueTarget.Unit),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <for [mapUnitFilter] units> <when fighting in [tileFilter] tiles>"), DeprecationLevel.ERROR)
    StrengthUnitsTiles("[amount]% Strength for [mapUnitFilter] units in [tileFilter]", UniqueTarget.Global),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+15]% Strength <for [All] units> <vs cities> <when attacking>"), DeprecationLevel.ERROR)
    StrengthVsCities("+15% Combat Strength for all units when attacking Cities", UniqueTarget.Global),


    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+amount] Movement <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    MovementUnits("+[amount] Movement for all [mapUnitFilter] units", UniqueTarget.Global),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+1] Movement <for [All] units> <during a Golden Age>"), DeprecationLevel.ERROR)
    MovementGoldenAge("+1 Movement for all units during Golden Age", UniqueTarget.Global),

    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount] Sight <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    SightUnits("[amount] Sight for all [mapUnitFilter] units", UniqueTarget.Global),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount] Sight"), DeprecationLevel.ERROR)
    VisibilityRange("[amount] Visibility Range", UniqueTarget.Unit),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[-1] Sight"), DeprecationLevel.ERROR)
    LimitedVisibility("Limited Visibility", UniqueTarget.Unit),

    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Spread Religion Strength <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    SpreadReligionStrengthUnits("[amount]% Spread Religion Strength for [mapUnitFilter] units", UniqueTarget.Global),

    @Deprecated("as of 3.17.10 - removed 3.18.5", ReplaceWith("[+amount]% Production when constructing [baseUnitFilter] units [cityFilter]"), DeprecationLevel.ERROR)
    PercentProductionUnitsDeprecated("+[amount]% Production when constructing [baseUnitFilter] units [cityFilter]", UniqueTarget.Global),

    @Deprecated("as of 3.17.10 - removed 3.18.5", ReplaceWith("[+amount]% Production when constructing [stat] buildings [in all cities]"), DeprecationLevel.ERROR)
    PercentProductionStatBuildings("+[amount]% Production when constructing [stat] buildings", UniqueTarget.Global),
    @Deprecated("as of 3.17.10 - removed 3.18.5", ReplaceWith("[+amount]% Production when constructing [constructionFilter] buildings [in all cities]"), DeprecationLevel.ERROR)
    PercentProductionConstructions("+[amount]% Production when constructing [constructionFilter]", UniqueTarget.Global),
    @Deprecated("as of 3.17.10 - removed 3.18.5", ReplaceWith("[amount]% Production when constructing [buildingName] buildings [in all cities]"), DeprecationLevel.ERROR)
    PercentProductionBuildingName("+[amount]% Production when constructing a [buildingName]", UniqueTarget.Global),
    @Deprecated("as of 3.17.10 - removed 3.18.5", ReplaceWith("[amount]% Production when constructing [constructionFilter] buildings [cityFilter]"), DeprecationLevel.ERROR)
    PercentProductionConstructionsCities("+[amount]% Production when constructing [constructionFilter] [cityFilter]", UniqueTarget.Global),


    @Deprecated("As of 3.17.1 - removed 3.17.13", ReplaceWith("Double movement in [Coast]"), DeprecationLevel.ERROR)
    DoubleMovementCoast("Double movement in coast", UniqueTarget.Unit),
    @Deprecated("As of 3.17.1 - removed 3.17.13", ReplaceWith("Double movement in [terrainFilter]"), DeprecationLevel.ERROR)
    DoubleMovementForestJungle("Double movement rate through Forest and Jungle", UniqueTarget.Unit),
    @Deprecated("As of 3.17.1 - removed 3.17.13", ReplaceWith("Double movement in [terrainFilter]"), DeprecationLevel.ERROR)
    DoubleMovementSnowTundraHill("Double movement in Snow, Tundra and Hills", UniqueTarget.Unit),


    @Deprecated("As of 3.17.3 - removed 3.17.13", ReplaceWith("[+amount]% Strength"), DeprecationLevel.ERROR)
    StrengthPlus("+[amount]% Strength", UniqueTarget.Unit),
    @Deprecated("As of 3.17.3 - removed 3.17.13", ReplaceWith("[-amount]% Strength"), DeprecationLevel.ERROR)
    StrengthMin("-[amount]% Strength", UniqueTarget.Unit),
    @Deprecated("As of 3.17.3 - removed 3.17.13", ReplaceWith("[+amount]% Strength <vs [combatantFilter] units>\" OR \"[+amount]% Strength <vs cities>"), DeprecationLevel.ERROR)
    StrengthPlusVs("+[amount]% Strength vs [combatantFilter]", UniqueTarget.Unit),
    @Deprecated("As of 3.17.3 - removed 3.17.13", ReplaceWith("[-amount]% Strength <vs [combatantFilter] units>\" OR \"[+amount]% Strength <vs cities>"), DeprecationLevel.ERROR)
    StrengthMinVs("-[amount]% Strength vs [combatantFilter]", UniqueTarget.Unit),
    @Deprecated("As of 3.17.3 - removed 3.17.13", ReplaceWith("[+amount]% Strength"), DeprecationLevel.ERROR)
    CombatBonus("+[amount]% Combat Strength", UniqueTarget.Unit),

    @Deprecated("As of 3.16.11 - removed 3.17.11", ReplaceWith("[+1] Movement <for [Embarked] units>"), DeprecationLevel.ERROR)
    EmbarkedUnitMovement1("Increases embarked movement +1", UniqueTarget.Global),
    @Deprecated("As of 3.16.11 - removed 3.17.11", ReplaceWith("[+1] Movement <for [Embarked] units>"), DeprecationLevel.ERROR)
    EmbarkedUnitMovement2("+1 Movement for all embarked units", UniqueTarget.Global),

    @Deprecated("As of 3.16.11 - removed 3.17.11", ReplaceWith("[-amount]% unhappiness from population [in all cities]"), DeprecationLevel.ERROR)
    UnhappinessFromPopulationPercentageChangeOld1("Unhappiness from population decreased by [amount]%", UniqueTarget.Global),
    @Deprecated("As of 3.16.11 - removed 3.17.11", ReplaceWith("[-amount]% unhappiness from population [cityFilter]"), DeprecationLevel.ERROR)
    UnhappinessFromPopulationPercentageChangeOld2("Unhappiness from population decreased by [amount]% [cityFilter]", UniqueTarget.Global),

    @Deprecated("As of 3.16.14 - removed 3.17.11", ReplaceWith("[+amount]% growth [cityFilter]"), DeprecationLevel.ERROR)
    GrowthPercentBonusPositive("+[amount]% growth [cityFilter]", UniqueTarget.Global),
    @Deprecated("As of 3.16.14 - removed 3.17.11", ReplaceWith("[+amount]% growth [cityFilter] <when not at war>"), DeprecationLevel.ERROR)
    GrowthPercentBonusWhenNotAtWar("+[amount]% growth [cityFilter] when not at war", UniqueTarget.Global),
    @Deprecated("As of 3.16.16 - removed as of 3.17.11", ReplaceWith("[-amount]% maintenance costs <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    DecreasedUnitMaintenanceCostsByFilter("-[amount]% [mapUnitFilter] unit maintenance costs", UniqueTarget.Global),
    @Deprecated("As of 3.16.16 - removed 3.17.11", ReplaceWith("[amount]% maintenance costs <for [All] units>"), DeprecationLevel.ERROR)
    DecreasedUnitMaintenanceCostsGlobally("-[amount]% unit upkeep costs", UniqueTarget.Global),

    @Deprecated("As of 3.16.16 - removed 3.17.11", ReplaceWith("[stats] from every specialist [in all cities]"), DeprecationLevel.ERROR)
    StatsFromSpecialistDeprecated("[stats] from every specialist", UniqueTarget.Global),
    @Deprecated("As of 3.16.16 - removed 3.17.11", ReplaceWith("[stats] <if this city has at least [amount] specialists>"), DeprecationLevel.ERROR)
    StatBonusForNumberOfSpecialists("[stats] if this city has at least [amount] specialists", UniqueTarget.Global),

    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[+1] Sight"), DeprecationLevel.ERROR)
    Plus1SightForAutoupdates("+1 Visibility Range", UniqueTarget.Unit),
    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[+amount] Sight"), DeprecationLevel.ERROR)
    PlusSightForAutoupdates("+[amount] Visibility Range", UniqueTarget.Unit),
    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[+amount] Sight <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    PlusSightForAutoupdates2("+[amount] Sight for all [mapUnitFilter] units", UniqueTarget.Unit),
    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[+2] Sight"), DeprecationLevel.ERROR)
    Plus2SightForAutoupdates("+2 Visibility Range", UniqueTarget.Unit),
    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("Can build [Land] improvements on tiles"), DeprecationLevel.ERROR)
    CanBuildImprovementsOnTiles("Can build improvements on tiles", UniqueTarget.Unit),

    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[+1] Happiness from each type of luxury resource"), DeprecationLevel.ERROR)
    BonusHappinessFromLuxuryDeprecated2("+1 happiness from each type of luxury resource", UniqueTarget.Global),

    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("Science gained from research agreements [+50]%"), DeprecationLevel.ERROR)
    ScienceGainedResearchAgreementsDeprecated("Science gained from research agreements +50%", UniqueTarget.Unit),

    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[-33]% maintenance costs <for [All] units>"), DeprecationLevel.ERROR)
    DecreasedUnitMaintenanceCostsGlobally2("-33% unit upkeep costs", UniqueTarget.Global),
    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[-50]% Food consumption by specialists [in all cities]"), DeprecationLevel.ERROR)
    FoodConsumptionBySpecialistsDeprecated2("-50% food consumption by specialists", UniqueTarget.Global),
    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[+50]% Strength for cities <with a garrison> <when attacking>"), DeprecationLevel.ERROR)
    StrengthForGarrisonedCitiesAttackingDeprecated("+50% attacking strength for cities with garrisoned units", UniqueTarget.Global),

    // endregion

    ;

    /** A map of allowed [UniqueParameterType]s per parameter position. Initialized from overridable function [parameterTypeMapInitializer]. */
    val parameterTypeMap = parameterTypeMapInitializer()

    /** For uniques that have "special" parameters that can accept multiple types, we can override them manually
     *  For 95% of cases, auto-matching is fine. */
    open fun parameterTypeMapInitializer(): ArrayList<List<UniqueParameterType>> {
        val map = ArrayList<List<UniqueParameterType>>()
        for (placeholder in text.getPlaceholderParameters()) {
            val matchingParameterTypes = placeholder
                .split('/')
                .map { UniqueParameterType.safeValueOf(it.replace(numberRegex, "")) }
            map.add(matchingParameterTypes)
        }
        return map
    }
    val targetTypes = HashSet<UniqueTarget>()

    init {
        targetTypes.addAll(targets)
    }

    val placeholderText = text.getPlaceholderText()

    /** Ordinal determines severity - ordered from least to most severe, so we can use Severity >=  */
    enum class UniqueComplianceErrorSeverity {

        /** This is for filters that can also potentially accept free text, like UnitFilter and TileFilter */
        WarningOnly,

        /** This is a problem like "unit/resource/tech name doesn't exist in ruleset" - definite bug */
        RulesetSpecific,


        /** This is a problem like "numbers don't parse", "stat isn't stat", "city filter not applicable" */
        RulesetInvariant

    }

    /** Maps uncompliant parameters to their required types */
    fun getComplianceErrors(
        unique: Unique,
        ruleset: Ruleset
    ): List<UniqueComplianceError> {
        val errorList = ArrayList<UniqueComplianceError>()
        for ((index, param) in unique.params.withIndex()) {
            val acceptableParamTypes = parameterTypeMap[index]
            val errorTypesForAcceptableParameters =
                acceptableParamTypes.map { it.getErrorSeverity(param, ruleset) }
            if (errorTypesForAcceptableParameters.any { it == null }) continue // This matches one of the types!
            val leastSevereWarning =
                errorTypesForAcceptableParameters.minByOrNull { it!!.ordinal }!!
            errorList += UniqueComplianceError(param, acceptableParamTypes, leastSevereWarning)
        }
        return errorList
    }

    fun getDeprecationAnnotation(): Deprecated? = declaringClass.getField(name)
        .getAnnotation(Deprecated::class.java)

}

// I didn't put this is a companion object because APPARENTLY doing that means you can't use it in the init function.
val numberRegex = Regex("\\d+$") // Any number of trailing digits
