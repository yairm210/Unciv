package com.unciv.models.ruleset.unique

import com.unciv.Constants
import com.unciv.logic.MultiFilter
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueParameterType.Companion.guessTypeForTranslationWriter
import com.unciv.models.ruleset.validation.Suppression
import com.unciv.models.stats.Stat
import com.unciv.models.translations.TranslationFileWriter

// 'region' names beginning with an underscore are used here for a prettier "Structure window" - they go in front ot the rest.

/**
 * These manage validation of parameters in [Unique]s and
 * placeholder guessing for untyped uniques in [TranslationFileWriter].
 *
 * [UniqueType] will build a map of valid [UniqueParameterType]s per parameter by matching
 * [parameterName] and can then validate the actual parameters in [Unique]s loaded from json
 * by calling [getErrorSeverity].
 *
 * Legacy placeholder guessing for untyped uniques in [TranslationFileWriter] is done by
 * [guessTypeForTranslationWriter] utilizing the [isTranslationWriterGuess] overloads.
 *
 * @param parameterName placeholder name used by [UniqueType] for matching.
 * @param docExample used by UniqueDocsWriter to fill parameters with plausible values for the examples.
 * @param docDescription used by UniqueDocsWriter to generate the Abbreviations list at the end for types that can be explained in one long line. Should be omitted when the Wiki contains a paragraph for this type.
 * @param displayName used by [TranslationFileWriter] for section header comments - needed _only_ if [getTranslationWriterStringsForOutput] returns a non-empty list.
 */
