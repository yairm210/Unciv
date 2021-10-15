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

enum class UniqueType(val text:String, vararg targets: UniqueTarget) {

    //////////////////////////////////////// GLOBAL UNIQUES ////////////////////////////////////////


    /////// Stat providing uniques

    Stats("[stats]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    StatsPerCity("[stats] [cityFilter]", UniqueTarget.Global),
    @Deprecated("As of 3.16.16", ReplaceWith("[stats] <if this city has at least [amount] specialists>"), DeprecationLevel.WARNING)
    StatBonusForNumberOfSpecialists("[stats] if this city has at least [amount] specialists", UniqueTarget.Global),

    StatPercentBonus("[amount]% [stat]", UniqueTarget.Global),
    BonusStatsFromCityStates("[amount]% [stat] from City-States", UniqueTarget.Global),

    RemoveAnnexUnhappiness("Remove extra unhappiness from annexed cities", UniqueTarget.Building),

    StatsFromSpecialist("[stats] from every specialist [cityFilter]", UniqueTarget.Global),
    @Deprecated("As of 3.16.16", ReplaceWith("[stats] from every specialist [in all cities]"), DeprecationLevel.WARNING)
    StatsFromSpecialistDeprecated("[stats] from every specialist", UniqueTarget.Global),

    StatsPerPopulation("[stats] per [amount] population [cityFilter]", UniqueTarget.Global),

    /////// City-State related uniques

    // I don't like the fact that currently "city state bonuses" are separate from the "global bonuses",
    // todo: merge city state bonuses into global bonuses
    CityStateStatsPerTurn("Provides [stats] per turn", UniqueTarget.CityState), // Should not be Happiness!
    CityStateStatsPerCity("Provides [stats] [cityFilter] per turn", UniqueTarget.CityState),
    CityStateHappiness("Provides [amount] Happiness", UniqueTarget.CityState),
    CityStateMilitaryUnits("Provides military units every ≈[amount] turns", UniqueTarget.CityState), // No conditional support as of yet
    CityStateUniqueLuxury("Provides a unique luxury", UniqueTarget.CityState), // No conditional support as of yet
    CityStateGiftedUnitsStartWithXp("Military Units gifted from City-States start with [amount] XP", UniqueTarget.Global),
    CityStateGoldGiftsProvideMoreInfluence("Gifts of Gold to City-States generate [amount]% more Influence", UniqueTarget.Global),
    CityStateCanBeBoughtForGold("Can spend Gold to annex or puppet a City-State that has been your ally for [amount] turns.", UniqueTarget.Global),
    CityStateTerritoryAlwaysFriendly("City-State territory always counts as friendly territory", UniqueTarget.Global),

    CityStateCanGiftGreatPeople("Allied City-States will occasionally gift Great People", UniqueTarget.Global),  // used in Policy
    CityStateDeprecated("Will not be chosen for new games", UniqueTarget.Nation), // implemented for CS only for now

    /////// Other global uniques

    FreeUnits("[amount] units cost no maintenance", UniqueTarget.Global),
    UnitMaintenanceDiscount("[amount]% maintenance costs for [mapUnitFilter] units", UniqueTarget.Global),
    @Deprecated("As of 3.16.16", ReplaceWith("[amount]% maintenance costs for [mapUnitFilter] units"), DeprecationLevel.WARNING)
    DecreasedUnitMaintenanceCostsByFilter("-[amount]% [mapUnitFilter] unit maintenance costs", UniqueTarget.Global),
    @Deprecated("As of 3.16.16", ReplaceWith("[amount]% maintenance costs for [mapUnitFilter] units"), DeprecationLevel.WARNING)
    DecreasedUnitMaintenanceCostsGlobally("-[amount]% unit upkeep costs", UniqueTarget.Global),

    ConsumesResources("Consumes [amount] [resource]",
        UniqueTarget.Improvement, UniqueTarget.Building, UniqueTarget.Unit),
    ProvidesResources("Provides [amount] [resource]",
            UniqueTarget.Improvement, UniqueTarget.Building),

    GrowthPercentBonus("[amount]% growth [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("As of 3.16.14", ReplaceWith("[amount]% growth [cityFilter]"), DeprecationLevel.WARNING)
    GrowthPercentBonusPositive("+[amount]% growth [cityFilter]", UniqueTarget.Global),
    @Deprecated("As of 3.16.14", ReplaceWith("[amount]% growth [cityFilter] <when not at war>"), DeprecationLevel.WARNING)
    GrowthPercentBonusWhenNotAtWar("+[amount]% growth [cityFilter] when not at war", UniqueTarget.Global),

    GainFreeBuildings("Gain a free [buildingName] [cityFilter]", UniqueTarget.Global),
    @Deprecated("As of 3.17.7", ReplaceWith("Gain a free [buildingName] [cityFilter]"), DeprecationLevel.WARNING)
    ProvidesFreeBuildings("Provides a free [buildingName] [cityFilter]", UniqueTarget.Global),

    FreeExtraBeliefs("May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion", UniqueTarget.Global),
    FreeExtraAnyBeliefs("May choose [amount] additional belief(s) of any type when [foundingOrEnhancing] a religion", UniqueTarget.Global),

    MayanGainGreatPerson("Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once.", UniqueTarget.Nation),
    MayanCalendarDisplay("Once The Long Count activates, the year on the world screen displays as the traditional Mayan Long Count.", UniqueTarget.Nation),

    ///////////////////////////////////////// UNIT UNIQUES /////////////////////////////////////////

    FoundCity("Founds a new city", UniqueTarget.Unit),
    BuildImprovements("Can build [improvementFilter/terrainFilter] improvements on tiles", UniqueTarget.Unit),
    CreateWaterImprovements("May create improvements on water resources", UniqueTarget.Unit),
    CanSeeInvisibleUnits("Can see invisible [mapUnitFilter] units", UniqueTarget.Unit),
    
    Strength("[amount]% Strength", UniqueTarget.Unit, UniqueTarget.Global),
    StrengthNearCapital("[amount]% Strength decreasing with distance from the capital", UniqueTarget.Unit),

    @Deprecated("As of 3.17.3", ReplaceWith("[amount]% Strength"), DeprecationLevel.WARNING)
    StrengthPlus("+[amount]% Strength", UniqueTarget.Unit),
    @Deprecated("As of 3.17.3", ReplaceWith("[amount]% Strength"), DeprecationLevel.WARNING)
    StrengthMin("-[amount]% Strength", UniqueTarget.Unit),
    @Deprecated("As of 3.17.3", ReplaceWith("[amount]% Strength <vs [mapUnitFilter] units>/<vs cities>"), DeprecationLevel.WARNING)
    StrengthPlusVs("+[amount]% Strength vs [combatantFilter]", UniqueTarget.Unit),
    @Deprecated("As of 3.17.3", ReplaceWith("[amount]% Strength <vs [mapUnitFilter] units>/<vs cities>"), DeprecationLevel.WARNING)
    StrengthMinVs("-[amount]% Strength vs [combatantFilter]", UniqueTarget.Unit),
    @Deprecated("As of 3.17.3", ReplaceWith("[amount]% Strength"), DeprecationLevel.WARNING)
    CombatBonus("+[amount]% Combat Strength", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5", ReplaceWith("[amount]% Strength <when attacking>"), DeprecationLevel.WARNING)
    StrengthAttacking("+[amount]% Strength when attacking", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5", ReplaceWith("[amount]% Strength <shen defending>"), DeprecationLevel.WARNING)
    StrengthDefending("+[amount]% Strength when defending", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5", ReplaceWith("[amount]% Strength <when defending> <vs [mapUnitFilter] units>"), DeprecationLevel.WARNING)
    StrengthDefendingUnitFilter("[amount]% Strength when defending vs [mapUnitFilter] units", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5", ReplaceWith("[amount]% Strength <for [mapUnitFilter] units>"), DeprecationLevel.WARNING)
    DamageForUnits("[mapUnitFilter] units deal +[amount]% damage", UniqueTarget.Global),
    @Deprecated("As of 3.17.5", ReplaceWith("[+10]% Strength <for [All] units> <during a Golden Age>"), DeprecationLevel.WARNING)
    StrengthGoldenAge("+10% Strength for all units during Golden Age", UniqueTarget.Global),
    @Deprecated("As of 3.17.5", ReplaceWith("[amount]% Strength <when fighting in [tileFilter] tiles> <when defending>"), DeprecationLevel.WARNING)
    StrengthDefenseTiles("+[amount]% defence in [tileFilter] tiles", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5", ReplaceWith("[amount]% Strength <when fighting in [tileFilter] tiles>"), DeprecationLevel.WARNING)
    StrengthIn("+[amount]% Strength in [tileFilter]", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5", ReplaceWith("[amount]% Strength <for [mapUnitFilter] units> <when fighting in [tileFilter] tiles>"))
    StrengthUnitsTiles("[amount]% Strength for [mapUnitFilter] units in [tileFilter]", UniqueTarget.Global),
    @Deprecated("As of 3.17.5", ReplaceWith("[+15]% Strength <for [All] units> <vs cities> <when attacking>"))
    StrengthVsCities("+15% Combat Strength for all units when attacking Cities", UniqueTarget.Global),


    Movement("[amount] Movement", UniqueTarget.Unit, UniqueTarget.Global),
    Sight("[amount] Sight", UniqueTarget.Unit, UniqueTarget.Global),
    SpreadReligionStrength("[amount]% Spread Religion Strength", UniqueTarget.Unit, UniqueTarget.Global),
    MayFoundReligion("May found a religion", UniqueTarget.Unit),
    MayEnhanceReligion("May enhance a religion", UniqueTarget.Unit),
    NormalVisionWhenEmbarked("Normal vision when embarked", UniqueTarget.Unit, UniqueTarget.Global),
    CannotAttack("Cannot attack", UniqueTarget.Unit),

    @Deprecated("As of 3.17.5", ReplaceWith("[amount] Movement <for [mapUnitFilter] units>"), DeprecationLevel.WARNING)
    MovementUnits("+[amount] Movement for all [mapUnitFilter] units", UniqueTarget.Global),
    @Deprecated("As of 3.17.5", ReplaceWith("[amount] Movement <for [All] units> <during a Golden Age>"), DeprecationLevel.WARNING)
    MovementGoldenAge("+1 Movement for all units during Golden Age", UniqueTarget.Global),

    @Deprecated("As of 3.17.5", ReplaceWith("[amount] Sight <for [mapUnitFilter] units>"), DeprecationLevel.WARNING)
    SightUnits("[amount] Sight for all [mapUnitFilter] units", UniqueTarget.Global),
    @Deprecated("As of 3.17.5", ReplaceWith("[amount] Sight"), DeprecationLevel.WARNING)
    VisibilityRange("[amount] Visibility Range", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5", ReplaceWith("[-1] Sight"), DeprecationLevel.WARNING)
    LimitedVisibility("Limited Visibility", UniqueTarget.Unit),

    @Deprecated("As of 3.17.5", ReplaceWith("[amount]% Spread Religion Strength <for [mapUnitFilter] units>"), DeprecationLevel.WARNING)
    SpreadReligionStrengthUnits("[amount]% Spread Religion Strength for [mapUnitFilter] units", UniqueTarget.Global),

    BlastRadius("Blast radius [amount]", UniqueTarget.Unit),

    CarryAirUnits("Can carry [amount] [mapUnitFilter] units", UniqueTarget.Unit),
    CarryExtraAirUnits("Can carry [amount] extra [mapUnitFilter] units", UniqueTarget.Unit),
    CannotBeCarriedBy("Cannot be carried by [mapUnitFilter] units", UniqueTarget.Unit),

    // The following block gets cached in MapUnit for faster getMovementCostBetweenAdjacentTiles
    DoubleMovementOnTerrain("Double movement in [terrainFilter]", UniqueTarget.Unit),
    @Deprecated("As of 3.17.1", ReplaceWith("Double movement in [terrainFilter]"), DeprecationLevel.WARNING)
    DoubleMovementCoast("Double movement in coast", UniqueTarget.Unit),
    @Deprecated("As of 3.17.1", ReplaceWith("Double movement in [terrainFilter]"), DeprecationLevel.WARNING)
    DoubleMovementForestJungle("Double movement rate through Forest and Jungle", UniqueTarget.Unit),
    @Deprecated("As of 3.17.1", ReplaceWith("Double movement in [terrainFilter]"), DeprecationLevel.WARNING)
    DoubleMovementSnowTundraHill("Double movement in Snow, Tundra and Hills", UniqueTarget.Unit),
    AllTilesCost1Move("All tiles cost 1 movement", UniqueTarget.Unit),
    CanPassImpassable("Can pass through impassable tiles", UniqueTarget.Unit),
    IgnoresTerrainCost("Ignores terrain cost", UniqueTarget.Unit),
    IgnoresZOC("Ignores Zone of Control", UniqueTarget.Unit),
    RoughTerrainPenalty("Rough terrain penalty", UniqueTarget.Unit),
    CanEnterIceTiles("Can enter ice tiles", UniqueTarget.Unit),
    CannotEnterOcean("Cannot enter ocean tiles", UniqueTarget.Unit),
    CannotEnterOceanUntilAstronomy("Cannot enter ocean tiles until Astronomy", UniqueTarget.Unit),
    
    HiddenWithoutReligion("Hidden when religion is disabled", UniqueTarget.Unit, UniqueTarget.Building, UniqueTarget.Ruins),

    //////////////////////////////////////// TERRAIN UNIQUES ////////////////////////////////////////


    NaturalWonderNeighborCount("Must be adjacent to [amount] [simpleTerrain] tiles", UniqueTarget.Terrain),
    NaturalWonderNeighborsRange("Must be adjacent to [amount] to [amount] [simpleTerrain] tiles", UniqueTarget.Terrain),
    NaturalWonderSmallerLandmass("Must not be on [amount] largest landmasses", UniqueTarget.Terrain),
    NaturalWonderLargerLandmass("Must be on [amount] largest landmasses", UniqueTarget.Terrain),
    NaturalWonderLatitude("Occurs on latitudes from [amount] to [amount] percent of distance equator to pole", UniqueTarget.Terrain),
    NaturalWonderGroups("Occurs in groups of [amount] to [amount] tiles", UniqueTarget.Terrain),
    NaturalWonderConvertNeighbors("Neighboring tiles will convert to [baseTerrain]", UniqueTarget.Terrain),

    // The "Except [terrainFilter]" could theoretically be implemented with a conditional
    NaturalWonderConvertNeighborsExcept("Neighboring tiles except [baseTerrain] will convert to [baseTerrain]", UniqueTarget.Terrain),

    TerrainGrantsPromotion("Grants [promotion] ([comment]) to adjacent [mapUnitFilter] units for the rest of the game", UniqueTarget.Terrain),

    TileProvidesYieldWithoutPopulation("Tile provides yield without assigned population", UniqueTarget.Terrain, UniqueTarget.Improvement),
    NullifyYields("Nullifies all other stats this tile provides", UniqueTarget.Terrain),
    VisibilityElevation("Has an elevation of [amount] for visibility calculations", UniqueTarget.Terrain),

    NoNaturalGeneration("Doesn't generate naturally", UniqueTarget.Terrain),

    OverrideDepositAmountOnTileFilter("Deposits in [tileFilter] tiles always provide [amount] resources", UniqueTarget.Resource),

    ///////////////////////////////////////// CONDITIONALS /////////////////////////////////////////

    
    // civ conditionals
    ConditionalWar("when at war", UniqueTarget.Conditional),
    ConditionalNotWar("when not at war", UniqueTarget.Conditional),
    ConditionalHappy("while the empire is happy", UniqueTarget.Conditional),
    ConditionalGoldenAge("during a Golden Age", UniqueTarget.Conditional),

    // city conditionals
    ConditionalSpecialistCount("if this city has at least [amount] specialists", UniqueTarget.Conditional),

    // unit conditionals
    ConditionalOurUnit("for [mapUnitFilter] units", UniqueTarget.Conditional),
    ConditionalVsCity("vs cities", UniqueTarget.Conditional),
    ConditionalVsUnits("vs [mapUnitFilter] units", UniqueTarget.Conditional),
    ConditionalVsLargerCiv("when fighting units from a Civilization with more Cities than you", UniqueTarget.Conditional),
    ConditionalAttacking("when attacking", UniqueTarget.Conditional),
    ConditionalDefending("when defending", UniqueTarget.Conditional),
    ConditionalInTiles("when fighting in [tileFilter] tiles", UniqueTarget.Conditional),
//    ConditionalIntercepting("when intercepting", UniqueTarget.Conditional),

    // tile conditionals
    ConditionalNeighborTiles("with [amount] to [amount] neighboring [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalNeighborTilesAnd("with [amount] to [amount] neighboring [tileFilter] [tileFilter] tiles", UniqueTarget.Conditional),

    ///////////////////////////////////////// TRIGGERED ONE-TIME /////////////////////////////////////////

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
    TimedAttackStrength("+[amount]% attack strength to all [mapUnitFilter] Units for [amount] turns", UniqueTarget.Global),  // used in Policy
    FreeStatBuildings("Provides the cheapest [stat] building in your first [amount] cities for free", UniqueTarget.Global),  // used in Policy
    FreeSpecificBuildings("Provides a [buildingName] in your first [amount] cities for free", UniqueTarget.Global),  // used in Policy
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
