package com.unciv.logic.map

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.utils.Log

//TODO: Eventually, all path generation in the game should be moved into here.
object MapPathing {

    /**
     * We prefer the worker to prioritize paths connected by existing roads. If a tile has a road, but the civ has the ability
     * to upgrade it to a railroad, we consider it to be a railroad for pathing since it will be upgraded.
     * Otherwise, we set every tile to have equal value since building a road on any of them makes the original movement cost irrelevant.
     */
    private fun roadPreferredMovementCost(unit: MapUnit, from: Tile, to: Tile): Float{
        // hasRoadConnection accounts for civs that treat jungle/forest as roads
        // Ignore road over river penalties.
        if ((to.hasRoadConnection(unit.civ, false) || to.hasRailroadConnection(false)))
            return .5f

        return 1f
    }

    fun isValidRoadPathTile(unit: MapUnit, tile: Tile): Boolean {
        val roadImprovement = tile.ruleset.roadImprovement ?: return false
        val railRoadImprovement = tile.ruleset.railroadImprovement ?: return false
        return tile.isLand
            && !tile.isImpassible()
            && unit.civ.hasExplored(tile)
            && tile.canCivPassThrough(unit.civ)
            && (tile.hasRoadConnection(unit.civ, false) 
                || tile.hasRailroadConnection(false) 
                || tile.improvementFunctions.canBuildImprovement(roadImprovement, unit.civ))
                || tile.improvementFunctions.canBuildImprovement(railRoadImprovement, unit.civ)
    }

    /**
     * Calculates the path for a road construction between two tiles.
     *
     * This function uses the A* search algorithm to find an optimal path for road construction between two specified tiles.
     *
     * @param unit The unit that will construct the road.
     * @param startTile The starting tile of the path.
     * @param endTile The destination tile of the path.
     * @return A sequence of tiles representing the path from startTile to endTile, or null if no valid path is found.
     */
    fun getRoadPath(unit: MapUnit, startTile: Tile, endTile: Tile): List<Tile>?{
        return getPath(unit,
            startTile,
            endTile,
            ::isValidRoadPathTile,
            ::roadPreferredMovementCost,
            {_, _, _ -> 0f}
            )
    }

    /**
     * Calculates the path between two tiles.
     *
     * This function uses the A* search algorithm to find an optimal path two specified tiles on a game map.
     *
     * @param unit The unit for which the path is being calculated.
     * @param startTile The tile from which the pathfinding begins.
     * @param endTile The destination tile for the pathfinding.
     * @param predicate A function that takes a MapUnit and a Tile, returning a Boolean. This function is used to determine whether a tile can be traversed by the unit.
     * @param cost A function that calculates the cost of moving from one tile to another.
     * It takes a MapUnit, a 'from' Tile, and a 'to' Tile, returning a Float value representing the cost.
     * @param heuristic A function that estimates the cost from a given tile to the end tile.
     * It takes a MapUnit, a 'from' Tile, and a 'to' Tile, returning a Float value representing the heuristic cost estimate.
     * @return A list of tiles representing the path from the startTile to the endTile. Returns null if no valid path is found.
     */
    private fun getPath(unit: MapUnit,
                startTile: Tile,
                endTile: Tile,
                predicate: (MapUnit, Tile) -> Boolean,
                cost: (MapUnit, Tile, Tile) -> Float,
                heuristic: (MapUnit, Tile, Tile) -> Float): List<Tile>? {
        val astar = AStar(startTile,
            { tile -> predicate(unit, tile) },
            { from, to -> cost(unit, from, to)},
            { from, to -> heuristic(unit, from, to) })
        while (true) {
            if (astar.hasEnded()) {
                // We failed to find a path
                Log.debug("getRoadPath failed at AStar search size ${astar.size()}")
                return null
            }
            if (!astar.hasReachedTile(endTile)) {
                astar.nextStep()
                continue
            }
            // Found a path.
            return astar.getPathTo(endTile)
                .toList()
                .reversed()
        }
    }

}
