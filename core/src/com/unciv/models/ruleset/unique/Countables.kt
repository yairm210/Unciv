package com.unciv.models.ruleset.unique

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.stats.Stat
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters

/**
 *  Prototype for each new [Countables] instance, to ensure a baseline.
 *
 *  Notes:
 *  - Each instance ***must*** implement _either_ overload of [validate] and indicate which one via [validateWithRuleset].
 *  - Override [getKnownValuesForAutocomplete] only if a sensible number of suggestions is obvious.
 */
interface ICountable {
    fun validate(parameterText: String): Boolean = false
    val validateWithRuleset: Boolean
        get() = false
    fun validate(parameterText: String, ruleset: Ruleset): Boolean = false
    fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int?
    fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> = emptySet()
}

/**
 *  Contains all knowledge about how to check and evaluate [countable Unique parameters][UniqueParameterType.Countable].
 *
 *  Expansion instructions:
 *  - A new simple "variable" needs to implement only [simpleName] and [eval].
 *  - A new "variable" using placeholder(s) needs to implement [validate] and [eval].
 *    Using [simpleName] inside [validate] as the examples do is only done for readability.
 *    Implement [getKnownValuesForAutocomplete] only when a meaningful, not too large set of suggestions is obvious.
 *  - A new countable that draws from an existing enum or set of RulesetObjects should work along the lines of the [Stats] or [TileResources] examples.
 *  - When implementing a formula language for Countables, create a new object in a separate file with the actual
 *    implementation, then a new instance here that delegates all its methods to that object. And delete these lines.
 */
enum class Countables(protected val simpleName: String = "") : ICountable {
    Integer {
        override fun validate(parameterText: String) = parameterText.toIntOrNull() != null
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) = parameterText.toIntOrNull()
    },

    Turns("turns") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) =
            stateForConditionals.gameInfo?.turns
    },
    Year("year") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) =
            stateForConditionals.gameInfo?.run { getYear(turns) }
    },
    Cities("Cities") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) =
            stateForConditionals.civInfo?.cities?.size
    },
    Units("Units") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) =
            stateForConditionals.civInfo?.units?.getCivUnitsSize()
    },

    Stats {
        override fun validate(parameterText: String) = Stat.isStat(parameterText)
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val relevantStat = Stat.safeValueOf(parameterText) ?: return null
            return stateForConditionals.getStatAmount(relevantStat)
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = Stat.names()
    },

    PolicyBranches("Completed Policy branches") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) =
            stateForConditionals.civInfo?.getCompletedPolicyBranchesCount()
    },

    FilteredCities("[] Cities") {
        override fun validate(parameterText: String) = parameterText.equalsPlaceholderText(simpleName)
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val cities = stateForConditionals.civInfo?.cities ?: return null
            return cities.count { it.matchesFilter(filter) }
        }
    },

    FilteredUnits("[] Units") {
        override fun validate(parameterText: String) = parameterText.equalsPlaceholderText(simpleName)
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val unitManager = stateForConditionals.civInfo?.units ?: return null
            return unitManager.getCivUnits().count { it.matchesFilter(filter) }
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            (ruleset.unitTypes.keys + ruleset.units.keys).mapTo(mutableSetOf()) { "[$it] Units" }
    },

    FilteredBuildings("[] Buildings") {
        override fun validate(parameterText: String) = parameterText.equalsPlaceholderText(simpleName)
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val cities = stateForConditionals.civInfo?.cities ?: return null
            return cities.sumOf { city ->
                city.cityConstructions.getBuiltBuildings().count { it.matchesFilter(filter) }
            }
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    RemainingCivs("Remaining [] Civilizations") {
        override fun validate(parameterText: String) = parameterText.equalsPlaceholderText(simpleName)
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val civilizations = stateForConditionals.gameInfo?.civilizations ?: return null
            return civilizations.count { it.isAlive() && it.matchesFilter(filter) }
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    OwnedTiles("Owned [] Tiles") {
        override fun validate(parameterText: String) = parameterText.equalsPlaceholderText(simpleName)
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val cities = stateForConditionals.civInfo?.cities ?: return null
            return cities.sumOf { city ->
                city.getTiles().count { it.matchesFilter(filter) }
            }
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    TileResources {
        override val validateWithRuleset = true
        override fun validate(parameterText: String, ruleset: Ruleset) = parameterText in ruleset.tileResources
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) =
            stateForConditionals.getResourceAmount(parameterText)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.tileResources.keys
    }
    ;

    // Leave these in place only for the really simple cases
    override fun validate(parameterText: String) = parameterText == simpleName
    override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf(simpleName)

    companion object {
        fun getCountableAmount(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val ruleset = stateForConditionals.gameInfo?.ruleset
            for (countable in Countables.entries) {
                if (countable.validateWithRuleset)
                    if (ruleset == null || !countable.validate(parameterText, ruleset)) continue
                else
                    if (!countable.validate(parameterText)) continue
                val potentialResult = countable.eval(parameterText, stateForConditionals)
                if (potentialResult != null) return potentialResult
            }
            return null
        }

        fun isKnownValue(parameterText: String, ruleset: Ruleset) =
            Countables.entries.any {
                if (it.validateWithRuleset) it.validate(parameterText, ruleset) else it.validate(parameterText)
            }

        // This will "leak memory" if game rulesets are changed over application lifetime, but it's a simple way to cache
        private val autocompleteCache = mutableMapOf<Ruleset, Set<String>>()
        fun getKnownValuesForAutocomplete(ruleset: Ruleset) =
            autocompleteCache.getOrPut(ruleset) {
                Countables.entries.fold(setOf()) { acc, next ->
                    acc + next.getKnownValuesForAutocomplete(ruleset)
                }
            }
    }
}
