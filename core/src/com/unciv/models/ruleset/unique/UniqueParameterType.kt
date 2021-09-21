package com.unciv.models.ruleset.unique

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TerrainType

// parameterName values should be compliant with autogenerated values in TranslationFileWriter.generateStringsFromJSONs
// Eventually we'll merge the translation generation to take this as the source of that
enum class UniqueParameterType(val parameterName:String) {
    Number("amount") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            return if (parameterText.toIntOrNull() == null) UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
            else null
        }
    },
    MapUnitFilter("mapUnitFilter"){
        private val knownValues = setOf("Wounded", "Barbarians", "City-State", "Embarked", "Non-City")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            return BaseUnitFilter.getErrorSeverity(parameterText, ruleset)
        }
    },
    BaseUnitFilter("baseUnitFilter"){
        // As you can see there is a difference between these and what's in unitTypeStrings (for translation) -
        // the goal is to unify, but for now this is the "real" list
        private val knownValues = setOf("All", "Melee", "Ranged", "Civilian", "Military", "Land", "Water", "Air",
            "non-air", "Nuclear Weapon", "Great Person", "Religious")
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText in knownValues) return null
            if (ruleset.unitTypes.containsKey(parameterText)) return null
            if (ruleset.units.containsKey(parameterText)) return null

            // We could add a giant hashset of all uniques used by units,
            //  so we could accept that unique-targeting uniques are OK. Maybe later.

            return UniqueType.UniqueComplianceErrorSeverity.WarningOnly
        }
    },
    Stats("stats"){
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (!com.unciv.models.stats.Stats.isStats(parameterText))
                return UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
            return null
        }
    },
    CityFilter("cityFilter"){
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText !in cityFilterMap)
                return UniqueType.UniqueComplianceErrorSeverity.RulesetInvariant
            return null
        }
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
    TerrainFilter("terrainFilter") {
        private val knownValues = setOf("All",
            "Coastal", "River", "Open terrain", "Rough terrain", "Water resource",
            "Foreign Land", "Foreign", "Friendly Land", "Friendly", "Enemy Land", "Enemy")
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
    /** Used by NaturalWonderGenerator, only tests base terrain or a feature */
    SimpleTerrain("simpleTerrain") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            if (parameterText == "Elevated") return null
            if (ruleset.terrains.values.any {
                it.name == parameterText &&
                (it.type.isBaseTerrain || it.type == TerrainType.TerrainFeature) 
            })
                return null
            return UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
        }
    },
    Unknown("param") {
        override fun getErrorSeverity(parameterText: String, ruleset: Ruleset):
                UniqueType.UniqueComplianceErrorSeverity? {
            return null
        }
    };

    abstract fun getErrorSeverity(parameterText:String, ruleset: Ruleset): UniqueType.UniqueComplianceErrorSeverity?

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

        val cityFilterMap = setOf( // taken straight from the translation!
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
    }
}


class UniqueComplianceError(
    val parameterName: String,
    val acceptableParameterTypes: List<UniqueParameterType>,
    val errorSeverity: UniqueType.UniqueComplianceErrorSeverity
)
