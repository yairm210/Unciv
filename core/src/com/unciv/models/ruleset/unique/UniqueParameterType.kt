package com.unciv.models.ruleset.unique

import com.unciv.Constants
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.TranslationFileWriter  // for  Kdoc only


/**
 * These manage validation of parameters in [Unique]s and 
 * placeholder guessing for untyped uniques in [TranslationFileWriter].
 *
 * [UniqueType] will build a map of valid [UniqueParameterType]s per parameter by matching
 * [parameterName] and can then validate the actual parameters in [Unique]s loaded from json
 * by calling [getErrorSeverity].
 *
 * @param parameterName placeholder name used by [UniqueType] for matching
 * @param displayName used by TranslationFileWriter for section header comments
 */
@Suppress("unused") // Some are used only via enumerating the enum matching on parameterName
enum class UniqueParameterType(
    var parameterName:String,
    val displayName: String = parameterName
) {
    Number("amount") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            return if (parameterText.toIntOrNull() == null) UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
            else null
        }
    },
    // todo potentially remove if OneTimeRevealSpecificMapTiles changes
    KeywordAll("'all'") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) =
            if (parameterText == "All") null else UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
    },
    CombatantFilter("combatantFilter") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText == "City") return null
            return MapUnitFilter.getErrorSeverity(parameterText, ruleset)
        }

    },
    MapUnitFilter("mapUnitFilter") {
        private val knownValues = setOf("Wounded", Constants.barbarians, "City-State", "Embarked", "Non-City")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if ('{' in parameterText) // "{filter} {filter}" for and logic
                return parameterText.removePrefix("{").removeSuffix("}").split("} {")
                    .mapNotNull { getErrorSeverity(it, ruleset) }
                    .maxByOrNull { it.ordinal }
            if (parameterText in knownValues) return null
            return BaseUnitFilter.getErrorSeverity(parameterText, ruleset)
        }
    },
    BaseUnitFilter("baseUnitFilter") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (ruleset.units.containsKey(parameterText)) return null
            return UnitTypeFilter.getErrorSeverity(parameterText, ruleset)
        }
    },
    UnitTypeFilter("unitType") {
        // As you can see there is a difference between these and what's in unitTypeStrings (for translation) -
        // the goal is to unify, but for now this is the "real" list
        private val knownValues = setOf("All", "Melee", "Ranged", "Civilian", "Military", "Land", "Water", "Air",
            "non-air", "Nuclear Weapon", "Great Person", "Religious")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ruleset.unitTypes.containsKey(parameterText)) return null
            return UniqueType.UniqueComplianceErrorSeverity.WarningOnly
        }

        override fun getTranslationWriterStringsForMatching(ruleset: Ruleset) =
            ruleset.unitTypes.keys + unitTypeStrings
    },
    GreatPerson("greatPerson") {
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            return if (parameterText in ruleset.units && ruleset.units[parameterText]!!.hasUnique("Great Person - []")) null
            else UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },
    Stats("stats") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (!com.unciv.models.stats.Stats.isStats(parameterText))
                return UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
            return null
        }
    },
    StatName("stat") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (Stat.values().any { it.name == parameterText }) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },
    PlunderableStatName("plunderableStat") {
        private val knownValues = setOf(Stat.Gold.name, Stat.Science.name, Stat.Culture.name, Stat.Faith.name)
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },
    CityFilter("cityFilter", "City filters") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText !in cityFilterStrings)
                return UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
            return null
        }

        override fun getTranslationWriterStringsForMatching(ruleset: Ruleset) = cityFilterStrings
        override fun getTranslationWriterStringsForOutput() = cityFilterStrings
    },
    BuildingName("buildingName") {
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            if (ruleset.buildings.containsKey(parameterText)) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },
    BuildingFilter("buildingFilter") {
        private val knownValues = setOf("All","Building","Buildings","Wonder","Wonders","National Wonder","World Wonder")
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (Stat.values().any { it.name == parameterText }) return null
            if (BuildingName.getErrorSeverity(parameterText, ruleset) == null) return null
            return UniqueType.UniqueComplianceErrorSeverity.WarningOnly
        } 
    },
    // Only used in values deprecated in 3.17.10
        ConstructionFilter("constructionFilter") {
            override fun getErrorSeverity(
                parameterText: String,
                ruleset: Ruleset
            ): UniqueType.UniqueComplianceErrorSeverity? {
                if (BuildingFilter.getErrorSeverity(parameterText, ruleset) == null) return null
                if (BaseUnitFilter.getErrorSeverity(parameterText, ruleset) == null) return null
                return UniqueType.UniqueComplianceErrorSeverity.WarningOnly
            }
        },
    //
    PopulationFilter("populationFilter") {
        private val knownValues = setOf("Population", "Specialists", "Unemployed", "Followers of the Majority Religion", "Followers of this Religion")
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }  
    },
    TerrainFilter("terrainFilter") {
        private val knownValues = setOf("All",
            Constants.coastal, "River", "Open terrain", "Rough terrain", "Water resource",
            "Foreign Land", "Foreign", "Friendly Land", "Friendly", "Enemy Land", "Enemy",
            "Featureless", Constants.freshWaterFilter, "Natural Wonder")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ruleset.terrains.containsKey(parameterText)) return null
            if (TerrainType.values().any { parameterText == it.name }) return null
            if (ruleset.tileResources.containsKey(parameterText)) return null
            if (ResourceType.values().any { parameterText == it.name + " resource" }) return null
            return UniqueType.UniqueComplianceErrorSeverity.WarningOnly
        }
    },
    TileFilter("tileFilter") {
        private val knownValues = setOf("unimproved", "All Road", "Great Improvement")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ruleset.tileImprovements.containsKey(parameterText)) return null
            return TerrainFilter.getErrorSeverity(parameterText, ruleset)
        }
    },
    /** Used by NaturalWonderGenerator, only tests base terrain or a feature */
    SimpleTerrain("simpleTerrain") {
        private val knownValues = setOf("Elevated", "Water", "Land")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ruleset.terrains.containsKey(parameterText)) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },
    /** Used by NaturalWonderGenerator, only tests base terrain */
    BaseTerrain("baseTerrain") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (ruleset.terrains[parameterText]?.type?.isBaseTerrain == true) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },
    TerrainName("terrainName") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (ruleset.terrains.containsKey(parameterText)) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },
    /** Used for region definitions, can be a terrain type with region unique, or "Hybrid" */
    RegionType("regionType") {
        private val knownValues = setOf("Hybrid")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ruleset.terrains[parameterText]?.hasUnique(UniqueType.RegionRequirePercentSingleType) == true ||
                    ruleset.terrains[parameterText]?.hasUnique(UniqueType.RegionRequirePercentTwoTypes) == true)
                return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },
    /** Used for start placements */
    TerrainQuality("terrainQuality") {
        private val knownValues = setOf("Undesirable", "Food", "Desirable", "Production")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },
    Promotion("promotion") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
                in ruleset.unitPromotions -> null
                else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
            }
    },
    Era("era") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
                in ruleset.eras -> null
                else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
            }
    },
    ImprovementName("improvementName"){
        override fun getErrorSeverity(parameterText: String,ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (ruleset.tileImprovements.containsKey(parameterText)) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },
    /** should mirror TileImprovement.matchesFilter exactly */
    ImprovementFilter("improvementFilter") {
        private val knownValues = setOf("All", "All Road", "Great Improvement", "Great")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ruleset.tileImprovements.containsKey(parameterText)) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },
    Resource("resource") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
                in ruleset.tileResources -> null
                else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
            }
    },
    BeliefTypeName("beliefType") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
            in BeliefType.values().map { it.name } -> null
            else -> UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },
    Belief("belief") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
            in ruleset.beliefs -> null
            else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },
    FoundingOrEnhancing("foundingOrEnhancing") {
        private val knownValues = setOf("founding", "enhancing")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
            in knownValues -> null
            else -> UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },
    Technology("tech") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
            in ruleset.technologies -> null
            else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },
    Specialist("specialist") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
            in ruleset.specialists -> null
            else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },
    Policy("policy") {
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            return when (parameterText) {
                in ruleset.policies -> null
                else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
            }
        }
    },
    VictoryT("victoryType") {
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            return if (parameterText in VictoryType.values().map { it.name }) null 
            else UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },
    CostOrStrength("costOrStrength") {
        private val knownValues = setOf("Cost", "Strength")
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            return if (parameterText in knownValues) null
            else UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },
    Action("action") {
        private val knownValues = setOf(Constants.spreadReligionAbilityCount, Constants.removeHeresyAbilityCount)
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            return if (parameterText in knownValues) null
            else UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },
    /** Behaves like [Unknown], but states explicitly the parameter is OK and its contents are ignored */
    Comment("comment", "Unique Specials") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = null

        override fun getTranslationWriterStringsForOutput() = scanExistingValues(this)
    },
    Unknown("param") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = null
    };

    /** Validate a [Unique] parameter */
    abstract fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueComplianceErrorSeverity?

    /** Get a list [TranslationFileWriter] can use to guess types of actual json parameters
     *  in order to get placeholders for the translatable string */
    open fun getTranslationWriterStringsForMatching(ruleset: Ruleset): Set<String> = setOf()

    /** Get a list of possible values [TranslationFileWriter] should include as translatable string 
     *  that are not recognized from other json sources */
    open fun getTranslationWriterStringsForOutput(): Set<String> = setOf()

    companion object {
        private val unitTypeStrings = hashSetOf(
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

        private val cityFilterStrings = setOf( // taken straight from the translation!
            "in this city",
            "in all cities",
            "in all coastal cities",
            "in capital",
            "in all non-occupied cities",
            "in all cities with a world wonder",
            "in all cities connected to capital",
            "in all cities with a garrison",
            "in all cities in which the majority religion is a major religion",
            "in all cities in which the majority religion is an enhanced religion",
            "in non-enemy foreign cities",
            "in foreign cities",
            "in annexed cities",
            "in holy cities",
            "in City-State cities",
            "in cities following this religion",
        )

        private fun scanExistingValues(type: UniqueParameterType): Set<String> {
            return BaseRuleset.values()
                .mapNotNull { RulesetCache[it.fullName] }
                .map { scanExistingValues(type, it) }
                .fold(setOf<String>()) { a, b -> a + b }
        }
        private fun scanExistingValues(type: UniqueParameterType, ruleset: Ruleset): Set<String> {
            val result = mutableSetOf<String>()
            for (obj in ruleset.allIHasUniques()) {
                for (unique in obj.uniqueObjects) {
                    val parameterMap = unique.type?.parameterTypeMap ?: continue
                    for ((index, param) in unique.params.withIndex()) {
                        if (type !in parameterMap[index]) continue
                        result += param
                    }
                }
            }
            return result
        }

        fun safeValueOf(param: String) = values().firstOrNull { it.parameterName == param }
            ?: Unknown.apply { this.parameterName = param }  //TODO Danger: There is only one instance of Unknown!
    }
}


class UniqueComplianceError(
    val parameterName: String,
    val acceptableParameterTypes: List<UniqueParameterType>,
    val errorSeverity: UniqueType.UniqueComplianceErrorSeverity
)
