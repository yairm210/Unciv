package com.unciv.models.ruleset.unique

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.expressions.Expressions
import com.unciv.models.ruleset.unique.expressions.Operator
import com.unciv.models.stats.Stat
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import yairm210.purity.annotations.Readonly
import org.jetbrains.annotations.VisibleForTesting

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
) {
    Integer {
        override val documentationHeader = "Integer constant - any positive or negative integer number"
        override fun matches(parameterText: String) = parameterText.toIntOrNull() != null
        override fun eval(parameterText: String, gameContext: GameContext) = parameterText.toIntOrNull()
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String>  = setOf()
        override val example: String = "123"
    },

    Turns("turns", shortDocumentation = "Number of turns played") {
        override val documentationStrings = listOf("Always starts at zero irrespective of game speed or start era")
        override fun eval(parameterText: String, gameContext: GameContext) =
            gameContext.gameInfo?.turns
    },
    Year("year", shortDocumentation = "The current year") {
        override val documentationStrings = listOf("Depends on game speed or start era, negative for years BC")
        override fun eval(parameterText: String, gameContext: GameContext) =
            gameContext.gameInfo?.run { getYear(turns) }
    },
    Cities("Cities", shortDocumentation = "The number of cities the relevant Civilization owns") {
        override fun eval(parameterText: String, gameContext: GameContext) =
            gameContext.civInfo?.cities?.size
    },
    Units("Units", shortDocumentation = "The number of units the relevant Civilization owns") {
        override fun eval(parameterText: String, gameContext: GameContext) =
            gameContext.civInfo?.units?.getCivUnitsSize()
    },

    Stats {
        override val documentationHeader = "Stat name (${Stat.names().niceJoin()})"
        override val documentationStrings = listOf("Gets the stat *reserve*, not the amount per turn (can be city stats or civilization stats, depending on where the unique is used)")
        override fun matches(parameterText: String) = Stat.isStat(parameterText)
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val relevantStat = Stat.safeValueOf(parameterText) ?: return null
            // This one isn't covered by City.getStatReserve or Civilization.getStatReserve but should be available here
            if (relevantStat == Stat.Happiness)
                return gameContext.civInfo?.getHappiness()
            return gameContext.getStatAmount(relevantStat)
        }

        override val example = "Science"
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = Stat.names()
        private fun Iterable<String>.niceJoin() = joinToString("`, `", "`", "`").run {
            val index = lastIndexOf("`, `")
            substring(0, index) + "` or `" + substring(index + 4)
        }
    },

    PolicyBranches("Completed Policy branches") {
        override fun eval(parameterText: String, gameContext: GameContext) =
            gameContext.civInfo?.getCompletedPolicyBranchesCount()
    },

    FilteredCities("[cityFilter] Cities") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val cities = gameContext.civInfo?.cities ?: return null
            return cities.count { it.matchesFilter(filter) }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.CityFilter.getTranslatedErrorSeverity(parameterText, ruleset)
    },

    FilteredUnits("[mapUnitFilter] Units") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val unitManager = gameContext.civInfo?.units ?: return null
            return unitManager.getCivUnits().count { it.matchesFilter(filter) }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.MapUnitFilter.getTranslatedErrorSeverity(parameterText, ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            (ruleset.unitTypes.keys + ruleset.units.keys).mapTo(mutableSetOf()) { "[$it] Units" }
    },

    FilteredBuildings("[buildingFilter] Buildings") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val cities = gameContext.civInfo?.cities ?: return null
            return cities.sumOf { city ->
                city.cityConstructions.getBuiltBuildings().count { it.matchesFilter(filter) }
            }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.BuildingFilter.getTranslatedErrorSeverity(parameterText, ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    FilteredPolicies("Adopted [policyFilter] Policies") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val policyManager = gameContext.civInfo?.policies ?: return null
            return policyManager.getAdoptedPoliciesMatching(filter, gameContext).size
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.PolicyFilter.getTranslatedErrorSeverity(parameterText, ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            UniqueParameterType.PolicyFilter.getKnownValuesForAutocomplete(ruleset)
                .map { text.fillPlaceholders(it) }.toSet()
    },

    RemainingCivs("Remaining [civFilter] Civilizations") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val civilizations = gameContext.gameInfo?.civilizations ?: return null
            return civilizations.count { it.isAlive() && it.matchesFilter(filter) }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.CivFilter.getTranslatedErrorSeverity(parameterText, ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },
    OwnedTiles("Owned [tileFilter] Tiles") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val cities = gameContext.civInfo?.cities ?: return null
            return cities.sumOf { city -> city.getTiles().count { it.matchesFilter(filter) } }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.TileFilter.getTranslatedErrorSeverity(parameterText, ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },
    TileFilterTiles("[tileFilter] Tiles") {
    override fun eval(parameterText: String, gameContext: GameContext): Int? {
        val filter = parameterText.getPlaceholderParameters()[0]
        val tileMap = gameContext.gameInfo?.tileMap ?: return null
        return tileMap.tileList.count { it.matchesFilter(filter, gameContext.civInfo) }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.TileFilter.getTranslatedErrorSeverity(parameterText, ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
        override val example: String = "[Desert] Tiles"
    },
    TileResources {
        override val documentationHeader = "Resource name - From [TileResources.json](3-Map-related-JSON-files.md#tileresourcesjson)"
        override val documentationStrings = listOf(
            "Can be city stats or civilization stats, depending on where the unique is used",
            "For example: If a unique is placed on a building, then the retrieved resources will be of the city. If placed on a policy, they will be of the civilization.",
            "This can make a difference for e.g. local resources, which are counted per city."
        )
        override val matchesWithRuleset = true
        override fun matches(parameterText: String, ruleset: Ruleset) = parameterText in ruleset.tileResources
        override fun eval(parameterText: String, gameContext: GameContext) =
            gameContext.getResourceAmount(parameterText)

        override val example = "Iron"
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.tileResources.keys
    },

    /** Please leave this one in, it is tested against in [com.unciv.uniques.CountableTests.testRulesetValidation] */
    @Deprecated("because it was never actually supported", ReplaceWith("Remaining [City-State] Civilizations"), DeprecationLevel.ERROR)
    CityStates("City-States", shortDocumentation = "counts all undefeated city-states") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val civilizations = gameContext.gameInfo?.civilizations ?: return null
            return civilizations.count { it.isAlive() && it.isCityState }
        }
    },

    DifficultyNumber("Difficulty number", shortDocumentation = "Number representing the difficulty the game is being played on") {
        override val documentationStrings = listOf("Zero-based index of the Difficulty in Difficulties.json.")
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val difficulty = gameContext.gameInfo?.getDifficulty() ?: return null
            val difficulties = gameContext.gameInfo?.ruleset?.difficulties?.keys?.toList() ?: return null
            return difficulties.indexOf(difficulty.name)
        }
    },

    EraNumber("Era number", shortDocumentation = "Number of the era the current player is in") {
        override val documentationStrings = listOf("Zero-based index of the Era in Eras.json.")
        override fun eval(parameterText: String, gameContext: GameContext) =
            gameContext.civInfo?.getEraNumber()
    },

    GameSpeedModifier("Speed modifier for [stat]", shortDocumentation = "A game speed modifier for a specific Stat, as percentage") {
        override val documentationStrings = listOf(
            "Chooses an appropriate field from the Speeds.json entry the player has chosen.",
            "It is returned multiplied by 100.",
            "Food and Happiness return the generic `modifier` field.",
            "Other fields like `goldGiftModifier` or `barbarianModifier` are not accessible with this Countable."
        )
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val stat = Stat.safeValueOf(parameterText.getPlaceholderParameters()[0]) ?: return null
            val speed = gameContext.gameInfo?.speed ?: return null
            val modifier = when(stat) {
                Stat.Gold -> speed.goldCostModifier
                Stat.Production -> speed.productionCostModifier
                Stat.Food -> speed.modifier
                Stat.Science -> speed.scienceCostModifier
                Stat.Culture -> speed.cultureCostModifier
                Stat.Happiness -> speed.modifier
                Stat.Faith -> speed.faithCostModifier
            }
            return modifier.times(100).toInt()
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            if (Stat.isStat(parameterText.getPlaceholderParameters()[0])) null else UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = Stat.entries.map { placeholderText.fillPlaceholders(it.name) }.toSet()
    },
    Expression {
        override val noPlaceholders = false

        private val engine = Expressions()
        override val matchesWithRuleset: Boolean = true

        override fun matches(parameterText: String, ruleset: Ruleset) =
            engine.matches(parameterText, ruleset)
        override fun eval(parameterText: String, gameContext: GameContext): Int? =
            engine.eval(parameterText, gameContext)
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            engine.getErrorSeverity(parameterText, ruleset)

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = emptySet<String>()
        override val example: String = "[Iron] + 2"

        override val documentationHeader = "Evaluate expressions!"
        override val documentationStrings = listOf(
            "Expressions support arbitrary math operations, and can include other countables, when surrounded by square brackets.",
            "For example, since `Cities` is a countable, and `[Melee] units` is a countable, " +
                    "you can have something like: `([[Melee] units] + 1) / [Cities]` (the whitespace is optional but helps readability)",
            "Since on translation, the brackets are removed, the expression will be displayed as `(Melee units + 1) / Cities`",
            "Supported operations between 2 values are: "+ Operator.BinaryOperators.entries.joinToString { it.symbol },
            "Supported operations on 1 value are: " + Operator.UnaryOperators.entries.joinToString { it.symbol+" (${it.description})" },
        )
    }
    ;

    val placeholderText = text.getPlaceholderText()
    open val matchesWithRuleset = false

    @VisibleForTesting
    open val noPlaceholders = !text.contains('[')

    // Leave these in place only for the really simple cases
    @Readonly
    open fun matches(parameterText: String) = if (noPlaceholders) parameterText == text
        else parameterText.equalsPlaceholderText(placeholderText)
    
    /** Needs to return the ENTIRE countable, not just parameters. */
    open fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf(text)
    
    /** This indicates whether a parameter *is of this countable type*, not *whether its parameters are correct*
     * E.g. "[fakeBuilding] Buildings" is obviously a countable of type "[buildingFilter] Buildings", therefore matches will return true.
     * But it has another problem, which is that the building filter is bad, so its getErrorSeverity will return "ruleset specific" */
    @Readonly
    open fun matches(parameterText: String, ruleset: Ruleset): Boolean = false
    @Readonly
    abstract fun eval(parameterText: String, gameContext: GameContext): Int?

    open val documentationHeader get() =
        "`$text`" + (if (shortDocumentation.isEmpty()) "" else " - $shortDocumentation")
    
    open val example: String
        get() {
            if (noPlaceholders) return text
            val placeholderParams = text.getPlaceholderParameters()
                .mapNotNull { UniqueParameterType.safeValueOf(it)?.docExample }
            return text.fillPlaceholders(*placeholderParams.toTypedArray())
        }

    /** Leave this only for Countables without any parameters - they can rely on [matches] having validated enough */
    open fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? = null

    fun getDeprecationAnnotation(): Deprecated? = declaringJavaClass.getField(name).getAnnotation(Deprecated::class.java)

    protected fun UniqueParameterType.getTranslatedErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
        getErrorSeverity(parameterText.getPlaceholderParameters().first(), ruleset)

    companion object {
        @Readonly
        fun getMatching(parameterText: String, ruleset: Ruleset?) = Countables.entries
            .firstOrNull {
                if (it.matchesWithRuleset)
                    ruleset != null && it.matches(parameterText, ruleset)
                else it.matches(parameterText)
            }

        @Readonly
        fun getCountableAmount(parameterText: String, gameContext: GameContext): Int? {
            val ruleset = gameContext.gameInfo?.ruleset
            val countable = getMatching(parameterText, ruleset) ?: return null
            val potentialResult = countable.eval(parameterText, gameContext) ?: return null
            return potentialResult
        }

        fun isKnownValue(parameterText: String, ruleset: Ruleset) = getMatching(parameterText, ruleset) != null

        // This will "leak memory" if game rulesets are changed over application lifetime, but it's a simple way to cache
        private val autocompleteCache = mutableMapOf<Ruleset, Set<String>>()
        fun getKnownValuesForAutocomplete(ruleset: Ruleset) =
            autocompleteCache.getOrPut(ruleset) {
                Countables.entries.fold(setOf()) { acc, next ->
                    acc + next.getKnownValuesForAutocomplete(ruleset)
                }
            }

        fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
            val countable = getMatching(parameterText, ruleset)
                ?: return UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
            return countable.getErrorSeverity(parameterText, ruleset)
        }
    }
}
