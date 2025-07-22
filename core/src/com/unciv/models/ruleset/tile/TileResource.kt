package com.unciv.models.ruleset.tile

import com.unciv.logic.MultiFilter
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetStatsObject
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.GameResource
import com.unciv.models.stats.Stats
import com.unciv.ui.objectdescriptions.ResourceDescriptions
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly

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

    val isCityWide by lazy { hasUnique(UniqueType.CityResource, GameContext.IgnoreConditionals) }

    val isStockpiled by lazy { hasUnique(UniqueType.Stockpiled, GameContext.IgnoreConditionals) }

    /** Cache collecting [improvement], [improvedBy] and [UniqueType.ImprovesResources] uniques on the improvements themselves. */
    @Cache private var allImprovements: Set<String>? = null
    private var ruleset: Ruleset? = null

    /** Collects which improvements "unlock" this resource, caches and returns the Set.
     *  - The cache is cleared after ruleset load and combine, via [setTransients].
     *  @see improvement
     *  @see improvedBy
     *  @see UniqueType.ImprovesResources
     */
    @Readonly
    fun getImprovements(): Set<String> {
        if (allImprovements != null) return allImprovements!!
        
        val ruleset = this.ruleset
            ?: throw IllegalStateException("No ruleset on TileResource when initializing improvements")
        
        val allImprovementsLocal = mutableSetOf<String>()
        
        if (improvement != null) allImprovementsLocal += improvement!!
        allImprovementsLocal.addAll(improvedBy)
        for (improvement in ruleset.tileImprovements.values) {
            // Explicitly stated by the improvement, or this is a replacement improvement
            if (improvement.getMatchingUniques(UniqueType.ImprovesResources).any { matchesFilter(it.params[0]) }
                || allImprovementsLocal.contains(improvement.replaces)) {
                allImprovementsLocal += improvement.name
            }
        }
        
        
        allImprovements = allImprovementsLocal
        return allImprovementsLocal
    }

    /** Clears the cache for [getImprovements] and saves the Ruleset the cache update will need.
     *  - Doesn't evaluate the cache immediately, that would break tests as it trips the uniqueObjects lazy and tests manipulate uniques after ruleset load.
     *  - called from [Ruleset.updateResourceTransients]
     */
    fun setTransients(ruleset: Ruleset) {
        allImprovements = null
        this.ruleset = ruleset
    }

    override fun getUniqueTarget() = UniqueTarget.Resource

    override fun makeLink() = "Resource/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset) =
        ResourceDescriptions.getCivilopediaTextLines(this, ruleset)

    @Readonly
    fun isImprovedBy(improvementName: String): Boolean {
        return getImprovements().contains(improvementName)
    }

    /** @return Of all the potential improvements in [getImprovements], the first this civ can actually build, if any. */
    @Readonly
    fun getImprovingImprovement(tile: Tile, gameContext: GameContext): String? {
        if (gameContext.civInfo != null) {
            val civ: Civilization = gameContext.civInfo
            return getImprovements().firstOrNull {
                tile.improvementFunctions.canBuildImprovement(civ.gameInfo.ruleset.tileImprovements[it]!!, gameContext)
            }
        }
        return null
    }

    @Readonly
    fun matchesFilter(filter: String, state: GameContext? = null): Boolean =
        MultiFilter.multiFilter(filter, {
            matchesSingleFilter(filter) ||
                state != null && hasTagUnique(filter, state) ||
                state == null && hasTagUnique(filter)
        })

    @Readonly
    fun matchesSingleFilter(filter: String) = when (filter) {
        name -> true
        "any" -> true
        "all" -> true
        resourceType.name -> true
        else -> improvementStats?.any { filter == it.key.name } == true
    }

    @Readonly
    fun generatesNaturallyOn(tile: Tile): Boolean {
        if (tile.lastTerrain.name !in terrainsCanBeFoundOn) return false
        val gameContext = GameContext(tile = tile)
        if (hasUnique(UniqueType.NoNaturalGeneration, gameContext)) return false
        if (tile.allTerrains.any { it.hasUnique(UniqueType.BlocksResources, gameContext) }) return false

        if (tile.temperature!=null && tile.humidity!=null) // Only works when in map generation
            for (unique in getMatchingUniques(UniqueType.TileGenerationConditions, gameContext)){
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
