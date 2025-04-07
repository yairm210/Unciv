package com.unciv.models.ruleset.unique

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.stats.Stat
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import org.jetbrains.annotations.VisibleForTesting

/**
 *  Prototype for each new [Countables] instance, to ensure a baseline.
 *
 *  Notes:
 *  - Each instance ***must*** implement _either_ overload of [matches] and indicate which one via [matchesWithRuleset].
 *  - [matches] is used to look up which instance implements a given string, [getErrorSeverity] is responsible for validating placeholders.
 *  - Override [getKnownValuesForAutocomplete] only if a sensible number of suggestions is obvious.
 */
interface ICountable {
    fun matches(parameterText: String): Boolean = false
    val matchesWithRuleset: Boolean
        get() = false
    fun matches(parameterText: String, ruleset: Ruleset): Boolean = false
    fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int?
    fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> = emptySet()
    fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity?
    fun getDeprecationAnnotation(): Deprecated?
}

/**
 *  Contains all knowledge about how to check and evaluate [countable Unique parameters][UniqueParameterType.Countable].
 *
 *  Expansion instructions:
 *  - A new simple "variable" needs to implement only [text] and [eval].
 *  - A new "variable" _using placeholder(s)_ needs to implement [matches] and [eval].
 *    - Using [simpleName] inside [validate] as the examples do is only done for readability.
 *    - Implement [getErrorSeverity] in most cases, use [UniqueParameterType] to validate each placeholder content.
 *    - Implement [getKnownValuesForAutocomplete] only when a meaningful, not too large set of suggestions is obvious.
 *  - A new countable that draws from an existing enum or set of RulesetObjects should work along the lines of the [Stats] or [TileResources] examples.
 *  - When implementing a formula language for Countables, create a new object in a separate file with the actual
 *    implementation, then a new instance here that delegates all its methods to that object. And delete these lines.
 */
