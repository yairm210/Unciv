package com.unciv.models.ruleset

import com.unciv.models.stats.Stats
import com.unciv.models.translations.*
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.worldscreen.unit.UnitActions
import kotlin.random.Random


// parameterName values should be compliant with autogenerated values in TranslationFileWriter.generateStringsFromJSONs
// Eventually we'll merge the translation generation to take this as the source of that
enum class UniqueParameterType(val parameterName:String) {
    Number("amount") {
        override fun getErrorType(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            return if (parameterText.toIntOrNull() != null) UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
            else null
        }
    },
    UnitFilter("unitType") {
        override fun getErrorType(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if(ruleset.unitTypes.containsKey(parameterText) || unitTypeStrings.contains(parameterText)) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },
    Unknown("param") {
        override fun getErrorType(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            return null
        }
    };

    abstract fun getErrorType(parameterText:String, ruleset: Ruleset): UniqueType.UniqueComplianceErrorSeverity?

    companion object {
        val unitTypeStrings = hashSetOf(
            "Military",
            "Civilian",
            "non-air",
            "relevant",
            "Nuclear Weapon",
            "City",
            // These are up for debate
            "Air",
            "land units",
            "water units",
            "air units",
            "military units",
            "submarine units",
            // Note: this can't handle combinations of parameters (e.g. [{Military} {Water}])
        )
    }
}

class UniqueComplianceError(
    val parameterName: String,
    val acceptableParameterTypes: List<UniqueParameterType>,
    val errorSeverity: UniqueType.UniqueComplianceErrorSeverity
)


enum class UniqueType(val text:String) {

    ConsumesResources("Consumes [amount] [resource]");

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
                acceptableParamTypes.map { it.getErrorType(param, ruleset) }
            if (errorTypesForAcceptableParameters.any { it == null }) continue // This matches one of the types!
            val leastSevereWarning =
                errorTypesForAcceptableParameters.maxByOrNull { it!!.ordinal }!!
            errorList += UniqueComplianceError(param, acceptableParamTypes, leastSevereWarning)
        }
        return errorList
    }
}



class Unique(val text: String) {
    /** This is so the heavy regex-based parsing is only activated once per unique, instead of every time it's called
     *  - for instance, in the city screen, we call every tile unique for every tile, which can lead to ANRs */
    val placeholderText = text.getPlaceholderText()
    val params = text.getPlaceholderParameters()
    val type = UniqueType.values().firstOrNull { it.placeholderText == placeholderText }
    val stats: Stats by lazy {
        val firstStatParam = params.firstOrNull { Stats.isStats(it) }
        if (firstStatParam == null) Stats() // So badly-defined stats don't crash the entire game
        else Stats.parse(firstStatParam)
    }
    val conditionals: List<Unique> = text.getConditionals()

    fun isOfType(uniqueType: UniqueType) = uniqueType == type

    /** We can't save compliance errors in the unique, since it's ruleset-dependant */
    fun matches(uniqueType: UniqueType, ruleset: Ruleset) = isOfType(uniqueType)
        && uniqueType.getComplianceErrors(this, ruleset).isEmpty()
    
    // This function will get LARGE, as it will basically check for all conditionals if they apply
    // This will require a lot of parameters to be passed (attacking unit, tile, defending unit, civInfo, cityInfo, ...)
    // I'm open for better ideas, but this was the first thing that I could think of that would
    // work in all cases.
    fun conditionalsApply(civInfo: CivilizationInfo? = null): Boolean {
        for (condition in conditionals) {
            if (!conditionalApplies(condition, civInfo)) return false
        }
        return true
    }
    
    private fun conditionalApplies(
        condition: Unique, 
        civInfo: CivilizationInfo? = null
    ): Boolean {
        return when (condition.placeholderText) {
            "when not at war" -> civInfo?.isAtWar() == false
            "when at war" -> civInfo?.isAtWar() == true
            else -> false
        }
    }
}

class UniqueMap:HashMap<String, ArrayList<Unique>>() {
    fun addUnique(unique: Unique) {
        if (!containsKey(unique.placeholderText)) this[unique.placeholderText] = ArrayList()
        this[unique.placeholderText]!!.add(unique)
    }

    fun getUniques(placeholderText: String): Sequence<Unique> {
        val result = this[placeholderText]
        if (result == null) return sequenceOf()
        else return result.asSequence()
    }

    fun getAllUniques() = this.asSequence().flatMap { it.value.asSequence() }
}
