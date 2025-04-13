package com.unciv.models.ruleset.unique

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.stats.Stat
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import org.jetbrains.annotations.VisibleForTesting

/**
 *  Prototype for each new [Countables] instance, core functionality, to ensure a baseline.
 *
 *  Notes:
 *  - [matches] is used to look up which instance implements a given string, **without** validating its placeholders.
 *    It can be called with or without a ruleset. The ruleset is **only** to be used if there is no selective pattern
 *    to detect when a specific countable is "responsible" for a certain input, and for these, when `matches` is called
 *    without a ruleset, they must return `MatchResult.No` (Example below: TileResource).
 *  - [getErrorSeverity] is responsible for validating placeholders, _and can assume [matches] was successful_.
 *  - Override [getKnownValuesForAutocomplete] only if a sensible number of suggestions is obvious.
 */
interface ICountable {
    /** Supports `MatchResult(true)`to get [Yes], MatchResult(false)`to get [No], or MatchResult(null)`to get [Maybe] */
    enum class MatchResult {
        No {
            override fun isOK(strict: Boolean) = false
        },
        Maybe {
            override fun isOK(strict: Boolean) = !strict
        },
        Yes {
            override fun isOK(strict: Boolean) = true
        }
        ;
        abstract fun isOK(strict: Boolean): Boolean
        companion object {
            operator fun invoke(bool: Boolean?) = when(bool) {
                true -> Yes
                false -> No
                else -> Maybe
            }
        }
    }

    fun matches(parameterText: String, ruleset: Ruleset? = null): MatchResult = MatchResult.No
    fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int?
    fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> = emptySet()
    fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity?
}

/**
 *  Contains all knowledge about how to check and evaluate [countable Unique parameters][UniqueParameterType.Countable].
 *
 *  Expansion instructions:
 *  - A new simple "variable" needs to implement only [text] and [eval].
 *  - Not supplying [text] means the "variable" **must** implement either [matches] overload. If it parses placeholders, then it **must** override [noPlaceholders] to `false`.
 *  - A new "variable" _using placeholder(s)_ needs to implement [matches] and [eval].
 *    - Implement [getErrorSeverity] in most cases, typically using [UniqueParameterType] to validate each placeholder content.
 *    - If it uses exactly one UniqueParameterType placeholder, [getErrorSeverity] can use the [UniqueParameterType.getTranslatedErrorSeverity] extension provided below.
 *    - Implement [getKnownValuesForAutocomplete] only when a meaningful, not too large set of suggestions is obvious.
 *  - A new countable that draws from an existing enum or set of RulesetObjects should work along the lines of the [Stats] or [TileResources] examples.
 *  - **Do** heed the docs of [ICountable] - but be aware the [Countables] Enum class pre-implements some of the methods.
 *  - Run the unit tests! There's one checking implementation conventions.
 *  - When implementing a formula language for Countables, create a new object in a separate file with the actual
 *    implementation, then a new instance here that delegates all its methods to that object. And delete these lines.
 *
 *  @param text The "key" to recognize this countable. If not empty, it will be included in translations.
 *              Placeholders should match a `UniqueParameterType` by its `parameterType`.
 *              If the countable implements non-UniqueParameterType placeholders, it may be better to leave this empty.
 */
