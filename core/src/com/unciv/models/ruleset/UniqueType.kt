package com.unciv.models.ruleset

import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText

enum class UniqueType(val text:String, val replacedBy: UniqueType? = null) {

    ConsumesResources("Consumes [amount] [resource]"),
    FreeUnits("[amount] units cost no maintenance"),
    UnitMaintenanceDiscount("[amount]% maintenance costs for [mapUnitFilter] units"),
    @Deprecated("As of 3.16.16")
    DecreasedUnitMaintenanceCostsByFilter("-[amount]% [mapUnitFilter] unit maintenance costs", UnitMaintenanceDiscount),
    @Deprecated("As of 3.16.16")
    DecreasedUnitMaintenanceCostsGlobally("-[amount]% unit upkeep costs", UnitMaintenanceDiscount),
    StatBonusForNumberOfSpecialists("[stats] if this city has at least [amount] specialists"),
    StatsPerCity("[stats] [cityFilter]")
    ;

    /** For uniques that have "special" parameters that can accept multiple types, we can override them manually
     *  For 95% of cases, auto-matching is fine. */
    private val parameterTypeMap = ArrayList<List<UniqueParameterType>>()

    init {
        for (placeholder in text.getPlaceholderParameters()) {
            val matchingParameterType =
                UniqueParameterType.values().firstOrNull { it.parameterName == placeholder }
                    ?: UniqueParameterType.Unknown
            parameterTypeMap.add(listOf(matchingParameterType))
        }
    }

    val placeholderText = text.getPlaceholderText()

    /** Ordinal determines severity - ordered from most severe at 0 */
    enum class UniqueComplianceErrorSeverity {

        /** This is a problem like "numbers don't parse", "stat isn't stat", "city filter not applicable" */
        RulesetInvariant,

        /** This is a problem like "unit/resource/tech name doesn't exist in ruleset" - definite bug */
        RulesetSpecific,

        /** This is for filters that can also potentially accept free text, like UnitFilter and TileFilter */
        WarningOnly
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
                errorTypesForAcceptableParameters.maxByOrNull { it!!.ordinal }!!
            errorList += UniqueComplianceError(param, acceptableParamTypes, leastSevereWarning)
        }
        return errorList
    }
}