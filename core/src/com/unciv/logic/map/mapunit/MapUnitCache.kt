package com.unciv.logic.map.mapunit

import com.unciv.Constants
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType

// Note: Single use in MapUnit and it's @Transient there, so no need for that here
class MapUnitCache(private val mapUnit: MapUnit) {
    // These are for performance improvements to getMovementCostBetweenAdjacentTiles,
    // a major component of getDistanceToTilesWithinTurn,
    // which in turn is a component of getShortestPath and canReach
    var ignoresTerrainCost = false
        private set

    var ignoresZoneOfControl = false
        private set

    var allTilesCosts1 = false
        private set

    var canMoveOnWater = false
        private set

    var canPassThroughImpassableTiles = false
        private set

    var roughTerrainPenalty = false
        private set

    /** `true` if movement 0 _or_ has CannotMove unique */
    var cannotMove = false
        private set

    /** If set causes an early exit in getMovementCostBetweenAdjacentTiles
     *  - means no double movement uniques, roughTerrainPenalty or ignoreHillMovementCost */
    var noTerrainMovementUniques = false
        private set

    /** If set causes a second early exit in getMovementCostBetweenAdjacentTiles */
    var noBaseTerrainOrHillDoubleMovementUniques = false
        private set

    /** If set skips tile.matchesFilter tests for double movement in getMovementCostBetweenAdjacentTiles */
    var noFilteredDoubleMovementUniques = false
        private set

    /** Used for getMovementCostBetweenAdjacentTiles only, based on order of testing */
    enum class DoubleMovementTerrainTarget { Feature, Base, Hill, Filter }
    class DoubleMovement(val terrainTarget: DoubleMovementTerrainTarget, val unique: Unique)
    /** Mod-friendly cache of double-movement terrains */
    val doubleMovementInTerrain = HashMap<String, DoubleMovement>()

    var canEnterIceTiles = false
    var cannotEmbark = false
    var cannotEnterOceanTiles = false
    var canEnterForeignTerrain: Boolean = false
    var canEnterCityStates: Boolean = false
    var costToDisembark: Float? = null
    var costToEmbark: Float? = null
    var paradropRange = 0

    var hasUniqueToBuildImprovements = false    // not canBuildImprovements to avoid confusion
    var hasUniqueToCreateWaterImprovements = false

    var hasStrengthBonusInRadiusUnique = false
    var hasCitadelPlacementUnique = false
    
    var state = StateForConditionals.EmptyState

