package com.unciv.logic.automation.unit

import com.unciv.view.MapUnitView
import com.unciv.view.TileView
import com.unciv.view.hasMovement
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.utils.Log
import com.unciv.utils.debug
import yairm210.purity.annotations.Readonly


/** Responsible for automating the "build road to" action
 *  This is *pretty bad code* overall and needs to be cleaned up */
class RoadToAutomation(val civInfo: Civilization) {

    private val actualBestRoadAvailable: RoadStatus = civInfo.tech.getBestRoadAvailable()

    /**
     * Automate the process of connecting a road between two points.
     * Current thoughts:
     * Will be a special case of MapUnit.automated property
     * Unit has new attributes startTile endTile
     * - We will progress towards the end path sequentially, taking absolute least distance w/o regard for movement cost
     * - Cancel upon risk of capture
     * - Cancel upon blocked
     * - End automation upon finish
     */
    // TODO: Caching
    @Suppress("UNUSED_PARAMETER")  // tilesWhereWeWillBeCaptured may be useful in the future
    fun automateConnectRoad(unitView: MapUnitView, tilesWhereWeWillBeCaptured: Set<TileView>) {
        if (actualBestRoadAvailable == RoadStatus.None) return

        var currentTile = unitView.getTile()

        val destinationTile = unitView.getAutomatedRoadConnectionDestination()
        if (destinationTile == null) {
            stopAndCleanAutomation(unitView)
            return
        }

        fun notify(msg: String) = unitView.tryAddNotification(msg, NotificationCategory.Units, NotificationIcon.Construction)

        // The path is rebuilt on every call, so it always starts from wherever the worker is now
        val pathToDest = unitView.getRoadPath(destinationTile)
        if (pathToDest == null) {
            Log.debug("WorkerAutomation: ${unitView.name} -> connect road failed")
            stopAndCleanAutomation(unitView)
            notify("Connect road failed!")
            return
        }
        debug("WorkerAutomation: ${unitView.name} -> found connect road path to destination tile: %s, %s", destinationTile.position(), pathToDest.map { it.position() })

        val currTileIndex = pathToDest.indexOf(currentTile)

        /* Cannot build a road on this tile, try to move on.
        * The worker should search for the next furthest tile in the path that:
        * - It can move to
        * - Can be improved/upgraded
        * */
        if (unitView.hasMovement() && !shouldBuildRoadOnTile(currentTile)) {
            if (currTileIndex == pathToDest.size - 1) { // The last tile in the path is unbuildable or has a road.
                stopAndCleanAutomation(unitView)
                notify("Connect road completed")
                return
            }

            if (currTileIndex < pathToDest.size - 1) { // Try to move to the next tile in the path
                var nextTile = currentTile

                // Create a new list with tiles where the index is greater than currTileIndex
                val futureTiles = pathToDest.asSequence().drop(currTileIndex + 1)

                for (futureTile in futureTiles) { // Find the furthest tile we can reach in this turn, move to, and does not have a road
                    if (unitView.canReachInCurrentTurn(futureTile) && unitView.canMoveTo(futureTile)) { // We can at least move to this tile
                        nextTile = futureTile
                        if (shouldBuildRoadOnTile(futureTile)) {
                            break // Stop on this tile
                        }
                    }
                }

                if (nextTile != currentTile) {
                    unitView.tryMoveToTile(nextTile)
                    currentTile = unitView.getTile()
                }
            }
        }

        // We need to check current movement again after we've (potentially) moved
        if (!unitView.hasMovement()) return
        // Repair pillaged roads first - try to build a new one if a mod removed repair
        val ruleset = currentTile.getRuleset()
        val repairImprovement = if (currentTile.roadStatus == RoadStatus.None || !currentTile.roadIsPillaged) null
            else ruleset.tileImprovements[Constants.repair]
        val buildImprovement = if (!shouldBuildRoadOnTile(currentTile) || currentTile.improvementInProgress == actualBestRoadAvailable.name) null
            else actualBestRoadAvailable.improvement(ruleset)
        (repairImprovement ?: buildImprovement)?.let { unitView.tryStartWorkingOnImprovement(it) }
    }

    /** Reset side effects from automation, return worker to non-automated state*/
    fun stopAndCleanAutomation(unitView: MapUnitView) {
        unitView.tryStopAutomation()
        val tile = unitView.getTile()
        if (tile.getRuleset().tileImprovements[tile.improvementInProgress]?.isRoad() == true)
            unitView.tryStopWorkingOnImprovement()
    }

    /** Conditions for whether it is acceptable to build a road on this tile */
    @Readonly
    fun shouldBuildRoadOnTile(tile: TileView): Boolean {
        if (tile.isMarkedForCreatesOneImprovement()) return false
        if (tile.roadIsPillaged) return true
        return !tile.isCityCenter() // Can't build road on city tiles
            // Special case for civs that treat forest/jungles as roads (inside their territory). We shouldn't build if railroads aren't unlocked.
            && !(tile.hasConnection() && actualBestRoadAvailable == RoadStatus.Road)
            && tile.roadStatus != actualBestRoadAvailable // Build (upgrade) if possible
    }
}
