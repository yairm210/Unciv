package com.unciv.logic.map.action

import com.unciv.logic.map.BFS
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo

class BuildLongRoadAction(
        mapUnit: MapUnit = MapUnit(),
        val target: TileInfo = TileInfo()
) : MapUnitAction(mapUnit, "Build Long Road") {

    override fun shouldStopOnEnemyInSight(): Boolean = true

    override fun isAvailable(): Boolean
            = unit.hasUnique("Can build improvements on tiles")
            && getPath(target).isNotEmpty()

    override fun doPreTurnAction() {

        // we're working!
        if (unit.currentTile.improvementInProgress != null)
            return
        else if (startWorking()) {
            return
        }

        // we reached our target? And road is finished?
        if (unit.currentTile.position == target.position
                && isRoadFinished(unit.currentTile)) {
            unit.action = null
            return
        }

        // move one step forward - and start building
        if (stepForward(target)) {
            startWorking()
        } else if (unit.currentMovement > 1f) {
            unit.action = null
            return
        }

    }

    // because the unit is building a road, we need to use a shortest path that is
    // independent of movement costs, but should respect impassable terrain like water and enemy territory
    private fun stepForward(destination: TileInfo): Boolean {
        var success = false
        for (step in getPath(destination).drop(1)) {
            if (unit.currentMovement > 0f && unit.canMoveTo(step)) {
                unit.moveToTile(step)
                success = true
                // if there is a road already, take multiple steps, otherwise break
                if (!isRoadFinished(step)) {
                    break
                }
            } else break
        }
        return success
    }

    private fun isRoadFinished(tile: TileInfo): Boolean {
        return tile.roadStatus == unit.civInfo.tech.getBestRoadAvailable()
    }

    private fun getPath(destination: TileInfo): List<TileInfo> {
        // BFS is not very efficient
        return BFS(unit.currentTile) { isRoadableTile(it) }
                .stepUntilDestination(destination)
                .getPathTo(destination).reversed()
    }

    private fun isRoadableTile(it: TileInfo) = it.isLand && unit.canPassThrough(it)

    private fun startWorking(): Boolean {
        val tile = unit.currentTile
        if (unit.currentMovement > 0 && isRoadableTile(tile)) {
            val roadToBuild = unit.civInfo.tech.getBestRoadAvailable()
            roadToBuild.improvement()?.let { improvement ->
                if (tile.roadStatus != roadToBuild && tile.improvementInProgress != improvement.name) {
                    tile.startWorkingOnImprovement(improvement, unit.civInfo)
                    return true
                }
            }
        }
        return false
    }


}