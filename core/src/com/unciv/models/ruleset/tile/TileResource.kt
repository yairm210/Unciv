package com.unciv.models.ruleset.tile

import com.unciv.logic.MultiFilter
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetStatsObject
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.GameResource
import com.unciv.models.stats.Stats
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.screens.civilopediascreen.FormattedLine

class TileResource : RulesetStatsObject(), GameResource {

    var resourceType: ResourceType = ResourceType.Bonus
    var terrainsCanBeFoundOn: List<String> = listOf()

    /** stats that this resource adds to a tile */
    var improvementStats: Stats? = null
    var revealedBy: String? = null

    /** Legacy "which improvement will unlock this treausre"
     *  @see improvedBy
     *  @see getImprovements
     */
    var improvement: String? = null
    /** Defines which improvement will "unlock" this resource
     *  @see improvement
     *  @see getImprovements
     */
    var improvedBy: List<String> = listOf()

    var majorDepositAmount: DepositAmount = DepositAmount()
    var minorDepositAmount: DepositAmount = DepositAmount()

    val isCityWide by lazy { hasUnique(UniqueType.CityResource, StateForConditionals.IgnoreConditionals) }

    val isStockpiled by lazy { hasUnique(UniqueType.Stockpiled, StateForConditionals.IgnoreConditionals) }

    private var improvementsInitialized = false
    /** Cache collecting [improvement], [improvedBy] and [UniqueType.ImprovesResources] uniques on the improvements themselves. */
    private val allImprovements = mutableSetOf<String>()
    private var ruleset: Ruleset? = null

    /** Collects which improvements "unlock" this resource, caches and returns the Set.
     *  - The cache is cleared after ruleset load and combine, via [setTransients].
     *  @see improvement
     *  @see improvedBy
     *  @see UniqueType.ImprovesResources
     */
    fun getImprovements(): Set<String> {
        if (improvementsInitialized) return allImprovements
        val ruleset = this.ruleset
            ?: throw IllegalStateException("No ruleset on TileResource when initializing improvements")
        if (improvement != null) allImprovements += improvement!!
        allImprovements.addAll(improvedBy)
        for (improvement in ruleset.tileImprovements.values) {
            if (improvement.getMatchingUniques(UniqueType.ImprovesResources).none { matchesFilter(it.params[0]) }) continue
            allImprovements += improvement.name
        }
        improvementsInitialized = true
        return allImprovements
    }

    /** Clears the cache for [getImprovements] and saves the Ruleset the cache update will need.
     *  - Doesn't evaluate the cache immediately, that would break tests as it trips the uniqueObjects lazy and tests manipulate uniques after ruleset load.
     *  - called from [Ruleset.updateResourceTransients]
     */
    fun setTransients(ruleset: Ruleset) {
        allImprovements.clear()
        improvementsInitialized = false
        this.ruleset = ruleset
    }

    override fun getUniqueTarget() = UniqueTarget.Resource

    override fun makeLink() = "Resource/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        textList += FormattedLine("${resourceType.name} resource", header = 4, color = resourceType.color)
        textList += FormattedLine()

        uniquesToCivilopediaTextLines(textList, sorted = true)

        textList += FormattedLine(cloneStats().toString())

        if (terrainsCanBeFoundOn.isNotEmpty()) {
            textList += FormattedLine()
            if (terrainsCanBeFoundOn.size == 1) {
                val terrainName = terrainsCanBeFoundOn[0]
                textList += FormattedLine("{Can be found on} {$terrainName}", link = "Terrain/$terrainName")
            } else {
                textList += FormattedLine("{Can be found on}:")
                terrainsCanBeFoundOn.forEach {
                    textList += FormattedLine(it, link = "Terrain/$it", indent = 1)
                }
            }
        }

        for (improvement in getImprovements()) {
            textList += FormattedLine()
            textList += FormattedLine("Improved by [$improvement]", link = "Improvement/$improvement")
            if (improvementStats != null && !improvementStats!!.isEmpty())
                textList += FormattedLine("{Bonus stats for improvement}: " + improvementStats.toString())
        }

        val improvementsThatProvideThis = ruleset.tileImprovements.values
            .filter { improvement ->
                improvement.uniqueObjects.any { unique ->
                    unique.type == UniqueType.ProvidesResources && unique.params[1] == name
                }
            }
        if (improvementsThatProvideThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Improvements that provide this resource}:")
            improvementsThatProvideThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        val buildingsThatProvideThis = ruleset.buildings.values
            .filter { building ->
                building.uniqueObjects.any { unique ->
                    unique.type == UniqueType.ProvidesResources && unique.params[1] == name
                }
            }
        if (buildingsThatProvideThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Buildings that provide this resource}:")
            buildingsThatProvideThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        val buildingsThatConsumeThis = ruleset.buildings.values.filter { it.getResourceRequirementsPerTurn(
            StateForConditionals.IgnoreConditionals).containsKey(name) }
        if (buildingsThatConsumeThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Buildings that consume this resource}:")
            buildingsThatConsumeThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        val unitsThatConsumeThis = ruleset.units.values.filter { it.getResourceRequirementsPerTurn(
            StateForConditionals.IgnoreConditionals).containsKey(name) }
        if (unitsThatConsumeThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Units that consume this resource}: ")
            unitsThatConsumeThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        val buildingsRequiringThis =  ruleset.buildings.values.filter {
            it.requiredNearbyImprovedResources?.contains(name) == true
        }
        if (buildingsRequiringThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Buildings that require this resource improved near the city}: ")
            buildingsRequiringThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        textList += Belief.getCivilopediaTextMatching(name, ruleset)

        return textList
    }

    fun isImprovedBy(improvementName: String): Boolean {
        return getImprovements().contains(improvementName)
    }

    /** @return Of all the potential improvements in [getImprovements], the first this [civ] can actually build, if any. */
    fun getImprovingImprovement(tile: Tile, civ: Civilization): String? {
        return getImprovements().firstOrNull {
            tile.improvementFunctions.canBuildImprovement(civ.gameInfo.ruleset.tileImprovements[it]!!, civ)
        }
    }

    fun matchesFilter(filter: String, state: StateForConditionals? = null): Boolean =
        MultiFilter.multiFilter(filter, {
            matchesSingleFilter(filter) ||
                state != null && hasUnique(filter, state) ||
                state == null && hasTagUnique(filter)
        })

    fun matchesSingleFilter(filter: String) = when (filter) {
        name -> true
        "any" -> true
        resourceType.name -> true
        else -> improvementStats?.any { filter == it.key.name } == true
    }

    fun generatesNaturallyOn(tile: Tile): Boolean {
        if (tile.lastTerrain.name !in terrainsCanBeFoundOn) return false
        val stateForConditionals = StateForConditionals(tile = tile)
        if (hasUnique(UniqueType.NoNaturalGeneration, stateForConditionals)) return false
        if (tile.allTerrains.any { it.hasUnique(UniqueType.BlocksResources, stateForConditionals) }) return false

        if (tile.temperature!=null && tile.humidity!=null) // Only works when in map generation
            for (unique in getMatchingUniques(UniqueType.TileGenerationConditions, stateForConditionals)){
                if (tile.temperature!! !in unique.params[0].toDouble() .. unique.params[1].toDouble()) return false
                if (tile.humidity!! !in unique.params[2].toDouble() .. unique.params[3].toDouble()) return false
            }

        return true
    }

    class DepositAmount {
        var sparse: Int = 1
        var default: Int = 2
        var abundant: Int = 3
    }
}
