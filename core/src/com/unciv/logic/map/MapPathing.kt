package com.unciv.logic.map

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.utils.Log

//TODO: Eventually, all path generation in the game should be moved into here.
object MapPathing {

    /**
     * Calculates the movement cost for a unit traveling from one tile to another, prioritizing paths with existing roads or railroads.
     * If both tiles have a road connection, the cost is determined by the civilization's technology: if the civilization can build railroads,
     * roads are treated as railroads for pathing purposes due to potential upgrades. If no railroad technology is available, the standard
     * movement speed on roads is used. If both tiles are connected by a railroad, railroad movement cost is used.
     * In cases where neither roads nor railroads connect the tiles, a default movement cost of 1f is applied.
     * Note: The function accounts for civilizations that treat jungle/forest as roads and ignores road over river penalties.
     *
     * @param unit The unit for which the movement cost is being calculated.
     * @param from The starting tile of the unit.
     * @param to The destination tile of the unit.
     * @return The movement cost for the unit to move from the starting tile to the destination tile.
     */
    private fun roadPreferredMovementCost(unit: MapUnit, from: Tile, to: Tile): Float{
        // hasRoadConnection accounts for civs that treat jungle/forest as roads
        // Ignore road over river penalties.
        val isConnectedByRoad = from.hasRoadConnection(unit.civ, mustBeUnpillaged = false) && to.hasRoadConnection(unit.civ, mustBeUnpillaged = false)
        if (isConnectedByRoad){
            // If the civ has railroad technology, consider roads as railroads since they will be upgraded
            return if (unit.civ.tech.getBestRoadAvailable() == RoadStatus.Railroad){
                RoadStatus.Railroad.movement
            }else{
                unit.civ.tech.movementSpeedOnRoads
            }
        }

        val isConnectedByRailroad = from.hasRailroadConnection(mustBeUnpillaged = false) && to.hasRailroadConnection(mustBeUnpillaged = false)
        if (isConnectedByRailroad)
            return RoadStatus.Railroad.movement

        return 1f
    }

    /**
     * Determines if a given tile is a valid option for a road path for a specific unit.
     *
     * @param unit The unit for which the road path validity is being assessed.
     * @param tile The tile to be evaluated for its suitability as part of the road path.
     * @return True if the tile is a valid part of a road path for the given unit, False otherwise.
     */
    fun isValidRoadPathTile(unit: MapUnit, tile: Tile): Boolean {
        return tile.isLand
            && !tile.isImpassible()
            && unit.civ.hasExplored(tile)
            && tile.canCivPassThrough(unit.civ)
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


