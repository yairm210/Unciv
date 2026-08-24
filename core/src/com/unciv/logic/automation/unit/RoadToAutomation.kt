package com.unciv.logic.automation.unit

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
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
    fun automateConnectRoad(unit: MapUnit, tilesWhereWeWillBeCaptured: Set<Tile>) {
        if (actualBestRoadAvailable == RoadStatus.None) return

        var currentTile = unit.getTile()

        if (unit.automatedRoadConnectionDestination == null) {
            stopAndCleanAutomation(unit)
            return
        }

        fun notify(msg: String) =
            unit.civ.addNotification(msg, MapUnitAction(unit), NotificationCategory.Units, NotificationIcon.Construction)

        val destinationTile = unit.civ.gameInfo.tileMap[unit.automatedRoadConnectionDestination!!]

        var pathToDest: List<HexCoord>? = unit.automatedRoadConnectionPath

        // The path does not exist, create it
        if (pathToDest == null) {
            val foundPath: List<Tile>? = unit.movement.getRoadPath(destinationTile)
            if (foundPath == null) {
                Log.debug("WorkerAutomation: $unit -> connect road failed")
                stopAndCleanAutomation(unit)
                notify("Connect road failed!")
                return
            }

            pathToDest = foundPath // Convert to a list of positions for serialization
                .map { it.position }

            unit.automatedRoadConnectionPath = pathToDest
            debug("WorkerAutomation: $unit -> found connect road path to destination tile: %s, %s", destinationTile, pathToDest)
        }

        val currTileIndex = pathToDest.indexOf(currentTile.position)

        // The worker was somehow moved off its path, cancel the action
        if (currTileIndex == -1) {
            Log.debug("$unit -> was moved off its connect road path. Operation cancelled.")
            stopAndCleanAutomation(unit)
            notify("Connect road cancelled!")
            return
        }

        /* Cannot build a road on this tile, try to move on.
        * The worker should search for the next furthest tile in the path that:
        * - It can move to
        * - Can be improved/upgraded
        * */
        if (unit.hasMovement() && !shouldBuildRoadOnTile(currentTile)) {
            if (currTileIndex == pathToDest.size - 1) { // The last tile in the path is unbuildable or has a road.
                stopAndCleanAutomation(unit)
                notify("Connect road completed")
                return
            }

            if (currTileIndex < pathToDest.size - 1) { // Try to move to the next tile in the path
                val tileMap = unit.civ.gameInfo.tileMap
                var nextTile: Tile = currentTile

                // Create a new list with tiles where the index is greater than currTileIndex
                val futureTiles = pathToDest.asSequence()
                    .drop(currTileIndex + 1)
                    .map { tileMap[it] }

                for (futureTile in futureTiles) { // Find the furthest tile we can reach in this turn, move to, and does not have a road
                    if (unit.movement.canReachInCurrentTurn(futureTile) && unit.movement.canMoveTo(futureTile)) { // We can at least move to this tile
                        nextTile = futureTile
                        if (shouldBuildRoadOnTile(futureTile)) {
                            break // Stop on this tile
                        }
                    }
                }

                if (nextTile.position != currentTile.position) {
                    unit.movement.moveToTile(nextTile)
                    currentTile = unit.getTile()
                }
            }
        }

        // We need to check current movement again after we've (potentially) moved
        if (!unit.hasMovement()) return
        // Repair pillaged roads first - try to build a new one if a mod removed repair
        val repairImprovement = if (currentTile.roadStatus == RoadStatus.None || !currentTile.roadIsPillaged) null
            else civInfo.gameInfo.ruleset.tileImprovements[Constants.repair]
        val buildImprovement = if (!shouldBuildRoadOnTile(currentTile) || currentTile.improvementInProgress == actualBestRoadAvailable.name) null
            else actualBestRoadAvailable.improvement(civInfo.gameInfo.ruleset)
        (repairImprovement ?: buildImprovement)?.let { currentTile.startWorkingOnImprovement(it, civInfo, unit) }
    }

    /** Reset side effects from automation, return worker to non-automated state*/
    fun stopAndCleanAutomation(unit: MapUnit) {
        unit.automated = false
        unit.action = null
        unit.automatedRoadConnectionDestination = null
        unit.automatedRoadConnectionPath = null
        if (unit.currentTile.getTileImprovementInProgress()?.isRoad() == true)
            unit.currentTile.stopWorkingOnImprovement()
    }

    /** Conditions for whether it is acceptable to build a road on this tile */
    @Readonly
    fun shouldBuildRoadOnTile(tile: Tile): Boolean {
        if (tile.isMarkedForCreatesOneImprovement()) return false
        if (tile.roadIsPillaged) return true
        return !tile.isCityCenter() // Can't build road on city tiles
            // Special case for civs that treat forest/jungles as roads (inside their territory). We shouldn't build if railroads aren't unlocked.
            && !(tile.hasConnection(civInfo) && actualBestRoadAvailable == RoadStatus.Road)
            && tile.roadStatus != actualBestRoadAvailable // Build (upgrade) if possible
    }
}
