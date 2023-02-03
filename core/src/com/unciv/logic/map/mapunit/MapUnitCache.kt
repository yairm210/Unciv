package com.unciv.logic.map.mapunit

import com.unciv.Constants
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType

class MapUnitCache(val mapUnit: MapUnit) {
    // These are for performance improvements to getMovementCostBetweenAdjacentTiles,
    // a major component of getDistanceToTilesWithinTurn,
    // which in turn is a component of getShortestPath and canReach
    @Transient
    var ignoresTerrainCost = false
        private set

    @Transient
    var ignoresZoneOfControl = false
        private set

    @Transient
    var allTilesCosts1 = false
        private set

    @Transient
    var canPassThroughImpassableTiles = false
        private set

    @Transient
    var roughTerrainPenalty = false
        private set

    /** If set causes an early exit in getMovementCostBetweenAdjacentTiles
     *  - means no double movement uniques, roughTerrainPenalty or ignoreHillMovementCost */
    @Transient
    var noTerrainMovementUniques = false
        private set

    /** If set causes a second early exit in getMovementCostBetweenAdjacentTiles */
    @Transient
    var noBaseTerrainOrHillDoubleMovementUniques = false
        private set

    /** If set skips tile.matchesFilter tests for double movement in getMovementCostBetweenAdjacentTiles */
    @Transient
    var noFilteredDoubleMovementUniques = false
        private set

    /** Used for getMovementCostBetweenAdjacentTiles only, based on order of testing */
    enum class DoubleMovementTerrainTarget { Feature, Base, Hill, Filter }
    /** Mod-friendly cache of double-movement terrains */
    @Transient
    val doubleMovementInTerrain = HashMap<String, DoubleMovementTerrainTarget>()

    @Transient
    var canEnterIceTiles = false

    @Transient
    var cannotEnterOceanTiles = false

    @Transient
    var canEnterForeignTerrain: Boolean = false

    @Transient
    var costToDisembark: Float? = null

    @Transient
    var costToEmbark: Float? = null

    @Transient
    var paradropRange = 0

    @Transient
    var hasUniqueToBuildImprovements = false    // not canBuildImprovements to avoid confusion

    @Transient
    var hasStrengthBonusInRadiusUnique = false
    @Transient
    var hasCitadelPlacementUnique = false

    fun updateUniques(){

        allTilesCosts1 = mapUnit.hasUnique(UniqueType.AllTilesCost1Move)
        canPassThroughImpassableTiles = mapUnit.hasUnique(UniqueType.CanPassImpassable)
        ignoresTerrainCost = mapUnit.hasUnique(UniqueType.IgnoresTerrainCost)
        ignoresZoneOfControl = mapUnit.hasUnique(UniqueType.IgnoresZOC)
        roughTerrainPenalty = mapUnit.hasUnique(UniqueType.RoughTerrainPenalty)

        doubleMovementInTerrain.clear()
        for (unique in mapUnit.getMatchingUniques(UniqueType.DoubleMovementOnTerrain)) {
            val param = unique.params[0]
            val terrain = mapUnit.civ.gameInfo.ruleSet.terrains[param]
            doubleMovementInTerrain[param] = when {
                terrain == null -> DoubleMovementTerrainTarget.Filter
                terrain.name == Constants.hill -> DoubleMovementTerrainTarget.Hill
                terrain.type == TerrainType.TerrainFeature -> DoubleMovementTerrainTarget.Feature
                terrain.type.isBaseTerrain -> DoubleMovementTerrainTarget.Base
                else -> DoubleMovementTerrainTarget.Filter
            }
        }
        // Init shortcut flags
        noTerrainMovementUniques = doubleMovementInTerrain.isEmpty() &&
                !roughTerrainPenalty && !mapUnit.civ.nation.ignoreHillMovementCost
        noBaseTerrainOrHillDoubleMovementUniques = doubleMovementInTerrain
            .none { it.value != DoubleMovementTerrainTarget.Feature }
        noFilteredDoubleMovementUniques = doubleMovementInTerrain
            .none { it.value == DoubleMovementTerrainTarget.Filter }
        costToDisembark = (mapUnit.getMatchingUniques(UniqueType.ReducedDisembarkCost, checkCivInfoUniques = true))
            .minOfOrNull { it.params[0].toFloat() }
        costToEmbark = mapUnit.getMatchingUniques(UniqueType.ReducedEmbarkCost, checkCivInfoUniques = true)
            .minOfOrNull { it.params[0].toFloat() }

        //todo: consider parameterizing [terrainFilter] in some of the following:
        canEnterIceTiles = mapUnit.hasUnique(UniqueType.CanEnterIceTiles)
        cannotEnterOceanTiles = mapUnit.hasUnique(
            UniqueType.CannotEnterOcean,
            StateForConditionals(civInfo = mapUnit.civ, unit = mapUnit)
        )

        hasUniqueToBuildImprovements = mapUnit.hasUnique(UniqueType.BuildImprovements)
        canEnterForeignTerrain = mapUnit.hasUnique(UniqueType.CanEnterForeignTiles)
                || mapUnit.hasUnique(UniqueType.CanEnterForeignTilesButLosesReligiousStrength)

        hasStrengthBonusInRadiusUnique = mapUnit.hasUnique(UniqueType.StrengthBonusInRadius)
        hasCitadelPlacementUnique = mapUnit.getMatchingUniques(UniqueType.ConstructImprovementConsumingUnit)
            .mapNotNull { mapUnit.civ.gameInfo.ruleSet.tileImprovements[it.params[0]] }
            .any { it.hasUnique(UniqueType.TakesOverAdjacentTiles) }
    }
}