    fun updateUniques() {

        state = StateForConditionals(mapUnit)
        allTilesCosts1 = mapUnit.hasUnique(UniqueType.AllTilesCost1Move)
        canPassThroughImpassableTiles = mapUnit.hasUnique(UniqueType.CanPassImpassable)
        ignoresTerrainCost = mapUnit.hasUnique(UniqueType.IgnoresTerrainCost)
        ignoresZoneOfControl = mapUnit.hasUnique(UniqueType.IgnoresZOC)
        roughTerrainPenalty = mapUnit.hasUnique(UniqueType.RoughTerrainPenalty)
        cannotMove = mapUnit.hasUnique(UniqueType.CannotMove) || mapUnit.baseUnit.movement == 0
        canMoveOnWater = mapUnit.hasUnique(UniqueType.CanMoveOnWater)

        doubleMovementInTerrain.clear()
        for (unique in mapUnit.getMatchingUniques(UniqueType.DoubleMovementOnTerrain,
                stateForConditionals = StateForConditionals.IgnoreConditionals, true)) {
            val param = unique.params[0]
            val terrain = mapUnit.civ.gameInfo.ruleset.terrains[param]
            doubleMovementInTerrain[param] = DoubleMovement(unique = unique,
                terrainTarget =  when {
                    terrain == null -> DoubleMovementTerrainTarget.Filter
                    terrain.name == Constants.hill -> DoubleMovementTerrainTarget.Hill
                    terrain.type == TerrainType.TerrainFeature -> DoubleMovementTerrainTarget.Feature
                    terrain.type.isBaseTerrain -> DoubleMovementTerrainTarget.Base
                    else -> DoubleMovementTerrainTarget.Filter
                })
        }
        // Init shortcut flags
        noTerrainMovementUniques = doubleMovementInTerrain.isEmpty() &&
                !roughTerrainPenalty && !mapUnit.civ.nation.ignoreHillMovementCost
        noBaseTerrainOrHillDoubleMovementUniques = doubleMovementInTerrain
            .none { it.value.terrainTarget != DoubleMovementTerrainTarget.Feature }
        noFilteredDoubleMovementUniques = doubleMovementInTerrain
            .none { it.value.terrainTarget == DoubleMovementTerrainTarget.Filter }
        costToDisembark = (mapUnit.getMatchingUniques(UniqueType.ReducedDisembarkCost, checkCivInfoUniques = true))
            .minOfOrNull { it.params[0].toFloat() }
        costToEmbark = mapUnit.getMatchingUniques(UniqueType.ReducedEmbarkCost, checkCivInfoUniques = true)
            .minOfOrNull { it.params[0].toFloat() }

        //todo: consider parameterizing [terrainFilter] in some of the following:
        canEnterIceTiles = mapUnit.hasUnique(UniqueType.CanEnterIceTiles)
        cannotEmbark = mapUnit.hasUnique(UniqueType.CannotEmbark)
        cannotEnterOceanTiles = mapUnit.hasUnique(UniqueType.CannotEnterOcean, state)

        hasUniqueToBuildImprovements = mapUnit.hasUnique(UniqueType.BuildImprovements)
        hasUniqueToCreateWaterImprovements = mapUnit.hasUnique(UniqueType.CreateWaterImprovements)

        canEnterForeignTerrain = mapUnit.hasUnique(UniqueType.CanEnterForeignTiles)
                || mapUnit.hasUnique(UniqueType.CanEnterForeignTilesButLosesReligiousStrength)

        canEnterCityStates = mapUnit.hasUnique(UniqueType.CanTradeWithCityStateForGoldAndInfluence)

        hasStrengthBonusInRadiusUnique = mapUnit.hasUnique(UniqueType.StrengthBonusInRadius, StateForConditionals.IgnoreConditionals)
        hasCitadelPlacementUnique = mapUnit.getMatchingUniques(UniqueType.ConstructImprovementInstantly)
            .mapNotNull { mapUnit.civ.gameInfo.ruleset.tileImprovements[it.params[0]] }
            .any { it.hasUnique(UniqueType.OneTimeTakeOverTilesInRadius) }
    }

    companion object {
        val UnitMovementUniques = setOf(
            UniqueType.AllTilesCost1Move,
            UniqueType.CanPassImpassable,
            UniqueType.IgnoresTerrainCost,
            UniqueType.IgnoresZOC,
            UniqueType.RoughTerrainPenalty,
            UniqueType.CannotMove,
            UniqueType.CanMoveOnWater,
            UniqueType.DoubleMovementOnTerrain,
            UniqueType.ReducedDisembarkCost,
            UniqueType.ReducedEmbarkCost,
            UniqueType.CanEnterIceTiles,
            UniqueType.CanEnterForeignTiles,
            UniqueType.CanEnterForeignTilesButLosesReligiousStrength,
            // Special - applied in Nation and not here, wshould be moved to mapunitcache as well
            UniqueType.ForestsAndJunglesAreRoads,
            UniqueType.IgnoreHillMovementCost,
            // Movement algorithm avoids damage on route, meaning terrain damage requires caching
            UniqueType.DamagesContainingUnits,
            UniqueType.LandUnitEmbarkation,
            UniqueType.LandUnitsCrossTerrainAfterUnitGained,
            UniqueType.EnemyUnitsSpendExtraMovement
            )
    }
}
