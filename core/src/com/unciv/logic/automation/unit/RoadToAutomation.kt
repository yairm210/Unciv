package com.unciv.logic.automation.unit

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.MapPathing
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.utils.Log
import com.unciv.utils.debug
import yairm210.purity.annotations.Readonly


/** Responsible for automation the "build road to" action
 * This is *pretty bad code* overall and needs to be cleaned up */
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
    // TODO: Hide the automate road button if road is not unlocked
    @Suppress("UNUSED_PARAMETER")  // tilesWhereWeWillBeCaptured may be useful in the future
    fun automateConnectRoad(unit: MapUnit, tilesWhereWeWillBeCaptured: Set<Tile>){
        if (actualBestRoadAvailable == RoadStatus.None) return

        var currentTile = unit.getTile()


        if (unit.automatedRoadConnectionDestination == null){
            stopAndCleanAutomation(unit)
            return
        }


        val destinationTile = unit.civ.gameInfo.tileMap[unit.automatedRoadConnectionDestination!!]

        var pathToDest: List<Vector2>? = unit.automatedRoadConnectionPath

        // The path does not exist, create it
        if (pathToDest == null) {
            val foundPath: List<Tile>? = MapPathing.getRoadPath(unit.civ, unit.getTile(), destinationTile)
            if (foundPath == null) {
                Log.debug("WorkerAutomation: $unit -> connect road failed")
                stopAndCleanAutomation(unit)
                unit.civ.addNotification("Connect road failed!", MapUnitAction(unit), NotificationCategory.Units, NotificationIcon.Construction)
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
            unit.civ.addNotification("Connect road cancelled!", MapUnitAction(unit), NotificationCategory.Units, unit.name)
            return
        }

        /* Can not build a road on this tile, try to move on.
        * The worker should search for the next furthest tile in the path that:
        * - It can move to
        * - Can be improved/upgraded
        * */
        if (unit.hasMovement() && !shouldBuildRoadOnTile(currentTile)) {
            if (currTileIndex == pathToDest.size - 1) { // The last tile in the path is unbuildable or has a road.
                stopAndCleanAutomation(unit)
                unit.civ.addNotification("Connect road completed", MapUnitAction(unit), NotificationCategory.Units, unit.name)
                return
            }

            if (currTileIndex < pathToDest.size - 1) { // Try to move to the next tile in the path
                val tileMap = unit.civ.gameInfo.tileMap
                var nextTile: Tile = currentTile

                // Create a new list with tiles where the index is greater than currTileIndex
                val futureTiles = pathToDest.asSequence()
                    .dropWhile { it != unit.currentTile.position }
                    .drop(1)
                    .map { tileMap[it] }



                for (futureTile in futureTiles) { // Find the furthest tile we can reach in this turn, move to, and does not have a road
                    if (unit.movement.canReachInCurrentTurn(futureTile) && unit.movement.canMoveTo(futureTile)) { // We can at least move to this tile
                        nextTile = futureTile
                        if (shouldBuildRoadOnTile(futureTile)) {
                            break // Stop on this tile
                        }
                    }
                }

                unit.movement.moveToTile(nextTile)
                currentTile = unit.getTile()
            }
        }

        // We need to check current movement again after we've (potentially) moved
        if (unit.hasMovement()) {
            // Repair pillaged roads first
            if (currentTile.roadStatus != RoadStatus.None && currentTile.roadIsPillaged){
                currentTile.setRepaired()
                return
            }
            if (shouldBuildRoadOnTile(currentTile) && currentTile.improvementInProgress != actualBestRoadAvailable.name) {
                val improvement = actualBestRoadAvailable.improvement(civInfo.gameInfo.ruleset)!!
                currentTile.startWorkingOnImprovement(improvement, civInfo, unit)
                return
            }
        }
    }

    /** Reset side effects from automation, return worker to non-automated state*/
    fun stopAndCleanAutomation(unit: MapUnit){
        unit.automated = false
        unit.action = null
        unit.automatedRoadConnectionDestination = null
        unit.automatedRoadConnectionPath = null
        unit.currentTile.stopWorkingOnImprovement()
    }


    /** Conditions for whether it is acceptable to build a road on this tile */
    @Readonly
    fun shouldBuildRoadOnTile(tile: Tile): Boolean {
        if (tile.roadIsPillaged) return true
        return !tile.isCityCenter() // Can't build road on city tiles
            // Special case for civs that treat forest/jungles as roads (inside their territory). We shouldn't build if railroads aren't unlocked.
            && !(tile.hasConnection(civInfo) && actualBestRoadAvailable == RoadStatus.Road)
            && tile.roadStatus != actualBestRoadAvailable // Build (upgrade) if possible
    }
}
