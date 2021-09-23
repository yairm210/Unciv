package com.unciv.models.ruleset.unique

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText

/** Buildings, units, nations, policies, religions, techs etc.
 * Basically anything caught by CivInfo.getMatchingUniques. */
enum class UniqueTarget {
    Global,

    // Civilization-specific
    Nation,
    Era,
    Tech,
    Policy,
    Belief,

    // City-specific
    Building,
    Wonder,

    // Unit-specific
    Unit,
    UnitType,
    Promotion,

    // Tile-specific
    Terrain,
    Improvement,
    Resource,
    Ruins,

    // Other
    CityState,
    ModOptions,
    Conditional,
}

enum class UniqueType(val text:String, vararg target: UniqueTarget) {

    Stats("[stats]", UniqueTarget.Global),
    StatsPerCity("[stats] [cityFilter]", UniqueTarget.Global),

    StatPercentBonus("[amount]% [Stat]", UniqueTarget.Global),

    ConsumesResources("Consumes [amount] [resource]",
        UniqueTarget.Improvement, UniqueTarget.Building, UniqueTarget.Unit), // No conditional support as of yet
    ProvidesResources("Provides [amount] [resource]",
            UniqueTarget.Improvement, UniqueTarget.Building),

    FreeUnits("[amount] units cost no maintenance", UniqueTarget.Global),
    UnitMaintenanceDiscount("[amount]% maintenance costs for [mapUnitFilter] units", UniqueTarget.Global),

    @Deprecated("As of 3.16.16", ReplaceWith("[amount]% maintenance costs for [mapUnitFilter] units"))
    DecreasedUnitMaintenanceCostsByFilter("-[amount]% [mapUnitFilter] unit maintenance costs"), // No conditional support
    @Deprecated("As of 3.16.16", ReplaceWith("[amount]% maintenance costs for [mapUnitFilter] units"))
    DecreasedUnitMaintenanceCostsGlobally("-[amount]% unit upkeep costs"), // No conditional support
    @Deprecated("As of 3.16.16", ReplaceWith("[stats] <if this city has at least [amount] specialists>"))
    StatBonusForNumberOfSpecialists("[stats] if this city has at least [amount] specialists"), // No conditional support

    // TODO: Unify these (I'm in favor of "gain a free" above "provides" because it fits more cases)
    ProvidesFreeBuildings("Provides a free [buildingName] [cityFilter]", UniqueTarget.Global),
    GainFreeBuildings("Gain a free [buildingName] [cityFilter]", UniqueTarget.Global),

    // I don't like the fact that currently "city state bonuses" are separate from the "global bonuses",
    // todo: merge city state bonuses into global bonuses
    CityStateStatsPerTurn("Provides [stats] per turn", UniqueTarget.CityState), // Should not be Happiness!
    CityStateStatsPerCity("Provides [stats] [cityFilter] per turn", UniqueTarget.CityState),
    CityStateHappiness("Provides [amount] Happiness", UniqueTarget.CityState),
    CityStateMilitaryUnits("Provides military units every ≈[amount] turns", UniqueTarget.CityState), // No conditional support as of yet
    CityStateUniqueLuxury("Provides a unique luxury", UniqueTarget.CityState), // No conditional support as of yet

    NaturalWonderNeighborCount("Must be adjacent to [amount] [terrainFilter] tiles", UniqueTarget.Terrain),
    NaturalWonderNeighborsRange("Must be adjacent to [amount] to [amount] [terrainFilter] tiles", UniqueTarget.Terrain),
    NaturalWonderLandmass("Must not be on [amount] largest landmasses", UniqueTarget.Terrain),
    NaturalWonderLatitude("Occurs on latitudes from [amount] to [amount] percent of distance equator to pole", UniqueTarget.Terrain),
    NaturalWonderGroups("Occurs in groups of [amount] to [amount] tiles", UniqueTarget.Terrain),
    NaturalWonderConvertNeighbors("Neighboring tiles will convert to [baseTerrain]", UniqueTarget.Terrain),
    NaturalWonderConvertNeighborsExcept("Neighboring tiles except [terrainFilter] will convert to [baseTerrain]", UniqueTarget.Terrain),


    ///// CONDITIONALS

    ConditionalWar("when at war", UniqueTarget.Conditional),
    ConditionalNotWar("when not at war", UniqueTarget.Conditional),
    ConditionalSpecialistCount("if this city has at least [amount] specialists", UniqueTarget.Conditional),
    ConditionalHappy("while the empire is happy", UniqueTarget.Conditional),
    ;

    /** For uniques that have "special" parameters that can accept multiple types, we can override them manually
     *  For 95% of cases, auto-matching is fine. */
    val parameterTypeMap = ArrayList<List<UniqueParameterType>>()
    val replacedBy: UniqueType? = null

    init {
        for (placeholder in text.getPlaceholderParameters()) {
            val matchingParameterType =
                UniqueParameterType.values().firstOrNull { it.parameterName == placeholder }
                    ?: UniqueParameterType.Unknown
            parameterTypeMap.add(listOf(matchingParameterType))
        }
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