enum class Countables(
    val text: String = "",
    private val shortDocumentation: String = "",
    open val documentationStrings: List<String> = emptyList()
) : ICountable {
    Integer {
        override val documentationHeader = "Integer constant - any positive or negative integer number"
        override fun matches(parameterText: String, ruleset: Ruleset?) = ICountable.MatchResult(parameterText.toIntOrNull() != null)
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
        override fun matches(parameterText: String, ruleset: Ruleset?) = ICountable.MatchResult(Stat.isStat(parameterText))
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
            UniqueParameterType.CityFilter.getTranslatedErrorSeverity(parameterText, ruleset)
    },

    FilteredUnits("[mapUnitFilter] Units") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val unitManager = stateForConditionals.civInfo?.units ?: return null
            return unitManager.getCivUnits().count { it.matchesFilter(filter) }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.MapUnitFilter.getTranslatedErrorSeverity(parameterText, ruleset)
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
            UniqueParameterType.BuildingFilter.getTranslatedErrorSeverity(parameterText, ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    RemainingCivs("Remaining [civFilter] Civilizations") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val civilizations = stateForConditionals.gameInfo?.civilizations ?: return null
            return civilizations.count { it.isAlive() && it.matchesFilter(filter) }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.CivFilter.getTranslatedErrorSeverity(parameterText, ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    OwnedTiles("Owned [tileFilter] Tiles") {
        override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val cities = stateForConditionals.civInfo?.cities ?: return null
            return cities.sumOf { city -> city.getTiles().count { it.matchesFilter(filter) } }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.TileFilter.getTranslatedErrorSeverity(parameterText, ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    TileResources {
        override val documentationHeader = "Resource name - From [TileResources.json](3-Map-related-JSON-files.md#tileresourcesjson)"
        override val documentationStrings = listOf(
            "(can be city stats or civilization stats, depending on where the unique is used)",
            "For example: If a unique is placed on a building, then the retrieved resources will be of the city. If placed on a policy, they will be of the civilization.",
            "This can make a difference for e.g. local resources, which are counted per city."
        )
        override fun matches(parameterText: String, ruleset: Ruleset?) = ICountable.MatchResult(ruleset?.tileResources?.containsKey(parameterText))
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
    ; //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region ' class-wide elements and ICountable'
    val placeholderText = text.getPlaceholderText()

    @VisibleForTesting
    open val noPlaceholders = !text.contains('[')

    // Leave these in place only for the really simple cases
    override fun matches(parameterText: String, ruleset: Ruleset?) = ICountable.MatchResult(
        if (noPlaceholders) parameterText == text
        else parameterText.equalsPlaceholderText(placeholderText)
    )

    override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf(text)

    open val documentationHeader get() =
        "`$text`" + (if (shortDocumentation.isEmpty()) "" else " - $shortDocumentation")

    /** Leave this only for Countables without any parameters - they can rely on [matches] having validated enough */
    override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? = null

    /** Helper for Countables with exactly one placeholder that is a UniqueParameterType */
    protected fun UniqueParameterType.getTranslatedErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
        // This calls UniqueParameterType's getErrorSeverity:
        val severity = getErrorSeverity(parameterText.getPlaceholderParameters().first(), ruleset)
        // Map PossibleFilteringUnique to RulesetSpecific otherwise those mistakes would be hidden later on in RulesetValidator
        return when {
            severity != UniqueType.UniqueParameterErrorSeverity.PossibleFilteringUnique -> severity
            else -> UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
    }

    fun getDeprecationAnnotation(): Deprecated? = declaringJavaClass.getField(name).getAnnotation(Deprecated::class.java)
    //endregion

    companion object {
        private fun getMatching(parameterText: String, ruleset: Ruleset?) = Countables.entries
            .filter {
                it.matches(parameterText, ruleset).isOK(strict = true)
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
            var result: UniqueType.UniqueParameterErrorSeverity? = null
            for (countable in Countables.entries) {
                val thisResult = when (countable.matches(parameterText, ruleset)) {
                    ICountable.MatchResult.No -> continue
                    ICountable.MatchResult.Yes ->
                        countable.getErrorSeverity(parameterText, ruleset)
                        // If any Countable is happy, we're happy: Should be the only path to return `null`, meaning perfectly OK
                            ?: return null
                    else -> UniqueType.UniqueParameterErrorSeverity.PossibleFilteringUnique
                }
                if (result == null || thisResult > result) result = thisResult
            }
            // return worst result - or if the loop found nothing, max severity
            return result ?: UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
        }

        /** Get deprecated [Countables] with their [Deprecated] object matching a [parameterText], for `UniqueValidator` */
        fun getDeprecatedCountablesMatching(parameterText: String): List<Pair<Countables, Deprecated>> =
            Countables.entries.filter {
                it.matches(parameterText, null).isOK(strict = false)
            }.mapNotNull { countable ->
                countable.getDeprecationAnnotation()?.let { countable to it }
            }
    }
}