enum class Countables(
    val text: String = "",
    private val shortDocumentation: String = "",
    open val documentationStrings: List<String> = emptyList()
) : ICountable {
    Integer {
        override val documentationHeader = "Integer constant - any positive or negative integer number"
        override fun matches(parameterText: String) = parameterText.toIntOrNull() != null
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) = parameterText.toIntOrNull()
    },

    Turns("turns", shortDocumentation = "Number of turns played") {
        override val documentationStrings = listOf("(Always starts at zero irrespective of game speed or start era)")
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) =
            stateForConditionals.gameInfo?.turns
    },
    Year("year", shortDocumentation = "The current year") {
        override val documentationStrings = listOf("(Depends on game speed or start era, negative for years BC)")
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) =
            stateForConditionals.gameInfo?.run { getYear(turns) }
    },
    Cities("Cities", shortDocumentation = "The number of cities the relevant Civilization owns") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) =
            stateForConditionals.civInfo?.cities?.size
    },
    Units("Units", shortDocumentation = "The number of units the relevant Civilization owns") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) =
            stateForConditionals.civInfo?.units?.getCivUnitsSize()
    },

    Stats {
        override val documentationHeader = "Stat name (${Stat.names().niceJoin()})"
        override val documentationStrings = listOf("gets the stat *reserve*, not the amount per turn (can be city stats or civilization stats, depending on where the unique is used)")
        override fun matches(parameterText: String) = Stat.isStat(parameterText)
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val relevantStat = Stat.safeValueOf(parameterText) ?: return null
            // This one isn't covered by City.getStatReserve or Civilization.getStatReserve but should be available here
            if (relevantStat == Stat.Happiness)
                return stateForConditionals.civInfo?.getHappiness()
            return stateForConditionals.getStatAmount(relevantStat)
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = Stat.names()
        private fun Iterable<String>.niceJoin() = joinToString("`, `", "`", "`").run {
            val index = lastIndexOf("`, `")
            substring(0, index) + "` or `" + substring(index + 4)
        }
    },

    PolicyBranches("Completed Policy branches") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) =
            stateForConditionals.civInfo?.getCompletedPolicyBranchesCount()
    },

    FilteredCities("[cityFilter] Cities") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val cities = stateForConditionals.civInfo?.cities ?: return null
            return cities.count { it.matchesFilter(filter) }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.CityFilter.getErrorSeverity(parameterText.getPlaceholderParameters().first(), ruleset)
    },

    FilteredUnits("[mapUnitFilter] Units") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val unitManager = stateForConditionals.civInfo?.units ?: return null
            return unitManager.getCivUnits().count { it.matchesFilter(filter) }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.MapUnitFilter.getErrorSeverity(parameterText.getPlaceholderParameters().first(), ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            (ruleset.unitTypes.keys + ruleset.units.keys).mapTo(mutableSetOf()) { "[$it] Units" }
    },

    FilteredBuildings("[buildingFilter] Buildings") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val cities = stateForConditionals.civInfo?.cities ?: return null
            return cities.sumOf { city ->
                city.cityConstructions.getBuiltBuildings().count { it.matchesFilter(filter) }
            }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.BuildingFilter.getErrorSeverity(parameterText.getPlaceholderParameters().first(), ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    RemainingCivs("Remaining [civFilter] Civilizations") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val civilizations = stateForConditionals.gameInfo?.civilizations ?: return null
            return civilizations.count { it.isAlive() && it.matchesFilter(filter) }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.CivFilter.getErrorSeverity(parameterText.getPlaceholderParameters().first(), ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    OwnedTiles("Owned [tileFilter] Tiles") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val cities = stateForConditionals.civInfo?.cities ?: return null
            return cities.sumOf { city -> city.getTiles().count { it.matchesFilter(filter) } }
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    TileResources {
        override val documentationHeader = "Resource name - From [TileResources.json](3-Map-related-JSON-files.md#tileresourcesjson)"
        override val documentationStrings = listOf(
            "(can be city stats or civilization stats, depending on where the unique is used)",
            "For example: If a unique is placed on a building, then the retrieved resources will be of the city. If placed on a policy, they will be of the civilization.",
            "This can make a difference for e.g. local resources, which are counted per city."
        )
        override val matchesWithRuleset = true
        override fun matches(parameterText: String, ruleset: Ruleset) = parameterText in ruleset.tileResources
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals) =
            stateForConditionals.getResourceAmount(parameterText)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.tileResources.keys
    },

    /** Please leave this one in, it is tested against in [com.unciv.uniques.CountableTests.testRulesetValidation] */
    @Deprecated("because it was never actually supported", ReplaceWith("Remaining [City-State] Civilizations"), DeprecationLevel.ERROR)
    CityStates("City-States", shortDocumentation = "counts all undefeated city-states") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val civilizations = stateForConditionals.gameInfo?.civilizations ?: return null
            return civilizations.count { it.isAlive() && it.isCityState }
        }
    }
    ;

    val placeholderText = text.getPlaceholderText()

    @VisibleForTesting
    open val noPlaceholders = !text.contains('[')

    // Leave these in place only for the really simple cases
    override fun matches(parameterText: String) = if (noPlaceholders) parameterText == text 
        else parameterText.equalsPlaceholderText(placeholderText)
    override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf(text)

    open val documentationHeader get() =
        "`$text`" + (if (shortDocumentation.isEmpty()) "" else " - $shortDocumentation")

    /** Leave this only for Countables without any parameters - they can rely on [matches] having validated enough */
    override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? = null

    override fun getDeprecationAnnotation(): Deprecated? = declaringJavaClass.getField(name).getAnnotation(Deprecated::class.java)

    companion object {
        fun getMatching(parameterText: String, ruleset: Ruleset?) = Countables.entries
            .filter {
                if (it.matchesWithRuleset) it.matches(parameterText, ruleset!!) else it.matches(parameterText)
            }

        fun getCountableAmount(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val ruleset = stateForConditionals.gameInfo?.ruleset
            for (countable in Countables.getMatching(parameterText, ruleset)) {
                val potentialResult = countable.eval(parameterText, stateForConditionals)
                if (potentialResult != null) return potentialResult
            }
            return null
        }

        fun isKnownValue(parameterText: String, ruleset: Ruleset) = getMatching(parameterText, ruleset).any()

        // This will "leak memory" if game rulesets are changed over application lifetime, but it's a simple way to cache
        private val autocompleteCache = mutableMapOf<Ruleset, Set<String>>()
        fun getKnownValuesForAutocomplete(ruleset: Ruleset) =
            autocompleteCache.getOrPut(ruleset) {
                Countables.entries.fold(setOf()) { acc, next ->
                    acc + next.getKnownValuesForAutocomplete(ruleset)
                }
            }

        fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
            var result = UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
            for (countable in Countables.getMatching(parameterText, ruleset)) {
                // If any Countable is happy, we're happy
                result = countable.getErrorSeverity(parameterText, ruleset) ?: return null
            }
            // return last result or default for simplicity - could do a max() instead
            return result
        }
    }
}
