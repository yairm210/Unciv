package com.unciv.models.ruleset.unique

import com.unciv.Constants
import com.unciv.models.ruleset.RejectionReasonType
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.models.ruleset.validation.RulesetValidator
import com.unciv.models.ruleset.validation.Suppression
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import yairm210.purity.annotations.Readonly

// I didn't put this in a companion object because APPARENTLY doing that means you can't use it in the init function.
private val numberRegex = Regex("\\d+$") // Any number of trailing digits

const val ADDITIVE_BONUS_EXPLANATION = "Multiple bonuses stack additively: +50% + +50% = +100%"
const val MULTIPLICATIVE_BONUS_EXPLANATION = "Multiple bonuses stack multiplicatively: +50% + +50% = x1.5 * x1.5 = +125%"

enum class UniqueType(
    val text: String,
    vararg targets: UniqueTarget,
    val flags: Set<UniqueFlag> = emptySet(),
    val docDescription: String? = null
) {

    //////////////////////////////////////// region 01 GLOBAL UNIQUES ////////////////////////////////////////

    // region Stat providing uniques

    // Used for *global* bonuses and improvement/terrain bonuses
    Stats("[stats]", UniqueTarget.Global, UniqueTarget.Improvement, UniqueTarget.Terrain),
    // Used for city-wide bonuses
    StatsPerCity("[stats] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    StatsFromSpecialist("[stats] from every specialist [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsPerPopulation("[stats] per [positiveAmount] population [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsPerPolicies("[stats] per [positiveAmount] social policies adopted", UniqueTarget.Global, docDescription = "Only works for civ-wide stats"),
    StatsPerStat("[stats] per every [positiveAmount] [civWideStat]", UniqueTarget.Global),

    StatsFromCitiesOnSpecificTiles("[stats] in cities on [terrainFilter] tiles", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsFromBuildings("[stats] from all [buildingFilter] buildings", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsFromTiles("[stats] from [tileFilter] tiles [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsFromTilesWithout("[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    // This is a doozy
    StatsFromObject("[stats] from every [tileFilter/specialist/buildingFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsFromTradeRoute("[stats] from each Trade Route", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsFromGlobalCitiesFollowingReligion("[stats] for each global city following this religion", UniqueTarget.FounderBelief),
    StatsFromGlobalFollowers("[stats] from every [positiveAmount] global followers [cityFilter]", UniqueTarget.FounderBelief),

    // Stat percentage boosts
    StatPercentBonus("[relativeAmount]% [stat]", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = ADDITIVE_BONUS_EXPLANATION),
    StatPercentBonusCities("[relativeAmount]% [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = ADDITIVE_BONUS_EXPLANATION),
    StatPercentFromObject("[relativeAmount]% [stat] from every [tileFilter/buildingFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = ADDITIVE_BONUS_EXPLANATION),
    StatPercentFromObjectToResource("[positiveAmount]% of [stat] from every [improvementFilter/buildingFilter] in the city added to [resource]", UniqueTarget.Building),
    AllStatsPercentFromObject("[relativeAmount]% Yield from every [tileFilter/buildingFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = ADDITIVE_BONUS_EXPLANATION),
    StatPercentFromReligionFollowers("[relativeAmount]% [stat] from every follower, up to [relativeAmount]%", UniqueTarget.FollowerBelief, UniqueTarget.FounderBelief),
    BonusStatsFromCityStates("[relativeAmount]% [stat] from City-States", UniqueTarget.Global),
    StatPercentFromTradeRoutes("[relativeAmount]% [stat] from Trade Routes", UniqueTarget.Global),

    NullifiesStat("Nullifies [stat] [cityFilter]", UniqueTarget.Global),
    NullifiesGrowth("Nullifies Growth [cityFilter]", UniqueTarget.Global),

    PercentProductionBuildings("[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = ADDITIVE_BONUS_EXPLANATION),
    PercentProductionUnits("[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = ADDITIVE_BONUS_EXPLANATION),
    PercentProductionWonders("[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = ADDITIVE_BONUS_EXPLANATION),
    PercentProductionBuildingsInCapital("[relativeAmount]% Production towards any buildings that already exist in the Capital", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    PercentYieldFromPillaging("[relativeAmount]% Yield from pillaging tiles", UniqueTarget.Global, UniqueTarget.Unit),
    PercentHealthFromPillaging("[relativeAmount]% Health from pillaging tiles", UniqueTarget.Global, UniqueTarget.Unit),
    
    // endregion Stat providing uniques

    // region City-State related uniques

    CityStateMilitaryUnits("Provides military units every ≈[positiveAmount] turns", UniqueTarget.CityState),
    CityStateUniqueLuxury("Provides a unique luxury", UniqueTarget.CityState), // No conditional support as of yet

    // Todo: Lowercase the 'U' of 'Units' in this unique
    CityStateGiftedUnitsStartWithXp("Military Units gifted from City-States start with [positiveAmount] XP", UniqueTarget.Global),
    CityStateMoreGiftedUnits("Militaristic City-States grant units [positiveAmount] times as fast when you are at war with a common nation", UniqueTarget.Global),

    CityStateGoldGiftsProvideMoreInfluence("Gifts of Gold to City-States generate [relativeAmount]% more Influence", UniqueTarget.Global),
    
    
    CityStateCanBeBoughtForGold("Can spend Gold to annex or puppet a City-State that has been your Ally for [nonNegativeAmount] turns", UniqueTarget.Global),
    CityStateTerritoryAlwaysFriendly("City-State territory always counts as friendly territory", UniqueTarget.Global),

    CityStateCanGiftGreatPeople("Allied City-States will occasionally gift Great People", UniqueTarget.Global),  // used in Policy
    CityStateInfluenceDegradation("[relativeAmount]% City-State Influence degradation", UniqueTarget.Global),
    CityStateRestingPoint("Resting point for Influence with City-States is increased by [amount]", UniqueTarget.Global),

    CityStateStatPercent("Allied City-States provide [stat] equal to [relativeAmount]% of what they produce for themselves", UniqueTarget.Global),
    CityStateResources("[relativeAmount]% resources gifted by City-States",  UniqueTarget.Global),
    CityStateLuxuryHappiness("[relativeAmount]% Happiness from luxury resources gifted by City-States", UniqueTarget.Global),
    CityStateInfluenceRecoversTwiceNormalRate("City-State Influence recovers at twice the normal rate", UniqueTarget.Global),

    // endregion

    /////// region Other global uniques

    /// Growth
    GrowthPercentBonus("[relativeAmount]% growth [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    CarryOverFood("[amount]% Food is carried over after population increases [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = ADDITIVE_BONUS_EXPLANATION),
    FoodConsumptionByPopulation("[relativeAmount]% Food consumption by [populationFilter] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("As of 4.19.10", ReplaceWith("[relativeAmount]% Food consumption by [Specialists] [cityFilter]"), DeprecationLevel.WARNING)
    FoodConsumptionBySpecialists("[relativeAmount]% Food consumption by specialists [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    /// Happiness
    UnhappinessFromCitiesPercentage("[relativeAmount]% unhappiness from the number of cities", UniqueTarget.Global),
    // Todo: capitalization of 'Unhappiness' -> 'unhappiness'
    UnhappinessFromPopulationTypePercentageChange("[relativeAmount]% Unhappiness from [populationFilter] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BonusHappinessFromLuxury("[amount] Happiness from each type of luxury resource", UniqueTarget.Global),
    // Todo: capitalization of 'happiness' -> 'Happiness'
    RetainHappinessFromLuxury("Retain [relativeAmount]% of the happiness from a luxury after the last copy has been traded away", UniqueTarget.Global),
    // Todo: capitalization of 'happiness' -> 'Happiness'
    ExcessHappinessToGlobalStat("[relativeAmount]% of excess happiness converted to [stat]", UniqueTarget.Global),

    /// Unit Production
    CannotBuildUnits("Cannot build [baseUnitFilter] units", UniqueTarget.Global),
    EnablesConstructionOfSpaceshipParts("Enables construction of Spaceship parts", UniqueTarget.Global),

    /// Buying units/buildings
    // There is potential to merge these
    
    BuyUnitsIncreasingCost("May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsIncreasingCost("May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter] at an increasing price ([amount])", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyUnitsForAmountStat("May buy [baseUnitFilter] units for [nonNegativeAmount] [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsForAmountStat("May buy [buildingFilter] buildings for [nonNegativeAmount] [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyUnitsWithStat("May buy [baseUnitFilter] units with [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsWithStat("May buy [buildingFilter] buildings with [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyUnitsByProductionCost("May buy [baseUnitFilter] units with [stat] for [nonNegativeAmount] times their normal Production cost", UniqueTarget.FollowerBelief, UniqueTarget.Global),
    BuyBuildingsByProductionCost("May buy [buildingFilter] buildings with [stat] for [nonNegativeAmount] times their normal Production cost", UniqueTarget.FollowerBelief, UniqueTarget.Global),
    BuyItemsDiscount("[stat] cost of purchasing items in cities [relativeAmount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),
    BuyBuildingsDiscount("[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),
    BuyUnitsDiscount("[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),

    /// Production to Stat conversion
    EnablesCivWideStatProduction("Enables conversion of city production to [civWideStat]", UniqueTarget.Global),
    ProductionToCivWideStatConversionBonus("Production to [civWideStat] conversion in cities changed by [relativeAmount]%", UniqueTarget.Global),

    /// Improvements
    // Should be replaced with moddable improvements when roads become moddable
    RoadMovementSpeed("Improves movement speed on roads",UniqueTarget.Global),
    RoadsConnectAcrossRivers("Roads connect tiles across rivers", UniqueTarget.Global),
    RoadMaintenance("[relativeAmount]% maintenance on road & railroads", UniqueTarget.Global,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),
    NoImprovementMaintenanceInSpecificTiles("No Maintenance costs for improvements in [tileFilter] tiles", UniqueTarget.Global),
    SpecificImprovementTime("[relativeAmount]% construction time for [improvementFilter] improvements", UniqueTarget.Global, UniqueTarget.Unit),
    ImprovementTimeIncrease("Can build [improvementFilter] improvements at a [relativeAmount]% rate", UniqueTarget.Global, UniqueTarget.Unit),

    /// Building Maintenance
    GainFreeBuildings("Gain a free [buildingName] [cityFilter]", UniqueTarget.Global, UniqueTarget.Triggerable,
        docDescription = "Free buildings CANNOT be self-removing - this leads to an endless loop of trying to add the building"),
    BuildingMaintenance("[relativeAmount]% maintenance cost for [buildingFilter] buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),
    RemoveBuilding("Remove [buildingFilter] [cityFilter]", UniqueTarget.Global, UniqueTarget.Triggerable),
    OneTimeSellBuilding("Sell [buildingFilter] buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.Triggerable),

    /// Border growth
    BorderGrowthPercentage("[relativeAmount]% Culture cost of natural border growth [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),
    TileCostPercentage("[relativeAmount]% Gold cost of acquiring tiles [cityFilter]", UniqueTarget.FollowerBelief, UniqueTarget.Global,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),

    /// Policy Cost
    LessPolicyCostFromCities("Each city founded increases culture cost of policies [relativeAmount]% less than normal", UniqueTarget.Global),
    LessPolicyCost("[relativeAmount]% Culture cost of adopting new Policies", UniqueTarget.Global,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),

    /// Tech Cost
    LessTechCostFromCities("Each city founded increases Science cost of Technologies [relativeAmount]% less than normal", UniqueTarget.Global),
    LessTechCost("[relativeAmount]% Science cost of researching new Technologies", UniqueTarget.Global,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),

    /// Natural Wonders
    StatsFromNaturalWonders("[stats] for every known Natural Wonder", UniqueTarget.Global),
    StatBonusWhenDiscoveringNaturalWonder("[stats] for discovering a Natural Wonder (bonus enhanced to [stats] if first to discover it)", UniqueTarget.Global),

    /// Great Persons
    GreatPersonPointPercentage("[relativeAmount]% Great Person generation [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    PercentGoldFromTradeMissions("[relativeAmount]% Gold from Great Merchant trade missions", UniqueTarget.Global, UniqueTarget.Unit),
    GreatGeneralProvidesDoubleCombatBonus("Great General provides double combat bonus", UniqueTarget.Unit, UniqueTarget.Global),
    // This should probably support conditionals, e.g. <after discovering [tech]>
    MayanGainGreatPerson("Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once.", UniqueTarget.Global),
    MayanCalendarDisplay("Once The Long Count activates, the year on the world screen displays as the traditional Mayan Long Count.", UniqueTarget.Global),

    /// Unit Maintenance & Supply
    BaseUnitSupply("[amount] Unit Supply", UniqueTarget.Global),
    UnitSupplyPerPop("[amount] Unit Supply per [positiveAmount] population [cityFilter]", UniqueTarget.Global),
    UnitSupplyPerCity("[amount] Unit Supply per city", UniqueTarget.Global),
    FreeUnits("[amount] units cost no maintenance", UniqueTarget.Global),
    UnitsInCitiesNoMaintenance("Units in cities cost no Maintenance", UniqueTarget.Global),

    // Units entering Tiles
    // ToDo: make per unit and use unit filters? "Enables embarkation <for [land] units>"
    LandUnitEmbarkation("Enables embarkation for land units", UniqueTarget.Global),
    UnitsMayEnterOcean("Enables [mapUnitFilter] units to enter ocean tiles", UniqueTarget.Global),
    LandUnitsCrossTerrainAfterUnitGained("Land units may cross [terrainName] tiles after the first [baseUnitFilter] is earned", UniqueTarget.Global),
    EnemyUnitsSpendExtraMovement("Enemy [mapUnitFilter] units must spend [positiveAmount] extra movement points when inside your territory", UniqueTarget.Global),

    /// Unit Abilities

    UnitStartingExperience("New [baseUnitFilter] units start with [amount] XP [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    UnitStartingPromotions("All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    // Todo: Lowercase the 'U' of 'Units' in this unique
    CityHealingUnits("[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    
    // change the XP cost for a relative amount %
    XPForPromotionModifier("[relativeAmount]% XP required for promotions",UniqueTarget.Global,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),

    /// City Strength
    BetterDefensiveBuildings("[relativeAmount]% City Strength from defensive buildings", UniqueTarget.Global,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),
    StrengthForCities("[relativeAmount]% Strength for cities", UniqueTarget.Global, UniqueTarget.FollowerBelief,
        docDescription = ADDITIVE_BONUS_EXPLANATION),

    /// Resource production & consumption
    ConsumesResources("Consumes [amount] [resource]", UniqueTarget.Improvement, UniqueTarget.Building, UniqueTarget.Unit),
    ProvidesResources("Provides [amount] [resource]", UniqueTarget.Global, UniqueTarget.Improvement, UniqueTarget.FollowerBelief),
    //todo should these two be merged to avoid the confusion?
    /** @see UnitActionStockpileCost */
    CostsResources("Costs [amount] [stockpiledResource]", UniqueTarget.Improvement, UniqueTarget.Building, UniqueTarget.Unit,
        docDescription = "These resources are removed *when work begins* on the construction. " +
                "Do not confuse with \"costs [amount] [stockpiledResource]\" (lowercase 'c'), the Unit Action Modifier.",
        flags = setOf(UniqueFlag.AcceptsSpeedModifier)),

    PercentResourceProduction("[relativeAmount]% [resourceFilter] resource production", UniqueTarget.Global),

    /// Diplomacy
    EnablesEmbassies("Enables establishment of embassies", UniqueTarget.Global),
    RequiresEmbassiesForDiplomacy("Requires establishing embassies to conduct advanced diplomacy", UniqueTarget.Global),

    /// Agreements
    EnablesOpenBorders("Enables Open Borders agreements", UniqueTarget.Global),
    // Should the 'R' in 'Research agreements' be capitalized?
    EnablesResearchAgreements("Enables Research agreements", UniqueTarget.Global),
    ScienceFromResearchAgreements("Science gained from research agreements [relativeAmount]%", UniqueTarget.Global),
    EnablesDefensivePacts("Enables Defensive Pacts", UniqueTarget.Global),
    GreatPersonBoostWithFriendship("When declaring friendship, both parties gain a [relativeAmount]% boost to great person generation", UniqueTarget.Global),

    /// City State Influence
    OtherCivsCityStateRelationsDegradeFaster("Influence of all other civilizations with all city-states degrades [relativeAmount]% faster", UniqueTarget.Global),
    GainInfluenceWithUnitGiftToCityState("Gain [amount] Influence with a [baseUnitFilter] gift to a City-State", UniqueTarget.Global),
    RestingPointOfCityStatesFollowingReligionChange("Resting point for Influence with City-States following this religion [amount]", UniqueTarget.Global),

    /// Barbarian Encampments, Pillaging them & Converting Units
    NotifiedOfBarbarianEncampments("Notified of new Barbarian encampments", UniqueTarget.Global),
    GoldFromEncampmentsAndCities("Receive [relativeAmount]% Gold from Barbarian encampments and pillaging Cities", UniqueTarget.Global),
    GainFromEncampment("When conquering an encampment, earn [amount] Gold and recruit a Barbarian unit", UniqueTarget.Global),
    GainFromDefeatingUnit("When defeating a [mapUnitFilter] unit, earn [amount] Gold and recruit it", UniqueTarget.Global),

    /// Religion
    DisablesReligion("Starting in this era disables religion", UniqueTarget.Era),
    FreeExtraBeliefs("May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion", UniqueTarget.Global),
    FreeExtraAnyBeliefs("May choose [amount] additional belief(s) of any type when [foundingOrEnhancing] a religion", UniqueTarget.Global),
    StatsWhenAdoptingReligion("[stats] when a city adopts this religion for the first time", UniqueTarget.Global, flags = setOf(UniqueFlag.AcceptsSpeedModifier)),
    NaturalReligionSpreadStrength("[relativeAmount]% Natural religion spread [cityFilter]", UniqueTarget.FollowerBelief, UniqueTarget.Global,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),
    ReligionSpreadDistance("Religion naturally spreads to cities [amount] tiles away", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    MayNotGenerateGreatProphet("May not generate great prophet equivalents naturally", UniqueTarget.Global),
    FaithCostOfGreatProphetChange("[relativeAmount]% Faith cost of generating Great Prophet equivalents", UniqueTarget.Global),

    /// Espionage
    SpyEffectiveness("[relativeAmount]% spy effectiveness [cityFilter]", UniqueTarget.Global),
    EnemySpyEffectiveness("[relativeAmount]% enemy spy effectiveness [cityFilter]", UniqueTarget.Global),
    SpyStartingLevel("New spies start with [amount] level(s)", UniqueTarget.Global),

    /// Things you get at the start of the game
    StartingTech("Starting tech", UniqueTarget.Tech),
    StartsWithTech("Starts with [tech]", UniqueTarget.Nation),
    StartsWithPolicy("Starts with [policy] adopted", UniqueTarget.Nation),

    /// Victory
    TriggersVictory("Triggers victory", UniqueTarget.Global),
    TriggersCulturalVictory("Triggers a Cultural Victory upon completion", UniqueTarget.Global),

    /// Misc.
    MayBuyConstructionsInPuppets("May buy items in puppet cities", UniqueTarget.Global),
    MayNotAnnexCities("May not annex cities", UniqueTarget.Global),
    BorrowsCityNames("\"Borrows\" city names from other civilizations in the game", UniqueTarget.Global),
    CitiesAreRazedXTimesFaster("Cities are razed [amount] times as fast", UniqueTarget.Global),

    TechBoostWhenScientificBuildingsBuiltInCapital("Receive a tech boost when scientific buildings/wonders are built in capital", UniqueTarget.Global),
    ResearchableMultipleTimes("Can be continually researched", UniqueTarget.Tech),

    GoldenAgeLength("[relativeAmount]% Golden Age length", UniqueTarget.Global,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),

    PopulationLossFromNukes("Population loss from nuclear attacks [relativeAmount]% [cityFilter]", UniqueTarget.Global),
    GarrisonDamageFromNukes("Damage to garrison from nuclear attacks [relativeAmount]% [cityFilter]", UniqueTarget.Global),

    SpawnRebels("Rebel units may spawn", UniqueTarget.Global),

    // endregion Other global uniques

    // endregion 01 Global uniques


    ///////////////////////////////////////// region 02 CONSTRUCTION UNIQUES /////////////////////////////////////////

    Unbuildable("Unbuildable", UniqueTarget.Building, UniqueTarget.Unit, UniqueTarget.Improvement,
        docDescription = "Blocks from being built, possibly by conditional. However it can still appear in the menu and be bought with other means such as Gold or Faith"),
    CannotBePurchased("Cannot be purchased", UniqueTarget.Building, UniqueTarget.Unit),
    CanBePurchasedWithStat("Can be purchased with [stat] [cityFilter]", UniqueTarget.Building, UniqueTarget.Unit),
    CanBePurchasedForAmountStat("Can be purchased for [amount] [stat] [cityFilter]", UniqueTarget.Building, UniqueTarget.Unit),
    MaxNumberBuildable("Limited to [amount] per Civilization", UniqueTarget.Building, UniqueTarget.Unit),
    
    /** A special unique, as it only activates [RejectionReasonType] when it has conditionals that *do not* apply.
     * Meant to be used together with conditionals, like `"Only available <after adopting [Piety]> <while the empire is happy>"`.
     * Restricts Upgrade/Transform pathways.
     * @See [CanOnlyBeBuiltWhen]
     */
    OnlyAvailable("Only available", UniqueTarget.Unit, UniqueTarget.Building, UniqueTarget.Improvement,
        UniqueTarget.Policy, UniqueTarget.Tech, UniqueTarget.Promotion, UniqueTarget.Ruins,
        UniqueTarget.FollowerBelief, UniqueTarget.FounderBelief, UniqueTarget.Event, UniqueTarget.EventChoice,
        docDescription = "Meant to be used together with conditionals, like \"Only available <after adopting [policy]> <while the empire is happy>\". Only allows Building when ALL conditionals are met. Will also block Upgrade and Transform actions. See also CanOnlyBeBuiltWhen"),
    Unavailable("Unavailable", UniqueTarget.Unit, UniqueTarget.Building, UniqueTarget.Improvement,
        UniqueTarget.Policy, UniqueTarget.Tech, UniqueTarget.Promotion, UniqueTarget.Ruins,
        UniqueTarget.FollowerBelief, UniqueTarget.FounderBelief, UniqueTarget.Event, UniqueTarget.EventChoice,
        docDescription = "Meant to be used together with conditionals, like \"Unavailable <after generating a Great Prophet>\"."),
    CannotBuildBuildings("Cannot build [buildingFilter] buildings", UniqueTarget.Global),
    ConvertFoodToProductionWhenConstructed("Excess Food converted to Production when under construction", UniqueTarget.Building, UniqueTarget.Unit),
    RequiresPopulation("Requires at least [amount] population", UniqueTarget.Building, UniqueTarget.Unit),

    TriggersAlertOnStart("Triggers a global alert upon build start", UniqueTarget.Building, UniqueTarget.Unit),
    TriggersAlertOnCompletion("Triggers a global alert upon completion", UniqueTarget.Building, UniqueTarget.Unit),
    //endregion

    ///////////////////////////////////////// region 03 BUILDING UNIQUES /////////////////////////////////////////


    CostIncreasesPerCity("Cost increases by [amount] per owned city", UniqueTarget.Building, UniqueTarget.Unit),
    CostIncreasesWhenBuilt("Cost increases by [amount] when built", UniqueTarget.Building, UniqueTarget.Unit),
    CostPercentageChange("[amount]% production cost", UniqueTarget.Building, UniqueTarget.Unit,
        docDescription = "Intended to be used with conditionals to dynamically alter construction costs. $MULTIPLICATIVE_BONUS_EXPLANATION"),

    /** Triggers [RejectionReasonType] when any conditional does NOT apply.
     * Doesn't restrict Upgrade/Transform pathways.
     * @see [OnlyAvailable]
     */
    CanOnlyBeBuiltWhen("Can only be built", UniqueTarget.Building, UniqueTarget.Unit,
        docDescription = "Meant to be used together with conditionals, like \"Can only be built <after adopting [policy]> <while the empire is happy>\". Only allows Building when ALL conditionals are met. Will also NOT block Upgrade and Transform actions. See also OnlyAvailable."),

    MustHaveOwnedWithinTiles("Must have an owned [tileFilter] within [amount] tiles", UniqueTarget.Building),


    // Todo nuclear weapon and spaceship enabling requires a rethink.
    // This doesn't actually directly affect anything, the "Only available <if [Manhattan Project] is constructed>" of the nuclear weapons does that.
    EnablesNuclearWeapons("Enables nuclear weapon", UniqueTarget.Building),

    MustBeOn("Must be on [tileFilter]", UniqueTarget.Building),
    MustNotBeOn("Must not be on [tileFilter]", UniqueTarget.Building),
    MustBeNextTo("Must be next to [tileFilter]", UniqueTarget.Building, UniqueTarget.Improvement),
    MustNotBeNextTo("Must not be next to [tileFilter]", UniqueTarget.Building),

    Unsellable("Unsellable", UniqueTarget.Building),
    ObsoleteWith("Obsolete with [tech]", UniqueTarget.Building, UniqueTarget.Resource, UniqueTarget.Improvement),
    IndicatesCapital("Indicates the capital city", UniqueTarget.Building),
    MovesToNewCapital("Moves to new capital when capital changes", UniqueTarget.Building),
    ProvidesExtraLuxuryFromCityResources("Provides 1 extra copy of each improved luxury resource near this City", UniqueTarget.Building),

    DestroyedWhenCityCaptured("Destroyed when the city is captured", UniqueTarget.Building),
    NotDestroyedWhenCityCaptured("Never destroyed when the city is captured", UniqueTarget.Building),
    GoldFromCapturingCity("[relativeAmount]% Gold given to enemy if city is captured", UniqueTarget.Building),

    RemovesAnnexUnhappiness("Removes extra unhappiness from annexed cities", UniqueTarget.Building),
    ConnectTradeRoutes("Connects trade routes over water", UniqueTarget.Building),
    GainBuildingWhereBuildable("Automatically built in all cities where it is buildable", UniqueTarget.Building),

    CreatesOneImprovement("Creates a [improvementName] improvement on a specific tile", UniqueTarget.Building,
        docDescription = "When choosing to construct this building, the player must select a tile where the improvement can be built." +
                " Upon building completion, the tile will gain this improvement." + 
                " Limited to one per building.",
        flags = UniqueFlag.setOfNoConditionals
        ),
    //endregion

    ///////////////////////////////////////// region 04 UNIT UNIQUES /////////////////////////////////////////

    // Unit action uniques
    // Unit actions should look like: "Can {action description}, to allow them to be combined with modifiers

    FoundCity("Founds a new city", UniqueTarget.UnitAction),
    FoundPuppetCity("Founds a new puppet city", UniqueTarget.UnitAction),
    ConstructImprovementInstantly("Can instantly construct a [improvementFilter] improvement", UniqueTarget.UnitAction),
    // TODO: Should be replaced by "Can instantly construct a [] improvement <by consuming this unit>"
    CreateWaterImprovements("May create improvements on water resources", UniqueTarget.Unit),
    BuildImprovements("Can build [improvementFilter/terrainFilter] improvements on tiles", UniqueTarget.Unit),
    CanSpreadReligion("Can Spread Religion", UniqueTarget.UnitAction),
    CanRemoveHeresy("Can remove other religions from cities", UniqueTarget.UnitAction),
    MayFoundReligion("May found a religion", UniqueTarget.UnitAction),
    MayEnhanceReligion("May enhance a religion", UniqueTarget.UnitAction),

    AddInCapital("Can be added to [comment] in the Capital", UniqueTarget.Unit),
    PreventSpreadingReligion("Prevents spreading of religion to the city it is next to", UniqueTarget.Unit),
    RemoveOtherReligions("Removes other religions when spreading religion", UniqueTarget.Unit),

    MayParadrop("May Paradrop to [tileFilter] tiles up to [positiveAmount] tiles away", UniqueTarget.Unit),
    CanAirsweep("Can perform Air Sweep", UniqueTarget.Unit),

    CanSpeedupConstruction("Can speed up construction of a building", UniqueTarget.Unit),
    CanSpeedupWonderConstruction("Can speed up the construction of a wonder", UniqueTarget.Unit),
    CanHurryResearch("Can hurry technology research", UniqueTarget.Unit),
    CanHurryPolicy("Can generate a large amount of culture", UniqueTarget.Unit),
    CanTradeWithCityStateForGoldAndInfluence("Can undertake a trade mission with City-State, giving a large sum of gold and [amount] Influence", UniqueTarget.Unit),
    CanTransform("Can transform to [unit]", UniqueTarget.UnitAction,
        docDescription = "By default consumes all movement"),

    AutomationPrimaryAction("Automation is a primary action", UniqueTarget.Unit, flags = UniqueFlag.setOfHiddenToUsers),

    // Strength bonuses
    Strength("[relativeAmount]% Strength", UniqueTarget.Unit, UniqueTarget.Global,
        docDescription = ADDITIVE_BONUS_EXPLANATION),
    StrengthAmount("[relativeAmount] Strength", UniqueTarget.Unit, UniqueTarget.Global),
    StrengthNearCapital("[relativeAmount]% Strength decreasing with distance from the capital", UniqueTarget.Unit, UniqueTarget.Global),
    FlankAttackBonus("[relativeAmount]% to Flank Attack bonuses", UniqueTarget.Unit, UniqueTarget.Global,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),
    StrengthForAdjacentEnemies("[relativeAmount]% Strength for enemy [mapUnitFilter] units in adjacent [tileFilter] tiles", UniqueTarget.Unit),
    StrengthBonusInRadius("[relativeAmount]% Strength bonus for [mapUnitFilter] units within [amount] tiles", UniqueTarget.Unit),

    // Stat bonuses
    AdditionalAttacks("[amount] additional attacks per turn", UniqueTarget.Unit, UniqueTarget.Global),
    Movement("[amount] Movement", UniqueTarget.Unit, UniqueTarget.Global),
    Sight("[amount] Sight", UniqueTarget.Unit, UniqueTarget.Global, UniqueTarget.Terrain, UniqueTarget.Improvement),
    Range("[amount] Range", UniqueTarget.Unit, UniqueTarget.Global),
    AirInterceptionRange("[relativeAmount] Air Interception Range", UniqueTarget.Unit, UniqueTarget.Global),
    Heal("[amount] HP when healing", UniqueTarget.Unit, UniqueTarget.Global),

    SpreadReligionStrength("[relativeAmount]% Spread Religion Strength", UniqueTarget.Unit, UniqueTarget.Global,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),
    StatsWhenSpreading("When spreading religion to a city, gain [amount] times the amount of followers of other religions as [stat]", UniqueTarget.Unit, UniqueTarget.Global),

    // Attack restrictions
    CanOnlyAttackUnits("Can only attack [combatantFilter] units", UniqueTarget.Unit),
    CanOnlyAttackTiles("Can only attack [tileFilter] tiles", UniqueTarget.Unit),
    CannotAttack("Cannot attack", UniqueTarget.Unit),
    MustSetUp("Must set up to ranged attack", UniqueTarget.Unit),
    SelfDestructs("Self-destructs when attacking", UniqueTarget.Unit),

    // Attack unrestrictions
    AttackAcrossCoast("Eliminates combat penalty for attacking across a coast", UniqueTarget.Unit),
    AttackOnSea("May attack when embarked", UniqueTarget.Unit),
    AttackAcrossRiver("Eliminates combat penalty for attacking over a river", UniqueTarget.Unit),

    // Missiles
    BlastRadius("Blast radius [amount]", UniqueTarget.Unit),
    IndirectFire("Ranged attacks may be performed over obstacles", UniqueTarget.Unit, UniqueTarget.Global),
    NuclearWeapon("Nuclear weapon of Strength [amount]", UniqueTarget.Unit),

    NoDefensiveTerrainBonus("No defensive terrain bonus", UniqueTarget.Unit, UniqueTarget.Global),
    NoDefensiveTerrainPenalty("No defensive terrain penalty", UniqueTarget.Unit, UniqueTarget.Global),
    NoDamagePenaltyWoundedUnits("No damage penalty for wounded units", UniqueTarget.Unit, UniqueTarget.Global),
    Uncapturable("Uncapturable", UniqueTarget.Unit),
    WithdrawsBeforeMeleeCombat("Withdraws before melee combat", UniqueTarget.Unit),
    CannotCaptureCities("Unable to capture cities", UniqueTarget.Unit, UniqueTarget.Global),
    CannotPillage("Unable to pillage tiles", UniqueTarget.Unit, UniqueTarget.Global),
    
    // allow any unit to destory cities instead of capturing them, also allows non melee units to destroy cities
    CanDestroyCities("Destroys [cityFilter] cities instead of capturing", UniqueTarget.Unit,
        docDescription = "The unit will destroy [cityFilter] cities instead of capturing them, also allows non-melee units to destroy cities." + "Capital cities (including city states) are immune to this effect."),

    // Movement
    NoMovementToPillage("No movement cost to pillage", UniqueTarget.Unit, UniqueTarget.Global),
    CanMoveAfterAttacking("Can move after attacking", UniqueTarget.Unit),
    TransferMovement("Transfer Movement to [mapUnitFilter]", UniqueTarget.Unit),
    CanMoveImmediatelyOnceBought("Can move immediately once bought", UniqueTarget.Unit),

    // Healing
    HealsOutsideFriendlyTerritory("May heal outside of friendly territory", UniqueTarget.Unit, UniqueTarget.Global),
    HealingEffectsDoubled("All healing effects doubled", UniqueTarget.Unit, UniqueTarget.Global),
    HealsAfterKilling("Heals [amount] damage if it kills a unit", UniqueTarget.Unit, UniqueTarget.Global),
    HealOnlyByPillaging("Can only heal by pillaging", UniqueTarget.Unit, UniqueTarget.Global),
    HealsEvenAfterAction("Unit will heal every turn, even if it performs an action", UniqueTarget.Unit),
    HealAdjacentUnits("All adjacent units heal [amount] HP when healing", UniqueTarget.Unit),

    // Vision
    NoSight("No Sight", UniqueTarget.Unit),
    CanSeeOverObstacles("Can see over obstacles", UniqueTarget.Unit),

    // Carrying
    CarryAirUnits("Can carry [amount] [mapUnitFilter] units", UniqueTarget.Unit),
    CarryExtraAirUnits("Can carry [amount] extra [mapUnitFilter] units", UniqueTarget.Unit, UniqueTarget.Building,
        docDescription = "For buildings, supports using `Air` for `mapUnitFilter` to increase city air unit capacity."),
    CannotBeCarriedBy("Cannot be carried by [mapUnitFilter] units", UniqueTarget.Unit),
    // Interception
    ChanceInterceptAirAttacks("[relativeAmount]% chance to intercept air attacks", UniqueTarget.Unit),
    DamageFromInterceptionReduced("Damage taken from interception reduced by [relativeAmount]%", UniqueTarget.Unit),
    DamageWhenIntercepting("[relativeAmount]% Damage when intercepting", UniqueTarget.Unit),
    ExtraInterceptionsPerTurn("[amount] extra interceptions may be made per turn", UniqueTarget.Unit),
    CannotBeIntercepted("Cannot be intercepted", UniqueTarget.Unit),
    CannotInterceptUnits("Cannot intercept [mapUnitFilter] units", UniqueTarget.Unit),
    StrengthWhenAirsweep("[relativeAmount]% Strength when performing Air Sweep", UniqueTarget.Unit),

    UnitMaintenanceDiscount("[relativeAmount]% maintenance costs", UniqueTarget.Unit, UniqueTarget.Global,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),
    UnitUpgradeCost("[relativeAmount]% Gold cost of upgrading", UniqueTarget.Unit, UniqueTarget.Global,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),

    // Gains from battle
    DamageUnitsPlunder("Earn [amount]% of the damage done to [combatantFilter] units as [stockpile]", UniqueTarget.Unit, UniqueTarget.Global),
    CaptureCityPlunder("Upon capturing a city, receive [amount] times its [stat] production as [stockpile] immediately", UniqueTarget.Unit, UniqueTarget.Global),
    KillUnitPlunder("Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [stockpile]", UniqueTarget.Unit, UniqueTarget.Global),
    KillUnitPlunderNearCity("Earn [amount]% of [mapUnitFilter] unit's [costOrStrength] as [stockpile] when killed within 4 tiles of a city following this religion", UniqueTarget.FollowerBelief),
    KillUnitCapture("May capture killed [mapUnitFilter] units", UniqueTarget.Unit),

    // XP
    FlatXPGain("[amount] XP gained from combat", UniqueTarget.Unit, UniqueTarget.Global),
    PercentageXPGain("[relativeAmount]% XP gained from combat", UniqueTarget.Unit, UniqueTarget.Global,
        docDescription = MULTIPLICATIVE_BONUS_EXPLANATION),
    GreatPersonFromCombat("Can be earned through combat", UniqueTarget.Unit),
    GreatPersonEarnedFaster("[greatPerson] is earned [relativeAmount]% faster", UniqueTarget.Unit, UniqueTarget.Global),

    // Invisibility
    Invisible("Invisible to others", UniqueTarget.Unit),
    InvisibleToNonAdjacent("Invisible to non-adjacent units", UniqueTarget.Unit),
    CanSeeInvisibleUnits("Can see invisible [mapUnitFilter] units", UniqueTarget.Unit),

    RuinsUpgrade("May upgrade to [unit] through ruins-like effects", UniqueTarget.Unit),
    CanUpgrade("Can upgrade to [unit]", UniqueTarget.Unit),

    DestroysImprovementUponAttack("Destroys tile improvements when attacking", UniqueTarget.Unit),

    // Movement - The following block gets cached in MapUnit for faster getMovementCostBetweenAdjacentTiles
    CannotMove("Cannot move", UniqueTarget.Unit),
    DoubleMovementOnTerrain("Double movement in [terrainFilter]", UniqueTarget.Unit),
    AllTilesCost1Move("All tiles cost 1 movement", UniqueTarget.Unit),
    CanMoveOnWater("May travel on Water tiles without embarking", UniqueTarget.Unit),
    CanPassImpassable("Can pass through impassable tiles", UniqueTarget.Unit),
    IgnoresTerrainCost("Ignores terrain cost", UniqueTarget.Unit),
    IgnoresZOC("Ignores Zone of Control", UniqueTarget.Unit),
    RoughTerrainPenalty("Rough terrain penalty", UniqueTarget.Unit),
    CanEnterIceTiles("Can enter ice tiles", UniqueTarget.Unit),
    CannotEmbark("Cannot embark", UniqueTarget.Unit),
    CannotEnterOcean("Cannot enter ocean tiles", UniqueTarget.Unit),
    CanEnterForeignTiles("May enter foreign tiles without open borders", UniqueTarget.Unit),
    CanEnterForeignTilesButLosesReligiousStrength("May enter foreign tiles without open borders, but loses [amount] religious strength each turn it ends there", UniqueTarget.Unit),
    ReducedDisembarkCost("[nonNegativeAmount] Movement point cost to disembark", UniqueTarget.Global, UniqueTarget.Unit),
    ReducedEmbarkCost("[nonNegativeAmount] Movement point cost to embark", UniqueTarget.Global, UniqueTarget.Unit),
    // These affect movement as Nation uniques
    ForestsAndJunglesAreRoads("All units move through Forest and Jungle Tiles in friendly territory as if they have roads. These tiles can be used to establish City Connections upon researching the Wheel.", UniqueTarget.Nation),
    IgnoreHillMovementCost("Units ignore terrain costs when moving into any tile with Hills", UniqueTarget.Nation),

    CannotBeBarbarian("Never appears as a Barbarian unit", UniqueTarget.Unit, flags = UniqueFlag.setOfHiddenToUsers),

    ReligiousUnit("Religious Unit", UniqueTarget.Unit),
    SpaceshipPart("Spaceship part", UniqueTarget.Unit, UniqueTarget.Building), // Should be deprecated in the near future
    TakeReligionOverBirthCity("Takes your religion over the one in their birth city", UniqueTarget.Unit),

    // Hurried means: sped up using great engineer/scientist ability, so this is in some sense a unit unique that should be here
    CannotBeHurried("Cannot be hurried", UniqueTarget.Building, UniqueTarget.Tech),
    GreatPerson("Great Person - [comment]", UniqueTarget.Unit),
    GPPointPool("Is part of Great Person group [comment]", UniqueTarget.Unit,
        docDescription = "Great people in the same group increase teach other's costs when gained. Gaining one will make all others in the same group cost more GPP."),

    //endregion

    ///////////////////////////////////////// region 05 UNIT ACTION MODIFIERS /////////////////////////////////////////

    UnitActionConsumeUnit("by consuming this unit", UniqueTarget.UnitActionModifier),
    UnitActionMovementCost("for [amount] movement", UniqueTarget.UnitActionModifier,
        docDescription = "Will consume up to [amount] of Movement to execute"),
    UnitActionMovementCostAll("for all movement", UniqueTarget.UnitActionModifier,
        docDescription = "Will consume all Movement to execute"),
    UnitActionMovementCostRequired("requires [nonNegativeAmount] movement", UniqueTarget.UnitActionModifier,
        docDescription = "Requires [nonNegativeAmount] of Movement to execute. Unit's Movement is rounded up"),
    UnitActionStatsCost("costs [stats] stats", UniqueTarget.UnitActionModifier,
        docDescription = "A positive Integer value will be subtracted from your stock. Food and Production will be removed from Closest City's current stock"),
    /** @see CostsResources */
    UnitActionStockpileCost("costs [amount] [stockpiledResource]", UniqueTarget.UnitActionModifier,
        docDescription = "A positive Integer value will be subtracted from your stock. Do not confuse with \"Costs [amount] [stockpiledResource]\" (uppercase 'C') for Improvements, Buildings, and Units."),
    UnitActionRemovingPromotion("removing the [promotion] promotion/status", UniqueTarget.UnitActionModifier,
        docDescription = "Removes the promotion/status from the unit -" +
                " this is not a cost, units will be able to activate the action even without the promotion/status. " +
                "To limit, use <with the [promotion] promotion> conditional"),
    UnitActionOnce("once", UniqueTarget.UnitActionModifier),
    UnitActionLimitedTimes("[positiveAmount] times", UniqueTarget.UnitActionModifier),
    UnitActionExtraLimitedTimes("[nonNegativeAmount] additional time(s)", UniqueTarget.UnitActionModifier),
    UnitActionAfterWhichConsumed("after which this unit is consumed", UniqueTarget.UnitActionModifier),

    // endregion

    ///////////////////////////////////////// region 06 TILE UNIQUES /////////////////////////////////////////

    // Natural wonders
    NaturalWonderNeighborCount("Must be adjacent to [amount] [simpleTerrain] tiles", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    NaturalWonderNeighborsRange("Must be adjacent to [amount] to [amount] [simpleTerrain] tiles", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    NaturalWonderSmallerLandmass("Must not be on [amount] largest landmasses", UniqueTarget.Terrain, UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),
    NaturalWonderLargerLandmass("Must be on [amount] largest landmasses", UniqueTarget.Terrain, UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),
    NaturalWonderLatitude("Occurs on latitudes from [amount] to [amount] percent of distance equator to pole", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    NaturalWonderGroups("Occurs in groups of [amount] to [amount] tiles", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    NaturalWonderConvertNeighbors("Neighboring tiles will convert to [baseTerrain/terrainFeature]", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers,
        docDescription = "Supports conditionals that need only a Tile as context and nothing else, like `<with [n]% chance>`, and applies them per neighbor." +
            "\nIf your mod renames Coast or Lakes, do not use this with one of these as parameter, as the code preventing artifacts won't work."),
    GrantsStatsToFirstToDiscover("Grants [stats] to the first civilization to discover it", UniqueTarget.Terrain),

    // General terrain
    DamagesContainingUnits("Units ending their turn on this terrain take [amount] damage", UniqueTarget.Terrain, flags = UniqueFlag.setOfNoConditionals),
    TerrainGrantsPromotion("Grants [promotion] ([comment]) to adjacent [mapUnitFilter] units for the rest of the game", UniqueTarget.Terrain),
    GrantsCityStrength("[amount] Strength for cities built on this terrain", UniqueTarget.Terrain),
    ProductionBonusWhenRemoved("Provides a one-time bonus of [stats] to the closest city when cut down", UniqueTarget.Terrain, flags = setOf(UniqueFlag.AcceptsSpeedModifier, UniqueFlag.AcceptsGameProgressModifier)),
    Vegetation("Vegetation", UniqueTarget.Terrain, UniqueTarget.Improvement, flags = UniqueFlag.setOfHiddenToUsers),  // Improvement included because use as tileFilter works


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
    TileGenerationConditions("Occurs at temperature between [fraction] and [fraction] and humidity between [fraction] and [fraction]",
        UniqueTarget.Terrain, UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),
    OccursInChains("Occurs in chains at high elevations", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    OccursInGroups("Occurs in groups around high elevations", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    MajorStrategicFrequency("Every [amount] tiles with this terrain will receive a major deposit of a strategic resource.", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),

    RareFeature("Rare feature", UniqueTarget.Terrain),

    DestroyableByNukesChance("[amount]% Chance to be destroyed by nukes", UniqueTarget.Terrain),

    FreshWater(Constants.freshWater, UniqueTarget.Terrain),
    RoughTerrain("Rough terrain", UniqueTarget.Terrain),
    CoastalWater("Coastal Water", UniqueTarget.Terrain),

    ExcludedFromMapEditor("Excluded from map editor", UniqueTarget.Terrain, UniqueTarget.Improvement, UniqueTarget.Resource, UniqueTarget.Nation, flags = UniqueFlag.setOfHiddenToUsers),

    /////// Resource uniques
    ResourceAmountOnTiles("Deposits in [tileFilter] tiles always provide [amount] resources", UniqueTarget.Resource),
    CityStateOnlyResource("Can only be created by Mercantile City-States", UniqueTarget.Resource),
    Stockpiled("Stockpiled", UniqueTarget.Resource,
        docDescription = "This resource is accumulated each turn, rather than having a set of producers and consumers at a given moment." +
                "The current stockpiled amount can be affected with trigger uniques."),
    CityResource("City-level resource", UniqueTarget.Resource, docDescription = "This resource is calculated on a per-city level rather than a per-civ level"),
    CannotBeTraded("Cannot be traded", UniqueTarget.Resource),
    NotShownOnWorldScreen("Not shown on world screen", UniqueTarget.Resource, UniqueTarget.Promotion, flags = UniqueFlag.setOfHiddenToUsers),

    ResourceWeighting("Generated with weight [amount]", UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers,
        docDescription = "The probability for this resource to be chosen is (this resource weight) / (sum weight of all eligible resources). " +
                "Resources without a unique are given weight `1`"),
    MinorDepositWeighting("Minor deposits generated with weight [amount]", UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers,
        docDescription = "The probability for this resource to be chosen is (this resource weight) / (sum weight of all eligible resources). " +
                "Resources without a unique are not generated as minor deposits."),
    LuxuryWeightingForCityStates("Generated near City States with weight [amount]", UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers,
        docDescription = "The probability for this resource to be chosen is (this resource weight) / (sum weight of all eligible resources). " +
                "Only assignable to luxuries, resources without a unique are given weight `1`"),
    LuxurySpecialPlacement("Special placement during map generation", UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),
    ResourceFrequency("Generated on every [amount] tiles", UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),
    StrategicBalanceResource("Guaranteed with Strategic Balance resource option", UniqueTarget.Resource),
    AiWillSellAt("AI will sell at [amount] Gold", UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),
    AiWillBuyAt("AI will buy at [amount] Gold", UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),

    ////// Improvement uniques
    ImprovementBuildableByFreshWater("Can also be built on tiles adjacent to fresh water", UniqueTarget.Improvement),
    ImprovementStatsOnTile("[stats] from [tileFilter] tiles", UniqueTarget.Improvement),
    ImprovementStatsForAdjacencies("[stats] for each adjacent [tileFilter]", UniqueTarget.Improvement),
    EnsureMinimumStats("Ensures a minimum tile yield of [stats]", UniqueTarget.Improvement), // City center

    CanBuildOutsideBorders("Can be built outside your borders", UniqueTarget.Improvement),
    CanBuildJustOutsideBorders("Can be built just outside your borders", UniqueTarget.Improvement),
    CanOnlyBeBuiltOnTile("Can only be built on [tileFilter] tiles", UniqueTarget.Improvement),
    CannotBuildOnTile("Cannot be built on [tileFilter] tiles", UniqueTarget.Improvement),
    CanOnlyImproveResource("Can only be built to improve a resource", UniqueTarget.Improvement),
    NoFeatureRemovalNeeded("Does not need removal of [terrainFeature]", UniqueTarget.Improvement),
    RemovesFeaturesIfBuilt("Removes removable features when built", UniqueTarget.Improvement),

    DefensiveBonus("Gives a defensive bonus of [relativeAmount]%", UniqueTarget.Improvement, 
        docDescription = "Does not accept unit-based conditionals"),
    ImprovementMaintenance("Costs [amount] [stat] per turn when in your territory", UniqueTarget.Improvement), // Roads
    ImprovementAllMaintenance("Costs [amount] [stat] per turn", UniqueTarget.Improvement), // Roads
    DamagesAdjacentEnemyUnits("Adjacent enemy units ending their turn take [amount] damage", UniqueTarget.Improvement),

    GreatImprovement("Great Improvement", UniqueTarget.Improvement),
    IsAncientRuinsEquivalent("Provides a random bonus when entered", UniqueTarget.Improvement),

    Unpillagable("Unpillagable", UniqueTarget.Improvement),
    PillageYieldRandom("Pillaging this improvement yields approximately [stats]", UniqueTarget.Improvement, flags = setOf(UniqueFlag.AcceptsSpeedModifier, UniqueFlag.AcceptsGameProgressModifier)),
    PillageYieldFixed("Pillaging this improvement yields [stats]", UniqueTarget.Improvement, flags = setOf(UniqueFlag.AcceptsSpeedModifier, UniqueFlag.AcceptsGameProgressModifier)),
    DestroyedWhenPillaged("Destroyed when pillaged", UniqueTarget.Improvement),
    Irremovable("Irremovable", UniqueTarget.Improvement),
    AutomatedUnitsWillNotReplace("Will not be replaced by automated units", UniqueTarget.Improvement),
    ImprovesResources("Improves [resourceFilter] resource in this tile", UniqueTarget.Improvement, flags = UniqueFlag.setOfNoConditionals,
        docDescription = "This is offered as an alternative to the improvedBy field of a resource." +
            " The result will be cached within the resource definition when loading a game, without knowledge about terrain, cities, civs, units or time." +
            " Therefore, most conditionals will not work, only those **not** dependent on game state."),
    //endregion

    /////////////////////////////////// region 07 PERSONALITY UNIQUES ////////////////////////////////////////

    WillNotBuild("Will not build [baseUnitFilter/buildingFilter]", UniqueTarget.Personality),
    //endregion

    ///////////////////////////////////////// region 08 CONDITIONALS /////////////////////////////////////////


    /////// game conditionals
    ConditionalEveryTurns("every [positiveAmount] turns", UniqueTarget.Conditional),
    ConditionalBeforeTurns("before turn number [nonNegativeAmount]", UniqueTarget.Conditional),
    ConditionalAfterTurns("after turn number [nonNegativeAmount]", UniqueTarget.Conditional),
    ConditionalSpeed("on [speed] game speed", UniqueTarget.Conditional),
    ConditionalDifficulty("on [difficulty] difficulty", UniqueTarget.Conditional),
    ConditionalDifficultyOrHigher("on [difficulty] difficulty or higher", UniqueTarget.Conditional),
    ConditionalDifficultyOrLower("on [difficulty] difficulty or lower", UniqueTarget.Conditional),
    ConditionalVictoryEnabled("when [victoryType] Victory is enabled", UniqueTarget.Conditional),
    ConditionalVictoryDisabled("when [victoryType] Victory is disabled", UniqueTarget.Conditional),
    ConditionalReligionEnabled("when religion is enabled", UniqueTarget.Conditional),
    ConditionalReligionDisabled("when religion is disabled", UniqueTarget.Conditional),
    ConditionalEspionageEnabled("when espionage is enabled", UniqueTarget.Conditional),
    ConditionalEspionageDisabled("when espionage is disabled", UniqueTarget.Conditional),
    ConditionalNuclearWeaponsEnabled("when nuclear weapons are enabled", UniqueTarget.Conditional),
    ConditionalNuclearWeaponsDisabled("when nuclear weapons are disabled", UniqueTarget.Conditional),

    /////// general conditionals
    ConditionalChance("with [nonNegativeAmount]% chance", UniqueTarget.Conditional),
    ConditionalTutorialsEnabled("if tutorials are enabled", UniqueTarget.Conditional, flags = UniqueFlag.setOfHiddenToUsers), // Hidden as no translations needed for now
    ConditionalTutorialCompleted("if tutorial [comment] is completed", UniqueTarget.Conditional, flags = UniqueFlag.setOfHiddenToUsers), // Hidden as no translations needed for now

    /////// civ conditionals
    ConditionalCivFilter("for [civFilter] Civilizations", UniqueTarget.Conditional),
    ConditionalWar("when at war", UniqueTarget.Conditional),
    ConditionalNotWar("when not at war", UniqueTarget.Conditional),
    ConditionalGoldenAge("during a Golden Age", UniqueTarget.Conditional),
    ConditionalNotGoldenAge("when not in a Golden Age", UniqueTarget.Conditional),
    ConditionalWLTKD("during We Love The King Day", UniqueTarget.Conditional),

    ConditionalHappy("while the empire is happy", UniqueTarget.Conditional),
    
    ConditionalDuringEra("during the [era]", UniqueTarget.Conditional),
    ConditionalBeforeEra("before the [era]", UniqueTarget.Conditional),
    ConditionalStartingFromEra("starting from the [era]", UniqueTarget.Conditional),
    ConditionalIfStartingInEra("if starting in the [era]", UniqueTarget.Conditional),

    ConditionalFirstCivToResearch("if no other Civilization has researched this", UniqueTarget.Conditional),
    ConditionalTech("after discovering [techFilter]", UniqueTarget.Conditional),
    ConditionalNoTech("before discovering [techFilter]", UniqueTarget.Conditional),
    ConditionalWhileResearching("while researching [techFilter]", UniqueTarget.Conditional,
        docDescription = "This condition is fulfilled while the technology is actively being researched (it is the one research points are added to)"),

    ConditionalFirstCivToAdopt("if no other Civilization has adopted this", UniqueTarget.Conditional),
    ConditionalNoCivAdopted("if no Civilization has adopted [policy/belief]", UniqueTarget.Conditional),
    ConditionalAfterPolicyOrBelief("after adopting [policy/belief]", UniqueTarget.Conditional),
    ConditionalBeforePolicyOrBelief("before adopting [policy/belief]", UniqueTarget.Conditional),

    ConditionalBeforePantheon("before founding a Pantheon", UniqueTarget.Conditional),
    ConditionalAfterPantheon("after founding a Pantheon", UniqueTarget.Conditional),
    ConditionalBeforeReligion("before founding a religion", UniqueTarget.Conditional),
    ConditionalAfterReligion("after founding a religion", UniqueTarget.Conditional),
    ConditionalBeforeEnhancingReligion("before enhancing a religion", UniqueTarget.Conditional),
    ConditionalAfterEnhancingReligion("after enhancing a religion", UniqueTarget.Conditional),
    ConditionalAfterGeneratingGreatProphet("after generating a Great Prophet", UniqueTarget.Conditional),

    ConditionalBuildingBuilt("if [buildingFilter] is constructed", UniqueTarget.Conditional),
    ConditionalBuildingNotBuilt("if [buildingFilter] is not constructed", UniqueTarget.Conditional),
    ConditionalBuildingBuiltAll("if [buildingFilter] is constructed in all [cityFilter] cities", UniqueTarget.Conditional),
    ConditionalBuildingBuiltAmount("if [buildingFilter] is constructed in at least [positiveAmount] of [cityFilter] cities", UniqueTarget.Conditional),
    ConditionalBuildingBuiltByAnybody("if [buildingFilter] is constructed by anybody", UniqueTarget.Conditional),
    ConditionalBuildingNotBuiltByAnybody("if [buildingFilter] is not constructed by anybody", UniqueTarget.Conditional),

    ConditionalWithResource("with [resource]", UniqueTarget.Conditional),
    ConditionalWithoutResource("without [resource]", UniqueTarget.Conditional),

    // Supports also stockpileable resources (Gold, Faith, Culture, Science)
    ConditionalWhenAboveAmountStatResource("when above [amount] [stat/resource]", UniqueTarget.Conditional, flags = setOf(UniqueFlag.AcceptsSpeedModifier),
        docDescription = "Stats refers to the accumulated stat, not stat-per-turn. Therefore, does not support Happiness - for that use 'when above [amount] Happiness'"),
    ConditionalWhenBelowAmountStatResource("when below [amount] [stat/resource]", UniqueTarget.Conditional, flags = setOf(UniqueFlag.AcceptsSpeedModifier),
        docDescription = "Stats refers to the accumulated stat, not stat-per-turn. Therefore, does not support Happiness - for that use 'when below [amount] Happiness'"),
    ConditionalWhenBetweenStatResource("when between [amount] and [amount] [stat/resource]", UniqueTarget.Conditional, flags = setOf(UniqueFlag.AcceptsSpeedModifier),
        docDescription = "Stats refers to the accumulated stat, not stat-per-turn." +
                " Therefore, does not support Happiness." +
                " 'Between' is inclusive - so 'between 1 and 5' includes 1 and 5."),

    /////// city conditionals
    ConditionalInThisCity("in this city", UniqueTarget.Conditional),
    ConditionalCityFilter("in [cityFilter] cities", UniqueTarget.Conditional),
    ConditionalCityConnected("in cities connected to the capital", UniqueTarget.Conditional),
    ConditionalCityReligion("in cities with a [religionFilter] religion", UniqueTarget.Conditional),
    ConditionalCityNotReligion("in cities not following a [religionFilter] religion", UniqueTarget.Conditional),
    ConditionalCityMajorReligion("in cities with a major religion", UniqueTarget.Conditional),
    ConditionalCityEnhancedReligion("in cities with an enhanced religion", UniqueTarget.Conditional),
    ConditionalCityThisReligion("in cities following our religion", UniqueTarget.Conditional),
    ConditionalCityWithBuilding("in cities with a [buildingFilter]", UniqueTarget.Conditional),
    ConditionalCityWithoutBuilding("in cities without a [buildingFilter]", UniqueTarget.Conditional),
    ConditionalPopulationFilter("in cities with at least [positiveAmount] [populationFilter]", UniqueTarget.Conditional),
    ConditionalExactPopulationFilter("in cities with [positiveAmount] [populationFilter]", UniqueTarget.Conditional),
    ConditionalBetweenPopulationFilter("in cities with between [amount] and [amount] [populationFilter]", UniqueTarget.Conditional,
        docDescription = "'Between' is inclusive - so 'between 1 and 5' includes 1 and 5."),
    ConditionalBelowPopulationFilter("in cities with less than [amount] [populationFilter]", UniqueTarget.Conditional),
    ConditionalWhenGarrisoned("with a garrison", UniqueTarget.Conditional),

    /////// unit conditionals
    ConditionalOurUnit("for [mapUnitFilter] units", UniqueTarget.Conditional),
    ConditionalOurUnitOnUnit("when [mapUnitFilter]", UniqueTarget.Conditional), // Same but for the unit itself
    ConditionalUnitWithPromotion("for units with [promotion]", UniqueTarget.Conditional, docDescription = "Also applies to units with temporary status"),
    ConditionalUnitWithoutPromotion("for units without [promotion]", UniqueTarget.Conditional, docDescription = "Also applies to units with temporary status"),
    ConditionalVsCity("vs cities", UniqueTarget.Conditional),
    ConditionalVsUnits("vs [mapUnitFilter] units", UniqueTarget.Conditional),
    ConditionalVsCombatant("vs [combatantFilter]", UniqueTarget.Conditional),
    ConditionalVsLargerCiv("when fighting units from a Civilization with more Cities than you", UniqueTarget.Conditional),
    ConditionalAttacking("when attacking", UniqueTarget.Conditional),
    ConditionalDefending("when defending", UniqueTarget.Conditional),
    ConditionalFightingInTiles("when fighting in [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalForeignContinent("on foreign continents", UniqueTarget.Conditional),
    ConditionalAdjacentUnit("when adjacent to a [mapUnitFilter] unit", UniqueTarget.Conditional),
    ConditionalAboveHP("when above [positiveAmount] HP", UniqueTarget.Conditional),
    ConditionalBelowHP("when below [positiveAmount] HP", UniqueTarget.Conditional),
    ConditionalBelowMovement("when below [positiveAmount] movement", UniqueTarget.Conditional),
    ConditionalAboveMovement("when above [positiveAmount] movement", UniqueTarget.Conditional),
    ConditionalHasNotUsedOtherActions("if it hasn't used other actions yet", UniqueTarget.Conditional),
    ConditionalStackedWithUnit("when stacked with a [mapUnitFilter] unit", UniqueTarget.Conditional),
    ConditionalNotStackedWithUnit("when not stacked with a [mapUnitFilter] unit", UniqueTarget.Conditional),

    /////// tile conditionals
    ConditionalNeighborTiles("with [nonNegativeAmount] to [nonNegativeAmount] neighboring [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalInTiles("in [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalInTilesNot("in tiles without [tileFilter]", UniqueTarget.Conditional),
    ConditionalNearTiles("within [positiveAmount] tiles of a [tileFilter]", UniqueTarget.Conditional),

    ConditionalAdjacentTo("in tiles adjacent to [tileFilter] tiles", UniqueTarget.Conditional),

    ConditionalNotAdjacentTo("in tiles not adjacent to [tileFilter] tiles", UniqueTarget.Conditional),


    /////// area conditionals
    ConditionalOnWaterMaps("on water maps", UniqueTarget.Conditional),
    ConditionalInRegionOfType("in [regionType] Regions", UniqueTarget.Conditional),
    ConditionalInRegionExceptOfType("in all except [regionType] Regions", UniqueTarget.Conditional),

    /////// countables conditionals
    ConditionalCountableEqualTo("when number of [countable] is equal to [countable]", UniqueTarget.Conditional),
    ConditionalCountableDifferentThan("when number of [countable] is different than [countable]", UniqueTarget.Conditional),
    ConditionalCountableMoreThan("when number of [countable] is more than [countable]", UniqueTarget.Conditional),
    ConditionalCountableLessThan("when number of [countable] is less than [countable]", UniqueTarget.Conditional),
    ConditionalCountableBetween("when number of [countable] is between [countable] and [countable]", UniqueTarget.Conditional,
        docDescription = "'Between' is inclusive - so 'between 1 and 5' includes 1 and 5."),

    /////// carrying conditionals
    ConditionalWhenCarriedBy("when carried by [mapUnitFilter] units", UniqueTarget.Conditional),

    //endregion

    ///////////////////////////////////////// region 09 TRIGGERED ONE-TIME /////////////////////////////////////////


    OneTimeFreeUnit("Free [unit] appears", UniqueTarget.Triggerable),  // used in Policies, Buildings
    OneTimeAmountFreeUnits("[positiveAmount] free [unit] units appear", UniqueTarget.Triggerable), // used in Buildings
    OneTimeRebel("A [unit] rebels", UniqueTarget.Triggerable),  // used in Policies, Buildings
    OneTimeAmountRebels("[positiveAmount] [unit]s rebel", UniqueTarget.Triggerable), // used in Buildings
    OneTimeFreeUnitRuins("Free [unit] found in the ruins", UniqueTarget.Ruins), // Differs from "Free [] appears" in that it spawns near the ruins instead of in a city
    OneTimeFreePolicy("Free Social Policy", UniqueTarget.Triggerable), // used in Buildings
    OneTimeAmountFreePolicies("[positiveAmount] Free Social Policies", UniqueTarget.Triggerable),  // Not used in Vanilla
    OneTimeEnterGoldenAge("Empire enters golden age", UniqueTarget.Triggerable),  // used in Policies, Buildings
    OneTimeEnterGoldenAgeTurns("Empire enters a [positiveAmount]-turn Golden Age", UniqueTarget.Triggerable),
    OneTimeFreeGreatPerson("Free Great Person", UniqueTarget.Triggerable),  // used in Policies, Buildings
    OneTimeGainPopulation("[amount] population [cityFilter]", UniqueTarget.Triggerable),  // used in CN tower
    OneTimeGainPopulationRandomCity("[amount] population in a random city", UniqueTarget.Triggerable),
    OneTimeDiscoverTech("Discover [tech]", UniqueTarget.Triggerable),
    OneTimeAdoptPolicyOrBelief("Adopt [policy/belief]", UniqueTarget.Triggerable),
    OneTimeRemovePolicy("Remove [policyFilter]", UniqueTarget.Triggerable),
    OneTimeRemovePolicyRefund("Remove [policyFilter] and refund [amount]% of its cost", UniqueTarget.Triggerable),
    OneTimeFreeTech("Free Technology", UniqueTarget.Triggerable),  // used in Buildings
    OneTimeAmountFreeTechs("[positiveAmount] Free Technologies", UniqueTarget.Triggerable),  // used in Policy
    OneTimeFreeTechRuins("[positiveAmount] free random researchable Tech(s) from the [eraFilter]", UniqueTarget.Triggerable),
    OneTimeRevealEntireMap("Reveals the entire map", UniqueTarget.Triggerable),  // used in tech
    OneTimeFreeBelief("Gain a free [beliefType] belief", UniqueTarget.Triggerable),
    OneTimeTriggerVoting("Triggers voting for the Diplomatic Victory", UniqueTarget.Triggerable),  // used in Building

    OneTimeConsumeResources("Instantly consumes [positiveAmount] [stockpiledResource]", UniqueTarget.Triggerable),
    OneTimeProvideResources("Instantly provides [positiveAmount] [stockpiledResource]", UniqueTarget.Triggerable),
    OneTimeSetStockpile("Set [stockpile] to [countable]", UniqueTarget.Triggerable, flags = setOf(UniqueFlag.AcceptsSpeedModifier)),
    OneTimeGainResource("Instantly gain [amount] [stockpile]", UniqueTarget.Triggerable, flags = setOf(UniqueFlag.AcceptsSpeedModifier)),
    OneTimeGainStat("Gain [amount] [stat]", UniqueTarget.Triggerable, flags = setOf(UniqueFlag.AcceptsSpeedModifier)),
    OneTimeGainStatRange("Gain [amount]-[amount] [stat]", UniqueTarget.Triggerable, flags = setOf(UniqueFlag.AcceptsSpeedModifier)),
    OneTimeGainPantheon("Gain enough Faith for a Pantheon", UniqueTarget.Triggerable),
    OneTimeGainProphet("Gain enough Faith for [positiveAmount]% of a Great Prophet", UniqueTarget.Triggerable),
    OneTimeGainTechPercent("Research [relativeAmount]% of [tech]", UniqueTarget.Triggerable),

    OneTimeTakeOverTilesInRadius("Gain control over [tileFilter] tiles in a [nonNegativeAmount]-tile radius", UniqueTarget.Triggerable),
    OneTimeTakeOverTilesInCity("Gain control over [positiveAmount] tiles [cityFilter]", UniqueTarget.Triggerable),

    // todo: The "up to [All]" used in vanilla json is not nice to read. Split?
    // Or just reword it without the 'up to', so it reads "Reveal [amount/'all'] [tileFilter] tiles within [amount] tiles"
    OneTimeRevealSpecificMapTiles("Reveal up to [positiveAmount/'all'] [tileFilter] within a [positiveAmount] tile radius", UniqueTarget.Triggerable),
    OneTimeRevealCrudeMap("From a randomly chosen tile [positiveAmount] tiles away from the ruins, reveal tiles up to [positiveAmount] tiles away with [positiveAmount]% chance", UniqueTarget.Ruins),
    OneTimeGlobalAlert("Triggers the following global alert: [comment]", UniqueTarget.Triggerable,
        docDescription = "Supported on Policies and Technologies.\n" +
            "For other targets, the generated Notification may not read nicely, and will likely not support translation." +
            " Reason: Your [comment] gets a generated introduction, other triggers usually notify _you_, not _others_," +
            " and that difference is currently handled by mapping text.\n" +
            "Conditionals evaluate in the context of the civilization having the Unique, not the recipients of the alerts."),
    OneTimeGlobalSpiesWhenEnteringEra("Every major Civilization gains a spy once a civilization enters this era", UniqueTarget.Era),
    OneTimeSpiesLevelUp("Promotes all spies [positiveAmount] time(s)", UniqueTarget.Triggerable),  // used in Policies, Buildings
    OneTimeGainSpy("Gain an extra spy", UniqueTarget.Triggerable),  // used in Wonders

    SkipPromotion("Doing so will consume this opportunity to choose a Promotion", UniqueTarget.Promotion),
    FreePromotion("This Promotion is free", UniqueTarget.Promotion),

    OneTimeChangeTerrain("Turn this tile into a [terrainName] tile", UniqueTarget.Triggerable),

    OneTimeRemoveResourcesFromTile("Remove [resourceFilter] resources from this tile", UniqueTarget.Triggerable),
    OneTimeRemoveImprovementsFromTile("Remove [improvementFilter] improvements from this tile", UniqueTarget.Triggerable),

    UnitsGainPromotion("[mapUnitFilter] units gain the [promotion] promotion", UniqueTarget.Triggerable,
        docDescription = "Works only with promotions that are valid for the unit's type - or for promotions that do not specify any."),  // Not used in Vanilla
    FreeStatBuildings("Provides the cheapest [stat] building in your first [positiveAmount] cities for free", UniqueTarget.Triggerable),  // used in Policy
    FreeSpecificBuildings("Provides a [buildingName] in your first [positiveAmount] cities for free", UniqueTarget.Triggerable),  // used in Policy
    TriggerEvent("Triggers a [event] event", UniqueTarget.Triggerable),
    MarkTutorialComplete("Mark tutorial [comment] complete", UniqueTarget.Triggerable, flags = UniqueFlag.setOfHiddenNoConditionals),
    PlaySound("Play [comment] sound", UniqueTarget.Triggerable, flags = UniqueFlag.setOfHiddenToUsers,
        docDescription = "See [Images and Audio](Images-and-Audio.md#sounds) for a list of available sounds."),
    GetLeaderTitle("Get the leader title of [leaderTitle]", UniqueTarget.Triggerable, flags = UniqueFlag.setOfHiddenToUsers),

    //endregion
    
    ///////////////////////////////////////// region 09 UNIT TRIGGERABLES /////////////////////////////////////////

    OneTimeUnitHeal("[unitTriggerTarget] heals [positiveAmount] HP", UniqueTarget.UnitTriggerable),
    OneTimeUnitDamage("[unitTriggerTarget] takes [positiveAmount] damage", UniqueTarget.UnitTriggerable),
    OneTimeUnitGainXP("[unitTriggerTarget] gains [amount] XP", UniqueTarget.UnitTriggerable),
    OneTimeUnitUpgrade("[unitTriggerTarget] upgrades for free", UniqueTarget.UnitTriggerable),
    OneTimeUnitSpecialUpgrade("[unitTriggerTarget] upgrades for free including special upgrades", UniqueTarget.UnitTriggerable),
    OneTimeUnitGainPromotion("[unitTriggerTarget] gains the [promotion] promotion", UniqueTarget.UnitTriggerable),
    OneTimeUnitRemovePromotion("[unitTriggerTarget] loses the [promotion] promotion", UniqueTarget.UnitTriggerable),
    OneTimeUnitGainMovement("[unitTriggerTarget] gains [positiveAmount] movement", UniqueTarget.UnitTriggerable),
    OneTimeUnitLoseMovement("[unitTriggerTarget] loses [positiveAmount] movement", UniqueTarget.UnitTriggerable),
    OneTimeUnitGainStatus("[unitTriggerTarget] gains the [promotion] status for [positiveAmount] turn(s)", UniqueTarget.UnitTriggerable,
        docDescription = "Statuses are temporary promotions. They do not stack, and reapplying a specific status take the highest number - so reapplying a 3-turn on a 1-turn makes it 3, but doing the opposite will have no effect. " +
                "Turns left on the status decrease at the *start of turn*, so bonuses applied for 1 turn are stll applied during other civ's turns."),
    OneTimeUnitLoseStatus("[unitTriggerTarget] loses the [promotion] status", UniqueTarget.UnitTriggerable),
    OneTimeUnitDestroyed("[unitTriggerTarget] is destroyed", UniqueTarget.UnitTriggerable),
    OneTimeUnitGetsName("[unitTriggerTarget] gets a name from the [unitNameGroup] group", UniqueTarget.UnitTriggerable),
    //endregion


    ///////////////////////////////////////// region 10 TRIGGERS /////////////////////////////////////////

    TriggerUponResearch("upon discovering [techFilter] technology", UniqueTarget.TriggerCondition),
    TriggerUponEnteringEra("upon entering the [era]", UniqueTarget.TriggerCondition),
    TriggerUponEnteringEraUnfiltered("upon entering a new era", UniqueTarget.TriggerCondition),
    TriggerUponAdoptingPolicyOrBelief("upon adopting [policy/belief]", UniqueTarget.TriggerCondition),
    TriggerUponDeclaringWarFiltered("upon declaring war on [civFilter] Civilizations", UniqueTarget.TriggerCondition),
    TriggerUponBeingDeclaredWarUpon("upon being declared war on by [civFilter] Civilizations", UniqueTarget.TriggerCondition),
    TriggerUponEnteringWar("upon entering a war with [civFilter] Civilizations", UniqueTarget.TriggerCondition),
    TriggerUponSigningPeace("upon signing a peace treaty with [civFilter] Civilizations", UniqueTarget.TriggerCondition),
    TriggerUponDeclaringFriendship("upon declaring friendship", UniqueTarget.TriggerCondition),
    TriggerUponSigningDefensivePact("upon declaring a defensive pact", UniqueTarget.TriggerCondition),
    TriggerUponEnteringGoldenAge("upon entering a Golden Age", UniqueTarget.TriggerCondition),
    TriggerUpponEndingGoldenAge("upon ending a Golden Age", UniqueTarget.TriggerCondition),
    /** Can be placed upon both units and as global */
    TriggerUponConqueringCity("upon conquering a city", UniqueTarget.TriggerCondition, UniqueTarget.UnitTriggerCondition),
    TriggerUponLosingCity("upon losing a city", UniqueTarget.TriggerCondition),
    TriggerUponFoundingCity("upon founding a city", UniqueTarget.TriggerCondition),
    TriggerUponBuildingImprovement("upon building a [improvementFilter] improvement", UniqueTarget.TriggerCondition, UniqueTarget.UnitTriggerCondition),
    TriggerUponDiscoveringNaturalWonder("upon discovering a Natural Wonder", UniqueTarget.TriggerCondition),
    TriggerUponConstructingBuilding("upon constructing [buildingFilter]", UniqueTarget.TriggerCondition),
    // We have a separate trigger to include the cityFilter, since '[in all cities]' can be read '*only* if it's in all cities'
    TriggerUponConstructingBuildingCityFilter("upon constructing [buildingFilter] [cityFilter]", UniqueTarget.TriggerCondition),
    TriggerUponGainingUnit("upon gaining a [baseUnitFilter] unit", UniqueTarget.TriggerCondition),
    TriggerUponLosingUnit("upon losing a [mapUnitFilter] unit", UniqueTarget.TriggerCondition),
    TriggerUponTurnEnd("upon turn end", UniqueTarget.TriggerCondition, UniqueTarget.UnitTriggerCondition),
    TriggerUponTurnStart("upon turn start", UniqueTarget.TriggerCondition, UniqueTarget.UnitTriggerCondition),

    TriggerUponFoundingPantheon("upon founding a Pantheon", UniqueTarget.TriggerCondition),
    TriggerUponFoundingReligion("upon founding a Religion", UniqueTarget.TriggerCondition),
    TriggerUponEnhancingReligion("upon enhancing a Religion", UniqueTarget.TriggerCondition),

    //endregion


    ///////////////////////////////////////// region 11 UNIT TRIGGERS /////////////////////////////////////////

    TriggerUponCombat("upon entering combat", UniqueTarget.UnitTriggerCondition),
    TriggerUponDamagingUnit("upon damaging a [mapUnitFilter] unit", UniqueTarget.UnitTriggerCondition,
        docDescription = "Can apply triggers to to damaged unit by setting the first parameter to 'Target Unit'"),
    TriggerUponDefeatingUnit("upon defeating a [mapUnitFilter] unit", UniqueTarget.UnitTriggerCondition),
    TriggerUponExpendingUnit("upon expending a [mapUnitFilter] unit", UniqueTarget.TriggerCondition),
    TriggerUponDefeat("upon being defeated", UniqueTarget.UnitTriggerCondition),
    TriggerUponPromotion("upon being promoted", UniqueTarget.UnitTriggerCondition),
    TriggerUponPromotionGain("upon gaining the [promotion] promotion", UniqueTarget.UnitTriggerCondition),
    TriggerUponPromotionLoss("upon losing the [promotion] promotion", UniqueTarget.UnitTriggerCondition),
    TriggerUponStatusGain("upon gaining the [promotion] status", UniqueTarget.UnitTriggerCondition),
    TriggerUponStatusLoss("upon losing the [promotion] status", UniqueTarget.UnitTriggerCondition),
    TriggerUponLosingHealth("upon losing at least [positiveAmount] HP in a single attack", UniqueTarget.UnitTriggerCondition),
    TriggerUponEndingTurnInTile("upon ending a turn in a [tileFilter] tile", UniqueTarget.UnitTriggerCondition),
    TriggerUponDiscoveringTile("upon discovering a [tileFilter] tile", UniqueTarget.UnitTriggerCondition),
    TriggerUponEnteringTile("upon entering a [tileFilter] tile", UniqueTarget.UnitTriggerCondition),

    //endregion

    ///////////////////////////////////////////// region 90 META /////////////////////////////////////////////

    ConditionalTimedUnique("for [nonNegativeAmount] turns", UniqueTarget.MetaModifier,
        docDescription = "Turns this unique into a trigger, activating this unique as a *global* unique for a number of turns"),

    AiChoiceWeight("[relativeAmount]% weight to this choice for AI decisions",
        UniqueTarget.Building,
        UniqueTarget.EventChoice,
        UniqueTarget.FollowerBelief,
        UniqueTarget.FounderBelief,
        UniqueTarget.Policy,
        UniqueTarget.Promotion,
        UniqueTarget.Tech,
        flags = UniqueFlag.setOfHiddenToUsers),
    
    UnitActionPriority("with [amount] priority",
        UniqueTarget.UnitActionModifier,
        UniqueTarget.MetaModifier, // Can also be applied to UniqueTarget.Triggerable
        flags = UniqueFlag.setOfHiddenToUsers,
        docDescription = "How often this action is used, a higher value means more often and that it should be on an earlier page. " +
        "100 is very frequent, 50 is somewhat frequent, less than 25 is press one time for multi-turn movement. " +
        "A Rare case is > 100 if a button is something like add in capital, promote or something, " +
        "we need to inform the player that taking the action is an option."),

    HiddenFromCivilopedia("Will not be displayed in Civilopedia", *UniqueTarget.Displayable, flags = UniqueFlag.setOfHiddenToUsers),
    ShowsWhenUnbuilable("Shown while unbuilable", UniqueTarget.Building, UniqueTarget.Unit, flags = UniqueFlag.setOfHiddenToUsers),
    ModifierHiddenFromUsers("hidden from users", UniqueTarget.MetaModifier),
    WillNotBeChosenForNewGames("Will not be chosen for new games", UniqueTarget.Nation),
    
    ForEveryCountable("for every [countable]", UniqueTarget.MetaModifier,
        docDescription = "Works for positive numbers only"),
    ForEveryAdjacentTile("for every adjacent [tileFilter]", UniqueTarget.MetaModifier,
        docDescription = "Works for positive numbers only"),
    ForEveryAmountCountable("for every [positiveAmount] [countable]", UniqueTarget.MetaModifier,
        docDescription = "Works for positive numbers only"),
    
    ModifiedByGameSpeed("(modified by game speed)", UniqueTarget.MetaModifier,
        docDescription = "Can only be applied to certain uniques, see details of each unique for specifics"),
    ModifiedByGameProgress("(modified by game progress up to [relativeAmount]%)", UniqueTarget.MetaModifier,
        docDescription = "Can only be applied to certain uniques, see details of each unique for specifics"),
    Comment("Comment [comment]", *UniqueTarget.Displayable,
        docDescription = "Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent."),
    CivilopediaLink("Civilopedia link [pediaLink]", UniqueTarget.MetaModifier, flags = UniqueFlag.setOfHiddenToUsers,
        docDescription = "Allows linking a unique to any Civilopedia page when it is listed in Civilopedia normally. This overrides automatic links to objects in the unique's parameters."),

    // Formerly `ModOptionsConstants`
    DiplomaticRelationshipsCannotChange("Diplomatic relationships cannot change", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals),
    ConvertGoldToScience("Can convert gold to science with sliders", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals),
    AllowCityStatesSpawnUnits("Allow City States to spawn with additional units", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals),
    TradeCivIntroductions("Can trade civilization introductions for [positiveAmount] Gold", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals),
    DisableReligion("Disable religion", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals),
    CanOnlyStartFromStartingEra("Can only start games from the starting era", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals,
        docDescription = "In this case, 'starting era' means the first defined Era in the entire ruleset."),
    AllowRazeCapital("Allow raze capital", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals),
    AllowRazeHolyCity("Allow raze holy city", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals),

    SuppressWarnings("Suppress warning [validationWarning]", *UniqueTarget.CanIncludeSuppression, flags = UniqueFlag.setOfHiddenNoConditionals, docDescription = Suppression.uniqueDocDescription),

    // Declarative Mod compatibility (see [ModCompatibility]):
    // Note there is currently no display for these, but UniqueFlag.HiddenToUsers is not set.
    // That means we auto-template and ask our translators for a translation that is currently unused.
    //todo To think over - leave as is for future use or remove templates and translations by adding the flag?

    ModIncompatibleWith("Mod is incompatible with [modFilter]", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals,
        docDescription = "Specifies that your Mod is incompatible with another. Always treated symmetrically, and cannot be overridden by the Mod you are declaring as incompatible."),
    ModRequires("Mod requires [modFilter]", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals,
        docDescription = "Specifies that your Extension Mod is only available if any other Mod matching the filter is active.\n" +
        "Multiple copies of this Unique cannot be used to specify alternatives, they work as 'and' logic. If you need alternates and wildcards can't filter them well enough, please open an issue."),
    ModIsAudioVisualOnly("Should only be used as permanent audiovisual mod", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals),
    ModIsAudioVisual("Can be used as permanent audiovisual mod", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals),
    ModIsNotAudioVisual("Cannot be used as permanent audiovisual mod", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals),
    ModMapPreselection("Mod preselects map [comment]", UniqueTarget.ModOptions, flags = UniqueFlag.setOfNoConditionals,
        docDescription = "Only meaningful for Mods containing several maps. When this mod is selected on the new game screen's custom maps mod dropdown, the named map will be selected on the map dropdown. Also disables selection by recently modified. Case insensitive."),
    ConditionalModEnabled("if [modFilter] is enabled", UniqueTarget.Conditional),
    ConditionalModNotEnabled("if [modFilter] is not enabled", UniqueTarget.Conditional),

    // endregion

    ///////////////////////////////////////////// region 99 DEPRECATED AND REMOVED /////////////////////////////////////////////

    @Deprecated("as of 3.18.12", ReplaceWith("[amount]% XP gained from combat"), DeprecationLevel.WARNING)
    BonuxXPGain("[amount]% Bonus XP gain", UniqueTarget.Unit),
    @Deprecated("as of 3.18.12", ReplaceWith("[amount]% XP gained from combat <for [mapUnitFilter] units>"), DeprecationLevel.WARNING)
    BonusXPGainForUnits("[mapUnitFilter] units gain [amount]% more Experience from combat", UniqueTarget.Global),

    @Deprecated("as of 3.18.14", ReplaceWith("[amount]% maintenance costs <for [mapUnitFilter] units>"), DeprecationLevel.WARNING)
    UnitMaintenanceDiscountGlobal("[amount]% maintenance costs for [mapUnitFilter] units", UniqueTarget.Global),

    // Keep the endregion after the semicolon or it won't work
    ;
    // endregion

    /** A map of allowed [UniqueParameterType]s per parameter position. Initialized from overridable function [parameterTypeMapInitializer]. */
    val parameterTypeMap = parameterTypeMapInitializer()

    /** For uniques that have "special" parameters that can accept multiple types, we can override them manually
     *  For 95% of cases, auto-matching is fine. */
    @Readonly
    open fun parameterTypeMapInitializer(): ArrayList<List<UniqueParameterType>> {
        val map = ArrayList<List<UniqueParameterType>>()
        for (placeholder in text.getPlaceholderParameters()) {
            val matchingParameterTypes = placeholder
                .split('/')
                .mapNotNull { UniqueParameterType.safeValueOf(it.replace(numberRegex, "")) }
            map.add(matchingParameterTypes)
        }
        return map
    }

    val targetTypes = HashSet<UniqueTarget>(targets.size)

    init {
        targetTypes.addAll(targets)
    }

    val placeholderText = text.getPlaceholderText()

    /** Ordinal determines severity - ordered from least to most severe, so we can use Severity >=  */
    enum class UniqueParameterErrorSeverity {

        /** This is a warning, regardless of what ruleset we're in.
         * This is for filters that can also potentially accept free text, like UnitFilter and TileFilter */
        PossibleFilteringUnique {
            override fun getRulesetErrorSeverity() = RulesetErrorSeverity.WarningOptionsOnly
        },

        /** An error, but only because of other information in the current ruleset.
         * This means that if this is an *add-on* to a different mod, it could be correct.
         * This is a problem like "unit/resource/tech name doesn't exist in ruleset" - definite bug */
        RulesetSpecific {
            // Report Warning on the first pass of RulesetValidator only, where mods are checked standalone
            // but upgrade to error when the econd pass asks, which runs only for combined or base rulesets.
            override fun getRulesetErrorSeverity() = RulesetErrorSeverity.Warning
        },

        /** An error, regardless of the ruleset we're in.
         * This is a problem like "numbers don't parse", "stat isn't stat", "city filter not applicable" */
        RulesetInvariant {
            override fun getRulesetErrorSeverity() = RulesetErrorSeverity.Error
        },
        ;

        /** Done as function instead of property so we can in the future upgrade severities depending
         *  on the [RulesetValidator] "pass": [severityToReport]==[RulesetInvariant] means it's the
         *  first pass that also runs for extension mods without a base mixed in; the complex check
         *  runs with [severityToReport]==[RulesetSpecific].
         */
        @Readonly abstract fun getRulesetErrorSeverity(): RulesetErrorSeverity
    }

    @Readonly
    fun getDeprecationAnnotation(): Deprecated? = declaringJavaClass.getField(name)
        .getAnnotation(Deprecated::class.java)

    /** Checks whether a specific [uniqueTarget] as e.g. given by [IHasUniques.getUniqueTarget] works with `this` UniqueType */
    @Readonly
    fun canAcceptUniqueTarget(uniqueTarget: UniqueTarget) =
        targetTypes.any { uniqueTarget.canAcceptUniqueTarget(it) }

    override fun toString() = text

    companion object {
        val uniqueTypeMap: Map<String, UniqueType> = entries.associateBy { it.placeholderText }
    }
}
