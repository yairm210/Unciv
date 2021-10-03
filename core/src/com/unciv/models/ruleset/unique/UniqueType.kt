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

    Stats("[stats]", UniqueTarget.Global),
    StatsPerCity("[stats] [cityFilter]", UniqueTarget.Global),
    @Deprecated("As of 3.16.16", ReplaceWith("[stats] <if this city has at least [amount] specialists>"), DeprecationLevel.WARNING)
    StatBonusForNumberOfSpecialists("[stats] if this city has at least [amount] specialists", UniqueTarget.Global),

    StatPercentBonus("[amount]% [Stat]", UniqueTarget.Global),
    BonusStatsFromCityStates("[amount]% [stat] from City-States", UniqueTarget.Global),

    RemoveAnnexUnhappiness("Remove extra unhappiness from annexed cities", UniqueTarget.Building),

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

    GrowthPercentBonus("[amount]% growth [cityFilter]", UniqueTarget.Global),
    @Deprecated("As of 3.16.14", ReplaceWith("[amount]% growth [cityFilter]"), DeprecationLevel.WARNING)
    GrowthPercentBonusPositive("+[amount]% growth [cityFilter]", UniqueTarget.Global),
    @Deprecated("As of 3.16.14", ReplaceWith("[amount]% growth [cityFilter] <when not at war>"), DeprecationLevel.WARNING)
    GrowthPercentBonusWhenNotAtWar("+[amount]% growth [cityFilter] when not at war", UniqueTarget.Global),

    // TODO: Unify these (I'm in favor of "gain a free" above "provides" because it fits more cases)
    ProvidesFreeBuildings("Provides a free [buildingName] [cityFilter]", UniqueTarget.Global),
    GainFreeBuildings("Gain a free [buildingName] [cityFilter]", UniqueTarget.Global),

    FreeExtraBeliefs("May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion", UniqueTarget.Global),
    FreeExtraAnyBeliefs("May choose [amount] additional of any type when [foundingOrEnhancing] a religion", UniqueTarget.Global),

    ///////////////////////////////////////// UNIT UNIQUES /////////////////////////////////////////

    Strength("[amount]% Strength", UniqueTarget.Unit, UniqueTarget.Global),

    @Deprecated("As of 3.17.3", ReplaceWith("[amount]% Strength"), DeprecationLevel.WARNING)
    StrengthPlus("+[amount]% Strength", UniqueTarget.Unit),
    @Deprecated("As of 3.17.3", ReplaceWith("[amount]% Strength"), DeprecationLevel.WARNING)
    StrengthMin("-[amount]% Strength", UniqueTarget.Unit),
    @Deprecated("As of 3.17.3", ReplaceWith("[amount]% Strength <vs [mapUnitFilter] units> OR [amount]% Strength <vs cities>"), DeprecationLevel.WARNING)
    StrengthPlusVs("+[amount]% Strength vs [combatantFilter]", UniqueTarget.Unit),
    @Deprecated("As of 3.17.3", ReplaceWith("[amount]% Strength <vs [mapUnitFilter] units> OR [amount]% Strength <vs cities>"), DeprecationLevel.WARNING)
    StrengthMinVs("-[amount]% Strength vs [combatantFilter]", UniqueTarget.Unit),
    @Deprecated("As of 3.17.3", ReplaceWith("[amount]% Strength"), DeprecationLevel.WARNING)
    CombatBonus("+[amount]% Combat Strength", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5", ReplaceWith("[amount]% Strength <when attacking>"), DeprecationLevel.WARNING)
    StrengthAttacking("+[amount]% Strength when attacking", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5", ReplaceWith("[amount]% Strength <shen defending>"), DeprecationLevel.WARNING)
    StrengthDefending("+[amount]% Strength when defending", UniqueTarget.Unit),
    @Deprecated("As of 3.17.5", ReplaceWith("[amount]% Strength <when defending> <vs [mapUnitFilter] units>"), DeprecationLevel.WARNING)
    StrengthDefendingUnitFilter("[amount]% Strength when defending vs [mapUnitFilter] units", UniqueTarget.Unit),

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

    NoNaturalGeneration("Doesn't generate naturally", UniqueTarget.Terrain),

    ///////////////////////////////////////// CONDITIONALS /////////////////////////////////////////

    ConditionalWar("when at war", UniqueTarget.Conditional),
    ConditionalNotWar("when not at war", UniqueTarget.Conditional),
    ConditionalSpecialistCount("if this city has at least [amount] specialists", UniqueTarget.Conditional),
    ConditionalHappy("while the empire is happy", UniqueTarget.Conditional),
    ConditionalVsCity("vs cities", UniqueTarget.Conditional),
    ConditionalVsUnits("vs [mapUnitFilter] units", UniqueTarget.Conditional),
    ConditionalAttacking("when attacking", UniqueTarget.Conditional),
    ConditionalDefending("when defending", UniqueTarget.Conditional),
//    ConditionalIntercepting("when intercepting", UniqueTarget.Conditional),
//    ConditionalInTiles("fighting in [tileFilter] tiles", UniqueTarget.Conditional),

    ConditionalNeighborTiles("with [amount] to [amount] neighboring [tileFilter] tiles", UniqueTarget.Conditional),
    ConditionalNeighborTilesAnd("with [amount] to [amount] neighboring [tileFilter] [tileFilter] tiles", UniqueTarget.Conditional),
    ;

    /** For uniques that have "special" parameters that can accept multiple types, we can override them manually
     *  For 95% of cases, auto-matching is fine. */
    val parameterTypeMap = ArrayList<List<UniqueParameterType>>()
    val targetTypes = HashSet<UniqueTarget>()

    init {
        for (placeholder in text.getPlaceholderParameters()) {
            val matchingParameterType =
                UniqueParameterType.values().firstOrNull { it.parameterName == placeholder }
                    ?: UniqueParameterType.Unknown
            parameterTypeMap.add(listOf(matchingParameterType))
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
