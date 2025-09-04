package com.unciv.models.ruleset.unique

import com.unciv.Constants
import com.unciv.logic.MultiFilter
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.UniqueParameterType.Companion.guessTypeForTranslationWriter
import com.unciv.models.ruleset.validation.Suppression
import com.unciv.models.stats.Stat
import com.unciv.models.stats.SubStat
import com.unciv.models.translations.TranslationFileWriter
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

// 'region' names beginning with an underscore are used here for a prettier "Structure window" - they go in front of the rest.

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
 * @param severityDefault the default severity used by the [getErrorSeverityViaKnownValue] helper, but not the [getErrorSeverityForFilter] one. Change to [RulesetInvariant][UniqueType.UniqueParameterErrorSeverity.RulesetInvariant] if you override [isKnownValue] and the ruleset is unused.
 */
//region _Fields
@Suppress("unused") // Some are used only via enumerating the enum matching on parameterName
enum class UniqueParameterType(
    val parameterName: String,
    val docExample: String,
    val docDescription: String? = null,
    val displayName: String = parameterName,
    private val severityDefault: UniqueType.UniqueParameterErrorSeverity = UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
) {
    //endregion

    Number("amount", "3", "This indicates a whole number, possibly with a + or - sign, such as `2`, `+13`, or `-3`") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) =
            parameterText.getInvariantSeverityUnless { toIntOrNull() != null }
    },

    PositiveNumber("positiveAmount", "3", "This indicates a positive whole number, larger than zero, a '+' sign is optional") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) =
            parameterText.getInvariantSeverityUnless { toIntOrNull()?.let { it > 0 } == true }
    },
    
    NonNegativeNumber("nonNegativeAmount", "3", "This indicates a non-negative whole number, larger than or equal to zero, a '+' sign is optional") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) =
            parameterText.getInvariantSeverityUnless { toIntOrNull()?.let { it >= 0 } == true }
    },

    Fraction("fraction", docExample = "0.5", "Indicates a fractional number, which can be negative") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) =
            parameterText.getInvariantSeverityUnless { toFloatOrNull() != null }
    },

    RelativeNumber("relativeAmount", "+20", "This indicates a number, usually with a + or - sign, such as `+25` (this kind of parameter is often followed by '%' which is nevertheless not part of the value)") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) =
            parameterText.getInvariantSeverityUnless { toIntOrNull() != null }
    },

    Countable("countable", "1000", "This indicates a number or a numeric variable." +
            "They can be tested in the developer console with `civ checkcountable` - for example, `civ checkcountable \"[Iron]+2\"`") {
        override fun isKnownValue(parameterText: String, ruleset: Ruleset) =
            Countables.isKnownValue(parameterText, ruleset)

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) =
            Countables.getKnownValuesForAutocomplete(ruleset)

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
            return Countables.getErrorSeverity(parameterText, ruleset)
        }
    },

    // todo potentially remove if OneTimeRevealSpecificMapTiles changes
    KeywordAll("'all'", "All", severityDefault = UniqueType.UniqueParameterErrorSeverity.RulesetInvariant) {
        override val staticKnownValues = Constants.all
    },

    /** Implemented by [ICombatant.matchesCategory][com.unciv.logic.battle.ICombatant.matchesFilter] */
    CombatantFilter("combatantFilter", "City", "This indicates a combatant, which can either be a unit or a city (when bombarding). Must either be `City` or a `mapUnitFilter`") {
        override val staticKnownValues = setOf("City")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when {
            parameterText in staticKnownValues -> true
            MapUnitFilter.isKnownValue(parameterText, ruleset) -> true
            else -> CityFilter.isKnownValue(parameterText, ruleset)
        }

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            staticKnownValues +
                    MapUnitFilter.getKnownValuesForAutocomplete(ruleset) +
                    CityFilter.getKnownValuesForAutocomplete(ruleset)
    },


    /** Implemented by [MapUnit.matchesFilter][com.unciv.logic.map.mapunit.MapUnit.matchesFilter] */
    MapUnitFilter("mapUnitFilter", Constants.wounded, null, "Map Unit Filters") {
        override val staticKnownValues = setOf(Constants.wounded, Constants.barbarians, "Barbarian",
            "City-State", Constants.embarked, "Non-City")

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = getErrorSeverityForFilter(parameterText, ruleset)


        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when {
            parameterText in staticKnownValues -> true
            parameterText in ruleset.unitPromotions -> true
            ruleset.unitPromotions.values.any { it.hasTagUnique(parameterText) } -> true
            CivFilter.isKnownValue(parameterText, ruleset) -> true
            BaseUnitFilter.isKnownValue(parameterText, ruleset) -> true
            else -> false
        }

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            staticKnownValues +
                ruleset.unitPromotions.keys +
                CivFilter.getKnownValuesForAutocomplete(ruleset) +
                BaseUnitFilter.getKnownValuesForAutocomplete(ruleset)
    },

    /** Implemented by [BaseUnit.matchesFilter][com.unciv.models.ruleset.unit.BaseUnit.matchesFilter] */
    BaseUnitFilter("baseUnitFilter", "Melee") {
        override val staticKnownValues = setOf(
            "Melee", "Ranged", "Civilian", "Military", "non-air",
            "Nuclear Weapon", "Great Person", "Religious",
            "relevant", // used for UniqueType.UnitStartingPromotions
        ) + Constants.all

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when {
            parameterText in staticKnownValues -> true
            UnitName.getErrorSeverity(parameterText, ruleset) == null -> true
            UnitTypeFilter.isKnownValue(parameterText, ruleset) -> true
            TechFilter.isKnownValue(parameterText, ruleset) -> true
            isKnownTag(parameterText, ruleset, "Unit type") -> true
            else -> false
        }

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            staticKnownValues +
                    UnitName.getKnownValuesForAutocomplete(ruleset) +
                    UnitTypeFilter.getKnownValuesForAutocomplete(ruleset) +
                    TechFilter.getKnownValuesForAutocomplete(ruleset)
    },

    /** Implemented by [UnitType.matchesFilter][com.unciv.models.ruleset.unit.UnitType.matchesFilter] */
    UnitTypeFilter("unitType", "Water", "Can be 'Land', 'Water', 'Air', any unit type, a filtering Unique on a unit type, or a multi-filter of these", "Unit Type Filters") {
        override val staticKnownValues = setOf(
            "Land", "Water", "Air",
        )
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when (parameterText) {
            in staticKnownValues -> true
            in ruleset.unitTypes -> true
            else -> parameterText.isFilteringUniqueIn(ruleset.unitTypes)
        }

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> {
            return super.getKnownValuesForAutocomplete(ruleset) +
                    ruleset.unitTypes.keys
        }

        override fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset) =
            parameterText in ruleset.unitTypes || parameterText in staticKnownValues
    },

    /** Used by [BaseUnitFilter] and e.g. [UniqueType.OneTimeFreeUnit] */
    UnitName("unit", "Musketman") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.units.keys
    },

    /** Used by [UniqueType.GreatPersonEarnedFaster] */
    GreatPerson("greatPerson", "Great General") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) =
            ruleset.units.filter { it.value.hasUnique(UniqueType.GreatPerson) }.keys
    },

    /** Implemented in [Unique.stats][com.unciv.models.ruleset.unique.Unique.stats] */
    Stats("stats", "+1 Gold, +2 Production", "For example: `+2 Production, +3 Food`. Note that the stat names need to be capitalized!",
        severityDefault = UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
    ) {
        override fun isKnownValue(parameterText: String, ruleset: Ruleset) =
            com.unciv.models.stats.Stats.isStats(parameterText)
    },

    /** Many UniqueTypes like [UniqueType.StatPercentBonus] */
    StatName("stat", "Culture", "This is one of the 7 major stats in the game - `Gold`, `Science`, `Production`, `Food`, `Happiness`, `Culture` and `Faith`. Note that the stat names need to be capitalized!",
        severityDefault = UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
    ) {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = Stat.names()
    },

    /** [UniqueType.DamageUnitsPlunder] and others near that one */
    CivWideStatName("civWideStat", "Gold", "All the following stats have civ-wide fields: `Gold`, `Science`, `Culture`, `Faith`",
        severityDefault = UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
    ) {
        override val staticKnownValues = Stat.statsWithCivWideField.map { it.name }.toSet()
    },

    /** Implemented by [Civ.matchesFilter][com.unciv.logic.civilization.Civilization.matchesFilter] */
    CivFilter("civFilter", Constants.cityStates) {
        override val staticKnownValues = setOf("AI player", "Human player")

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when {
            parameterText in staticKnownValues -> true
            else -> NationFilter.isKnownValue(parameterText, ruleset)
        }

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            staticKnownValues + NationFilter.getKnownValuesForAutocomplete(ruleset)
    },

    /** Implemented by [Nation.matchesFilter][com.unciv.models.ruleset.nation.Nation.matchesFilter] */
    NationFilter("nationFilter", Constants.cityStates) {
        override val staticKnownValues = setOf(Constants.cityStates, "City-State", "Major") + Constants.all

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when (parameterText) {
            in staticKnownValues -> true
            in ruleset.nations -> true
            else -> isKnownTag(parameterText, ruleset, "Nation")
        }

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            staticKnownValues + ruleset.nations.keys
    },

    Tag("tag", "Communist", "A tagged unique (verbatim, no placeholders)") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = getErrorSeverityForFilter(parameterText, ruleset)
        override fun isKnownValue(parameterText: String, ruleset: Ruleset) =
            isKnownTag(parameterText, ruleset, "Nation") ||
            isKnownTag(parameterText, ruleset, "Unit type")
    },

    TagTarget("tagTarget", "Nation", "`Nation`, or `Unit type`") {
        override val staticKnownValues = setOf("Nation", "Unit type")
    },

    /** Implemented by [City.matchesFilter][com.unciv.logic.city.City.matchesFilter] */
    CityFilter("cityFilter", "in all cities", null, "City filters") {
        override val staticKnownValues = setOf(
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

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = getErrorSeverityForFilter(parameterText, ruleset)
    },

    /** Used by [BuildingFilter] and e.g. [UniqueType.ConditionalCityWithBuilding] */
    BuildingName("buildingName", "Library", "The name of any building") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> = ruleset.buildings.keys
    },

    /** Implemented by [Building.matchesFilter][com.unciv.models.ruleset.Building.matchesFilter] */
    BuildingFilter("buildingFilter", "Culture") {
        override val staticKnownValues = setOf("Building", "Buildings", "Wonder", "Wonders", "National Wonder", "National", "World Wonder", "World") +
            Stat.names() + Constants.all

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when {
            parameterText in staticKnownValues -> true
            BuildingName.isKnownValue(parameterText, ruleset) -> true
            ruleset.buildings.values.any { it.hasTagUnique(parameterText) } -> true
            TechFilter.isKnownValue(parameterText, ruleset) -> true
            else -> false
        }

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            staticKnownValues +
                BuildingName.getKnownValuesForAutocomplete(ruleset) +
                TechFilter.getKnownValuesForAutocomplete(ruleset)

        override fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset) =
            parameterText !in Constants.all && getErrorSeverity(parameterText, ruleset) == null
    },

    /** Implemented by [PopulationManager.getPopulationFilterAmount][com.unciv.logic.city.managers.CityPopulationManager.getPopulationFilterAmount] */
    PopulationFilter("populationFilter", "Followers of this Religion", null, "Population Filters",
        severityDefault = UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
    ) {
        override val staticKnownValues = setOf("Population", "Specialists", "Unemployed", "Followers of the Majority Religion", "Followers of this Religion")
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            staticKnownValues + ruleset.specialists.keys
    },

    /** Implemented by [Tile.matchesTerrainFilter][com.unciv.logic.map.tile.Tile.matchesTerrainFilter] */
    TerrainFilter("terrainFilter", Constants.freshWaterFilter, null, "Terrain Filters") {
        override val staticKnownValues = setOf(
            "Terrain",
            Constants.coastal, Constants.river, "Open terrain", "Rough terrain", "Water resource",
            "resource", "Foreign Land", "Foreign", "Friendly Land", "Friendly", "Enemy Land", "Enemy", "your", "Unowned",
            "Featureless", Constants.freshWaterFilter, "non-fresh water", "Natural Wonder",
            "Impassable", "Land", "Water"
        ) + ResourceType.entries.map { it.name + " resource" } + Constants.all

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when {
            parameterText in staticKnownValues -> true
            parameterText in ruleset.terrains -> true
            parameterText in ruleset.tileResources -> true
            parameterText.isFilteringUniqueIn(ruleset.terrains) -> true
            parameterText.isFilteringUniqueIn(ruleset.tileResources) -> true
            else -> false
        }

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            staticKnownValues +
                ruleset.terrains.keys +
                ruleset.tileResources.keys

        override fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset) =
            parameterText in ruleset.terrains
                    || parameterText !in Constants.all && parameterText in staticKnownValues
    },

    /** Implemented by [Tile.matchesFilter][com.unciv.logic.map.tile.Tile.matchesFilter] */
    TileFilter("tileFilter", "Farm", "Anything that can be used either in an improvementFilter or in a terrainFilter can be used here, plus 'unimproved'", "Tile Filters") {
        override val staticKnownValues = setOf("unimproved", "improved", "worked", "pillaged", "All Road", "Great Improvement")

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when {
            parameterText in staticKnownValues -> true
            ImprovementFilter.isKnownValue(parameterText, ruleset) -> true
            TerrainFilter.isKnownValue(parameterText, ruleset) -> true
            CivFilter.isKnownValue(parameterText, ruleset) -> true
            else -> false
        }

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            staticKnownValues +
                ImprovementFilter.getKnownValuesForAutocomplete(ruleset) +
                TerrainFilter.getKnownValuesForAutocomplete(ruleset)
    },

    /** Used by [NaturalWonderGenerator][com.unciv.logic.map.mapgenerator.NaturalWonderGenerator], only tests base terrain or a feature */
    SimpleTerrain("simpleTerrain", "Elevated") {
        override val staticKnownValues = setOf("Elevated", "Water", "Land")
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) =
            staticKnownValues + ruleset.terrains.keys
    },

    /** Used by [NaturalWonderGenerator][com.unciv.logic.map.mapgenerator.NaturalWonderGenerator], only tests base terrain */
    BaseTerrain("baseTerrain", Constants.grassland, "The name of any terrain that is a base terrain according to the json file") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            ruleset.terrains.filter { it.value.type.isBaseTerrain }.keys
    },
    /** Used by [UniqueType.NaturalWonderConvertNeighbors], only tests base terrain.
     *  - See [NaturalWonderGenerator.trySpawnOnSuitableLocation][com.unciv.logic.map.mapgenerator.NaturalWonderGenerator.trySpawnOnSuitableLocation] */
    TerrainFeature("terrainFeature", Constants.hill, "The name of any terrain that is a terrain feature according to the json file") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueParameterErrorSeverity? {
            if (ruleset.terrains[parameterText]?.type == TerrainType.TerrainFeature) return null
            return UniqueType.UniqueParameterErrorSeverity.RulesetSpecific
        }
    },

    /** Used by: [UniqueType.LandUnitsCrossTerrainAfterUnitGained] (CivilizationInfo.addUnit),
     *  [UniqueType.ChangesTerrain] (MapGenerator.convertTerrains) */
    TerrainName("terrainName", Constants.forest) {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.terrains.keys
    },

    /** Used for region definitions, can be a terrain type with region unique, or "Hybrid"
     *
     *  See also: [UniqueType.ConditionalInRegionOfType], [UniqueType.ConditionalInRegionExceptOfType], [MapRegions][com.unciv.logic.map.mapgenerator.mapregions.MapRegions] */
    RegionType("regionType", "Hybrid", null, "Region Types") {
        override val staticKnownValues = setOf("Hybrid")

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) =
            staticKnownValues +
                    ruleset.terrains.filter {
                        it.value.hasUnique(UniqueType.RegionRequirePercentSingleType) ||
                            it.value.hasUnique(UniqueType.RegionRequirePercentTwoTypes) }.keys
    },

    /** Used for start placements: [UniqueType.HasQuality], MapRegions.MapGenTileData.evaluate */
    TerrainQuality("terrainQuality", "Undesirable", null, "Terrain Quality",
        severityDefault = UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
    ) {
        override val staticKnownValues = setOf("Undesirable", "Food", "Desirable", "Production")
    },

    /** [UniqueType.UnitStartingPromotions], [UniqueType.TerrainGrantsPromotion], [UniqueType.ConditionalUnitWithPromotion] and others */
    Promotion("promotion", "Shock I", "The name of any promotion") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.unitPromotions.keys
    },

    /** [UniqueType.OneTimeFreeTechRuins], [UniqueType.ConditionalDuringEra] and similar */
    Era("era", "Ancient era", "The name of any era") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.eras.keys
    },

    Speed("speed", "Quick", "The name of any speed") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.speeds.keys
    },
    Difficulty("difficulty", "Prince", "The name of any difficulty") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.difficulties.keys
    },

    /** For [UniqueType.CreatesOneImprovement] */
    ImprovementName("improvementName", "Trading Post", "The name of any improvement excluding 'Cancel improvement order'") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.tileImprovements.keys - Constants.cancelImprovementOrder
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) =
            if (parameterText == Constants.cancelImprovementOrder) UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
            else getErrorSeverityViaKnownValue(parameterText, ruleset)
    },

    /** Implemented by [TileImprovement.matchesFilter][com.unciv.models.ruleset.tile.TileImprovement.matchesFilter] */
    ImprovementFilter("improvementFilter", "All Road", null, "Improvement Filters") {
        override val staticKnownValues = setOf("Improvement", "All Road", "Great Improvement", "Great") + Constants.all

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = getErrorSeverityForFilter(parameterText, ruleset)

        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when {
            parameterText in staticKnownValues -> true
            ImprovementName.isKnownValue(parameterText, ruleset) -> true
            ruleset.tileImprovements.values.any { it.hasTagUnique(parameterText) } -> true
            else -> false
        }

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> =
            staticKnownValues +
                ImprovementName.getKnownValuesForAutocomplete(ruleset)

        override fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset) =
            parameterText !in Constants.all && getErrorSeverityForFilter(parameterText, ruleset) == null
    },

    /** Used by [UniqueType.ConsumesResources] and others, implementation not centralized */
    Resource("resource", "Iron", "The name of any resource") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.tileResources.keys
    },

    /** Used by [UniqueType.OneTimeConsumeResources], [UniqueType.OneTimeProvideResources], [UniqueType.CostsResources], [UniqueType.UnitActionStockpileCost], implementation not centralized */
    StockpiledResource("stockpiledResource", "Mana", "The name of any stockpiled resource") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.tileResources.filter { it.value.isStockpiled }.keys
    },

    /** Used by [UniqueType.OneTimeGainResource], implementation not centralized */
    Stockpile("stockpile", "Mana", "The name of any stockpiled resource") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> {
            return ruleset.tileResources.filter { it.value.isStockpiled }.keys +
                Stat.entries.map { it.name } + SubStat.StoredFood.text + SubStat.GoldenAgePoints.text
        }
    },

    /** Used by [UniqueType.ImprovesResources], implemented by [com.unciv.models.ruleset.tile.TileResource.matchesFilter] */
    ResourceFilter("resourceFilter", "Strategic", "A resource name, type, 'all', or a Stat listed in the resource's improvementStats",
        severityDefault = UniqueType.UniqueParameterErrorSeverity.PossibleFilteringUnique
    ) {
        override val staticKnownValues = setOf("any") + Constants.all // "any" sounds nicer than "all" in that UniqueType
        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when {
            parameterText in staticKnownValues -> true
            parameterText in ruleset.tileResources -> true
            ResourceType.entries.any { it.name == parameterText } -> true
            Stat.isStat(parameterText) -> true
            else -> false
        }

        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) =
            staticKnownValues + ruleset.tileResources.keys + ResourceType.entries.map { it.name } + Stat.names()
    },

    /** Used by [UniqueType.FreeExtraBeliefs], see ReligionManager.getBeliefsToChooseAt* functions */
    BeliefTypeName("beliefType", "Follower", "'Pantheon', 'Follower', 'Founder' or 'Enhancer'",
        severityDefault = UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
    ) {
        override val staticKnownValues = BeliefType.entries.map { it.name }.toSet()
    },

    /** unused at the moment with vanilla rulesets */
    Belief("belief", "God of War", "The name of any belief") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.beliefs.keys
    },
    
    /**Used by [UniqueType.ConditionalCityReligion]*/
    ReligionFilter("religionFilter", "major") {
        override val staticKnownValues = setOf("any", "major", "enhanced", "your", "foreign", "enemy")
        override fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean {
            return when (parameterText) {
                in staticKnownValues -> true
                in ruleset.religions -> true
                in ruleset.beliefs -> true
                else -> ruleset.beliefs.values.any { it.hasTagUnique(parameterText) }
            }
        }
    },

    /** Used by [UniqueType.FreeExtraBeliefs] and its any variant, see ReligionManager.getBeliefsToChooseAt* functions */
    FoundingOrEnhancing("foundingOrEnhancing", "founding", "`founding` or `enhancing`", "Prophet Action Filters",
        severityDefault = UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
    ) {
        override val staticKnownValues = setOf("founding", "enhancing")
    },

    /** [UniqueType.ConditionalTech] and others, no central implementation */
    Event("event", "Inspiration", "The name of any event") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.events.keys
    },


    /** [UniqueType.ConditionalTech] and others, no central implementation */
    Technology("tech", "Agriculture", "The name of any tech") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.technologies.keys
    },

    /** Implemented by [Technology.matchesFilter][com.unciv.models.ruleset.tech.Technology.matchesFilter] */
    TechFilter("techFilter", "Agriculture") {
        override val staticKnownValues = Constants.all
        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when (parameterText) {
            in staticKnownValues -> true
            in ruleset.technologies -> true
            in ruleset.eras -> true
            else -> ruleset.technologies.values.any { it.hasTagUnique(parameterText) }
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = staticKnownValues + ruleset.technologies.keys + ruleset.eras.keys
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = getErrorSeverityForFilter(parameterText, ruleset)
    },


    /** unused at the moment with vanilla rulesets */
    Specialist("specialist", "Merchant", "The name of any specialist") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.specialists.keys
    },

    /** [UniqueType.ConditionalAfterPolicyOrBelief] and others, no central implementation */
    Policy("policy", "Oligarchy", "The name of any policy") {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.policies.keys
    },

    /** Implemented by [com.unciv.models.ruleset.Policy.matchesFilter] */
    PolicyFilter("policyFilter", "Oligarchy",
        "The name of any policy, a filtering Unique, any branch (matching only the branch itself)," +
        " a branch name with \" Completed\" appended (matches if the branch is completed)," +
        " or a policy branch as `[branchName] branch` (matching all policies in that branch)."
    ) {
        override val staticKnownValues = Constants.all
        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when {
            parameterText in staticKnownValues -> true
            parameterText in ruleset.policies -> true
            parameterText.startsWith('[') && parameterText.endsWith("] branch") &&
                parameterText.removeSurrounding("[", "] branch") in ruleset.policyBranches -> true
            ruleset.policies.values.any { it.hasTagUnique(parameterText) } -> true
            else -> false
        }
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) =
            staticKnownValues + ruleset.policies.keys +
            ruleset.policyBranches.keys.map { "[$it] branch" }.toSet()

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = getErrorSeverityForFilter(parameterText, ruleset)
    },

    /** Used by [UniqueType.ConditionalVictoryEnabled], implementation in Civilopedia, OverviewScreen and to exclude e.g. from Quests */
    VictoryT("victoryType",
        "Domination", "The name of any victory type: 'Cultural', 'Diplomatic', 'Domination', 'Scientific', 'Time' or one of your mod's VictoryTypes.json names"
    ) {
        override fun getKnownValuesForAutocomplete(ruleset: Ruleset) = ruleset.victories.keys
    },

    /** Used by [UniqueType.KillUnitPlunder] and [UniqueType.KillUnitPlunderNearCity], implementation in [Battle.tryEarnFromKilling][com.unciv.logic.battle.Battle.tryEarnFromKilling] */
    CostOrStrength("costOrStrength", "Cost", "`Cost` or `Strength`",
        severityDefault = UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
    ) {
        override val staticKnownValues = setOf("Cost", "Strength")
    },


    UnitTriggerTarget("unitTriggerTarget", Constants.thisUnit, "`${Constants.thisUnit}` or `${Constants.targetUnit}`") {
        override val staticKnownValues = setOf(Constants.thisUnit, Constants.targetUnit)
    },

    /** Mod declarative compatibility: Define Mod relations by their name. */
    ModName("modFilter",
        docExample = "DeCiv Redux",
        docDescription = """
            |A Mod name, case-sensitive _or_ a simple wildcard filter beginning and ending in an Asterisk, case-insensitive.
            |Note that this must use the Mod name as Unciv displays it, not the Repository name.
            |There is a conversion affecting dashes and leading/trailing blanks. Please make sure not to get confused.
        """.trimMargin(),
        displayName = "Mod name filter",
        severityDefault = UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
    ) {
        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = when {
            BaseRuleset.entries.any { it.fullName == parameterText } -> true  // Verbatim builtin
            parameterText == "*Civ V -*" || parameterText == "*Civ V - *" -> true  // Wildcard filter for builtin
            '-' in parameterText -> false  // Only Builtin ruleset names can contain '-'
            parameterText.matches(Regex("""^\*[^*]+\*$""")) -> true  // Wildcard on both ends and no wildcard in between
            parameterText.startsWith('*') || parameterText.endsWith('*') -> true  // one-sided wildcards aren't implemented (feel free to...)
            else -> true
        }
        override fun getTranslationWriterStringsForOutput() = scanExistingValues(this)
    },

    /** Suppress RulesetValidator warnings: Parameter check delegated to RulesetValidator, and auto-translation off. */
    ValidationWarning("validationWarning",
        Suppression.parameterDocExample, Suppression.parameterDocDescription, "Mod-check warning",
        severityDefault = UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
    ) {
        override fun isKnownValue(parameterText: String, ruleset: Ruleset) = Suppression.isValidFilter(parameterText)
    },

    /** Behaves like [Unknown], but states explicitly the parameter is OK and its contents are ignored */
    Comment("comment", "comment", null, "Unique Specials") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = null
        override fun getTranslationWriterStringsForOutput() = scanExistingValues(this)
    },

    /** We don't know anything about this parameter - this needs to return
     *  [isTranslationWriterGuess]() == `true` for all inputs or TranslationFileWriter will have a problem! */
    Unknown("param", "Unknown") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = null
    };

    //region _Internals

    /** Validate a [Unique] parameter.
     *  - The default implementation flags [severityDefault] when [isKnownValue] returns `false`.
     *  - This means [getErrorSeverity] or [isKnownValue] ***must*** be overridden or else the UniqueParameterType is never valid.
     *  - Can be delegated to helper [getErrorSeverityForFilter] for multiFilters (uses [isKnownValue]).
     *  - Can be delegated to helper [getErrorSeverityViaKnownValue] for simple filters (uses [isKnownValue]).
     */
    @Readonly
    open fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? =
        getErrorSeverityViaKnownValue(parameterText, ruleset)

    /** Checks if [parameterText] is a valid value.
     *  - The default implementation returns `false`, and controls the default implementation of [getErrorSeverity].
     *  - If this is overridden and [getErrorSeverity] is not, the "bad" outcome is [severityDefault].
     *  - [getErrorSeverity] takes precedence and chooses whether to call this or not.
     *  - This means [getErrorSeverity] or [isKnownValue] ***must*** be overridden or else the UniqueParameterType is never valid.
     */
    @Readonly
    open fun isKnownValue(parameterText: String, ruleset: Ruleset): Boolean =
        getKnownValuesForAutocomplete(ruleset).contains(parameterText)

    /** This returns the known values *for autocomplete* -
     *  there may be 'known values' not in this set, for example uniques.
     *  If there aren't, you don't need to override isKnownValue at all, since it will compare to this */
    @Readonly open fun getKnownValuesForAutocomplete(ruleset: Ruleset): Set<String> = staticKnownValues

    open val staticKnownValues: Set<String> = emptySet()

    @Readonly
    protected fun getErrorSeverityForFilter(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
        val isKnown = MultiFilter.multiFilter(parameterText, { isKnownValue(it, ruleset) }, true)
        if (isKnown) return null
        return UniqueType.UniqueParameterErrorSeverity.PossibleFilteringUnique
    }

    @Readonly
    protected fun getErrorSeverityViaKnownValue(
        parameterText: String, ruleset: Ruleset,
        errorSeverity: UniqueType.UniqueParameterErrorSeverity = severityDefault
    ) = if (isKnownValue(parameterText, ruleset)) null else errorSeverity

    @Readonly
    protected fun String.isFilteringUniqueIn(map: Map<String, IHasUniques>)
        = map.values.any { this in it.uniques }

    @Readonly
    protected fun String.getInvariantSeverityUnless(@Readonly predicate: String.() -> Boolean) =
        if (predicate()) null else UniqueType.UniqueParameterErrorSeverity.RulesetInvariant

    /** Pick this type when [TranslationFileWriter] tries to guess for an untyped [Unique] */
    @Readonly
    open fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset): Boolean =
        getErrorSeverity(parameterText, ruleset) == null

    /** Get a list of possible values [TranslationFileWriter] should include as translatable string
     *  that are not recognized from other json sources */
    @Readonly 
    open fun getTranslationWriterStringsForOutput(): Set<String> = staticKnownValues


    companion object {
        @Readonly
        private fun scanExistingValues(type: UniqueParameterType): Set<String> {
            return BaseRuleset.entries
                .mapNotNull { RulesetCache[it.fullName] }
                .map { scanExistingValues(type, it) }
                .fold(setOf()) { a, b -> a + b }
        }
        @Readonly
        private fun scanExistingValues(type: UniqueParameterType, ruleset: Ruleset): Set<String> {
            val result = mutableSetOf<String>()
            for (unique in ruleset.allUniques()) {
                val parameterMap = unique.type?.parameterTypeMap ?: continue
                for ((index, param) in unique.params.withIndex()) {
                    if (type !in parameterMap[index]) continue
                    result += param
                }
            }
            return result
        }

        /** Checks whether or not the given tag applies to the associated tagTarget. */
        @Readonly
        private fun isKnownTag(tag: String, ruleset: Ruleset, tagTarget: String): Boolean =
            when (tagTarget) {
                "Nation" -> ruleset.nations.values.any { it.hasTagUnique(tag) }
                "Unit type" -> ruleset.units.values.any { it.hasTagUnique(tag) }
                else -> throw IllegalArgumentException("Unknown tagTarget: $tagTarget")
            } || ruleset.allUniques().any { it.type == UniqueType.MarkTargetAsTag && it.params[0] == tagTarget && it.params[1] == tag }

        /** Emulate legacy behaviour as exactly as possible */
        private val translationWriterGuessingOrder = sequenceOf(
            Number, StatName,
            UnitName, ImprovementName, Resource, Technology, Promotion,
            BuildingFilter, UnitTypeFilter, Stats,
            ImprovementFilter, CityFilter, TileFilter, Unknown
        )
        @Readonly
        fun guessTypeForTranslationWriter(parameterName: String, ruleset: Ruleset): UniqueParameterType {
            return translationWriterGuessingOrder.firstOrNull {
                it.isTranslationWriterGuess(parameterName, ruleset)
            }!!
        }

        @Pure fun safeValueOf(param: String) = entries.firstOrNull { it.parameterName == param }
    }

    //endregion
}


class UniqueComplianceError(
    val parameterName: String,
    val acceptableParameterTypes: List<UniqueParameterType>,
    val errorSeverity: UniqueType.UniqueParameterErrorSeverity
)
