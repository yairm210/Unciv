package com.unciv.models.ruleset.unique

import com.unciv.Constants
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueParameterType.Companion.guessTypeForTranslationWriter
import com.unciv.models.stats.Stat
import com.unciv.models.translations.TranslationFileWriter
import com.unciv.ui.utils.extensions.filterCompositeLogic

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
                UniqueType.UniqueComplianceErrorSeverity? {
            return if (parameterText.toIntOrNull() == null) UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
            else null
        }
    },

    RelativeNumber("relativeAmount", "+20", "This indicates a number, usually with a + or - sign, such as `+25` (this kind of parameter is often followed by '%' which is nevertheless not part of the value)") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            return if (parameterText.toIntOrNull() == null) UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
            else null
        }
    },

    // todo potentially remove if OneTimeRevealSpecificMapTiles changes
    KeywordAll("'all'", "All") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) =
            if (parameterText == "All") null else UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
    },

    /** Implemented by [ICombatant.matchesCategory][com.unciv.logic.battle.ICombatant.matchesCategory] */
    CombatantFilter("combatantFilter", "City", "This indicates a combatant, which can either be a unit or a city (when bombarding). Must either be `City` or a `mapUnitFilter`") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText == "City") return null  // City also recognizes "All" but that's covered by UnitTypeFilter too
            return MapUnitFilter.getErrorSeverity(parameterText, ruleset)
        }
    },

    /** Implemented by [MapUnit.matchesFilter][com.unciv.logic.map.MapUnit.matchesFilter] */
    MapUnitFilter("mapUnitFilter", "Wounded", null, "Map Unit Filters") {
        private val knownValues = setOf("Wounded", Constants.barbarians, "City-State", "Embarked", "Non-City")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if ('{' in parameterText) // "{filter} {filter}" for and logic
                return parameterText.filterCompositeLogic({ getErrorSeverity(it, ruleset) }) { a, b -> maxOf(a, b) }
            if (parameterText in knownValues) return null
            return BaseUnitFilter.getErrorSeverity(parameterText, ruleset)
        }
        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Implemented by [BaseUnit.matchesFilter][com.unciv.models.ruleset.unit.BaseUnit.matchesFilter] */
    BaseUnitFilter("baseUnitFilter", "Melee") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if ('{' in parameterText) // "{filter} {filter}" for and logic
                return parameterText.filterCompositeLogic({ getErrorSeverity(it, ruleset) }) { a, b -> maxOf(a, b) }
            if (UnitName.getErrorSeverity(parameterText, ruleset) == null) return null
            if (ruleset.units.values.any { it.uniques.contains(parameterText) }) return null
            return UnitTypeFilter.getErrorSeverity(parameterText, ruleset)
        }
    },

    /** Implemented by [UnitType.matchesFilter][com.unciv.models.ruleset.unit.UnitType.matchesFilter] */
    //todo there is a large discrepancy between this parameter type and the actual filter, most of these are actually implemented by BaseUnitFilter
    UnitTypeFilter("unitType", "Water", null, "Unit Type Filters") {
        // As you can see there is a difference between these and what's in unitTypeStrings (for translation) -
        // the goal is to unify, but for now this is the "real" list
        // Note: this can't handle combinations of parameters (e.g. [{Military} {Water}])
        private val knownValues = setOf(
            "All", "Melee", "Ranged", "Civilian", "Military", "Land", "Water", "Air",
            "non-air", "Nuclear Weapon", "Great Person", "Religious", "Barbarian",
            "relevant", "City",
            // These are up for debate
//            "land units", "water units", "air units", "military units", "submarine units",
        )

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ruleset.unitTypes.containsKey(parameterText)) return null
            if (ruleset.unitTypes.values.any { it.uniques.contains(parameterText) }) return null
            return UniqueType.UniqueComplianceErrorSeverity.WarningOnly
        }

        override fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset) =
            parameterText in ruleset.unitTypes.keys || parameterText in getTranslationWriterStringsForOutput()

        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Only used by [BaseUnitFilter] */
    UnitName("unit", "Musketman") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (ruleset.units.containsKey(parameterText)) return null
            return UniqueType.UniqueComplianceErrorSeverity.WarningOnly
        }
    },

    /** Used by [UniqueType.GreatPersonEarnedFaster] */
    GreatPerson("greatPerson", "Great General") {
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            if (ruleset.units[parameterText]?.hasUnique(UniqueType.GreatPerson) == true) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },

    /** Implemented in [Unique.stats][com.unciv.models.ruleset.unique.Unique.stats] */
    Stats("stats", "+1 Gold, +2 Production", "For example: `+2 Production, +3 Food`. Note that the stat names need to be capitalized!") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (com.unciv.models.stats.Stats.isStats(parameterText)) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },

    /** Many UniqueTypes like [UniqueType.StatPercentBonus] */
    StatName("stat", "Culture", "This is one of the 7 major stats in the game - `Gold`, `Science`, `Production`, `Food`, `Happiness`, `Culture` and `Faith`. Note that the stat names need to be capitalized!") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (Stat.isStat(parameterText)) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },

    /** [UniqueType.DamageUnitsPlunder] and others near that one */
    CivWideStatName("civWideStat", "Gold", "All the following stats have civ-wide fields: `Gold`, `Science`, `Culture`, `Faith`") {
        private val knownValues = Stat.statsWithCivWideField.map { it.name }.toSet()
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },

    /** Implemented by [CityInfo.matchesFilter][com.unciv.logic.city.CityInfo.matchesFilter] */
    CityFilter("cityFilter", "in all cities", null, "City filters") {
        private val cityFilterStrings = setOf(
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
            "in puppeted cities",
            "in holy cities",
            "in City-State cities",
            "in cities following this religion",
        )

        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in cityFilterStrings) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }

        override fun getTranslationWriterStringsForOutput() = cityFilterStrings
    },

    /** Used by [BuildingFilter] and e.g. [UniqueType.ConditionalCityWithBuilding] */
    BuildingName("buildingName", "Library", "The name of any building") {
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in ruleset.buildings) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },

    /** Implemented by [Building.matchesFilter][com.unciv.models.ruleset.Building.matchesFilter] */
    BuildingFilter("buildingFilter", "Culture") {
        private val knownValues = mutableSetOf("All","Building","Buildings","Wonder","Wonders","National Wonder","World Wonder")
            .apply { addAll(Stat.names()) }
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (BuildingName.getErrorSeverity(parameterText, ruleset) == null) return null
            return UniqueType.UniqueComplianceErrorSeverity.WarningOnly
        }

        override fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset) =
            parameterText != "All" && getErrorSeverity(parameterText, ruleset) == null
    },

        /** [UniqueType.PercentProductionConstructions], [UniqueType.PercentProductionConstructionsCities] */
        @Deprecated("as of 3.17.10 - removed 3.18.5")
        ConstructionFilter("constructionFilter", "Spaceship Part") {
            override fun getErrorSeverity(
                parameterText: String,
                ruleset: Ruleset
            ): UniqueType.UniqueComplianceErrorSeverity? {
                if (BuildingFilter.getErrorSeverity(parameterText, ruleset) == null) return null
                if (BaseUnitFilter.getErrorSeverity(parameterText, ruleset) == null) return null
                return UniqueType.UniqueComplianceErrorSeverity.WarningOnly
            }
        },

    /** Implemented by [PopulationManager.getPopulationFilterAmount][com.unciv.logic.city.PopulationManager.getPopulationFilterAmount] */
    PopulationFilter("populationFilter", "Followers of this Religion", null, "Population Filters") {
        private val knownValues = setOf("Population", "Specialists", "Unemployed", "Followers of the Majority Religion", "Followers of this Religion")
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Implemented by [TileInfo.matchesTerrainFilter][com.unciv.logic.map.TileInfo.matchesTerrainFilter] */
    TerrainFilter("terrainFilter", Constants.freshWaterFilter, null, "Terrain Filters") {
        private val knownValues = setOf("All",
                Constants.coastal, "River", "Open terrain", "Rough terrain", "Water resource",
                "Foreign Land", "Foreign", "Friendly Land", "Friendly", "Enemy Land", "Enemy",
                "Featureless", Constants.freshWaterFilter, "non-fresh water", "Natural Wonder",
                "Impassable", "Land", "Water") +
            ResourceType.values().map { it.name + " resource" }
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) = when(parameterText) {
            in knownValues -> null
            in ruleset.terrains -> null
            in ruleset.tileResources -> null
            in ruleset.terrains.values.asSequence().flatMap { it.uniques } -> null
            in ruleset.tileResources.values.asSequence().flatMap { it.uniques } -> null
            else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
        override fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset) =
            parameterText in ruleset.terrains || parameterText != "All" && parameterText in knownValues
        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Implemented by [TileInfo.matchesFilter][com.unciv.logic.map.TileInfo.matchesFilter] */
    TileFilter("tileFilter", "Farm", "Anything that can be used either in an improvementFilter or in a terrainFilter can be used here, plus 'unimproved'", "Tile Filters") {
        private val knownValues = setOf("unimproved", "All Road", "Great Improvement")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ruleset.tileImprovements.containsKey(parameterText)) return null
            return TerrainFilter.getErrorSeverity(parameterText, ruleset)
        }
        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Used by [NaturalWonderGenerator][com.unciv.logic.map.mapgenerator.NaturalWonderGenerator], only tests base terrain or a feature */
    SimpleTerrain("simpleTerrain", "Elevated") {
        private val knownValues = setOf("Elevated", "Water", "Land")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ruleset.terrains.containsKey(parameterText)) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },

    /** Used by [NaturalWonderGenerator.trySpawnOnSuitableLocation][com.unciv.logic.map.mapgenerator.NaturalWonderGenerator.trySpawnOnSuitableLocation], only tests base terrain */
    BaseTerrain("baseTerrain", Constants.grassland, "The name of any terrain that is a base terrain according to the json file") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (ruleset.terrains[parameterText]?.type?.isBaseTerrain == true) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },

    /** Used by: [UniqueType.LandUnitsCrossTerrainAfterUnitGained] (CivilizationInfo.addUnit),
     *  [UniqueType.ChangesTerrain] (MapGenerator.convertTerrains) */
    TerrainName("terrainName", Constants.forest) {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (ruleset.terrains.containsKey(parameterText)) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },

    /** Used for region definitions, can be a terrain type with region unique, or "Hybrid"
     *
     *  See also: [UniqueType.ConditionalInRegionOfType], [UniqueType.ConditionalInRegionExceptOfType], [MapRegions][com.unciv.logic.map.mapgenerator.MapRegions] */
    RegionType("regionType", "Hybrid", null, "Region Types") {
        private val knownValues = setOf("Hybrid")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ruleset.terrains[parameterText]?.hasUnique(UniqueType.RegionRequirePercentSingleType) == true ||
                    ruleset.terrains[parameterText]?.hasUnique(UniqueType.RegionRequirePercentTwoTypes) == true)
                return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Used for start placements: [UniqueType.HasQuality], MapRegions.MapGenTileData.evaluate */
    TerrainQuality("terrainQuality", "Undesirable", null, "Terrain Quality") {
        private val knownValues = setOf("Undesirable", "Food", "Desirable", "Production")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** [UniqueType.UnitStartingPromotions], [UniqueType.TerrainGrantsPromotion], [UniqueType.ConditionalUnitWithPromotion] and others */
    Promotion("promotion", "Shock I", "The name of any promotion") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
                in ruleset.unitPromotions -> null
                else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
            }
    },

    /** [UniqueType.OneTimeFreeTechRuins], [UniqueType.ConditionalDuringEra] and similar */
    Era("era", "Ancient era", "The name of any era") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
                in ruleset.eras -> null
                else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
            }
    },

    /** For [UniqueType.ConstructImprovementConsumingUnit], [UniqueType.CreatesOneImprovement] */
    ImprovementName("improvementName", "Trading Post", "The name of any improvement"){
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText == Constants.cancelImprovementOrder)
                return UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
            if (ruleset.tileImprovements.containsKey(parameterText)) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },

    /** Implemented by [TileImprovement.matchesFilter][com.unciv.models.ruleset.tile.TileImprovement.matchesFilter] */
    ImprovementFilter("improvementFilter", "All Road", null, "Improvement Filters") {
        private val knownValues = setOf("All", "All Road", "Great Improvement", "Great")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ImprovementName.getErrorSeverity(parameterText, ruleset) == null) return null
            if (ruleset.tileImprovements.values.any { it.hasUnique(parameterText) }) return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
        override fun isTranslationWriterGuess(parameterText: String, ruleset: Ruleset) =
            parameterText != "All" && getErrorSeverity(parameterText, ruleset) == null
        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Used by [UniqueType.ConsumesResources] and others, implementation not centralized */
    Resource("resource", "Iron", "The name of any resource") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
            UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
                in ruleset.tileResources -> null
                else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
            }
    },

    /** Used by [UniqueType.FreeExtraBeliefs], see ReligionManager.getBeliefsToChooseAt* functions */
    BeliefTypeName("beliefType", "Follower", "'Pantheon', 'Follower', 'Founder' or 'Enhancer'") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
            in BeliefType.values().map { it.name } -> null
            else -> UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },

    /** unused at the moment with vanilla rulesets */
    Belief("belief", "God of War", "The name of any belief") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
            in ruleset.beliefs -> null
            else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },

    /** Used by [UniqueType.FreeExtraBeliefs] and its any variant, see ReligionManager.getBeliefsToChooseAt* functions */
    FoundingOrEnhancing("foundingOrEnhancing", "founding", "`founding` or `enhancing`", "Prophet Action Filters") {
        // Used in FreeExtraBeliefs, FreeExtraAnyBeliefs
        private val knownValues = setOf("founding", "enhancing")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
            in knownValues -> null
            else -> UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** [UniqueType.ConditionalTech] and others, no central implementation */
    Technology("tech", "Agriculture", "The name of any tech") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
            in ruleset.technologies -> null
            else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },

    /** unused at the moment with vanilla rulesets */
    Specialist("specialist", "Merchant", "The name of any specialist") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = when (parameterText) {
            in ruleset.specialists -> null
            else -> UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },

    /** [UniqueType.ConditionalPolicy] and others, no central implementation */
    Policy("policy", "Oligarchy", "The name of any policy") {
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

    /** Used by [UniqueType.HiddenWithoutVictoryType], implementation in Civilopedia and OverviewScreen */
    VictoryT("victoryType", "Domination", "The name of any victory type: 'Neutral', 'Cultural', 'Diplomatic', 'Domination', 'Scientific', 'Time'") {
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            return if (parameterText in ruleset.victories) null
            else UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },

    /** Used by [UniqueType.KillUnitPlunder] and [UniqueType.KillUnitPlunderNearCity], implementation in [Battle.tryEarnFromKilling][com.unciv.logic.battle.Battle.tryEarnFromKilling] */
    CostOrStrength("costOrStrength", "Cost", "`Cost` or `Strength`") {
        private val knownValues = setOf("Cost", "Strength")
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            return if (parameterText in knownValues) null
            else UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
    },

    /** For untyped "Can [] [] times" unique */
    Action("action", Constants.spreadReligion, "An action that a unit can perform. Currently, there are only two actions part of this: 'Spread Religion' and 'Remove Foreign religions from your own cities'", "Religious Action Filters") {
        private val knownValues = setOf(Constants.spreadReligion, Constants.removeHeresy)
        override fun getErrorSeverity(
            parameterText: String,
            ruleset: Ruleset
        ): UniqueType.UniqueComplianceErrorSeverity? {
            return if (parameterText in knownValues) null
            else UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
        }
        override fun getTranslationWriterStringsForOutput() = knownValues
    },

    /** Behaves like [Unknown], but states explicitly the parameter is OK and its contents are ignored */
    Comment("comment", "comment", null, "Unique Specials") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = null

        override fun getTranslationWriterStringsForOutput() = scanExistingValues(this)
    },

    /** We don't know anything about this parameter - this needs to return
     *  [isTranslationWriterGuess]() == `true` for all inputs or TranslationFileWriter will have a problem! */
    Unknown("param", "Unknown") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? = null
    };

    //region _Internals

    /** Validate a [Unique] parameter */
    abstract fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueComplianceErrorSeverity?

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
    val errorSeverity: UniqueType.UniqueComplianceErrorSeverity
)
