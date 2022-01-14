package com.unciv.models.ruleset.unique

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/** inheritsFrom means that all such uniques are acceptable as well.
 * For example, all Global uniques are acceptable for Nations, Eras, etc. */
enum class UniqueTarget(val inheritsFrom:UniqueTarget?=null) {

    /** Buildings, units, nations, policies, religions, techs etc.
     * Basically anything caught by CivInfo.getMatchingUniques. */
    Global,

    // Civilization-specific
    Nation(Global),
    Era(Global),
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
    Unit,
    UnitType(Unit),
    Promotion(Unit),

    // Tile-specific
    Terrain,
    Improvement,
    Resource,
    Ruins,

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
}

enum class UniqueType(val text:String, vararg targets: UniqueTarget, val flags: List<UniqueFlag> = emptyList()) {

    //////////////////////////////////////// region GLOBAL UNIQUES ////////////////////////////////////////

    // region Stat providing uniques

    Stats("[stats]", UniqueTarget.Global, UniqueTarget.FollowerBelief, UniqueTarget.Improvement),
    StatsPerCity("[stats] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    StatsFromSpecialist("[stats] from every specialist [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    StatsPerPopulation("[stats] per [amount] population [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsFromXPopulation("[stats] in cities with [amount] or more population", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsFromCitiesOnSpecificTiles("[stats] in cities on [terrainFilter] tiles", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("As of 3.18.14", ReplaceWith("[stats] [in all cities] <before discovering [tech]> OR [stats] [in all cities] <before adopting [policy]>"))
    StatsFromCitiesBefore("[stats] per turn from cities before [tech/policy]", UniqueTarget.Global, UniqueTarget.FollowerBelief),


    StatsSpendingGreatPeople("[stats] whenever a Great Person is expended", UniqueTarget.Global),
    StatsFromTiles("[stats] from [tileFilter] tiles [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsFromTilesWithout("[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    // This is a doozy
    StatsFromObject("[stats] from every [tileFilter/specialist/buildingName]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    StatPercentBonus("[amount]% [stat]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatPercentFromObject("[amount]% [stat] from every [tileFilter/specialist/buildingName]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("As of 3.18.17", ReplaceWith("[amount]% [stat] from every [tileFilter/specialist/buildingName]"))
    StatPercentSignedFromObject("+[amount]% [stat] from every [tileFilter/specialist/buildingName]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    AllStatsPercentFromObject("[amount]% Yield from every [tileFilter]", UniqueTarget.FollowerBelief, UniqueTarget.Global),
    @Deprecated("As of 3.18.17", ReplaceWith("[+amount]% Yield from every [tileFilter]"))
    AllStatsSignedPercentFromObject("+[amount]% yield from every [tileFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatPercentFromReligionFollowers("[amount]% [stat] from every follower, up to [amount]%", UniqueTarget.FollowerBelief),
    BonusStatsFromCityStates("[amount]% [stat] from City-States", UniqueTarget.Global),
    StatPercentBonusCities("[amount]% [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    PercentProductionWonders("[amount]% Production when constructing [buildingFilter] wonders [cityFilter]", UniqueTarget.Global, UniqueTarget.Resource, UniqueTarget.FollowerBelief),
    PercentProductionBuildings("[amount]% Production when constructing [buildingFilter] buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    PercentProductionUnits("[amount]% Production when constructing [baseUnitFilter] units [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

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
    
    CityStateGoldGiftsProvideMoreInfluence("Gifts of Gold to City-States generate [amount]% more Influence", UniqueTarget.Global),
    CityStateCanBeBoughtForGold("Can spend Gold to annex or puppet a City-State that has been your ally for [amount] turns.", UniqueTarget.Global),
    CityStateTerritoryAlwaysFriendly("City-State territory always counts as friendly territory", UniqueTarget.Global),

    CityStateCanGiftGreatPeople("Allied City-States will occasionally gift Great People", UniqueTarget.Global),  // used in Policy
    CityStateDeprecated("Will not be chosen for new games", UniqueTarget.Nation), // implemented for CS only for now
    CityStateInfluenceDegradation("[amount]% City-State Influence degradation", UniqueTarget.Global),
    @Deprecated("As of 3.18.17", ReplaceWith("[-amount]% City-State Influence degradation"))
    CityStateInfluenceDegradationDeprecated("City-State Influence degrades [amount]% slower", UniqueTarget.Global),
    CityStateRestingPoint("Resting point for Influence with City-States is increased by [amount]", UniqueTarget.Global),
    
    CityStateStatPercent("Allied City-States provide [stat] equal to [amount]% of what they produce for themselves", UniqueTarget.Global),
    CityStateResources("[amount]% resources gifted by City-States",  UniqueTarget.Global),
    @Deprecated("As of 3.18.17", ReplaceWith("[+amount]% resources gifted by City-States"))
    CityStateResourcesDeprecated("Quantity of Resources gifted by City-States increased by [amount]%", UniqueTarget.Global),
    CityStateLuxuryHappiness("[amount]% Happiness from luxury resources gifted by City-States", UniqueTarget.Global),
    @Deprecated("As of 3.18.17", ReplaceWith("[+amount]% Happiness from luxury resources gifted by City-States"))
    CityStateLuxuryHappinessDeprecated("Happiness from Luxury Resources gifted by City-States increased by [amount]%", UniqueTarget.Global),

    // endregion

    /////// Other global uniques

    FreeUnits("[amount] units cost no maintenance", UniqueTarget.Global),

    ConsumesResources("Consumes [amount] [resource]", UniqueTarget.Improvement, UniqueTarget.Building, UniqueTarget.Unit),
    ProvidesResources("Provides [amount] [resource]", UniqueTarget.Improvement, UniqueTarget.Building),

    GrowthPercentBonus("[amount]% growth [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),


    GainFreeBuildings("Gain a free [buildingName] [cityFilter]", UniqueTarget.Global),
    @Deprecated("As of 3.17.7", ReplaceWith("Gain a free [buildingName] [cityFilter]"), DeprecationLevel.WARNING)
    ProvidesFreeBuildings("Provides a free [buildingName] [cityFilter]", UniqueTarget.Global),

    FreeExtraBeliefs("May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion", UniqueTarget.Global),
    FreeExtraAnyBeliefs("May choose [amount] additional belief(s) of any type when [foundingOrEnhancing] a religion", UniqueTarget.Global),

    UnhappinessFromPopulationPercentageChange("[amount]% unhappiness from population [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    UnhappinessFromSpecialistsPercentageChange("[amount]% unhappiness from specialists [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    FoodConsumptionBySpecialists("[amount]% Food consumption by specialists [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("As of 3.18.2", ReplaceWith("[-amount]% Food consumption by specialists [cityFilter]"), DeprecationLevel.WARNING)
    FoodConsumptionBySpecialistsDeprecated("-[amount]% food consumption by specialists [cityFilter]", UniqueTarget.Global),

    ExcessHappinessToGlobalStat("[amount]% of excess happiness converted to [stat]", UniqueTarget.Global),
    @Deprecated("As of 3.18.2", ReplaceWith("[50]% of excess happiness converted to [Culture]"), DeprecationLevel.WARNING)
    ExcessHappinessToCultureDeprecated("50% of excess happiness added to culture towards policies", UniqueTarget.Global),


    // There is potential to merge these
    BuyUnitsIncreasingCost("May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] at an increasing price ([amount])", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsIncreasingCost("May buy [buildingFilter] buildings for [amount] [stat] [cityFilter] at an increasing price ([amount])", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyUnitsForAmountStat("May buy [baseUnitFilter] units for [amount] [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsForAmountStat("May buy [buildingFilter] buildings for [amount] [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyUnitsWithStat("May buy [baseUnitFilter] units with [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsWithStat("May buy [buildingFilter] buildings with [stat] [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    
    BuyUnitsByProductionCost("May buy [baseUnitFilter] units with [stat] for [amount] times their normal Production cost", UniqueTarget.FollowerBelief, UniqueTarget.Global),
    BuyBuildingsByProductionCost("May buy [buildingFilter] buildings with [stat] for [amount] times their normal Production cost", UniqueTarget.FollowerBelief, UniqueTarget.Global),

    @Deprecated("As of 3.17.9", ReplaceWith ("May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] at an increasing price ([amount]) <starting from the [era]>"))
    BuyUnitsIncreasingCostEra("May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] starting from the [era] at an increasing price ([amount])", UniqueTarget.Global),
    
    // ToDo: Unify
    EnablesGoldProduction("Enables conversion of city production to gold", UniqueTarget.Global),
    EnablesScienceProduction("Enables conversion of city production to science", UniqueTarget.Global),
    
    BuyItemsDiscount("[stat] cost of purchasing items in cities [amount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyBuildingsDiscount("[stat] cost of purchasing [buildingFilter] buildings [amount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    BuyUnitsDiscount("[stat] cost of purchasing [baseUnitFilter] units in cities [amount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    // Should be replaced with moddable improvements when roads become moddable
    RoadMovementSpeed("Improves movement speed on roads",UniqueTarget.Global),
    RoadsConnectAcrossRivers("Roads connect tiles across rivers", UniqueTarget.Global),
    RoadMaintenance("[amount]% maintenance on road & railroads", UniqueTarget.Global), 
    @Deprecated("As of 3.18.17", ReplaceWith("[-amount]% maintenance on road & railroads"))
    DecreasedRoadMaintenanceDeprecated("Maintenance on roads & railroads reduced by [amount]%", UniqueTarget.Global),
    
    BuildingMaintenance("[amount]% maintenance cost for buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("As of 3.18.17", ReplaceWith("[-amount]% maintenace cost for buildings [cityFilter]"))
    DecrasedBuildingMaintenanceDeprecated("-[amount]% maintenance cost for buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    
    // This should probably support conditionals, e.g. <after discovering [tech]>
    MayanGainGreatPerson("Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once.", UniqueTarget.Global),
    MayanCalendarDisplay("Once The Long Count activates, the year on the world screen displays as the traditional Mayan Long Count.", UniqueTarget.Global),

    RetainHappinessFromLuxury("Retain [amount]% of the happiness from a luxury after the last copy has been traded away", UniqueTarget.Global),
    BonusHappinessFromLuxury("[amount] Happiness from each type of luxury resource", UniqueTarget.Global),
    @Deprecated("As of 3.18.17", ReplaceWith("[+amount] Happiness from each type of luxury resource"))
    BonusHappinessFromLuxuryDeprecated("+[amount] happiness from each type of luxury resource", UniqueTarget.Global),
    LessPolicyCostFromCities("Each city founded increases culture cost of policies [amount]% less than normal", UniqueTarget.Global),
    LessPolicyCost("[amount]% Culture cost of adopting new policies", UniqueTarget.Global),
    @Deprecated("As of 3.18.17", ReplaceWith("[amount]% Culture cost of adopting new policies"))
    LessPolicyCostDeprecated("Culture cost of adopting new Policies reduced by [amount]%", UniqueTarget.Global),

    EnablesOpenBorders("Enables Open Borders agreements", UniqueTarget.Global),
    // Should the 'R' in 'Research agreements' be capitalized?
    EnablesResearchAgreements("Enables Research agreements", UniqueTarget.Global),
    ScienceFromResearchAgreements("Science gained from research agreements [amount]%", UniqueTarget.Global),
    TriggersVictory("Triggers victory", UniqueTarget.Global),
    TriggersCulturalVictory("Triggers a Cultural Victory upon completion", UniqueTarget.Global),
    
    CannotBuildUnits("Cannot build [baseUnitFilter] units", UniqueTarget.Global),
    
    BetterDefensiveBuildings("[amount]% City Strength from defensive buildings", UniqueTarget.Global),
    @Deprecated("As of 3.18.17", ReplaceWith("[+25]% City Strength from defensive buildings"))
    DefensiveBuilding25("Defensive buildings in all cities are 25% more effective", UniqueTarget.Global),
    
    TileImprovementTime("[amount]% tile improvement construction time", UniqueTarget.Global),
    PercentGoldFromTradeMissions("[amount]% Gold from Great Merchant trade missions", UniqueTarget.Global),
    
    @Deprecated("As of 3.18.17", ReplaceWith("[amount]% Strength <for [mapUnitFilter] units> <when adjacent to a [mapUnitFilter] unit>"))
    StrengthFromAdjacentUnits("[amount]% Strength for [mapUnitFilter] units which have another [mapUnitFilter] unit in an adjacent tile", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("As of 3.18.17", ReplaceWith("[-amount]% Gold cost of upgrading <for [baseUnitFilter] units>"))
    ReducedUpgradingGoldCost("Gold cost of upgrading [baseUnitFilter] units reduced by [amount]%", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("As of 3.18.17", ReplaceWith("[+100]% Gold from Great Merchant trade missions"))
    DoubleGoldFromTradeMissions("Double gold from Great Merchant trade missions", UniqueTarget.Global),

    GoldenAgeLength("[amount]% Golden Age length", UniqueTarget.Global),
    @Deprecated("As of 3.18.17", ReplaceWith("[+amount]% Golden Age length"))
    GoldenAgeLengthIncreased("Golden Age length increased by [amount]%", UniqueTarget.Global),
    
    StrengthForCities("[amount]% Strength for cities", UniqueTarget.Global),
    @Deprecated("As of 3.18.17", ReplaceWith("[+amount]% Strength for cities <when defending>"))
    StrengthForCitiesDefending("+[amount]% Defensive Strength for cities", UniqueTarget.Global),
    @Deprecated("As of 3.18.17", ReplaceWith("[amount]% Strength for cities <when attacking>"))
    StrengthForCitiesAttacking("[amount]% Attacking Strength for cities", UniqueTarget.Global),
    
    UnitStartingExperience("New [baseUnitFilter] units start with [amount] Experience [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    // ToDo: make per unit and use unit filters?
    LandUnitEmbarkation("Enables embarkation for land units", UniqueTarget.Global),
    EmbarkedUnitsMayEnterOcean("Enables embarked units to enter ocean tiles", UniqueTarget.Global),
    
    IncompatibleWith("Incompatible with [policy/tech/promotion]", UniqueTarget.Policy, UniqueTarget.Tech, UniqueTarget.Promotion),
    StartingTech("Starting tech", UniqueTarget.Tech),
    ResearchableMultipleTimes("Can be continually researched", UniqueTarget.Global),

    //endregion Global uniques

    ///////////////////////////////////////// region CONSTRUCTION UNIQUES /////////////////////////////////////////

    Unbuildable("Unbuildable", UniqueTarget.Building, UniqueTarget.Unit),
    CannotBePurchased("Cannot be purchased", UniqueTarget.Building, UniqueTarget.Unit),
    CanBePurchasedWithStat("Can be purchased with [stat] [cityFilter]", UniqueTarget.Building, UniqueTarget.Unit),
    CanBePurchasedForAmountStat("Can be purchased for [amount] [stat] [cityFilter]", UniqueTarget.Building, UniqueTarget.Unit),
    MaxNumberBuildable("Limited to [amount] per Civilization", UniqueTarget.Building, UniqueTarget.Unit),
    HiddenBeforeAmountPolicies("Hidden until [amount] social policy branches have been completed", UniqueTarget.Building, UniqueTarget.Unit),
    NotDisplayedWithout("Not displayed as an available construction without [buildingName/tech/resource/policy]", UniqueTarget.Building, UniqueTarget.Unit),
    //UniqueType added in 3.18.4
    @Deprecated("As of 3.16.11", ReplaceWith("Not displayed as an available construction without [buildingName]"), DeprecationLevel.WARNING)
    NotDisplayedUnlessOtherBuildingBuilt("Not displayed as an available construction unless [buildingName] is built", UniqueTarget.Building),
    
    ConvertFoodToProductionWhenConstructed("Excess Food converted to Production when under construction", UniqueTarget.Building, UniqueTarget.Unit),
    RequiresPopulation("Requires at least [amount] population", UniqueTarget.Building, UniqueTarget.Unit),
    
    //endregion
    
    
    ///////////////////////////////////////// region BUILDING UNIQUES /////////////////////////////////////////


    CostIncreasesPerCity("Cost increases by [amount] per owned city", UniqueTarget.Building),
    CannotBeBuiltWith("Cannot be built with [buildingName]", UniqueTarget.Building),
    RequiresAnotherBuilding("Requires a [buildingName] in this city", UniqueTarget.Building),
    RequiresBuildingInAllCities("Requires a [buildingName] in all cities", UniqueTarget.Building),

    

    MustBeOn("Must be on [terrainFilter]", UniqueTarget.Building),
    MustNotBeOn("Must not be on [terrainFilter]", UniqueTarget.Building),
    MustBeNextTo("Must be next to [terrainFilter]", UniqueTarget.Building),
    MustNotBeNextTo("Must not be next to [terrainFilter]", UniqueTarget.Building),

    Unsellable("Unsellable", UniqueTarget.Building),

    RemoveAnnexUnhappiness("Remove extra unhappiness from annexed cities", UniqueTarget.Building),

    //endregion

    
    ///////////////////////////////////////// region UNIT UNIQUES /////////////////////////////////////////

    FoundCity("Founds a new city", UniqueTarget.Unit),
    ConstructImprovementConsumingUnit("Can construct [improvementName]", UniqueTarget.Unit),
    BuildImprovements("Can build [improvementFilter/terrainFilter] improvements on tiles", UniqueTarget.Unit),
    CreateWaterImprovements("May create improvements on water resources", UniqueTarget.Unit),
    
    Strength("[amount]% Strength", UniqueTarget.Unit, UniqueTarget.Global),
    StrengthNearCapital("[amount]% Strength decreasing with distance from the capital", UniqueTarget.Unit, UniqueTarget.Global),

    Movement("[amount] Movement", UniqueTarget.Unit, UniqueTarget.Global),
    Sight("[amount] Sight", UniqueTarget.Unit, UniqueTarget.Global, UniqueTarget.Terrain),
    Range("[amount] Range", UniqueTarget.Unit, UniqueTarget.Global),
    SpreadReligionStrength("[amount]% Spread Religion Strength", UniqueTarget.Unit, UniqueTarget.Global),
    MayFoundReligion("May found a religion", UniqueTarget.Unit),
    MayEnhanceReligion("May enhance a religion", UniqueTarget.Unit),
    
    CanOnlyAttackUnits("Can only attack [combatantFilter] units", UniqueTarget.Unit),
    CannotAttack("Cannot attack", UniqueTarget.Unit),
    MustSetUp("Must set up to ranged attack", UniqueTarget.Unit),
    NoDefensiveTerrainBonus("No defensive terrain bonus", UniqueTarget.Unit, UniqueTarget.Global),
    NoDefensiveTerrainPenalty("No defensive terrain penalty", UniqueTarget.Unit, UniqueTarget.Global),
    Uncapturable("Uncapturable", UniqueTarget.Unit),
    SelfDestructs("Self-destructs when attacking", UniqueTarget.Unit),
    HealsEvenAfterAction("Unit will heal every turn, even if it performs an action", UniqueTarget.Unit),
    NoMovementToPillage("No movement cost to pillage", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("As of 3.18.17", ReplaceWith("No movement cost to pillage <for [Melee] units>"), DeprecationLevel.WARNING)
    NoMovementToPillageMelee("Melee units pay no movement cost to pillage", UniqueTarget.Unit, UniqueTarget.Global),
    CanMoveAfterAttacking("Can move after attacking", UniqueTarget.Unit),
    MoveImmediatelyOnceBought("Can move immediately once bought", UniqueTarget.Unit),
    BlastRadius("Blast radius [amount]", UniqueTarget.Unit),

    HealsOutsideFriendlyTerritory("May heal outside of friendly territory", UniqueTarget.Unit, UniqueTarget.Global),
    HealingEffectsDoubled("All healing effects doubled", UniqueTarget.Unit, UniqueTarget.Global),
    HealsAfterKilling("Heals [amount] damage if it kills a unit", UniqueTarget.Unit, UniqueTarget.Global),

    NormalVisionWhenEmbarked("Normal vision when embarked", UniqueTarget.Unit, UniqueTarget.Global),
    DefenceBonusWhenEmbarked("Defense bonus when embarked", UniqueTarget.Unit, UniqueTarget.Global),
    DefenceBonusWhenEmbarkedCivwide("Embarked units can defend themselves", UniqueTarget.Global),

    SixTilesAlwaysVisible("6 tiles in every direction always visible", UniqueTarget.Unit),
    
    CarryAirUnits("Can carry [amount] [mapUnitFilter] units", UniqueTarget.Unit),
    CarryExtraAirUnits("Can carry [amount] extra [mapUnitFilter] units", UniqueTarget.Unit),
    CannotBeCarriedBy("Cannot be carried by [mapUnitFilter] units", UniqueTarget.Unit),

    UnitMaintenanceDiscount("[amount]% maintenance costs", UniqueTarget.Unit, UniqueTarget.Global),
    UnitUpgradeCost("[amount]% Gold cost of upgrading", UniqueTarget.Unit, UniqueTarget.Global),
    GreatPersonEarnedFaster("[greatPerson] is earned [amount]% faster", UniqueTarget.Unit, UniqueTarget.Global),
    
    CaptureCityPlunder("Upon capturing a city, receive [amount] times its [stat] production as [stat] immediately", UniqueTarget.Unit, UniqueTarget.Global),
    KillUnitPlunder("Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [stat]", UniqueTarget.Unit, UniqueTarget.Global),
    KillUnitPlunderNearCity("Earn [amount]% of [mapUnitFilter] unit's [costOrStrength] as [stat] when killed within 4 tiles of a city following this religion", UniqueTarget.FollowerBelief),
    
    FlatXPGain("[amount] XP gained from combat", UniqueTarget.Unit, UniqueTarget.Global),
    PercentageXPGain("[amount]% XP gained from combat", UniqueTarget.Unit, UniqueTarget.Global),

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
    @Deprecated("As of 3.18.6", ReplaceWith("Cannot enter ocean tiles <before discovering [Astronomy]>"))
    CannotEnterOceanUntilAstronomy("Cannot enter ocean tiles until Astronomy", UniqueTarget.Unit),
    CannotBeBarbarian("Never appears as a Barbarian unit", UniqueTarget.Unit, flags = listOf(UniqueFlag.HiddenToUsers)),
    CanEnterForeignTiles("May enter foreign tiles without open borders", UniqueTarget.Unit),
    CanEnterForeignTilesButLosesReligiousStrength("May enter foreign tiles without open borders, but loses [amount] religious strength each turn it ends there", UniqueTarget.Unit),

    ReligiousUnit("Religious Unit", UniqueTarget.Unit),
    SpaceshipPart("Spaceship part", UniqueTarget.Building, UniqueTarget.Unit),


    @Deprecated("As of 3.18.12", ReplaceWith("[amount]% XP gained from combat"), DeprecationLevel.WARNING)
    BonuxXPGain("[amount]% Bonus XP gain", UniqueTarget.Unit),
    @Deprecated("As of 3.18.12", ReplaceWith("[amount]% XP gained from combat <for [mapUnitFilter] units>"), DeprecationLevel.WARNING)
    BonusXPGainForUnits("[mapUnitFilter] units gain [amount]% more Experience from combat", UniqueTarget.Global),

    @Deprecated("As of 3.18.14", ReplaceWith("[amount]% maintenance costs <for [mapUnitFilter] units>"), DeprecationLevel.WARNING)
    UnitMaintenanceDiscountGlobal("[amount]% maintenance costs for [mapUnitFilter] units", UniqueTarget.Global),


    //endregion

    ///////////////////////////////////////// region TILE UNIQUES /////////////////////////////////////////

    // region natural wonders
    NaturalWonderNeighborCount("Must be adjacent to [amount] [simpleTerrain] tiles", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    NaturalWonderNeighborsRange("Must be adjacent to [amount] to [amount] [simpleTerrain] tiles", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    NaturalWonderSmallerLandmass("Must not be on [amount] largest landmasses", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    NaturalWonderLargerLandmass("Must be on [amount] largest landmasses", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    NaturalWonderLatitude("Occurs on latitudes from [amount] to [amount] percent of distance equator to pole", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    NaturalWonderGroups("Occurs in groups of [amount] to [amount] tiles", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    NaturalWonderConvertNeighbors("Neighboring tiles will convert to [baseTerrain]", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    // The "Except [terrainFilter]" could theoretically be implemented with a conditional
    NaturalWonderConvertNeighborsExcept("Neighboring tiles except [baseTerrain] will convert to [baseTerrain]", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    GrantsGoldToFirstToDiscover("Grants 500 Gold to the first civilization to discover it", UniqueTarget.Terrain),
    // endregion

    DamagesContainingUnits("Units ending their turn on this terrain take [amount] damage", UniqueTarget.Terrain),
    TerrainGrantsPromotion("Grants [promotion] ([comment]) to adjacent [mapUnitFilter] units for the rest of the game", UniqueTarget.Terrain),
    GrantsCityStrength("[amount] Strength for cities built on this terrain", UniqueTarget.Terrain),
    ProductionBonusWhenRemoved("Provides a one-time Production bonus to the closest city when cut down", UniqueTarget.Terrain),

    TileProvidesYieldWithoutPopulation("Tile provides yield without assigned population", UniqueTarget.Terrain, UniqueTarget.Improvement),
    NullifyYields("Nullifies all other stats this tile provides", UniqueTarget.Terrain),
    RestrictedBuildableImprovements("Only [improvementFilter] improvements may be built on this tile", UniqueTarget.Terrain),
    
    BlocksLineOfSightAtSameElevation("Blocks line-of-sight from tiles at same elevation", UniqueTarget.Terrain),
    VisibilityElevation("Has an elevation of [amount] for visibility calculations", UniqueTarget.Terrain),
  
    OverrideFertility("Always Fertility [amount] for Map Generation", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    AddFertility("[amount] to Fertility for Map Generation", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),

    RegionRequirePercentSingleType("A Region is formed with at least [amount]% [simpleTerrain] tiles, with priority [amount]", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    RegionRequirePercentTwoTypes("A Region is formed with at least [amount]% [simpleTerrain] tiles and [simpleTerrain] tiles, with priority [amount]",
            UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    RegionRequireFirstLessThanSecond("A Region can not contain more [simpleTerrain] tiles than [simpleTerrain] tiles", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    IgnoreBaseTerrainForRegion("Base Terrain on this tile is not counted for Region determination", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    RegionExtraResource("Starts in regions of this type receive an extra [resource]", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    BlocksResources("Never receives any resources", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),

    HasQuality("Considered [terrainQuality] when determining start locations", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),

    ResourceWeighting("Generated with weight [amount]", UniqueTarget.Resource, flags = listOf(UniqueFlag.HiddenToUsers)),
    MinorDepositWeighting("Minor deposits generated with weight [amount]", UniqueTarget.Resource, flags = listOf(UniqueFlag.HiddenToUsers)),
    LuxuryWeightingForCityStates("Generated near City States with weight [amount]", UniqueTarget.Resource, flags = listOf(UniqueFlag.HiddenToUsers)),
    LuxurySpecialPlacement("Special placement during map generation", UniqueTarget.Resource, flags = listOf(UniqueFlag.HiddenToUsers)),
    ResourceFrequency("Generated on every [amount] tiles", UniqueTarget.Resource, flags = listOf(UniqueFlag.HiddenToUsers)),

    StrategicBalanceResource("Guaranteed with Strategic Balance resource option", UniqueTarget.Resource),
  
    NoNaturalGeneration("Doesn't generate naturally", UniqueTarget.Terrain, UniqueTarget.Resource, flags = listOf(UniqueFlag.HiddenToUsers)),
    TileGenerationConditions("Occurs at temperature between [amount] and [amount] and humidity between [amount] and [amount]", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    OccursInChains("Occurs in chains at high elevations", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    OccursInGroups("Occurs in groups around high elevations", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
    MajorStrategicFrequency("Every [amount] tiles with this terrain will receive a major deposit of a strategic resource.", UniqueTarget.Terrain, flags = listOf(UniqueFlag.HiddenToUsers)),
  
    RareFeature("Rare feature", UniqueTarget.Terrain),
    
    ResistsNukes("Resistant to nukes", UniqueTarget.Terrain),
    DestroyableByNukes("Can be destroyed by nukes", UniqueTarget.Terrain),
    
    FreshWater("Fresh water", UniqueTarget.Terrain),
    RoughTerrain("Rough terrain", UniqueTarget.Terrain),
    
    /////// Resource uniques
    ResourceAmountOnTiles("Deposits in [tileFilter] tiles always provide [amount] resources", UniqueTarget.Resource),
    CityStateOnlyResource("Can only be created by Mercantile City-States", UniqueTarget.Resource),

    //endregion

    ////// region Improvement uniques
    ImprovementBuildableByFreshWater("Can also be built on tiles adjacent to fresh water", UniqueTarget.Improvement),
    ImprovementStatsOnTile("[stats] from [tileFilter] tiles", UniqueTarget.Improvement),
    @Deprecated("As of 3.17.10", ReplaceWith("[stats] from [tileFilter] tiles <after discovering [tech]>"), DeprecationLevel.WARNING)
    StatsOnTileWithTech("[stats] on [tileFilter] tiles once [tech] is discovered", UniqueTarget.Improvement),
    @Deprecated("As of 3.17.10", ReplaceWith("[stats] <after discovering [tech]>"), DeprecationLevel.WARNING)
    StatsWithTech("[stats] once [tech] is discovered", UniqueTarget.Improvement, UniqueTarget.Building),
    ImprovementStatsForAdjacencies("[stats] for each adjacent [tileFilter]", UniqueTarget.Improvement),

    CanBuildOutsideBorders("Can be built outside your borders", UniqueTarget.Improvement),
    CanBuildJustOutsideBorders("Can be built just outside your borders", UniqueTarget.Improvement),
    @Deprecated("As of 3.18.5", ReplaceWith("Cannot be built on [tileFilter] tiles <before discovering [tech]>"))
    RequiresTechToBuildOnTile("Cannot be built on [tileFilter] tiles until [tech] is discovered", UniqueTarget.Improvement),
    CannotBuildOnTile("Cannot be built on [tileFilter] tiles", UniqueTarget.Improvement),
    NoFeatureRemovalNeeded("Does not need removal of [tileFilter]", UniqueTarget.Improvement),
    
    DefensiveBonus("Gives a defensive bonus of [amount]%", UniqueTarget.Improvement),
    ImprovementMaintenance("Costs [amount] gold per turn when in your territory", UniqueTarget.Improvement), // Unused
    DamagesAdjacentEnemyUnits("Adjacent enemy units ending their turn take [amount] damage", UniqueTarget.Improvement),
    @Deprecated("As of 3.18.17", ReplaceWith("Adjacent enemy units ending their turn take [30] damage"), DeprecationLevel.WARNING)
    DamagesAdjacentEnemyUnitsOld("Deal [amount] damage to adjacent enemy units", UniqueTarget.Improvement),
    @Deprecated("As of 3.17.10", ReplaceWith("Adjacent enemy units ending their turn take [30] damage"), DeprecationLevel.WARNING)
    DamagesAdjacentEnemyUnitsForExactlyThirtyDamage("Deal 30 damage to adjacent enemy units", UniqueTarget.Improvement),
    
    GreatImprovement("Great Improvement", UniqueTarget.Improvement),
    IsAncientRuinsEquivalent("Provides a random bonus when entered", UniqueTarget.Improvement),
    
    Unpillagable("Unpillagable", UniqueTarget.Improvement),

    Indestructible("Indestructible", UniqueTarget.Improvement),
    //endregion

    ///////////////////////////////////////// region CONDITIONALS /////////////////////////////////////////

    
    /////// civ conditionals
    ConditionalWar("when at war", UniqueTarget.Conditional),
    ConditionalNotWar("when not at war", UniqueTarget.Conditional),
    ConditionalHappy("while the empire is happy", UniqueTarget.Conditional),
    ConditionalGoldenAge("during a Golden Age", UniqueTarget.Conditional),
    
    ConditionalDuringEra("during the [era]", UniqueTarget.Conditional),
    ConditionalBeforeEra("before the [era]", UniqueTarget.Conditional),
    ConditionalStartingFromEra("starting from the [era]", UniqueTarget.Conditional),
    
    ConditionalTech("after discovering [tech]", UniqueTarget.Conditional),
    ConditionalNoTech("before discovering [tech]", UniqueTarget.Conditional),
    ConditionalPolicy("after adopting [policy]", UniqueTarget.Conditional),
    ConditionalNoPolicy("before adopting [policy]", UniqueTarget.Conditional),

    /////// city conditionals
    ConditionalSpecialistCount("if this city has at least [amount] specialists", UniqueTarget.Conditional),

    /////// unit conditionals
    ConditionalOurUnit("for [mapUnitFilter] units", UniqueTarget.Conditional),
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

    /////// tile conditionals
    ConditionalNeighborTiles("with [amount] to [amount] neighboring [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalNeighborTilesAnd("with [amount] to [amount] neighboring [tileFilter] [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalInTiles("in [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalInTilesAnd("in [tileFilter] [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalInTilesNot("in tiles without [tileFilter]", UniqueTarget.Conditional),

    /////// area conditionals
    ConditionalOnWaterMaps("on water maps", UniqueTarget.Conditional),
    ConditionalInRegionOfType("in [regionType] Regions", UniqueTarget.Conditional),
    ConditionalInRegionExceptOfType("in all except [regionType] Regions", UniqueTarget.Conditional),

    //endregion

    ///////////////////////////////////////// region TRIGGERED ONE-TIME /////////////////////////////////////////

    
    OneTimeFreeUnit("Free [baseUnitFilter] appears", UniqueTarget.Global),  // used in Policies, Buildings
    OneTimeAmountFreeUnits("[amount] free [baseUnitFilter] units appear", UniqueTarget.Global), // used in Buildings
    OneTimeFreeUnitRuins("Free [baseUnitFilter] found in the ruins", UniqueTarget.Ruins), // Differs from "Free [] appears" in that it spawns near the ruins instead of in a city
    OneTimeFreePolicy("Free Social Policy", UniqueTarget.Global), // used in Buildings
    OneTimeAmountFreePolicies("[amount] Free Social Policies", UniqueTarget.Global),  // Not used in Vanilla
    OneTimeEnterGoldenAge("Empire enters golden age", UniqueTarget.Global),  // used in Policies, Buildings
    OneTimeFreeGreatPerson("Free Great Person", UniqueTarget.Global),  // used in Policies, Buildings
    OneTimeGainPopulation("[amount] population [cityFilter]", UniqueTarget.Global),  // used in CN tower
    OneTimeGainPopulationRandomCity("[amount] population in a random city", UniqueTarget.Ruins),
    OneTimeFreeTech("Free Technology", UniqueTarget.Global),  // used in Buildings
    OneTimeAmountFreeTechs("[amount] Free Technologies", UniqueTarget.Global),  // used in Policy
    OneTimeFreeTechRuins("[amount] free random researchable Tech(s) from the [era]", UniqueTarget.Ruins),  // todo: Not picked up by TranslationFileWriter?
    OneTimeRevealEntireMap("Reveals the entire map", UniqueTarget.Global),  // used in tech
    OneTimeGainStat("Gain [amount] [stat]", UniqueTarget.Ruins),
    OneTimeGainStatRange("Gain [amount]-[amount] [stat]", UniqueTarget.Ruins),
    OneTimeGainPantheon("Gain enough Faith for a Pantheon", UniqueTarget.Ruins),
    OneTimeGainProphet("Gain enough Faith for [amount]% of a Great Prophet", UniqueTarget.Ruins),
    // todo: The "up to [All]" used in vanilla json is not nice to read. Split?
    OneTimeRevealSpecificMapTiles("Reveal up to [amount/'all'] [tileFilter] within a [amount] tile radius", UniqueTarget.Ruins),
    OneTimeRevealCrudeMap("From a randomly chosen tile [amount] tiles away from the ruins, reveal tiles up to [amount] tiles away with [amount]% chance", UniqueTarget.Ruins),
    OneTimeTriggerVoting("Triggers voting for the Diplomatic Victory", UniqueTarget.Global),  // used in Building

    OneTimeUnitHeal("Heal this unit by [amount] HP", UniqueTarget.Promotion),
    OneTimeUnitGainXP("This Unit gains [amount] XP", UniqueTarget.Ruins),
    OneTimeUnitUpgrade("This Unit upgrades for free", UniqueTarget.Global),  // Not used in Vanilla
    OneTimeUnitSpecialUpgrade("This Unit upgrades for free including special upgrades", UniqueTarget.Ruins),
    OneTimeUnitGainPromotion("This Unit gains the [promotion] promotion", UniqueTarget.Global),  // Not used in Vanilla

    UnitsGainPromotion("[mapUnitFilter] units gain the [promotion] promotion", UniqueTarget.Global),  // Not used in Vanilla
    // todo: remove forced sign
    StrategicResourcesIncrease("Quantity of strategic resources produced by the empire +[amount]%", UniqueTarget.Global),  // used in Policy
    // todo: remove forced sign
    // todo: convert to "[amount]% Strength <when attacking> <for [baseUnitFilter] units> <for [amount] turns>"
    TimedAttackStrength("+[amount]% attack strength to all [mapUnitFilter] units for [amount] turns", UniqueTarget.Global),  // used in Policy
    FreeStatBuildings("Provides the cheapest [stat] building in your first [amount] cities for free", UniqueTarget.Global),  // used in Policy
    FreeSpecificBuildings("Provides a [buildingName] in your first [amount] cities for free", UniqueTarget.Global),  // used in Policy

    //endregion

    ///////////////////////////////////////////// META /////////////////////////////////////////////
    
    
    HiddenWithoutReligion("Hidden when religion is disabled", UniqueTarget.Unit, UniqueTarget.Building, UniqueTarget.Ruins),
    HiddenBeforePantheon("Hidden before founding a Pantheon", UniqueTarget.Ruins),
    HiddenAfterPantheon("Hidden after founding a Pantheon", UniqueTarget.Ruins),
    HiddenAfterGreatProphet("Hidden after generating a Great Prophet", UniqueTarget.Ruins),
    AvailableAfterCertainTurns("Only available after [amount] turns", UniqueTarget.Ruins),
    HiddenWithoutVictoryType("Hidden when [victoryType] Victory is disabled", UniqueTarget.Building, UniqueTarget.Unit),
    

    // region DEPRECATED AND REMOVED


    @Deprecated("As of 3.17.10 - removed 3.18.18", ReplaceWith("[+amount]% [stat] [cityFilter]"), DeprecationLevel.ERROR)
    StatPercentBonusCitiesDeprecated("+[amount]% [stat] [cityFilter]", UniqueTarget.Global),
    @Deprecated("As of 3.17.10 - removed 3.18.18", ReplaceWith("[+amount]% [stat] [in all cities]"), DeprecationLevel.ERROR)
    StatPercentBonusCitiesDeprecated2("+[amount]% [stat] in all cities", UniqueTarget.Global),
    // type added 3.18.5
    @Deprecated("As of 3.17.1 - removed 3.18.18", ReplaceWith("[amount]% [stat] [in all cities] <while the empire is happy>"), DeprecationLevel.ERROR)
    StatPercentBonusCitiesDeprecatedWhileEmpireHappy("[amount]% [stat] while the empire is happy", UniqueTarget.Global),

    @Deprecated("As of 3.16.15 - removed 3.18.4", ReplaceWith("Provides the cheapest [stat] building in your first [amount] cities for free"), DeprecationLevel.ERROR)
    FreeStatBuildingsDeprecated("Immediately creates the cheapest available cultural building in each of your first [amount] cities for free", UniqueTarget.Global),
    @Deprecated("As of 3.16.15 - removed 3.18.4", ReplaceWith("Provides a [buildingName] in your first [amount] cities for free"), DeprecationLevel.ERROR)
    FreeSpecificBuildingsDeprecated("Immediately creates a [buildingName] in each of your first [amount] cities for free", UniqueTarget.Global),



    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <when attacking>"), DeprecationLevel.ERROR)
    StrengthAttacking("+[amount]% Strength when attacking", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <shen defending>"), DeprecationLevel.ERROR)
    StrengthDefending("+[amount]% Strength when defending", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <when defending> <vs [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    StrengthDefendingUnitFilter("[amount]% Strength when defending vs [mapUnitFilter] units", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    DamageForUnits("[mapUnitFilter] units deal +[amount]% damage", UniqueTarget.Global),
    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[+10]% Strength <for [All] units> <during a Golden Age>"), DeprecationLevel.ERROR)
    StrengthGoldenAge("+10% Strength for all units during Golden Age", UniqueTarget.Global),
    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <when fighting in [tileFilter] tiles> <when defending>"), DeprecationLevel.ERROR)
    StrengthDefenseTiles("+[amount]% defence in [tileFilter] tiles", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <when fighting in [tileFilter] tiles>"), DeprecationLevel.ERROR)
    StrengthIn("+[amount]% Strength in [tileFilter]", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <for [mapUnitFilter] units> <when fighting in [tileFilter] tiles>"), DeprecationLevel.ERROR)
    StrengthUnitsTiles("[amount]% Strength for [mapUnitFilter] units in [tileFilter]", UniqueTarget.Global),
    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[+15]% Strength <for [All] units> <vs cities> <when attacking>"), DeprecationLevel.ERROR)
    StrengthVsCities("+15% Combat Strength for all units when attacking Cities", UniqueTarget.Global),


    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[amount] Movement <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    MovementUnits("+[amount] Movement for all [mapUnitFilter] units", UniqueTarget.Global),
    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[amount] Movement <for [All] units> <during a Golden Age>"), DeprecationLevel.ERROR)
    MovementGoldenAge("+1 Movement for all units during Golden Age", UniqueTarget.Global),

    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[amount] Sight <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    SightUnits("[amount] Sight for all [mapUnitFilter] units", UniqueTarget.Global),
    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[amount] Sight"), DeprecationLevel.ERROR)
    VisibilityRange("[amount] Visibility Range", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[-1] Sight"), DeprecationLevel.ERROR)
    LimitedVisibility("Limited Visibility", UniqueTarget.Unit),

    @Deprecated("As of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Spread Religion Strength <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    SpreadReligionStrengthUnits("[amount]% Spread Religion Strength for [mapUnitFilter] units", UniqueTarget.Global),

    @Deprecated("As of 3.17.10 - removed 3.18.5", ReplaceWith("[+amount]% Production when constructing [baseUnitFilter] units [cityFilter]"), DeprecationLevel.ERROR)
    PercentProductionUnitsDeprecated("+[amount]% Production when constructing [baseUnitFilter] units [cityFilter]", UniqueTarget.Global),

    @Deprecated("As of 3.17.10 - removed 3.18.5", ReplaceWith("[amount]% Production when constructing [buildingFilter] buildings [cityFilter]"), DeprecationLevel.ERROR)
    PercentProductionStatBuildings("+[amount]% Production when constructing [stat] buildings", UniqueTarget.Global),
    @Deprecated("As of 3.17.10 - removed 3.18.5", ReplaceWith("[amount]% Production when constructing [buildingFilter] buildings [cityFilter]"), DeprecationLevel.ERROR)
    PercentProductionConstructions("+[amount]% Production when constructing [constructionFilter]", UniqueTarget.Global),
    @Deprecated("As of 3.17.10 - removed 3.18.5", ReplaceWith("[amount]% Production when constructing [buildingFilter] buildings [cityFilter]"), DeprecationLevel.ERROR)
    PercentProductionBuildingName("+[amount]% Production when constructing a [buildingName]", UniqueTarget.Global),
    @Deprecated("As of 3.17.10 - removed 3.18.5", ReplaceWith("[amount]% Production when constructing [buildingFilter] buildings [cityFilter]"), DeprecationLevel.ERROR)
    PercentProductionConstructionsCities("+[amount]% Production when constructing [constructionFilter] [cityFilter]", UniqueTarget.Global),
    
    // endregion

    ;

    /** For uniques that have "special" parameters that can accept multiple types, we can override them manually
     *  For 95% of cases, auto-matching is fine. */
    val parameterTypeMap = ArrayList<List<UniqueParameterType>>()
    val targetTypes = HashSet<UniqueTarget>()

    init {
        for (placeholder in text.getPlaceholderParameters()) {
            val matchingParameterTypes = placeholder
                .split('/')
                .map { UniqueParameterType.safeValueOf(it) }
            parameterTypeMap.add(matchingParameterTypes)
        }
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
}
