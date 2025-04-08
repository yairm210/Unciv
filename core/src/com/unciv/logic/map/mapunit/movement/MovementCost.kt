package com.unciv.logic.map.mapunit.movement

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.MapUnitCache
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType

object MovementCost {

    fun getMovementCostBetweenAdjacentTilesEscort(
        unit: MapUnit,
        from: Tile,
        to: Tile,
        considerZoneOfControl: Boolean = true,
        includeEscortUnit: Boolean = true,
    ): Float {
        val movementCost = if (includeEscortUnit && unit.isEscorting()) {
            maxOf(getMovementCostBetweenAdjacentTiles(unit, from, to, considerZoneOfControl),
                getMovementCostBetweenAdjacentTiles(unit.getOtherEscortUnit()!!, from, to, considerZoneOfControl))
        } else {
            getMovementCostBetweenAdjacentTiles(unit, from, to, considerZoneOfControl)
        }
        if (movementCost < 0) throw Exception("Got a negative movement cost?!")
        return movementCost
    }

    // This function is called ALL THE TIME and should be as time-optimal as possible!
    /**
     * Does not include escort unit
     * @return The cost of movment for the unit between two tiles
     */
    fun getMovementCostBetweenAdjacentTiles(
        unit: MapUnit,
        from: Tile,
        to: Tile,
        considerZoneOfControl: Boolean = true,
    ): Float {
        val civ = unit.civ

        if (unit.cache.cannotMove) return 100f

        if (from.isLand != to.isLand && unit.baseUnit.isLandUnit && !unit.cache.canMoveOnWater)
            return if (from.isWater && to.isLand) unit.cache.costToDisembark ?: 100f
            else unit.cache.costToEmbark ?: 100f

        // If the movement is affected by a Zone of Control, all movement points are expended
        if (considerZoneOfControl && isMovementAffectedByZoneOfControl(unit, from, to))
            return 100f

        // land units will still spend all movement points to embark even with this unique
        if (unit.cache.allTilesCosts1)
            return 1f

        val toOwner = to.getOwner()

        val extraCost = if (
            toOwner != null &&
            toOwner.hasActiveEnemyMovementPenalty &&
            civ.isAtWarWith(toOwner)
        ) getEnemyMovementPenalty(toOwner, unit) else 0f

        if (from.getUnpillagedRoad() == RoadStatus.Railroad && to.getUnpillagedRoad() == RoadStatus.Railroad)
            return RoadStatus.Railroad.movement + extraCost

        // Each of these two function calls `hasUnique(UniqueType.CityStateTerritoryAlwaysFriendly)`
        // when entering territory of a city state
        val areConnectedByRoad = from.hasConnection(civ) && to.hasConnection(civ)

        // You might think "wait doesn't isAdjacentToRiver() call isConnectedByRiver() anyway, why have those checks?"
        // The answer is that the isAdjacentToRiver values are CACHED per tile, but the isConnectedByRiver are not - this is an efficiency optimization
        val areConnectedByRiver =
            from.isAdjacentToRiver() && to.isAdjacentToRiver() && from.isConnectedByRiver(to)

        if (areConnectedByRoad && (!areConnectedByRiver || civ.tech.roadsConnectAcrossRivers))
            return unit.civ.tech.movementSpeedOnRoads + extraCost

        if (unit.cache.ignoresTerrainCost) return 1f + extraCost
        if (areConnectedByRiver) return 100f  // Rivers take the entire turn to cross

        // Cities reduce terrain cost to 1
        val terrainCost = if (to.isCityCenter()) 1f
            else to.lastTerrain.movementCost.toFloat()

        if (unit.cache.noTerrainMovementUniques)
            return terrainCost + extraCost

        val stateForConditionals = StateForConditionals(unit.civ, unit = unit, tile = to)

        if (to.terrainFeatures.any { hasDoubleMovement(unit, it, MapUnitCache.DoubleMovementTerrainTarget.Feature, stateForConditionals) })
            return terrainCost * 0.5f + extraCost

        if (unit.cache.roughTerrainPenalty && to.isRoughTerrain())
            return 100f // units that have to spend all movement in rough terrain, have to spend all movement in rough terrain
        // Placement of this 'if' based on testing, see #4232

        if (civ.nation.ignoreHillMovementCost && to.isHill())
            return 1f + extraCost // usually hills take 2 movements, so here it is 1

        if (unit.cache.noBaseTerrainOrHillDoubleMovementUniques)
            return terrainCost + extraCost

        if (hasDoubleMovement(unit, to.baseTerrain, MapUnitCache.DoubleMovementTerrainTarget.Base, stateForConditionals))
            return terrainCost * 0.5f + extraCost
        if (hasDoubleMovement(unit, Constants.hill, MapUnitCache.DoubleMovementTerrainTarget.Hill, stateForConditionals)
            && to.isHill())
            return terrainCost * 0.5f + extraCost

        if (unit.cache.noFilteredDoubleMovementUniques)
            return terrainCost + extraCost
        if (unit.cache.doubleMovementInTerrain.any {
                hasDoubleMovement(it.value, MapUnitCache.DoubleMovementTerrainTarget.Filter, stateForConditionals)
                    && to.matchesFilter(it.key)
            })
            return terrainCost * 0.5f + extraCost

        return terrainCost + extraCost // no road or other movement cost reduction
    }


