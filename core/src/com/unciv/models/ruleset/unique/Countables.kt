package com.unciv.models.ruleset.unique

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Countables.Stats
import com.unciv.models.ruleset.unique.Countables.TileResources
import com.unciv.models.ruleset.unique.expressions.Expressions
import com.unciv.models.ruleset.unique.expressions.Operator
import com.unciv.models.stats.GameResource
import com.unciv.models.stats.Stat
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly

/**
 *  Contains all knowledge about how to check and evaluate [countable Unique parameters][UniqueParameterType.Countable].
 *
 *  Expansion instructions:
 *  - TODO This Kdoc hasn't been updated following logic changes and needs validation
 *  - A new simple "variable" needs to implement only [text] and [eval].
 *  - Not supplying [text] means the "variable" **must** implement either [matches] overload. If it parses placeholders, then it **must** override [noPlaceholders] to `false`.
 *  - A new "variable" _using placeholder(s)_ needs to implement [matches] and [eval].
 *    - Implement [getErrorSeverity] in most cases, typically using [UniqueParameterType] to validate each placeholder content.
 *    - If it uses exactly one UniqueParameterType placeholder, [getErrorSeverity] can use the [UniqueParameterType.getTranslatedErrorSeverity] extension provided below.
 *    - Implement [getKnownValuesForAutocomplete] only when a meaningful, not too large set of suggestions is obvious.
 *  - A new countable that draws from an existing enum or set of RulesetObjects should work along the lines of the [Stats] or [TileResources] examples.
 *  - Run the unit tests! There's one checking implementation conventions.
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
            gameContext.gameInfo?.getYear(0)
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
        override val documentationHeader = "Stat name (${niceJoinList(Stat.names())})"
        override val documentationStrings = listOf("Gets the stat *reserve*, not the amount per turn (can be city stats or civilization stats, depending on where the unique is used)")
        override fun matches(parameterText: String) = Stat.isStat(parameterText)
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val relevantStat = Stat.safeValueOf(parameterText) ?: return null
            if (relevantStat == Stat.Happiness) return gameContext.civInfo?.getHappiness()
            val city = gameContext.city
            return city?.getStatReserve(relevantStat)
                ?: gameContext.civInfo?.getStatReserve(relevantStat)
        }

        override val example = "Science"
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = Stat.names()
    },

    StatOrResourcePerTurn("[stat/resource] Per Turn", shortDocumentation = "The amount of a stat or resource gained per turn") {
        override val documentationHeader = "Stat/Resource Per Turn"
        override val documentationStrings = listOf("Gets the amount of a stat or resource the civilization gains per turn")
        override val matchesWithRuleset = true
        override fun matches(parameterText: String, ruleset: Ruleset): Boolean {
            if (!parameterText.startsWith('[') || !parameterText.endsWith("] Per Turn")) return false
            val param = parameterText.getPlaceholderParameters().firstOrNull() ?: return false
            return Stat.isStat(param) || TileResources.matches(param, ruleset)
        }
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val param = parameterText.getPlaceholderParameters().firstOrNull() ?: return null
            val civ = gameContext.civInfo ?: return null
            val city = gameContext.city

            var resource: GameResource? = Stat.safeValueOf(param)
            if (resource is Stat) { // Type check instead of null check for smart cast
                if (city != null && resource.isCityWide) {
                    return city.cityStats.currentCityStats[resource].toInt()
                }
                return civ.stats.getStatMapForNextTurn().values.map { it[resource] }.sum().toInt()
            }

            resource = gameContext.gameInfo!!.ruleset.tileResources[param] ?: return null
            if (city != null) {
                return city.getResourcesGeneratedByCity().sumBy(resource)
            }
            return civ.getCivResourceSupply().sumBy(resource)
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
            val param = parameterText.getPlaceholderParameters().firstOrNull() ?: return UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
            if (Stat.isStat(param)) {
                return UniqueParameterType.StatName.getTranslatedErrorSeverity(parameterText, ruleset)
            }
            return UniqueParameterType.Resource.getTranslatedErrorSeverity(parameterText, ruleset)
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            UniqueParameterType.StatName.getKnownValuesForAutocomplete(ruleset).asSequence()
                .plus(UniqueParameterType.Resource.getKnownValuesForAutocomplete(ruleset))
                .map { text.fillPlaceholders(it) }.toSet()
        override val example: String = "[Culture] Per Turn"
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
            (ruleset.unitTypes.keys + ruleset.units.keys).map { "[$it] Units" }.toSet()
    },

    Carried("Carried [mapUnitFilter] units", shortDocumentation = "The number of units being carried by this unit") {
        override val documentationStrings = listOf("Only counts transported units matching the filter. For use with 'when number of' conditionals.")
        override val example: String = "Carried [Air] units"
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            if (gameContext.relevantUnit == null) return null
            val filter = parameterText.getPlaceholderParameters()[0]
            // Count transported units on the same tile matching the filter
            return gameContext.relevantUnit!!.getTile().airUnits.count { 
                it.isTransported && it.matchesFilter(filter) 
            }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.MapUnitFilter.getTranslatedErrorSeverity(parameterText, ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            (ruleset.unitTypes.keys + ruleset.units.keys).map { "Carried [$it] units" }.toSet()
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

    FilteredBuildingsByCivs("[buildingFilter] Buildings by [civFilter] Civilizations") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val (buildingFilter, civFilter) = parameterText.getPlaceholderParameters()
            val civilizations = gameContext.gameInfo?.civilizations ?: return null
            return civilizations.asSequence()
                .filter { it.isAlive() && it.matchesFilter(civFilter, gameContext) }
                .sumOf { civ ->
                    civ.cities.sumOf { city ->
                        city.cityConstructions.getBuiltBuildings().count { it.matchesFilter(buildingFilter, gameContext) }
                    }
                }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
            val params = parameterText.getPlaceholderParameters()
            return UniqueParameterType.BuildingFilter.getErrorSeverity(params[0], ruleset) ?:
                UniqueParameterType.CivFilter.getErrorSeverity(params[1], ruleset)
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    FilteredCitiesByCivs("[cityFilter] Cities of [civFilter] Civilizations") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val (cityFilter, civFilter) = parameterText.getPlaceholderParameters()
            val civilizations = gameContext.gameInfo?.civilizations ?: return null
            return civilizations.asSequence()
                .filter { it.isAlive() && it.matchesFilter(civFilter, gameContext) }
                .sumOf { civ ->
                    civ.cities.count { city -> city.matchesFilter(cityFilter) }
                }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
            val params = parameterText.getPlaceholderParameters()
            return UniqueParameterType.CityFilter.getErrorSeverity(params[0], ruleset)
                ?: UniqueParameterType.CivFilter.getErrorSeverity(params[1], ruleset)
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    FilteredPolicies("Adopted [policyFilter] Policies") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val policyManager = gameContext.civInfo?.policies ?: return null
            return policyManager.getAdoptedPoliciesMatching(filter, gameContext).count()
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.PolicyFilter.getTranslatedErrorSeverity(parameterText, ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            UniqueParameterType.PolicyFilter.getKnownValuesForAutocomplete(ruleset)
                .map { text.fillPlaceholders(it) }.toSet()
    },

    FilteredPoliciesByCivs("Adopted [policyFilter] Policies by [civFilter] Civilizations") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val (policyFilter, civFilter) = parameterText.getPlaceholderParameters()
            val civilizations = gameContext.gameInfo?.civilizations ?: return null
            return civilizations
                .filter { it.isAlive() && it.matchesFilter(civFilter, gameContext) }
                .flatMap { it.policies.getAdoptedPoliciesMatching(policyFilter, gameContext) }
                .count()
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
            val params = parameterText.getPlaceholderParameters()
            return UniqueParameterType.PolicyFilter.getErrorSeverity(params[0], ruleset) ?:
                UniqueParameterType.CivFilter.getErrorSeverity(params[1], ruleset)
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    FilteredTechnologies("Researched [techFilter] Technologies") {
        override val documentationStrings = listOf(
            "Counts researched matching technologies for the relevant Civilization",
            "Repeatable technologies, like Future Tech, are only counted once"
        )
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val technologies = gameContext.gameInfo?.ruleset?.technologies ?: return null
            val techManager = gameContext.civInfo?.tech ?: return null
            val filter = parameterText.getPlaceholderParameters()[0]
            return techManager.techsResearched.count {
                technologies[it]?.matchesFilter(filter, gameContext) ?: false
            }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.TechFilter.getTranslatedErrorSeverity(parameterText, ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            UniqueParameterType.TechFilter.getKnownValuesForAutocomplete(ruleset)
                .map { text.fillPlaceholders(it) }.toSet()
    },

    RemainingCivs("Remaining [civFilter] Civilizations") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val filter = parameterText.getPlaceholderParameters()[0]
            val civilizations = gameContext.gameInfo?.civilizations ?: return null
            return civilizations.count { it.isAlive() && it.matchesFilter(filter, gameContext) }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            UniqueParameterType.CivFilter.getTranslatedErrorSeverity(parameterText, ruleset)
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            UniqueParameterType.CivFilter.getKnownValuesForAutocomplete(ruleset)
                .map { text.fillPlaceholders(it) }.toSet()
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
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val resource = gameContext.gameInfo?.ruleset?.tileResources[parameterText] ?: return null
            val city = gameContext.city
            return city?.getAvailableResourceAmount(resource) ?: gameContext.civInfo?.getResourceAmount(resource)
        }

        override val example = "Iron"
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.tileResources.keys
    },

    TileResourcesByCivs("[resourceFilter] resource of [civFilter] Civilizations") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val (resouceFilter, civFilter) = parameterText.getPlaceholderParameters()
            val civilizations = gameContext.gameInfo?.civilizations ?: return null
            val ruleset = gameContext.gameInfo?.ruleset ?: return null
            val relevantCivs = civilizations.asSequence().filter {
                it.isAlive() && it.matchesFilter(civFilter, gameContext)
            }.toList()
            return ruleset.tileResources.values
                .filter { it.matchesFilter(resouceFilter, gameContext) }
                .sumOf { resource ->
                    relevantCivs.sumOf { civ ->
                        civ.getResourceAmount(resource.name)
                    }
                }
        }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
            val params = parameterText.getPlaceholderParameters()
            return UniqueParameterType.ResourceFilter.getErrorSeverity(params[0], ruleset) ?:
                UniqueParameterType.CivFilter.getErrorSeverity(params[1], ruleset)
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf<String>()
    },

    /** Please leave this one in, it is tested against in [com.unciv.uniques.CountableTests.testRulesetValidation] */
    @Deprecated("because it was never actually supported", ReplaceWith("Remaining [City-State] Civilizations"), DeprecationLevel.ERROR)
    CityStates("City-States", shortDocumentation = "counts all undefeated city-states") {
        override fun eval(parameterText: String, gameContext: GameContext): Int? {
            val civilizations = gameContext.gameInfo?.civilizations ?: return null
            return civilizations.count { it.isAlive() && it.isCityState }
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
            "Supported operations on 1 value are: " + Operator.UnaryOperators.entries.joinToString { "${it.symbol} (${it.description})" },
        )
    }
    ;

    val placeholderText = text.getPlaceholderText()
    open val matchesWithRuleset = false

    @VisibleForTesting
    open val noPlaceholders = !text.contains('[')

    // Leave these in place only for the really simple cases
    @Readonly open fun matches(parameterText: String) = if (noPlaceholders) parameterText == text
        else parameterText.equalsPlaceholderText(placeholderText)

    /** Needs to return the ENTIRE countable, not just parameters. */
    @Readonly open fun getKnownValuesForAutocomplete(ruleset: Ruleset) = setOf(text)

    /** This indicates whether a parameter *is of this countable type*, not *whether its parameters are correct*
     * E.g. "[fakeBuilding] Buildings" is obviously a countable of type "[buildingFilter] Buildings", therefore matches will return true.
     * But it has another problem, which is that the building filter is bad, so its getErrorSeverity will return "ruleset specific" */
    @Readonly open fun matches(parameterText: String, ruleset: Ruleset): Boolean = false
    @Readonly abstract fun eval(parameterText: String, gameContext: GameContext): Int?

    open val documentationHeader get() =
        "`$text`" + (if (shortDocumentation.isEmpty()) "" else " - $shortDocumentation")

    open val example: String
        get() {
            if (noPlaceholders) return text
            val placeholderParams = text.getPlaceholderParameters()
                .mapNotNull { UniqueParameterType.safeValueOf(it)?.docExample }
            return text.fillPlaceholders(*placeholderParams.toTypedArray())
        }

    /**
     * Joins a list with `,` while having an or for the last entry. Useful for the documentation headers.
     */
    @Readonly protected fun niceJoinList(list: Iterable<String>) = list.joinToString("`, `", "`", "`").run {
        val index = lastIndexOf("`, `")
        substring(0, index) + "` or `" + substring(index + 4)
    }

    /** Leave this only for Countables without any parameters - they can rely on [matches] having validated enough */
    open fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? = null

    data class DeprecationInfo(val message: String, val replaceWith: String, val level: DeprecationLevel)

    @Readonly
    fun getDeprecationInfo(): DeprecationInfo? {
        if (!com.unciv.platform.PlatformCapabilities.current.backgroundThreadPools) {
            if (name == "CityStates") {
                return DeprecationInfo(
                    "because it was never actually supported",
                    "Remaining [City-State] Civilizations",
                    DeprecationLevel.ERROR
                )
            }
            return null
        }
        val deprecation = getDeprecationAnnotation() ?: return null
        return DeprecationInfo(deprecation.message, deprecation.replaceWith.expression, deprecation.level)
    }

    @Readonly
    private fun getDeprecationAnnotation(): Deprecated? = try {
        declaringJavaClass.getField(name).getAnnotation(Deprecated::class.java)
    } catch (_: Exception) {
        null
    }

    @Readonly protected fun UniqueParameterType.getTranslatedErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
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
            val potentialResult = countable.eval(parameterText, gameContext)
            return potentialResult
        }

        @Readonly fun isKnownValue(parameterText: String, ruleset: Ruleset) = getMatching(parameterText, ruleset) != null

        // This will "leak memory" if game rulesets are changed over application lifetime, but it's a simple way to cache
        @Cache private val autocompleteCache = mutableMapOf<Ruleset, Set<String>>()
        @Readonly
        fun getKnownValuesForAutocomplete(ruleset: Ruleset) =
            autocompleteCache.getOrPut(ruleset) {
                Countables.entries.fold(setOf()) { acc, next ->
                    acc + next.getKnownValuesForAutocomplete(ruleset)
                }
            }

        @Readonly
        fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
            val countable = getMatching(parameterText, ruleset)
                ?: return UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
            return countable.getErrorSeverity(parameterText, ruleset)
        }
    }
}