//region _Fields
@Suppress("unused") // Some are used only via enumerating the enum matching on parameterName
enum class UniqueParameterType(
    var parameterName: String,
    val docExample: String,
    val docDescription: String? = null,
    val displayName: String = parameterName
) {
    //endregion



    Number("amount", "3", "This indicates a whole number, possibly with a + or - sign, such as `2`, `+13`, or `-3`") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? {
            return if (parameterText.toIntOrNull() == null) UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
            else null
        }
    },

    PositiveNumber("positiveAmount", "3", "This indicates a positive whole number, larger than zero, a '+' sign is optional") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? {
            val amount = parameterText.toIntOrNull()
                ?: return UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
            if (amount <= 0) return UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
            return null
        }
    },

    Fraction("fraction", docExample = "0.5", "Indicates a fractional number, which can be negative") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
            return if (parameterText.toFloatOrNull () == null) UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
            else null
        }
    },

    RelativeNumber("relativeAmount", "+20", "This indicates a number, usually with a + or - sign, such as `+25` (this kind of parameter is often followed by '%' which is nevertheless not part of the value)") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? {
            return if (parameterText.toIntOrNull() == null) UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
            else null
        }
    },

    // todo potentially remove if OneTimeRevealSpecificMapTiles changes
    KeywordAll("'all'", "All") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) =
            if (parameterText in Constants.all) null else UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
    },

    /** Implemented by [ICombatant.matchesCategory][com.unciv.logic.battle.ICombatant.matchesFilter] */
    CombatantFilter("combatantFilter", "City", "This indicates a combatant, which can either be a unit or a city (when bombarding). Must either be `City` or a `mapUnitFilter`") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean {
            if (parameterText == "City") return true
            if (MapUnitFilter.isKnownValue(parameterText, ruleset)) return true
            if (CityFilter.isKnownValue(parameterText, ruleset)) return true
            return false
        }
    },

    /** Implemented by [MapUnit.matchesFilter][com.unciv.logic.map.mapunit.MapUnit.matchesFilter] */
    MapUnitFilter("mapUnitFilter", Constants.wounded, null, "Map Unit Filters") {
        private val knownValues = setOf(Constants.wounded, Constants.barbarians, "Barbarian",
            "City-State", Constants.embarked, "Non-City")

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean {
            if (parameterText in knownValues) return true
            if (ruleset.unitPromotions.values.any { it.hasUnique(parameterText) }) return true
            if (CivFilter.isKnownValue(parameterText, ruleset)) return true
            if (BaseUnitFilter.isKnownValue(parameterText, ruleset)) return true
            return false
        }

        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Implemented by [BaseUnit.matchesFilter][com.unciv.models.ruleset.unit.BaseUnit.matchesFilter] */
    BaseUnitFilter("baseUnitFilter", "Melee") {
        private val knownValues = setOf(
            "Melee", "Ranged", "Civilian", "Military", "non-air",
            "Nuclear Weapon", "Great Person", "Religious",
            "relevant", // used for UniqueType.UnitStartingPromotions
        ) + Constants.all
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean {
            if (parameterText in knownValues) return true
            if (UnitName.getErrorSeverity(parameterText, ruleset) == null) return true
            if (ruleset.units.values.any { it.uniques.contains(parameterText) }) return true
            if (UnitTypeFilter.isKnownValue(parameterText, ruleset)) return true
            return false
        }

        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Implemented by [UnitType.matchesFilter][com.unciv.models.ruleset.unit.UnitType.matchesFilter] */
    UnitTypeFilter("unitType", "Water", null, "Unit Type Filters") {
        private val knownValues = setOf(
            "Land", "Water", "Air",
        )
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean {
            if (parameterText in knownValues) return true
            if (ruleset.unitTypes.containsKey(parameterText)) return true
            if (ruleset.eras.containsKey(parameterText)) return true
            if (ruleset.unitTypes.values.any { it.uniques.contains(parameterText) }) return true
            return false
        }

        override fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset) =
            parameterText in ruleset.unitTypes.keys || parameterText in getTranslationWriterStringsForOutput()

        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Used by [BaseUnitFilter] and e.g. [UniqueType.OneTimeFreeUnit] */
    UnitName("unit", "Musketman") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? {
            if (ruleset.units.containsKey(parameterText)) return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetSpecific  // OneTimeFreeUnitRuins crashes with a bad parameter
        }
    },

    /** Used by [UniqueType.GreatPersonEarnedFaster] */
    GreatPerson("greatPerson", "Great General") {
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueParameterErrorSeverity? {
            if (ruleset.units[parameterText]?.hasUnique(UniqueType.GreatPerson) == true) return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
    },

    /** Implemented in [Unique.stats][com.unciv.models.ruleset.unique.Unique.stats] */
    Stats("stats", "+1 Gold, +2 Production", "For example: `+2 Production, +3 Food`. Note that the stat names need to be capitalized!") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? {
            if (com.unciv.models.stats.Stats.isStats(parameterText)) return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
        }
    },

    /** Many UniqueTypes like [UniqueType.StatPercentBonus] */
    StatName("stat", "Culture", "This is one of the 7 major stats in the game - `Gold`, `Science`, `Production`, `Food`, `Happiness`, `Culture` and `Faith`. Note that the stat names need to be capitalized!") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? {
            if (Stat.isStat(parameterText)) return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
        }
    },

    /** [UniqueType.DamageUnitsPlunder] and others near that one */
    CivWideStatName("civWideStat", "Gold", "All the following stats have civ-wide fields: `Gold`, `Science`, `Culture`, `Faith`") {
        private val knownValues = Stat.statsWithCivWideField.map { it.name }.toSet()
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueParameterErrorSeverity? {
            if (parameterText in knownValues) return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
        }
    },


    /** Implemented by [Civ.matchesFilter][com.unciv.logic.civilization.Civilization.matchesFilter] */
    CivFilter("civFilter", Constants.cityStates) {
        private val knownValues = setOf("AI player", "Human player")

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean {
            if (parameterText in knownValues) return true
            if (NationFilter.isKnownValue(parameterText, ruleset)) return true
            return false
        }
    },

    /** Implemented by [Nation.matchesFilter][com.unciv.models.ruleset.nation.Nation.matchesFilter] */
    NationFilter("nationFilter", Constants.cityStates) {
        private val knownValues = setOf(Constants.cityStates, "Major") + Constants.all

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean {
            if (parameterText in knownValues) return true
            if (ruleset.nations.containsKey(parameterText)) return true
            if (ruleset.nations.values.any { it.hasUnique(parameterText) }) return true
            return false
        }
    },

    /** Implemented by [City.matchesFilter][com.unciv.logic.city.City.matchesFilter] */
    CityFilter("cityFilter", "in all cities", null, "City filters") {
        private val knownValues = setOf(
            "in this city",
            "in all cities",
            "in your cities", "Your",
            "in all coastal cities", "Coastal",
            "in capital", "Capital",
            "in all non-occupied cities", "Non-occupied",
            "in all cities with a world wonder",
            "in all cities connected to capital",
            "in all cities with a garrison", "Garrisoned",
            "in all cities in which the majority religion is a major religion",
            "in all cities in which the majority religion is an enhanced religion",
            "in non-enemy foreign cities",
            "in enemy cities", "Enemy",
            "in foreign cities", "Foreign",
            "in annexed cities", "Annexed",
            "in puppeted cities", "Puppeted",
            "in resisting cities", "Resisting",
            "in cities being razed", "Razing",
            "in holy cities", "Holy",
            "in City-State cities",
            "in cities following this religion",
            "in cities following our religion",
        ) + Constants.all

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean {
            if (parameterText in knownValues) return true
            return false
        }

        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Used by [BuildingFilter] and e.g. [UniqueType.ConditionalCityWithBuilding] */
    BuildingName("buildingName", "Library", "The name of any building") {
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueParameterErrorSeverity? {
            if (parameterText in ruleset.buildings) return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
    },

    /** Implemented by [Building.matchesFilter][com.unciv.models.ruleset.Building.matchesFilter] */
    BuildingFilter("buildingFilter", "Culture") {
        private val knownValues = mutableSetOf("Building", "Buildings", "Wonder", "Wonders", "National Wonder", "World Wonder")
            .apply { addAll(Stat.names()); addAll(Constants.all) }

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean {
            if (parameterText in knownValues) return true
            if (BuildingName.getErrorSeverity(parameterText, ruleset) == null) return true
            if (ruleset.buildings.values.any { it.hasUnique(parameterText) }) return true
            return false
        }

        override fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset) =
            parameterText !in Constants.all && getErrorSeverity(parameterText, ruleset) == null
    },

    /** Implemented by [PopulationManager.getPopulationFilterAmount][com.unciv.logic.city.managers.CityPopulationManager.getPopulationFilterAmount] */
    PopulationFilter("populationFilter", "Followers of this Religion", null, "Population Filters") {
        private val knownValues = setOf("Population", "Specialists", "Unemployed", "Followers of the Majority Religion", "Followers of this Religion")
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueParameterErrorSeverity? {
            if (parameterText in knownValues) return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Implemented by [Tile.matchesTerrainFilter][com.unciv.logic.map.tile.Tile.matchesTerrainFilter] */
    TerrainFilter("terrainFilter", Constants.freshWaterFilter, null, "Terrain Filters") {
        private val knownValues = setOf(
            "Terrain",
            Constants.coastal, Constants.river, "Open terrain", "Rough terrain", "Water resource",
            "resource", "Foreign Land", "Foreign", "Friendly Land", "Friendly", "Enemy Land", "Enemy", "your",
            "Featureless", Constants.freshWaterFilter, "non-fresh water", "Natural Wonder",
            "Impassable", "Land", "Water"
        ) + ResourceType.values().map { it.name + " resource" } + Constants.all

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean {
            return when (parameterText) {
                in knownValues -> true
                in ruleset.terrains -> true
                in ruleset.tileResources -> true
                in ruleset.terrains.values.asSequence().flatMap { it.uniques } -> true
                in ruleset.tileResources.values.asSequence().flatMap { it.uniques } -> true
                else -> false
            }
        }

        override fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset) =
            parameterText in ruleset.terrains || parameterText !in Constants.all && parameterText in knownValues

        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Implemented by [Tile.matchesFilter][com.unciv.logic.map.tile.Tile.matchesFilter] */
    TileFilter("tileFilter", "Farm", "Anything that can be used either in an improvementFilter or in a terrainFilter can be used here, plus 'unimproved'", "Tile Filters") {
        private val knownValues = setOf("unimproved", "improved", "All Road", "Great Improvement")

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean {
            if (parameterText in knownValues) return true
            if (ImprovementFilter.isKnownValue(parameterText, ruleset)) return true
            if (TerrainFilter.isKnownValue(parameterText, ruleset)) return true
            return false
        }

        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Used by [NaturalWonderGenerator][com.unciv.logic.map.mapgenerator.NaturalWonderGenerator], only tests base terrain or a feature */
    SimpleTerrain("simpleTerrain", "Elevated") {
        private val knownValues = setOf("Elevated", "Water", "Land")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ruleset.terrains.containsKey(parameterText)) return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
    },

    /** Used by [NaturalWonderGenerator.trySpawnOnSuitableLocation][com.unciv.logic.map.mapgenerator.NaturalWonderGenerator.trySpawnOnSuitableLocation], only tests base terrain */
    BaseTerrain("baseTerrain", Constants.grassland, "The name of any terrain that is a base terrain according to the json file") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? {
            if (ruleset.terrains[parameterText]?.type?.isBaseTerrain == true) return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
    },

    /** Used by: [UniqueType.LandUnitsCrossTerrainAfterUnitGained] (CivilizationInfo.addUnit),
     *  [UniqueType.ChangesTerrain] (MapGenerator.convertTerrains) */
    TerrainName("terrainName", Constants.forest) {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? {
            if (ruleset.terrains.containsKey(parameterText)) return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
    },

    /** Used for region definitions, can be a terrain type with region unique, or "Hybrid"
     *
     *  See also: [UniqueType.ConditionalInRegionOfType], [UniqueType.ConditionalInRegionExceptOfType], [MapRegions][com.unciv.logic.map.mapgenerator.mapregions.MapRegions] */
    RegionType("regionType", "Hybrid", null, "Region Types") {
        private val knownValues = setOf("Hybrid")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ruleset.terrains[parameterText]?.hasUnique(UniqueType.RegionRequirePercentSingleType) == true ||
                    ruleset.terrains[parameterText]?.hasUnique(UniqueType.RegionRequirePercentTwoTypes) == true)
                return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Used for start placements: [UniqueType.HasQuality], MapRegions.MapGenTileData.evaluate */
    TerrainQuality("terrainQuality", "Undesirable", null, "Terrain Quality") {
        private val knownValues = setOf("Undesirable", "Food", "Desirable", "Production")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? {
            if (parameterText in knownValues) return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
        }
        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** [UniqueType.UnitStartingPromotions], [UniqueType.TerrainGrantsPromotion], [UniqueType.ConditionalUnitWithPromotion] and others */
    Promotion("promotion", "Shock I", "The name of any promotion") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = when (parameterText) {
                in ruleset.unitPromotions -> null
                else -> UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
            }
    },

    /** [UniqueType.OneTimeFreeTechRuins], [UniqueType.ConditionalDuringEra] and similar */
    Era("era", "Ancient era", "The name of any era") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = when (parameterText) {
                in ruleset.eras -> null
                else -> UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
            }
    },

    Speed("speed", "Quick", "The name of any speed") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = when (parameterText) {
                in ruleset.speeds -> null
                else -> UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
            }
    },

    /** For [UniqueType.CreatesOneImprovement] */
    ImprovementName("improvementName", "Trading Post", "The name of any improvement") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? {
            if (parameterText == Constants.cancelImprovementOrder)
                return UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
            if (ruleset.tileImprovements.containsKey(parameterText)) return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
    },

    /** Implemented by [TileImprovement.matchesFilter][com.unciv.models.ruleset.tile.TileImprovement.matchesFilter] */
    ImprovementFilter("improvementFilter", "All Road", null, "Improvement Filters") {
        private val knownValues = setOf("Improvement", "All Road", "Great Improvement", "Great") + Constants.all

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean {
            if (parameterText in knownValues) return true
            if (ImprovementName.getErrorSeverity(parameterText, ruleset) == null) return true
            if (ruleset.tileImprovements.values.any { it.hasUnique(parameterText) }) return true
            return false
        }

        override fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset) =
            parameterText !in Constants.all && getErrorSeverity(parameterText, ruleset) == null

        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Used by [UniqueType.ConsumesResources] and others, implementation not centralized */
    Resource("resource", "Iron", "The name of any resource") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = when (parameterText) {
                in ruleset.tileResources -> null
                else -> UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
            }
    },

    StockpiledResource("stockpiledResource", "StockpiledResource", "The name of any stockpiled") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? = if (parameterText in ruleset.tileResources && ruleset.tileResources[parameterText]!!.isStockpiled()) null
                else UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
    },


    /** Used by [UniqueType.FreeExtraBeliefs], see ReligionManager.getBeliefsToChooseAt* functions */
    BeliefTypeName("beliefType", "Follower", "'Pantheon', 'Follower', 'Founder' or 'Enhancer'") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? = when (parameterText) {
            in BeliefType.values().map { it.name } -> null
            else -> UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
        }
    },

    /** unused at the moment with vanilla rulesets */
    Belief("belief", "God of War", "The name of any belief") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? = when (parameterText) {
            in ruleset.beliefs -> null
            else -> UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
    },

    /** Used by [UniqueType.FreeExtraBeliefs] and its any variant, see ReligionManager.getBeliefsToChooseAt* functions */
    FoundingOrEnhancing("foundingOrEnhancing", "founding", "`founding` or `enhancing`", "Prophet Action Filters") {
        // Used in FreeExtraBeliefs, FreeExtraAnyBeliefs
        private val knownValues = setOf("founding", "enhancing")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = when (parameterText) {
            in knownValues -> null
            else -> UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
        }
        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** [UniqueType.ConditionalTech] and others, no central implementation */
    Event("event", "Inspiration", "The name of any event") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = when (parameterText) {
            in ruleset.events -> null
            else -> UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
    },


    /** [UniqueType.ConditionalTech] and others, no central implementation */
    Technology("tech", "Agriculture", "The name of any tech") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? = when (parameterText) {
            in ruleset.technologies -> null
            else -> UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
    },

    /** Implemented by [Technology.matchesFilter][com.unciv.models.ruleset.tech.Technology.matchesFilter] */
    TechFilter("techFilter", "Agriculture") {
        private val knownValues = setOf("All", "all")

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean {
            if (parameterText in knownValues) return true
            if (parameterText in ruleset.technologies) return true
            if (parameterText in ruleset.eras) return true
            return false
        }

        override fun getTranslationWriterStringsForOutput() = knownValues
    },


    /** unused at the moment with vanilla rulesets */
    Specialist("specialist", "Merchant", "The name of any specialist") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? = when (parameterText) {
            in ruleset.specialists -> null
            else -> UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
    },

    /** [UniqueType.ConditionalAfterPolicyOrBelief] and others, no central implementation */
    Policy("policy", "Oligarchy", "The name of any policy") {
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueParameterErrorSeverity? {
            return when (parameterText) {
                in ruleset.policies -> null
                else -> UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
            }
        }
    },

    /** Used by [UniqueType.HiddenWithoutVictoryType], implementation in Civilopedia and OverviewScreen */
    VictoryT("victoryType", "Domination", "The name of any victory type: 'Neutral', 'Cultural', 'Diplomatic', 'Domination', 'Scientific', 'Time'") {
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueParameterErrorSeverity? {
            return if (parameterText in ruleset.victories) null
            else UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
        }
    },

    /** Used by [UniqueType.KillUnitPlunder] and [UniqueType.KillUnitPlunderNearCity], implementation in [Battle.tryEarnFromKilling][com.unciv.logic.battle.Battle.tryEarnFromKilling] */
    CostOrStrength("costOrStrength", "Cost", "`Cost` or `Strength`") {
        private val knownValues = setOf("Cost", "Strength")
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueParameterErrorSeverity? {
            return if (parameterText in knownValues) null
            else UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
        }
    },

    /** Mod declarative compatibility: Define Mod relations by their name. */
    ModName("modFilter", "DeCiv Redux", """A Mod name, case-sensitive _or_ a simple wildcard filter beginning and ending in an Asterisk, case-insensitive""", "Mod name filter") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = when {
                BaseRuleset.values().any { it.fullName == parameterText } -> null  // Only Builtin ruleset names can contain '-'
                parameterText == "*Civ V -*" || parameterText == "*Civ V - *" -> null  // Wildcard filter for builtin
                '-' in parameterText -> UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
                parameterText.matches(Regex("""^\*[^*]+\*$""")) -> null
                parameterText.startsWith('*') || parameterText.endsWith('*') -> UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
                else -> null
        }

        override fun getTranslationWriterStringsForOutput() = scanExistingValues(this)
    },

    /** Suppress RulesetValidator warnings: Parameter check delegated to RulesetValidator, and auto-translation off. */
    ValidationWarning("validationWarning", Suppression.parameterDocExample, Suppression.parameterDocDescription, "Mod-check warning") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
            if (Suppression.isValidFilter(parameterText)) null
            else UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
    },

    /** Behaves like [Unknown], but states explicitly the parameter is OK and its contents are ignored */
    Comment("comment", "comment", null, "Unique Specials") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? = null

        override fun getTranslationWriterStringsForOutput() = scanExistingValues(this)
    },

    /** We don't know anything about this parameter - this needs to return
     *  [isTranslationWriterGuess]() == `true` for all inputs or TranslationFileWriter will have a problem! */
    Unknown("param", "Unknown") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueParameterErrorSeverity? = null
    };

    //region _Internals

    /** Validate a [Unique] parameter */
    abstract fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity?

    open fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean = false

    fun getErrorSeverityForFilter(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
        val isKnown = MultiFilter.multiFilter(parameterText, {isKnownValue(it, ruleset)}, true)
        if (isKnown) return null
        return UniqueType.UniqueParameterErrorSeverity.PossibleFilteringUnique
    }

    /** Pick this type when [TranslationFileWriter] tries to guess for an untyped [Unique] */
    open fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset): Boolean =
        getErrorSeverity(parameterText, ruleset) == null

    /** Get a list of possible values [TranslationFileWriter] should include as translatable string
     *  that are not recognized from other json sources */
    open fun getTranslationWriterStringsForOutput(): Set<String> = setOf()


    companion object {
        private fun scanExistingValues(type: UniqueParameterType): Set<String> {
            return BaseRuleset.values()
                .mapNotNull { RulesetCache[it.fullName] }
                .map { scanExistingValues(type, it) }
                .fold(setOf()) { a, b -> a + b }
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

        /** Emulate legacy behaviour as exactly as possible */
        private val translationWriterGuessingOrder = sequenceOf(
            Number, StatName,
            UnitName, ImprovementName, Resource, Technology, Promotion,
            BuildingFilter, UnitTypeFilter, Stats,
            ImprovementFilter, CityFilter, TileFilter, Unknown
        )
        fun guessTypeForTranslationWriter(parameterName: String, ruleset: Ruleset): UniqueParameterType {
            return translationWriterGuessingOrder.firstOrNull {
                it.isTranslationWriterGuess(parameterName, ruleset)
            }!!
        }

        fun safeValueOf(param: String) = values().firstOrNull { it.parameterName == param }
            ?: Unknown.apply { this.parameterName = param }  //TODO Danger: There is only one instance of Unknown!
    }

    //endregion
}


class UniqueComplianceError(
    val parameterName: String,
    val acceptableParameterTypes: List<UniqueParameterType>,
    val errorSeverity: UniqueType.UniqueParameterErrorSeverity
)
