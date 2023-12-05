package com.unciv.logic.map

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.utils.Log

object MapPathing {

    /**
     * We prefer the worker to prioritize paths connected by existing roads. Otherwise, we set every tile to have
     * equal value since building a road on any of them makes the original movement cost irrelevant.
     */
    private fun roadPreferredMovementCost(unit: MapUnit, from: Tile, to: Tile): Float{
        // hasConnection accounts for civs that treat jungle/forest as roads
        val areConnectedByRoad = from.hasConnection(unit.civ) && to.hasConnection(unit.civ)
        if (areConnectedByRoad) // Ignore road over river penalties.
            return unit.civ.tech.movementSpeedOnRoads

        if (from.getUnpillagedRoad() == RoadStatus.Railroad && to.getUnpillagedRoad() == RoadStatus.Railroad)
            return RoadStatus.Railroad.movement

        return 1f
    }

    /**
     * Calculates the path for a road construction between two tiles.
     *
     * This function uses the A* search algorithm to find an optimal path for road construction between two specified tiles on a game map.
     *
     * @param unit The unit that will construct the road.
     * @param startTile The starting tile of the path.
     * @param endTile The destination tile of the path.
     * @return A sequence of tiles representing the path from startTile to endTile, or null if no valid path is found.
     */
    fun getRoadPath(unit: MapUnit, startTile: Tile, endTile: Tile): List<Tile>?{
        val astar = AStar(startTile,
            {tile: Tile -> tile.isLand && !tile.isImpassible() && unit.civ.hasExplored(tile) && (tile.getOwner() == unit.civ || tile.getOwner() == null)},
            { from: Tile, to: Tile -> roadPreferredMovementCost(unit, from, to) },
            { _: Tile, _: Tile -> 0f}) // Heuristic left empty. If the search ever starts to hang the game, start here -- or add a maxSize.
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