    private fun hasDoubleMovement(
        doubleMovement: MapUnitCache.DoubleMovement,
        target: MapUnitCache.DoubleMovementTerrainTarget,
        stateForConditionals: StateForConditionals
    ): Boolean {
        if (doubleMovement.terrainTarget != target) return false
        if (doubleMovement.unique.modifiers.isNotEmpty()
            && !doubleMovement.unique.conditionalsApply(stateForConditionals)) return false

        return true
    }

    private fun hasDoubleMovement(
        unit: MapUnit,
        terrainName: String,
        target: MapUnitCache.DoubleMovementTerrainTarget,
        stateForConditionals: StateForConditionals
    ): Boolean {
        val doubleMovement = unit.cache.doubleMovementInTerrain[terrainName] ?: return false
        return hasDoubleMovement(doubleMovement, target, stateForConditionals)
    }

    private fun getEnemyMovementPenalty(civInfo: Civilization, enemyUnit: MapUnit): Float {
        if (civInfo.enemyMovementPenaltyUniques != null && civInfo.enemyMovementPenaltyUniques!!.any()) {
            return civInfo.enemyMovementPenaltyUniques!!.sumOf {
                if (it.type!! == UniqueType.EnemyUnitsSpendExtraMovement
                    && enemyUnit.matchesFilter(it.params[0]))
                    it.params[1].toInt()
                else 0
            }.toFloat()
        }
        return 0f // should not reach this point
    }


    /** Returns whether the movement between the adjacent tiles [from] and [to] is affected by Zone of Control */
    private fun isMovementAffectedByZoneOfControl(unit: MapUnit, from: Tile, to: Tile): Boolean {
        // Sources:
        // - https://civilization.fandom.com/wiki/Zone_of_control_(Civ5)
        // - https://forums.civfanatics.com/resources/understanding-the-zone-of-control-vanilla.25582/
        //
        // Enemy military units exert a Zone of Control over the tiles surrounding them. Moving from
        // one tile in the ZoC of an enemy unit to another tile in the same unit's ZoC expends all
        // movement points. Land units only exert a ZoC against land units. Sea units exert a ZoC
        // against both land and sea units. Cities exert a ZoC as well, and it also affects both
        // land and sea units. Embarked land units do not exert a ZoC. Finally, units that can move
        // after attacking are not affected by zone of control if the movement is caused by killing
        // a unit. This last case is handled in the movement-after-attacking code instead of here.

        // We only need to check the two shared neighbors of [from] and [to]: the way of getting
        // these two tiles can perhaps be optimized. Using a hex-math-based "commonAdjacentTiles"
        // function is surprisingly less efficient than the current neighbor-intersection approach.
        // See #4085 for more details.
        if (!anyTilesExertingZoneOfControl(unit, from, to))
            return false

        // Even though this is a very fast check, we perform it last. This is because very few units
        // ignore zone of control, so the previous check has a much higher chance of yielding an
        // early "false". If this function is going to return "true", the order doesn't matter
        // anyway.
        if (unit.cache.ignoresZoneOfControl)
            return false
        return true
    }

    private fun anyTilesExertingZoneOfControl(unit: MapUnit, from: Tile, to:Tile): Boolean {
        for (neighbor in from.neighbors) {
            if (neighbor.isCityCenter()) {
                if (neighbor.aerialDistanceTo(to) == 1
                    && unit.civ.isAtWarWith(neighbor.getOwner()!!))
                        return true
            } else if (neighbor.militaryUnit != null) {
                if (neighbor.aerialDistanceTo(to) == 1
                    && (neighbor.militaryUnit!!.type.isWaterUnit() || (unit.type.isLandUnit() && !neighbor.militaryUnit!!.isEmbarked()))
                    && unit.civ.isAtWarWith(neighbor.militaryUnit!!.civ))
                        return true
            }
        }
        return false
    }
}
