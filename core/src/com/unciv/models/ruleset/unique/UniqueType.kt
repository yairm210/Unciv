package com.unciv.models.ruleset.unique

import com.unciv.Constants
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.models.ruleset.validation.RulesetValidator
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText

// I didn't put this in a companion object because APPARENTLY doing that means you can't use it in the init function.
private val numberRegex = Regex("\\d+$") // Any number of trailing digits

enum class UniqueType(
    val text: String,
    vararg targets: UniqueTarget,
    val flags: List<UniqueFlag> = emptyList(),
    val docDescription: String? = null
) {

    //////////////////////////////////////// region 01 GLOBAL UNIQUES ////////////////////////////////////////

    // region Stat providing uniques

    // Used for *global* bonuses and improvement/terrain bonuses
    Stats("[stats]", UniqueTarget.Global, UniqueTarget.Improvement, UniqueTarget.Terrain),
    // Used for city-wide bonuses
    StatsPerCity("[stats] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    StatsFromSpecialist("[stats] from every specialist [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsPerPopulation("[stats] per [amount] population [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsPerPolicies("[stats] per [amount] social policies adopted", UniqueTarget.Global),
    StatsPerStat("[stats] per every [amount] [civWideStat]", UniqueTarget.Global),

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
    StatPercentFromObject("[relativeAmount]% [stat] from every [tileFilter/buildingFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    AllStatsPercentFromObject("[relativeAmount]% Yield from every [tileFilter/buildingFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatPercentFromReligionFollowers("[relativeAmount]% [stat] from every follower, up to [relativeAmount]%", UniqueTarget.FollowerBelief, UniqueTarget.FounderBelief),
    BonusStatsFromCityStates("[relativeAmount]% [stat] from City-States", UniqueTarget.Global),
    StatPercentFromTradeRoutes("[relativeAmount]% [stat] from Trade Routes", UniqueTarget.Global),

    NullifiesStat("Nullifies [stat] [cityFilter]", UniqueTarget.Global),
    NullifiesGrowth("Nullifies Growth [cityFilter]", UniqueTarget.Global),

    PercentProductionBuildings("[relativeAmount]% Production when constructing [buildingFilter] buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    PercentProductionUnits("[relativeAmount]% Production when constructing [baseUnitFilter] units [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    PercentProductionWonders("[relativeAmount]% Production when constructing [buildingFilter] wonders [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    PercentProductionBuildingsInCapital("[relativeAmount]% Production towards any buildings that already exist in the Capital", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    // endregion Stat providing uniques

    // region City-State related uniques

    CityStateMilitaryUnits("Provides military units every ≈[amount] turns", UniqueTarget.CityState),
    CityStateUniqueLuxury("Provides a unique luxury", UniqueTarget.CityState), // No conditional support as of yet

    // Todo: Lowercase the 'U' of 'Units' in this unique
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

    /// Growth
    GrowthPercentBonus("[relativeAmount]% growth [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    CarryOverFood("[amount]% Food is carried over after population increases [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    // Todo: moddability: specialists -> [populationFilter]
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
    BuyUnitsIncreasingCost("May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] at an increasing price ([amount])", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsIncreasingCost("May buy [buildingFilter] buildings for [amount] [stat] [cityFilter] at an increasing price ([amount])", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyUnitsForAmountStat("May buy [baseUnitFilter] units for [amount] [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsForAmountStat("May buy [buildingFilter] buildings for [amount] [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyUnitsWithStat("May buy [baseUnitFilter] units with [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsWithStat("May buy [buildingFilter] buildings with [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyUnitsByProductionCost("May buy [baseUnitFilter] units with [stat] for [amount] times their normal Production cost", UniqueTarget.FollowerBelief, UniqueTarget.Global),
    BuyBuildingsByProductionCost("May buy [buildingFilter] buildings with [stat] for [amount] times their normal Production cost", UniqueTarget.FollowerBelief, UniqueTarget.Global),
    BuyItemsDiscount("[stat] cost of purchasing items in cities [relativeAmount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsDiscount("[stat] cost of purchasing [buildingFilter] buildings [relativeAmount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyUnitsDiscount("[stat] cost of purchasing [baseUnitFilter] units [relativeAmount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    /// Production to Stat conversion
    EnablesCivWideStatProduction("Enables conversion of city production to [civWideStat]", UniqueTarget.Global),
    ProductionToCivWideStatConversionBonus("Production to [civWideStat] conversion in cities changed by [relativeAmount]%", UniqueTarget.Global),

    /// Improvements
    // Should be replaced with moddable improvements when roads become moddable
    RoadMovementSpeed("Improves movement speed on roads",UniqueTarget.Global),
    RoadsConnectAcrossRivers("Roads connect tiles across rivers", UniqueTarget.Global),
    RoadMaintenance("[relativeAmount]% maintenance on road & railroads", UniqueTarget.Global),
    NoImprovementMaintenanceInSpecificTiles("No Maintenance costs for improvements in [tileFilter] tiles", UniqueTarget.Global),
    @Deprecated("As of",ReplaceWith("[relativeAmount]% construction time for [All] improvements"))
    TileImprovementTime("[relativeAmount]% tile improvement construction time", UniqueTarget.Global, UniqueTarget.Unit),
    SpecificImprovementTime("[relativeAmount]% construction time for [improvementFilter] improvements", UniqueTarget.Global, UniqueTarget.Unit),

    /// Building Maintenance
    GainFreeBuildings("Gain a free [buildingName] [cityFilter]", UniqueTarget.Global, UniqueTarget.Triggerable),
    BuildingMaintenance("[relativeAmount]% maintenance cost for buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    RemoveBuilding("Remove [buildingFilter] [cityFilter]", UniqueTarget.Global, UniqueTarget.Triggerable),
    SellBuilding("Sell [buildingFilter] buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.Triggerable),

    /// Border growth
    BorderGrowthPercentage("[relativeAmount]% Culture cost of natural border growth [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    TileCostPercentage("[relativeAmount]% Gold cost of acquiring tiles [cityFilter]", UniqueTarget.FollowerBelief, UniqueTarget.Global),

    /// Policy Cost
    LessPolicyCostFromCities("Each city founded increases culture cost of policies [relativeAmount]% less than normal", UniqueTarget.Global),
    LessPolicyCost("[relativeAmount]% Culture cost of adopting new Policies", UniqueTarget.Global),

    /// Natural Wonders
    StatsFromNaturalWonders("[stats] for every known Natural Wonder", UniqueTarget.Global),
    // TODO: moddability of the numbers
    GoldWhenDiscoveringNaturalWonder("100 Gold for discovering a Natural Wonder (bonus enhanced to 500 Gold if first to discover it)", UniqueTarget.Global),

    /// Great Persons
    GreatPersonPointPercentage("[relativeAmount]% Great Person generation [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    ProvidesGoldWheneverGreatPersonExpended("Provides a sum of gold each time you spend a Great Person", UniqueTarget.Global),
    ProvidesStatsWheneverGreatPersonExpended("[stats] whenever a Great Person is expended", UniqueTarget.Global),
    PercentGoldFromTradeMissions("[relativeAmount]% Gold from Great Merchant trade missions", UniqueTarget.Global),
    GreatGeneralProvidesDoubleCombatBonus("Great General provides double combat bonus", UniqueTarget.Unit, UniqueTarget.Global),
    // This should probably support conditionals, e.g. <after discovering [tech]>
    MayanGainGreatPerson("Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once.", UniqueTarget.Global),
    MayanCalendarDisplay("Once The Long Count activates, the year on the world screen displays as the traditional Mayan Long Count.", UniqueTarget.Global),

    /// Unit Maintenance & Supply
    BaseUnitSupply("[amount] Unit Supply", UniqueTarget.Global),
    UnitSupplyPerPop("[amount] Unit Supply per [amount] population [cityFilter]", UniqueTarget.Global),
    UnitSupplyPerCity("[amount] Unit Supply per city", UniqueTarget.Global),
    FreeUnits("[amount] units cost no maintenance", UniqueTarget.Global),
    UnitsInCitiesNoMaintenance("Units in cities cost no Maintenance", UniqueTarget.Global),
    @Deprecated("As of 4.8.5", ReplaceWith("Free [unit] appears <upon discovering [tech]>"))
    ReceiveFreeUnitWhenDiscoveringTech("Receive free [unit] when you discover [tech]", UniqueTarget.Global),

    // Units entering Tiles
    // ToDo: make per unit and use unit filters? "Enables embarkation <for [land] units>"
    LandUnitEmbarkation("Enables embarkation for land units", UniqueTarget.Global),
    UnitsMayEnterOcean("Enables [mapUnitFilter] units to enter ocean tiles", UniqueTarget.Global),
    LandUnitsCrossTerrainAfterUnitGained("Land units may cross [terrainName] tiles after the first [baseUnitFilter] is earned", UniqueTarget.Global),
    EnemyUnitsSpendExtraMovement("Enemy [mapUnitFilter] units must spend [amount] extra movement points when inside your territory", UniqueTarget.Global),

    /// Unit Abilities
    UnitStartingExperience("New [baseUnitFilter] units start with [amount] Experience [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    UnitStartingPromotions("All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    // Todo: Lowercase the 'U' of 'Units' in this unique
    CityHealingUnits("[mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    /// City Strength
    BetterDefensiveBuildings("[relativeAmount]% City Strength from defensive buildings", UniqueTarget.Global),
    StrengthForCities("[relativeAmount]% Strength for cities", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    /// Resource production & consumption
    ConsumesResources("Consumes [amount] [resource]", UniqueTarget.Improvement, UniqueTarget.Building, UniqueTarget.Unit),
    ProvidesResources("Provides [amount] [resource]", UniqueTarget.Global, UniqueTarget.Improvement, UniqueTarget.FollowerBelief),
    CostsResources("Costs [amount] [stockpiledResource]", UniqueTarget.Improvement, UniqueTarget.Building, UniqueTarget.Unit),
    // Todo: Get rid of forced sign (+[relativeAmount]) and unify these two, e.g.: "[relativeAmount]% [resource/resourceType] production"
    // Note that the parameter type 'resourceType' (strategic, luxury, bonus) currently doesn't exist and should then be added as well
    StrategicResourcesIncrease("Quantity of strategic resources produced by the empire +[relativeAmount]%", UniqueTarget.Global),  // used by Policies
    DoubleResourceProduced("Double quantity of [resource] produced", UniqueTarget.Global),

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
    TripleGoldFromEncampmentsAndCities("Receive triple Gold from Barbarian encampments and pillaging Cities", UniqueTarget.Global),
    GainFromEncampment("When conquering an encampment, earn [amount] Gold and recruit a Barbarian unit", UniqueTarget.Global),
    GainFromDefeatingUnit("When defeating a [mapUnitFilter] unit, earn [amount] Gold and recruit it", UniqueTarget.Global),

    /// Religion
    DisablesReligion("Starting in this era disables religion", UniqueTarget.Era),
    FreeExtraBeliefs("May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion", UniqueTarget.Global),
    FreeExtraAnyBeliefs("May choose [amount] additional belief(s) of any type when [foundingOrEnhancing] a religion", UniqueTarget.Global),
    StatsWhenAdoptingReligionSpeed("[stats] when a city adopts this religion for the first time (modified by game speed)", UniqueTarget.Global),
    StatsWhenAdoptingReligion("[stats] when a city adopts this religion for the first time", UniqueTarget.Global),
    NaturalReligionSpreadStrength("[relativeAmount]% Natural religion spread [cityFilter]", UniqueTarget.FollowerBelief, UniqueTarget.Global),
    ReligionSpreadDistance("Religion naturally spreads to cities [amount] tiles away", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    MayNotGenerateGreatProphet("May not generate great prophet equivalents naturally", UniqueTarget.Global),
    FaithCostOfGreatProphetChange("[relativeAmount]% Faith cost of generating Great Prophet equivalents", UniqueTarget.Global),
    @Deprecated("As of 4.8.9", ReplaceWith("All newly-trained [baseUnitFilter] units [cityFilter] receive the [Devout] promotion"))
    UnitStartingActions("[baseUnitFilter] units built [cityFilter] can [action] [amount] extra times", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    /// Things you get at the start of the game
    StartingTech("Starting tech", UniqueTarget.Tech),
    StartsWithTech("Starts with [tech]", UniqueTarget.Nation),
    StartsWithPolicy("Starts with [policy] adopted", UniqueTarget.Nation),

    /// Victory
    TriggersVictory("Triggers victory", UniqueTarget.Global),
    TriggersCulturalVictory("Triggers a Cultural Victory upon completion", UniqueTarget.Global),

    /// Misc.
    MayNotAnnexCities("May not annex cities", UniqueTarget.Global),
    BorrowsCityNames("\"Borrows\" city names from other civilizations in the game", UniqueTarget.Global),
    CitiesAreRazedXTimesFaster("Cities are razed [amount] times as fast", UniqueTarget.Global),

    TechBoostWhenScientificBuildingsBuiltInCapital("Receive a tech boost when scientific buildings/wonders are built in capital", UniqueTarget.Global),
    ResearchableMultipleTimes("Can be continually researched", UniqueTarget.Tech),

    GoldenAgeLength("[relativeAmount]% Golden Age length", UniqueTarget.Global),

    PopulationLossFromNukes("Population loss from nuclear attacks [relativeAmount]% [cityFilter]", UniqueTarget.Global),
    GarrisonDamageFromNukes("Damage to garrison from nuclear attacks [relativeAmount]% [cityFilter]", UniqueTarget.Global),

    SpawnRebels("Rebel units may spawn", UniqueTarget.Global),

    // endregion Other global uniques

    // endregion 01 Global uniques


    ///////////////////////////////////////// region 02 CONSTRUCTION UNIQUES /////////////////////////////////////////

    Unbuildable("Unbuildable", UniqueTarget.Building, UniqueTarget.Unit, UniqueTarget.Improvement),
    CannotBePurchased("Cannot be purchased", UniqueTarget.Building, UniqueTarget.Unit),
    CanBePurchasedWithStat("Can be purchased with [stat] [cityFilter]", UniqueTarget.Building, UniqueTarget.Unit),
    CanBePurchasedForAmountStat("Can be purchased for [amount] [stat] [cityFilter]", UniqueTarget.Building, UniqueTarget.Unit),
    MaxNumberBuildable("Limited to [amount] per Civilization", UniqueTarget.Building, UniqueTarget.Unit),
    HiddenBeforeAmountPolicies("Hidden until [amount] social policy branches have been completed", UniqueTarget.Building, UniqueTarget.Unit),
    // Meant to be used together with conditionals, like "Only available <after adopting [policy]> <while the empire is happy>"
    OnlyAvailableWhen("Only available", UniqueTarget.Unit, UniqueTarget.Building, UniqueTarget.Improvement,
        UniqueTarget.Policy, UniqueTarget.Tech, UniqueTarget.Promotion, UniqueTarget.Ruins, UniqueTarget.FollowerBelief, UniqueTarget.FounderBelief),

    ConvertFoodToProductionWhenConstructed("Excess Food converted to Production when under construction", UniqueTarget.Building, UniqueTarget.Unit),
    RequiresPopulation("Requires at least [amount] population", UniqueTarget.Building, UniqueTarget.Unit),

    TriggersAlertOnStart("Triggers a global alert upon build start", UniqueTarget.Building, UniqueTarget.Unit),
    TriggersAlertOnCompletion("Triggers a global alert upon completion", UniqueTarget.Building, UniqueTarget.Unit),
    //endregion

    ///////////////////////////////////////// region 03 BUILDING UNIQUES /////////////////////////////////////////


    CostIncreasesPerCity("Cost increases by [amount] per owned city", UniqueTarget.Building),

    RequiresBuildingInAllCities("Requires a [buildingFilter] in all cities", UniqueTarget.Building),
    RequiresBuildingInSomeCities("Requires a [buildingFilter] in at least [amount] cities", UniqueTarget.Building),
    CanOnlyBeBuiltInCertainCities("Can only be built [cityFilter]", UniqueTarget.Building),

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
    ProvidesExtraLuxuryFromCityResources("Provides 1 extra copy of each improved luxury resource near this City", UniqueTarget.Building),

    DestroyedWhenCityCaptured("Destroyed when the city is captured", UniqueTarget.Building),
    NotDestroyedWhenCityCaptured("Never destroyed when the city is captured", UniqueTarget.Building),
    DoublesGoldFromCapturingCity("Doubles Gold given to enemy if city is captured", UniqueTarget.Building),

    RemoveAnnexUnhappiness("Remove extra unhappiness from annexed cities", UniqueTarget.Building),
    ConnectTradeRoutes("Connects trade routes over water", UniqueTarget.Building),
    GainBuildingWhereBuildable("Automatically built in all cities where it is buildable", UniqueTarget.Building),

    CreatesOneImprovement("Creates a [improvementName] improvement on a specific tile", UniqueTarget.Building),
    //endregion

    ///////////////////////////////////////// region 04 UNIT UNIQUES /////////////////////////////////////////

    // Unit action uniques
    // Unit actions should look like: "Can {action description}, to allow them to be combined with modifiers

    FoundCity("Founds a new city", UniqueTarget.UnitAction),
    ConstructImprovementInstantly("Can instantly construct a [improvementFilter] improvement", UniqueTarget.UnitAction),
    CanSpreadReligion("Can Spread Religion", UniqueTarget.UnitAction),
    CanRemoveHeresy("Can remove other religions from cities", UniqueTarget.UnitAction),
    MayFoundReligion("May found a religion", UniqueTarget.UnitAction),
    MayEnhanceReligion("May enhance a religion", UniqueTarget.UnitAction),

    BuildImprovements("Can build [improvementFilter/terrainFilter] improvements on tiles", UniqueTarget.Unit),
    CreateWaterImprovements("May create improvements on water resources", UniqueTarget.Unit),

    AddInCapital("Can be added to [comment] in the Capital", UniqueTarget.Unit),
    PreventSpreadingReligion("Prevents spreading of religion to the city it is next to", UniqueTarget.Unit),
    RemoveOtherReligions("Removes other religions when spreading religion", UniqueTarget.Unit),

    MayParadrop("May Paradrop up to [amount] tiles from inside friendly territory", UniqueTarget.Unit),
    CanAirsweep("Can perform Air Sweep", UniqueTarget.Unit),

    @Deprecated("As of 4.8.9", ReplaceWith("Can Spread Religion <[amount] times> <after which this unit is consumed>\" OR \"Can remove other religions from cities <in [Friendly] tiles> <once> <after which this unit is consumed>"))
    CanActionSeveralTimes("Can [action] [amount] times", UniqueTarget.Unit),

    CanSpeedupConstruction("Can speed up construction of a building", UniqueTarget.Unit),
    CanSpeedupWonderConstruction("Can speed up the construction of a wonder", UniqueTarget.Unit),
    CanHurryResearch("Can hurry technology research", UniqueTarget.Unit),
    CanTradeWithCityStateForGoldAndInfluence("Can undertake a trade mission with City-State, giving a large sum of gold and [amount] Influence", UniqueTarget.Unit),
    CanTransform("Can transform to [unit]", UniqueTarget.Unit),

    AutomationPrimaryAction("Automation is a primary action", UniqueTarget.Unit, flags = UniqueFlag.setOfHiddenToUsers),

    // Strength bonuses
    Strength("[relativeAmount]% Strength", UniqueTarget.Unit, UniqueTarget.Global),
    StrengthNearCapital("[relativeAmount]% Strength decreasing with distance from the capital", UniqueTarget.Unit, UniqueTarget.Global),
    FlankAttackBonus("[relativeAmount]% to Flank Attack bonuses", UniqueTarget.Unit, UniqueTarget.Global),
    // There's currently no conditional that would allow you strength vs city-state *cities* and that's why this isn't deprecated yet
    StrengthBonusVsCityStates("+30% Strength when fighting City-State units and cities", UniqueTarget.Global),
    StrengthForAdjacentEnemies("[relativeAmount]% Strength for enemy [combatantFilter] units in adjacent [tileFilter] tiles", UniqueTarget.Unit),
    StrengthWhenStacked("[relativeAmount]% Strength when stacked with [mapUnitFilter]", UniqueTarget.Unit),  // candidate for conditional!
    StrengthBonusInRadius("[relativeAmount]% Strength bonus for [mapUnitFilter] units within [amount] tiles", UniqueTarget.Unit),

    // Stat bonuses
    AdditionalAttacks("[amount] additional attacks per turn", UniqueTarget.Unit, UniqueTarget.Global),
    Movement("[amount] Movement", UniqueTarget.Unit, UniqueTarget.Global),
    Sight("[amount] Sight", UniqueTarget.Unit, UniqueTarget.Global, UniqueTarget.Terrain),
    Range("[amount] Range", UniqueTarget.Unit, UniqueTarget.Global),
    AirInterceptionRange("[relativeAmount] Air Interception Range", UniqueTarget.Unit, UniqueTarget.Global),
    Heal("[amount] HP when healing", UniqueTarget.Unit, UniqueTarget.Global),

    SpreadReligionStrength("[relativeAmount]% Spread Religion Strength", UniqueTarget.Unit, UniqueTarget.Global),
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
    IndirectFire("Ranged attacks may be performed over obstacles", UniqueTarget.Unit),
    NuclearWeapon("Nuclear weapon of Strength [amount]", UniqueTarget.Unit),

    NoDefensiveTerrainBonus("No defensive terrain bonus", UniqueTarget.Unit, UniqueTarget.Global),
    NoDefensiveTerrainPenalty("No defensive terrain penalty", UniqueTarget.Unit, UniqueTarget.Global),
    NoDamagePenalty("Damage is ignored when determining unit Strength", UniqueTarget.Unit, UniqueTarget.Global),
    Uncapturable("Uncapturable", UniqueTarget.Unit),
    // Replace with "Withdraws before melee combat <with [amount]% chance>"?
    MayWithdraw("May withdraw before melee ([amount]%)", UniqueTarget.Unit),
    CannotCaptureCities("Unable to capture cities", UniqueTarget.Unit),
    CannotPillage("Unable to pillage tiles", UniqueTarget.Unit),

    // Movement
    NoMovementToPillage("No movement cost to pillage", UniqueTarget.Unit, UniqueTarget.Global),
    CanMoveAfterAttacking("Can move after attacking", UniqueTarget.Unit),
    TransferMovement("Transfer Movement to [mapUnitFilter]", UniqueTarget.Unit),
    MoveImmediatelyOnceBought("Can move immediately once bought", UniqueTarget.Unit),

    // Healing
    HealsOutsideFriendlyTerritory("May heal outside of friendly territory", UniqueTarget.Unit, UniqueTarget.Global),
    HealingEffectsDoubled("All healing effects doubled", UniqueTarget.Unit, UniqueTarget.Global),
    HealsAfterKilling("Heals [amount] damage if it kills a unit", UniqueTarget.Unit, UniqueTarget.Global),
    HealOnlyByPillaging("Can only heal by pillaging", UniqueTarget.Unit, UniqueTarget.Global),
    HealsEvenAfterAction("Unit will heal every turn, even if it performs an action", UniqueTarget.Unit),
    HealAdjacentUnits("All adjacent units heal [amount] HP when healing", UniqueTarget.Unit),

    // Vision

    DefenceBonusWhenEmbarked("Defense bonus when embarked", UniqueTarget.Unit, UniqueTarget.Global),
    NoSight("No Sight", UniqueTarget.Unit),
    CanSeeOverObstacles("Can see over obstacles", UniqueTarget.Unit),

    // Carrying
    CarryAirUnits("Can carry [amount] [mapUnitFilter] units", UniqueTarget.Unit),
    CarryExtraAirUnits("Can carry [amount] extra [mapUnitFilter] units", UniqueTarget.Unit),
    CannotBeCarriedBy("Cannot be carried by [mapUnitFilter] units", UniqueTarget.Unit),
    // Interception
    ChanceInterceptAirAttacks("[relativeAmount]% chance to intercept air attacks", UniqueTarget.Unit),
    DamageFromInterceptionReduced("Damage taken from interception reduced by [relativeAmount]%", UniqueTarget.Unit),
    DamageWhenIntercepting("[relativeAmount]% Damage when intercepting", UniqueTarget.Unit),
    ExtraInterceptionsPerTurn("[amount] extra interceptions may be made per turn", UniqueTarget.Unit),
    CannotBeIntercepted("Cannot be intercepted", UniqueTarget.Unit),
    CannotInterceptUnits("Cannot intercept [mapUnitFilter] units", UniqueTarget.Unit),
    StrengthWhenAirsweep("[relativeAmount]% Strength when performing Air Sweep", UniqueTarget.Unit),

    UnitMaintenanceDiscount("[relativeAmount]% maintenance costs", UniqueTarget.Unit, UniqueTarget.Global),
    UnitUpgradeCost("[relativeAmount]% Gold cost of upgrading", UniqueTarget.Unit, UniqueTarget.Global),

    // Gains from battle
    DamageUnitsPlunder("Earn [amount]% of the damage done to [combatantFilter] units as [civWideStat]", UniqueTarget.Unit, UniqueTarget.Global),
    CaptureCityPlunder("Upon capturing a city, receive [amount] times its [stat] production as [civWideStat] immediately", UniqueTarget.Unit, UniqueTarget.Global),
    KillUnitPlunder("Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [civWideStat]", UniqueTarget.Unit, UniqueTarget.Global),
    KillUnitPlunderNearCity("Earn [amount]% of [mapUnitFilter] unit's [costOrStrength] as [civWideStat] when killed within 4 tiles of a city following this religion", UniqueTarget.FollowerBelief),
    KillUnitCapture("May capture killed [mapUnitFilter] units", UniqueTarget.Unit),

    // XP
    FlatXPGain("[amount] XP gained from combat", UniqueTarget.Unit, UniqueTarget.Global),
    PercentageXPGain("[relativeAmount]% XP gained from combat", UniqueTarget.Unit, UniqueTarget.Global),
    GreatPersonEarnedFaster("[greatPerson] is earned [relativeAmount]% faster", UniqueTarget.Unit, UniqueTarget.Global),

    // Invisibility
    Invisible("Invisible to others", UniqueTarget.Unit),
    InvisibleToNonAdjacent("Invisible to non-adjacent units", UniqueTarget.Unit),
    CanSeeInvisibleUnits("Can see invisible [mapUnitFilter] units", UniqueTarget.Unit),

    RuinsUpgrade("May upgrade to [baseUnitFilter] through ruins-like effects", UniqueTarget.Unit),

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
    CannotEnterOcean("Cannot enter ocean tiles", UniqueTarget.Unit),
    CanEnterForeignTiles("May enter foreign tiles without open borders", UniqueTarget.Unit),
    CanEnterForeignTilesButLosesReligiousStrength("May enter foreign tiles without open borders, but loses [amount] religious strength each turn it ends there", UniqueTarget.Unit),
    ReducedDisembarkCost("[amount] Movement point cost to disembark", UniqueTarget.Global, UniqueTarget.Unit),
    ReducedEmbarkCost("[amount] Movement point cost to embark", UniqueTarget.Global, UniqueTarget.Unit),
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
    GPPointPool("Is part of Great Person group [comment]", UniqueTarget.Unit),

    //endregion

    ///////////////////////////////////////// region 05 UNIT ACTION MODIFIERS /////////////////////////////////////////

    UnitActionConsumeUnit("by consuming this unit", UniqueTarget.UnitActionModifier),
    UnitActionMovementCost("for [amount] movement", UniqueTarget.UnitActionModifier),
    UnitActionOnce("once", UniqueTarget.UnitActionModifier),
    UnitActionLimitedTimes("[amount] times", UniqueTarget.UnitActionModifier),
    UnitActionExtraLimitedTimes("[amount] additional time(s)", UniqueTarget.UnitActionModifier),
    UnitActionAfterWhichConsumed("after which this unit is consumed", UniqueTarget.UnitActionModifier),

    // endregion

    ///////////////////////////////////////// region 06 TILE UNIQUES /////////////////////////////////////////

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
    TileGenerationConditions("Occurs at temperature between [fraction] and [fraction] and humidity between [fraction] and [fraction]", UniqueTarget.Terrain, UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),
    OccursInChains("Occurs in chains at high elevations", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    OccursInGroups("Occurs in groups around high elevations", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),
    MajorStrategicFrequency("Every [amount] tiles with this terrain will receive a major deposit of a strategic resource.", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers),

    RareFeature("Rare feature", UniqueTarget.Terrain),

    DestroyableByNukesChance("[amount]% Chance to be destroyed by nukes", UniqueTarget.Terrain),

    FreshWater(Constants.freshWater, UniqueTarget.Terrain),
    RoughTerrain("Rough terrain", UniqueTarget.Terrain),

    ExcludedFromMapEditor("Excluded from map editor", UniqueTarget.Terrain, UniqueTarget.Improvement, UniqueTarget.Resource, UniqueTarget.Nation, flags = UniqueFlag.setOfHiddenToUsers),

    /////// Resource uniques
    ResourceAmountOnTiles("Deposits in [tileFilter] tiles always provide [amount] resources", UniqueTarget.Resource),
    CityStateOnlyResource("Can only be created by Mercantile City-States", UniqueTarget.Resource),
    Stockpiled("Stockpiled", UniqueTarget.Resource),
    CityResource("City-level resource", UniqueTarget.Resource),
    CannotBeTraded("Cannot be traded", UniqueTarget.Resource),
    NotShownOnWorldScreen("Not shown on world screen", UniqueTarget.Resource, flags = UniqueFlag.setOfHiddenToUsers),

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
    EnsureMinimumStats("Ensures a minimum tile yield of [stats]", UniqueTarget.Improvement), // City center

    CanBuildOutsideBorders("Can be built outside your borders", UniqueTarget.Improvement),
    CanBuildJustOutsideBorders("Can be built just outside your borders", UniqueTarget.Improvement),
    CanOnlyBeBuiltOnTile("Can only be built on [tileFilter] tiles", UniqueTarget.Improvement),
    CannotBuildOnTile("Cannot be built on [tileFilter] tiles", UniqueTarget.Improvement),
    CanOnlyImproveResource("Can only be built to improve a resource", UniqueTarget.Improvement),
    NoFeatureRemovalNeeded("Does not need removal of [tileFilter]", UniqueTarget.Improvement),
    RemovesFeaturesIfBuilt("Removes removable features when built", UniqueTarget.Improvement),

    DefensiveBonus("Gives a defensive bonus of [relativeAmount]%", UniqueTarget.Improvement),
    ImprovementMaintenance("Costs [amount] [stat] per turn when in your territory", UniqueTarget.Improvement), // Roads
    ImprovementAllMaintenance("Costs [amount] [stat] per turn", UniqueTarget.Improvement), // Roads
    DamagesAdjacentEnemyUnits("Adjacent enemy units ending their turn take [amount] damage", UniqueTarget.Improvement),

    GreatImprovement("Great Improvement", UniqueTarget.Improvement),
    IsAncientRuinsEquivalent("Provides a random bonus when entered", UniqueTarget.Improvement),
    TakesOverAdjacentTiles("Constructing it will take over the tiles around it and assign them to your closest city", UniqueTarget.Improvement),

    Unpillagable("Unpillagable", UniqueTarget.Improvement),
    PillageYieldRandom("Pillaging this improvement yields approximately [stats]", UniqueTarget.Improvement),
    PillageYieldFixed("Pillaging this improvement yields [stats]", UniqueTarget.Improvement),
    Irremovable("Irremovable", UniqueTarget.Improvement),
    AutomatedWorkersWillReplace("Will be replaced by automated workers", UniqueTarget.Improvement),
    //endregion

    ///////////////////////////////////////// region 07 CONDITIONALS /////////////////////////////////////////


    /////// general conditionals
    ConditionalTimedUnique("for [amount] turns", UniqueTarget.Conditional),
    ConditionalChance("with [amount]% chance", UniqueTarget.Conditional),
    ConditionalBeforeTurns("before [amount] turns", UniqueTarget.Conditional),

    ConditionalAfterTurns("after [amount] turns", UniqueTarget.Conditional),


    /////// civ conditionals
    ConditionalCivFilter("for [civFilter]", UniqueTarget.Conditional),
    ConditionalWar("when at war", UniqueTarget.Conditional),
    ConditionalNotWar("when not at war", UniqueTarget.Conditional),
    ConditionalGoldenAge("during a Golden Age", UniqueTarget.Conditional),
    ConditionalWLTKD("during We Love The King Day", UniqueTarget.Conditional),

    ConditionalHappy("while the empire is happy", UniqueTarget.Conditional),
    ConditionalBetweenHappiness("when between [amount] and [amount] Happiness", UniqueTarget.Conditional),
    ConditionalBelowHappiness("when below [amount] Happiness", UniqueTarget.Conditional),

    ConditionalDuringEra("during the [era]", UniqueTarget.Conditional),
    ConditionalBeforeEra("before the [era]", UniqueTarget.Conditional),
    ConditionalStartingFromEra("starting from the [era]", UniqueTarget.Conditional),
    ConditionalIfStartingInEra("if starting in the [era]", UniqueTarget.Conditional),

    ConditionalFirstCivToResearch("if no other Civilization has researched this", UniqueTarget.Conditional),
    ConditionalTech("after discovering [tech]", UniqueTarget.Conditional),
    ConditionalNoTech("before discovering [tech]", UniqueTarget.Conditional),

    ConditionalFirstCivToAdopt("if no other Civilization has adopted this", UniqueTarget.Conditional),
    ConditionalAfterPolicyOrBelief("after adopting [policy/belief]", UniqueTarget.Conditional),
    ConditionalBeforePolicyOrBelief("before adopting [policy/belief]", UniqueTarget.Conditional),

    ConditionalBeforePantheon("before founding a Pantheon", UniqueTarget.Conditional),
    ConditionalAfterPantheon("after founding a Pantheon", UniqueTarget.Conditional),
    ConditionalBeforeReligion("before founding a religion", UniqueTarget.Conditional),
    ConditionalAfterReligion("after founding a religion", UniqueTarget.Conditional),
    ConditionalBeforeEnhancingReligion("before enhancing a religion", UniqueTarget.Conditional),
    ConditionalAfterEnhancingReligion("after enhancing a religion", UniqueTarget.Conditional),

    ConditionalBuildingBuilt("if [buildingFilter] is constructed", UniqueTarget.Conditional),

    ConditionalWithResource("with [resource]", UniqueTarget.Conditional),
    ConditionalWithoutResource("without [resource]", UniqueTarget.Conditional),

    // Supports also stockpileable resources (Gold, Faith, Culture, Science)
    ConditionalWhenAboveAmountStatResource("when above [amount] [stat/resource]", UniqueTarget.Conditional),
    ConditionalWhenBelowAmountStatResource("when below [amount] [stat/resource]", UniqueTarget.Conditional),

    // The game speed-adjusted versions of above
    ConditionalWhenAboveAmountStatSpeed("when above [amount] [stat] (modified by game speed)", UniqueTarget.Conditional),
    ConditionalWhenBelowAmountStatSpeed("when below [amount] [stat] (modified by game speed)", UniqueTarget.Conditional),

    /////// city conditionals
    ConditionalInThisCity("in this city", UniqueTarget.Conditional),
    ConditionalCityWithBuilding("in cities with a [buildingFilter]", UniqueTarget.Conditional),
    ConditionalCityWithoutBuilding("in cities without a [buildingFilter]", UniqueTarget.Conditional),
    ConditionalPopulationFilter("in cities with at least [amount] [populationFilter]", UniqueTarget.Conditional),
    ConditionalWhenGarrisoned("with a garrison", UniqueTarget.Conditional),

    /////// unit conditionals
    ConditionalOurUnit("for [mapUnitFilter] units", UniqueTarget.Conditional),
    ConditionalOurUnitOnUnit("when [mapUnitFilter]", UniqueTarget.Conditional), // Same but for the unit itself
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

    ///////////////////////////////////////// region 08 TRIGGERED ONE-TIME /////////////////////////////////////////


    OneTimeFreeUnit("Free [unit] appears", UniqueTarget.Triggerable),  // used in Policies, Buildings
    OneTimeAmountFreeUnits("[amount] free [unit] units appear", UniqueTarget.Triggerable), // used in Buildings
    OneTimeFreeUnitRuins("Free [unit] found in the ruins", UniqueTarget.Ruins), // Differs from "Free [] appears" in that it spawns near the ruins instead of in a city
    OneTimeFreePolicy("Free Social Policy", UniqueTarget.Triggerable), // used in Buildings
    OneTimeAmountFreePolicies("[amount] Free Social Policies", UniqueTarget.Triggerable),  // Not used in Vanilla
    OneTimeEnterGoldenAge("Empire enters golden age", UniqueTarget.Triggerable),  // used in Policies, Buildings
    OneTimeEnterGoldenAgeTurns("Empire enters a [amount]-turn Golden Age", UniqueTarget.Triggerable),
    OneTimeFreeGreatPerson("Free Great Person", UniqueTarget.Triggerable),  // used in Policies, Buildings
    OneTimeGainPopulation("[amount] population [cityFilter]", UniqueTarget.Triggerable),  // used in CN tower
    OneTimeGainPopulationRandomCity("[amount] population in a random city", UniqueTarget.Triggerable),
    OneTimeDiscoverTech("Discover [tech]", UniqueTarget.Triggerable),
    OneTimeAdoptPolicy("Adopt [policy]", UniqueTarget.Triggerable),
    OneTimeFreeTech("Free Technology", UniqueTarget.Triggerable),  // used in Buildings
    OneTimeAmountFreeTechs("[amount] Free Technologies", UniqueTarget.Triggerable),  // used in Policy
    OneTimeFreeTechRuins("[amount] free random researchable Tech(s) from the [era]", UniqueTarget.Triggerable),
    OneTimeRevealEntireMap("Reveals the entire map", UniqueTarget.Triggerable),  // used in tech
    OneTimeFreeBelief("Gain a free [beliefType] belief", UniqueTarget.Triggerable),
    OneTimeTriggerVoting("Triggers voting for the Diplomatic Victory", UniqueTarget.Triggerable),  // used in Building

    OneTimeConsumeResources("Instantly consumes [amount] [stockpiledResource]", UniqueTarget.Triggerable),
    OneTimeProvideResources("Instantly provides [amount] [stockpiledResource]", UniqueTarget.Triggerable),

    OneTimeGainStat("Gain [amount] [stat]", UniqueTarget.Triggerable),
    OneTimeGainStatSpeed("Gain [amount] [stat] (modified by game speed)", UniqueTarget.Triggerable),
    OneTimeGainStatRange("Gain [amount]-[amount] [stat]", UniqueTarget.Triggerable),
    OneTimeGainPantheon("Gain enough Faith for a Pantheon", UniqueTarget.Triggerable),
    OneTimeGainProphet("Gain enough Faith for [amount]% of a Great Prophet", UniqueTarget.Triggerable),
    // todo: The "up to [All]" used in vanilla json is not nice to read. Split?
    // Or just reword it without the 'up to', so it reads "Reveal [amount/'all'] [tileFilter] tiles within [amount] tiles"
    OneTimeRevealSpecificMapTiles("Reveal up to [amount/'all'] [tileFilter] within a [amount] tile radius", UniqueTarget.Triggerable),
    OneTimeRevealCrudeMap("From a randomly chosen tile [amount] tiles away from the ruins, reveal tiles up to [amount] tiles away with [amount]% chance", UniqueTarget.Ruins),
    OneTimeGlobalAlert("Triggers the following global alert: [comment]", UniqueTarget.Triggerable), // used in Policy
    OneTimeGlobalSpiesWhenEnteringEra("Every major Civilization gains a spy once a civilization enters this era", UniqueTarget.Era),

    OneTimeUnitHeal("Heal this unit by [amount] HP", UniqueTarget.UnitTriggerable),
    OneTimeUnitGainXP("This Unit gains [amount] XP", UniqueTarget.UnitTriggerable),
    OneTimeUnitUpgrade("This Unit upgrades for free", UniqueTarget.UnitTriggerable),  // Not used in Vanilla
    OneTimeUnitSpecialUpgrade("This Unit upgrades for free including special upgrades", UniqueTarget.UnitTriggerable),
    OneTimeUnitGainPromotion("This Unit gains the [promotion] promotion", UniqueTarget.UnitTriggerable),  // Not used in Vanilla
    SkipPromotion("Doing so will consume this opportunity to choose a Promotion", UniqueTarget.Promotion),
    FreePromotion("This Promotion is free", UniqueTarget.Promotion),

    UnitsGainPromotion("[mapUnitFilter] units gain the [promotion] promotion", UniqueTarget.Triggerable),  // Not used in Vanilla
    FreeStatBuildings("Provides the cheapest [stat] building in your first [amount] cities for free", UniqueTarget.Triggerable),  // used in Policy
    FreeSpecificBuildings("Provides a [buildingName] in your first [amount] cities for free", UniqueTarget.Triggerable),  // used in Policy

    //endregion


    ///////////////////////////////////////// region 09 TRIGGERS /////////////////////////////////////////

    TriggerUponResearch("upon discovering [tech]", UniqueTarget.TriggerCondition),
    TriggerUponEnteringEra("upon entering the [era]", UniqueTarget.TriggerCondition),
    TriggerUponAdoptingPolicyOrBelief("upon adopting [policy/belief]", UniqueTarget.TriggerCondition),
    TriggerUponDeclaringWar("upon declaring war with a major Civilization", UniqueTarget.TriggerCondition),
    TriggerUponDeclaringFriendship("upon declaring friendship", UniqueTarget.TriggerCondition),
    TriggerUponSigningDefensivePact("upon declaring a defensive pact", UniqueTarget.TriggerCondition),
    TriggerUponEnteringGoldenAge("upon entering a Golden Age", UniqueTarget.TriggerCondition),
    /** Can be placed upon both units and as global */
    TriggerUponConqueringCity("upon conquering a city", UniqueTarget.TriggerCondition, UniqueTarget.UnitTriggerCondition),
    TriggerUponFoundingCity("upon founding a city", UniqueTarget.TriggerCondition),
    TriggerUponDiscoveringNaturalWonder("upon discovering a Natural Wonder", UniqueTarget.TriggerCondition),
    TriggerUponConstructingBuilding("upon constructing [buildingFilter]", UniqueTarget.TriggerCondition),
    // We have a separate trigger to include the cityFilter, since '[in all cities]' can be read '*only* if it's in all cities'
    TriggerUponConstructingBuildingCityFilter("upon constructing [buildingFilter] [cityFilter]", UniqueTarget.TriggerCondition),
    TriggerUponGainingUnit("upon gaining a [baseUnitFilter] unit", UniqueTarget.TriggerCondition),

    TriggerUponFoundingPantheon("upon founding a Pantheon", UniqueTarget.TriggerCondition),
    TriggerUponFoundingReligion("upon founding a Religion", UniqueTarget.TriggerCondition),
    TriggerUponEnhancingReligion("upon enhancing a Religion", UniqueTarget.TriggerCondition),

    //endregion


    ///////////////////////////////////////// region 10 UNIT TRIGGERS /////////////////////////////////////////

    TriggerUponDefeatingUnit("upon defeating a [mapUnitFilter] unit", UniqueTarget.UnitTriggerCondition),
    TriggerUponDefeat("upon being defeated", UniqueTarget.UnitTriggerCondition),
    TriggerUponPromotion("upon being promoted", UniqueTarget.UnitTriggerCondition),
    TriggerUponLosingHealth("upon losing at least [amount] HP in a single attack", UniqueTarget.UnitTriggerCondition),
    TriggerUponEndingTurnInTile("upon ending a turn in a [tileFilter] tile", UniqueTarget.UnitTriggerCondition),
    TriggerUponDiscoveringTile("upon discovering a [tileFilter] tile", UniqueTarget.UnitTriggerCondition),

    //endregion

    ///////////////////////////////////////////// region 90 META /////////////////////////////////////////////
    HiddenWithoutReligion("Hidden when religion is disabled",
        UniqueTarget.Unit, UniqueTarget.Building, UniqueTarget.Ruins, UniqueTarget.Tutorial,
        flags = UniqueFlag.setOfHiddenToUsers),

    HiddenAfterGreatProphet("Hidden after generating a Great Prophet", UniqueTarget.Ruins),
    HiddenWithoutVictoryType("Hidden when [victoryType] Victory is disabled", UniqueTarget.Building, UniqueTarget.Unit, flags = UniqueFlag.setOfHiddenToUsers),
    HiddenFromCivilopedia("Will not be displayed in Civilopedia", *UniqueTarget.Displayable, flags = UniqueFlag.setOfHiddenToUsers),
    ConditionalHideUniqueFromUsers("hidden from users", UniqueTarget.Conditional),
    Comment("Comment [comment]", *UniqueTarget.Displayable,
        docDescription = "Allows displaying arbitrary text in a Unique listing. Only the text within the '[]' brackets will be displayed, the rest serves to allow Ruleset validation to recognize the intent."),

    // Declarative Mod compatibility (so far rudimentary):
    ModIncompatibleWith("Mod is incompatible with [modFilter]", UniqueTarget.ModOptions),
    ModIsAudioVisualOnly("Should only be used as permanent audiovisual mod", UniqueTarget.ModOptions),
    ModIsAudioVisual("Can be used as permanent audiovisual mod", UniqueTarget.ModOptions),
    ModIsNotAudioVisual("Cannot be used as permanent audiovisual mod", UniqueTarget.ModOptions),

    // endregion

    ///////////////////////////////////////////// region 99 DEPRECATED AND REMOVED /////////////////////////////////////////////

    @Deprecated("As of 4.7.3", ReplaceWith("[+100]% unhappiness from the number of cities"), DeprecationLevel.ERROR)
    UnhappinessFromCitiesDoubled("Unhappiness from number of Cities doubled", UniqueTarget.Global),
    @Deprecated("as of 4.6.4", ReplaceWith("[+1] Sight <for [Embarked] units>\" OR \"[+1] Sight <when [Embarked]>"), DeprecationLevel.ERROR)
    NormalVisionWhenEmbarked("Normal vision when embarked", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("as of 4.5.3", ReplaceWith("Empire enters a [amount]-turn Golden Age <by consuming this unit>"), DeprecationLevel.ERROR)
    StartGoldenAge("Can start an [amount]-turn golden age", UniqueTarget.Unit),
    @Deprecated("as of 4.5.2", ReplaceWith("Can instantly construct a [improvementName] improvement <by consuming this unit>"), DeprecationLevel.ERROR)
    ConstructImprovementConsumingUnit("Can construct [improvementName]", UniqueTarget.Unit),
    @Deprecated("as of 4.3.9", ReplaceWith("Costs [amount] [stats] per turn when in your territory"), DeprecationLevel.ERROR)
    OldImprovementMaintenance("Costs [amount] gold per turn when in your territory", UniqueTarget.Improvement),
    @Deprecated("as of 4.3.4", ReplaceWith("[+1 Happiness] per [2] social policies adopted"), DeprecationLevel.ERROR)
    HappinessPer2Policies("Provides 1 happiness per 2 additional social policies adopted", UniqueTarget.Global),
    @Deprecated("as of 4.3.6", ReplaceWith("[+1 Happiness] for every known Natural Wonder"), DeprecationLevel.ERROR)
    DoubleHappinessFromNaturalWonders("Double Happiness from Natural Wonders", UniqueTarget.Global),
    @Deprecated("as of 4.2.18", ReplaceWith("Only available <after [amount] turns>"), DeprecationLevel.ERROR)
    AvailableAfterCertainTurns("Only available after [amount] turns", UniqueTarget.Ruins),
    @Deprecated("as of 4.2.18", ReplaceWith("Only available <before founding a Pantheon>"), DeprecationLevel.ERROR)
    HiddenBeforePantheon("Hidden before founding a Pantheon", UniqueTarget.Ruins),
    @Deprecated("as of 4.2.18", ReplaceWith("Only available <before founding a Pantheon>"), DeprecationLevel.ERROR)
    HiddenAfterPantheon("Hidden after founding a Pantheon", UniqueTarget.Ruins),
    @Deprecated("as of 4.3.4", ReplaceWith("[stats]"), DeprecationLevel.ERROR)
    CityStateStatsPerTurn("Provides [stats] per turn", UniqueTarget.CityState), // Should not be Happiness!
    @Deprecated("as of 4.3.4", ReplaceWith("[stats] [cityFilter]"), DeprecationLevel.ERROR)
    CityStateStatsPerCity("Provides [stats] [cityFilter] per turn", UniqueTarget.CityState),
    @Deprecated("as of 4.3.4", ReplaceWith("[+amount Happiness]"), DeprecationLevel.ERROR)
    CityStateHappiness("Provides [amount] Happiness", UniqueTarget.CityState),
    @Deprecated("As of 4.2.4", ReplaceWith("Enemy [Land] units must spend [1] extra movement points when inside your territory <before discovering [Dynamite]>"), DeprecationLevel.ERROR)
    EnemyLandUnitsSpendExtraMovementDepreciated("Enemy land units must spend 1 extra movement point when inside your territory (obsolete upon Dynamite)", UniqueTarget.Global),
    @Deprecated("as of 4.1.7", ReplaceWith("Can construct [improvementName] <if it hasn't used other actions yet>"), DeprecationLevel.ERROR)
    CanConstructIfNoOtherActions("Can construct [improvementName] if it hasn't used other actions yet", UniqueTarget.Unit),
    @Deprecated("s of 4.1.14", ReplaceWith("Production to [Science] conversion in cities changed by [33]%"), DeprecationLevel.ERROR)
    ProductionToScienceConversionBonus("Production to science conversion in cities increased by 33%", UniqueTarget.Global),
    @Deprecated("As of 4.1.19", ReplaceWith("[+100]% Yield from every [Natural Wonder]"), DeprecationLevel.ERROR)
    DoubleStatsFromNaturalWonders("Tile yields from Natural Wonders doubled", UniqueTarget.Global),
    @Deprecated("As of 4.1.14", ReplaceWith("Enables conversion of city production to [Gold]"), DeprecationLevel.ERROR)
    EnablesGoldProduction("Enables conversion of city production to gold", UniqueTarget.Global),
    @Deprecated("s of 4.1.14", ReplaceWith("Enables conversion of city production to [Science]"), DeprecationLevel.ERROR)
    EnablesScienceProduction("Enables conversion of city production to science", UniqueTarget.Global),
    @Deprecated("as of 4.0.3", ReplaceWith("Damage is ignored when determining unit Strength <for [All] units>"), DeprecationLevel.ERROR)
    UnitsFightFullStrengthWhenDamaged("Units fight as though they were at full strength even when damaged", UniqueTarget.Global),
    @Deprecated("as of 4.0.3", ReplaceWith("[+amount]% Strength <within [amount2] tiles of a [tileFilter]>"), DeprecationLevel.ERROR)
    StrengthWithinTilesOfTile("+[amount]% Strength if within [amount2] tiles of a [tileFilter]", UniqueTarget.Global),
    @Deprecated("as of 3.19.7", ReplaceWith("[stats] <with [resource]>"), DeprecationLevel.ERROR)
    StatsWithResource("[stats] with [resource]", UniqueTarget.Building),
    @Deprecated("as of 3.19.16", ReplaceWith("Can only be built [in annexed cities]"), DeprecationLevel.ERROR)
    CanOnlyBeBuiltInAnnexedCities("Can only be built in annexed cities", UniqueTarget.Building),
    @Deprecated("as of 4.0.3", ReplaceWith("Defense bonus when embarked <for [All] units>"), DeprecationLevel.ERROR)
    DefenceBonusWhenEmbarkedCivwide("Embarked units can defend themselves", UniqueTarget.Global),
    @Deprecated("as of 4.0.3", ReplaceWith("[1] Movement point cost to disembark <for [All] units>"), DeprecationLevel.ERROR)
    DisembarkCostDeprecated("Units pay only 1 movement point to disembark", UniqueTarget.Global),

    @Deprecated("as of 4.0.3", ReplaceWith("When conquering an encampment, earn [25] Gold and recruit a Barbarian unit <with [67]% chance>"), DeprecationLevel.ERROR)
    ChanceToRecruitBarbarianFromEncampment("67% chance to earn 25 Gold and recruit a Barbarian unit from a conquered encampment", UniqueTarget.Global),
    @Deprecated("as of 4.0.3", ReplaceWith("When defeating a [{Barbarian} {Water}] unit, earn [25] Gold and recruit it <with [50]% chance>"), DeprecationLevel.ERROR)
    ChanceToRecruitNavalBarbarian("50% chance of capturing defeated Barbarian naval units and earning 25 Gold", UniqueTarget.Global),

    @Deprecated("as of 3.19.8", ReplaceWith("Eliminates combat penalty for attacking across a coast"), DeprecationLevel.ERROR)
    AttackFromSea("Eliminates combat penalty for attacking from the sea", UniqueTarget.Unit),
    @Deprecated("as of 3.19.19", ReplaceWith("[+4] Sight\", \"Can see over obstacles"), DeprecationLevel.ERROR)
    SixTilesAlwaysVisible("6 tiles in every direction always visible", UniqueTarget.Unit),
    @Deprecated("as of 3.19.19", ReplaceWith("[25]% Chance to be destroyed by nukes"), DeprecationLevel.ERROR)
    ResistsNukes("Resistant to nukes", UniqueTarget.Terrain),
    @Deprecated("as of 3.19.19", ReplaceWith("[50]% Chance to be destroyed by nukes"), DeprecationLevel.ERROR)
    DestroyableByNukes("Can be destroyed by nukes", UniqueTarget.Terrain),
    @Deprecated("as of 3.19.19", ReplaceWith("in cities with at least [amount] [Specialists]"), DeprecationLevel.ERROR)
    ConditionalSpecialistCount("if this city has at least [amount] specialists", UniqueTarget.Conditional),
    @Deprecated("as of 3.19.19", ReplaceWith("in cities with at least [amount] [Followers of the Majority Religion]"), DeprecationLevel.ERROR)
    ConditionalFollowerCount("in cities where this religion has at least [amount] followers", UniqueTarget.Conditional),
    @Deprecated("as of 3.19.8", ReplaceWith("[+amount]% Strength <when attacking> <for [mapUnitFilter] units> <for [amount2] turns>"), DeprecationLevel.ERROR)
    TimedAttackStrength("+[amount]% attack strength to all [mapUnitFilter] units for [amount2] turns", UniqueTarget.Global),  // used in Policy
    @Deprecated("as of 3.19.13", ReplaceWith("Enables [Embarked] units to enter ocean tiles <starting from the [Ancient era]>"), DeprecationLevel.ERROR)
    EmbarkedUnitsMayEnterOcean("Enables embarked units to enter ocean tiles", UniqueTarget.Global),
    @Deprecated("as of 3.19.9", ReplaceWith("Enables embarkation for land units <starting from the [Ancient era]>\", \"Enables [All] units to enter ocean tiles <starting from the [Ancient era]>"), DeprecationLevel.ERROR)
    EmbarkAndEnterOcean("Can embark and move over Coasts and Oceans immediately", UniqueTarget.Global),
    @Deprecated("As of 3.19.19", ReplaceWith("[relativeAmount]% Unhappiness from [Population] [cityFilter]"), DeprecationLevel.ERROR)
    UnhappinessFromPopulationPercentageChange("[relativeAmount]% unhappiness from population [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("As of 3.19.19", ReplaceWith("[relativeAmount]% Unhappiness from [Specialists] [cityFilter]"), DeprecationLevel.ERROR)
    UnhappinessFromSpecialistsPercentageChange("[relativeAmount]% unhappiness from specialists [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("As of 3.19.19", ReplaceWith("[relativeAmount]% Great Person generation [cityFilter]"), DeprecationLevel.ERROR)
    GreatPersonPointPercentageDeprecated("[relativeAmount]% great person generation [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.19", ReplaceWith("[+25]% [Gold] from Trade Routes"), DeprecationLevel.ERROR)
    GoldBonusFromTradeRoutesDeprecated("Gold from all trade routes +25%", UniqueTarget.Global),
    @Deprecated("as of 3.19.19", ReplaceWith("[stats] <in cities with at least [amount] [Population]>"), DeprecationLevel.ERROR)
    StatsFromXPopulation("[stats] in cities with [amount] or more population", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.8", ReplaceWith("Only available <before adopting [policy/tech/promotion]>" +
            "\" OR \"Only available <before discovering [policy/tech/promotion]>" +
            "\" OR \"Only available <for units without [policy/tech/promotion]>"), DeprecationLevel.ERROR)
    IncompatibleWith("Incompatible with [policy/tech/promotion]", UniqueTarget.Policy, UniqueTarget.Tech, UniqueTarget.Promotion),
    @Deprecated("as of 3.19.8", ReplaceWith("Only available <after adopting [buildingName/tech/resource/policy]>\"" +
            " OR \"Only available <with [buildingName/tech/resource/policy]>\"" +
            " OR \"Only available <if [buildingName/tech/resource/policy] is constructed>\"" +
            " OR \"Only available <after discovering [buildingName/tech/resource/policy]>"), DeprecationLevel.ERROR)
    NotDisplayedWithout("Not displayed as an available construction without [buildingName/tech/resource/policy]", UniqueTarget.Building, UniqueTarget.Unit),

    @Deprecated("as of 3.19.12", ReplaceWith("Only available <after adopting [buildingName/tech/era/policy]>\"" +
            " OR \"Only available <if [buildingName/tech/era/policy] is constructed>\"" +
            " OR \"Only available <starting from the [buildingName/tech/era/policy]>\"" +
            " OR \"Only available <after discovering [buildingName/tech/era/policy]>"), DeprecationLevel.ERROR)
    UnlockedWith("Unlocked with [buildingName/tech/era/policy]", UniqueTarget.Building, UniqueTarget.Unit),


    @Deprecated("as of 3.19.12", ReplaceWith("Only available <after adopting [buildingName/tech/era/policy]>\"" +
            " OR \"Only available <if [buildingName/tech/era/policy] is constructed>\"" +
            " OR \"Only available <starting from the [buildingName/tech/era/policy]>\"" +
            " OR \"Only available <after discovering [buildingName/tech/era/policy]>"), DeprecationLevel.ERROR)
    Requires("Requires [buildingName/tech/era/policy]", UniqueTarget.Building, UniqueTarget.Unit),
    @Deprecated("as of 3.19.9", ReplaceWith("Only available <in cities without a [buildingName]>"), DeprecationLevel.ERROR)
    CannotBeBuiltWith("Cannot be built with [buildingName]", UniqueTarget.Building),
    @Deprecated("as of 3.19.9", ReplaceWith("Only available <in cities with a [buildingName]>"), DeprecationLevel.ERROR)
    RequiresAnotherBuilding("Requires a [buildingName] in this city", UniqueTarget.Building),


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
    BorderGrowthPercentageWithoutPercentageSign("[amount] Culture cost of natural border growth [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
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

    // Keep the endregion after the semicolon or it won't work
    ;
    // endregion

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
        abstract fun getRulesetErrorSeverity(): RulesetErrorSeverity
    }

    fun getDeprecationAnnotation(): Deprecated? = declaringJavaClass.getField(name)
        .getAnnotation(Deprecated::class.java)

    /** Checks whether a specific [uniqueTarget] as e.g. given by [IHasUniques.getUniqueTarget] works with `this` UniqueType */
    fun canAcceptUniqueTarget(uniqueTarget: UniqueTarget) =
        targetTypes.any { uniqueTarget.canAcceptUniqueTarget(it) }

    companion object {
        val uniqueTypeMap: Map<String, UniqueType> = UniqueType.values().associateBy { it.placeholderText }
    }
}
